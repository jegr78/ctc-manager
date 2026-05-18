package org.ctc.backup.it;

import org.ctc.backup.lock.ImportLockService;
import org.springframework.boot.test.context.TestComponent;

/**
 * D-03 reset helper — idempotently releases the {@link ImportLockService} mutex after
 * each backup IT method so that the {@code ReentrantLock} state does not leak between
 * test methods that do not rely on the {@code CountDownLatch} handshake.
 *
 * <p>Call {@link #reset()} from an {@code @AfterEach} method in each backup IT.
 * JUnit 5 lifecycle annotations on {@code @TestComponent} beans are not auto-discovered;
 * the {@code @AfterEach} must live in the test class itself.
 */
@TestComponent
class ImportLockServiceResetHelper {

    private final ImportLockService importLockService;

    ImportLockServiceResetHelper(ImportLockService importLockService) {
        this.importLockService = importLockService;
    }

    /**
     * Releases the import lock if it is currently held by the current thread.
     * Delegates to {@link ImportLockService#unlock()} which is idempotent:
     * calling this when the lock is not held is a safe no-op.
     */
    public void reset() {
        importLockService.unlock();
    }
}
