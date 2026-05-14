---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
fixed_at: 2026-05-14T16:55:00Z
review_path: .planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-REVIEW.md
iteration: 1
findings_in_scope: 10
fixed: 9
skipped: 1
status: partial
---

# Phase 75: Code Review Fix Report

**Fixed at:** 2026-05-14T16:55:00Z
**Source review:** `.planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 10 (CR-01, CR-02, WR-01..WR-08)
- Fixed: 9
- Skipped: 1 (WR-01 — multi-file refactor unsafe in single fix slot, see below)
- Full `./mvnw verify -Djacoco.skip=true`: BUILD SUCCESS (1402 unit + 216 IT, 0 failures, 0 errors).

## Fixed Issues

### CR-01: `Map.copyOf(LinkedHashMap)` strips insertion order — audit-row JSON columns are unordered

**Files modified:** `src/main/java/org/ctc/backup/service/BackupImportService.java`
**Commit:** `b39d003`
**Applied fix:** Replaced `Map.copyOf(wipedCounts)` and `Map.copyOf(restoredCounts)` in the `BackupImportSucceededEvent` publish call with `Collections.unmodifiableMap(new LinkedHashMap<>(...))` so the listener-side serialization preserves the explicit export-order chosen by the upstream `LinkedHashMap`. Added `import java.util.Collections;` and inline comment referencing CR-01.

### CR-02: AFTER_COMMIT Step-1 revert can throw `FileAlreadyExistsException`, stranding uploads tree

**Files modified:** `src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java`
**Commit:** `4212a3d`
**Applied fix:** In the Step-2-failure revert block, added a defensive sweep that detects a partially-materialized `uploadsTarget` (left behind by a non-atomic-move fallback or a third-party process between Step 1 and the revert attempt) and moves it aside to `importBackupDir/uploads-step2-orphan` via `ATOMIC_MOVE` before retrying the `uploads-old → uploadsTarget` rename. This prevents the revert from throwing `FileAlreadyExistsException` and leaving the operator with NO uploads tree.

### WR-02: `BackupImportResult.entityCount` always returns 24, not "entities that contributed rows"

**Files modified:** `src/main/java/org/ctc/backup/service/BackupImportService.java`
**Commit:** `34cbecb`
**Applied fix:** Replaced `int entityCount = restoredCounts.size();` with a stream-filter on non-zero counts: `(int) restoredCounts.values().stream().filter(c -> c > 0).count()`. The D-15 success flash ("across N tables") now reflects entities that actually contributed rows, matching the `BackupImportResult.entityCount` Javadoc contract.

### WR-03: `tryRecordFailure` swallows audit-write exceptions but loses the original cause's UUID linkage

**Files modified:** `src/main/java/org/ctc/backup/exception/BackupImportException.java`, `src/main/java/org/ctc/backup/service/BackupImportService.java`, `src/main/java/org/ctc/backup/BackupController.java`
**Commit:** `2f326b6`
**Applied fix:** Added an `auditWritten` flag to `BackupImportException` plus an `isAuditWritten()` accessor and a new `(UUID, boolean, Throwable)` constructor. `tryRecordFailure` now returns `boolean` (true on success, false in the double-failure path). Both throw sites in `execute()` propagate the flag into the exception. The controller's `catch (BackupImportException)` block now reads `isAuditWritten()` and renders either the UUID or `"unavailable (audit write failed; see logs for <uuid>)"` so the operator no longer chases a non-existent audit row.

### WR-04: `Files.readString(metaFile)` swallows malformed UTF-8 and uses staging-UUID as filename

**Files modified:** `src/main/java/org/ctc/backup/service/BackupImportService.java`
**Commit:** `76a9b52`
**Applied fix:** When `Files.readString(metaFile, UTF_8)` throws `IOException` (corrupted `.meta` sidecar), `sourceFilename` is now set to the explicit sentinel `"<filename-unavailable: meta-read-failed-<stagingId>>"` instead of silently falling back to the staging-UUID filename, and the log level is escalated from WARN to ERROR. The audit row now tells the truth about which signal was lost.

### WR-05: `restoreOneTable` rescans the ZIP from the start for every entity (24× full ZIP scans)

**Files modified:** `src/main/java/org/ctc/backup/service/BackupImportService.java`
**Commit:** `f2e9125`
**Applied fix:** Refactored `restoreAll(Path, Map)` to open the staged ZIP exactly once via `java.util.zip.ZipFile` (random-access) and pass it into `restoreOneTable`. The per-entity restore now uses `ZipFile.getEntry(entryPath)` plus `ZipFile.getInputStream(entry)` instead of replaying a `ZipInputStream` from the start. Removed the unused `ZipInputStream` import; added `import java.util.zip.ZipFile;`. Eliminates the 24× rescan, the Windows-side `FileSystemException` race window, and yields a meaningful perf win on the Saison-2023 ~1000-row fixture.

### WR-06: `BackupController.importExecute` exception chain swallows `BackupArchiveException` cause

**Files modified:** `src/main/java/org/ctc/backup/BackupController.java`
**Commit:** `a310e4e`
**Applied fix:** In the `catch (BackupImportException)` block, added a pattern check on `ex.getCause()` and, when it is a `BackupArchiveException`, appended ` (REASON)` to the failure-flash text. Schema-mismatch detected inside `execute()` after `reparse` (today: impossible, future-safe) now surfaces the reason in the operator-visible message rather than being buried.

### WR-07: `BackupArchiveService.extractUploadsTo` calls `assertEntrySafe` after writing the entry

**Files modified:** `src/main/java/org/ctc/backup/service/BackupArchiveService.java`
**Commit:** `930b078`
**Applied fix:** Added a pre-write check `if (entryCount > MAX_ENTRIES) throw new BackupArchiveException(Reason.TOO_MANY_ENTRIES, ...)` immediately after `PathTraversalGuard.assertWithin` and BEFORE `Files.createDirectories`/`Files.copy`. The post-write `assertEntrySafe` call is preserved as a belt-and-suspenders check (the total-byte cap is fundamentally post-write because inflated size is only known after inflation). A hostile ZIP with `MAX_ENTRIES + 1` upload entries no longer bloats the disk with the first `MAX_ENTRIES` files before failing.

### WR-08: `BackupImportService.execute` catches `Exception` (broad), masking `Error` siblings

**Files modified:** `src/main/java/org/ctc/backup/service/BackupImportService.java`
**Commit:** `ef38ca5`
**Applied fix:** Widened the catch clause from `catch (Exception e)` to `catch (Throwable t)` so an `OutOfMemoryError` (or other JVM-fatal `Error`) during the 1000-row restore still triggers `tryRecordFailure(...)` and `tryCleanupUploadsNew(...)` before propagating. Preserved the JVM-fatal contract by re-throwing `Error` unchanged via `if (t instanceof Error err) throw err;`. Non-Error `Throwable` instances are wrapped in `BackupImportException(auditUuid, auditWritten, t)` (WR-03 constructor).

## Skipped Issues

### WR-01: `executedBy` resolution is duplicated in `BackupImportService` and `DataImportAuditService`

**Files:** `src/main/java/org/ctc/backup/service/BackupImportService.java:666-675`, `src/main/java/org/ctc/backup/audit/DataImportAuditService.java:152-164`
**Reason:** skipped: refactor scope larger than safe in single fix slot — rolled back during attempt.

The reviewer-suggested refactor extracts a new `BackupExecutedByResolver` `@Component` and rewires constructor parameters in both services. During the attempt I removed the `Environment` import from `DataImportAuditService` while the field was still referenced by an unfinished migration, breaking the field-list view. I rolled back via `git checkout --` and chose not to retry because:

1. The reviewer explicitly notes: "The dev/local override on the success path is harmless today" — no behavioural drift exists between the two implementations today.
2. The change spans **two services + one new bean + Javadoc rewrites + at least one Spring-context test class** (the `DataImportAuditService` constructor change cascades into every test using `@MockBean` or `@TestConfiguration` for it). That cascade is outside the scope of a per-finding fix slot.
3. Both `resolveExecutedBy` implementations carry the same six-line CONTEXT §D-02 rule; drift risk is real but bounded — code review (this report) is the existing safety net.

Recommended follow-up: open a small dedicated cleanup commit on the next plan that creates `BackupExecutedByResolver`, replaces both private methods, and updates the constructor-arg + Javadoc + tests in one atomic change.

**Original issue:** Drift between the two `resolveExecutedBy` definitions would produce different `executed_by` values for the same operator across success vs. failure audit rows.

---

_Fixed: 2026-05-14T16:55:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
