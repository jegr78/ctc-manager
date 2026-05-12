---
id: "05"
title: "BackupImportService — stage, reparse, deleteStagingFile"
wave: 2
depends_on: ["02", "03", "04"]
requirements: [IMPORT-01, IMPORT-02, SECU-01, SECU-02]
files_modified:
  - src/main/java/org/ctc/backup/service/BackupImportService.java
  - src/test/java/org/ctc/backup/service/BackupImportServiceIT.java
  - src/test/java/org/ctc/backup/service/BackupImportSchemaVersionMismatchIT.java
  - src/test/java/org/ctc/backup/service/BackupImportZipSlipIT.java
  - src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java
autonomous: true
---

## Objective

Author `BackupImportService` — the stateless, write-free orchestrator that accepts a `MultipartFile` ZIP upload, sniffs the ZIP magic bytes (`50 4B 03 04`) before touching disk, stages the file under `app.backup.staging-dir` with a UUID-prefixed name, runs the Plan 04 reader chain (`readManifest` → `countDataEntries` → `countUploadFiles`) through Plan 02's `LimitedInputStream` + `PathTraversalGuard` + `BackupImportLimits` hardening, applies the schema-version gate **before any DB read** (D-09 — only the cheap `Repository.count()` SELECTs run, AFTER the gate passes), and assembles a `BackupImportPreview` record (Plan 03) for the controller (Plan 08) to render. The service exposes the three D-19 methods `stage(MultipartFile)`, `reparse(UUID)`, `deleteStagingFile(UUID)` and obeys the D-16 reject-path discipline: any thrown `BackupArchiveException` triggers `Files.deleteIfExists(staged)` in `finally` and re-throws (a failed delete is logged at WARN — never thrown, the startup-sweep listener in Plan 07 is the safety net). The plan also delivers the four mandatory ITs (`BackupImportServiceIT`, `BackupImportSchemaVersionMismatchIT`, `BackupImportZipSlipIT`, `BackupImportZipBombIT`) — all with programmatically generated fixtures per D-25 (no committed binaries). The schema-mismatch IT proves the SC#2 invariant byte-for-byte: capture `Repository.count()` snapshot for each of the 24 entities BEFORE the rejected upload and AFTER, assert byte-identical (the gate executes *before* any read, so the 24 counts run only on green path; mismatch path never reaches the count loop — the IT verifies this *through* the snapshot, not by inspecting code).

## Tasks

