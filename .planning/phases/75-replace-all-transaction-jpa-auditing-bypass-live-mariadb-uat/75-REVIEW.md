---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
reviewed: 2026-05-14T15:30:00Z
depth: standard
files_reviewed: 43
files_reviewed_list:
  - .github/workflows/mariadb-migration-smoke.yml
  - pom.xml
  - src/main/java/org/ctc/backup/BackupController.java
  - src/main/java/org/ctc/backup/audit/DataImportAuditService.java
  - src/main/java/org/ctc/backup/dto/BackupImportResult.java
  - src/main/java/org/ctc/backup/event/BackupImportSucceededEvent.java
  - src/main/java/org/ctc/backup/exception/BackupImportException.java
  - src/main/java/org/ctc/backup/exception/RestoreFailureSimulatedException.java
  - src/main/java/org/ctc/backup/exception/UploadsRestoreException.java
  - src/main/java/org/ctc/backup/restore/EntityRestorer.java
  - src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java
  - src/main/java/org/ctc/backup/restore/RestoreFailureInjector.java
  - src/main/java/org/ctc/backup/restore/entity/CarRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/DriverRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/MatchScoringRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/MatchdayRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/PhaseTeamRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/PlayoffMatchupRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/PlayoffRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/PlayoffRoundRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/PlayoffSeedRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/PsnAliasRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/RaceAttachmentRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/RaceRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/RaceResultRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/RaceScoringRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/RaceSettingsRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/SeasonDriverRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/SeasonPhaseGroupRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/SeasonPhaseRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/SeasonRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/SeasonTeamRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/TeamRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/TrackRestorer.java
  - src/main/java/org/ctc/backup/service/BackupArchiveService.java
  - src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java
  - src/main/java/org/ctc/backup/service/BackupImportService.java
  - src/main/resources/application-docker.yml
  - src/main/resources/application-local.yml
  - src/main/resources/application-prod.yml
  - src/main/resources/application.yml
findings:
  critical: 2
  warning: 8
  info: 4
  total: 14
status: issues_found
---

# Phase 75: Code Review Report

**Reviewed:** 2026-05-14T15:30:00Z
**Depth:** standard
**Files Reviewed:** 43
**Status:** issues_found

## Summary

Phase 75 replaces the Phase-74 stub with the real backup-import pipeline: schema-gated wipe via native `DELETE FROM`, restore via 24 `EntityRestorer` beans on `JdbcTemplate.batchUpdate`, uploads-tree atomic move in an `AFTER_COMMIT` listener, and `REQUIRES_NEW` audit row writes that survive the outer rollback.

The architecture is sound. The 24 restorers consistently use parameterized `INSERT` SQL with hard-coded column lists — no SQL-injection vector via JSON inputs — and the only string-concatenated table name in the codebase (`DELETE FROM <table>` in `wipeAllTables`) is guarded by a regex allow-list (`^[a-z_]+$`) on top of a closed source set (JPA `@Table(name=...)` from hard-coded entity classes). Path traversal in `BackupArchiveService.extractUploadsTo` is enforced via `PathTraversalGuard.assertWithin` and the post-commit `Files.move` triple uses only paths derived from server-side configuration plus a timestamp, never from the uploaded ZIP entry names.

Two correctness defects were found that warrant Critical/Blocker:

1. **`Map.copyOf(LinkedHashMap)` strips insertion order** in `BackupImportService.execute`. The event carrier's `tableCountsWiped`/`tableCountsRestored` are persisted as JSON into `data_import_audit` rows in random hash order, defeating the explicit `LinkedHashMap` ordering chosen elsewhere in the pipeline and harming forensic readability for the v1.10 milestone whose auditability is a stated goal.

2. **AFTER_COMMIT Step-1 revert clobbers a fresh `uploadsTarget`** in a rare but plausible interleaving. If Step-1 succeeded (live uploads renamed to `uploads-old/`) and Step-2 then fails AFTER the destination filesystem object was partially created (e.g., on a non-atomic-move fallback), the Step-1 revert `Files.move(uploadsOld, uploadsTarget, ATOMIC_MOVE)` will fail with `FileAlreadyExistsException` — leaving the operator with NO uploads tree and the original tree stranded in `uploads-old/`. The class-Javadoc claims revert is "best-effort"; in practice the failure path is not exhaustively covered.

