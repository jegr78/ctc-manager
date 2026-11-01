package org.ctc.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeploymentScriptsTest {

    @Test
    void whenDeploymentScriptsRun_thenRecipeAndReleaseContractsPass() throws Exception {
        Path output = Files.createTempFile("ctc-deployment-test-", ".log");
        try {
            Process process = new ProcessBuilder("bash", "deploy/tests/acceptance.sh")
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertThat(process.waitFor(90, TimeUnit.SECONDS)).as("deployment acceptance completed").isTrue();
            assertThat(process.exitValue()).as("deployment acceptance: %s", Files.readString(output)).isZero();
        } finally {
            Files.deleteIfExists(output);
        }
    }
}
