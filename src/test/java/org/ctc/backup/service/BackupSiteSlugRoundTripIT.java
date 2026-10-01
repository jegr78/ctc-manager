package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.ctc.admin.TestDataService;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SiteSlugRepository;
import org.ctc.domain.repository.TeamRepository;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * Exports stored profile slugs, including a reservation without an entity, and checks that they
 * survive the backup import.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupSiteSlugRoundTripIT {

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
    @Autowired private SiteSlugRepository siteSlugRepository;
    @Autowired private TeamRepository teamRepository;

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
    void givenStoredSlugsAndAReservation_whenExportedAndImported_thenTheyAllSurvive() throws Exception {
        // given
        String id = UUID.randomUUID().toString().substring(0, 8);
        UUID teamId = teamRepository.save(new Team("Test Slug Backup " + id, "Test_SB_" + id)).getId();
        siteSlugRepository.save(new SiteSlug(SiteSlugKind.TEAM, "test-owned-" + id, "test-owned-" + id, teamId));
        siteSlugRepository.save(new SiteSlug(SiteSlugKind.DRIVER, "test-shared-" + id, "test-shared-" + id, null));

        // when
        runFullBackupRoundTrip();

        // then
        assertThat(siteSlugRepository.findAll())
                .as("slugs after restore")
                .extracting(SiteSlug::getKind, SiteSlug::getSlug, SiteSlug::getBaseSlug, SiteSlug::getEntityId)
                .contains(tuple(SiteSlugKind.TEAM, "test-owned-" + id, "test-owned-" + id, teamId),
                        tuple(SiteSlugKind.DRIVER, "test-shared-" + id, "test-shared-" + id, null));
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
