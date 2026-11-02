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
class BackupSeasonCarPoolRoundTripIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-season-pool-it-");
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

    @Autowired private org.ctc.domain.repository.SeasonRepository seasons;
    @Autowired private org.ctc.domain.repository.SeasonCarPoolEntryRepository entries;
    @Autowired private org.ctc.domain.service.SeasonCarPoolService pools;
    @Autowired private org.ctc.TestHelper helper;
    @Autowired private org.ctc.domain.repository.RaceRepository races;
    @org.springframework.beans.factory.annotation.Value("${app.backup.staging-dir}") private String stagingDir;

    @Test
    void givenFrozenPoolAndSavedReference_whenArchiveRestored_thenExactIdsAndMembershipSurvive() throws Exception {
        // given
        var ids = transactionTemplate.execute(status -> {
            var fixture = helper.createFullSeasonFixture("PoolBackup " + UUID.randomUUID().toString().substring(0, 8));
            var car = new org.ctc.domain.model.Car("Test Pool Backup", "Snapshot member " + UUID.randomUUID());
            car.setCategory(org.ctc.domain.model.CarCategory.GR_X);
            car.setModelYear(9876);
            car.setModelYearSource("MANUAL");
            car.setModelYearEvidence("Deliberate test fixture year");
            carRepository.saveAndFlush(car);
            pools.changeMode(fixture.season().getId(), org.ctc.domain.model.CarPoolMode.CATEGORY_POOL);
            var entryId = pools.saveEntry(fixture.season().getId(), null, "Historical label", java.util.List.of(
                    new org.ctc.domain.service.SeasonCarPoolService.ClauseInput(org.ctc.domain.model.CarCategory.GR_X, null, 9876)));
            // when
            pools.freeze(fixture.season().getId());
            var entry = entries.findById(entryId).orElseThrow();
            fixture.race().setCar(car);
            fixture.race().setCarPoolEntry(entry);
            races.saveAndFlush(fixture.race());
            var memberId = entry.getMembers().getFirst().getId();
            var clauseId = entry.getClauses().getFirst().getId();
            car.setCategory(org.ctc.domain.model.CarCategory.GR_4);
            carRepository.saveAndFlush(car);
            return new UUID[]{fixture.season().getId(), entryId, car.getId(), fixture.race().getId(), memberId, clauseId};
        });
        byte[] validArchive = exportArchive();
        byte[] brokenArchive = removeEntryReference(validArchive, ids[3]);
        // then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> backupImportService.stage(upload(brokenArchive)))
                .isInstanceOfSatisfying(org.ctc.backup.exception.BackupArchiveException.class, error -> {
                    assertThat(error.reason()).isEqualTo(org.ctc.backup.exception.BackupArchiveException.Reason.DATA_MISMATCH);
                    assertThat(error.getMessage()).contains("both entry and car");
                });
        var validPreview = backupImportService.stage(upload(validArchive));
        Files.write(Path.of(stagingDir).toAbsolutePath().resolve("upload-" + validPreview.stagingId() + ".zip"), brokenArchive);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> backupImportService.execute(validPreview.stagingId()))
                .isInstanceOf(org.ctc.backup.exception.BackupImportException.class).cause()
                .isInstanceOfSatisfying(org.ctc.backup.exception.BackupArchiveException.class,
                        error -> assertThat(error.reason()).isEqualTo(org.ctc.backup.exception.BackupArchiveException.Reason.DATA_MISMATCH));
        backupImportService.deleteStagingFile(validPreview.stagingId());
        transactionTemplate.executeWithoutResult(status -> assertThat(races.findById(ids[3]).orElseThrow().getCarPoolEntry().getId()).isEqualTo(ids[1]));
        var preview = backupImportService.stage(upload(validArchive));
        backupImportService.execute(preview.stagingId());
        transactionTemplate.executeWithoutResult(status -> {
            var season = seasons.findById(ids[0]).orElseThrow();
            assertThat(season.getCarPoolMode()).isEqualTo(org.ctc.domain.model.CarPoolMode.CATEGORY_POOL);
            assertThat(season.getCarPoolState()).isEqualTo(org.ctc.domain.model.CarPoolState.FROZEN);
            var entry = entries.findById(ids[1]).orElseThrow();
            assertThat(entry.getLabel()).isEqualTo("Historical label");
            assertThat(entry.getMembers()).singleElement().satisfies(member -> {
                assertThat(member.getId()).isEqualTo(ids[4]);
                assertThat(member.getCar().getId()).isEqualTo(ids[2]);
                assertThat(member.getCar().getCategory()).isEqualTo(org.ctc.domain.model.CarCategory.GR_4);
            });
            assertThat(entry.getClauses()).singleElement().satisfies(clause -> {
                assertThat(clause.getId()).isEqualTo(ids[5]);
                assertThat(clause.getCategory()).isEqualTo(org.ctc.domain.model.CarCategory.GR_X);
                assertThat(clause.getModelYear()).isEqualTo(9876);
                assertThat(clause.getDrivetrain()).isNull();
            });
            assertThat(races.findById(ids[3]).orElseThrow().getCarPoolEntry().getId()).isEqualTo(ids[1]);
            assertThat(pools.preview(ids[0]).entries().getFirst().carIds()).containsExactly(ids[2]);
            helper.deleteSeasonCascade(season);
            carRepository.deleteById(ids[2]);
        });
    }

    private byte[] exportArchive() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        return baos.toByteArray();
    }

    private static MockMultipartFile upload(byte[] archive) {
        return new MockMultipartFile("file", "season-pool-roundtrip-export.zip", "application/zip", archive);
    }

    private static byte[] removeEntryReference(byte[] archive, UUID raceId) throws IOException {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var out = new ByteArrayOutputStream();
        try (var in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(archive));
             var zip = new java.util.zip.ZipOutputStream(out)) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] content = in.readAllBytes();
                if (entry.getName().equals("data/races.json")) {
                    var rows = mapper.readTree(content);
                    for (var row : rows) if (row.path("id").asText().equals(raceId.toString())) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) row).remove("carPoolEntry");
                    }
                    content = mapper.writeValueAsBytes(rows);
                }
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                zip.write(content);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
