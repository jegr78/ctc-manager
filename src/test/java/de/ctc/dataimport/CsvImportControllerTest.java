package de.ctc.dataimport;

import de.ctc.TestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class CsvImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestHelper testHelper;

    @Test
    void shouldShowImportForm() throws Exception {
        mockMvc.perform(get("/admin/import"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/import"))
                .andExpect(model().attributeExists("seasons", "sheetsAvailable", "playoffMatchups"));
    }

    @Test
    void shouldPreviewCsvUploadWithValidFile() throws Exception {
        var season = testHelper.createSeason("Import Preview Season");
        var matchday = testHelper.createMatchday(season, "Import MD1", 1);

        var csvContent = "Position,Driver,Team,Quali,Fastest Lap,Points\n1,TestDriver1,HOM,1,true,20\n2,TestDriver2,AWY,2,false,17";
        var file = new MockMultipartFile("file", "results.csv", "text/csv", csvContent.getBytes());

        mockMvc.perform(multipart("/admin/import/preview")
                        .file(file)
                        .param("seasonName", season.getName())
                        .param("matchdayLabel", matchday.getLabel())
                        .param("matchdayId", matchday.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldHandlePreviewErrorWithMalformedCsv() throws Exception {
        // Use binary garbage that cannot be parsed as CSV with expected columns
        var file = new MockMultipartFile("file", "bad.csv", "text/csv",
                new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x00});

        mockMvc.perform(multipart("/admin/import/preview")
                        .file(file)
                        .param("seasonName", "NonExistent Season 999")
                        .param("matchdayLabel", "MD1"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRedirectWithErrorOnExecuteWithoutFile() throws Exception {
        mockMvc.perform(post("/admin/import/execute")
                        .param("seasonName", "Test Season")
                        .param("source", "csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/import"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void shouldHandleExecuteWithInvalidCsv() throws Exception {
        var file = new MockMultipartFile("file", "bad.csv", "text/csv", "invalid,data".getBytes());

        mockMvc.perform(multipart("/admin/import/execute")
                        .file(file)
                        .param("seasonName", "NonExistent")
                        .param("source", "csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/import"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void shouldHandlePreviewSheetErrorWhenSheetsUnavailable() throws Exception {
        mockMvc.perform(post("/admin/import/preview-sheet")
                        .param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123")
                        .param("seasonName", "Test Season")
                        .param("matchdayLabel", "MD1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/import"))
                .andExpect(model().attributeExists("errorMessage"));
    }
}
