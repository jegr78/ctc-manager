package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.domain.service.RacePenaltyService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_team_penalties_backup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Tag("integration")
class RacePenaltyBackupRoundTripIT extends RacePenaltyFixture {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = SitegenTestDir.create("team-penalty-backup");
    static {
        try { Files.createDirectories(ROOT.resolve("uploads")); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("recovery").toString());
    }
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService backupImports;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.ctc.domain.service.PlayoffService playoffs;
    @Autowired org.ctc.domain.repository.PlayoffMatchupRepository matchups;
    @Autowired org.ctc.domain.repository.RaceRepository raceRows;

    private void correction(int home, int away) {
        var view = penalties.view(raceId);
        penalties.save(raceId, new RacePenaltyService.Correction(homeId, awayId, home, away, view.revision(),
                view.fingerprint(), view.generation(), "Private steward history"), "trusted-manager");
    }
    private byte[] archive() throws Exception {
        var output = new ByteArrayOutputStream(); archives.writeZip(output, Instant.now()); return output.toByteArray();
    }
    private MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "penalties.zip", "application/zip", bytes); }

    @Test
    void givenMultipleCorrections_whenExportingAndRestoring_thenRetainExactIdentityAmountsAndPrivateEvidence() throws Exception {
        // given
        correction(20, 10); correction(100, 110); var before = penalties.view(raceId);
        var ids = before.history().stream().map(row -> row.getId()).toList();
        var bytes = archive(); correction(0, 0);
        // when
        var preview = backupImports.stage(upload(bytes)); backupImports.execute(preview.stagingId());
        // then
        var restored = penalties.view(raceId);
        assertThat(restored.history()).extracting(row -> row.getId()).containsExactlyElementsOf(ids);
        assertThat(restored.history()).extracting(row -> row.getActorId()).containsOnly("trusted-manager");
        assertThat(restored.history()).extracting(row -> row.getReason()).containsOnly("Private steward history");
        assertThat(restored.history()).extracting(row -> row.getHomeGross()).containsOnly(80);
        assertThat(restored.totals()).isEqualTo(before.totals()); assertScores(80, 60);
    }

    @Test
    void givenStandalonePlayoffPenaltyHistory_whenRestoring_thenKeepNullableMatchAndOfficialPlayoffScores() throws Exception {
        // given
        var legId = tx.execute(status -> {
            var playoff = playoffs.createPlayoff(seasonId, "Test penalty archive final", 2);
            var matchup = matchups.findByRoundPlayoffId(playoff.getId()).getFirst();
            matchup.setTeam1(teams.findById(homeId).orElseThrow()); matchup.setTeam2(teams.findById(awayId).orElseThrow());
            var race = playoffs.addRaceToMatchup(matchup.getId(), null, null, null);
            lineups.saveAllAndFlush(List.of(new org.ctc.domain.model.RaceLineup(race, drivers.findById(homeDriver).orElseThrow(), teams.findById(homeId).orElseThrow()),
                    new org.ctc.domain.model.RaceLineup(race, drivers.findById(awayDriver).orElseThrow(), teams.findById(awayId).orElseThrow())));
            races.saveResults(race.getId(), results(false)); return race.getId();
        });
        var view = penalties.view(legId);
        penalties.save(legId, new RacePenaltyService.Correction(homeId, awayId, 20, 10, view.revision(), view.fingerprint(), view.generation(), "Playoff ruling"), "trusted-manager");
        var bytes = archive();
        // when
        var preview = backupImports.stage(upload(bytes)); backupImports.execute(preview.stagingId());
        // then
        var restored = penalties.view(legId);
        assertThat(restored.history()).hasSize(1); assertThat(restored.history().getFirst().getMatch()).isNull();
        tx.executeWithoutResult(status -> {
            var race = raceRows.findById(legId).orElseThrow(); assertThat(race.getMatch()).isNull();
            assertThat(race.getPlayoffMatchup().getHomeScore()).isEqualTo(70); assertThat(race.getPlayoffMatchup().getAwayScore()).isEqualTo(70);
        });
    }

    @Test
    void givenMicrosecondAuditTimeBeyond2038_whenRestoring_thenPreserveExactTimestamp() throws Exception {
        // given
        correction(20, 10);
        var time = java.sql.Timestamp.valueOf("2200-11-05 12:34:56.123456");
        jdbc.update("update race_penalty_revisions set created_at = ?, updated_at = ? where race_id = ?", time, time, raceId);
        var bytes = archive();
        // when
        var preview = backupImports.stage(upload(bytes)); backupImports.execute(preview.stagingId());
        // then
        var row = penalties.view(raceId).history().getFirst();
        assertThat(row.getCreatedAt()).isEqualTo(time.toLocalDateTime()); assertThat(row.getUpdatedAt()).isEqualTo(time.toLocalDateTime());
        assertScores(70, 70);
    }

    @ParameterizedTest
    @ValueSource(strings = {"negative", "wrong-team", "gap", "actor", "fingerprint", "legacy-history", "forged-participants"})
    void givenInvalidPenaltyArchive_whenStagingOrExecuting_thenRejectBeforeWipeAndPreserveLiveScores(String kind) throws Exception {
        // given
        correction(20, 10); var valid = archive(); var broken = mutate(valid, kind);
        clearInvocations(backupImports);
        // when / then
        assertThatThrownBy(() -> backupImports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class,
                failure -> {
                    assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH);
                    assertThat(failure.getMessage()).contains("penalty");
                });
        verify(backupImports, never()).wipeAllTables(anyMap());
        var preview = backupImports.stage(upload(valid));
        Files.write(ROOT.resolve("staging/upload-" + preview.stagingId() + ".zip"), broken);
        clearInvocations(backupImports);
        assertThatThrownBy(() -> backupImports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class)
                .cause().isInstanceOf(BackupArchiveException.class).hasMessageContaining("penalty");
        verify(backupImports, never()).wipeAllTables(anyMap());
        assertThat(penalties.view(raceId).history()).hasSize(1); assertScores(70, 70);
    }

    @ParameterizedTest
    @ValueSource(strings = {"home_penalty", "away_penalty", "revision_number", "reason", "actor_id", "result_fingerprint", "away_team_id"})
    void givenMigratedDatabase_whenInsertingInvalidRevisionFromFreshConnection_thenConstraintsRejectIt(String field) {
        // given
        correction(20, 10);
        var columns = List.of("id", "race_id", "season_id", "phase_id", "matchday_id", "match_id", "playoff_matchup_id", "home_team_id", "away_team_id",
                "revision_number", "home_penalty", "away_penalty", "home_gross", "away_gross", "reason", "actor_id", "result_fingerprint", "created_at", "updated_at");
        var values = columns.stream().map(column -> {
            if (column.equals("id")) return "?";
            if (column.equals(field)) return switch (field) {
                case "revision_number" -> "0";
                case "reason", "actor_id" -> "''";
                case "result_fingerprint" -> "'short'";
                case "away_team_id" -> "home_team_id";
                default -> "-1";
            };
            return column.equals("revision_number") ? "2" : column;
        }).toList();
        String statement = "insert into race_penalty_revisions (" + String.join(",", columns) + ") select "
                + String.join(",", values) + " from race_penalty_revisions where race_id = ?";
        // when / then
        assertThatThrownBy(() -> jdbc.update(statement, UUID.randomUUID(), raceId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(penalties.view(raceId).history()).hasSize(1); assertScores(70, 70);
    }

    @Test
    void givenLegacyArchiveWithoutPenaltyFile_whenRestoring_thenRetainUnadjustedScores() throws Exception {
        // given
        var bytes = mutate(archive(), "legacy-empty");
        // when
        var preview = backupImports.stage(upload(bytes)); backupImports.execute(preview.stagingId());
        // then
        assertThat(penalties.view(raceId).history()).isEmpty(); assertScores(80, 70);
    }

    private byte[] mutate(byte[] original, String kind) throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(original))) {
            ZipEntry entry; while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes());
        }
        var file = "data/race-penalty-revisions.json";
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        if (kind.startsWith("legacy")) manifest.put("schema_version", 12);
        if (kind.equals("legacy-empty")) {
            files.remove(file); ((ObjectNode) manifest.get("table_counts")).put("race_penalty_revisions", 0);
        } else {
            var rows = (ArrayNode) JSON.readTree(files.get(file)); var row = (ObjectNode) rows.get(0);
            switch (kind) {
                case "negative" -> row.put("homePenalty", -1);
                case "wrong-team" -> row.put("homeTeam", awayId.toString());
                case "forged-participants" -> {
                    var teamRows = (ArrayNode) JSON.readTree(files.get("data/teams.json"));
                    String stranger = java.util.stream.StreamSupport.stream(teamRows.spliterator(), false).map(team -> team.path("id").asText())
                            .filter(id -> !id.equals(homeId.toString()) && !id.equals(awayId.toString())).findFirst().orElseThrow();
                    row.put("homeTeam", stranger);
                    var raceRows = (ArrayNode) JSON.readTree(files.get("data/races.json"));
                    for (var race : raceRows) if (race.path("id").asText().equals(raceId.toString())) ((ObjectNode) race).put("homeTeamOverride", stranger);
                    files.put("data/races.json", JSON.writeValueAsBytes(raceRows));
                }
                case "gap" -> row.put("revisionNumber", 2);
                case "actor" -> row.put("actorId", " ");
                case "fingerprint" -> row.put("resultFingerprint", "z".repeat(64));
                default -> { }
            }
            files.put(file, JSON.writeValueAsBytes(rows));
        }
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var fileEntry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(fileEntry.getKey())); zip.write(fileEntry.getValue()); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
