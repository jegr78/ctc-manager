package org.ctc.media.job;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.util.LogSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Runs at most one media job (trailer render or car cutout) at a time on a single background thread and keeps the
 * latest status per scope in memory. A committed restore abandons a running job at its next check.
 */
@Slf4j
@Component
public class MediaJobExecutor {

    private final ImportLockService imports;
    private final PickSessionEpoch epoch;
    private final Executor executor;
    private final Clock clock;
    private final Map<String, MediaJobStatus> statuses = new ConcurrentHashMap<>();
    private MediaJobStatus current;

    @Autowired
    public MediaJobExecutor(ImportLockService imports, PickSessionEpoch epoch, Clock clock) {
        this(imports, epoch, Executors.newSingleThreadExecutor(Thread.ofPlatform().name("media-jobs").daemon().factory()), clock);
    }

    MediaJobExecutor(ImportLockService imports, PickSessionEpoch epoch, Executor executor, Clock clock) {
        this.imports = imports;
        this.epoch = epoch;
        this.executor = executor;
        this.clock = clock;
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) service.shutdownNow();
    }

    public synchronized MediaJobStatus submit(MediaJobKind kind, String scopeKey, MediaJob job) {
        if (current != null && current.running()) throw new MediaJobBusyException("Another media job is running.");
        if (imports.isLocked()) throw new MediaJobBusyException("A restore is running.");
        var id = UUID.randomUUID();
        long generation = epoch.getAsLong();
        var queued = new MediaJobStatus(id, kind, scopeKey, MediaJobState.QUEUED, 0, 0, "Waiting to start", clock.instant());
        publish(queued);
        try {
            executor.execute(() -> run(queued, generation, job));
        } catch (RejectedExecutionException shuttingDown) {
            publish(with(queued, MediaJobState.FAILED, 0, 0, "The application is shutting down."));
            throw new MediaJobBusyException("The application is shutting down.");
        }
        return queued;
    }

    public Optional<MediaJobStatus> status(String scopeKey) {
        return Optional.ofNullable(statuses.get(scopeKey));
    }

    private void run(MediaJobStatus queued, long generation, MediaJob job) {
        var ctx = new Context(queued, generation);
        publish(with(queued, MediaJobState.RUNNING, 0, 0, "Starting"));
        try {
            job.run(ctx);
            ctx.finish(MediaJobState.DONE, ctx.last.message());
        } catch (MediaJobAbandonedException abandoned) {
            ctx.finish(MediaJobState.ABANDONED, abandoned.getMessage());
        } catch (Exception | Error failure) {
            log.warn("Media job {} for {} failed", queued.kind(), LogSanitizer.sanitize(queued.scopeKey()), failure);
            String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            ctx.finish(MediaJobState.FAILED, LogSanitizer.sanitize(reason));
        }
    }

    private synchronized void publish(MediaJobStatus status) {
        statuses.put(status.scopeKey(), status);
        current = status;
    }

    private MediaJobStatus with(MediaJobStatus base, MediaJobState state, int step, int steps, String message) {
        return new MediaJobStatus(base.jobId(), base.kind(), base.scopeKey(), state, step, steps, message, clock.instant());
    }

    private final class Context implements MediaJobContext {
        private final long generation;
        private MediaJobStatus last;

        Context(MediaJobStatus queued, long generation) {
            this.generation = generation;
            this.last = queued;
        }

        @Override
        public void progress(int step, int steps, String message) {
            last = with(last, MediaJobState.RUNNING, step, steps, message);
            publish(last);
        }

        @Override
        public void checkNotAbandoned() {
            if (epoch.getAsLong() != generation || Thread.currentThread().isInterrupted()) throw new MediaJobAbandonedException();
        }

        void finish(MediaJobState state, String message) {
            last = with(last, state, last.step(), last.steps(), message);
            publish(last);
        }
    }
}
