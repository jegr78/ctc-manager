package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaceCarSelectionService {
    private final SeasonRepository seasons;
    private final TeamRepository teams;
    private final RaceRepository races;
    private final PickReservationService reservations;

    public Availability availability(UUID seasonId, UUID homeTeamId, UUID excludeRaceId) {
        var season = seasons.findById(seasonId).orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        if (homeTeamId != null && teams.findById(homeTeamId).isEmpty()) {
            throw new EntityNotFoundException("Team", homeTeamId);
        }
        var used = selectionUsage(seasonId, races.findByMatchdaySeasonId(seasonId), homeTeamId, excludeRaceId, false);
        var consumed = used.stream().map(SelectionUsage::entryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        var usedCars = used.stream().map(SelectionUsage::carId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (season.getCarPoolMode() == CarPoolMode.FIXED_CARS) {
            return new Availability(season.getCarPoolMode(), season.getCarPoolState(), List.of(), Set.of(), usedCars);
        }
        boolean frozen = season.getCarPoolState() == CarPoolState.FROZEN;
        var choices = season.getCarPoolEntries().stream().filter(e -> ownedBy(e, seasonId)).map(entry -> {
            boolean taken = consumed.contains(entry.getId());
            var cars = frozen ? entry.getMembers().stream().map(SeasonCarPoolMember::getCar)
                    .sorted(Comparator.comparing(Car::getDisplayName).thenComparing(c -> c.getId().toString()))
                    .map(car -> new CarChoice(car.getId(), car.getDisplayName())).toList() : List.<CarChoice>of();
            String reason = !frozen ? "Freeze this season's pool before choosing a car."
                    : taken ? "Already used by this home team this season." : null;
            return new EntryChoice(entry.getId(), entry.getLabel(), cars, taken, reason);
        }).toList();
        return new Availability(season.getCarPoolMode(), season.getCarPoolState(), choices, consumed, usedCars);
    }

    @Transactional
    public Validation validate(UUID seasonId, UUID homeTeamId, UUID excludeRaceId, UUID entryId, UUID carId) {
        try {
            var season = seasons.findByIdForUpdate(seasonId).orElse(null);
            if (season == null) return rejected("Choose an existing season.");
            var rows = races.findBySeasonForSelectionUpdate(seasonId);
            if ((entryId != null || carId != null) && rows.stream().anyMatch(race ->
                    race.getId().equals(excludeRaceId) && isBye(race))) {
                return rejected("A bye cannot select a car or pool entry.");
            }
            return validateSelection(season, homeTeamId, entryId, carId, selectionUsage(seasonId, rows, homeTeamId, excludeRaceId, true));
        } catch (RuntimeException ex) {
            throw RaceSelectionConcurrency.translate(ex);
        }
    }

    private Validation validateSelection(Season season, UUID homeTeamId, UUID entryId, UUID carId, List<SelectionUsage> used) {
        if (entryId == null && carId == null) return new Validation(null, null, null);
        if (homeTeamId == null || teams.findById(homeTeamId).isEmpty()) {
            return rejected("Choose an existing home team for this selection.");
        }
        if (season.getCarPoolMode() == CarPoolMode.FIXED_CARS) {
            if (entryId != null) return rejected("Fixed-car seasons cannot select a category pool entry.");
            var car = season.getCars().stream().filter(c -> c.getId().equals(carId)).findFirst().orElse(null);
            if (car == null) return rejected("Car is not in this season's pool");
            if (used.stream().anyMatch(r -> carId.equals(r.carId()))) {
                var home = teams.findById(homeTeamId).orElseThrow();
                return rejected(home.getShortName() + " has already used " + car.getDisplayName() + " this season");
            }
            return new Validation(null, null, car);
        }
        if (entryId == null || carId == null) return rejected("Choose both a pool entry and its concrete car, or clear both.");
        if (season.getCarPoolState() != CarPoolState.FROZEN) return rejected("Freeze the category pool before choosing a car.");
        var entry = season.getCarPoolEntries().stream()
                .filter(e -> entryId.equals(e.getId()) && ownedBy(e, season.getId())).findFirst().orElse(null);
        if (entry == null) return rejected("Choose a pool entry from this season.");
        var car = entry.getMembers().stream().map(SeasonCarPoolMember::getCar)
                .filter(c -> carId.equals(c.getId())).findFirst().orElse(null);
        if (car == null) return rejected("Choose a frozen member car of this pool entry.");
        if (used.stream().anyMatch(r -> entryId.equals(r.entryId()))) {
            return rejected("This home team has already used " + entry.getLabel() + " this season.");
        }
        return new Validation(null, entry, car);
    }

    @Transactional
    public Validation validateAssignments(UUID seasonId, List<Assignment> assignments) {
        try {
            var season = seasons.findByIdForUpdate(seasonId).orElse(null);
            if (season == null) return rejected("Choose an existing season.");
            var changedIds = new HashSet<UUID>();
            for (var assignment : assignments) {
                if (assignment.raceId() != null && !changedIds.add(assignment.raceId())) {
                    return rejected("A race may appear only once in a selection change.");
                }
            }
            var retained = races.findBySeasonForSelectionUpdate(seasonId).stream()
                    .filter(r -> !changedIds.contains(r.getId())).toList();
            var proposed = new ArrayList<Assignment>();
            for (var assignment : assignments) {
                if (assignment.bye() && (assignment.entryId() != null || assignment.carId() != null)) {
                    return rejected("A bye cannot select a car or pool entry.");
                }
                var result = validateSelection(season, assignment.homeTeamId(), assignment.entryId(), assignment.carId(),
                        selectionUsage(seasonId, retained, assignment.homeTeamId(), assignment.raceId(), true));
                if (!result.valid()) return result;
                if (assignment.carId() != null && proposed.stream().anyMatch(other ->
                        assignment.homeTeamId().equals(other.homeTeamId())
                        && (season.getCarPoolMode() == CarPoolMode.CATEGORY_POOL
                            ? assignment.entryId().equals(other.entryId()) : assignment.carId().equals(other.carId())))) {
                    return rejected("This home team has already used this selection in another changed race.");
                }
                if (assignment.carId() != null) proposed.add(assignment);
            }
            return new Validation(null, null, null);
        } catch (RuntimeException ex) {
            throw RaceSelectionConcurrency.translate(ex);
        }
    }

    public record Assignment(UUID raceId, UUID homeTeamId, boolean bye, UUID entryId, UUID carId) {}

    private static List<SelectionUsage> raceUsage(List<Race> candidates, UUID homeTeamId, UUID excludeRaceId) {
        if (homeTeamId == null) return List.of();
        return candidates.stream().filter(r -> !isBye(r))
                .filter(r -> r.getHomeTeam() != null && homeTeamId.equals(r.getHomeTeam().getId()))
                .filter(r -> excludeRaceId == null || !excludeRaceId.equals(r.getId()))
                .map(r -> new SelectionUsage(r.getCar() == null ? null : r.getCar().getId(),
                        r.getCarPoolEntry() == null ? null : r.getCarPoolEntry().getId())).toList();
    }

    private List<SelectionUsage> selectionUsage(UUID seasonId, List<Race> candidates, UUID homeTeamId,
                                               UUID excludeRaceId, boolean currentRead) {
        var used = new ArrayList<>(raceUsage(candidates, homeTeamId, excludeRaceId));
        for (var pending : reservations.pending(seasonId, currentRead)) {
            if (pending.teamId().equals(homeTeamId) && !pending.raceId().equals(excludeRaceId)) {
                used.add(new SelectionUsage(pending.carId(), pending.entryId()));
            }
        }
        return used;
    }

    private record SelectionUsage(UUID carId, UUID entryId) { }

    private static boolean isBye(Race race) {
        return race.isBye() || race.getPlayoffMatchup() != null && race.getPlayoffMatchup().isBye();
    }

    private static boolean ownedBy(SeasonCarPoolEntry entry, UUID seasonId) {
        return entry.getSeason() != null && seasonId.equals(entry.getSeason().getId());
    }

    private static Validation rejected(String message) {
        return new Validation(message, null, null);
    }

    public record Availability(CarPoolMode poolMode, CarPoolState poolState, List<EntryChoice> entries,
                               Set<UUID> consumedEntryIds, Set<UUID> usedCarIds) {
        public Availability {
            entries = List.copyOf(entries);
            consumedEntryIds = Set.copyOf(consumedEntryIds);
            usedCarIds = Set.copyOf(usedCarIds);
        }
    }

    public record EntryChoice(UUID id, String label, List<CarChoice> cars, boolean consumed, String reason) {
        public EntryChoice { cars = List.copyOf(cars); }
    }

    public record CarChoice(UUID id, String label) {}

    public record Validation(String message, SeasonCarPoolEntry entry, Car car) {
        public boolean valid() { return message == null; }
    }
}
