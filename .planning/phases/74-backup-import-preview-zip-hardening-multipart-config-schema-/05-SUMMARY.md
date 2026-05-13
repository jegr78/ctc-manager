---
phase: 74
plan: "05"
subsystem: backup-import
tags: [backup, import, staging, zip-hardening, schema-gate, multipart, preview]
dependency_graph:
  requires:
    - "74-02 (BackupArchiveException Reason enum, BackupImportLimits, PathTraversalGuard)"
    - "74-03 (BackupImportPreview, EntityRowCount records)"
    - "74-04 (BackupArchiveService.readManifest, countDataEntries, countUploadFiles)"
  provides:
    - BackupImportService.stage(MultipartFile) -> BackupImportPreview
    - BackupImportService.reparse(UUID) -> BackupImportPreview
    - BackupImportService.deleteStagingFile(UUID)
  affects:
    - Plan 06 (BackupController.uploadPreview calls service.stage)
    - Plan 08 (BackupController calls reparse, deleteStagingFile)
    - Plan 10 (BackupImportE2ETest — preview page rendering)
tech_stack:
  added: []
  patterns:
    - "boolean keep flag + try/finally reject-delete (D-16 staging-file lifecycle)"
    - "GenericTypeResolver.resolveTypeArguments for @PostConstruct repo-by-tableName map"
    - "ZIP magic-byte sniff via readNBytes(4) BEFORE file.transferTo(staged)"
    - "schema-version gate BEFORE any Repository.count() call (D-09 SC#2 invariant)"
    - "toHumanLabel static helper: snake_case -> Title Words"
key_files:
  created:
    - src/main/java/org/ctc/backup/service/BackupImportService.java
    - src/test/java/org/ctc/backup/service/BackupImportServiceIT.java
    - src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java
    - src/test/java/org/ctc/backup/service/BackupImportZipSlipIT.java
    - src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java
  modified: []
decisions:
  - "Bomb-test fixtures use uploads/ entries (not data/) because countUploadFiles() is the only method in the stage() pipeline that drains upload entries through LimitedInputStream; countDataEntries() is not called in stage()"
  - "reparse() does NOT delete staging file on reject — D-08 Phase 75 inheritance; Plan 07 startup-sweep is the safety net for stale files"
  - "repositoryByTableName built via GenericTypeResolver walk over List<JpaRepository<?,?>> — avoids 24 constructor parameters while preserving fail-fast @PostConstruct size assertion"
  - "MANIFEST_MISSING reused in reparse() for missing staging file — no separate STAGING_FILE_MISSING Reason value needed (semantic overlap documented in JavaDoc)"
metrics:
  duration_seconds: ~65
  completed_date: "2026-05-13"
  tasks_completed: 5
  commits: 6
  test_methods: 12
---

# Phase 74 Plan 05: BackupImportService Summary

`BackupImportService` — stateless, write-free orchestrator that stages a `MultipartFile` ZIP upload, sniffs ZIP magic bytes before touching disk, enforces the schema-version gate before any DB read, and assembles a `BackupImportPreview` with 24 `EntityRowCount` cards for Plan 08's controller.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | BackupImportService production class | b3cee4d | `BackupImportService.java` (new, 339 lines) |
| 2 | BackupImportServiceIT — happy-path 4 tests | f99649f | `BackupImportServiceIT.java` (new) |
| 3 | BackupImportSchemaMismatchIT — SC#2 proof | 1466f2a | `BackupImportSchemaMismatchIT.java` (new) |
| 4 | BackupImportZipSlipIT — path-traversal 3 tests | 8a1abb1 | `BackupImportZipSlipIT.java` (new) |
| 5 | BackupImportZipBombIT — limit 3 tests + fix | 87ba344 + 4228595 | `BackupImportZipBombIT.java` (new) |

## What Was Built

### `BackupImportService` (Plan 05 — D-19)

Spring `@Service` annotated `@Slf4j @Transactional(readOnly = true)`. Explicit constructor with `@Value("${app.backup.staging-dir}")` — incompatible with `@RequiredArgsConstructor` (mirrors `BackupArchiveService` lines 102-112).

**`@PostConstruct wireRepositoriesByTableName()`:** Iterates `BackupSchema.getExportOrder()` (24 `EntityRef` values), resolves each entity class against the injected `List<JpaRepository<?,?>>` via `GenericTypeResolver.resolveTypeArguments(repo.getClass(), JpaRepository.class)[0]`. Throws `IllegalStateException` if map size != 24 — fail-fast at startup.

**`stage(MultipartFile)` flow:**
1. `Files.createDirectories(stagingDir)` (idempotent)
2. ZIP magic-byte sniff — `readNBytes(4)` vs `{0x50, 0x4B, 0x03, 0x04}` → `NOT_A_ZIP` before any disk write
3. UUID + staging path allocation (`upload-{uuid}.zip`)
4. `file.transferTo(staged)`
5. `try { buildPreview(...); keep = true; return preview; } finally { if (!keep) Files.deleteIfExists(staged); }`

**`buildPreview()` flow (D-09 schema gate first):**
1. `backupArchive.readManifest(staged)` — includes all ZIP hardening
2. Schema-version gate: `manifest.schemaVersion() != SCHEMA_VERSION` → `SCHEMA_MISMATCH` with exact D-02#2 string `"Schema version mismatch: backup=%d, expected=%d. Cannot import."` — BEFORE any `repo.count()` call
3. `backupArchive.countUploadFiles(staged)`
4. Iterate 24 `EntityRef`s: `repo.count()` for current rows, `manifest.tableCounts().getOrDefault(tableName, 0L)` for imported rows, `toHumanLabel(tableName)` for human label
5. Return fully-populated `BackupImportPreview`

