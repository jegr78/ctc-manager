package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.ctc.admin.TestDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupTimeProgressionRoundTripIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-site-slug-it-");
            IMPORT_BACKUPS_ROOT.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate import-backups tempdir", e);
        }
    }

    @DynamicPropertySource
    static void overrideImportBackupsDir(DynamicPropertyRegistry registry) {
        registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
    }

    @Autowired private TestDataService testDataService;
    @Autowired private BackupArchiveService backupArchiveService;
    @Autowired private BackupImportService backupImportService;
    @Autowired private org.ctc.domain.repository.RaceRepository raceRepository;
    @Autowired private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @BeforeEach
    void seedFixture() {
        testDataService.seed();
    }

    @AfterEach
    void cleanImportBackupsRoot() throws IOException {
        if (Files.exists(IMPORT_BACKUPS_ROOT)) {
            try (var stream = Files.walk(IMPORT_BACKUPS_ROOT)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .filter(p -> !p.equals(IMPORT_BACKUPS_ROOT))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }

    @Test
    void givenEqualConditions_whenExportedAndImported_thenModeAndAbsentMultiplierSurvive() throws Exception {
        // given
        UUID raceId = transactionTemplate.execute(status -> {
            var race = raceRepository.findAll().stream().filter(r -> r.getSettings() != null).findFirst().orElseThrow();
            race.getSettings().setTimeProgressionMode(org.ctc.domain.model.TimeProgressionMode.EQUAL_CONDITIONS);
            race.getSettings().setTimeProgressionMultiplier(null);
            return raceRepository.save(race).getId();
        });
        // when
        runFullBackupRoundTrip();
        // then
        transactionTemplate.executeWithoutResult(status -> {
            var settings = raceRepository.findById(raceId).orElseThrow().getSettings();
            assertThat(settings.getTimeProgressionMode()).isEqualTo(org.ctc.domain.model.TimeProgressionMode.EQUAL_CONDITIONS);
            assertThat(settings.getTimeProgressionMultiplier()).isNull();
            assertThat(settings.getTimeProgressionDisplay()).isEqualTo("Equal Conditions");
        });
    }

    private void runFullBackupRoundTrip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        MockMultipartFile file = new MockMultipartFile(
                "file", "site-slug-roundtrip-export.zip", "application/zip", baos.toByteArray());
        var preview = backupImportService.stage(file);
        backupImportService.execute(preview.stagingId());
    }
}
