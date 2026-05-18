---
phase: 82-backup-cleanup
plan: "08"
subsystem: backup
tags: [backup, roundtrip, integration-test, parity, BACK-05]
dependency_graph:
  requires: [82-01, 82-02, 82-03, 82-04, 82-05]
  provides: [24-entity-row-count-parity-test-H2, 24-entity-row-count-parity-test-MariaDB]
  affects: [BackupRoundTripIT]
tech_stack:
  added: []
  patterns: [JUnit5-Nested, AssertJ-map-equality, per-table-failure-message]
key_files:
  created: []
  modified:
    - src/test/java/org/ctc/backup/service/BackupRoundTripIT.java
decisions:
  - "Reused existing captureRowCounts() + exportToBytes() helpers unchanged; no new helpers"
  - "Per-table for-loop assertion with .as('row-count parity for table=...') replaces single-map assertThat for richer failure messages"
  - "Non-trivial-fixture safeguard added (>12 entities with data) to catch silent fixture regression"
  - "MariaDB nested class inherits @EnabledIfSystemProperty(docker.available) from class header; no per-method gate"
metrics:
  duration: "~8 minutes (compilation + H2 IT run)"
  completed: "2026-05-16T22:37:05Z"
  tasks_completed: 3
  files_changed: 1
---

# Phase 82 Plan 08: BACK-05 Extend BackupRoundTripIT to All 24 Entities Summary

**One-liner:** Added dedicated 24-entity row-count parity `@Test` to both `H2RoundTripTests` and `MariaDbRoundTripTests` nested classes, using per-table AssertJ assertions that produce table-named failure messages.

## What Was Built

Two new `@Test void givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch()` methods added to `BackupRoundTripIT`:

- **`H2RoundTripTests`**: Runs on every `./mvnw verify` (H2 + dev profile). Captures pre-counts, executes full export → stage → execute round-trip, captures post-counts, then iterates `backupSchema.getExportOrder()` and asserts per-table equality with `.as("row-count parity for table=" + ref.tableName())` for targeted failure messages.
- **`MariaDbRoundTripTests`**: Identical logic, inherits `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` from the nested class header. Skipped on dev machines without Docker; exercised in CI.

Both tests include a non-trivial-fixture safeguard:
```java
assertThat(preCounts.values().stream().filter(c -> c > 0).count())
    .as("fixture must have data in at least 12 entities for a meaningful parity test")
    .isGreaterThan(12);
```

Existing helpers `captureRowCounts()` and `exportToBytes()` were reused without modification. The existing spot-check tests (`givenH2DevFixture_whenExportWipeImport_...` / `givenLiveMariaDb_whenExportWipeImport_...`) remain unchanged.

## Tasks Completed

| Task | Name | Status |
|------|------|--------|
| 1 | Add parity @Test to H2RoundTripTests | Done |
| 2 | Add parity @Test to MariaDbRoundTripTests | Done |
| 3 | Run H2 IT + atomic commit | Done |

## Verification Results

| Check | Result |
|-------|--------|
| `grep -c 'void givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch'` | 2 |
| `grep -c "row-count parity for table="` | 2 |
| `grep -c 'private Map<String, Long> captureRowCounts()'` | 2 (unchanged) |
| `./mvnw verify -Dit.test='BackupRoundTripIT$H2RoundTripTests' -DfailIfNoTests=false -q` | EXIT 0 |
| MariaDB nested class | Skipped (docker.available not set on dev machine, expected per D-25) |
| `git log -1 --pretty=%s` | `test(82): BACK-05 extend BackupRoundTripIT to all 24 entities` |
| Files in commit | `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` only |

## Commit

`ecf64f9c` — `test(82): BACK-05 extend BackupRoundTripIT to all 24 entities`

## Deviations from Plan

None — plan executed exactly as written. Per-table `for`-loop assertion instead of single `assertThat(postCounts).isEqualTo(preCounts)` was used to produce per-table failure messages; this is consistent with the plan's `<behavior>` section which specified the per-table assertion pattern with `.as("row-count parity for table=...")`.

## Known Stubs

None.

## Threat Flags

None — test-only change, no new production surface introduced.

## Self-Check: PASSED

- `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` modified and committed
- Commit `ecf64f9c` exists: verified via `git log -1`
- H2 nested class: green (exit 0)
- MariaDB nested class: skipped per `docker.available` gate (expected)
