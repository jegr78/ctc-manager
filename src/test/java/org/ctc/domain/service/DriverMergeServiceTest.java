package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
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

	private Driver createDriver(UUID id, String psnId) {
		var driver = new Driver(psnId, psnId);
		driver.setId(id);
		return driver;
	}

	private SeasonDriver createSeasonDriver(Driver driver) {
		var sd = new SeasonDriver();
		sd.setDriver(driver);
		sd.setSeason(createSeason(UUID.randomUUID(), "Season"));
		return sd;
	}

	private RaceLineup createRaceLineup(Driver driver) {
		var rl = new RaceLineup();
		rl.setDriver(driver);
		rl.setRace(createRace(UUID.randomUUID()));
		return rl;
	}

	private RaceResult createRaceResult(Driver driver) {
		var rr = new RaceResult();
		rr.setDriver(driver);
		rr.setRace(createRace(UUID.randomUUID()));
		return rr;
	}

	private PsnAlias createPsnAlias(Driver driver, String alias) {
		return new PsnAlias(driver, alias);
	}

	private Season createSeason(UUID id, String name) {
		var season = new Season();
		season.setId(id);
		season.setName(name);
		return season;
	}

	private Race createRace(UUID id) {
		var race = new Race();
		race.setId(id);
		return race;
	}


	private SeasonDriver createSeasonDriverWithSeason(Driver driver, Season season) {
		var sd = new SeasonDriver();
		sd.setDriver(driver);
		sd.setSeason(season);
		return sd;
	}

	private RaceLineup createRaceLineupWithRace(Driver driver, Race race) {
		var rl = new RaceLineup();
		rl.setDriver(driver);
		rl.setRace(race);
		return rl;
	}

	private RaceResult createRaceResultWithRace(Driver driver, Race race) {
		var rr = new RaceResult();
		rr.setDriver(driver);
		rr.setRace(race);
		return rr;
	}

	private void setupStandardMerge(UUID sourceId, UUID targetId, Driver source, Driver target) {
		when(driverRepository.findById(sourceId)).thenReturn(Optional.of(source));
		when(driverRepository.findById(targetId)).thenReturn(Optional.of(target));
		when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of());
		when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of());
		when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of());
		when(psnAliasRepository.findByDriverId(sourceId)).thenReturn(List.of());
		when(psnAliasRepository.existsByAliasIgnoreCase(source.getPsnId())).thenReturn(false);
		lenient().when(seasonDriverRepository.findBySeasonIdAndDriverId(any(), eq(targetId))).thenReturn(Optional.empty());
		lenient().when(raceLineupRepository.findByRaceIdAndDriverId(any(), eq(targetId))).thenReturn(Optional.empty());
		lenient().when(raceResultRepository.findByRaceIdAndDriverId(any(), eq(targetId))).thenReturn(Optional.empty());
	}

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
			assertThat(result.seasonDriversDropped()).isZero();
			assertThat(result.raceLineupsDropped()).isZero();
			assertThat(result.raceResultsDropped()).isZero();
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

	@Nested
	class DuplicateHandlingTests {

		@Test
		void givenSeasonDriverConflict_whenMerge_thenSourceDeletedNotReassigned() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");
			var seasonA = createSeason(UUID.randomUUID(), "TestSeason");
			var sourceSD = createSeasonDriverWithSeason(source, seasonA);
			var targetSD = createSeasonDriverWithSeason(target, seasonA);

			setupStandardMerge(sourceId, targetId, source, target);
			when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceSD));
			when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonA.getId(), targetId))
					.thenReturn(Optional.of(targetSD));

			// when
			driverMergeService.merge(sourceId, targetId);

			// then
			verify(seasonDriverRepository).delete(sourceSD);
			verify(seasonDriverRepository, never()).save(sourceSD);
		}

		@Test
		void givenSeasonDriverNoConflict_whenMerge_thenStillReassigned() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");
			var seasonA = createSeason(UUID.randomUUID(), "TestSeason");
			var sourceSD = createSeasonDriverWithSeason(source, seasonA);

			setupStandardMerge(sourceId, targetId, source, target);
			when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceSD));
			lenient().when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonA.getId(), targetId))
					.thenReturn(Optional.empty());

			// when
			driverMergeService.merge(sourceId, targetId);

			// then
			assertThat(sourceSD.getDriver()).isEqualTo(target);
			verify(seasonDriverRepository).save(sourceSD);
		}

		@Test
		void givenRaceLineupConflict_whenMerge_thenSourceDeletedNotReassigned() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");
			var raceA = createRace(UUID.randomUUID());
			var sourceRL = createRaceLineupWithRace(source, raceA);
			var targetRL = createRaceLineupWithRace(target, raceA);

			setupStandardMerge(sourceId, targetId, source, target);
			when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceRL));
			when(raceLineupRepository.findByRaceIdAndDriverId(raceA.getId(), targetId))
					.thenReturn(Optional.of(targetRL));

			// when
			driverMergeService.merge(sourceId, targetId);

			// then
			verify(raceLineupRepository).delete(sourceRL);
			verify(raceLineupRepository, never()).save(sourceRL);
		}

		@Test
		void givenRaceLineupNoConflict_whenMerge_thenStillReassigned() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");
			var raceA = createRace(UUID.randomUUID());
			var sourceRL = createRaceLineupWithRace(source, raceA);

			setupStandardMerge(sourceId, targetId, source, target);
			when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceRL));
			lenient().when(raceLineupRepository.findByRaceIdAndDriverId(raceA.getId(), targetId))
					.thenReturn(Optional.empty());

			// when
			driverMergeService.merge(sourceId, targetId);

			// then
			assertThat(sourceRL.getDriver()).isEqualTo(target);
			verify(raceLineupRepository).save(sourceRL);
		}

		@Test
		void givenRaceResultConflict_whenMerge_thenSourceDeletedNotReassigned() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");
			var raceA = createRace(UUID.randomUUID());
			var sourceRR = createRaceResultWithRace(source, raceA);
			var targetRR = createRaceResultWithRace(target, raceA);

			setupStandardMerge(sourceId, targetId, source, target);
			when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceRR));
			when(raceResultRepository.findByRaceIdAndDriverId(raceA.getId(), targetId))
					.thenReturn(Optional.of(targetRR));

			// when
			driverMergeService.merge(sourceId, targetId);

			// then
			verify(raceResultRepository).delete(sourceRR);
			verify(raceResultRepository, never()).save(sourceRR);
		}

		@Test
		void givenRaceResultNoConflict_whenMerge_thenStillReassigned() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");
			var raceA = createRace(UUID.randomUUID());
			var sourceRR = createRaceResultWithRace(source, raceA);

			setupStandardMerge(sourceId, targetId, source, target);
			when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceRR));
			lenient().when(raceResultRepository.findByRaceIdAndDriverId(raceA.getId(), targetId))
					.thenReturn(Optional.empty());

			// when
			driverMergeService.merge(sourceId, targetId);

			// then
			assertThat(sourceRR.getDriver()).isEqualTo(target);
			verify(raceResultRepository).save(sourceRR);
		}

		@Test
		void givenMixedConflictsAndNonConflicts_whenMerge_thenCorrectReassignedAndDroppedCounts() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");

			var seasonConflict = createSeason(UUID.randomUUID(), "ConflictSeason");
			var seasonNoConflict = createSeason(UUID.randomUUID(), "NoConflictSeason");
			var sourceSD1 = createSeasonDriverWithSeason(source, seasonConflict);
			var sourceSD2 = createSeasonDriverWithSeason(source, seasonNoConflict);
			var targetSD1 = createSeasonDriverWithSeason(target, seasonConflict);

			var raceConflict = createRace(UUID.randomUUID());
			var raceNoConflict = createRace(UUID.randomUUID());
			var sourceRL1 = createRaceLineupWithRace(source, raceConflict);
			var sourceRL2 = createRaceLineupWithRace(source, raceNoConflict);
			var targetRL1 = createRaceLineupWithRace(target, raceConflict);

			var raceResultConflict = createRace(UUID.randomUUID());
			var raceResult2 = createRace(UUID.randomUUID());
			var raceResult3 = createRace(UUID.randomUUID());
			var sourceRR1 = createRaceResultWithRace(source, raceResultConflict);
			var sourceRR2 = createRaceResultWithRace(source, raceResult2);
			var sourceRR3 = createRaceResultWithRace(source, raceResult3);
			var targetRR1 = createRaceResultWithRace(target, raceResultConflict);

			setupStandardMerge(sourceId, targetId, source, target);
			when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceSD1, sourceSD2));
			when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonConflict.getId(), targetId))
					.thenReturn(Optional.of(targetSD1));
			when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonNoConflict.getId(), targetId))
					.thenReturn(Optional.empty());

			when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceRL1, sourceRL2));
			when(raceLineupRepository.findByRaceIdAndDriverId(raceConflict.getId(), targetId))
					.thenReturn(Optional.of(targetRL1));
			when(raceLineupRepository.findByRaceIdAndDriverId(raceNoConflict.getId(), targetId))
					.thenReturn(Optional.empty());

			when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceRR1, sourceRR2, sourceRR3));
			when(raceResultRepository.findByRaceIdAndDriverId(raceResultConflict.getId(), targetId))
					.thenReturn(Optional.of(targetRR1));
			when(raceResultRepository.findByRaceIdAndDriverId(raceResult2.getId(), targetId))
					.thenReturn(Optional.empty());
			when(raceResultRepository.findByRaceIdAndDriverId(raceResult3.getId(), targetId))
					.thenReturn(Optional.empty());

			// when
			var result = driverMergeService.merge(sourceId, targetId);

			// then
			assertThat(result.seasonDrivers()).isEqualTo(1);
			assertThat(result.seasonDriversDropped()).isEqualTo(1);
			assertThat(result.raceLineups()).isEqualTo(1);
			assertThat(result.raceLineupsDropped()).isEqualTo(1);
			assertThat(result.raceResults()).isEqualTo(2);
			assertThat(result.raceResultsDropped()).isEqualTo(1);
		}
	}

	@Nested
	class PreviewMergeTests {

		@Test
		void givenSourceEqualsTarget_whenPreviewMerge_thenThrowsBusinessRuleException() {
			// given
			var id = UUID.randomUUID();

			// when / then
			assertThatThrownBy(() -> driverMergeService.previewMerge(id, id))
					.isInstanceOf(BusinessRuleException.class)
					.hasMessageContaining("Cannot merge driver with itself");
		}

		@Test
		void givenNonExistentSource_whenPreviewMerge_thenThrowsEntityNotFoundException() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			when(driverRepository.findById(sourceId)).thenReturn(Optional.empty());

			// when / then
			assertThatThrownBy(() -> driverMergeService.previewMerge(sourceId, targetId))
					.isInstanceOf(EntityNotFoundException.class)
					.hasMessageContaining("Driver")
					.hasMessageContaining(sourceId.toString());
		}

		@Test
		void givenNonExistentTarget_whenPreviewMerge_thenThrowsEntityNotFoundException() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			when(driverRepository.findById(sourceId)).thenReturn(Optional.of(source));
			when(driverRepository.findById(targetId)).thenReturn(Optional.empty());

			// when / then
			assertThatThrownBy(() -> driverMergeService.previewMerge(sourceId, targetId))
					.isInstanceOf(EntityNotFoundException.class)
					.hasMessageContaining("Driver")
					.hasMessageContaining(targetId.toString());
		}

		@Test
		void givenMixedConflictsAcrossAllFkTables_whenPreviewMerge_thenReturnsCorrectCounts() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");

			var seasonConflict = createSeason(UUID.randomUUID(), "ConflictSeason");
			var seasonNoConflict = createSeason(UUID.randomUUID(), "NoConflictSeason");
			var sourceSD1 = createSeasonDriverWithSeason(source, seasonConflict);
			var sourceSD2 = createSeasonDriverWithSeason(source, seasonNoConflict);
			var targetSD1 = createSeasonDriverWithSeason(target, seasonConflict);

			var raceConflict = createRace(UUID.randomUUID());
			var raceNoConflict = createRace(UUID.randomUUID());
			var sourceRL1 = createRaceLineupWithRace(source, raceConflict);
			var sourceRL2 = createRaceLineupWithRace(source, raceNoConflict);
			var targetRL1 = createRaceLineupWithRace(target, raceConflict);

			var raceResultConflict = createRace(UUID.randomUUID());
			var raceResult2 = createRace(UUID.randomUUID());
			var raceResult3 = createRace(UUID.randomUUID());
			var sourceRR1 = createRaceResultWithRace(source, raceResultConflict);
			var sourceRR2 = createRaceResultWithRace(source, raceResult2);
			var sourceRR3 = createRaceResultWithRace(source, raceResult3);
			var targetRR1 = createRaceResultWithRace(target, raceResultConflict);

			var alias1 = createPsnAlias(source, "OldAlias1");
			var alias2 = createPsnAlias(source, "OldAlias2");

			when(driverRepository.findById(sourceId)).thenReturn(Optional.of(source));
			when(driverRepository.findById(targetId)).thenReturn(Optional.of(target));
			when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceSD1, sourceSD2));
			when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonConflict.getId(), targetId))
					.thenReturn(Optional.of(targetSD1));
			when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonNoConflict.getId(), targetId))
					.thenReturn(Optional.empty());
			when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceRL1, sourceRL2));
			when(raceLineupRepository.findByRaceIdAndDriverId(raceConflict.getId(), targetId))
					.thenReturn(Optional.of(targetRL1));
			when(raceLineupRepository.findByRaceIdAndDriverId(raceNoConflict.getId(), targetId))
					.thenReturn(Optional.empty());
			when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of(sourceRR1, sourceRR2, sourceRR3));
			when(raceResultRepository.findByRaceIdAndDriverId(raceResultConflict.getId(), targetId))
					.thenReturn(Optional.of(targetRR1));
			when(raceResultRepository.findByRaceIdAndDriverId(raceResult2.getId(), targetId))
					.thenReturn(Optional.empty());
			when(raceResultRepository.findByRaceIdAndDriverId(raceResult3.getId(), targetId))
					.thenReturn(Optional.empty());
			when(psnAliasRepository.findByDriverId(sourceId)).thenReturn(List.of(alias1, alias2));

			// when
			var preview = driverMergeService.previewMerge(sourceId, targetId);

			// then
			assertThat(preview.seasonDriversToReassign()).isEqualTo(1);
			assertThat(preview.seasonDriversDuplicate()).isEqualTo(1);
			assertThat(preview.raceLineupsToReassign()).isEqualTo(1);
			assertThat(preview.raceLineupsDuplicate()).isEqualTo(1);
			assertThat(preview.raceResultsToReassign()).isEqualTo(2);
			assertThat(preview.raceResultsDuplicate()).isEqualTo(1);
			assertThat(preview.psnAliasesToReassign()).isEqualTo(2);
			assertThat(preview.totalToReassign()).isEqualTo(6);
			assertThat(preview.totalDuplicates()).isEqualTo(3);
		}

		@Test
		void givenNoReferences_whenPreviewMerge_thenReturnsAllZeroCounts() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");

			when(driverRepository.findById(sourceId)).thenReturn(Optional.of(source));
			when(driverRepository.findById(targetId)).thenReturn(Optional.of(target));
			when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of());
			when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of());
			when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of());
			when(psnAliasRepository.findByDriverId(sourceId)).thenReturn(List.of());

			// when
			var preview = driverMergeService.previewMerge(sourceId, targetId);

			// then
			assertThat(preview.seasonDriversToReassign()).isZero();
			assertThat(preview.seasonDriversDuplicate()).isZero();
			assertThat(preview.raceLineupsToReassign()).isZero();
			assertThat(preview.raceLineupsDuplicate()).isZero();
			assertThat(preview.raceResultsToReassign()).isZero();
			assertThat(preview.raceResultsDuplicate()).isZero();
			assertThat(preview.psnAliasesToReassign()).isZero();
			assertThat(preview.totalToReassign()).isZero();
			assertThat(preview.totalDuplicates()).isZero();
		}

		@Test
		void givenValidPreview_whenPreviewMerge_thenNoMutationsExecuted() {
			// given
			var sourceId = UUID.randomUUID();
			var targetId = UUID.randomUUID();
			var source = createDriver(sourceId, "SourcePSN");
			var target = createDriver(targetId, "TargetPSN");

			when(driverRepository.findById(sourceId)).thenReturn(Optional.of(source));
			when(driverRepository.findById(targetId)).thenReturn(Optional.of(target));
			when(seasonDriverRepository.findByDriverId(sourceId)).thenReturn(List.of());
			when(raceLineupRepository.findByDriverId(sourceId)).thenReturn(List.of());
			when(raceResultRepository.findByDriverId(sourceId)).thenReturn(List.of());
			when(psnAliasRepository.findByDriverId(sourceId)).thenReturn(List.of());

			// when
			driverMergeService.previewMerge(sourceId, targetId);

			// then
			verify(seasonDriverRepository, never()).save(any());
			verify(seasonDriverRepository, never()).delete(any(SeasonDriver.class));
			verify(raceLineupRepository, never()).save(any());
			verify(raceLineupRepository, never()).delete(any(RaceLineup.class));
			verify(raceResultRepository, never()).save(any());
			verify(raceResultRepository, never()).delete(any(RaceResult.class));
			verify(psnAliasRepository, never()).save(any());
			verify(driverRepository, never()).delete(any(Driver.class));
		}
	}
}
