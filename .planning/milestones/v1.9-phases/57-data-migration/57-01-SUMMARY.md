---
phase: 57-data-migration
plan: "01"
type: execute
status: complete
completed: 2026-04-27
subsystem: db-migration
tags:
  - test
  - integration
  - flyway
  - migration
  - tdd-red
dependency_graph:
  requires:
    - "56-05: V3__add_season_phase_tables.sql schema (season_phases, phase_teams, matchdays.phase_id nullable)"
  provides:
    - "V4MigrateSeasonsToPhasesIT: six failing integration tests for V4 migration (TDD contract)"
  affects:
    - "57-02: V4__MigrateSeasonsToPhases.java must turn these tests GREEN"
tech_stack:
  added: []
  patterns:
    - "Programmatic Flyway harness: EmbeddedDatabaseBuilder + Flyway.configure().target(N) (new pattern for this codebase)"
    - "TDD-RED test class without @SpringBootTest (first such class in repo)"
key_files:
  created:
    - src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java
  modified: []
decisions:
  - "D-15: Programmatic Flyway (EmbeddedDatabaseBuilder + Flyway.configure()) over @SpringBootTest — enables seed-before-V4 pattern"
  - "D-16: Six locked test method names per CONTEXT.md"
  - "D-17: 3-season seed scenario with one empty season, one without playoff, one with playoff"
  - "D-19: Test package db.migration mirrors production package"
metrics:
  duration: "~12 minutes"
  completed: "2026-04-27"
  tasks_completed: 1
  tasks_total: 1
  files_changed: 1
---

# Phase 57 Plan 01: TDD-RED Integration Test for V4 Migration Summary

**One-liner:** Programmatic Flyway integration test (6 methods, EmbeddedDatabaseBuilder + target("4")) establishes the failing RED contract that drives Plan 02's V4__MigrateSeasonsToPhases implementation.

## What Was Built

Created `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` (355 lines).

The test class:
- Uses `EmbeddedDatabaseBuilder` + `Flyway.configure()` (programmatic harness — new pattern for this codebase, no @SpringBootTest)
- Runs V1+V2+V3 with `target("3")`, seeds legacy data via JdbcTemplate, then runs V4 with `target("4")`
- Seeds the D-17 scenario: 3 seasons (one with playoff, one without, one empty), 4 teams (2 per non-empty season), 4 matchdays (2 per non-empty season), 1 playoff on Season 1
- Contains 6 @Test methods with the exact D-16 locked names
- Contains `seedLegacyData()` private helper with deterministic UUID constants
- Contains defensive `toUUID(Object)` helper for H2/MariaDB portability

## Test RED State Confirmation

`./mvnw -q test -Dtest=V4MigrateSeasonsToPhasesIT` exits **NON-ZERO** with:

```
org.flywaydb.core.api.FlywayException: No migration with a target version 4 could be found.
Ensure target is specified correctly and the migration exists.
    at org.flywaydb.core.internal.info.MigrationInfoServiceImpl.validateTarget(MigrationInfoServiceImpl.java:261)
    at db.migration.V4MigrateSeasonsToPhasesIT.setUp(V4MigrateSeasonsToPhasesIT.java:85)

[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
```

**Root cause:** Flyway cannot find `V4__MigrateSeasonsToPhases.java` on the classpath because Plan 02 has not yet created it. The error fires in `@BeforeAll setUp()` at the second `Flyway.configure().target("4").migrate()` call — this is the expected TDD-RED state.

## Compile Verification

`./mvnw -q test-compile` exits **0** (only Lombok deprecation warnings, no errors). The test class compiles cleanly because it references Flyway, Spring JdbcTemplate, and JUnit 5 — all already on the classpath. It does NOT import the V4 production class directly; Flyway discovers it at runtime via classpath scanning.

## Production Code Verification

`git diff --name-only HEAD -- src/main/` returns **empty** — no production code was modified. Only the new test file was added:

```
src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java (355 lines, 1 file created)
```

No V1/V2/V3 SQL files were modified.

## Plan 02 Transition Note

`V4__MigrateSeasonsToPhases.java` implementation in Plan 02 will turn this RED to GREEN. When Plan 02 creates the production migration class in `src/main/java/db/migration/V4__MigrateSeasonsToPhases.java`, Flyway's classpath scanner will discover it, `target("4")` will execute successfully, and all six test methods will verify the migration state.

## Deviations from Plan

None — plan executed exactly as written.

The only minor note: The V1 schema uses `season_year` and `season_number` (not `year` and `number` as mentioned in the plan's column list overview). The seed INSERT statements correctly use the actual V1 column names. Similarly, V1's `teams.short_name` is `VARCHAR(50)` (not 10), but all seeded short names fit within that limit. These were discovered by reading V1__initial_schema.sql before writing the seed code — no deviations required.

## Known Stubs

None.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries were introduced. This plan adds only a test file that uses an isolated H2 in-memory database. Threat register mitigations T-57-01 and T-57-02 are implemented: `EmbeddedDatabaseBuilder` provides per-JVM isolation, and real programmatic Flyway assertions prevent silent test mocking.

## Self-Check: PASSED

- File `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` exists: FOUND
- Commit `f4d8004` exists: FOUND
- `./mvnw -q test-compile` exit code: 0 (PASS)
- `./mvnw -q test -Dtest=V4MigrateSeasonsToPhasesIT` exit code: non-zero (RED — PASS)
- No production code modified: CONFIRMED
