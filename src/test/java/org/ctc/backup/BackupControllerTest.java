package org.ctc.backup;

import java.io.OutputStream;
import java.time.Instant;

import org.ctc.backup.service.BackupArchiveService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 73-04 — MockMvc unit tests for {@link BackupController}.
 *
 * <p>Boots the {@code dev} profile (no security filters by virtue of
 * {@code OpenSecurityConfig}) with the archive service mocked out via
 * {@link MockitoBean}. Three behaviours are pinned:
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
 * <p>Profile-conditional auth/CSRF semantics are exercised by
 * {@code BackupControllerSecurityIT} — kept out of this unit test so that the
 * controller signature is the only thing under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BackupControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BackupArchiveService backupArchiveService;

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
		// when
		mockMvc.perform(post("/admin/backup/export"))
				.andExpect(status().isOk());

		// then
		verify(backupArchiveService).writeZip(any(OutputStream.class), any(Instant.class));
	}
}
