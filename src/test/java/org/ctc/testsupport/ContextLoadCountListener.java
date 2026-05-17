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
                Path out = Paths.get("target/test-perf/context-loads-" + pid + ".txt");
                Files.createDirectories(out.getParent());
                Files.writeString(out, String.valueOf(count.get()));
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

    static int getCount() {
        return count.get();
    }
}
