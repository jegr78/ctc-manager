# Phase 58: Service Layer - Context

**Gathered:** 2026-04-27
**Status:** Ready for planning

<domain>
## Phase Boundary

All domain services operate exclusively on the new Phase / Group model introduced in Phase 56 and populated in Phase 57. Service-level cutover means:

- **`SeasonPhaseService` (NEW)** provides CRUD for `SeasonPhase` and `SeasonPhaseGroup`, plus roster management via `PhaseTeam`.
- **`StandingsService`** accepts `phaseId` (canonical) + optional `groupId`; returns per-group standings or a flat combined-view across groups.
- **`PlayoffService` + `PlayoffSeedingService`** load and save via `phase_id` (the BRACKET-layout PLAYOFF phase) instead of `season_id`. `Playoff.phase` is the single source of truth; the legacy M:N `playoff_seasons` is left intact (Phase 61 drops it).
- **`MatchdayGeneratorService` + `SwissPairingService`** generate matchdays linked to a `phase_id` (and optionally a `group_id`). Per-group operation is the canonical mode for `layout=GROUPS`.
- **`DriverRankingService`** ranks drivers within a phase and aggregates across all phases of a season for the season-wide ranking.

**Explicitly out of scope for Phase 58** (locked by ROADMAP/STATE):
- Driver-import refactor (`SeasonRepository.findByYearAndNumber`, group resolution via `PhaseTeam`) — Phase 59 (IMPORT-01..04).
- `TestDataService` / `DevDataSeeder` rebuild on the new model — Phase 59 (DATA-01, DATA-02).
- Admin UI (season-form slim, phase-tabs, group sub-tabs, phase/group CRUD forms, standings UI with combined view) — Phase 60 (UI-01..07).
- Drop of bridge columns (`matchday.season_id`, `playoff.season_id`, M:N `playoff_seasons`) and removal of legacy `Season` columns — Phase 61 (MIGR-06).
- `SeasonForm` cleanup (remove format/scoring/dates fields) — Phase 60 (UI-01). Phase 58 keeps the form 1:1 and instead auto-syncs writes into the REGULAR phase.
- E2E test for full GROUPS-season workflow — Phase 61 (QUAL-02).

</domain>

<decisions>
## Implementation Decisions

### API cutover style (D-01..D-03)

- **D-01: phaseId-only canonical signatures with seasonId-overload bridge.** Each refactored service exposes `phaseId`-based methods as the canonical entry point. Each canonical method gets a thin `seasonId`-overload that resolves the REGULAR phase via `SeasonPhaseService.findRegularPhase(seasonId)` and delegates. Controllers stay on `seasonId` in Phase 58; Phase 60 (UI) removes the overloads alongside the UI cutover. No parallel implementations — overloads are 1-line bridges.
- **D-02: Resolution centralized in `SeasonPhaseService.findRegularPhase(UUID seasonId)`.** Single resolution point. All six refactored services inject `SeasonPhaseService` (added to their `@RequiredArgsConstructor` constructor). On missing REGULAR phase: throws `EntityNotFoundException` (consistent with "No Fallback Calculations" — should never happen post-V4 migration; fail loud is correct).
- **D-03: `@Deprecated` annotation on every transitional API.** All `seasonId`-overloads, the M:N methods (`playoffService.addSeasonToPlayoff` / `removeSeasonFromPlayoff`), and any helper that exists only to bridge the old model are marked `@Deprecated` with a Javadoc note `"remove in Phase 60/61"`. Makes the cleanup path grep-able. The M:N methods stay functional in Phase 58 (still write to `playoff_seasons` — table exists until Phase 61) so existing PlayoffController + templates continue to work.

### Combined-view Standings (D-04..D-06)

