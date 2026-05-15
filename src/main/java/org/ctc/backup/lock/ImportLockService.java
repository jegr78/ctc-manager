package org.ctc.backup.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Singleton mutex for the import-execute path — exactly one thread may hold this lock at a time.
 *
 * <p>{@link #tryLock()} is non-blocking (zero-timeout); a second concurrent call from a
 * different thread returns {@code false} immediately — the caller is expected to reject the
 * request (HTTP 409) rather than queue. {@link #unlock()} is idempotent: calling it from a
 * thread that does not hold the lock is a silent no-op, guarded by
 * {@link ReentrantLock#isHeldByCurrentThread()}, so a {@code finally { unlock(); }} after a
 * failed {@code tryLock()} cannot cause {@link IllegalMonitorStateException}.
 *
 * <p>The lock is released in {@code finally} AFTER {@code BackupImportService.execute()} returns.
 * Spring's default {@code @TransactionalEventListener(phase = AFTER_COMMIT)} runs synchronously
 * on the same thread; by the time {@code execute()} returns, the uploads-move listener has
 * already completed. Do NOT add {@code @Async} to {@code BackupImportPostCommitListener} —
 * that would move the listener to a different thread, and the {@code finally { unlock(); }}
 * in the controller would release the lock BEFORE the listener finishes (race condition / pitfall).
 *
 * <p>This is an in-memory, single-JVM lock. Multi-instance deployment is not in scope.
 */
@Slf4j
@Service
@Scope("singleton")  // explicit — redundant but documents singleton intent for concurrent-access safety
public class ImportLockService {

    private final ReentrantLock lock = new ReentrantLock();  // fairness=false (non-blocking tryLock)

    /**
     * Attempts to acquire the import lock without blocking.
     *
     * @return {@code true} if the lock was acquired; {@code false} if another thread already holds it
     */
    public boolean tryLock() {
        boolean acquired = lock.tryLock();  // non-blocking, zero-timeout
        if (acquired) {
            log.info("Import lock acquired by thread={}", Thread.currentThread().getName());
        }
        return acquired;
    }

    /**
     * Releases the import lock if held by the current thread; otherwise a no-op (idempotent).
     *
     * <p>Guarded by {@link ReentrantLock#isHeldByCurrentThread()} so a stray
     * {@code finally { unlock(); }} after a failed {@link #tryLock()} is a silent no-op.
     */
    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.info("Import lock released by thread={}", Thread.currentThread().getName());
        }
    }

    /**
     * Returns whether the lock is currently held by any thread.
     *
     * <p>Read-only; does NOT require the current thread to hold the lock.
     * Used by {@code ImportLockBannerAdvice} and {@code ImportLockedWriteRejector}.
     *
     * @return {@code true} if any thread currently holds the lock
     */
    public boolean isLocked() {
        return lock.isLocked();
    }
}
