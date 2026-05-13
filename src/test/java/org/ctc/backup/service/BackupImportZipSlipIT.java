package org.ctc.backup.service;

import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 74 Plan 05 — SC#3 (ZIP-Slip half): path-traversal attack rejection.
 *
 * <p>Boots the {@code dev} profile and verifies that {@link BackupImportService#stage}
 * rejects ZIP archives whose entries attempt to escape the base directory via:
 * <ol>
 *   <li>Dot-dot path segments ({@code ../../etc/passwd})</li>
 *   <li>Absolute entry names ({@code /etc/passwd})</li>
 *   <li>Dot-dot under the uploads prefix ({@code uploads/../../etc/passwd})</li>
 * </ol>
 *
 * <p>All fixtures are generated programmatically (D-25). No binary blobs committed.
 * The manifest entry in each fixture is byte-valid for {@code backupObjectMapper}
 * strict parsing — a malformed manifest would throw {@code MANIFEST_INVALID} before
 * the traversal check, masking the intended assertion.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackupImportZipSlipIT {

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
     * ZIP with a dot-dot entry after the manifest — traversal fires during data-entry scan.
     *
     * <p>The offending entry is placed AFTER the valid manifest (entry #1) to prove that
     * the hardening is end-to-end (not just on entry #0).
     */
    @Test
    void givenZipWithDotDotEntry_whenStage_thenThrowsPathTraversal_andStagingFileDeleted()
            throws Exception {
        // given
        byte[] maliciousBytes = zipWithMaliciousEntry("../../etc/passwd", backupSchema);
        MockMultipartFile file = new MockMultipartFile(
                "file", "slip-dotdot.zip", "application/zip", maliciousBytes);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .as("reason must be PATH_TRAVERSAL for dot-dot entry")
                        .isEqualTo(Reason.PATH_TRAVERSAL));

        // D-16: try/finally must have deleted the staging file
        assertThat(Files.list(stagingDir)
                .filter(p -> p.getFileName().toString().endsWith(".zip"))
                .count())
                .as("staging dir must contain zero ZIP files after PATH_TRAVERSAL rejection")
                .isZero();
    }

    /**
     * ZIP with an absolute path entry — {@code PathTraversalGuard} rejects absolute paths
     * before the resolve/startsWith check.
     */
    @Test
    void givenZipWithAbsolutePathEntry_whenStage_thenThrowsPathTraversal()
            throws Exception {
        // given
        byte[] maliciousBytes = zipWithMaliciousEntry("/etc/passwd", backupSchema);
        MockMultipartFile file = new MockMultipartFile(
                "file", "slip-absolute.zip", "application/zip", maliciousBytes);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .as("reason must be PATH_TRAVERSAL for absolute path entry")
                        .isEqualTo(Reason.PATH_TRAVERSAL));

        // staging file must be cleaned up
        assertThat(Files.list(stagingDir)
                .filter(p -> p.getFileName().toString().endsWith(".zip"))
                .count())
                .as("staging dir must be empty after absolute path rejection")
                .isZero();
    }

    /**
     * ZIP with a dot-dot entry prefixed by {@code uploads/} — proves hardening applies
     * to every entry name, not just {@code manifest.json} or {@code data/} entries.
     */
    @Test
    void givenZipWithDotDotEntryInUploadsPath_whenStage_thenThrowsPathTraversal()
            throws Exception {
        // given
        byte[] maliciousBytes = zipWithMaliciousEntry("uploads/../../etc/passwd", backupSchema);
        MockMultipartFile file = new MockMultipartFile(
                "file", "slip-uploads.zip", "application/zip", maliciousBytes);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .as("reason must be PATH_TRAVERSAL for uploads dot-dot entry")
                        .isEqualTo(Reason.PATH_TRAVERSAL));

        // staging file must be cleaned up
        assertThat(Files.list(stagingDir)
                .filter(p -> p.getFileName().toString().endsWith(".zip"))
                .count())
                .as("staging dir must be empty after uploads dot-dot rejection")
                .isZero();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Produces a ZIP with:
     * <ul>
     *   <li>Entry #0: valid {@code manifest.json} (schema_version=1, empty table_counts)</li>
     *   <li>Entry #1: the malicious entry name with payload {@code "malicious"}</li>
     * </ul>
     *
     * <p>The manifest JSON is byte-valid for {@code backupObjectMapper} strict parsing.
     * Using schema_version=1 ensures the schema gate passes — the traversal check is
     * the intended rejection trigger.
     */
    private static byte[] zipWithMaliciousEntry(String maliciousEntryName, BackupSchema schema)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // Entry #0: valid manifest (schema_version=1 so schema gate passes)
            String manifestJson =
                    "{\"schema_version\":1,\"app_version\":\"test\","
                    + "\"export_date\":\"2026-05-12T00:00:00Z\",\"table_counts\":{}}";
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // Entry #1: the malicious entry — triggers PathTraversalGuard
            zip.putNextEntry(new ZipEntry(maliciousEntryName));
            zip.write("malicious".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
