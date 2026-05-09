---
phase: 60-admin-ui
plan: "01"
subsystem: test
tags: [tdd, red-tests, wave-0, java, spring-boot, thymeleaf]
dependency_graph:
  requires: []
  provides: [wave-0-red-tests]
  affects: [60-02, 60-03, 60-04, 60-05, 60-06, 60-07]
tech_stack:
  added: []
  patterns: [TDD Red-Green-Refactor, BDD given-when-then, T-Phase60- test isolation]
key_files:
  created:
    - src/test/java/org/ctc/admin/controller/SeasonPhaseControllerTest.java
    - src/test/java/org/ctc/admin/controller/SeasonPhaseGroupControllerTest.java
    - src/test/java/org/ctc/admin/controller/integration/SeasonPhaseControllerIT.java
    - src/test/java/org/ctc/admin/controller/integration/SeasonPhaseGroupControllerIT.java
    - src/test/java/org/ctc/admin/dto/SeasonPhaseFormTest.java
    - src/test/java/org/ctc/admin/dto/PhaseTeamFormTest.java
  modified:
    - src/test/java/org/ctc/admin/controller/SeasonControllerTest.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java
    - src/test/java/org/ctc/admin/controller/StandingsControllerTest.java
    - src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java
    - src/test/java/org/ctc/admin/controller/PlayoffControllerTest.java
decisions:
  - "Used existsByPhaseSeasonId (existing method) instead of non-existent existsByPhaseSeasonIdAndTeamId for D-25 strict remove guard in unit test; implementation will use the appropriate check"
  - "W-11 phaseType immutability documented via givenExistingPhase_whenUpdateWithSameLayout_thenPhaseTypePersisted test in SeasonPhaseServiceTest"
  - "DriverSheetImportControllerTest new tests use real DriverSheetImportService with mocked GoogleSheetsService — no separate service mock needed"
requirements-completed: [UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07]
metrics:
  duration: "~90 minutes (resumed from prior session)"
  completed: "2026-04-30"
  tasks: 2
  files: 12
---

# Phase 60 Plan 01: Wave 0 TDD-RED Tests Summary

TDD Wave 0 for Phase 60 admin UI cutover — 12 test files created/extended with ~33 RED tests that pin expected behavior before any production code changes. All tests fail with compile errors (missing production classes) or assertion mismatches (missing model attributes/behavior).

## Tasks

### Task 1: Create new test scaffold files (UI-02, UI-03, UI-04)

Committed as `455379b`.

| File | Tests | RED Reason |
|------|-------|-----------|
| SeasonPhaseControllerTest.java | 5 | SeasonPhaseController does not exist → Spring context fails to wire |
| SeasonPhaseGroupControllerTest.java | 2 | SeasonPhaseGroupController does not exist |
| SeasonPhaseControllerIT.java | 2 | createGroup() not on SeasonPhaseService yet |
| SeasonPhaseGroupControllerIT.java | 3 | createGroup() + assignTeamToPhase() not on SeasonPhaseService yet |
| SeasonPhaseFormTest.java | 5 | SeasonPhaseForm DTO does not exist → compile error |
| PhaseTeamFormTest.java | 3 | PhaseTeamForm DTO does not exist → compile error |

**Total new: 20 RED tests**

### Task 2: Extend existing test classes with Phase 60 RED tests

Committed as `b4b5693`.

| File | Phase 60 tests added | Removed tests |
|------|---------------------|---------------|
| SeasonControllerTest.java | 2 (slim form save, scoring attrs absent) | 1 old 14-param test rewritten |
| SeasonManagementServiceTest.java | 6 (slim save, no-sync, strict remove, atomic PhaseTeam, bootstrap, existing-no-save) | 3 old D-25 auto-sync tests replaced |
| SeasonPhaseServiceTest.java | 8 (update layout guard, delete guard, delete happy-path, updateGroup, deleteGroup strict guard, assignTeamsToPhase diff, no-op, W-11 phaseType immutable) | 0 |
| StandingsControllerTest.java | 3 (legacy bridge, combinedView+showGroupColumn, showBuchholz) | 0 |
| DriverSheetImportControllerTest.java | 3 (raw tab name, showGroupColumn for GROUPS, null resolvedGroup passthrough) | 0 |
| PlayoffControllerTest.java | 1 (Add/Remove Season UI absent D-43) | 0 |

**Total new: 23 RED tests in Task 2**

**Grand total: ~33 new RED tests across 12 files**

## Requirement Coverage

