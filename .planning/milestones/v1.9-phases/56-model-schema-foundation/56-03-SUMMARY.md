---
phase: 56-model-schema-foundation
plan: 03
subsystem: schema
tags:
  - flyway
  - sql
  - migration
  - schema
  - ddl
requirements:
  - MODEL-02
  - MODEL-04
  - MODEL-05
  - MODEL-06
  - MIGR-01
  - MIGR-07
dependency_graph:
  requires:
    - V1__initial_schema.sql (frozen — references seasons, race_scorings, match_scorings, teams, matchdays, playoffs)
    - V2__add_fk_indexes.sql (frozen — pattern source for FK index naming)
  provides:
    - season_phases table (Season → Phase root)
    - season_phase_groups table (Phase → Group)
    - phase_teams table (per-phase team roster)
    - matchdays.phase_id + matchdays.group_id columns (nullable; flipped NOT NULL by Phase 57)
    - playoffs.phase_id column (nullable + UNIQUE; flipped NOT NULL by Phase 57)
  affects:
    - Plan 56-04 (entities + repositories backed by these tables)
    - Plan 56-05 (Season.phases collection mapped against season_phases)
    - Phase 57 (V4 migration backfills phase_id and flips NOT NULL)
    - Phase 61 (V?? drops legacy seasons.* columns + playoff_seasons)
tech-stack:
  added: []
  patterns:
    - Additive Flyway migration (CLAUDE.md "Do Not Modify Flyway Migrations")
    - Long FK constraint names (D-02 deviation from V1 abbreviated style for readability)
    - VARCHAR(20) + @Enumerated(STRING) for enum columns (no DB CHECK — D-03)
    - FK index naming idx_<table>_<column> with IF NOT EXISTS (V2 pattern)
    - DB-level UNIQUE for business-key uniqueness (uk_season_phase_type, uk_phase_team, uk_playoff_phase)
key-files:
  created:
    - src/main/resources/db/migration/V3__add_season_phase_tables.sql
  modified: []
decisions:
  - "Single V3 migration file (not split V3+V4) per D-02 default — keeps related schema additions atomic"
  - "matchdays.phase_id, matchdays.group_id, playoffs.phase_id remain NULLABLE in Phase 56 (D-02). Phase 57 backfills data and flips NOT NULL"
  - "Long FK names (fk_seasonphase_*, fk_phaseteam_*, fk_matchday_phase, fk_matchday_group, fk_playoff_phase, fk_seasonphasegroup_phase) — deliberate departure from V1 abbreviations (fk_st_*, fk_md_*) for readability of the new constraints (D-02)"
  - "No ON DELETE CASCADE on any V3 FK (D-02). JPA orphanRemoval=true on Season.phases (Plan 05) and SeasonPhase.groups (Plan 02) handles cascade at the application layer"
  - "No CHECK constraints on phase_type / layout (D-03). @Enumerated(EnumType.STRING) plus typed PhaseType / PhaseLayout enums (delivered by Plan 56-01) prevent invalid values from the application side"
  - "DB-level UNIQUE (season_id, phase_type) on season_phases (D-03 / MODEL-02) — enforces max 1× REGULAR / PLAYOFF / PLACEMENT per season at the schema level, independent of any service-layer guard"
  - "DB-level UNIQUE (phase_id, team_id) on phase_teams (D-03 / MODEL-04) — same belt-and-suspenders strategy"
  - "Header comment uses three lines (extends V1's two-line precedent with a third describing the additive nature) — plan §1 explicitly accepts this"
metrics:
  duration: "~5 min (migration write + grep verification + ./mvnw verify run)"
  completed: 2026-04-26
---

# Phase 56 Plan 03: Add Season Phase Tables Migration — Summary

Created the additive Flyway V3 migration `V3__add_season_phase_tables.sql` that lays down the Season → Phase → Group hierarchy at the schema level: three new tables (`season_phases`, `season_phase_groups`, `phase_teams`), nullable `phase_id` / `group_id` FK columns on `matchdays` and `playoffs`, ten FK indexes, and the three required UNIQUE constraints (`uk_season_phase_type`, `uk_phase_team`, `uk_playoff_phase`).

## What Was Built

### V3 Migration File

Single new file `src/main/resources/db/migration/V3__add_season_phase_tables.sql` (69 lines) — V1 and V2 are byte-identical to HEAD, preserving Flyway checksum integrity (MIGR-07).

Structure of V3 in source order:

