---
phase: 03-god-service-split
plan: 01
subsystem: domain-service
tags: [refactoring, service-extraction, god-service-split, spring-service]

# Dependency graph
requires:
  - phase: 02-controller-cleanup
    provides: Clean controller-service delegation pattern
provides:
  - RaceAttachmentService with file/link attachment operations
  - RaceGraphicService with graphic generation operations
  - RaceManagementService reduced from 673 to 515 lines
affects: [03-god-service-split]

# Tech tracking
tech-stack:
  added: []
  patterns: [functional-interface-for-DRY, single-responsibility-service-extraction]

key-files:
  created:
    - src/main/java/org/ctc/domain/service/RaceAttachmentService.java
    - src/main/java/org/ctc/domain/service/RaceGraphicService.java
    - src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java
    - src/test/java/org/ctc/domain/service/RaceGraphicServiceTest.java
  modified:
    - src/main/java/org/ctc/domain/service/RaceManagementService.java
    - src/test/java/org/ctc/domain/service/RaceManagementServiceTest.java
    - src/main/java/org/ctc/admin/controller/RaceController.java

key-decisions:
  - "DRY refactoring in RaceGraphicService via GraphicGenerator functional interface"
  - "RaceController updated to inject both new services directly"

patterns-established:
  - "GraphicGenerator: @FunctionalInterface for DRY graphic generation pattern"
  - "Service extraction: move methods + tests + update controller callers atomically"

requirements-completed: [SRVC-08]

# Metrics
duration: 7min
completed: 2026-04-04
---

# Phase 03 Plan 01: RaceAttachmentService + RaceGraphicService Extraction Summary

**Extracted 9 methods into 2 focused services from RaceManagementService, reducing dependencies from 20 to 14 with DRY GraphicGenerator pattern**

## Performance

- **Duration:** 7 min
- **Started:** 2026-04-04T07:29:37Z
- **Completed:** 2026-04-04T07:36:52Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Extracted RaceAttachmentService with 5 methods (upload, addLink, delete, download, getExtension) and 6 passing tests
- Extracted RaceGraphicService with 4 public methods using DRY helper via @FunctionalInterface and 5 passing tests
- Reduced RaceManagementService from 673 to 515 lines and from 20 to 14 dependencies
- Updated RaceController to delegate to new services

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract RaceAttachmentService + RaceAttachmentServiceTest** - `6343c5d` (refactor)
2. **Task 2: Extract RaceGraphicService + RaceGraphicServiceTest** - `ed2c5bd` (refactor)

## Files Created/Modified
- `src/main/java/org/ctc/domain/service/RaceAttachmentService.java` - File/link attachment operations (upload, addLink, delete, download)
- `src/main/java/org/ctc/domain/service/RaceGraphicService.java` - Graphic generation with DRY helper (lineup, results, settings, overlay)
- `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java` - 6 unit tests for attachment operations
- `src/test/java/org/ctc/domain/service/RaceGraphicServiceTest.java` - 5 unit tests for graphic generation
- `src/main/java/org/ctc/domain/service/RaceManagementService.java` - Removed 9 methods, 6 dependencies
- `src/test/java/org/ctc/domain/service/RaceManagementServiceTest.java` - Removed 11 tests, cleaned unused mocks
- `src/main/java/org/ctc/admin/controller/RaceController.java` - Added RaceAttachmentService + RaceGraphicService injection

## Decisions Made
- Applied DRY refactoring in RaceGraphicService: 4 identical generate-and-save patterns consolidated via GraphicGenerator @FunctionalInterface and private generateAndSaveGraphic helper
- Updated RaceController to inject both new services directly (not via RaceManagementService delegation)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated RaceController to use new services**
- **Found during:** Task 1 and Task 2
- **Issue:** Plan focused on service extraction but RaceController calls moved methods via raceManagementService
- **Fix:** Added RaceAttachmentService and RaceGraphicService to RaceController, updated all 8 method calls
- **Files modified:** src/main/java/org/ctc/admin/controller/RaceController.java
- **Verification:** Clean compilation, all tests pass
- **Committed in:** 6343c5d (Task 1), ed2c5bd (Task 2)

---

**Total deviations:** 1 auto-fixed (blocking - controller would not compile without update)
**Impact on plan:** Essential fix for compilation. No scope creep.

## Issues Encountered
None

## Known Stubs
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- RaceManagementService still has 515 lines with core CRUD, calendar, results, and form data methods
- Ready for Plan 02 (further extraction if planned)
- All 37 tests pass across the 3 test classes

---
*Phase: 03-god-service-split*
*Completed: 2026-04-04*
