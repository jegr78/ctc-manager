package org.ctc.backup.it;

import org.ctc.backup.lock.ImportLockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Failsafe IT proving SECU-06 banner rendering by
 * {@link org.ctc.backup.lock.ImportLockBannerAdvice}.
 *
 * <p>The banner advice is a {@code @ControllerAdvice} that exposes
 * {@code ${importInProgress} = importLockService.isLocked()} on every controller
 * invocation. The advice only reads the lock state — it does not care which thread
 * holds it — so this IT acquires the lock directly on the test thread and issues
 * synchronous MockMvc requests against the locked state. No executor, no latches,
 * no timeouts: deterministic at any load level.
 *
 * <p>The orthogonal "real import in flight" scenario (audit row, rollback, AFTER_COMMIT
 * listener) is covered by {@code BackupImportRollbackIT} and {@code BackupImportServiceIT}.
 *
 * <p>Three scenarios:
 * <ol>
 *   <li>Admin GET {@code /admin/seasons} while locked → response body contains banner wording,
 *       {@code class="alert alert-warning"}, and {@code role="status"}.</li>
 *   <li>Site root GET {@code /} while locked → response body does NOT contain banner wording
 *       (site templates do not reference {@code ${importInProgress}}).</li>
 *   <li>Admin GET when lock is NOT held → banner wording absent.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Tag("integration")
class ImportLockBannerAdviceIT {

    private static final String BANNER_TEXT =
            "Backup import in progress — write access is temporarily locked.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ImportLockService importLockService;

    @AfterEach
    void releaseLockIfHeld() {
        importLockService.unlock();
    }

    @Test
    void givenLockHeld_whenGetAdminSeasons_thenResponseBodyContainsBannerWording() throws Exception {
        assertThat(importLockService.tryLock())
                .as("test thread must be able to acquire the import lock")
                .isTrue();

        String body = mockMvc.perform(get("/admin/seasons"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .as("admin GET must contain the locked-state banner wording while lock is held")
                .contains(BANNER_TEXT);
        assertThat(body)
                .as("banner div must carry class=\"alert alert-warning\"")
                .contains("class=\"alert alert-warning\"");
        assertThat(body)
                .as("banner div must carry role=\"status\"")
                .contains("role=\"status\"");
    }

    @Test
    void givenLockHeld_whenGetSiteIndex_thenBannerWordingAbsent() throws Exception {
        assertThat(importLockService.tryLock())
                .as("test thread must be able to acquire the import lock")
                .isTrue();

        MvcResult siteResult = mockMvc.perform(get("/")).andReturn();

        String siteBody = siteResult.getResponse().getContentAsString();
        assertThat(siteBody)
                .as("GET / response must not contain banner wording — site templates are banner-free")
                .doesNotContain(BANNER_TEXT);
    }

    @Test
    void givenLockNotHeld_whenGetAdminSeasons_thenBannerWordingAbsent() throws Exception {
        assertThat(importLockService.isLocked()).as("lock must not be held").isFalse();

        String body = mockMvc.perform(get("/admin/seasons"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .as("banner wording must be absent when the import lock is not held")
                .doesNotContain(BANNER_TEXT);
    }
}
