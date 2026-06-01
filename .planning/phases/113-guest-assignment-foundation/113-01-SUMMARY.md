---
phase: 113-guest-assignment-foundation
plan: 01
subsystem: data-model
tags: [flyway, entity, backup-restore, guest-drivers]
requires: []
provides:
  - "race_lineups.is_guest column (NOT NULL default false)"
  - "RaceLineup.isGuest() / 4-arg constructor (race, driver, team, guest)"
  - "RaceLineupRestorer round-trips is_guest (backward-compatible)"
affects:
  - "Phase 113-02 (service reads/writes the guest flag)"
  - "Phase 114 scoring, Phase 115 visual marking (derive guest via lineup join)"
tech-stack:
  added: []
  patterns:
    - "Additive Flyway column migration (BOOLEAN NOT NULL DEFAULT FALSE), H2+MariaDB compatible"
    - "row.path(field).asBoolean(false) for backward-compatible backup restore"
key-files:
  created:
    - src/main/resources/db/migration/V18__add_race_lineups_is_guest.sql
    - src/test/java/db/migration/V18MigrationIT.java
  modified:
    - src/main/java/org/ctc/domain/model/RaceLineup.java
    - src/main/java/org/ctc/backup/restore/entity/RaceLineupRestorer.java
    - src/test/java/org/ctc/backup/restore/entity/RaceLineupRestorerTest.java
key-decisions:
  - "V18 is column-only: no UNIQUE re-add — uk_race_lineup_driver already exists in V1 (planner-verified, supersedes CONTEXT D-03 DB part; dedup moves to service layer in 113-02)"
  - "Guest flag mapped via @Column(name=\"is_guest\") on a plain boolean field named guest; Lombok generates isGuest()/setGuest()"
  - "3-arg roster constructor preserved untouched; 4-arg constructor added for guests"
requirements-completed: [GUEST-04]
duration: 12 min
completed: 2026-06-01
---

# Phase 113 Plan 01: Guest Assignment Data-Model Foundation Summary

Added the persistent `is_guest` flag to the lineup data model: Flyway V18 adds an additive `race_lineups.is_guest` BOOLEAN NOT NULL DEFAULT FALSE column, `RaceLineup` exposes the flag via a `@Column(name = "is_guest")`-mapped boolean and a new 4-arg constructor, and `RaceLineupRestorer` round-trips the flag through backup export/import with a pre-V18 backward-compatible default.

## Tasks

- **Task 1** — V18 migration (`ALTER TABLE race_lineups ADD COLUMN is_guest ...`), `RaceLineup.guest` field + 4-arg constructor, `V18MigrationIT` (column-exists + NOT-NULL assertions). Commit `27bcc5a3`.
- **Task 2** — `RaceLineupRestorer.INSERT_SQL` extended with `is_guest` (7 placeholders), `setBoolean(5, row.path("guest").asBoolean(false))`, timestamps shifted to 6/7; test extended with `is_guest` SQL assertion, `setBoolean(5, true)`, and a new pre-V18 (no `guest` field → `setBoolean(5, false)`) backward-compat test. Commit `e5295fce`.

2 tasks, 5 files (2 created, 3 modified), 2 atomic commits.

## Verification

- `./mvnw -Dit.test=V18MigrationIT -DfailIfNoTests=false clean verify` — V18MigrationIT 2/2 green (build-level JaCoCo check reports a partial-run ratio because only one IT executes under a targeted `-Dit.test`; authoritative coverage gate is the full `clean verify -Pe2e` at phase end).
- `./mvnw -Dtest=RaceLineupRestorerTest clean test` — 3/3 green, BUILD SUCCESS.
- Clean build compiles all existing 3-arg `RaceLineup` callers (no signature breakage).

## Deviations from Plan

None - plan executed exactly as written. (Incidental cleanup: removed a hard-banned `Phase 75 / Plan 04` marker comment from the touched `RaceLineupRestorerTest` Javadoc per CLAUDE.md "remove pollution from touched files"; updated the restorer schema Javadoc to mention `is_guest`.)

**Total deviations:** 0.

## Issues Encountered

None.

## Next Phase Readiness

Ready for 113-02 — `RaceLineupService` can now read/write `isGuest()`, construct guest entries via the 4-arg constructor, and the column persists through backup.
