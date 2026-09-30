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
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.ctc.TestHelper;
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
import static org.assertj.core.api.Assertions.assertThatCode;
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
	@Autowired TestHelper testHelper;

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
		var preview = new BackupImportPreview[1];
		assertThatCode(() -> preview[0] = stage(zip)).as("v1 never exported the Discord tables").doesNotThrowAnyException();

		// then
		assertThat(preview[0].schemaVersion()).as("schema version").isEqualTo(1);
		backupImportService.deleteStagingFile(preview[0].stagingId());
	}

	@Test
	void givenVersionTwoArchiveWithoutDiscordTables_whenStaged_thenRejectedAsDataMismatch() throws Exception {
		// given
		byte[] zip = rewrite(export(), name -> !name.equals("data/discord-post.json"),
				manifest -> counts(manifest).remove("discord_post"));

		// when / then
		assertDataMismatch(() -> stage(zip), "discord_post: data file missing");
	}

	@Test
	void givenDataFileUnderAnUnderscoreName_whenStaged_thenTheRestoredFileCountsAsMissing() throws Exception {
		// given
		var season = testHelper.createSeason("Test_Manifest_" + id);
		try {
			byte[] zip = rewrite(export(), name -> true,
					name -> name.equals("data/season-phases.json") ? "data/season_phases.json" : name, manifest -> { });

			// when / then
			assertDataMismatch(() -> stage(zip), "season_phases: data file missing");
		} finally {
			testHelper.deleteSeasonCascade(season);
		}
	}

	@Test
	void givenTwoEntriesWithTheSameName_whenStaged_thenRejectedAsDuplicateEntry() throws Exception {
		// given
		byte[] zip = duplicateCarsEntry(export());

		// when / then
		assertThatThrownBy(() -> stage(zip))
				.isInstanceOfSatisfying(BackupArchiveException.class, ex -> {
					assertThat(ex.reason()).as("reason").isEqualTo(Reason.DUPLICATE_ENTRY);
					assertThat(ex.getMessage()).as("message").contains("data/cars.json");
				});
	}

	@Test
	void givenManifestWithoutTableCounts_whenStaged_thenRejectedAsInvalidManifest() throws Exception {
		// given
		byte[] zip = rewrite(export(), name -> true, manifest -> manifest.remove("table_counts"));

		// when / then
		assertThatThrownBy(() -> stage(zip))
				.isInstanceOfSatisfying(BackupArchiveException.class, ex ->
						assertThat(ex.reason()).as("reason").isEqualTo(Reason.MANIFEST_INVALID));
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
		return rewrite(zip, keep, UnaryOperator.identity(), editManifest);
	}

	/** Writes {@code data/cars.json} twice by patching a same-length sibling name in every header. */
	private static byte[] duplicateCarsEntry(byte[] zip) throws IOException {
		var out = new ByteArrayOutputStream();
		try (var in = new ZipInputStream(new ByteArrayInputStream(zip));
		     var rewritten = new ZipOutputStream(out)) {
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				byte[] content = in.readAllBytes();
				rewritten.putNextEntry(new ZipEntry(entry.getName()));
				rewritten.write(content);
				rewritten.closeEntry();
				if (entry.getName().equals("data/cars.json")) {
					rewritten.putNextEntry(new ZipEntry("data/cars.jsoX"));
					rewritten.write("[]".getBytes(StandardCharsets.UTF_8));
					rewritten.closeEntry();
				}
			}
		}
		byte[] bytes = out.toByteArray();
		byte[] from = "data/cars.jsoX".getBytes(StandardCharsets.US_ASCII);
		byte[] to = "data/cars.json".getBytes(StandardCharsets.US_ASCII);
		for (int i = 0; i <= bytes.length - from.length; i++) {
			if (java.util.Arrays.equals(bytes, i, i + from.length, from, 0, from.length)) {
				System.arraycopy(to, 0, bytes, i, to.length);
			}
		}
		return bytes;
	}

	private static byte[] rewrite(byte[] zip, Predicate<String> keep, UnaryOperator<String> rename,
			Consumer<ObjectNode> editManifest) throws IOException {
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
				rewritten.putNextEntry(new ZipEntry(rename.apply(entry.getName())));
				rewritten.write(content);
				rewritten.closeEntry();
			}
		}
		return out.toByteArray();
	}
}
