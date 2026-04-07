---
phase: 15-alltime-standings-recovery
plan: 01
subsystem: standings
tags: [alltime, standings, aggregation, tdd]
dependency_graph:
  requires: []
  provides: [calculateAlltimeStandings, TeamStanding.merge]
  affects: [StandingsController alltime branch]
tech_stack:
  added: []
  patterns: [cross-season aggregation, parent team resolution]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/StandingsService.java
    - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
    - src/main/java/org/ctc/admin/controller/StandingsController.java
    - src/test/java/org/ctc/admin/controller/StandingsControllerTest.java
decisions:
  - id: D-01
    summary: "Reuse calculateStandings(seasonId) per season, sum W/D/L/Points/PointsFor/PointsAgainst"
  - id: D-02
    summary: "No Buchholz in alltime - merge() does not touch buchholz field"
  - id: D-03
    summary: "Sub-team resolution via Team.getParentOrSelf()"
metrics:
  duration: 5m
  completed: 2026-04-07T08:51:00Z
  tasks: 2
  files: 4
  tests_added: 7
  total_tests: 820
---

# Phase 15 Plan 01: Alltime Standings Recovery Summary

Cross-season team standings aggregation via calculateAlltimeStandings() with parent team resolution and per-season MatchScoring respect.

## Tasks Completed

| Task | Name | Key Changes |
|------|------|-------------|
| 1 | TDD: Unit tests + calculateAlltimeStandings + merge | 7 unit tests in AlltimeStandingsTest, calculateAlltimeStandings() method, TeamStanding.merge() |
| 2 | Controller wiring + integration test | Replaced TODO stub with service call, enhanced integration test with match data |

## Implementation Details

### StandingsService.calculateAlltimeStandings()
- Iterates all seasons via `seasonRepository.findAll()`
- Delegates to existing `calculateStandings(seasonId)` per season
- Resolves sub-teams to parent via `standing.getTeam().getParentOrSelf()`
- Merges standings into alltime map using `TeamStanding.merge()`
- Sorts by points desc, point difference desc, pointsFor desc
- Buchholz remains 0 (not set in alltime context)

### TeamStanding.merge()
- Accumulates: wins, draws, losses, points, pointsFor, pointsAgainst
- Does NOT touch buchholz field

### Controller Change
- Replaced `java.util.List.of()` stub with `standingsService.calculateAlltimeStandings()`
- Removed TODO comment

### Integration Test Enhancement
- Creates Matchday + Match with scores before asserting alltime standings
- Asserts `hasSize(greaterThan(0))` instead of just checking attribute exists

## Test Results

- 7 new unit tests in `AlltimeStandingsTest` nested class
- 820 total tests passing
- `./mvnw verify` BUILD SUCCESS

## Deviations from Plan

None - plan executed as written. Worktree merge required manual conflict resolution due to stale worktree base.

## Self-Check: PASSED
