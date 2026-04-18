# Phase 33: Controller Cleanup - Context

**Gathered:** 2026-04-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Move business logic and data transformation from controllers to service layer, and fix SiteGeneratorService to use RaceLineup as source of truth for driver-team assignment.

Requirements: ARCH-03 (controller delegation), ARCH-04 (RaceLineup source of truth in site generator).

</domain>

<decisions>
## Implementation Decisions

### Controller Logic Extraction (ARCH-03)
- **D-01:** PowerRankingsController.list() (L33-56) contains complex season grouping, team count aggregation, and sorting logic. Extract this into a new method on an appropriate service (e.g., StandingsService or a new PowerRankingsService helper method).
- **D-02:** MatchdayController.detail() (L60-66) contains non-bye match filtering, schedule completeness checking, and missing schedule count calculation. Move to MatchdayService as a helper method that returns the computed data.
- **D-03:** DriverController.mergeForm() (L111-114) has minor stream filtering/sorting. Move to DriverService.
- **D-04:** RaceController.saveResults() DTO-to-service mapping is acceptable controller-layer responsibility (thin adapter). Do NOT move this — it's the controller's job to translate form input.

### SiteGeneratorService RaceLineup Fix (ARCH-04)
- **D-05:** SiteGeneratorService.toRaceView() (L272-283) resolves driver-team from SeasonDriver only. Fix to use RaceLineup as primary source with SeasonDriver as fallback — same pattern as ScoringService.isDriverInTeam() and RaceFormDataService.toRaceData().
- **D-06:** SiteGeneratorService.generateDriverProfiles() (L174-182) uses SeasonDriver for profile generation. This is acceptable for season-level profiles (not race-specific). Do NOT change this — it's the correct data source for season-scoped driver lists.
- **D-07:** Inject RaceLineupRepository into SiteGeneratorService for the race-level driver-team lookup.

### Claude's Discretion
- Whether to create new service methods or add to existing services for the extracted controller logic
- Exact method signatures for the extracted service methods
- Test strategy: unit tests for new service methods, update controller tests to verify delegation

</decisions>

<specifics>
## Specific References

- ScoringService.isDriverInTeam() already implements the RaceLineup-first pattern (Phase 31 added season filter)
- RaceFormDataService.toRaceData() also uses RaceLineup with SeasonDriver fallback
- PowerRankingsController uses a record SeasonGroupOption — this should move with the logic
- MatchdayController computes isScheduleComplete — this concept belongs in the service

</specifics>

<canonical_refs>
## Canonical References

- `.planning/codebase/ARCHITECTURE.md` — Layer boundaries
- `.planning/codebase/CONVENTIONS.md` — Controller/service patterns
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java` — Season grouping logic (L33-56)
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — Schedule checking logic (L60-66)
- `src/main/java/org/ctc/admin/controller/DriverController.java` — Driver filtering (L111-114)
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Driver-team resolution (L272-283)
- `src/main/java/org/ctc/domain/service/ScoringService.java` — RaceLineup pattern reference

</canonical_refs>

<deferred>
## Deferred Ideas

None.

</deferred>
