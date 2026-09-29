package org.ctc.backup.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.ctc.domain.model.Driver;
import org.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recovery archives on {@code /dev/shm} and uploads in the checkout sit on different mounts, the
 * boundary a volume-per-directory container layout creates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupImportUploadsPreflightIT {

	private static final Path OTHER_FILESYSTEM_ARCHIVES = createArchivesOnAnotherMount();

	@DynamicPropertySource
	static void archivesOnAnotherMount(DynamicPropertyRegistry registry) {
		registry.add("app.backup.import-backups-dir", OTHER_FILESYSTEM_ARCHIVES::toString);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BackupArchiveService backupArchiveService;

	@Autowired
	BackupImportService backupImportService;

	@Autowired
	DriverRepository driverRepository;

	@Autowired
	DataImportAuditRepository dataImportAuditRepository;

	@Value("${app.upload-dir}")
	String uploadDirRaw;

	@Value("${app.backup.staging-dir}")
	String stagingDirRaw;

	@AfterAll
	void removeArchives() throws IOException {
		try (Stream<Path> walk = Files.walk(OTHER_FILESYSTEM_ARCHIVES)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	@Test
	void givenArchivesOnAnotherFilesystem_whenImportExecuted_thenAbortedBeforeAnyChange() throws Exception {
		// given
		Path uploads = Files.createDirectories(Paths.get(uploadDirRaw).toAbsolutePath().normalize());
		assumeThat(Files.getFileStore(OTHER_FILESYSTEM_ARCHIVES).equals(Files.getFileStore(uploads)))
				.as("needs /dev/shm on another filesystem than the uploads").isFalse();
		Path marker = Files.writeString(uploads.resolve("preflight-marker-" + UUID.randomUUID() + ".txt"), "kept");
		UUID stagingId = exportAndStage();
		Driver addedAfterBackup = new Driver();
		addedAfterBackup.setPsnId("Test_Preflight_" + UUID.randomUUID());
		addedAfterBackup.setNickname("Test Preflight");
		driverRepository.save(addedAfterBackup);
		long driversBefore = driverRepository.count();
		long auditsBefore = dataImportAuditRepository.count();

		// when
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", stagingId.toString())
						.param("acknowledged", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/backup"))
				.andExpect(flash().attribute("errorMessage", allOf(
						containsString("uploads directory cannot be replaced"),
						containsString("cannot be moved atomically"),
						containsString("No database changes"))));

		// then
		assertThat(driverRepository.existsById(addedAfterBackup.getId()))
				.as("a driver created after the backup must survive the aborted import").isTrue();
		assertThat(driverRepository.count()).isEqualTo(driversBefore);
		assertThat(marker).as("uploads must stay untouched").hasContent("kept");
		assertThat(dataImportAuditRepository.count()).as("one failure audit row").isEqualTo(auditsBefore + 1);
		assertThat(archiveEntries()).as("no auto-backup, uploads-old or probe may remain").isEmpty();
		assertThat(Paths.get(stagingDirRaw).resolve("upload-" + stagingId + ".zip"))
				.as("the staged backup stays for a retry").exists();
	}

	private UUID exportAndStage() throws IOException, BackupArchiveException {
		ByteArrayOutputStream zip = new ByteArrayOutputStream();
		backupArchiveService.writeZip(zip, Instant.now());
		MockMultipartFile file = new MockMultipartFile("file", "preflight.zip", "application/zip", zip.toByteArray());
		return backupImportService.stage(file).stagingId();
	}

	private List<Path> archiveEntries() throws IOException {
		try (Stream<Path> walk = Files.walk(OTHER_FILESYSTEM_ARCHIVES)) {
			return walk.filter(p -> !p.equals(OTHER_FILESYSTEM_ARCHIVES)).toList();
		}
	}

	private static Path createArchivesOnAnotherMount() {
		Path shm = Path.of("/dev/shm");
		try {
			return Files.isWritable(shm)
					? Files.createTempDirectory(shm, "ctc-import-backups-preflight-it-")
					: Files.createTempDirectory("ctc-import-backups-preflight-it-");
		} catch (IOException e) {
			throw new IllegalStateException("Cannot allocate the archive directory", e);
		}
	}
}
