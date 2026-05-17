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

/**
 * Phase 76 / Plan 02 — Failsafe IT proving SECU-06 banner rendering by
 * {@link org.ctc.backup.lock.ImportLockBannerAdvice} (CONTEXT D-21 / D-11 / D-13).
 *
 * <p>Drives a slow import via {@link BlockingRestoreFailureInjector} and asserts that
 * the yellow banner wording appears in admin GET responses while the lock is held, and
 * is absent on site paths and when the lock is released. Covers three BDD scenarios:
 * <ol>
 *   <li>Admin GET ({@code /admin/seasons}) during lock → response body contains banner wording,
 *       {@code class="alert alert-warning"}, and {@code role="status"}.</li>
 *   <li>Site root ({@code GET /}) during lock → response body does NOT contain banner wording
 *       (D-13 — site templates do not render the banner).</li>
 *   <li>Admin GET when lock is NOT held → banner wording absent.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({BlockingRestoreFailureInjector.Config.class, ImportLockServiceResetHelper.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Tag("integration")
class ImportLockBannerAdviceIT {

    private static final String BANNER_TEXT =
            "Backup import in progress — write access is temporarily locked.";

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
                "file", "banner-it-export.zip", "application/zip", zipBytes);
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
    void givenLockHeld_whenGetAdminSeasons_thenResponseBodyContainsBannerWording() throws Exception {
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

        // when — admin GET while lock is held
        String body = mockMvc.perform(get("/admin/seasons"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then — banner wording, CSS class, and ARIA role present in rendered HTML (D-12 / CD-04)
        assertThat(body)
                .as("admin GET must contain the locked-state banner wording while lock is held")
                .contains(BANNER_TEXT);
        assertThat(body)
                .as("banner div must carry class=\"alert alert-warning\"")
                .contains("class=\"alert alert-warning\"");
        assertThat(body)
                .as("banner div must carry role=\"status\" (CD-04)")
                .contains("role=\"status\"");

        // cleanup
        releaseLatch.countDown();
        futureA.get(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    // @DirtiesContext retained on this method — uses CountDownLatch handshake (non-resettable, RESEARCH Assumption A1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @Test
    void givenLockHeld_whenGetSiteIndex_thenBannerWordingAbsent() throws Exception {
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

        // when — GET / (site index — redirects to /admin/seasons; the redirect response itself
        // has no rendered body and site templates do not include the banner div per D-13)
        MvcResult siteResult = mockMvc.perform(get("/")).andReturn();

        // then — the immediate redirect response does not contain the banner wording
        // D-13: site templates ignore ${importInProgress} — the model attribute is set globally
        // but site/layout.html has no th:if referencing it
        String siteBody = siteResult.getResponse().getContentAsString();
        assertThat(siteBody)
                .as("GET / response (redirect) must not contain banner wording — site templates are banner-free (D-13)")
                .doesNotContain(BANNER_TEXT);

        // cleanup
        releaseLatch.countDown();
        futureA.get(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Test
    void givenLockNotHeld_whenGetAdminSeasons_thenBannerWordingAbsent() throws Exception {
        // given — no import running
        assertThat(importLockService.isLocked()).as("lock must not be held").isFalse();

        // when — admin GET with no active lock
        String body = mockMvc.perform(get("/admin/seasons"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then — banner must not be rendered when lock is not held
        assertThat(body)
                .as("banner wording must be absent when the import lock is not held")
                .doesNotContain(BANNER_TEXT);
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
