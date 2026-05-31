package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.schema.BackupSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 74 Plan 05 — SC#3 (ZipBomb half): inflate-size and entry-count limit rejections.
 *
 * <p>Boots the {@code dev} profile and verifies that {@link BackupImportService#stage}
 * rejects ZIP archives that would explode to unsafe sizes when inflated:
 * <ol>
 *   <li>Single-entry per-entry inflate bomb (> {@code MAX_ENTRY_BYTES}) — also exercises
 *       {@code LimitedInputStream}'s defense against {@code ZipEntry.setSize(Long.MAX_VALUE)}
 *       size-spoofing (canonical CVE for naive ZIP-bomb defenses).</li>
 *   <li>Multi-entry total inflate bomb (> {@code MAX_TOTAL_BYTES}).</li>
 *   <li>Entry-count bomb (> {@code MAX_ENTRIES}).</li>
 * </ol>
 *
 * <p>Fixtures are generated programmatically (D-25). No binary blobs committed.
 * Low-entropy payloads (zero bytes) compress very small; inflation explodes to the
 * intended size. The {@code ByteArrayOutputStream} stays small throughout.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupImportZipBombIT {

    @Autowired
    BackupImportService service;

    @Autowired
    BackupSchema backupSchema;

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    Path stagingDir;

    @BeforeEach
    void clearStagingDir() throws IOException {
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
        try (var paths = Files.list(stagingDir)) {
            paths.filter(p -> p.getFileName().toString().startsWith("upload-"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // ignore in @BeforeEach
                        }
                    });
        }
    }

    /**
     * Per-entry inflate bomb — {@code ZipEntry.setSize(Long.MAX_VALUE)} size-spoof defense.
     *
     * <p>The entry header lies about the size (Long.MAX_VALUE). {@code LimitedInputStream}
     * counts actual inflated bytes — not the header value — so the 50 MB cap fires when
     * the inflated byte count exceeds {@code MAX_ENTRY_BYTES}.
     */
    @Test
    void givenEntryWithInflatedSizeExceedingLimit_whenStage_thenThrowsEntryTooLarge()
            throws Exception {
        // given
        long bombSize = BackupImportLimits.MAX_ENTRY_BYTES + 1;
        byte[] maliciousBytes = inflationBombZip(bombSize, backupSchema);
        MockMultipartFile file = new MockMultipartFile(
                "file", "entry-bomb.zip", "application/zip", maliciousBytes);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .as("reason must be ENTRY_TOO_LARGE for per-entry bomb")
                        .isEqualTo(Reason.ENTRY_TOO_LARGE));

        // Staging file must be deleted.
        assertThat(Files.list(stagingDir)
                .filter(p -> p.getFileName().toString().endsWith(".zip"))
                .count())
                .as("staging dir must be empty after ENTRY_TOO_LARGE rejection")
                .isZero();
    }

    /**
     * Total inflate bomb — cumulative inflated bytes across multiple entries exceed
     * {@code MAX_TOTAL_BYTES}. Each individual entry stays under {@code MAX_ENTRY_BYTES}.
     */
    @Test
    void givenTotalInflatedSizeExceedingLimit_whenStage_thenThrowsTotalTooLarge()
            throws Exception {
        // given
        // 12 entries × 45 MB each = 540 MB total > MAX_TOTAL_BYTES (500 MB)
        // Each entry stays under MAX_ENTRY_BYTES (50 MB)
        int entryCount = 12;
        long perEntryBytes = 45L * 1024 * 1024; // 45 MB each, under MAX_ENTRY_BYTES
        byte[] maliciousBytes = totalSizeBombZip(entryCount, perEntryBytes, backupSchema);
        MockMultipartFile file = new MockMultipartFile(
                "file", "total-bomb.zip", "application/zip", maliciousBytes);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .as("reason must be TOTAL_TOO_LARGE for total inflate bomb")
                        .isEqualTo(Reason.TOTAL_TOO_LARGE));

        // Staging file must be deleted.
        assertThat(Files.list(stagingDir)
                .filter(p -> p.getFileName().toString().endsWith(".zip"))
                .count())
                .as("staging dir must be empty after TOTAL_TOO_LARGE rejection")
                .isZero();
    }

    /**
     * Entry-count bomb — archive with {@code MAX_ENTRIES + 1} entries.
     *
     * <p>Each entry is trivial (1 byte), so the ZIP stays small on disk (~50 KB total).
     * The entry-count check fires after reading the manifest + the MAX_ENTRIES+1-th entry.
     */
    @Test
    void givenEntryCountExceedingLimit_whenStage_thenThrowsTooManyEntries()
            throws Exception {
        // given — exactly MAX_ENTRIES + 1 entries after the manifest
        int bombCount = BackupImportLimits.MAX_ENTRIES + 1;
        byte[] maliciousBytes = entryCountBombZip(bombCount, backupSchema);
        MockMultipartFile file = new MockMultipartFile(
                "file", "count-bomb.zip", "application/zip", maliciousBytes);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .as("reason must be TOO_MANY_ENTRIES for entry-count bomb")
                        .isEqualTo(Reason.TOO_MANY_ENTRIES));

        // Staging file must be deleted.
        assertThat(Files.list(stagingDir)
                .filter(p -> p.getFileName().toString().endsWith(".zip"))
                .count())
                .as("staging dir must be empty after TOO_MANY_ENTRIES rejection")
                .isZero();
    }

    // =========================================================================
    // Private fixture builders
    // =========================================================================

    /**
     * Produces a ZIP with: valid manifest (entry #0) + one {@code uploads/} entry with
     * {@code ZipEntry.setSize(Long.MAX_VALUE)} whose payload inflates to
     * {@code perEntryInflatedBytes} zeros (exceeds {@code MAX_ENTRY_BYTES}).
     *
     * <p>The bomb entry is placed under {@code uploads/} so that
     * {@code BackupArchiveService.countUploadFiles()} drains it through a
     * {@code LimitedInputStream}, triggering the per-entry cap. Entries under {@code data/}
     * are only drained by {@code countDataEntries()}, which is not called in the
     * {@code stage()} preview pipeline.
     */
    private static byte[] inflationBombZip(long perEntryInflatedBytes, BackupSchema schema)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeValidManifest(zip);

            // Lie about the size in the header — LimitedInputStream must ignore this value
            ZipEntry bombEntry = new ZipEntry("uploads/bomb.bin");
            bombEntry.setSize(Long.MAX_VALUE);
            zip.putNextEntry(bombEntry);
            // Write zero bytes — Deflate compresses this very efficiently (~200 bytes on disk)
            // but inflation restores the full perEntryInflatedBytes
            byte[] zeros = new byte[(int) Math.min(perEntryInflatedBytes, Integer.MAX_VALUE)];
            Arrays.fill(zeros, (byte) 0);
            zip.write(zeros);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    /**
     * Produces a ZIP with: valid manifest (entry #0) + {@code entryCount} {@code uploads/}
     * entries each inflating to {@code perEntryInflatedBytes} zeros.
     *
     * <p>Each entry stays under {@code MAX_ENTRY_BYTES}; the cumulative total exceeds
     * {@code MAX_TOTAL_BYTES}. Placed under {@code uploads/} so
     * {@code BackupArchiveService.countUploadFiles()} drains them.
     */
    private static byte[] totalSizeBombZip(int entryCount, long perEntryInflatedBytes,
            BackupSchema schema) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeValidManifest(zip);

            byte[] zeros = new byte[(int) Math.min(perEntryInflatedBytes, Integer.MAX_VALUE)];
            Arrays.fill(zeros, (byte) 0);
            for (int i = 0; i < entryCount; i++) {
                zip.putNextEntry(new ZipEntry("uploads/file-" + i + ".bin"));
                zip.write(zeros);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /**
     * Produces a ZIP with: valid manifest (entry #0) + {@code entryCount} trivial
     * 1-byte entries. The entry count exceeds {@code MAX_ENTRIES}.
     */
    private static byte[] entryCountBombZip(int entryCount, BackupSchema schema)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeValidManifest(zip);

            byte[] singleByte = new byte[]{0};
            for (int i = 0; i < entryCount; i++) {
                zip.putNextEntry(new ZipEntry("data/entry-" + i + ".json"));
                zip.write(singleByte);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /**
     * Writes a byte-valid {@code manifest.json} entry as entry #0.
     *
     * <p>schema_version=1 ensures the schema gate passes; table_counts={} is accepted by
     * the backupObjectMapper strict parser ({@code Map<String, Long>} accepts empty maps).
     */
    private static void writeValidManifest(ZipOutputStream zip) throws IOException {
        String manifestJson =
                "{\"schema_version\":1,\"app_version\":\"test\","
                + "\"export_date\":\"2026-05-12T00:00:00Z\",\"table_counts\":{}}";
        zip.putNextEntry(new ZipEntry("manifest.json"));
        zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
