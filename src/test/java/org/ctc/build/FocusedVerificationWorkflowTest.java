package org.ctc.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FocusedVerificationWorkflowTest {
    @Test
    void givenDeferredFailuresAndStaleEvidence_whenRunningWorkflowFixtures_thenRejectFalseSuccess(
            @TempDir Path directory) throws Exception {
        // given
        Path output = directory.resolve("feedback-workflow.log");
        Process process = new ProcessBuilder("python3", "-m", "unittest", "discover",
                "-s", "scripts/tests", "-p", "test_feedback_workflow.py")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            // when
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            // then
            assertThat(completed).as("feedback fixture suite completed").isTrue();
            assertThat(process.exitValue()).as("feedback fixtures: %s", Files.readString(output)).isZero();
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(child -> child.destroyForcibly());
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
