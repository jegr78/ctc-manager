package org.ctc.backup.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.ctc.domain.model.Driver;
import org.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class BackupImportUploadsSwapFailureIT {

	@Autowired
	MockMvc mockMvc;

	@MockitoSpyBean
	BackupArchiveService backupArchiveService;

	@Autowired
	BackupImportService backupImportService;

	@Autowired
	DriverRepository driverRepository;

	@Autowired
	ImportLockService importLockService;

	@Value("${app.upload-dir}")
	String uploadDirRaw;

	@Test
	void givenUploadsSwapFailsAfterCommit_whenImportExecuted_thenPartialFailureWithRecoveryPathIsReported()
			throws Exception {
		// given
		Path uploads = Files.createDirectories(Paths.get(uploadDirRaw).toAbsolutePath().normalize());
		Path marker = Files.writeString(uploads.resolve("swap-marker-" + UUID.randomUUID() + ".txt"), "old");
		UUID stagingId = exportAndStage();
		Driver addedAfterBackup = new Driver();
		addedAfterBackup.setPsnId("Test_SwapFailure_" + UUID.randomUUID());
		addedAfterBackup.setNickname("Test Swap Failure");
		driverRepository.save(addedAfterBackup);
		Path[] recoveryDir = new Path[1];
		Mockito.doAnswer(invocation -> {
			invocation.callRealMethod();
			Path uploadsNew = invocation.getArgument(1);
			recoveryDir[0] = uploadsNew.getParent();
			Files.createDirectories(recoveryDir[0].resolve("uploads-old"));
			Files.writeString(recoveryDir[0].resolve("uploads-old/blocker"), "blocks the rename");
			return null;
		}).when(backupArchiveService).extractUploadsTo(Mockito.any(Path.class), Mockito.any(Path.class));

		// when
		ResultActions response = mockMvc.perform(post("/admin/backup/import-execute")
				.param("stagingId", stagingId.toString())
				.param("acknowledged", "true"));

		// then
		response.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/backup"))
				.andExpect(flash().attributeCount(1))
				.andExpect(flash().attribute("errorMessage", allOf(
						containsString("database was restored, but the uploads were not replaced"),
						containsString("moving the current uploads aside failed"),
						containsString("import " + recoveryDir[0].resolve("auto-backup-before-import.zip") + "."),
						containsString("Audit-id:"))));
		assertThat(driverRepository.existsById(addedAfterBackup.getId()))
				.as("the committed restore removed the driver created after the backup").isFalse();
		assertThat(marker).as("the swap failed before touching the live uploads").hasContent("old");
		assertThat(recoveryDir[0].resolve("auto-backup-before-import.zip"))
				.as("the recovery archive named in the message exists").exists();
		assertThat(importLockService.isLocked()).isFalse();
	}

	private UUID exportAndStage() throws IOException, BackupArchiveException {
		ByteArrayOutputStream zip = new ByteArrayOutputStream();
		backupArchiveService.writeZip(zip, Instant.now());
		MockMultipartFile file = new MockMultipartFile("file", "swap-failure.zip", "application/zip", zip.toByteArray());
		return backupImportService.stage(file).stagingId();
	}
}
