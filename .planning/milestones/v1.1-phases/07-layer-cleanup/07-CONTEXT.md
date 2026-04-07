# Phase 7: Layer Cleanup - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Clean three-tier separation: Controllers contain no business logic and no direct repository access. Domain services do not depend on admin DTOs. All 5 target controllers delegate exclusively to services. StandingsController Buchholz/Swiss-sorting logic moves to StandingsService.

</domain>

<decisions>
## Implementation Decisions

### DTO Decoupling (ARCH-01)
- **D-01:** Move Form→Entity conversion from domain services to controllers. Services accept domain primitives (IDs, strings, entities) instead of admin Form DTOs. Controllers do the conversion before calling service methods.
- **D-02:** No new domain-layer DTO classes. The 12 service-DTO imports are eliminated by changing service method signatures to accept primitives/entities, and moving the mapping logic to controller methods.
- **D-03:** Affected services (10 total): CarService, DriverService, MatchScoringService, MatchdayService, PlayoffService, RaceScoringService, RaceService, SeasonManagementService, TeamManagementService, TrackService.

### Repository Removal (ARCH-02)
- **D-04:** Add finder/delegation methods to existing services instead of creating new services. Each controller already injects relevant services — extend those services with the needed query methods.
- **D-05:** Target controllers and their repository injections to eliminate:
  - StandingsController → SeasonRepository (use SeasonManagementService)
  - PowerRankingsController → SeasonRepository (use SeasonManagementService)
  - TeamCardController → SeasonRepository + SeasonTeamRepository (use SeasonManagementService + TeamManagementService)
  - PlayoffController → PlayoffRoundRepository (use PlayoffService)
  - CsvImportController → SeasonRepository (use SeasonManagementService)

### Buchholz/Swiss Integration (FEAT-02)
- **D-06:** Extend StandingsService with a combined method (e.g., `calculateStandingsWithBuchholz(seasonId)`) that integrates Buchholz scores from SwissPairingService and applies Swiss-format sorting. Controller calls one method instead of orchestrating calculation + sorting.
- **D-07:** The existing `calculateStandings()` remains unchanged for non-Swiss formats. The new method extends it for Swiss-format seasons.

### Claude's Discretion
- Method signature design for refactored services (which primitives to accept)
- Whether to use overloaded methods or new method names when replacing Form-accepting methods
- Ordering of refactoring (controllers first vs services first)
- Test strategy: unit tests for new service methods, integration tests for controller behavior

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architecture
- `.planning/codebase/ARCHITECTURE.md` — Controller layer rules, service layer responsibilities, three-tier pattern
- `.planning/codebase/CONVENTIONS.md` — Naming patterns, DTO patterns, controller conventions
- `.planning/codebase/CONCERNS.md` — Layer violation details with specific file/line references

### Controllers (to modify)
- `src/main/java/org/ctc/admin/controller/StandingsController.java` — SeasonRepository injection + Buchholz/Swiss sorting logic (lines 44-56)
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java` — SeasonRepository injection (line 25)
- `src/main/java/org/ctc/admin/controller/TeamCardController.java` — SeasonRepository + SeasonTeamRepository injections (lines 36-37)
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — PlayoffRoundRepository injection (line 31)
- `src/main/java/org/ctc/admin/controller/CsvImportController.java` — SeasonRepository injection (line 23)

### Services (to extend/modify)
- `src/main/java/org/ctc/domain/service/StandingsService.java` — Extend with Buchholz-integrated standings
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — Add finder methods for Season lookups
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` — Add SeasonTeam lookups
- `src/main/java/org/ctc/domain/service/PlayoffService.java` — Add PlayoffRound lookups

### Domain Services (DTO decoupling targets)
- All 10 services listed in D-03 under `src/main/java/org/ctc/domain/service/`

### Requirements
- `.planning/REQUIREMENTS.md` — ARCH-01, ARCH-02, FEAT-02 definitions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SeasonManagementService` already provides many Season-related operations — extending with findById() is trivial
- `TeamManagementService` already has team/season-team query methods
- `PlayoffService` already manages playoff data — adding round lookups is natural
- `SwissPairingService.calculateBuchholz()` already exists and is called from StandingsController

### Established Patterns
- v1.0 Phase 2 established the pattern: move repo access to service, controller calls service. Apply same pattern to remaining 5 controllers.
- v1.0 Phase 3 established the pattern: split large services. Not needed here — just adding methods to existing services.
- Controller → Service delegation: all v1.0-refactored controllers follow `service.findById(id).orElseThrow()` pattern

### Integration Points
- StandingsController is used by the admin Standings page — verify page still renders correctly after refactoring
- PowerRankingsController feeds the rankings display
- TeamCardController generates team card images — uses SeasonTeam data
- PlayoffController manages playoff bracket views
- CsvImportController handles race result CSV imports

</code_context>

<specifics>
## Specific Ideas

No specific requirements — standard layer cleanup following established v1.0 patterns.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 07-layer-cleanup*
*Context gathered: 2026-04-04*
