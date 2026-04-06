package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only service for assembling race form data.
 * Extracted from RaceService per D-08.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaceFormDataService {

    private final RaceRepository raceRepository;
    private final MatchdayRepository matchdayRepository;
    private final TeamRepository teamRepository;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final RaceLineupRepository raceLineupRepository;

    // --- Form data for new race ---

    public RaceService.RaceFormData getNewRaceFormData(UUID matchdayId) {
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

        var data = new RaceService.RaceData(null, effectiveMatchdayId, null, null, null, null, null,
                List.of(), null, null, null, null, null, null, null, null, null, null, null);

        return new RaceService.RaceFormData(data, matchdayRepository.findAll(), teamRepository.findAll(),
                seasonCars, seasonTracks, Set.of(), Set.of());
    }

    // --- Form data for edit ---

    public RaceService.RaceFormData getRaceFormData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var data = toRaceData(race);
        var season = race.getMatchday().getSeason();

        return new RaceService.RaceFormData(data, matchdayRepository.findAll(), teamRepository.findAll(),
                season.getCars(), season.getTracks(),
                getUsedCarIds(season.getId(), race.getHomeTeam().getId(), race.getId()),
                getUsedTrackIds(season.getId(), race.getHomeTeam().getId(), race.getId()));
    }

    // --- Results form data ---

    public RaceService.ResultsFormData getResultsFormData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var data = toRaceData(race);

        if (data.results().isEmpty()) {
            var seasonId = race.getMatchday().getSeason().getId();
            var results = new ArrayList<RaceService.RaceResultData>();
            populateDrivers(results, raceId, seasonId, race.getHomeTeam());
            populateDrivers(results, raceId, seasonId, race.getAwayTeam());
            data = new RaceService.RaceData(data.id(), data.matchdayId(), data.homeTeamId(), data.awayTeamId(),
                    data.trackId(), data.carId(), data.dateTime(), results,
                    data.numberOfLaps(), data.tyreWearMultiplier(), data.fuelConsumptionMultiplier(),
                    data.refuelingSpeed(), data.initialFuel(), data.numberOfRequiredPitStops(),
                    data.timeProgressionMultiplier(), data.weather(), data.timeOfDay(),
                    data.availableTyres(), data.mandatoryTyres());
        }

        return new RaceService.ResultsFormData(data, race, race.getMatchday().getSeason().getRaceScoring());
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

    private void populateDrivers(List<RaceService.RaceResultData> results, UUID raceId, UUID seasonId, Team team) {
        var allLineups = raceLineupRepository.findByRaceId(raceId);
        var lineupDrivers = allLineups.stream()
                .filter(lu -> lu.getTeam().getId().equals(team.getId())
                        || lu.getTeam().getParentOrSelf().getId().equals(team.getId()))
                .toList();

        if (!lineupDrivers.isEmpty()) {
            int pos = results.size() + 1;
            for (var lineup : lineupDrivers) {
                results.add(new RaceService.RaceResultData(
                        lineup.getDriver().getId(), lineup.getDriver().getPsnId(),
                        lineup.getTeam().getShortName(), pos, pos, false));
                pos++;
            }
        } else {
            var seasonDrivers = seasonDriverRepository.findBySeasonIdAndTeamId(seasonId, team.getId());
            int pos = results.size() + 1;
            for (var sd : seasonDrivers) {
                results.add(new RaceService.RaceResultData(
                        sd.getDriver().getId(), sd.getDriver().getPsnId(),
                        team.getShortName(), pos, pos, false));
                pos++;
            }
        }
    }

    private RaceService.RaceData toRaceData(Race race) {
        var resultDataList = new ArrayList<RaceService.RaceResultData>();
        for (var result : race.getResults()) {
            // Use RaceLineup for team name (Source of Truth), fallback to SeasonDriver
            String teamShortName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())
                    .map(rl -> rl.getTeam().getShortName())
                    .orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
                            .filter(sd -> sd.getSeason().getId().equals(race.getMatchday().getSeason().getId()))
                            .map(sd -> sd.getTeam().getShortName())
                            .findFirst().orElse("?"));
            resultDataList.add(new RaceService.RaceResultData(
                    result.getDriver().getId(), result.getDriver().getPsnId(),
                    teamShortName, result.getPosition(), result.getQualiPosition(),
                    result.isFastestLap()));
        }

        var settings = race.getSettings();
        return new RaceService.RaceData(
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
