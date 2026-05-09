package org.ctc.dataimport;

import org.ctc.dataimport.DriverSheetImportService.DriverSheetImportPreview;
import org.ctc.dataimport.DriverSheetImportService.ExecuteResult;
import org.ctc.dataimport.DriverSheetImportService.TabPreview;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link DriverSheetImportService} preview→execute roundtrip.
 *
 * <p>Runs against the live Spring context with the consolidated 2023 GROUPS season
 * seeded by {@link org.ctc.admin.TestDataService} (delivered by Phase 59 Plan 03).
 * {@link GoogleSheetsService} is replaced by a {@link MockitoBean} so no real Sheets
 * API call happens in CI.
 *
 * <p>Test-shape override per {@code @SpringBootTest @ActiveProfiles("dev") @Transactional}.
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

    // Helpers

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

    // 1. Tab pattern — legacy ^\d{4}$ resolves single-season-per-year (D-01 + D-04)

    @Test
    void givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToUniqueYearSeason() throws IOException {
        // given — insert a fresh single-season year so legacy ^\d{4}$ tab path is unambiguous.
        // Avoids coupling to seed years where additional same-year seasons may exist
        // (e.g. (2024,2) + (2024,3) D-22 empty-state). @Transactional rolls back after test.
        var season = new Season();
        season.setName("Phase59-IT-Legacy-2027");
        season.setYear(2027);
        season.setNumber(1);
        season.setActive(false);
        seasonRepository.save(season);

        setupSheetsStub(Map.of("2027", oneDataRow("ADR_Driver01", "Adr One", "ADR")));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        assertThat(preview.tabPreviews()).hasSize(1);
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.tabName()).isEqualTo("2027");
        assertThat(tab.year()).isEqualTo(2027);
        assertThat(tab.number()).isNull();    // legacy tab → null number
        assertThat(tab.suggestedSeasonId()).isEqualTo(season.getId());
        assertThat(tab.ambiguousReason()).isNull();
    }

    // 2. Tab pattern — new ^\d{4}_S\d+$ resolves via findUnique(year, number) (D-01 + D-02)

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

    // 3. Tab pattern — legacy form with multiple seasons in year (ambiguous, D-03 + D-18)

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
        // scoring lives on the SeasonPhase (not Season). The seasons.race_scoring_id /
        // match_scoring_id columns are nullable post-V5; tests do not need to populate them on Season directly.
        seasonRepository.save(extra2024);

        setupSheetsStub(Map.of("2024", oneDataRow("ADR_Driver01", "Adr One", "ADR")));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — BusinessRuleException routed to ambiguousReason
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).startsWith("Multiple seasons exist for year 2024");
    }

    // 4. Execute writes only SeasonDriver, NEVER PhaseTeam (D-07 + D-16)

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

        // when — preview, then execute with seasonId_<tabName> = consolidated season id
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        assertThat(preview.tabPreviews().get(0).newDrivers()).hasSize(1);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2023_S1", season2023.getId().toString());
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

    // 5. Execute against team without PhaseTeam — SeasonDriver written, PhaseTeam still untouched

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

        // when — Phase 70 D-09: warning category removed; preview categorizes the orphan-team
        // row into NEW_DRIVER (team exists in teams table → resolveTeamByShortName returns it
        // → no UNKNOWN_TEAM_CODE) and execute proceeds, writing the SeasonDriver.
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        assertThat(preview.tabPreviews().get(0).newDrivers())
                .anyMatch(r -> r.teamShortName().equals("XYZ"));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2023_S1", season2023.getId().toString());
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

        // then — PhaseTeam count UNCHANGED — Roster pflege bleibt Phase 60
        int phaseTeamCountAfter = phaseTeamRepository.findByPhaseId(regular2023.getId()).size();
        assertThat(phaseTeamCountAfter).isEqualTo(phaseTeamCountBefore);
        // Specifically: no new PhaseTeam for the orphan team
        assertThat(phaseTeamRepository
                .findByPhaseIdAndTeamId(regular2023.getId(), orphanTeam.getId()))
                .isEmpty();
    }

    // 6. Negative regression — wrong-shape form key for a seasoned tab is rejected (CR-01 lock)

    @Test
    void givenSeasonIdKeyUsesYearOnly_whenExecuteWithSeasonedTab_thenTabSkipped() throws IOException {
        // given — a seasoned tab "2023_S1" but params still use the legacy buggy "seasonId_2023"
        // shape (without the _S1 suffix). This locks the post-CR-01 contract: keys must use
        // the raw tabName, not the year alone, otherwise the tab is silently skipped.
        var season2023 = findSeason(2023, 1);
        setupSheetsStub(Map.of("2023_S1", oneDataRow("Phase70-IT-NegKey", "Neg Key Driver", "ADR")));

        // when — supply legacy-shape key
        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2023", season2023.getId().toString());
        ExecuteResult result = driverSheetImportService.execute(SHEET_URL, params);

        // then — tab skipped (key did not match), no driver created
        assertThat(result.hasSkippedTabs()).isTrue();
        assertThat(result.getSkippedTabNames()).containsExactly("2023_S1");
        assertThat(result.getNewDriversCount()).isZero();
        assertThat(result.getNewAssignmentsCount()).isZero();
    }

    // 7. GAP-70-01 hypothesis 1 — same NEW_DRIVER PSN in 2 tabs of one execute call.
    // Pre-fix (lines 121-127 of DriverSheetImportService) the second tab's
    // computeIfAbsent could miss the cache and trigger
    // DataIntegrityViolationException at flush. Post-fix (driverRepository
    // .findByPsnId(psnId).orElseGet(...)) exactly one Driver row is inserted
    // and both tabs reuse it for their per-season SeasonDriver writes.
    @Test
    void givenSameNewDriverPsnInTwoTabs_whenExecute_thenExactlyOneDriverRowInserted() throws IOException {
        // given — Tab A: existing LEAGUE season 2026_S4; Tab B: a fresh single-season year 2027.
        var season2026 = findSeason(2026, 4);
        var season2027 = new Season();
        season2027.setName("Phase70-IT-DupTab-2027");
        season2027.setYear(2027);
        season2027.setNumber(1);
        season2027.setActive(false);
        seasonRepository.save(season2027);

        String sharedPsn = "Phase70-IT-DupTab-Same";
        Map<String, List<List<Object>>> tabsToRows = new LinkedHashMap<>();
        tabsToRows.put("2026_S4", oneDataRow(sharedPsn, "Dup Tab Same A", "ADR"));
        tabsToRows.put("2027",    oneDataRow(sharedPsn, "Dup Tab Same B", "ADR"));
        setupSheetsStub(tabsToRows);

        // sanity-check the preview: both tabs see the row as NEW_DRIVER
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        assertThat(preview.tabPreviews()).hasSize(2);
        assertThat(preview.tabPreviews().get(0).newDrivers()).hasSize(1);
        assertThat(preview.tabPreviews().get(1).newDrivers()).hasSize(1);

        // when — execute against BOTH seasons in a single call
        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2026_S4", season2026.getId().toString());
        params.put("seasonId_2027",    season2027.getId().toString());
        ExecuteResult result = driverSheetImportService.execute(SHEET_URL, params);

        // then — exactly ONE Driver row inserted (counter fires once, regardless of tab count)
        assertThat(result.getNewDriversCount()).isEqualTo(1);
        // SeasonDriver written for BOTH seasons → 2 new assignments
        assertThat(result.getNewAssignmentsCount()).isEqualTo(2);
        // physical Driver row count: exactly 1 with that PSN
        assertThat(driverRepository.findAll().stream()
                .filter(d -> sharedPsn.equals(d.getPsnId()))
                .count())
                .isEqualTo(1L);
        // both SeasonDriver rows point to the SAME Driver UUID
        var driver = driverRepository.findByPsnId(sharedPsn).orElseThrow();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2026.getId(), driver.getId()))
                .isPresent();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2027.getId(), driver.getId()))
                .isPresent();
    }
}
