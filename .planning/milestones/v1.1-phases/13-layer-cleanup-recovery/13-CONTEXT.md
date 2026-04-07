# Phase 13: Layer Cleanup Recovery - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Re-apply controller→service delegation and domain DTO decoupling lost by worktree file clobber (commit 5b3a58b regression). Recover the exact changes from Phase 7 via cherry-pick of commits b733781 + Plan 02/03 commits. All 5 target controllers delegate exclusively to services, 10 domain services have zero admin.dto imports, and Buchholz/Swiss-sorting logic lives in StandingsService.

</domain>

<decisions>
## Implementation Decisions

### Recovery Strategy
- **D-01:** Cherry-pick Phase 7 commits (b733781 + Plan 02/03 commits) and resolve merge conflicts. The code was already reviewed and tested — fastest recovery path.
- **D-02:** Commit grouping at Claude's discretion — logical groups or original commits depending on merge conflict situation.

### Scope
- **D-03:** 1:1 identical scope with Phase 7 — all 3 areas: DTO-Decoupling (10 services), Repository-Removal (5 controllers), Buchholz-Integration.
- **D-04:** CsvImportController (now at `org.ctc.dataimport.CsvImportController`) is included in the cleanup despite being in a feature module. It still has SeasonRepository injection that needs to go through SeasonManagementService.

### DTO Decoupling (ARCH-01) — from Phase 7
- **D-05:** Move Form→Entity conversion from domain services to controllers. Services accept domain primitives (IDs, strings, entities) instead of admin Form DTOs.
- **D-06:** No new domain-layer DTO classes. The 10 service-DTO imports are eliminated by changing service method signatures to accept primitives/entities.
- **D-07:** Affected services: CarService, DriverService, MatchScoringService, MatchdayService, PlayoffSeedingService, RaceScoringService, SeasonManagementService, TeamManagementService, TrackService, and any additional services with admin.dto imports.

### Repository Removal (ARCH-02) — from Phase 7
- **D-08:** Add finder/delegation methods to existing services instead of creating new services.
- **D-09:** Target controllers and their repository injections to eliminate:
  - StandingsController → SeasonRepository (use SeasonManagementService)
  - PowerRankingsController → SeasonRepository (use SeasonManagementService)
  - TeamCardController → SeasonRepository + SeasonTeamRepository (use SeasonManagementService + TeamManagementService)
  - PlayoffController → PlayoffRoundRepository (use PlayoffService)
  - CsvImportController → SeasonRepository (use SeasonManagementService)

### Buchholz/Swiss Integration (FEAT-02) — from Phase 7
- **D-10:** Move Buchholz calculation and Swiss-format sorting from StandingsController to StandingsService. Add a combined method (e.g., `calculateStandingsWithBuchholz(seasonId)`) that integrates Buchholz scores from SwissPairingService and applies Swiss-format sorting.
- **D-11:** Existing `calculateStandings()` remains unchanged for non-Swiss formats.

### Test Strategy
- **D-12:** Cherry-pick Phase 7 tests alongside the implementation. Adapt tests if cherry-pick conflicts arise.
- **D-13:** Verification via `./mvnw verify -Pe2e` — full test suite including Playwright E2E to guarantee all admin UI pages still render correctly.

### Execution Order
- **D-14:** Follow original Phase 7 order: Plan 01: DTO-Decoupling → Plan 02: Repository-Removal → Plan 03: Buchholz-Integration. Cherry-pick commits in matching sequence.

### Claude's Discretion
- Commit grouping strategy (logical groups vs individual cherry-picks depending on conflict resolution)
- Method signature design for refactored services
- Whether to use overloaded methods or new method names when replacing Form-accepting methods

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 7 (Recovery Source)
- `.planning/phases/07-layer-cleanup/07-CONTEXT.md` — Original decisions D-01 through D-07, exact same cleanup targets
- `.planning/phases/07-layer-cleanup/07-PLAN-01.md` — DTO-Decoupling plan (if exists, for cherry-pick reference)
- `.planning/phases/07-layer-cleanup/07-PLAN-02.md` — Repository-Removal plan (if exists)
- `.planning/phases/07-layer-cleanup/07-PLAN-03.md` — Buchholz-Integration plan (if exists)

### Architecture
- `.planning/codebase/ARCHITECTURE.md` — Controller layer rules, service layer responsibilities, three-tier pattern
- `.planning/codebase/CONVENTIONS.md` — Naming patterns, DTO patterns, controller conventions
- `.planning/codebase/CONCERNS.md` — Layer violation details with specific file/line references

### Controllers (to modify)
- `src/main/java/org/ctc/admin/controller/StandingsController.java` — SeasonRepository injection + Buchholz/Swiss sorting logic in controller
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java` — SeasonRepository injection
- `src/main/java/org/ctc/admin/controller/TeamCardController.java` — SeasonRepository + SeasonTeamRepository injections
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — PlayoffRoundRepository injection
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — SeasonRepository injection (moved from admin.controller to dataimport)

### Services (to extend/modify)
- `src/main/java/org/ctc/domain/service/StandingsService.java` — Extend with Buchholz-integrated standings
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — Add finder methods for Season lookups
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` — Add SeasonTeam lookups
- `src/main/java/org/ctc/domain/service/PlayoffService.java` — Add PlayoffRound lookups

### Domain Services (DTO decoupling targets)
- `src/main/java/org/ctc/domain/service/CarService.java` — imports CarForm
- `src/main/java/org/ctc/domain/service/DriverService.java` — imports DriverForm
- `src/main/java/org/ctc/domain/service/MatchScoringService.java` — imports MatchScoringForm
- `src/main/java/org/ctc/domain/service/MatchdayService.java` — imports MatchdayDto
- `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` — imports SeedForm
- `src/main/java/org/ctc/domain/service/RaceScoringService.java` — imports RaceScoringForm
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — imports SeasonForm
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` — imports SeasonDriverGroupDto, TeamForm
- `src/main/java/org/ctc/domain/service/TrackService.java` — imports TrackForm

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
- v1.0 Phase 2 established the pattern: move repo access to service, controller calls service
- Controller → Service delegation: all v1.0-refactored controllers follow `service.findById(id).orElseThrow()` pattern
- `@RequiredArgsConstructor` for constructor injection via `final` fields

### Integration Points
- StandingsController serves the admin Standings page — verify page still renders correctly
- PowerRankingsController feeds the rankings display
- TeamCardController generates team card images — uses SeasonTeam data
- PlayoffController manages playoff bracket views
- CsvImportController handles race result CSV imports with season label display

</code_context>

<specifics>
## Specific Ideas

- Recovery source commits: `b733781` + Phase 7 Plan 02/03 commits — cherry-pick these in order
- CsvImportController package path changed since Phase 7: now `org.ctc.dataimport` instead of `org.ctc.admin.controller`
- PlayoffSeedingService (imports SeedForm) was not in original Phase 7 D-03 list but has admin.dto import — include in cleanup

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 13-layer-cleanup-recovery*
*Context gathered: 2026-04-06*
