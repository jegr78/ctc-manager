package org.ctc.domain.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeasonCarPoolService {
    public static final int MAX_CLAUSES = 32;
    private final SeasonRepository seasons;
    private final CarRepository cars;
    private final SeasonCarPoolEntryRepository entries;
    private final RaceRepository races;
    private final PickHistoryReferences pickHistory;

    public record ClauseInput(CarCategory category, Drivetrain drivetrain, Integer modelYear) {}
    public record CarOption(UUID id, String label) {}
    public record EntryPreview(UUID id, String label, List<CarOption> cars) {
        public List<UUID> carIds() { return cars.stream().map(CarOption::id).toList(); }
    }
    public record Overlap(String firstLabel, String secondLabel, List<UUID> carIds) {}
    public record Preview(List<EntryPreview> entries, List<Overlap> overlaps, List<String> problems,
                          List<String> notices, boolean frozen) {}

    @Transactional
    public UUID saveEntry(UUID seasonId, UUID entryId, String label, List<ClauseInput> clauses) {
        return saveDefinition(seasonId, entryId, label, clauses, null, false);
    }

    @Transactional
    public UUID saveOneMakeEntry(UUID seasonId, UUID entryId, String label, UUID carId) {
        if (carId == null) throw new BusinessRuleException("Choose a car for the one-make entry.");
        return saveDefinition(seasonId, entryId, label, List.of(), carId, false);
    }

    @Transactional
    public UUID addFrozenEntry(UUID seasonId, String label, List<ClauseInput> clauses) {
        return saveDefinition(seasonId, null, label, clauses, null, true);
    }

    @Transactional
    public UUID addFrozenOneMakeEntry(UUID seasonId, String label, UUID carId) {
        if (carId == null) throw new BusinessRuleException("Choose a car for the one-make entry.");
        return saveDefinition(seasonId, null, label, List.of(), carId, true);
    }

    private UUID saveDefinition(UUID seasonId, UUID entryId, String label, List<ClauseInput> clauses, UUID carId, boolean addition) {
        var season = locked(seasonId);
        if (addition) {
            requireCategoryMode(season);
            if (season.getCarPoolState() != CarPoolState.FROZEN) {
                throw new BusinessRuleException("The pool is no longer frozen. Reload before adding an entry.");
            }
        } else requireEditable(season);
        validateDefinition(label, clauses, carId);
        var oneMakeCar = carId == null ? null : cars.findById(carId)
                .orElseThrow(() -> new BusinessRuleException("The selected one-make car no longer exists."));
        var addedMembers = addition ? additionMembers(season, clauses, oneMakeCar) : List.<Car>of();
        var entry = entryId == null ? new SeasonCarPoolEntry() : ownedEntry(season, entryId);
        if (entryId == null) {
            entry.setSeason(season);
            int nextIndex = addition ? season.getCarPoolEntries().stream().mapToInt(SeasonCarPoolEntry::getSortIndex)
                    .max().orElse(-1) + 1 : 0;
            if (!addition) for (var existing : season.getCarPoolEntries()) existing.setSortIndex(nextIndex++);
            entry.setSortIndex(nextIndex);
            season.getCarPoolEntries().add(entry);
        }
        entry.setLabel(label.trim());
        entry.setOneMakeCar(oneMakeCar);
        entry.getClauses().clear();
        for (int i = 0; i < clauses.size(); i++) {
            var input = clauses.get(i);
            var clause = new SeasonCarPoolClause();
            clause.setEntry(entry);
            clause.setCategory(input.category());
            clause.setDrivetrain(input.drivetrain());
            clause.setModelYear(input.modelYear());
            clause.setSortIndex(i);
            entry.getClauses().add(clause);
        }
        for (var car : addedMembers) {
            var member = new SeasonCarPoolMember(); member.setEntry(entry); member.setCar(car);
            entry.getMembers().add(member);
        }
        return entries.save(entry).getId();
    }

    private List<Car> additionMembers(Season season, List<ClauseInput> clauses, Car oneMakeCar) {
        var members = oneMakeCar != null ? List.of(oneMakeCar) : cars.findAllByOrderByManufacturerAscNameAsc().stream()
                .filter(car -> clauses.stream().anyMatch(clause -> matches(car, clause))).toList();
        if (members.isEmpty()) throw new BusinessRuleException("The new entry has no eligible cars.");
        var existingIds = season.getCarPoolEntries().stream().flatMap(entry -> entry.getMembers().stream())
                .map(member -> member.getCar().getId()).collect(java.util.stream.Collectors.toSet());
        if (members.stream().anyMatch(car -> existingIds.contains(car.getId()))) {
            throw new BusinessRuleException("The new entry would overlap an existing frozen entry.");
        }
        return members;
    }

    @Transactional
    public void removeEntry(UUID seasonId, UUID entryId) {
        var season = locked(seasonId);
        requireEditable(season);
        season.getCarPoolEntries().remove(ownedEntry(season, entryId));
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID seasonId) {
        return preview(seasons.findById(seasonId).orElseThrow(() -> new EntityNotFoundException("Season", seasonId)));
    }

    @Transactional
    public void freeze(UUID seasonId) {
        var season = locked(seasonId);
        requireEditable(season);
        var catalog = cars.findAllByOrderByManufacturerAscNameAsc();
        var preview = preview(season, catalog);
        if (!preview.problems().isEmpty()) throw new BusinessRuleException(String.join("; ", preview.problems()));
        var carsById = new HashMap<UUID, Car>();
        catalog.forEach(car -> carsById.put(car.getId(), car));
        for (int i = 0; i < season.getCarPoolEntries().size(); i++) {
            var entry = season.getCarPoolEntries().get(i);
            entry.getMembers().clear();
            for (var carId : preview.entries().get(i).carIds()) {
                var member = new SeasonCarPoolMember();
                member.setEntry(entry);
                member.setCar(carsById.get(carId));
                entry.getMembers().add(member);
            }
        }
        season.setCarPoolState(CarPoolState.FROZEN);
        seasons.saveAndFlush(season);
    }

    @Transactional
    public void reopen(UUID seasonId) {
        var season = locked(seasonId);
        requireCategoryMode(season);
        if (season.getCarPoolState() != CarPoolState.FROZEN) throw new BusinessRuleException("The pool is already a draft.");
        requireUnused(season);
        clearSnapshot(season);
    }

    @Transactional
    public void changeMode(UUID seasonId, CarPoolMode mode) {
        var season = locked(seasonId);
        if (mode == null) throw new BusinessRuleException("Choose a car pool mode.");
        if (mode == season.getCarPoolMode()) return;
        requireUnused(season);
        if (races.existsByMatchdayPhaseSeasonIdAndCarIsNotNull(seasonId)) {
            throw new BusinessRuleException("Cannot change mode: a race has a saved car selection.");
        }
        clearSnapshot(season);
        season.setCarPoolMode(mode);
    }

    private Preview preview(Season season) {
        return preview(season, season.getCarPoolState() == CarPoolState.FROZEN ? List.of() : cars.findAllByOrderByManufacturerAscNameAsc());
    }

    private Preview preview(Season season, List<Car> catalog) {
        boolean frozen = season.getCarPoolState() == CarPoolState.FROZEN;
        var result = new ArrayList<EntryPreview>();
        var problems = new ArrayList<String>();
        var notices = new ArrayList<String>();
        if (season.getCarPoolEntries().isEmpty()) problems.add("Add at least one entry before freezing.");
        for (var entry : season.getCarPoolEntries()) {
            var clauses = entry.getClauses().stream().map(c -> new ClauseInput(c.getCategory(), c.getDrivetrain(), c.getModelYear())).toList();
            UUID oneMakeId = entry.getOneMakeCar() == null ? null : entry.getOneMakeCar().getId();
            try { validateDefinition(entry.getLabel(), clauses, oneMakeId); }
            catch (BusinessRuleException e) { problems.add(entry.getLabel() + ": " + e.getMessage()); }
            List<Car> matching = frozen ? entry.getMembers().stream().map(SeasonCarPoolMember::getCar).toList()
                    : catalog.stream().filter(car -> oneMakeId != null ? oneMakeId.equals(car.getId())
                            : clauses.stream().anyMatch(clause -> matches(car, clause))).toList();
            if (frozen && oneMakeId != null && (matching.size() != 1 || !oneMakeId.equals(matching.getFirst().getId()))) {
                problems.add("One-make entry '" + entry.getLabel() + "' must contain exactly its selected car.");
            }
            var choices = matching.stream().map(car -> new CarOption(car.getId(), car.getManufacturer() + " — " + car.getName()))
                    .distinct().toList();
            result.add(new EntryPreview(entry.getId(), entry.getLabel(), choices));
            if (choices.isEmpty()) problems.add("Entry '" + entry.getLabel() + "' has no matching cars.");
        }
        var overlaps = new ArrayList<Overlap>();
        for (int i = 0; i < result.size(); i++) {
            for (int j = i + 1; j < result.size(); j++) {
                var first = result.get(i);
                var second = result.get(j);
                var secondIds = new HashSet<>(second.carIds());
                var shared = first.carIds().stream().filter(secondIds::contains).toList();
                if (!shared.isEmpty()) {
                    overlaps.add(new Overlap(first.label(), second.label(), shared));
                    problems.add("Entries '" + first.label() + "' and '" + second.label() + "' overlap: " + shared);
                }
            }
        }
        for (var car : catalog) {
            if (car.getCategory() == null || car.getDrivetrain() == null || car.getModelYear() == null) {
                var unknown = new ArrayList<String>();
                if (car.getCategory() == null) unknown.add("category");
                if (car.getDrivetrain() == null) unknown.add("drivetrain");
                if (car.getModelYear() == null) unknown.add("model year");
                notices.add(car.getManufacturer() + " " + car.getName() + ": unknown " + String.join(", ", unknown) + ". Explicit filters exclude unknown values.");
            }
        }
        return new Preview(List.copyOf(result), List.copyOf(overlaps), List.copyOf(problems), List.copyOf(notices), frozen);
    }

    private static boolean matches(Car car, ClauseInput clause) {
        return clause.category() != null && car.getCategory() == clause.category()
                && (clause.drivetrain() == null || car.getDrivetrain() == clause.drivetrain())
                && (clause.modelYear() == null || clause.modelYear().equals(car.getModelYear()));
    }

    private static void validateDefinition(String label, List<ClauseInput> clauses, UUID oneMakeCarId) {
        if (label == null || label.isBlank() || label.trim().length() > 120) throw new BusinessRuleException("Entry label must contain 1 to 120 characters.");
        if (oneMakeCarId != null) {
            if (clauses == null || !clauses.isEmpty()) throw new BusinessRuleException("A one-make entry cannot also have category clauses.");
            return;
        }
        if (clauses == null || clauses.isEmpty() || clauses.size() > MAX_CLAUSES) throw new BusinessRuleException("An entry requires 1 to " + MAX_CLAUSES + " clauses.");
        var unique = new HashSet<ClauseInput>();
        for (var clause : clauses) {
            if (clause == null || clause.category() == null) throw new BusinessRuleException("Every clause requires a category.");
            CarMetadataService.validateYear(clause.modelYear());
            if (!unique.add(clause)) throw new BusinessRuleException("Duplicate clauses are not allowed.");
        }
    }

    private Season locked(UUID seasonId) {
        return seasons.findByIdForUpdate(seasonId).orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
    }

    private static SeasonCarPoolEntry ownedEntry(Season season, UUID entryId) {
        return season.getCarPoolEntries().stream().filter(entry -> entry.getId().equals(entryId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Pool entry does not belong to this season."));
    }

    private void requireEditable(Season season) {
        requireCategoryMode(season);
        if (season.getCarPoolState() == CarPoolState.FROZEN) throw new BusinessRuleException("The pool is frozen. Reload to add a new entry; existing entries require reopening an unused pool.");
        requireUnused(season);
    }

    private static void requireCategoryMode(Season season) {
        if (season.getCarPoolMode() != CarPoolMode.CATEGORY_POOL) throw new BusinessRuleException("Choose category pool mode first.");
    }

    private void requireUnused(Season season) {
        if (pickHistory.hasSelection(season.getId())) throw new BusinessRuleException("The pool is referenced by confidential pick history and cannot be changed");
        if (races.existsByCarPoolEntrySeasonId(season.getId())) throw new BusinessRuleException("The pool is used by a race and cannot be changed.");
    }

    private static void clearSnapshot(Season season) {
        season.getCarPoolEntries().forEach(entry -> entry.getMembers().clear());
        season.setCarPoolState(CarPoolState.DRAFT);
    }
}
