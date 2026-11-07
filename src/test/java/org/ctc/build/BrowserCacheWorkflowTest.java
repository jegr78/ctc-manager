package org.ctc.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.assertThat;

class BrowserCacheWorkflowTest {
    @Test
    void givenResolvedVersionsAndPlatformChanges_whenComputingBrowserCacheKeys_thenRejectIncompatibleReuse(
            @TempDir Path directory) throws Exception {
        // given
        Path log = directory.resolve("browser-cache.log");
        Process process = new ProcessBuilder("python3", "-m", "unittest", "discover",
                "-s", "scripts/tests", "-p", "test_browser_cache.py")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            // when
            boolean complete = process.waitFor(30, TimeUnit.SECONDS);
            // then
            assertThat(complete).as("browser cache fixtures completed").isTrue();
            assertThat(process.exitValue()).as("browser fixtures: %s", Files.readString(log)).isZero();
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(child -> child.destroyForcibly());
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenWarmCacheAndAnApplicationOnlyChange_whenBuilding_thenKeepBrowserIdentityAndPreinstallation() throws Exception {
        // given
        Map<String, Object> action = new Yaml().load(Files.readString(Path.of(".github/actions/test-partition/action.yml")));
        var steps = (List<Map<String, Object>>) ((Map<String, Object>) action.get("runs")).get("steps");
        int resolve = -1, cache = -1, install = -1, tests = -1;
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            if ("browser".equals(step.get("id"))) resolve = i;
            if (String.valueOf(step.get("uses")).startsWith("actions/cache@")) {
                cache = i;
                var inputs = (Map<String, Object>) step.get("with");
                assertThat(inputs.get("key")).isEqualTo("${{ steps.browser.outputs.key }}-all");
                assertThat(inputs).doesNotContainKey("restore-keys");
            }
            if (String.valueOf(step.get("run")).contains("CLI install --with-deps")) install = i;
            if (String.valueOf(step.get("run")).contains("run-partition.py")) tests = i;
        }
        // when / then
        assertThat(resolve).isNotNegative().isLessThan(cache);
        assertThat(cache).isLessThan(install);
        assertThat(install).isLessThan(tests);
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        int appCopy = dockerfile.indexOf("COPY --from=build --chown=ctc:ctc /build/target/ctc-manager-");
        assertThat(dockerfile.indexOf("CLI install --with-deps chromium")).isPositive().isLessThan(appCopy);
        assertThat(dockerfile.indexOf("CLI screenshot")).isPositive().isLessThan(appCopy);
        assertThat(dockerfile).contains("--mount=type=bind,from=build-dependencies,source=/playwright",
                "-DincludeGroupIds=com.microsoft.playwright", "USER ctc", "ENTRYPOINT [\"java\", \"-jar\", \"ctc-manager.jar\"]");
    }
}
