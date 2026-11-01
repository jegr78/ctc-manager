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
class BackupCarMetadataRoundTripIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-car-metadata-it-");
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
    @Autowired private org.ctc.domain.repository.CarRepository carRepository;
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
    void givenReviewedAndExplicitUnknownCars_whenBackupRestored_thenAllMetadataAndIdentitySurvive() throws Exception {
        // given
        UUID acceptedId = transactionTemplate.execute(status -> {
            var car = new org.ctc.domain.model.Car("Test_Metadata", "Accepted '20");
            car.setGt7Id("car3531");
            car.setImageUrl("/uploads/cars/test-metadata.png");
            car.setCategory(org.ctc.domain.model.CarCategory.GR_3);
            car.setDrivetrain(org.ctc.domain.model.Drivetrain.FR);
            car.setModelYear(2020);
            car.setProposedModelYear(2025);
            car.setCategorySource("GT7:car3531:carClass=Gr.3");
            car.setDrivetrainSource("MANUAL");
            car.setModelYearSource("MANUAL");
            car.setModelYearEvidence("Reviewed by administrator");
            car.setProposedModelYearEvidence("GTPlus ID 3531 candidate 2025");
            car.setCategoryOverride(false);
            car.setDrivetrainOverride(true);
            car.setModelYearOverride(true);
            return carRepository.save(car).getId();
        });
        UUID unknownId = transactionTemplate.execute(status -> {
            var car = new org.ctc.domain.model.Car("Test_Metadata", "Explicit unknown");
            car.setModelYearOverride(true);
            car.setModelYearSource("MANUAL");
            car.setModelYearEvidence("Explicitly unknown model year");
            return carRepository.save(car).getId();
        });
        // when
        runFullBackupRoundTrip();
        // then
        transactionTemplate.executeWithoutResult(status -> {
            var accepted = carRepository.findById(acceptedId).orElseThrow();
            assertThat(accepted.getGt7Id()).isEqualTo("car3531");
            assertThat(accepted.getImageUrl()).isEqualTo("/uploads/cars/test-metadata.png");
            assertThat(accepted.getCategory()).isEqualTo(org.ctc.domain.model.CarCategory.GR_3);
            assertThat(accepted.getDrivetrain()).isEqualTo(org.ctc.domain.model.Drivetrain.FR);
            assertThat(accepted.getModelYear()).isEqualTo(2020);
            assertThat(accepted.getProposedModelYear()).isEqualTo(2025);
            assertThat(accepted.getCategorySource()).isEqualTo("GT7:car3531:carClass=Gr.3");
            assertThat(accepted.getDrivetrainSource()).isEqualTo("MANUAL");
            assertThat(accepted.getModelYearSource()).isEqualTo("MANUAL");
            assertThat(accepted.getModelYearEvidence()).isEqualTo("Reviewed by administrator");
            assertThat(accepted.getProposedModelYearEvidence()).isEqualTo("GTPlus ID 3531 candidate 2025");
            assertThat(accepted.isCategoryOverride()).isFalse();
            assertThat(accepted.isDrivetrainOverride()).isTrue();
            assertThat(accepted.isModelYearOverride()).isTrue();
            var unknown = carRepository.findById(unknownId).orElseThrow();
            assertThat(unknown.getGt7Id()).isNull();
            assertThat(unknown.getModelYear()).isNull();
            assertThat(unknown.isModelYearOverride()).isTrue();
            assertThat(unknown.getModelYearSource()).isEqualTo("MANUAL");
            assertThat(unknown.getModelYearEvidence()).isEqualTo("Explicitly unknown model year");
        });
    }

    private void runFullBackupRoundTrip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        MockMultipartFile file = new MockMultipartFile(
                "file", "car-metadata-roundtrip-export.zip", "application/zip", baos.toByteArray());
        var preview = backupImportService.stage(file);
        backupImportService.execute(preview.stagingId());
    }
}
