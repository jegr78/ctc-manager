package org.ctc.backup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Startup safety net that reaps orphaned staging files left by a previous JVM that died
 * between upload and import confirmation.
 *
 * <p>On {@link ApplicationReadyEvent}, walks {@code app.backup.staging-dir} and deletes
 * every {@code upload-*.zip} and {@code upload-*.zip.meta} file.
 *
 * <p>A scheduled periodic sweep is deferred to v1.11 — startup-sweep plus the
 * per-request reject-delete is enough for the 1–2-uploads-per-week cadence.
 */
@Component
@Slf4j
class BackupStagingCleanup {

    private final Path stagingDir;

    BackupStagingCleanup(@Value("${app.backup.staging-dir}") Path stagingDir) {
        this.stagingDir = stagingDir;
    }

    @EventListener(ApplicationReadyEvent.class)
    void sweepStagingDir() {
        if (!Files.isDirectory(stagingDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(stagingDir)) {
            long deleted = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("upload-")
                            && (name.endsWith(".zip") || name.endsWith(".zip.meta"));
                    })
                    .mapToInt(this::deleteOrLog)
                    .sum();
            log.info("Cleared {} stale staging files", deleted);
        } catch (IOException e) {
            log.warn("Failed to sweep staging directory {}: {}", stagingDir, e.getMessage());
        }
    }

    private int deleteOrLog(Path p) {
        try {
            Files.delete(p);
            return 1;
        } catch (IOException e) {
            log.warn("Failed to delete stale staging file {}: {}", p, e.getMessage());
            return 0;
        }
    }
}
