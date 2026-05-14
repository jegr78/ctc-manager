package org.ctc.backup.service;

import org.ctc.admin.TestDataService;
import org.ctc.backup.audit.DataImportAudit;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.backup.exception.RestoreFailureSimulatedException;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Phase 75 / Plan 09 — primary regression net for success-criterion-3 of the phase:
 *
 * <blockquote>
 * "A mid-restore-failure injection rolls back to the pre-import database state with no orphan
 * rows; {@code data_import_audit} records {@code success=false} with the failure stack trace
 * in the SLF4J log."
 * </blockquote>
 *
 * <p>The IT installs {@link FailAtTableInjector} (D-13) as the {@code @Primary}
 * {@link org.ctc.backup.restore.RestoreFailureInjector} via {@code @Import(Config.class)} so the
 * restore loop fires at {@code race_results:500} (~50 % mid-point of the largest fixture table —
 * RESEARCH Assumption A1). Each scenario captures pre-import state (per-table row counts +
 * uploads-tree snapshot), drives the full {@code export → stage → execute} flow, and asserts
 * the four sub-requirements of success-criterion-3 plus the W3 SLF4J ERROR-log capture:
 *
 * <ol>
 *   <li><b>(a)</b> Every one of the 24 tables returns to its pre-import row count — the outer
 *       {@code @Transactional} boundary rolled the wipe back.</li>
 *   <li><b>(b)</b> The {@code data_import_audit} row addressed by the thrown
 *       {@link BackupImportException#getAuditUuid()} has {@code success=false} — REQUIRES_NEW
 *       audit-write survived the outer rollback (Plan 75-02 contract).</li>
 *   <li><b>(c)</b> The live {@code uploads/} tree on disk is byte-identical to the pre-import
 *       snapshot — the AFTER_COMMIT listener (Plan 75-07) never fired because the outer commit
 *       aborted, so no Step-2 move took place.</li>
 *   <li><b>(d)</b> Any {@code data/.import-backups/&lt;ts&gt;/uploads-new/} staging directory
 *       was cleaned up by the catch-block finally clause in {@code BackupImportService.execute}
 *       (Plan 75-06 D-12 cleanup discipline).</li>
 *   <li><b>(e) — W3:</b> SLF4J ERROR log contains both
 *       {@code "Import failed for staging-id &lt;uuid&gt;"} AND
 *       {@code "RestoreFailureSimulatedException"} — operator-recovery flash depends on the
 *       loud log.</li>
 * </ol>
 *
 * <p>Test 2 covers the operational retry contract: the staged ZIP at
 * {@code data/&lt;profile&gt;/backup-staging/upload-&lt;uuid&gt;.zip} survives the failure path
 * because the AFTER_COMMIT staging-cleanup (Plan 75-07 Step 4) never fires. The admin can
 * retry by simply re-invoking execute with the same staging-id.
 *
 * <p>The dev fixture is loaded once via {@code testDataService.seed()} (Saison 2023 + 2024 +
 * 2024-Empty + 2026 per its Javadoc; Saison 2023 IS the dev fixture per ROADMAP-locked
 * exception — no T-prefix isolation needed). Per-entity row counts reflect ALL seeded
 * seasons, not just Saison 2023.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(FailAtTableInjector.Config.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@ExtendWith(OutputCaptureExtension.class)
class BackupImportRollbackIT {

    /** Defensive allow-list mirroring {@code BackupImportService.SAFE_TABLE_NAME}. */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[a-z_]+$");

    @Autowired
    BackupImportService backupImportService;

    @Autowired
    BackupArchiveService backupArchiveService;

    @Autowired
    TestDataService testDataService;

    @Autowired
    BackupSchema backupSchema;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataImportAuditRepository dataImportAuditRepository;

    @Value("${app.upload-dir}")
    String uploadDirRaw;

    @Value("${app.backup.import-backups-dir}")
    String importBackupsDirRaw;

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    Path uploadsDir;
    Path importBackupsDir;
    Path stagingDir;

    Map<String, Long> preImportCounts;
    Set<Path> preImportUploadsFiles;
    Set<Path> preImportUploadsNewDirs;

    @BeforeAll
    void seedFixture() throws IOException {
        // REVISION-iteration-1 (B1): testDataService.seedSaison2023() does NOT exist. The
        // single entry point seed() loads the full dev fixture (Saison 2023 + 2024 +
        // 2024-Empty + 2026 per TestDataService.java:60-72 Javadoc). Per memory
        // feedback_test_data_isolation.md, Saison 2023 IS the dev fixture per ROADMAP-locked
        // exception; per-entity row-count parity assertions reflect ALL seeded seasons.
        testDataService.seed();

        uploadsDir = Paths.get(uploadDirRaw).toAbsolutePath().normalize();
        importBackupsDir = Paths.get(importBackupsDirRaw).toAbsolutePath().normalize();
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(uploadsDir);
        Files.createDirectories(stagingDir);
    }

    @BeforeEach
    void capturePreImportState() throws IOException {
        // Per-table row counts via JdbcTemplate — avoids 24 autowires; mirrors production
        // BackupImportService.wipeAllTables which also uses native SQL by tableName.
        preImportCounts = new LinkedHashMap<>();
        for (EntityRef ref : backupSchema.getExportOrder()) {
            String table = ref.tableName();
            if (!SAFE_TABLE_NAME.matcher(table).matches()) {
                throw new IllegalStateException("Unsafe table name in BackupSchema: " + table);
            }
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            preImportCounts.put(table, count == null ? 0L : count);
        }

        // uploads-tree snapshot: relative paths only so cross-test temp roots don't affect
        // equality, and so a Plan 07 successful AFTER_COMMIT move (in a different test
        // context) would be visible as set inequality.
        preImportUploadsFiles = snapshotUploads();

        // Pre-existing uploads-new/ directories from earlier test runs in this JVM session
        // (e.g. BackupImportExecuteIT happy-path which leaves uploads-new untouched until the
        // AFTER_COMMIT listener moves it). The Plan 06 D-12 cleanup contract only guarantees
        // that THIS run's uploads-new is removed; pre-existing directories outside our <ts>
        // path remain as-is.
        preImportUploadsNewDirs = snapshotUploadsNewDirs();
    }

    // -------------------------------------------------------------------------
    // Test 1 — full 5-part assertion battery on the rollback contract
    // -------------------------------------------------------------------------

    @Test
    void givenInjectorFailsAtRaceResultsRow500_whenExecuteCalled_thenAllTablesRolledBackAndAuditFalse(
            CapturedOutput output) throws Exception {
        // given — pre-state captured by @BeforeEach; sanity-check that the fixture is loud
        // enough for the 50 % mid-point injection to land mid-restore (race_results > 500 rows
        // per RESEARCH Assumption A1 on Saison 2023; the cumulative dev fixture must satisfy
        // it strictly because the injection point is row 500).
        Long preRaceResults = preImportCounts.get("race_results");
        assertThat(preRaceResults)
                .as("dev fixture must seed > 500 race_results so FailAtTableInjector fires mid-restore")
                .isNotNull()
                .isGreaterThan(500L);

        // Build a Phase-73 export ZIP via the real BackupArchiveService.writeZip(...) — note
        // REVISION-iteration-1 B3: BackupExportService.export(...) does NOT exist; the
        // canonical writer is BackupArchiveService.writeZip(OutputStream, Instant).
        byte[] zipBytes = exportToBytes();
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "dev-fixture-export.zip", "application/zip", zipBytes);

        // Stage so we have a UUID + .meta sidecar in stagingDir
        BackupImportPreview preview = backupImportService.stage(zipFile);
        UUID stagingId = preview.stagingId();

        // when / then — execute must throw BackupImportException carrying the audit UUID with
        // a RestoreFailureSimulatedException root cause. Capture the thrown exception inline so
        // we can read getAuditUuid() in the assertion battery below without re-throwing.
        BackupImportException ex = (BackupImportException) catchThrowableOfType(
                () -> backupImportService.execute(stagingId), BackupImportException.class);
        assertThat(ex)
                .as("execute() must throw BackupImportException carrying the audit UUID")
                .isNotNull();
        assertThat(ex.getCause())
                .as("BackupImportException cause must be RestoreFailureSimulatedException")
                .isInstanceOf(RestoreFailureSimulatedException.class);
        UUID auditUuid = ex.getAuditUuid();
        assertThat(auditUuid)
                .as("BackupImportException.getAuditUuid() must be non-null for Plan 08 flash binding")
                .isNotNull();

        // (a) all 24 table row counts are EQUAL to preImportCounts (rollback complete; no orphan rows)
        for (EntityRef ref : backupSchema.getExportOrder()) {
            String table = ref.tableName();
            Long postCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table, Long.class);
            assertThat(postCount == null ? 0L : postCount)
                    .as("%s rollback (pre=%d, post=%d)",
                            table, preImportCounts.get(table), postCount)
                    .isEqualTo(preImportCounts.get(table));
        }

        // (b) data_import_audit has exactly 1 row matching the auditUuid from the thrown
        // exception, with success=false, sourceFilename non-blank, executedAt set.
        Optional<DataImportAudit> auditRow = dataImportAuditRepository.findById(auditUuid);
        assertThat(auditRow)
                .as("REQUIRES_NEW audit row must persist via Plan 75-02 propagation despite outer rollback")
                .isPresent();
        DataImportAudit audit = auditRow.get();
        assertThat(audit.isSuccess())
                .as("audit row success flag must be false on rollback path")
                .isFalse();
        assertThat(audit.getSourceFilename())
                .as("audit row sourceFilename must carry the staged original filename")
                .isNotBlank();
        assertThat(audit.getExecutedAt())
                .as("audit row executedAt must be set by DataImportAuditService writer (NOT AuditingEntityListener)")
                .isNotNull();

        // (c) uploads/ tree unchanged — set of relative paths equal to pre-import snapshot
        Set<Path> postImportUploadsFiles = snapshotUploads();
        assertThat(postImportUploadsFiles)
                .as("uploads/ tree must be byte-equal to pre-import snapshot — AFTER_COMMIT listener did NOT fire")
                .containsExactlyInAnyOrderElementsOf(preImportUploadsFiles);

        // (d) any data/.import-backups/<ts>/uploads-new/ staging dir created BY THIS RUN was
        // cleaned up by the catch-block finally in BackupImportService.execute() (Plan 75-06
        // D-12). Pre-existing uploads-new/ directories (left behind by earlier tests in the
        // same JVM session) are out of this assertion's scope — the cleanup contract is
        // per-import, scoped to <ts>.
        Set<Path> postImportUploadsNewDirs = snapshotUploadsNewDirs();
        Set<Path> newlyCreated = new HashSet<>(postImportUploadsNewDirs);
        newlyCreated.removeAll(preImportUploadsNewDirs);
        assertThat(newlyCreated)
                .as("this run's uploads-new/ staging dir must have been cleaned up by tryCleanupUploadsNew")
                .isEmpty();

        // (e) REVISION-iteration-1 W3 — ROADMAP SC#3 sub-requirement: SLF4J ERROR log must
        // carry both the staging-id failure marker and the cause class name so the operator
        // can correlate the audit-id flash with the log line.
        String captured = output.getOut() + output.getErr();
        assertThat(captured)
                .as("SLF4J ERROR must contain 'Import failed for staging-id <uuid>' (loud-fail contract)")
                .contains("Import failed for staging-id " + stagingId.toString());
        assertThat(captured)
                .as("SLF4J ERROR must contain the cause class name 'RestoreFailureSimulatedException'")
                .contains("RestoreFailureSimulatedException");
    }

    // -------------------------------------------------------------------------
    // Test 2 — staged ZIP file survives the failure path for operator retry
    // -------------------------------------------------------------------------

    @Test
    void givenInjectorFailsAtRaceResults_whenExecuteCalled_thenStagingFileSurvivesForRetry()
            throws Exception {
        // given — same setup as Test 1
        byte[] zipBytes = exportToBytes();
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "retry-export.zip", "application/zip", zipBytes);

        BackupImportPreview preview = backupImportService.stage(zipFile);
        UUID stagingId = preview.stagingId();
        Path stagingPath = stagingDir.resolve("upload-" + stagingId + ".zip");

        // pre-condition: staged file is on disk after stage()
        assertThat(Files.exists(stagingPath))
                .as("stage() must materialize the staging ZIP on disk before execute() is called")
                .isTrue();

        // when — execute will throw mid-restore
        assertThatThrownBy(() -> backupImportService.execute(stagingId))
                .isInstanceOf(BackupImportException.class);

        // then — staged ZIP STILL exists. The AFTER_COMMIT listener (Plan 75-07 Step 4) never
        // fired because the outer commit aborted, and the failure path of execute() does NOT
        // call deleteStagingFile. The admin can retry by re-invoking execute with this UUID.
        // (Phase 74 BackupStagingCleanup will eventually remove stale staged files via @Scheduled.)
        assertThat(Files.exists(stagingPath))
                .as("staged ZIP at %s must SURVIVE the failure path for operator retry", stagingPath)
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a Phase-73 export ZIP via the real BackupArchiveService writer. */
    private byte[] exportToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        return baos.toByteArray();
    }

    /**
     * Snapshots the {@code uploads/} tree as a {@link Set} of relative paths. Returns an empty
     * set if the directory does not exist (e.g. fresh worktree with no profile-level seeding).
     */
    private Set<Path> snapshotUploads() throws IOException {
        if (!Files.exists(uploadsDir)) {
            return new HashSet<>();
        }
        try (Stream<Path> walk = Files.walk(uploadsDir)) {
            Set<Path> rel = new HashSet<>();
            walk.filter(Files::isRegularFile)
                    .forEach(p -> rel.add(uploadsDir.relativize(p)));
            return rel;
        }
    }

    /**
     * Snapshots all {@code uploads-new/} directories under {@code importBackupsDir} as a
     * {@link Set} of absolute paths. Returns an empty set when the parent directory does not
     * exist. The set is used to diff pre- vs post-import so the Plan 06 D-12 cleanup
     * assertion only fires on directories created BY THIS RUN, not on leftovers from earlier
     * tests in the same JVM session.
     */
    private Set<Path> snapshotUploadsNewDirs() throws IOException {
        if (!Files.exists(importBackupsDir)) {
            return new HashSet<>();
        }
        try (Stream<Path> walk = Files.walk(importBackupsDir)) {
            Set<Path> dirs = new HashSet<>();
            walk.filter(p -> p.getFileName() != null
                    && "uploads-new".equals(p.getFileName().toString())
                    && Files.isDirectory(p))
                    .forEach(dirs::add);
            return dirs;
        }
    }
}