- **D-04: Combined-view = flat list of raw points.** `calculateStandings(phaseId, null)` for a `layout=GROUPS` phase returns ONE flat `List<TeamStanding>` containing every team across every sub-group, each team carrying the points it earned in its own group's matches. Sorting uses the existing tiebreaker chain: `points → pointDifference → pointsFor`. No cross-group "fair-rank" math — the view is an anzeige-comfort aggregation, not a championship metric. Per-group view stays available via `calculateStandings(phaseId, groupId)`.
- **D-05: `TeamStanding` gets a nullable `SeasonPhaseGroup group` field.** Single return type `List<TeamStanding>` for both LEAGUE and GROUPS layouts. For LEAGUE: `group=null`. For GROUPS: `group=set` to the team's sub-group. Templates can render a group badge via `th:if="${standing.group}"`. No new wrapper record, no `Map<Group, ...>` return type, no sealed-interface choice.
- **D-06: Buchholz is per-group only.** `calculateStandingsWithBuchholz(phaseId, groupId)` requires a `groupId` for `layout=GROUPS` phases, since Buchholz mathematically depends on opposition that all played within the same group. The combined-view for Swiss-format GROUPS phases (`calculateStandingsWithBuchholz(phaseId, null)`) IGNORES the `buchholz` field as a tiebreaker — falls back to `points → pointDifference → pointsFor`. The `buchholz` field is still populated on `TeamStanding` for UI display, just not used for cross-group sorting.

### Driver-Ranking aggregation (D-07..D-10)

- **D-07: All phase types count in the season-wide aggregation.** REGULAR + PLAYOFF + PLACEMENT all contribute to season-wide driver ranking. **This is a behavior change vs today** — current `calculateRanking(seasonId)` via `findByRaceMatchdaySeasonId` only catches REGULAR-phase results because PLAYOFF races link via `Race.playoffMatchup`, not via `Race.matchday`. Phase 58 widens the source to include playoff race results.
- **D-08: Driver-team for season-wide aggregation = REGULAR-phase team.** A driver appears in the saisonweite Aggregation under their REGULAR-phase `PhaseTeam`. Per-phase ranking shows the phase-specific team. Stand-Ins (drivers who only ran in PLAYOFF for a different team) get folded into their REGULAR-phase team in the season-wide view.
- **D-09: API split — narrow per-phase + named aggregation method.**
  - `calculateRankingForPhase(UUID phaseId)` — primary per-phase entry point.
  - `aggregateAcrossPhases(List<UUID> phaseIds, UUID seasonId)` — named season-wide aggregation default-method on `DriverRankingService`. Loops phases, runs per-phase ranking, merges with REGULAR-team attribution.
  - `calculateRanking(UUID seasonId)` — `@Deprecated` bridge: `aggregateAcrossPhases(seasonPhaseService.findAllPhases(seasonId).map(SeasonPhase::getId), seasonId)`.
  - `calculateAlltimeRanking()` and `calculateAlltimeRanking(List<UUID> seasonIds)` stay structurally unchanged but internally use the new per-phase paths via the bridge.
- **D-10: RaceLineup-fallback for stand-ins without REGULAR-phase `PhaseTeam`.** When a driver has race results in some phase but no `PhaseTeam` row in the REGULAR phase (e.g., playoff stand-in with no regular-season roster), team attribution falls back to `RaceLineup` — the team from the driver's first/last `RaceLineup` in the season wins. Consistent with `CLAUDE.md` "RaceLineup is Source of Truth".

### Test-fixtures strategy (D-11..D-13)

- **D-11: `PhaseTestFixtures` helper class.** New `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` with builder-style methods: `createRegularPhase(Season)`, `createGroupsPhase(Season, int groupCount)`, `assignTeamToPhase(SeasonPhase, Team, SeasonPhaseGroup?)`, `createPlayoffPhase(Season, String label)`. Pure-Java entity construction (no DB). Reused by `@DataJpaTest` repository-IT-tests and by Mockito service tests for stub-data setup. Lives in Phase 58, may be retired in Phase 59 once `TestDataService` covers the patterns or kept for fine-grained fixtures.
- **D-12: Service tests stay Mockito-only.** Phase 58 service tests follow the existing Codebase pattern (`@ExtendWith(MockitoExtension.class)`, mocked repositories). `SeasonPhaseRepository`, `PhaseTeamRepository`, `RaceResultRepository`, etc. are mocked. `PhaseTestFixtures` provides the entity stubs returned by mocks. Fast, isolated, consistent with the 23 existing service tests.
- **D-13: `@DataJpaTest` per new repository.** Three new IT-test classes: `SeasonPhaseRepositoryIT`, `SeasonPhaseGroupRepositoryIT`, `PhaseTeamRepositoryIT`. Each tests its custom finders against H2 with `PhaseTestFixtures`-built data. This is the DB-truth garantie for the new finders without forcing service-layer tests onto an integration footprint.

