---
phase: 31-null-safety-and-transaction-fix
plan: "02"
subsystem: domain-service
tags: [null-safety, bye-race, scoring, season-filter, tdd]
dependency_graph:
  requires: []
  provides: [null-safe-race-form-data, bye-guard-scoring, season-filtered-driver-team]
  affects: [RaceFormDataService, ScoringService]
tech_stack:
  added: []
  patterns: [null-guard, early-return, season-scoped-fallback]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/RaceFormDataService.java
    - src/main/java/org/ctc/domain/service/ScoringService.java
    - src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java
    - src/test/java/org/ctc/domain/service/ScoringServiceTest.java
decisions:
  - "D-05/D-06: Null-safe team access in RaceFormDataService — return empty sets / null IDs for bye races instead of NPE"
  - "D-07: No logging for bye race skip in aggregateMatchScores — bye matches are normal operation"
  - "D-11: Season-filtered SeasonDriver fallback in isDriverInTeam() — prevents cross-season misattribution"
metrics:
  duration: ~15min
  completed_date: "2026-04-14"
  tasks_completed: 2
  files_changed: 4
  tests_added: 6
  tests_total: 874
---

# Phase 31 Plan 02: Null Safety + Season-Filtered Fallback Summary

**One-liner:** Null-safe bye race handling in RaceFormDataService (empty sets, null IDs) plus isBye guard and season-scoped SeasonDriver fallback in ScoringService.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Null safety for bye races in RaceFormDataService + aggregateMatchScores isBye guard | 7883c9e | RaceFormDataService.java, ScoringService.java, RaceFormDataServiceTest.java, ScoringServiceTest.java |
| 2 | Season-filtered driver-team fallback in ScoringService.isDriverInTeam() | 7883c9e | ScoringService.java, ScoringServiceTest.java |

## What Was Built

### Task 1: Bye Race Null Safety

Fixed three NPE paths in `RaceFormDataService` and one in `ScoringService`:

1. **`toRaceData()`** — `race.getHomeTeam().getId()` and `race.getAwayTeam().getId()` now use null-conditional expressions, returning `null` for bye races instead of throwing NPE.
2. **`getRaceFormData()`** — `getUsedCarIds()` and `getUsedTrackIds()` now return `Set.of()` when `homeTeam` is null (bye race with no teams).
3. **`getResultsFormData()`** — `populateDrivers()` calls are now guarded: only called when `homeTeam != null` / `awayTeam != null`.
4. **`aggregateMatchScores()`** — Added `if (race.isBye()) return;` early guard after the empty-results check. Bye matches do not participate in match scoring.

### Task 2: Season-Filtered Driver-Team Fallback

Modified `ScoringService.isDriverInTeam()` fallback path:

- Added `RaceRepository` as new dependency to `ScoringService`.
- Added `raceRepository.findById(raceId)` call to resolve the race's current season.
- Filters `SeasonDriver` stream by `sd.getSeason().getId().equals(seasonId)` before checking team.
- Returns `false` when race not found or matchday is null (safe default).
- Prevents a driver registered for TeamA in Season 1 from being counted as TeamA in Season 2 scoring.

## Tests Added

| Test Class | Test Method | Purpose |
|-----------|-------------|---------|
| RaceFormDataServiceTest | `givenByeRaceWithNullHomeTeam_whenGetRaceFormData_thenReturnsFormDataWithEmptyUsedSets` | Null homeTeam -> empty used sets, null team IDs |
| RaceFormDataServiceTest | `givenByeRace_whenGetResultsFormData_thenReturnsResultsWithoutNPE` | Null awayTeam -> only homeTeam drivers populated |
| ScoringServiceTest | `givenByeRace_whenAggregateMatchScores_thenReturnsWithoutScoring` | Bye race -> early return, scores unchanged |
| ScoringServiceTest | `givenNoRaceLineupAndDriverInTeamForDifferentSeason_whenIsDriverInTeam_thenReturnsFalse` | Cross-season SeasonDriver not matched |
| ScoringServiceTest | `givenNoRaceLineupAndDriverInTeamForCurrentSeason_whenIsDriverInTeam_thenReturnsTrue` | Same-season SeasonDriver correctly matched |
| ScoringServiceTest | `givenNoRaceLineupAndRaceNotFound_whenIsDriverInTeam_thenReturnsFalse` | Race not found -> safe false return |

Updated `givenNoRaceLineup_whenAggregateMatchScores_thenFallsBackToSeasonDriver` to set `matchday.setSeason(season)` and add `raceRepository.findById()` stub.

## Deviations from Plan

- Combined Task 1 and Task 2 into a single commit (inline execution after worktree agent reformatted entire codebase)
- `givenByeRace_whenAggregateMatchScores_thenReturnsWithoutScoring` asserts `null` scores (not `0`) since `Match.homeScore` is `Integer` type

## Self-Check: PASSED

Files exist: all 4 modified files verified.
Commit: 7883c9e — FOUND
Test results: 874 tests, 0 failures, BUILD SUCCESS
