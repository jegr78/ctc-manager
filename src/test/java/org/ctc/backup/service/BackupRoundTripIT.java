package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73-03 — round-trip IT through the Phase 72 manifest contract.
 *
 * <p>Drives a full export against the dev fixture, then re-opens the ZIP and parses
 * {@code manifest.json} back via the qualified {@code backupObjectMapper}. Asserts:
 * <ol>
 *   <li>The manifest deserialises into a {@link BackupManifest} record with no
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES} fallout — every field on the wire matches
 *       the record shape (Phase 72 contract).</li>
 *   <li>{@link BackupManifest#schemaVersion()} equals {@link BackupSchema#SCHEMA_VERSION}.</li>
 *   <li>{@link BackupManifest#appVersion()} is non-null and non-empty.</li>
 *   <li>{@link BackupManifest#exportDate()} matches the supplied {@link Instant}.</li>
 *   <li>The {@code tableCounts} keyset equals the set of {@code ref.tableName()} values
 *       in {@link BackupSchema#getExportOrder()}.</li>
 *   <li>The sum of {@code tableCounts} entries that have a non-zero count corresponds
 *       to a subset of the {@code data/*.json} ZIP entries (count entries are upper-bound
 *       by ZIP entries; data entries always equal the export-order size).</li>
 * </ol>
 *
 * <p>This is the local round-trip for Plan 73-03. Phase 77 will extend it into a full
 * export → wipe → import → re-assert round-trip; this plan ships only the manifest leg.
 */
@SpringBootTest
@ActiveProfiles("dev")
class BackupRoundTripIT {

	@Autowired
	private BackupArchiveService archiveService;

	@Autowired
	private BackupSchema backupSchema;

	@Autowired
	@Qualifier("backupObjectMapper")
	private ObjectMapper backupObjectMapper;

	@Test
	void givenDevFixture_whenWriteZipAndReadManifest_thenManifestRoundTripsThroughBackupObjectMapper()
			throws Exception {
		// given
		Instant exportDate = Instant.parse("2026-05-12T07:00:00Z");
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, exportDate);
		byte[] manifestBytes = readEntry(out.toByteArray(), "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then
		assertThat(manifest).as("manifest must deserialise via the qualified backupObjectMapper")
				.isNotNull();
		assertThat(manifest.schemaVersion()).isEqualTo(BackupSchema.SCHEMA_VERSION);
		assertThat(manifest.appVersion()).isNotBlank();
		assertThat(manifest.exportDate()).isEqualTo(exportDate);
	}

	@Test
	void givenDevFixture_whenWriteZipWithCurrentInstant_thenExportDateWithinAcceptableWindow()
			throws Exception {
		// given — capture the test-start instant and pass a freshly computed Instant.now()
		Instant beforeWrite = Instant.now();
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		Instant exportDate = Instant.now();
		archiveService.writeZip(out, exportDate);
		Instant afterWrite = Instant.now();
		byte[] manifestBytes = readEntry(out.toByteArray(), "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then — exportDate must equal the passed instant and lie within the test window
		// (defense-in-depth: zero clock drift, but allow a 60s tolerance for slow CI runs).
		assertThat(manifest.exportDate()).isEqualTo(exportDate);
		assertThat(Duration.between(beforeWrite, manifest.exportDate()))
				.isLessThanOrEqualTo(Duration.between(beforeWrite, afterWrite).plusSeconds(60));
	}

	@Test
	void givenDevFixture_whenReadManifest_thenTableCountsKeysetEqualsExportOrderTableNames()
			throws Exception {
		// given
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());
		byte[] manifestBytes = readEntry(out.toByteArray(), "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then
		Set<String> expectedKeys = new HashSet<>();
		backupSchema.getExportOrder().forEach(ref -> expectedKeys.add(ref.tableName()));
		assertThat(manifest.tableCounts().keySet())
				.as("tableCounts keys must match the export-order tableName set")
				.containsExactlyInAnyOrderElementsOf(expectedKeys);
	}

	@Test
	void givenDevFixture_whenWriteZip_thenEntityCountsKeysMatchDataJsonEntryCount() throws Exception {
		// given
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());
		byte[] zipBytes = out.toByteArray();
		byte[] manifestBytes = readEntry(zipBytes, "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then — number of tableCounts entries equals the number of data/*.json entries.
		long dataEntries = countEntriesMatching(zipBytes, "data/", ".json");
		assertThat((long) manifest.tableCounts().size())
				.as("entityCounts entries must equal the data/*.json entry count")
				.isEqualTo(dataEntries);
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

	private long countEntriesMatching(byte[] zipBytes, String prefix, String suffix) throws Exception {
		long count = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().startsWith(prefix) && entry.getName().endsWith(suffix)) {
					count++;
				}
				zip.closeEntry();
			}
		}
		return count;
	}
}
