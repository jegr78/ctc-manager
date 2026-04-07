---
phase: 10-service-refactoring
plan: "03"
subsystem: api
tags: [spring, service-layer, refactoring, google-calendar, transactional]

requires:
  - phase: 10-service-refactoring
    provides: Phase context and research on RaceService split

provides:
  - RaceFormDataService — read-only form data assembly with @Transactional(readOnly=true)
  - RaceCalendarService — Google Calendar delegation isolated from CRUD
  - Slimmed RaceService — CRUD + scoring only (~347 lines, down from 525)
  - Updated RaceController — injects 3 focused services

affects:
  - Any plan referencing RaceService (form data or calendar methods)
  - RaceController integration tests

tech-stack:
  added: []
  patterns:
    - "Service decomposition: extract read-only form data assembly into dedicated @Transactional(readOnly=true) service"
    - "Service decomposition: isolate external-API integration (Google Calendar) in dedicated service"
    - "Nested record type reuse: RaceFormDataService returns RaceService.RaceFormData to keep records co-located with domain"

key-files:
  created:
    - src/main/java/org/ctc/domain/service/RaceFormDataService.java
    - src/main/java/org/ctc/domain/service/RaceCalendarService.java
    - src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java
    - src/test/java/org/ctc/domain/service/RaceCalendarServiceTest.java
  modified:
    - src/main/java/org/ctc/domain/service/RaceService.java
    - src/main/java/org/ctc/admin/controller/RaceController.java
    - src/test/java/org/ctc/domain/service/RaceServiceTest.java

key-decisions:
  - "All record types (RaceFormData, ResultsFormData, etc.) stay in RaceService as API contracts; new services reference them as RaceService.RaceFormData"
  - "RaceCalendarService exposes isCalendarAvailable() delegating to GoogleCalendarService so RaceService can remove GoogleCalendarService field while getRaceDetailData still reports calendar availability"
  - "RaceController update treated as Rule 3 auto-fix (blocking compilation) and bundled into Task 1 commit"
  - "Private getUsedCarIds/getUsedTrackIds duplicated in both RaceService (for saveRace validation) and RaceFormDataService (for form data assembly) — intentional, avoids cross-service dependency"

requirements-completed:
  - ARCH-05

duration: 15min
completed: 2026-04-06
---

# Phase 10 Plan 03: Service Refactoring — RaceService Split Summary

**RaceService (525 lines) split into RaceFormDataService (read-only form assembly, 181 lines), RaceCalendarService (Google Calendar delegation, 76 lines), and slimmed RaceService (CRUD + scoring, 347 lines)**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-06T10:08:00Z
- **Completed:** 2026-04-06T10:13:00Z
- **Tasks:** 2 (Task 1 + Task 2 verification)
- **Files modified:** 7

## Accomplishments

- Extracted 3 form data assembly methods + 5 private helpers from RaceService into `RaceFormDataService` with `@Transactional(readOnly = true)` class-level annotation
- Extracted `createOrUpdateCalendarEvent` + `resolveEventDuration` from RaceService into `RaceCalendarService`, isolating the `GoogleCalendarService` external dependency
- Added `RaceCalendarService.isCalendarAvailable()` delegation so `RaceDetailData` can still report calendar availability without `GoogleCalendarService` in `RaceService`
- Updated `RaceController` to inject all three services and route method calls accordingly
- 25 new unit tests across `RaceFormDataServiceTest` (5) and `RaceCalendarServiceTest` (5); updated `RaceServiceTest` (15) removing extracted tests
- Full test suite: 780 tests pass, JaCoCo coverage checks met

## Task Commits

1. **Task 1: Extract RaceFormDataService and RaceCalendarService from RaceService** - `351ef34` (feat)

**Plan metadata:** (created as part of this summary)

_Note: Task 2 (RaceController wiring) was bundled into Task 1 commit as a Rule 3 auto-fix (blocking compilation)._

