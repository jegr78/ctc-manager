package org.ctc.dataimport;

import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class CsvImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestHelper testHelper;
    @Autowired private DriverRepository driverRepository;
    @Autowired private TeamRepository teamRepository;

    @Test
    void whenGetImportPage_thenShowsImportForm() throws Exception {
        // when / then
        mockMvc.perform(get("/admin/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/import"))
                .andExpect(model().attributeExists("seasons", "sheetsAvailable", "playoffMatchups"));
    }

    @Test
    void givenValidCsvFile_whenPreview_thenReturnsOk() throws Exception {
        // given
        var season = testHelper.createSeason("Import Preview Season");
        var matchday = testHelper.createMatchday(season, "Import MD1", 1);

        var csvContent = "Position,Driver,Team,Quali,Fastest Lap,Points\n1,TestDriver1,HOM,1,true,20\n2,TestDriver2,AWY,2,false,17";
        var file = new MockMultipartFile("file", "results.csv", "text/csv", csvContent.getBytes());

        // when / then
        mockMvc.perform(multipart("/admin/import/preview")
                        .file(file)
                        .param("seasonId", season.getId().toString())
                        .param("matchdayLabel", matchday.getLabel())
                        .param("matchdayId", matchday.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void givenMalformedCsvFile_whenPreview_thenHandlesErrorGracefully() throws Exception {
        // given
        // Use binary garbage that cannot be parsed as CSV with expected columns
        var file = new MockMultipartFile("file", "bad.csv", "text/csv",
                new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x00});

        // when / then
        mockMvc.perform(multipart("/admin/import/preview")
                        .file(file)
                        .param("seasonId", UUID.randomUUID().toString())
                        .param("matchdayLabel", "MD1"))
                .andExpect(status().isOk());
    }

    @Test
    void givenNoFile_whenExecute_thenRedirectsWithError() throws Exception {
        // when / then
        mockMvc.perform(post("/admin/import/execute")
                        .param("seasonId", UUID.randomUUID().toString())
                        .param("source", "csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/import"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenInvalidCsvFile_whenExecute_thenRedirectsWithError() throws Exception {
        // given
        var file = new MockMultipartFile("file", "bad.csv", "text/csv", "invalid,data".getBytes());

        // when / then
        mockMvc.perform(multipart("/admin/import/execute")
                        .file(file)
                        .param("seasonId", UUID.randomUUID().toString())
                        .param("source", "csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/import"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenSheetsUnavailable_whenPreviewSheet_thenShowsErrorMessage() throws Exception {
        // when / then
        mockMvc.perform(post("/admin/import/preview-sheet")
                        .param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123")
                        .param("seasonId", UUID.randomUUID().toString())
                        .param("matchdayLabel", "MD1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/import"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    // --- POST /admin/import/execute with valid CSV ---

    @Test
    void givenValidCsvWithMatchedDrivers_whenExecute_thenRedirectsWithSuccess() throws Exception {
        // given
        var fixture = testHelper.createFullSeasonFixture("CsvExec");
        var season = fixture.season();
        var homeTeam = fixture.homeTeam();
        var awayTeam = fixture.awayTeam();

        // Create drivers that exist in DB (exact match)
        var driver1 = driverRepository.save(new Driver("csv_exec_drv1", "CsvExecDriver1"));
        var driver2 = driverRepository.save(new Driver("csv_exec_drv2", "CsvExecDriver2"));

        // CSV with correct format: Team, PSN ID, Position, Quali, FL
        var csvContent = homeTeam.getShortName() + ",csv_exec_drv1,1,1,true\n"
                + awayTeam.getShortName() + ",csv_exec_drv2,2,2,false";
        var file = new MockMultipartFile("file", "results.csv", "text/csv", csvContent.getBytes());

        // Use a new matchday label so no duplicate
        // when / then
        mockMvc.perform(multipart("/admin/import/execute")
                        .file(file)
                        .param("seasonId", season.getId().toString())
                        .param("matchdayLabel", "CsvExec ImportMD")
                        .param("source", "csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/import"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    // --- POST /admin/import/preview with valid data ---

    @Test
    void givenValidCsvWithMatchedDrivers_whenPreview_thenShowsPreviewView() throws Exception {
        // given
        var fixture = testHelper.createFullSeasonFixture("CsvPrev");
        var season = fixture.season();
        var homeTeam = fixture.homeTeam();
        var awayTeam = fixture.awayTeam();

        var driver1 = driverRepository.save(new Driver("csv_prev_drv1", "CsvPrevDriver1"));
        var driver2 = driverRepository.save(new Driver("csv_prev_drv2", "CsvPrevDriver2"));

        var csvContent = homeTeam.getShortName() + ",csv_prev_drv1,1,1,true\n"
                + awayTeam.getShortName() + ",csv_prev_drv2,2,2,false";
        var file = new MockMultipartFile("file", "results.csv", "text/csv", csvContent.getBytes());

        // when / then
        mockMvc.perform(multipart("/admin/import/preview")
                        .file(file)
                        .param("seasonId", season.getId().toString())
                        .param("matchdayLabel", "CsvPrev ImportMD"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/import-preview"))
                .andExpect(model().attributeExists("preview", "metadata"));
    }

    // --- POST /admin/import/execute with overwrite ---

    @Test
    void givenExistingImportAndOverwriteEnabled_whenExecute_thenRedirectsToImport() throws Exception {
        // given
        var fixture = testHelper.createFullSeasonFixture("CsvOver");
        var season = fixture.season();
        var homeTeam = fixture.homeTeam();
        var awayTeam = fixture.awayTeam();
        var matchday = fixture.matchday();

        var driver1 = driverRepository.save(new Driver("csv_over_drv1", "CsvOverDriver1"));
        var driver2 = driverRepository.save(new Driver("csv_over_drv2", "CsvOverDriver2"));

        var csvContent = homeTeam.getShortName() + ",csv_over_drv1,1,1,true\n"
                + awayTeam.getShortName() + ",csv_over_drv2,2,2,false";
        var file = new MockMultipartFile("file", "results.csv", "text/csv", csvContent.getBytes());

        // First import
        mockMvc.perform(multipart("/admin/import/execute")
                        .file(file)
                        .param("seasonId", season.getId().toString())
                        .param("matchdayId", matchday.getId().toString())
                        .param("source", "csv"))
                .andExpect(status().is3xxRedirection());

        // when
        // Second import with overwrite=true
        var file2 = new MockMultipartFile("file", "results.csv", "text/csv", csvContent.getBytes());
        // then
        mockMvc.perform(multipart("/admin/import/execute")
                        .file(file2)
                        .param("seasonId", season.getId().toString())
                        .param("matchdayId", matchday.getId().toString())
                        .param("source", "csv")
                        .param("overwrite", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/import"));
    }
}
