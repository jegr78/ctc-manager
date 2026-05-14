package org.ctc.backup.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Phase 76 / Plan 01 — singleton mutex for the import-execute path (SECU-05 / CONTEXT D-01).
 *
 * <p><strong>D-01 contract:</strong> exactly one thread may hold this lock at a time.
 * {@link #tryLock()} is non-blocking (zero-timeout); a second concurrent call from a
 * different thread returns {@code false} immediately — the caller is expected to reject the
 * request (HTTP 409) rather than queue. {@link #unlock()} is idempotent: calling it from a
 * thread that does not hold the lock is a silent no-op, guarded by
 * {@link ReentrantLock#isHeldByCurrentThread()}, so a {@code finally { unlock(); }} after a
 * failed {@code tryLock()} cannot cause {@link IllegalMonitorStateException}.
 *
 * <p><strong>D-06 contract:</strong> the lock is released in {@code finally} AFTER
 * {@code BackupImportService.execute()} returns. Spring's default
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} runs synchronously on the same
 * thread; by the time {@code execute()} returns, Plan 75-07's uploads-move listener has
 * already completed. Do NOT add {@code @Async} to {@code BackupImportPostCommitListener} —
 * that would move the listener to a different thread, and the {@code finally { unlock(); }}
 * in the controller would release the lock BEFORE the listener finishes, breaking this
 * contract (RESEARCH Pitfall 2).
 *
 * <p><strong>D-03 note:</strong> this is an in-memory, single-JVM lock.
 * Multi-instance deployment is not in v1.10 scope; if needed, the
 * {@code ImportLockService} can be replaced with a Hazelcast/Redis-backed variant.
 */
@Slf4j
@Service
@Scope("singleton")  // explicit per CONTEXT D-01 / SECU-05 wording — redundant but documents intent
public class ImportLockService {

    private final ReentrantLock lock = new ReentrantLock();  // fairness=false per D-01

    /**
     * Attempts to acquire the import lock without blocking.
     *
     * @return {@code true} if the lock was acquired; {@code false} if another thread already holds it
     */
    public boolean tryLock() {
        boolean acquired = lock.tryLock();  // non-blocking, zero-timeout (D-02)
        if (acquired) {
            log.info("Import lock acquired by thread={}", Thread.currentThread().getName());
        }
        return acquired;
    }

    /**
     * Releases the import lock if held by the current thread; otherwise a no-op (D-01 idempotent).
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
