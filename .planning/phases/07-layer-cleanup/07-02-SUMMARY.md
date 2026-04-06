---
phase: 07-layer-cleanup
plan: 02
subsystem: architecture
tags: [layer-separation, domain-services, dto-decoupling, refactoring]

# Dependency graph
requires:
  - phase: 07-layer-cleanup plan 01
    provides: StandingsController business logic extracted to service
provides:
  - 5 domain services decoupled from admin DTO imports
  - Primitive-based save() methods on CarService, TrackService, DriverService, RaceScoringService, MatchScoringService
affects: [07-layer-cleanup plan 03]

# Tech tracking
tech-stack:
  added: []
  patterns: [form-to-primitives extraction in controllers, domain services accept only primitives]

key-files:
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

key-decisions:
  - "Primitive parameters instead of wrapper/record objects -- keeps services maximally decoupled"

patterns-established:
  - "Domain services accept only primitives/standard types, never admin DTOs"
  - "Controllers extract form fields and pass as primitives to service methods"

requirements-completed: [ARCH-01]

# Metrics
duration: 5min
completed: 2026-04-05
---

# Phase 07 Plan 02: Simple Service DTO Decoupling Summary

**5 domain services decoupled from admin DTO imports by converting save() signatures to primitives and moving form extraction to controllers**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-05T10:28:24Z
- **Completed:** 2026-04-05T10:34:05Z
- **Tasks:** 2
- **Files modified:** 15

## Accomplishments
- Removed all `org.ctc.admin.dto` imports from CarService, TrackService, DriverService, RaceScoringService, MatchScoringService
- Changed save() method signatures from Form objects to primitive parameters
- Controllers now extract form fields and pass as primitives to services
- All 108 related tests pass (76 for Task 1, 32 for Task 2)
- Full test suite green with coverage checks met

## Task Commits

Each task was committed atomically:

1. **Task 1: Decouple CarService, TrackService, DriverService** - `a79cce5` (refactor)
2. **Task 2: Decouple RaceScoringService, MatchScoringService** - `98ae052` (refactor)

## Files Created/Modified
- `src/main/java/org/ctc/domain/service/CarService.java` - save(UUID, String, String) instead of save(CarForm)
- `src/main/java/org/ctc/domain/service/TrackService.java` - save(UUID, String, String) instead of save(TrackForm)
- `src/main/java/org/ctc/domain/service/DriverService.java` - save(UUID, String, String, boolean, List) instead of save(DriverForm)
- `src/main/java/org/ctc/domain/service/RaceScoringService.java` - save(UUID, String, String, String, int) instead of save(RaceScoringForm)
- `src/main/java/org/ctc/domain/service/MatchScoringService.java` - save(UUID, String, int, int, int) instead of save(MatchScoringForm)
- `src/main/java/org/ctc/admin/controller/CarController.java` - Extracts CarForm fields before calling service
- `src/main/java/org/ctc/admin/controller/TrackController.java` - Extracts TrackForm fields before calling service
- `src/main/java/org/ctc/admin/controller/DriverController.java` - Extracts DriverForm fields before calling service
- `src/main/java/org/ctc/admin/controller/RaceScoringController.java` - Extracts RaceScoringForm fields before calling service
- `src/main/java/org/ctc/admin/controller/MatchScoringController.java` - Extracts MatchScoringForm fields before calling service
- `src/test/java/org/ctc/domain/service/CarServiceTest.java` - Updated save tests to use primitives
- `src/test/java/org/ctc/domain/service/TrackServiceTest.java` - Updated save tests to use primitives
- `src/test/java/org/ctc/domain/service/DriverServiceTest.java` - Updated save tests to use primitives
- `src/test/java/org/ctc/domain/service/RaceScoringServiceTest.java` - Updated save tests to use primitives
- `src/test/java/org/ctc/domain/service/MatchScoringServiceTest.java` - Updated save tests to use primitives

## Decisions Made
- Used primitive parameters directly rather than introducing a domain-layer record/DTO -- keeps services maximally simple and avoids creating yet another abstraction layer

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None

## Next Phase Readiness
- 5 simple domain services now fully decoupled from admin layer
- Ready for plan 03 (remaining complex service decoupling if applicable)

---
## Self-Check: PASSED

All files exist, all commits verified.

---
*Phase: 07-layer-cleanup*
*Completed: 2026-04-05*
