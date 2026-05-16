package org.ctc.backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring binding chain IT for {@code BackupImportConfirmForm}.
 * Verifies field-error re-render on {@code acknowledged=false} or absent,
 * redirect with success flash on {@code acknowledged=true}, and staging-file
 * cleanup by the AFTER_COMMIT listener on successful execute.
 * Each test produces a real staged ZIP via the full pipeline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class BackupImportConfirmFormValidationIT {

	private static final Path IMPORT_BACKUPS_ROOT;
	static {
		try {
			IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-confirm-validation-it-");
			IMPORT_BACKUPS_ROOT.toFile().deleteOnExit();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to allocate import-backups tempdir", e);
		}
	}

	@DynamicPropertySource
	static void overrideImportBackupsDir(DynamicPropertyRegistry registry) {
		registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
	}

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

	/**
	 * Wipe IMPORT_BACKUPS_ROOT contents between tests so two @Test methods that exercise
	 * import-execute within the same second do not collide on
	 * {@code &lt;tmp&gt;/&lt;ts&gt;/auto-backup-before-import.zip}.
	 */
	@AfterEach
	void cleanImportBackupsRoot() throws IOException {
		if (!Files.exists(IMPORT_BACKUPS_ROOT)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(IMPORT_BACKUPS_ROOT)) {
			walk.sorted(Comparator.reverseOrder())
					.filter(p -> !p.equals(IMPORT_BACKUPS_ROOT))
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException ignored) {
							// best-effort cleanup
						}
					});
		}
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
	// Test 3: acknowledged=true → redirect to /admin/backup with success flash
	// -------------------------------------------------------------------------

	@Test
	void givenAcknowledgedTrue_whenPostImportExecute_thenRedirectsToBackupWithSuccessFlash()
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
						org.hamcrest.Matchers.startsWith("Import completed.")));
	}

	// -------------------------------------------------------------------------
	// Test 4: staging file is deleted by BackupImportPostCommitListener after execute
	// -------------------------------------------------------------------------

	@Test
	void givenSuccessfulExecute_whenAcknowledgedTrue_thenStagingFileIsDeleted()
			throws Exception {
		// given — stage a valid ZIP
		String stagingIdStr = stageValidZip();
		UUID stagingId = UUID.fromString(stagingIdStr);
		Path stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
		Path expectedFile = stagingDir.resolve("upload-" + stagingId + ".zip");

		// Verify file exists before execute call
		assertThat(expectedFile).exists();

		// when
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingIdStr)
						.param("acknowledged", "true"))
				.andExpect(status().is3xxRedirection());

		// then — staging file is deleted by BackupImportPostCommitListener after AFTER_COMMIT
		assertThat(expectedFile)
				.as("staging file must be deleted by BackupImportPostCommitListener after AFTER_COMMIT")
				.doesNotExist();
	}
}
