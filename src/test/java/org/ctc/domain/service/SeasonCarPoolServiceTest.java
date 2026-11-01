package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeasonCarPoolServiceTest {
    @Mock SeasonRepository seasons;
    @Mock CarRepository cars;
    @Mock SeasonCarPoolEntryRepository entries;
    @Mock RaceRepository races;
    @InjectMocks SeasonCarPoolService service;
    Season season;

    @BeforeEach
    void setUp() {
        season = new Season("Local pool");
        season.setId(UUID.randomUUID());
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        lenient().when(seasons.findById(season.getId())).thenReturn(Optional.of(season));
        lenient().when(seasons.findByIdForUpdate(season.getId())).thenReturn(Optional.of(season));
        lenient().when(entries.save(any())).thenAnswer(invocation -> {
            SeasonCarPoolEntry entry = invocation.getArgument(0);
            if (entry.getId() == null) entry.setId(UUID.randomUUID());
            return entry;
        });
    }

    @Test
    void givenMaximalRestoredSortIndex_whenAppending_thenOrderIsNormalizedWithoutOverflow() {
        // given
        save("Restored", clause(CarCategory.GR_3, null, null));
        season.getCarPoolEntries().getFirst().setSortIndex(Integer.MAX_VALUE);
        save("Appended", clause(CarCategory.GR_4, null, null));
        // when / then
        assertThat(season.getCarPoolEntries()).extracting(SeasonCarPoolEntry::getLabel).containsExactly("Restored", "Appended");
        assertThat(season.getCarPoolEntries()).extracting(SeasonCarPoolEntry::getSortIndex).containsExactly(0, 1);
    }

    @Test
    void givenNullOrOversizedDefinition_whenSaving_thenRejectBeforeAddingEntry() {
        // given
        var valid = List.of(clause(CarCategory.GR_3, null, null));
        // when / then
        assertThatThrownBy(() -> service.saveEntry(season.getId(), null, null, valid)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.saveEntry(season.getId(), null, "x".repeat(121), valid)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.saveEntry(season.getId(), null, "Null clauses", null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.saveEntry(season.getId(), null, "Null clause", java.util.Collections.singletonList(null))).isInstanceOf(BusinessRuleException.class);
        var tooMany = java.util.stream.IntStream.range(0, 33).mapToObj(i -> clause(CarCategory.GR_3, null, 1000 + i)).toList();
        assertThatThrownBy(() -> service.saveEntry(season.getId(), null, "Too many", tooMany)).isInstanceOf(BusinessRuleException.class);
        assertThat(season.getCarPoolEntries()).isEmpty();
    }

    @Test
    void givenUsedDraft_whenEditingOrFreezing_thenRejectEvenIfStateWasIncorrectlyRestored() {
        // given
        var id = save("Keep", clause(CarCategory.GR_3, null, null));
        when(races.existsByCarPoolEntrySeasonId(season.getId())).thenReturn(true);
        // when / then
        assertThatThrownBy(() -> save("Forged", clause(CarCategory.GR_4, null, null))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("used");
        assertThatThrownBy(() -> service.removeEntry(season.getId(), id)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("used");
        assertThatThrownBy(() -> service.freeze(season.getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("used");
        assertThat(season.getCarPoolEntries()).singleElement().extracting(SeasonCarPoolEntry::getLabel).isEqualTo("Keep");
    }

    @Test
    void givenUnusedSeason_whenChangingModesOrRemovingEntry_thenDefinitionsRemainExplicitAndFixedCarsArePreserved() {
        // given
        var fixed = car("Dormant", CarCategory.GR_4, null, null);
        season.getCars().add(fixed);
        var id = save("Remove", clause(CarCategory.GR_3, null, null));
        // when
        service.removeEntry(season.getId(), id);
        // then
        assertThat(season.getCarPoolEntries()).isEmpty();
        service.changeMode(season.getId(), CarPoolMode.FIXED_CARS);
        service.changeMode(season.getId(), CarPoolMode.FIXED_CARS);
        assertThat(season.getCarPoolMode()).isEqualTo(CarPoolMode.FIXED_CARS);
        assertThat(season.getCars()).containsExactly(fixed);
        service.changeMode(season.getId(), CarPoolMode.CATEGORY_POOL);
        assertThat(season.getCarPoolState()).isEqualTo(CarPoolState.DRAFT);
        assertThatThrownBy(() -> service.changeMode(season.getId(), null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reopen(season.getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("already");
    }

    @Test
    void givenCombinedClauses_whenPreviewing_thenAndWithinOrBetweenAndDeduplicationApply() {
        // given
        var mr = car("MR", CarCategory.GR_3, Drivetrain.MR, null);
        var rr = car("RR", CarCategory.GR_3, Drivetrain.RR, 1999);
        var fr = car("FR", CarCategory.GR_4, Drivetrain.FR, 2000);
        var gr4mr = car("Gr4 MR", CarCategory.GR_4, Drivetrain.MR, 2026);
        var unknown = car("Unknown", null, null, null);
        when(cars.findAllByOrderByManufacturerAscNameAsc()).thenReturn(List.of(mr, rr, fr, gr4mr, unknown));
        save("Gr.3 alternatives", clause(CarCategory.GR_3, Drivetrain.MR, null), clause(CarCategory.GR_3, Drivetrain.RR, null));
        save("Gr.4 alternatives", clause(CarCategory.GR_4, Drivetrain.FR, null), clause(CarCategory.GR_4, Drivetrain.MR, null));
        // when
        var preview = service.preview(season.getId());
        // then
        assertThat(preview.entries().get(0).carIds()).containsExactly(mr.getId(), rr.getId());
        assertThat(preview.entries().get(1).carIds()).containsExactly(fr.getId(), gr4mr.getId());
        assertThat(preview.notices()).anyMatch(message -> message.contains("Unknown"));
        assertThat(preview.problems()).isEmpty();
        save("Deduplicated union", clause(CarCategory.GR_3, null, null), clause(CarCategory.GR_3, Drivetrain.MR, null));
        assertThat(service.preview(season.getId()).entries().get(2).carIds()).containsExactly(mr.getId(), rr.getId());
    }

    @Test
    void givenExactYear_whenPreviewing_thenOnlyTheThreeKnown2016CarsMatch() {
        // given
        var honda = car("Honda NSX CONCEPT-GT", CarCategory.GR_2, Drivetrain.MR, 2016);
        var lexus = car("Lexus RC F GT500", CarCategory.GR_2, Drivetrain.FR, 2016);
        var nissan = car("Nissan GT-R NISMO GT500", CarCategory.GR_2, Drivetrain.FR, 2016);
        var unknownYear = car("Unknown year", CarCategory.GR_2, Drivetrain.FR, null);
        var older = car("Older", CarCategory.GR_2, null, 1929);
        when(cars.findAllByOrderByManufacturerAscNameAsc()).thenReturn(List.of(honda, lexus, nissan, unknownYear, older));
        save("2016", clause(CarCategory.GR_2, null, 2016));
        save("All years", clause(CarCategory.GR_2, null, null));
        // when / then
        assertThat(service.preview(season.getId()).entries().get(0).carIds()).containsExactly(honda.getId(), lexus.getId(), nissan.getId());
        assertThat(service.preview(season.getId()).entries().get(1).carIds()).hasSize(5);
    }

    @Test
    void givenUnknownRequiredMetadata_whenPreviewing_thenItCannotMatchAnExplicitFilter() {
        // given
        var unknownDrive = car("Unknown drive", CarCategory.GR_3, null, 2016);
        var unknownCategory = car("Unknown category", null, Drivetrain.MR, 2016);
        when(cars.findAllByOrderByManufacturerAscNameAsc()).thenReturn(List.of(unknownDrive, unknownCategory));
        save("MR only", clause(CarCategory.GR_3, Drivetrain.MR, null));
        // when / then
        assertThat(service.preview(season.getId()).entries().getFirst().carIds()).isEmpty();
        assertThatThrownBy(() -> service.freeze(season.getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("MR only");
        assertThat(season.getCarPoolState()).isEqualTo(CarPoolState.DRAFT);
    }

    @Test
    void givenMalformedDefinitions_whenSaving_thenExistingDefinitionRemainsUntouched() {
        // given
        var id = save("Original", clause(CarCategory.GR_3, null, null));
        var valid = clause(CarCategory.GR_4, null, null);
        for (var invalid : List.of(List.<SeasonCarPoolService.ClauseInput>of(), List.of(valid, valid),
                List.of(clause(null, null, null)), List.of(clause(CarCategory.GR_4, null, 99)))) {
            // when / then
            assertThatThrownBy(() -> service.saveEntry(season.getId(), id, "Changed", invalid)).isInstanceOf(BusinessRuleException.class);
            assertThat(season.getCarPoolEntries().getFirst().getLabel()).isEqualTo("Original");
        }
        assertThatThrownBy(() -> service.saveEntry(season.getId(), id, " ", List.of(valid))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.saveEntry(season.getId(), UUID.randomUUID(), "Foreign", List.of(valid))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.removeEntry(season.getId(), UUID.randomUUID())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenCrossEntryOverlap_whenFreezing_thenSharedIdsAreReportedAndNothingIsPersisted() {
        // given
        var car = car("Shared", CarCategory.GR_3, Drivetrain.MR, 2016);
        when(cars.findAllByOrderByManufacturerAscNameAsc()).thenReturn(List.of(car));
        save("Broad", clause(CarCategory.GR_3, null, null));
        save("Narrow", clause(CarCategory.GR_3, Drivetrain.MR, null));
        // when
        var preview = service.preview(season.getId());
        // then
        assertThat(preview.overlaps()).hasSize(1);
        assertThat(preview.overlaps().getFirst().carIds()).containsExactly(car.getId());
        assertThat(preview.problems()).anyMatch(message -> message.contains("Broad") && message.contains("Narrow"));
        assertThatThrownBy(() -> service.freeze(season.getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("overlap");
        assertThat(season.getCarPoolEntries()).allSatisfy(entry -> assertThat(entry.getMembers()).isEmpty());
        assertThat(season.getCarPoolState()).isEqualTo(CarPoolState.DRAFT);
    }

    @Test
    void givenValidPool_whenFrozen_thenSnapshotAndDefinitionsSurviveCatalogChanges() {
        // given
        var car = car("Member", CarCategory.GR_3, Drivetrain.MR, null);
        when(cars.findAllByOrderByManufacturerAscNameAsc()).thenReturn(List.of(car));
        var id = save("Original label", clause(CarCategory.GR_3, null, null));
        // when
        service.freeze(season.getId());
        // then
        assertThat(season.getCarPoolState()).isEqualTo(CarPoolState.FROZEN);
        assertThat(season.getCarPoolEntries().getFirst().getMembers()).extracting(member -> member.getCar().getId()).containsExactly(car.getId());
        car.setCategory(CarCategory.GR_4);
        assertThat(service.preview(season.getId()).entries().getFirst().carIds()).containsExactly(car.getId());
        assertThatThrownBy(() -> service.saveEntry(season.getId(), id, "Changed", List.of(clause(CarCategory.GR_4, null, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("frozen");
        assertThatThrownBy(() -> service.removeEntry(season.getId(), id)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.freeze(season.getId())).isInstanceOf(BusinessRuleException.class);
        verify(seasons, atLeast(4)).findByIdForUpdate(season.getId());
    }

    @Test
    void givenUsedPool_whenReopeningOrChangingMode_thenRejectWithoutMutation() {
        // given
        season.setCarPoolState(CarPoolState.FROZEN);
        when(races.existsByCarPoolEntrySeasonId(season.getId())).thenReturn(true);
        // when / then
        assertThatThrownBy(() -> service.reopen(season.getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("used");
        assertThatThrownBy(() -> service.changeMode(season.getId(), CarPoolMode.FIXED_CARS)).isInstanceOf(BusinessRuleException.class);
        assertThat(season.getCarPoolState()).isEqualTo(CarPoolState.FROZEN);
        assertThat(season.getCarPoolMode()).isEqualTo(CarPoolMode.CATEGORY_POOL);
    }

    @Test
    void givenUnusedFrozenPool_whenReopening_thenOnlySnapshotIsCleared() {
        // given
        var car = car("Member", CarCategory.GR_3, Drivetrain.MR, null);
        when(cars.findAllByOrderByManufacturerAscNameAsc()).thenReturn(List.of(car));
        save("Keep definition", clause(CarCategory.GR_3, null, null));
        // when
        service.freeze(season.getId());
        service.reopen(season.getId());
        // then
        assertThat(season.getCarPoolState()).isEqualTo(CarPoolState.DRAFT);
        assertThat(season.getCarPoolEntries().getFirst().getMembers()).isEmpty();
        assertThat(season.getCarPoolEntries().getFirst().getClauses()).hasSize(1);
        assertThat(season.getCarPoolEntries().getFirst().getLabel()).isEqualTo("Keep definition");
    }

    @Test
    void givenFixedSeasonWithSavedCar_whenChangingMode_thenSelectionCannotBeInvalidated() {
        // given
        season.setCarPoolMode(CarPoolMode.FIXED_CARS);
        when(races.existsByMatchdayPhaseSeasonIdAndCarIsNotNull(season.getId())).thenReturn(true);
        // when / then
        assertThatThrownBy(() -> service.changeMode(season.getId(), CarPoolMode.CATEGORY_POOL)).isInstanceOf(BusinessRuleException.class);
        assertThat(season.getCarPoolMode()).isEqualTo(CarPoolMode.FIXED_CARS);
    }

    @Test
    void givenEmptyOrFixedPool_whenFreezing_thenReject() {
        // given
        // when / then
        assertThatThrownBy(() -> service.freeze(season.getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("entry");
        season.setCarPoolMode(CarPoolMode.FIXED_CARS);
        assertThatThrownBy(() -> service.freeze(season.getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("category");
        assertThatThrownBy(() -> save("Invalid", clause(CarCategory.GR_3, null, null))).isInstanceOf(BusinessRuleException.class);
    }

    private UUID save(String label, SeasonCarPoolService.ClauseInput... clauses) {
        return service.saveEntry(season.getId(), null, label, List.of(clauses));
    }

    private static SeasonCarPoolService.ClauseInput clause(CarCategory category, Drivetrain drive, Integer year) {
        return new SeasonCarPoolService.ClauseInput(category, drive, year);
    }

    private static Car car(String name, CarCategory category, Drivetrain drive, Integer year) {
        var car = new Car("Fixture", name);
        car.setId(UUID.randomUUID());
        car.setCategory(category);
        car.setDrivetrain(drive);
        car.setModelYear(year);
        return car;
    }
}
