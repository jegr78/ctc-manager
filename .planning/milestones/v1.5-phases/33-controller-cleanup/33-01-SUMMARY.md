---
phase: 33-controller-cleanup
plan: 01
subsystem: architecture
tags: [spring, service-layer, refactoring, tdd]

requires: []
provides:
  - SeasonManagementService.SeasonGroupOption record with getSeasonGroupOptions() method
  - MatchdayService.MatchdayDetailData extended with hasMatches, hasSchedule, scheduleMissingCount, hasResults
  - DriverService.getMergeFormDrivers(UUID excludeDriverId) method
  - PowerRankingsController, MatchdayController, DriverController contain only HTTP handling
affects:
  - 33-02 (remaining controller cleanup tasks)
  - any future feature touching PowerRankings, Matchday detail, or Driver merge

tech-stack:
  added: []
  patterns:
    - "Business logic extraction: inline controller stream/grouping logic moved to service methods"
    - "Record extension: MatchdayDetailData record expanded with computed graphic status fields"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/main/java/org/ctc/domain/service/MatchdayService.java
    - src/main/java/org/ctc/domain/service/DriverService.java
    - src/main/java/org/ctc/admin/controller/PowerRankingsController.java
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/main/java/org/ctc/admin/controller/DriverController.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/MatchdayServiceTest.java
    - src/test/java/org/ctc/domain/service/DriverServiceTest.java

key-decisions:
  - "Comparator chain: use .thenComparing(Comparator.comparingInt(...).reversed()) instead of chained .reversed() to get correct year-desc+number-desc sort order"
  - "SeasonGroupOption record moved from PowerRankingsController to SeasonManagementService (only call site)"

patterns-established:
  - "Controllers delegate all stream filtering, grouping, and sorting to service methods"
  - "MatchdayDetailData record carries computed status fields to avoid repeated computation in controller and template"

requirements-completed: [ARCH-03]

duration: 25min
completed: 2026-04-14
---

# Phase 33 Plan 01: Controller Cleanup Summary

**Season grouping/sorting, matchday graphic status computation, and driver merge filtering extracted from three controllers into their respective service methods using TDD**

## Performance

- **Duration:** 25 min
- **Started:** 2026-04-14T16:47:00Z
- **Completed:** 2026-04-14T16:53:21Z
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments

- `SeasonManagementService.getSeasonGroupOptions()` replaces 20+ lines of inline grouping/mapping/sorting in `PowerRankingsController.index()`
- `MatchdayDetailData` record extended with four graphic status fields — `MatchdayController.detail()` now reads from record instead of computing inline
- `DriverService.getMergeFormDrivers()` replaces inline stream filter+sort in `DriverController.mergeForm()`
- All three controller methods now contain only HTTP handling; 883 tests green, JaCoCo coverage maintained

## Task Commits

1. **Task 1: RED — Failing tests** - `6b6b25c` (test)
2. **Task 2: GREEN — Service implementation + controller updates** - `a5e2926` (feat)
3. **Task 3: Full test suite verification** - (no separate commit — verified clean in Task 2)

## Files Created/Modified

- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — Added `SeasonGroupOption` record + `getSeasonGroupOptions()`
- `src/main/java/org/ctc/domain/service/MatchdayService.java` — Extended `MatchdayDetailData` + updated `getMatchdayDetail()`
- `src/main/java/org/ctc/domain/service/DriverService.java` — Added `getMergeFormDrivers(UUID excludeDriverId)`
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java` — Removed inline grouping logic and `SeasonGroupOption` record
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — Replaced inline computation with record field access
- `src/main/java/org/ctc/admin/controller/DriverController.java` — Replaced inline stream with service call, removed unused imports
- `src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java` — Added `GetSeasonGroupOptions` nested test class (3 tests)
- `src/test/java/org/ctc/domain/service/MatchdayServiceTest.java` — Added `GetMatchdayDetailGraphicStatus` nested test class (4 tests)
- `src/test/java/org/ctc/domain/service/DriverServiceTest.java` — Added `GetMergeFormDrivers` nested test class (1 test)

## Decisions Made

- Used `Comparator.comparingInt(SeasonGroupOption::year).reversed().thenComparing(Comparator.comparingInt(SeasonGroupOption::number).reversed())` instead of the chained `.reversed()` pattern from the original controller code — the plan's original `.thenComparingInt(...).reversed()` reverses the entire comparator chain, producing ascending order. The corrected form wraps the second comparator explicitly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Comparator chain producing wrong sort order**
- **Found during:** Task 2 (GREEN phase test run)
- **Issue:** The plan's suggested sort pattern `.sorted(Comparator.comparingInt(SeasonGroupOption::year).reversed().thenComparingInt(SeasonGroupOption::number).reversed())` causes the second `.reversed()` to invert the entire chain, sorting ascending by year instead of descending.
- **Fix:** Changed to `.thenComparing(Comparator.comparingInt(SeasonGroupOption::number).reversed())` so each component is independently reversed.
- **Files modified:** `src/main/java/org/ctc/domain/service/SeasonManagementService.java`
- **Verification:** `givenSeasonsFromDifferentYears_whenGetSeasonGroupOptions_thenSortedByYearDescThenNumberDesc` passes
- **Committed in:** `a5e2926` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug in suggested comparator pattern)
**Impact on plan:** Necessary correctness fix. No scope creep.

## Issues Encountered

None beyond the comparator bug documented above.

## Next Phase Readiness

- Three controller violations from the code review (D-01, D-02, D-03) are resolved
- ARCH-03 requirement satisfied for these three controllers
- Plan 33-02 can proceed with remaining cleanup tasks

---
*Phase: 33-controller-cleanup*
*Completed: 2026-04-14*
