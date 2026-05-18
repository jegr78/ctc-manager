package org.ctc.backup;

import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc unit tests for {@link BackupController}.
 * Boots the {@code dev} profile (no security filters via {@code OpenSecurityConfig})
 * with the archive and import services mocked out via {@link MockitoBean}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BackupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BackupArchiveService backupArchiveService;

	@MockitoBean
	private BackupImportService backupImportService;

	@Test
	void givenAuthenticatedUser_whenGetBackup_thenViewIsAdminBackupAndModelHasTitle() throws Exception {
		// when / then
		mockMvc.perform(get("/admin/backup"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/backup"))
				.andExpect(model().attribute("title", "Backup"));
	}

	@Test
	void givenAuthenticatedUser_whenPostExport_thenResponseHasContentDispositionMatchingIsoFilename() throws Exception {
		// when / then
		mockMvc.perform(post("/admin/backup/export"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						Matchers.matchesPattern("attachment; filename=\"?ctc-backup-\\d{8}T\\d{6}Z\\.zip\"?")))
				.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE));
	}

	@Test
	void givenArchiveServiceWired_whenPostExport_thenWriteZipIsInvoked() throws Exception {
		// when — POST /admin/backup/export returns ResponseEntity<StreamingResponseBody>;
		// the streaming body lambda (which calls writeZip) is only invoked AFTER MockMvc
		// asyncDispatches the response. Without asyncDispatch the body is never executed
		// and the Mockito verify below sees zero interactions.
		MvcResult result = mockMvc.perform(post("/admin/backup/export"))
				.andExpect(status().isOk())
				.andReturn();
		mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(result))
				.andExpect(status().isOk());

		// then
		verify(backupArchiveService).writeZip(any(OutputStream.class), any(Instant.class));
	}

	@Test
	void givenValidConfirmForm_whenExecutePost_thenServiceExecuteCalledAndSuccessFlashRendered() throws Exception {
		// given — service returns a stubbed result (17042 rows across 24 tables)
		UUID stagingId = UUID.randomUUID();
		UUID auditUuid = UUID.randomUUID();
		BackupImportResult stubbed = new BackupImportResult(auditUuid, 17042L, 24);
		when(backupImportService.execute(stagingId)).thenReturn(stubbed);

		// when / then
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingId.toString())
						.param("acknowledged", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/backup"))
				.andExpect(flash().attribute("successMessage",
						"Import completed. 17042 rows restored across 24 tables."));

		verify(backupImportService).reparse(stagingId);
		verify(backupImportService).execute(stagingId);
	}

	@Test
	void givenServiceThrowsBackupImportException_whenExecutePost_thenFailureFlashWithAuditUuid() throws Exception {
		// given — service.execute() throws BackupImportException carrying a fixed audit UUID
		UUID stagingId = UUID.randomUUID();
		UUID specificAuditUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
		when(backupImportService.execute(stagingId))
				.thenThrow(new BackupImportException(specificAuditUuid, new RuntimeException("simulated")));

		// when / then — failure flash is rendered verbatim with the audit UUID
		String expected = String.format(
				"Import failed and was rolled back — see logs. Audit-id: %s.", specificAuditUuid);
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingId.toString())
						.param("acknowledged", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/backup"))
				.andExpect(flash().attribute("errorMessage", expected));

		verify(backupImportService).execute(stagingId);
	}

	@Test
	void givenInvalidConfirmForm_whenExecutePost_thenServiceNotCalledAndBindingErrorFlashed() throws Exception {
		// given — acknowledged is false; the @AssertTrue validator must reject.
		// The controller re-renders admin/backup-confirm.html (which expects a preview),
		// so stub reparse(...) to return a minimal but well-formed BackupImportPreview.
		UUID stagingId = UUID.randomUUID();
		BackupImportPreview stubPreview = new BackupImportPreview(
				stagingId,
				"unit-test.zip",
				1024L,
				1,
				1,
				true,
				List.of(),
				0,
				0L);
		when(backupImportService.reparse(stagingId)).thenReturn(stubPreview);

		// when — submit with acknowledged=false (binding error on @AssertTrue)
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingId.toString())
						.param("acknowledged", "false"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/backup-confirm"));

		// then — execute MUST NEVER be invoked when binding fails
		verify(backupImportService, never()).execute(any(UUID.class));
		verify(backupImportService, times(1)).reparse(stagingId);
	}
}
