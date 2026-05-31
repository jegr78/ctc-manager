package org.ctc.dataimport;

import java.io.IOException;
import java.util.*;
import org.ctc.dataimport.DriverMatchingService.MatchResult;
import org.ctc.dataimport.DriverSheetImportService.DriverSheetImportPreview;
import org.ctc.dataimport.DriverSheetImportService.ErrorReason;
import org.ctc.dataimport.DriverSheetImportService.TabPreview;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverSheetImportServiceTest {

    private static final String SHEET_URL = "https://docs.google.com/spreadsheets/d/sheet-id-123";
    private static final String SPREADSHEET_ID = "sheet-id-123";

    @Mock
    private GoogleSheetsService googleSheetsService;
    @Mock
    private DriverMatchingService driverMatchingService;
    @Mock
    private SeasonRepository seasonRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private SeasonDriverRepository seasonDriverRepository;
    @Mock
    private org.ctc.domain.repository.DriverRepository driverRepository;
    @Mock
    private SeasonManagementService seasonManagementService;
    @Mock
    private SeasonPhaseService seasonPhaseService;
    @Mock
    private PhaseTeamRepository phaseTeamRepository;

    @InjectMocks
    private DriverSheetImportService driverSheetImportService;

    // Fixtures
    private Season season2024;
    private Season season2023;
    private Team teamAhr;
    private Team teamCrl;
    private Driver existingDriver;
    private SeasonDriver seasonDriverSameTeam;
    private SeasonDriver seasonDriverDifferentTeam;

    @BeforeEach
    void setUp() {
        season2024 = new Season();
        season2024.setId(UUID.randomUUID());
        season2024.setName("CTC Season 2024");
        season2024.setYear(2024);
        season2024.setNumber(1);

        season2023 = new Season();
        season2023.setId(UUID.randomUUID());
        season2023.setName("CTC Season 2023");
        season2023.setYear(2023);
        season2023.setNumber(1);

        teamAhr = new Team("Alpha Racing", "AHR");
        teamAhr.setId(UUID.randomUUID());

        teamCrl = new Team("Charlie Racing", "CRL");
        teamCrl.setId(UUID.randomUUID());

        existingDriver = new Driver("existing_psn", "Existing Driver");
        existingDriver.setId(UUID.randomUUID());

        seasonDriverSameTeam = new SeasonDriver(season2024, existingDriver, teamAhr);
        seasonDriverSameTeam.setId(UUID.randomUUID());

        seasonDriverDifferentTeam = new SeasonDriver(season2024, existingDriver, teamCrl);
        seasonDriverDifferentTeam.setId(UUID.randomUUID());
    }

    /**
     * Helper: stubs GoogleSheetsService to return specified tabs and their rows.
     * Each tab maps to a list of rows; caller supplies header + data rows combined.
     *
     * @param url        the sheet URL expected by extractSpreadsheetId
     * @param tabsToRows map of tabName → List of rows (each row = List&lt;Object&gt;)
     */
    private void setupSheetsStub(String url, Map<String, List<List<Object>>> tabsToRows) throws IOException {
        when(googleSheetsService.extractSpreadsheetId(url)).thenReturn(SPREADSHEET_ID);
        when(googleSheetsService.getSheetNames(SPREADSHEET_ID)).thenReturn(new ArrayList<>(tabsToRows.keySet()));
        for (Map.Entry<String, List<List<Object>>> entry : tabsToRows.entrySet()) {
            lenient().when(googleSheetsService.readRangeFromSheet(SPREADSHEET_ID, entry.getKey(), "A:C"))
                    .thenReturn(entry.getValue());
        }
    }

    /** Builds a header row + single data row (PSN, name, teamCode) for use in sheet stubs. */
    private List<List<Object>> oneDataRow(String psn, String name, String teamCode) {
        return List.of(
                List.of("PSN ID", "Name", "Team"),  // header
                List.of(psn, name, teamCode)         // data
        );
    }

    // 1. Tab filtering and sorting (IMPORT-02, IMPORT-04)

    @Test
    void givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded() throws IOException {
        // given
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2023", List.of(List.of("PSN ID", "Name", "Team")));
        tabs.put("Roster", List.of());
        tabs.put("2024", List.of(List.of("PSN ID", "Name", "Team")));
        tabs.put("Overall", List.of());
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(anyInt())).thenReturn(Optional.empty());

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        assertThat(preview.tabPreviews()).hasSize(2);
        assertThat(preview.tabPreviews()).extracting(TabPreview::tabName)
                .containsExactly("2023", "2024");
    }

    @Test
    void givenTabsInReverseOrder_whenPreview_thenTabsSortedAscendingByYear() throws IOException {
        // given — getSheetNames returns 2025 before 2023
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2025", List.of(List.of("PSN ID", "Name", "Team")));
        tabs.put("2023", List.of(List.of("PSN ID", "Name", "Team")));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(anyInt())).thenReturn(Optional.empty());

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — ascending by year
        assertThat(preview.tabPreviews()).extracting(TabPreview::year)
                .containsExactly(2023, 2025);
    }

    @Test
    void givenRowShorterThanThreeColumns_whenPreview_thenTreatedAsBlankTeamCode() throws IOException {
        // given — row has only PSN and name, no team column (IMPORT-03 defensive read)
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2024", List.of(
                List.of("PSN ID", "Name", "Team"),  // header
                List.of("short_row_psn", "Short Row") // only 2 columns — col 2 missing
        ));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — missing column C → treated as blank team code
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.errors()).hasSize(1);
        assertThat(tab.errors().get(0).reason()).isEqualTo(ErrorReason.BLANK_TEAM_CODE);
    }

    // 2. Season auto-match (IMPORT-05, DATA-01 — D-01..D-03)

    @Test
    void givenMultipleSeasonsForYear_whenPreview_thenSuggestedSeasonNullWithAmbiguousReason() throws IOException {
        // given — two seasons for 2024 → findUnique throws BusinessRuleException
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2024", List.of(List.of("PSN ID", "Name", "Team")));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(2024)).thenThrow(
                new BusinessRuleException("Multiple seasons exist for year 2024 — consolidate them first or rename sheet tab to disambiguate"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).contains("Multiple seasons exist for year 2024");
    }

    @Test
    void givenNoSeasonForYear_whenPreview_thenSuggestedSeasonNullWithNoSeasonReason() throws IOException {
        // given — no season exists for 2024
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2024", List.of(List.of("PSN ID", "Name", "Team")));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.empty());

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).contains("No season found for year 2024");
    }

    // 3. Bucket: NEW_DRIVER (UX-01)

    @Test
    void givenNewPsnId_whenPreview_thenCategorisedAsNewDriver() throws IOException {
        // given
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("brand_new_psn", "New Guy", "AHR")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(driverMatchingService.findDriver("brand_new_psn"))
                .thenReturn(MatchResult.noMatch("brand_new_psn"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.newDrivers()).hasSize(1);
        assertThat(tab.newDrivers().get(0).psnId()).isEqualTo("brand_new_psn");
        assertThat(tab.newDrivers().get(0).teamShortName()).isEqualTo("AHR");
        assertThat(tab.newAssignments()).isEmpty();
        assertThat(tab.errors()).isEmpty();
    }

    // 4. Bucket: NEW_ASSIGNMENT (UX-02)

    @Test
    void givenExistingDriverNoSeasonDriver_whenPreview_thenCategorisedAsNewAssignment() throws IOException {
        // given — driver exists but has no SeasonDriver for this season
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "AHR")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(driverMatchingService.findDriver("existing_psn"))
                .thenReturn(MatchResult.exact("existing_psn", existingDriver));
        when(seasonDriverRepository.findBySeasonIdAndDriverId(season2024.getId(), existingDriver.getId()))
                .thenReturn(Optional.empty());

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.newAssignments()).hasSize(1);
        assertThat(tab.newAssignments().get(0).existingDriverId()).isEqualTo(existingDriver.getId());
        assertThat(tab.newAssignments().get(0).teamShortName()).isEqualTo("AHR");
        assertThat(tab.conflicts()).isEmpty();
        assertThat(tab.unchanged()).isEmpty();
    }

    @Test
    void givenExistingDriverAndAmbiguousSeason_whenPreview_thenCategorisedAsNewAssignment() throws IOException {
        // given — two seasons for 2024 → suggestedSeasonId will be null (ambiguous)
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "AHR")));
        when(seasonManagementService.findUnique(2024)).thenThrow(
                new BusinessRuleException("Multiple seasons exist for year 2024 — consolidate them first or rename sheet tab to disambiguate"));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(driverMatchingService.findDriver("existing_psn"))
                .thenReturn(MatchResult.exact("existing_psn", existingDriver));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — no season resolved → fall through to NEW_ASSIGNMENT, no SeasonDriver lookup
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).contains("Multiple seasons exist for year 2024");
        assertThat(tab.newAssignments()).hasSize(1);
        assertThat(tab.newAssignments().get(0).existingDriverId()).isEqualTo(existingDriver.getId());
        assertThat(tab.newAssignments().get(0).teamShortName()).isEqualTo("AHR");
        assertThat(tab.unchanged()).isEmpty();
        assertThat(tab.conflicts()).isEmpty();
        // Pins the short-circuit invariant: SeasonDriver MUST NOT be queried when no season is resolved
        verifyNoInteractions(seasonDriverRepository);
    }

    // 5. Bucket: CONFLICT (UX-03)

    @Test
    void givenExistingSeasonDriverDifferentTeam_whenPreview_thenCategorisedAsConflict() throws IOException {
        // given — driver already in season with AHR, sheet says CRL
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "CRL")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("CRL")).thenReturn(List.of(teamCrl));
        when(driverMatchingService.findDriver("existing_psn"))
                .thenReturn(MatchResult.exact("existing_psn", existingDriver));
        // SeasonDriver records driver under AHR
        when(seasonDriverRepository.findBySeasonIdAndDriverId(season2024.getId(), existingDriver.getId()))
                .thenReturn(Optional.of(seasonDriverSameTeam)); // seasonDriverSameTeam has teamAhr

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.conflicts()).hasSize(1);
        var conflict = tab.conflicts().get(0);
        assertThat(conflict.psnId()).isEqualTo("existing_psn");
        assertThat(conflict.existingTeamShortName()).isEqualTo("AHR");
        assertThat(conflict.sheetTeamShortName()).isEqualTo("CRL");
        assertThat(tab.unchanged()).isEmpty();
    }

    // 6. Bucket: FUZZY_SUGGESTION (UX-04, MATCH-01)

    @Test
    void givenFuzzyCandidate_whenPreview_thenSuggestedMatchAwaitsUserOptIn() throws IOException {
        // given
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existng_psn", "Typo Guy", "AHR")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(driverMatchingService.findDriver("existng_psn"))
                .thenReturn(MatchResult.fuzzy("existng_psn", existingDriver, 0.9));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.fuzzySuggestions()).hasSize(1);
        var fuzzy = tab.fuzzySuggestions().get(0);
        assertThat(fuzzy.psnId()).isEqualTo("existng_psn");
        assertThat(fuzzy.suggestedDriverId()).isEqualTo(existingDriver.getId());
        assertThat(fuzzy.similarity()).isEqualTo(0.9);
        // Verify delegation to DriverMatchingService
        verify(driverMatchingService).findDriver("existng_psn");
        assertThat(tab.newDrivers()).isEmpty();
    }

    // 7. Bucket: UNCHANGED (UX-05)

    @Test
    void givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged() throws IOException {
        // given — driver already in season with AHR, sheet also says AHR
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "AHR")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(driverMatchingService.findDriver("existing_psn"))
                .thenReturn(MatchResult.exact("existing_psn", existingDriver));
        when(seasonDriverRepository.findBySeasonIdAndDriverId(season2024.getId(), existingDriver.getId()))
                .thenReturn(Optional.of(seasonDriverSameTeam)); // same team = AHR

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.unchanged()).hasSize(1);
        assertThat(tab.unchanged().get(0).existingDriverId()).isEqualTo(existingDriver.getId());
        // IR-03: UnchangedRow carries existingSeasonDriverId for symmetry with ConflictRow
        // so Phase 55 can emit an audit trail without re-fetching SeasonDriver.
        assertThat(tab.unchanged().get(0).existingSeasonDriverId()).isEqualTo(seasonDriverSameTeam.getId());
        assertThat(tab.conflicts()).isEmpty();
    }

    // 8. ERROR buckets (UX-06, DATA-02)

    @Test
    void givenBlankPsnId_whenPreview_thenRowErroredWithBlankPsn() throws IOException {
        // given — column A is blank
        Map<String, List<List<Object>>> tabs = Map.of("2024", List.of(
                List.of("PSN ID", "Name", "Team"),
                List.of("", "Nobody", "AHR")
        ));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.errors()).hasSize(1);
        assertThat(tab.errors().get(0).reason()).isEqualTo(ErrorReason.BLANK_PSN_ID);
        assertThat(tab.newDrivers()).isEmpty();
    }

    @Test
    void givenBlankTeamCode_whenPreview_thenRowErroredWithBlankTeam() throws IOException {
        // given — column C is blank
        Map<String, List<List<Object>>> tabs = Map.of("2024", List.of(
                List.of("PSN ID", "Name", "Team"),
                List.of("some_psn", "Someone", "")
        ));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.errors()).hasSize(1);
        assertThat(tab.errors().get(0).reason()).isEqualTo(ErrorReason.BLANK_TEAM_CODE);
    }

    @Test
    void givenUnknownTeamCode_whenPreview_thenRowErroredWithUnknownTeam() throws IOException {
        // given — team code not in repository
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("some_psn", "Someone", "XYZ")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("XYZ")).thenReturn(List.of());

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.errors()).hasSize(1);
        assertThat(tab.errors().get(0).reason()).isEqualTo(ErrorReason.UNKNOWN_TEAM_CODE);
        assertThat(tab.errors().get(0).teamCode()).isEqualTo("XYZ");
    }

    @Test
    void givenDuplicatePsnInTab_whenPreview_thenSecondRowErroredWithDuplicate() throws IOException {
        // given — same PSN appears twice in the same tab (D-11 first occurrence wins)
        Map<String, List<List<Object>>> tabs = Map.of("2024", List.of(
                List.of("PSN ID", "Name", "Team"),
                List.of("dup_psn", "Driver A", "AHR"),
                List.of("dup_psn", "Driver A Again", "CRL")
        ));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        // Both team codes must be resolvable so duplicate check (step 4) fires before team check (step 3)
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(teamRepository.findAllByShortName("CRL")).thenReturn(List.of(teamCrl));
        when(driverMatchingService.findDriver("dup_psn"))
                .thenReturn(MatchResult.noMatch("dup_psn"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — first occurrence goes to NEW_DRIVER, second to ERROR/DUPLICATE_IN_TAB
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.newDrivers()).hasSize(1);
        assertThat(tab.errors()).hasSize(1);
        assertThat(tab.errors().get(0).reason()).isEqualTo(ErrorReason.DUPLICATE_IN_TAB);
        assertThat(tab.errors().get(0).teamCode()).isEqualTo("CRL");
    }

    // 9. MATCH-01: case-insensitive matching delegates to DriverMatchingService

    @Test
    void givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitive() throws IOException {
        // given — DriverMatchingService handles CI internally and returns EXACT
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("EXISTING_PSN", "Driver", "AHR")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(driverMatchingService.findDriver("EXISTING_PSN"))
                .thenReturn(MatchResult.exact("EXISTING_PSN", existingDriver));
        when(seasonDriverRepository.findBySeasonIdAndDriverId(season2024.getId(), existingDriver.getId()))
                .thenReturn(Optional.empty());

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — resolved as EXACT via CI match inside DriverMatchingService → NEW_ASSIGNMENT
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.newAssignments()).hasSize(1);
        verify(driverMatchingService).findDriver("EXISTING_PSN");
    }

    // 10. MATCH-02: same PSN in multiple tabs → independent bucketing

    @Test
    void givenSamePsnInMultipleTabs_whenPreview_thenEachTabCategorisedIndependently() throws IOException {
        // given — "cross_psn" appears in both 2023 and 2024 tabs
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2023", oneDataRow("cross_psn", "Cross Driver", "AHR"));
        tabs.put("2024", oneDataRow("cross_psn", "Cross Driver", "CRL"));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonManagementService.findUnique(2023)).thenReturn(Optional.of(season2023));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(teamRepository.findAllByShortName("CRL")).thenReturn(List.of(teamCrl));
        // Both tabs: driver is brand-new (NONE match)
        when(driverMatchingService.findDriver("cross_psn"))
                .thenReturn(MatchResult.noMatch("cross_psn"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — two independent TabPreviews, each has one NEW_DRIVER row (not a duplicate error)
        assertThat(preview.tabPreviews()).hasSize(2);
        TabPreview tab2023 = preview.tabPreviews().get(0);
        TabPreview tab2024 = preview.tabPreviews().get(1);
        assertThat(tab2023.newDrivers()).hasSize(1);
        assertThat(tab2024.newDrivers()).hasSize(1);
        assertThat(tab2023.errors()).isEmpty();
        assertThat(tab2024.errors()).isEmpty();
        // DriverMatchingService called once per tab (2 times total)
        verify(driverMatchingService, times(2)).findDriver("cross_psn");
    }

    // 11. legacy tab pattern with single season auto-resolution

    @Test
    void givenLegacyFourDigitTab_whenPreview_thenSeasonResolvedViaFindUniqueByYear() throws IOException {
        // given
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("ahr-d1", "Driver", "AHR")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(driverMatchingService.findDriver("ahr-d1")).thenReturn(MatchResult.noMatch("ahr-d1"));
        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        // then
        var tab = preview.tabPreviews().get(0);
        assertThat(tab.tabName()).isEqualTo("2024");
        assertThat(tab.year()).isEqualTo(2024);
        assertThat(tab.number()).isNull();
        assertThat(tab.suggestedSeasonId()).isEqualTo(season2024.getId());
        assertThat(tab.ambiguousReason()).isNull();
    }

    // 12. Phase 59 new pattern 2025_S2 resolved via two-arg findUnique

    @Test
    void givenNumberedTab_whenPreview_thenSeasonResolvedViaFindUniqueByYearAndNumber() throws IOException {
        // given
        var season2025s2 = new Season();
        season2025s2.setId(UUID.randomUUID());
        season2025s2.setYear(2025);
        season2025s2.setNumber(2);
        setupSheetsStub(SHEET_URL, Map.of("2025_S2", oneDataRow("psn", "X", "AHR")));
        when(seasonManagementService.findUnique(2025, 2)).thenReturn(Optional.of(season2025s2));
        when(teamRepository.findAllByShortName("AHR")).thenReturn(List.of(teamAhr));
        when(driverMatchingService.findDriver("psn")).thenReturn(MatchResult.noMatch("psn"));
        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        // then
        var tab = preview.tabPreviews().get(0);
        assertThat(tab.tabName()).isEqualTo("2025_S2");
        assertThat(tab.year()).isEqualTo(2025);
        assertThat(tab.number()).isEqualTo(2);
        assertThat(tab.suggestedSeasonId()).isEqualTo(season2025s2.getId());
        assertThat(tab.ambiguousReason()).isNull();
    }

    // 13. ambiguous legacy tab surfaces BusinessRuleException as ambiguousReason

    @Test
    void givenAmbiguousLegacyTab_whenPreview_thenSurfacesBusinessRuleMessage() throws IOException {
        // given
        setupSheetsStub(SHEET_URL, Map.of("2023", oneDataRow("psn", "X", "AHR")));
        when(seasonManagementService.findUnique(2023)).thenThrow(
                new BusinessRuleException(
                        "Multiple seasons exist for year 2023 — consolidate them first or rename sheet tab to disambiguate"));
        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        // then
        var tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).contains("Multiple seasons exist for year 2023");
    }

    // 14. ambiguous numbered tab surfaces BusinessRuleException

    @Test
    void givenAmbiguousNumberedTab_whenPreview_thenSurfacesBusinessRuleMessage() throws IOException {
        // given
        setupSheetsStub(SHEET_URL, Map.of("2023_S1", oneDataRow("psn", "X", "AHR")));
        when(seasonManagementService.findUnique(2023, 1)).thenThrow(
                new BusinessRuleException(
                        "Multiple seasons exist for (2023, 1) — consolidate them first or rename sheet tab to disambiguate"));
        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);
        // then
        var tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).contains("Multiple seasons exist for (2023, 1)");
    }

    // 21. Gap-66-02 — legacy season with no REGULAR phase: collision falls back to parent precedence

    @Test
    void givenSeasonHasNoRegularPhase_whenPreviewWithCollision_thenFallsBackToParentPrecedence() throws IOException {
        // given — season without REGULAR phase + parent + sub collision
        Team parentZfs = new Team("ZF Schweinfurt", "ZFS");
        parentZfs.setId(UUID.randomUUID());
        Team subZfs = new Team("ZF Schweinfurt 1", "ZFS", parentZfs);
        subZfs.setId(UUID.randomUUID());

        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("zfs_driver", "ZFS Driver", "ZFS")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("ZFS")).thenReturn(List.of(parentZfs, subZfs));
        when(driverMatchingService.findDriver("zfs_driver"))
                .thenReturn(MatchResult.noMatch("zfs_driver"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — parent picked (legacy fallback), no PhaseTeam interactions, no warnings
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.newDrivers()).hasSize(1);
        assertThat(tab.newDrivers().get(0).teamShortName()).isEqualTo("ZFS");
        assertThat(tab.errors()).isEmpty();
    }

    // 22. Phase 66 D-12 retained — two parent teams with same shortName, no REGULAR phase: first wins, no exception

    @Test
    void givenLegacyPath_whenTwoParentTeamsCollideWithoutRegularPhase_thenFirstParentWinsWithoutException() throws IOException {
        // given — data-integrity edge: two parents share shortName, no REGULAR phase
        Team parentA = new Team("Alpha", "DUP");
        parentA.setId(UUID.randomUUID());
        Team parentB = new Team("Bravo", "DUP");
        parentB.setId(UUID.randomUUID());

        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("dup_driver", "Dup Driver", "DUP")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("DUP")).thenReturn(List.of(parentA, parentB));
        when(driverMatchingService.findDriver("dup_driver"))
                .thenReturn(MatchResult.noMatch("dup_driver"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — first parent wins via WARN log (preserved Phase 66 D-07 semantics)
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.newDrivers()).hasSize(1);
        assertThat(tab.newDrivers().get(0).teamShortName()).isEqualTo("DUP");
        assertThat(tab.errors()).isEmpty();
    }

    // 23. Phase 70 D-13 — parent-always regression: parent MRL + 2 subs in 2 groups,
    // sheet references parent shortName "T-MRL" → resolved team is parent, no warning, no errors.
    // Test-data prefix per CLAUDE.md `Isolate Test Data Completely` (D-14).

    @Test
    void givenSheetReferencesParentShortNameWithSubsInGroupsPhase_whenPreview_thenAssignsParentNoWarning() throws IOException {
        // given — parent + 2 subs all sharing shortName "T-MRL"
        // (mirrors live UAT data: parent MRL + sub MRL 1 + sub MRL 2 in different Groups)
        Team parentMrl = new Team("Test-MRL Parent", "T-MRL");
        parentMrl.setId(UUID.randomUUID());
        Team subMrl1 = new Team("Test-MRL Sub 1", "T-MRL", parentMrl);
        subMrl1.setId(UUID.randomUUID());
        Team subMrl2 = new Team("Test-MRL Sub 2", "T-MRL", parentMrl);
        subMrl2.setId(UUID.randomUUID());

        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("t-mrl-driver", "Test MRL Driver", "T-MRL")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("T-MRL")).thenReturn(List.of(parentMrl, subMrl1, subMrl2));
        when(driverMatchingService.findDriver("t-mrl-driver"))
                .thenReturn(MatchResult.noMatch("t-mrl-driver"));

        // when — preview categorises as NEW_DRIVER without consulting any phase data
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — parent picked unconditionally; no error, single newDriver row with shortName T-MRL
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.newDrivers()).hasSize(1);
        assertThat(tab.newDrivers().get(0).teamShortName()).isEqualTo("T-MRL");
        assertThat(tab.errors()).isEmpty();

        // also-then — execute writes SeasonDriver pointing at the parent (not a sub),
        // proving the resolver picks the parent on multi-match regardless of sub-team data.
        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2024", season2024.getId().toString());
        when(seasonRepository.findById(season2024.getId())).thenReturn(Optional.of(season2024));
        ArgumentCaptor<SeasonDriver> captor = ArgumentCaptor.forClass(SeasonDriver.class);
        when(seasonDriverRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        driverSheetImportService.execute(SHEET_URL, params);

        SeasonDriver written = captor.getValue();
        assertThat(written.getTeam().getId()).isEqualTo(parentMrl.getId());
    }

    // 24. DRIV-01 happy path — parent (no PhaseTeam) + sub (with PhaseTeam in target REGULAR phase):
    // sub wins, overriding the legacy parent-precedence rule.

    @Test
    void givenMultiMatchShortNameAndSubHasPhaseTeam_whenPreview_thenSubTeamWinsOverParent() throws IOException {
        // given — parent T-MRL (no PhaseTeam) + sub T-MRL (PhaseTeam present in REGULAR phase)
        Team parentMrl = new Team("Test-MRL Parent", "T-MRL");
        parentMrl.setId(UUID.randomUUID());
        Team subMrl = new Team("Test-MRL Sub", "T-MRL", parentMrl);
        subMrl.setId(UUID.randomUUID());

        SeasonPhase regularPhase = PhaseTestFixtures.groupsRegularPhase(season2024, null, null, "Group A");
        when(seasonPhaseService.findByType(season2024.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.of(regularPhase));
        when(phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), parentMrl.getId()))
                .thenReturn(Optional.empty());
        PhaseTeam subPt = PhaseTestFixtures.assignTeam(regularPhase, subMrl, regularPhase.getGroups().get(0));
        when(phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), subMrl.getId()))
                .thenReturn(Optional.of(subPt));

        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("t-mrl-sub-driver", "Sub Driver", "T-MRL")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("T-MRL")).thenReturn(List.of(parentMrl, subMrl));
        when(driverMatchingService.findDriver("t-mrl-sub-driver"))
                .thenReturn(MatchResult.noMatch("t-mrl-sub-driver"));

        // when — execute persists SeasonDriver pointing at the SUB team, not the parent
        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2024", season2024.getId().toString());
        when(seasonRepository.findById(season2024.getId())).thenReturn(Optional.of(season2024));
        ArgumentCaptor<SeasonDriver> captor = ArgumentCaptor.forClass(SeasonDriver.class);
        when(seasonDriverRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        driverSheetImportService.execute(SHEET_URL, params);

        // then — sub wins because it has the PhaseTeam in the target REGULAR phase
        SeasonDriver written = captor.getValue();
        assertThat(written.getTeam().getId()).isEqualTo(subMrl.getId());
    }

    // 25. DRIV-01 legacy fallback — multi-match where NO candidate has a PhaseTeam in the target
    // REGULAR phase: resolver falls through to parent-precedence (preserves Phase 66/70 semantics).

    @Test
    void givenMultiMatchShortNameAndNoPhaseTeamForAnyCandidate_whenPreview_thenParentWins() throws IOException {
        // given — parent + sub, neither has a PhaseTeam in the target REGULAR phase
        Team parentMrl = new Team("Test-MRL Parent", "T-MRL");
        parentMrl.setId(UUID.randomUUID());
        Team subMrl = new Team("Test-MRL Sub", "T-MRL", parentMrl);
        subMrl.setId(UUID.randomUUID());

        SeasonPhase regularPhase = PhaseTestFixtures.regularPhase(season2024, null, null);
        when(seasonPhaseService.findByType(season2024.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.of(regularPhase));
        when(phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), parentMrl.getId()))
                .thenReturn(Optional.empty());
        when(phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), subMrl.getId()))
                .thenReturn(Optional.empty());

        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("t-mrl-driver", "Driver", "T-MRL")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("T-MRL")).thenReturn(List.of(parentMrl, subMrl));
        when(driverMatchingService.findDriver("t-mrl-driver"))
                .thenReturn(MatchResult.noMatch("t-mrl-driver"));

        // when
        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2024", season2024.getId().toString());
        when(seasonRepository.findById(season2024.getId())).thenReturn(Optional.of(season2024));
        ArgumentCaptor<SeasonDriver> captor = ArgumentCaptor.forClass(SeasonDriver.class);
        when(seasonDriverRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        driverSheetImportService.execute(SHEET_URL, params);

        // then — parent wins via legacy fallback (D-07 semantics preserved)
        SeasonDriver written = captor.getValue();
        assertThat(written.getTeam().getId()).isEqualTo(parentMrl.getId());
    }

    // 26. DRIV-01 legacy season — no REGULAR phase at all (pre-V4 data): resolver falls through to
    // parent-precedence without calling phaseTeamRepository, WARN-free and exception-free (D-07).

    @Test
    void givenLegacySeasonWithoutRegularPhaseAndMultiMatchShortName_whenPreview_thenParentWins() throws IOException {
        // given — multi-match collision + season has NO REGULAR phase (legacy data)
        Team parentMrl = new Team("Test-MRL Parent", "T-MRL");
        parentMrl.setId(UUID.randomUUID());
        Team subMrl = new Team("Test-MRL Sub", "T-MRL", parentMrl);
        subMrl.setId(UUID.randomUUID());

        when(seasonPhaseService.findByType(season2024.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.empty());

        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("legacy-driver", "Legacy", "T-MRL")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("T-MRL")).thenReturn(List.of(parentMrl, subMrl));
        when(driverMatchingService.findDriver("legacy-driver"))
                .thenReturn(MatchResult.noMatch("legacy-driver"));

        // when — execute does NOT throw BusinessRuleException; falls back to parent
        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2024", season2024.getId().toString());
        when(seasonRepository.findById(season2024.getId())).thenReturn(Optional.of(season2024));
        ArgumentCaptor<SeasonDriver> captor = ArgumentCaptor.forClass(SeasonDriver.class);
        when(seasonDriverRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        driverSheetImportService.execute(SHEET_URL, params);

        // then — parent wins, no PhaseTeam lookup attempted
        SeasonDriver written = captor.getValue();
        assertThat(written.getTeam().getId()).isEqualTo(parentMrl.getId());
        verifyNoInteractions(phaseTeamRepository);
    }

    // 27. DRIV-01 data-integrity edge — multi-match where NEITHER candidate has a PhaseTeam AND no
    // candidate has parentTeam==null: resolver returns the first match deterministically with a
    // WARN log (D-07 — no BusinessRuleException).

    @Test
    void givenMultiMatchShortNameWithNoParentAndNoPhaseTeam_whenPreview_thenFirstMatchWinsWithoutException() throws IOException {
        // given — two sub-teams sharing shortName, both have non-null parentTeam refs (synthetic
        // data-integrity edge), and neither has a PhaseTeam in the target REGULAR phase
        Team distantParent = new Team("Test-Parent Distant", "OTHER-PARENT");
        distantParent.setId(UUID.randomUUID());
        Team subA = new Team("Test-Sub A", "T-DUP", distantParent);
        subA.setId(UUID.randomUUID());
        Team subB = new Team("Test-Sub B", "T-DUP", distantParent);
        subB.setId(UUID.randomUUID());

        SeasonPhase regularPhase = PhaseTestFixtures.regularPhase(season2024, null, null);
        when(seasonPhaseService.findByType(season2024.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.of(regularPhase));
        when(phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), subA.getId()))
                .thenReturn(Optional.empty());
        when(phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.getId(), subB.getId()))
                .thenReturn(Optional.empty());

        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("edge-driver", "Edge", "T-DUP")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("T-DUP")).thenReturn(List.of(subA, subB));
        when(driverMatchingService.findDriver("edge-driver"))
                .thenReturn(MatchResult.noMatch("edge-driver"));

        // when — execute does NOT throw; first match returned deterministically
        Map<String, String> params = new LinkedHashMap<>();
        params.put("seasonId_2024", season2024.getId().toString());
        when(seasonRepository.findById(season2024.getId())).thenReturn(Optional.of(season2024));
        ArgumentCaptor<SeasonDriver> captor = ArgumentCaptor.forClass(SeasonDriver.class);
        when(seasonDriverRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        driverSheetImportService.execute(SHEET_URL, params);

        // then — first match returned (subA), WARN-logged, no exception
        SeasonDriver written = captor.getValue();
        assertThat(written.getTeam().getId()).isEqualTo(subA.getId());
    }

    // 28. DRIV-02 — LEAGUE-layout REGULAR phase reports usesGroups=false so future Group-aware
    // UI surfaces (e.g. the page-wide showGroupColumn aggregation in DriverSheetImportController)
    // suppress group rendering on non-GROUPS tabs.

    @Test
    void givenLeagueLayoutRegularPhase_whenPreview_thenTabPreviewUsesGroupsIsFalse() throws IOException {
        // given — season with a LEAGUE-layout REGULAR phase
        SeasonPhase leaguePhase = PhaseTestFixtures.regularPhase(season2024, null, null);
        when(seasonPhaseService.findByType(season2024.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.of(leaguePhase));

        Team team = new Team("Test-League Team", "T-LG");
        team.setId(UUID.randomUUID());
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("lg-driver", "League Driver", "T-LG")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("T-LG")).thenReturn(List.of(team));
        when(driverMatchingService.findDriver("lg-driver"))
                .thenReturn(MatchResult.noMatch("lg-driver"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — usesGroups is false because the phase is LEAGUE-layout
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.usesGroups()).isFalse();
    }

    // 29. DRIV-02 — GROUPS-layout REGULAR phase reports usesGroups=true so the controller's
    // showGroupColumn aggregation lights up the page-wide Group column for mixed multi-tab previews.

    @Test
    void givenGroupsLayoutRegularPhase_whenPreview_thenTabPreviewUsesGroupsIsTrue() throws IOException {
        // given — season with a GROUPS-layout REGULAR phase
        SeasonPhase groupsPhase = PhaseTestFixtures.groupsRegularPhase(season2024, null, null, "Group A", "Group B");
        when(seasonPhaseService.findByType(season2024.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.of(groupsPhase));

        Team team = new Team("Test-Groups Team", "T-GR");
        team.setId(UUID.randomUUID());
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("gr-driver", "Groups Driver", "T-GR")));
        when(seasonManagementService.findUnique(2024)).thenReturn(Optional.of(season2024));
        when(teamRepository.findAllByShortName("T-GR")).thenReturn(List.of(team));
        when(driverMatchingService.findDriver("gr-driver"))
                .thenReturn(MatchResult.noMatch("gr-driver"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — usesGroups is true because the phase is GROUPS-layout
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.usesGroups()).isTrue();
    }

}
