package org.ctc.dataimport;

import static org.ctc.util.LogSanitizer.sanitize;
import static org.springframework.util.StringUtils.hasText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.dataimport.exception.ImportRejectedException;
import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.PlayoffDecisionGuard;
import org.ctc.domain.service.ScoringService;
import org.ctc.domain.service.SeasonPhaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

	private final DriverMatchingService driverMatchingService;
	private final DriverRepository driverRepository;
	private final SeasonRepository seasonRepository;
	private final SeasonDriverRepository seasonDriverRepository;
	private final MatchdayRepository matchdayRepository;
	private final MatchRepository matchRepository;
	private final RaceRepository raceRepository;
	private final PlayoffMatchupRepository playoffMatchupRepository;
	private final PlayoffRepository playoffRepository;
	private final ScoringService scoringService;
	private final RaceLineupRepository raceLineupRepository;
	private final SeasonPhaseService seasonPhaseService;

	/**
	 * Returns all seasons for the import form.
	 */
	public List<Season> getAllSeasons() {
		return seasonRepository.findAll();
	}

	/**
	 * Returns playoff matchups for all seasons that have playoffs (for the import form dropdown).
	 */
	public List<PlayoffMatchupDto> getPlayoffMatchups() {
		List<PlayoffMatchupDto> matchups = new ArrayList<>();
		for (var season : seasonRepository.findAll()) {
			playoffRepository.findBySeasonId(season.getId()).ifPresent(playoff -> {
				var playoffMatchups = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId());
				for (var matchup : playoffMatchups) {
					if (matchup.isReady() && !matchup.isComplete()) {
						matchups.add(new PlayoffMatchupDto(
								matchup.getId(),
								season.getDisplayLabel(),
								matchup.getRound().getLabel(),
								matchup.getTeam1().getShortName(),
								matchup.getTeam2().getShortName()
						));
					}
				}
			});
		}
		return matchups;
	}

	/**
	 * Returns the matchday label for a given matchday ID.
	 */
	public Optional<String> getMatchdayLabel(UUID matchdayId) {
		return matchdayRepository.findById(matchdayId).map(Matchday::getLabel);
	}

	public ImportPreview parseAndPreview(InputStream csvStream, ImportMetadata metadata) throws IOException {
		var preview = new ImportPreview(metadata);
		var lines = readCsvLines(csvStream);

		for (int i = 0; i < lines.size(); i++) {
			var fields = lines.get(i);
			if (fields.length < 5) {
				preview.addError("Row " + (i + 2) + ": Too few columns (expected: Team, PSN ID, Position, Quali, FL)");
				continue;
			}

			var teamShortName = fields[0].trim();
			var psnId = fields[1].trim();
			var position = parseIntSafe(fields[2].trim(), "Position", i + 2, preview);
			var qualiPosition = parseIntSafe(fields[3].trim(), "Quali", i + 2, preview);
			var fastestLap = parseBooleanSafe(fields[4].trim());

			if (position == null || qualiPosition == null) {
				continue;
			}

			var matchResult = driverMatchingService.findDriver(psnId);
			preview.addRow(new ImportRow(teamShortName, psnId, position, qualiPosition, fastestLap, matchResult));
		}

		return preview;
	}

	@Transactional
	public ImportResult executeImport(ImportPreview preview, Map<String, UUID> confirmedMatches,
	                                  Set<String> createNewDrivers, boolean overwriteExisting) {
		return executeMultiRaceImport(List.of(preview), confirmedMatches, createNewDrivers, overwriteExisting);
	}

	/**
	 * Executes import for multiple race previews, reusing matches across races.
	 * For the same team pairing, creates one Match with multiple Races (for legs).
	 *
	 * @param previews          list of ImportPreview objects (one per race)
	 * @param confirmedMatches  map of fuzzy driver matches confirmed by user
	 * @param createNewDrivers  set of PSN IDs to create as new drivers
	 * @param overwriteExisting whether to overwrite existing matches
	 * @return cumulative import result
	 * @throws ImportRejectedException when any preview row, the metadata or a race row is invalid;
	 *                                 nothing is written in that case
	 */
	@Transactional
	public ImportResult executeMultiRaceImport(List<ImportPreview> previews, Map<String, UUID> confirmedMatches,
	                                           Set<String> createNewDrivers, boolean overwriteExisting) {
		var result = new ImportResult();
		rejectInvalidPreviews(previews);

		var metadata = previews.get(0).getMetadata();
		var season = seasonRepository.findById(metadata.seasonId()).orElseThrow(
				() -> new ValidationException("Season not found in CSV import: " + metadata.seasonId()));
		rejectInvalidTarget(season, metadata, previews);

		// Resolve or create matchday
		var matchday = findOrCreateMatchday(season, metadata);
		var raceScoring = matchday.getPhase().getRaceScoring();
		if (raceScoring == null) {
			throw new ImportRejectedException(List.of("The target phase has no race scoring"));
		}

		// Group all rows from all previews by team pair
		var seasonTeams = season.getTeams();
		Map<String, List<ImportRow>> byTeamPair = new HashMap<>();
		Map<String, Map<Integer, List<ImportRow>>> byTeamPairAndRaceIndex = new HashMap<>();

		for (int raceIndex = 0; raceIndex < previews.size(); raceIndex++) {
			var preview = previews.get(raceIndex);
			var grouped = groupByTeamPair(preview.getRows(), seasonTeams);

			for (var entry : grouped.entrySet()) {
				var teamPair = entry.getKey();
				var rows = entry.getValue();

				// Track by race index for later processing
				byTeamPairAndRaceIndex.computeIfAbsent(teamPair, k -> new HashMap<>())
						.put(raceIndex, rows);

				// Also add to flat list for compatibility
				byTeamPair.computeIfAbsent(teamPair, k -> new ArrayList<>()).addAll(rows);
			}
		}

		// Process each team pairing once, but handle multiple races
		for (var entry : byTeamPairAndRaceIndex.entrySet()) {
			// The first leg's block order orients a new pairing; later legs may list the teams reversed.
			var firstLegRows = entry.getValue().get(Collections.min(entry.getValue().keySet()));
			var teamNames = firstLegRows.isEmpty() ? List.of(entry.getKey()) : pairingTeams(firstLegRows, seasonTeams);
			var homeTeam = findTeamFlexible(teamNames.getFirst(), seasonTeams);
			var awayTeam = teamNames.size() > 1 ? findTeamFlexible(teamNames.get(1), seasonTeams) : null;

			if (homeTeam == null) {
				result.addError("Team not found: " + teamNames.getFirst());
				continue;
			}
			if (awayTeam == null && teamNames.size() > 1) {
				result.addError("Team not found: " + teamNames.get(1));
				continue;
			}

			var effectiveAwayTeam = awayTeam != null ? awayTeam : homeTeam;

			// Duplicate check: the same pairing on this matchday, in either orientation
			var existingMatch = findPairing(matchday, homeTeam, effectiveAwayTeam);

			Match match;
			if (existingMatch.isPresent()) {
				if (overwriteExisting) {
					match = existingMatch.get();
					// Delete existing races (cascades to results)
					var racesToDelete = raceRepository.findByMatchId(match.getId());
					if (racesToDelete.stream().anyMatch(race -> race.getPlayoffMatchup() != null
							&& race.getPlayoffMatchup().isComplete())) {
						throw new ImportRejectedException(List.of(PlayoffDecisionGuard.DECIDED));
					}
					for (var race : racesToDelete) {
						raceLineupRepository.deleteAll(raceLineupRepository.findByRaceId(race.getId()));
						match.getRaces().remove(race);
						raceRepository.delete(race);
					}
					raceRepository.flush();
					log.info("Overwriting existing match: {} vs {} on {}",
							sanitize(homeTeam.getShortName()), sanitize(effectiveAwayTeam.getShortName()), sanitize(matchday.getLabel()));
				} else {
					result.addError("Match already exists: " + homeTeam.getShortName() +
							" vs " + effectiveAwayTeam.getShortName() + " on " + matchday.getLabel());
					continue;
				}
			} else {
				match = new Match(matchday, homeTeam, effectiveAwayTeam);
				match = matchRepository.save(match);
			}

			// Now create a race for each preview (leg)
			for (int raceIndex = 0; raceIndex < previews.size(); raceIndex++) {
				var raceRows = entry.getValue().get(raceIndex);
				if (raceRows == null || raceRows.isEmpty()) {
					continue; // This team pair doesn't appear in this race
				}

				var race = new Race();
				race.setMatchday(matchday);
				race.setMatch(match);
				var legHome = findTeamFlexible(raceRows.getFirst().teamShortName(), seasonTeams);
				if (legHome != null && match != null && match.getAwayTeam() != null
						&& !legHome.getId().equals(match.getHomeTeam().getId())) {
					race.setHomeTeamOverride(match.getAwayTeam());
					race.setAwayTeamOverride(match.getHomeTeam());
				}

				// Link to playoff matchup if applicable
				if (metadata.isPlayoff()) {
					var matchup = playoffMatchupRepository.findById(metadata.playoffMatchupId())
							.orElseThrow(() -> new ValidationException(
									"Playoff matchup not found in CSV import: " + metadata.playoffMatchupId()));
					race.setPlayoffMatchup(matchup);
				}

				// Save race early to get ID for RaceLineup references
				race = raceRepository.save(race);

				for (var row : raceRows) {
					var driver = resolveDriver(row, confirmedMatches, createNewDrivers, result);
					if (driver == null) {
						continue;
					}

					// Ensure SeasonDriver exists
					ensureSeasonDriver(season, driver, row.teamShortName());

					var raceResult = new RaceResult(race, driver, row.position(), row.qualiPosition(), row.fastestLap());
					scoringService.calculatePoints(raceResult, raceScoring);
					race.getResults().add(raceResult);

					// Create RaceLineup for all teams
					var resolvedTeam = findTeamFlexible(row.teamShortName(), seasonTeams);
					if (resolvedTeam != null) {
						var existingLineup = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId());
						if (existingLineup.isEmpty()) {
							raceLineupRepository.save(new RaceLineup(race, driver, resolvedTeam));
							result.incrementLineupCount();
						}
					}
				}

				raceRepository.save(race);
				scoringService.aggregateMatchScores(race);
				result.addImportedRace((raceIndex + 1) + ". " + homeTeam.getShortName() +
						(awayTeam != null ? " vs " + awayTeam.getShortName() : ""));
			}
		}

		if (result.hasErrors()) {
			log.warn("Import rejected after {} validation errors; rolling back", result.getErrors().size());
			throw new ImportRejectedException(result.getErrors());
		}
		log.info("Import completed: {} races, {} new drivers",
				result.getImportedRaces().size(), result.getNewDriversCreated());
		return result;
	}

	private static void rejectInvalidPreviews(List<ImportPreview> previews) {
		if (previews.isEmpty()) {
			throw new ImportRejectedException(List.of("No previews provided for import"));
		}
		var errors = new ArrayList<String>();
		var metadata = previews.get(0).getMetadata();
		for (int i = 0; i < previews.size(); i++) {
			var preview = previews.get(i);
			var prefix = previews.size() > 1 ? "Race " + (i + 1) + ": " : "";
			preview.getErrors().forEach(error -> errors.add(prefix + error));
			if (!Objects.equals(preview.getMetadata(), metadata)) {
				errors.add(prefix + "import metadata differs from race 1");
			}
		}
		if (!errors.isEmpty()) {
			throw new ImportRejectedException(errors);
		}
	}

	/** Rejects metadata that points outside the season or at a playoff matchup the scorecards do not belong to. */
	private void rejectInvalidTarget(Season season, ImportMetadata metadata, List<ImportPreview> previews) {
		var errors = new ArrayList<String>();
		var matchday = metadata.hasMatchdayId() ? matchdayRepository.findById(metadata.matchdayId()).orElse(null) : null;
		if (matchday != null && !matchday.getSeason().getId().equals(season.getId())) {
			errors.add("The matchday does not belong to the selected season");
		}
		if (!metadata.isPlayoff()) {
			if (matchday != null && matchday.getPhase().getPhaseType() == PhaseType.PLAYOFF) {
				errors.add("A playoff matchday needs a playoff matchup");
			}
			rejectIfAny(errors);
			return;
		}
		var matchup = playoffMatchupRepository.findById(metadata.playoffMatchupId()).orElse(null);
		if (matchup == null) {
			errors.add("The playoff matchup does not exist");
			rejectIfAny(errors);
			return;
		}
		if (matchup.isComplete()) {
			throw new ImportRejectedException(List.of(PlayoffDecisionGuard.DECIDED));
		}
		var playoffPhase = matchup.getRound().getPlayoff().getPhase();
		if (!playoffPhase.getSeason().getId().equals(season.getId())) {
			errors.add("The playoff matchup does not belong to the selected season");
		} else {
			errors.addAll(foreignPairings(matchup, previews, season.getTeams()));
		}
		if (matchday != null && !matchday.getPhase().getId().equals(playoffPhase.getId())) {
			errors.add("The matchday does not belong to the playoff matchup's phase");
		}
		rejectIfAny(errors);
	}

	private List<String> foreignPairings(PlayoffMatchup matchup, List<ImportPreview> previews, List<Team> seasonTeams) {
		var participants = Stream.of(matchup.getTeam1(), matchup.getTeam2())
				.filter(Objects::nonNull).map(Team::getId).collect(Collectors.toSet());
		var errors = new LinkedHashSet<String>();
		for (var preview : previews) {
			var names = List.copyOf(blockOrder(preview.getRows(), seasonTeams).values());
			var teamIds = names.stream().map(name -> findTeamFlexible(name, seasonTeams))
					.filter(Objects::nonNull).map(Team::getId).collect(Collectors.toSet());
			if (teamIds.size() == names.size() && !teamIds.equals(participants)) {
				errors.add("The scorecard teams " + String.join(" and ", names) + " are not the teams of the playoff matchup");
			}
		}
		return List.copyOf(errors);
	}

	private static void rejectIfAny(List<String> errors) {
		if (!errors.isEmpty()) {
			throw new ImportRejectedException(errors);
		}
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
				var confirmed = driverRepository.findById(confirmedId);
				if (confirmed.isEmpty()) {
					result.addError("Confirmed driver no longer exists for: " + row.psnId());
				}
				return confirmed.orElse(null);
			}
		}

		if (createNewDrivers.contains(row.psnId()) || matchResult.type() == DriverMatchingService.MatchType.NONE) {
			// Check if driver already exists to avoid constraint violation
			var existingDriver = driverRepository.findByPsnId(row.psnId());
			if (existingDriver.isPresent()) {
				return existingDriver.get();
			}

			var newDriver = new Driver(row.psnId(), row.psnId());
			driverRepository.save(newDriver);
			result.incrementNewDrivers();
			log.info("Created new driver: {}", sanitize(row.psnId()));
			return newDriver;
		}

		result.addError("Driver could not be assigned: " + row.psnId());
		return null;
	}

	private void ensureSeasonDriver(Season season, Driver driver, String teamShortName) {
		var team = findTeamFlexible(teamShortName, season.getTeams());
		if (team == null) {
			return;
		}

		var existing = seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), driver.getId());
		if (existing.isEmpty()) {
			seasonDriverRepository.save(new SeasonDriver(season, driver, team));
			log.debug("Created SeasonDriver: {} -> {} ({})", sanitize(driver.getPsnId()), sanitize(teamShortName), sanitize(season.getName()));
		} else if (!existing.get().getTeam().getId().equals(team.getId())) {
			existing.get().setTeam(team);
			seasonDriverRepository.save(existing.get());
			log.debug("Updated SeasonDriver: {} -> {} ({})", sanitize(driver.getPsnId()), sanitize(teamShortName), sanitize(season.getName()));
		}
	}

	/**
	 * Finds a team by short name within the season's assigned teams.
	 * Flexible matching: exact → case-insensitive → normalized (spaces ↔ underscores).
	 */
	private Team findTeamFlexible(String shortName, List<Team> seasonTeams) {
		// 1. Exact match within season teams
		for (var team : seasonTeams) {
			if (team.getShortName().equals(shortName)) {
				return team;
			}
		}
		// 2. Case-insensitive
		for (var team : seasonTeams) {
			if (team.getShortName().equalsIgnoreCase(shortName)) {
				return team;
			}
		}
		// 3. Normalized (spaces ↔ underscores)
		var withUnderscores = shortName.replace(" ", "_");
		var withSpaces = shortName.replace("_", " ");
		for (var team : seasonTeams) {
			var sn = team.getShortName();
			if (sn.equalsIgnoreCase(withUnderscores) || sn.equalsIgnoreCase(withSpaces)) {
				return team;
			}
		}
		return null;
	}

	/** The phase a label-addressed matchday lives in: the playoff phase for a playoff import, otherwise REGULAR. */
	private SeasonPhase labelPhase(Season season, ImportMetadata metadata) {
		if (metadata.isPlayoff()) {
			return playoffMatchupRepository.findById(metadata.playoffMatchupId())
					.orElseThrow(() -> new ValidationException(
							"Playoff matchup not found in CSV import: " + metadata.playoffMatchupId()))
					.getRound().getPlayoff().getPhase();
		}
		return seasonPhaseService.findRegularPhase(season.getId());
	}

	private Matchday findOrCreateMatchday(Season season, ImportMetadata metadata) {
		if (metadata.hasMatchdayId()) {
			return matchdayRepository.findById(metadata.matchdayId())
					.orElseThrow(() -> new ValidationException(
							"Matchday not found in CSV import: " + metadata.matchdayId()));
		}
		// Label lookup and sortIndex stay inside one phase, so a same-named matchday of another
		// phase is never reused and PLAYOFF indexes (>= 100) never shift the REGULAR ones.
		var phase = labelPhase(season, metadata);
		var phaseMatchdays = matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phase.getId());
		return phaseMatchdays.stream()
				.filter(md -> md.getLabel().equals(metadata.matchdayLabel()))
				.findFirst()
				.orElseGet(() -> {
					var maxIndex = phaseMatchdays.stream()
							.mapToInt(Matchday::getSortIndex)
							.max().orElse(phase.getPhaseType() == PhaseType.PLAYOFF ? 99 : 0);
					var md = new Matchday(phase, metadata.matchdayLabel(), maxIndex + 1);
					return matchdayRepository.save(md);
				});
	}

	/** Keys a scorecard by its two teams regardless of block order, so A/B and B/A are one pairing. */
	private Map<String, List<ImportRow>> groupByTeamPair(List<ImportRow> rows, List<Team> seasonTeams) {
		var blocks = blockOrder(rows, seasonTeams);
		if (blocks.size() == 2) {
			return Map.of(blocks.keySet().stream().sorted().collect(Collectors.joining("|")), rows);
		}
		// Fallback: group all under first team
		return Map.of(blocks.isEmpty() ? "UNKNOWN" : blocks.firstEntry().getKey(), rows);
	}

	/** The pairing's two teams in block order, or only the first team when the scorecard is no pairing. */
	private List<String> pairingTeams(List<ImportRow> rows, List<Team> seasonTeams) {
		var names = List.copyOf(blockOrder(rows, seasonTeams).values());
		return names.size() == 2 ? names : names.subList(0, 1);
	}

	/**
	 * Team names in the order their blocks appear, keyed by the resolved team so spelling variants of
	 * one team collapse while different teams with similar short names stay apart.
	 */
	private LinkedHashMap<String, String> blockOrder(List<ImportRow> rows, List<Team> seasonTeams) {
		var byTeam = new LinkedHashMap<String, String>();
		rows.forEach(row -> byTeam.putIfAbsent(teamKey(row.teamShortName(), seasonTeams), row.teamShortName()));
		return byTeam;
	}

	private String teamKey(String teamShortName, List<Team> seasonTeams) {
		var team = findTeamFlexible(teamShortName, seasonTeams);
		return team != null ? team.getId().toString() : teamShortName.replace('_', ' ').toLowerCase(Locale.ROOT);
	}

	private Optional<Match> findPairing(Matchday matchday, Team home, Team away) {
		return matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(matchday.getId(), home.getId(), away.getId())
				.or(() -> matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(
						matchday.getId(), away.getId(), home.getId()));
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
				if (!hasText(line)) {
					continue;
				}
				lines.add(line.split("[,;\\t]", -1));
			}
		}
		return lines;
	}

	private Integer parseIntSafe(String value, String fieldName, int lineNumber, ImportPreview preview) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			preview.addError("Row " + lineNumber + ": Invalid value for " + fieldName + ": " + value);
			return null;
		}
	}

	private boolean parseBooleanSafe(String value) {
		return "true".equalsIgnoreCase(value) || "1".equals(value)
				|| "yes".equalsIgnoreCase(value) || "ja".equalsIgnoreCase(value)
				|| "x".equalsIgnoreCase(value) || "✓".equals(value);
	}

	public boolean checkDuplicate(ImportPreview preview) {
		var metadata = preview.getMetadata();
		var season = seasonRepository.findById(metadata.seasonId()).orElse(null);
		if (season == null) {
			return false;
		}

		org.ctc.domain.model.Matchday matchday;
		if (metadata.hasMatchdayId()) {
			matchday = matchdayRepository.findById(metadata.matchdayId()).orElse(null);
		} else {
			matchday = matchdayRepository.findByPhaseIdOrderBySortIndexAsc(labelPhase(season, metadata).getId()).stream()
					.filter(md -> md.getLabel().equals(metadata.matchdayLabel()))
					.findFirst().orElse(null);
		}
		if (matchday == null) {
			return false;
		}

		var seasonTeams = season.getTeams();
		var teams = List.copyOf(blockOrder(preview.getRows(), seasonTeams).values());
		if (teams.size() < 2) {
			return false;
		}

		var homeTeam = findTeamFlexible(teams.get(0), seasonTeams);
		var awayTeam = findTeamFlexible(teams.get(1), seasonTeams);
		if (homeTeam == null || awayTeam == null) {
			return false;
		}

		boolean exists = findPairing(matchday, homeTeam, awayTeam).isPresent();
		if (exists) {
			preview.setDuplicateDetected(true);
		}
		return exists;
	}

	public record PlayoffMatchupDto(UUID id, String seasonDisplayLabel, String roundLabel,
	                                String team1, String team2) {
		public String displayLabel() {
			return roundLabel + ": " + team1 + " vs " + team2;
		}
	}

	public record ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car,
	                             UUID playoffMatchupId, UUID matchdayId) {
		public ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car) {
			this(seasonId, matchdayLabel, track, car, null, null);
		}

		public ImportMetadata(UUID seasonId, String matchdayLabel, String track, String car,
		                      UUID playoffMatchupId) {
			this(seasonId, matchdayLabel, track, car, playoffMatchupId, null);
		}

		public boolean isPlayoff() {
			return playoffMatchupId != null;
		}

		public boolean hasMatchdayId() {
			return matchdayId != null;
		}
	}

	public record ImportRow(String teamShortName, String psnId, int position, int qualiPosition,
	                        boolean fastestLap, DriverMatchingService.MatchResult matchResult) {
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

		public void addRow(ImportRow row) {
			rows.add(row);
		}

		public void addError(String error) {
			errors.add(error);
		}

		public boolean hasErrors() {
			return !errors.isEmpty();
		}

		public boolean hasFuzzyMatches() {
			return rows.stream().anyMatch(r -> r.matchResult().needsConfirmation());
		}

		public boolean hasNewDrivers() {
			return rows.stream().anyMatch(r -> !r.matchResult().isMatch());
		}
	}

	@Getter
	public static class ImportResult {
		private final List<String> importedRaces = new ArrayList<>();
		private final List<String> errors = new ArrayList<>();
		private int newDriversCreated;
		private int lineupCount;

		public void addImportedRace(String race) {
			importedRaces.add(race);
		}

		public void addError(String error) {
			errors.add(error);
		}

		public void incrementNewDrivers() {
			newDriversCreated++;
		}

		public void incrementLineupCount() {
			lineupCount++;
		}

		public void setNewDriversCreated(int count) {
			newDriversCreated = count;
		}

		public void setLineupCount(int count) {
			lineupCount = count;
		}

		public boolean hasErrors() {
			return !errors.isEmpty();
		}
	}
}
