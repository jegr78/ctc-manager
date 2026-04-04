---
phase: 01-exception-infrastructure
plan: 02
subsystem: exception-handling
tags: [EntityNotFoundException, ValidationException, orElseThrow, spring-boot]

requires:
  - phase: 01-exception-infrastructure/plan-01
    provides: EntityNotFoundException, ValidationException, BusinessRuleException classes
provides:
  - "All 135 orElseThrow calls across 21 files migrated to typed exceptions"
  - "Every missing-entity scenario produces debuggable exception with entity type and ID"
  - "CsvImportService uses ValidationException for import-context lookups"
affects: [02-service-extraction, 03-god-service-split]

tech-stack:
  added: []
  patterns: ["EntityNotFoundException('EntityType', id) for all repository lookups", "ValidationException for CSV import context lookups"]

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/RaceManagementService.java
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/main/java/org/ctc/domain/service/PlayoffService.java
    - src/main/java/org/ctc/domain/service/MatchService.java
    - src/main/java/org/ctc/domain/service/SwissPairingService.java
    - src/main/java/org/ctc/domain/service/MatchdayService.java
    - src/main/java/org/ctc/domain/service/RaceLineupService.java
    - src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java
    - src/main/java/org/ctc/domain/service/TeamManagementService.java
    - src/main/java/org/ctc/domain/service/DriverService.java
    - src/main/java/org/ctc/admin/controller/SeasonController.java
    - src/main/java/org/ctc/admin/controller/DriverController.java
    - src/main/java/org/ctc/admin/controller/TeamController.java
    - src/main/java/org/ctc/admin/controller/TrackController.java
    - src/main/java/org/ctc/admin/controller/CarController.java
    - src/main/java/org/ctc/admin/controller/RaceScoringController.java
    - src/main/java/org/ctc/admin/controller/MatchScoringController.java
    - src/main/java/org/ctc/admin/controller/TeamCardController.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/main/java/org/ctc/admin/TestDataService.java
    - src/main/java/org/ctc/dataimport/CsvImportService.java
    - src/test/java/org/ctc/domain/service/PlayoffServiceTest.java

key-decisions:
  - "SeasonManagementService.findSeasonTeam() IllegalStateException preserved as business rule, not entity lookup"
  - "CsvImportService uses ValidationException (not EntityNotFoundException) for import-context lookups"
  - "TestDataService included in migration for better debug messages on test setup failures"
  - "MatchdayService.createInline ResponseStatusException preserved (re-thrown by GlobalExceptionHandler)"

patterns-established:
  - "EntityNotFoundException pattern: .orElseThrow(() -> new EntityNotFoundException('EntityType', id))"
  - "ValidationException for import/CSV context: .orElseThrow(() -> new ValidationException('... in CSV import: ' + id))"

requirements-completed: [EXCP-02]

duration: 17min
completed: 2026-04-03
---

# Phase 01 Plan 02: orElseThrow Migration Summary

**Migrated all 135 orElseThrow calls across 21 production files to EntityNotFoundException/ValidationException with entity type and ID in every message**

## Performance

- **Duration:** 17 min
- **Started:** 2026-04-03T19:48:50Z
- **Completed:** 2026-04-03T20:05:56Z
- **Tasks:** 2
- **Files modified:** 22 (21 production + 1 test)

## Accomplishments
- Zero bare .orElseThrow() calls remaining in src/main/java
- 132 EntityNotFoundException instances across 20 files with entity type and ID
- 3 ValidationException instances in CsvImportService for import-context lookups
- All 671 tests pass with 82%+ coverage maintained

## Task Commits

Each task was committed atomically:

1. **Task 1: Migrate orElseThrow in services (10 files)** - `68051ca` (feat)
2. **Task 2: Migrate orElseThrow in controllers and remaining files (11 files)** - `6b93c5e` (feat)

