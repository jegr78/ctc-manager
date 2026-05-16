package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73-03 — full Spring-context IT for {@link BackupArchiveService}.
 *
 * <p>Boots the {@code dev} profile (H2 in-memory + {@code DevDataSeeder} fixture),
 * then drives a full {@code writeZip()} against a {@link ByteArrayOutputStream} and
 * re-opens the bytes via {@link ZipInputStream}. Asserts the three ZIP-layout
 * invariants the wire contract depends on:
 * <ol>
 *   <li><b>Manifest-first</b> — {@code manifest.json} is entries[0], regardless of
 *       export order or upload count (RESEARCH §L-72.D-14).</li>
 *   <li><b>Per-entity data file</b> — every {@link EntityRef#fileName()} returned by
 *       {@link BackupSchema#getExportOrder()} has a matching {@code data/<slug>.json}
 *       entry in the ZIP.</li>
 *   <li><b>Schema-version round-trip</b> — the {@code manifest.json} parses back via
 *       the qualified {@code backupObjectMapper} and its {@code schemaVersion} equals
 *       {@link BackupSchema#SCHEMA_VERSION}.</li>
 * </ol>
 *
 * <p>Class is NOT {@code @Transactional} — the production
 * {@code @Transactional(readOnly = true)} on {@link BackupArchiveService} provides the
 * Hibernate session needed for {@code Season.tracks} lazy materialization; a
 * test-level transaction would silently mask a regression that removed the production
 * annotation.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupArchiveServiceIT {

	@Autowired
	private BackupArchiveService archiveService;

	@Autowired
	private BackupSchema backupSchema;

	@Autowired
	@Qualifier("backupObjectMapper")
	private ObjectMapper backupObjectMapper;

	@Test
	void givenDevFixture_whenWriteZip_thenManifestIsFirstEntry() throws Exception {
		// given
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());

		// then
		List<String> entryNames = readEntryNames(out.toByteArray());
		assertThat(entryNames).isNotEmpty();
		assertThat(entryNames.get(0))
				.as("manifest.json must be ZipEntry #0 — RESEARCH §L-72.D-14")
				.isEqualTo("manifest.json");
	}

	@Test
	void givenDevFixture_whenWriteZip_thenEveryEntityRefHasMatchingDataEntry() throws Exception {
		// given
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		List<EntityRef> exportOrder = backupSchema.getExportOrder();
		assertThat(exportOrder).as("dev fixture must seed all 24 entities").hasSize(24);

		// when
		archiveService.writeZip(out, Instant.now());

		// then
		Set<String> entryNames = new HashSet<>(readEntryNames(out.toByteArray()));
		for (EntityRef ref : exportOrder) {
			assertThat(entryNames)
					.as("ZIP must contain %s for entity %s",
							ref.fileName(), ref.entityClass().getSimpleName())
					.contains(ref.fileName());
		}
	}

	@Test
	void givenDevFixture_whenWriteZipAndReadManifest_thenSchemaVersionMatches() throws Exception {
		// given
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.parse("2026-05-12T07:00:00Z"));
		byte[] manifestBytes = readEntry(out.toByteArray(), "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then
		assertThat(manifest.schemaVersion()).isEqualTo(BackupSchema.SCHEMA_VERSION);
		assertThat(manifest.appVersion()).isNotBlank();
		assertThat(manifest.exportDate()).isEqualTo(Instant.parse("2026-05-12T07:00:00Z"));
		assertThat(manifest.tableCounts()).isNotEmpty();
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

	private byte[] readEntry(byte[] zipBytes, String name) throws Exception {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().equals(name)) {
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					byte[] buf = new byte[8192];
					int n;
					while ((n = zip.read(buf)) > 0) {
						out.write(buf, 0, n);
					}
					return out.toByteArray();
				}
				zip.closeEntry();
			}
		}
		throw new AssertionError("ZIP entry not found: " + name);
	}
}
