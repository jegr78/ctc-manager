package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonManagementService {

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final SeasonTeamRepository seasonTeamRepository;
    private final FileStorageService fileStorageService;
    private final PlayoffRepository playoffRepository;
    private final RaceScoringRepository raceScoringRepository;
    private final MatchScoringRepository matchScoringRepository;
    private final ScoringService scoringService;
    // Phase 58 D-18 / D-25 — strict delete-guard + REGULAR-phase auto-sync
    private final SeasonPhaseService seasonPhaseService;
    private final MatchdayRepository matchdayRepository;
    private final PhaseTeamRepository phaseTeamRepository;
    private final SeasonPhaseRepository seasonPhaseRepository;

    // --- Records for structured return data ---

    public record SeasonDetailData(Season season, Playoff playoff, boolean hasTeams,
                                   boolean hasMatchdays, boolean canGenerate, boolean isSwiss) {}

    public record SeasonEditFormData(Season season, List<Team> allTeams, List<Car> allCars,
                                     List<Track> allTracks, List<RaceScoring> allRaceScorings,
                                     List<MatchScoring> allMatchScorings) {}

    public record SwissRoundData(Season season, Map<UUID, int[]> raceScores) {}

    public record SeasonGroupOption(int year, int number, String label, int teamCount) {}

    // --- Season CRUD ---

    @Transactional(readOnly = true)
    public List<Season> findAll() {
        return seasonRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SeasonGroupOption> getSeasonGroupOptions() {
        return seasonRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        s -> s.getYear() + "|" + s.getNumber(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    var seasons = entry.getValue();
                    var first = seasons.getFirst();
                    int teamCount = seasons.stream()
                            .mapToInt(s -> s.getSeasonTeams().size())
                            .sum();
                    return new SeasonGroupOption(
                            first.getYear(), first.getNumber(),
                            "Season " + first.getNumber() + " (" + first.getYear() + ") — " + teamCount + " Teams",
                            teamCount);
                })
                .sorted(Comparator.comparingInt(SeasonGroupOption::year).reversed()
                        .thenComparing(Comparator.comparingInt(SeasonGroupOption::number).reversed()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Season findById(UUID id) {
        return seasonRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Season", id));
    }

    @Transactional(readOnly = true)
    public Optional<Season> findActiveSeason() {
        return seasonRepository.findAll().stream()
                .filter(Season::isActive)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Season> findByIdOptional(UUID id) {
        return seasonRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public SeasonDetailData getDetailData(UUID id) {
        var season = findById(id);
        var playoff = playoffRepository.findBySeasonId(id).orElse(null);
        boolean hasTeams = !season.getSeasonTeams().isEmpty();
        boolean hasMatchdays = !season.getMatchdays().isEmpty();
        boolean isSwiss = season.getFormat() == SeasonFormat.SWISS;
        boolean canGenerate = !isSwiss && !hasMatchdays && season.getEligibleTeams().size() >= 2;
        return new SeasonDetailData(season, playoff, hasTeams, hasMatchdays, canGenerate, isSwiss);
    }

    @Transactional(readOnly = true)
    public SeasonEditFormData getEditFormData(UUID id) {
        Season season = id != null ? findById(id) : null;
        var allTeams = teamRepository.findAll();
        var allCars = carRepository.findAllByOrderByManufacturerAscNameAsc();
        var allTracks = trackRepository.findAllByOrderByNameAsc();
        var allRaceScorings = raceScoringRepository.findAll();
        var allMatchScorings = matchScoringRepository.findAll();
        return new SeasonEditFormData(season, allTeams, allCars, allTracks, allRaceScorings, allMatchScorings);
    }

    /**
     * Saves a season form. Phase 58 D-25: in addition to writing the legacy {@link Season}
     * fields (format, totalRounds, legs, eventDurationMinutes, dates, scoring), the service
     * additionally find-or-creates the REGULAR {@link SeasonPhase} and synchronises the
     * same fields onto it, all within a single {@code @Transactional} boundary
     * (Pitfall 7 mitigation).
     *
     * <p>Phase 60 will remove the legacy field writes; Phase 61 will drop the legacy columns.
     */
    @Transactional
    public Season save(UUID id, String name, int year, int number, String description,
                       LocalDate startDate, LocalDate endDate, boolean active,
                       SeasonFormat format, Integer totalRounds, int legs,
                       Integer eventDurationMinutes, UUID raceScoringId, UUID matchScoringId) {
        var raceScoring = raceScoringRepository.findById(raceScoringId)
                .orElseThrow(() -> new EntityNotFoundException("RaceScoring", raceScoringId));
        var matchScoring = matchScoringRepository.findById(matchScoringId)
                .orElseThrow(() -> new EntityNotFoundException("MatchScoring", matchScoringId));

        Season season;
        if (id != null) {
            season = findById(id);
        } else {
            season = new Season();
        }
        season.setName(name);
        season.setYear(year);
        season.setNumber(number);
        season.setDescription(description);
        season.setStartDate(startDate);
        season.setEndDate(endDate);
        season.setActive(active);
        season.setFormat(format);
        season.setTotalRounds(totalRounds);
        season.setLegs(legs);
        season.setEventDurationMinutes(eventDurationMinutes);
        season.setRaceScoring(raceScoring);
        season.setMatchScoring(matchScoring);

        season = seasonRepository.save(season);
        final UUID savedSeasonId = season.getId(); // effectively-final capture for the lambda below

        // D-25: find-or-create REGULAR phase + write-through fields (Pitfall 7: bootstrap on new season)
        var regular = seasonPhaseService.findByType(savedSeasonId, PhaseType.REGULAR)
                .orElseGet(() -> seasonPhaseService.create(savedSeasonId,
                        PhaseType.REGULAR, PhaseLayout.LEAGUE, /*sortIndex*/ 0,
                        /*label*/ null,
                        raceScoring, matchScoring,
                        format, startDate, endDate,
                        totalRounds, legs, eventDurationMinutes));
        // Sync fields onto the existing-or-just-created REGULAR phase (idempotent on the freshly-created path)
        if (format != null) {
            regular.setFormat(format);
        }
        regular.setTotalRounds(totalRounds);
        regular.setLegs(legs);
        regular.setEventDurationMinutes(eventDurationMinutes);
        regular.setStartDate(startDate);
        regular.setEndDate(endDate);
        regular.setRaceScoring(raceScoring);
        regular.setMatchScoring(matchScoring);
        seasonPhaseRepository.save(regular);

        if (id != null) {
            log.info("Updated season: {}", season.getName());
        } else {
            log.info("Created season: {}", season.getName());
        }
        return season;
    }

    /**
     * Deletes a season. Phase 58 D-18 introduces a strict pre-check: if the season has
     * any active phase content (matchdays, playoffs, or {@code phase_teams} rows),
     * the delete is refused with {@link BusinessRuleException}. The {@link
     * org.ctc.admin.controller.GlobalExceptionHandler} maps the exception to HTTP 409
     * with a "Business Rule Violation" error page.
     *
     * <p>BEHAVIOR CHANGE vs pre-Phase-58: was blind cascade, now a fail-loud guard.
     */
    @Transactional
    public String delete(UUID id) {
        var season = findById(id);
        // D-18 strict pre-check (BEHAVIOR CHANGE vs blind cascade)
        if (matchdayRepository.existsByPhaseSeasonId(id)
                || playoffRepository.existsByPhaseSeasonId(id)
                || phaseTeamRepository.existsByPhaseSeasonId(id)) {
            throw new BusinessRuleException(
                    "Season has active phases — clear matches/teams before deleting");
        }
        seasonRepository.delete(season);
        log.info("Deleted season: {}", season.getName());
        return season.getName();
    }

    // --- Scoring lookups ---

    @Transactional(readOnly = true)
    public List<RaceScoring> getAllRaceScorings() {
        return raceScoringRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<MatchScoring> getAllMatchScorings() {
        return matchScoringRepository.findAll();
    }

    // --- Swiss round data ---

    @Transactional(readOnly = true)
    public SwissRoundData getSwissRoundData(UUID seasonId) {
        var season = findById(seasonId);
        Map<UUID, int[]> raceScores = new HashMap<>();
        for (var md : season.getMatchdays()) {
            for (var race : md.getRaces()) {
                if (race.isBye()) continue;
                if (race.getHomeScore() != null && race.getAwayScore() != null) {
                    raceScores.put(race.getId(), new int[]{race.getHomeScore(), race.getAwayScore()});
                } else if (!race.getResults().isEmpty()) {
                    int homeTotal = race.getResults().stream()
                            .filter(r -> scoringService.isDriverInTeam(r, race.getId(), race.getHomeTeam().getId()))
                            .mapToInt(RaceResult::getPointsTotal).sum();
                    int awayTotal = race.getResults().stream()
                            .filter(r -> !scoringService.isDriverInTeam(r, race.getId(), race.getHomeTeam().getId()))
                            .mapToInt(RaceResult::getPointsTotal).sum();
                    raceScores.put(race.getId(), new int[]{homeTotal, awayTotal});
                }
            }
        }
        return new SwissRoundData(season, raceScores);
    }

    /**
     * Returns teams available as replacement candidates for a season
     * (all teams not currently active in the season).
     */
    @Transactional(readOnly = true)
    public List<Team> getAvailableTeamsForReplacement(UUID seasonId) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        Set<UUID> activeTeamIds = season.getSeasonTeams().stream()
                .filter(st -> !st.isReplaced())
                .map(st -> st.getTeam().getId())
                .collect(Collectors.toSet());
        return teamRepository.findAll().stream()
                .filter(t -> !activeTeamIds.contains(t.getId()))
                .sorted(Comparator.comparing(Team::getShortName))
                .toList();
    }

    /**
     * Adds a team to a season. Auto-adds the parent team when adding a sub-team.
     * Returns the team's short name for flash messages.
     */
    @Transactional
    public String addTeamToSeason(UUID seasonId, UUID teamId) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team", teamId));

        if (!season.containsTeam(team)) {
            if (team.isSubTeam() && !season.containsTeam(team.getParentTeam())) {
                season.addTeam(team.getParentTeam());
                log.info("Auto-added parent team {} to season {}", team.getParentTeam().getShortName(), season.getName());
            }
            season.addTeam(team);
            seasonRepository.save(season);
            log.info("Added team {} to season {}", team.getShortName(), season.getName());
        }

        return team.getShortName();
    }

    /**
     * Removes a team from a season with sub-team constraint check.
     * Auto-removes the parent team if no more sub-teams remain.
     * Throws IllegalStateException if trying to remove a parent team with sub-teams still in the season.
     */
    @Transactional
    public String removeTeamFromSeason(UUID seasonId, UUID teamId) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team", teamId));

        if (!team.isSubTeam()) {
            boolean hasSubs = season.getTeams().stream()
                    .anyMatch(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(team.getId()));
            if (hasSubs) {
                throw new IllegalStateException(
                        "Cannot remove parent team " + team.getShortName() + " — remove its sub-teams first");
            }
        }

        season.removeTeamById(teamId);

        if (team.isSubTeam()) {
            var parent = team.getParentTeam();
            boolean hasOtherSubs = season.getTeams().stream()
                    .anyMatch(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(parent.getId()));
            if (!hasOtherSubs) {
                season.removeTeam(parent);
                log.info("Auto-removed parent team {} from season {} (no sub-teams left)",
                        parent.getShortName(), season.getName());
            }
        }

        seasonRepository.save(season);
        log.info("Removed team {} from season {}", team.getShortName(), season.getName());

        return team.getShortName();
    }

    /**
     * Updates season team properties including optional logo upload.
     * Returns the team's short name for flash messages.
     */
    @Transactional
    public String updateSeasonTeam(UUID seasonTeamId, Integer rating,
                                   String primaryColor, String secondaryColor, String accentColor,
                                   MultipartFile logoOverride) throws IOException {
        var seasonTeam = seasonTeamRepository.findById(seasonTeamId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonTeam", seasonTeamId));
        seasonTeam.setRating(rating);
        seasonTeam.setPrimaryColor(primaryColor != null && !primaryColor.isBlank() ? primaryColor : null);
        seasonTeam.setSecondaryColor(secondaryColor != null && !secondaryColor.isBlank() ? secondaryColor : null);
        seasonTeam.setAccentColor(accentColor != null && !accentColor.isBlank() ? accentColor : null);

        if (logoOverride != null && !logoOverride.isEmpty()) {
            if (seasonTeam.getLogoUrl() != null) {
                fileStorageService.delete(seasonTeam.getLogoUrl());
            }
            String url = fileStorageService.storeImage("season-teams", seasonTeamId, logoOverride);
            seasonTeam.setLogoUrl(url);
        }

        seasonTeamRepository.save(seasonTeam);
        return seasonTeam.getTeam().getShortName();
    }

    /**
     * Replaces a team in a season with a successor team.
     * The predecessor's results are inherited by the successor in standings.
     * Returns "OLD → NEW" for flash messages.
     */
    @Transactional
    public String replaceTeam(UUID seasonId, UUID predecessorTeamId, UUID successorTeamId, LocalDate replacedAt) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        var predecessor = teamRepository.findById(predecessorTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Team", predecessorTeamId));
        var successor = teamRepository.findById(successorTeamId)
                .orElseThrow(() -> new EntityNotFoundException("Team", successorTeamId));

        var predecessorSt = season.findSeasonTeam(predecessor)
                .orElseThrow(() -> new IllegalStateException("Team " + predecessor.getShortName() + " not in season"));

        if (predecessorSt.isReplaced()) {
            throw new IllegalStateException("Team " + predecessor.getShortName() + " already replaced");
        }

        if (!season.containsTeam(successor)) {
            season.addTeam(successor);
        }
        seasonRepository.save(season);

        var successorSt = seasonTeamRepository.findBySeasonIdAndTeamId(seasonId, successorTeamId)
                .orElseThrow(() -> new EntityNotFoundException("SeasonTeam", successorTeamId));

        predecessorSt.setSuccessor(successorSt);
        predecessorSt.setReplacedAt(replacedAt);
        seasonTeamRepository.save(predecessorSt);

        log.info("Replaced team {} with {} in season {} (effective {})",
                predecessor.getShortName(), successor.getShortName(), season.getName(), replacedAt);

        return predecessor.getShortName() + " → " + successor.getShortName();
    }

    /**
     * Adds cars to a season's car pool. Returns the number of cars actually added.
     */
    @Transactional
    public int addCarsToSeason(UUID seasonId, List<UUID> carIds) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        int added = 0;
        for (UUID carId : carIds) {
            var car = carRepository.findById(carId).orElse(null);
            if (car != null && !season.getCars().contains(car)) {
                season.getCars().add(car);
                added++;
            }
        }
        if (added > 0) seasonRepository.save(season);
        return added;
    }

    /**
     * Removes cars from a season's car pool. Returns the number of car IDs requested for removal.
     */
    @Transactional
    public int removeCarsFromSeason(UUID seasonId, List<UUID> carIds) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        season.getCars().removeIf(c -> carIds.contains(c.getId()));
        seasonRepository.save(season);
        return carIds.size();
    }

    /**
     * Adds tracks to a season's track pool. Returns the number of tracks actually added.
     */
    @Transactional
    public int addTracksToSeason(UUID seasonId, List<UUID> trackIds) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        int added = 0;
        for (UUID trackId : trackIds) {
            var track = trackRepository.findById(trackId).orElse(null);
            if (track != null && !season.getTracks().contains(track)) {
                season.getTracks().add(track);
                added++;
            }
        }
        if (added > 0) seasonRepository.save(season);
        return added;
    }

    /**
     * Removes tracks from a season's track pool. Returns the number of track IDs requested for removal.
     */
    @Transactional
    public int removeTracksFromSeason(UUID seasonId, List<UUID> trackIds) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        season.getTracks().removeIf(t -> trackIds.contains(t.getId()));
        seasonRepository.save(season);
        return trackIds.size();
    }
}
