package org.ctc.backup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.ctc.backup.exception.UploadsSwapPreflightException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

class UploadsSwapPreflightTest {

	private static final Path SHM = Path.of("/dev/shm");

	private final UploadsSwapPreflight preflight = new UploadsSwapPreflight();

	@Test
	void givenUploadsAndArchiveOnOneFilesystem_whenChecked_thenPassesAndLeavesNoProbeBehind(@TempDir Path root)
			throws Exception {
		// given
		Path uploads = Files.createDirectories(root.resolve("uploads"));
		Files.writeString(uploads.resolve("logo.png"), "png");
		Path archive = Files.createDirectories(root.resolve("import-backups/ts"));

		// when
		preflight.check(uploads, archive);

		// then
		assertThat(uploads.resolve("logo.png")).hasContent("png");
		assertThat(list(root)).as("probe entries must be removed").containsExactlyInAnyOrder("uploads", "import-backups");
		assertThat(list(archive)).as("probe entries must be removed").isEmpty();
	}

	@Test
	void givenUploadsDirectoryMissing_whenChecked_thenPassesBecauseTheSwapCreatesIt(@TempDir Path root)
			throws IOException {
		// given
		Path archive = Files.createDirectories(root.resolve("import-backups/ts"));

		// when / then
		assertThatCode(() -> preflight.check(root.resolve("uploads"), archive)).doesNotThrowAnyException();
	}

	@Test
	void givenUploadsPathIsAFile_whenChecked_thenRejected(@TempDir Path root) throws IOException {
		// given
		Path uploads = Files.writeString(root.resolve("uploads"), "not a directory");
		Path archive = Files.createDirectories(root.resolve("import-backups/ts"));

		// when / then
		assertThatThrownBy(() -> preflight.check(uploads, archive))
				.isInstanceOf(UploadsSwapPreflightException.class)
				.hasMessageContaining("is not a directory");
	}

	@Test
	void givenArchiveOnAnotherFilesystem_whenChecked_thenRejectedAndUploadsUntouched(@TempDir Path root)
			throws IOException {
		// given
		assumeThat(Files.isDirectory(SHM) && Files.isWritable(SHM)).as("needs a writable /dev/shm").isTrue();
		assumeThat(!Files.getFileStore(SHM).equals(Files.getFileStore(root))).as("needs /dev/shm on another filesystem").isTrue();
		Path uploads = Files.createDirectories(root.resolve("uploads"));
		Files.writeString(uploads.resolve("logo.png"), "png");
		Path archive = Files.createTempDirectory(SHM, "ctc-preflight-");
		try {
			// when / then
			assertThatThrownBy(() -> preflight.check(uploads, archive))
					.isInstanceOf(UploadsSwapPreflightException.class)
					.hasMessageContaining("cannot be moved atomically");
			assertThat(uploads.resolve("logo.png")).hasContent("png");
			assertThat(list(root)).containsExactly("uploads");
			assertThat(list(archive)).isEmpty();
		} finally {
			Files.deleteIfExists(archive);
		}
	}

	@Test
	void givenUploadsDirectoryIsAMountRoot_whenChecked_thenRejectedWithoutTouchingIt(@TempDir Path root)
			throws IOException {
		// given
		assumeThat(Files.isDirectory(SHM)).as("needs /dev/shm").isTrue();
		assumeThat(!Files.getFileStore(SHM).equals(Files.getFileStore(SHM.getParent()))).as("needs /dev/shm as mount root").isTrue();
		Path archive = Files.createDirectories(root.resolve("import-backups/ts"));

		// when / then
		assertThatThrownBy(() -> preflight.check(SHM, archive))
				.isInstanceOf(UploadsSwapPreflightException.class)
				.hasMessageContaining("is a mount point");
		assertThat(list(archive)).isEmpty();
	}

	private static List<String> list(Path dir) throws IOException {
		try (Stream<Path> entries = Files.list(dir)) {
			return entries.map(p -> p.getFileName().toString()).toList();
		}
	}
}
