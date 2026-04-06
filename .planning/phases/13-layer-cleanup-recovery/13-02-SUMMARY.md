---
phase: 13-layer-cleanup-recovery
plan: 02
subsystem: domain-service-dto-decoupling
tags: [arch-01, refactoring, layer-cleanup, dto-decoupling]
dependency_graph:
  requires: [13-01]
  provides: [simple-service-dto-decoupling]
  affects: [13-03]
tech_stack:
  added: []
  patterns: [primitive-parameter-delegation]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/CarService.java
    - src/main/java/org/ctc/domain/service/TrackService.java
    - src/main/java/org/ctc/domain/service/DriverService.java
    - src/main/java/org/ctc/domain/service/RaceScoringService.java
    - src/main/java/org/ctc/domain/service/MatchScoringService.java
    - src/main/java/org/ctc/admin/controller/CarController.java
    - src/main/java/org/ctc/admin/controller/TrackController.java
    - src/main/java/org/ctc/admin/controller/DriverController.java
    - src/main/java/org/ctc/admin/controller/RaceScoringController.java
    - src/main/java/org/ctc/admin/controller/MatchScoringController.java
    - src/test/java/org/ctc/domain/service/CarServiceTest.java
    - src/test/java/org/ctc/domain/service/TrackServiceTest.java
    - src/test/java/org/ctc/domain/service/DriverServiceTest.java
    - src/test/java/org/ctc/domain/service/RaceScoringServiceTest.java
    - src/test/java/org/ctc/domain/service/MatchScoringServiceTest.java
decisions:
  - "DriverService.save() includes aliases parameter (List<String>) to maintain existing alias sync behavior"
  - "RaceScoringService.save() passes qualiPoints as String (nullable) matching existing form field type"
metrics:
  duration: 5min
  completed: "2026-04-06T18:10:48Z"
  tasks: 2
  files_modified: 15
  tests_added: 0
  test_count: 794
---

# Phase 13 Plan 02: DTO Decoupling Simple Services Summary

5 simple domain services decoupled from admin DTOs via primitive parameter delegation pattern, controllers extract form fields

## Task Results

| Task | Name | Commit(s) | Files |
|------|------|-----------|-------|
| 1 | Decouple CarService, TrackService, DriverService from admin DTOs (TDD) | 50f1f87 (red), 44a5e25 (green) | CarService, TrackService, DriverService + 3 controllers + 3 test files |
| 2 | Decouple RaceScoringService and MatchScoringService from admin DTOs (TDD) | 4d6a0ef (red), 5406747 (green) | RaceScoringService, MatchScoringService + 2 controllers + 2 test files |

## What Changed

### Task 1: Car/Track/Driver Service Decoupling
- **CarService**: `save(CarForm)` changed to `save(UUID id, String manufacturer, String name)` -- removed `CarForm` import
- **TrackService**: `save(TrackForm)` changed to `save(UUID id, String name, String country)` -- removed `TrackForm` import
- **DriverService**: `save(DriverForm)` changed to `save(UUID id, String psnId, String nickname, boolean active, List<String> aliases)` -- removed `DriverForm` import
- **Controllers**: CarController, TrackController, DriverController now extract form fields and pass primitives to service calls

### Task 2: RaceScoring/MatchScoring Service Decoupling
- **RaceScoringService**: `save(RaceScoringForm)` changed to `save(UUID id, String name, String racePoints, String qualiPoints, int fastestLapPoints)` -- removed `RaceScoringForm` import
- **MatchScoringService**: `save(MatchScoringForm)` changed to `save(UUID id, String name, int pointsWin, int pointsDraw, int pointsLoss)` -- removed `MatchScoringForm` import
- **Controllers**: RaceScoringController, MatchScoringController now extract form fields and pass primitives

### Test Coverage
- All existing tests updated to use primitive-based service calls
- Test count remains at 794 (no new tests needed, existing tests refactored)
- JaCoCo coverage checks pass (above 82% minimum)

## Deviations from Plan

None - plan executed exactly as written.

## Verification

- `./mvnw verify`: 794 tests pass, BUILD SUCCESS
- JaCoCo coverage: All checks met
- Zero `import org.ctc.admin.dto` in all 5 target services (verified via grep, exit code 1 = no matches)
- All 5 controllers extract form fields with `form.getX()` pattern

## Self-Check: PASSED

All 15 modified files exist. All 4 commits verified in git log.
