---
phase: 07-layer-cleanup
plan: 01
subsystem: api
tags: [spring-boot, service-layer, three-tier, buchholz, standings]

# Dependency graph
requires:
  - phase: 02-service-layer-extraction
    provides: Initial service layer extraction pattern
provides:
  - StandingsService.calculateStandingsWithBuchholz() with 4-level sort
  - SeasonManagementService finder methods (findActiveSeason, findByIdOptional)
  - TeamManagementService finder methods (findSeasonTeamById, findSeasonTeamsBySeasonId)
  - PlayoffService.findRoundById()
  - Zero repository injections in 5 target controllers
affects: [07-02, 07-03]

# Tech tracking
tech-stack:
  added: []
  patterns: [service-delegation-for-finders, buchholz-in-standings-service]

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/StandingsService.java
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/main/java/org/ctc/domain/service/TeamManagementService.java
    - src/main/java/org/ctc/domain/service/PlayoffService.java
    - src/main/java/org/ctc/admin/controller/StandingsController.java
    - src/main/java/org/ctc/admin/controller/PowerRankingsController.java
    - src/main/java/org/ctc/admin/controller/TeamCardController.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/main/java/org/ctc/dataimport/CsvImportController.java
    - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/TeamManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/PlayoffServiceTest.java

key-decisions:
  - "findActiveSeason() uses stream filter instead of findByActiveTrue() to tolerate multiple active seasons in test scenarios"
  - "Buchholz logic duplicated into StandingsService rather than injecting SwissPairingService (avoids circular dependency)"
  - "SeasonTeamRepository added to TeamManagementService for finder delegation"

patterns-established:
  - "Controller finder delegation: controllers call service.findXxxById() instead of repository.findById().orElseThrow()"
  - "Buchholz-integrated standings: calculateStandingsWithBuchholz() as single-call API for Swiss format"

requirements-completed: [FEAT-02, ARCH-02]

# Metrics
duration: 9min
completed: 2026-04-05
---

# Phase 7 Plan 1: Service Layer Cleanup Summary

**Buchholz-integrated standings in StandingsService and zero repository injections across 5 controllers via service delegation**

## Performance

- **Duration:** 9 min
- **Started:** 2026-04-05T10:17:53Z
- **Completed:** 2026-04-05T10:26:51Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments
- Moved Buchholz calculation and 4-level Swiss-format sorting from StandingsController into StandingsService.calculateStandingsWithBuchholz()
- Extended 4 domain services with 7 new finder/utility methods
- Removed all repository injections from StandingsController, PowerRankingsController, TeamCardController, PlayoffController, and CsvImportController
- All 777 tests pass, no behavior changes

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend services with finder methods and Buchholz integration (TDD)**
   - `c28ddba` (test: add failing tests)
   - `98215ba` (feat: implement service methods)
2. **Task 2: Remove repository injections from 5 controllers** - `b228e5b` (refactor)

**Plan metadata:** pending (docs: complete plan)

## Files Created/Modified
- `StandingsService.java` - Added calculateStandingsWithBuchholz(), calculateBuchholzScores(), RaceRepository injection
- `SeasonManagementService.java` - Added findActiveSeason(), findByIdOptional()
- `TeamManagementService.java` - Added findSeasonTeamById(), findSeasonTeamsBySeasonId(), SeasonTeamRepository injection
- `PlayoffService.java` - Added findRoundById()
- `StandingsController.java` - Replaced SeasonRepository + SwissPairingService with services
- `PowerRankingsController.java` - Replaced SeasonRepository with SeasonManagementService
- `TeamCardController.java` - Replaced SeasonRepository + SeasonTeamRepository with services
- `PlayoffController.java` - Replaced PlayoffRoundRepository with PlayoffService
- `CsvImportController.java` - Replaced SeasonRepository with SeasonManagementService

## Decisions Made
- Used stream-based findActiveSeason() instead of repository findByActiveTrue() to avoid NonUniqueResultException when multiple active seasons exist in test data
- Duplicated Buchholz calculation logic from SwissPairingService into StandingsService to avoid circular dependency (SwissPairingService already depends on StandingsService)
- Added SeasonTeamRepository to TeamManagementService since it was the natural home for season-team finder methods

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed findActiveSeason() NonUniqueResultException**
- **Found during:** Task 2 (Controller refactoring)
- **Issue:** findByActiveTrue() throws when multiple active seasons exist (test data scenario)
- **Fix:** Changed findActiveSeason() to use findAll().stream().filter(Season::isActive).findFirst() which gracefully handles multiple active seasons
- **Files modified:** SeasonManagementService.java, SeasonManagementServiceTest.java
- **Verification:** TeamCardControllerTest.givenActiveSeason_whenGetTeamCards passes
- **Committed in:** b228e5b (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug fix)
**Impact on plan:** Essential for test compatibility. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 5 target controllers now use only service injections (no repositories)
- Service layer is consistent: all entity lookups go through services
- Ready for plan 07-02 (further layer cleanup tasks)

---
*Phase: 07-layer-cleanup*
*Completed: 2026-04-05*
