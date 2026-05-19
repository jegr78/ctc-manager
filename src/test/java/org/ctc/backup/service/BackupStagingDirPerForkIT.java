package org.ctc.backup.service;

import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupStagingDirPerForkIT {

    @Value("${app.backup.staging-dir}")
    Path stagingDir;

    @Test
    void whenAppRuns_thenStagingDirNameMatchesPerForkPattern() {
        // given the Surefire/Failsafe per-fork system property has been resolved
        String dirName = stagingDir.getFileName().toString();

        // when matched against the per-fork pattern
        // then it carries the fork-number suffix injected by the build
        assertThat(dirName).matches("backup-staging-fork-\\d+");
    }

    @Test
    void givenForkNumberPropertySet_whenAppRuns_thenStagingDirSuffixEqualsForkNumber() {
        // given the JVM was forked by Surefire/Failsafe (IDE direct-launch bypasses the system property)
        String forkNum = System.getProperty("test.fork.number");
        assumeThat(forkNum).as("test.fork.number is only exposed inside Surefire/Failsafe forks").isNotBlank();

        // when reading the resolved staging-dir
        String dirName = stagingDir.getFileName().toString();

        // then the dir name's numeric suffix equals the JVM-side fork number
        assertThat(dirName).endsWith("-" + forkNum);
    }
}
