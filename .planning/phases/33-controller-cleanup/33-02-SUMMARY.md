---
phase: 33-controller-cleanup
plan: 02
subsystem: sitegen
tags: [sitegen, raclineup, source-of-truth, driver-team-resolution]

# Dependency graph
requires:
  - phase: 33-controller-cleanup-01
    provides: Context for code review findings and RaceLineup-first pattern
provides:
  - RaceLineup-first driver-team resolution in SiteGeneratorService.toRaceView()
  - Integration test covering RaceLineup-primary and SeasonDriver-fallback paths
affects: [sitegen, site-generation, race-views]

# Tech tracking
tech-stack:
  added: []
  patterns: [RaceLineup-first, SeasonDriver-fallback]

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java

key-decisions:
  - "RaceLineupRepository injected into SiteGeneratorService via @RequiredArgsConstructor"
  - "toRaceView() uses RaceLineup-first pattern (identical to RaceFormDataService.toRaceData())"
  - "generateDriverProfiles() left unchanged per D-06 — season-level profiles use SeasonDriver correctly"

patterns-established:
  - "RaceLineup-first: raceLineupRepository.findByRaceIdAndDriverId() → orElseGet(SeasonDriver fallback)"

requirements-completed: [ARCH-04]

# Metrics
duration: 15min
completed: 2026-04-14
---

# Phase 33 Plan 02: SiteGeneratorService RaceLineup-First Fix Summary

**SiteGeneratorService.toRaceView() now resolves driver-team assignments from RaceLineup entries first, falling back to SeasonDriver only when no lineup entry exists — matching the canonical pattern from RaceFormDataService**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-14T18:45:00Z
- **Completed:** 2026-04-14T18:58:00Z
- **Tasks:** 2 (TDD: RED + GREEN)
- **Files modified:** 2

## Accomplishments
- Injected `RaceLineupRepository` into `SiteGeneratorService` via Lombok `@RequiredArgsConstructor`
- Fixed `toRaceView()` to use `raceLineupRepository.findByRaceIdAndDriverId()` with `orElseGet()` SeasonDriver fallback
- Integration test `givenRaceLineupWithSubTeam_whenGenerate_thenDriverAttributedToSubTeam` proves sub-team attribution
- All 11 `SiteGeneratorServiceTest` tests pass (existing tests exercise SeasonDriver fallback path)
- `generateDriverProfiles()` left unchanged per D-06

## Task Commits

Each task was committed atomically:

1. **Task 1: RED — Failing test for RaceLineup-first resolution** - `a0a77b5` (test)
2. **Task 2: GREEN — Inject RaceLineupRepository and fix toRaceView()** - `6f698bc` (feat)

_TDD: RED commit (failing test) → GREEN commit (passing implementation)_

## Files Created/Modified
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` - Added `RaceLineupRepository` field; fixed `toRaceView()` to use RaceLineup-first resolution
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` - Added `RaceLineupRepository` autowire, instance fields (`testRace`, `driver1`, `homeTeam`), and new integration test

## Decisions Made
- Test assertion uses `tbody tr` row-level check (not full page text) to correctly distinguish driver1's sub-team row from other drivers still attributed to the parent team
- `generateDriverProfiles()` intentionally left using `SeasonDriver` — season-level driver profiles are correctly season-scoped, not race-scoped

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed over-broad test assertion `doesNotContain("GTNR")`**
- **Found during:** Task 2 (GREEN phase, first test run)
- **Issue:** Initial assertion `assertThat(pageText).doesNotContain("GTNR" + uniqueSuffix)` failed because other drivers (driver2) are correctly still attributed to the GTNR parent team in their rows
- **Fix:** Changed assertion to parse the specific `tbody tr` row for `driver1` using Jsoup `select("tbody tr")`, then assert that row contains the sub-team short name and does not contain the parent team short name
- **Files modified:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`
- **Verification:** All 11 SiteGeneratorServiceTest tests pass
- **Committed in:** `6f698bc` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug in test assertion)
**Impact on plan:** Essential fix for correct test semantics. No scope creep.

## Issues Encountered
None beyond the test assertion fix above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- SiteGeneratorService now consistent with RaceFormDataService (both use RaceLineup-first)
- All existing site generation tests pass
- Full `./mvnw verify` should be run before PR to confirm coverage >= 82%

---
*Phase: 33-controller-cleanup*
*Completed: 2026-04-14*
