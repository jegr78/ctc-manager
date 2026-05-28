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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2-thread Failsafe IT proving SECU-05: when one thread holds the import lock, a
 * concurrent {@code /admin/backup/import-execute} POST from another thread is rejected
 * with HTTP 409 + a flash message redirecting to {@code /admin/backup}.
 *
 * <p>{@link ImportLockService} guards mutual exclusion across import-execute calls. The
 * lock is a {@link java.util.concurrent.locks.ReentrantLock}; only the owning thread can
 * release it via {@link ImportLockService#unlock()} (idempotent, guarded by
 * {@code isHeldByCurrentThread()}). The contract under test is symmetric: any thread
 * other than the owner observes {@code tryLock() == false}, and the controller responds
 * with 409.
 *
 * <p>This IT acquires the lock on a dedicated background thread (no slow-restore
 * handshake, no latch deadlines), fires the rejecting POST from the test thread, then
 * releases. Audit-row write semantics for the happy-path import are covered in
 * {@code BackupImportExecuteIT}, {@code BackupImportRollbackIT}, and
 * {@code BackupImportPostCommitIT}.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Tag("integration")
class ImportConcurrentLockIT {

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
    void givenLockHeldByOtherThread_whenImportExecutePost_thenHttp409WithRedirectFlash() throws Exception {
        holdLockFromBackgroundThread();

        mockMvc.perform(post("/admin/backup/import-execute")
                        .param("stagingId", UUID.randomUUID().toString())
                        .param("acknowledged", "true"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Location", "/admin/backup"))
                .andExpect(flash().attribute("errorMessage",
                        "Another import is already running — please wait."));
    }
}
