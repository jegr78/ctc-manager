package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import org.ctc.admin.TestDataService;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression IT: a {@code season_phases} row with NULL {@code race_scoring_id} /
 * {@code match_scoring_id} (a legitimate state — the columns are nullable and
 * {@code SeasonPhaseService.updatePhase} sets them null when no scoring id is supplied)
 * must survive a full export → wipe → import round-trip.
 *
 * <p>Before the {@code SeasonPhaseRestorer} null-guard fix, the import crashed with
 * {@code java.lang.IllegalArgumentException: Invalid UUID string: null} inside
 * {@code SeasonPhaseRestorer.restore} because it called {@code UUID.fromString("null")}.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupImportNullScoringFkRoundTripIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-null-scoring-roundtrip-it-");
            IMPORT_BACKUPS_ROOT.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate import-backups tempdir", e);
        }
    }

    @DynamicPropertySource
    static void overrideImportBackupsDir(DynamicPropertyRegistry registry) {
        registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
    }

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private BackupArchiveService backupArchiveService;

    @Autowired
    private BackupImportService backupImportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.backup.staging-dir}")
    private String stagingDirRaw;

    @BeforeEach
    void seedFixture() throws IOException {
        testDataService.seed();
        Files.createDirectories(Paths.get(stagingDirRaw).toAbsolutePath().normalize());
    }

    @Test
    void givenPhaseWithNullScoringFks_whenExportWipeImport_thenImportSucceedsAndNullsRoundTrip()
            throws Exception {
        // given — force one phase into the legitimate null-scoring state
        UUID phaseId = jdbcTemplate.queryForObject(
                "SELECT id FROM season_phases ORDER BY id LIMIT 1", UUID.class);
        jdbcTemplate.update(
                "UPDATE season_phases SET race_scoring_id = NULL, match_scoring_id = NULL WHERE id = ?",
                phaseId);

        // when — full round-trip: export → stage → execute (wipe + restore)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        backupArchiveService.writeZip(out, Instant.now());
        MockMultipartFile file = new MockMultipartFile(
                "file", "null-scoring-export.zip", "application/zip", out.toByteArray());
        BackupImportPreview preview = backupImportService.stage(file);
        BackupImportResult result = backupImportService.execute(preview.stagingId());

        // then — import must NOT crash on the null FK
        assertThat(result).as("import of a null-scoring phase must succeed").isNotNull();

        // then — the null scoring FKs round-trip as null (not resurrected, not defaulted)
        Integer nullScoringCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM season_phases WHERE id = ? "
                        + "AND race_scoring_id IS NULL AND match_scoring_id IS NULL",
                Integer.class, phaseId);
        assertThat(nullScoringCount)
                .as("phase %s must still have null race/match scoring after round-trip", phaseId)
                .isEqualTo(1);
    }
}
