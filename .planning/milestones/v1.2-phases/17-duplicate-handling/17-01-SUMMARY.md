---
phase: 17-duplicate-handling
plan: 01
subsystem: domain-service
tags: [driver-merge, duplicate-handling, unique-constraint, tdd, mockito]

# Dependency graph
requires:
  - phase: 16-merge-service-core
    provides: DriverMergeService with FK reassignment, MergeResult record, audit logging
provides:
  - Proactive duplicate detection for SeasonDriver, RaceLineup, RaceResult in merge()
  - Extended MergeResult with seasonDriversDropped, raceLineupsDropped, raceResultsDropped fields
  - findByRaceIdAndDriverId() query method on RaceResultRepository
  - DuplicateHandlingTests nested class with 7 test methods covering all conflict scenarios
affects: [18-merge-ui, phase-18]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Proactive conflict detection: check before mutate, delete source on conflict, preserve target unchanged"
    - "Lenient Mockito stubs for duplicate-check calls that are absent during RED phase"
    - "TDD RED/GREEN with UnnecessaryStubbingException awareness for pre-implementation stubs"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/DriverMergeService.java
    - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
    - src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java

key-decisions:
  - "Log race ID (UUID) not race name in duplicate drop messages — Race entity has no name field"
  - "Use lenient() stubs for no-conflict tests to avoid UnnecessaryStubbingException during RED phase"
  - "Update existing createSeasonDriver/createRaceLineup/createRaceResult helpers to set non-null season/race so duplicate-check code can call getId()"

patterns-established:
  - "Duplicate-safe FK reassignment: findByXxxIdAndDriverId() → if present delete, if absent reassign+save"

requirements-completed: [MERGE-11, MERGE-12, MERGE-13]

# Metrics
duration: 12min
completed: 2026-04-07
---

# Phase 17 Plan 01: Duplicate-Handling Summary

**Proactive duplicate detection in DriverMergeService for all 3 FK tables — source entries deleted instead of reassigned when target driver already exists in the same season/race, preventing unique-constraint violations**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-07T11:57:00Z
- **Completed:** 2026-04-07T12:09:00Z
- **Tasks:** 2 (TDD RED + GREEN)
- **Files modified:** 3

## Accomplishments

- Added `findByRaceIdAndDriverId()` to `RaceResultRepository` (plan artifact D-02)
- Extended `MergeResult` record with 3 new dropped-count fields (7 fields total)
- Implemented proactive conflict detection in all 3 FK reassignment loops in `merge()`
- Dropped entries logged at `log.info()` level with season name / race UUID
- 7 new `DuplicateHandlingTests` covering conflict, no-conflict, and mixed scenarios
- Full build: 839 tests pass, JaCoCo coverage checks met (>= 82%)

## Task Commits

Each task was committed atomically:

1. **Task 1: RED — Failing tests for duplicate handling + repository method** - `49d9f16` (test)
2. **Task 2: GREEN — Implement duplicate detection in merge() method** - `3f34150` (feat)

_TDD plan: test commit → feat commit_

## Files Created/Modified

- `src/main/java/org/ctc/domain/service/DriverMergeService.java` — Extended MergeResult record (7 fields), replaced 3 simple FK loops with duplicate-aware loops, updated audit log
- `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` — Added `Optional<RaceResult> findByRaceIdAndDriverId(UUID raceId, UUID driverId)`
- `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java` — Added DuplicateHandlingTests (7 methods), lenient stubs in setupStandardMerge, updated existing helper methods with non-null season/race

## Decisions Made

- **Race has no name field:** Plan specified `rl.getRace().getName()` in log messages, but `Race` entity has no `name` field. Used `rl.getRace().getId()` instead for RaceLineup and RaceResult drop logs. Season does have `getName()` so that log remains as planned.
- **Lenient stubs for no-conflict tests:** The 3 "no-conflict" tests use `lenient().when()` for the `findByXxxIdAndDriverId()` stubs. During the RED phase these calls don't happen (merge() doesn't detect duplicates yet), so strict `when()` would trigger `UnnecessaryStubbingException`. Post-GREEN these stubs become active.
- **Update existing helpers with non-null season/race:** The existing `createSeasonDriver()`, `createRaceLineup()`, `createRaceResult()` helpers created entities without a season/race. After adding `sd.getSeason().getId()` to the merge loop, these caused NPEs in FkReassignmentTests. Updated helpers to set a random season/race UUID so the duplicate-check can call `getId()` safely.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Race entity has no getName() method**
- **Found during:** Task 2 (GREEN — implement duplicate detection)
- **Issue:** Plan action specified `rl.getRace().getName()` in log.info calls, but `Race` has no `name` field (Race is identified by its matchday relationship, not a direct name)
- **Fix:** Used `rl.getRace().getId()` for RaceLineup and RaceResult drop log messages
- **Files modified:** `src/main/java/org/ctc/domain/service/DriverMergeService.java`
- **Verification:** Build succeeds, no compilation error
- **Committed in:** `3f34150` (Task 2 feat commit)

**2. [Rule 1 - Bug] Existing test helpers caused NPE in FkReassignmentTests after duplicate-check added**
- **Found during:** Task 2 (GREEN — first test run attempt)
- **Issue:** `createSeasonDriver(driver)`, `createRaceLineup(driver)`, `createRaceResult(driver)` did not set `season`/`race`. The new `merge()` code calls `sd.getSeason().getId()` on every entry, causing NPE for entities without a season/race.
- **Fix:** Updated the 3 existing helper methods to set a fresh `Season`/`Race` with a random UUID
- **Files modified:** `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java`
- **Verification:** All 19 tests pass after fix
- **Committed in:** `3f34150` (Task 2 feat commit)

**3. [Rule 1 - Bug] UnnecessaryStubbingException in no-conflict tests during RED phase**
- **Found during:** Task 1 (first RED test run)
- **Issue:** `when(repo.findByXxx(...)).thenReturn(Optional.empty())` in no-conflict tests was unused during RED (merge() didn't call those methods yet), triggering strict Mockito failure
- **Fix:** Changed those 3 stubs to `lenient().when(...)` so they don't trigger the strict check
- **Files modified:** `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java`
- **Verification:** RED run shows correct 4 failures (conflict tests + mixed), no errors
- **Committed in:** `49d9f16` (Task 1 test commit, after amendment)

---

**Total deviations:** 3 auto-fixed (all Rule 1 — bugs in plan's code assumptions)
**Impact on plan:** All fixes necessary for correctness. No scope creep. Plan objective fully achieved.

## Issues Encountered

- Initial RED run had `UnnecessaryStubbingException` in 3 no-conflict tests — resolved by using `lenient()` stubs
- Initial GREEN run had NPE in 4 existing tests (FkReassignmentTests + ResultTests) — resolved by updating helper methods

## Known Stubs

None — all FK reassignment data is fully wired and test-verified.

## Threat Flags

No new security-relevant surface introduced. The duplicate detection is internal service logic with no new endpoints, auth paths, or schema changes. Threat model from plan (T-17-01 through T-17-04) remains accurate.

## Next Phase Readiness

- `DriverMergeService.merge()` is now safe for drivers who raced in the same seasons/races
- Phase 18 (Merge UI) can wire the merge controller to `DriverMergeService` without risk of unique-constraint violations
- No blockers

---
*Phase: 17-duplicate-handling*
*Completed: 2026-04-07*
