package org.ctc.domain.service;

import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only service for assembling race form data.
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
    private final RaceCarSelectionService carSelections;
    private final PickReservationService reservations;
    private final SeasonRepository seasonRepository;

    public RaceService.RaceFormData getSubmittedRaceFormData(UUID matchdayId, UUID homeTeamId, UUID raceId,
                                                           UUID entryId, UUID carId, UUID trackId) {
        var season = matchdayId == null ? null : matchdayRepository.findById(matchdayId).map(Matchday::getSeason).orElse(null);
        var data = new RaceService.RaceData(raceId, matchdayId, homeTeamId, null, trackId, carId, null,
                List.of(), null, null, null, null, null, null, null, null, null, null, null,
                TimeProgressionMode.MULTIPLIER, entryId, season == null ? LobbyRulesProfile.LEGACY : season.getLobbyRulesProfile(),
                RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
        return assemble(data, season);
    }

    public RaceService.RaceFormData getNewRaceFormData(UUID matchdayId) {
        return getSubmittedRaceFormData(matchdayId, null, null, null, null, null);
    }

    public RaceService.RaceFormData getRaceFormData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        return assemble(toRaceData(race), race.getMatchday().getSeason());
    }

    private RaceService.RaceFormData assemble(RaceService.RaceData data, Season season) {
        var teams = teamRepository.findAll();
        if (season == null) {
            return new RaceService.RaceFormData(data, matchdayRepository.findAll(), teams,
                    List.of(), List.of(), Set.of(), Set.of(), null, null);
        }
        UUID knownHome = teams.stream().anyMatch(team -> team.getId().equals(data.homeTeamId())) ? data.homeTeamId() : null;
        var available = carSelections.availability(season.getId(), knownHome, data.id());
        boolean category = available.poolMode() == CarPoolMode.CATEGORY_POOL;
        var selected = available.entries().stream().filter(entry -> entry.id().equals(data.carPoolEntryId())).findFirst().orElse(null);
        var memberCars = selected == null ? List.<RaceCarSelectionService.CarChoice>of() : selected.cars();
        var selectedCar = data.carId() == null ? null : carRepository.findById(data.carId()).orElse(null);
        boolean carInPool = data.carId() == null || (category
                ? memberCars.stream().anyMatch(car -> car.id().equals(data.carId()))
                : season.getCars().stream().anyMatch(car -> car.getId().equals(data.carId())));
        var unavailableCar = carInPool ? null : new RaceCarSelectionService.CarChoice(data.carId(),
                selectedCar == null ? "Unavailable submitted car" : selectedCar.getDisplayName() + (category ? " (not in frozen entry)" : " (not in season pool)"));
        var unavailableEntry = data.carPoolEntryId() != null && selected == null
                ? new RaceCarSelectionService.CarChoice(data.carPoolEntryId(), "Unavailable submitted entry") : null;
        String hint = !category ? "Choose from this season's catalog. Previously used choices are unavailable."
                : available.poolState() != CarPoolState.FROZEN ? "Freeze this season's category pool before choosing a car."
                : selected != null && selected.reason() != null ? selected.reason()
                : "Choose one entry, then one concrete car. Saving consumes the whole entry for this home team.";
        var track = data.trackId() == null ? null : trackRepository.findById(data.trackId()).orElse(null);
        boolean trackInPool = data.trackId() == null || season.getTracks().stream()
                .anyMatch(candidate -> candidate.getId().equals(data.trackId()));
        var unavailableTrack = trackInPool ? null : new TrackChoice(data.trackId(),
                track == null ? "Unavailable submitted track" : track.getName() + " (not in season pool)");
        return new RaceService.RaceFormData(data, matchdayRepository.findAll(), teams,
                category ? List.of() : season.getCars(), season.getTracks(), available.usedCarIds(),
                getUsedTrackIds(season.getId(), knownHome, data.id()), carInPool ? null : selectedCar,
                track != null && season.getTracks().stream().noneMatch(t -> t.getId().equals(track.getId())) ? track : null,
                new RaceService.CarSelectionForm(category, available.poolState() == CarPoolState.FROZEN,
                        available.entries(), memberCars, unavailableEntry, unavailableCar, hint, selected != null && selected.consumed()),
                unavailableTrack);
    }

    public RaceService.ResultsFormData getResultsFormData(UUID raceId) {
        var race = raceRepository.findById(raceId).orElseThrow();
        var data = toRaceData(race);

        if (data.results().isEmpty()) {
            var seasonId = race.getMatchday().getSeason().getId();
            var results = new ArrayList<RaceService.RaceResultData>();
			if (race.getHomeTeam() != null) {
				populateDrivers(results, raceId, seasonId, race.getHomeTeam());
			}
			if (race.getAwayTeam() != null) {
				populateDrivers(results, raceId, seasonId, race.getAwayTeam());
			}
            data = new RaceService.RaceData(data.id(), data.matchdayId(), data.homeTeamId(), data.awayTeamId(),
                    data.trackId(), data.carId(), data.dateTime(), results,
                    data.numberOfLaps(), data.tyreWearMultiplier(), data.fuelConsumptionMultiplier(),
                    data.refuelingSpeed(), data.initialFuel(), data.numberOfRequiredPitStops(),
                    data.timeProgressionMultiplier(), data.weather(), data.timeOfDay(),
                    data.availableTyres(), data.mandatoryTyres(), data.timeProgressionMode(), data.carPoolEntryId(),
                    data.lobbyRulesProfile(), data.startType(), data.slipstream());
        }

        return new RaceService.ResultsFormData(data, race, race.getMatchday().getPhase().getRaceScoring(),
                RaceCarPresentation.from(race));
    }

    public Map<String, Object> getUsedSelections(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        var available = carSelections.availability(seasonId, homeTeamId, excludeRaceId);
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new org.ctc.domain.exception.EntityNotFoundException("Season", seasonId));
        return Map.of("usedCarIds", available.usedCarIds(),
                "usedTrackIds", getUsedTrackIds(seasonId, homeTeamId, excludeRaceId),
                "entries", available.entries(), "consumedEntryIds", available.consumedEntryIds(),
                "carPoolMode", available.poolMode(), "carPoolState", available.poolState(),
                "fixedCars", season.getCars().stream()
                        .map(car -> new RaceCarSelectionService.CarChoice(car.getId(), car.getDisplayName())).toList(),
                "tracks", season.getTracks().stream().map(track -> new TrackChoice(track.getId(),
                        track.getName() + (track.getCountry() == null ? "" : " (" + track.getCountry() + ")"))).toList());
    }

    public record TrackChoice(UUID id, String label) {}

    private Set<UUID> getUsedTrackIds(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        if (homeTeamId == null) return Set.of();
        var used = raceRepository.findByMatchdaySeasonId(seasonId).stream()
                .filter(r -> !r.isBye())
                .filter(r -> r.getPlayoffMatchup() == null || !r.getPlayoffMatchup().isBye())
                .filter(r -> r.getHomeTeam() != null && r.getHomeTeam().getId().equals(homeTeamId))
                .filter(r -> excludeRaceId == null || !r.getId().equals(excludeRaceId))
                .filter(r -> r.getTrack() != null)
                .map(r -> r.getTrack().getId())
                .collect(Collectors.toSet());
        reservations.pending(seasonId, false).stream()
                .filter(pending -> homeTeamId.equals(pending.teamId()) && !pending.raceId().equals(excludeRaceId))
                .forEach(pending -> used.add(pending.trackId()));
        return used;
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
                race.getHomeTeam() != null ? race.getHomeTeam().getId() : null,
                race.getAwayTeam() != null ? race.getAwayTeam().getId() : null,
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
                settings != null ? settings.getMandatoryTyres() : null,
                settings != null ? settings.getTimeProgressionMode() : TimeProgressionMode.MULTIPLIER,
                race.getCarPoolEntry() == null ? null : race.getCarPoolEntry().getId(), LobbyRulesProfile.from(race),
                settings == null ? RaceStartType.GRID_FALSE_START : settings.getStartType(),
                settings == null ? RaceSlipstream.REAL : settings.getSlipstream());
    }
}
