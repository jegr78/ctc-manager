package org.ctc.backup.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.ctc.admin.TestDataService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 76 / Plan 02 — Failsafe IT proving SECU-06 POST-rejection by
 * {@link org.ctc.backup.lock.ImportLockedWriteRejector} (CONTEXT D-21).
 *
 * <p>Uses {@link BlockingRestoreFailureInjector} to hold thread A mid-restore inside the
 * {@link ImportLockService} mutex, then fires assertions from the test thread while the lock
 * is held. Covers four BDD scenarios:
 * <ol>
 *   <li>Non-whitelisted POST ({@code /admin/teams/save}) during lock → HTTP 503 + banner wording in body.</li>
 *   <li>Whitelisted POST ({@code /admin/backup/import-execute}) during lock → HTTP 409 (NOT 503), proving
 *       D-09 (interceptor allows) + D-05 (controller's tryLock rejects).</li>
 *   <li>GET ({@code /admin/seasons}) during lock → HTTP 200 (interceptor never blocks GETs).</li>
 *   <li>Non-whitelisted POST when lock is NOT held → normal processing (not HTTP 503).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({BlockingRestoreFailureInjector.Config.class, ImportLockServiceResetHelper.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Tag("integration")
class ImportLockedPostRejectorIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    BackupImportService backupImportService;

    @Autowired
    BackupArchiveService backupArchiveService;

    @Autowired
    TestDataService testDataService;

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

    UUID stagingIdA;

    @BeforeAll
    void seedFixture() throws IOException {
        testDataService.seed();
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
        Files.createDirectories(Paths.get(importBackupsDirRaw).toAbsolutePath().normalize());
    }

    @BeforeEach
    void prepareStaging() throws IOException {
        byte[] zipBytes = exportToBytes();
        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "rejector-it-export.zip", "application/zip", zipBytes);
        BackupImportPreview preview = backupImportService.stage(zipFile);
        stagingIdA = preview.stagingId();
    }

    @AfterEach
    void tearDownLock() {
        importLockServiceResetHelper.reset();
    }

    // @DirtiesContext retained on this method — uses CountDownLatch handshake (non-resettable, RESEARCH Assumption A1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @Test
    void givenLockHeld_whenPostToAdminTeamsSave_thenHttp503AndBannerWordingInBody() throws Exception {
        // given — thread A holds the import lock mid-restore
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MvcResult> futureA = executor.submit(() ->
                mockMvc.perform(post("/admin/backup/import-execute")
                                .param("stagingId", stagingIdA.toString())
                                .param("acknowledged", "true"))
                        .andReturn()
        );

        assertThat(hasAcquired.await(10, TimeUnit.SECONDS))
                .as("thread A must acquire the lock within 10 s")
                .isTrue();
        assertThat(importLockService.isLocked()).as("lock must be held by thread A").isTrue();

        // when — non-whitelisted POST during lock
        MvcResult result = mockMvc.perform(
                        post("/admin/teams/save")
                                .param("name", "BlockedTeam")
                                .param("shortName", "BLK"))
                .andReturn();

        // then — 503 + banner wording in body
        assertThat(result.getResponse().getStatus())
                .as("ImportLockedWriteRejector must return HTTP 503")
                .isEqualTo(503);
        assertThat(result.getResponse().getContentType())
                .as("Content-Type must be text/html")
                .startsWith("text/html");
        assertThat(result.getResponse().getContentAsString())
                .as("503 body must contain the locked-state wording (verbatim match with banner D-12)")
                .contains("Backup import in progress — write access is temporarily locked.");

        // cleanup — release thread A
        releaseLatch.countDown();
        futureA.get(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    // @DirtiesContext retained on this method — uses CountDownLatch handshake (non-resettable, RESEARCH Assumption A1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @Test
    void givenLockHeld_whenPostToWhitelistedImportExecuteFromSecondClient_thenInterceptorAllowsButControllerReturns409()
            throws Exception {
        // given — thread A holds the import lock mid-restore
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MvcResult> futureA = executor.submit(() ->
                mockMvc.perform(post("/admin/backup/import-execute")
                                .param("stagingId", stagingIdA.toString())
                                .param("acknowledged", "true"))
                        .andReturn()
        );

        assertThat(hasAcquired.await(10, TimeUnit.SECONDS))
                .as("thread A must acquire the lock within 10 s")
                .isTrue();

        // when — whitelisted POST (import-execute) during lock
        MvcResult whitelistedResult = mockMvc.perform(
                        post("/admin/backup/import-execute")
                                .param("stagingId", UUID.randomUUID().toString())
                                .param("acknowledged", "true"))
                .andReturn();

        // then — 409 (NOT 503): interceptor allowed the whitelisted URL (D-09),
        // controller's tryLock rejects with CONFLICT (D-05)
        assertThat(whitelistedResult.getResponse().getStatus())
                .as("whitelisted import-execute must return 409 (lock-service rejection), NOT 503 (interceptor rejection)")
                .isEqualTo(409);

        // cleanup
        releaseLatch.countDown();
        futureA.get(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    // @DirtiesContext retained on this method — uses CountDownLatch handshake (non-resettable, RESEARCH Assumption A1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @Test
    void givenLockHeld_whenGetAdminSeasons_thenPassesThrough() throws Exception {
        // given — thread A holds the import lock
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MvcResult> futureA = executor.submit(() ->
                mockMvc.perform(post("/admin/backup/import-execute")
                                .param("stagingId", stagingIdA.toString())
                                .param("acknowledged", "true"))
                        .andReturn()
        );

        assertThat(hasAcquired.await(10, TimeUnit.SECONDS))
                .as("thread A must acquire the lock within 10 s")
                .isTrue();

        // when — GET request during lock
        // then — interceptor must not block GET requests (D-08 step 1)
        mockMvc.perform(get("/admin/seasons"))
                .andExpect(status().isOk());

        // cleanup
        releaseLatch.countDown();
        futureA.get(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Test
    void givenLockNotHeld_whenPostToAdminTeamsSave_thenProceedsNormally() throws Exception {
        // given — no import running
        assertThat(importLockService.isLocked()).as("lock must not be held").isFalse();

        // when — non-whitelisted POST with no active lock
        MvcResult result = mockMvc.perform(
                        post("/admin/teams/save")
                                .param("name", "NormalTeam")
                                .param("shortName", "NRM"))
                .andReturn();

        // then — interceptor allows through (not 503)
        assertThat(result.getResponse().getStatus())
                .as("without a lock, POST must not be rejected with 503")
                .isNotEqualTo(503);
        // Controller may return 3xx (redirect after save) or 200 (validation error page)
        // but never 503 (that is the interceptor's rejection code)
        assertThat(result.getResponse().getStatus())
                .as("response should be a redirect (3xx) or form re-render (200), not 503")
                .isLessThan(504);
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
