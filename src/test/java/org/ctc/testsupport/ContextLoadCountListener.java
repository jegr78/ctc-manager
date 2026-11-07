package org.ctc.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * PERF-02 instrumentation: counts every Spring {@link ConfigurableApplicationContext}
 * initialization in the test JVM and writes the per-fork tally to
 * {@code target/test-perf/context-loads-{PID}.txt} at JVM shutdown. PID-keyed output
 * is required because Surefire runs with {@code forkCount=2}; a shared filename would
 * be clobbered by the second fork's shutdown hook.
 */
public class ContextLoadCountListener
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final AtomicInteger count = new AtomicInteger(0);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long pid = ProcessHandle.current().pid();
                writeMarkers(Paths.get("target/test-perf"), pid, new CacheAccess().cache());
            } catch (IOException e) {
                System.err.println("ContextLoadCountListener: could not write marker file: "
                        + e.getMessage());
            }
        }, "ContextLoadCountListener-shutdown"));
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        count.incrementAndGet();
    }

    static void writeMarkers(Path directory, long pid,
            org.springframework.test.context.cache.ContextCache cache) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("context-loads-" + pid + ".txt"), "total " + count.get());
        Files.writeString(directory.resolve("context-loads-" + pid + "-cache.txt"),
                "size " + cache.size() + "\nhits " + cache.getHitCount() + "\nmisses " + cache.getMissCount() + "\n");
    }

    private static class CacheAccess extends org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate {
        org.springframework.test.context.cache.ContextCache cache() {
            return getContextCache();
        }
    }

    static int getCount() {
        return count.get();
    }
}