### SeasonPhaseService CRUD-validation (D-14)

- **D-14: Service pre-checks the "max 1 REGULAR / 1 PLAYOFF / 1 PLACEMENT per season" rule before INSERT.** `SeasonPhaseService.create()` calls `seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, phaseType)` — if present, throws `BusinessRuleException("Season already has REGULAR/PLAYOFF/PLACEMENT phase")`. Database `UNIQUE (season_id, phase_type)` constraint (Phase 56 D-03) is the belt; service guard is the suspenders for sprechende Fehlermeldungen.

### Playoff seeding pool (D-15)

- **D-15: `PlayoffSeedingService.autoSeedBracket` pulls from REGULAR-phase standings.** Resolves the REGULAR phase via `SeasonPhaseService.findRegularPhase`, runs `StandingsService.calculateStandings(regularPhaseId, null)` (or per-group depending on layout), and takes Top-N teams as initial seeds. The PLAYOFF-phase's own `PhaseTeam` roster is NOT used for auto-seeding — it's populated as a *side-effect* of seed creation (each seeded team becomes a `PhaseTeam` row of the PLAYOFF phase). For GROUPS-layout REGULAR phases, the combined-view (D-04: flat list, raw points) drives the Top-N selection. Manual override via the existing PlayoffSeedingController save-flow remains.

### Matchday + Swiss group granularity (D-16, D-17)

- **D-16: MatchdayGenerator works per group.** `MatchdayGeneratorService.generate(UUID phaseId, UUID groupId, int numberOfRounds, boolean homeAndAway)`. For `layout=LEAGUE`: `groupId` MUST be null (`IllegalArgumentException` if not). For `layout=GROUPS`: `groupId` MUST be set. `MatchdayGeneratorService.GeneratorFormData` record gains a `SeasonPhase phase` field (replacing or alongside `Season season`); planner picks shape that minimizes Phase-60 churn but ensures the new field is present.
- **D-17: SwissPairingService is fully per-group-isolated.** All four methods (`generateNextRound`, `getByeTeams`, `getCurrentRound`, `isCurrentRoundComplete`) accept `(UUID phaseId, UUID groupId)`. For `layout=LEAGUE`: `groupId` must be null. For `layout=GROUPS`: `groupId` must be set. Each group has its own bye list, its own current round, its own progress. Different groups can be at different rounds — no implicit cross-group synchronization.

### Season delete-cascade (D-18)

- **D-18: `SeasonManagementService.delete` enforces a strict pre-check.** Refuses to delete if the season has any phase containing matchdays, playoff matches, or `phase_teams` rows. Throws `BusinessRuleException("Season has active phases — clear matches/teams before deleting")`. **This is a behavior change vs today** — current `delete(seasonId)` just calls `seasonRepository.delete(season)` and trusts JPA cascade. Phase 58 introduces the guard because the new phase hierarchy makes accidental cascade-deletes much higher-impact (phases, groups, phase-teams chain plus existing matchdays/playoff). UI flow via `/admin/seasons/delete` will see a flash error message explaining what the user needs to clear first.

### Playoff-phase auto-creation (D-19)

- **D-19: `playoffService.createPlayoff(seasonId, ...)` auto-creates the PLAYOFF phase.** Checks via `seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF)`. If absent: invokes `seasonPhaseService.create(...)` to insert a `SeasonPhase` row with `phaseType=PLAYOFF`, `layout=BRACKET`, `format=LEAGUE` (DB-default workaround per Phase 57 D-08), `sortIndex=10`, `label=playoff.name`, `raceScoring`/`matchScoring` copied from `Season`. If a PLAYOFF phase already exists: throws `BusinessRuleException("Season already has a playoff phase")`. The new phase's id becomes `playoff.phase_id`. Removes the need for an extra UI step in Phase 60.

### PhaseTeam roster initial-population (D-20)

