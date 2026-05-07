# Phase 65: Graphics Services Bridge Migration - Context

**Gathered:** 2026-05-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Migrate **all** production callers of the `@Deprecated StandingsService.calculateStandings(UUID seasonId)` overload to the canonical phase-aware `calculateStandings(UUID phaseId, UUID groupId)` API, then **delete** the bridge entirely from `StandingsService`. After this phase:

- `grep -nR "calculateStandings(seasonId" src/main/java | wc -l` reports `0` (SC1).
- The seasonId-only overload is gone from `StandingsService`.
- LEAGUE-layout seasons render byte-identical graphics (SC2 behavior preservation).
- GROUPS-layout matchday/race graphics correctly scope standings to the matchday's group.

The ROADMAP entry lists 5 graphics callers, but the codebase scout found **2 additional internal callers** that also use the seasonId bridge. Achieving SC1 requires migrating all 7 callers, not 5. Scope clarification approved by user during discussion.

</domain>

<decisions>
## Implementation Decisions

### Bridge fate

- **D-01:** `StandingsService.calculateStandings(UUID seasonId)` (line 148) is **completely removed** after all callers migrate. No deprecation runway. The cleaner cut closes audit item W-A definitively (per ROADMAP SC4 option a).
- **D-02:** `calculateStandingsLegacy` private fallback **does NOT exist anymore** — it was mentioned in the Phase 58 SUMMARY but earlier cleanup (likely Phase 61) already removed it. Codebase scout confirmed no occurrence of `calculateStandingsLegacy` in `src/`. No action needed.

### Migration scope (all 7 callers)

ROADMAP names 5 graphics callers. Codebase scout found 2 additional callers that also use the seasonId bridge. All 7 must migrate to satisfy SC1 (`grep = 0`).

**Graphics layer (5):**

- **D-03a:** `src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java:47` — covers `MatchdayScheduleGraphicService` via inheritance (no separate edit needed).
- **D-03b:** `src/main/java/org/ctc/admin/service/TeamCardService.java:52`
- **D-03c:** `src/main/java/org/ctc/admin/service/OverlayGraphicService.java:58`
- **D-03d:** `src/main/java/org/ctc/admin/service/SettingsGraphicService.java:67`
- **D-03e:** `src/main/java/org/ctc/admin/service/LineupGraphicService.java:70`

**Domain layer (2):**

- **D-04a:** `src/main/java/org/ctc/domain/service/SwissPairingService.java:158` — `calculateBuchholz(UUID seasonId)`. **Public API with 0 production callers, only 1 test (`SwissPairingServiceTest:198`).** Functional duplicate of `StandingsService.calculateStandingsWithBuchholz`. **Delete the method + delete the test** (cleanest cut).
- **D-04b:** `src/main/java/org/ctc/domain/service/StandingsService.java:201` — private helper `calculateBuchholzScores(UUID seasonId)`. Wrapped by `calculateBuchholzScoresForPhase(SeasonPhase phase)` which extracts `phase.getSeason().getId()` and calls the seasonId variant. Refactor: inline the seasonId variant into the phase-aware caller, eliminating the redundant indirection.

### Migration atomicity (3 plans)

- **D-05:** Phase 65 ships as **3 plans** with atomic commits per plan:
  - **Plan 65-01** — Graphics callers (D-03a..e): all 5 files migrated atomically with mock-test rewrites; one commit `fix(65): migrate graphics services to phase-aware standings API`.
  - **Plan 65-02** — Domain callers + cleanup (D-04a..b): SwissPairingService.calculateBuchholz removed (and its test); StandingsService.calculateBuchholzScores inlined into phase-aware path; one commit.
  - **Plan 65-03** — Bridge removal (D-01) + StandingsServiceTest cleanup (D-09): remove `calculateStandings(UUID seasonId)`, triage the ~14 dedicated bridge tests in `StandingsServiceTest` (delete duplicates, rewrite unique behaviors via canonical API). Final commit; SC1-grep `wc -l` = 0 verified in plan acceptance.

### Group-awareness per caller

Standings scope at each call site is decided by the caller's context (Matchday/Race vs. Season-only).

- **D-06:** Matchday/Race-context callers (D-03a, D-03c, D-03d, D-03e) use:
  ```java
  var matchday = ...;
  var phase = matchday.getPhase();
  var group = matchday.getGroup();  // direct accessor; nullable for LEAGUE
  var standings = standingsService.calculateStandings(
      phase.getId(),
      group != null ? group.getId() : null);
  ```
  This means GROUPS-layout matchday graphics show **per-group standings** for the matchday's group, while LEAGUE-layout graphics get `groupId = null` (combined view, byte-identical to today).
- **D-07:** Season-only caller `TeamCardService` (D-03b) uses:
  ```java
  var regular = seasonPhaseService.findRegularPhase(seasonId);
  var standings = standingsService.calculateStandings(regular.getId(), null);
  ```
  Combined view — data-identical to bridge behavior. Card files on disk (`{seasonId}/{shortName}.png`) need no path change; cache contract is invariant under this migration.
