package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 74-04 — Failsafe IT for the reader extension of {@link BackupArchiveService}.
 *
 * <p>Boots the {@code dev} profile (H2 in-memory + DevDataSeeder fixture) and verifies
 * the six reader-method contracts introduced in Plan 04:
 * <ol>
 *   <li>Round-trip: a Phase-73 export is readable via {@code readManifest} (D-14).</li>
 *   <li>Manifest-not-first rejection: {@code Reason.MANIFEST_MISSING}.</li>
 *   <li>Malformed manifest JSON rejection: {@code Reason.MANIFEST_INVALID}.</li>
 *   <li>Data-entry counts: 24-key map, all counts &ge; 0, at least one non-zero.</li>
 *   <li>ZIP-Slip rejection: {@code Reason.PATH_TRAVERSAL} (D-11, SECU-01).</li>
 *   <li>Deflate-bomb rejection: {@code Reason.ENTRY_TOO_LARGE} (D-12, SECU-02).</li>
 * </ol>
 *
 * <p>Class is NOT {@code @Transactional} — the production
 * {@code @Transactional(readOnly = true)} on {@link BackupArchiveService} provides the
 * Hibernate session for the {@code writeZip()} call; a test-level transaction would
 * silently mask a regression that removed the production annotation.
 *
 * <p>All malicious fixtures are generated programmatically (D-25). No binary blobs
 * committed under {@code src/test/resources/backup-fixtures/malicious/}.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupArchiveServiceReadIT {

    @Autowired
    private BackupArchiveService archiveService;

    @Autowired
    private BackupSchema backupSchema;

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupObjectMapper;

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Test 1 — round-trip: Phase-73 export → readManifest → schemaVersion == 1
    // -------------------------------------------------------------------------

    @Test
    void givenPhase73Export_whenReadManifest_thenSchemaVersionEqualsOne() throws Exception {
        // given
        Path zipPath = writePhase73Export();

        // when
        BackupManifest manifest = archiveService.readManifest(zipPath);

        // then
        assertThat(manifest.schemaVersion())
                .as("schemaVersion must equal BackupSchema.SCHEMA_VERSION (== 1) — D-14 round-trip")
                .isEqualTo(BackupSchema.SCHEMA_VERSION);
        assertThat(manifest.appVersion())
                .as("appVersion must be populated by the export pipeline")
                .isNotBlank();
        assertThat(manifest.exportDate())
                .as("exportDate must be a non-null Instant")
                .isNotNull();
        assertThat(manifest.tableCounts())
                .as("tableCounts must not be empty — dev fixture seeds data")
                .isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 2 — manifest not first → MANIFEST_MISSING
    // -------------------------------------------------------------------------

    @Test
    void givenManifestNotFirst_whenReadManifest_thenThrowsManifestMissing() throws Exception {
        // given
        // Entry 0 is a data file, entry 1 is manifest.json — violates manifest-first contract.
        Path zipPath = tempDir.resolve("manifest-not-first.zip");
        writeProgrammaticZip(zipPath,
                new ZipFixture("data/foo.json", "[]".getBytes(StandardCharsets.UTF_8), null),
                new ZipFixture("manifest.json", minimalManifestJson(), null)
        );

        // when / then
        assertThatThrownBy(() -> archiveService.readManifest(zipPath))
                .as("first entry is not manifest.json — must throw MANIFEST_MISSING (D-14)")
                .isInstanceOf(BackupArchiveException.class)
                .extracting("reason")
                .isEqualTo(Reason.MANIFEST_MISSING);
    }

    // -------------------------------------------------------------------------
    // Test 3 — malformed manifest JSON → MANIFEST_INVALID
    // -------------------------------------------------------------------------

    @Test
    void givenManifestMalformedJson_whenReadManifest_thenThrowsManifestInvalid() throws Exception {
        // given
        // manifest.json contains an incomplete JSON object — Jackson cannot deserialize it.
        Path zipPath = tempDir.resolve("manifest-malformed.zip");
        writeProgrammaticZip(zipPath,
                new ZipFixture("manifest.json", "{".getBytes(StandardCharsets.UTF_8), null)
        );

        // when / then
        assertThatThrownBy(() -> archiveService.readManifest(zipPath))
                .as("manifest.json with malformed JSON must throw MANIFEST_INVALID — D-14")
                .isInstanceOf(BackupArchiveException.class)
                .extracting("reason")
                .isEqualTo(Reason.MANIFEST_INVALID);
    }

    // -------------------------------------------------------------------------
    // Test 4 — valid ZIP → per-table row counts
    // -------------------------------------------------------------------------

    @Test
    void givenValidZip_whenCountDataEntries_thenReturnsPerTableRowCounts() throws Exception {
        // given
        Path zipPath = writePhase73Export();

        // when
        Map<String, Long> counts = archiveService.countDataEntries(zipPath);

        // then
        assertThat(counts)
                .as("countDataEntries must return one key per EntityRef — D-20 / VALIDATION SC#1")
                .hasSize(backupSchema.getExportOrder().size());

        for (EntityRef ref : backupSchema.getExportOrder()) {
            assertThat(counts).as("map must contain key for table '%s'", ref.tableName())
                    .containsKey(ref.tableName());
            assertThat(counts.get(ref.tableName()))
                    .as("row count for '%s' must be >= 0", ref.tableName())
                    .isGreaterThanOrEqualTo(0L);
        }

        long nonZeroCount = counts.values().stream().filter(c -> c > 0).count();
        assertThat(nonZeroCount)
                .as("at least one table must have rows — dev fixture seeds data")
                .isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // Test 5 — ZIP-Slip entry → PATH_TRAVERSAL
    // -------------------------------------------------------------------------

    @Test
    void givenZipSlipEntry_whenCountDataEntries_thenThrowsPathTraversal() throws Exception {
        // given
        // A well-formed manifest is first (passes the manifest-first check), then a ZIP-Slip entry.
        Path zipPath = tempDir.resolve("zip-slip.zip");
        writeProgrammaticZip(zipPath,
                new ZipFixture("manifest.json", minimalManifestJson(), null),
                new ZipFixture("../../etc/passwd", "[]".getBytes(StandardCharsets.UTF_8), null)
        );

        // when / then
        assertThatThrownBy(() -> archiveService.countDataEntries(zipPath))
                .as("ZIP-Slip entry '../../etc/passwd' must throw PATH_TRAVERSAL — D-11 / SECU-01")
                .isInstanceOf(BackupArchiveException.class)
                .extracting("reason")
                .isEqualTo(Reason.PATH_TRAVERSAL);
    }

    // -------------------------------------------------------------------------
    // Test 6 — deflate bomb → ENTRY_TOO_LARGE
    // -------------------------------------------------------------------------

    @Test
    void givenEntryWithInflatedSizeExceedingLimit_whenCountDataEntries_thenThrowsEntryTooLarge()
            throws Exception {
        // given
        // Generate a data array with repeated padding rows until inflated size crosses 60 MB.
        // The pattern repeats → compresses well → compressed payload is ~200 KB.
        // Deflater.BEST_SPEED keeps generation under 2 s on M-series hardware.
        Path zipPath = tempDir.resolve("bomb.zip");

        byte[] row = ("{\"k\":\"" + "A".repeat(256) + "\"}").getBytes(StandardCharsets.UTF_8);
        long targetInflatedBytes = BackupImportLimits.MAX_ENTRY_BYTES + 10L * 1024 * 1024; // 60 MB
        long rowsNeeded = (targetInflatedBytes / (row.length + 1)) + 1;

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            // Entry 0: minimal manifest.json
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(minimalManifestJson());
            zos.closeEntry();

            // Entry 1: data/big.json — inflates to > 60 MB
            ZipEntry bigEntry = new ZipEntry("data/big.json");
            bigEntry.setMethod(ZipEntry.DEFLATED);
            zos.setLevel(Deflater.BEST_SPEED);
            zos.putNextEntry(bigEntry);
            zos.write('[');
            zos.write('\n');
            for (long i = 0; i < rowsNeeded; i++) {
                if (i > 0) {
                    zos.write(',');
                    zos.write('\n');
                }
                zos.write(row);
            }
            zos.write('\n');
            zos.write(']');
            zos.closeEntry();

            zos.finish();
        }

        // when / then
        assertThatThrownBy(() -> archiveService.countDataEntries(zipPath))
                .as("entry inflating > 50 MB must throw ENTRY_TOO_LARGE — D-12 / SECU-02")
                .isInstanceOf(BackupArchiveException.class)
                .extracting("reason")
                .isEqualTo(Reason.ENTRY_TOO_LARGE);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Produces a Phase-73 export ZIP in {@code tempDir} and returns its path. */
    private Path writePhase73Export() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        archiveService.writeZip(out, Instant.now());
        Path zipPath = tempDir.resolve("export.zip");
        Files.write(zipPath, out.toByteArray());
        return zipPath;
    }

    /**
     * Builds a hand-rolled ZIP from the supplied {@link ZipFixture} entries, in order.
     *
     * @param target  destination file path
     * @param entries entries to write; order is preserved in the ZIP central directory
     * @throws IOException if writing fails
     */
    private void writeProgrammaticZip(Path target, ZipFixture... entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            for (ZipFixture fixture : entries) {
                ZipEntry entry = new ZipEntry(fixture.name());
                if (fixture.levelOverride() != null) {
                    zos.setLevel(fixture.levelOverride());
                }
                zos.putNextEntry(entry);
                zos.write(fixture.body());
                zos.closeEntry();
            }
            zos.finish();
        }
    }

    /**
     * Returns the bytes of a syntactically valid, minimal {@code manifest.json} that
     * satisfies Jackson's {@code FAIL_ON_UNKNOWN_PROPERTIES=true} deserialization into
     * {@link BackupManifest}. Used as a "harmless first entry" in programmatic ZIPs for
     * tests that exercise later-entry rejection paths (ZIP-Slip, bomb).
     */
    private byte[] minimalManifestJson() {
        String json = """
                {
                  "schema_version": 1,
                  "app_version": "test",
                  "export_date": "2026-01-01T00:00:00Z",
                  "table_counts": {}
                }
                """;
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Fixture descriptor for {@link #writeProgrammaticZip(Path, ZipFixture...)}.
     *
     * @param name          ZIP entry name (may include path separators)
     * @param body          raw bytes to write as the entry body
     * @param levelOverride deflate level override; {@code null} uses the ZipOutputStream default
     */
    private record ZipFixture(String name, byte[] body, Integer levelOverride) {}
}
