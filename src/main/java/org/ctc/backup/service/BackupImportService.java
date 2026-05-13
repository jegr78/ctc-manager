package org.ctc.backup.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.EntityRowCount;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stateless, write-free orchestrator for the backup import preview pipeline.
 *
 * <p>Phase 74 Plan 05 — D-19 public surface: {@link #stage(MultipartFile)},
 * {@link #reparse(UUID)}, {@link #deleteStagingFile(UUID)}.
 *
 * <p>No DB writes anywhere in this class. The class annotation
 * {@code @Transactional(readOnly = true)} makes this constraint declarative;
 * the 24 {@code Repository.count()} calls in {@link #buildPreview} share one
 * Hibernate session (OSIV is enabled; CONTEXT D-09).
 *
 * <p><strong>Staging-file lifecycle (D-16 / D-08):</strong>
 * <ul>
 *   <li>{@link #stage}: on any {@link BackupArchiveException} or {@link IOException},
 *       the staged file is deleted in a {@code finally} block. On success, the file
 *       survives for Phase 75's {@code import-execute} step.</li>
 *   <li>{@link #reparse}: never deletes the staging file — Phase 75 inherits it
 *       (D-08). The Plan 07 startup-sweep is the safety net for stale files.</li>
 *   <li>{@link #deleteStagingFile}: idempotent, swallows all {@code IOException}
 *       (never throws). Called from the cancel-button controller (Plan 08).</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BackupImportService {

    /** ZIP magic bytes: PK\x03\x04 (local file header signature). */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final BackupArchiveService backupArchive;
    private final BackupSchema backupSchema;
    private final List<JpaRepository<?, ?>> allRepositories;
    private final Path stagingDir;

    /**
     * {@code tableName -> JpaRepository} lookup map; populated by {@link #wireRepositoriesByTableName()}.
     *
     * <p>Not final — populated by {@link PostConstruct} after Spring populates {@code allRepositories}.
     */
    private Map<String, JpaRepository<?, ?>> repositoryByTableName;

    /**
     * Explicit constructor — {@code @RequiredArgsConstructor} is incompatible with
     * the {@code @Value} annotation on {@code stagingDirRaw} (mirrors
     * {@code BackupArchiveService} lines 102-112).
     */
    public BackupImportService(
            BackupArchiveService backupArchive,
            BackupSchema backupSchema,
            List<JpaRepository<?, ?>> allRepositories,
            @Value("${app.backup.staging-dir}") String stagingDirRaw
    ) {
        this.backupArchive = backupArchive;
        this.backupSchema = backupSchema;
        this.allRepositories = allRepositories;
        this.stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        this.repositoryByTableName = new HashMap<>();
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

    // =========================================================================
    // D-19 public surface
    // =========================================================================

    /**
     * Stages a {@link MultipartFile} ZIP upload and returns a preview of its content.
     *
     * <p>Flow (CONTEXT D-16 reject-path discipline):
     * <ol>
     *   <li>Ensure staging directory exists.</li>
     *   <li>ZIP magic-byte sniff — BEFORE any disk write ({@code RESEARCH §Pattern 5}).</li>
     *   <li>Allocate UUID + staging path; transfer the multipart body to disk.</li>
     *   <li>Delegate to {@link #buildPreview} — schema gate fires before any DB read (D-09).</li>
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
     * <p>D-08 defense-in-depth: re-runs the full validation chain including the schema gate
     * so that a schema-version bump between preview and execute time is detected at execute
     * time as well. The staging file is NOT deleted on reject — Phase 75 inherits it.
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
    // Private helpers
    // =========================================================================

    /**
     * Core preview builder — shared by {@link #stage} and {@link #reparse}.
     *
     * <p>Step order (D-09: schema gate BEFORE any DB read):
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

        // Step 2: schema-version gate — BEFORE any DB read (D-09 / SC#2 invariant)
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