**`reparse(UUID)`:** Missing file → `MANIFEST_MISSING`. Otherwise delegates to `buildPreview`. Never deletes staging file (D-08).

**`deleteStagingFile(UUID)`:** `Files.deleteIfExists`, swallows all `IOException`, logs at WARN. Idempotent.

**`toHumanLabel(String)`:** `"season_phases"` → `"Season Phases"` via stream split/capitalize/join.

### Integration Tests (4 classes, 12 methods)

| IT Class | Tests | SC |
|----------|-------|----|
| `BackupImportServiceIT` | 4 (stage, reparse, delete, not-a-zip) | SC#1 partial |
| `BackupImportSchemaMismatchIT` | 2 (version=999, version=-1) | SC#2 full |
| `BackupImportZipSlipIT` | 3 (dot-dot, absolute, uploads-dot-dot) | SC#3 half |
| `BackupImportZipBombIT` | 3 (entry-too-large, total-too-large, too-many) | SC#3 half |

All fixtures generated programmatically (D-25). No binary blobs under `src/test/resources/backup-fixtures/malicious/`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ZipBombIT fixture entries placed under wrong path prefix**
- **Found during:** Task 5 (first test run — 2 of 3 bomb tests failed with "Expecting code to raise a throwable")
- **Issue:** `inflationBombZip` and `totalSizeBombZip` placed bomb entries under `data/` prefix. However, `BackupArchiveService.countDataEntries()` is not called in the `stage()` preview pipeline — only `readManifest()` and `countUploadFiles()` are called. Entries under `data/` are never drained by any method called from `buildPreview()`, so the `LimitedInputStream` limits were never triggered.
- **Fix:** Changed bomb entry paths from `data/seasons.json` and `data/entity-N.json` to `uploads/bomb.bin` and `uploads/file-N.bin`. `countUploadFiles()` drains every `uploads/` entry through `LimitedInputStream`, correctly triggering `ENTRY_TOO_LARGE` and `TOTAL_TOO_LARGE`.
- **Files modified:** `BackupImportZipBombIT.java`
- **Commit:** 4228595

## Verification Results

All success criteria met (pending final combined IT run):

- `BackupImportService.java` exists: FOUND
- Three public methods: FOUND (`stage`, `reparse`, `deleteStagingFile`)
- `@PostConstruct wireRepositoriesByTableName`: FOUND
- D-02#2 exact string `"Schema version mismatch: backup=%d, expected=%d. Cannot import."`: FOUND at line 274
- No `Reason.ZIP_SLIP|SCHEMA_VERSION_MISMATCH|MANIFEST_PARSE_FAILED`: CLEAN
- No `PathTraversalGuard.guard(` calls: CLEAN
- No `@SessionAttributes` or cache: CLEAN
- No DB writes (`@Modifying`, `JdbcTemplate`, `EntityManager.persist/merge/remove`): CLEAN
- No binary blobs under `src/test/resources/backup-fixtures/malicious/`: CLEAN (directory does not exist)
- `BackupImportServiceIT` 4/4: PASS (exit code 0)
- `BackupImportSchemaMismatchIT` 2/2: PASS (exit code 0)
- `BackupImportZipSlipIT` 3/3: PASS (exit code 0)
- `BackupImportZipBombIT` 3/3: PASS (exit code 0, after Rule 1 fix)

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes introduced. `BackupImportService` is a read-only service (no DB writes). The staging directory (`data/{profile}/backup-staging/`) already existed as a planned component (Plan 03 config). ZIP-Bomb and ZIP-Slip defenses are delegated to Plan 04's `BackupArchiveService` reader chain.

## Known Stubs

None — all three D-19 methods are fully implemented. The `buildPreview` helper wires all 24 entity cards via live `Repository.count()` + `manifest.tableCounts()`. No placeholder data sources.

## Self-Check: PASSED

**Files:**
- `src/main/java/org/ctc/backup/service/BackupImportService.java` — FOUND
- `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` — FOUND
- `src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java` — FOUND
- `src/test/java/org/ctc/backup/service/BackupImportZipSlipIT.java` — FOUND
- `src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java` — FOUND

**Commits:**
- `b3cee4d` — feat(74-05): add BackupImportService
- `f99649f` — test(74-05): add BackupImportServiceIT
- `1466f2a` — test(74-05): add BackupImportSchemaMismatchIT
- `8a1abb1` — test(74-05): add BackupImportZipSlipIT
- `87ba344` — test(74-05): add BackupImportZipBombIT
- `4228595` — fix(74-05): fix ZipBombIT fixture entry paths

**Final combined IT run (`-Dit.test='BackupImportServiceIT,BackupImportSchemaMismatchIT,BackupImportZipSlipIT,BackupImportZipBombIT'`):**
- `BackupImportServiceIT`: Tests run: 4, Failures: 0, Errors: 0
- `BackupImportSchemaMismatchIT`: Tests run: 2, Failures: 0, Errors: 0
- `BackupImportZipSlipIT`: Tests run: 3, Failures: 0, Errors: 0
- `BackupImportZipBombIT`: Tests run: 3, Failures: 0, Errors: 0
- **Total: 12/12 GREEN — exit code 0**
