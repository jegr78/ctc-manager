package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.ctc.admin.TestDataService;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exports a playoff matchup decided as a bye or a walkover and checks that the outcome survives
 * the backup import.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupPlayoffOutcomeRoundTripIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-playoff-outcome-it-");
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
    @Autowired private PlayoffMatchupRepository playoffMatchupRepository;
    @Autowired private TransactionTemplate transactionTemplate;

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
    void givenMatchupDecidedAsABye_whenExportedAndImported_thenTheByeSurvives() throws Exception {
        // given
        UUID matchupId = mutateFirstMatchupWithTeams(matchup -> matchup.setBye(true));

        // when
        runFullBackupRoundTrip();

        // then
        assertThat(playoffMatchupRepository.findById(matchupId).orElseThrow().isBye()).as("bye after restore").isTrue();
    }

    @Test
    void givenMatchupDecidedByWalkover_whenExportedAndImported_thenTheForfeitingTeamSurvives() throws Exception {
        // given
        UUID[] forfeiter = new UUID[1];
        UUID matchupId = mutateFirstMatchupWithTeams(matchup -> {
            matchup.setWalkoverTeam(matchup.getTeam2());
            forfeiter[0] = matchup.getTeam2().getId();
        });

        // when
        runFullBackupRoundTrip();

        // then
        var restored = transactionTemplate.execute(status -> {
            var walkoverTeam = playoffMatchupRepository.findById(matchupId).orElseThrow().getWalkoverTeam();
            return walkoverTeam == null ? null : walkoverTeam.getId();
        });
        assertThat(restored).as("forfeiting team after restore").isEqualTo(forfeiter[0]);
    }

    private UUID mutateFirstMatchupWithTeams(java.util.function.Consumer<PlayoffMatchup> mutator) {
        return transactionTemplate.execute(status -> {
            PlayoffMatchup matchup = playoffMatchupRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
                    .filter(PlayoffMatchup::isReady)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the seed has no playoff matchup with two teams"));
            mutator.accept(matchup);
            playoffMatchupRepository.save(matchup);
            return matchup.getId();
        });
    }

    private void runFullBackupRoundTrip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        MockMultipartFile file = new MockMultipartFile(
                "file", "playoff-outcome-roundtrip-export.zip", "application/zip", baos.toByteArray());
        var preview = backupImportService.stage(file);
        backupImportService.execute(preview.stagingId());
    }
}
