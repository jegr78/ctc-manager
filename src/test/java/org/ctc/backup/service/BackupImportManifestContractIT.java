package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.domain.model.Car;
import org.ctc.domain.repository.CarRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stages exported backups whose data files disagree with the manifest and checks that preview and
 * execute reject them before any table is wiped.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupImportManifestContractIT {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired BackupImportService backupImportService;
	@Autowired BackupArchiveService backupArchiveService;
	@Autowired CarRepository carRepository;

	@Value("${app.backup.staging-dir}")
	String stagingDirRaw;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Car car;

	@BeforeEach
	void createCar() {
		car = carRepository.save(new Car("Test_Manifest", "Car " + id));
	}

	@AfterEach
	void removeCar() {
		carRepository.deleteById(car.getId());
	}

	@Test
	void givenManifestOnlyArchive_whenStaged_thenRejectedAsDataMismatch() throws Exception {
		// given
		byte[] zip = rewrite(export(), name -> !name.startsWith("data/"), manifest -> { });

		// when / then
		assertDataMismatch(() -> stage(zip), "cars: data file missing");
	}

	@Test
	void givenMissingDataFileWithAnnouncedRows_whenStaged_thenRejectedAsDataMismatch() throws Exception {
		// given
		byte[] zip = rewrite(export(), name -> !name.equals("data/cars.json"), manifest -> { });

		// when / then
		assertDataMismatch(() -> stage(zip), "cars: data file missing");
	}

	@Test
	void givenManifestAnnouncingMoreRowsThanTheDataFile_whenStaged_thenRejectedAsDataMismatch() throws Exception {
		// given
		byte[] zip = rewrite(export(), name -> true,
				manifest -> counts(manifest).put("cars", counts(manifest).get("cars").asLong() + 5));

		// when / then
		assertDataMismatch(() -> stage(zip), "cars: manifest announces");
	}

	@Test
	void givenStrippedArchivePlacedInStagingAfterPreview_whenExecuted_thenRejectedBeforeAnyTableIsWiped()
			throws Exception {
		// given
		UUID stagingId = UUID.randomUUID();
		Files.write(stagingDir().resolve("upload-" + stagingId + ".zip"),
				rewrite(export(), name -> !name.equals("data/cars.json"), manifest -> { }));
		long carsBefore = carRepository.count();

		// when / then
		assertThatThrownBy(() -> backupImportService.execute(stagingId))
				.isInstanceOf(BackupImportException.class)
				.cause()
				.isInstanceOfSatisfying(BackupArchiveException.class, ex -> {
					assertThat(ex.reason()).isEqualTo(Reason.DATA_MISMATCH);
					assertThat(ex.getMessage()).contains("cars: data file missing");
				});
		assertThat(carRepository.count()).as("no table may be wiped").isEqualTo(carsBefore);
		assertThat(carRepository.findById(car.getId())).as("the existing car survives").isPresent();
	}

	@Test
	void givenVersionOneArchiveWithoutDiscordTables_whenStaged_thenAccepted() throws Exception {
		// given
		byte[] zip = rewrite(export(), name -> !name.equals("data/discord-global-config.json")
				&& !name.equals("data/discord-post.json"), manifest -> {
			manifest.put("schema_version", 1);
			counts(manifest).remove("discord_global_config");
			counts(manifest).remove("discord_post");
		});

		// when
		var preview = stage(zip);

		// then
		assertThat(preview.schemaVersion()).as("schema version").isEqualTo(1);
		backupImportService.deleteStagingFile(preview.stagingId());
	}

	@Test
	void givenVersionTwoArchiveWithoutDiscordTables_whenStaged_thenRejectedAsDataMismatch() throws Exception {
		// given
		byte[] zip = rewrite(export(), name -> !name.equals("data/discord-post.json"),
				manifest -> counts(manifest).remove("discord_post"));

		// when / then
		assertDataMismatch(() -> stage(zip), "discord_post: data file missing");
	}

	private void assertDataMismatch(ThrowingCallable call, String detail) {
		assertThatThrownBy(call::call)
				.isInstanceOfSatisfying(BackupArchiveException.class, ex -> {
					assertThat(ex.reason()).as("reason").isEqualTo(Reason.DATA_MISMATCH);
					assertThat(ex.getMessage()).as("message").contains(detail);
				});
	}

	@FunctionalInterface
	private interface ThrowingCallable {
		Object call() throws Exception;
	}

	private BackupImportPreview stage(byte[] zip) throws Exception {
		return backupImportService.stage(new MockMultipartFile("file", "manifest-contract.zip", "application/zip", zip));
	}

	private Path stagingDir() throws IOException {
		Path dir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
		Files.createDirectories(dir);
		return dir;
	}

	private byte[] export() throws IOException {
		var out = new ByteArrayOutputStream();
		backupArchiveService.writeZip(out, Instant.now());
		return out.toByteArray();
	}

	private static ObjectNode counts(ObjectNode manifest) {
		return (ObjectNode) manifest.get("table_counts");
	}

	private static byte[] rewrite(byte[] zip, Predicate<String> keep, Consumer<ObjectNode> editManifest)
			throws IOException {
		var out = new ByteArrayOutputStream();
		try (var in = new ZipInputStream(new ByteArrayInputStream(zip));
		     var rewritten = new ZipOutputStream(out)) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				if (!keep.test(entry.getName())) {
					continue;
				}
				byte[] content = in.readAllBytes();
				if (entry.getName().equals("manifest.json")) {
					var manifest = (ObjectNode) JSON.readTree(content);
					editManifest.accept(manifest);
					content = JSON.writeValueAsString(manifest).getBytes(StandardCharsets.UTF_8);
				}
				rewritten.putNextEntry(new ZipEntry(entry.getName()));
				rewritten.write(content);
				rewritten.closeEntry();
			}
		}
		return out.toByteArray();
	}
}
