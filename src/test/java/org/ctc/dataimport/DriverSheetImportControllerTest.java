package org.ctc.dataimport;

import jakarta.persistence.EntityManager;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DriverSheetImportController — happy-path flow.
 * Uses @MockitoBean GoogleSheetsService to short-circuit real API calls;
 * all other beans (DriverSheetImportService, repositories) are real Spring beans using H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DriverSheetImportControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestHelper testHelper;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private SeasonDriverRepository seasonDriverRepository;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private GoogleSheetsService googleSheetsService;

    // Shared fixtures — reused across multiple test methods
    // NOTE: years 2021 and 2022 are intentionally used — TestDataService uses 2023/2024/2025/2026,
    // so these years produce exactly one Season per findByYear() call → correct CONFLICT/UNCHANGED detection.
    private Season season2021;
    private Season season2022;
    private Team teamAhr;
    private Team teamCrl;
    private Driver existingDriver;

    @BeforeEach
    void setUp() {
        // Use unique prefixes to avoid cross-test collision (TestHelper ensures DB isolation via @Transactional)
        season2021 = testHelper.createSeason("ImpTest_2021", 2021, 1);
        season2022 = testHelper.createSeason("ImpTest_2022", 2022, 1);
        teamAhr = testHelper.createTeam("Import Test AHR", "I_AHR");
        teamCrl = testHelper.createTeam("Import Test CRL", "I_CRL");
        existingDriver = testHelper.createDriver("imp_existing_drv", "Imp Existing Driver");
    }

    // --- Helper methods ---

    /**
     * Stubs GoogleSheetsService to return one year-tab with provided data rows.
     */
    private void stubSheets(String sheetUrl, int year, List<List<Object>> rows) throws Exception {
        String fakeId = "fake-spreadsheet-id";
        when(googleSheetsService.extractSpreadsheetId(sheetUrl)).thenReturn(fakeId);
        when(googleSheetsService.getSheetNames(fakeId))
                .thenReturn(List.of(String.valueOf(year)));
        when(googleSheetsService.readRangeFromSheet(eq(fakeId), eq(String.valueOf(year)), eq("A:C")))
                .thenReturn(rows);
    }

    /**
     * Stubs GoogleSheetsService to return two year-tabs with provided data.
     */
    private void stubSheetsForTwoTabs(String sheetUrl, int year1, List<List<Object>> rows1,
                                       int year2, List<List<Object>> rows2) throws Exception {
        String fakeId = "fake-spreadsheet-id";
        when(googleSheetsService.extractSpreadsheetId(sheetUrl)).thenReturn(fakeId);
        when(googleSheetsService.getSheetNames(fakeId))
                .thenReturn(List.of(String.valueOf(year1), String.valueOf(year2)));
        when(googleSheetsService.readRangeFromSheet(eq(fakeId), eq(String.valueOf(year1)), eq("A:C")))
                .thenReturn(rows1);
        when(googleSheetsService.readRangeFromSheet(eq(fakeId), eq(String.valueOf(year2)), eq("A:C")))
                .thenReturn(rows2);
    }

    /**
     * Returns header + one NEW_DRIVER data row.
     */
    private static List<List<Object>> newDriverRows(String psnId, String teamCode) {
        return List.of(
                List.of("PSN ID", "Nickname", "Team"),  // header
                List.of(psnId, psnId, teamCode)          // data row
        );
    }

    // --- Test methods ---

    @Test
    void whenGetImportPage_thenShowsImportFormWithSeasonsAndSheetsAvailable() throws Exception {
        // when / then
        mockMvc.perform(get("/admin/drivers/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-import"))
                .andExpect(model().attributeExists("seasons", "sheetsAvailable"));
    }

    @Test
    void givenValidSheetUrl_whenPostPreview_thenRendersPreviewTemplate() throws Exception {
        // given
        stubSheets("https://sheets.test/d/abc", 2024, newDriverRows("prev_new_psn", "I_AHR"));

        // when / then
        mockMvc.perform(post("/admin/drivers/import/preview")
                        .param("sheetUrl", "https://sheets.test/d/abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-import-preview"))
                .andExpect(model().attributeExists("preview", "sheetUrl", "seasons"));
    }

    @Test
    void givenBlankSheetUrl_whenPostPreview_thenRendersFormWithErrorMessage() throws Exception {
        // when / then
        mockMvc.perform(post("/admin/drivers/import/preview")
                        .param("sheetUrl", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-import"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void givenSheetsUnavailable_whenGetImportPage_thenModelHasSheetsAvailableFalse() throws Exception {
        // given
        when(googleSheetsService.isAvailable()).thenReturn(false);

        // when / then
        mockMvc.perform(get("/admin/drivers/import"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sheetsAvailable", false));
    }

    @Test
    void givenNewDriverRow_whenExecute_thenCreatesDriverAndSeasonDriver() throws Exception {
        // given
        stubSheets("https://sheets.test/d/abc", 2024, newDriverRows("imp_new_d1", "I_AHR"));

        // when / then
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage", containsString("new drivers")));

        assertThat(driverRepository.findByPsnId("imp_new_d1")).isPresent();
        Driver created = driverRepository.findByPsnId("imp_new_d1").get();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2024.getId(), created.getId()
        )).isPresent();
    }

    @Test
    void givenNewAssignmentRow_whenExecute_thenCreatesSeasonDriverForExistingDriver() throws Exception {
        // given — existingDriver already in DB (from setUp); sheet row references them → NEW_ASSIGNMENT
        stubSheets("https://sheets.test/d/abc", 2024, newDriverRows("imp_existing_drv", "I_AHR"));

        // when / then
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // existingDriver should still exist (not duplicated)
        assertThat(driverRepository.findByPsnId("imp_existing_drv")).isPresent();
        // SeasonDriver created for existing driver
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2024.getId(), existingDriver.getId()
        )).isPresent();
    }

    @Test
    void givenConflictRowWithSkipSet_whenExecute_thenExistingSeasonDriverUntouched() throws Exception {
        // given — existing SeasonDriver has teamAhr; sheet row says teamCrl (CONFLICT)
        SeasonDriver existingSd = testHelper.createSeasonDriver(season2024, existingDriver, teamAhr);
        java.util.UUID existingSdId = existingSd.getId();
        // Sheet row: same PSN, different team → categorized as CONFLICT by preview
        stubSheets("https://sheets.test/d/abc", 2024,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("imp_existing_drv", "imp_existing_drv", "I_CRL")
                ));

        // when — skip_<psnId>_<year>=on → skip the conflict
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString())
                        .param("skip_imp_existing_drv_2024", "on"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — existing SeasonDriver still points to teamAhr (flush+clear evicts L1 cache)
        entityManager.flush();
        entityManager.clear();
        SeasonDriver afterExecute = seasonDriverRepository.findById(existingSdId).orElseThrow();
        assertThat(afterExecute.getTeam().getId()).isEqualTo(teamAhr.getId());
    }

    @Test
    void givenConflictRowWithoutSkip_whenExecute_thenSeasonDriverTeamOverwritten() throws Exception {
        // given — existing SeasonDriver has teamAhr; sheet row says teamCrl (CONFLICT)
        SeasonDriver existingSd = testHelper.createSeasonDriver(season2024, existingDriver, teamAhr);
        java.util.UUID existingSdId = existingSd.getId();
        stubSheets("https://sheets.test/d/abc", 2024,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("imp_existing_drv", "imp_existing_drv", "I_CRL")
                ));

        // when — no skip param → overwrite
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — flush+clear to evict L1 cache before verifying DB state
        entityManager.flush();
        entityManager.clear();
        SeasonDriver afterExecute = seasonDriverRepository.findById(existingSdId).orElseThrow();
        assertThat(afterExecute.getTeam().getId()).isEqualTo(teamCrl.getId());
    }

    @Test
    void givenFuzzyRowWithAcceptSet_whenExecute_thenLinksToExistingDriver() throws Exception {
        // given — driver with PSN "fuzz_src" exists; sheet PSN "fuzz_srx" → fuzzy match
        // Similarity: Levenshtein("fuzz_src", "fuzz_srx") = 2 edits, max=8, sim=0.75 — too low
        // Use shorter strings: "fzz_a" vs "fzz_b" — dist=1, max=5, sim=0.8 (exactly at threshold)
        // Use "fzz_src" (7) vs "fzz_scr" (7): dist=2, sim=1-2/7≈0.71 — too low
        // Use "fz_src" (6) vs "fz_srd" (6): dist=1, sim=1-1/6≈0.833 — OK!
        Driver fuzzyDriver = testHelper.createDriver("fz_src", "Fuzzy Source");
        // Sheet row has "fz_srd" → fuzzy match to fuzzyDriver
        stubSheets("https://sheets.test/d/abc", 2024,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("fz_srd", "fz_srd", "I_AHR")
                ));

        // when — accept param provides fuzzyDriver's UUID → link to existing driver
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString())
                        .param("accept_fz_srd_2024", fuzzyDriver.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — no new Driver created; SeasonDriver linked to fuzzyDriver
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2024.getId(), fuzzyDriver.getId()
        )).isPresent();
    }

    @Test
    void givenFuzzyRowWithoutAccept_whenExecute_thenCreatesNewDriver() throws Exception {
        // given — driver "fz_src" exists; sheet has "fz_srd" → fuzzy match, but accept not set
        Driver fuzzyDriver = testHelper.createDriver("fz_src", "Fuzzy Source No Accept");
        stubSheets("https://sheets.test/d/abc", 2024,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("fz_srd_2", "fz_srd_2", "I_AHR")
                ));
        // "fz_srd_2" (7) vs "fz_src" (6): sim = 1 - 3/7 ≈ 0.57 — not fuzzy
        // Need a better pair. "fz_src" (6) vs "fz_src2" (7): sim = 1 - 1/7 ≈ 0.857 — OK!
        // But "fz_src2" exact match will fail since it does not exist — goes to FUZZY if similarity >= 0.8
        // Actually: Levenshtein("fz_src", "fz_src2") = 1 (append '2'), max=7, sim=1-1/7=0.857 — FUZZY
        // Let's redo: existingDriver PSN="fz_noacc", sheet PSN="fz_noac0"
        // "fz_noacc" (8) vs "fz_noac0" (8): dist=1 ('c'→'0'), sim=1-1/8=0.875 — OK!
        Driver fuzzyDriverNoAccept = testHelper.createDriver("fz_noacc", "Fuzzy No Accept Driver");
        stubSheets("https://sheets.test/d/abc2", 2024,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("fz_noac0", "fz_noac0", "I_AHR")
                ));

        // when — no accept param → create new driver
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc2")
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — new Driver with PSN "fz_noac0" created
        assertThat(driverRepository.findByPsnId("fz_noac0")).isPresent();
    }

    @Test
    void givenUnchangedRow_whenExecute_thenNoWrite() throws Exception {
        // given — existingDriver already assigned to teamAhr in season2024 (UNCHANGED row)
        testHelper.createSeasonDriver(season2024, existingDriver, teamAhr);
        long countBefore = seasonDriverRepository.count();

        stubSheets("https://sheets.test/d/abc", 2024,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("imp_existing_drv", "imp_existing_drv", "I_AHR")
                ));

        // when
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — no additional SeasonDriver rows (unchanged row makes no write)
        assertThat(seasonDriverRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void givenErrorRow_whenExecute_thenRowExcluded() throws Exception {
        // given — row with blank PSN → ERROR bucket, never imported
        long driverCountBefore = driverRepository.count();
        stubSheets("https://sheets.test/d/abc", 2024,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("", "", "I_AHR")  // blank PSN → ERROR
                ));

        // when
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — no new Driver created (error row excluded)
        assertThat(driverRepository.count()).isEqualTo(driverCountBefore);
    }

    @Test
    void givenSameNewPsnInTwoTabs_whenExecute_thenSingleDriverCreated() throws Exception {
        // given — same PSN "xtab_new_psn" in both 2023 and 2024 tabs → only one Driver created
        List<List<Object>> rows2023 = List.of(
                List.of("PSN ID", "Nickname", "Team"),
                List.of("xtab_new_psn", "xtab_new_psn", "I_AHR")
        );
        List<List<Object>> rows2024 = List.of(
                List.of("PSN ID", "Nickname", "Team"),
                List.of("xtab_new_psn", "xtab_new_psn", "I_CRL")
        );
        stubSheetsForTwoTabs("https://sheets.test/d/xtab", 2023, rows2023, 2024, rows2024);

        // when
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/xtab")
                        .param("seasonId_2023", season2023.getId().toString())
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — exactly one Driver with PSN "xtab_new_psn"
        assertThat(driverRepository.findByPsnId("xtab_new_psn")).isPresent();
        Driver xtabDriver = driverRepository.findByPsnId("xtab_new_psn").get();
        // two SeasonDriver rows — one per season
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2023.getId(), xtabDriver.getId())).isPresent();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2024.getId(), xtabDriver.getId())).isPresent();
    }

    @Test
    void givenTabWithoutSeasonId_whenExecute_thenTabSkippedAndFlaggedInFlash() throws Exception {
        // given — only one tab (2024); no seasonId_2024 param provided → tab skipped
        stubSheets("https://sheets.test/d/abc", 2024, newDriverRows("skipped_drv", "I_AHR"));

        // when — no seasonId_2024 param → tab is skipped
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage", containsString("Skipped tabs")));

        // then — driver not created (tab skipped)
        assertThat(driverRepository.findByPsnId("skipped_drv")).isEmpty();
    }

    @Test
    void givenDataAccessException_whenExecute_thenFullRollback() throws Exception {
        // given — sheet has a NEW_DRIVER row with a valid team; but the seasonId param
        // references a non-existent UUID → execute() throws IllegalArgumentException ("Season not found")
        // which is caught by the controller catch block → redirect with errorMessage flash
        stubSheets("https://sheets.test/d/abc", 2024, newDriverRows("rollback_drv", "I_AHR"));

        // when — provide a bogus seasonId that does not exist in DB → execute throws
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("errorMessage"));

        // then — no driver created (transaction rolled back on exception)
        assertThat(driverRepository.findByPsnId("rollback_drv")).isEmpty();
    }

    @Test
    void givenMixedBucketExecute_whenSuccess_thenFlashContainsAggregatedCounts() throws Exception {
        // given — one new driver row + one unchanged row
        testHelper.createSeasonDriver(season2024, existingDriver, teamAhr);
        stubSheets("https://sheets.test/d/abc", 2024,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("imp_mix_new", "imp_mix_new", "I_AHR"),         // NEW_DRIVER
                        List.of("imp_existing_drv", "imp_existing_drv", "I_AHR") // UNCHANGED
                ));

        // when
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2024", season2024.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage", containsString("new drivers")))
                .andExpect(flash().attribute("successMessage", containsString("unchanged")));
    }

    @Test
    void givenDriversPage_whenGet_thenContainsImportButton() throws Exception {
        // when / then — drivers.html toolbar contains the import link (IMPORT-01)
        mockMvc.perform(get("/admin/drivers"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/drivers/import")));
    }
}
