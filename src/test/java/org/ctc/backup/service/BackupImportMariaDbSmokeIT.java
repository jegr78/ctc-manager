package org.ctc.backup.service;

import org.ctc.admin.TestDataService;
import org.ctc.backup.audit.DataImportAudit;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 75 / Plan 10 — live MariaDB smoke IT for the QUAL-03 CI layer.
 *
 * <p>Boots the {@code local} profile against a real MariaDB:11 container provisioned via
 * Testcontainers. {@link DynamicPropertySource @DynamicPropertySource} overrides
 * {@code spring.datasource.url} / {@code username} / {@code password} so Flyway applies the
 * full V1..VN migration chain on the live engine before the {@link SpringBootTest @SpringBootTest}
 * context starts. {@code rewriteBatchedStatements=true} is appended to the JDBC URL (Phase 75
 * RESEARCH §10) so the production {@code JdbcTemplate.batchUpdate} 500-row batches compile to a
 * single multi-row INSERT on the wire.
 *
 * <p>The single test drives the full round-trip:
 * <ol>
 *   <li>Seed the dev fixture via {@link TestDataService#seed()} — REVISION-iteration-1 B1: no
 *       {@code seedSaison2023()} method exists; {@code seed()} loads Saison 2023 + 2024 +
 *       2024-Empty + 2026 per its Javadoc.</li>
 *   <li>Capture per-entity row counts via {@code JdbcTemplate.queryForObject("SELECT COUNT(*)
 *       FROM &lt;table&gt;")} across all 24 entities in {@link BackupSchema#getExportOrder()}.</li>
 *   <li>Export ZIP via {@link BackupArchiveService#writeZip(java.io.OutputStream, Instant)}
 *       (REVISION-iteration-1 B3: {@code BackupExportService.export(...)} does NOT exist).</li>
 *   <li>Stage + execute → wipe + restore on the live MariaDB engine.</li>
 *   <li>Assert per-entity row count parity for all 24 entities (CONTEXT D-16 IT layer).</li>
 *   <li>Assert {@code result.entityCount() == 24} (locks the 24-entity scope).</li>
 *   <li>Assert {@code result.restoredTotal()} equals the sum of pre-export counts.</li>
 *   <li>Poll the {@link DataImportAuditRepository} for up to 2 seconds for the success-time
 *       audit row written by the AFTER_COMMIT listener (Plan 75-07 Step 3) — defensive against
 *       any future async listener refactor; Spring 6 fires AFTER_COMMIT synchronously by
 *       default so a single retry typically suffices.</li>
 * </ol>
 *
 * <p>SHA-256 byte-equality assertions on sample entities are OUT OF SCOPE per CONTEXT D-16
 * (deferred to Phase 77 QUAL-02 {@code BackupRoundTripIT}). Uploads-tree assertions are OUT OF
 * SCOPE per REVISION-iteration-1 W6 — the {@code uploads-old/uploads-new} retention is proven
 * DB-engine-independently by {@code BackupImportPostCommitIT} (Plan 07) on H2.
 *
 * <p>Q4 resolution: this IT uses Testcontainers MariaDB on {@code @ActiveProfiles("local")} for
 * both local dev ({@code mvn -Dit.test=BackupImportMariaDbSmokeIT verify}) AND CI. The container
 * is provisioned via the host Docker daemon, which Testcontainers auto-detects on Linux/macOS
 * (developer workstations) and on GitHub Actions Linux runners. The companion
 * {@code mariadb-migration-smoke.yml} workflow stays Flyway-only and does NOT exercise this IT
 * — Failsafe's default {@code *IT.java} pattern in the main CI workflow picks it up.
 */
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@EnabledIfSystemProperty(named = "docker.available", matches = "true",
        disabledReason = "Set -Ddocker.available=true (with a running Docker daemon) to run the MariaDB Testcontainers round-trip IT")
class BackupImportMariaDbSmokeIT {

    /** Defensive table-name allow-list mirroring {@code BackupImportService.SAFE_TABLE_NAME}. */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[a-z_]+$");

    /** Locked 24-entity export scope (Phase 72 BackupSchema; Phase 75 ROADMAP success-criterion-1). */
    private static final int EXPECTED_ENTITY_COUNT = 24;

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("ctc_test")
            .withUsername("ctc")
            .withPassword("test");

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-mariadb-smoke-it-");
            IMPORT_BACKUPS_ROOT.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate import-backups tempdir", e);
        }
    }

    @DynamicPropertySource
    static void overrideJdbcUrl(DynamicPropertyRegistry registry) {
        // Append the Phase 75 RESEARCH §10 batch-rewrite flag so the production
        // JdbcTemplate.batchUpdate(500) calls compile to a single multi-row INSERT on
        // the wire — matching the application-local.yml shape (with rewriteBatchedStatements=true).
        registry.add("spring.datasource.url",
                () -> mariadb.getJdbcUrl() + "?rewriteBatchedStatements=true");
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        // Isolate auto-backup-before-import ZIP path from the real data/.import-backups/
        // to prevent same-second collisions with other import-execute ITs.
        registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
    }

    @Autowired
    BackupImportService backupImportService;

    @Autowired
    BackupArchiveService backupArchiveService;

    @Autowired
    TestDataService testDataService;

    @Autowired
    BackupSchema backupSchema;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataImportAuditRepository dataImportAuditRepository;

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    Path stagingDir;

    @BeforeEach
    void seedFixture() throws IOException {
        // REVISION-iteration-1 (B1): testDataService.seedSaison2023() does NOT exist. The
        // single entry point seed() loads the full dev fixture (Saison 2023 + 2024 +
        // 2024-Empty + 2026 per TestDataService.java:60-72 Javadoc). Per memory
        // feedback_test_data_isolation.md, Saison 2023 IS the dev fixture per ROADMAP-locked
        // exception; per-entity row-count parity assertions reflect ALL seeded seasons.
        testDataService.seed();
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
    }

    // -------------------------------------------------------------------------
    // Single scenario — 24-entity round-trip on a real MariaDB engine
    // -------------------------------------------------------------------------

    @Test
    void givenDevFixtureOnMariaDb_whenRoundTripExecuted_thenAllRowCountsMatch() throws Exception {
        // given — seed pre-state and capture per-entity row counts
        Map<String, Long> preExportCounts = captureRowCounts();
        assertThat(preExportCounts)
                .as("BackupSchema must expose exactly %d entities", EXPECTED_ENTITY_COUNT)
                .hasSize(EXPECTED_ENTITY_COUNT);
        long preExportTotal = preExportCounts.values().stream().mapToLong(Long::longValue).sum();
        assertThat(preExportTotal)
                .as("dev fixture must seed at least one row total")
                .isGreaterThan(0L);

        // when — export → wipe → import (full round-trip on live MariaDB)
        // REVISION-iteration-1 (B3): exportToBytes() wraps backupArchiveService.writeZip(baos,
        // Instant.now()). BackupExportService.export(...) does NOT exist; the real ZIP writer
        // is BackupArchiveService.writeZip(OutputStream, Instant) per BackupArchiveService.java:130.
        byte[] zipBytes = exportToBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "mariadb-smoke-export.zip", "application/zip", zipBytes);
        BackupImportPreview preview = backupImportService.stage(file);
        UUID stagingId = preview.stagingId();

        BackupImportResult result = backupImportService.execute(stagingId);

        // then — counters
        assertThat(result).as("execute() must return a non-null result").isNotNull();
        assertThat(result.auditUuid()).as("auditUuid must be present").isNotNull();
        assertThat(result.entityCount())
                .as("entityCount must equal the locked 24-entity scope")
                .isEqualTo(EXPECTED_ENTITY_COUNT);
        assertThat(result.restoredTotal())
                .as("restoredTotal must equal the sum of pre-export row counts")
                .isEqualTo(preExportTotal);

        // and — per-entity row-count parity for all 24 entities
        Map<String, Long> postImportCounts = captureRowCounts();
        assertThat(postImportCounts)
                .as("24-entity row-count parity on live MariaDB after export → wipe → import")
                .isEqualTo(preExportCounts);

        // and — AFTER_COMMIT listener wrote the success-time audit row (Plan 75-07 Step 3)
        UUID auditUuid = result.auditUuid();
        DataImportAudit audit = awaitAuditRow(auditUuid, Duration.ofSeconds(2));
        assertThat(audit.isSuccess())
                .as("AFTER_COMMIT listener must have written success=true audit row for %s", auditUuid)
                .isTrue();
        assertThat(audit.getTableCountsRestored())
                .as("audit.tableCountsRestored JSON must be non-blank on success")
                .isNotBlank();
        assertThat(audit.getExecutedAt())
                .as("audit.executedAt must be populated by DataImportAuditService writer")
                .isNotNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a Phase-73 export ZIP via the real {@link BackupArchiveService} writer.
     *
     * <p>REVISION-iteration-1 B3: {@code BackupExportService.export(...)} does NOT exist; the
     * canonical ZIP writer is {@link BackupArchiveService#writeZip(java.io.OutputStream, Instant)}.
     */
    private byte[] exportToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        return baos.toByteArray();
    }

    /**
     * Captures per-entity row counts as a {@link LinkedHashMap} keyed by snake_case table
     * name. Mirrors the production {@code BackupImportService.SAFE_TABLE_NAME} regex guard
     * before each native {@code COUNT(*)} concatenation so an unsafe table name in
     * {@link BackupSchema} fails loud rather than silently injects.
     */
    private Map<String, Long> captureRowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EntityRef ref : backupSchema.getExportOrder()) {
            String table = ref.tableName();
            if (!SAFE_TABLE_NAME.matcher(table).matches()) {
                throw new IllegalStateException("Unsafe table name in BackupSchema: " + table);
            }
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table, Long.class);
            counts.put(table, count == null ? 0L : count);
        }
        return counts;
    }

    /**
     * Polls {@link DataImportAuditRepository#findById(Object)} every 100&nbsp;ms for up to
     * {@code timeout} for the audit row addressed by {@code auditUuid}. Throws an
     * {@link AssertionError} on timeout so the test fails loud with the missing UUID rather
     * than NPE-ing on a downstream {@code .isSuccess()} call.
     *
     * <p>Spring 6 fires {@code @TransactionalEventListener(AFTER_COMMIT)} synchronously by
     * default, so {@link BackupImportService#execute(UUID)}'s return statement already runs
     * after the listener has completed — a single retry typically suffices. The poll is
     * defensive against any future async-listener refactor.
     */
    private DataImportAudit awaitAuditRow(UUID auditUuid, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        Optional<DataImportAudit> maybe;
        while (Instant.now().isBefore(deadline)) {
            maybe = dataImportAuditRepository.findById(auditUuid);
            if (maybe.isPresent()) {
                return maybe.get();
            }
            Thread.sleep(100L);
        }
        // Final attempt before throwing
        maybe = dataImportAuditRepository.findById(auditUuid);
        return maybe.orElseThrow(() -> new AssertionError(
                "data_import_audit row " + auditUuid + " did not materialize within " + timeout));
    }
}
