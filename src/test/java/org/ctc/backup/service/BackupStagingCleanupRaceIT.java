package org.ctc.backup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupStagingCleanupRaceIT {

    @Value("${app.backup.staging-dir}")
    Path ownForkDir;

    @Autowired
    ApplicationContext context;

    private Path siblingForkDir;

    @BeforeAll
    void setUp() throws IOException {
        Files.createDirectories(ownForkDir);
        siblingForkDir = ownForkDir.getParent().resolve("backup-staging-fork-99");
        Files.createDirectories(siblingForkDir);
    }

    @AfterAll
    void tearDown() throws IOException {
        deleteIfExists(ownForkDir.resolve("unrelated.txt"));
        if (siblingForkDir != null && Files.exists(siblingForkDir)) {
            try (Stream<Path> walk = Files.walk(siblingForkDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(BackupStagingCleanupRaceIT::deleteIfExists);
            }
        }
    }

    private static void deleteIfExists(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException _) {
            // best-effort cleanup; surfacing failures here only obscures the test verdict
        }
    }

    @Test
    void givenFilesInOwnAndSiblingForkDirs_whenApplicationReady_thenOnlyOwnForkFilesRemoved() throws IOException {
        // given
        Path ownUpload = ownForkDir.resolve("upload-test-A.zip");
        Path ownUploadMeta = ownForkDir.resolve("upload-test-A.zip.meta");
        Path ownUnrelated = ownForkDir.resolve("unrelated.txt");
        Path siblingUpload = siblingForkDir.resolve("upload-test-A.zip");
        Path siblingUploadMeta = siblingForkDir.resolve("upload-test-A.zip.meta");
        Path siblingUnrelated = siblingForkDir.resolve("unrelated.txt");

        Files.write(ownUpload, "test".getBytes());
        Files.write(ownUploadMeta, "meta".getBytes());
        Files.write(ownUnrelated, "keepme".getBytes());
        Files.write(siblingUpload, "test".getBytes());
        Files.write(siblingUploadMeta, "meta".getBytes());
        Files.write(siblingUnrelated, "keepme".getBytes());

        // when
        context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));

        // then — own fork swept selectively
        assertThat(Files.exists(ownUpload)).isFalse();
        assertThat(Files.exists(ownUploadMeta)).isFalse();
        assertThat(Files.exists(ownUnrelated)).isTrue();

        // then — sibling fork untouched
        assertThat(Files.exists(siblingUpload)).isTrue();
        assertThat(Files.exists(siblingUploadMeta)).isTrue();
        assertThat(Files.exists(siblingUnrelated)).isTrue();
    }
}
