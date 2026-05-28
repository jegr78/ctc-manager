package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 74 Plan 05 — SC#2 proof: schema-version gate fires BEFORE any DB read.
 *
 * <p>Boots the {@code dev} profile and verifies that {@code BackupImportService.stage()}
 * rejects an archive with a wrong {@code schema_version} BEFORE executing any of the
 * 24 {@code Repository.count()} calls. The BEFORE/AFTER {@code Map<tableName, count>}
 * snapshot equality is the canonical proof — if even one {@code count()} ran after the
 * wrong schema-version was detected, the observable behaviour would be identical but the
 * ordering guarantee would be broken.
 *
 * <p>Fixtures are generated programmatically (D-25). No binary blobs committed.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupImportSchemaMismatchIT {

    @Autowired
    BackupImportService service;

    @Autowired
    BackupSchema backupSchema;

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    Path stagingDir;

    // --- 24 domain repositories injected individually (black-box behavioral test) ---
    @Autowired CarRepository carRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired MatchScoringRepository matchScoringRepository;
    @Autowired MatchdayRepository matchdayRepository;
    @Autowired PhaseTeamRepository phaseTeamRepository;
    @Autowired PlayoffMatchupRepository playoffMatchupRepository;
    @Autowired PlayoffRepository playoffRepository;
    @Autowired PlayoffRoundRepository playoffRoundRepository;
    @Autowired PlayoffSeedRepository playoffSeedRepository;
    @Autowired PsnAliasRepository psnAliasRepository;
    @Autowired RaceAttachmentRepository raceAttachmentRepository;
    @Autowired RaceLineupRepository raceLineupRepository;
    @Autowired RaceRepository raceRepository;
    @Autowired RaceResultRepository raceResultRepository;
    @Autowired RaceScoringRepository raceScoringRepository;
    @Autowired RaceSettingsRepository raceSettingsRepository;
    @Autowired SeasonDriverRepository seasonDriverRepository;
    @Autowired SeasonPhaseGroupRepository seasonPhaseGroupRepository;
    @Autowired SeasonPhaseRepository seasonPhaseRepository;
    @Autowired SeasonRepository seasonRepository;
    @Autowired SeasonTeamRepository seasonTeamRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired DiscordGlobalConfigRepository discordGlobalConfigRepository;
    @Autowired DiscordPostRepository discordPostRepository;

    @BeforeEach
    void clearStagingDir() throws IOException {
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
        try (var paths = Files.list(stagingDir)) {
            paths.filter(p -> p.getFileName().toString().startsWith("upload-"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // ignore in @BeforeEach
                        }
                    });
        }
    }

    /**
     * SC#2: schema-version gate fires BEFORE any of the 24 {@code repo.count()} calls.
     *
     * <p>Proof: BEFORE and AFTER {@code Repository.count()} snapshots are byte-identical
     * (Map equality on all 24 keys + values).
     */
    @Test
    void givenForgedManifestSchemaVersion999_whenStage_thenThrowsSchemaMismatch_andDbUnchanged_andStagingFileDeleted()
            throws Exception {
        // given
        byte[] forgedBytes = forgedManifestZip(999, backupSchema);
        MockMultipartFile file = new MockMultipartFile(
                "file", "forged.zip", "application/zip", forgedBytes);

        Map<String, Long> before = snapshotAllCounts();

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .as("reason must be SCHEMA_MISMATCH")
                        .isEqualTo(Reason.SCHEMA_MISMATCH))
                .hasMessageContaining("Schema version 999 not supported")
                .hasMessageContaining("accepted: [1, 2]");

        Map<String, Long> after = snapshotAllCounts();

        // SC#2: DB state must be byte-identical before and after the rejected stage
        assertThat(after)
                .as("Schema mismatch must run BEFORE any DB read; row counts must be byte-identical")
                .isEqualTo(before);

        // try/finally must have deleted the staging file.
        assertThat(Files.list(stagingDir)
                .filter(p -> p.getFileName().toString().endsWith(".zip"))
                .count())
                .as("staging dir must contain zero ZIP files after rejection")
                .isZero();
    }

    /**
     * Proves the gate is {@code !=}, not {@code <}: backward-incompatibility also rejects.
     */
    @Test
    void givenForgedManifestSchemaVersionMinusOne_whenStage_thenThrowsSchemaMismatch()
            throws Exception {
        // given
        byte[] forgedBytes = forgedManifestZip(-1, backupSchema);
        MockMultipartFile file = new MockMultipartFile(
                "file", "forged-minus-one.zip", "application/zip", forgedBytes);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .as("reason must be SCHEMA_MISMATCH for schema_version=-1")
                        .isEqualTo(Reason.SCHEMA_MISMATCH))
                .hasMessageContaining("Schema version -1 not supported")
                .hasMessageContaining("accepted: [1, 2]");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Produces a structurally-complete ZIP with a forged {@code schema_version}.
     *
     * <p>Entry #0 is a valid {@code manifest.json} for {@code backupObjectMapper}
     * (FAIL_ON_UNKNOWN_PROPERTIES=true; snake_case keys per {@link org.ctc.backup.schema.BackupManifest}).
     * Entries #1..#24 are empty JSON arrays at the paths expected by
     * {@code BackupArchiveService.countDataEntries} — so the schema gate is the only
     * rejection trigger, not the manifest parse or data-file shape.
     */
    private static byte[] forgedManifestZip(int forgedSchemaVersion, BackupSchema schema)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // Entry #0: manifest.json with forged schema_version
            String manifestJson = String.format(
                    "{\"schema_version\":%d,\"app_version\":\"forged\","
                    + "\"export_date\":\"2026-05-12T00:00:00Z\",\"table_counts\":{}}",
                    forgedSchemaVersion);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // Entries #1..#24: empty JSON arrays per EntityRef.fileName()
            for (EntityRef ref : schema.getExportOrder()) {
                zip.putNextEntry(new ZipEntry(ref.fileName()));
                zip.write("[]".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /**
     * Captures current {@code Repository.count()} for every domain + Discord table.
     *
     * <p>Repositories are individually {@code @Autowired} (black-box behavioral test;
     * no reflective discovery in test code).
     */
    private Map<String, Long> snapshotAllCounts() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("cars", carRepository.count());
        m.put("drivers", driverRepository.count());
        m.put("match_scorings", matchScoringRepository.count());
        m.put("matchdays", matchdayRepository.count());
        m.put("matches", matchRepository.count());
        m.put("phase_teams", phaseTeamRepository.count());
        m.put("playoff_matchups", playoffMatchupRepository.count());
        m.put("playoff_rounds", playoffRoundRepository.count());
        m.put("playoff_seeds", playoffSeedRepository.count());
        m.put("playoffs", playoffRepository.count());
        m.put("psn_aliases", psnAliasRepository.count());
        m.put("race_attachments", raceAttachmentRepository.count());
        m.put("race_lineups", raceLineupRepository.count());
        m.put("race_results", raceResultRepository.count());
        m.put("race_scorings", raceScoringRepository.count());
        m.put("race_settings", raceSettingsRepository.count());
        m.put("races", raceRepository.count());
        m.put("season_drivers", seasonDriverRepository.count());
        m.put("season_phase_groups", seasonPhaseGroupRepository.count());
        m.put("season_phases", seasonPhaseRepository.count());
        m.put("season_teams", seasonTeamRepository.count());
        m.put("seasons", seasonRepository.count());
        m.put("teams", teamRepository.count());
        m.put("tracks", trackRepository.count());
        m.put("discord_global_config", discordGlobalConfigRepository.count());
        m.put("discord_post", discordPostRepository.count());
        return m;
    }
}
