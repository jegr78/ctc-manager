package org.ctc.domain.service;

import org.ctc.dataimport.GoogleCalendarService;
import org.ctc.admin.service.TeamCardService;
import org.ctc.domain.model.*;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceService {

    private final RaceRepository raceRepository;
    private final MatchRepository matchRepository;
    private final MatchdayRepository matchdayRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final DriverRepository driverRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final SeasonTeamRepository seasonTeamRepository;
    private final ScoringService scoringService;
    private final TeamCardService teamCardService;
    private final GoogleCalendarService googleCalendarService;

    // --- Domain records (replacing admin DTOs) ---

    public record RaceData(UUID id, UUID matchdayId, UUID homeTeamId, UUID awayTeamId,
                           UUID trackId, UUID carId, LocalDateTime dateTime,
                           List<RaceResultData> results,
                           Integer numberOfLaps, Integer tyreWearMultiplier,
                           Integer fuelConsumptionMultiplier, Integer refuelingSpeed,
                           String initialFuel, Integer numberOfRequiredPitStops,
                           Integer timeProgressionMultiplier, String weather,
                           String timeOfDay, String availableTyres, String mandatoryTyres) {}

    public record RaceResultData(UUID driverId, String driverPsnId, String teamShortName,
                                 int position, int qualiPosition, boolean fastestLap) {}

    // --- Return types ---

    public record RaceListData(List<Race> races, Map<UUID, int[]> raceScores,
                               Matchday matchday, UUID selectedSeasonId, List<Season> seasons) {}

    public record RaceDetailData(Race race, int homeTotal, int awayTotal,
                                 Map<UUID, String> driverTeamMap, boolean canGenerateLineup,
                                 boolean lineupMissing, boolean cardsMissing, boolean lineupExists,
                                 boolean canGenerateResults, boolean resultsMissing, boolean resultsExist,
                                 boolean canGenerateSettings, boolean settingsMissing, boolean settingsExist,
                                 boolean canGenerateOverlay, boolean overlayExists,
                                 boolean calendarAvailable, boolean hasCalendarEvent,
                                 boolean canCreateCalendarEvent) {}

    public record ResultsFormData(RaceData data, Race race, RaceScoring raceScoring) {}

    public record RaceFormData(RaceData data, List<Matchday> matchdays, List<Team> teams,
                               List<Car> seasonCars, List<Track> seasonTracks,
                               Set<UUID> usedCarIds, Set<UUID> usedTrackIds) {}

    public record SaveResult(boolean success, String message, UUID raceId, UUID matchdayId) {}

    // --- List ---

    public RaceListData getRaceListData(UUID matchdayId, UUID seasonId) {
        List<Race> races;
        Matchday matchday = null;
        UUID selectedSeasonId = null;

        if (matchdayId != null) {
            races = raceRepository.findByMatchdayId(matchdayId);
            matchday = matchdayRepository.findById(matchdayId).orElse(null);
        } else if (seasonId != null) {
            races = raceRepository.findByMatchdaySeasonId(seasonId);
            selectedSeasonId = seasonId;
        } else {
            races = List.of();
        }

        var raceScores = new HashMap<UUID, int[]>();
        for (var race : races) {
            if (race.getHomeScore() != null && race.getAwayScore() != null) {
                raceScores.put(race.getId(), new int[]{race.getHomeScore(), race.getAwayScore()});
            }
        }

        return new RaceListData(races, raceScores, matchday, selectedSeasonId, seasonRepository.findAll());
    }

    // --- Detail ---

    public RaceDetailData getRaceDetailData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();

        int homeTotal = 0;
        int awayTotal = 0;
        Map<UUID, String> driverTeamMap = null;

        if (!race.getResults().isEmpty() && race.getHomeTeam() != null) {
            homeTotal = race.getResults().stream()
                    .filter(r -> scoringService.isDriverInTeam(r, race.getId(), race.getHomeTeam().getId()))
                    .mapToInt(RaceResult::getPointsTotal).sum();
            awayTotal = race.getResults().stream()
                    .filter(r -> !scoringService.isDriverInTeam(r, race.getId(), race.getHomeTeam().getId()))
                    .mapToInt(RaceResult::getPointsTotal).sum();

            var sid = race.getMatchday().getSeason().getId();
            driverTeamMap = new HashMap<>();
            for (var result : race.getResults()) {
                var teamName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())
                        .map(rl -> rl.getTeam().getShortName())
                        .orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
                                .filter(sd -> sd.getSeason().getId().equals(sid))
                                .map(sd -> sd.getTeam().getShortName())
                                .findFirst().orElse("?"));
                driverTeamMap.put(result.getDriver().getId(), teamName);
            }
        }

        // Check if lineup graphic can be generated
        var lineups = raceLineupRepository.findByRaceId(race.getId());
        boolean hasLineup = !lineups.isEmpty();
        boolean hasHomeCard = false;
        boolean hasAwayCard = false;
        if (race.getMatch() != null && race.getHomeTeam() != null && race.getAwayTeam() != null) {
            var season = race.getMatchday().getSeason();
            hasHomeCard = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), race.getHomeTeam().getId())
                    .map(st -> teamCardService.cardExists(st)).orElse(false);
            hasAwayCard = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), race.getAwayTeam().getId())
                    .map(st -> teamCardService.cardExists(st)).orElse(false);
        }
        boolean lineupExists = race.getAttachments().stream()
                .anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/lineup.png"));
        boolean resultsGraphicExists = race.getAttachments().stream()
                .anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/results.png"));
        boolean hasResults = !race.getResults().isEmpty();

        boolean settingsGraphicExists = race.getAttachments().stream()
                .anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/settings.png"));
        boolean hasAllSettings = race.hasAllSettings() && race.getCar() != null && race.getTrack() != null;

        boolean overlayExists = race.getAttachments().stream()
                .anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/overlay.png"));
        boolean hasMatch = race.getMatch() != null && race.getHomeTeam() != null && race.getAwayTeam() != null;

        boolean calendarAvailable = googleCalendarService.isAvailable();
        boolean hasCalendarEvent = race.hasCalendarEvent();
        boolean canCreateCalendarEvent = calendarAvailable
                && race.getDateTime() != null
                && race.getHomeTeam() != null
                && race.getAwayTeam() != null;

        return new RaceDetailData(race, homeTotal, awayTotal, driverTeamMap,
                hasLineup && hasHomeCard && hasAwayCard && !lineupExists,
                !hasLineup, !hasHomeCard || !hasAwayCard, lineupExists,
                hasResults && hasHomeCard && hasAwayCard && !resultsGraphicExists,
                !hasResults, resultsGraphicExists,
                hasAllSettings && hasHomeCard && hasAwayCard && !settingsGraphicExists,
                !hasAllSettings, settingsGraphicExists,
                hasMatch && !overlayExists, overlayExists,
                calendarAvailable, hasCalendarEvent, canCreateCalendarEvent);
    }

    // --- Calendar event ---

    @Transactional
    public void createOrUpdateCalendarEvent(UUID raceId) throws IOException {
        var race = raceRepository.findById(raceId).orElseThrow();

        if (!googleCalendarService.isAvailable()) {
            throw new IllegalStateException("Google Calendar integration not available");
        }
        if (race.getDateTime() == null) {
            throw new IllegalStateException("Race has no date/time set");
        }
        if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
            throw new IllegalStateException("Race has no teams assigned");
        }

        Integer durationMinutes = resolveEventDuration(race);
        if (durationMinutes == null) {
            throw new IllegalStateException("Event duration not configured. Set it in the season or playoff form.");
        }

        String title = race.getMatchday().getLabel() + " - "
                + race.getHomeTeam().getShortName() + " vs. "
                + race.getAwayTeam().getShortName();

        if (race.hasCalendarEvent()) {
            googleCalendarService.updateEvent(race.getCalendarEventId(), title, race.getDateTime(), durationMinutes);
            log.info("Updated calendar event for race {}: {}", raceId, title);
        } else {
            String eventId = googleCalendarService.createEvent(title, race.getDateTime(), durationMinutes);
            race.setCalendarEventId(eventId);
            raceRepository.save(race);
            log.info("Created calendar event for race {}: {} (eventId: {})", raceId, title, eventId);
        }
    }

    private Integer resolveEventDuration(Race race) {
        if (race.getPlayoffMatchup() != null) {
            var playoffDuration = race.getPlayoffMatchup().getRound().getPlayoff().getEventDurationMinutes();
            if (playoffDuration != null) {
                return playoffDuration;
            }
        }
        return race.getMatchday().getSeason().getEventDurationMinutes();
    }

    // --- Form data for new race ---

    public RaceFormData getNewRaceFormData(UUID matchdayId) {
        UUID effectiveMatchdayId = matchdayId;
        List<Car> seasonCars = List.of();
        List<Track> seasonTracks = List.of();

        if (matchdayId != null) {
            var md = matchdayRepository.findById(matchdayId).orElse(null);
            if (md != null) {
                var season = md.getSeason();
                seasonCars = season.getCars();
                seasonTracks = season.getTracks();
            }
        }

        var data = new RaceData(null, effectiveMatchdayId, null, null, null, null, null,
                List.of(), null, null, null, null, null, null, null, null, null, null, null);

        return new RaceFormData(data, matchdayRepository.findAll(), teamRepository.findAll(),
                seasonCars, seasonTracks, Set.of(), Set.of());
    }

    // --- Form data for edit ---

    public RaceFormData getRaceFormData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var data = toRaceData(race);
        var season = race.getMatchday().getSeason();

        return new RaceFormData(data, matchdayRepository.findAll(), teamRepository.findAll(),
                season.getCars(), season.getTracks(),
                getUsedCarIds(season.getId(), race.getHomeTeam().getId(), race.getId()),
                getUsedTrackIds(season.getId(), race.getHomeTeam().getId(), race.getId()));
    }

    // --- Results form data ---

    public ResultsFormData getResultsFormData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var data = toRaceData(race);

        if (data.results().isEmpty()) {
            var seasonId = race.getMatchday().getSeason().getId();
            var results = new ArrayList<RaceResultData>();
            populateDrivers(results, raceId, seasonId, race.getHomeTeam());
            populateDrivers(results, raceId, seasonId, race.getAwayTeam());
            data = new RaceData(data.id(), data.matchdayId(), data.homeTeamId(), data.awayTeamId(),
                    data.trackId(), data.carId(), data.dateTime(), results,
                    data.numberOfLaps(), data.tyreWearMultiplier(), data.fuelConsumptionMultiplier(),
                    data.refuelingSpeed(), data.initialFuel(), data.numberOfRequiredPitStops(),
                    data.timeProgressionMultiplier(), data.weather(), data.timeOfDay(),
                    data.availableTyres(), data.mandatoryTyres());
        }

        return new ResultsFormData(data, race, race.getMatchday().getSeason().getRaceScoring());
    }

    // --- Save race ---

    @Transactional
    public SaveResult saveRace(UUID id, UUID matchdayId, UUID homeTeamId, UUID awayTeamId,
                               UUID trackId, UUID carId, LocalDateTime dateTime,
                               Integer numberOfLaps, Integer tyreWearMultiplier,
                               Integer fuelConsumptionMultiplier, Integer refuelingSpeed,
                               String initialFuel, Integer numberOfRequiredPitStops,
                               Integer timeProgressionMultiplier, String weather,
                               String timeOfDay, String availableTyres, String mandatoryTyres) {
        var matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        var homeTeam = teamRepository.findById(homeTeamId).orElseThrow();
        var awayTeam = teamRepository.findById(awayTeamId).orElseThrow();

        Race race;
        if (id != null) {
            race = raceRepository.findById(id).orElseThrow();
        } else {
            race = new Race();
        }

        race.setMatchday(matchday);

        Match match = race.getMatch();
        if (match == null) {
            match = new Match(matchday, homeTeam, awayTeam);
            match = matchRepository.save(match);
            race.setMatch(match);
        } else {
            match.setHomeTeam(homeTeam);
            match.setAwayTeam(awayTeam);
        }

        if (trackId != null) {
            race.setTrack(trackRepository.findById(trackId).orElse(null));
        } else {
            race.setTrack(null);
        }
        if (carId != null) {
            race.setCar(carRepository.findById(carId).orElse(null));
        } else {
            race.setCar(null);
        }
        race.setDateTime(dateTime);

        // Settings
        var settings = race.getSettings();
        if (settings == null) {
            settings = new RaceSettings(race);
            race.setSettings(settings);
        }
        settings.setNumberOfLaps(numberOfLaps);
        settings.setTyreWearMultiplier(tyreWearMultiplier);
        settings.setFuelConsumptionMultiplier(fuelConsumptionMultiplier);
        settings.setRefuelingSpeed(refuelingSpeed);
        settings.setInitialFuel(initialFuel);
        settings.setNumberOfRequiredPitStops(numberOfRequiredPitStops);
        settings.setTimeProgressionMultiplier(timeProgressionMultiplier);
        settings.setWeather(weather);
        settings.setTimeOfDay(timeOfDay);
        settings.setAvailableTyres(availableTyres);
        settings.setMandatoryTyres(mandatoryTyres);

        // Pool validation
        var season = matchday.getSeason();
        if (race.getCar() != null && !season.getCars().contains(race.getCar())) {
            return new SaveResult(false, "Car is not in this season's pool", id, matchdayId);
        }
        if (race.getTrack() != null && !season.getTracks().contains(race.getTrack())) {
            return new SaveResult(false, "Track is not in this season's pool", id, matchdayId);
        }

        // Uniqueness validation
        if (race.getCar() != null) {
            var usedCarIds = getUsedCarIds(season.getId(), homeTeam.getId(), id);
            if (usedCarIds.contains(race.getCar().getId())) {
                return new SaveResult(false,
                        homeTeam.getShortName() + " has already used " + race.getCar().getDisplayName() + " this season",
                        id, matchdayId);
            }
        }
        if (race.getTrack() != null) {
            var usedTrackIds = getUsedTrackIds(season.getId(), homeTeam.getId(), id);
            if (usedTrackIds.contains(race.getTrack().getId())) {
                return new SaveResult(false,
                        homeTeam.getShortName() + " has already used " + race.getTrack().getName() + " this season",
                        id, matchdayId);
            }
        }

        raceRepository.save(race);
        log.info("Saved race: {} vs {} ({})", homeTeam.getShortName(), awayTeam.getShortName(), matchday.getLabel());
        return new SaveResult(true,
                "Race saved: " + homeTeam.getShortName() + " vs " + awayTeam.getShortName(),
                race.getId(), matchdayId);
    }

    // --- Save results ---

    @Transactional
    public String saveResults(UUID raceId, List<RaceResultData> results) {
        var race = raceRepository.findById(raceId).orElseThrow();

        race.getResults().clear();

        for (var rd : results) {
            if (rd.driverId() == null) continue;

            var driver = driverRepository.findById(rd.driverId()).orElseThrow();
            var result = new RaceResult(race, driver, rd.position(), rd.qualiPosition(), rd.fastestLap());
            scoringService.calculatePoints(result, race.getMatchday().getSeason().getRaceScoring());
            race.getResults().add(result);
        }

        raceRepository.save(race);
        scoringService.aggregateMatchScores(race);

        var homeScore = race.getHomeScore() != null ? race.getHomeScore() : 0;
        var awayScore = race.getAwayScore() != null ? race.getAwayScore() : 0;

        log.info("Saved results for {} vs {}: {} : {}",
                race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName(), homeScore, awayScore);
        return "Results saved: " + race.getHomeTeam().getShortName() + " " + homeScore +
                " : " + awayScore + " " + race.getAwayTeam().getShortName();
    }

    // --- Quick score ---

    @Transactional
    public String quickScore(UUID raceId, int homeScore, int awayScore) {
        var race = raceRepository.findById(raceId).orElseThrow();
        if (race.getMatch() != null) {
            race.getMatch().setHomeScore(homeScore);
            race.getMatch().setAwayScore(awayScore);
        }
        raceRepository.save(race);
        log.info("Quick score: {} {} : {} {}",
                race.getHomeTeam().getShortName(), homeScore, awayScore, race.getAwayTeam().getShortName());
        return race.getHomeTeam().getShortName() + " " + homeScore + " : " + awayScore + " " + race.getAwayTeam().getShortName();
    }

    // --- Delete race ---

    @Transactional
    public UUID deleteRace(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var matchdayId = race.getMatchday().getId();
        raceRepository.delete(race);
        log.info("Deleted race: {} vs {}", race.getHomeTeam().getShortName(), race.getAwayTeam().getShortName());
        return matchdayId;
    }

    // --- Used selections ---

    public Map<String, Set<UUID>> getUsedSelections(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        return Map.of(
                "usedCarIds", getUsedCarIds(seasonId, homeTeamId, excludeRaceId),
                "usedTrackIds", getUsedTrackIds(seasonId, homeTeamId, excludeRaceId));
    }

    // --- Private helpers ---

    private Set<UUID> getUsedCarIds(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        return raceRepository.findByMatchdaySeasonId(seasonId).stream()
                .filter(r -> !r.isBye())
                .filter(r -> r.getHomeTeam().getId().equals(homeTeamId))
                .filter(r -> excludeRaceId == null || !r.getId().equals(excludeRaceId))
                .filter(r -> r.getCar() != null)
                .map(r -> r.getCar().getId())
                .collect(Collectors.toSet());
    }

    private Set<UUID> getUsedTrackIds(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        return raceRepository.findByMatchdaySeasonId(seasonId).stream()
                .filter(r -> !r.isBye())
                .filter(r -> r.getHomeTeam().getId().equals(homeTeamId))
                .filter(r -> excludeRaceId == null || !r.getId().equals(excludeRaceId))
                .filter(r -> r.getTrack() != null)
                .map(r -> r.getTrack().getId())
                .collect(Collectors.toSet());
    }

    private void populateDrivers(List<RaceResultData> results, UUID raceId, UUID seasonId, Team team) {
        var allLineups = raceLineupRepository.findByRaceId(raceId);
        var lineupDrivers = allLineups.stream()
                .filter(lu -> lu.getTeam().getId().equals(team.getId())
                        || lu.getTeam().getParentOrSelf().getId().equals(team.getId()))
                .toList();

        if (!lineupDrivers.isEmpty()) {
            int pos = results.size() + 1;
            for (var lineup : lineupDrivers) {
                results.add(new RaceResultData(
                        lineup.getDriver().getId(), lineup.getDriver().getPsnId(),
                        lineup.getTeam().getShortName(), pos, pos, false));
                pos++;
            }
        } else {
            var seasonDrivers = seasonDriverRepository.findBySeasonIdAndTeamId(seasonId, team.getId());
            int pos = results.size() + 1;
            for (var sd : seasonDrivers) {
                results.add(new RaceResultData(
                        sd.getDriver().getId(), sd.getDriver().getPsnId(),
                        team.getShortName(), pos, pos, false));
                pos++;
            }
        }
    }

    private RaceData toRaceData(Race race) {
        var resultDataList = new ArrayList<RaceResultData>();
        for (var result : race.getResults()) {
            // Use RaceLineup for team name (Source of Truth), fallback to SeasonDriver
            String teamShortName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())
                    .map(rl -> rl.getTeam().getShortName())
                    .orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
                            .filter(sd -> sd.getSeason().getId().equals(race.getMatchday().getSeason().getId()))
                            .map(sd -> sd.getTeam().getShortName())
                            .findFirst().orElse("?"));
            resultDataList.add(new RaceResultData(
                    result.getDriver().getId(), result.getDriver().getPsnId(),
                    teamShortName, result.getPosition(), result.getQualiPosition(),
                    result.isFastestLap()));
        }

        var settings = race.getSettings();
        return new RaceData(
                race.getId(), race.getMatchday().getId(),
                race.getHomeTeam().getId(), race.getAwayTeam().getId(),
                race.getTrack() != null ? race.getTrack().getId() : null,
                race.getCar() != null ? race.getCar().getId() : null,
                race.getDateTime(), resultDataList,
                settings != null ? settings.getNumberOfLaps() : null,
                settings != null ? settings.getTyreWearMultiplier() : null,
                settings != null ? settings.getFuelConsumptionMultiplier() : null,
                settings != null ? settings.getRefuelingSpeed() : null,
                settings != null ? settings.getInitialFuel() : null,
                settings != null ? settings.getNumberOfRequiredPitStops() : null,
                settings != null ? settings.getTimeProgressionMultiplier() : null,
                settings != null ? settings.getWeather() : null,
                settings != null ? settings.getTimeOfDay() : null,
                settings != null ? settings.getAvailableTyres() : null,
                settings != null ? settings.getMandatoryTyres() : null);
    }

}
