package org.ctc.backup.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 75 / Plan 06 — Failsafe IT for {@code BackupArchiveService.extractUploadsTo(...)}.
 *
 * <p>Scenarios:
 * <ol>
 *   <li>Benign uploads — 3 files extracted byte-identical to destDir.</li>
 *   <li>Path-traversal entry — {@link BackupArchiveException} with {@link Reason#PATH_TRAVERSAL}.</li>
 *   <li>Oversized entry — {@link BackupArchiveException} with {@link Reason#ENTRY_TOO_LARGE}
 *       (via {@link BackupImportLimits#MAX_ENTRY_BYTES} = 50 MB cap; deflate-bomb shape).</li>
 * </ol>
 *
 * <p>All ZIP fixtures are generated programmatically — no binary blobs committed under
 * {@code src/test/resources/}.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupArchiveExtractUploadsIT {

    @Autowired
    private BackupArchiveService archiveService;

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Scenario (a) — benign uploads: all files materialized at dest with identical content
    // -------------------------------------------------------------------------

    @Test
    void givenZipWithBenignUploads_whenExtractCalled_thenAllFilesPresentInDestDir() throws Exception {
        // given
        Path zipPath = tempDir.resolve("benign.zip");
        byte[] payload1 = "team-logo-1\n".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "team-logo-2-with-more-bytes\n".getBytes(StandardCharsets.UTF_8);
        byte[] payload3 = "race-attachment.png-fake-content\n".getBytes(StandardCharsets.UTF_8);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            writeEntry(zos, "uploads/team-logos/a.png", payload1);
            writeEntry(zos, "uploads/team-logos/sub/b.png", payload2);
            writeEntry(zos, "uploads/attachments/c.png", payload3);
            // Add a non-uploads entry that must be skipped.
            writeEntry(zos, "manifest.json", "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8));
        }
        Path destDir = tempDir.resolve("dest");

        // when
        archiveService.extractUploadsTo(zipPath, destDir);

        // then
        assertThat(destDir.resolve("team-logos/a.png"))
                .as("first uploads file must be extracted")
                .exists()
                .hasBinaryContent(payload1);
        assertThat(destDir.resolve("team-logos/sub/b.png"))
                .as("nested uploads file must be extracted")
                .exists()
                .hasBinaryContent(payload2);
        assertThat(destDir.resolve("attachments/c.png"))
                .as("second uploads-root file must be extracted")
                .exists()
                .hasBinaryContent(payload3);

        // Non-uploads entries are NOT materialized under destDir
        assertThat(destDir.resolve("manifest.json"))
                .as("non-uploads entry must NOT leak into destDir")
                .doesNotExist();
    }

    // -------------------------------------------------------------------------
    // Scenario (b) — path-traversal entry rejected with PATH_TRAVERSAL
    // -------------------------------------------------------------------------

    @Test
    void givenZipWithPathTraversalEntry_whenExtractCalled_thenThrowsBackupArchiveException() throws Exception {
        // given — entry attempts to escape destDir via "../" segments after the uploads/ prefix
        Path zipPath = tempDir.resolve("traversal.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            writeEntry(zos, "uploads/../../etc/passwd",
                    "evil-content\n".getBytes(StandardCharsets.UTF_8));
        }
        Path destDir = tempDir.resolve("dest-traversal");

        // when / then
        assertThatThrownBy(() -> archiveService.extractUploadsTo(zipPath, destDir))
                .as("traversal entry must throw PATH_TRAVERSAL")
                .isInstanceOf(BackupArchiveException.class)
                .extracting("reason")
                .isEqualTo(Reason.PATH_TRAVERSAL);
    }

    // -------------------------------------------------------------------------
    // Scenario (c) — oversized entry rejected via LimitedInputStream
    // -------------------------------------------------------------------------

    @Test
    void givenZipWithOversizedEntry_whenExtractCalled_thenThrowsBackupArchiveException() throws Exception {
        // given — synthesize an entry whose inflated size exceeds MAX_ENTRY_BYTES (50 MB).
        // We use Deflate with a single repeated byte so the compressed ZIP itself is small
        // (~50 KB) but the inflated payload trips LimitedInputStream's per-entry cap.
        long oversize = BackupImportLimits.MAX_ENTRY_BYTES + 1024L;
        Path zipPath = tempDir.resolve("oversize.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("uploads/huge.bin"));
            byte[] chunk = new byte[64 * 1024]; // 64 KB of zero bytes (highly compressible)
            long written = 0;
            while (written < oversize) {
                long remaining = oversize - written;
                int len = (int) Math.min(chunk.length, remaining);
                zos.write(chunk, 0, len);
                written += len;
            }
            zos.closeEntry();
        }
        Path destDir = tempDir.resolve("dest-oversize");

        // when / then
        assertThatThrownBy(() -> archiveService.extractUploadsTo(zipPath, destDir))
                .as("oversize entry must throw ENTRY_TOO_LARGE via LimitedInputStream cap")
                .isInstanceOf(BackupArchiveException.class)
                .extracting("reason")
                .isEqualTo(Reason.ENTRY_TOO_LARGE);
    }

    // -------------------------------------------------------------------------
    // Helper — write a single ZIP entry with the given name + bytes (closes the entry).
    // -------------------------------------------------------------------------

    private static void writeEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        OutputStream out = zos;
        out.write(bytes);
        zos.closeEntry();
    }
}
