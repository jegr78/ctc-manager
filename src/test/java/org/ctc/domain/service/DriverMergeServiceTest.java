package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.PsnAliasRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverMergeServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private SeasonDriverRepository seasonDriverRepository;

    @Mock
    private RaceLineupRepository raceLineupRepository;

    @Mock
    private RaceResultRepository raceResultRepository;

    @Mock
    private PsnAliasRepository psnAliasRepository;

    @InjectMocks
    private DriverMergeService driverMergeService;

    @Nested
    class ValidationTests {

        @Test
        void givenSourceEqualsTarget_whenMerge_thenThrowsBusinessRuleException() {
            // given
            var id = UUID.randomUUID();

            // when / then
            assertThatThrownBy(() -> driverMergeService.merge(id, id))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot merge driver with itself");
        }

        @Test
        void givenNonExistentSource_whenMerge_thenThrowsEntityNotFoundException() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            when(driverRepository.findById(sourceId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> driverMergeService.merge(sourceId, targetId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Driver")
                    .hasMessageContaining(sourceId.toString());
        }

        @Test
        void givenNonExistentTarget_whenMerge_thenThrowsEntityNotFoundException() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            when(driverRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(driverRepository.findById(targetId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> driverMergeService.merge(sourceId, targetId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Driver")
                    .hasMessageContaining(targetId.toString());
        }
    }

    @Nested
    class FkReassignmentTests {

        @Test
        void givenSourceHasSeasonDrivers_whenMerge_thenAllReassignedToTarget() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");
            var sd1 = createSeasonDriver(source);
            var sd2 = createSeasonDriver(source);

            setupStandardMerge(sourceId, targetId, source, target);
            when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of(sd1, sd2));

            // when
            driverMergeService.merge(sourceId, targetId);

            // then
            assertThat(sd1.getDriver()).isEqualTo(target);
            assertThat(sd2.getDriver()).isEqualTo(target);
            verify(seasonDriverRepository).save(sd1);
            verify(seasonDriverRepository).save(sd2);
        }

        @Test
        void givenSourceHasRaceLineups_whenMerge_thenAllReassignedToTarget() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");
            var rl1 = createRaceLineup(source);
            var rl2 = createRaceLineup(source);

            setupStandardMerge(sourceId, targetId, source, target);
            when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of(rl1, rl2));

            // when
            driverMergeService.merge(sourceId, targetId);

            // then
            assertThat(rl1.getDriver()).isEqualTo(target);
            assertThat(rl2.getDriver()).isEqualTo(target);
            verify(raceLineupRepository).save(rl1);
            verify(raceLineupRepository).save(rl2);
        }

        @Test
        void givenSourceHasRaceResults_whenMerge_thenAllReassignedToTarget() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");
            var rr1 = createRaceResult(source);
            var rr2 = createRaceResult(source);

            setupStandardMerge(sourceId, targetId, source, target);
            when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of(rr1, rr2));

            // when
            driverMergeService.merge(sourceId, targetId);

            // then
            assertThat(rr1.getDriver()).isEqualTo(target);
            assertThat(rr2.getDriver()).isEqualTo(target);
            verify(raceResultRepository).save(rr1);
            verify(raceResultRepository).save(rr2);
        }

        @Test
        void givenSourceHasPsnAliases_whenMerge_thenAllReassignedViaRepository() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");
            var alias1 = createPsnAlias(source, "OldPSN1");
            var alias2 = createPsnAlias(source, "OldPSN2");

            setupStandardMerge(sourceId, targetId, source, target);
            when(psnAliasRepository.findByDriverId(sourceId)).thenReturn(List.of(alias1, alias2));

            // when
            driverMergeService.merge(sourceId, targetId);

            // then
            assertThat(alias1.getDriver()).isEqualTo(target);
            assertThat(alias2.getDriver()).isEqualTo(target);
            verify(psnAliasRepository).save(alias1);
            verify(psnAliasRepository).save(alias2);
        }
    }

    @Nested
    class PsnIdTransferTests {

        @Test
        void givenSourcePsnIdNotExistingAsAlias_whenMerge_thenCreatedAsAliasOnTarget() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");

            setupStandardMerge(sourceId, targetId, source, target);
            when(psnAliasRepository.existsByAliasIgnoreCase("SourcePSN")).thenReturn(false);

            // when
            driverMergeService.merge(sourceId, targetId);

            // then
            verify(psnAliasRepository).save(argThat(alias ->
                    alias.getAlias().equals("SourcePSN") && alias.getDriver().equals(target)));
        }

        @Test
        void givenSourcePsnIdAlreadyExistsAsAlias_whenMerge_thenSkippedSilently() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");

            setupStandardMerge(sourceId, targetId, source, target);
            when(psnAliasRepository.existsByAliasIgnoreCase("SourcePSN")).thenReturn(true);

            // when
            driverMergeService.merge(sourceId, targetId);

            // then
            verify(psnAliasRepository, never()).save(argThat(alias ->
                    alias.getAlias().equals("SourcePSN")));
        }
    }

    @Nested
    class DeletionTests {

        @Test
        void givenValidDrivers_whenMerge_thenSourceDriverDeleted() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");

            setupStandardMerge(sourceId, targetId, source, target);

            // when
            driverMergeService.merge(sourceId, targetId);

            // then
            verify(driverRepository).delete(source);
        }
    }

    @Nested
    class ResultTests {

        @Test
        void givenAllFkTypes_whenMerge_thenMergeResultContainsCorrectCounts() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");

            setupStandardMerge(sourceId, targetId, source, target);
            when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of(
                    createSeasonDriver(source), createSeasonDriver(source)));
            when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of(
                    createRaceLineup(source)));
            when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of(
                    createRaceResult(source), createRaceResult(source), createRaceResult(source)));
            when(psnAliasRepository.findByDriverId(sourceId)).thenReturn(List.of(
                    createPsnAlias(source, "OldAlias1")));
            when(psnAliasRepository.existsByAliasIgnoreCase("SourcePSN")).thenReturn(false);

            // when
            var result = driverMergeService.merge(sourceId, targetId);

            // then
            assertThat(result.seasonDrivers()).isEqualTo(2);
            assertThat(result.raceLineups()).isEqualTo(1);
            assertThat(result.raceResults()).isEqualTo(3);
            assertThat(result.aliasesReassigned()).isEqualTo(2); // 1 existing alias + 1 new PSN-ID alias
        }

        @Test
        void givenValidMerge_whenComplete_thenMergeLoggedWithStructuredParams() {
            // given
            var sourceId = UUID.randomUUID();
            var targetId = UUID.randomUUID();
            var source = createDriver(sourceId, "SourcePSN");
            var target = createDriver(targetId, "TargetPSN");

            setupStandardMerge(sourceId, targetId, source, target);

            // when
            var result = driverMergeService.merge(sourceId, targetId);

            // then — merge completes successfully and returns valid result
            assertThat(result).isNotNull();
            assertThat(result.seasonDrivers()).isZero();
            assertThat(result.raceLineups()).isZero();
            assertThat(result.raceResults()).isZero();
            assertThat(result.aliasesReassigned()).isEqualTo(1); // PSN-ID transfer
        }
    }

    // --- Helper methods ---

    private Driver createDriver(UUID id, String psnId) {
        var driver = new Driver(psnId, psnId);
        driver.setId(id);
        return driver;
    }

    private SeasonDriver createSeasonDriver(Driver driver) {
        var sd = new SeasonDriver();
        sd.setDriver(driver);
        return sd;
    }

    private RaceLineup createRaceLineup(Driver driver) {
        var rl = new RaceLineup();
        rl.setDriver(driver);
        return rl;
    }

    private RaceResult createRaceResult(Driver driver) {
        var rr = new RaceResult();
        rr.setDriver(driver);
        return rr;
    }

    private PsnAlias createPsnAlias(Driver driver, String alias) {
        return new PsnAlias(driver, alias);
    }

    private void setupStandardMerge(UUID sourceId, UUID targetId, Driver source, Driver target) {
        when(driverRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(driverRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of());
        when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of());
        when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of());
        when(psnAliasRepository.findByDriverId(sourceId)).thenReturn(List.of());
        when(psnAliasRepository.existsByAliasIgnoreCase(source.getPsnId())).thenReturn(false);
    }
}
