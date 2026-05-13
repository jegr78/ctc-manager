package org.ctc.backup;

import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Phase 74-08 — Spring binding chain IT for {@code BackupImportConfirmForm}.
 *
 * <p>Verifies:
 * <ol>
 *   <li>Re-render with field error when {@code acknowledged=false} (explicit reject).</li>
 *   <li>Re-render with field error when {@code acknowledged} param is absent (null wrapper).</li>
 *   <li>Redirect to {@code /admin/backup} with D-02#5 stub Flash when {@code acknowledged=true}.</li>
 *   <li>D-08 Seam: staging file is NOT deleted by the stub (Phase 75 inherits it).</li>
 * </ol>
 *
 * <p>Each test that exercises the execute endpoint requires a real staging UUID because
 * {@code importExecute} calls {@link BackupImportService#reparse(UUID)} when there are no
 * binding errors. A valid ZIP is produced via {@link BackupArchiveService#writeZip} and
 * staged via the HTTP multipart endpoint — this ensures the test uses the full real pipeline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BackupImportConfirmFormValidationIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private BackupArchiveService backupArchiveService;

	@Autowired
	private BackupImportService backupImportService;

	@Value("${app.backup.staging-dir}")
	private String stagingDirRaw;

	// -------------------------------------------------------------------------
	// Helper
	// -------------------------------------------------------------------------

	/**
	 * Produces a valid backup ZIP via {@link BackupArchiveService#writeZip} and stages it
	 * directly via {@link BackupImportService#stage(MultipartFile)}.
	 *
	 * <p>Bypasses the HTTP layer intentionally: the {@code admin/backup-preview} Thymeleaf
	 * template ships in a later plan (Plan 09/10). The controller binding-chain tests only
	 * need a valid {@code stagingId} — the service path is sufficient for that purpose.
	 *
	 * @return the stagingId UUID string for use in subsequent POST params
	 */
	private String stageValidZip() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		backupArchiveService.writeZip(baos, Instant.now());
		byte[] zipBytes = baos.toByteArray();

		MockMultipartFile file = new MockMultipartFile("file", "ctc-backup-test.zip",
				"application/zip", zipBytes);

		BackupImportPreview preview = backupImportService.stage(file);
		assertThat(preview).isNotNull();
		return preview.stagingId().toString();
	}

	// -------------------------------------------------------------------------
	// Test 1: acknowledged=false → re-render confirm with field error
	// -------------------------------------------------------------------------

	@Test
	void givenAcknowledgedFalse_whenPostImportExecute_thenReRendersConfirmWithFieldError()
			throws Exception {
		// given — a real staged ZIP
		String stagingId = stageValidZip();

		// when
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingId)
						.param("acknowledged", "false"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/backup-confirm"))
				.andExpect(model().attributeHasFieldErrors("backupImportConfirmForm", "acknowledged"));
	}

	// -------------------------------------------------------------------------
	// Test 2: acknowledged param absent → re-render confirm with field error
	// -------------------------------------------------------------------------

	@Test
	void givenAcknowledgedMissing_whenPostImportExecute_thenReRendersConfirmWithFieldError()
			throws Exception {
		// given — a real staged ZIP
		String stagingId = stageValidZip();

		// when — no "acknowledged" param → null → @NotNull fires
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingId))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/backup-confirm"))
				.andExpect(model().attributeHasFieldErrors("backupImportConfirmForm", "acknowledged"));
	}

	// -------------------------------------------------------------------------
	// Test 3: acknowledged=true → redirect to /admin/backup with D-02#5 stub flash
	// -------------------------------------------------------------------------

	@Test
	void givenAcknowledgedTrue_whenPostImportExecute_thenRedirectsToBackupWithStubFlash()
			throws Exception {
		// given — a real staged ZIP
		String stagingId = stageValidZip();

		// when / then
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingId)
						.param("acknowledged", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/backup"))
				.andExpect(flash().attribute("successMessage",
						"Validation succeeded. Import execution will be enabled in Phase 75."));
	}

	// -------------------------------------------------------------------------
	// Test 4: D-08 Seam — staging file NOT deleted by stub
	// -------------------------------------------------------------------------

	@Test
	void givenSuccessfulExecuteStub_whenAcknowledgedTrue_thenStagingFileStillExists()
			throws Exception {
		// given — stage a valid ZIP
		String stagingIdStr = stageValidZip();
		UUID stagingId = UUID.fromString(stagingIdStr);
		Path stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
		Path expectedFile = stagingDir.resolve("upload-" + stagingId + ".zip");

		// Verify file exists before execute call
		assertThat(expectedFile).exists();

		// when — execute stub with acknowledged=true
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingIdStr)
						.param("acknowledged", "true"))
				.andExpect(status().is3xxRedirection());

		// then — D-08: staging file must still exist (Phase 75 inherits it)
		assertThat(expectedFile)
				.as("D-08: staging file must survive Phase-74 stub execute")
				.exists();

		// Cleanup — delete the staging file after assertion so the test environment stays clean
		Files.deleteIfExists(expectedFile);
	}
}
