package de.ctc.dataimport;

import de.ctc.domain.model.*;
import de.ctc.domain.model.Match;
import de.ctc.domain.repository.*;
import de.ctc.domain.service.ScoringService;
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
    @Mock private TeamRepository teamRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private MatchdayRepository matchdayRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private PlayoffMatchupRepository playoffMatchupRepository;
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

        driver1 = new Driver("driver1_psn", "Driver One");
        driver1.setId(UUID.randomUUID());

        driver2 = new Driver("driver2_psn", "Driver Two");
        driver2.setId(UUID.randomUUID());
    }

    private void setupCommonMocks() {
        when(seasonRepository.findByName("Season 1")).thenReturn(Optional.of(season));
        when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(any(), any(), any())).thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            var m = inv.getArgument(0, Match.class);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            var r = inv.getArgument(0, Race.class);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(seasonDriverRepository.findBySeasonIdAndDriverId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void executeImport_withSubTeams_createsRaceLineup() {
        setupCommonMocks();
        when(teamRepository.findByShortName("AHR_1")).thenReturn(Optional.of(subTeam1));
        when(teamRepository.findByShortName("AHR_2")).thenReturn(Optional.of(subTeam2));
        when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

        var metadata = new CsvImportService.ImportMetadata("Season 1", null, null, null, null, matchday.getId());
        var row1 = new CsvImportService.ImportRow("AHR_1", "driver1_psn", 1, 1, false,
                DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
        var row2 = new CsvImportService.ImportRow("AHR_2", "driver2_psn", 2, 2, false,
                DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

        var preview = new CsvImportService.ImportPreview(metadata);
        preview.addRow(row1);
        preview.addRow(row2);

        var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getLineupCount()).isEqualTo(2);

        var captor = ArgumentCaptor.forClass(RaceLineup.class);
        verify(raceLineupRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(rl -> rl.getDriver().getId())
                .containsExactlyInAnyOrder(driver1.getId(), driver2.getId());
    }

    @Test
    void executeImport_withStandaloneTeams_createsRaceLineup() {
        setupCommonMocks();
        when(teamRepository.findByShortName("BRV")).thenReturn(Optional.of(standaloneTeam1));
        when(teamRepository.findByShortName("CRL")).thenReturn(Optional.of(standaloneTeam2));
        when(raceLineupRepository.findByRaceIdAndDriverId(any(), any())).thenReturn(Optional.empty());

        var metadata = new CsvImportService.ImportMetadata("Season 1", null, null, null, null, matchday.getId());
        var row1 = new CsvImportService.ImportRow("BRV", "driver1_psn", 1, 1, false,
                DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
        var row2 = new CsvImportService.ImportRow("CRL", "driver2_psn", 2, 2, false,
                DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

        var preview = new CsvImportService.ImportPreview(metadata);
        preview.addRow(row1);
        preview.addRow(row2);

        var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getLineupCount()).isEqualTo(2);
        verify(raceLineupRepository, times(2)).save(any());
    }

    @Test
    void executeImport_withExistingLineup_doesNotDuplicate() {
        setupCommonMocks();
        when(teamRepository.findByShortName("AHR_1")).thenReturn(Optional.of(subTeam1));
        when(teamRepository.findByShortName("AHR_2")).thenReturn(Optional.of(subTeam2));

        // driver1 already has a lineup entry, driver2 does not
        when(raceLineupRepository.findByRaceIdAndDriverId(any(), eq(driver1.getId())))
                .thenReturn(Optional.of(new RaceLineup()));
        when(raceLineupRepository.findByRaceIdAndDriverId(any(), eq(driver2.getId())))
                .thenReturn(Optional.empty());

        var metadata = new CsvImportService.ImportMetadata("Season 1", null, null, null, null, matchday.getId());
        var row1 = new CsvImportService.ImportRow("AHR_1", "driver1_psn", 1, 1, false,
                DriverMatchingService.MatchResult.exact("driver1_psn", driver1));
        var row2 = new CsvImportService.ImportRow("AHR_2", "driver2_psn", 2, 2, false,
                DriverMatchingService.MatchResult.exact("driver2_psn", driver2));

        var preview = new CsvImportService.ImportPreview(metadata);
        preview.addRow(row1);
        preview.addRow(row2);

        var result = csvImportService.executeImport(preview, Map.of(), Set.of(), false);

        assertThat(result.getLineupCount()).isEqualTo(1);
        verify(raceLineupRepository, times(1)).save(any());
    }
}