## Files Created/Modified
- `src/main/java/org/ctc/domain/service/RaceManagementService.java` - 20 EntityNotFoundException migrations
- `src/main/java/org/ctc/domain/service/PlayoffService.java` - 21 EntityNotFoundException migrations (bare + lambda)
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` - 14 EntityNotFoundException migrations
- `src/main/java/org/ctc/domain/service/MatchService.java` - 7 EntityNotFoundException migrations
- `src/main/java/org/ctc/domain/service/SwissPairingService.java` - 5 EntityNotFoundException migrations
- `src/main/java/org/ctc/domain/service/MatchdayService.java` - 4 EntityNotFoundException migrations
- `src/main/java/org/ctc/domain/service/RaceLineupService.java` - 4 EntityNotFoundException migrations
- `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java` - 2 EntityNotFoundException migrations
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` - 1 EntityNotFoundException migration
- `src/main/java/org/ctc/domain/service/DriverService.java` - 1 EntityNotFoundException migration
- `src/main/java/org/ctc/admin/controller/SeasonController.java` - 9 EntityNotFoundException migrations
- `src/main/java/org/ctc/admin/controller/DriverController.java` - 6 EntityNotFoundException migrations
- `src/main/java/org/ctc/admin/controller/TeamController.java` - 6 EntityNotFoundException migrations
- `src/main/java/org/ctc/admin/controller/TrackController.java` - 4 EntityNotFoundException migrations
- `src/main/java/org/ctc/admin/controller/CarController.java` - 4 EntityNotFoundException migrations
- `src/main/java/org/ctc/admin/controller/TeamCardController.java` - 4 EntityNotFoundException migrations
- `src/main/java/org/ctc/admin/controller/RaceScoringController.java` - 3 EntityNotFoundException migrations
- `src/main/java/org/ctc/admin/controller/MatchScoringController.java` - 3 EntityNotFoundException migrations
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` - 3 EntityNotFoundException migrations (from IllegalArgumentException)
- `src/main/java/org/ctc/admin/TestDataService.java` - 11 EntityNotFoundException migrations (4 bare + 7 lambda)
- `src/main/java/org/ctc/dataimport/CsvImportService.java` - 3 ValidationException migrations (from IllegalArgumentException)
- `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` - Updated test expectation for EntityNotFoundException

## Decisions Made
- SeasonManagementService.findSeasonTeam() keeps IllegalStateException (business rule: "team not in season", not a missing entity)
- MatchdayService.createInline keeps ResponseStatusException (re-thrown as-is by GlobalExceptionHandler from Plan 01)
- CsvImportService uses ValidationException for all 3 lookups (import context = validation failure, not entity lookup)
- TestDataService included in migration to provide better debug messages on test setup failures

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Updated PlayoffServiceTest to expect EntityNotFoundException**
- **Found during:** Task 1 (service migration)
- **Issue:** PlayoffServiceTest.givenUnknownRoundId_whenSetRoundLegs_thenThrowsException expected IllegalArgumentException but service now throws EntityNotFoundException
- **Fix:** Changed assertThrows expectation to EntityNotFoundException, added import
- **Files modified:** src/test/java/org/ctc/domain/service/PlayoffServiceTest.java
- **Verification:** All 671 tests pass
- **Committed in:** 68051ca (Task 1 commit)

**2. [Rule 2 - Missing Critical] CsvImportService has 3 lambdas not 2**
- **Found during:** Task 2 (controller migration)
- **Issue:** Plan counted 2 lambda-style calls in CsvImportService but there are actually 3 (Season, PlayoffMatchup, Matchday lookups)
- **Fix:** Converted all 3 to ValidationException for consistency
- **Files modified:** src/main/java/org/ctc/dataimport/CsvImportService.java
- **Verification:** ./mvnw verify passes
- **Committed in:** 6b93c5e (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both auto-fixes necessary for correctness. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all migrations are complete with no placeholder data.

## Next Phase Readiness
- Phase 01 (exception-infrastructure) is complete
- All orElseThrow calls produce typed exceptions with entity type and ID
- GlobalExceptionHandler (Plan 01) will render meaningful error pages for all EntityNotFoundException instances
- Ready for Phase 02 (service extraction) where extracted services will inherit the clean exception pattern

---
*Phase: 01-exception-infrastructure*
*Completed: 2026-04-03*