1. **Three-line header comment** — describes purpose, additive nature, and H2 + MariaDB compatibility.
2. **`CREATE TABLE season_phases`** — UUID PK, FK to `seasons` / `race_scorings` / `match_scorings`, scoring/format/duration columns moved here from `seasons` (Java side stays put until Phase 61 per D-01), `phase_type VARCHAR(20)`, `layout VARCHAR(20)`, `format VARCHAR(20) DEFAULT 'LEAGUE'`, `label VARCHAR(255)` for display, audit columns, and `CONSTRAINT uk_season_phase_type UNIQUE (season_id, phase_type)`.
3. **`CREATE TABLE season_phase_groups`** — UUID PK, NOT NULL `phase_id` FK to `season_phases`, `name VARCHAR(255)`, `sort_index`, audit columns, FK `fk_seasonphasegroup_phase`.
4. **`CREATE TABLE phase_teams`** — UUID PK, NOT NULL `phase_id` FK, NOT NULL `team_id` FK, NULLABLE `group_id` FK to `season_phase_groups`, audit columns, FKs `fk_phaseteam_phase` / `fk_phaseteam_team` / `fk_phaseteam_group`, and `CONSTRAINT uk_phase_team UNIQUE (phase_id, team_id)`.
5. **`ALTER TABLE matchdays`** — `ADD COLUMN phase_id UUID` + `ADD COLUMN group_id UUID` (both NULLABLE per D-02), plus FK constraints `fk_matchday_phase` and `fk_matchday_group`.
6. **`ALTER TABLE playoffs`** — `ADD COLUMN phase_id UUID` (NULLABLE), FK `fk_playoff_phase`, and `ADD CONSTRAINT uk_playoff_phase UNIQUE (phase_id)` mirroring V1's `uk_playoff_season` precedent.
7. **Ten FK indexes** — `idx_season_phases_season_id`, `idx_season_phases_race_scoring_id`, `idx_season_phases_match_scoring_id`, `idx_season_phase_groups_phase_id`, `idx_phase_teams_phase_id`, `idx_phase_teams_team_id`, `idx_phase_teams_group_id`, `idx_matchdays_phase_id`, `idx_matchdays_group_id`, `idx_playoffs_phase_id`. All use `CREATE INDEX IF NOT EXISTS` per V2 convention.

### UNIQUE Constraints Declared (verbatim from V3)

| Constraint Name | Table | Columns | Requirement |
|-----------------|-------|---------|-------------|
| `uk_season_phase_type` | `season_phases` | `(season_id, phase_type)` | MODEL-02 |
| `uk_phase_team` | `phase_teams` | `(phase_id, team_id)` | MODEL-04 |
| `uk_playoff_phase` | `playoffs` | `(phase_id)` | MODEL-06 |

### FK Constraint Names (long form per D-02)

| Constraint | From | To |
|------------|------|-----|
| `fk_seasonphase_season` | `season_phases.season_id` | `seasons(id)` |
| `fk_seasonphase_race_scoring` | `season_phases.race_scoring_id` | `race_scorings(id)` |
| `fk_seasonphase_match_scoring` | `season_phases.match_scoring_id` | `match_scorings(id)` |
| `fk_seasonphasegroup_phase` | `season_phase_groups.phase_id` | `season_phases(id)` |
| `fk_phaseteam_phase` | `phase_teams.phase_id` | `season_phases(id)` |
| `fk_phaseteam_team` | `phase_teams.team_id` | `teams(id)` |
| `fk_phaseteam_group` | `phase_teams.group_id` | `season_phase_groups(id)` |
| `fk_matchday_phase` | `matchdays.phase_id` | `season_phases(id)` |
| `fk_matchday_group` | `matchdays.group_id` | `season_phase_groups(id)` |
| `fk_playoff_phase` | `playoffs.phase_id` | `season_phases(id)` |

All ten use plain `REFERENCES` (default RESTRICT). Zero `ON DELETE CASCADE`, zero `CHECK` constraints.

## How It Was Built

1. Read the plan + CONTEXT.md (D-02 / D-03) + PATTERNS.md from git history (the worktree only contained the Wave 1 SUMMARY for plan 56-01).
2. Read `V1__initial_schema.sql` and `V2__add_fk_indexes.sql` to align with project DDL style: UUID PKs, `VARCHAR(20)` for enum strings, `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` audit columns, `CONSTRAINT <name> FOREIGN KEY ... REFERENCES <table>(id)` syntax.
3. Wrote `V3__add_season_phase_tables.sql` in one pass following the `<interfaces>` block verbatim — three CREATE TABLE blocks, two ALTER TABLE blocks, ten CREATE INDEX statements.
4. Verified all 13 acceptance grep criteria mechanically (every grep returned the expected count).
5. Ran `./mvnw verify` end-to-end — 687 Surefire tests, 0 failures, 0 errors. JaCoCo report regenerated. Build SUCCESS (exit code 0).
6. Confirmed `git diff --quiet HEAD -- V1 V2` exits 0 (V1/V2 unchanged).
7. Committed atomically via `git commit --no-verify` per parallel-executor protocol.

