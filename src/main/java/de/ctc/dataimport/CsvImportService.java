package de.ctc.dataimport;

import de.ctc.domain.model.*;
import de.ctc.domain.model.Match;
import de.ctc.domain.repository.*;
import de.ctc.domain.service.ScoringService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final DriverMatchingService driverMatchingService;
    private final DriverRepository driverRepository;
    private final TeamRepository teamRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final MatchdayRepository matchdayRepository;
    private final MatchRepository matchRepository;
    private final RaceRepository raceRepository;
    private final PlayoffMatchupRepository playoffMatchupRepository;
    private final ScoringService scoringService;
    private final RaceLineupRepository raceLineupRepository;

    public ImportPreview parseAndPreview(InputStream csvStream, ImportMetadata metadata) throws IOException {
        var preview = new ImportPreview(metadata);
        var lines = readCsvLines(csvStream);

        for (int i = 0; i < lines.size(); i++) {
            var fields = lines.get(i);
            if (fields.length < 5) {
                preview.addError("Zeile " + (i + 2) + ": Zu wenige Spalten (erwartet: Team, PSN ID, Position, Quali, FL)");
                continue;
            }

            var teamShortName = fields[0].trim();
            var psnId = fields[1].trim();
            var position = parseIntSafe(fields[2].trim(), "Position", i + 2, preview);
            var qualiPosition = parseIntSafe(fields[3].trim(), "Quali", i + 2, preview);
            var fastestLap = parseBooleanSafe(fields[4].trim());

            if (position == null || qualiPosition == null) continue;

            var matchResult = driverMatchingService.findDriver(psnId);
            preview.addRow(new ImportRow(teamShortName, psnId, position, qualiPosition, fastestLap, matchResult));
        }

        return preview;
    }

    @Transactional
    public ImportResult executeImport(ImportPreview preview, Map<String, UUID> confirmedMatches,
                                      Set<String> createNewDrivers, boolean overwriteExisting) {
        var result = new ImportResult();
        var metadata = preview.getMetadata();

        // Resolve season
        var season = seasonRepository.findByName(metadata.seasonName()).orElseThrow(
                () -> new IllegalArgumentException("Season not found: " + metadata.seasonName()));

        // Resolve or create matchday
        var matchday = findOrCreateMatchday(season, metadata);

        // Process each row
        Map<String, List<ImportRow>> byTeamPair = groupByTeamPair(preview.getRows());

        for (var entry : byTeamPair.entrySet()) {
            var teamParts = entry.getKey().split("\\|");
            var homeTeam = findTeamFlexible(teamParts[0]);
            var awayTeam = teamParts.length > 1 ? findTeamFlexible(teamParts[1]) : null;

            if (homeTeam == null) {
                result.addError("Team nicht gefunden: " + teamParts[0]);
                continue;
            }
            if (awayTeam == null && teamParts.length > 1) {
                result.addError("Team nicht gefunden: " + teamParts[1]);
                continue;
            }

            var effectiveAwayTeam = awayTeam != null ? awayTeam : homeTeam;

            // Duplicate check: same home vs away on this matchday
            if (matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(
                    matchday.getId(), homeTeam.getId(), effectiveAwayTeam.getId())) {
                if (overwriteExisting) {
                    // Delete existing match (cascades to races + results)
                    var existing = matchRepository.findByMatchdayId(matchday.getId()).stream()
                            .filter(m -> m.getHomeTeam().getId().equals(homeTeam.getId())
                                    && m.getAwayTeam() != null
                                    && m.getAwayTeam().getId().equals(effectiveAwayTeam.getId()))
                            .findFirst();
                    existing.ifPresent(m -> {
                        matchRepository.delete(m);
                        matchRepository.flush();
                        log.info("Overwriting existing match: {} vs {} on {}",
                                homeTeam.getShortName(), effectiveAwayTeam.getShortName(), matchday.getLabel());
                    });
                } else {
                    result.addError("Match already exists: " + homeTeam.getShortName() +
                            " vs " + effectiveAwayTeam.getShortName() + " on " + matchday.getLabel());
                    continue;
                }
            }

            var match = new Match(matchday, homeTeam, effectiveAwayTeam);
            match = matchRepository.save(match);
            var race = new Race();
            race.setMatchday(matchday);
            race.setMatch(match);

            // Link to playoff matchup if applicable
            if (metadata.isPlayoff()) {
                var matchup = playoffMatchupRepository.findById(metadata.playoffMatchupId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Playoff-Matchup nicht gefunden: " + metadata.playoffMatchupId()));
                race.setPlayoffMatchup(matchup);
            }

            for (var row : entry.getValue()) {
                var driver = resolveDriver(row, confirmedMatches, createNewDrivers, result);
                if (driver == null) continue;

                // Ensure SeasonDriver exists
                ensureSeasonDriver(season, driver, row.teamShortName());

                var raceResult = new RaceResult(race, driver, row.position(), row.qualiPosition(), row.fastestLap());
                scoringService.calculatePoints(raceResult, season.getRaceScoring());
                race.getResults().add(raceResult);

                // Create RaceLineup for sub-teams
                var resolvedTeam = findTeamFlexible(row.teamShortName());
                if (resolvedTeam != null && resolvedTeam.isSubTeam()) {
                    var existingLineup = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId());
                    if (existingLineup.isEmpty()) {
                        raceLineupRepository.save(new RaceLineup(race, driver, resolvedTeam));
                        result.incrementLineupCount();
                    }
                }
            }

            raceRepository.save(race);
            scoringService.aggregateMatchScores(race);
            result.addImportedRace(homeTeam.getShortName() +
                    (awayTeam != null ? " vs " + awayTeam.getShortName() : ""));
        }

        log.info("Import completed: {} races, {} new drivers, {} errors",
                result.getImportedRaces().size(), result.getNewDriversCreated(), result.getErrors().size());
        return result;
    }

    private Driver resolveDriver(ImportRow row, Map<String, UUID> confirmedMatches,
                                  Set<String> createNewDrivers, ImportResult result) {
        var matchResult = row.matchResult();

        if (matchResult.type() == DriverMatchingService.MatchType.EXACT) {
            return matchResult.driver();
        }

        if (matchResult.type() == DriverMatchingService.MatchType.FUZZY) {
            var confirmedId = confirmedMatches.get(row.psnId());
            if (confirmedId != null) {
                return driverRepository.findById(confirmedId).orElse(null);
            }
        }

        if (createNewDrivers.contains(row.psnId()) || matchResult.type() == DriverMatchingService.MatchType.NONE) {
            var newDriver = new Driver(row.psnId(), row.psnId());
            driverRepository.save(newDriver);
            result.incrementNewDrivers();
            log.info("Created new driver: {}", row.psnId());
            return newDriver;
        }

        result.addError("Driver could not be assigned: " + row.psnId());
        return null;
    }

    private void ensureSeasonDriver(Season season, Driver driver, String teamShortName) {
        var existing = seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), driver.getId());
        if (existing.isEmpty()) {
            var team = findTeamFlexible(teamShortName);
            if (team != null) {
                seasonDriverRepository.save(new SeasonDriver(season, driver, team));
                log.debug("Created SeasonDriver: {} -> {} ({})", driver.getPsnId(), teamShortName, season.getName());
            }
        }
    }

    /**
     * Finds a team by short name with flexible matching:
     * 1. Exact match
     * 2. Case-insensitive match
     * 3. Normalized match (spaces ↔ underscores)
     */
    private Team findTeamFlexible(String shortName) {
        // 1. Exact match
        var exact = teamRepository.findByShortName(shortName);
        if (exact.isPresent()) return exact.get();

        // 2. Case-insensitive
        var caseInsensitive = teamRepository.findByShortNameIgnoreCase(shortName);
        if (caseInsensitive.isPresent()) return caseInsensitive.get();

        // 3. Try with spaces replaced by underscores and vice versa
        var withUnderscores = shortName.replace(" ", "_");
        var withSpaces = shortName.replace("_", " ");

        var alt1 = teamRepository.findByShortNameIgnoreCase(withUnderscores);
        if (alt1.isPresent()) return alt1.get();

        var alt2 = teamRepository.findByShortNameIgnoreCase(withSpaces);
        if (alt2.isPresent()) return alt2.get();

        return null;
    }

    private Matchday findOrCreateMatchday(Season season, ImportMetadata metadata) {
        if (metadata.hasMatchdayId()) {
            return matchdayRepository.findById(metadata.matchdayId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Matchday nicht gefunden: " + metadata.matchdayId()));
        }
        return matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId()).stream()
                .filter(md -> md.getLabel().equals(metadata.matchdayLabel()))
                .findFirst()
                .orElseGet(() -> {
                    var maxIndex = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId()).stream()
                            .mapToInt(Matchday::getSortIndex)
                            .max().orElse(0);
                    var md = new Matchday(season, metadata.matchdayLabel(), maxIndex + 1);
                    return matchdayRepository.save(md);
                });
    }

    private Map<String, List<ImportRow>> groupByTeamPair(List<ImportRow> rows) {
        var teams = rows.stream().map(ImportRow::teamShortName).distinct().toList();
        if (teams.size() == 2) {
            return Map.of(teams.get(0) + "|" + teams.get(1), rows);
        }
        // Fallback: group all under first team
        return Map.of(teams.isEmpty() ? "UNKNOWN" : teams.getFirst(), rows);
    }

    private List<String[]> readCsvLines(InputStream stream) throws IOException {
        var lines = new ArrayList<String[]>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    if (line.toLowerCase().contains("team") || line.toLowerCase().contains("psn")) {
                        continue; // Skip header
                    }
                }
                if (line.isBlank()) continue;
                lines.add(line.split("[,;\\t]", -1));
            }
        }
        return lines;
    }

    private Integer parseIntSafe(String value, String fieldName, int lineNumber, ImportPreview preview) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            preview.addError("Zeile " + lineNumber + ": Ungültiger Wert für " + fieldName + ": " + value);
            return null;
        }
    }

    private boolean parseBooleanSafe(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value)
                || "yes".equalsIgnoreCase(value) || "ja".equalsIgnoreCase(value)
                || "x".equalsIgnoreCase(value) || "✓".equals(value);
    }

    public record ImportMetadata(String seasonName, String matchdayLabel, String track, String car,
                                    UUID playoffMatchupId, UUID matchdayId) {
        public ImportMetadata(String seasonName, String matchdayLabel, String track, String car) {
            this(seasonName, matchdayLabel, track, car, null, null);
        }

        public ImportMetadata(String seasonName, String matchdayLabel, String track, String car,
                              UUID playoffMatchupId) {
            this(seasonName, matchdayLabel, track, car, playoffMatchupId, null);
        }

        public boolean isPlayoff() {
            return playoffMatchupId != null;
        }

        public boolean hasMatchdayId() {
            return matchdayId != null;
        }
    }

    public record ImportRow(String teamShortName, String psnId, int position, int qualiPosition,
                            boolean fastestLap, DriverMatchingService.MatchResult matchResult) {}

    public boolean checkDuplicate(ImportPreview preview) {
        var metadata = preview.getMetadata();
        var season = seasonRepository.findByName(metadata.seasonName()).orElse(null);
        if (season == null) return false;

        de.ctc.domain.model.Matchday matchday;
        if (metadata.hasMatchdayId()) {
            matchday = matchdayRepository.findById(metadata.matchdayId()).orElse(null);
        } else {
            matchday = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId()).stream()
                    .filter(md -> md.getLabel().equals(metadata.matchdayLabel()))
                    .findFirst().orElse(null);
        }
        if (matchday == null) return false;

        var teams = preview.getRows().stream().map(ImportRow::teamShortName).distinct().toList();
        if (teams.size() < 2) return false;

        var homeTeam = findTeamFlexible(teams.get(0));
        var awayTeam = findTeamFlexible(teams.get(1));
        if (homeTeam == null || awayTeam == null) return false;

        boolean exists = matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(
                matchday.getId(), homeTeam.getId(), awayTeam.getId());
        if (exists) {
            preview.setDuplicateDetected(true);
        }
        return exists;
    }

    @Getter
    public static class ImportPreview {
        private final ImportMetadata metadata;
        private final List<ImportRow> rows = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        @lombok.Setter
        private boolean duplicateDetected;

        public ImportPreview(ImportMetadata metadata) {
            this.metadata = metadata;
        }

        public void addRow(ImportRow row) { rows.add(row); }
        public void addError(String error) { errors.add(error); }

        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasFuzzyMatches() { return rows.stream().anyMatch(r -> r.matchResult().needsConfirmation()); }
        public boolean hasNewDrivers() { return rows.stream().anyMatch(r -> !r.matchResult().isMatch()); }
    }

    @Getter
    public static class ImportResult {
        private final List<String> importedRaces = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private int newDriversCreated;
        private int lineupCount;

        public void addImportedRace(String race) { importedRaces.add(race); }
        public void addError(String error) { errors.add(error); }
        public void incrementNewDrivers() { newDriversCreated++; }
        public void incrementLineupCount() { lineupCount++; }
        public boolean hasErrors() { return !errors.isEmpty(); }
    }
}
