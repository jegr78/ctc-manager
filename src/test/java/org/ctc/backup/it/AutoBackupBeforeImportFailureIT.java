package org.ctc.backup.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.ctc.admin.TestDataService;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.exception.AutoBackupBeforeImportException;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 76 / Plan 03 — failure-path Failsafe IT for the Step 0.5 pre-import auto-backup
 * (CONTEXT D-17 / D-18 / D-19 / SECU-07).
 *
 * <p>Drives the {@code stage → execute} flow with the pre-import auto-backup ZIP write
 * forced to fail via a {@link MockitoSpyBean} that throws {@link IOException} on the
 * Step 0.5 {@code writeZip} call. Asserts the four failure-path post-conditions:
 *
 * <ol>
 *   <li>{@link AutoBackupBeforeImportException} is thrown (a distinct subclass of
 *       {@code BackupImportException} — RESEARCH Pitfall #3).</li>
 *   <li>No database mutation occurred — per-table row counts are byte-equal to the
 *       pre-import snapshot (Step 1 wipe never ran).</li>
 *   <li>Exactly one new {@code data_import_audit} row exists with
 *       {@code success=false}, {@code table_counts_wiped="{}"}, and
 *       {@code table_counts_restored="{}"} (D-18 empty count maps).</li>
 *   <li>The partial ZIP is cleaned up on POSIX (D-19); on Windows the assertion is
 *       skipped because the file-locking semantics can defeat the best-effort
 *       {@code Files.deleteIfExists} (RESEARCH Pitfall #7).</li>
 * </ol>
 *
 * <p>A third method drives the same failure through the controller via MockMvc to prove
 * the {@code try { execute(); } finally { unlock(); }} wrapper from Plan 76-01 releases
 * the {@link ImportLockService} mutex on the auto-backup failure path
 * (cross-ring T-76-06 mode E).
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@Tag("integration")
class AutoBackupBeforeImportFailureIT {

    @Autowired
    BackupImportService backupImportService;

    @MockitoSpyBean
    BackupArchiveService backupArchiveService;

    @Autowired
    TestDataService testDataService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataImportAuditRepository dataImportAuditRepository;

    @Autowired
    ImportLockService importLockService;

    @Autowired
    MockMvc mockMvc;

    @Value("${app.backup.import-backups-dir}")
    String importBackupsDirRaw;

    Path importBackupsDir;

    @BeforeAll
    void seedFixture() throws IOException {
        testDataService.seed();
        importBackupsDir = Paths.get(importBackupsDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(importBackupsDir);
    }

    @BeforeEach
    void resetSpy() {
        // Each test re-installs its own stubbing; Mockito.reset() ensures stubs from a
        // prior test do not bleed into the export setup of the next.
        Mockito.reset(backupArchiveService);
        // Defensive: the lock may have been left held by a previous test that did not
        // exercise the controller's finally block (Test 1 calls the service directly).
        if (importLockService.isLocked()) {
            importLockService.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Test 1 — primary failure-path assertion battery (D-17 / D-18)
    // -------------------------------------------------------------------------

    @Test
    void givenAutoExportTargetPreExisting_whenExecute_thenAutoBackupBeforeImportExceptionThrownAndNoWipeOccurred()
            throws Exception {
        // given — pre-export a real ZIP via the spy delegating to the real impl (no stub installed yet),
        // then stage it.
        UUID stagingId = exportAndStage("auto-backup-failure-export-1.zip");

        // Capture row counts BEFORE execute to prove no wipe occurred.
        long preSeasons = countRows("seasons");
        long preTeams = countRows("teams");
        long preDrivers = countRows("drivers");
        long preRaceResults = countRows("race_results");
        long preAuditCount = dataImportAuditRepository.count();

        // Install the failure stub — every writeZip call from this point on throws IOException.
        // This includes the Step 0.5 call inside backupImportService.execute(...).
        Mockito.doThrow(new IOException("simulated auto-export failure"))
                .when(backupArchiveService).writeZip(Mockito.any(OutputStream.class), Mockito.any(Instant.class));

        // when / then
        assertThatThrownBy(() -> backupImportService.execute(stagingId))
                .as("execute() must throw AutoBackupBeforeImportException when Step 0.5 writeZip fails")
                .isInstanceOf(AutoBackupBeforeImportException.class);

        // then (a) — no DB mutation occurred: row counts match the pre-execute snapshot.
        assertThat(countRows("seasons")).as("seasons row count unchanged").isEqualTo(preSeasons);
        assertThat(countRows("teams")).as("teams row count unchanged").isEqualTo(preTeams);
        assertThat(countRows("drivers")).as("drivers row count unchanged").isEqualTo(preDrivers);
        assertThat(countRows("race_results")).as("race_results row count unchanged").isEqualTo(preRaceResults);

        // then (b) — audit count increased by exactly 1, and the new row has success=false
        // with both count maps empty {} (D-18: no DB mutation occurred).
        long postAuditCount = dataImportAuditRepository.count();
        assertThat(postAuditCount)
                .as("REQUIRES_NEW audit row must persist via Plan 75-02 propagation")
                .isEqualTo(preAuditCount + 1L);

        Long successFalseCountWithEmptyMaps = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_import_audit "
                        + "WHERE success = FALSE AND table_counts_wiped = '{}' AND table_counts_restored = '{}'",
                Long.class);
        assertThat(successFalseCountWithEmptyMaps)
                .as("at least one data_import_audit row must have success=false + empty count maps (D-18)")
                .isNotNull()
                .isGreaterThanOrEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // Test 2 — partial-ZIP cleanup (D-19), POSIX only
    // -------------------------------------------------------------------------

    @Test
    void givenAutoExportFails_whenCleanupRuns_thenPartialZipIsRemovedOrToleratedPerOs() throws Exception {
        // given — pre-export + stage with the spy delegating to the real impl.
        UUID stagingId = exportAndStage("auto-backup-failure-export-2.zip");

        // Capture pre-existing <ts> directories so we can pinpoint the one created by this run.
        List<Path> preTsDirs = listImportBackupsDirs();

        // Trap: after the OutputStream is opened (CREATE_NEW succeeded, ZIP is now an empty
        // file on disk), the writeZip call throws IOException → the catch block runs
        // tryDeletePartialAutoBackup(autoBackupZip) (D-19) which calls Files.deleteIfExists.
        Mockito.doAnswer(this::throwAfterStreamOpen)
                .when(backupArchiveService).writeZip(Mockito.any(OutputStream.class), Mockito.any(Instant.class));

        // when
        assertThatThrownBy(() -> backupImportService.execute(stagingId))
                .isInstanceOf(AutoBackupBeforeImportException.class);

        // then — find the <ts> directory created by this run.
        List<Path> postTsDirs = listImportBackupsDirs();
        List<Path> newDirs = postTsDirs.stream().filter(p -> !preTsDirs.contains(p)).toList();
        // The <ts> directory may or may not have been left behind depending on the order of
        // operations in tryDeletePartialAutoBackup — but the ZIP file itself MUST be gone.
        if (!newDirs.isEmpty()) {
            Path autoBackupZip = newDirs.get(0).resolve("auto-backup-before-import.zip");
            if (isWindows()) {
                // RESEARCH Pitfall #7 — Windows file-locking may prevent Files.deleteIfExists;
                // the D-19 contract is "best-effort, never throws", NOT "always deletes".
                Assumptions.assumeFalse(true, "Windows file-locking — skipping cleanup assertion");
            } else {
                assertThat(Files.notExists(autoBackupZip))
                        .as("partial auto-backup ZIP must be cleaned up on POSIX (D-19)")
                        .isTrue();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Test 3 — controller-level: lock released on auto-backup failure (cross-ring T-76-06 mode E)
    // -------------------------------------------------------------------------

    @Test
    void givenAutoExportFails_whenControllerExecutes_thenLockIsReleased() throws Exception {
        // given — pre-export + stage.
        UUID stagingId = exportAndStage("auto-backup-failure-export-3.zip");

        // Install the failure stub.
        Mockito.doThrow(new IOException("simulated auto-export failure (controller path)"))
                .when(backupArchiveService).writeZip(Mockito.any(OutputStream.class), Mockito.any(Instant.class));

        // pre-condition: lock is released before the POST.
        assertThat(importLockService.isLocked())
                .as("lock must start released for this test")
                .isFalse();

        // when — drive POST through the controller so the try { execute(); } finally { unlock(); }
        // wrapper from Plan 76-01 is exercised on the AutoBackup-failure path. Form field
        // names match BackupImportConfirmForm (acknowledged checkbox, not "confirmation").
        mockMvc.perform(post("/admin/backup/import-execute")
                .param("stagingId", stagingId.toString())
                .param("acknowledged", "true"));

        // then — finally { importLockService.unlock(); } must have run regardless of the
        // exception type thrown inside the try block.
        assertThat(importLockService.isLocked())
                .as("lock must be released after AutoBackupBeforeImportException catch + finally")
                .isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Lets the OutputStream remain opened by Files.newOutputStream(..., CREATE_NEW) before
     * the exception is thrown — so the empty ZIP file exists on disk at the moment the
     * catch block runs tryDeletePartialAutoBackup (D-19).
     */
    private Object throwAfterStreamOpen(InvocationOnMock invocation) throws IOException {
        OutputStream out = invocation.getArgument(0);
        try {
            out.flush();
        } catch (IOException ignored) {
            // some stream impls may throw on flush after close — irrelevant for this trap
        }
        throw new IOException("simulated auto-export failure (mid-stream)");
    }

    private UUID exportAndStage(String filename) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        byte[] zipBytes = baos.toByteArray();
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/zip", zipBytes);
        return backupImportService.stage(file).stagingId();
    }

    private long countRows(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }

    private List<Path> listImportBackupsDirs() throws IOException {
        if (!Files.exists(importBackupsDir)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(importBackupsDir)) {
            return children.filter(Files::isDirectory).sorted().toList();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
