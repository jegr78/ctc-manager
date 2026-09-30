package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Runs two imports at the same fixed instant and checks that each keeps its own recovery archive.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupRecoveryArchiveCollisionIT {

	private static final Path IMPORT_BACKUPS_ROOT;

	static {
		try {
			IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-recovery-collision-it-");
			IMPORT_BACKUPS_ROOT.toFile().deleteOnExit();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to allocate import-backups tempdir", e);
		}
	}

	@DynamicPropertySource
	static void overrideImportBackupsDir(DynamicPropertyRegistry registry) {
		registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
	}

	@Autowired BackupImportService backupImportService;
	@Autowired BackupArchiveService backupArchiveService;

	@Test
	void givenTwoImportsInTheSameSecond_whenBothRun_thenEachKeepsItsOwnRecoveryArchive() throws Exception {
		// given
		byte[] backup = export();

		backupImportService.execute(stage(backup));
		Path firstArchive = recoveryArchives().getFirst();

		// when
		var secondImportFailure = catchThrowable(() -> backupImportService.execute(stage(backup)));

		// then
		assertThat(secondImportFailure).as("second import in the same second").isNull();
		assertThat(firstArchive).as("the first import's recovery archive survives").exists();
		assertThat(recoveryArchives()).as("one intact recovery archive per import").hasSize(2)
				.allSatisfy(archive -> assertThat(Files.size(archive)).as("archive size").isPositive());
	}

	private java.util.UUID stage(byte[] backup) throws Exception {
		return backupImportService.stage(
				new MockMultipartFile("file", "collision.zip", "application/zip", backup)).stagingId();
	}

	private byte[] export() throws IOException {
		var out = new ByteArrayOutputStream();
		backupArchiveService.writeZip(out, Instant.now());
		return out.toByteArray();
	}

	private static List<Path> recoveryArchives() throws IOException {
		try (Stream<Path> walk = Files.walk(IMPORT_BACKUPS_ROOT)) {
			return walk.filter(p -> p.getFileName().toString().equals("auto-backup-before-import.zip")).toList();
		}
	}

	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
		}
	}
}