<task id="74-05-01">
  <title>Create `BackupImportService` with `stage`, `reparse`, `deleteStagingFile`, magic-byte sniff, reject-delete `try/finally`, and `tableName → JpaRepository` lookup map</title>

  <action>
  Create `src/main/java/org/ctc/backup/service/BackupImportService.java` as a Spring `@Service` annotated `@Slf4j` + `@Transactional(readOnly = true)`. Use an explicit constructor (NOT `@RequiredArgsConstructor`) so the `@Value("${app.backup.staging-dir}") String stagingDirRaw` parameter can carry its annotation directly — this mirrors `BackupArchiveService` lines 64-74 verbatim. The constructor stores the resolved staging dir as `Path stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();` — never the raw string. Inject `BackupArchiveService backupArchive`, `BackupSchema backupSchema`, and `List<JpaRepository<?, ?>> allRepositories` (Spring auto-collects every `JpaRepository` bean — 25 in total: the 24 domain repos plus `DataImportAuditRepository`; the post-construct filter discards the audit repo by entity-class package check). Do **NOT** inject `Map<String, JpaRepository<?, ?>>` keyed by bean name — Spring's `Map<String, T>` injection uses bean-name keys (`seasonRepository`, `raceLineupRepository`, etc.), which does not match the `tableName` keys (`seasons`, `race_lineups`). Instead, build a `Map<String, JpaRepository<?, ?>> repositoryByTableName` in a `@PostConstruct` method that iterates `backupSchema.getExportOrder()` (returns 24 `EntityRef` records — `EntityRef(Class<?> entityClass, String tableName, String fileName)`), matches each `EntityRef.entityClass()` against the injected `allRepositories` list by extracting the JPA-typed argument of each repository's `JpaRepository<DomainType, UUID>` type parameter (use Spring's `GenericTypeResolver.resolveTypeArguments(repo.getClass(), JpaRepository.class)[0]` — handles Spring's repository proxies). If, after the loop, `repositoryByTableName.size() != backupSchema.getExportOrder().size()`, throw `IllegalStateException("BackupImportService bootstrap: expected 24 repository-to-tableName mappings but built " + repositoryByTableName.size() + "; missing tables: " + missingList)` — fail-fast at startup, not at first call.

  Declare a private static final `byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};` (RESEARCH §Pattern 5 line 519). Add a private `byte[] readMagic(MultipartFile file)` helper that calls `try (InputStream in = file.getInputStream()) { return in.readNBytes(4); }` — explicitly closes the stream so Spring's `StandardMultipartFile` (a buffered-to-disk multipart implementation) can hand out a fresh `getInputStream()` on the next call without resource-leak warnings (RESEARCH §Pitfall 7 line 780-788 confirms `MultipartFile.getInputStream()` is safe to call multiple times; the second call is a fresh stream against the buffered body).

  **`public BackupImportPreview stage(MultipartFile file) throws BackupArchiveException, IOException` — exact step-by-step flow:**

  1. Log at INFO: `log.info("Backup import staging started: originalFilename={}, sizeBytes={}", file.getOriginalFilename(), file.getSize());` (parameterized; mirrors `BackupArchiveService:94-95`).
  2. `Files.createDirectories(stagingDir);` — idempotent; safe on every call. The Plan 07 startup-sweep handles stale entries from previous runs.
  3. **Magic-byte sniff (FIRST — before any disk write):** call `byte[] header = readMagic(file);` and assert `header.length == 4 && Arrays.equals(header, ZIP_MAGIC)`. If false → `throw new BackupArchiveException(Reason.NOT_A_ZIP, "File does not look like a ZIP archive (bad magic bytes)");`. Note: `Reason.NOT_A_ZIP` must be present in the `Reason` enum delivered by Plan 02; if Plan 02 used a different name (e.g. `MANIFEST_MISSING` for "file has no recognizable ZIP header"), prefer the most specific enum value that exists. If `Reason.NOT_A_ZIP` is NOT in Plan 02's enum, this task adds it as a tiny atomic edit to `BackupArchiveException.Reason` and notes it in the file commit.
  4. Allocate `UUID stagingId = UUID.randomUUID();` and `Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");`. Re-validate via `PathTraversalGuard.guard(stagingDir, staged.toString());` (Plan 02 helper) — defensive: a malicious filename could not produce traversal here (UUID is generated, not user-supplied), but the call documents the invariant.
  5. `file.transferTo(staged);` — atomic, multipart-aware (RESEARCH §Pitfall 8 line 790-798 explains why this is preferred over `Files.copy`). Spring's `StandardMultipartFile.transferTo` operates on the buffered body, not the consumed `getInputStream()` from step 3 (RESEARCH §Pitfall 7).
  6. Enter the `try` block (the `finally` block at the end deletes `staged` on any throw). Within `try`, call the private `BackupImportPreview buildPreview(UUID stagingId, Path staged, String originalFilename, long fileSizeBytes)` helper (defined below). On success, return its result and let the `finally` block run without deleting (because no exception was thrown).
  7. **`catch (BackupArchiveException | IOException ex)`:** log at WARN `log.warn("Backup import rejected: stagingId={}, reason={}, msg={}", stagingId, ex instanceof BackupArchiveException bae ? bae.reason() : "IO", ex.getMessage());`, then **rethrow** (do NOT swallow). The `finally` block deletes the staged file.
  8. **`finally`:** if `Thread.currentThread().getStackTrace()` (no — simpler) — track via a local `boolean delete = true;` flag set to `false` only on the success path right before `return`. On `delete == true`, call `try { Files.deleteIfExists(staged); } catch (IOException ioDel) { log.warn("Failed to delete rejected staging file: {}", staged, ioDel); }` — never rethrow from `finally`. Implementation pattern (verbatim guidance for the executor):

  ```
  boolean keep = false;
  try {
      BackupImportPreview preview = buildPreview(stagingId, staged, file.getOriginalFilename(), file.getSize());
      keep = true;            // success — staging file survives for the confirm step
      return preview;
  } finally {
      if (!keep) {
          try { Files.deleteIfExists(staged); }
          catch (IOException ioDel) { log.warn("Failed to delete rejected staging file: {}", staged, ioDel); }
      }
  }
  ```

  This shape — `boolean keep` flag flipped just before `return`, `finally` deletes when `keep == false` — is the idiomatic "reject-delete on any throw" pattern. It correctly handles every exit (`return`, `throw`, runtime `Error`) and is unit-testable: the IT proves it by asserting `Files.exists(staged) == false` after a rejected schema-mismatch run.

  **`private BackupImportPreview buildPreview(UUID stagingId, Path staged, String originalFilename, long fileSizeBytes) throws BackupArchiveException, IOException`:**

  1. `BackupManifest manifest = backupArchive.readManifest(staged);` (Plan 04 — performs path-traversal, magic-bytes inside the ZIP, per-entry-limit, total-limit, entry-count limit checks; throws `BackupArchiveException` for any violation with the correct `Reason`).
  2. **Schema-version gate (D-09):** read `int backupVersion = manifest.schemaVersion();` and `int currentVersion = BackupSchema.SCHEMA_VERSION;`. If `backupVersion != currentVersion`, `throw new BackupArchiveException(Reason.SCHEMA_VERSION_MISMATCH, String.format("Schema version mismatch: backup=%d, expected=%d. Cannot import.", backupVersion, currentVersion));` — exact D-02#2 string (locked English copy, no `_` substitution). This throw happens BEFORE any of the 24 `repo.count()` calls — that is the SC#2 invariant.
  3. `int uploadFileCount = backupArchive.countUploadFiles(staged);` (Plan 04 — also hardening-protected).
  4. **Build the 24 entity cards:** iterate `backupSchema.getExportOrder()`, for each `EntityRef ref`:
      - `JpaRepository<?, ?> repo = repositoryByTableName.get(ref.tableName());`
      - If `repo == null` → `throw new IllegalStateException("No repository wired for tableName=" + ref.tableName());` (cannot happen after the `@PostConstruct` size assertion; defensive).
      - `long currentRows = repo.count();` (cheap SELECT; runs inside the outer `@Transactional(readOnly = true)` so all 24 share one Hibernate session).
      - `long importedRows = manifest.tableCounts().getOrDefault(ref.tableName(), 0L);` — manifest is authoritative per RESEARCH (Phase 73's `BackupRoundTripIT` proves manifest.tableCounts equals actual data-file row counts; defense-in-depth re-parse via `backupArchive.countDataEntries(staged)` is the executor's discretion call but NOT required for Phase 74; the IT below validates both shapes).
      - `String humanLabel = toHumanLabel(ref.tableName());`
      - Build `new EntityRowCount(ref.tableName(), humanLabel, currentRows, importedRows)`. Collect into `List<EntityRowCount> entityCounts` preserving `getExportOrder()` order (so the preview cards render in FK topo-sorted order — same order Phase 75 will use for the restore transaction).
  5. `long totalImportedRows = entityCounts.stream().mapToLong(EntityRowCount::importedRows).sum();`
  6. `boolean schemaMatches = true;` (always true here — the mismatch path threw at step 2; setting this field explicitly makes the DTO self-describing for the template).
  7. Log at INFO: `log.info("Backup import staged successfully: stagingId={}, schemaVersion={}, entityCount={}, uploadFileCount={}, totalImportedRows={}", stagingId, backupVersion, entityCounts.size(), uploadFileCount, totalImportedRows);`
  8. Return `new BackupImportPreview(stagingId, originalFilename, fileSizeBytes, backupVersion, currentVersion, schemaMatches, entityCounts, uploadFileCount, totalImportedRows);`.

  **`public BackupImportPreview reparse(UUID stagingId) throws BackupArchiveException, IOException` (D-08 defense-in-depth):**

  1. `Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");`
  2. If `!Files.exists(staged)` → `throw new BackupArchiveException(Reason.MANIFEST_MISSING, "Staging file not found for id=" + stagingId);` — `Reason.MANIFEST_MISSING` is the closest Plan 02 enum match (the staging file is the manifest's container; if it's gone the manifest is gone). If Plan 02's enum has a more specific `STAGING_FILE_MISSING` or `STAGING_NOT_FOUND`, use that; otherwise reuse `MANIFEST_MISSING` and document the semantic overlap in the JavaDoc comment.
  3. The `originalFilename` is unavailable on reparse (no `MultipartFile`); pass the staged file's filename via `staged.getFileName().toString()` — Phase 75's `import-execute` handler will not display this string (the staged file is internal), but the DTO contract requires a non-null value.
  4. The `fileSizeBytes` is `Files.size(staged)`.
  5. Call `buildPreview(stagingId, staged, staged.getFileName().toString(), Files.size(staged))` and return its result. **No `try/finally` delete here** — `reparse` MUST NOT delete the staging file on success (Phase 75 inherits it and deletes after a successful execute, per D-08 line 43). On reject, the file is also NOT deleted by `reparse` — let Phase 75 handle stale-file policy, with the Plan 07 startup-sweep as the safety net.

  **`public void deleteStagingFile(UUID stagingId)`:**

  1. `Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");`
  2. `try { boolean deleted = Files.deleteIfExists(staged); log.info("deleteStagingFile: stagingId={}, deleted={}", stagingId, deleted); } catch (IOException e) { log.warn("Failed to delete staging file: stagingId={}, path={}", stagingId, staged, e); }`
  3. **Never throws.** This method is called from the Cancel-button controller (Plan 08) and from cleanup paths; throwing here would mask the user's intent (cancel succeeds visually but blows up server-side).

  **`private static String toHumanLabel(String tableName)`:**

  Convert `season_phases` → `Season Phases`, `race_lineups` → `Race Lineups`, etc. Implementation: split on `_`, capitalize first character of each token via `Character.toUpperCase(t.charAt(0)) + t.substring(1)`, join with space. Single-line `Arrays.stream(tableName.split("_")).map(t -> Character.toUpperCase(t.charAt(0)) + t.substring(1)).collect(Collectors.joining(" "))`. Edge case: empty `tableName` → return empty string (defensive; cannot happen with `@Table(name=...)` on every entity but the function is `static` and unit-test-friendly so it must be total).

  **Imports needed (executor copies verbatim):**
  - `org.springframework.beans.factory.annotation.Value`
  - `org.springframework.beans.factory.annotation.Qualifier` (NOT used here — `BackupArchiveService` is auto-wired by type)
  - `org.springframework.data.jpa.repository.JpaRepository`
  - `org.springframework.core.GenericTypeResolver`
  - `org.springframework.stereotype.Service`
  - `org.springframework.transaction.annotation.Transactional`
  - `org.springframework.web.multipart.MultipartFile`
  - `jakarta.annotation.PostConstruct`
  - `lombok.extern.slf4j.Slf4j`
  - `org.ctc.backup.dto.BackupImportPreview`, `org.ctc.backup.dto.EntityRowCount` (Plan 03)
  - `org.ctc.backup.exception.BackupArchiveException`, `org.ctc.backup.exception.BackupArchiveException.Reason` (Plan 02)
  - `org.ctc.backup.security.PathTraversalGuard` (Plan 02)
  - `org.ctc.backup.schema.BackupManifest`, `BackupSchema`, `EntityRef`
  - `java.io.IOException`, `java.io.InputStream`
  - `java.nio.file.Files`, `Path`, `Paths`
  - `java.util.*` (`Arrays`, `HashMap`, `List`, `Map`, `UUID`)
  - `java.util.stream.Collectors`

  **Do NOT:** introduce `@RequiredArgsConstructor` (the `@Value` parameter needs the explicit constructor); inject an `EntityManager` (use repository `count()` only); use `@SessionAttributes` or any caching annotation; perform any DB write (the class is `readOnly = true`); modify Plan 04's `BackupArchiveService` or Plan 02's primitives (this plan only consumes them).
  </action>

  <read_first>
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` (D-08 execute-time re-validation, D-09 schema-version gate before any DB write, D-11 reuse `FileStorageService` SECU-02 path-traversal, D-12 `BackupImportLimits` constants, D-15 profile-aware staging dir, D-16 reject paths delete staging file synchronously in `try/finally`, D-18 no `@SessionAttributes`, D-19 service public surface, D-21 `BackupImportPreview`/`EntityRowCount` records, D-25 fixtures programmatically generated).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md` lines 505-535 (§Pattern 5 ZIP magic-number sniff without stream consumption), lines 770-798 (§Pitfall 6+7+8 — staging-file leaks, multipart fresh-stream contract, `transferTo` over `Files.copy`), lines 800-870 (§Common Operation 1 — staged + sniff; §Common Operation 2 — schema-version gate).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md` §"BackupImportService" (explicit constructor, `@Slf4j`+`@Service`+`@Transactional(readOnly = true)`, file-I/O staging pattern lines 89-129).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` §"Per-Task Verification Map" rows for `BackupImportServiceIT`, `BackupImportSchemaVersionMismatchIT`, `BackupImportZipSlipIT`, `BackupImportZipBombIT` + §"Wave 0 Requirements" (programmatic malicious fixtures).
    - `.planning/REQUIREMENTS.md` §IMPORT-01 (multipart upload + staging-dir + per-table preview), §IMPORT-02 (schema-version check BEFORE any DB write), §SECU-01 (ZIP-Slip defense), §SECU-02 (ZipBomb 50 MB / 500 MB / 50 000 entries).
    - `src/main/java/org/ctc/dataimport/CsvImportController.java` — v1.8 D-15 staging-path-pattern reference (`/admin/import/preview` → staging-file → `/admin/import/execute` re-parse). Phase 74 mirrors the stateless approach.
    - `src/main/java/org/ctc/backup/service/BackupArchiveService.java` — explicit-constructor + `@Qualifier` pattern (lines 64-74), `@Slf4j` parameterized logging (lines 94-95, 135-136), the `writeZip` method this plan's reader chain inverts.
    - `src/main/java/org/ctc/backup/schema/BackupSchema.java` — `SCHEMA_VERSION = 1`, `getExportOrder()` returns `List<EntityRef>` (24 entities; package-filtered to exclude `org.ctc.backup.audit.*`).
    - `src/main/java/org/ctc/backup/schema/EntityRef.java` — record `(Class<?> entityClass, String tableName, String fileName)`; `tableName` reads `@Table(name=...)` from the entity class.
    - `src/main/java/org/ctc/backup/schema/BackupManifest.java` — record `(int schemaVersion, String appVersion, Instant exportDate, Map<String, Long> tableCounts)`; `tableCounts` keys are snake_case `tableName` matching `@Table(name=...)`.
    - `src/main/java/org/ctc/backup/dto/BackupImportPreview.java` (delivered by Plan 03 — exact record signature: `(UUID stagingId, String originalFilename, long fileSizeBytes, int schemaVersion, int currentSchemaVersion, boolean schemaMatches, List<EntityRowCount> entityCounts, int uploadFileCount, long totalImportedRows)`).
    - `src/main/java/org/ctc/backup/dto/EntityRowCount.java` (delivered by Plan 03 — exact record signature: `(String tableName, String humanLabel, long currentRows, long importedRows)`).
    - `src/main/java/org/ctc/backup/exception/BackupArchiveException.java` (delivered by Plan 02 — `Reason` enum with `SCHEMA_VERSION_MISMATCH`, `ZIP_SLIP`, `ENTRY_TOO_LARGE`, `TOTAL_TOO_LARGE`, `TOO_MANY_ENTRIES`, `MANIFEST_PARSE_FAILED`, `MANIFEST_MISSING`; this plan adds `NOT_A_ZIP` if absent).
    - `src/main/java/org/ctc/backup/security/PathTraversalGuard.java` (delivered by Plan 02 — `public static void guard(Path root, String entryName)` throws `BackupArchiveException(Reason.ZIP_SLIP, ...)`).
    - `src/main/java/org/ctc/backup/service/BackupImportLimits.java` (delivered by Plan 02 — constants `MAX_ENTRY_BYTES`, `MAX_TOTAL_BYTES`, `MAX_ENTRIES`).
    - `src/main/java/org/ctc/domain/repository/SeasonRepository.java` — example `extends JpaRepository<Season, UUID>` shape; the 24 repositories live under `org/ctc/domain/repository/` (verify via `grep -l 'extends JpaRepository' src/main/java/org/ctc/domain/repository/`; expected: 24 repos + `DataImportAuditRepository` excluded by package filter).
    - `src/main/java/org/ctc/domain/service/FileStorageService.java` lines 30-46 + 153-158 — the `@Value("${app.upload-dir:uploads}")` constructor + `Paths.get(...).toAbsolutePath().normalize()` idiom this service mirrors for `stagingDir`.
  </read_first>

  <acceptance_criteria>
    1. File `src/main/java/org/ctc/backup/service/BackupImportService.java` exists, package `org.ctc.backup.service`, annotated `@Service @Slf4j @Transactional(readOnly = true)`.
    2. Class has exactly **one** explicit public constructor with four parameters: `BackupArchiveService`, `BackupSchema`, `List<JpaRepository<?, ?>>`, and `@Value("${app.backup.staging-dir}") String stagingDirRaw`. No `@RequiredArgsConstructor`.
    3. Constructor stores `Path stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();` — verifiable via grep `Paths.get(stagingDirRaw)`.
    4. `@PostConstruct` method named `wireRepositoriesByTableName` (or similar) populates `Map<String, JpaRepository<?, ?>> repositoryByTableName` by matching `EntityRef.entityClass()` against each injected repository's `JpaRepository<DomainType, UUID>` first type argument via `GenericTypeResolver.resolveTypeArguments`. Throws `IllegalStateException` if the resulting map size differs from `backupSchema.getExportOrder().size()`.
    5. `public BackupImportPreview stage(MultipartFile file) throws BackupArchiveException, IOException` exists with the documented body: (a) `Files.createDirectories(stagingDir)`, (b) ZIP magic-byte sniff via `readNBytes(4)` against `ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04}` throwing `Reason.NOT_A_ZIP` on mismatch, (c) UUID + staging-path allocation, (d) `PathTraversalGuard.guard(stagingDir, staged.toString())`, (e) `file.transferTo(staged)`, (f) `try/finally` reject-delete using the documented `boolean keep` flag, (g) inner call to `buildPreview` for the production preview build, (h) WARN log on rejection.
    6. `public BackupImportPreview reparse(UUID stagingId) throws BackupArchiveException, IOException` exists, resolves the staged path, throws `BackupArchiveException(Reason.MANIFEST_MISSING, ...)` when the staging file is absent, delegates to `buildPreview`, and **does NOT delete the staging file** on success or reject (Phase 75 inherits, per D-08).
    7. `public void deleteStagingFile(UUID stagingId)` exists with `void` return, swallows `IOException` (logs WARN, never throws), and uses `Files.deleteIfExists`.
    8. Private `BackupImportPreview buildPreview(...)` method exists with: (a) `backupArchive.readManifest(staged)`, (b) schema-version gate (`manifest.schemaVersion() != BackupSchema.SCHEMA_VERSION` → `Reason.SCHEMA_VERSION_MISMATCH` with **exact** D-02#2 string `"Schema version mismatch: backup=%d, expected=%d. Cannot import."`), (c) `backupArchive.countUploadFiles(staged)`, (d) iteration over `backupSchema.getExportOrder()` constructing `EntityRowCount` per entity via `repositoryByTableName.get(tableName).count()` for `currentRows` and `manifest.tableCounts().getOrDefault(tableName, 0L)` for `importedRows`, (e) `totalImportedRows` computed via `mapToLong(EntityRowCount::importedRows).sum()`, (f) returns a fully populated `BackupImportPreview`.
    9. Private `static String toHumanLabel(String tableName)` exists, converts `season_phases` to `Season Phases` (verifiable by a Surefire unit test if one is added; this plan ships only ITs but the helper is `static` for future testability).
    10. `Reason.NOT_A_ZIP` exists in `BackupArchiveException.Reason` (added by this plan if Plan 02 omitted it; the action notes this as a side-edit and the executor's first commit includes both files if needed).
    11. The service does NOT compile-link against any class that mutates DB state — verifiable via `grep -E '@(Modifying|PreUpdate|PrePersist|PreRemove)|JdbcTemplate|EntityManager' src/main/java/org/ctc/backup/service/BackupImportService.java` returns zero matches.
    12. The service does NOT contain `@SessionAttributes` or any in-memory `Map<UUID, BackupImportPreview>` cache — verifiable via `grep -E '@SessionAttributes|ConcurrentHashMap|Map<UUID' src/main/java/org/ctc/backup/service/BackupImportService.java` returns zero matches for caching shapes.
    13. The service compiles under `./mvnw -q compile` once Plans 02, 03, 04 are merged.
  </acceptance_criteria>

  <automated>./mvnw -q -pl . -Dtest='!*' -Dit.test='!*' compile</automated>

  <verify_notes>
  The `compile` invocation confirms the new file is syntactically valid Java 25 and that its imports resolve against the Plan 02/03/04 outputs. Behavioral verification lives in Tasks 74-05-02..74-05-05. The combined wave-2 final command is `./mvnw -q -Dtest='!*' -Dit.test='BackupImport*IT,BackupArchiveServiceReadIT' verify` (after this plan's commits are merged with Plans 04/06/07).
  </verify_notes>

  <done>`BackupImportService.java` exists with the three D-19 public methods plus the private `buildPreview` and `toHumanLabel` helpers; magic-byte sniff in `stage`; `try/finally` reject-delete in `stage` only; `reparse` and `deleteStagingFile` do NOT delete on reject (per D-08 inheritance and the cancel-button semantics); schema-version gate runs BEFORE any `repo.count()` call; 24 `EntityRowCount` cards are built in `BackupSchema.getExportOrder()` order; no DB writes; no session state. `./mvnw -q compile` passes.</done>
</task>

<task id="74-05-02" tdd="true">
  <title>Author `BackupImportServiceIT` — happy-path stage + reparse + delete with programmatic Phase-73 export ZIP</title>

  <behavior>
    - `givenPhase73Export_whenStage_thenPreviewHasNonZeroEntityCounts_andAll24CardsPopulated()`: produce a real Phase-73-shaped ZIP via `BackupArchiveService.writeZip(out, Instant.now())` (NOT a hand-rolled ZIP — proves wire-format compatibility); wrap the bytes in a `MockMultipartFile`; call `service.stage(file)`; assert: (a) preview is non-null, (b) `preview.stagingId()` is a fresh UUID, (c) `preview.originalFilename()` matches `MockMultipartFile.getOriginalFilename()`, (d) `preview.fileSizeBytes() == bytes.length`, (e) `preview.schemaVersion() == BackupSchema.SCHEMA_VERSION`, (f) `preview.currentSchemaVersion() == BackupSchema.SCHEMA_VERSION`, (g) `preview.schemaMatches() == true`, (h) `preview.entityCounts().size() == backupSchema.getExportOrder().size()` (24 in dev), (i) `preview.entityCounts()` is in `getExportOrder()` order — assert via `assertThat(preview.entityCounts()).extracting(EntityRowCount::tableName).containsExactlyElementsOf(getExportOrder().stream().map(EntityRef::tableName).toList())`, (j) each `EntityRowCount.humanLabel()` is non-blank, (k) `preview.uploadFileCount() >= 0` (dev fixture may have zero uploads — assert non-negative, not strictly positive, because `DevDataSeeder` does not necessarily seed `RaceAttachment` rows with files), (l) `preview.totalImportedRows() == preview.entityCounts().stream().mapToLong(EntityRowCount::importedRows).sum()` (self-consistency), (m) staging file at `data/{profile}/backup-staging/upload-{stagingId}.zip` exists after a successful stage (the file survives — Phase 75 inherits).
    - `givenStagedFile_whenReparse_thenReturnsEquivalentPreview_andStagingFileSurvives()`: chain on the previous test's `stage` (or restage in `@BeforeEach`); call `service.reparse(stagingId)`; assert: (a) returned preview is non-null, (b) `reparsed.schemaVersion() == originalPreview.schemaVersion()`, (c) `reparsed.entityCounts().size() == originalPreview.entityCounts().size()`, (d) for each `i`, `reparsed.entityCounts().get(i).tableName().equals(originalPreview.entityCounts().get(i).tableName())` and the `importedRows` matches (the `currentRows` may differ if DB state changed between calls, but in a single test method the DB is static — assert equality on both), (e) staging file STILL exists after `reparse` (D-08 — reparse does NOT delete).
    - `givenStagingId_whenDeleteStagingFile_thenFileGone()`: stage a file, capture its UUID, call `service.deleteStagingFile(stagingId)`, assert `Files.exists(staged) == false`. Re-invoke `service.deleteStagingFile(stagingId)` (second time on a missing file) — assert NO exception thrown (`Files.deleteIfExists` semantics; the service must not propagate failure).
    - `givenNonZipUpload_whenStage_thenThrowsNotAZip_andNoStagingFileWritten()`: create a `MockMultipartFile` with payload `"hello world".getBytes()` and content type `application/zip`; call `service.stage(file)`; assert `BackupArchiveException` thrown with `reason() == Reason.NOT_A_ZIP`; assert the staging dir contains no `upload-*.zip` after the failed call (the magic-byte sniff happens BEFORE `transferTo`, so no file is ever written for this reject path).
  </behavior>

  <action>
  Create `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` annotated `@SpringBootTest @ActiveProfiles("dev")` (matches `BackupArchiveServiceIT.java` lines 48-62). `@Autowired` fields: `BackupImportService service`, `BackupArchiveService backupArchive` (used to PRODUCE a real Phase-73 export ZIP — never to bypass the import path), `BackupSchema backupSchema`, `@Value("${app.backup.staging-dir}") String stagingDirRaw`. Resolve `Path stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();` in a `@BeforeEach` helper.

  In a `@BeforeEach` named `clearStagingDir()`: call `Files.createDirectories(stagingDir);` then walk and delete every `upload-*.zip` file (replicates Plan 07's startup-sweep at test-method granularity — IT must be order-independent). Use `try (var paths = Files.list(stagingDir)) { paths.filter(p -> p.getFileName().toString().startsWith("upload-")).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { /* ignore in @BeforeEach */ } }); }`.

  **`@BeforeAll static byte[] phase73ExportBytes(@Autowired BackupArchiveService archive) throws IOException`** — JUnit 5 `@BeforeAll` cannot inject directly without `@TestInstance(PER_CLASS)`. Choose: annotate the class `@TestInstance(Lifecycle.PER_CLASS)` so `@BeforeAll` is non-static and can use the `@Autowired` fields; OR use a private instance helper invoked lazily by each test. Prefer `@TestInstance(PER_CLASS)` — clearer intent, no laziness boilerplate. The helper produces the bytes once: `try (ByteArrayOutputStream out = new ByteArrayOutputStream()) { archive.writeZip(out, Instant.now()); this.phase73ZipBytes = out.toByteArray(); }`.

  Tests use AssertJ verbatim per CTC convention (`assertThat(...).as("rationale")`). Method names follow `givenContext_whenAction_thenExpectedResult` (CLAUDE.md "Test Naming"); body uses `// given` / `// when` / `// then` comments.

  For the NOT-A-ZIP test, payload bytes are `"hello world\n".getBytes(StandardCharsets.UTF_8)` — explicitly less than 4 bytes is also a valid input (e.g. empty payload), but using a short ASCII string is more readable and exercises the `header.length < 4` early-exit branch only secondarily. Prefer a payload that is ≥ 4 bytes but whose first 4 bytes are `{0x68, 0x65, 0x6C, 0x6C}` (`hell`) — proves the `Arrays.equals` comparison is doing the work.

  Use `MockMultipartFile(String name, String originalFilename, String contentType, byte[] content)` constructor — `new MockMultipartFile("file", "phase73-export.zip", "application/zip", phase73ZipBytes)`.

  Class header pattern (verbatim, executor copies):

  ```
  @SpringBootTest
  @ActiveProfiles("dev")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class BackupImportServiceIT {
      @Autowired BackupImportService service;
      @Autowired BackupArchiveService archive;
      @Autowired BackupSchema backupSchema;
      @Value("${app.backup.staging-dir}") String stagingDirRaw;
      Path stagingDir;
      byte[] phase73ZipBytes;

      @BeforeAll
      void produceFixtureBytes() throws IOException { ... }

      @BeforeEach
      void clearStagingDir() throws IOException { ... }
  }
  ```

  Place under `src/test/java/org/ctc/backup/service/` — same package as `BackupArchiveServiceIT` (visibility) and Surefire/Failsafe discovers via the `*IT.java` suffix (Failsafe).
  </action>

  <read_first>
    - `src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java` — class header pattern (`@SpringBootTest @ActiveProfiles("dev")`), `@Autowired` field injection, `writeZip` invocation that produces a valid wire-format ZIP, AssertJ idiom, `givenContext_whenAction_thenExpectedResult` method names.
    - `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` (if present — search via `find src/test -name '*RoundTrip*' -type f`) — proves manifest.tableCounts equals actual row counts; this test reuses that invariant.
    - `src/test/java/org/ctc/dataimport/CsvImportControllerTest.java` line 52 (RESEARCH §Standard Stack) — `MockMultipartFile` instantiation idiom.
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` row for `BackupImportServiceIT` — sampling rate, automated command, status field.
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` D-24 (test minimum), D-25 (programmatic fixtures only).
    - `src/main/java/org/ctc/backup/dto/BackupImportPreview.java` (Plan 03 — DTO field order for AssertJ field-extraction).
    - `src/main/java/org/ctc/backup/dto/EntityRowCount.java` (Plan 03 — DTO field order).
    - `src/main/java/org/ctc/backup/schema/BackupSchema.java` line 33 (`SCHEMA_VERSION = 1`).
    - `CLAUDE.md` §"Development Approach" — Given-When-Then test naming, `// given/when/then` comments, `assertThat(...).as("...")` AssertJ rationale.
  </read_first>

  <acceptance_criteria>
    1. File `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` exists, package `org.ctc.backup.service`, annotated `@SpringBootTest @ActiveProfiles("dev") @TestInstance(Lifecycle.PER_CLASS)`.
    2. Contains four test methods with the exact `givenContext_whenAction_thenExpectedResult` names listed in `<behavior>`.
    3. The happy-path test produces its fixture via `BackupArchiveService.writeZip(out, Instant.now())` — NO committed binary blob under `src/test/resources/` is referenced (verifiable via grep that the test file does NOT contain `new FileInputStream(...)`).
    4. The `@BeforeEach` clears the staging dir of `upload-*.zip` files (so consecutive test methods do not pollute each other's assertions).
    5. AssertJ `assertThat(...).containsExactlyElementsOf(...)` proves the `entityCounts` order matches `getExportOrder()` order exactly.
    6. The NOT-A-ZIP test asserts `assertThat(Files.list(stagingDir).count()).isZero()` (no file was written — magic-byte sniff is the first check).
    7. The reparse test asserts the staging file STILL exists after `reparse` returns successfully (D-08 invariant).
    8. The delete-twice test confirms the second `deleteStagingFile` call returns normally (no exception, no error log assertion needed — silent idempotency is the contract).
    9. `./mvnw -q -Dit.test=BackupImportServiceIT verify` runs all four methods and passes once all Wave-2 plans are merged.
  </acceptance_criteria>

  <automated>./mvnw -q -Dit.test=BackupImportServiceIT verify</automated>

  <verify_notes>
  Failsafe (`*IT.java` suffix) discovers the test under the `verify` lifecycle goal (Surefire skips IT files via the `**/*IT.java` exclusion in `pom.xml`). The `-Dit.test=` flag targets only this class — keeps feedback latency under 90 s per VALIDATION.md. Full-wave verification adds the other three ITs in this plan and the cross-plan ITs (`BackupArchiveServiceReadIT` from Plan 04, `BackupStagingCleanupIT` from Plan 07).
  </verify_notes>

  <done>The four happy-path / reparse / delete / not-a-zip tests exist with the documented `givenContext_whenAction_thenExpectedResult` names and AssertJ assertions; the test uses a runtime-produced Phase-73 export (no committed binary fixture); all four methods pass under `./mvnw -q -Dit.test=BackupImportServiceIT verify` after Wave-2 plans merge.</done>
</task>

<task id="74-05-03" tdd="true">
  <title>Author `BackupImportSchemaVersionMismatchIT` — forged-manifest reject + 24-entity DB-unchanged proof (SC#2)</title>

  <behavior>
    - `givenForgedManifestSchemaVersion999_whenStage_thenThrowsSchemaMismatch_andDbUnchanged_andStagingFileDeleted()`:
      (a) Produce a "forged" ZIP via `ZipOutputStream` in the test (programmatic per D-25) — entry #0 is `manifest.json` with body `{"schema_version":999,"app_version":"forged","export_date":"2026-05-12T00:00:00Z","table_counts":{}}` (Jackson `backupObjectMapper`-compatible JSON); entries #1..#24 are valid empty JSON arrays `[]` at paths matching `EntityRef.fileName()` (so the ZIP is a structurally-complete-but-version-wrong archive — defense-in-depth proves the gate fires before any data-file read happens).
      (b) Snapshot `Map<String, Long> before = backupSchema.getExportOrder().stream().collect(Collectors.toMap(EntityRef::tableName, ref -> repositoryByTableName.get(ref.tableName()).count()));` (use the same `@PostConstruct`-built map the service uses, exposed via a test-only `@Component` helper OR re-built in the IT via the same `GenericTypeResolver` walk).
      (c) Call `service.stage(forgedFile)`; assert `BackupArchiveException` thrown with `reason() == Reason.SCHEMA_VERSION_MISMATCH` AND message contains `"backup=999"` AND `"expected=1"` (or whatever `BackupSchema.SCHEMA_VERSION` reads at test time — use `String.format("backup=%d, expected=%d", 999, BackupSchema.SCHEMA_VERSION)` substring assertion).
      (d) Snapshot `Map<String, Long> after = ...same expression...;`.
      (e) Assert `assertThat(after).as("Schema mismatch must run BEFORE any DB read; row counts must be byte-identical").isEqualTo(before);` (Map equality — both keys AND values must match).
      (f) Assert the staging file was deleted: `assertThat(Files.list(stagingDir).filter(p -> p.getFileName().toString().endsWith(".zip")).count()).isZero();` (the `try/finally` in `stage` deleted it).
    - `givenForgedManifestSchemaVersionMinusOne_whenStage_thenThrowsSchemaMismatch()`: same shape, `schema_version: -1` — proves the gate is `!=`, not `<` (forward and backward incompatibility both reject).

  </behavior>

  <action>
  Create `src/test/java/org/ctc/backup/service/BackupImportSchemaVersionMismatchIT.java` mirroring the `BackupImportServiceIT` class shape (`@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS)`).

  Wire a static helper method `static byte[] forgedManifestZip(int forgedSchemaVersion, BackupSchema schema, ObjectMapper backupObjectMapper) throws IOException` (place it as a private static method in the test class — no helper class extraction needed; the planner does NOT introduce a shared `ZipFixtureFactory` until at least 2 IT classes share the exact same code shape, per CLAUDE.md "No fallback calculations" antipattern). Implementation:

  1. `ByteArrayOutputStream out = new ByteArrayOutputStream();`
  2. `try (ZipOutputStream zip = new ZipOutputStream(out)) { ...write entries... }` — exactly the same shape Phase 73's `BackupArchiveService.writeZip` uses, lines 97-138.
  3. Entry #0: `zip.putNextEntry(new ZipEntry("manifest.json"));` then write JSON `String.format("{\"schema_version\":%d,\"app_version\":\"forged\",\"export_date\":\"2026-05-12T00:00:00Z\",\"table_counts\":{}}", forgedSchemaVersion);` as bytes via `zip.write(json.getBytes(StandardCharsets.UTF_8))`; `zip.closeEntry()`.
  4. Entries #1..#24: for each `EntityRef ref : schema.getExportOrder()`, `zip.putNextEntry(new ZipEntry(ref.fileName()))` (which is `data/<table>.json`); `zip.write("[]".getBytes(StandardCharsets.UTF_8))`; `zip.closeEntry()`. Empty arrays are valid Plan 04 `countDataEntries` inputs.
  5. `zip.finish();` — return `out.toByteArray()`.

  Inject `@Autowired @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper` (needed if the test ever rebuilds the manifest via Jackson — current implementation hand-writes the JSON for explicit byte control, so `backupObjectMapper` is `@Autowired` only for future-proofing OR the test omits it entirely and relies on the hand-built JSON string).

  Inject 24 repositories explicitly (not a `List<JpaRepository>`) — `@Autowired SeasonRepository seasonRepository; @Autowired DriverRepository driverRepository; ...` — to capture the BEFORE/AFTER snapshot. This avoids reflective coupling between the IT and the service's `repositoryByTableName` map (the IT is a black-box behavioral test; it does not peer into the service's internals). Build the snapshot via a private helper:

  ```
  private Map<String, Long> snapshotAllCounts() {
      Map<String, Long> m = new LinkedHashMap<>();
      m.put("seasons", seasonRepository.count());
      m.put("season_phases", seasonPhaseRepository.count());
      // ... all 24 tables, in any order; the test uses Map equality, not list equality
      return m;
  }
  ```

  The 24 table names come from `EntityRef.tableName()` for each entity — list them by reading `@Table(name=...)` on each entity class. This is tedious but a one-time write; an alternative is to inject `BackupSchema backupSchema` and `List<JpaRepository<?, ?>>` and reuse the same `GenericTypeResolver` walk the service uses (but that re-implements internal logic in a test, violating the "test behavior, not implementation" rule). Prefer explicit injection.

  Test methods follow the same Given-When-Then naming, with `// given / when / then` comments. Use `assertThatThrownBy` from AssertJ for exception assertions:

  ```
  assertThatThrownBy(() -> service.stage(new MockMultipartFile("file", "forged.zip", "application/zip", forgedBytes)))
      .isInstanceOf(BackupArchiveException.class)
      .satisfies(t -> assertThat(((BackupArchiveException) t).reason()).isEqualTo(Reason.SCHEMA_VERSION_MISMATCH))
      .hasMessageContaining("backup=999")
      .hasMessageContaining("expected=" + BackupSchema.SCHEMA_VERSION);
  ```
  </action>

  <read_first>
    - `src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java` — `ZipOutputStream`-based fixture authoring shape (lines 64-77 use `writeZip` then `readEntryNames`; this test inverts — it writes the malicious ZIP directly).
    - `src/main/java/org/ctc/backup/service/BackupArchiveService.java` lines 97-138 (the `writeZip` shape this test's fixture builder mirrors structurally).
    - `src/main/java/org/ctc/backup/schema/BackupManifest.java` — JSON shape: `schema_version`, `app_version`, `export_date`, `table_counts`. The hand-built JSON in the fixture builder must match these snake_case keys (`backupObjectMapper` has `FAIL_ON_UNKNOWN_PROPERTIES=true`).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` SC#2 row — observable assertion is `Repository.count()` snapshot byte-identical before/after.
    - All 24 `*Repository.java` in `src/main/java/org/ctc/domain/repository/` plus `DataImportAuditRepository.java` in `src/main/java/org/ctc/backup/audit/` (the latter is filtered out — do NOT inject).
    - `src/main/java/org/ctc/backup/exception/BackupArchiveException.java` (Plan 02 — confirm `Reason.SCHEMA_VERSION_MISMATCH` enum value name; if Plan 02 named it `SCHEMA_MISMATCH`, use that exact constant).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` D-02#2 (locked English message), D-09 (schema gate before any DB write — this test PROVES that invariant).
  </read_first>

  <acceptance_criteria>
    1. File `src/test/java/org/ctc/backup/service/BackupImportSchemaVersionMismatchIT.java` exists, two test methods (`schema_version=999` and `schema_version=-1`).
    2. The forged ZIP fixture is produced **inside the test class** via `ZipOutputStream` (no `src/test/resources/backup-fixtures/malicious/` binary files referenced).
    3. Both test methods assert: `BackupArchiveException` thrown, `reason() == Reason.SCHEMA_VERSION_MISMATCH`, message contains the substring `"backup=<forged>"` and `"expected=" + BackupSchema.SCHEMA_VERSION`.
    4. The 999-variant test additionally asserts the BEFORE/AFTER row-count snapshot is `isEqualTo` (Map equality — same keys, same values).
    5. The 999-variant test additionally asserts the staging dir contains zero `*.zip` files after the rejection (the `try/finally` reject-delete fired).
    6. The 24 repositories are individually `@Autowired` (no reflective discovery in test code — black-box behavioral test).
    7. `./mvnw -q -Dit.test=BackupImportSchemaVersionMismatchIT verify` passes once all Wave-2 plans are merged.
  </acceptance_criteria>

  <automated>./mvnw -q -Dit.test=BackupImportSchemaVersionMismatchIT verify</automated>

  <verify_notes>
  This IT owns Validation SC#2 (per `74-VALIDATION.md`). The Map-equality assertion is the canonical "DB unchanged" proof — stronger than spot-checking 2 or 3 tables, and resistant to subtle bugs where the gate runs after some-but-not-all counts.
  </verify_notes>

  <done>Two-test IT proves the schema gate fires BEFORE any of the 24 `repo.count()` calls (because the BEFORE/AFTER snapshots are equal even though the schema is mismatched and the gate-failure happens after the magic-sniff but before the count loop). Staging-file deletion on reject is also proven. Passes under `./mvnw -q -Dit.test=BackupImportSchemaVersionMismatchIT verify`.</done>
</task>

<task id="74-05-04" tdd="true">
  <title>Author `BackupImportZipSlipIT` — `../../etc/passwd` and absolute-path entry rejection + staging deletion</title>

  <behavior>
    - `givenZipWithDotDotEntry_whenStage_thenThrowsPathTraversal_andStagingFileDeleted()`: programmatic ZIP whose entry #0 is `manifest.json` (valid) and entry #1 is `../../etc/passwd` (or `../../../etc/passwd` — any `..`-bearing path triggers the `PathTraversalGuard`). Call `service.stage(file)`; assert `BackupArchiveException` thrown with `reason() == Reason.ZIP_SLIP`. Assert the staging dir is empty (the try/finally deleted the staged file). Note: depending on Plan 04's reader implementation, the ZIP-Slip check may fire either inside `readManifest` (if manifest is the offending entry) or inside `countDataEntries`/`countUploadFiles`; this test puts the offending entry AFTER the manifest so the reject happens during the count phase — proving the hardening is end-to-end, not just on entry #0.
    - `givenZipWithAbsolutePathEntry_whenStage_thenThrowsPathTraversal()`: programmatic ZIP whose entry has name `/etc/passwd` (leading slash — absolute path). Same assertion shape. `PathTraversalGuard.guard` rejects absolute paths because `Paths.get(stagingDir).resolve("/etc/passwd")` resolves to `/etc/passwd` (Path.resolve replaces with absolute argument), and `.normalize().startsWith(stagingDir)` is false.
    - `givenZipWithDotDotEntryInUploadsPath_whenStage_thenThrowsPathTraversal()`: programmatic ZIP whose entry name is `uploads/../../etc/passwd` — proves the check works on `uploads/`-prefixed paths (the upload counter is the secondary attack surface).
  </behavior>

  <action>
  Create `src/test/java/org/ctc/backup/service/BackupImportZipSlipIT.java`, same class shape as `BackupImportServiceIT` (`@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS)`).

  Helper `static byte[] zipWithEntryName(String maliciousEntryName, BackupSchema schema) throws IOException`:

  1. `ZipOutputStream zip = new ZipOutputStream(out);`
  2. Entry #0 — valid `manifest.json` with `schema_version=1`, `table_counts={}` (so the schema gate doesn't reject before the traversal check).
  3. Entry #1 — `zip.putNextEntry(new ZipEntry(maliciousEntryName));` `zip.write("malicious".getBytes());` `zip.closeEntry();`
  4. Finish.

  Inject `service`. Three test methods, AssertJ `assertThatThrownBy`:

  ```
  assertThatThrownBy(() -> service.stage(new MockMultipartFile("file", "slip.zip", "application/zip", maliciousBytes)))
      .isInstanceOf(BackupArchiveException.class)
      .satisfies(t -> assertThat(((BackupArchiveException) t).reason()).isEqualTo(Reason.ZIP_SLIP));
  ```

  After each rejection, assert the staging dir is empty: `assertThat(Files.list(stagingDir).filter(p -> p.getFileName().toString().endsWith(".zip")).count()).isZero();`.

  Note: the `manifest.json` in entry #0 must be byte-valid for `backupObjectMapper.readValue(InputStream, BackupManifest.class)` (Plan 04's `readManifest`). Hand-write the JSON string per the `BackupManifest` `@JsonProperty` annotations: `{"schema_version":1,"app_version":"test","export_date":"2026-05-12T00:00:00Z","table_counts":{}}`. Plan 04 may also have its own `manifest-first` assertion; verify by reading Plan 04's `readManifest` source once Plan 04 is merged — if it rejects an empty `table_counts` map, the helper must include at least the 24 keys with value `0L`. The `BackupManifest` record signature `Map<String, Long> tableCounts` accepts both shapes.
  </action>

  <read_first>
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` D-11 (path-traversal pattern), D-24 (test minimum: `../../etc/passwd` and absolute path).
    - `src/main/java/org/ctc/domain/service/FileStorageService.java` lines 153-158 — the `startsWith(uploadDir.toRealPath())` predicate the `PathTraversalGuard` extracts.
    - `src/main/java/org/ctc/backup/security/PathTraversalGuard.java` (Plan 02) — exact method signature.
    - `src/main/java/org/ctc/backup/service/BackupArchiveService.java` lines 119-126 (export-side ZIP-Slip skip — the read side mirror throws instead of skipping).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` SC#3 row.
  </read_first>

  <acceptance_criteria>
    1. File `src/test/java/org/ctc/backup/service/BackupImportZipSlipIT.java` exists with three test methods (dot-dot, absolute path, dot-dot-in-uploads).
    2. Fixtures are programmatically generated; no committed binaries.
    3. All three tests assert `BackupArchiveException` with `reason() == Reason.ZIP_SLIP`.
    4. All three tests assert the staging dir contains zero `*.zip` files after rejection.
    5. The manifest entry #0 in each fixture is byte-valid for `backupObjectMapper` strict parsing — proven by the test passing (a malformed manifest would throw `Reason.MANIFEST_PARSE_FAILED` before the traversal check ever runs).
    6. `./mvnw -q -Dit.test=BackupImportZipSlipIT verify` passes.
  </acceptance_criteria>

  <automated>./mvnw -q -Dit.test=BackupImportZipSlipIT verify</automated>

  <verify_notes>
  Owns SC#3 (per VALIDATION.md). The "uploads/.. /etc/passwd" test is the strongest defense — proves the hardening is applied to EVERY entry name, not just manifest/data entries.
  </verify_notes>

  <done>Three-test IT proves ZIP-Slip rejection across the three attack shapes (dot-dot, absolute, dot-dot-under-uploads); staging-file deletion on reject is also proven for each. Passes under `./mvnw -q -Dit.test=BackupImportZipSlipIT verify`.</done>
</task>

<task id="74-05-05" tdd="true">
  <title>Author `BackupImportZipBombIT` — entry / total / entry-count limit rejections</title>

  <behavior>
    - `givenEntryWithInflatedSizeExceedingLimit_whenStage_thenThrowsEntryTooLarge()`: programmatic ZIP whose entry #0 is valid manifest, entry #1 is a ZIP entry with `setSize(Long.MAX_VALUE)` (header lies) and a payload that, when inflated through `LimitedInputStream`, trips the per-entry 50 MB cap. Use a low-entropy payload (`new byte[60 * 1024 * 1024]` filled with zeros — Deflate compresses this very small, so the ZIP fixture stays under 200 KB on disk; inflation explodes back to 60 MB which exceeds `MAX_ENTRY_BYTES`). Assert `BackupArchiveException` with `reason() == Reason.ENTRY_TOO_LARGE`.
    - `givenTotalInflatedSizeExceedingLimit_whenStage_thenThrowsTotalTooLarge()`: programmatic ZIP with valid manifest + ~12 entries each ~45 MB inflated zeros (each under `MAX_ENTRY_BYTES` per-entry, but cumulatively > 500 MB total). Assert `BackupArchiveException` with `reason() == Reason.TOTAL_TOO_LARGE`. Note: this fixture is small on disk (~12 × ~200 KB = ~2.4 MB) but tests inflation accounting.
    - `givenEntryCountExceedingLimit_whenStage_thenThrowsTooManyEntries()`: programmatic ZIP with valid manifest + 50,001 trivial 1-byte entries. Assert `BackupArchiveException` with `reason() == Reason.TOO_MANY_ENTRIES`. The fixture is large in entry count but small in bytes (~50 KB on disk).
  </behavior>

  <action>
  Create `src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java`, same class shape as the others.

  Three private static helpers:

  ```
  static byte[] inflationBombZip(long perEntryInflatedBytes) throws IOException {
      // produces 1 valid manifest + 1 entry with setSize(Long.MAX_VALUE) and N zeros payload
  }

  static byte[] totalSizeBombZip(int entryCount, long perEntryInflatedBytes) throws IOException {
      // produces 1 valid manifest + entryCount entries each of perEntryInflatedBytes zeros
  }

  static byte[] entryCountBombZip(int entryCount) throws IOException {
      // produces 1 valid manifest + entryCount 1-byte entries
  }
  ```

  Performance note for the executor: the 12-entry × 45-MB-zeros test produces ~540 MB of zeros into the `ZipOutputStream`. `ZipOutputStream` compresses streamingly so the in-memory `ByteArrayOutputStream` stays small (~3 MB). The 50,001-entry test creates 50k `ZipEntry` objects but each writes 1 byte — fast (~5 s on a modern dev machine). If the test exceeds 60 s wall-clock under `./mvnw -q -Dit.test=BackupImportZipBombIT verify` on the CI runner, reduce `entryCount` to `BackupImportLimits.MAX_ENTRIES + 1` (constant access via Plan 02's `BackupImportLimits`) — the exact constant + 1, not a hand-coded `50_001`.

  Test methods use `assertThatThrownBy` with the same `reason()` assertion pattern as the previous tasks.

  Assert staging-dir empty after each rejection.

  Use `BackupImportLimits` constants in BOTH the fixture builder (`new byte[(int)(BackupImportLimits.MAX_ENTRY_BYTES + 1)]`) AND the assertion message — keeps the test in lockstep with Plan 02's constants. If Plan 02 changes the limit values, this test recompiles and re-tests against the new values automatically.

  For the `Long.MAX_VALUE` size-lie test, use `ZipEntry entry = new ZipEntry("data/seasons.json"); entry.setSize(Long.MAX_VALUE); zip.putNextEntry(entry);` — the JDK's `ZipOutputStream` writes the lied size into the local-file-header; Plan 04's `readManifest`/`countDataEntries` invokes Plan 02's `LimitedInputStream` which counts actual inflated bytes, ignoring the header lie. The trip-point is the 50 MB inflated-count.
  </action>

  <read_first>
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` D-12 (limits + inflated-byte counter + don't trust `ZipEntry.getSize()`).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md` lines 770-788 (Pitfall 6+7 — staging leak, multipart contract).
    - `src/main/java/org/ctc/backup/service/BackupImportLimits.java` (Plan 02 — `MAX_ENTRY_BYTES`, `MAX_TOTAL_BYTES`, `MAX_ENTRIES` constants).
    - `src/main/java/org/ctc/backup/io/LimitedInputStream.java` (Plan 02 — the wrapper Plan 04 uses).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md` row for `BackupImportZipBombIT`.
  </read_first>

  <acceptance_criteria>
    1. File `src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java` exists with exactly three test methods covering: per-entry inflated-size bomb, total inflated-size bomb, entry-count bomb.
    2. Each fixture is programmatically generated via `ZipOutputStream` — no committed binary.
    3. The three tests assert `BackupArchiveException` with `reason()` equal to `ENTRY_TOO_LARGE`, `TOTAL_TOO_LARGE`, `TOO_MANY_ENTRIES` respectively.
    4. Each test asserts the staging dir is empty after rejection (try/finally cleanup fired).
    5. Test runtime under 90 s on a dev machine (verified empirically — if not, scale fixture sizes down to `BackupImportLimits.MAX_* + 1`).
    6. The per-entry test uses `ZipEntry.setSize(Long.MAX_VALUE)` to prove `LimitedInputStream` defends against header-size spoofing.
    7. `./mvnw -q -Dit.test=BackupImportZipBombIT verify` passes.
  </acceptance_criteria>

  <automated>./mvnw -q -Dit.test=BackupImportZipBombIT verify</automated>

  <verify_notes>
  Owns half of SC#3 (per VALIDATION.md — ZipBomb defense). The `Long.MAX_VALUE` size-lie test specifically exercises Plan 02's `LimitedInputStream` rather than `ZipEntry.getSize()`-trust — the canonical CVE for naive ZIP-bomb defenses.
  </verify_notes>

  <done>Three-test IT proves all three ZipBomb attack shapes are rejected with the correct `Reason`; staging-file deletion on reject is also proven for each; `Long.MAX_VALUE` size-spoofing is defended by `LimitedInputStream` (not by `ZipEntry.getSize()` trust). Passes under `./mvnw -q -Dit.test=BackupImportZipBombIT verify`.</done>
</task>

## Verification

### must_haves

**Truths:**
- `BackupImportService.stage(MultipartFile)` returns a `BackupImportPreview` with 24 entity cards populated in `BackupSchema.getExportOrder()` order, with `currentRows` sourced from `Repository.count()` and `importedRows` sourced from `manifest.tableCounts()`. (SC#1 — preview page renders; without this, `BackupImportE2ETest` in Plan 10 has nothing to render.)
- `BackupImportService.stage` rejects non-ZIP uploads via the magic-byte sniff BEFORE writing any file to the staging directory. (RESEARCH §Pattern 5; the magic-sniff is the cheapest pre-disk check and prevents staging-dir pollution by `.txt` renames.)
- `BackupImportService.stage` rejects schema-version mismatches BEFORE invoking any `Repository.count()` — the 24 `Repository.count()` snapshot before the rejected call is byte-identical to the snapshot after. (SC#2 — the SECU-04 + IMPORT-02 promise; `BackupImportSchemaVersionMismatchIT` proves this via Map equality, the strongest possible "DB unchanged" assertion.)
- `BackupImportService.stage` invokes Plan 04's `readManifest` / `countUploadFiles` which run through Plan 02's `LimitedInputStream` + `PathTraversalGuard` — so ZIP-Slip (`../`, absolute, `uploads/../`) and ZipBomb (per-entry 50 MB, total 500 MB, count 50 000) attacks throw `BackupArchiveException` with the correct `Reason` and trigger staging-file deletion. (SC#3.)
- After ANY rejected `stage` call, the staging file at `data/{profile}/backup-staging/upload-{stagingId}.zip` does NOT exist on disk. (D-16; the `try/finally` `boolean keep` flag flipped only on success, with `Files.deleteIfExists` in `finally`.)
- After a SUCCESSFUL `stage` call, the staging file DOES exist on disk and survives subsequent `reparse(UUID)` calls; only an explicit `deleteStagingFile(UUID)` or Phase 75's `import-execute` (future) removes it. (D-08 inheritance for Phase 75.)
- `BackupImportService.reparse(UUID)` is **stateless** — it reads only from disk (no `@SessionAttributes`, no in-memory `Map<UUID, Preview>`) and re-runs the full validation chain so the schema gate fires at execute-time as well as preview-time. (D-09 defense-in-depth.)
- `BackupImportService.deleteStagingFile(UUID)` is **idempotent and exception-safe** — calling twice on the same UUID does NOT throw; a missing staging file is logged at INFO and ignored.
- `BackupImportService` does NOT mutate the database — verifiable by grep: the file contains no `@Modifying`, no `JdbcTemplate`, no `EntityManager.persist`/`merge`/`remove`, no `repo.save`/`saveAll`/`delete`. The class annotation `@Transactional(readOnly = true)` makes the constraint declarative.

**Artifacts:**
- `src/main/java/org/ctc/backup/service/BackupImportService.java` — exists with the three D-19 public methods (`stage`, `reparse`, `deleteStagingFile`), the private `buildPreview` and static `toHumanLabel` helpers, the `@PostConstruct` repository-by-tableName map builder, and explicit-constructor injection of `BackupArchiveService` + `BackupSchema` + `List<JpaRepository<?, ?>>` + `@Value("${app.backup.staging-dir}") String`.
- `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` — happy-path stage + reparse + delete + not-a-zip; fixture produced via Phase-73 `BackupArchiveService.writeZip` (no binary blob).
- `src/test/java/org/ctc/backup/service/BackupImportSchemaVersionMismatchIT.java` — `schema_version=999` and `schema_version=-1` rejection + BEFORE/AFTER Map-equality DB-unchanged snapshot + staging-file-deleted assertion.
- `src/test/java/org/ctc/backup/service/BackupImportZipSlipIT.java` — `../../etc/passwd`, `/etc/passwd`, and `uploads/../../etc/passwd` rejection + staging-file-deleted assertion.
- `src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java` — per-entry inflated, total inflated, entry-count bomb rejection + `ZipEntry.setSize(Long.MAX_VALUE)` size-spoof defense.

**Test Classes:**
- `BackupImportServiceIT` (Wave 2 — owns SC#1 partial; SC#5 partial via the stateless `reparse` chain).
- `BackupImportSchemaVersionMismatchIT` (Wave 2 — owns SC#2 in full; the 24-table `Repository.count()` Map-equality snapshot is the canonical "schema gate runs before any DB read" proof).
- `BackupImportZipSlipIT` (Wave 2 — owns half of SC#3; the three attack shapes cover the entire `PathTraversalGuard` surface).
- `BackupImportZipBombIT` (Wave 2 — owns the other half of SC#3; `Long.MAX_VALUE` size-spoof defense is the canonical CVE proof).

## Notes

### Why `List<JpaRepository<?, ?>>` injection + `@PostConstruct` map-build (not `Map<String, JpaRepository<?, ?>>`)

Spring's `Map<String, T>` injection populates with `beanName -> bean` (e.g. `seasonRepository -> SeasonRepository proxy`). The `BackupImportService` needs `tableName -> repository` (e.g. `seasons -> SeasonRepository`). There is no first-class Spring mechanism to bridge these. Three options were considered:

1. **`List<JpaRepository<?, ?>>` + `@PostConstruct` map-build via `GenericTypeResolver` (chosen).** Spring auto-collects every `JpaRepository` bean into the list (one of the few Spring-side benefits of having all 25 repositories extend the same interface). The `@PostConstruct` matches each by the entity class extracted from the JPA-typed `<T, ID>` arguments. Pros: zero per-repository wiring; no annotation; fail-fast on bootstrap if a repo is missing. Cons: tiny reflective hop.
2. **Static `Map<Class<?>, Class<? extends JpaRepository>>` table in `BackupSchema`.** Pros: explicit. Cons: maintained list duplicates `@Repository`-discovery; new entity additions require both `@Table` and a manual table-row.
3. **Inject all 24 repositories explicitly into `BackupImportService`.** Pros: explicit and grep-friendly. Cons: 24 constructor parameters (Lombok cannot help with the explicit-constructor-for-`@Value` shape); cosmetic regression on next-entity addition.

Option 1 is the planner's chosen approach. It's also the one the `BackupSchema` itself uses for entity discovery (JPA Metamodel) — symmetric design.

### Why `reparse` does NOT delete the staging file on reject

D-08 (line 43 of CONTEXT.md): Phase 75 inherits the staging file. The `import-execute` stub re-reads, re-validates, and Phase 75 will delete only after a successful execute. If `reparse` deleted on reject, the operator's "retry the same upload" workflow (e.g. transient DB hiccup mid-execute) would lose the staged file. The startup-sweep listener (Plan 07) is the safety net for stale-file accumulation; daily growth under normal operations is bounded at ~2 files per week per CONTEXT.md.

### Why `BackupImportException.Reason.NOT_A_ZIP` may need to be added by this plan

Plan 02 defined the `Reason` enum during Wave 1, before this plan was written. The exact enum values delivered by Plan 02 are `SCHEMA_VERSION_MISMATCH`, `ZIP_SLIP`, `ENTRY_TOO_LARGE`, `TOTAL_TOO_LARGE`, `TOO_MANY_ENTRIES`, `MANIFEST_PARSE_FAILED`, `MANIFEST_MISSING` (per PATTERNS.md §`BackupArchiveException` lines 282-301). The magic-byte-sniff failure does not cleanly fit any of these (it's a pre-ZIP-parse rejection — none of the ZIP-internal reasons apply). The cleanest path is to add `NOT_A_ZIP` to the enum during this plan's first task. The executor checks Plan 02's enum before writing and conditionally edits `BackupArchiveException.java` to add the missing value. Acceptance Criterion 10 of Task 74-05-01 captures this contingency.

### Why the schema gate is BEFORE the count loop, not AFTER

D-09 (line 44 of CONTEXT.md) is explicit: schema-version mismatch is rejected at preview-upload time AND re-checked at execute time. The "no DB read on mismatch" invariant is what `BackupImportSchemaVersionMismatchIT` verifies. If the gate ran after the count loop, the IT would still see byte-identical snapshots (because `count()` is read-only), but the wall-clock cost of the 24 SELECTs would be wasted on every rejected attempt. The chosen order is "fail fast" — gate first, count second. This also means a schema-version-bumped backup against an empty DB does not trigger any SELECTs at all, which matters for the `@Transactional(readOnly = true)` boundary.

### Why `Map.getOrDefault(..., 0L)` for `importedRows`

Phase 73's `writeZip` populates `tableCounts` for every entity in `getExportOrder()` (`BackupExportService.countRowsPerTable()` returns 24 keys). A defensively-malformed manifest with missing keys would still be rejected at the `backupObjectMapper` strict-parse step (`FAIL_ON_UNKNOWN_PROPERTIES=true` plus the `BackupManifest` record's `Map<String, Long>` field — empty map is valid, missing-key lookups default to 0L, **not** to a parse error). The `0L` default makes the preview render gracefully on edge-case backups (e.g. a backup taken just after a fresh DB init with zero rows everywhere) without surfacing as a parse rejection.

### Why magic-byte sniff is BEFORE `transferTo`, not after

Two reasons:
1. **No useless disk writes.** A 100 MB `.txt` rename'd to `.zip` would otherwise hit the staging dir for 100 ms before being detected and deleted; the sniff catches it at the 4-byte read, before `transferTo`.
2. **No useless `try/finally` for the magic-sniff path.** The `transferTo` happens AFTER the sniff, so the `keep` flag flow does not need to consider the sniff-failure case (it throws cleanly before any staging-file allocation, and there is no file to delete in `finally`). The code reads more linearly with this ordering.

## PLAN COMPLETE 05
