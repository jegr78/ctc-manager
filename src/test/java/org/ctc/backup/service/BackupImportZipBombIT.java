package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Value("${app.backup.import-backups-dir}")
    String importBackupsDirRaw;

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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"data/teams.json", "notes/unknown.bin"})
    void givenNonUploadEntryInflatingPastTheEntryLimit_whenStage_thenThrowsEntryTooLarge(String entryName)
            throws Exception {
        // given
        byte[] zip = zipWith(entry(entryName, BackupImportLimits.MAX_ENTRY_BYTES + 1));
        MockMultipartFile file = new MockMultipartFile("file", "entry-bomb.zip", "application/zip", zip);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOfSatisfying(BackupArchiveException.class, ex -> assertThat(ex.reason())
                        .as("%s must obey the per-entry limit like uploads do", entryName)
                        .isEqualTo(Reason.ENTRY_TOO_LARGE));
    }

    @Test
    void givenManifestFollowedByOversizedTrailingContent_whenStage_thenThrowsEntryTooLarge() throws Exception {
        // given
        byte[] zip = zipWithManifestPadding(BackupImportLimits.MAX_ENTRY_BYTES + 1);
        MockMultipartFile file = new MockMultipartFile("file", "manifest-bomb.zip", "application/zip", zip);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOfSatisfying(BackupArchiveException.class, ex -> assertThat(ex.reason())
                        .isEqualTo(Reason.ENTRY_TOO_LARGE));
    }

    @Test
    void givenUnknownEntriesExceedingTheTotalLimit_whenStage_thenThrowsTotalTooLarge() throws Exception {
        // given
        long perEntry = 45L * 1024 * 1024;
        String[] names = new String[12];
        for (int i = 0; i < 12; i++) {
            names[i] = "extra/file-" + i + ".bin";
        }
        byte[] zip = zipWith(names, perEntry);
        MockMultipartFile file = new MockMultipartFile("file", "total-bomb.zip", "application/zip", zip);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOfSatisfying(BackupArchiveException.class, ex -> assertThat(ex.reason())
                        .as("12 x 45 MiB outside uploads/ must hit the 500 MiB total")
                        .isEqualTo(Reason.TOTAL_TOO_LARGE));
    }

    @Test
    void givenOversizedEntryPlacedInStagingAfterPreview_whenExecute_thenRejectedBeforeAutoBackup() throws Exception {
        // given
        UUID stagingId = UUID.randomUUID();
        Files.write(stagingDir.resolve("upload-" + stagingId + ".zip"),
                zipWith(entry("notes/unknown.bin", BackupImportLimits.MAX_ENTRY_BYTES + 1)));
        long archivesBefore = countImportBackupDirs();

        // when / then
        assertThatThrownBy(() -> service.execute(stagingId))
                .isInstanceOf(BackupImportException.class)
                .cause()
                .isInstanceOfSatisfying(BackupArchiveException.class, ex -> assertThat(ex.reason())
                        .isEqualTo(Reason.ENTRY_TOO_LARGE));
        assertThat(countImportBackupDirs()).as("no auto-backup directory may be created").isEqualTo(archivesBefore);
    }

    @Test
    void givenOversizedDataEntry_whenRestoreReadsItDirectly_thenThrowsEntryTooLarge(@TempDir Path tmp)
            throws Exception {
        // given
        String firstTable = backupSchema.getExportOrder().get(0).fileName();
        Path zip = Files.write(tmp.resolve("restore-bomb.zip"),
                zipWith(entry(firstTable, BackupImportLimits.MAX_ENTRY_BYTES + 1)));

        // when / then
        assertThatThrownBy(() -> service.restoreAll(zip, new LinkedHashMap<>()))
                .isInstanceOfSatisfying(BackupArchiveException.class, ex -> assertThat(ex.reason())
                        .as("restore must not trust the preview")
                        .isEqualTo(Reason.ENTRY_TOO_LARGE));
    }

    @Test
    void givenDataEntriesExceedingTheTotalLimit_whenRestoreReadsThem_thenThrowsTotalTooLarge(@TempDir Path tmp)
            throws Exception {
        // given
        String[] names = backupSchema.getExportOrder().stream().limit(12).map(EntityRef::fileName)
                .toArray(String[]::new);
        Path zip = Files.write(tmp.resolve("restore-total-bomb.zip"), zipWith(names, 45L * 1024 * 1024));

        // when / then
        assertThatThrownBy(() -> service.restoreAll(zip, new LinkedHashMap<>()))
                .isInstanceOfSatisfying(BackupArchiveException.class, ex -> assertThat(ex.reason())
                        .isEqualTo(Reason.TOTAL_TOO_LARGE));
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

    private long countImportBackupDirs() throws IOException {
        Path root = Paths.get(importBackupsDirRaw).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return 0;
        }
        try (var dirs = Files.list(root)) {
            return dirs.count();
        }
    }

    private record SizedEntry(String name, long inflatedBytes) {
    }

    private static SizedEntry entry(String name, long inflatedBytes) {
        return new SizedEntry(name, inflatedBytes);
    }

    /** Manifest plus one JSON-array entry padded with whitespace to the given inflated size. */
    private static byte[] zipWith(SizedEntry sized) throws IOException {
        return zipWith(new String[]{sized.name()}, sized.inflatedBytes());
    }

    private static byte[] zipWith(String[] names, long inflatedBytesEach) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] padding = whitespaceArray(inflatedBytesEach);
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeValidManifest(zip);
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(padding);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] zipWithManifestPadding(long trailingBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(("{\"schema_version\":1,\"app_version\":\"test\","
                    + "\"export_date\":\"2026-05-12T00:00:00Z\",\"table_counts\":{}}").getBytes(StandardCharsets.UTF_8));
            byte[] spaces = new byte[(int) trailingBytes];
            Arrays.fill(spaces, (byte) ' ');
            zip.write(spaces);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    /** {@code [ ... ]} with spaces in between: valid JSON that only a size limit can stop. */
    private static byte[] whitespaceArray(long totalBytes) {
        byte[] bytes = new byte[(int) totalBytes];
        Arrays.fill(bytes, (byte) ' ');
        bytes[0] = '[';
        bytes[bytes.length - 1] = ']';
        return bytes;
    }
}
