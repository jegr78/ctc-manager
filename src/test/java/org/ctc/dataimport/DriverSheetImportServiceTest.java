package org.ctc.dataimport;

import org.ctc.dataimport.DriverMatchingService.MatchResult;
import org.ctc.dataimport.DriverMatchingService.MatchType;
import org.ctc.dataimport.DriverSheetImportService.DriverSheetImportPreview;
import org.ctc.dataimport.DriverSheetImportService.ErrorReason;
import org.ctc.dataimport.DriverSheetImportService.TabPreview;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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

    // ---------------------------------------------------------------------------
    // 1. Tab filtering and sorting (IMPORT-02, IMPORT-04)
    // ---------------------------------------------------------------------------

    @Test
    void givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded() throws IOException {
        // given
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2023", List.of(List.of("PSN ID", "Name", "Team")));
        tabs.put("Roster", List.of());
        tabs.put("2024", List.of(List.of("PSN ID", "Name", "Team")));
        tabs.put("Overall", List.of());
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonRepository.findByYear(anyInt())).thenReturn(List.of());

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
        when(seasonRepository.findByYear(anyInt())).thenReturn(List.of());

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
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — missing column C → treated as blank team code
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.errors()).hasSize(1);
        assertThat(tab.errors().get(0).reason()).isEqualTo(ErrorReason.BLANK_TEAM_CODE);
    }

    // ---------------------------------------------------------------------------
    // 2. Season auto-match (IMPORT-05, DATA-01 — D-01..D-03)
    // ---------------------------------------------------------------------------

    @Test
    void givenMultipleSeasonsForYear_whenPreview_thenSuggestedSeasonNullWithAmbiguousReason() throws IOException {
        // given — two seasons for 2024
        Season season2024b = new Season();
        season2024b.setId(UUID.randomUUID());
        season2024b.setYear(2024);
        season2024b.setNumber(2);

        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2024", List.of(List.of("PSN ID", "Name", "Team")));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024, season2024b));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).contains("Multiple seasons for year 2024");
    }

    @Test
    void givenNoSeasonForYear_whenPreview_thenSuggestedSeasonNullWithNoSeasonReason() throws IOException {
        // given — no season exists for 2024
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2024", List.of(List.of("PSN ID", "Name", "Team")));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonRepository.findByYear(2024)).thenReturn(List.of());

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).contains("No season found for year 2024");
    }

    // ---------------------------------------------------------------------------
    // 3. Bucket: NEW_DRIVER (UX-01)
    // ---------------------------------------------------------------------------

    @Test
    void givenNewPsnId_whenPreview_thenCategorisedAsNewDriver() throws IOException {
        // given
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("brand_new_psn", "New Guy", "AHR")));
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
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

    // ---------------------------------------------------------------------------
    // 4. Bucket: NEW_ASSIGNMENT (UX-02)
    // ---------------------------------------------------------------------------

    @Test
    void givenExistingDriverNoSeasonDriver_whenPreview_thenCategorisedAsNewAssignment() throws IOException {
        // given — driver exists but has no SeasonDriver for this season
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "AHR")));
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
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
        Season season2024b = new Season();
        season2024b.setId(UUID.randomUUID());
        season2024b.setYear(2024);
        season2024b.setNumber(2);

        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "AHR")));
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024, season2024b));
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
        when(driverMatchingService.findDriver("existing_psn"))
                .thenReturn(MatchResult.exact("existing_psn", existingDriver));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — no season resolved → fall through to NEW_ASSIGNMENT, no SeasonDriver lookup
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.suggestedSeasonId()).isNull();
        assertThat(tab.ambiguousReason()).contains("Multiple seasons for year 2024");
        assertThat(tab.newAssignments()).hasSize(1);
        assertThat(tab.newAssignments().get(0).existingDriverId()).isEqualTo(existingDriver.getId());
        assertThat(tab.newAssignments().get(0).teamShortName()).isEqualTo("AHR");
        assertThat(tab.unchanged()).isEmpty();
        assertThat(tab.conflicts()).isEmpty();
        // Pins the short-circuit invariant: SeasonDriver MUST NOT be queried when no season is resolved
        verifyNoInteractions(seasonDriverRepository);
    }

    // ---------------------------------------------------------------------------
    // 5. Bucket: CONFLICT (UX-03)
    // ---------------------------------------------------------------------------

    @Test
    void givenExistingSeasonDriverDifferentTeam_whenPreview_thenCategorisedAsConflict() throws IOException {
        // given — driver already in season with AHR, sheet says CRL
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "CRL")));
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        when(teamRepository.findByShortName("CRL")).thenReturn(Optional.of(teamCrl));
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

    // ---------------------------------------------------------------------------
    // 6. Bucket: FUZZY_SUGGESTION (UX-04, MATCH-01)
    // ---------------------------------------------------------------------------

    @Test
    void givenFuzzyCandidate_whenPreview_thenSuggestedMatchAwaitsUserOptIn() throws IOException {
        // given
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existng_psn", "Typo Guy", "AHR")));
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
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

    // ---------------------------------------------------------------------------
    // 7. Bucket: UNCHANGED (UX-05)
    // ---------------------------------------------------------------------------

    @Test
    void givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged() throws IOException {
        // given — driver already in season with AHR, sheet also says AHR
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("existing_psn", "Existing", "AHR")));
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
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
        assertThat(tab.conflicts()).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // 8. ERROR buckets (UX-06, DATA-02)
    // ---------------------------------------------------------------------------

    @Test
    void givenBlankPsnId_whenPreview_thenRowErroredWithBlankPsn() throws IOException {
        // given — column A is blank
        Map<String, List<List<Object>>> tabs = Map.of("2024", List.of(
                List.of("PSN ID", "Name", "Team"),
                List.of("", "Nobody", "AHR")
        ));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));

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
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));

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
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        when(teamRepository.findByShortName("XYZ")).thenReturn(Optional.empty());

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.errors()).hasSize(1);
        assertThat(tab.errors().get(0).reason()).isEqualTo(ErrorReason.UNKNOWN_TEAM_CODE);
        assertThat(tab.errors().get(0).rawTeamCode()).isEqualTo("XYZ");
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
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        // Both team codes must be resolvable so duplicate check (step 4) fires before team check (step 3)
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
        when(teamRepository.findByShortName("CRL")).thenReturn(Optional.of(teamCrl));
        when(driverMatchingService.findDriver("dup_psn"))
                .thenReturn(MatchResult.noMatch("dup_psn"));

        // when
        DriverSheetImportPreview preview = driverSheetImportService.preview(SHEET_URL);

        // then — first occurrence goes to NEW_DRIVER, second to ERROR/DUPLICATE_IN_TAB
        TabPreview tab = preview.tabPreviews().get(0);
        assertThat(tab.newDrivers()).hasSize(1);
        assertThat(tab.errors()).hasSize(1);
        assertThat(tab.errors().get(0).reason()).isEqualTo(ErrorReason.DUPLICATE_IN_TAB);
        assertThat(tab.errors().get(0).rawTeamCode()).isEqualTo("CRL");
    }

    // ---------------------------------------------------------------------------
    // 9. MATCH-01: case-insensitive matching delegates to DriverMatchingService
    // ---------------------------------------------------------------------------

    @Test
    void givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitive() throws IOException {
        // given — DriverMatchingService handles CI internally and returns EXACT
        setupSheetsStub(SHEET_URL, Map.of("2024", oneDataRow("EXISTING_PSN", "Driver", "AHR")));
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
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

    // ---------------------------------------------------------------------------
    // 10. MATCH-02: same PSN in multiple tabs → independent bucketing (D-07)
    // ---------------------------------------------------------------------------

    @Test
    void givenSamePsnInMultipleTabs_whenPreview_thenEachTabCategorisedIndependently() throws IOException {
        // given — "cross_psn" appears in both 2023 and 2024 tabs
        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put("2023", oneDataRow("cross_psn", "Cross Driver", "AHR"));
        tabs.put("2024", oneDataRow("cross_psn", "Cross Driver", "CRL"));
        setupSheetsStub(SHEET_URL, tabs);
        when(seasonRepository.findByYear(2023)).thenReturn(List.of(season2023));
        when(seasonRepository.findByYear(2024)).thenReturn(List.of(season2024));
        when(teamRepository.findByShortName("AHR")).thenReturn(Optional.of(teamAhr));
        when(teamRepository.findByShortName("CRL")).thenReturn(Optional.of(teamCrl));
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
}
