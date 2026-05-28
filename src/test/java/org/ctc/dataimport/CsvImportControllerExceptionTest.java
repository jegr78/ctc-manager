package org.ctc.dataimport;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused tests for CsvImportController exception-handling behavior.
 * Verifies the 5-arm typed-catch surface (4 sealed permits + defensive base) on
 * previewSheet() and execute(), and the typed-catch info-leak invariant: typed
 * GoogleApiException arms must render a whitelisted literal — never echo
 * e.getMessage().
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CsvImportControllerExceptionTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CsvImportService csvImportService;
	@MockitoBean
	private GoogleSheetsService googleSheetsService;
	@MockitoBean
	private ScorecardParser scorecardParser;

	@Test
	void givenIoException_whenPreviewCsv_thenGenericErrorWithoutRawCauseEcho() throws Exception {
		// given — raw exception message must NOT bleed into the user-facing flash
		String rawCause = "DANGEROUS_PAYLOAD_<script>alert(1)</script>";
		when(csvImportService.parseAndPreview(any(), any()))
				.thenThrow(new IOException(rawCause));
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
				.andExpect(model().attribute("errorMessage",
						org.hamcrest.Matchers.startsWith("Error reading CSV")))
				.andExpect(model().attribute("errorMessage",
						org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(rawCause))));
	}

	@Test
	void givenAuthFailure_whenPreviewSheet_thenRendersAuthBadge() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new AuthGoogleApiException("auth failure", null));
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
				.andExpect(model().attribute("errorMessage", equalTo("Authentication problem — re-link Google account")))
				.andExpect(model().attribute("errorCategory", equalTo("AUTH")));
	}

	@Test
	void givenNotFound_whenPreviewSheet_thenRendersNotFoundBadge() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new NotFoundGoogleApiException("404 not found", null));
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
				.andExpect(model().attribute("errorMessage", equalTo("Sheet not found — check ID")))
				.andExpect(model().attribute("errorCategory", equalTo("NOT_FOUND")));
	}

	@Test
	void givenPermissionDenied_whenPreviewSheet_thenRendersPermissionBadge() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new PermissionGoogleApiException("403 forbidden", null));
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
				.andExpect(model().attribute("errorMessage", equalTo("Access denied — share the sheet with the service account")))
				.andExpect(model().attribute("errorCategory", equalTo("PERMISSION")));
	}

	@Test
	void givenTransientFailure_whenPreviewSheet_thenRendersTransientBadge() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new TransientGoogleApiException("network error", null));
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
				.andExpect(model().attribute("errorMessage", equalTo("Connection problem — retry")))
				.andExpect(model().attribute("errorCategory", equalTo("TRANSIENT")));
	}

	@Test
	void givenAuthFailure_whenExecuteSheet_thenRedirectsWithAuthBadge() throws Exception {
		// given
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new AuthGoogleApiException("auth failure", null));

		// when
		mockMvc.perform(post("/admin/import/execute")
						.param("seasonId", UUID.randomUUID().toString())
						.param("source", "sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/import"))
				.andExpect(flash().attribute("errorMessage", equalTo("Authentication problem — re-link Google account")))
				.andExpect(flash().attribute("errorCategory", equalTo("AUTH")));
	}

	@Test
	void givenNotFound_whenExecuteSheet_thenRedirectsWithNotFoundBadge() throws Exception {
		// given
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new NotFoundGoogleApiException("404 not found", null));

		// when
		mockMvc.perform(post("/admin/import/execute")
						.param("seasonId", UUID.randomUUID().toString())
						.param("source", "sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/import"))
				.andExpect(flash().attribute("errorMessage", equalTo("Sheet not found — check ID")))
				.andExpect(flash().attribute("errorCategory", equalTo("NOT_FOUND")));
	}

	@Test
	void givenPermissionDenied_whenExecuteSheet_thenRedirectsWithPermissionBadge() throws Exception {
		// given
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new PermissionGoogleApiException("403 forbidden", null));

		// when
		mockMvc.perform(post("/admin/import/execute")
						.param("seasonId", UUID.randomUUID().toString())
						.param("source", "sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/import"))
				.andExpect(flash().attribute("errorMessage", equalTo("Access denied — share the sheet with the service account")))
				.andExpect(flash().attribute("errorCategory", equalTo("PERMISSION")));
	}

	@Test
	void givenTransientFailure_whenExecuteSheet_thenRedirectsWithTransientBadge() throws Exception {
		// given
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new TransientGoogleApiException("network error", null));

		// when
		mockMvc.perform(post("/admin/import/execute")
						.param("seasonId", UUID.randomUUID().toString())
						.param("source", "sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/import"))
				.andExpect(flash().attribute("errorMessage", equalTo("Connection problem — retry")))
				.andExpect(flash().attribute("errorCategory", equalTo("TRANSIENT")));
	}

	@Test
	void givenValidSheet_whenPreviewSheet_thenRendersPreviewView() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames("abc123")).thenReturn(List.of("Race 1", "Overall"));
		when(googleSheetsService.filterRaceSheets(List.of("Race 1", "Overall"))).thenReturn(List.of("Race 1"));
		when(googleSheetsService.readRangeFromSheet("abc123", "Race 1", "A:H"))
				.thenReturn(List.of(List.of((Object) "headers")));
		var preview = new CsvImportService.ImportPreview(new CsvImportService.ImportMetadata(
				UUID.randomUUID(), "MD1", null, null, null, null));
		when(scorecardParser.parseMultipleRaces(any(), any(), any())).thenReturn(List.of(preview));
		when(csvImportService.getAllSeasons()).thenReturn(List.of());
		when(csvImportService.getPlayoffMatchups()).thenReturn(List.of());

		// when
		mockMvc.perform(post("/admin/import/preview-sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123")
						.param("seasonId", UUID.randomUUID().toString())
						.param("matchdayLabel", "MD1"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/import-preview"))
				.andExpect(model().attributeExists("previews", "raceSheetNames", "isMultiRace", "metadata", "source", "sheetUrl"));
	}

	@Test
	void givenEmptyRaceSheets_whenPreviewSheet_thenShowsNoSheetsFoundError() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames("abc123")).thenReturn(List.of("Overall"));
		when(googleSheetsService.filterRaceSheets(List.of("Overall"))).thenReturn(List.of());
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
	void givenScorecardParserReturnsEmpty_whenPreviewSheet_thenShowsParseError() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames("abc123")).thenReturn(List.of("Race 1"));
		when(googleSheetsService.filterRaceSheets(List.of("Race 1"))).thenReturn(List.of("Race 1"));
		when(googleSheetsService.readRangeFromSheet("abc123", "Race 1", "A:H"))
				.thenReturn(List.of(List.of((Object) "garbage")));
		when(scorecardParser.parseMultipleRaces(any(), any(), any())).thenReturn(List.of());
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
				.andExpect(model().attribute("errorMessage", equalTo("Unable to parse any race sheets from the spreadsheet")));
	}

	@Test
	void givenSheetWithEmptyRaceSheets_whenExecute_thenRedirectsWithNoSheetsError() throws Exception {
		// given
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames("abc123")).thenReturn(List.of("Overall"));
		when(googleSheetsService.filterRaceSheets(List.of("Overall"))).thenReturn(List.of());

		// when
		mockMvc.perform(post("/admin/import/execute")
						.param("seasonId", UUID.randomUUID().toString())
						.param("source", "sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/import"))
				.andExpect(flash().attributeExists("errorMessage"));
	}

	@Test
	void givenNoFileAndNoSheet_whenExecute_thenRedirectsWithError() throws Exception {
		// when
		mockMvc.perform(post("/admin/import/execute")
						.param("seasonId", UUID.randomUUID().toString())
						.param("source", "csv"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/import"))
				.andExpect(flash().attribute("errorMessage", equalTo("No CSV file provided")));
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

	@Test
	void givenIllegalStateFromSheetCall_whenPreviewSheet_thenFlashesWhitelistedMessageOnly() throws Exception {
		// given
		var leakyMessage = "INTERNAL_GOOGLE_LEAK_state_abc_12345_private_field_xyz";
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new IllegalStateException(leakyMessage));
		when(csvImportService.getAllSeasons()).thenReturn(List.of());
		when(csvImportService.getPlayoffMatchups()).thenReturn(List.of());

		// when
		var result = mockMvc.perform(post("/admin/import/preview-sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123")
						.param("seasonId", UUID.randomUUID().toString())
						.param("matchdayLabel", "MD1"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/import"))
				.andExpect(model().attribute("errorMessage", not(containsString("INTERNAL_GOOGLE_LEAK"))))
				.andReturn();

		var rendered = (String) result.getModelAndView().getModel().get("errorMessage");
		assertThat(rendered).doesNotContain(leakyMessage);
	}

	@Test
	void givenIllegalArgumentFromSheetCall_whenPreviewSheet_thenFlashesWhitelistedMessageOnly() throws Exception {
		// given
		var leakyMessage = "INTERNAL_GOOGLE_LEAK_arg_def_67890_private_class_path";
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(googleSheetsService.extractSpreadsheetId(anyString())).thenReturn("abc123");
		when(googleSheetsService.getSheetNames(anyString()))
				.thenThrow(new IllegalArgumentException(leakyMessage));
		when(csvImportService.getAllSeasons()).thenReturn(List.of());
		when(csvImportService.getPlayoffMatchups()).thenReturn(List.of());

		// when
		var result = mockMvc.perform(post("/admin/import/preview-sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123")
						.param("seasonId", UUID.randomUUID().toString())
						.param("matchdayLabel", "MD1"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/import"))
				.andExpect(model().attribute("errorMessage", not(containsString("INTERNAL_GOOGLE_LEAK"))))
				.andReturn();

		var rendered = (String) result.getModelAndView().getModel().get("errorMessage");
		assertThat(rendered).doesNotContain(leakyMessage);
	}

	@Test
	void givenIllegalArgumentFromExtractId_whenExecuteSheet_thenFlashesWhitelistedMessageOnly() throws Exception {
		// given
		var leakyMessage = "INTERNAL_GOOGLE_LEAK_exec_arg_99999_internal_url_validator";
		when(googleSheetsService.extractSpreadsheetId(anyString()))
				.thenThrow(new IllegalArgumentException(leakyMessage));

		// when
		mockMvc.perform(post("/admin/import/execute")
						.param("seasonId", UUID.randomUUID().toString())
						.param("source", "sheet")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc123"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/import"))
				.andExpect(flash().attribute("errorMessage", not(containsString("INTERNAL_GOOGLE_LEAK"))));
	}

	@Test
	void givenBusinessRuleException_whenExecuteImport_thenSurfacesValidationMessage() throws Exception {
		// given
		var validationDetail = "duplicate-entry-validation-detail-XYZ";
		when(csvImportService.parseAndPreview(any(), any()))
				.thenThrow(new BusinessRuleException(validationDetail));
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
				.andExpect(flash().attribute("errorMessage", containsString(validationDetail)));
	}
}
