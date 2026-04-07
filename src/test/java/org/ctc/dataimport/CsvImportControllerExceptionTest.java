package org.ctc.dataimport;

import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused tests for CsvImportController exception-handling behavior.
 * Uses @MockitoBean to verify narrowed catch blocks (IOException | IllegalArgumentException).
 * Normal controller behavior is covered in CsvImportControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CsvImportControllerExceptionTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CsvImportService csvImportService;
    @MockitoBean private GoogleSheetsService googleSheetsService;
    @MockitoBean private ScorecardParser scorecardParser;

    @Test
    void givenIoException_whenPreviewCsv_thenRedirectsWithError() throws Exception {
        // given
        when(csvImportService.parseAndPreview(any(), any()))
                .thenThrow(new IOException("file read error"));
        when(csvImportService.getAllSeasons()).thenReturn(List.of());
        when(csvImportService.getPlayoffMatchups()).thenReturn(List.of());

        var file = new MockMultipartFile("file", "results.csv", "text/csv", "data".getBytes());

        // when
        mockMvc.perform(multipart("/admin/import/preview")
                        .file(file)
                        .param("seasonId", UUID.randomUUID().toString())
                        .param("matchdayLabel", "MD1"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/import"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void givenIoException_whenPreviewSheet_thenRedirectsWithError() throws Exception {
        // given
        when(googleSheetsService.isAvailable()).thenReturn(true);
        when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
        when(googleSheetsService.readRange(anyString(), anyString()))
                .thenThrow(new IOException("network error"));
        when(csvImportService.getAllSeasons()).thenReturn(List.of());
        when(csvImportService.getPlayoffMatchups()).thenReturn(List.of());

        // when
        mockMvc.perform(post("/admin/import/preview-sheet")
                        .param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123")
                        .param("seasonId", UUID.randomUUID().toString())
                        .param("matchdayLabel", "MD1"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/import"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void givenBusinessRuleException_whenExecuteImport_thenRedirectsWithError() throws Exception {
        // given
        when(csvImportService.parseAndPreview(any(), any()))
                .thenThrow(new BusinessRuleException("duplicate entry"));
        when(csvImportService.getAllSeasons()).thenReturn(List.of());
        when(csvImportService.getPlayoffMatchups()).thenReturn(List.of());

        var file = new MockMultipartFile("file", "results.csv", "text/csv", "data".getBytes());

        // when
        mockMvc.perform(multipart("/admin/import/execute")
                        .file(file)
                        .param("seasonId", UUID.randomUUID().toString())
                        .param("source", "csv"))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/import"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
