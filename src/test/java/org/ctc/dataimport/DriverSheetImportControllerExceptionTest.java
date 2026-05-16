package org.ctc.dataimport;

import java.io.IOException;
import java.util.List;
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
	void givenPreviewThrowsIOException_whenPostPreview_thenFormWithError() throws Exception {
		// given
		when(googleSheetsService.isAvailable()).thenReturn(true);
		when(seasonManagementService.findAll()).thenReturn(List.of());
		when(driverSheetImportService.preview(anyString()))
				.thenThrow(new IOException("network error"));

		// when
		mockMvc.perform(post("/admin/drivers/import/preview")
						.param("sheetUrl", "https://docs.google.com/spreadsheets/d/abc"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/driver-import"))
				.andExpect(model().attributeExists("errorMessage"));
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
