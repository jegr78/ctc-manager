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

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupTieBreakRoundTripIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-tie-break-it-");
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

    @Autowired private org.ctc.domain.repository.MatchRepository matchRepository;
    @Autowired private org.ctc.domain.repository.SeasonPhaseRepository seasonPhaseRepository;

    private UUID[] changed;

    @AfterEach
    void resetTieBreakState() {
        if (changed == null) return;
        transactionTemplate.executeWithoutResult(status -> {
            for (var phaseId : new UUID[]{changed[0], changed[3]}) {
                seasonPhaseRepository.findById(phaseId).ifPresent(phase -> { phase.setTieBreakRaceWinner(false); seasonPhaseRepository.save(phase); });
            }
            matchRepository.findById(changed[1]).ifPresent(match -> { match.setTieBreakWinner(null); matchRepository.save(match); });
            playoffMatchupRepository.findById(changed[2]).ifPresent(matchup -> { matchup.setDecidedByTieBreak(false); playoffMatchupRepository.save(matchup); });
        });
    }

    @Test
    void givenTieBreakDecisions_whenExportedAndImported_thenFlagsAndWinnersSurvive() throws Exception {
        // given
        UUID[] ids = transactionTemplate.execute(status -> {
            var match = matchRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
                    .filter(m -> !m.isBye() && m.getAwayTeam() != null && m.getWalkoverTeam() == null).findFirst().orElseThrow();
            var phase = match.getMatchday().getPhase();
            phase.setTieBreakRaceWinner(true);
            seasonPhaseRepository.save(phase);
            match.setHomeScore(40); match.setAwayScore(40); match.setTieBreakWinner(match.getAwayTeam());
            matchRepository.save(match);
            return new UUID[]{phase.getId(), match.getId(), match.getAwayTeam().getId()};
        });
        UUID[] playoffPhaseId = new UUID[1];
        UUID matchupId = mutateFirstMatchupWithTeams(matchup -> {
            var playoffPhase = matchup.getRound().getPlayoff().getPhase();
            playoffPhaseId[0] = playoffPhase.getId();
            playoffPhase.setTieBreakRaceWinner(true);
            seasonPhaseRepository.save(playoffPhase);
            matchup.setBye(false); matchup.setWalkoverTeam(null);
            matchup.setHomeScore(30); matchup.setAwayScore(30); matchup.setWinner(matchup.getTeam1()); matchup.setDecidedByTieBreak(true);
        });

        changed = new UUID[]{ids[0], ids[1], matchupId, playoffPhaseId[0]};

        // when
        runFullBackupRoundTrip();

        // then
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(seasonPhaseRepository.findById(ids[0]).orElseThrow().isTieBreakRaceWinner()).as("phase flag").isTrue();
            assertThat(matchRepository.findById(ids[1]).orElseThrow().getTieBreakWinner().getId()).as("match winner").isEqualTo(ids[2]);
            assertThat(playoffMatchupRepository.findById(matchupId).orElseThrow().isDecidedByTieBreak()).as("matchup marker").isTrue();
        });
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
