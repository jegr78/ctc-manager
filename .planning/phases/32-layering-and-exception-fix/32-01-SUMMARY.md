---
phase: 32-layering-and-exception-fix
plan: 01
subsystem: api
tags: [spring, layering, refactoring, architecture]

requires: []
provides:
  - RaceGraphicService relocated from domain.service to admin.service
  - RaceService decoupled from TeamCardService (ARCH-01 violation resolved)
  - Zero admin imports in domain service layer
affects: [RaceController, RaceService, RaceGraphicService, TeamCardService]

tech-stack:
  added: []
  patterns:
    - "Card existence checks belong in the controller that coordinates the view data"
    - "Admin services must not be referenced from domain services"
    - "Controller helper lookups (findRaceById, findSeasonTeam) delegate to service layer"

key-files:
  created:
    - src/main/java/org/ctc/admin/service/RaceGraphicService.java
    - src/test/java/org/ctc/admin/service/RaceGraphicServiceTest.java
  modified:
    - src/main/java/org/ctc/domain/service/RaceService.java
    - src/test/java/org/ctc/domain/service/RaceServiceTest.java
    - src/main/java/org/ctc/admin/controller/RaceController.java

key-decisions:
  - "RaceGraphicService moved to admin.service — it only delegates to other admin graphic services, so it belongs there"
  - "getRaceDetailData signature changed to accept hasHomeCard/hasAwayCard as boolean parameters — controller computes card flags before calling the method"
  - "findRaceById and findSeasonTeam added to RaceService as thin delegation methods — controller accesses domain data without bypassing the service layer"

patterns-established:
  - "Pattern: Controller computes admin-level state (card existence) before calling domain service for detail data"

requirements-completed: [ARCH-01]

duration: 15min
completed: 2026-04-13
---

# Phase 32 Plan 01: Layering Fix Summary

**RaceGraphicService relocated from domain.service to admin.service and TeamCardService decoupled from RaceService — zero admin imports now remain in the domain layer**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-13
- **Completed:** 2026-04-13
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Moved RaceGraphicService (and its test) from `org.ctc.domain.service` to `org.ctc.admin.service` — the service only delegates to other admin graphic services so the admin package is its correct home
- Removed `TeamCardService` import and field from `RaceService` — domain services no longer reference admin services
- Changed `getRaceDetailData` signature to `(UUID raceId, boolean hasHomeCard, boolean hasAwayCard)` — card flags now passed in by the caller
- Updated `RaceController.detail()` to compute card existence flags via `TeamCardService` before calling `getRaceDetailData`
- All 874 tests pass with zero regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Move RaceGraphicService to admin.service and relocate test** - `50909af` (refactor)
2. **Task 2: Decouple TeamCardService from RaceService and verify zero admin imports in domain** - `f6927e0` (refactor)

## Files Created/Modified

- `src/main/java/org/ctc/admin/service/RaceGraphicService.java` - Created: service relocated to admin.service package
- `src/test/java/org/ctc/admin/service/RaceGraphicServiceTest.java` - Created: test relocated to admin.service package
- `src/main/java/org/ctc/domain/service/RaceGraphicService.java` - Deleted: old location removed
- `src/test/java/org/ctc/domain/service/RaceGraphicServiceTest.java` - Deleted: old test location removed
- `src/main/java/org/ctc/domain/service/RaceService.java` - Removed TeamCardService dependency; changed getRaceDetailData signature; added findRaceById/findSeasonTeam helpers
- `src/test/java/org/ctc/domain/service/RaceServiceTest.java` - Removed TeamCardService mock; updated getRaceDetailData calls to new signature
- `src/main/java/org/ctc/admin/controller/RaceController.java` - Updated import; injected TeamCardService; computes card flags in detail()

## Decisions Made

- Card existence check moved to `RaceController.detail()` — the controller is already the coordinator for view data and naturally owns the decision of which admin services to call
- `findRaceById` and `findSeasonTeam` added as thin delegation methods on `RaceService` — keeps the controller from bypassing the service layer while avoiding direct repository injection in the controller
- No additional database round-trip: `findRaceById` is called once to get home/away team IDs, then `getRaceDetailData` loads the same race again internally; acceptable trade-off since OSIV keeps this in one session

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Known Stubs

None.

## Threat Flags

None — pure internal refactoring, no new network endpoints, no changed HTTP behavior, no new data exposed.

## Next Phase Readiness

- ARCH-01 requirement satisfied: `grep -r "import org.ctc.admin" src/main/java/org/ctc/domain/` returns zero results
- Ready for Phase 32 Plan 02

---
*Phase: 32-layering-and-exception-fix*
*Completed: 2026-04-13*
