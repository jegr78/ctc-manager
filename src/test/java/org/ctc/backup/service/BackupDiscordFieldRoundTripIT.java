package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.ctc.admin.TestDataService;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-15 regression fence — 13 per-field round-trip assertions on the V8-V15 columns
 * carried forward by the existing-entity Restorers (MatchRestorer +8, TeamRestorer +1,
 * MatchdayRestorer +2, SeasonRestorer +2 = 13 total).
 *
 * <p>Each test seeds a deterministic non-null sample value on one V8-V15 field of a
 * fixture row, exports the DB to an in-memory ZIP via {@code BackupArchiveService}, stages
 * + executes the import (wipe + restore), and asserts the field round-trips byte-equal
 * (string equality or {@code LocalDateTime} equality).
 *
 * <p>A future Restorer rewrite that drops a column will turn the corresponding test red.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupDiscordFieldRoundTripIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-discord-field-it-");
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
    @Autowired private MatchRepository matchRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchdayRepository matchdayRepository;
    @Autowired private SeasonRepository seasonRepository;

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupObjectMapper;

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
    void givenMatchWithDiscordChannelId_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID matchId = mutateFirstMatch(m -> m.setDiscordChannelId("T-channel-101"));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getDiscordChannelId())
                .isEqualTo("T-channel-101");
    }

    @Test
    void givenMatchWithDiscordChannelWebhookUrl_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID matchId = mutateFirstMatch(m ->
                m.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/T-101/abc"));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getDiscordChannelWebhookUrl())
                .isEqualTo("https://discord.com/api/webhooks/T-101/abc");
    }

    @Test
    void givenMatchWithDiscordTeaser_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID matchId = mutateFirstMatch(m -> m.setDiscordTeaser("T-teaser-101"));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getDiscordTeaser())
                .isEqualTo("T-teaser-101");
    }

    @Test
    void givenMatchWithStreamLink_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID matchId = mutateFirstMatch(m -> m.setStreamLink("https://twitch.tv/T-101"));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getStreamLink())
                .isEqualTo("https://twitch.tv/T-101");
    }

    @Test
    void givenMatchWithLobbyHost_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID matchId = mutateFirstMatch(m -> m.setLobbyHost("T-host-101"));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getLobbyHost())
                .isEqualTo("T-host-101");
    }

    @Test
    void givenMatchWithRaceDirector_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID matchId = mutateFirstMatch(m -> m.setRaceDirector("T-director-101"));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getRaceDirector())
                .isEqualTo("T-director-101");
    }

    @Test
    void givenMatchWithStreamer_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID matchId = mutateFirstMatch(m -> m.setStreamer("T-streamer-101"));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getStreamer())
                .isEqualTo("T-streamer-101");
    }

    @Test
    void givenMatchWithDiscordChannelArchivedAt_whenExportAndImport_thenValueSurvives() throws Exception {
        LocalDateTime ts = LocalDateTime.parse("2026-05-25T10:00:00");
        UUID matchId = mutateFirstMatch(m -> m.setDiscordChannelArchivedAt(ts));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getDiscordChannelArchivedAt())
                .isEqualTo(ts);
    }

    @Test
    void givenTeamWithDiscordRoleId_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID teamId = mutateFirstRootTeam(t -> t.setDiscordRoleId("T-role-101"));
        runFullBackupRoundTrip();
        assertThat(teamRepository.findById(teamId).orElseThrow().getDiscordRoleId())
                .isEqualTo("T-role-101");
    }

    @Test
    void givenMatchdayWithPickDeadline_whenExportAndImport_thenValueSurvives() throws Exception {
        LocalDateTime ts = LocalDateTime.parse("2026-05-30T19:00:00");
        UUID matchdayId = mutateFirstMatchday(md -> md.setPickDeadline(ts));
        runFullBackupRoundTrip();
        assertThat(matchdayRepository.findById(matchdayId).orElseThrow().getPickDeadline())
                .isEqualTo(ts);
    }

    @Test
    void givenMatchdayWithScheduledWeekend_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID matchdayId = mutateFirstMatchday(md -> md.setScheduledWeekend("T-30-May-1-June"));
        runFullBackupRoundTrip();
        assertThat(matchdayRepository.findById(matchdayId).orElseThrow().getScheduledWeekend())
                .isEqualTo("T-30-May-1-June");
    }

    @Test
    void givenSeasonWithDiscordRaceResultsThreadId_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID seasonId = mutateFirstSeason(s -> s.setDiscordRaceResultsThreadId("T-rr-thread-101"));
        runFullBackupRoundTrip();
        assertThat(seasonRepository.findById(seasonId).orElseThrow().getDiscordRaceResultsThreadId())
                .isEqualTo("T-rr-thread-101");
    }

    @Test
    void givenSeasonWithDiscordStandingsThreadId_whenExportAndImport_thenValueSurvives() throws Exception {
        UUID seasonId = mutateFirstSeason(s -> s.setDiscordStandingsThreadId("T-st-thread-101"));
        runFullBackupRoundTrip();
        assertThat(seasonRepository.findById(seasonId).orElseThrow().getDiscordStandingsThreadId())
                .isEqualTo("T-st-thread-101");
    }

    @Test
    void givenMatchWithDiscordChannelIdNull_whenExportAndImport_thenValueRemainsNull() throws Exception {
        UUID matchId = mutateFirstMatch(m -> m.setDiscordChannelId(null));
        runFullBackupRoundTrip();
        assertThat(matchRepository.findById(matchId).orElseThrow().getDiscordChannelId())
                .isNull();
    }

    private UUID mutateFirstMatch(java.util.function.Consumer<Match> mutator) {
        Match m = matchRepository.findAll(Sort.by(Sort.Order.asc("id"))).get(0);
        mutator.accept(m);
        matchRepository.save(m);
        return m.getId();
    }

    private UUID mutateFirstRootTeam(java.util.function.Consumer<Team> mutator) {
        Team t = teamRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
                .filter(team -> team.getParentTeam() == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No root team found in dev fixture"));
        mutator.accept(t);
        teamRepository.save(t);
        return t.getId();
    }

    private UUID mutateFirstMatchday(java.util.function.Consumer<Matchday> mutator) {
        Matchday md = matchdayRepository.findAll(Sort.by(Sort.Order.asc("id"))).get(0);
        mutator.accept(md);
        matchdayRepository.save(md);
        return md.getId();
    }

    private UUID mutateFirstSeason(java.util.function.Consumer<Season> mutator) {
        Season s = seasonRepository.findAll(Sort.by(Sort.Order.asc("id"))).get(0);
        mutator.accept(s);
        seasonRepository.save(s);
        return s.getId();
    }

    private void runFullBackupRoundTrip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        MockMultipartFile file = new MockMultipartFile(
                "file", "discord-field-roundtrip-export.zip", "application/zip", baos.toByteArray());
        BackupImportPreview preview = backupImportService.stage(file);
        backupImportService.execute(preview.stagingId());
    }
}
