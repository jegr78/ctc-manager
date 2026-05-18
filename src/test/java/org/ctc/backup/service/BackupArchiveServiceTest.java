package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 73-03 — Mockito unit test for {@link BackupArchiveService}.
 *
 * <p>Verifies the ZIP-layout invariants regardless of dataset:
 * <ul>
 *   <li>{@code manifest.json} is ALWAYS the first entry.</li>
 *   <li>{@code data/<slug>.json} entries follow in {@link BackupSchema#getExportOrder()} order.</li>
 *   <li>{@code uploads/<rel>} entries come last.</li>
 *   <li>Upload entries whose {@code relativePath} contains {@code ".."} are skipped (ZIP-slip defense).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BackupArchiveServiceTest {

	@Mock private BackupExportService backupExportService;
	@Mock private BackupSchema backupSchema;

	private BackupArchiveService archiveService;
	private ObjectMapper mapper;

	@BeforeEach
	void setUp() {
		mapper = new ObjectMapper().registerModule(new JavaTimeModule());
		archiveService = new BackupArchiveService(backupExportService, backupSchema, mapper, "1.6.0");
	}

	@Test
	void givenStubbedSchemaAndExport_whenWriteZip_thenManifestIsFirstEntry() throws Exception {
		// given
		List<EntityRef> order = List.of(
				new EntityRef(String.class, "cars", "data/cars.json"),
				new EntityRef(String.class, "teams", "data/teams.json")
		);
		when(backupSchema.getExportOrder()).thenReturn(order);
		when(backupExportService.countRowsPerTable()).thenReturn(Map.of("cars", 0L, "teams", 0L));
		when(backupExportService.fetchAllForBackup(any())).thenReturn(List.of());
		when(backupExportService.enumerateReferencedUploads()).thenReturn(List.of());

		// when
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		archiveService.writeZip(out, Instant.parse("2026-05-12T07:00:00Z"));

		// then
		List<String> entryNames = readEntryNames(out.toByteArray());
		assertThat(entryNames).isNotEmpty();
		assertThat(entryNames.get(0)).isEqualTo("manifest.json");
	}

	@Test
	void givenTwoEntitiesAndNoUploads_whenWriteZip_thenDataEntriesFollowExportOrderAfterManifest() throws Exception {
		// given
		List<EntityRef> order = List.of(
				new EntityRef(String.class, "cars", "data/cars.json"),
				new EntityRef(String.class, "teams", "data/teams.json")
		);
		when(backupSchema.getExportOrder()).thenReturn(order);
		when(backupExportService.countRowsPerTable()).thenReturn(Map.of("cars", 0L, "teams", 0L));
		when(backupExportService.fetchAllForBackup(any())).thenReturn(List.of());
		when(backupExportService.enumerateReferencedUploads()).thenReturn(List.of());

		// when
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		archiveService.writeZip(out, Instant.now());

		// then
		List<String> entryNames = readEntryNames(out.toByteArray());
		assertThat(entryNames).containsExactly(
				"manifest.json", "data/cars.json", "data/teams.json"
		);
	}

	@Test
	void givenUploads_whenWriteZip_thenUploadsEntriesComeAfterDataEntries(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
		// given
		Path logo = tempDir.resolve("logo.png");
		Files.writeString(logo, "image-bytes");
		List<EntityRef> order = List.of(
				new EntityRef(String.class, "cars", "data/cars.json")
		);
		when(backupSchema.getExportOrder()).thenReturn(order);
		when(backupExportService.countRowsPerTable()).thenReturn(Map.of("cars", 0L));
		when(backupExportService.fetchAllForBackup(any())).thenReturn(List.of());
		when(backupExportService.enumerateReferencedUploads()).thenReturn(List.of(
				new UploadEntry(logo, "teams/alf/logo.png")
		));

		// when
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		archiveService.writeZip(out, Instant.now());

		// then
		List<String> entryNames = readEntryNames(out.toByteArray());
		assertThat(entryNames).containsExactly(
				"manifest.json", "data/cars.json", "uploads/teams/alf/logo.png"
		);
	}

	@Test
	void givenUploadEntryWithDotDotInRelativePath_whenWriteZip_thenEntryIsSkippedAndLoggedAsWarning(
			@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
		// given
		Path safeFile = tempDir.resolve("safe.png");
		Path traversalFile = tempDir.resolve("traversal.png");
		Files.writeString(safeFile, "ok");
		Files.writeString(traversalFile, "evil");
		List<EntityRef> order = List.of();
		when(backupSchema.getExportOrder()).thenReturn(order);
		when(backupExportService.countRowsPerTable()).thenReturn(Map.of());
		when(backupExportService.enumerateReferencedUploads()).thenReturn(List.of(
				new UploadEntry(traversalFile, "../etc/passwd"),
				new UploadEntry(safeFile, "teams/safe.png")
		));

		// when
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		archiveService.writeZip(out, Instant.now());

		// then
		List<String> entryNames = readEntryNames(out.toByteArray());
		assertThat(entryNames).containsExactly(
				"manifest.json", "uploads/teams/safe.png"
		);
	}

	private List<String> readEntryNames(byte[] zipBytes) throws Exception {
		List<String> names = new ArrayList<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				names.add(entry.getName());
				zip.closeEntry();
			}
		}
		return names;
	}
}