## Files Created/Modified

- `src/main/java/org/ctc/domain/service/RaceFormDataService.java` — New read-only form data service; @Transactional(readOnly=true); assembles RaceFormData and ResultsFormData
- `src/main/java/org/ctc/domain/service/RaceCalendarService.java` — New calendar service; wraps GoogleCalendarService; createOrUpdateCalendarEvent + isCalendarAvailable()
- `src/main/java/org/ctc/domain/service/RaceService.java` — Removed form data methods, calendar methods, GoogleCalendarService field; now CRUD + scoring only
- `src/main/java/org/ctc/admin/controller/RaceController.java` — Added RaceFormDataService + RaceCalendarService injection; updated 5 method call targets
- `src/test/java/org/ctc/domain/service/RaceFormDataServiceTest.java` — 5 unit tests for form data assembly
- `src/test/java/org/ctc/domain/service/RaceCalendarServiceTest.java` — 5 unit tests for calendar delegation
- `src/test/java/org/ctc/domain/service/RaceServiceTest.java` — Removed calendar/form tests; replaced GoogleCalendarService mock with RaceCalendarService mock

## Decisions Made

- Record types stay in `RaceService` (per RESEARCH assumption A3); `RaceFormDataService` returns `RaceService.RaceFormData` etc. as inner type references.
- `RaceCalendarService.isCalendarAvailable()` added to preserve `getRaceDetailData` calendar-flag logic without keeping `GoogleCalendarService` in `RaceService`.
- Private `getUsedCarIds`/`getUsedTrackIds` duplicated in both services: `RaceService` uses them for uniqueness validation in `saveRace`; `RaceFormDataService` uses them for form data population. No cross-service dependency introduced.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] RaceController updated in Task 1 to fix compilation**

- **Found during:** Task 1 (running service tests)
- **Issue:** Removing methods from RaceService caused compilation failure in RaceController (5 unresolved method references)
- **Fix:** Updated RaceController imports and method calls to target RaceFormDataService and RaceCalendarService in the same commit
- **Files modified:** `src/main/java/org/ctc/admin/controller/RaceController.java`
- **Verification:** `./mvnw test -Dtest="RaceFormDataServiceTest,RaceCalendarServiceTest,RaceServiceTest"` — 25 tests pass
- **Committed in:** `351ef34` (bundled with Task 1)

**2. [Rule 2 - Missing Critical] Added isCalendarAvailable() to RaceCalendarService**

- **Found during:** Task 1 (analyzing getRaceDetailData in RaceService)
- **Issue:** `getRaceDetailData` called `googleCalendarService.isAvailable()` — removing GoogleCalendarService field would break calendar availability flag
- **Fix:** Added `isCalendarAvailable()` delegate method to `RaceCalendarService`; injected `RaceCalendarService` into `RaceService` instead of `GoogleCalendarService`
- **Files modified:** `src/main/java/org/ctc/domain/service/RaceCalendarService.java`, `src/main/java/org/ctc/domain/service/RaceService.java`
- **Verification:** `getRaceDetailData` tests pass, calendarAvailable flag still computed correctly
- **Committed in:** `351ef34`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** Both auto-fixes necessary for compilation correctness and feature completeness. No scope creep.

## Issues Encountered

None beyond the auto-fixed deviations above.

## Known Stubs

None — all methods are fully implemented with real data sources.

## Threat Flags

No new trust boundaries introduced. RaceService split is internal refactoring only. Disposition matches threat register entries T-10-04 and T-10-05.

## Next Phase Readiness

- RaceService reduced from 525 to 347 lines with focused CRUD + scoring responsibility
- RaceFormDataService and RaceCalendarService are independently testable and injectable
- RaceController wires all three services correctly
- Full test suite green, coverage requirements met
- Ready for phase completion and PR creation

---
*Phase: 10-service-refactoring*
*Completed: 2026-04-06*