## Choice: single V3 vs. split V3+V4

CONTEXT.md D-02 left this open: "planner may split into V3 (phase tables) + V4 (matchdays/playoffs FK columns) if it reduces single-file size, but a single V3 is the default."

Picked **single V3**: 69 lines is well under any readability threshold; keeping the new-table CREATEs and the matchdays/playoffs FK additions in one atomic migration matches the additive intent ("schema for Season Phase hierarchy") and avoids the temptation for someone in Phase 57 to confuse "V4 = Phase 57 backfill" with "V4 = matchdays/playoffs ADD COLUMN". Phase 57's data backfill stays the unambiguous next migration version.

## Deviations from Plan

None. Plan executed exactly as written:

- All `<interfaces>` SQL blocks copied verbatim.
- All ten FK constraint names match D-02.
- All three UNIQUE constraints present with the names specified by the plan.
- Ten FK indexes present with V2 naming convention.
- Hard rules respected: no `ON DELETE CASCADE`, no `CHECK`, no `NOT NULL` on the new phase_id columns, no modification of V1/V2.
- No Rule 1/2/3 auto-fixes triggered (no scope creep, no missing critical functionality, no blocking issues).
- No CLAUDE.md rules violated (snake_case English DDL, additive Flyway only, H2 + MariaDB compatible).

## Verification Results

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| `CREATE TABLE season_phases` | 1 | 1 | PASS |
| `CREATE TABLE season_phase_groups` | 1 | 1 | PASS |
| `CREATE TABLE phase_teams` | 1 | 1 | PASS |
| `uk_season_phase_type UNIQUE (season_id, phase_type)` | 1 | 1 | PASS |
| `uk_phase_team UNIQUE (phase_id, team_id)` | 1 | 1 | PASS |
| `uk_playoff_phase UNIQUE (phase_id)` | 1 | 1 | PASS |
| FK constraints (ten long names) | 10 | 10 | PASS |
| `ON DELETE CASCADE` | 0 | 0 | PASS |
| `CHECK` constraints | 0 | 0 | PASS |
| `label VARCHAR(255)` (MODEL-01 display label) | 1 | 1 | PASS |
| FK indexes (ten idx_<table>_<column>) | 10 | 10 | PASS |
| `ALTER TABLE … ADD COLUMN ... NOT NULL` (forbidden) | 0 | 0 | PASS |
| `phase_type VARCHAR(20)` + `layout VARCHAR(20)` | ≥2 | 2 | PASS |
| V1 + V2 byte-identical (`git diff --quiet` exits 0) | yes | yes | PASS |
| `./mvnw verify` exit code | 0 | 0 | PASS |
| Surefire tests | all green | 687 / 0 / 0 | PASS |

## Threat Surface Scan

No new security-relevant surface beyond the threats already itemised in the plan's `<threat_model>`:

- T-56-07 (V1/V2 checksum drift) — **mitigated**: `git diff --quiet HEAD -- V1 V2` exits 0.
- T-56-08 (orphan rows on phase deletion) — **mitigated**: all ten new FKs default to RESTRICT.
- T-56-10 (slow joins from missing FK indexes) — **mitigated**: all ten new FK columns indexed.
- T-56-11 (duplicate REGULAR/PLAYOFF/PLACEMENT phases per season) — **mitigated**: `uk_season_phase_type` enforced at the schema level.
- T-56-12 (duplicate phase_teams) — **mitigated**: `uk_phase_team` enforced at the schema level.

No new threats introduced. Schema additions are additive DDL only; zero data movement, zero code execution surface change.

## Known Stubs

None. Migration is data-free DDL.

## Commits

| Hash | Message |
|------|---------|
| `f9c29f2` | `feat(56-03): add Flyway V3 migration for Season Phase tables` |

## Self-Check: PASSED

- File exists: `src/main/resources/db/migration/V3__add_season_phase_tables.sql` — FOUND
- Commit `f9c29f2` is on the worktree branch — FOUND
- V1 + V2 unmodified (`git diff --quiet HEAD~1 -- V1 V2` ⇒ V1/V2 only the prior baseline; V3 is a new file)
- `./mvnw verify` exit code 0 (Surefire 687 / 0 / 0)
- All 13 acceptance greps returned the required counts
