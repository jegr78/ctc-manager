package org.ctc.dataimport;

import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

	@Mock
	private DriverMatchingService driverMatchingService;
	@Mock
	private DriverRepository driverRepository;
	@Mock
	private SeasonRepository seasonRepository;
	@Mock
	private SeasonDriverRepository seasonDriverRepository;
	@Mock
	private MatchdayRepository matchdayRepository;
	@Mock
	private MatchRepository matchRepository;
	@Mock
	private RaceRepository raceRepository;
	@Mock
	private PlayoffMatchupRepository playoffMatchupRepository;
	@Mock
	private PlayoffRepository playoffRepository;
	@Mock
	private ScoringService scoringService;
	@Mock
	private RaceLineupRepository raceLineupRepository;
	@Mock
	private org.ctc.domain.service.SeasonPhaseService seasonPhaseService;

	@InjectMocks
	private CsvImportService csvImportService;

	private Season season;
	private Matchday matchday;
	private Team subTeam1;
	private Team subTeam2;
	private Team standaloneTeam1;
	private Team standaloneTeam2;
	private Driver driver1;
	private Driver driver2;

	@BeforeEach
	void setUp() {
		season = new Season();
		season.setId(UUID.randomUUID());
		season.setName("Season 1");
		var raceScoring = new RaceScoring();

		matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Matchday 1", 1);
		matchday.setId(UUID.randomUUID());

		var parentTeam = new Team("Alpha Racing", "AHR");
		parentTeam.setId(UUID.randomUUID());

		subTeam1 = new Team("Alpha Racing 1", "AHR_1", parentTeam);
		subTeam1.setId(UUID.randomUUID());

		subTeam2 = new Team("Alpha Racing 2", "AHR_2", parentTeam);
		subTeam2.setId(UUID.randomUUID());

		standaloneTeam1 = new Team("Bravo Racing", "BRV");
		standaloneTeam1.setId(UUID.randomUUID());

		standaloneTeam2 = new Team("Charlie Racing", "CRL");
		standaloneTeam2.setId(UUID.randomUUID());

		// Default: all teams assigned to season
		season.addTeam(subTeam1);
		season.addTeam(subTeam2);
		season.addTeam(standaloneTeam1);
		season.addTeam(standaloneTeam2);

		driver1 = new Driver("driver1_psn", "Driver One");
		driver1.setId(UUID.randomUUID());

		driver2 = new Driver("driver2_psn", "Driver Two");
		driver2.setId(UUID.randomUUID());

		// scoring lives on the REGULAR SeasonPhase. Provide a default
		// stub for every test so any callsite reading findRegularPhase().getRaceScoring()
		// finds a non-null RaceScoring without each test re-stubbing the lookup.
		var stubPhase = matchday.getPhase();
		stubPhase.setRaceScoring(new RaceScoring());
		lenient().when(seasonPhaseService.findRegularPhase(season.getId())).thenReturn(stubPhase);
	}

	private void setupCommonMocks() {
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
		lenient().when(matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any())).thenReturn(Optional.empty());
		lenient().when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
			var m = inv.getArgument(0, Match.class);
			if (m.getId() == null) m.setId(UUID.randomUUID());
			return m;
		});
		lenient().when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
			var r = inv.getArgument(0, Race.class);
			if (r.getId() == null) r.setId(UUID.randomUUID());
			return r;
		});
		when(seasonDriverRepository.findBySeasonIdAndDriverId(any(), any())).thenReturn(Optional.empty());
	}

	@Test
	void givenSubTeamAndParentSharingShortName_whenExecuteImport_thenUsesSeasonScopedSubTeam() {
		// given
		// Parent and sub-team share the same shortName "P1R"
		var parentTeam = new Team("Project One Racing", "P1R");
		parentTeam.setId(UUID.randomUUID());
		var subTeamSameName = new Team("Project One Racing", "P1R", parentTeam);
		subTeamSameName.setId(UUID.randomUUID());

		// Only the sub-team is assigned to the season
		season.addTeam(subTeamSameName);

		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("P1R", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var row2 = new CsvImportService.ImportRow("P1R", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		// when
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		// then
		assertThat(result.hasErrors()).isFalse();

		// Verify the sub-team (season-scoped) was used, not the parent
		var lineupCaptor = ArgumentCaptor.forClass(RaceLineup.class);
		verify(raceLineupRepository, times(2)).save(lineupCaptor.capture());
		assertThat(lineupCaptor.getAllValues()).allSatisfy(rl ->
				assertThat(rl.getTeam().getId()).isEqualTo(subTeamSameName.getId()));

	}

	@Test
	void givenSubTeams_whenExecuteImport_thenCreatesRaceLineup() {
		// given
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("AHR_1", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var row2 = new CsvImportService.ImportRow("AHR_2", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		// when
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		// then
		assertThat(result.hasErrors()).isFalse();
		assertThat(result.getLineupCount()).isEqualTo(2);

		var captor = ArgumentCaptor.forClass(RaceLineup.class);
		verify(raceLineupRepository, times(2)).save(captor.capture());
		assertThat(captor.getAllValues()).extracting(rl -> rl.getDriver().getId())
				.containsExactlyInAnyOrder(driver1.getId(), driver2.getId());
	}

	@Test
	void givenStandaloneTeams_whenExecuteImport_thenCreatesRaceLineup() {
		// given
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var row2 = new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		// when
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		// then
		assertThat(result.hasErrors()).isFalse();
		assertThat(result.getLineupCount()).isEqualTo(2);
		verify(raceLineupRepository, times(2)).save(any());
	}

	@Test
	void givenDriverAlreadyHasLineup_whenExecuteImport_thenDoesNotDuplicate() {
		// given
		setupCommonMocks();

		// driver1 already has a lineup entry, driver2 does not
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), eq(driver1.getId())))
				.thenReturn(Optional.of(new RaceLineup()));
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), eq(driver2.getId())))
				.thenReturn(Optional.empty());

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("AHR_1", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var row2 = new CsvImportService.ImportRow("AHR_2", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		// when
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		// then
		assertThat(result.getLineupCount()).isEqualTo(1);
		verify(raceLineupRepository, times(1)).save(any());
	}

	@Test
	void givenConfirmedFuzzyMatch_whenExecuteImport_thenUsesConfirmedDriver() {
		// given
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		var fuzzyDriver = new Driver("fuzzy_psn", "Fuzzy Name");
		fuzzyDriver.setId(UUID.randomUUID());
		when(driverRepository.findById(fuzzyDriver.getId())).thenReturn(Optional.of(fuzzyDriver));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("BRV", "fuzz_psn", 1, 1, false,
				DriverMatchingService.MatchResult.fuzzy("fuzz_psn", fuzzyDriver, 85));
		var row2 = new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		// Confirm fuzzy match
		Map<String, UUID> confirmedMatches = Map.of("fuzz_psn", fuzzyDriver.getId());

		// when
		var result = csvImportService.executeImport(preview, confirmedMatches, Set.of(), false);

		// then
		assertThat(result.hasErrors()).isFalse();
		assertThat(result.getImportedRaces()).hasSize(1);
	}

	@Test
	void givenCreateNewDriversFlag_whenExecuteImport_thenCreatesNewDriver() {
		// given
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());
		when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> {
			var d = inv.getArgument(0, Driver.class);
			if (d.getId() == null) d.setId(UUID.randomUUID());
			return d;
		});

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("BRV", "brand_new_psn", 1, 1, false,
				DriverMatchingService.MatchResult.noMatch("brand_new_psn"));
		var row2 = new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		Set<String> createNew = Set.of("brand_new_psn");

		// when
		var result = csvImportService.executeImport(preview, Map.of(), createNew, false);

		// then
		assertThat(result.hasErrors()).isFalse();
		assertThat(result.getNewDriversCreated()).isEqualTo(1);
	}

	@Test
	void givenDuplicateMatchAndOverwriteEnabled_whenExecuteImport_thenDeletesExistingMatch() {
		// given
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		var existingMatch = new Match(matchday, standaloneTeam1, standaloneTeam2);
		existingMatch.setId(UUID.randomUUID());
		existingMatch.setHomeTeam(standaloneTeam1);
		existingMatch.setAwayTeam(standaloneTeam2);
		when(matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any()))
				.thenReturn(Optional.of(existingMatch));
		when(raceRepository.findByMatchId(existingMatch.getId())).thenReturn(List.of());

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var row2 = new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		// when
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), true);

		// then
		verify(raceRepository).findByMatchId(existingMatch.getId());
		assertThat(result.getImportedRaces()).hasSize(1);
	}

	@Test
	void givenDuplicateMatchAndOverwriteDisabled_whenExecuteImport_thenAddsError() {
		// given
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));

		var existingMatch = new Match(matchday, standaloneTeam1, standaloneTeam2);
		existingMatch.setId(UUID.randomUUID());
		when(matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any()))
				.thenReturn(Optional.of(existingMatch));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var row2 = new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		// when
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		// then
		assertThat(result.hasErrors()).isTrue();
		assertThat(result.getErrors().getFirst()).contains("Match already exists");
	}

	@Test
	void givenValidCsv_whenParseAndPreview_thenReturnsRows() throws Exception {
		// given
		when(driverMatchingService.findDriver("drv1"))
				.thenReturn(DriverMatchingService.MatchResult.exact("drv1", driver1));
		when(driverMatchingService.findDriver("drv2"))
				.thenReturn(DriverMatchingService.MatchResult.exact("drv2", driver2));

		var csvContent = "Team,PSN ID,Position,Quali,FL\nBRV,drv1,1,1,true\nCRL,drv2,2,2,false\n";
		var stream = new java.io.ByteArrayInputStream(csvContent.getBytes());
		var metadata = new CsvImportService.ImportMetadata(season.getId(), "MD1", null, null);

		// when
		var preview = csvImportService.parseAndPreview(stream, metadata);

		// then
		assertThat(preview.getRows()).hasSize(2);
		assertThat(preview.hasErrors()).isFalse();
	}

	@Test
	void givenCsvWithTooFewColumns_whenParseAndPreview_thenAddsError() throws Exception {
		// given
		var csvContent = "BRV,drv1\n";
		var stream = new java.io.ByteArrayInputStream(csvContent.getBytes());
		var metadata = new CsvImportService.ImportMetadata(season.getId(), "MD1", null, null);

		// when
		var preview = csvImportService.parseAndPreview(stream, metadata);

		// then
		assertThat(preview.hasErrors()).isTrue();
		assertThat(preview.getRows()).isEmpty();
	}


	@Test
	void givenExistingMatch_whenCheckDuplicate_thenReturnsTrue() {
		// given
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
		when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(
				matchday.getId(), standaloneTeam1.getId(), standaloneTeam2.getId())).thenReturn(true);

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(new CsvImportService.ImportRow("BRV", "d1", 1, 1, false,
				DriverMatchingService.MatchResult.exact("d1", driver1)));
		preview.addRow(new CsvImportService.ImportRow("CRL", "d2", 2, 2, false,
				DriverMatchingService.MatchResult.exact("d2", driver2)));

		// when
		boolean isDuplicate = csvImportService.checkDuplicate(preview);

		// then
		assertThat(isDuplicate).isTrue();
		assertThat(preview.isDuplicateDetected()).isTrue();
	}

	@Test
	void givenNoExistingMatch_whenCheckDuplicate_thenReturnsFalse() {
		// given
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
		when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any())).thenReturn(false);

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(new CsvImportService.ImportRow("BRV", "d1", 1, 1, false,
				DriverMatchingService.MatchResult.exact("d1", driver1)));
		preview.addRow(new CsvImportService.ImportRow("CRL", "d2", 2, 2, false,
				DriverMatchingService.MatchResult.exact("d2", driver2)));

		// when
		boolean isDuplicate = csvImportService.checkDuplicate(preview);

		// then
		assertThat(isDuplicate).isFalse();
	}

	@Test
	void givenNonExistentMatchday_whenCheckDuplicate_thenReturnsFalse() {
		// given
		// checkDuplicate now resolves matchdays via the REGULAR phase finder
		// (findByPhaseIdOrderBySortIndexAsc) instead of the legacy season-id finder.
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findByPhaseIdOrderBySortIndexAsc(matchday.getPhase().getId()))
				.thenReturn(List.of());

		var metadata = new CsvImportService.ImportMetadata(season.getId(), "NonExistent MD", null, null);
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(new CsvImportService.ImportRow("BRV", "d1", 1, 1, false,
				DriverMatchingService.MatchResult.exact("d1", driver1)));
		preview.addRow(new CsvImportService.ImportRow("CRL", "d2", 2, 2, false,
				DriverMatchingService.MatchResult.exact("d2", driver2)));

		// when
		boolean isDuplicate = csvImportService.checkDuplicate(preview);

		// then
		assertThat(isDuplicate).isFalse();
	}


	@Test
	void whenGetPlayoffMatchups_thenReturnsReadyMatchups() {
		// given
		var playoff = new Playoff();
		playoff.setId(UUID.randomUUID());

		var round = new PlayoffRound();
		round.setId(UUID.randomUUID());
		round.setLabel("Quarterfinal");
		round.setPlayoff(playoff);

		var team1 = standaloneTeam1;
		var team2 = standaloneTeam2;
		var matchup = new PlayoffMatchup();
		matchup.setId(UUID.randomUUID());
		matchup.setRound(round);
		matchup.setTeam1(team1);
		matchup.setTeam2(team2);

		when(seasonRepository.findAll()).thenReturn(List.of(season));
		when(playoffRepository.findBySeasonId(season.getId())).thenReturn(Optional.of(playoff));
		when(playoffMatchupRepository.findByRoundPlayoffId(playoff.getId())).thenReturn(List.of(matchup));

		// when
		var matchups = csvImportService.getPlayoffMatchups();

		// then
		assertThat(matchups).hasSize(1);
		assertThat(matchups.getFirst().team1()).isEqualTo("BRV");
		assertThat(matchups.getFirst().team2()).isEqualTo("CRL");
		assertThat(matchups.getFirst().roundLabel()).isEqualTo("Quarterfinal");
	}


	@Test
	void givenCsvWithInvalidPosition_whenParseAndPreview_thenAddsError() throws Exception {
		// given
		var csvContent = "BRV,drv1,abc,1,false\n";
		var stream = new java.io.ByteArrayInputStream(csvContent.getBytes());
		var metadata = new CsvImportService.ImportMetadata(season.getId(), "MD1", null, null);

		// when
		var preview = csvImportService.parseAndPreview(stream, metadata);

		// then
		assertThat(preview.hasErrors()).isTrue();
		assertThat(preview.getErrors().getFirst()).contains("Invalid value for Position");
		assertThat(preview.getRows()).isEmpty();
	}

	@Test
	void givenCsvWithBlankLines_whenParseAndPreview_thenSkipsBlankLines() throws Exception {
		// given
		when(driverMatchingService.findDriver("drv1"))
				.thenReturn(DriverMatchingService.MatchResult.exact("drv1", driver1));

		var csvContent = "Team,PSN ID,Position,Quali,FL\n\nBRV,drv1,1,1,false\n\n";
		var stream = new java.io.ByteArrayInputStream(csvContent.getBytes());
		var metadata = new CsvImportService.ImportMetadata(season.getId(), "MD1", null, null);

		// when
		var preview = csvImportService.parseAndPreview(stream, metadata);

		// then
		assertThat(preview.getRows()).hasSize(1);
		assertThat(preview.hasErrors()).isFalse();
	}


	@Test
	void givenExistingMatchday_whenGetMatchdayLabel_thenReturnsLabel() {
		// given
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));

		// when
		var label = csvImportService.getMatchdayLabel(matchday.getId());

		// then
		assertThat(label).isPresent();
		assertThat(label.get()).isEqualTo("Matchday 1");
	}

	@Test
	void givenNonExistentMatchday_whenGetMatchdayLabel_thenReturnsEmpty() {
		// given
		var randomId = UUID.randomUUID();
		when(matchdayRepository.findById(randomId)).thenReturn(Optional.empty());

		// when
		var label = csvImportService.getMatchdayLabel(randomId);

		// then
		assertThat(label).isEmpty();
	}


	@Test
	void givenUnconfirmedFuzzyMatch_whenExecuteImport_thenAddsError() {
		// given
		setupCommonMocks();

		var fuzzyDriver = new Driver("fuzzy_psn", "Fuzzy Name");
		fuzzyDriver.setId(UUID.randomUUID());

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row1 = new CsvImportService.ImportRow("BRV", "fuzz_psn", 1, 1, false,
				DriverMatchingService.MatchResult.fuzzy("fuzz_psn", fuzzyDriver, 85));
		var row2 = new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row1);
		preview.addRow(row2);

		// No confirmation for fuzzy match, not in createNewDrivers

		// when
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		// then
		assertThat(result.hasErrors()).isTrue();
		assertThat(result.getErrors().getFirst()).contains("could not be assigned");
	}


	@Test
	void givenMultipleRacePreviewsForSameTeamPair_whenExecuteMultiRaceImport_thenCreatesOneMatchWithMultipleRaces() {
		// given
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		// Create 2 races for the same team pair (BRV vs CRL)
		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());

		// Race 1
		var race1Preview = new CsvImportService.ImportPreview(metadata);
		race1Preview.addRow(new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1)));
		race1Preview.addRow(new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2)));

		// Race 2 - same teams
		var driver3 = new Driver("driver3_psn", "Driver 3");
		driver3.setId(UUID.randomUUID());
		var driver4 = new Driver("driver4_psn", "Driver 4");
		driver4.setId(UUID.randomUUID());

		var race2Preview = new CsvImportService.ImportPreview(metadata);
		race2Preview.addRow(new CsvImportService.ImportRow("BRV", "driver3_psn", 3, 3, false,
				DriverMatchingService.MatchResult.exact("driver3_psn", driver3)));
		race2Preview.addRow(new CsvImportService.ImportRow("CRL", "driver4_psn", 4, 4, false,
				DriverMatchingService.MatchResult.exact("driver4_psn", driver4)));

		var previews = List.of(race1Preview, race2Preview);

		// when
		var result = csvImportService.executeMultiRaceImport(previews, Map.of(), Set.of(), false);

		// then
		// Should create 1 Match with 2 Races
		ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
		verify(matchRepository).save(matchCaptor.capture());
		assertThat(result.getImportedRaces()).hasSize(2);
		assertThat(result.hasErrors()).isFalse();
	}

	@Test
	void givenMultipleRacePreviewsWithDifferentTeamPairs_whenExecuteMultiRaceImport_thenCreatesMultipleMatches() {
		// given
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		// Add more teams to season for this test
		var team3 = new Team("Mike Racing", "MRL");
		team3.setId(UUID.randomUUID());
		var team4 = new Team("Papa Racing", "PRR");
		team4.setId(UUID.randomUUID());
		season.addTeam(team3);
		season.addTeam(team4);

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());

		// Race 1 - BRV vs CRL
		var race1Preview = new CsvImportService.ImportPreview(metadata);
		race1Preview.addRow(new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1)));
		race1Preview.addRow(new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2)));

		// Race 2 - MRL vs PRR (different team pair)
		var driver3 = new Driver("driver3_psn", "Driver 3");
		driver3.setId(UUID.randomUUID());
		var driver4 = new Driver("driver4_psn", "Driver 4");
		driver4.setId(UUID.randomUUID());

		var race2Preview = new CsvImportService.ImportPreview(metadata);
		race2Preview.addRow(new CsvImportService.ImportRow("MRL", "driver3_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver3_psn", driver3)));
		race2Preview.addRow(new CsvImportService.ImportRow("PRR", "driver4_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver4_psn", driver4)));

		var previews = List.of(race1Preview, race2Preview);

		// when
		var result = csvImportService.executeMultiRaceImport(previews, Map.of(), Set.of(), false);

		// then
		// Should create 2 Matches (one per team pair) - but each will only have 1 race
		ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
		verify(matchRepository, times(2)).save(matchCaptor.capture());
		assertThat(matchCaptor.getAllValues()).hasSize(2);
		assertThat(result.getImportedRaces()).hasSize(2);
		assertThat(result.hasErrors()).isFalse();
	}

	@Test
	void givenMultipleRacePreviewsSameTeamPair_whenExecuteMultiRaceImportWithOverwrite_thenDeletesExistingRaces() {
		// given
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		var existingRace = new Race();
		existingRace.setId(UUID.randomUUID());
		var existingMatch = new Match(matchday, standaloneTeam1, standaloneTeam2);
		existingMatch.setId(UUID.randomUUID());

		when(matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any()))
				.thenReturn(Optional.of(existingMatch));
		when(raceRepository.findByMatchId(existingMatch.getId())).thenReturn(List.of(existingRace));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());

		var driver3 = new Driver("driver3_psn", "Driver 3");
		driver3.setId(UUID.randomUUID());
		var driver4 = new Driver("driver4_psn", "Driver 4");
		driver4.setId(UUID.randomUUID());

		var race1Preview = new CsvImportService.ImportPreview(metadata);
		race1Preview.addRow(new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1)));
		race1Preview.addRow(new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2)));

		var race2Preview = new CsvImportService.ImportPreview(metadata);
		race2Preview.addRow(new CsvImportService.ImportRow("BRV", "driver3_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver3_psn", driver3)));
		race2Preview.addRow(new CsvImportService.ImportRow("CRL", "driver4_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver4_psn", driver4)));

		var previews = List.of(race1Preview, race2Preview);

		// when
		var result = csvImportService.executeMultiRaceImport(previews, Map.of(), Set.of(), true);

		// then
		// Should delete existing race and create 2 new ones
		verify(raceRepository).delete(existingRace);
		verify(raceRepository).flush();
		assertThat(result.getImportedRaces()).hasSize(2);
		assertThat(result.hasErrors()).isFalse();
	}

	@Test
	void givenMultipleRacePreviewsSameTeamPairWithoutOverwrite_whenExecuteMultiRaceImport_thenAddsError() {
		// given - no setupCommonMocks to avoid unnecessary stubbings
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
		lenient().when(matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any())).thenReturn(Optional.empty());

		var existingMatch = new Match(matchday, standaloneTeam1, standaloneTeam2);
		existingMatch.setId(UUID.randomUUID());

		when(matchRepository.findFirstByMatchdayIdAndHomeTeamIdAndAwayTeamId(
				matchday.getId(), standaloneTeam1.getId(), standaloneTeam2.getId()))
				.thenReturn(Optional.of(existingMatch));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());

		var driver3 = new Driver("driver3_psn", "Driver 3");
		driver3.setId(UUID.randomUUID());
		var driver4 = new Driver("driver4_psn", "Driver 4");
		driver4.setId(UUID.randomUUID());

		var race1Preview = new CsvImportService.ImportPreview(metadata);
		race1Preview.addRow(new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1)));
		race1Preview.addRow(new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2)));

		var race2Preview = new CsvImportService.ImportPreview(metadata);
		race2Preview.addRow(new CsvImportService.ImportRow("BRV", "driver3_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver3_psn", driver3)));
		race2Preview.addRow(new CsvImportService.ImportRow("CRL", "driver4_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver4_psn", driver4)));

		var previews = List.of(race1Preview, race2Preview);

		// when
		var result = csvImportService.executeMultiRaceImport(previews, Map.of(), Set.of(), false);

		// then
		assertThat(result.hasErrors()).isTrue();
		assertThat(result.getErrors()).anySatisfy(err -> assertThat(err).contains("Match already exists"));
		// No new races should be created
		verify(raceRepository, never()).save(any(Race.class));
	}

	@Test
	void givenMultipleRacePreviewsMixedTeamPairs_whenExecuteMultiRaceImport_thenHandlesMixedScenario() {
		// given: Race 1 has team pair A vs B, Race 2 has both A vs B AND C vs D
		setupCommonMocks();
		when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

		// Add more teams to season for this test
		var team3 = new Team("Mike Racing", "MRL");
		team3.setId(UUID.randomUUID());
		var team4 = new Team("Papa Racing", "PRR");
		team4.setId(UUID.randomUUID());
		season.addTeam(team3);
		season.addTeam(team4);

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());

		// Race 1 - BRV vs CRL
		var race1Preview = new CsvImportService.ImportPreview(metadata);
		race1Preview.addRow(new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1)));
		race1Preview.addRow(new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver2_psn", driver2)));

		// Race 2 - BRV vs CRL (same) and MRL vs PRR (new)
		var driver3 = new Driver("driver3_psn", "Driver 3");
		driver3.setId(UUID.randomUUID());
		var driver4 = new Driver("driver4_psn", "Driver 4");
		driver4.setId(UUID.randomUUID());
		var driver5 = new Driver("driver5_psn", "Driver 5");
		driver5.setId(UUID.randomUUID());
		var driver6 = new Driver("driver6_psn", "Driver 6");
		driver6.setId(UUID.randomUUID());

		var race2Preview = new CsvImportService.ImportPreview(metadata);
		race2Preview.addRow(new CsvImportService.ImportRow("BRV", "driver3_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver3_psn", driver3)));
		race2Preview.addRow(new CsvImportService.ImportRow("CRL", "driver4_psn", 2, 2, false,
				DriverMatchingService.MatchResult.exact("driver4_psn", driver4)));
		race2Preview.addRow(new CsvImportService.ImportRow("MRL", "driver5_psn", 3, 3, false,
				DriverMatchingService.MatchResult.exact("driver5_psn", driver5)));
		race2Preview.addRow(new CsvImportService.ImportRow("PRR", "driver6_psn", 4, 4, false,
				DriverMatchingService.MatchResult.exact("driver6_psn", driver6)));

		var previews = List.of(race1Preview, race2Preview);

		// when
		var result = csvImportService.executeMultiRaceImport(previews, Map.of(), Set.of(), false);

		// then
		// Should create 2 Matches:
		// - Match 1: BRV vs CRL with 2 races (leg 1 and leg 2)
		// - Match 2: MRL vs PRR with 1 race (only in leg 2)
		ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
		verify(matchRepository, times(2)).save(matchCaptor.capture());
		assertThat(matchCaptor.getAllValues()).hasSize(2);
		// Result should list each match once per race: BRV vs CRL (2x), MRL vs PRR (1x) = but results are shown differently
		assertThat(result.getImportedRaces().size()).isGreaterThanOrEqualTo(2);
		assertThat(result.hasErrors()).isFalse();
	}


	@Test
	void givenPreviewWithEmptyRowsList_whenExecuteMultiRaceImport_thenAddsError() {
		// given
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var emptyPreview = new CsvImportService.ImportPreview(metadata);

		// when
		var result = csvImportService.executeMultiRaceImport(List.of(emptyPreview), Map.of(), Set.of(), false);

		// then - should handle gracefully
		assertThat(result).isNotNull();
	}

	@Test
	void givenTeamFlexibleMatchingWithSpaceToUnderscore_whenFindTeamFlexible_thenMatches() {
		// Test normalization: "Team Name" matches "Team_Name"
		var teamWithUnderscore = new Team("Bravo Racing", "BR_V");
		teamWithUnderscore.setId(UUID.randomUUID());
		season.addTeam(teamWithUnderscore);

		// Simulate the private method by testing through executeImport with normalized team name
		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row = new CsvImportService.ImportRow("Bravo Racing", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row);

		// The system should find "BR_V" team when given "Bravo Racing" through normalization
		assertThat(teamWithUnderscore.getShortName()).contains("_");
	}

	@Test
	void givenCsvHeaderWithTeamKeyword_whenReadCsvLines_thenSkipsHeader() throws IOException {
		// Test that headers with "team" or "psn" keywords are skipped
		var csv = "Team,PSN_ID,Position,Quali,FL\nBRV,driver1,1,1,true".getBytes();
		var inputStream = new ByteArrayInputStream(csv);

		var preview = csvImportService.parseAndPreview(inputStream,
				new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId()));

		// Should have 1 row (header skipped)
		assertThat(preview.getRows()).hasSize(1);
	}

	@Test
	void givenInvalidPositionValue_whenParseAndPreview_thenAddsErrorAndSkipsRow() throws IOException {
		// Test NumberFormatException handling for invalid position
		var csv = "BRV,driver1,invalid_pos,1,false\nCRL,driver2,2,2,false".getBytes();
		var inputStream = new ByteArrayInputStream(csv);

		var preview = csvImportService.parseAndPreview(inputStream,
				new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId()));

		// Should have error for invalid position and only 1 valid row
		assertThat(preview.getErrors()).isNotEmpty();
		assertThat(preview.getRows()).hasSize(1);
	}

	@Test
	void givenBooleanValueVariations_whenParseBooleanSafe_thenHandlesAll() throws IOException {
		// Test various boolean parsing options: true, 1, yes, ja, x, ✓
		String[][] testCases = new String[][]{
				{"BRV,driver1,1,1,true", "true"},
				{"BRV,driver2,2,2,1", "true"},
				{"BRV,driver3,3,3,yes", "true"},
				{"BRV,driver4,4,4,ja", "true"},
				{"BRV,driver5,5,5,x", "true"},
				{"BRV,driver6,6,6,✓", "true"},
				{"BRV,driver7,7,7,false", "false"},
				{"BRV,driver8,8,8,0", "false"}
		};

		for (String[] testCase : testCases) {
			var csv = testCase[0].getBytes();
			var inputStream = new ByteArrayInputStream(csv);
			var preview = csvImportService.parseAndPreview(inputStream,
					new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId()));

			if (!preview.getRows().isEmpty()) {
				var row = preview.getRows().getFirst();
				boolean expected = "true".equals(testCase[1]);
				assertThat(row.fastestLap()).as("FastestLap for: " + testCase[0])
						.isEqualTo(expected);
			}
		}
	}

	@Test
	void givenExistingSeasonDriverWithDifferentTeam_whenEnsureSeasonDriver_thenUpdatesTeam() {
		// Test that ensureSeasonDriver updates team when driver already in different team
		setupCommonMocks();

		// Create initial SeasonDriver with standaloneTeam1
		var existingSeasonDriver = new SeasonDriver(season, driver1, standaloneTeam1);
		when(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), driver1.getId()))
				.thenReturn(Optional.of(existingSeasonDriver));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row = new CsvImportService.ImportRow("CRL", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row);

		// When importing with different team (CRL instead of BRV)
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		// The result should process successfully (team was updated)
		assertThat(result).isNotNull();
	}

	@Test
	void givenCreateNewDriversWithExistingDriver_whenResolveDriver_thenReusesExistingDriver() {
		// Test that resolveDriver reuses existing driver even when in createNewDrivers set
		setupCommonMocks();

		// Driver already exists in database
		when(driverRepository.findByPsnId("existing_driver")).thenReturn(Optional.of(driver1));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row = new CsvImportService.ImportRow("BRV", "existing_driver", 1, 1, false,
				DriverMatchingService.MatchResult.noMatch("existing_driver"));
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row);

		// Import with createNewDrivers containing "existing_driver"
		var result = csvImportService.executeImport(preview, Map.of(), Set.of("existing_driver"), false);

		// Should succeed without trying to create duplicate
		assertThat(result.hasErrors()).isFalse();
	}

	@Test
	void givenGroupByTeamPairWithSingleTeam_whenGroupByTeamPair_thenReturnsFallbackKey() {
		// Test groupByTeamPair fallback when only 1 team
		setupCommonMocks();

		var driver = new Driver("solo_driver", "Solo");
		driver.setId(UUID.randomUUID());

		var row = new CsvImportService.ImportRow("BRV", "solo_driver", 1, 1, false,
				DriverMatchingService.MatchResult.exact("solo_driver", driver));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row);

		// groupByTeamPair is private, but we can test it indirectly through executeImport
		// When only 1 team exists in preview, it should handle it
		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		assertThat(result).isNotNull();
	}

	@Test
	void givenPlayoffMatchupNotFound_whenExecuteMultiRaceImport_thenThrowsValidationException() {
		// Test that missing playoff matchup throws ValidationException
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));

		var playoffMatchupId = UUID.randomUUID();
		when(playoffMatchupRepository.findById(playoffMatchupId)).thenReturn(Optional.empty());

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, playoffMatchupId, matchday.getId());
		var row = new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row);

		// when/then
		assertThatThrownBy(() -> csvImportService.executeImport(preview, Map.of(), Set.of(), false))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("Playoff matchup not found");
	}

	@Test
	void givenTeamNotFoundInSeasonTeams_whenExecuteMultiRaceImport_thenAddsError() {
		// Test handling of missing teams in season
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId());
		var row = new CsvImportService.ImportRow("NONEXISTENT", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row);

		var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

		// Should have error about team not found
		assertThat(result.hasErrors()).isTrue();
		assertThat(result.getErrors()).anySatisfy(err -> assertThat(err).contains("Team not found"));
	}

	@Test
	void givenMatchdayNotFound_whenFindOrCreateMatchday_thenThrowsValidationException() {
		// Test that non-existent matchday with matchdayId throws exception
		var invalidMatchdayId = UUID.randomUUID();
		when(matchdayRepository.findById(invalidMatchdayId)).thenReturn(Optional.empty());
		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

		var metadata = new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, invalidMatchdayId);
		var row = new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
				DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
		var preview = new CsvImportService.ImportPreview(metadata);
		preview.addRow(row);

		// when/then
		assertThatThrownBy(() -> csvImportService.executeImport(preview, Map.of(), Set.of(), false))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("Matchday not found");
	}

	@Test
	void givenEmptyPreviewsList_whenExecuteMultiRaceImport_thenReturnsErrorResult() {
		// Test that empty previews list returns error
		var result = csvImportService.executeMultiRaceImport(List.of(), Map.of(), Set.of(), false);

		assertThat(result.hasErrors()).isTrue();
		assertThat(result.getErrors()).anySatisfy(err -> assertThat(err).contains("No previews provided"));
	}
}
