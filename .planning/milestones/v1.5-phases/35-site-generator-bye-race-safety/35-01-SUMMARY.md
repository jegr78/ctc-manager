---
phase: 35-site-generator-bye-race-safety
plan: 01
subsystem: sitegen
tags: [null-safety, bye-race, tdd, site-generator]
dependency_graph:
  requires: []
  provides: [null-safe-toRaceView]
  affects: [site-generation]
tech_stack:
  added: []
  patterns: [null-guard-with-local-variable]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "Standard bye matches (homeTeam set, awayTeam null) do not NPE in current code, but defensive null guard added for robustness against edge cases (e.g. race without match)"
metrics:
  duration: 4m
  completed: "2026-04-14"
  tasks: 2/2
  test_count: 12
  coverage: "82%+"
---

# Phase 35 Plan 01: SiteGeneratorService Bye Race Null Safety Summary

Null-safe toRaceView() with homeTeam guard replacing 4 unguarded race.getHomeTeam() calls and removing unused homeTeamId variable.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | RED - Add failing bye-race integration test | c17a801 | SiteGeneratorServiceTest.java |
| 2 | GREEN - Add null guard for homeTeam in toRaceView() | db0df91 | SiteGeneratorService.java |

## Changes Made

### Task 1: Bye-race integration test
- Added `givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE` test method
- Creates bye match (homeTeam set, awayTeam null, bye=true) with a Race on separate matchday
- Verifies `siteGeneratorService.generate()` completes without errors and generates pages
- Test passed GREEN immediately (standard bye match has non-null homeTeam via Match delegation)

### Task 2: Null guard for homeTeam in toRaceView()
- Replaced `var homeTeamId = race.getHomeTeam().getId()` (unused variable + NPE risk) with `var homeTeam = race.getHomeTeam()` + null-safe `homeShortName`
- Replaced 3 remaining `race.getHomeTeam().getShortName()` calls (lines 291, 294, 300) with pre-computed `homeShortName`
- Bye races now produce `"Bye"` as homeShortName instead of potential NPE
- All 12 SiteGeneratorServiceTest tests pass, full `./mvnw verify` passes with 82%+ coverage

## Deviations from Plan

None - plan executed exactly as written. The plan anticipated the test might pass GREEN immediately for standard bye matches and instructed to proceed directly to Task 2 for defensive cleanup.

## Verification Results

1. `./mvnw test -Dtest=SiteGeneratorServiceTest` - 12 tests pass (11 existing + 1 new)
2. `./mvnw verify` - full suite passes with 82%+ coverage
3. `grep "homeTeam != null"` in SiteGeneratorService.java - null guard present at line 275
4. `grep "race.getHomeTeam().getShortName()"` - returns 0 matches (all unsafe calls removed)
5. `grep "race.getHomeTeam().getId()"` - returns 0 matches (unused variable removed)

## Self-Check: PASSED

All files exist, all commits verified.
