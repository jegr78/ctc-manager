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
     * @param url         the sheet URL expected by extractSpreadsheetId
     * @param tabsToRows  map of tabName → List of rows (each row = List&lt;Object&gt;)
     */
    private void setupSheetsStub(String url, Map<String, List<List<Object>>> tabsToRows) throws IOException {
        when(googleSheetsService.extractSpreadsheetId(url)).thenReturn(SPREADSHEET_ID);
        when(googleSheetsService.getSheetNames(SPREADSHEET_ID)).thenReturn(new ArrayList<>(tabsToRows.keySet()));
        for (Map.Entry<String, List<List<Object>>> entry : tabsToRows.entrySet()) {
            lenient().when(googleSheetsService.readRangeFromSheet(SPREADSHEET_ID, entry.getKey(), "A:C"))
                    .thenReturn(entry.getValue());
        }
    }

    // ---------------------------------------------------------------------------
    // Smoke test (Wave 0 skeleton)
    // ---------------------------------------------------------------------------

    @Test
    void givenSkeleton_whenCompiles_thenPasses() {
        assertThat(true).isTrue();
    }
}
