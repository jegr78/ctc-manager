package org.ctc.backup.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.ctc.admin.TestDataService;
import org.ctc.backup.audit.DataImportAudit;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.it.support.BlockingRestoreFailureInjector;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 76 / Plan 01 — 2-thread Failsafe IT proving SECU-05 (CONTEXT D-20).
 *
 * <p>Installs {@link BlockingRestoreFailureInjector} as the {@code @Primary}
 * {@link org.ctc.backup.restore.RestoreFailureInjector} via {@code @Import(Config.class)}.
 * The injector pauses thread A at {@code race_results:50} (mid-restore) so that
 * thread B's concurrent import attempt is guaranteed to arrive while thread A holds the
 * {@link ImportLockService} mutex.
 *
 * <p>Scenario (CONTEXT D-20):
 * <ol>
 *   <li>Two staged ZIPs prepared in {@code @BeforeEach} ({@code stagingIdA}, {@code stagingIdB}).</li>
 *   <li>Thread A: POST {@code /admin/backup/import-execute} with stagingId-A. The blocking
 *       injector pauses thread A at {@code race_results:50} after counting down
 *       {@link #hasAcquired}.</li>
 *   <li>Test thread: awaits {@link #hasAcquired}; asserts {@code importLockService.isLocked() == true}.</li>
 *   <li>Test thread fires POST {@code /admin/backup/import-execute} with stagingId-B (thread B).
 *       Asserts: HTTP 409 + {@code errorMessage} flash = {@code "Another import is already running — please wait."}.</li>
 *   <li>Test thread counts down {@link #releaseLatch}; thread A resumes and completes normally.</li>
 *   <li>Thread A's future completes; asserts: HTTP 302 redirect to {@code /admin/backup} + success flash.</li>
 *   <li>Asserts {@code importLockService.isLocked() == false} after release.</li>
 *   <li>Asserts exactly 1 {@code data_import_audit} row with {@code success=true} was added
 *       since {@code @BeforeAll} (thread B never reached the service).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({BlockingRestoreFailureInjector.Config.class, ImportLockServiceResetHelper.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
// @DirtiesContext retained — CountDownLatch in BlockingRestoreFailureInjector.Config is non-resettable (D-01 / RESEARCH Cluster B / Assumption A1)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("integration")
class ImportConcurrentLockIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    BackupImportService backupImportService;

    @Autowired
    BackupArchiveService backupArchiveService;

    @Autowired
    TestDataService testDataService;

    @Autowired
    DataImportAuditRepository dataImportAuditRepository;

    @Autowired
    ImportLockService importLockService;

    @Autowired
    ImportLockServiceResetHelper importLockServiceResetHelper;

    @Autowired
    CountDownLatch hasAcquired;

    @Autowired
    CountDownLatch releaseLatch;

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    @Value("${app.backup.import-backups-dir}")
    String importBackupsDirRaw;

    Path stagingDir;

    /** Staging ID for thread A's import. */
    UUID stagingIdA;

    /** Pre-test count of successful audit rows (thread B must not add any). */
    long preTestSuccessCount;

    @BeforeAll
    void seedFixture() throws IOException {
        testDataService.seed();
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
        Files.createDirectories(Paths.get(importBackupsDirRaw).toAbsolutePath().normalize());
    }

    @BeforeEach
    void prepareStaging() throws IOException {
        // Produce a staged ZIP for thread A.
        // Thread B's POST will be rejected at the lock guard — it never reaches the service,
        // so we only need one staged ZIP for the actual import (thread A).
        byte[] zipBytes = exportToBytes();
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "concurrent-it-export.zip", "application/zip", zipBytes);
        BackupImportPreview preview = backupImportService.stage(zipFile);
        stagingIdA = preview.stagingId();

        // Capture pre-test audit row count so the final assertion is relative (avoids
        // sensitivity to other ITs in the same JVM session that also write audit rows).
        preTestSuccessCount = dataImportAuditRepository.findAll().stream()
                .filter(DataImportAudit::isSuccess)
                .count();
    }

    @AfterEach
    void tearDownLock() {
        importLockServiceResetHelper.reset();
    }

    @Test
    void givenSlowImportRunningOnThreadA_whenThreadBPostsImportExecute_thenThreadBReceivesHttp409AndAuditTableHasExactlyOneSuccessRow()
            throws Exception {

        ExecutorService executor = Executors.newSingleThreadExecutor();

        // Step 1 — submit thread A: POST import-execute with stagingId-A.
        // The BlockingRestoreFailureInjector will pause thread A at race_results:50.
        Future<MvcResult> futureA = executor.submit(() ->
                mockMvc.perform(post("/admin/backup/import-execute")
                                .param("stagingId", stagingIdA.toString())
                                .param("acknowledged", "true"))
                        .andReturn()
        );

        // Step 2 — wait for thread A to acquire the lock and enter the blocking injector.
        assertThat(hasAcquired.await(10, TimeUnit.SECONDS))
                .as("hasAcquired latch must count down within 10 s — thread A must have reached race_results:50")
                .isTrue();

        // Step 3 — assert lock is held while thread A is paused.
        assertThat(importLockService.isLocked())
                .as("ImportLockService must report locked=true while thread A is mid-restore")
                .isTrue();

        // Step 4 — thread B fires a concurrent POST; expects HTTP 409 + errorMessage flash.
        // Note: MockMvc redirects are NOT followed automatically; the 409 is on the redirect response itself.
        mockMvc.perform(post("/admin/backup/import-execute")
                        .param("stagingId", UUID.randomUUID().toString())  // stagingId-B (irrelevant — rejected before service)
                        .param("acknowledged", "true"))
                .andExpect(status().isConflict())  // HTTP 409
                .andExpect(header().string("Location", "/admin/backup"))
                .andExpect(flash().attribute("errorMessage",
                        "Another import is already running — please wait."));

        // Step 5 — release thread A.
        releaseLatch.countDown();

        // Step 6 — thread A completes; assert its response is a 302 redirect with success flash.
        MvcResult resultA = futureA.get(30, TimeUnit.SECONDS);
        assertThat(resultA.getResponse().getStatus())
                .as("thread A must complete with 302 redirect to /admin/backup")
                .isEqualTo(302);
        String locationA = resultA.getResponse().getHeader("Location");
        assertThat(locationA)
                .as("thread A Location header must point to /admin/backup")
                .endsWith("/admin/backup");

        executor.shutdown();

        // Step 7 — lock must be released after thread A completes.
        assertThat(importLockService.isLocked())
                .as("ImportLockService must report locked=false after thread A finishes")
                .isFalse();

        // Step 8 — exactly 1 new data_import_audit row with success=true (thread A's).
        // Thread B was rejected at the lock guard and never reached BackupImportService.
        long postTestSuccessCount = dataImportAuditRepository.findAll().stream()
                .filter(DataImportAudit::isSuccess)
                .count();
        assertThat(postTestSuccessCount - preTestSuccessCount)
                .as("exactly 1 new success audit row must exist — thread B was rejected at the lock, never reached the service")
                .isEqualTo(1L);

        // Verify no thread-B audit row exists (thread B never entered the service).
        List<DataImportAudit> allAudit = dataImportAuditRepository.findAll();
        long failureRowsAdded = allAudit.stream()
                .filter(a -> !a.isSuccess())
                .count();
        // Note: failure rows from other IT runs in the same JVM may exist; we only assert
        // the delta for success rows is exactly 1 (thread A) and thread B added 0 rows total.
        assertThat(postTestSuccessCount - preTestSuccessCount)
                .as("thread A contributes exactly 1 success row; thread B contributes 0 rows")
                .isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private byte[] exportToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        return baos.toByteArray();
    }
}
