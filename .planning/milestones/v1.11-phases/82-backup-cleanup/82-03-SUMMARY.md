---
phase: 82-backup-cleanup
plan: "03"
subsystem: backup
tags: [backup, logging, observability, IN-03]
dependency_graph:
  requires: ["82-01"]
  provides: ["IN-03 WARN escalation in BackupImportService.restoreOneTable"]
  affects: ["BackupImportService.restoreOneTable log output"]
tech_stack:
  added: []
  patterns: ["log.warn parameterized message for corruption signal"]
key_files:
  modified:
    - src/main/java/org/ctc/backup/service/BackupImportService.java
decisions:
  - "D-10: Soft-tolerance preserved — return totalRows (0) path unchanged; only log level and message escalated"
  - "D-11: WARN-log assertion test deferred to plan 82-07 (BackupRestoreZipOpenCountIT)"
metrics:
  duration: "~10 minutes"
  completed: "2026-05-16T21:22:08Z"
  tasks_completed: 2
  files_changed: 1
---

# Phase 82 Plan 03: IN-03 Escalate log.debug to log.warn on Missing ZIP Data Entry Summary

Single-line log level escalation in `BackupImportService.restoreOneTable`: `log.debug` replaced by `log.warn` with explicit corruption-signal message, making backup ZIP integrity gaps visible in operator logs without changing soft-tolerance semantics (return-0 preserved per D-10).

## What Was Built

Escalated the silent `log.debug` on missing ZIP data entries in `BackupImportService.restoreOneTable` to `log.warn` with an explicit corruption-signal message.

**Before:**
```java
log.debug("No data entry for table={} (entryPath={}) — restore count is 0",
        ref.tableName(), entryPath);
```

**After:**
```java
log.warn("Backup ZIP has no data entry for table={} (entryPath={}) — possible corruption or schema regression",
        ref.tableName(), entryPath);
```

The `return totalRows;` (returning 0) immediately after the log statement is unchanged — soft-tolerance semantics preserved per D-10.

## Commits

| Hash | Message | Files |
|------|---------|-------|
| 6934044b | fix(82): IN-03 warn on missing ZIP data entry | src/main/java/org/ctc/backup/service/BackupImportService.java |

## Verification Results

| Check | Result |
|-------|--------|
| `grep -c 'log.warn("Backup ZIP has no data entry for table='` | 1 (PASS) |
| `grep -c 'log.debug("No data entry for table='` | 0 (PASS) |
| `./mvnw test-compile -q` | exit 0 (PASS) |
| `./mvnw spotbugs:check -DskipTests -q` | exit 0 (PASS) |
| `./mvnw verify -Dit.test='BackupImport*' -DfailIfNoTests=false` | exit 0 (PASS) |
| Branch | gsd/v1.11-tooling-and-cleanup (PASS) |
| Only 1 file in commit | src/main/java/org/ctc/backup/service/BackupImportService.java (PASS) |

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None. Log-level escalation reduces repudiation risk (T-82-05) without introducing new attack surface.

## Decisions Made

- D-10 applied: Soft tolerance preserved — `return totalRows;` (returns 0) unchanged after the warn call. The audit row for the missing table still reflects 0 rows restored.
- D-11 applied: No test added in this plan. The WARN-log assertion (`givenZipWithMissingEntry_whenRestore_thenWarnLogEmittedAndZeroRows`) is co-located with the BACK-03 IT in plan 82-07 (`BackupRestoreZipOpenCountIT`).

## Self-Check: PASSED

- BackupImportService.java: FOUND
- Commit 6934044b: FOUND
