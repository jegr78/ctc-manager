package org.ctc.backup.lock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.exception.ImportWritersStillActiveException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

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
 * <p>Mutating admin requests register as writers through {@link #tryEnterWriter()}. Once the lock is
 * held no new writer is admitted, and the holder calls {@link #awaitWritersDrained()} so writers
 * admitted before the lock finish before the restore touches anything.
 *
 * <p>This is an in-memory, single-JVM lock. Multi-instance deployment is not in scope.
 */
@Slf4j
@Service
@Scope("singleton")  // explicit — redundant but documents singleton intent for concurrent-access safety
public final class ImportLockService {

    private final ReentrantLock lock = new ReentrantLock();  // fairness=false (non-blocking tryLock)
    private final Object writerMonitor = new Object();
    private final Duration writerDrainTimeout;
    private int activeWriters;

    public ImportLockService() {
        this(Duration.ofSeconds(30));
    }

    @Autowired
    public ImportLockService(@Value("${app.backup.writer-drain-timeout:PT30S}") Duration writerDrainTimeout) {
        if (writerDrainTimeout.isNegative() || writerDrainTimeout.isZero()) {
            throw new IllegalArgumentException("app.backup.writer-drain-timeout must be positive");
        }
        this.writerDrainTimeout = writerDrainTimeout;
    }

    /**
     * Attempts to acquire the import lock without blocking.
     *
     * @return {@code true} if the lock was acquired; {@code false} if another thread already holds it
     */
    public boolean tryLock() {
        boolean acquired;
        synchronized (writerMonitor) {
            acquired = lock.tryLock();  // non-blocking, zero-timeout; inside the monitor so no writer slips past
        }
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

    /**
     * Admits a mutating request unless the import lock is held.
     *
     * @return {@code false} when the request must be rejected; otherwise the caller must call
     *         {@link #exitWriter()} once the request completes
     */
    public boolean tryEnterWriter() {
        synchronized (writerMonitor) {
            if (lock.isLocked()) {
                return false;
            }
            activeWriters++;
            return true;
        }
    }

    public void exitWriter() {
        synchronized (writerMonitor) {
            activeWriters--;
            writerMonitor.notifyAll();
        }
    }

    /**
     * Waits until every writer admitted before the lock has completed.
     *
     * @throws ImportWritersStillActiveException when writers are still running after the drain timeout
     */
    public void awaitWritersDrained() throws ImportWritersStillActiveException, InterruptedException {
        long deadline = System.nanoTime() + writerDrainTimeout.toNanos();
        synchronized (writerMonitor) {
            while (activeWriters > 0) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis <= 0) {
                    log.warn("Import aborted: {} admitted writer(s) still running after {}", activeWriters, writerDrainTimeout);
                    throw new ImportWritersStillActiveException(activeWriters);
                }
                writerMonitor.wait(remainingMillis);
            }
        }
    }
}
