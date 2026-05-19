package org.ctc.dataimport;

import jakarta.persistence.EntityManager;
import java.util.List;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

    // Shared fixtures — reused across multiple test methods.
    // NOTE: years 2021 and 2022 are intentionally used. DevDataSeeder (CommandLineRunner
    // @Profile("dev")) seeds Seasons for years 2023/2024/2026 on context startup, so
    // those years would yield ambiguous findByYear() results in the categorizer
    // (suggestedSeasonId = null → NEW_ASSIGNMENT instead of CONFLICT/UNCHANGED).
    // Years 2021 and 2022 stay clean and produce exactly one Season per findByYear() call.
    private Season season2021;
    private Season season2022;
    private Team teamAhr;
    private Team teamCrl;
    private Driver existingDriver;

    @BeforeEach
    void setUp() {
        season2021 = testHelper.createSeason("ImpTest_2021", 2021, 1);
        season2022 = testHelper.createSeason("ImpTest_2022", 2022, 1);
        teamAhr = testHelper.createTeam("Import Test AHR", "I_AHR");
        teamCrl = testHelper.createTeam("Import Test CRL", "I_CRL");
        existingDriver = testHelper.createDriver("imp_existing_drv", "Imp Existing Driver");
    }


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
        stubSheets("https://sheets.test/d/abc", 2021, newDriverRows("prev_new_psn", "I_AHR"));

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
        stubSheets("https://sheets.test/d/abc", 2021, newDriverRows("imp_new_d1", "I_AHR"));

        // when / then
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", season2021.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage", containsString("new drivers")));

        assertThat(driverRepository.findByPsnId("imp_new_d1")).isPresent();
        Driver created = driverRepository.findByPsnId("imp_new_d1").get();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2021.getId(), created.getId()
        )).isPresent();
    }

    @Test
    void givenNewAssignmentRow_whenExecute_thenCreatesSeasonDriverForExistingDriver() throws Exception {
        // given — existingDriver already in DB (from setUp); sheet row references them → NEW_ASSIGNMENT
        stubSheets("https://sheets.test/d/abc", 2021, newDriverRows("imp_existing_drv", "I_AHR"));

        // when / then
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", season2021.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // existingDriver should still exist (not duplicated)
        assertThat(driverRepository.findByPsnId("imp_existing_drv")).isPresent();
        // SeasonDriver created for existing driver
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2021.getId(), existingDriver.getId()
        )).isPresent();
    }

    @Test
    void givenConflictRowWithSkipSet_whenExecute_thenExistingSeasonDriverUntouched() throws Exception {
        // given — existing SeasonDriver has teamAhr; sheet row says teamCrl (CONFLICT)
        SeasonDriver existingSd = testHelper.createSeasonDriver(season2021, existingDriver, teamAhr);
        java.util.UUID existingSdId = existingSd.getId();
        // Sheet row: same PSN, different team → categorized as CONFLICT by preview
        stubSheets("https://sheets.test/d/abc", 2021,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("imp_existing_drv", "imp_existing_drv", "I_CRL")
                ));

        // when — skip_<psnId>_<year>=on → skip the conflict
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", season2021.getId().toString())
                        .param("skip_imp_existing_drv_2021", "on"))
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
        SeasonDriver existingSd = testHelper.createSeasonDriver(season2021, existingDriver, teamAhr);
        java.util.UUID existingSdId = existingSd.getId();
        stubSheets("https://sheets.test/d/abc", 2021,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("imp_existing_drv", "imp_existing_drv", "I_CRL")
                ));

        // when — no skip param → overwrite
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", season2021.getId().toString()))
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
        stubSheets("https://sheets.test/d/abc", 2021,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("fz_srd", "fz_srd", "I_AHR")
                ));

        // when — accept param provides fuzzyDriver's UUID → link to existing driver
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", season2021.getId().toString())
                        .param("accept_fz_srd_2021", fuzzyDriver.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — no new Driver created; SeasonDriver linked to fuzzyDriver
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2021.getId(), fuzzyDriver.getId()
        )).isPresent();
    }

    @Test
    void givenSameFuzzyPsnInTwoTabsWithDifferentAcceptUuids_whenExecute_thenEachTabLinksToItsOwnDriver() throws Exception {
        // given — two drivers that each appear as FUZZY suggestions for the same sheet PSN in different year-tabs.
        // Sheet PSN: "fz_xtab" (7). DB driver A: "fz_xtab_a" (9), dist=2, sim=1-2/9≈0.78 — too low.
        // Use shorter pair: sheet PSN "fz_x" (4). DB "fz_a" (4): dist=1, sim=0.75 — too low (threshold 0.8).
        // "fz_xa" (5) vs DB "fz_xb" (5): dist=1, sim=0.8 — exactly at threshold, FUZZY!
        // Tab 2021 sheet PSN: "fz_xa" → fuzzy match to driverA ("fz_xb", dist=1, len=5, sim=0.8)
        // Tab 2022 sheet PSN: "fz_xa" → fuzzy match to driverB ("fz_xc", dist=1, len=5, sim=0.8)
        // Each tab supplies a different accept UUID — the service must NOT return driverA's result from cache for tab 2022.
        Driver driverA = testHelper.createDriver("fz_xb", "Fuzzy CrossTab A");
        Driver driverB = testHelper.createDriver("fz_xc", "Fuzzy CrossTab B");

        List<List<Object>> rows2021 = List.of(
                List.of("PSN ID", "Nickname", "Team"),
                List.of("fz_xa", "fz_xa", "I_AHR")
        );
        List<List<Object>> rows2022 = List.of(
                List.of("PSN ID", "Nickname", "Team"),
                List.of("fz_xa", "fz_xa", "I_CRL")
        );
        // stubSheetsForTwoTabs returns tabs sorted by year (2021, 2022)
        stubSheetsForTwoTabs("https://sheets.test/d/xtab_fuzzy", 2021, rows2021, 2022, rows2022);

        // when — tab 2021 accepts driverA, tab 2022 accepts driverB (different UUIDs for same sheet PSN)
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/xtab_fuzzy")
                        .param("seasonId_2021", season2021.getId().toString())
                        .param("seasonId_2022", season2022.getId().toString())
                        .param("accept_fz_xa_2021", driverA.getId().toString())
                        .param("accept_fz_xa_2022", driverB.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — season2021 linked to driverA; season2022 linked to driverB (NOT driverA due to first-tab cache hit)
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2021.getId(), driverA.getId())).isPresent();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2022.getId(), driverB.getId())).isPresent();
        // Cross-check: driverB must NOT be linked to season2021, driverA must NOT be linked to season2022
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2021.getId(), driverB.getId())).isEmpty();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2022.getId(), driverA.getId())).isEmpty();
    }

    @Test
    void givenSameFuzzyPsnAcceptedInOneTabAndUnacceptedInAnother_whenExecute_thenNoDuplicatePsnCreated() throws Exception {
        // given — WR-01 regression: same sheet PSN is FUZZY in two tabs.
        // Tab 1 accepts the existing fuzzy match (cache key tab-scoped, bare-PSN key untouched).
        // Tab 2 does NOT accept (would otherwise create a new Driver with the same PSN, which
        // collides with Driver.psnId unique constraint and rolls back the entire transaction).
        // Threshold-respecting pair: sheet PSN "fz_dx" (5) vs DB driver "fz_d1" (5),
        // dist=1, sim=0.8 — FUZZY threshold met.
        Driver fuzzyDriver = testHelper.createDriver("fz_d1", "Fuzzy Cross-Mode");

        List<List<Object>> rows2021 = List.of(
                List.of("PSN ID", "Nickname", "Team"),
                List.of("fz_dx", "fz_dx", "I_AHR")
        );
        List<List<Object>> rows2022 = List.of(
                List.of("PSN ID", "Nickname", "Team"),
                List.of("fz_dx", "fz_dx", "I_CRL")
        );
        // stubSheetsForTwoTabs returns tabs sorted by year (2021, 2022)
        stubSheetsForTwoTabs("https://sheets.test/d/xtab_mixed_fuzzy",
                2021, rows2021, 2022, rows2022);

        // when — tab 2021 accepts fuzzyDriver; tab 2022 leaves accept blank.
        // Pre-fix this would throw DataIntegrityViolationException on the second tab's
        // new-Driver insert and surface as "Import failed due to an internal error".
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/xtab_mixed_fuzzy")
                        .param("seasonId_2021", season2021.getId().toString())
                        .param("seasonId_2022", season2022.getId().toString())
                        .param("accept_fz_dx_2021", fuzzyDriver.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage", containsString("new drivers")));

        // then — Tab 1 linked to fuzzyDriver via the accept path
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2021.getId(), fuzzyDriver.getId())).isPresent();
        // Tab 2 did NOT collide: a new Driver with PSN "fz_dx" was created exactly once.
        assertThat(driverRepository.findByPsnId("fz_dx")).isPresent();
        Driver newDriver = driverRepository.findByPsnId("fz_dx").get();
        assertThat(newDriver.getId()).isNotEqualTo(fuzzyDriver.getId());
        // Tab 2 SeasonDriver assigned to the freshly-created driver (not fuzzyDriver)
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2022.getId(), newDriver.getId())).isPresent();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(
                season2022.getId(), fuzzyDriver.getId())).isEmpty();
    }

    @Test
    void givenFuzzyRowWithoutAccept_whenExecute_thenCreatesNewDriver() throws Exception {
        // given — "fz_noacc" (8 chars) vs sheet PSN "fz_noac0" (8 chars):
        // Levenshtein dist=1 ('c'→'0'), max=8, similarity=0.875 — FUZZY threshold met
        testHelper.createDriver("fz_noacc", "Fuzzy No Accept Driver");
        stubSheets("https://sheets.test/d/abc2", 2021,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("fz_noac0", "fz_noac0", "I_AHR")
                ));

        // when — no accept param → create new driver
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc2")
                        .param("seasonId_2021", season2021.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — new Driver with PSN "fz_noac0" created; original "fz_noacc" still present
        assertThat(driverRepository.findByPsnId("fz_noac0")).isPresent();
        assertThat(driverRepository.findByPsnId("fz_noacc")).isPresent(); // not modified
    }

    @Test
    void givenUnchangedRow_whenExecute_thenNoWrite() throws Exception {
        // given — existingDriver already assigned to teamAhr in season2021 (UNCHANGED row)
        testHelper.createSeasonDriver(season2021, existingDriver, teamAhr);
        long countBefore = seasonDriverRepository.count();

        stubSheets("https://sheets.test/d/abc", 2021,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("imp_existing_drv", "imp_existing_drv", "I_AHR")
                ));

        // when
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", season2021.getId().toString()))
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
        stubSheets("https://sheets.test/d/abc", 2021,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("", "", "I_AHR")  // blank PSN → ERROR
                ));

        // when
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", season2021.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — no new Driver created (error row excluded)
        assertThat(driverRepository.count()).isEqualTo(driverCountBefore);
    }

    @Test
    void givenSameNewPsnInTwoTabs_whenExecute_thenSingleDriverCreated() throws Exception {
        // given — same PSN "xtab_new_psn" in both 2022 and 2021 tabs → only one Driver created
        List<List<Object>> rows2022 = List.of(
                List.of("PSN ID", "Nickname", "Team"),
                List.of("xtab_new_psn", "xtab_new_psn", "I_AHR")
        );
        List<List<Object>> rows2021 = List.of(
                List.of("PSN ID", "Nickname", "Team"),
                List.of("xtab_new_psn", "xtab_new_psn", "I_CRL")
        );
        stubSheetsForTwoTabs("https://sheets.test/d/xtab", 2022, rows2022, 2021, rows2021);

        // when
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/xtab")
                        .param("seasonId_2022", season2022.getId().toString())
                        .param("seasonId_2021", season2021.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("successMessage"));

        // then — exactly one Driver with PSN "xtab_new_psn"
        assertThat(driverRepository.findByPsnId("xtab_new_psn")).isPresent();
        Driver xtabDriver = driverRepository.findByPsnId("xtab_new_psn").get();
        // two SeasonDriver rows — one per season
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2022.getId(), xtabDriver.getId())).isPresent();
        assertThat(seasonDriverRepository.findBySeasonIdAndDriverId(season2021.getId(), xtabDriver.getId())).isPresent();
    }

    @Test
    void givenNoSeasonIdsAtAll_whenExecute_thenExplicitErrorMessage() throws Exception {
        // given — sheet contains a tab, but the operator submitted zero seasonId_* params
        // (e.g. all dropdowns left at the empty default). Per REVIEW WR-02 the controller
        // now short-circuits with an explicit error message instead of letting the service
        // produce a "Skipped tabs: …" success — silent skipping looked like success in the
        // operator-facing flash banner.
        stubSheets("https://sheets.test/d/abc", 2021, newDriverRows("skipped_drv", "I_AHR"));

        // when — no seasonId_* params at all
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage",
                        containsString("No tabs were assigned a season")));

        // then — driver not created (service execute() never invoked)
        assertThat(driverRepository.findByPsnId("skipped_drv")).isEmpty();
    }

    @Test
    void givenDataAccessException_whenExecute_thenFullRollback() throws Exception {
        // given — sheet has a NEW_DRIVER row with a valid team; but the seasonId param
        // references a non-existent UUID → execute() throws IllegalArgumentException ("Season not found")
        // which is caught by the controller catch block → redirect with errorMessage flash
        stubSheets("https://sheets.test/d/abc", 2021, newDriverRows("rollback_drv", "I_AHR"));

        // when — provide a bogus seasonId that does not exist in DB → execute throws
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/drivers/import"))
                .andExpect(flash().attributeExists("errorMessage"));

        // then — no driver created (transaction rolled back on exception)
        assertThat(driverRepository.findByPsnId("rollback_drv")).isEmpty();
    }

    @Test
    void givenMixedBucketExecute_whenSuccess_thenFlashContainsAggregatedCounts() throws Exception {
        // given — one new driver row + one unchanged row
        testHelper.createSeasonDriver(season2021, existingDriver, teamAhr);
        stubSheets("https://sheets.test/d/abc", 2021,
                List.of(
                        List.of("PSN ID", "Nickname", "Team"),
                        List.of("imp_mix_new", "imp_mix_new", "I_AHR"),         // NEW_DRIVER
                        List.of("imp_existing_drv", "imp_existing_drv", "I_AHR") // UNCHANGED
                ));

        // when
        mockMvc.perform(post("/admin/drivers/import/execute")
                        .param("sheetUrl", "https://sheets.test/d/abc")
                        .param("seasonId_2021", season2021.getId().toString()))
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


    @Test
    void given2025_S2Tab_whenPreview_thenTemplateRendersRawTabName() throws Exception {
        // given: a season matching "2025_S2" tab name
        var season2025S2 = testHelper.createSeason("T-Phase60-DIP-2025S2", 2025, 2);
        String fakeId = "fake-60-tabname";
        when(googleSheetsService.extractSpreadsheetId("https://t60-tabname-test")).thenReturn(fakeId);
        when(googleSheetsService.getSheetNames(fakeId)).thenReturn(List.of("2025_S2"));
        when(googleSheetsService.readRangeFromSheet(eq(fakeId), eq("2025_S2"), eq("A:C")))
                .thenReturn(List.of(List.of("PSN ID", "Nickname", "Team")));

        // when
        mockMvc.perform(post("/admin/drivers/import/preview")
                        .param("sheetUrl", "https://t60-tabname-test"))
                // then: preview rendered; tab name "2025_S2" is passed through (D-37 raw tabName)
                .andExpect(status().isOk())
                .andExpect(view().name("admin/driver-import-preview"))
                .andExpect(model().attributeExists("preview"));
        // Visual H2-rendering of raw "2025_S2" name is verified via playwright-cli (Plan 60-05 Task 4)
    }

}
