package org.ctc.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeploymentScriptsTest {

    @Test
    void whenDeploymentScriptsRun_thenRecipeAndReleaseContractsPass() throws Exception {
        for (String script : new String[]{"recipe.sh", "release-bundle.sh", "host-setup.sh"}) {
            Path output = Files.createTempFile("ctc-deployment-test-", ".log");
            try {
                Process process = new ProcessBuilder("bash", "deploy/tests/" + script)
                        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
                assertThat(process.waitFor(90, TimeUnit.SECONDS)).as("script %s completed", script).isTrue();
                assertThat(process.exitValue()).as("%s: %s", script, Files.readString(output)).isZero();
            } finally {
                Files.deleteIfExists(output);
            }
        }
    }
}
