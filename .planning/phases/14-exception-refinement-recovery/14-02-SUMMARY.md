---
phase: 14-exception-refinement-recovery
plan: 02
subsystem: exception-handling
tags: [java, exception-handling, ioexception, service-layer, tdd]

# Dependency graph
requires:
  - phase: 14-exception-refinement-recovery
    provides: "Phase 14 context, ERRH-01 scope, FileStorageService IOException contracts"
provides:
  - "5 service catch(Exception e) blocks narrowed to catch(IOException e)"
  - "QUAL-02 disposition documented for DriverRankingService.calculateAlltimeRanking()"
  - "TDD tests verifying IOException catch AND RuntimeException propagation"
affects: [14-exception-refinement-recovery, verifier]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Narrow catch(Exception) to catch(IOException) in service image upload methods"
    - "Add propagation test (givenRuntimeException_thenPropagates) alongside IOException wrapping test"
    - "Document acceptable findAll() usage with QUAL-02 Javadoc"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/CarService.java
    - src/main/java/org/ctc/domain/service/TrackService.java
    - src/main/java/org/ctc/domain/service/TeamManagementService.java
    - src/main/java/org/ctc/gt7sync/Gt7SyncService.java
    - src/main/java/org/ctc/gt7sync/Gt7ScraperService.java
    - src/main/java/org/ctc/domain/service/DriverRankingService.java
    - src/test/java/org/ctc/domain/service/CarServiceTest.java
    - src/test/java/org/ctc/domain/service/TrackServiceTest.java
    - src/test/java/org/ctc/domain/service/TeamManagementServiceTest.java
    - src/test/java/org/ctc/gt7sync/Gt7SyncServiceTest.java

key-decisions:
  - "TDD Red/Green: wrote propagation tests first (failing with catch(Exception e)), then narrowed catches"
  - "Gt7SyncService has 4 catch(IOException e) total: 2 pre-existing + 2 newly narrowed batch lambda catches"
  - "DriverRankingService.calculateAlltimeRanking() findAll() acceptable — QUAL-02 documented via Javadoc"
  - "RaceService.getRaceListData() already clean (List.of() fallback, no findAll())"

patterns-established:
  - "Propagation test pattern: givenRuntimeException_whenAction_thenPropagates verifies narrowed catch does NOT catch unexpected exceptions"

requirements-completed:
  - ERRH-01

# Metrics
duration: 25min
completed: 2026-04-07
---

# Phase 14 Plan 02: Exception Refinement Recovery (Services) Summary

**5 service catch(Exception e) blocks narrowed to catch(IOException e) via TDD, with QUAL-02 Javadoc on DriverRankingService.calculateAlltimeRanking()**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-07T07:10:00Z
- **Completed:** 2026-04-07T07:35:00Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments
- Narrowed 5 `catch(Exception e)` blocks in service classes to `catch(IOException e)` — programming errors (RuntimeException) now propagate to GlobalExceptionHandler instead of being silently wrapped
- Added TDD tests verifying both the IOException→BusinessRuleException wrapping AND that unexpected RuntimeException propagates (not caught)
- Documented QUAL-02 disposition for DriverRankingService.calculateAlltimeRanking() — findAll() intentional for alltime cross-season view
- Confirmed RaceService already clean (uses List.of() fallback, not findAll())
- 774 tests pass, BUILD SUCCESS, coverage ≥ 82%

## Task Commits

Each task was committed atomically:

1. **Task 1 (RED):** test(14-02): add failing tests for IOException-narrowed catch blocks - `f21d5e9`
2. **Task 1 (GREEN):** feat(14-02): narrow service catch(Exception e) to catch(IOException e) - `14d4791`
3. **Task 2:** docs(14-02): add QUAL-02 disposition Javadoc to DriverRankingService - `d1e9190`

_Note: Task 1 used TDD — test commit (RED) followed by implementation commit (GREEN)._

## Files Created/Modified
- `src/main/java/org/ctc/domain/service/CarService.java` - Added IOException import, narrowed catch
- `src/main/java/org/ctc/domain/service/TrackService.java` - Added IOException import, narrowed catch
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` - Added IOException import, narrowed catch in uploadLogo()
- `src/main/java/org/ctc/gt7sync/Gt7SyncService.java` - Narrowed both batch lambda catches (car + track download)
- `src/main/java/org/ctc/gt7sync/Gt7ScraperService.java` - Narrowed catch in resolveTrackImagesParallel()
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` - Added QUAL-02 Javadoc to calculateAlltimeRanking()
- `src/test/java/org/ctc/domain/service/CarServiceTest.java` - Added givenRuntimeException_whenUploadImage_thenPropagates
- `src/test/java/org/ctc/domain/service/TrackServiceTest.java` - Added givenRuntimeException_whenUploadImage_thenPropagates
- `src/test/java/org/ctc/domain/service/TeamManagementServiceTest.java` - Updated existing test to throw IOException, added RuntimeException propagation test
- `src/test/java/org/ctc/gt7sync/Gt7SyncServiceTest.java` - Added givenIoException_whenDownloadCarImage_thenBatchCompletesWithoutThrowing

## Decisions Made
- Gt7SyncService had 2 pre-existing `catch(IOException e)` blocks (at lines 29, 33 — fetch result error handlers). After narrowing, total count is 4. The plan's expected count of 2 referred only to the newly narrowed batch lambda blocks.
- TeamManagementServiceTest existing upload failure test was updated from `RuntimeException` to `IOException` — the original test was not testing the correct exception type for the narrowed catch contract.
- Gt7ScraperService `resolveTrackImagesParallel()` is a private method with non-mockable `fetchText()`. No unit test was added for the IOException path in Gt7ScraperService — the catch narrowing is verified by compiler correctness (fetchText() declares `throws IOException`, catch narrowed to IOException).

## Deviations from Plan

None — plan executed exactly as written. The Gt7SyncService catch count discrepancy (4 actual vs 2 expected) is explained by pre-existing IOException catches in other try-blocks not mentioned in the plan scope.

## Issues Encountered
- Initial test run was accidentally using the main repo Maven (not worktree). Corrected to use worktree-relative Maven commands.
- Gt7SyncService `replace_all` edit only replaced the first `catch (Exception e)` (car batch). Second occurrence (track batch) required a separate edit — both now narrowed correctly.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 5 service catch blocks from ERRH-01 scope are narrowed
- QUAL-02 disposition documented
- Plan 14-01 (controller catch narrowing) can proceed in parallel or after this plan
- Zero `catch(Exception e)` remains in domain/service and gt7sync service classes

---
*Phase: 14-exception-refinement-recovery*
*Completed: 2026-04-07*
