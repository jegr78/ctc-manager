---
phase: 08-exception-refinement
plan: 02
subsystem: error-handling
tags: [exception-handling, IOException, findAll, query-scoping]

# Dependency graph
requires:
  - phase: 01-exception-infrastructure
    provides: BusinessRuleException, EntityNotFoundException base classes
provides:
  - "Zero catch(Exception e) blocks in domain services and gt7sync"
  - "Bounded queries in RaceService (no unbounded findAll fallback)"
  - "Documented intentional findAll() in DriverRankingService"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "IOException-specific catch for FileStorageService image operations"
    - "Empty list fallback instead of unbounded findAll() for missing filter"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/CarService.java
    - src/main/java/org/ctc/domain/service/TrackService.java
    - src/main/java/org/ctc/domain/service/TeamManagementService.java
    - src/main/java/org/ctc/gt7sync/Gt7SyncService.java
    - src/main/java/org/ctc/gt7sync/Gt7ScraperService.java
    - src/main/java/org/ctc/domain/service/RaceService.java
    - src/main/java/org/ctc/domain/service/DriverRankingService.java

key-decisions:
  - "Narrowed all 6 catch(Exception e) to catch(IOException e) since FileStorageService and Jsoup are the only exception sources"
  - "RaceService returns empty list when no filter provided -- UI always passes seasonId"

patterns-established:
  - "FileStorageService callers catch IOException specifically, wrap to BusinessRuleException"

requirements-completed: [ERRH-01, QUAL-02]

# Metrics
duration: 5min
completed: 2026-04-05
---

# Phase 08 Plan 02: Service Exception Narrowing Summary

**Narrowed 6 catch(Exception e) blocks to IOException in 5 service files, removed unbounded findAll() fallback in RaceService, documented intentional findAll() in DriverRankingService**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-05T11:11:13Z
- **Completed:** 2026-04-05T11:16:34Z
- **Tasks:** 2
- **Files modified:** 9 (7 production + 2 test)

## Accomplishments
- All catch(Exception e) blocks in domain services and gt7sync narrowed to catch(IOException e)
- RaceService.getRaceListData() no longer falls back to unbounded findAll() -- returns empty list when no filter
- DriverRankingService.calculateAlltimeRanking() findAll() documented as intentional (small dataset)
- All 753 tests pass with coverage >= 82%

## Task Commits

Each task was committed atomically:

1. **Task 1: Narrow service and gt7sync catch blocks to specific exceptions** - `28966eb` (refactor)
2. **Task 2: Scope unbounded findAll() queries and run full verification** - `30a3f75` (refactor)

## Files Created/Modified
- `src/main/java/org/ctc/domain/service/CarService.java` - catch(IOException e) for image upload
- `src/main/java/org/ctc/domain/service/TrackService.java` - catch(IOException e) for image upload
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` - catch(IOException e) for logo upload
- `src/main/java/org/ctc/gt7sync/Gt7SyncService.java` - 2x catch(IOException e) in async image downloads
- `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java` - catch(IOException e) in async chunk fetching
- `src/main/java/org/ctc/domain/service/RaceService.java` - List.of() instead of findAll() fallback
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` - findAll() documented as intentional
- `src/test/java/org/ctc/domain/service/RaceServiceTest.java` - Updated test for empty list behavior
- `src/test/java/org/ctc/domain/service/TeamManagementServiceTest.java` - Fixed mock to throw IOException

## Decisions Made
- Narrowed all 6 catch blocks to IOException since FileStorageService methods (storeImage, storeFromUrl, delete) and Jsoup (fetchText) are the only exception sources in these try blocks
- RaceService returns empty list when no filter provided -- the UI always passes a seasonId from the dropdown, so the else branch is a safety fallback that should not load all races

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed TeamManagementServiceTest mock exception type**
- **Found during:** Task 2 (full verification)
- **Issue:** Test threw RuntimeException("IO error") which is not caught by narrowed catch(IOException e)
- **Fix:** Changed mock to throw IOException("IO error") to match production code
- **Files modified:** src/test/java/org/ctc/domain/service/TeamManagementServiceTest.java
- **Verification:** All 753 tests pass
- **Committed in:** 30a3f75 (Task 2 commit)

**2. [Rule 1 - Bug] Fixed RaceServiceTest assertion for removed findAll()**
- **Found during:** Task 2 (full verification)
- **Issue:** Test expected raceRepository.findAll() to be called, but we removed that call
- **Fix:** Changed test to verify findAll() is never called and result is empty list
- **Files modified:** src/test/java/org/ctc/domain/service/RaceServiceTest.java
- **Verification:** All 753 tests pass
- **Committed in:** 30a3f75 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs in tests due to production code changes)
**Impact on plan:** Both fixes necessary for test correctness after narrowing catches and removing findAll(). No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ERRH-01 service-level exception narrowing complete
- QUAL-02 unbounded query scoping complete
- Phase 08 plans fully executed

---
*Phase: 08-exception-refinement*
*Completed: 2026-04-05*