- **D-08:** Resolution stays **inline at each call site** — no shared helper extracted. The 1–2 lines per caller (`matchday.getPhase().getId() + matchday.getGroup() != null ? ...`) are too trivial to abstract. Locality > DRY for 5 near-identical sites; introducing a helper while removing another convenience overload would be self-contradictory.

### Test strategy

- **D-09:** The ~14 dedicated bridge tests in `StandingsServiceTest` are **triaged at planning time**: duplicates of phase/group coverage are deleted; unique behaviors (e.g., REGULAR-phase fallback semantics) are rewritten as `calculateStandings(regular.getId(), null)` calls.
- **D-10:** Existing graphics-service Mockito tests must stay green AFTER migration — their `when(...calculateStandings(season.getId()))` stubs are rewritten as `when(...calculateStandings(eq(regularPhase.getId()), isNull()))` per caller.
- **D-11:** **One explicit LEAGUE-regression test per migrated caller** (5 in total, modeled on existing test structure in each caller's `*Test.java`) using `Mockito.verify(standingsService).calculateStandings(eq(regularPhase.getId()), isNull())`. Belegt SC2 behavior-preservation explicitly.
- **D-12:** **3 representative GROUPS-tests** for matchday/race callers:
  - `AbstractMatchdayGraphicServiceTest` — `givenGroupsLayoutMatchday_whenPrepareBaseContext_thenStandingsCalledWithPhaseAndMatchdayGroup()`
  - `OverlayGraphicServiceTest` — same pattern via Race → Matchday → Group
  - `SettingsGraphicServiceTest` — same pattern, plus existing playoff branch coverage stays
  Skipped: `LineupGraphicServiceTest` (shares the Race-pattern with Settings), `TeamCardServiceTest` (always combined view, GROUPS scenario behaves identically).
- **D-13:** **Test style: Mockito unit tests** (consistent with existing `*GraphicServiceTest.java` patterns). No new `@SpringBootTest` ITs added — the canonical `(phaseId, groupId)` algorithm is already covered by `StandingsServiceTest` and Phase 61/62 GROUPS E2E coverage. The new tests only assert API-contract behavior at the caller boundary.

### Claude's Discretion

- **D-14:** **Bridge-test triage at planning time** (D-09) is delegated to the planner: planner identifies which tests in `StandingsServiceTest` (~14 candidates: lines 209, 240, 266, 286, 310, 332, 353, 384, 412, 439, 475, 507, 745) duplicate already-covered phase/group test surface and which encode unique semantics worth preserving in canonical form.
- **D-15:** **Migration wave order within a plan** is the planner's call. Suggested heuristic: `TeamCardService` first (simplest, season-only), then matchday/race callers in any order (same pattern), then domain callers in Plan 65-02, then bridge removal in Plan 65-03.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase artifacts (foundation)

- `.planning/ROADMAP.md` § "Phase 65: Graphics Services Bridge Migration" — phase goal, success criteria SC1–SC4, depends-on Phase 63.
- `.planning/REQUIREMENTS.md` SVC-02 — phase-aware standings requirement (Complete in Phase 58).
- `.planning/phases/58-service-layer/58-02-SUMMARY.md` — original deprecated bridge introduction context.
- `.planning/phases/61-cleanup-quality-gate/61-VALIDATION.md` — gold-standard validation template (used by Phase 64 sweep).

### Service surface

- `src/main/java/org/ctc/domain/service/StandingsService.java` lines 41, 102, 148–151, 201–211 — canonical and bridge entrypoints; private Buchholz helper.
- `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` `findRegularPhase(UUID seasonId)` — canonical helper for season → REGULAR-phase resolution.
- `src/main/java/org/ctc/domain/model/Matchday.java` lines 31–32 — direct `group` field with nullable `@JoinColumn(name = "group_id")` (post-Phase-57).

### CLAUDE.md / repo conventions

- `CLAUDE.md` § Subagent Rules — branch protection, no `git stash/checkout/reset`, opus|sonnet only.
- `CLAUDE.md` § Architectural Principles — "Keep Controllers Thin", "DTOs instead of Entities", "Keep Thymeleaf Templates Lean", **"No Inline Styles on Buttons"**.
- `CLAUDE.md` § Test Coverage — JaCoCo line coverage ≥ 82% (project-wide gate).
- `CLAUDE.md` § Test Naming — Given-When-Then BDD-style for all new tests.

### v1.9 cluster (decision lineage)

- `.planning/phases/56-model-schema-foundation/56-CONTEXT.md` — D-29 model decisions.
- `.planning/phases/62-public-site-phases-groups/62-CONTEXT.md` — public-site phase-awareness analog (lateral patterns).
- `.planning/phases/64-nyquist-validation-sweep/64-CONTEXT.md` — recent v1.9 sweep context.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`SeasonPhaseService.findRegularPhase(UUID seasonId)`** — the canonical helper. Already used by `StandingsController` and `PlayoffSeedingService`. TeamCardService should reuse this directly.
- **`Matchday.getPhase()` + `Matchday.getGroup()`** — direct accessors post-Phase-57. The matchday/race callers can use these without going through PhaseTeam resolution.
- **Mockito test pattern** — `AbstractMatchdayGraphicServiceTest` (and the 4 sibling `*GraphicServiceTest` classes) all share a common test-fixture style with `season`, `matchday`, `homeTeam`, `awayTeam` setup. New tests follow the same structure.
- **`StandingsServiceTest` lines 646, 691, 724** — already cover the canonical `(phaseId, groupId)` API across LEAGUE, GROUPS-combined, GROUPS-per-group scenarios. Phase 65 does not need to duplicate these.

### Established Patterns

- **`@Transactional(readOnly = true)`** at every `calculateStandings*` entrypoint — preserved on the canonical `(phaseId, groupId)` path; bridge removal does not affect transaction semantics.
- **`StandingsController.java:154`** — already uses the canonical phase-aware API with `resolvedPhase.getId()` + `group`. Reference implementation for the migration.
- **`SiteGeneratorService.java:268`, `:401`** — already migrated; uses `regularPhaseOpt.get().getId()` + `null`. Reference for combined-view callers.
- **`StandingsPageGenerator.java:121–122`** — already migrated; conditional Buchholz vs. standard call. Pattern reference for the `calculateStandingsWithBuchholz` migration if any caller currently uses Buchholz via the seasonId bridge (none do — Buchholz callers already pass `phaseId, groupId`).

### Integration Points

- **`StandingsServiceTest`** — needs ~14 tests triaged (delete duplicates, rewrite unique-behavior assertions on canonical API). Planner decides per-test in Plan 65-03.
- **`SwissPairingServiceTest:198`** — only test of the soon-to-be-deleted `calculateBuchholz(seasonId)`; deleted alongside the production method in Plan 65-02.
- **`SiteGeneratorServiceIT:154`** — `verify(standingsService, never()).calculateStandings(seasonId)` negative-assertion. Becomes redundant once the bridge is removed; Plan 65-03 deletes the assertion (the method no longer exists, so `verify(...never())` is vacuously true and noisy).
- **`TeamProfilePageGeneratorTest` lines 33, 153** — Javadoc-only references; safe to leave, no functional impact.

### Caller-context summary

| Caller | Context object | Phase resolution | Group resolution |
|---|---|---|---|
| `AbstractMatchdayGraphicService` | `Matchday matchday` | `matchday.getPhase()` | `matchday.getGroup()` (nullable) |
| `OverlayGraphicService` | `Race race` | `race.getMatchday().getPhase()` | `race.getMatchday().getGroup()` |
| `SettingsGraphicService` | `Race race` | `race.getMatchday().getPhase()` | `race.getMatchday().getGroup()` |
| `LineupGraphicService` | `Race race` | `race.getMatchday().getPhase()` | `race.getMatchday().getGroup()` |
| `TeamCardService` | `SeasonTeam seasonTeam` | `seasonPhaseService.findRegularPhase(seasonId)` | `null` |
| `SwissPairingService.calculateBuchholz` | `UUID seasonId` (public API) | n/a — method **deleted** | n/a |
| `StandingsService.calculateBuchholzScores` | `UUID seasonId` (private) | n/a — **inlined** into `calculateBuchholzScoresForPhase` | direct phase access |

</code_context>

<specifics>
## Specific Ideas

- "Komplett entfernen" — user explicitly chose hard removal of the bridge over deprecation runway. Cleanup over compatibility.
- "Du entscheidest beim Plan" — bridge-test triage delegated to planning step (per `feedback_orchestrator_discipline`: process gates respected).
- "1 Plan pro Caller-Gruppe + 1 Cleanup-Plan" — 3-plan structure preserves atomic commits per logical scope (graphics group / domain group / cleanup).
- The codebase scout discovered **calculateStandingsLegacy was already removed** — Phase 58 SUMMARY referenced it but it doesn't exist in the current source. Saved a discussion area.

</specifics>

<deferred>
## Deferred Ideas

- **Card-path phase-awareness** (`{phaseId}/{shortName}.png` instead of `{seasonId}/{shortName}.png`): user explicitly rejected this scope expansion. Card files stay seasonId-keyed; the Phase 65 migration does NOT touch the card-cache filesystem layout.
- **Buchholz/playoff edge-case exploration**: deferred — current tests cover Buchholz separately, and the playoff branch in `SettingsGraphicService` and `LineupGraphicService` is unaffected by the migration (it bypasses `calculateStandings` entirely via `playoffSeedRepository`).
- **JaCoCo per-caller pre-measurement**: not done — the JaCoCo gate (≥ 82% project-wide) is the binding contract; `./mvnw verify` at plan close validates it.

</deferred>

---

*Phase: 65-graphics-bridge-migration*
*Context gathered: 2026-05-07*
