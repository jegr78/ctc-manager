package org.ctc.testsupport;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.support.DefaultTestContext;
import org.springframework.util.ReflectionUtils;

/**
 * PERF-02 instrumentation: records a fingerprint of every Spring TCF
 * {@link MergedContextConfiguration} bucketed by {@code DefaultContextCache}
 * (i.e. the same key that drives Spring's test-context cache hit/miss decision).
 * Writes a sidecar marker file {@code target/test-perf/context-loads-{PID}-fingerprints.txt}
 * at JVM shutdown, with one {@code <hex-hash>\t<mcc-display>} line per
 * {@code beforeTestClass} event.
 *
 * <p>The hash is {@link MergedContextConfiguration#hashCode()} hex-encoded — the
 * exact function {@code DefaultContextCache} uses to bucket contexts. The display
 * portion is truncated to 200 characters to keep the marker file bounded.
 *
 * <p>The listener uses reflection on {@link DefaultTestContext#mergedConfig}
 * (private final) because Spring's public {@link TestContext} interface exposes
 * neither the field nor a getter. Access is read-only.
 */
public class ContextCacheKeyFingerprintListener implements TestExecutionListener {

    private static final int DISPLAY_MAX_LENGTH = 200;

    private static final CopyOnWriteArrayList<String> FINGERPRINT_LINES = new CopyOnWriteArrayList<>();

    private static final Field MERGED_CONFIG_FIELD;

    static {
        Field f = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
        if (f != null) {
            ReflectionUtils.makeAccessible(f);
        } else {
            System.err.println(
                    "ContextCacheKeyFingerprintListener: DefaultTestContext.mergedConfig not found — degraded to no-op");
        }
        MERGED_CONFIG_FIELD = f;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                long pid = ProcessHandle.current().pid();
                Path sidecar = Paths.get("target/test-perf/context-loads-" + pid + "-fingerprints.txt");
                Files.createDirectories(sidecar.getParent());
                Files.write(sidecar, FINGERPRINT_LINES,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                System.err.println(
                        "ContextCacheKeyFingerprintListener: could not write sidecar: " + e.getMessage());
            }
        }, "ContextCacheKeyFingerprintListener-shutdown"));
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        if (MERGED_CONFIG_FIELD == null) {
            return;
        }
        if (!(testContext instanceof DefaultTestContext)) {
            return;
        }
        try {
            MergedContextConfiguration mcc =
                    (MergedContextConfiguration) ReflectionUtils.getField(MERGED_CONFIG_FIELD, testContext);
            if (mcc == null) {
                return;
            }
            String hex = Integer.toHexString(mcc.hashCode());
            String display = mcc.toString();
            if (display.length() > DISPLAY_MAX_LENGTH) {
                display = display.substring(0, DISPLAY_MAX_LENGTH);
            }
            FINGERPRINT_LINES.add(hex + "\t" + display);
        } catch (RuntimeException e) {
            System.err.println(
                    "ContextCacheKeyFingerprintListener: fingerprint failed: " + e.getMessage());
        }
    }

    static List<String> getRecordedLines() {
        return List.copyOf(FINGERPRINT_LINES);
    }
}
