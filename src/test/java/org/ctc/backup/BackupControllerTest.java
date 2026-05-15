package org.ctc.backup;

import java.io.OutputStream;
import java.time.Instant;
import java.util.UUID;

import java.util.List;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 73-04 — MockMvc unit tests for {@link BackupController}.
 *
 * <p>Boots the {@code dev} profile (no security filters by virtue of
 * {@code OpenSecurityConfig}) with the archive service mocked out via
 * {@link MockitoBean}. Three Phase-73 behaviours are pinned:
 * <ol>
 *   <li>GET {@code /admin/backup} renders the {@code admin/backup} view with
 *       the {@code title} model attribute equal to {@code "Backup"}.</li>
 *   <li>POST {@code /admin/backup/export} returns {@code 200} with
 *       {@code application/octet-stream} and a {@code Content-Disposition}
 *       header matching the locked ISO-compact filename regex.</li>
 *   <li>POST delegates to
 *       {@link BackupArchiveService#writeZip(OutputStream, Instant)}.</li>
 * </ol>
 *
 * <p>Phase 75 — additional scenarios pin the upgraded
 * {@link BackupController#importExecute import-execute} handler (D-15 flash
 * strings + D-17 endpoint stability):
 * <ol start="4">
 *   <li>Valid confirm form → {@link BackupImportService#execute(UUID)} invoked,
 *       redirect to {@code /admin/backup} with D-15 #1 success flash.</li>
 *   <li>{@link BackupImportException} thrown → redirect with D-15 #2 failure
 *       flash carrying the audit UUID.</li>
 *   <li>Invalid form (acknowledged != true) → service NEVER invoked, no flash
 *       message rendered, validation error path exercised.</li>
 * </ol>
 *
 * <p>Profile-conditional auth/CSRF semantics are exercised by
 * {@code BackupControllerSecurityIT} — kept out of this unit test so that the
 * controller signature is the only thing under test. The Phase 74 stub-flash
 * scenario was REMOVED in Plan 75-08 because the stub string no longer exists
 * in production code (D-15 supersedes D-08).
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

	// =========================================================================
	// Phase 75 Plan 08 — D-15 flash strings on /admin/backup/import-execute
	// =========================================================================

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

		// when / then — D-15 #2 string is rendered verbatim with the audit UUID
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
