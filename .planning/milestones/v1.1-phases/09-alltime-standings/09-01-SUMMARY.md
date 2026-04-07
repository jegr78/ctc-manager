---
phase: 09-alltime-standings
plan: 01
subsystem: domain/standings
tags: [alltime, standings, aggregation, tdd]
dependency_graph:
  requires: []
  provides: [calculateAlltimeStandings, alltime-standings-endpoint]
  affects: [StandingsService, StandingsController]
tech_stack:
  added: []
  patterns: [cross-season-aggregation, parent-team-resolution, per-season-delegation]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/StandingsService.java
    - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
    - src/main/java/org/ctc/admin/controller/StandingsController.java
    - src/test/java/org/ctc/admin/controller/StandingsControllerTest.java
decisions:
  - Reused TeamStanding with merge() method instead of creating alltime-specific class
  - Delegated to calculateStandings(seasonId) per season for consistent scoring rule application
  - Resolved sub-teams to parent via getParentOrSelf() after per-season calculation
metrics:
  duration: ~3min
  completed: 2026-04-05
  tasks: 2/2
  tests_added: 7
  files_modified: 4
---

# Phase 9 Plan 1: Alltime Standings Implementation Summary

Cross-season team standings aggregation via calculateAlltimeStandings() delegating to per-season calculateStandings() with parent-team resolution via getParentOrSelf()

## What Was Done

### Task 1: TDD -- calculateAlltimeStandings() in StandingsService (0979c0f)
- Added `merge(TeamStanding other)` method to TeamStanding inner class for cross-season accumulation
- Implemented `calculateAlltimeStandings()` method with `@Transactional(readOnly = true)`
- Iterates all seasons, delegates to `calculateStandings(season.getId())` per season
- Resolves sub-team results to parent team via `getParentOrSelf()`
- Sorts results by points (desc), point difference (desc), pointsFor (desc)
- No Buchholz in alltime standings (per D-02)
- 7 new unit tests in `@Nested class AlltimeStandingsTest`:
  1. Multi-season aggregation (W/D/L/Pts/PF/PA)
  2. Different MatchScoring per season (3-1-0 vs 2-1-0)
  3. Sub-team aggregation to parent team
  4. Empty season exclusion
  5. No seasons returns empty list
  6. Sorting by points, then point diff, then pointsFor
  7. Buchholz is always zero in alltime

### Task 2: Wire alltime standings into controller (d5c6e56)
- Replaced `java.util.List.of()` TODO placeholder with `standingsService.calculateAlltimeStandings()`
- Updated integration test to create match data and verify `hasSize(greaterThan(0))`
- Added `MatchdayRepository` and `MatchRepository` injections to test class

## Deviations from Plan

None -- plan executed exactly as written.

## Verification

- `./mvnw test -Dtest=StandingsServiceTest` -- all tests pass (existing + 7 new)
- `./mvnw test -Dtest=StandingsControllerTest` -- all tests pass (existing + updated alltime test)
- `./mvnw verify` -- full test suite passes with coverage maintained

## Known Stubs

None -- alltime standings are fully wired with real data.

## Self-Check: PASSED

- All 4 modified files exist
- Both commits found (0979c0f, d5c6e56)
- calculateAlltimeStandings present in service and controller
- AlltimeStandingsTest nested class present
- hasSize(greaterThan(0)) assertion present in controller test
