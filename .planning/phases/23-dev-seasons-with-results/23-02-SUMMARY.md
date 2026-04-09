---
phase: 23-dev-seasons-with-results
plan: 02
subsystem: admin/test-data
tags: [seed-data, matchdays, race-results, scoring, tdd]
dependency_graph:
  requires: [season-formats, team-restructuring, scoring-service-injection, season-drivers-s1-s2]
  provides: [matchday-seeding, race-result-seeding, scoring-aggregation, dev-standings-data]
  affects: [TestDataService, TestDataServiceIntegrationTest]
tech_stack:
  added: [EntityManager]
  patterns: [tdd-red-green, jpa-flush-detach-reload, deterministic-position-rotation]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/TestDataService.java
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
decisions:
  - Used EntityManager.detach() + flush() to solve JPA Pitfall 2 (lazy results collection empty before aggregation)
  - Test matchday filters use label regex "Matchday \\d+" to isolate seed data from other test-created matchdays in shared Spring context
  - Reused same driver assignments from seedSeasonDrivers() for consistency between SeasonDriver records and RaceLineup entries
metrics:
  duration: "7m 1s"
  completed: "2026-04-09T21:22:18Z"
  tasks_completed: 2
  tasks_total: 3
  tests_added: 7
  tests_total: 867
---

# Phase 23 Plan 02: Matchday and Result Seeding Summary

Seeded matchdays, matches, races, lineups, results, and scoring for all three season formats using deterministic position data and the existing ScoringService.

## One-liner

Full race pipeline (121 races, 1452 results) for League/Swiss/Round Robin with ScoringService point calculation and match score aggregation using JPA flush+detach pattern.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | e98297f | TDD RED: 7 failing integration tests for matchdays, results, and scoring |
| 2 | 7772c88 | TDD GREEN: implement seedMatchdaysAndResults with full race seeding pipeline |

## Task Details

### Task 1: Integration tests for matchdays, results, and scoring (TDD RED)

Added 7 new test methods to `TestDataServiceIntegrationTest.java`:
- `givenDevSeed_whenStarted_thenLeagueSeasonHasFiveMatchdays` - S4 2026 has 5 matchdays
- `givenDevSeed_whenStarted_thenSwissSeasonHasFiveMatchdays` - S2 2024 has 5 matchdays
- `givenDevSeed_whenStarted_thenRoundRobinGroupAHasThreeMatchdays` - S1 Group A has 3 matchdays
- `givenDevSeed_whenStarted_thenRoundRobinGroupBHasThreeMatchdays` - S1 Group B has 3 matchdays
- `givenDevSeed_whenStarted_thenLeagueRacesHaveResults` - S4 races have exactly 12 results each
- `givenDevSeed_whenStarted_thenAllRaceResultsHaveNonZeroPoints` - DATA-07 validation
- `givenDevSeed_whenStarted_thenAllMatchesHaveNonNullScores` - all dev matches have scores

Injected `MatchdayRepository`, `RaceResultRepository`, `MatchRepository`, `RaceRepository`.

### Task 2: Implement seedMatchdaysAndResults with full race seeding pipeline (TDD GREEN)

Added `seedMatchdaysAndResults()` method to `TestDataService.java`, called from `seed()` after `seedSeasonDrivers()` and before `seedRaceLineups()`.

Season seeding structure:
- **League S4 2026**: 5 matchdays x 7 matches x 1 race = 35 races, 420 results
- **Swiss S2 2024**: 5 matchdays x 5 matches x 2 races = 50 races, 600 results
- **Round Robin S1 2023 Group A**: 3 matchdays x 3 matches x 2 races = 18 races, 216 results
- **Round Robin S1 2023 Group B**: 3 matchdays x 3 matches x 2 races = 18 races, 216 results
- **Total**: 121 races, 1,452 results

Helper methods: `seedLeagueSeason()`, `seedSwissSeason()`, `seedRoundRobinSeason()`, `seedRace()`.

Core race seeding sequence: Race -> RaceSettings -> RaceLineup (12 per race) -> RaceResult (12 per race) -> ScoringService.calculatePoints() -> flush+detach -> ScoringService.aggregateMatchScores().

### Task 3: Visual verification (CHECKPOINT - awaiting user)

Not yet executed. Checkpoint for human-verify of standings pages.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] JPA flush + detach for aggregateMatchScores**
- **Found during:** Task 2
- **Issue:** `aggregateMatchScores(race)` calls `race.getResults()` which returned empty in the same transaction even after `raceResultRepository.saveAll()`. The JPA first-level cache prevented reload.
- **Fix:** Added `raceResultRepository.flush()` + `entityManager.detach(race)` before `raceRepository.findById()` reload, and explicit `matchRepository.save()` after aggregation.
- **Files modified:** TestDataService.java
- **Commit:** 7772c88

**2. [Rule 1 - Bug] Test matchday filter for shared Spring context**
- **Found during:** Task 2 (full suite run)
- **Issue:** Other integration tests in the suite create additional matchdays for S4 2026, causing matchday count tests to fail (expected 5 but got 8).
- **Fix:** Added `md.getLabel().matches("Matchday \\d+")` filter to isolate seed-created matchdays from test-created ones. Also filtered results/match tests by seed matchday IDs.
- **Files modified:** TestDataServiceIntegrationTest.java
- **Commit:** 7772c88

## Known Stubs

None - all data is fully wired and functional.

## Verification

```
./mvnw verify: BUILD SUCCESS
Tests run: 867, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
```

## Self-Check: PASSED
