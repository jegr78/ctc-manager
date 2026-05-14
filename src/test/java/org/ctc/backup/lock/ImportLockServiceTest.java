package org.ctc.backup.lock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 76 / Plan 01 — Surefire unit tests for {@link ImportLockService}.
 *
 * <p>No Spring context — direct instantiation of the service. Tests cover all six
 * semantic behaviors defined in CONTEXT D-01 / D-02 / D-03.
 */
class ImportLockServiceTest {

    @Test
    void givenFreshService_whenIsLocked_thenReturnsFalse() {
        // given
        ImportLockService service = new ImportLockService();

        // when / then
        assertThat(service.isLocked()).isFalse();
    }

    @Test
    void givenFreshService_whenTryLockCalled_thenReturnsTrueAndIsLockedFlips() {
        // given
        ImportLockService service = new ImportLockService();

        // when
        boolean acquired = service.tryLock();

        // then
        assertThat(acquired).isTrue();
        assertThat(service.isLocked()).isTrue();

        // cleanup
        service.unlock();
    }

    @Test
    void givenLockHeld_whenSecondTryLockOnSameThread_thenReturnsTrue() {
        // given — ReentrantLock allows re-entrance on the same thread (D-01)
        ImportLockService service = new ImportLockService();
        assertThat(service.tryLock()).isTrue();

        // when — same thread tries to acquire again
        boolean reentrant = service.tryLock();

        // then — ReentrantLock is reentrant; second acquisition succeeds
        assertThat(reentrant).isTrue();

        // cleanup — must unlock once per tryLock call (hold count = 2)
        service.unlock();
        service.unlock();
    }

    @Test
    void givenLockHeld_whenUnlockCalled_thenIsLockedFlipsBackToFalse() {
        // given
        ImportLockService service = new ImportLockService();
        service.tryLock();
        assertThat(service.isLocked()).isTrue();

        // when
        service.unlock();

        // then
        assertThat(service.isLocked()).isFalse();
    }

    @Test
    void givenLockNotHeld_whenUnlockCalled_thenIdempotentNoOp() {
        // given — no lock acquired on this thread
        ImportLockService service = new ImportLockService();
        assertThat(service.isLocked()).isFalse();

        // when / then — must NOT throw IllegalMonitorStateException (D-01 idempotent guard)
        service.unlock();

        // state unchanged
        assertThat(service.isLocked()).isFalse();
    }

    @Test
    void givenLockHeldByOtherThread_whenIsLockedRead_thenReturnsTrueWithoutCurrentThreadCheck()
            throws Exception {
        // given — worker thread acquires the lock and holds it
        ImportLockService service = new ImportLockService();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // Latches coordinate: test thread waits for worker to hold lock, then signals release
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> future = executor.submit(() -> {
            service.tryLock();
            acquired.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            service.unlock();
        });

        // when — test thread reads isLocked() without holding the lock itself
        assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
        boolean lockedByOther = service.isLocked();  // D-01: isLocked() does NOT require current-thread holding

        // then
        assertThat(lockedByOther).isTrue();

        // cleanup
        release.countDown();
        future.get(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(service.isLocked()).isFalse();
    }
}
