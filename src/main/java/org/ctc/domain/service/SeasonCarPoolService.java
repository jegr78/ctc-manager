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
        var season = locked(seasonId);
        requireEditable(season);
        validateDefinition(label, clauses);
        var entry = entryId == null ? new SeasonCarPoolEntry() : ownedEntry(season, entryId);
        if (entryId == null) {
            entry.setSeason(season);
            int nextIndex = 0;
            for (var existing : season.getCarPoolEntries()) existing.setSortIndex(nextIndex++);
            entry.setSortIndex(nextIndex);
            season.getCarPoolEntries().add(entry);
        }
        entry.setLabel(label.trim());
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
        return entries.save(entry).getId();
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
            try { validateDefinition(entry.getLabel(), clauses); }
            catch (BusinessRuleException e) { problems.add(entry.getLabel() + ": " + e.getMessage()); }
            List<Car> matching = frozen ? entry.getMembers().stream().map(SeasonCarPoolMember::getCar).toList()
                    : catalog.stream().filter(car -> clauses.stream().anyMatch(clause -> matches(car, clause))).toList();
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

    private static void validateDefinition(String label, List<ClauseInput> clauses) {
        if (label == null || label.isBlank() || label.trim().length() > 120) throw new BusinessRuleException("Entry label must contain 1 to 120 characters.");
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
        if (season.getCarPoolState() == CarPoolState.FROZEN) throw new BusinessRuleException("The pool is frozen. Reopen the unused pool before editing.");
        requireUnused(season);
    }

    private static void requireCategoryMode(Season season) {
        if (season.getCarPoolMode() != CarPoolMode.CATEGORY_POOL) throw new BusinessRuleException("Choose category pool mode first.");
    }

    private void requireUnused(Season season) {
        if (races.existsByCarPoolEntrySeasonId(season.getId())) throw new BusinessRuleException("The pool is used by a race and cannot be changed.");
    }

    private static void clearSnapshot(Season season) {
        season.getCarPoolEntries().forEach(entry -> entry.getMembers().clear());
        season.setCarPoolState(CarPoolState.DRAFT);
    }
}
