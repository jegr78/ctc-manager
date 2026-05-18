---
phase: 82-backup-cleanup
plan: "06"
subsystem: backup
tags: [backup, schema, guard-test, integration-test]
dependency_graph:
  requires: [82-01, 82-02, 82-03, 82-04, 82-05]
  provides: [BACK-01]
  affects: []
tech_stack:
  added: []
  patterns: [SpringBootTest, AssertJ, Tag-integration]
key_files:
  created:
    - src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java
  modified: []
decisions:
  - "BackupSchemaGuardTest uses @SpringBootTest (not plain unit test) because BackupSchema requires EntityManagerFactory + EntityTopoSorter injection via @PostConstruct"
  - "Filename uses *Test suffix (not *IT) per 82-VALIDATION.md validation map, but @Tag(integration) routes to Failsafe"
  - "Assertion messages cite Phase 75 SCHEMA_VERSION gate and BackupRoundTripIT per D-17"
metrics:
  duration: "~7 minutes (Spring context startup + full Failsafe IT run)"
  completed: "2026-05-16"
  tasks_completed: 2
  files_count: 1
---

# Phase 82 Plan 06: BACK-01 Schema-Version + Export-Order Guard Test Summary

**One-liner:** Guard IT pinning `BackupSchema.SCHEMA_VERSION == 1` and `getExportOrder().size() == 24` with human-readable D-17 failure messages citing Phase 75 SCHEMA_VERSION gate and BackupRoundTripIT.

## What Was Built

New integration test class `BackupSchemaGuardTest` in package `org.ctc.backup.schema` locking the two wire-contract invariants of the backup subsystem:

1. `givenBackupSchema_whenInspected_thenSchemaVersionIsOne` — asserts `BackupSchema.SCHEMA_VERSION == 1` with failure message: "BackupSchema.SCHEMA_VERSION changed from 1 — this is a wire contract bump; see Phase 75 SCHEMA_VERSION gate or write a new migration phase"

2. `givenBackupSchema_whenInspected_thenExportOrderHasTwentyFourEntities` — asserts `backupSchema.getExportOrder().size() == 24` with failure message: "BackupSchema.EXPORT_ORDER size changed from 24 — if a new entity was added, bump SCHEMA_VERSION and update BackupRoundTripIT expected row-count assertions"

## Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| Task 1 | Create BackupSchemaGuardTest (2 IT methods) | d18a9be1 | src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java |
| Task 2 | Atomic commit test(82): BACK-01 | d18a9be1 | (same commit — tasks 1+2 combined per plan) |

## Assertion Messages Used

**Test 1 (SCHEMA_VERSION):**
```
"BackupSchema.SCHEMA_VERSION changed from 1 — this is a wire contract bump; see Phase 75 SCHEMA_VERSION gate or write a new migration phase"
```

**Test 2 (EXPORT_ORDER size):**
```
"BackupSchema.EXPORT_ORDER size changed from 24 — if a new entity was added, bump SCHEMA_VERSION and update BackupRoundTripIT expected row-count assertions"
```

## Verification

- `./mvnw verify -Dit.test=BackupSchemaGuardTest -DfailIfNoTests=false -q` exited 0
- Both guard tests pass on the `dev` profile (H2 in-memory, Spring Boot 4.0.6)
- Commit `d18a9be1` contains exactly one file

## Deviations from Plan

None — plan executed exactly as written. BackupSchema.SCHEMA_VERSION confirmed at 1 (line 32), getExportOrder().size() confirmed at 24 (aligned with BackupSchemaTopologyIT.hasSize(24)).

## Known Stubs

None.

## Threat Flags

None — test-only addition, no new production code, no new trust boundary.

## Self-Check: PASSED

- [x] File exists: `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java`
- [x] Commit `d18a9be1` exists on branch `gsd/v1.11-tooling-and-cleanup`
- [x] Commit contains exactly one file
- [x] Commit subject matches exactly: `test(82): BACK-01 schema-version + export-order guard test`
- [x] Both tests passed (`./mvnw verify ... -q` exit 0)
