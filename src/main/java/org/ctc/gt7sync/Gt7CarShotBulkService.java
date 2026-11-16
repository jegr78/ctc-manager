package org.ctc.gt7sync;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.repository.CarRepository;
import org.ctc.integration.ExternalIntegrationDisabledException;
import org.ctc.integration.OutboundPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Loads the GT7 car shots of every car with a GT7 ID and no GT7 image yet, as one background run. The car list bundle
 * is fetched once per run; each car takes the restore writer admission on its own, so a restore can drain between cars.
 */
@Slf4j
@Service
public class Gt7CarShotBulkService {
    static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final int LISTED_FAILURES = 10;

    private final CarRepository cars;
    private final Gt7ScraperService scraper;
    private final Gt7CarShotService shots;
    private final ImportLockService imports;
    private final OutboundPolicy outbound;
    private final Executor executor;
    private final Clock clock;
    private Run run;

    @Autowired
    public Gt7CarShotBulkService(CarRepository cars, Gt7ScraperService scraper, Gt7CarShotService shots, ImportLockService imports,
                                 OutboundPolicy outbound, Clock clock) {
        this(cars, scraper, shots, imports, outbound, worker(), clock);
    }

    Gt7CarShotBulkService(CarRepository cars, Gt7ScraperService scraper, Gt7CarShotService shots, ImportLockService imports,
                          OutboundPolicy outbound, Executor executor, Clock clock) {
        this.cars = cars;
        this.scraper = scraper;
        this.shots = shots;
        this.imports = imports;
        this.outbound = outbound;
        this.executor = executor;
        this.clock = clock;
    }

    /** Interrupts an active run so it ends after the current car. */
    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) service.shutdownNow();
    }

    private static ExecutorService worker() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("gt7-car-shots").daemon().factory());
    }

    /** Number of cars a new run would load. */
    public int pendingCars() {
        return candidates().size();
    }

    /** The current or last run, or {@code null} when none ran since startup. */
    public synchronized Status status() {
        return run == null ? null : run.status();
    }

    /** Starts a run in the background; rejects a second run while one is active. */
    public synchronized Status start() {
        if (run != null && run.status().running()) throw new BusinessRuleException("GT7 images are already loading for all cars.");
        if (!outbound.isEnabled()) throw new BusinessRuleException(ExternalIntegrationDisabledException.MESSAGE);
        var ids = candidates();
        if (ids.isEmpty()) throw new BusinessRuleException("Every car with a GT7 ID already has GT7 images.");
        var started = new Run(ids.size(), clock.instant());
        run = started;
        try {
            executor.execute(() -> process(started, ids));
        } catch (RejectedExecutionException shuttingDown) {
            started.finish("the application is shutting down", clock.instant());
            throw new BusinessRuleException("GT7 images cannot load while the application shuts down.");
        }
        return started.status();
    }

    /** Asks the active run to stop after the current car. */
    public synchronized void stop() {
        if (run == null || !run.status().running()) throw new BusinessRuleException("No GT7 image run is active.");
        run.stopRequested = true;
    }

    private List<UUID> candidates() {
        return cars.findWithoutGt7Images().stream().filter(car -> Gt7ScraperService.isCarId(car.getGt7Id())).map(CarRepository.Gt7CarId::getId).toList();
    }

    private void process(Run current, List<UUID> ids) {
        try {
            Gt7ScraperService.CarShotIndex index;
            try {
                index = scraper.carShotIndex();
            } catch (IOException | RuntimeException failure) {
                log.warn("Reading the GT7 car list for a bulk car shot run failed", failure);
                current.finish("the GT7 car list could not be read", clock.instant());
                return;
            }
            int consecutiveFailures = 0;
            for (UUID id : ids) {
                String reason = stopReason(current);
                if (reason != null) {
                    current.finish(reason, clock.instant());
                    return;
                }
                if (!imports.tryEnterWriter()) {
                    current.finish("a backup restore started", clock.instant());
                    return;
                }
                try {
                    var result = shots.load(id, index);
                    current.loaded(result.added().size() + result.replaced().size(), result.empty());
                    consecutiveFailures = 0;
                } catch (EntityNotFoundException deleted) {
                    current.skipped();
                } catch (BusinessRuleException failed) {
                    current.failed(id, failed.getMessage());
                    if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        current.finish(MAX_CONSECUTIVE_FAILURES + " cars in a row failed", clock.instant());
                        return;
                    }
                } finally {
                    imports.exitWriter();
                }
            }
            current.finish(null, clock.instant());
        } catch (RuntimeException unexpected) {
            log.error("Bulk car shot run failed", unexpected);
            current.finish("an unexpected error occurred", clock.instant());
        }
    }

    private String stopReason(Run current) {
        if (current.stopRequested) return "stopped by management";
        if (Thread.currentThread().isInterrupted()) return "the application is shutting down";
        if (!outbound.isEnabled()) return "outbound access is disabled";
        return null;
    }

    private static final class Run {
        private final int total;
        private final Instant startedAt;
        private volatile boolean stopRequested;
        private int processed;
        private int images;
        private int withoutShots;
        private int skipped;
        private final List<String> failures = new ArrayList<>();
        private int failedCount;
        private boolean running = true;
        private String stopReason;
        private Instant finishedAt;

        Run(int total, Instant startedAt) {
            this.total = total;
            this.startedAt = startedAt;
        }

        synchronized void loaded(int stored, boolean none) {
            processed++;
            images += stored;
            if (none) withoutShots++;
        }

        synchronized void skipped() {
            processed++;
            skipped++;
        }

        synchronized void failed(UUID car, String reason) {
            processed++;
            failedCount++;
            if (failures.size() < LISTED_FAILURES) failures.add(car + ": " + reason);
        }

        synchronized void finish(String reason, Instant at) {
            running = false;
            stopReason = reason;
            finishedAt = at;
        }

        synchronized Status status() {
            return new Status(running, total, processed, images, withoutShots, skipped, failedCount, List.copyOf(failures),
                    stopReason, startedAt, finishedAt);
        }
    }

    /** A snapshot of a run; {@code stopReason} is set when the run ended before processing every car. */
    public record Status(boolean running, int total, int processed, int images, int withoutShots, int skipped, int failed,
                         List<String> failures, String stopReason, Instant startedAt, Instant finishedAt) {
        public String message() {
            String progress = "processed " + processed + " of " + total + " cars, stored " + images + " images"
                    + (withoutShots > 0 ? ", " + withoutShots + " cars without GT7 shots" : "")
                    + (skipped > 0 ? ", " + skipped + " cars deleted meanwhile" : "")
                    + (failed > 0 ? ", " + failed + " failed" : "");
            if (running) return "Loading GT7 images: " + progress + ".";
            return "GT7 image run " + (stopReason == null ? "finished" : "stopped (" + stopReason + ")") + ": " + progress + ".";
        }
    }
}