A handful of Warning-class issues center on: (a) duplicate `executedBy` resolution logic in `BackupImportService` AND `DataImportAuditService`, (b) D-15 success-flash counting all 24 entities even when most contributed zero rows, and (c) silent fallback when staging-file `.meta` read fails. Info-class items are minor copy-paste / consistency concerns across the 24 restorers (mixing `Lombok @RequiredArgsConstructor` on classes with no fields, mixing `Slf4j` annotation order, etc.).

The implementation is solid for v1.10 ship-readiness once the two Critical findings are addressed.

## Critical Issues

### CR-01: `Map.copyOf(LinkedHashMap)` strips insertion order — audit-row JSON columns are unordered

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java:494-495`
**Issue:** `BackupImportService.execute` deliberately uses `LinkedHashMap` for `wipedCounts` (line 455) and `restoredCounts` (line 456) so the export-order iteration is preserved. However, when publishing the `BackupImportSucceededEvent`, both maps are wrapped via `Map.copyOf(...)`:

```java
eventPublisher.publishEvent(new BackupImportSucceededEvent(
        ...
        Map.copyOf(wipedCounts),
        Map.copyOf(restoredCounts),
        ...));
```

`Map.copyOf(...)` returns `ImmutableCollections.MapN`, a hash-table-backed map that does NOT preserve iteration order — verified empirically with a 24-key map: insertion order `[seasons, drivers, cars, tracks, teams, ...]` is replaced by an unordered iteration `[races, phase_teams, race_settings, seasons, ...]`. The downstream `DataImportAuditService.recordResult` serializes these maps via `backupObjectMapper.writeValueAsString(safeMap)` (line 170), producing `data_import_audit.table_counts_wiped` and `table_counts_restored` columns whose JSON keys are in random hash order across rows.

Impact: forensic readability of `data_import_audit` rows degrades. The implicit promise of the `LinkedHashMap` (insertion order = export order = wipe order) is lost at the listener boundary. This is the v1.10 auditability surface; auditors comparing two import rows side-by-side would expect deterministic key ordering.

**Fix:** Use a defensive copy that preserves order. Two valid options:

```java
// Option A: defensive LinkedHashMap copy (mutable but listener treats as immutable)
new LinkedHashMap<>(wipedCounts),
new LinkedHashMap<>(restoredCounts),

