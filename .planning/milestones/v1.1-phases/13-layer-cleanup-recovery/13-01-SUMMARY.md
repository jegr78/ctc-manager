---
phase: 13-layer-cleanup-recovery
plan: 01
subsystem: controller-service-delegation
tags: [arch-02, feat-02, refactoring, layer-cleanup]
dependency_graph:
  requires: []
  provides: [service-finder-methods, controller-service-delegation]
  affects: [13-02, 13-03]
tech_stack:
  added: []
  patterns: [service-finder-delegation, buchholz-service-integration]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/main/java/org/ctc/domain/service/TeamManagementService.java
    - src/main/java/org/ctc/domain/service/PlayoffService.java
    - src/main/java/org/ctc/admin/controller/StandingsController.java
    - src/main/java/org/ctc/admin/controller/PowerRankingsController.java
    - src/main/java/org/ctc/admin/controller/TeamCardController.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/main/java/org/ctc/dataimport/CsvImportController.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/TeamManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/PlayoffServiceTest.java
decisions:
  - "findActiveSeason() uses stream filter over findAll() to tolerate multiple active seasons"
  - "TeamManagementService gets SeasonTeamRepository injection for findSeasonTeamById/findSeasonTeamsBySeasonId"
  - "StandingsController delegates Swiss Buchholz entirely to standingsService.calculateStandingsWithBuchholz()"
metrics:
  duration: 5min
  completed: "2026-04-06T18:03:00Z"
  tasks: 2
  files_modified: 11
  tests_added: 9
  test_count: 794
---

# Phase 13 Plan 01: Service Finder Methods + Controller Cleanup Summary

Service finder methods added to 3 services, repository injections removed from 5 controllers, Swiss Buchholz logic delegated to StandingsService

## Task Results

| Task | Name | Commit(s) | Files |
|------|------|-----------|-------|
| 1 | Add service finder methods with tests (TDD) | a70b9f7 (red), c4e68d7 (green) | SeasonManagementService, TeamManagementService, PlayoffService + 3 test files |
| 2 | Remove repository injections from 5 controllers | 9fd7ef4 | StandingsController, PowerRankingsController, TeamCardController, PlayoffController, CsvImportController |

## What Changed

### Service Finder Methods (Task 1)
- **SeasonManagementService**: Added `findActiveSeason()` (stream filter for multiple-active tolerance) and `findByIdOptional()` (Optional return)
- **TeamManagementService**: Injected `SeasonTeamRepository`, added `findSeasonTeamById()` (throws EntityNotFoundException) and `findSeasonTeamsBySeasonId()`
- **PlayoffService**: Added `findRoundById()` (throws EntityNotFoundException)

### Controller Cleanup (Task 2)
- **StandingsController**: Removed `SeasonRepository` + `SwissPairingService` fields; replaced with `SeasonManagementService`; Swiss format now uses `standingsService.calculateStandingsWithBuchholz()` instead of inline 10-line Buchholz/sort block
- **PowerRankingsController**: Removed `SeasonRepository`; replaced with `SeasonManagementService.findAll()`
- **TeamCardController**: Removed `SeasonRepository` + `SeasonTeamRepository`; replaced with `SeasonManagementService` + `TeamManagementService`
- **PlayoffController**: Removed `PlayoffRoundRepository`; replaced 3 `playoffRoundRepository.findById().orElseThrow()` calls with `playoffService.findRoundById()`
- **CsvImportController**: Removed `SeasonRepository`; replaced 2 `seasonRepository.findById().ifPresent()` calls with `seasonManagementService.findByIdOptional().ifPresent()`

### Test Coverage
- 9 new test methods added across 3 test files
- Test count rose from 785 to 794
- JaCoCo coverage checks pass (above 82% minimum)

## Deviations from Plan

None - plan executed exactly as written.

## Verification

- `./mvnw verify`: 794 tests pass, BUILD SUCCESS
- JaCoCo coverage: All checks met
- Zero `Repository` references in all 5 target controllers (verified via grep)
- `calculateStandingsWithBuchholz` present in StandingsController

## Self-Check: PASSED

All 11 modified files exist. All 3 commits verified in git log.