| Requirement | Tests pinning behavior |
|-------------|----------------------|
| UI-01 (slim SeasonForm) | SeasonControllerTest (2), SeasonManagementServiceTest (4) |
| UI-02 (SeasonPhaseController CRUD) | SeasonPhaseControllerTest (5), SeasonPhaseControllerIT (2) |
| UI-03 (Phase form + add phase) | SeasonPhaseControllerTest (phaseType dup guard, form defaults), SeasonPhaseFormTest (5) |
| UI-04 (Group CRUD + roster diff) | SeasonPhaseGroupControllerTest (2), SeasonPhaseGroupControllerIT (3), PhaseTeamFormTest (3), SeasonPhaseServiceTest (assignTeamsToPhase) |
| UI-05 (Phase-scoped standings) | StandingsControllerTest (3) |
| UI-06 (Driver import group column) | DriverSheetImportControllerTest (3) |
| UI-07 (Playoff bracket UI cleanup) | PlayoffControllerTest (1) |

## What Plans 60-02..60-07 Must Implement to Turn Tests GREEN

- **60-02**: SeasonPhaseController, SeasonPhaseGroupController, SeasonPhaseForm, PhaseTeamForm, SeasonPhaseService.update/delete/updateGroup/deleteGroup/assignTeamsToPhase/createGroup
- **60-03**: SeasonManagementService.save 6-param signature (remove 14-param), remove D-25 auto-sync block, add D-26 atomic PhaseTeam insert on addTeamToSeason, add D-25 strict remove guard via existsByPhaseSeasonId
- **60-05**: StandingsController: add ?phase= param, combinedView/showGroupColumn/showBuchholz model attrs; DriverSheetImportController: add showGroupColumn model attr
- **60-07**: Playoff bracket template: remove Add/Remove Season UI buttons

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed non-existent mock method in SeasonManagementServiceTest**
- **Found during:** Task 2
- **Issue:** Plan specified `phaseTeamRepository.existsByPhaseSeasonIdAndTeamId(seasonId, teamId)` but `PhaseTeamRepository` only has `existsByPhaseSeasonId(UUID seasonId)`
- **Fix:** Changed test mock to use `existsByPhaseSeasonId(season.getId())` which matches the actual repository interface; the D-25 strict guard behavior is still correctly pinned
- **Files modified:** SeasonManagementServiceTest.java
- **Commit:** b4b5693

**2. [Rule 3 - Blocking] DriverSheetImportControllerTest plan template incompatible with actual service API**
- **Found during:** Task 2
- **Issue:** Plan specified `driverSheetImportService.previewSheet()` (returns List) and `DriverRow` record, but actual service has `preview()` (returns `DriverSheetImportPreview`) with `NewDriverRow` record
- **Fix:** New tests use real `DriverSheetImportService` via mocked `GoogleSheetsService` (matching existing test pattern); tests verify model attributes `preview` and `showGroupColumn` (the latter is RED as Plan 60-05 hasn't implemented it yet)
- **Files modified:** DriverSheetImportControllerTest.java
- **Commit:** b4b5693

**3. [Rule 3 - Blocking] PlayoffControllerTest `testHelper.createPlayoff()` does not exist**
- **Found during:** Task 2
- **Issue:** Plan referenced `testHelper.createPlayoff(season, name)` but TestHelper has no such method
- **Fix:** Used `playoffService.createPlayoff(season.getId(), name, size)` matching existing test patterns in the same file
- **Files modified:** PlayoffControllerTest.java
- **Commit:** b4b5693

## Known Stubs

None — this is a test-only plan, no production code modified.

## Threat Flags

None — all changes are test-only, run in H2 in-memory profile with `@Transactional` rollback. No production attack surface introduced.

## Self-Check: PASSED

Files created:
- FOUND: src/test/java/org/ctc/admin/controller/SeasonPhaseControllerTest.java
- FOUND: src/test/java/org/ctc/admin/controller/SeasonPhaseGroupControllerTest.java
- FOUND: src/test/java/org/ctc/admin/controller/integration/SeasonPhaseControllerIT.java
- FOUND: src/test/java/org/ctc/admin/controller/integration/SeasonPhaseGroupControllerIT.java
- FOUND: src/test/java/org/ctc/admin/dto/SeasonPhaseFormTest.java
- FOUND: src/test/java/org/ctc/admin/dto/PhaseTeamFormTest.java

Commits verified:
- 455379b: test(60-01): add Wave 0 scaffold RED tests for SeasonPhase/Group/DTO (Task 1)
- b4b5693: test(60-01): extend existing test classes with Phase 60 RED tests (Task 2)

Acceptance criteria verified:
- T-Phase60- prefix in all new test files: YES (4+ files)
- Old 14-param save tests removed (grep format.*totalRounds.*legs returns 0): YES
- All required method names present: YES (checked individually)
- showGroupColumn in DriverSheetImportControllerTest: YES (2 occurrences)
