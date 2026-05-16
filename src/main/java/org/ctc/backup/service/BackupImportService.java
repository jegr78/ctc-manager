package org.ctc.backup.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.audit.BackupExecutedByResolver;
import org.ctc.backup.audit.DataImportAuditService;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.dto.EntityRowCount;
import org.ctc.backup.event.BackupImportSucceededEvent;
import org.ctc.backup.exception.AutoBackupBeforeImportException;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.backup.restore.EntityRestorer;
import org.ctc.backup.restore.RestoreFailureInjector;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stateless orchestrator for the backup import pipeline.
 *
 * <p>Preview surface: {@link #stage(MultipartFile)}, {@link #reparse(UUID)},
 * {@link #deleteStagingFile(UUID)}.
 *
 * <p>Execute surface: {@link #execute(UUID)} — single {@code @Transactional} method
 * backed by package-private helpers {@link #wipeAllTables(Map)} and
 * {@link #restoreAll(Path, Map)}.
 *
 * <p>The execute method:
 * <ol>
 *   <li>Locates the staged ZIP + sidecar (Phase 74 staging-file contract).</li>
 *   <li>Reads the manifest (re-validates schemaVersion).</li>
 *   <li>Wipes all 24 tables in {@code BackupSchema.getExportOrder().reversed()} order via
 *       native {@code DELETE FROM <table>} after three self-FK pre-step UPDATEs
 *       ({@code teams.parent_team_id}, {@code season_teams.successor_season_team_id},
 *       {@code playoff_matchups.next_matchup_id}). {@code em.flush() + em.clear()} drops the
 *       L1 cache.</li>
 *   <li>Restores each entity via {@code JdbcTemplate.batchUpdate} through the 24
 *       {@link EntityRestorer @Component} beans — bypasses {@code AuditingEntityListener} so
 *       imported {@code created_at} / {@code updated_at} survive verbatim.</li>
 *   <li>Extracts the staged {@code uploads/} tree to
 *       {@code data/.import-backups/<ts>/uploads-new/} (D-11 / D-12).</li>
 *   <li>Publishes {@link BackupImportSucceededEvent} as the LAST statement inside the try
 *       block — Spring's TX-aware buffering defers the listener (Plan 07) until AFTER_COMMIT.</li>
 *   <li>On any exception: SLF4J ERROR + REQUIRES_NEW {@code success=false} audit row (Plan 02
 *       contract, survives the outer rollback) + best-effort cleanup of {@code uploads-new/}
 *       + throw {@link BackupImportException} carrying the audit-row UUID for the Plan 08
 *       controller flash.</li>
 * </ol>
 *
 * <p><strong>Staging-file lifecycle (D-16 / D-08):</strong>
 * <ul>
 *   <li>{@link #stage}: on any {@link BackupArchiveException} or {@link IOException},
 *       the staged file is deleted in a {@code finally} block. On success, the file
 *       survives for the execute step.</li>
 *   <li>{@link #reparse}: never deletes the staging file — execute inherits it
 *       (D-08). The Plan 07 startup-sweep is the safety net for stale files.</li>
 *   <li>{@link #deleteStagingFile}: idempotent, swallows all {@code IOException}
 *       (never throws). Called from the cancel-button controller (Plan 08).</li>
 *   <li>{@link #execute}: success path delegates staging-file cleanup to the AFTER_COMMIT
 *       listener (Plan 07) via the published event. Failure path leaves the staging file
 *       in place so the operator can retry without re-uploading.</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BackupImportService {

    /** ZIP magic bytes: PK\x03\x04 (local file header signature). */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    /** Batch size for the JSON-stream-to-batchUpdate accumulator. */
    private static final int RESTORE_BATCH_SIZE = 500;

    /** Frequency at which {@link RestoreFailureInjector#maybeFailAt(String, int)} fires (production no-op). */
    private static final int FAIL_INJECT_INTERVAL = 50;

    /** Defensive allow-list for native-SQL table-name concatenation (no SQL injection on hard-coded BackupSchema slugs). */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[a-z_]+$");

    private final AtomicInteger zipOpenCounter = new AtomicInteger(0);

    public int getZipOpenCount() {
        return zipOpenCounter.get();
    }

    private final BackupArchiveService backupArchive;
    private final BackupSchema backupSchema;
    private final List<JpaRepository<?, ?>> allRepositories;
    private final List<EntityRestorer> entityRestorers;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper backupObjectMapper;
    private final RestoreFailureInjector failureInjector;
    private final DataImportAuditService dataImportAuditService;
    private final ApplicationEventPublisher eventPublisher;
    private final BackupExecutedByResolver executedByResolver;
    private final Path stagingDir;
    private final Path importBackupsDir;
    private final Path uploadsTargetDir;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * {@code tableName -> JpaRepository} lookup map; populated by {@link #wireRepositoriesByTableName()}.
     *
     * <p>Not final — populated by {@link PostConstruct} after Spring populates {@code allRepositories}.
     */
    private Map<String, JpaRepository<?, ?>> repositoryByTableName;

    /**
     * {@code tableName -> EntityRestorer} lookup map; populated by {@link #wireRestorersByTableName()}.
     */
    private Map<String, EntityRestorer> restorerByTableName;

    /**
     * Explicit constructor — {@code @RequiredArgsConstructor} is incompatible with
     * the {@code @Value} annotations and the {@code @Qualifier} on
     * {@code backupObjectMapper}.
     */
    public BackupImportService(
            BackupArchiveService backupArchive,
            BackupSchema backupSchema,
            List<JpaRepository<?, ?>> allRepositories,
            List<EntityRestorer> entityRestorers,
            JdbcTemplate jdbcTemplate,
            @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper,
            RestoreFailureInjector failureInjector,
            DataImportAuditService dataImportAuditService,
            ApplicationEventPublisher eventPublisher,
            BackupExecutedByResolver executedByResolver,
            @Value("${app.backup.staging-dir}") String stagingDirRaw,
            @Value("${app.backup.import-backups-dir}") String importBackupsDirRaw,
            @Value("${app.upload-dir}") String uploadDirRaw
    ) {
        this.backupArchive = backupArchive;
        this.backupSchema = backupSchema;
        this.allRepositories = allRepositories;
        this.entityRestorers = entityRestorers;
        this.jdbcTemplate = jdbcTemplate;
        this.backupObjectMapper = backupObjectMapper;
        this.failureInjector = failureInjector;
        this.dataImportAuditService = dataImportAuditService;
        this.eventPublisher = eventPublisher;
        this.executedByResolver = executedByResolver;
        this.stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        this.importBackupsDir = Paths.get(importBackupsDirRaw).toAbsolutePath().normalize();
        this.uploadsTargetDir = Paths.get(uploadDirRaw).toAbsolutePath().normalize();
        this.repositoryByTableName = new HashMap<>();
        this.restorerByTableName = new HashMap<>();
    }

    /**
     * Builds the {@code tableName -> JpaRepository} lookup map at startup.
     *
     * <p>Iterates {@link BackupSchema#getExportOrder()} and matches each
     * {@link EntityRef#entityClass()} against the Spring-injected
     * {@code allRepositories} list by extracting the first JPA type argument
     * via {@link GenericTypeResolver#resolveTypeArguments}.
     *
     * @throws IllegalStateException if the resulting map size differs from
     *                               {@code backupSchema.getExportOrder().size()} — fail-fast at startup
     */
    @PostConstruct
    void wireRepositoriesByTableName() {
        Map<Class<?>, JpaRepository<?, ?>> byEntityClass = new HashMap<>();
        for (JpaRepository<?, ?> repo : allRepositories) {
            Class<?>[] typeArgs = GenericTypeResolver.resolveTypeArguments(repo.getClass(), JpaRepository.class);
            if (typeArgs != null && typeArgs.length >= 1) {
                byEntityClass.put(typeArgs[0], repo);
            }
        }

        Map<String, JpaRepository<?, ?>> map = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (EntityRef ref : backupSchema.getExportOrder()) {
            JpaRepository<?, ?> repo = byEntityClass.get(ref.entityClass());
            if (repo == null) {
                missing.add(ref.tableName());
            } else {
                map.put(ref.tableName(), repo);
            }
        }

        if (map.size() != backupSchema.getExportOrder().size()) {
            throw new IllegalStateException(
                    "BackupImportService bootstrap: expected " + backupSchema.getExportOrder().size()
                    + " repository-to-tableName mappings but built " + map.size()
                    + "; missing tables: " + missing);
        }
        this.repositoryByTableName = map;
        log.info("BackupImportService: wired {} table-to-repository mappings", map.size());
    }

    /**
     * Builds the {@code tableName -> EntityRestorer} lookup map at startup.
     *
     * <p>Iterates the Spring-injected {@code entityRestorers} list (auto-collected
     * {@code @Component} beans) and keys each by {@link EntityRestorer#tableName()}.
     *
     * @throws IllegalStateException if the resulting map size differs from
     *                               {@code backupSchema.getExportOrder().size()} or contains
     *                               unknown table names — fail-fast at startup
     */
    @PostConstruct
    void wireRestorersByTableName() {
        Map<String, EntityRestorer> map = new HashMap<>();
        for (EntityRestorer restorer : entityRestorers) {
            String table = restorer.tableName();
            EntityRestorer prev = map.put(table, restorer);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate EntityRestorer for tableName=" + table
                                + ": " + prev.getClass().getName()
                                + " AND " + restorer.getClass().getName());
            }
        }

        List<String> missing = new ArrayList<>();
        for (EntityRef ref : backupSchema.getExportOrder()) {
            if (!map.containsKey(ref.tableName())) {
                missing.add(ref.tableName());
            }
        }

        if (map.size() != backupSchema.getExportOrder().size() || !missing.isEmpty()) {
            throw new IllegalStateException(
                    "BackupImportService bootstrap: expected " + backupSchema.getExportOrder().size()
                            + " EntityRestorer beans but wired " + map.size()
                            + "; missing tables: " + missing);
        }
        this.restorerByTableName = map;
        log.info("BackupImportService: wired {} table-to-restorer mappings", map.size());
    }

    // =========================================================================
    // Preview surface (public API)
    // =========================================================================

    /**
     * Stages a {@link MultipartFile} ZIP upload and returns a preview of its content.
     *
     * <p>Flow:
     * <ol>
     *   <li>Ensure staging directory exists.</li>
     *   <li>ZIP magic-byte sniff — BEFORE any disk write.</li>
     *   <li>Allocate UUID + staging path; transfer the multipart body to disk.</li>
     *   <li>Delegate to {@link #buildPreview} — schema gate fires before any DB read.</li>
     *   <li>On any exception: log WARN, delete staged file in {@code finally}, rethrow.</li>
     * </ol>
     *
     * @param file the uploaded ZIP (multipart body)
     * @return a fully-populated {@link BackupImportPreview}
     * @throws BackupArchiveException for ZIP-structural or schema violations
     * @throws IOException            for I/O failures during staging or parsing
     */
    public BackupImportPreview stage(MultipartFile file) throws BackupArchiveException, IOException {
        log.info("Backup import staging started: originalFilename={}, sizeBytes={}",
                file.getOriginalFilename(), file.getSize());

        // Step 1: ensure staging directory exists (idempotent)
        Files.createDirectories(stagingDir);

        // Step 2: ZIP magic-byte sniff — BEFORE any disk write
        byte[] header = readMagic(file);
        if (header.length < 4 || !Arrays.equals(header, ZIP_MAGIC)) {
            throw new BackupArchiveException(Reason.NOT_A_ZIP,
                    "File does not look like a ZIP archive (bad magic bytes)");
        }

        // Step 3: allocate staging path
        UUID stagingId = UUID.randomUUID();
        Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
        Path metaFile = stagingDir.resolve("upload-" + stagingId + ".zip.meta");

        // Step 4: transfer multipart body to disk + persist original filename for reparse()
        file.transferTo(staged);
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            Files.writeString(metaFile, originalFilename, java.nio.charset.StandardCharsets.UTF_8);
        }

        // Step 5: build preview — schema gate fires before any DB read (D-09)
        boolean keep = false;
        try {
            BackupImportPreview preview = buildPreview(stagingId, staged,
                    originalFilename, file.getSize());
            keep = true;  // success — staging file survives for the confirm step
            return preview;
        } catch (BackupArchiveException | IOException ex) {
            log.warn("Backup import rejected: stagingId={}, reason={}, msg={}",
                    stagingId,
                    ex instanceof BackupArchiveException bae ? bae.reason() : "IO",
                    ex.getMessage());
            throw ex;
        } finally {
            if (!keep) {
                try {
                    Files.deleteIfExists(staged);
                    Files.deleteIfExists(metaFile);
                } catch (IOException ioDel) {
                    log.warn("Failed to delete rejected staging file: {}", staged, ioDel);
                }
            }
        }
    }

    /**
     * Re-reads an already-staged file and returns a fresh {@link BackupImportPreview}.
     *
     * <p>Defense-in-depth: re-runs the full validation chain including the schema gate so that
     * a schema-version bump between preview and execute time is detected at execute time as well.
     * The staging file is NOT deleted on reject.
     *
     * @param stagingId UUID of the staging file (from a previous {@link #stage} call)
     * @return a fresh {@link BackupImportPreview}
     * @throws BackupArchiveException with {@link Reason#MANIFEST_MISSING} when the staging
     *                                file is absent; other reasons per validation chain
     * @throws IOException            for I/O failures during parsing
     */
    public BackupImportPreview reparse(UUID stagingId) throws BackupArchiveException, IOException {
        Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
        Path metaFile = stagingDir.resolve("upload-" + stagingId + ".zip.meta");
        if (!Files.exists(staged)) {
            // MANIFEST_MISSING is the closest canonical Reason — the staging file is the
            // manifest's container; if it's gone the manifest is gone (D-08 semantic overlap).
            throw new BackupArchiveException(Reason.MANIFEST_MISSING,
                    "Staging file not found for id=" + stagingId);
        }
        // Restore original filename from sidecar; fall back to staging filename if absent
        // NP: staged.getFileName() cannot return null — staged is a staging file path, never a root.
        // See config/spotbugs-exclude.xml BackupImportService.reparse NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE.
        String originalFilename = Files.exists(metaFile)
                ? Files.readString(metaFile, java.nio.charset.StandardCharsets.UTF_8)
                : staged.getFileName().toString();
        return buildPreview(stagingId, staged, originalFilename, Files.size(staged));
    }

    /**
     * Deletes the staging file for the given {@code stagingId}.
     *
     * <p>Idempotent: calling twice on the same UUID is safe ({@code Files.deleteIfExists}
     * semantics). Never throws — any {@link IOException} is logged at WARN and swallowed.
     * Called from the cancel-button controller (Plan 08); a throw here would mask the user's
     * cancel intent.
     *
     * @param stagingId UUID of the staging file to delete
     */
    public void deleteStagingFile(UUID stagingId) {
        Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
        Path metaFile = stagingDir.resolve("upload-" + stagingId + ".zip.meta");
        try {
            boolean deleted = Files.deleteIfExists(staged);
            Files.deleteIfExists(metaFile);
            log.info("deleteStagingFile: stagingId={}, deleted={}", stagingId, deleted);
        } catch (IOException e) {
            log.warn("Failed to delete staging file: stagingId={}, path={}", stagingId, staged, e);
        }
    }

    // =========================================================================
    // Execute surface (public API)
    // =========================================================================

    /**
     * Replaces the entire DB content with the contents of the staged backup ZIP, atomically.
     *
     * <p>Single {@code @Transactional(REQUIRED, READ_COMMITTED)} method that owns wipe +
     * restore + uploads extraction + event publish. The post-commit listener consumes
     * {@link BackupImportSucceededEvent} for the move-triple + audit success-row write.
     *
     * <p>On failure: the JPA transaction rolls back (wipe + restore are undone), a
     * {@code success=false} audit row is written via REQUIRES_NEW (Plan 02 contract,
     * survives the outer rollback), the partially-extracted {@code uploads-new/} is
     * cleaned best-effort, and a {@link BackupImportException} carrying the audit-row UUID
     * is thrown so the Plan 08 controller can bind the D-15 #2 failure-flash placeholder.
     *
     * @param stagingId UUID of the staged ZIP (from a previous {@link #stage} call)
     * @return a {@link BackupImportResult} carrying the audit-row UUID + restored counts
     * @throws BackupImportException on any failure (catch-all rollback path)
     */
    @Transactional(
            propagation = Propagation.REQUIRED,
            isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public BackupImportResult execute(UUID stagingId) {
        zipOpenCounter.set(0);
        log.info("Backup import execute started: stagingId={}", stagingId);

        UUID auditUuid = UUID.randomUUID();

        // Stage file lookup (same shape as reparse())
        Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
        Path metaFile = stagingDir.resolve("upload-" + stagingId + ".zip.meta");
        if (!Files.exists(staged)) {
            BackupArchiveException missing = new BackupArchiveException(Reason.MANIFEST_MISSING,
                    "Staging file not found for id=" + stagingId);
            log.error("Import failed for staging-id {}: {}", stagingId, missing.getMessage(), missing);
            boolean auditWritten = tryRecordFailure(auditUuid, /* schemaVersion */ 0,
                    /* sourceFilename */ stagingId.toString(),
                    Map.of(), Map.of());
            throw new BackupImportException(auditUuid, auditWritten, missing);
        }

        // <ts> directory for atomic move-triple — computed ONCE here and shared by the
        // auto-backup ZIP path (Step 0.5) and the uploads-old/ sibling (AFTER_COMMIT listener).
        String ts = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-");
        Path importBackupDir = importBackupsDir.resolve(ts);
        // Target ZIP for the pre-import auto-backup (runs BEFORE any DB mutation).
        Path autoBackupZip = importBackupDir.resolve("auto-backup-before-import.zip");

        // NP: staged.getFileName() cannot return null — staged is a path to a staging file,
        // never a root path. See config/spotbugs-exclude.xml BackupImportService.execute entry.
        String sourceFilename;
        try {
            sourceFilename = Files.exists(metaFile)
                    ? Files.readString(metaFile, java.nio.charset.StandardCharsets.UTF_8)
                    : staged.getFileName().toString();
        } catch (IOException ioe) {
            // WR-04: make the meta-read failure explicit in the audit row so the operator can
            // tell "no .meta file ever existed" (handled via the Files.exists branch above)
            // apart from "the .meta file was corrupted / unreadable" (this branch). Falling back
            // to the staging UUID-filename silently lost the user-friendly upload name.
            sourceFilename = "<filename-unavailable: meta-read-failed-" + stagingId + ">";
            log.error("Staging .meta sidecar corrupted for id={} — original filename lost",
                    stagingId, ioe);
        }

        Path uploadsNewDir = importBackupDir.resolve("uploads-new");

        Map<String, Long> wipedCounts = new LinkedHashMap<>();
        Map<String, Long> restoredCounts = new LinkedHashMap<>();
        int schemaVersion = 0;

        try {
            // Step 0: manifest re-read (schemaVersion validation is implicit — readManifest
            // does not gate, but Plan 04 contract guarantees the value is the integer from
            // the staged ZIP; the controller path always invokes reparse() first which
            // already gates via buildPreview).
            BackupManifest manifest = backupArchive.readManifest(staged);
            schemaVersion = manifest.schemaVersion();

            // Step 0.5: pre-import auto-backup.
            // Runs INSIDE the outer @Transactional(REQUIRED, READ_COMMITTED) — the read-only
            // BackupArchiveService.writeZip(...) joins this tx (no-op read-only join).
            // If the write fails, NO DB mutation has occurred yet; the outer tx rolls back as
            // a no-op. A distinct AutoBackupBeforeImportException is thrown so the controller
            // can flash a semantically correct "no DB changes" message.
            try {
                Files.createDirectories(importBackupDir);
                try (OutputStream out = Files.newOutputStream(autoBackupZip,
                        StandardOpenOption.CREATE_NEW)) {
                    backupArchive.writeZip(out, Instant.now());
                }
            } catch (IOException | RuntimeException autoExportEx) {
                tryDeletePartialAutoBackup(autoBackupZip);  // best-effort cleanup, never throws
                log.error("Auto-backup-before-import failed for staging-id {} — aborting import",
                        stagingId, autoExportEx);
                boolean auditWritten = tryRecordFailure(auditUuid, schemaVersion,
                        sourceFilename, Map.of(), Map.of());  // empty count maps (no DB mutation)
                throw new AutoBackupBeforeImportException(auditUuid, auditWritten, autoExportEx);
            }

            // Step 1: wipe (3 self-FK NULLs + native DELETE in reverse export order + flush/clear)
            wipeAllTables(wipedCounts);

            // Step 2: extract staged uploads (D-12) — BEFORE restore so a restore failure leaves
            // the FS staging area visible for forensic inspection but the catch-block still
            // best-effort cleans uploads-new/. importBackupDir was already created in Step 0.5
            // (D-15 single-source-of-truth), so only the uploadsNewDir sibling needs creation.
            Files.createDirectories(uploadsNewDir);
            backupArchive.extractUploadsTo(staged, uploadsNewDir);

            // Step 3: restore (JdbcTemplate.batchUpdate via 24 EntityRestorers; auditing bypass)
            restoreAll(staged, restoredCounts);

            // Step 4: publish the success event as the LAST statement inside the try block.
            // Spring's TransactionalEventListener(phase=AFTER_COMMIT) buffers delivery until
            // the outer @Transactional method commits — Plan 07 consumes the event there.
            String executedBy = executedByResolver.resolve(null);
            long totalRestored = restoredCounts.values().stream().mapToLong(Long::longValue).sum();
            // WR-02: entityCount documents "entities that contributed rows" — filter to
            // non-zero counts so the D-15 success flash ("across N tables") tells the truth
            // on partial imports instead of always reporting the iteration count (24).
            int entityCount = (int) restoredCounts.values().stream().filter(c -> c > 0).count();

            // CR-01: use unmodifiable LinkedHashMap copies so the audit-row JSON columns preserve
            // export-order (Map.copyOf returns a hash-table-backed ImmutableCollections.MapN that
            // strips insertion order — defeating the explicit LinkedHashMap ordering above).
            eventPublisher.publishEvent(new BackupImportSucceededEvent(
                    stagingId,
                    auditUuid,
                    importBackupDir,
                    uploadsTargetDir,
                    uploadsNewDir,
                    schemaVersion,
                    Collections.unmodifiableMap(new LinkedHashMap<>(wipedCounts)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(restoredCounts)),
                    sourceFilename,
                    executedBy));

            log.info("Backup import execute completed: stagingId={}, auditUuid={}, "
                            + "restoredTotal={}, entityCount={}",
                    stagingId, auditUuid, totalRestored, entityCount);

            return new BackupImportResult(auditUuid, totalRestored, entityCount);
        } catch (Throwable t) {
            // WR-08: catch Throwable (not Exception) so an OutOfMemoryError or similar JVM-fatal
            // Error during the 1000-row restore still gets an audit row written via REQUIRES_NEW
            // before propagating. Spring's @Transactional rollback fires on Error by default;
            // we preserve the JVM-fatal contract by re-throwing Error unchanged.
            // AutoBackupBeforeImportException is rethrown unchanged — Step 0.5 already recorded
            // its own audit row + cleaned up its partial ZIP, and wrapping it here would shadow
            // the subclass-specific controller catch (superclass-first exception matching).
            if (t instanceof AutoBackupBeforeImportException ae) {
                throw ae;
            }
            log.error("Import failed for staging-id {}: ", stagingId, t);
            boolean auditWritten = tryRecordFailure(auditUuid, schemaVersion, sourceFilename,
                    wipedCounts, restoredCounts);
            tryCleanupUploadsNew(uploadsNewDir);
            if (t instanceof Error err) {
                throw err;
            }
            throw new BackupImportException(auditUuid, auditWritten, t);
        }
    }

    // =========================================================================
    // Private helpers — wipe / restore / housekeeping
    // =========================================================================

    /**
     * Wipes all 24 tables in {@link BackupSchema#getExportOrder()} reverse order.
     *
     * <p>Step 0: NULL the 3 self-FK columns so the FK-reverse DELETE loop is safe
     * regardless of FK direction:
     * <ul>
     *   <li>{@code teams.parent_team_id} — sub-team → parent self-FK.</li>
     *   <li>{@code season_teams.successor_season_team_id} — successor self-FK.</li>
     *   <li>{@code playoff_matchups.next_matchup_id} — bracket-progression self-FK.</li>
     * </ul>
     *
     * <p>Step 1: forward iteration over {@code getExportOrder().reversed()}, issuing
     * {@code DELETE FROM <table>} via {@link EntityManager#createNativeQuery(String)}. The
     * table name comes from {@link EntityRef#tableName()} (which derives from the JPA
     * {@code @Table(name=...)} annotation on the entity class) — defensively re-validated
     * against {@link #SAFE_TABLE_NAME} before concatenation.
     *
     * <p>Step 2: {@link EntityManager#flush()} + {@link EntityManager#clear()} drops the L1
     * cache so downstream native restore queries don't see stale managed entities.
     *
     * @param wipedCounts out-parameter map filled with {tableName → rows-deleted}
     */
    void wipeAllTables(Map<String, Long> wipedCounts) {
        // 3 self-FK pre-step NULLs (teams.parent_team_id, season_teams.successor, playoff_matchups.next)
        entityManager.createNativeQuery("UPDATE teams SET parent_team_id = NULL").executeUpdate();
        entityManager.createNativeQuery("UPDATE season_teams SET successor_season_team_id = NULL").executeUpdate();
        entityManager.createNativeQuery("UPDATE playoff_matchups SET next_matchup_id = NULL").executeUpdate();

        List<EntityRef> wipeOrder = backupSchema.getExportOrder().reversed();
        for (EntityRef ref : wipeOrder) {
            String table = ref.tableName();
            validateTableName(table);
            int rows = entityManager.createNativeQuery("DELETE FROM " + table).executeUpdate();
            wipedCounts.put(table, (long) rows);
        }

        entityManager.flush();
        entityManager.clear();
        log.info("Wipe completed: {} tables, total rows deleted={}", wipedCounts.size(),
                wipedCounts.values().stream().mapToLong(Long::longValue).sum());
    }

    /**
     * Restores all 24 tables from the staged ZIP via {@code JdbcTemplate.batchUpdate} through
     * the per-entity {@link EntityRestorer} beans.
     *
     * <p>For each {@link EntityRef} in {@link BackupSchema#getExportOrder()} (forward), opens
     * the {@code data/<slug>.json} ZIP entry, parses it with a Jackson {@link JsonParser},
     * accumulates {@link JsonNode} rows into a batch of {@link #RESTORE_BATCH_SIZE}, and
     * delegates to {@link EntityRestorer#restore(List, JdbcTemplate)} per batch. Calls
     * {@link RestoreFailureInjector#maybeFailAt(String, int)} every
     * {@link #FAIL_INJECT_INTERVAL} rows (production no-op; tests inject failures via the
     * {@code FailAtTableInjector} bean).
     *
     * @param staged          path to the staged ZIP
     * @param restoredCounts  out-parameter map filled with {tableName → rows-restored}
     * @throws IOException on ZIP / JSON parse failure (caller catches and rolls back)
     */
    void restoreAll(Path staged, Map<String, Long> restoredCounts) throws IOException {
        // WR-05: open the ZIP exactly once (random-access ZipFile) instead of re-opening a
        // ZipInputStream per entity (24× rescans from the start). This eliminates the on-Windows
        // race where the listener's Step-4 cleanup can hit the same staging file while a
        // JVM-internal ZipInputStream lifecycle is still settling, and is a meaningful perf
        // win on the Saison-2023 ~1000-row fixture.
        zipOpenCounter.incrementAndGet();
        try (ZipFile zf = new ZipFile(staged.toFile())) {
            for (EntityRef ref : backupSchema.getExportOrder()) {
                String table = ref.tableName();
                EntityRestorer restorer = restorerByTableName.get(table);
                if (restorer == null) {
                    throw new IllegalStateException("No EntityRestorer wired for tableName=" + table);
                }
                long restored = restoreOneTable(zf, ref, restorer);
                restoredCounts.put(table, restored);
            }
        }
        log.info("Restore completed: {} tables, total rows restored={}", restoredCounts.size(),
                restoredCounts.values().stream().mapToLong(Long::longValue).sum());
    }

    /**
     * Restores a single entity's {@code data/<slug>.json} ZIP entry by streaming the JSON
     * array with a Jackson {@link JsonParser}, accumulating batches of
     * {@link #RESTORE_BATCH_SIZE} rows, and delegating to
     * {@link EntityRestorer#restore(List, JdbcTemplate)} per batch.
     *
     * <p>WR-05: takes an open {@link ZipFile} so the caller can resolve the entry by name
     * via the random-access {@link ZipFile#getEntry(String)} lookup, then stream the JSON
     * through {@link ZipFile#getInputStream(ZipEntry)} — no per-entity rescan.
     */
    private long restoreOneTable(ZipFile zf, EntityRef ref, EntityRestorer restorer) throws IOException {
        String entryPath = ref.fileName();
        long totalRows = 0;
        List<JsonNode> batch = new ArrayList<>(RESTORE_BATCH_SIZE);

        ZipEntry entry = zf.getEntry(entryPath);
        if (entry == null) {
            // Absent data files for an entity are not a hard error — an exported empty array is
            // semantically equivalent. The restorer is simply not invoked and the count is 0.
            log.warn("Backup ZIP has no data entry for table={} (entryPath={}) — possible corruption or schema regression",
                    ref.tableName(), entryPath);
            return totalRows;
        }

        try (InputStream entryStream = zf.getInputStream(entry)) {
            JsonParser parser = backupObjectMapper.getFactory().createParser(entryStream);
            parser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
            try {
                JsonToken firstToken = parser.nextToken();
                if (firstToken != JsonToken.START_ARRAY) {
                    throw new BackupArchiveException(Reason.MANIFEST_INVALID,
                            "data file is not a JSON array: " + entryPath);
                }
                int rowIndex = 0;
                JsonToken tok;
                while ((tok = parser.nextToken()) != null && tok != JsonToken.END_ARRAY) {
                    if (tok == JsonToken.START_OBJECT) {
                        JsonNode row = backupObjectMapper.readTree(parser);
                        batch.add(row);
                        rowIndex++;
                        totalRows++;

                        if (rowIndex % FAIL_INJECT_INTERVAL == 0) {
                            failureInjector.maybeFailAt(ref.tableName(), rowIndex);
                        }

                        if (batch.size() >= RESTORE_BATCH_SIZE) {
                            restorer.restore(batch, jdbcTemplate);
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    restorer.restore(batch, jdbcTemplate);
                    batch.clear();
                }
            } finally {
                parser.close();
            }
        }
        return totalRows;
    }

    /**
     * Defensively asserts that a table name only contains lowercase letters and underscores
     * before native-SQL concatenation. {@link BackupSchema#getExportOrder()} returns names
     * read from JPA {@code @Table(name=...)} annotations on hard-coded entity classes — there
     * is no realistic SQL-injection vector here, but a malformed annotation should fail loud
     * rather than execute arbitrary native SQL.
     */
    private static void validateTableName(String tableName) {
        if (tableName == null || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalStateException("Refusing native-SQL concat for unsafe table name: " + tableName);
        }
    }

    /**
     * Best-effort REQUIRES_NEW audit-row write on the failure path. Logs at ERROR if the
     * audit write itself fails (rare double-failure) but does not throw — the original
     * cause must propagate to the caller via {@link BackupImportException}.
     *
     * @return {@code true} when the audit row was persisted; {@code false} on the
     *         double-failure path (WR-03 — controller flash adjusts wording so the operator
     *         knows no row exists to query)
     */
    private boolean tryRecordFailure(UUID auditUuid, int schemaVersion, String sourceFilename,
            Map<String, Long> wipedCounts, Map<String, Long> restoredCounts) {
        try {
            dataImportAuditService.recordResult(
                    auditUuid,
                    /* executedByCaller */ null,
                    schemaVersion,
                    wipedCounts == null ? Map.of() : wipedCounts,
                    restoredCounts == null ? Map.of() : restoredCounts,
                    sourceFilename,
                    /* success */ false);
            return true;
        } catch (Exception auditEx) {
            log.error("Audit-row write ALSO failed for auditUuid={} — manual reconciliation required",
                    auditUuid, auditEx);
            return false;
        }
    }

    /**
     * Best-effort partial-ZIP cleanup on auto-backup failure. Never throws.
     *
     * <p>Calls {@link Files#deleteIfExists(Path)} inside a try-catch that logs at WARN on failure
     * but does not propagate. Windows file-locking semantics may prevent deletion when the
     * ZipOutputStream handle was not fully closed; the operator can clean up manually via
     * {@code rm -rf data/.import-backups/<ts>/}.
     *
     * @param target path to the partial auto-backup ZIP (may be {@code null})
     */
    private static void tryDeletePartialAutoBackup(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException io) {
            log.warn("Failed to delete partial auto-backup ZIP {}", target, io);
        }
    }

    /**
     * Best-effort cleanup of the partially-extracted {@code uploads-new/} directory on the
     * failure path (D-12 finally / CONTEXT discretion: delete on rollback).
     */
    private static void tryCleanupUploadsNew(Path uploadsNewDir) {
        if (uploadsNewDir == null || !Files.exists(uploadsNewDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(uploadsNewDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException io) {
                    log.warn("Failed to delete {} during uploads-new cleanup", p, io);
                }
            });
        } catch (IOException io) {
            log.warn("Failed to walk uploads-new for cleanup: {}", uploadsNewDir, io);
        }
    }

    // =========================================================================
    // Private helpers — preview-side
    // =========================================================================

    /**
     * Core preview builder — shared by {@link #stage} and {@link #reparse}.
     *
     * <p>Step order (schema gate BEFORE any DB read):
     * <ol>
     *   <li>Read and deserialize the manifest (includes ZIP hardening).</li>
     *   <li>Schema-version gate — throws {@link Reason#SCHEMA_MISMATCH} if versions differ,
     *       BEFORE any of the 24 {@code repo.count()} calls.</li>
     *   <li>Count upload files.</li>
     *   <li>Build 24 {@link EntityRowCount} cards via {@code repo.count()} +
     *       {@code manifest.tableCounts()}.</li>
     *   <li>Assemble and return {@link BackupImportPreview}.</li>
     * </ol>
     */
    private BackupImportPreview buildPreview(UUID stagingId, Path staged,
            String originalFilename, long fileSizeBytes) throws BackupArchiveException, IOException {

        // Step 1: read and deserialize manifest (includes all hardening checks)
        BackupManifest manifest = backupArchive.readManifest(staged);

        // Step 2: schema-version gate — BEFORE any DB read
        int backupVersion = manifest.schemaVersion();
        int currentVersion = BackupSchema.SCHEMA_VERSION;
        if (backupVersion != currentVersion) {
            throw new BackupArchiveException(Reason.SCHEMA_MISMATCH,
                    String.format("Schema version mismatch: backup=%d, expected=%d. Cannot import.",
                            backupVersion, currentVersion));
        }

        // Step 3: count upload files
        int uploadFileCount = backupArchive.countUploadFiles(staged);

        // Step 4: build 24 entity cards in getExportOrder() order
        List<EntityRowCount> entityCounts = new ArrayList<>();
        for (EntityRef ref : backupSchema.getExportOrder()) {
            JpaRepository<?, ?> repo = repositoryByTableName.get(ref.tableName());
            if (repo == null) {
                throw new IllegalStateException("No repository wired for tableName=" + ref.tableName());
            }
            long currentRows = repo.count();
            long importedRows = manifest.tableCounts().getOrDefault(ref.tableName(), 0L);
            String humanLabel = toHumanLabel(ref.tableName());
            entityCounts.add(new EntityRowCount(ref.tableName(), humanLabel, currentRows, importedRows));
        }

        // Step 5: aggregate totals
        long totalImportedRows = entityCounts.stream()
                .mapToLong(EntityRowCount::importedRows)
                .sum();
        boolean schemaMatches = true;  // gate at step 2 already ensured this

        log.info("Backup import staged successfully: stagingId={}, schemaVersion={}, " +
                "entityCount={}, uploadFileCount={}, totalImportedRows={}",
                stagingId, backupVersion, entityCounts.size(), uploadFileCount, totalImportedRows);

        return new BackupImportPreview(stagingId, originalFilename, fileSizeBytes,
                backupVersion, currentVersion, schemaMatches, entityCounts,
                uploadFileCount, totalImportedRows);
    }

    /**
     * Reads the first 4 bytes of the multipart file via a fresh {@code InputStream}.
     *
     * <p>Explicitly closes the stream so Spring's {@code StandardMultipartFile} (buffered-to-disk
     * implementation) can hand out a fresh stream on the next call without resource-leak
     * warnings (RESEARCH §Pitfall 7).
     */
    private byte[] readMagic(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(4);
        }
    }

    /**
     * Converts a snake_case table name to a human-readable label.
     *
     * <p>Examples: {@code "season_phases"} → {@code "Season Phases"},
     * {@code "race_lineups"} → {@code "Race Lineups"}.
     *
     * @param tableName the snake_case table name; empty string returns empty string
     * @return capitalized words joined by spaces
     */
    static String toHumanLabel(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return "";
        }
        return Arrays.stream(tableName.split("_"))
                .map(t -> Character.toUpperCase(t.charAt(0)) + t.substring(1))
                .collect(Collectors.joining(" "));
    }
}
