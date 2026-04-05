---
phase: 07-layer-cleanup
plan: 03
subsystem: api
tags: [spring-boot, service-layer, dto-decoupling, records, architecture]

requires:
  - phase: 07-01
    provides: finder methods in domain services, repository removal from controllers
  - phase: 07-02
    provides: simple service DTO decoupling (5 services)
provides:
  - zero admin.dto imports in all 10 domain services
  - nested domain records (SeasonDriverGroup, SeedEntry, MatchdayData, RaceData, RaceResultData)
  - primitive-parameter save() methods for all services
affects: [future-refactoring, service-splitting]

tech-stack:
  added: []
  patterns: [nested-public-records-as-service-api-contracts, controller-dto-to-primitive-mapping]

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/main/java/org/ctc/domain/service/TeamManagementService.java
    - src/main/java/org/ctc/domain/service/PlayoffService.java
    - src/main/java/org/ctc/domain/service/MatchdayService.java
    - src/main/java/org/ctc/domain/service/RaceService.java
    - src/main/java/org/ctc/admin/controller/SeasonController.java
    - src/main/java/org/ctc/admin/controller/TeamController.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/main/java/org/ctc/admin/controller/MatchdayController.java
    - src/main/java/org/ctc/admin/controller/RaceController.java

key-decisions:
  - "Nested records in services as API contracts instead of separate domain DTO classes (per D-02)"
  - "RaceController maps RaceData<->RaceForm for Thymeleaf template compatibility"

patterns-established:
  - "Nested public records: Service return types use nested records (e.g., RaceService.RaceData) instead of admin DTOs"
  - "Controller-layer mapping: Controllers convert between admin Forms (for Thymeleaf binding) and service primitives/records"

requirements-completed: [ARCH-01]

duration: 11min
completed: 2026-04-05
---

# Phase 07 Plan 03: DTO Decoupling Summary

**Complete admin DTO elimination from all 10 domain services via primitive parameters and nested domain records**

## Performance

- **Duration:** 11 min
- **Started:** 2026-04-05T10:35:42Z
- **Completed:** 2026-04-05T10:47:00Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments
- All 10 domain services have zero imports from org.ctc.admin.dto (ARCH-01 complete)
- 5 complex services decoupled: SeasonManagementService, TeamManagementService, PlayoffService, MatchdayService, RaceService
- 5 nested domain records created as service API contracts: SeasonDriverGroup, SeedEntry, MatchdayData, RaceData, RaceResultData
- All 777 tests passing with full build green

## Task Commits

Each task was committed atomically:

1. **Task 1: Decouple SeasonManagement, TeamManagement, PlayoffService** - `ed387cb` (refactor)
2. **Task 2: Decouple MatchdayService and RaceService** - `daede6a` (refactor)

## Files Created/Modified
- `SeasonManagementService.java` - save() accepts 14 primitives instead of SeasonForm
- `TeamManagementService.java` - save() accepts 6 primitives; SeasonDriverGroup replaces SeasonDriverGroupDto
- `PlayoffService.java` - SeedEntry record replaces SeedForm; saveSeed() accepts List<SeedEntry>
- `MatchdayService.java` - MatchdayData record replaces MatchdayDto
- `RaceService.java` - RaceData/RaceResultData records replace RaceForm/RaceResultForm; saveRace() accepts 18 primitives
- `SeasonController.java` - Extracts SeasonForm fields for service call
- `TeamController.java` - Extracts TeamForm fields for service call
- `PlayoffController.java` - Converts SeedForm entries to PlayoffService.SeedEntry list
- `MatchdayController.java` - Uses MatchdayService.MatchdayData instead of MatchdayDto
- `RaceController.java` - Maps RaceData<->RaceForm, extracts primitives for save, converts results

## Decisions Made
- Nested records in services as API contracts instead of separate domain DTO classes (per D-02, consistent with existing PlayoffService.PlayoffListData pattern)
- RaceController maintains a private toRaceForm() helper to map domain RaceData back to admin RaceForm for Thymeleaf template binding
- admin.service imports (TeamCardService, graphic services) in domain services noted but left out of scope

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ARCH-01 fully complete: zero admin.dto imports in domain services
- Remaining admin.service imports (TeamCardService, graphic services) in RaceService/RaceGraphicService are a separate concern for future phases
- Phase 07 layer-cleanup complete

## Self-Check: PASSED

All files exist. All commits verified (ed387cb, daede6a).

---
*Phase: 07-layer-cleanup*
*Completed: 2026-04-05*
