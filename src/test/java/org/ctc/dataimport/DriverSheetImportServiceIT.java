package org.ctc.dataimport;

import org.ctc.dataimport.DriverSheetImportService.DriverSheetImportPreview;
import org.ctc.dataimport.DriverSheetImportService.ExecuteResult;
import org.ctc.dataimport.DriverSheetImportService.NewDriverRow;
import org.ctc.dataimport.DriverSheetImportService.TabPreview;
import org.ctc.dataimport.DriverSheetImportService.TabWarning;
import org.ctc.dataimport.DriverSheetImportService.WarningType;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link DriverSheetImportService} preview→execute roundtrip.
 *
 * <p>Runs against the live Spring context with the consolidated 2023 GROUPS season
 * seeded by {@link org.ctc.admin.TestDataService} (delivered by Phase 59 Plan 03).
 * {@link GoogleSheetsService} is replaced by a {@link MockitoBean} so no real Sheets
 * API call happens in CI (Phase 59 D-22).
 *
 * <p>Test-shape override per Phase 58 D-13: {@code @SpringBootTest @ActiveProfiles("dev") @Transactional}.
 * Each test runs in its own rolled-back transaction.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DriverSheetImportServiceIT {

    private static final String SHEET_URL = "https://docs.google.com/spreadsheets/d/it-sheet-id";
    private static final String SPREADSHEET_ID = "it-sheet-id";

    @MockitoBean
    private GoogleSheetsService googleSheetsService;

    @Autowired private DriverSheetImportService driverSheetImportService;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PhaseTeamRepository phaseTeamRepository;
    @Autowired private SeasonDriverRepository seasonDriverRepository;
    @Autowired private org.ctc.domain.repository.DriverRepository driverRepository;

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Stubs GoogleSheetsService to return the given tabs and rows.
     * Adapted from DriverSheetImportServiceTest.setupSheetsStub for @MockitoBean usage.
     */
    private void setupSheetsStub(Map<String, List<List<Object>>> tabsToRows) throws IOException {
        when(googleSheetsService.extractSpreadsheetId(SHEET_URL)).thenReturn(SPREADSHEET_ID);
        when(googleSheetsService.getSheetNames(SPREADSHEET_ID))
                .thenReturn(new ArrayList<>(tabsToRows.keySet()));
        for (Map.Entry<String, List<List<Object>>> entry : tabsToRows.entrySet()) {
            lenient().when(googleSheetsService.readRangeFromSheet(SPREADSHEET_ID, entry.getKey(), "A:C"))
                    .thenReturn(entry.getValue());
        }
    }

    /** Header row + arbitrary number of data rows. */
    private List<List<Object>> dataRows(List<List<Object>> dataRows) {
        List<List<Object>> all = new ArrayList<>();
        all.add(List.of("PSN ID", "Name", "Team"));
        all.addAll(dataRows);
        return all;
    }

    /** Header row + single data row. */
    private List<List<Object>> oneDataRow(String psn, String name, String teamCode) {
        return List.of(
                List.of("PSN ID", "Name", "Team"),
                List.of(psn, name, teamCode));
    }

    private Season findSeason(int year, int number) {
        return seasonRepository.findAll().stream()
                .filter(s -> s.getYear() == year && s.getNumber() == number)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Season not found: year=" + year + " number=" + number));
    }

    private SeasonPhase findRegularPhase(Season season) {
        return season.getPhases().stream()
                .filter(p -> p.getPhaseType() == PhaseType.REGULAR)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "REGULAR phase not found for season " + season.getName()));
    }

    // ---------------------------------------------------------------------
    // 1. Tab pattern — legacy ^\d{4}$ resolves single-season-per-year (D-01 + D-04)
    // ---------------------------------------------------------------------

    @Test
    void givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToLeague2024() throws IOException {
        // given — the seed has exactly one 2024 season (year=2024, number=2)
        var season2024 = findSeason(2024, 2);
        setupSheetsStub(Map.of("2024", oneDataRow("ADR_Driver01", "Adr One", "ADR")));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        assertThat(preview.tabPreviews()).hasSize(1);
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.tabName()).isEqualTo("2024");
        assertThat(tab.year()).isEqualTo(2024);
        assertThat(tab.number()).isNull();    // legacy tab → null number per D-01
        assertThat(tab.suggestedSeasonId()).isEqualTo(season2024.getId());
        assertThat(tab.ambiguousReason()).isNull();
    }

    // ---------------------------------------------------------------------
    // 2. Tab pattern — new ^\d{4}_S\d+$ resolves via findUnique(year, number) (D-01 + D-02)
    // ---------------------------------------------------------------------

    @Test
    void givenYearAndNumberTab_whenPreview_thenSeasonResolvedViaFindUniqueByNumber() throws IOException {
        // given — seed has (year=2026, number=4) as the active LEAGUE season
        var season2026 = findSeason(2026, 4);
        setupSheetsStub(Map.of("2026_S4", oneDataRow("ADR_Driver01", "Adr One", "ADR")));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        assertThat(preview.tabPreviews()).hasSize(1);
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.tabName()).isEqualTo("2026_S4");
        assertThat(tab.year()).isEqualTo(2026);
        assertThat(tab.number()).isEqualTo(4);
        assertThat(tab.suggestedSeasonId()).isEqualTo(season2026.getId());
        assertThat(tab.ambiguousReason()).isNull();
    }

    // ---------------------------------------------------------------------
    // 3. Tab pattern — legacy form with multiple seasons in year (ambiguous, D-03 + D-18)
    // ---------------------------------------------------------------------

    @Test
    void givenLegacyTabWithMultipleSeasons_whenPreview_thenAmbiguousReasonStartsWithMultipleSeasons() throws IOException {
        // given — seed only has (2024, 2). Persist an additional (2024, 5) inline so
        // findByYear(2024) returns 2 hits and the legacy '2024' tab triggers
        // BusinessRuleException via SeasonManagementService.findUnique(int).
        var extra2024 = new Season();
        extra2024.setName("Phase59-IT-Extra-2024");
        extra2024.setYear(2024);
        extra2024.setNumber(5);
        extra2024.setActive(false);
        // Phase 61 MIGR-06: scoring lives on the SeasonPhase (not Season). The seasons.race_scoring_id /
        // match_scoring_id columns are nullable post-V5; tests do not need to populate them on Season directly.
        seasonRepository.save(extra2024);

        setupSheetsStub(Map.of("2024", oneDataRow("ADR_Driver01", "Adr One", "ADR")));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — BusinessRuleException routed to ambiguousReason per D-18
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).startsWith("Multiple seasons exist for year 2024");
    }

    // ---------------------------------------------------------------------
    // 4. Group resolution — driver in roster of Group A (D-05)
    // ---------------------------------------------------------------------

    @Test
    void givenDriverInGroupATeam_whenPreview_thenResolvedGroupNameIsGroupA() throws IOException {
        // given — consolidated 2023 GROUPS season; ADR is in Group A
        setupSheetsStub(Map.of("2023_S1", oneDataRow("Phase59-IT-AdrNew", "Adr New Driver", "ADR")));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNotNull();
        assertThat(tab.warnings()).isEmpty();
        // The PSN "Phase59-IT-AdrNew" is brand-new → categorized as NEW_DRIVER
        assertThat(tab.newDrivers()).hasSize(1);
        NewDriverRow row = tab.newDrivers().get(0);
        assertThat(row.psnId()).isEqualTo("Phase59-IT-AdrNew");
        assertThat(row.teamShortName()).isEqualTo("ADR");
        assertThat(row.resolvedGroupName()).isEqualTo("Group A");
    }

    // ---------------------------------------------------------------------
    // 5. Group resolution — driver in roster of Group B (D-05)
    // ---------------------------------------------------------------------

    @Test
    void givenDriverInGroupBTeam_whenPreview_thenResolvedGroupNameIsGroupB() throws IOException {
        // given — consolidated 2023 GROUPS season; EGP is in Group B
        setupSheetsStub(Map.of("2023_S1", oneDataRow("Phase59-IT-EgpNew", "Egp New Driver", "EGP")));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNotNull();
        assertThat(tab.warnings()).isEmpty();
        assertThat(tab.newDrivers()).hasSize(1);
        NewDriverRow row = tab.newDrivers().get(0);
        assertThat(row.psnId()).isEqualTo("Phase59-IT-EgpNew");
        assertThat(row.teamShortName()).isEqualTo("EGP");
        assertThat(row.resolvedGroupName()).isEqualTo("Group B");
    }

    // ---------------------------------------------------------------------
    // 6. TabWarning — team not in REGULAR PhaseTeam roster, deduplicated per team (D-06)
    // ---------------------------------------------------------------------

    @Test
    void givenTeamNotInRegularPhase_whenPreview_thenSingleTabWarningEmittedAndDeduplicated() throws IOException {
        // given — persist a Team with shortName "XYZ" that exists in `teams` but has
        // NO PhaseTeam in the consolidated 2023 REGULAR phase. The seed never
        // creates this team, so the team-existence check passes (no UNKNOWN_TEAM_CODE
        // ErrorRow), but the PhaseTeam lookup returns Optional.empty() → warning fires.
        var orphanTeam = new Team("Phase59-IT-Orphan", "XYZ");
        teamRepository.save(orphanTeam);

        // Two rows reference the SAME orphan team — warning must dedupe to ONE entry.
        setupSheetsStub(Map.of("2023_S1", dataRows(List.of(
                List.of("Phase59-IT-Orphan-D1", "Orphan Driver 1", "XYZ"),
                List.of("Phase59-IT-Orphan-D2", "Orphan Driver 2", "XYZ")))));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNotNull();
        assertThat(tab.warnings()).hasSize(1);  // D-06 dedup: one per team, not per row
        TabWarning warning = tab.warnings().get(0);
        assertThat(warning.type()).isEqualTo(WarningType.TEAM_NOT_IN_REGULAR_PHASE);
        assertThat(warning.teamShortName()).isEqualTo("XYZ");
        assertThat(warning.message()).contains("XYZ");
        // Both rows still get categorized — group resolution null but row created
        assertThat(tab.newDrivers()).hasSize(2);
        assertThat(tab.newDrivers()).allMatch(r -> r.resolvedGroupName() == null);
    }

    // ---------------------------------------------------------------------
    // 7. Execute writes only SeasonDriver, NEVER PhaseTeam (D-07 + D-16)
    // ---------------------------------------------------------------------

    @Test
    void givenNewDriverRowOnConsolidated2023_whenExecute_thenOnlySeasonDriverIsWritten() throws IOException {
        // given — consolidated 2023 GROUPS season exists; PhaseTeam count is 12 (6+6)
        var season2023 = findSeason(2023, 1);
        var regular2023 = findRegularPhase(season2023);
        int phaseTeamCountBefore = phaseTeamRepository.findByPhaseId(regular2023.getId()).size();
        assertThat(phaseTeamCountBefore).isEqualTo(12);  // sanity: 59-03 seeded 12

        // A NEW driver assigned to existing team ADR (which has a PhaseTeam in Group A)
        String newPsn = "Phase59-IT-Execute-AdrNew";
        setupSheetsStub(Map.of("2023_S1", oneDataRow(newPsn, "New Adr Driver", "ADR")));

        // when — preview, then execute with seasonId_2023 = consolidated season id
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        assertThat(preview.tabPreviews().get(0).newDrivers()).hasSize(1);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2023", season2023.getId().toString());
        ExecuteResult result = driverSheetImportService.execute(SHEET_URL, params);

        // then — SeasonDriver row IS written
        assertThat(result.getNewDriversCount()).isEqualTo(1);
        assertThat(result.getNewAssignmentsCount()).isEqualTo(1);
        var newDriver = driverRepository.findAll().stream()
                .filter(d -> newPsn.equals(d.getPsnId()))
                .findFirst().orElseThrow(() -> new AssertionError("Driver not created: " + newPsn));
        assertThat(seasonDriverRepository
                .findBySeasonIdAndDriverId(season2023.getId(), newDriver.getId()))
                .isPresent();

        // then — PhaseTeam count is BYTE-FOR-BYTE unchanged (D-07 + D-16 invariant)
        int phaseTeamCountAfter = phaseTeamRepository.findByPhaseId(regular2023.getId()).size();
        assertThat(phaseTeamCountAfter).isEqualTo(phaseTeamCountBefore);
    }

    // ---------------------------------------------------------------------
    // 8. Execute against team without PhaseTeam — SeasonDriver written, PhaseTeam still untouched (D-07)
    // ---------------------------------------------------------------------

    @Test
    void givenTeamWithoutPhaseTeam_whenExecute_thenSeasonDriverWrittenAndPhaseTeamUnchanged() throws IOException {
        // given — orphan team XYZ exists in `teams` but has no PhaseTeam on 2023 REGULAR
        var orphanTeam = new Team("Phase59-IT-Execute-Orphan", "XYZ");
        teamRepository.save(orphanTeam);

        var season2023 = findSeason(2023, 1);
        var regular2023 = findRegularPhase(season2023);
        int phaseTeamCountBefore = phaseTeamRepository.findByPhaseId(regular2023.getId()).size();
        assertThat(phaseTeamCountBefore).isEqualTo(12);

        String newPsn = "Phase59-IT-Execute-OrphanDriver";
        setupSheetsStub(Map.of("2023_S1", oneDataRow(newPsn, "Orphan Driver", "XYZ")));

        // when — preview emits one warning; execute proceeds anyway (D-07 informational)
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        assertThat(preview.tabPreviews().get(0).warnings()).hasSize(1);
        assertThat(preview.tabPreviews().get(0).warnings().get(0).teamShortName()).isEqualTo("XYZ");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2023", season2023.getId().toString());
        ExecuteResult result = driverSheetImportService.execute(SHEET_URL, params);

        // then — SeasonDriver row IS written, even though team has no PhaseTeam
        assertThat(result.getNewDriversCount()).isEqualTo(1);
        assertThat(result.getNewAssignmentsCount()).isEqualTo(1);
        var newDriver = driverRepository.findAll().stream()
                .filter(d -> newPsn.equals(d.getPsnId()))
                .findFirst().orElseThrow(() -> new AssertionError("Driver not created: " + newPsn));
        assertThat(seasonDriverRepository
                .findBySeasonIdAndDriverId(season2023.getId(), newDriver.getId()))
                .isPresent();

        // then — PhaseTeam count UNCHANGED — Roster pflege bleibt Phase 60 (D-07)
        int phaseTeamCountAfter = phaseTeamRepository.findByPhaseId(regular2023.getId()).size();
        assertThat(phaseTeamCountAfter).isEqualTo(phaseTeamCountBefore);
        // Specifically: no new PhaseTeam for the orphan team
        assertThat(phaseTeamRepository
                .findByPhaseIdAndTeamId(regular2023.getId(), orphanTeam.getId()))
                .isEmpty();
    }
}
