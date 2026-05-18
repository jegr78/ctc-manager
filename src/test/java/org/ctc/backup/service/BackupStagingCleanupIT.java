package org.ctc.backup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BackupStagingCleanup} — the startup sweep listener (D-17, Phase 74).
 *
 * <p>Four scenarios from D-17 are covered:
 * <ol>
 *   <li>Three stale {@code upload-*.zip} files → all deleted, {@code Cleared 3 stale staging files} logged.</li>
 *   <li>Empty staging dir → zero deleted, {@code Cleared 0 stale staging files} logged (operational signal).</li>
 *   <li>Staging dir does not exist → silent no-op (early-return branch; first-ever boot).</li>
 *   <li>Unrelated file present alongside one stale zip → only the zip deleted, unrelated file survives.</li>
 * </ol>
 *
 * <p>{@link OutputCaptureExtension} (Spring Boot Test) is used to capture stdout output for log-line
 * assertions. No existing CTC test uses this extension — this IT introduces it. No Logback
 * {@code ListAppender} plumbing is needed because D-17 pins the contract to the exact formatted
 * string {@code "Cleared {N} stale staging files"} (see Plan 07, Notes section).
 *
 * <p>Each test method re-publishes {@link ApplicationReadyEvent} via
 * {@link ApplicationContext#publishEvent(Object)} after seeding fixture files, because the
 * listener fires once during context bootstrap (before {@code @BeforeEach}) and must be
 * re-triggered with the test's specific file state. This avoids {@code @DirtiesContext} (+5 s
 * per test) — the listener has no in-memory state.
 */
@SpringBootTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
@Tag("integration")
class BackupStagingCleanupIT {

    @TempDir
    static Path tempStagingDir;

    @DynamicPropertySource
    static void overrideStagingDir(DynamicPropertyRegistry registry) {
        registry.add("app.backup.staging-dir", () -> tempStagingDir.toString());
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    BackupStagingCleanup cleanup;

    @Test
    void givenThreeStaleStagingFiles_whenApplicationReady_thenAllDeletedAndCountLogged(CapturedOutput output)
            throws IOException {
        // given
        Files.createDirectories(tempStagingDir);
        Files.write(tempStagingDir.resolve("upload-" + UUID.randomUUID() + ".zip"), new byte[]{0x50, 0x4B, 0x05, 0x06});
        Files.write(tempStagingDir.resolve("upload-" + UUID.randomUUID() + ".zip"), new byte[]{0x50, 0x4B, 0x05, 0x06});
        Files.write(tempStagingDir.resolve("upload-" + UUID.randomUUID() + ".zip"), new byte[]{0x50, 0x4B, 0x05, 0x06});

        // when
        context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));

        // then
        try (Stream<Path> stream = Files.list(tempStagingDir)) {
            assertThat(stream.count()).isZero();
        }
        assertThat(output.getAll()).contains("Cleared 3 stale staging files");
    }

    @Test
    void givenEmptyStagingDir_whenApplicationReady_thenLogsZeroCleared(CapturedOutput output) throws IOException {
        // given — tempStagingDir must exist and be empty
        Files.createDirectories(tempStagingDir);

        // when
        context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));

        // then
        assertThat(output.getAll()).contains("Cleared 0 stale staging files");
    }

    @Test
    void givenStagingDirDoesNotExist_whenApplicationReady_thenNoCleanupLogEmitted(CapturedOutput output)
            throws IOException {
        // given
        Files.deleteIfExists(tempStagingDir);
        assertThat(Files.isDirectory(tempStagingDir)).isFalse();

        // when
        int outputLengthBefore = output.getAll().length();
        context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));

        // then — no "Cleared" log line after the re-published event
        assertThat(output.getAll().substring(outputLengthBefore)).doesNotContain("Cleared ");
    }

    @Test
    void givenUnrelatedFileAndOneStaleStagingFile_whenApplicationReady_thenOnlyStagingFileDeleted(CapturedOutput output)
            throws IOException {
        // given — recreate tempStagingDir if it was deleted by a prior test
        Files.createDirectories(tempStagingDir);
        Files.write(tempStagingDir.resolve("keepme.txt"), "do not delete".getBytes());
        Files.write(tempStagingDir.resolve("upload-" + UUID.randomUUID() + ".zip"), new byte[]{0x50, 0x4B, 0x05, 0x06});

        // when
        context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));

        // then
        assertThat(Files.exists(tempStagingDir.resolve("keepme.txt"))).isTrue();
        try (Stream<Path> stream = Files.list(tempStagingDir)) {
            long uploadCount = stream.filter(p -> p.getFileName().toString().startsWith("upload-")).count();
            assertThat(uploadCount).isZero();
        }
        assertThat(output.getAll()).contains("Cleared 1 stale staging files");
    }
}
