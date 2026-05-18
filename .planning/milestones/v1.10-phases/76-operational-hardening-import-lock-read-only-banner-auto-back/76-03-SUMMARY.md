---
phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
plan: 03
status: complete
requirements:
  - SECU-07
files_created:
  - src/main/java/org/ctc/backup/exception/AutoBackupBeforeImportException.java
  - src/test/java/org/ctc/backup/it/AutoBackupBeforeImportPathIT.java
  - src/test/java/org/ctc/backup/it/AutoBackupBeforeImportFailureIT.java
files_modified:
  - src/main/java/org/ctc/backup/service/BackupImportService.java
  - src/main/java/org/ctc/backup/BackupController.java
---

# Plan 76-03 — Ring 3: Pre-Import Auto-Backup (SECU-07)

Implements the synchronous pre-import auto-backup ZIP write between Step 0
(manifest re-read) and Step 1 (wipe) inside `BackupImportService.execute`,
with full failure-mode coverage: distinct exception subclass, audit-row with
empty count maps, partial-ZIP cleanup, and a semantically correct controller
flash that communicates "no DB mutation occurred".

## Artifacts

| Path | Wire-point |
|------|------------|
| `org.ctc.backup.exception.AutoBackupBeforeImportException` | extends `BackupImportException`; 3-arg ctor delegates to parent (auditUuid, auditWritten, cause). |
| `BackupImportService.execute(UUID)` Step 0.5 | `Files.newOutputStream(autoBackupZip, StandardOpenOption.CREATE_NEW)` → `backupArchive.writeZip(out, Instant.now())`. On IOException/RuntimeException: `tryDeletePartialAutoBackup(autoBackupZip)` (D-19, never throws) → `tryRecordFailure(..., Map.of(), Map.of())` (D-18 empty count maps) → throw `AutoBackupBeforeImportException`. |
| `BackupImportService.execute(UUID)` outer `catch (Throwable t)` | Rethrows `AutoBackupBeforeImportException` unchanged BEFORE the generic wrapper to prevent the subclass from being shadowed (Pitfall #3 fix). |
| `BackupController.importExecute` | New `catch (AutoBackupBeforeImportException)` inserted BEFORE `catch (BackupImportException)`. Flash: `"Import aborted — pre-import auto-backup failed. No database changes. Audit-id: %s."` (D-17). |

## Verification

```
$ ./mvnw -q -Dit.test='AutoBackupBeforeImportPathIT,AutoBackupBeforeImportFailureIT,ImportConcurrentLockIT' -DfailIfNoTests=false verify
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`grep` checks from PLAN.md §verification:

- `grep "StandardOpenOption.CREATE_NEW" BackupImportService.java` → 1 hit (Step 0.5 atomic-create-or-fail).
- `grep "tryDeletePartialAutoBackup" BackupImportService.java` → 3 hits (declaration + 2 call sites: Step 0.5 catch + comment).
- `grep -B1 "catch (BackupImportException" BackupController.java` → `catch (AutoBackupBeforeImportException` IMMEDIATELY before (Pitfall #3 negative check).
- `grep "Map.of(), Map.of()" BackupImportService.java` → 1 hit (D-18 empty count maps).

## Directory layout after happy-path run

```
data/dev/.import-backups/2026-05-14T18-52-15Z/
  auto-backup-before-import.zip      # D-14 / D-16 — non-empty, parseable manifest
  uploads-old/                        # D-15 — same <ts> as the auto-backup ZIP
    (existing uploads tree, moved from data/dev/uploads/)
```

Same `<ts>` value drives both files — `Instant.now()` is computed ONCE at the
top of `execute()` and shared (D-15 single-source-of-truth).

## Catch chain order — Pitfall #3 negative check

```java
catch (UploadsRestoreException ex) { ... }
catch (AutoBackupBeforeImportException ex) { /* D-17 flash */ }   // ← INSERTED here
catch (BackupImportException ex) { /* D-15 #2 flash */ }
```

Java first-match-wins: the subclass MUST appear BEFORE the parent. The
`grep -B1 "catch (BackupImportException"` check confirms this is in source
order.

## Scope deviation (deliberate)

Task 2's pre-existing outer `catch (Throwable t)` (Phase 75 WR-08) wrapped
ALL inner exceptions in a generic `BackupImportException`, which shadowed the
new subclass before it could reach the controller. The fix mirrors the
existing `if (t instanceof Error err) throw err` pattern — a 6-line
`instanceof AutoBackupBeforeImportException` short-circuit at the top of the
outer catch. The PLAN.md `<files_modified>` already listed
`BackupImportService.java`, so this is a within-scope addition to the same
file rather than a new file.

## Handoff to Plan 76-04

Ring 3 active — operator now has a pre-wipe recovery snapshot at
`data/.import-backups/<ts>/auto-backup-before-import.zip` after every import
attempt. The 24h retention runbook (Plan 76-04) covers operator-side cleanup
and Windows file-locking caveats (Pitfall #7). Plan 76-04 also runs the
final `./mvnw verify -Pe2e` regression gate.
