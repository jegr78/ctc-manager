package org.ctc.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class CiPartitionWorkflowTest {
    @Test
    void givenIncompleteOrConflictingPartitionEvidence_whenCheckingTheAggregate_thenRejectFalseSuccess(
            @TempDir Path directory) throws Exception {
        // given
        Path log = directory.resolve("ci-partitions.log");
        Process process = new ProcessBuilder("python3", "-m", "unittest", "discover",
                "-s", "scripts/tests", "-p", "test_ci_partitions.py")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            // when
            boolean complete = process.waitFor(30, TimeUnit.SECONDS);
            // then
            assertThat(complete).as("CI partition fixtures completed").isTrue();
            assertThat(process.exitValue()).as("partition fixtures: %s", Files.readString(log)).isZero();
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
    void givenParallelWorkers_whenCheckingRequiredAndReleaseGates_thenRequireEveryExpectedJob() throws Exception {
        // given
        Map<String, Object> workflow = new Yaml().load(Files.readString(Path.of(".github/workflows/ci.yml")));
        var jobs = (Map<String, Map<String, Object>>) workflow.get("jobs");
        var aggregate = jobs.get("build-and-test");
        // when / then
        assertThat((List<String>) aggregate.get("needs")).containsExactlyInAnyOrder("changes", "prepare-tests",
                "test-core", "test-mariadb-1", "test-mariadb-2", "test-browser");
        assertThat(aggregate.get("if")).isEqualTo("${{ !cancelled() }}");
        for (String group : List.of("core", "mariadb-1", "mariadb-2", "browser")) {
            var worker = jobs.get("test-" + group);
            assertThat((List<String>) worker.get("needs")).containsExactly("changes", "prepare-tests");
            assertThat(worker.get("if")).isEqualTo("needs.changes.outputs.code == 'true'");
            var steps = (List<Map<String, Object>>) worker.get("steps");
            assertThat(steps).anySatisfy(step -> {
                assertThat(step.get("uses")).isEqualTo("./.github/actions/test-partition");
                assertThat((Map<String, String>) step.get("with")).containsEntry("group", group);
            });
        }
        assertThat(Files.readString(Path.of(".github/workflows/release.yml")))
                .contains(".github/workflows/ci.yml:build-and-test", ".github/workflows/ci.yml:docker-build",
                        ".github/workflows/codeql.yml:Analyze (java-kotlin)");
        assertThat((List<String>) jobs.get("docker-build").get("needs"))
                .containsExactly("changes", "dockerfile-noble-pin-guard");
    }

}
