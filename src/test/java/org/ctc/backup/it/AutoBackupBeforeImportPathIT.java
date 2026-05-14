package org.ctc.backup.it;

import org.ctc.admin.TestDataService;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 76 / Plan 03 — happy-path Failsafe IT for the Step 0.5 pre-import auto-backup
 * (CONTEXT D-14 / D-15 / D-16 / SECU-07).
 *
 * <p>Drives the full export → stage → execute round trip and asserts:
 * <ol>
 *   <li>{@code data/.import-backups/<ts>/auto-backup-before-import.zip} exists after execute.</li>
 *   <li>The ZIP is non-empty and parses via {@code backupArchive.readManifest(autoBackupZip)}.</li>
 *   <li>The manifest {@code schemaVersion == BackupSchema.SCHEMA_VERSION} (valid backup).</li>
 *   <li>A sibling {@code <ts>/uploads-old/} directory exists with the SAME {@code <ts>}
 *       (D-15 single-source-of-truth).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoBackupBeforeImportPathIT {

    @Autowired
    BackupImportService backupImportService;

    @Autowired
    BackupArchiveService backupArchiveService;

    @Autowired
    TestDataService testDataService;

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    @Value("${app.backup.import-backups-dir}")
    String importBackupsDirRaw;

    Path stagingDir;
    Path importBackupsDir;

    /** Tracks <ts> dirs created during each test for @AfterEach cleanup. */
    List<Path> createdTsDirs;

    @BeforeAll
    void seedFixture() throws IOException {
        testDataService.seed();
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        importBackupsDir = Paths.get(importBackupsDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
        Files.createDirectories(importBackupsDir);
    }

    @BeforeEach
    void cleanupImportBackupsDirsBefore() throws IOException {
        cleanupImportBackupsDirs();
    }

    @AfterEach
    void cleanupImportBackupsDirsAfter() throws IOException {
        cleanupImportBackupsDirs();
    }

    /**
     * Remove every <ts> sub-tree under {@code data/.import-backups/} so the per-second
     * timestamp in Step 0.5 never collides with leftovers from earlier ITs (e.g. empty
     * {@code <ts>/} directories left behind by {@link AutoBackupBeforeImportFailureIT}
     * after its {@code tryDeletePartialAutoBackup} cleanup).
     */
    private void cleanupImportBackupsDirs() throws IOException {
        if (!Files.exists(importBackupsDir)) {
            return;
        }
        try (Stream<Path> children = Files.list(importBackupsDir)) {
            children.forEach(tsDir -> {
                try (Stream<Path> walk = Files.walk(tsDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    });
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Test 1 — happy path: auto-backup ZIP exists at correct path, shared <ts>
    // -------------------------------------------------------------------------

    @Test
    void givenSuccessfulImport_whenExecuteCompletes_thenAutoBackupZipExistsWithSharedTimestampDirectory()
            throws Exception {
        // given — export + stage
        UUID stagingId = exportAndStage("auto-backup-path-it-export.zip");

        // Snapshot existing <ts> dirs BEFORE execute to isolate dirs created by this run.
        List<Path> preTsDirs = listImportBackupsDirs();

        // when
        BackupImportResult result = backupImportService.execute(stagingId);

        // then — execute succeeded
        assertThat(result).as("execute() must return a non-null result").isNotNull();

        // Identify the new <ts> directory created by this execute call
        List<Path> postTsDirs = listImportBackupsDirs();
        List<Path> newTsDirs = postTsDirs.stream()
                .filter(p -> !preTsDirs.contains(p))
                .toList();
        assertThat(newTsDirs)
                .as("exactly one new <ts> directory must exist under data/.import-backups/")
                .hasSize(1);

        Path tsDir = newTsDirs.get(0);

        // Assert auto-backup ZIP exists at <ts>/auto-backup-before-import.zip
        Path autoBackupZip = tsDir.resolve("auto-backup-before-import.zip");
        assertThat(autoBackupZip)
                .as("auto-backup-before-import.zip must exist under the <ts> directory")
                .exists();
        assertThat(Files.size(autoBackupZip))
                .as("auto-backup-before-import.zip must be non-empty")
                .isGreaterThan(0);

        // Assert uploads-old/ sibling exists with the SAME <ts> (D-15 single-source-of-truth)
        Path uploadsOld = tsDir.resolve("uploads-old");
        assertThat(uploadsOld)
                .as("uploads-old/ directory must exist as a sibling to auto-backup-before-import.zip "
                        + "under the same <ts> directory (D-15)")
                .exists();
    }

    // -------------------------------------------------------------------------
    // Test 2 — manifest schemaVersion validates the ZIP is a real backup
    // -------------------------------------------------------------------------

    @Test
    void givenSuccessfulImport_whenInspectingAutoBackup_thenManifestSchemaVersionMatches()
            throws Exception {
        // given
        UUID stagingId = exportAndStage("auto-backup-manifest-it-export.zip");
        List<Path> preTsDirs = listImportBackupsDirs();

        // when
        backupImportService.execute(stagingId);

        // then — find the auto-backup ZIP
        List<Path> newTsDirs = listImportBackupsDirs().stream()
                .filter(p -> !preTsDirs.contains(p))
                .toList();
        assertThat(newTsDirs).hasSize(1);
        Path autoBackupZip = newTsDirs.get(0).resolve("auto-backup-before-import.zip");
        assertThat(autoBackupZip).exists();

        // Parse the manifest from the auto-backup ZIP — must be a valid backup
        BackupManifest manifest = backupArchiveService.readManifest(autoBackupZip);
        assertThat(manifest.schemaVersion())
                .as("auto-backup manifest schemaVersion must match BackupSchema.SCHEMA_VERSION")
                .isEqualTo(BackupSchema.SCHEMA_VERSION);
        assertThat(manifest.tableCounts())
                .as("auto-backup manifest tableCounts must be non-empty (pre-wipe DB had data)")
                .isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID exportAndStage(String filename) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        byte[] zipBytes = baos.toByteArray();
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/zip", zipBytes);
        return backupImportService.stage(file).stagingId();
    }

    private List<Path> listImportBackupsDirs() throws IOException {
        if (!Files.exists(importBackupsDir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(importBackupsDir)) {
            return children.filter(Files::isDirectory).sorted().toList();
        }
    }
}
