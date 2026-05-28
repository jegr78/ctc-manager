package org.ctc.backup.it;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Failsafe IT proving SECU-06 POST-rejection by
 * {@link org.ctc.backup.lock.ImportLockedWriteRejector}.
 *
 * <p>The interceptor reads {@link ImportLockService#isLocked()} and the import-execute
 * controller calls {@link ImportLockService#tryLock()} — both APIs are thread-agnostic
 * for the read and ownership-aware for tryLock. This IT acquires the lock on a
 * background thread, asserts the rejection behaviour from the test thread (MockMvc
 * dispatch runs synchronously on the test thread, so the controller's tryLock sees a
 * different owner and rejects with 409), then releases.
 *
 * <p>No latches, no executors that race the slow restore flow, no BlockingRestoreFailureInjector
 * — the test no longer depends on the import-execute path reaching {@code race_results}
 * row 50 within an arbitrary deadline. Sub-second deterministic at any load level.
 *
 * <p>Four scenarios:
 * <ol>
 *   <li>Non-whitelisted POST ({@code /admin/teams/save}) during lock → HTTP 503 + banner wording.</li>
 *   <li>Whitelisted POST ({@code /admin/backup/import-execute}) during lock → HTTP 409
 *       (interceptor allows the whitelisted URL; controller's tryLock rejects).</li>
 *   <li>GET ({@code /admin/seasons}) during lock → HTTP 200 (interceptor never blocks GETs).</li>
 *   <li>Non-whitelisted POST when lock is NOT held → normal processing (not HTTP 503).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Tag("integration")
class ImportLockedPostRejectorIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ImportLockService importLockService;

    private CompletableFuture<Void> releaseSignal;
    private CompletableFuture<Void> holderThread;

    @AfterEach
    void releaseLockIfHeld() throws Exception {
        if (releaseSignal != null) {
            releaseSignal.complete(null);
            holderThread.get(5, TimeUnit.SECONDS);
            releaseSignal = null;
            holderThread = null;
        }
        importLockService.unlock();
    }

    /**
     * Acquire the import lock on a dedicated background thread and block until the test
     * thread completes its assertions. Returns once the lock is provably held — no
     * arbitrary deadline on the assert path.
     */
    private void holdLockFromBackgroundThread() throws Exception {
        releaseSignal = new CompletableFuture<>();
        CompletableFuture<Void> lockAcquired = new CompletableFuture<>();
        holderThread = CompletableFuture.runAsync(() -> {
            if (!importLockService.tryLock()) {
                lockAcquired.completeExceptionally(new IllegalStateException(
                        "background thread could not acquire the import lock"));
                return;
            }
            try {
                lockAcquired.complete(null);
                releaseSignal.join();
            } finally {
                importLockService.unlock();
            }
        });
        lockAcquired.get(5, TimeUnit.SECONDS);
        assertThat(importLockService.isLocked()).isTrue();
    }

    @Test
    void givenLockHeld_whenPostToAdminTeamsSave_thenHttp503AndBannerWordingInBody() throws Exception {
        holdLockFromBackgroundThread();

        MvcResult result = mockMvc.perform(
                        post("/admin/teams/save")
                                .param("name", "BlockedTeam")
                                .param("shortName", "BLK"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("ImportLockedWriteRejector must return HTTP 503")
                .isEqualTo(503);
        assertThat(result.getResponse().getContentType())
                .as("Content-Type must be text/html")
                .startsWith("text/html");
        assertThat(result.getResponse().getContentAsString())
                .as("503 body must contain the locked-state wording (verbatim match with banner)")
                .contains("Backup import in progress — write access is temporarily locked.");
    }

    @Test
    void givenLockHeld_whenPostToWhitelistedImportExecuteFromSecondClient_thenInterceptorAllowsButControllerReturns409()
            throws Exception {
        holdLockFromBackgroundThread();

        MvcResult whitelistedResult = mockMvc.perform(
                        post("/admin/backup/import-execute")
                                .param("stagingId", UUID.randomUUID().toString())
                                .param("acknowledged", "true"))
                .andReturn();

        assertThat(whitelistedResult.getResponse().getStatus())
                .as("whitelisted import-execute must return 409 (lock-service rejection), NOT 503 (interceptor rejection)")
                .isEqualTo(409);
    }

    @Test
    void givenLockHeld_whenGetAdminSeasons_thenPassesThrough() throws Exception {
        holdLockFromBackgroundThread();

        mockMvc.perform(get("/admin/seasons"))
                .andExpect(status().isOk());
    }

    @Test
    void givenLockNotHeld_whenPostToAdminTeamsSave_thenProceedsNormally() throws Exception {
        assertThat(importLockService.isLocked()).as("lock must not be held").isFalse();

        MvcResult result = mockMvc.perform(
                        post("/admin/teams/save")
                                .param("name", "NormalTeam")
                                .param("shortName", "NRM"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("without a lock, POST must not be rejected with 503")
                .isNotEqualTo(503);
        assertThat(result.getResponse().getStatus())
                .as("response should be a redirect (3xx) or form re-render (200), not 503")
                .isLessThan(504);
    }
}