- **D-20: Roster init is phase-type-specific.**
  - REGULAR phase: `PhaseTeam` rows auto-derived from existing `SeasonTeam` rows (1:1, `group_id=NULL` because `layout=LEAGUE`). Mirrors what V4 migration did for legacy data.
  - PLAYOFF phase: roster starts empty. `autoSeedBracket` populates it as a side-effect (D-15) when seeds are created.
  - PLACEMENT phase: roster starts empty (admin chooses).
  - GROUPS-layout REGULAR phase: roster starts empty — admin must define groups first, then assign teams. The auto-derivation only triggers for `layout=LEAGUE` REGULAR phases.

### SwissPairing bye-team in GROUPS (D-21)

- **D-21: Per-group isolation extends to bye-handling.** `getByeTeams(phaseId, groupId)` returns the bye-team for that specific group only. With odd-team-count groups, each group has its own potential bye. Ressources `getCurrentRound` and `isCurrentRoundComplete` likewise treat each group as independent. The phase-aggregate "is this whole phase done?" check is left to the caller (sum over groups) — service does not provide a phase-level convenience.

### Repository finder naming (D-22)

- **D-22: Spring-Data-magic-naming preferred; `@Query`-JPQL as fallback.** New custom finders use Spring Data method-name convention where it works:
  - `SeasonPhaseRepository.findBySeasonIdAndPhaseType(UUID, PhaseType)`
  - `SeasonPhaseRepository.findBySeasonIdOrderBySortIndex(UUID)`
  - `SeasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(UUID)`
  - `PhaseTeamRepository.findByPhaseIdAndGroupId(UUID, UUID)`
  - `PhaseTeamRepository.findByPhaseId(UUID)`
  - `RaceResultRepository.findByRaceMatchdayPhaseId(UUID)` — for per-phase REGULAR ranking
  - `RaceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID)` — for per-phase PLAYOFF ranking
  Switch to `@Query` JPQL only when the magic-naming becomes ambiguous, when joining requires `JOIN FETCH`, or when the method name exceeds ~60 chars.

### SiteGenerator phase update (D-23)

- **D-23: SiteGenerator is brought along in Phase 58.** `SiteGeneratorService` (and helpers under `org.ctc.sitegen`) call the new phase-aware service APIs directly where possible (e.g., `standingsService.calculateStandings(regularPhaseId, null)` via `seasonPhaseService.findRegularPhase`). Driver-ranking saisonweite Aggregation goes through the new `aggregateAcrossPhases` path. Per-group standings are not yet rendered (UI-side Phase 60 — site templates stay LEAGUE-shaped). The static site stays correct after Phase 58 and Phase 61 cleanup is mechanical.

### EntityGraph strategy (D-24)

- **D-24: Pragmatic — `@EntityGraph` only where it removes a measurable N+1.** New custom finders ship without `@EntityGraph` by default. Add `@EntityGraph(attributePaths = ...)` only when:
  - A service method iterates over a collection that would otherwise N+1 (e.g., `seasonPhaseRepository.findBySeasonIdOrderBySortIndex` → `attributePaths={"groups", "raceScoring", "matchScoring"}` if the caller iterates per-group standings).
  - A test or profiling shows the lazy-load is the bottleneck.
  Consistent with "OSIV bleibt aktiv — `@EntityGraph` als Optimization" (CLAUDE.md). No EntityGraph-on-every-method boilerplate.

### SeasonManagementService.save Form-cleanup (D-25)

- **D-25: Status-quo writes + auto-sync to REGULAR phase.** `SeasonManagementService.save(form, scoringIds)` keeps writing `format`, `totalRounds`, `legs`, `eventDurationMinutes`, `startDate`, `endDate`, `raceScoring`, `matchScoring` onto the legacy `Season` entity (those columns exist until Phase 61). After saving, the service additionally finds the REGULAR phase via `SeasonPhaseService.findRegularPhase` and synchronizes the same fields onto the `SeasonPhase` row. After Phase 58: REGULAR phase is the read-truth for services; legacy Season fields stay write-aligned for backward compat. Phase 60 (UI-01) removes the form fields AND this sync block in one go.

### MatchdayService phase-filter (D-26)

