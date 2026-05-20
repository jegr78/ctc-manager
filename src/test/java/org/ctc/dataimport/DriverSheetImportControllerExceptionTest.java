package org.ctc.dataimport;

import java.util.List;
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.SeasonManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused tests for DriverSheetImportController exception-handling behavior.
 * Uses @MockitoBean to verify the narrowed catch blocks of the preview and execute
 * handlers (IOException, BusinessRuleException, DataAccessException, missing input).
 * Normal controller behavior is covered in DriverSheetImportControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DriverSheetImportControllerExceptionTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DriverSheetImportService driverSheetImportService;
	@MockitoBean
	private GoogleSheetsService googleSheetsService;
	@MockitoBean
	private SeasonManagementService seasonManagementService;

	@Test
	void givenPreviewThrowsAuthGoogleApiException_whenPostPreview_thenModelAttributeCategoryAuth() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(seasonManagementService.findAll()).thenReturn(List.of());
		when(driverSheetImportService.preview(anyString()))
				.thenThrow(new AuthGoogleApiException("auth failure", null));

		// when
		mockMvc.perform(post("/admin/drivers/import/preview")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/driver-import"))
				.andExpect(model().attribute("errorMessage", "Authentication problem — re-link Google account"))
				.andExpect(model().attribute("errorCategory", "AUTH"));
	}

	@Test
	void givenPreviewThrowsNotFoundGoogleApiException_whenPostPreview_thenModelAttributeCategoryNotFound() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(seasonManagementService.findAll()).thenReturn(List.of());
		when(driverSheetImportService.preview(anyString()))
				.thenThrow(new NotFoundGoogleApiException("404 not found", null));

		// when
		mockMvc.perform(post("/admin/drivers/import/preview")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/driver-import"))
				.andExpect(model().attribute("errorMessage", "Sheet not found — check ID"))
				.andExpect(model().attribute("errorCategory", "NOT_FOUND"));
	}

	@Test
	void givenPreviewThrowsPermissionGoogleApiException_whenPostPreview_thenModelAttributeCategoryPermission() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(seasonManagementService.findAll()).thenReturn(List.of());
		when(driverSheetImportService.preview(anyString()))
				.thenThrow(new PermissionGoogleApiException("403 forbidden", null));

		// when
		mockMvc.perform(post("/admin/drivers/import/preview")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/driver-import"))
				.andExpect(model().attribute("errorMessage", "Access denied — share the sheet with the service account"))
				.andExpect(model().attribute("errorCategory", "PERMISSION"));
	}

	@Test
	void givenPreviewThrowsTransientGoogleApiException_whenPostPreview_thenModelAttributeCategoryTransient() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(seasonManagementService.findAll()).thenReturn(List.of());
		when(driverSheetImportService.preview(anyString()))
				.thenThrow(new TransientGoogleApiException("socket timeout", null));

		// when
		mockMvc.perform(post("/admin/drivers/import/preview")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/driver-import"))
				.andExpect(model().attribute("errorMessage", "Connection problem — retry"))
				.andExpect(model().attribute("errorCategory", "TRANSIENT"));
	}

	@Test
	void givenExecuteThrowsAuthGoogleApiException_whenPostExecute_thenRedirectFlashCategoryAuth() throws Exception {
		// given
		when(driverSheetImportService.execute(anyString(), any()))
				.thenThrow(new AuthGoogleApiException("auth failure", null));

		// when
		mockMvc.perform(post("/admin/drivers/import/execute")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc")
						.param("seasonId_2024", "00000000-0000-0000-0000-000000000001"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/drivers/import"))
				.andExpect(flash().attribute("errorMessage", "Authentication problem — re-link Google account"))
				.andExpect(flash().attribute("errorCategory", "AUTH"));
	}

	@Test
	void givenExecuteThrowsNotFoundGoogleApiException_whenPostExecute_thenRedirectFlashCategoryNotFound() throws Exception {
		// given
		when(driverSheetImportService.execute(anyString(), any()))
				.thenThrow(new NotFoundGoogleApiException("404 not found", null));

		// when
		mockMvc.perform(post("/admin/drivers/import/execute")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc")
						.param("seasonId_2024", "00000000-0000-0000-0000-000000000001"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/drivers/import"))
				.andExpect(flash().attribute("errorMessage", "Sheet not found — check ID"))
				.andExpect(flash().attribute("errorCategory", "NOT_FOUND"));
	}

	@Test
	void givenExecuteThrowsPermissionGoogleApiException_whenPostExecute_thenRedirectFlashCategoryPermission() throws Exception {
		// given
		when(driverSheetImportService.execute(anyString(), any()))
				.thenThrow(new PermissionGoogleApiException("403 forbidden", null));

		// when
		mockMvc.perform(post("/admin/drivers/import/execute")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc")
						.param("seasonId_2024", "00000000-0000-0000-0000-000000000001"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/drivers/import"))
				.andExpect(flash().attribute("errorMessage", "Access denied — share the sheet with the service account"))
				.andExpect(flash().attribute("errorCategory", "PERMISSION"));
	}

	@Test
	void givenExecuteThrowsTransientGoogleApiException_whenPostExecute_thenRedirectFlashCategoryTransient() throws Exception {
		// given
		when(driverSheetImportService.execute(anyString(), any()))
				.thenThrow(new TransientGoogleApiException("socket timeout", null));

		// when
		mockMvc.perform(post("/admin/drivers/import/execute")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc")
						.param("seasonId_2024", "00000000-0000-0000-0000-000000000001"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/drivers/import"))
				.andExpect(flash().attribute("errorMessage", "Connection problem — retry"))
				.andExpect(flash().attribute("errorCategory", "TRANSIENT"));
	}

	@Test
	void givenExecuteThrowsBusinessRule_whenPostExecute_thenRedirectWithFlashError() throws Exception {
		// given
		when(driverSheetImportService.execute(anyString(), any()))
				.thenThrow(new BusinessRuleException("duplicate driver entry"));

		// when
		mockMvc.perform(post("/admin/drivers/import/execute")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/drivers/import"))
				.andExpect(flash().attributeExists("errorMessage"));
	}

	@Test
	void givenExecuteThrowsDataAccessException_whenPostExecute_thenRedirectWithFlashError() throws Exception {
		// given
		when(driverSheetImportService.execute(anyString(), any()))
				.thenThrow(new DataIntegrityViolationException("constraint violation"));

		// when
		mockMvc.perform(post("/admin/drivers/import/execute")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/drivers/import"))
				.andExpect(flash().attributeExists("errorMessage"));
	}

	@Test
	void givenMissingSheetUrl_whenPostExecute_thenRedirectWithError() throws Exception {
		// when
		mockMvc.perform(post("/admin/drivers/import/execute")
						.param("sheetUrl", ""))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/drivers/import"))
				.andExpect(flash().attributeExists("errorMessage"));
	}
}
