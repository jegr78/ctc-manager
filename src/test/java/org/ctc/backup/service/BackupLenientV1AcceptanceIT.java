package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-17 acceptance IT for the lenient schema_version gate (D-10, D-11).
 *
 * <p>Builds synthetic backup ZIPs programmatically (per RESEARCH §RQ-9 — no binary
 * fixtures committed) and asserts:
 * <ul>
 *   <li>v1 manifest (24 pre-v1.13 entities, no Discord JSON entries) — stage succeeds,
 *       {@code preview.schemaMatches() == true}, execute leaves Discord tables empty
 *       and surfaces the {@code DiscordGlobalConfigService.getOrInitialize()} self-heal
 *       contract as the only path that introduces a row.</li>
 *   <li>v3 (and higher) manifests — refused with {@code SCHEMA_MISMATCH}.</li>
 *   <li>v0 (and lower) manifests — refused with {@code SCHEMA_MISMATCH}.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupLenientV1AcceptanceIT {

    private static final List<String> V1_TABLES_24 = List.of(
            "cars", "tracks", "race_scorings", "match_scorings",
            "drivers", "psn_aliases", "teams", "seasons",
            "season_phases", "season_phase_groups", "phase_teams", "season_teams",
            "season_drivers", "playoffs", "playoff_rounds", "playoff_matchups",
            "playoff_seeds", "matchdays", "matches", "races",
            "race_lineups", "race_results", "race_settings", "race_attachments");

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-lenient-it-");
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
    private BackupImportService backupImportService;

    @Autowired
    private DiscordGlobalConfigRepository discordGlobalConfigRepository;

    @Autowired
    private DiscordPostRepository discordPostRepository;

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupObjectMapper;

    @Autowired
    private org.ctc.backup.schema.BackupSchema backupSchema;

    private Path tempZip;

    @AfterEach
    void cleanUp() throws IOException {
        if (tempZip != null) {
            Files.deleteIfExists(tempZip);
        }
    }

    @Test
    void givenV1Tables24_thenEveryNameMatchesAJpaEntityTable() {
        // given — non-vacuous gate proof: every V1_TABLES_24 name must resolve to a real
        // @Table-mapped entity in the schema's export-order. A singular name like
        // "race_scoring" would never appear here because no @Table uses that form.
        Set<String> schemaTableNames = backupSchema.getExportOrder().stream()
                .map(org.ctc.backup.schema.EntityRef::tableName)
                .collect(java.util.stream.Collectors.toSet());

        // then
        assertThat(schemaTableNames)
                .as("V1_TABLES_24 lists @Table(name=...) values; mismatch means the test never exercises restore")
                .containsAll(V1_TABLES_24);
    }

    @Test
    void givenV1ManifestZip_whenStage_thenAcceptedAndSchemaMatchesIsTrue() throws Exception {
        // given
        MockMultipartFile file = wrapAsMultipart(buildSyntheticZip(1, V1_TABLES_24));

        // when
        BackupImportPreview preview = backupImportService.stage(file);

        // then
        assertThat(preview.schemaVersion()).isEqualTo(1);
        assertThat(preview.currentSchemaVersion()).isEqualTo(2);
        assertThat(preview.schemaMatches())
                .as("v1 backup must be accepted by SUPPORTED_SCHEMA_VERSIONS contains check")
                .isTrue();
    }

    @Test
    void givenV1ManifestZip_whenExecute_thenDiscordTablesStayEmpty() throws Exception {
        // given — v1 ZIP has no data/discord-global-config.json or data/discord-post.json entries
        MockMultipartFile file = wrapAsMultipart(buildSyntheticZip(1, V1_TABLES_24));
        BackupImportPreview preview = backupImportService.stage(file);

        // when
        backupImportService.execute(preview.stagingId());

        // then — both Discord tables are empty after the v1 wipe-and-restore.
        // The DiscordGlobalConfigService.getOrInitialize() self-heal is on a separate
        // first-page-load pathway and does not run inside execute().
        assertThat(discordGlobalConfigRepository.count())
                .as("v1 manifest lists no discord_global_config — wipe leaves it empty")
                .isZero();
        assertThat(discordPostRepository.count())
                .as("v1 manifest lists no discord_post — wipe leaves it empty")
                .isZero();
    }

    @Test
    void givenV3ManifestZip_whenStage_thenRefusedWithSchemaMismatch() throws Exception {
        // given
        MockMultipartFile file = wrapAsMultipart(buildSyntheticZip(3, V1_TABLES_24));

        // when / then
        assertThatThrownBy(() -> backupImportService.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void givenV0ManifestZip_whenStage_thenRefusedWithSchemaMismatch() throws Exception {
        // given
        MockMultipartFile file = wrapAsMultipart(buildSyntheticZip(0, V1_TABLES_24));

        // when / then
        assertThatThrownBy(() -> backupImportService.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void givenV2ManifestZipBuiltLikeV1_whenStage_thenSchemaMatchesIsTrue() throws Exception {
        // given — sanity check the upper accepted bound
        MockMultipartFile file = wrapAsMultipart(buildSyntheticZip(2, V1_TABLES_24));

        // when / then
        assertThatCode(() -> {
            BackupImportPreview preview = backupImportService.stage(file);
            assertThat(preview.schemaMatches()).isTrue();
        }).doesNotThrowAnyException();
    }

    private Path buildSyntheticZip(int schemaVersion, List<String> tables) throws IOException {
        Map<String, Long> tableCounts = new LinkedHashMap<>();
        for (String table : tables) {
            tableCounts.put(table, 0L);
        }
        BackupManifest manifest = new BackupManifest(
                schemaVersion, "1.13.0-test", Instant.parse("2026-05-26T10:00:00Z"), tableCounts);

        tempZip = Files.createTempFile("ctc-synthetic-v" + schemaVersion + "-", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            zos.write(backupObjectMapper.writeValueAsBytes(manifest));
            zos.closeEntry();

            for (String table : tables) {
                ZipEntry dataEntry = new ZipEntry("data/" + table.replace('_', '-') + ".json");
                zos.putNextEntry(dataEntry);
                zos.write("[]".getBytes());
                zos.closeEntry();
            }
        }
        return tempZip;
    }

    private MockMultipartFile wrapAsMultipart(Path zipPath) throws IOException {
        return new MockMultipartFile(
                "file",
                "synthetic-backup.zip",
                "application/zip",
                Files.readAllBytes(zipPath));
    }
}
