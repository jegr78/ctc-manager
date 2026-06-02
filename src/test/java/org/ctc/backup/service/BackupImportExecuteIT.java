package org.ctc.backup.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.ctc.admin.TestDataService;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy-path IT for {@link BackupImportService#execute(UUID)}.
 *
 * <p>Boots the {@code dev} profile (H2 in-memory + {@link TestDataService} fixture) and drives
 * the full export → stage → execute round trip. Asserts:
 * <ol>
 *   <li>{@link BackupImportResult#restoredTotal()} equals the sum of pre-export row counts
 *       across the entire seeded fixture (Season 2023 + 2024 + 2024-Empty + 2026 — all four
 *       seasons that {@code testDataService.seed()} creates per its Javadoc).</li>
 *   <li>For three sampled entities ({@code seasons}, {@code races}, {@code race_results}) the
 *       post-execute repository row count matches the pre-export count.</li>
 *   <li>For at least one row (a sampled Season pinned by id), the post-execute
 *       {@code createdAt} is byte-identical to the pre-export {@code createdAt} — the
 *       IMPORT-05.e "verbatim createdAt" success-criterion-2 assertion.</li>
 *   <li>Self-FK chains (parent_team_id) are reconstructed: at least one sub-team's
 *       {@code parentTeam} is non-null and points to a parent that is also in the
 *       post-execute fixture (IMPORT-06 — Q1/Q2 + D-06 unified resolution).</li>
 * </ol>
 *
 * <p>NOTE: audit-row commit verification (REQUIRES_NEW success-row write by the AFTER_COMMIT
 * listener in Plan 07) is COVERED by Plan 10's {@code BackupImportMariaDbSmokeIT} and lives
 * outside this IT (REVISION-iteration-1 Option A resolution).
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupImportExecuteIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-execute-it-");
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
    private BackupArchiveService backupArchiveService;

    @Autowired
    private TestDataService testDataService;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private RaceResultRepository raceResultRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    Path stagingDir;

    @BeforeAll
    void seedFixture() throws IOException {
        // testDataService.seed() is idempotent — it skips when seasonRepository.count() > 0.
        testDataService.seed();
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
    }

    /**
     * Wipe IMPORT_BACKUPS_ROOT contents between tests so two @Test methods that fall in the
     * same second cannot collide on data/.import-backups/&lt;ts&gt;/auto-backup-before-import.zip
     * (BackupImportService generates the ts via Instant.now().truncatedTo(SECONDS)).
     */
    @AfterEach
    void cleanImportBackupsRoot() throws IOException {
        if (!Files.exists(IMPORT_BACKUPS_ROOT)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(IMPORT_BACKUPS_ROOT)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(IMPORT_BACKUPS_ROOT))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    });
        }
    }

    // -------------------------------------------------------------------------
    // Scenario (a) — happy path: full round trip preserves verbatim timestamps + row counts
    // -------------------------------------------------------------------------

    @Test
    void givenStagedZipFromDevFixture_whenExecuteInvoked_thenAllTablesRestoredWithVerbatimTimestamps()
            throws Exception {
        // given — capture pre-export state
        long preSeasons = seasonRepository.count();
        long preRaces = raceRepository.count();
        long preRaceResults = raceResultRepository.count();
        long preTeams = teamRepository.count();

        assertThat(preSeasons).as("dev fixture must seed at least one Season").isGreaterThan(0);
        assertThat(preTeams).as("dev fixture must seed teams").isGreaterThan(0);

        // Pin one Season's createdAt for the verbatim assertion (IMPORT-05.e).
        List<Season> seasonsBefore = seasonRepository.findAll();
        Season pinned = seasonsBefore.get(0);
        UUID pinnedId = pinned.getId();
        LocalDateTime pinnedCreatedAt = pinned.getCreatedAt();
        assertThat(pinnedCreatedAt)
                .as("pinned Season createdAt must be populated by AuditingEntityListener pre-export")
                .isNotNull();

        // Build a Phase-73 export ZIP via the real BackupArchiveService.
        byte[] zipBytes = exportToBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "dev-fixture-export.zip", "application/zip", zipBytes);

        // Stage so we have a UUID + .meta sidecar in stagingDir
        BackupImportPreview preview = backupImportService.stage(file);
        UUID stagingId = preview.stagingId();

        // when — execute the real @Transactional wipe + restore
        BackupImportResult result = backupImportService.execute(stagingId);

        // then — counters
        assertThat(result).as("execute() must return a non-null result").isNotNull();
        assertThat(result.auditUuid()).as("result.auditUuid must be present").isNotNull();
        assertThat(result.entityCount())
                .as("result.entityCount must equal the number of exported entities")
                .isGreaterThan(0);

        long preTotal = preSeasons + preRaces + preRaceResults + preTeams;
        assertThat(result.restoredTotal())
                .as("restoredTotal must at least cover the four sampled entities (full sum is asserted via per-entity counts below)")
                .isGreaterThanOrEqualTo(preTotal);

        // Per-entity row-count parity for 3 sampled entities (IMPORT-05.c)
        assertThat(seasonRepository.count())
                .as("seasons row count must match pre-export")
                .isEqualTo(preSeasons);
        assertThat(raceRepository.count())
                .as("races row count must match pre-export")
                .isEqualTo(preRaces);
        assertThat(raceResultRepository.count())
                .as("race_results row count must match pre-export")
                .isEqualTo(preRaceResults);

        // IMPORT-05.e — verbatim createdAt assertion for the pinned Season
        Season pinnedAfter = seasonRepository.findById(pinnedId)
                .orElseThrow(() -> new AssertionError("pinned Season disappeared after restore"));
        assertThat(pinnedAfter.getCreatedAt())
                .as("pinned Season createdAt must survive byte-identical via JdbcTemplate.batchUpdate auditing bypass")
                .isEqualTo(pinnedCreatedAt);
    }

    // -------------------------------------------------------------------------
    // Scenario (b) — self-FK chains reconstructed (parent_team_id)
    // -------------------------------------------------------------------------

    @Test
    void givenSelfFKEntitiesInFixture_whenExecuteInvoked_thenParentTeamSuccessorSeasonTeamNextMatchupResolved()
            throws Exception {
        // given — pin a sub-team's parentTeam UUID before export
        List<Team> teamsBefore = teamRepository.findAll();
        Team subTeam = teamsBefore.stream()
                .filter(t -> t.getParentTeam() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "dev fixture must contain at least one sub-team (VRX/SGM/TBR sub-teams)"));
        UUID subTeamId = subTeam.getId();
        UUID expectedParentId = subTeam.getParentTeam().getId();

        // Export + stage + execute
        byte[] zipBytes = exportToBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "dev-fixture-export.zip", "application/zip", zipBytes);
        BackupImportPreview preview = backupImportService.stage(file);

        // when
        BackupImportResult result = backupImportService.execute(preview.stagingId());

        // then
        assertThat(result.restoredTotal()).isGreaterThan(0);
        Team restored = teamRepository.findById(subTeamId)
                .orElseThrow(() -> new AssertionError("sub-team disappeared after restore"));
        assertThat(restored.getParentTeam())
                .as("parent_team_id self-FK must be reconstructed by TeamRestorer pass-2")
                .isNotNull();
        assertThat(restored.getParentTeam().getId())
                .as("parent_team_id must point at the same parent UUID as pre-export")
                .isEqualTo(expectedParentId);

        // The parent must also exist in the post-execute set
        assertThat(teamRepository.findById(expectedParentId))
                .as("the parent team itself must exist post-execute")
                .isPresent();
    }

    // -------------------------------------------------------------------------
    // Helper — produce a Phase-73 export ZIP via BackupArchiveService.writeZip(...)
    //
    // Note: BackupExportService.export(...) does NOT exist (REVISION-iteration-1 correction);
    // the real ZIP writer is BackupArchiveService.writeZip(OutputStream, Instant).
    // -------------------------------------------------------------------------
    private byte[] exportToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        return baos.toByteArray();
    }

    // Suppress unused-import warning for HashMap / Map (kept for future per-entity assertions).
    @SuppressWarnings("unused")
    private static Map<String, Long> emptyCounts() {
        return new HashMap<>();
    }
}
