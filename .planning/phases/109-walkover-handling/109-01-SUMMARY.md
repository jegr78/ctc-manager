---
phase: 109
plan: "01"
subsystem: scoring-data
tags: [flyway, jpa, scoring, walkover]
requires: []
provides:
  - matches.walkover_team_id nullable FK column (V17)
  - Match.walkoverTeam @ManyToOne mapping
  - recomputeMatchScoresFromAllLegs walkover guard
affects: [ScoringService, Match, standings-read-time-scoring]
tech-stack:
  added: []
  patterns: [flyway-additive-migration, lazy-manytoone, read-time-scoring-guard]
key-files:
  created:
    - src/main/resources/db/migration/V17__add_matches_walkover_team_id.sql
    - src/test/java/db/migration/V17MigrationIT.java
  modified:
    - src/main/java/org/ctc/domain/model/Match.java
    - src/main/java/org/ctc/domain/service/ScoringService.java
    - src/test/java/org/ctc/domain/service/ScoringServiceTest.java
key-decisions:
  - V17 is a plain 2-statement .sql (ADD COLUMN + ADD CONSTRAINT FK); H2+MariaDB compatible; V1-V16 untouched
  - walkoverTeam mapped exactly like awayTeam (@ManyToOne LAZY, @JoinColumn walkover_team_id), added to @ToString exclude
  - recompute guard returns before any setHomeScore/setAwayScore when match.getWalkoverTeam() != null, mirroring the isBye() guard
requirements-completed: [WO-02]
duration: ~25 min
completed: 2026-05-30
---

# Phase 109 Plan 01: Walkover Data Foundation Summary

Flyway V17 adds a nullable `walkover_team_id` FK to `matches`; `Match` exposes a lazy `walkoverTeam` mapping; and `ScoringService.recomputeMatchScoresFromAllLegs` now returns early for walkover races so it never overwrites the null scores the standings branch relies on (D-05/D-08).

**Tasks:** 3 | **Files:** 5 (2 created, 3 modified)

## What was built

- **Task 1 — V17 + V17MigrationIT (one green commit):** `V17__add_matches_walkover_team_id.sql` (`ADD COLUMN walkover_team_id UUID NULL` + `ADD CONSTRAINT fk_match_walkover_team FOREIGN KEY ... REFERENCES teams(id)`). `V17MigrationIT` mirrors `V13MigrationIT` (`@CtcDevSpringBootContext @Tag("integration")`), asserting the column exists and is nullable on H2. Tests run: 2, green — authored together with the migration, never red/@Disabled.
- **Task 2 — Match.walkoverTeam:** `@ManyToOne(fetch = LAZY) @JoinColumn(name = "walkover_team_id") private Team walkoverTeam;`, mirroring `awayTeam`; added `"walkoverTeam"` to `@ToString(exclude=...)`. Lombok generates the accessors.
- **Task 3 — ScoringService guard + unit test (TDD):** added `if (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null) return;` immediately after the `isBye()` guard in `recomputeMatchScoresFromAllLegs`. Unit test `givenWalkoverMatchRace_whenRecomputeMatchScoresFromAllLegs_thenScoresRemainUnchanged` was written first and confirmed RED (`expected: <null> but was: <0>`), then GREEN after the guard. Committed green.

## Grep-all-usages audit (CLAUDE.md)

`recomputeMatchScoresFromAllLegs` / `aggregateMatchScores` callsites:
- `RaceService:268` → `recomputeMatchScoresFromAllLegs` (clear path) — **needs the guard** (covered).
- `RaceService:270/289`, `CsvImportService:253/363`, `TestDataService:823` → `aggregateMatchScores` — **no change needed**: `aggregateMatchScores` already returns on `results.isEmpty()` (a walkover race has no results), so it never writes scores for a walkover. Left unchanged.

## Verification

- `./mvnw -Dit.test=V17MigrationIT -DfailIfNoTests=false verify -DskipE2e=true` → Tests run: 2, Failures: 0.
- `./mvnw -Dtest=ScoringServiceTest test` → 37 tests green (incl. new walkover test); `aggregateMatchScores` method body unchanged (git diff confirms).
- `git diff --name-only` under `db/migration` shows only the new `V17` file.

## Deviations from Plan

None - plan executed exactly as written.

## Self-Check: PASSED

Ready for 109-02 (processMatch walkover branch + TeamStanding.hasWalkover).