// Option B: java.util.Collections.unmodifiableMap on a LinkedHashMap (immutable + ordered)
Collections.unmodifiableMap(new LinkedHashMap<>(wipedCounts)),
Collections.unmodifiableMap(new LinkedHashMap<>(restoredCounts)),
```

Apply identical fix at `BackupImportService.java:494-495`. Add a unit test that asserts the JSON column starts with the expected leading table name (e.g., `seasons` for export order) after a round-trip.

### CR-02: AFTER_COMMIT Step-1 revert can throw `FileAlreadyExistsException`, stranding uploads tree

**File:** `src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java:107-126`
**Issue:** Step-2 in the listener moves `uploadsNewDir → uploadsTarget` via `ATOMIC_MOVE`. On `IOException`, the listener attempts a Step-1 revert:

```java
} catch (IOException e) {
    log.error("AFTER_COMMIT Step 2 failed: attempting Step-1 revert", e);
    try {
        if (Files.exists(uploadsOld)) {
            Files.move(uploadsOld, uploadsTarget, StandardCopyOption.ATOMIC_MOVE);
            ...
```

`Files.move` with `ATOMIC_MOVE` and a non-empty existing destination directory fails on most filesystems (including ext4, xfs, and the macOS APFS — even when `StandardCopyOption.REPLACE_EXISTING` is added, `ATOMIC_MOVE` rejects directory replacement). Two failure scenarios where `uploadsTarget` is non-empty when the revert fires:

1. Step-2 partially created `uploadsTarget` before failing (a `FileSystemException` mid-move on some filesystems leaves the destination half-populated, especially across mountpoints).
2. A separate process (anti-virus, file-watcher, operator scrolling in a file manager) created a `.DS_Store` / `Thumbs.db` / metadata inode under the just-vacated `uploadsTarget` parent between Step-1 and the revert attempt.

When the revert throws, the catch-block logs `"Step-1 revert ALSO failed - manual recovery required from {}"` (line 121-123) and proceeds to write the `success=false` audit row + rethrow `UploadsRestoreException`. The operator now sees:
- DB content fully replaced (committed).
- `data/<profile>/uploads/` may be empty, half-restored, or non-existent.
- Original uploads tree stranded in `data/.import-backups/<ts>/uploads-old/`.
- Flash message: "Import database succeeded but uploads restore failed and was reverted. See logs."

The "and was reverted" wording (locked D-15 text) is now factually wrong — the revert may have failed. The Phase 75 design contract (RESEARCH §"atomic move triple"; D-09) promises the FS is restored on Step-2 failure.

**Fix:** The revert must accept that the destination might exist. Two complementary improvements:

```java
} catch (IOException e) {
    log.error("AFTER_COMMIT Step 2 failed: attempting Step-1 revert", e);
    try {
        if (Files.exists(uploadsOld)) {
            // Defensive: if uploadsTarget partially materialized during failed Step 2,
            // sweep it aside before reverting Step 1 so ATOMIC_MOVE can complete.
            if (Files.exists(uploadsTarget)) {
                Path orphan = importBackupDir.resolve("uploads-step2-orphan");
                Files.move(uploadsTarget, orphan, StandardCopyOption.ATOMIC_MOVE);
                log.warn("Step-2 left orphan at {} - moved to {} before Step-1 revert",
                        uploadsTarget, orphan);
            }
            Files.move(uploadsOld, uploadsTarget, StandardCopyOption.ATOMIC_MOVE);
            log.warn("Step-1 revert succeeded; uploads tree restored to pre-import state");
        } else { ... }
    } catch (IOException revertEx) {
        log.error("Step-1 revert ALSO failed - manual recovery required from {}",
                importBackupDir, revertEx);
    }
    ...
```

Additionally, update the locked D-15 soft-fail flash string to acknowledge that revert is best-effort: change "Import database succeeded but uploads restore failed and was reverted. See logs." to "Import database succeeded but uploads restore failed — see logs for revert outcome. Audit-id: {auditUuid}." (Touches `BackupController.java:267` and tests; out of scope for a Critical fix if the operator-facing docs make the recovery procedure explicit.)

## Warnings

### WR-01: `executedBy` resolution is duplicated in `BackupImportService` and `DataImportAuditService`

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java:666-675` AND `src/main/java/org/ctc/backup/audit/DataImportAuditService.java:152-164`
**Issue:** Both classes implement the same D-02 profile-aware resolution logic:

```java
// BackupImportService.resolveExecutedBy()  AND
// DataImportAuditService.resolveExecutedBy(String executedByCaller)
if (environment.matchesProfiles("dev | local")) {
    return "dev";
}
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
    return auth.getName();
}
return "unknown";
```

`BackupImportService.resolveExecutedBy()` is called on the success-path to populate `event.executedBy()`; `DataImportAuditService.resolveExecutedBy(executedByCaller)` runs again in the REQUIRES_NEW audit-row insert. If the two definitions ever drift (e.g., a profile predicate change), the success vs. failure audit rows will record different `executed_by` values for the same operator. This already happened in a subtle form: the failure-path call from `BackupImportService.tryRecordFailure` passes `executedByCaller=null` (line 687), forcing `DataImportAuditService` to do its own resolution; the success-path call from the listener passes `event.executedBy()` (line 134), which is the cached value from `BackupImportService.resolveExecutedBy()` — but `DataImportAuditService` will OVERRIDE it on the dev/local profile branch (line 153). The dev/local override on the success path is harmless today but couples both classes to the same profile constants.

**Fix:** Extract a single `BackupExecutedByResolver` bean (or static helper on a new `BackupExecutorContext` class) and inject it into both services:

```java
@Component
@RequiredArgsConstructor
class BackupExecutedByResolver {
    private final Environment environment;
    String resolve(String callerOverride) {
        if (environment.matchesProfiles("dev | local")) return "dev";
        if (callerOverride != null && !callerOverride.isBlank()) return callerOverride;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null && !auth.getName().isBlank())
            ? auth.getName() : "unknown";
    }
}
```

### WR-02: `BackupImportResult.entityCount` always returns 24, not "entities that contributed rows"

**File:** `src/main/java/org/ctc/backup/dto/BackupImportResult.java:38-40` (contract) AND `src/main/java/org/ctc/backup/service/BackupImportService.java:485`
**Issue:** The `BackupImportResult.entityCount` Javadoc specifies "number of distinct entities (`data/<entity>.json` files) that contributed rows (binds to the D-15 `{entities}` placeholder)". However, `BackupImportService.restoreAll` iterates `getExportOrder()` and ALWAYS puts an entry in `restoredCounts` for every table, even when `restoreOneTable` returns 0:

```java
for (EntityRef ref : backupSchema.getExportOrder()) {
    ...
    long restored = restoreOneTable(staged, ref, restorer);
    restoredCounts.put(table, restored);  // <-- always called
}
```

So `restoredCounts.size() == 24` always (for the 24-entity export order). The D-15 success-flash bound by the controller — `"Import completed. %d rows restored across %d tables."` — will say "across 24 tables" even when the backup ZIP only contained data for 5 tables. Misleading on a partial-import scenario (none exists today, but the contract is wrong).

**Fix:** Filter to non-zero entries when computing `entityCount`:

```java
int entityCount = (int) restoredCounts.values().stream().filter(c -> c > 0).count();
```

Or change the contract (Javadoc on `BackupImportResult`) to say "number of tables iterated" and adjust the flash string. The fix is a one-liner.

### WR-03: `tryRecordFailure` swallows audit-write exceptions but loses the original cause's UUID linkage

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java:682-697`
**Issue:** When the REQUIRES_NEW audit write itself fails (double-failure path), the catch-block logs ERROR but proceeds. The caller (`execute`) then throws `BackupImportException(auditUuid, e)` — but no audit row with that `auditUuid` exists. The controller flashes `"Import failed and was rolled back — see logs. Audit-id: <uuid>."` and the operator runs `SELECT * FROM data_import_audit WHERE id = <uuid>` returning zero rows.

This degrades the operator-recovery story documented in `BackupController.importExecute`'s Javadoc.

**Fix:** Either (a) make the failure-path UUID derivable from a stable correlation column (e.g., write the UUID into the SLF4J MDC at the start of `execute()` so it appears in every log line for that import), or (b) explicitly mention the no-audit-row case in the flash text via a flag:

```java
boolean auditWritten = false;
try {
    dataImportAuditService.recordResult(...);
    auditWritten = true;
} catch (Exception auditEx) { ... }
// pass auditWritten to BackupImportException constructor
throw new BackupImportException(auditUuid, auditWritten, e);
// controller adjusts flash: "Audit-id: <uuid>" vs "Audit-id: unavailable, see logs"
```

### WR-04: `Files.readString(metaFile)` swallows malformed UTF-8 and uses staging-UUID as filename

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java:440-448`
**Issue:** When the staging `.meta` sidecar exists but contains malformed UTF-8 (e.g., a half-written file from a partial crash, or a manual operator edit), `Files.readString(...UTF_8)` throws `MalformedInputException`. The catch-block:

```java
} catch (IOException ioe) {
    sourceFilename = staged.getFileName().toString();
    log.warn("Failed to read staging .meta sidecar for id={} — falling back to staging filename", stagingId, ioe);
}
```

This falls back to `upload-<uuid>.zip` as the audit-row `source_filename`, which is operationally useless — the operator wanted the user-friendly upload name. Worse: if the `.meta` file existed during `stage()` but was corrupted between stage and execute (unlikely but possible), the user-friendly name is silently lost. No alerting beyond a WARN log.

**Fix:** If `metaFile` exists but read fails, this is a stronger signal than "absent meta — use staging filename": treat as soft-recoverable but record the corruption explicitly:

```java
} catch (IOException ioe) {
    sourceFilename = "<filename-unavailable: meta-read-failed-" + stagingId + ">";
    log.error("Staging .meta sidecar corrupted for id={} — original filename lost", stagingId, ioe);
}
```

This makes the audit row tell the truth about what was lost. Additionally, consider adding an integrity check (length sanity / NUL-byte rejection) when writing `metaFile` in `stage()` (line 317).

### WR-05: `restoreOneTable` rescans the ZIP from the start for every entity (24× full ZIP scans)

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java:594-646`
**Issue:** Per the brief, performance is out of v1 scope. But this is also a correctness/robustness concern: opening 24 `ZipInputStream` instances against the same staged file means 24 `Files.newInputStream` operations on a file that is being held by the staging-write side. On Windows hosts (relevant when an operator runs the local profile from a Windows workstation per the v1.10 ROADMAP), this elevates the risk of `FileSystemException: The process cannot access the file because it is being used by another process.` if any cleanup thread tries to touch the staging file mid-restore. The Phase 74 docs already note the staging file is preserved across stage/preview/execute — but the listener's Step-4 cleanup hits the same file after a successful execute. If the JVM-internal stream lifecycle on Windows defers close, the listener's `Files.deleteIfExists` can race.

**Fix:** Open the ZIP once at the top of `restoreAll`:

```java
void restoreAll(Path staged, Map<String, Long> restoredCounts) throws IOException {
    try (ZipFile zf = new ZipFile(staged.toFile())) {  // ZipFile, not ZipInputStream — random access
        for (EntityRef ref : backupSchema.getExportOrder()) {
            ZipEntry entry = zf.getEntry(ref.fileName());
            long restored = (entry == null) ? 0L : restoreOneTable(zf, entry, ref, restorerByTableName.get(ref.tableName()));
            restoredCounts.put(ref.tableName(), restored);
        }
    }
}
```

This reduces the ZIP-open count from 24 to 1, eliminates the on-Windows race, and is also a meaningful perf win on the Saison-2023 ~1000-row fixture.

### WR-06: `BackupController.importExecute` exception chain is unreachable for `BackupArchiveException` post-execute

**File:** `src/main/java/org/ctc/backup/BackupController.java:247-273`
**Issue:** The execute-path catch chain:

```java
try {
    backupImportService.reparse(form.getStagingId());
    BackupImportResult result = backupImportService.execute(form.getStagingId());
    ra.addFlashAttribute("successMessage", ...);
} catch (BackupArchiveException ex) {
    ra.addFlashAttribute("errorMessage", mapReason(ex));
} catch (IOException ex) { ... }
catch (UploadsRestoreException ex) { ... }
catch (BackupImportException ex) { ... }
```

`execute()` wraps any internal `BackupArchiveException` in `BackupImportException(auditUuid, e)` (line 508), so the only path that can throw a raw `BackupArchiveException` from this `try` block is the `reparse(...)` call (line 248). On the execute-path:

- A schema-mismatch detected by `reparse` → `BackupArchiveException` flashes the `mapReason(...)` text (no audit row, because we never entered `execute()`).
- A schema-mismatch detected INSIDE `execute()` after `reparse` somehow returned (impossible today) → wrapped in `BackupImportException`, flashes the generic `"Import failed and was rolled back — see logs. Audit-id: ..."` — the operator never sees the schema-mismatch detail.

The class Javadoc (lines 208-213) calls the order "INDEPENDENT" because the three exceptions are sibling RuntimeException subclasses — which is correct for compiler ordering. But the operator-visible behavior is asymmetric: a schema-mismatch surfaces nicely from reparse but is buried by execute. Probably acceptable today (the gate at reparse catches it), but the controller should not silently absorb diagnostic detail.

**Fix:** Unwrap the cause in the `BackupImportException` catch:

```java
} catch (BackupImportException ex) {
    String detail = (ex.getCause() instanceof BackupArchiveException bae)
            ? " (" + bae.reason() + ")" : "";
    ra.addFlashAttribute("errorMessage",
            String.format("Import failed and was rolled back — see logs. Audit-id: %s%s.",
                    ex.getAuditUuid(), detail));
}
```

### WR-07: `BackupArchiveService.extractUploadsTo` calls `assertEntrySafe` after creating directory + before writing entry

**File:** `src/main/java/org/ctc/backup/service/BackupArchiveService.java:504-528`
**Issue:** The current ordering is:

1. Validate stripped relativePath via `PathTraversalGuard.assertWithin(absoluteDest, relativePath)` (line 502).
2. `target = absoluteDest.resolve(relativePath).normalize()` (line 504).
3. `Files.createDirectories(target.getParent())` (line 506) — **filesystem mutation**.
4. Wrap `LimitedInputStream` and `Files.copy(limited, target, REPLACE_EXISTING)` (line 520) — **filesystem mutation**.
5. THEN `assertEntrySafe(entry, absoluteDest, entryCount, inflatedAcc[0])` (line 528) — entry-count + total-byte cap check.

If a hostile ZIP contains entry #N+1 that triggers `TOO_MANY_ENTRIES` or `TOTAL_TOO_LARGE`, entries #1..#N have already been extracted to disk before the cap fires. The `extractUploadsTo` failure rolls back the DB transaction (called from `execute()`'s try block) and the orchestrator's `tryCleanupUploadsNew(uploadsNewDir)` sweeps the partial extraction — but only if `uploadsNewDir` was recorded. Defense-in-depth would be to check caps BEFORE writing to disk.

**Fix:** Move `assertEntrySafe` ahead of the `Files.copy` call when the entry IS in the `uploads/` scope:

```java
PathTraversalGuard.assertWithin(absoluteDest, relativePath);
// Pre-check entry cap so a hostile ZIP with 100k +1 entries doesn't bloat the disk before failing.
if (entryCount > MAX_ENTRIES) {
    throw new BackupArchiveException(Reason.TOO_MANY_ENTRIES, "exceeded " + MAX_ENTRIES);
}
// Total-byte cap is unknown until after this entry inflates — still post-check.
Path target = absoluteDest.resolve(relativePath).normalize();
...
```

Note: the total-byte cap is fundamentally post-write because we only learn the inflated size by inflating. The `LimitedInputStream` per-entry cap (50 MB) bounds the damage, but the aggregate cap is racy by design. The entry-count cap, however, is known up front — pre-check it.

### WR-08: `BackupImportService.execute` catches `Exception` (broad), risks masking `Error` siblings

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java:504`
**Issue:** `catch (Exception e)` catches every checked + unchecked exception but NOT `Error` (e.g., `OutOfMemoryError` during the 1000-row restore on a constrained container). On `OutOfMemoryError`, the catch-block is skipped, `tryRecordFailure` does not run, no audit row is written, and the `@Transactional(rollbackFor = Exception.class)` declaration also does NOT roll back on `Error` (rollbackFor only adds checked exceptions — `Error`s already trigger rollback per default, but the JPA proxy must be reachable; in practice it is). The operator sees a generic 500 page with no audit row.

For backup imports specifically — a single-operator-initiated long-running operation where OOMs are plausible given the 500-byte payload per row × 1000 rows × 24 entities — the loss-of-audit on `Error` is the only difference between a silent crash and a recoverable failure.

**Fix:** Catch `Throwable` and re-throw `Error` after recording:

```java
} catch (Throwable t) {
    log.error("Import failed for staging-id {}: ", stagingId, t);
    tryRecordFailure(auditUuid, schemaVersion, sourceFilename, wipedCounts, restoredCounts);
    tryCleanupUploadsNew(uploadsNewDir);
    if (t instanceof Error err) {
        throw err;  // preserve JVM-fatal contract
    }
    throw new BackupImportException(auditUuid, t);
}
```

Combined with the Spring proxy's default rollback-on-Error behavior, this gives the operator an audit row even on OOM.

## Info

### IN-01: `@RequiredArgsConstructor` on classes with no `final` fields is a no-op

**File:** Multiple — `src/main/java/org/ctc/backup/restore/entity/CarRestorer.java:38`, `src/main/java/org/ctc/backup/restore/entity/PlayoffMatchupRestorer.java:51`, `src/main/java/org/ctc/backup/restore/entity/PlayoffRestorer.java:43`, `src/main/java/org/ctc/backup/restore/entity/PlayoffRoundRestorer.java:31`, `src/main/java/org/ctc/backup/restore/entity/PlayoffSeedRestorer.java:30`, `src/main/java/org/ctc/backup/restore/entity/TrackRestorer.java:34` — note these LACK the annotation (they have only static constants).
**Issue:** Inconsistency: most restorers (`DriverRestorer`, `MatchRestorer`, `SeasonDriverRestorer`, `TeamRestorer`, etc.) carry `@RequiredArgsConstructor` despite having NO instance fields — only `static final` constants. Lombok generates an empty default constructor in this case, which is functionally identical to the no-arg default. Other restorers (`CarRestorer`, `TrackRestorer`, `PlayoffRestorer`, `PlayoffRoundRestorer`, `PlayoffSeedRestorer`, `PlayoffMatchupRestorer`) correctly omit the annotation.

**Fix:** Remove `@RequiredArgsConstructor` from all 18 restorers that have no instance fields. Two-line delete per file. Reduces Lombok annotation processor work and improves consistency.

### IN-02: `@Slf4j` and `@Component` ordering is inconsistent across restorers

**File:** Multiple — compare `src/main/java/org/ctc/backup/restore/entity/CarRestorer.java:37-38` (`@Component @Slf4j`) vs. `src/main/java/org/ctc/backup/restore/entity/DriverRestorer.java:39-40` (`@Slf4j @Component`)
**Issue:** No functional impact, but cosmetic noise: 8 restorers put `@Component` before `@Slf4j`; 16 put it the other way. The repo has no documented annotation-ordering convention.

**Fix:** Pick one — `@Slf4j @Component @RequiredArgsConstructor` (alphabetical) is the most common pattern in the rest of `org.ctc` per a quick spot-check.

### IN-03: `restoreOneTable` returns `0L` without invoking the restorer when ZIP entry is missing

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java:644-646`
**Issue:** When a `data/<slug>.json` entry is absent from the ZIP, the method falls through to `log.debug(...)` and returns 0. The Javadoc comment (line 642-643) correctly notes "Absent data files for an entity are not a hard error — an exported empty array is semantically equivalent." However, the Phase 73 export contract creates one entry per entity in `getExportOrder()` — there is no legitimate way a Phase-73-produced backup can omit an entry. Tolerating the absence weakens the round-trip guarantee.

**Fix:** Promote the silent skip to a WARN log (or fail-loud `IllegalStateException`) so the operator sees corruption signal:

```java
log.warn("Backup ZIP has no data entry for table={} (entryPath={}) — possible corruption or schema regression",
        ref.tableName(), entryPath);
```

### IN-04: Documentation says ImportBackupsDir lives at `data/.import-backups` but does not isolate by profile

**File:** `src/main/resources/application.yml:6` (`import-backups-dir: data/.import-backups`)
**Issue:** `staging-dir` is profile-isolated (`data/${spring.profiles.active:dev}/backup-staging`) but `import-backups-dir` is not (`data/.import-backups`). When two profiles run on the same machine (`dev` and `local` on a developer laptop, plausible per the v1.10 ROADMAP), the `<ts>` directory names may collide because `Instant.now().truncatedTo(SECONDS)` only carries seconds resolution. Two parallel imports starting in the same second would race for `data/.import-backups/2026-05-14T15-30-00Z/uploads-new/`.

**Fix:** Either profile-isolate the dir (`data/${spring.profiles.active:dev}/import-backups`) or append the staging UUID to the timestamp:

```java
String ts = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-")
          + "-" + stagingId;
```

The UUID suffix is the cheaper change.

---

_Reviewed: 2026-05-14T15:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
