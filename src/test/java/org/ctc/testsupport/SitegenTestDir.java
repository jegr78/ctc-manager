package org.ctc.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Per-class temporary output directory for sitegen tests, initialized at class-load time.
 *
 * <p>Equivalent to {@code @TempDir static Path}, but works under {@code @TestInstance(PER_CLASS)}.
 * With PER_CLASS lifecycle, JUnit's {@code @TempDir} {@code BeforeAllCallback} fires AFTER
 * Spring's {@code @DynamicPropertySource} resolver, so the static field is still {@code null}
 * when a {@code @ConfigurationProperties} bean tries to bind. Initializing via a static field
 * initializer guarantees the path exists before any Spring or JUnit callback runs.
 *
 * <p>Cleanup is best-effort via a JVM shutdown hook (the OS reclaims {@code /tmp} on reboot
 * regardless).
 */
public final class SitegenTestDir {

    private SitegenTestDir() {}

    public static Path create(String label) {
        try {
            Path dir = Files.createTempDirectory("sitegen-" + label + "-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(dir)));
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
