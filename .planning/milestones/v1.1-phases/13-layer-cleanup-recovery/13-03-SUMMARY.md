---
phase: 13-layer-cleanup-recovery
plan: 03
subsystem: domain-service-dto-decoupling
tags: [arch-01, refactoring, layer-cleanup, dto-decoupling, inner-records]
dependency_graph:
  requires: [13-01, 13-02]
  provides: [complex-service-dto-decoupling, arch-01-complete]
  affects: []
tech_stack:
  added: []
  patterns: [primitive-parameter-delegation, inner-record-api-contract]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/main/java/org/ctc/domain/service/TeamManagementService.java
    - src/main/java/org/ctc/domain/service/PlayoffSeedingService.java
    - src/main/java/org/ctc/domain/service/MatchdayService.java
    - src/main/java/org/ctc/admin/controller/SeasonController.java
    - src/main/java/org/ctc/admin/controller/TeamController.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/TeamManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/PlayoffSeedingServiceTest.java
    - src/test/java/org/ctc/domain/service/PlayoffServiceTest.java
    - src/test/java/org/ctc/domain/service/MatchdayServiceTest.java
decisions:
  - "SeasonManagementService.save() takes 14 primitive parameters matching SeasonForm fields"
  - "TeamManagementService.SeasonDriverGroup inner record mirrors SeasonDriverGroupDto with totalDriverCount()"
  - "PlayoffSeedingService.SeedEntry inner record mirrors SeedForm.SeedEntry fields"
  - "MatchdayService.MatchdayData inner record replaces MatchdayDto with identical field names for JSON compatibility"
metrics:
  duration: 7min
  completed: "2026-04-06T18:20:00Z"
  tasks: 2
  files_modified: 13
  tests_added: 1
  test_count: 795
---

# Phase 13 Plan 03: Complex Service DTO Decoupling Summary

4 complex domain services decoupled from admin DTOs using inner records and primitive parameters, completing ARCH-01 across all 9 in-scope services

## Task Results

| Task | Name | Commit(s) | Files |
|------|------|-----------|-------|
| 1 | Decouple SeasonManagementService, TeamManagementService, PlayoffSeedingService (TDD) | 315889a (red), 2063212 (green) | 3 services + 3 controllers + 4 test files |
| 2 | Decouple MatchdayService and complete ARCH-01 verification (TDD) | 99364c7 (red), 46030d1 (green) | 1 service + 1 controller + 1 test file |

## What Changed

### Task 1: Season/Team/PlayoffSeeding Service Decoupling
- **SeasonManagementService**: `save(SeasonForm, UUID, UUID)` changed to `save(UUID id, String name, int year, ...)` with 14 primitive parameters -- removed `SeasonForm` import
- **TeamManagementService**: `save(TeamForm)` changed to `save(UUID id, String name, String shortName, ...)` with 6 primitives -- removed `TeamForm` and `SeasonDriverGroupDto` imports. New inner `SeasonDriverGroup` record replaces `SeasonDriverGroupDto` in `TeamDetailData`
- **PlayoffSeedingService**: `saveSeed(UUID, SeedForm)` changed to `saveSeed(UUID, List<SeedEntry>)` -- removed `SeedForm` import. New inner `SeedEntry` record mirrors SeedForm.SeedEntry fields
- **Controllers**: SeasonController, TeamController, PlayoffController extract form fields and map to service API. PlayoffController maps `SeedForm` entries to `List<PlayoffSeedingService.SeedEntry>`
- **PlayoffServiceTest** (integration test): Updated to use `List<SeedEntry>` instead of `SeedForm`

### Task 2: MatchdayService Decoupling + Full Verification
- **MatchdayService**: New inner `MatchdayData(UUID id, String label, int sortIndex)` record replaces `MatchdayDto`. Both `getMatchdaysBySeason()` and `createInline()` return `MatchdayData`
- **MatchdayController**: JSON API endpoints updated to use `MatchdayService.MatchdayData`. Field names remain identical (`id`, `label`, `sortIndex`) -- JavaScript callers unaffected
- **Full verification**: `./mvnw verify` passes with 795 tests, JaCoCo coverage checks met

### ARCH-01 Complete
All 9 in-scope domain services verified with zero `import org.ctc.admin.dto` statements:
- CarService, TrackService, DriverService (Plan 02)
- RaceScoringService, MatchScoringService (Plan 02)
- SeasonManagementService, TeamManagementService, PlayoffSeedingService, MatchdayService (Plan 03)

Note: RaceService and RaceGraphicService are explicitly OUT OF SCOPE (pre-existing concerns not in Phase 7).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed PlayoffServiceTest compilation error**
- **Found during:** Task 1
- **Issue:** PlayoffServiceTest (integration test) also called `saveSeed(UUID, SeedForm)` -- not listed in plan's file list
- **Fix:** Updated PlayoffServiceTest SaveSeed tests to use `List<PlayoffSeedingService.SeedEntry>` instead of `SeedForm`
- **Files modified:** src/test/java/org/ctc/domain/service/PlayoffServiceTest.java
- **Commit:** 2063212

## Verification

- `./mvnw verify`: 795 tests pass, BUILD SUCCESS
- JaCoCo coverage: All checks met (above 82% minimum)
- Zero `import org.ctc.admin.dto` across all 9 in-scope domain services (verified via grep)
- `PlayoffSeedingService.SeedEntry` referenced in PlayoffController
- `TeamManagementService.SeasonDriverGroup` used in TeamDetailData record
- `MatchdayService.MatchdayData` used in MatchdayController JSON API

## Self-Check: PASSED

All 8 modified source files exist. All 4 commits verified in git log. All 3 inner records confirmed. PlayoffSeedingService.SeedEntry referenced in PlayoffController.