- **D-26: Both seasonId- and phaseId-shaped methods exist in parallel.** `MatchdayService` gains `findByPhaseId(UUID phaseId)` and `findByPhaseIdAndGroupId(UUID phaseId, UUID groupId)` as primary methods. The existing `findBySeasonId(UUID seasonId)` becomes `@Deprecated`: delegates by aggregating `findByPhaseId` across all phases of the season (REGULAR + any PLAYOFF/PLACEMENT). MatchdayController in Phase 58 keeps using `findBySeasonId` (matches Area 1's bridge pattern). New phase-aware controllers added in Phase 60 will switch over.

### Claude's Discretion

- Exact method signatures and return shapes (e.g., should `MatchdayGeneratorService.GeneratorFormData` have `SeasonPhase phase` only, or carry both `Season season` and `SeasonPhase phase` for template compat) — planner picks the minimum-churn shape that lets Phase-60 UI be a clean swap.
- Whether `aggregateAcrossPhases` is a `default` method on the service interface (Java interface default) vs. a normal public method on the `@Service`-annotated class — both work; planner picks the more idiomatic option for Spring.
- Detection of "matchday/playoff/phase-team is non-empty" inside the `Season.delete` guard — fastest is `boolean exists`-style query on the bridging tables; planner finalizes.
- Order in which the six services are migrated within the phase (probably SeasonPhaseService first, then StandingsService, then everything else; but `PlayoffService.createPlayoff` auto-creating the phase couples it to SeasonPhaseService).
- Whether `findByRaceMatchdayPhaseId` is keyed via `Race.matchday.phase.id` (4-step JPA name parse) or via an explicit `@Query` — Spring Data should resolve the magic name; if it doesn't, fall back to `@Query`.
- Test-method counts per service — planner inflates D-12's "Mockito-only" decision into concrete given/when/then methods per success criterion.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Foundation & requirements (read before planning)
- `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` §"Service-Layer" / §"Schluessel-Entscheidungen" — six-step migration plan; foundation document for the entire v1.9 milestone.
- `.planning/REQUIREMENTS.md` §SVC-01..SVC-05 — locked requirements scoped to this phase.
- `.planning/ROADMAP.md` §"Phase 58: Service Layer" — Goal, success criteria, dependency boundary.
- `.planning/STATE.md` §"Key Technical Context" — critical files (~25-30 modify, ~12-15 create), v1.9 phase split rationale.
- `.planning/phases/56-model-schema-foundation/56-CONTEXT.md` §D-01 (parallel-additive entities) §D-04..D-05 (enums) §D-06 (no custom finders in 56 — defer to 58) — pre-conditions Phase 58 builds on.
- `.planning/phases/57-data-migration/57-CONTEXT.md` §D-08 (PLAYOFF format='LEAGUE' DB-default) §D-09 (`playoff.phase_id` populated) §D-11 (PhaseTeam derivation) — data-state assumptions Phase 58 inherits.
- `.planning/phases/57-data-migration/57-VERIFICATION.md` (if produced) — confirms the data state Phase 58 starts from.

### Project conventions (binding)
- `CLAUDE.md` §"Architectural Principles" — Keep Controllers Thin, DTOs instead of Entities in Controllers, No Fallback Calculations, RaceLineup is Source of Truth.
- `CLAUDE.md` §"Constraints" — 82% line coverage minimum, OSIV stays enabled (use `@EntityGraph` for optimization), no breaking endpoint changes.
- `CLAUDE.md` §"Development Approach" — TDD: write tests first; given/when/then naming.
- `.planning/codebase/ARCHITECTURE.md` — three-tier MVC, Controller→Service→Repository contract, scoring-system overview, RaceLineup as source of truth, season-format catalog.
- `.planning/codebase/CONVENTIONS.md` — service naming patterns, `@Service` + `@RequiredArgsConstructor` + `@Slf4j`, BusinessRuleException for business rule violations, EntityNotFoundException for missing rows.
- `.planning/codebase/STACK.md` — Spring Boot 4.0.5, Spring Data JPA, JaCoCo 82% gate, H2 + MariaDB compatibility.
- `.planning/codebase/STRUCTURE.md` — `org.ctc.domain.service` for new services, test mirror paths.
- `.planning/codebase/TESTING.md` — Mockito-first pattern for service tests, `@DataJpaTest` for repository IT-tests.

### Existing code (read for pattern alignment)
- `src/main/java/org/ctc/domain/service/StandingsService.java` — current implementation; `TeamStanding` inner class; tiebreaker chain; alltime aggregation pattern.
- `src/main/java/org/ctc/domain/service/PlayoffService.java` — current implementation including M:N read-paths via `playoff.getSeasons()` (preserved as @Deprecated bridges).
- `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` — current `autoSeedBracket` flow; integration with PlayoffMatchup ordering.
- `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java` — current `GeneratorFormData` record; round-robin algorithm.
- `src/main/java/org/ctc/domain/service/SwissPairingService.java` — current Swiss-round generation; bye logic.
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — current per-season ranking; alltime-rankings; team-attribution via `SeasonDriver`.
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — current `save(form, scoringIds)` and `delete(id)`; site of D-18 guard and D-25 sync-block.
- `src/main/java/org/ctc/domain/service/MatchdayService.java` — current `findBySeasonId`-shaped finders; bridging surface.
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — caller surface that must keep working unchanged in Phase 58 via @Deprecated overloads.
- `src/main/java/org/ctc/admin/controller/StandingsController.java` — caller surface; calls both `calculateStandings` and `calculateStandingsWithBuchholz`.
- `src/main/java/org/ctc/admin/controller/SeasonController.java` — caller surface for `MatchdayGeneratorService` and `SwissPairingService`.
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` (and helpers) — D-23 target; iterates seasons and produces standings/rankings/playoffs HTML.
- `src/main/java/org/ctc/domain/model/SeasonPhase.java` / `SeasonPhaseGroup.java` / `PhaseTeam.java` — Phase 56 entities; field set Phase 58 services read/write.
- `src/main/java/org/ctc/domain/model/RaceLineup.java` — D-10 fallback target.

### Spring Boot 4 / JPA references
- Spring Data JPA query-method derivation reference — D-22 magic-naming validation.
- Spring `@EntityGraph` autoconfiguration — D-24 EntityGraph attribute path semantics.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`SeasonPhaseRepository`**, **`SeasonPhaseGroupRepository`**, **`PhaseTeamRepository`** — created in Phase 56 with default `JpaRepository` CRUD. Phase 58 extends them with the custom finders listed in D-22.
- **`TeamStanding`** inner class on `StandingsService` — extended in Phase 58 with the nullable `group` field (D-05).
- **`@Slf4j` + `@RequiredArgsConstructor` + `@Service`** Spring stereotype combo — applied to the new `SeasonPhaseService` exactly like every other domain service.
- **`DriverRanking` record/class on `DriverRankingService`** — kept as the per-driver result shape; `aggregateAcrossPhases` returns the same `List<DriverRanking>`.
- **`BusinessRuleException`** — used by D-14 (duplicate phase type), D-18 (delete guard), D-19 (duplicate playoff phase). `EntityNotFoundException` used by D-02 (missing REGULAR phase).
- **`@Transactional(readOnly = true)`** on standings/ranking reads — pattern reused.
- **Existing `findByRacePlayoffMatchupIsNull` filter** — informs the choice of new finders for per-phase REGULAR vs PLAYOFF race-result selection.

### Established Patterns
- **`@Service` + constructor-injection via `final`** — all new services follow.
- **`@Transactional` boundary at service method level** — read methods `(readOnly=true)`, write methods full transaction.
- **`record` for service-return data structures** — `GeneratorFormData`, `TeamStanding`, `DriverRanking` are precedents for any new return DTO needed (e.g., a `SeasonPhaseSummary` if the planner introduces one).
- **Bridge pattern: `@Deprecated` + Javadoc** — well-established (e.g., `PlayoffService.findRoundById` already exists alongside more recent variants).
- **Bidirectional FK navigation via JPA** — `Race.matchday.phase`, `Race.playoffMatchup.round.playoff.phase` — confirmed via Phase 56 D-01 entity additions; safe for D-22 magic-naming queries.

### Integration Points
- **`SeasonManagementService.save` and `delete`** — D-25 (auto-sync) and D-18 (delete guard) are the two new behavior changes there.
- **`PlayoffService.createPlayoff`** — D-19 makes it call `SeasonPhaseService.create` internally; cross-service dependency added.
- **`SiteGeneratorService`** — D-23 brings it along; expect 5-10 new call sites where `seasonId` becomes `phaseId`.
- **`StandingsController.viewStandings`** — already supports a `season` query param; in Phase 58 it stays on `seasonId` but routes through the seasonId-overload of `calculateStandings`.
- **`MatchdayController` and `PlayoffController`** — keep their `seasonId` query params and form-binding; no Phase-58-side changes (Phase 60 cuts over).
- **`TestDataService`, `DevDataSeeder`, `DemoDataSeeder`** — explicitly NOT touched. Phase 59 rebuilds them. Tests for Phase 58 use `PhaseTestFixtures` (D-11) instead.

</code_context>

<specifics>
## Specific Ideas

- The `seasonId`-overload bridge is **a 1-line delegate** in every service — not duplicated logic. Pattern: `public X foo(UUID seasonId) { return foo(seasonPhaseService.findRegularPhase(seasonId).getId()); }`.
- D-07's behavior change (PLAYOFF results now flow into season-wide driver ranking) MAY affect existing alltime-ranking numbers for current data. The planner should add a quick pre-execution sanity test: count race-result inclusion before/after, document the delta in the SUMMARY for visibility.
- D-09 keeps `calculateAlltimeRanking()` "structurally unchanged" — meaning its public API doesn't change. Internally it now goes through `aggregateAcrossPhases` per-season, but callers (SiteGenerator) don't see the difference.
- D-16 / D-17 require the new finders D-22's `RaceResultRepository.findByRaceMatchdayPhaseId(UUID)` and the per-group `MatchdayRepository.findByPhaseIdAndGroupId(UUID, UUID)` (or whatever the planner names them) — phase 58 ships those with the services that consume them.
- D-19's auto-creation of PLAYOFF phase means `playoffService.createPlayoff` writes to TWO entities (SeasonPhase + Playoff) in one transaction. Atomicity matters; both should be in one `@Transactional` boundary.
- D-25's auto-sync block on `SeasonManagementService.save` is the linchpin: after Phase 58 lands, both Season and REGULAR-phase rows hold the same scoring/format/dates. Phase 60 removes the legacy write; Phase 61 drops the legacy columns.

</specifics>

<deferred>
## Deferred Ideas

- **Per-group playoff brackets** (`PLAYOFF-FUT-01`) — model already supports it via `PlayoffPhase` per group, but UI default stays a single shared bracket. Future milestone.
- **`MatchdayController` / `StandingsController` / `PlayoffController` cutover to phase-aware URL params** — Phase 60 (UI-02..UI-07).
- **`SeasonForm` slimming** — Phase 60 (UI-01). Keeps current shape with auto-sync hack in Phase 58.
- **`TestDataService` rebuild on the new model** — Phase 59 (DATA-01). `PhaseTestFixtures` is the Phase-58-local stand-in.
- **`DevDataSeeder` rebuild with GROUPS-example season + playoff** — Phase 59 (DATA-02).
- **Driver-import refactor (`findByYearAndNumber`, group resolution)** — Phase 59 (IMPORT-01..04).
- **Drop of M:N `playoff_seasons` join table** + removal of `Playoff.seasons` collection — Phase 61 (MIGR-06). Phase 58 keeps `addSeasonToPlayoff` / `removeSeasonFromPlayoff` functional with @Deprecated.
- **Drop of legacy Season columns** (`format`, `totalRounds`, …) — Phase 61 (MIGR-06). Phase 58 keeps writing to them via D-25's auto-sync.
- **E2E test for full GROUPS-season workflow** (creation, roster per group, matchday-per-group, driver-import resolution, standings per group + combined) — Phase 61 (QUAL-02).
- **Cross-phase audit for stale `seasonId`-callers** — Phase 60 / 61 grep `@Deprecated` to find all remaining cleanup sites.
- **`PLACEMENT`-phase concrete behavior** — only stub support in Phase 58 (D-07 includes PLACEMENT in aggregation; D-14 enforces "max 1"). Real PLACEMENT-flow design deferred to a future milestone.

</deferred>

---

*Phase: 58-service-layer*
*Context gathered: 2026-04-27*
