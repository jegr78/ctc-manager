package org.ctc.dataimport;

import org.ctc.domain.model.*;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock private DriverMatchingService driverMatchingService;
    @Mock private DriverRepository driverRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private MatchdayRepository matchdayRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private PlayoffMatchupRepository playoffMatchupRepository;
    @Mock private PlayoffRepository playoffRepository;
    @Mock private ScoringService scoringService;
    @Mock private RaceLineupRepository raceLineupRepository;

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
        season.setRaceScoring(raceScoring);

        matchday = new Matchday(season, "Matchday 1", 1);
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

    // --- checkDuplicate ---

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
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of());

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

    // --- getPlayoffMatchups ---

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

    // --- parseAndPreview edge cases ---

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

    // --- getMatchdayLabel ---

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

    // --- resolveDriver edge case: unconfirmed fuzzy match ---

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
}
