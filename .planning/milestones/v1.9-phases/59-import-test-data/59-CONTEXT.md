# Phase 59: Import & Test Data - Context

**Gathered:** 2026-04-28
**Status:** Ready for planning

<domain>
## Phase Boundary

The driver-sheet importer is rebuilt to operate on the new Phase / Group model from Phase 56-58, and `TestDataService` + `DevDataSeeder` are rebuilt so all dev/test data exercises the new structure from the start. Concretely:

- **`SeasonRepository.findByYearAndNumber(int, int)`** stays a `List<Season>` returner; a thin Service-Layer wrapper (`SeasonManagementService.findUnique(year, number)` or equivalent) caps the contract to "exactly one" via `BusinessRuleException` on `>1`.
- **`DriverSheetImportService.preview(...)`** accepts both legacy `^\d{4}$` and the new `^\d{4}_S\d+$` tab-name patterns; tabs are resolved through the year+number tuple.
- **Group membership** of every previewed driver row is resolved purely via `Team → PhaseTeam(REGULAR-phase) → group_id` — no fallback, no SeasonDriver inference, no RaceLineup heuristics at preview time.
- **`TabPreview`** carries a new `List<TabWarning>` for tab-level warnings (e.g., team has no PhaseTeam in target REGULAR phase) and every driver-row record is extended with `String resolvedGroupName` for UI display.
- **Execute path** writes only `SeasonDriver` (status quo); it does NOT create or mutate `PhaseTeam` rows. Roster management remains the responsibility of Phase 60 admin UI.
- **`TestDataService`** consolidates the legacy `2023 Group A` + `2023 Group B` workaround into ONE season `(year=2023, number=1)` with a GROUPS-layout REGULAR phase, two `SeasonPhaseGroup`s (`Group A`, `Group B`), and 12 `PhaseTeam` rows. All other seeded seasons keep their layouts (`2024 SWISS`, `2026 LEAGUE`).
- **`DevDataSeeder`** stays a thin wrapper around `TestDataService.seed()` — the GROUPS-showcase requirement (DATA-02) is satisfied by the consolidated 2023 season created in TestDataService.

**Explicitly out of scope for Phase 59** (locked by ROADMAP / STATE / REQUIREMENTS Out-of-Scope table):

- Driver-import preview-template redesign (Group column, warning badge, manual season-selection dropdown for ambiguous tabs) — Phase 60 (UI-06). Phase 59 ships only the backend data contract.
- Admin UI for `SeasonPhase` / `SeasonPhaseGroup` / `PhaseTeam` CRUD — Phase 60 (UI-02..UI-05).
- Admin UI for "consolidate two legacy seasons into one GROUPS season" — `CONSOL-FUT-01`, future milestone.
- Drop of legacy `Season` columns and bridge FKs — Phase 61 (MIGR-06).
- DB-level `UNIQUE (year, number)` constraint on `seasons` — explicitly NOT added; bestand may have duplicates from old group-workaround pattern (e.g., legacy prod `2023 Group A` + `2023 Group B`).
- Phase-Override column in driver-import sheet (`IMPORT-FUT-01`) — future milestone.
- Playwright E2E for the importer — deferred (Sheets API mocking through Playwright is fragile, mirrors v1.8 deferral).
- Real Google Sheets API in CI — `GoogleSheetsService` stays mocked in tests.

</domain>

<decisions>
## Implementation Decisions

### Tab resolution & season disambiguation (D-01..D-04)

- **D-01: Two tab patterns supported.** `DriverSheetImportService` accepts `^\d{4}$` (legacy, e.g., `2025`) AND `^\d{4}_S\d+$` (new, e.g., `2025_S2`). Replace the existing `YEAR_TAB_PATTERN` constant with the union; parsing extracts `year` from group 1 and `number` from group 2 if present, defaulting `number` to `null` when only the legacy form is matched. Other separators (`-`, alternate orderings) are NOT accepted.

- **D-02: `findByYearAndNumber` repo method stays `List<Season>`.** No DB-UNIQUE constraint added (D-17). Service-Layer wrapper `SeasonManagementService.findUnique(int year, int number)` (or static helper) returns `Optional<Season>`:
  - 0 hits → `Optional.empty()`
  - 1 hit → `Optional.of(...)`
  - >1 hit → throws `BusinessRuleException("Multiple seasons exist for ({year}, {number}) — consolidate them first or rename sheet tab to disambiguate")`.
  Importer calls the wrapper, catches the exception in `buildTabPreview`, and surfaces it as `ambiguousReason`.

- **D-03: Ambiguous tab `2025` (no `_S2`) with multiple seasons in the year.** When the legacy pattern matches and `seasonRepository.findByYear(year).size() > 1`, the importer keeps today's behavior: `suggestedSeasonId = null`, `ambiguousReason = "Multiple seasons for year {year}"`. Phase 60 UI will provide a manual dropdown for selection. Phase 59 ships only the backend data contract — no UI dropdown work in this phase.

- **D-04: Single-season `2025` tab → auto-resolution.** When `findByYear(year).size() == 1`, set `suggestedSeasonId` to that season's id (status quo for IMPORT-01 backwards-compat). User does not need to rename pre-existing single-season sheets.

### Group resolution & warning surface (D-05..D-08)

- **D-05: Pure `Team → PhaseTeam(REGULAR) → group_id` lookup.** For every previewed driver row with a resolved Season (`suggestedSeasonId != null`), the importer:
  1. Resolves the REGULAR phase via `SeasonPhaseService.findRegularPhase(seasonId)` (Phase 58 D-02).
  2. Looks up `phaseTeamRepository.findByPhaseIdAndTeamId(regularPhaseId, teamId)` for the team referenced in the sheet row.
  3. If `PhaseTeam` exists with non-null `group_id`, populates `resolvedGroupName` from the linked `SeasonPhaseGroup.name`.
  4. If `PhaseTeam` is missing OR `group_id` is null, `resolvedGroupName` is null.
  No RaceLineup fallback, no SeasonDriver inference. Stays consistent with "No Fallback Calculations" (CLAUDE.md).

- **D-06: `List<TabWarning>` per `TabPreview`.** New record `TabWarning(WarningType type, String teamShortName, String message)` becomes a field on `TabPreview`. `WarningType` enum: `TEAM_NOT_IN_REGULAR_PHASE`. Warnings are emitted once per distinct team (deduplicated by `teamShortName`), not per row. Future warning types can be added to the enum without breaking the API.

- **D-07: Execute path: pass-through with `group_id=NULL` when team is not in PhaseTeam.** No exception, no skip. The driver is assigned to the season via `SeasonDriver` (existing behavior). The Standings view will display the driver without a group badge. The warning shown at preview time persists into Phase 60 UI as a non-blocking flag.

- **D-08: Driver-row records gain `String resolvedGroupName`.** `NewDriverRow`, `NewAssignmentRow`, `ConflictRow`, `FuzzySuggestionRow`, `UnchangedRow` each get an additional `String resolvedGroupName` field (null when no group resolved). `ErrorRow` does NOT (errors short-circuit before group resolution). Phase 60 templates render this value; Phase 59 only ships the data field.

### TestDataService rebuild (D-09..D-12)

- **D-09: Consolidate 2023 to ONE GROUPS-layout season.** Replace today's two `Season` rows (`2023 Group A` + `2023 Group B` with identical `(year=2023, number=1)`) with a SINGLE season `(year=2023, number=1, name="Season 2023")`. The season's REGULAR phase is `layout=GROUPS` with two `SeasonPhaseGroup` rows: `Group A` (sortIndex=0) and `Group B` (sortIndex=1). Each group has 6 PhaseTeam rows mirroring today's team rosters: `Group A` = ADR/ICL/SVT/NFR/HMS/VRX-A; `Group B` = EGP/PWR/VRX-B/SGM-B/SGM-S/TBR-R. All matchday/race seed code keeps the team set, but matches are now per-group. Standings can be computed per-group AND combined (Phase 58 D-04 covers).

- **D-10: 2026 (S4) keeps LEAGUE layout. 2024 (S2) keeps SWISS layout.** Only 2023 is GROUPS-shaped — that's enough for DATA-01 / DATA-02 coverage. The active season (2026) stays LEAGUE so existing race-data and team-card seeding doesn't cascade into a GROUPS rewrite. Test-Season 2026 (T-ALF / T-BRV / T-BRV1 / T-BRV2 from `seedRaceLineups`) also stays LEAGUE — its purpose is sub-team edge-case coverage, not GROUPS.

- **D-11: `PhaseTestFixtures` is preserved.** It stays in `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` for Mockito-only service tests (Phase 58 D-12). `TestDataService` is the DB-seed source; `PhaseTestFixtures` is the in-memory entity-stub source. Both coexist — neither replaces the other.

- **D-12: `SeasonDriver` stays in the seed; `PhaseTeam` is added as a parallel write.** `TestDataService.seedSeasonDrivers()` keeps writing `SeasonDriver` rows (driver-team snapshot per season — required for `RaceLineup` source-of-truth and stand-in fallback per Phase 58 D-10). New `seedPhaseTeams()` (or inlined into `seedSeasons()`) adds the `PhaseTeam` rows for the new GROUPS-layout 2023 season AND for the LEAGUE-layout 2024/2026 seasons (each season's REGULAR phase needs PhaseTeam rows for its team roster, with `group_id=NULL` for LEAGUE phases). Phase 57's data migration already does this for legacy data; TestDataService mirrors the same logic in test seeds.

### Demo / dev data layout (D-13..D-15)

- **D-13: 2023 = 2 groups × 6 teams (mirroring today's Group A / Group B).** Group A: ADR/ICL/SVT/NFR/HMS/VRX-A. Group B: EGP/PWR/VRX-B/SGM-B/SGM-S/TBR-R. Identical to current setup, just consolidated to one season. Existing race-result seeding in `seedRoundRobinSeason(s1a, ...)` and `seedRoundRobinSeason(s1b, ...)` is rewired so `s1a` and `s1b` become Matchdays linked to ONE season + DIFFERENT `SeasonPhaseGroup` (Matchday.group_id is set per group; `Matchday.season_id` and `Matchday.phase_id` are the same across both group's matchdays).

- **D-14: PLAYOFF phases — keep the 2023 + 2024 setup.** `seedPlayoffs()` retains today's structure: 2023 Playoff Semifinal (4 teams, top-2 from each group of the consolidated 2023 GROUPS season — driven by Phase 58 D-15 PlayoffSeedingService.autoSeedBracket) + 2024 Playoff Final (2 teams). The active 2026 (S4) season does NOT get a PLAYOFF phase in the seed (race data is mid-season). The 2023 Playoff source-of-truth shifts from "two separate seasons added via `playoff.getSeasons().add(s1b)`" to "single GROUPS season with auto-seeding pulling top-2 from each group" — eliminates the legacy M:N `playoff_seasons` write.

- **D-15: No dedicated import-E2E test season.** E2E coverage of the importer reuses the consolidated 2023 GROUPS season. Sheet-tab `2023_S1` resolves to that season; the importer's group-resolution path is exercised via the existing 12-team roster. No `(year=2025, number=1)` placeholder season needed.

### Importer execute behavior — SeasonDriver vs PhaseTeam ordering (D-16)

- **D-16: PhaseTeam-lookup is preview-only; execute writes ONLY SeasonDriver.**
  - Preview phase: reads `PhaseTeam` for `resolvedGroupName` and warning emission.
  - Execute phase: writes `SeasonDriver` rows for NEW_DRIVER, NEW_ASSIGNMENT, CONFLICT (team-overwrite), FUZZY_SUGGESTION (with accept). Does NOT touch `PhaseTeam` — even if the sheet introduces a team that has no roster entry in the target REGULAR phase. Roster pflege bleibt explizit Phase 60 (UI-04). Warning persists post-execute (informational).
  - Conflict (team-Wechsel zu Team B without PhaseTeam): SeasonDriver is updated; warning stays in the preview output.

### Migration strategy & uniqueness (D-17..D-20)

- **D-17: NO V5 migration for `UNIQUE (year, number)`.** Bestand-Daten (legacy prod) may have `(year, number)`-duplicates from the old group-workaround pattern. REQUIREMENTS.md "Out of Scope" table explicitly lists "Heuristische Konsolidierung alter Group-Workaround-Saisons" — Phase 59 honors that boundary.

- **D-18: Service-Layer enforces uniqueness contract.** `findUnique(year, number)` (D-02) wraps `findByYearAndNumber` and throws `BusinessRuleException` on duplicates. The exception is caught by the importer (in `buildTabPreview`) and surfaced as `ambiguousReason`. UI (Phase 60) will offer the user the option to either rename their sheet tab to disambiguate (`2023_S1` / `2023_S2`) or wait for `CONSOL-FUT-01` (manual UI consolidation).

- **D-19: Repo signature unchanged.** `SeasonRepository.findByYearAndNumber(int year, int number)` keeps its current `List<Season>` return type. No deprecation, no overload. The Service-Layer wrapper is the only new artifact. This minimizes churn in callers (Phase 56 already added the method; Phase 58 services don't use it).

- **D-20: `CONSOL-FUT-01` documented as deferred, not built.** Manual season-consolidation UI is out of scope. Phase 59 only documents in CONTEXT.md `<deferred>` so future milestones know it's a real follow-up. No backlog ticket creation in this phase.

### Test coverage strategy (D-21..D-24)

- **D-21: Unit (Mockito) + Service-IT (`@SpringBootTest @ActiveProfiles("dev") @Transactional`); E2E deferred.** Mirrors v1.8 (`/Users/jegr/Documents/github/ctc-manager/.planning/STATE.md` "[v1.8 start]" decision). New unit tests for `SeasonManagementService.findUnique`, `DriverSheetImportService` group-resolution logic, and `TabWarning` emission. New IT tests for the full preview→execute roundtrip with the consolidated 2023 GROUPS season seeded.

- **D-22: `GoogleSheetsService` is `@MockBean`-mocked in all tests.** Tests define tab lists + row lists directly. No real Sheets API calls in CI. Same pattern as `DriverSheetImportServiceIT` from v1.8.

- **D-23: `@SpringBootTest` smoke test for `TestDataService` rebuild.** New test verifies: seed runs; consolidated 2023 season has 1 REGULAR phase with `layout=GROUPS`; that phase has 2 `SeasonPhaseGroup` rows; each group has 6 `PhaseTeam` rows; standings can be computed per-group via `StandingsService.calculateStandings(phaseId, groupId)` (Phase 58 D-05). Plus: the existing 1127-test suite (Phase 58 baseline) must remain green after the seed restructure — regression coverage by virtue of running.

- **D-24: 82% line-coverage gate stays project-wide.** No special bar for the importer or seeder. Wave-of-coverage tests add to the existing baseline (1127 tests, 86.78% as of Phase 58). New Phase 59 code (Service-Wrapper, Importer-additions, TestDataService rewrite) must individually meet the gate.

### Implementation sequence (D-25..D-28)

- **D-25: Wave-1 = Service-Wrapper. Wave-2 = Importer + TestData (parallel). Wave-3 = E2E + Verification.**
  - Wave 1 (foundation): `SeasonManagementService.findUnique` + `BusinessRuleException` translation. Smallest change; everyone downstream depends on it.
  - Wave 2a (Importer): Tab-pattern extension, group-resolution logic in `buildTabPreview`, `TabWarning` record, `resolvedGroupName` on row records.
  - Wave 2b (TestData): consolidated 2023 season, PhaseTeam seeding, Playoff rewiring.
  - Wave 3: cross-cutting tests (preview→execute IT with consolidated season), regression-test sweep, JaCoCo verify.

- **D-26: 3-4 separate plans with clean responsibilities.** Suggested split (planner finalizes):
  - **Plan 59-01:** SeasonRepository service-wrapper + `findUnique` + `BusinessRuleException` integration.
  - **Plan 59-02:** `DriverSheetImportService` tab-pattern + group-resolution + warnings + row-record extension.
  - **Plan 59-03:** `TestDataService` 2023-consolidation + PhaseTeam seeding + Playoff seeding rewire.
  - **Plan 59-04** (optional / can be merged into 59-02): preview→execute IT covering IMPORT-03 SC3 end-to-end.

- **D-27: PhaseTeam seeding in `TestDataService` is direct entity construction.** No `seasonPhaseService.create(...)` call from the seed (avoids zirkuläre Test-/Service-Abhängigkeit). `TestDataService` builds the `SeasonPhase`, `SeasonPhaseGroup`, and `PhaseTeam` entity graph manually (Lombok-style cascade-save via `Season.phases` collection — Phase 56 D-01 added `Season.phases` with `cascade=ALL`). Self-contained.

- **D-28: Group-resolution logic lives in `DriverSheetImportService` (private method).** `phaseTeamRepository` and `seasonPhaseService` (for `findRegularPhase`) are added to the service's constructor. No new `GroupResolutionService` — the resolution is local to the import flow. `SeasonPhaseService.findRegularPhase` (Phase 58 D-02) is the single shared building block.

### Claude's Discretion

- Exact name of the service-wrapper method (`findUnique`, `findOneByYearAndNumber`, `findUniqueByYearAndNumber`) — planner picks the most idiomatic for the codebase. Locking signature: `Optional<Season>`-returning + `BusinessRuleException` on multi-hit.
- Whether `TabWarning.message` is rendered server-side (English String) or kept as a structured token for UI-side i18n — defer to planner; recommendation: English String for consistency with existing exception messages.
- Whether `seedPhaseTeams()` is a new method on `TestDataService` or inlined into the existing `seedSeasons()` — planner picks based on resulting method size; both acceptable.
- Whether the PhaseTeam-and-SeasonDriver write order during execute matters (race conditions on save) — almost certainly NOT (single transaction), planner only revisits if a concrete issue surfaces.
- Tests for the "ambiguous bestand-DB duplicate" case (`findByYearAndNumber` returns 2): planner adds at minimum one IT test asserting `BusinessRuleException` is thrown and surfaces correctly through `DriverSheetImportService.preview`.
- Visibility / wiring of the `findRegularPhase` call inside `buildTabPreview` (per-row vs once-per-tab) — planner picks; recommendation: once per tab, cache in a local var, since one tab = one season = one REGULAR phase.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Foundation & requirements (read before planning)
- `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` §"Driver-Import" / §"Test-/Demo-Daten" — non-negotiable design source for the import behavior and the new test-data layout.
- `.planning/REQUIREMENTS.md` §IMPORT-01..04 + §DATA-01, DATA-02 — locked requirements scoped to this phase. §"Out of Scope" table — `UNIQUE (year, number)` constraint and heuristic season-consolidation are explicitly excluded.
- `.planning/ROADMAP.md` §"Phase 59: Import & Test Data" — Goal, success criteria, dependency boundary (Depends on Phase 58).
- `.planning/STATE.md` §"Key Technical Context" — driver-import fix (`findByYearAndNumber` replaces `findByYear`), tab pattern extension to `^\d{4}_S\d+$`, group membership resolved via `PhaseTeam` of REGULAR phase.
- `.planning/PROJECT.md` §"Current Milestone: v1.9 Season Phases & Groups" — milestone goal alignment.
- `.planning/phases/56-model-schema-foundation/56-CONTEXT.md` §D-03 (UNIQUE on phase_teams) §D-05 (PhaseLayout enum) — schema invariants Phase 59 builds on.
- `.planning/phases/57-data-migration/57-CONTEXT.md` §D-11 (`phase_teams` derived 1:1 from `season_teams`) — bestand-data shape.
- `.planning/phases/58-service-layer/58-CONTEXT.md` §D-02 (centralized `findRegularPhase`) §D-04..D-06 (combined-view standings) §D-10 (RaceLineup-Fallback for stand-ins) §D-11 (`PhaseTestFixtures` Mockito helper) §D-15 (`autoSeedBracket` from REGULAR-phase standings) §D-19 (auto-create PLAYOFF phase) §D-20 (PhaseTeam roster init: REGULAR auto-derived from SeasonTeam, GROUPS empty) — pre-conditions Phase 59 inherits.

### Project conventions (binding)
- `CLAUDE.md` §"Architectural Principles" — Keep Controllers Thin, DTOs instead of Entities in Controllers, **No Fallback Calculations** (D-05 / D-07 honor this), **RaceLineup is Source of Truth** (D-12 keeps SeasonDriver writes), **Isolate Test Data Completely** (Test-Prefix convention, e.g., `T-ALF`, stays).
- `CLAUDE.md` §"Constraints" — 82% line coverage minimum (D-24), Flyway V1+V2+V3+V4 immutable (D-17 = no new migration), H2 + MariaDB compatibility, no breaking endpoint changes.
- `CLAUDE.md` §"Development Approach" — TDD: write tests first, given/when/then naming.
- `.planning/codebase/ARCHITECTURE.md` — three-tier MVC, Controller→Service→Repository.
- `.planning/codebase/CONVENTIONS.md` — service naming, `@Service` + `@RequiredArgsConstructor` + `@Slf4j`, `BusinessRuleException` for business-rule violations (D-02 / D-18), `EntityNotFoundException` for missing rows.
- `.planning/codebase/TESTING.md` — Mockito-first for service tests; `@SpringBootTest @ActiveProfiles("dev") @Transactional` for IT (Phase 58 D-13 override).

### Existing code (read for pattern alignment)
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — current implementation; `YEAR_TAB_PATTERN`, `buildTabPreview`, all 6 row types and the public records ARE the surface to extend. Constructor injection adds `SeasonPhaseService` + `PhaseTeamRepository`.
- `src/main/java/org/ctc/dataimport/DriverMatchingService.java` — used by `DriverSheetImportService` for fuzzy / exact / none matching; unchanged in Phase 59.
- `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` — external-API wrapper; mocked in all Phase 59 tests (D-22).
- `src/main/java/org/ctc/domain/repository/SeasonRepository.java` — already has `findByYearAndNumber(int, int): List<Season>` (line 19); D-19 keeps it. Phase 59 adds the service-layer wrapper, NOT a new repo method.
- `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` — Phase 56 entity, Phase 58 D-22 added `findByPhaseIdAndTeamId(UUID, UUID)`; D-05 calls it.
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — site of the new `findUnique` wrapper (or sibling helper).
- `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` — Phase 58 D-02 added `findRegularPhase(UUID seasonId)`; D-05 calls it once per tab.
- `src/main/java/org/ctc/admin/TestDataService.java` — site of the 2023-consolidation rewrite + `seedPhaseTeams()` addition (D-09, D-12, D-13, D-14).
- `src/main/java/org/ctc/admin/DevDataSeeder.java` — thin wrapper; no behavior change in Phase 59 beyond what `TestDataService.seed()` produces.
- `src/main/java/org/ctc/admin/DemoDataSeeder.java` — independent (GT7 cars/tracks); not touched in Phase 59.
- `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` — Phase 58 D-11 helper; D-11 keeps it for Mockito service tests.
- `src/main/java/org/ctc/domain/exception/BusinessRuleException.java` — used by D-02 / D-18.
- `src/main/java/org/ctc/domain/model/SeasonPhase.java` / `SeasonPhaseGroup.java` / `PhaseTeam.java` — entity graph for the seed (D-09, D-13).

### Spring Boot 4 / JPA references
- Spring Data JPA `Optional<T>` query methods on multi-hit → `IncorrectResultSizeDataAccessException` (the exception we explicitly route around in D-19 by keeping `List<Season>` and wrapping at the service layer).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`DriverSheetImportService.YEAR_TAB_PATTERN`** (line 33): single regex constant; D-01 swaps it for the union pattern with two named groups.
- **`DriverSheetImportService.TabPreview` record** (line 345): adds `List<TabWarning> warnings` field; consumers (controller, template) update accordingly.
- **`DriverSheetImportService.NewDriverRow` / `NewAssignmentRow` / `ConflictRow` / `FuzzySuggestionRow` / `UnchangedRow` records**: each gets one new `String resolvedGroupName` field (D-08). `ErrorRow` does NOT.
- **`SeasonRepository.findByYearAndNumber(int, int)`**: already returns `List<Season>` with `@EntityGraph(attributePaths = {"raceScoring", "matchScoring"})`; Phase 59 reuses as-is via the service wrapper.
- **`PhaseTeamRepository.findByPhaseIdAndTeamId(UUID, UUID)`** (Phase 58 D-22): single-call lookup for D-05.
- **`SeasonPhaseService.findRegularPhase(UUID seasonId)`** (Phase 58 D-02): cached per-tab in `buildTabPreview`.
- **`PhaseTestFixtures`** (Phase 58 D-11): `createGroupsPhase(Season, int groupCount)` already exists — reuse pattern when seeding the 2023 consolidated season.
- **`Season.phases` collection** (Phase 56 D-01): cascade-save via `seasonRepository.save(season)` after attaching new `SeasonPhase` entities — used by `TestDataService` D-13.
- **`@Slf4j @Service @RequiredArgsConstructor`** Spring stereotype combo: every new wrapper method lives in `SeasonManagementService` which already has it.
- **`BusinessRuleException`**: used by D-02 / D-18.
- **`@Transactional(readOnly = true)`** on `preview()` (line 52): unchanged. `execute()` keeps `@Transactional` (full).

### Established Patterns

- **Service-Layer wrapping over raw Repo signatures** — Phase 58 D-01 / D-09 set the precedent; D-02 / D-18 mirror it.
- **`record` for service-return data structures** — `TabPreview`, `TabWarning` (new), all six row records: continue the precedent.
- **`@MockBean` in IT tests** — Phase 54/55 IT tests for the importer mock `GoogleSheetsService`; D-22 keeps the pattern.
- **`@SpringBootTest @ActiveProfiles("dev") @Transactional`** — Phase 58 D-13 override; Phase 59 IT tests follow.
- **Test-data isolation via T-prefix** — CLAUDE.md "Isolate Test Data Completely"; D-10 keeps `T-ALF`/`T-BRV*` test-season teams.
- **Single-transaction execute** — `DriverSheetImportService.execute()` is `@Transactional`; SeasonDriver writes are atomic. Phase 59 adds no cross-service writes (D-16: PhaseTeam not touched).
- **`PhaseTeamRepository.findByPhaseIdAndTeamId`** uses Spring Data magic naming (Phase 58 D-22) — D-05 stays in that pattern.

### Integration Points

- **`DriverSheetImportController`** (in `src/main/java/org/ctc/admin/controller/`): consumer of `TabPreview`. Phase 59 modifies the DTO; Phase 60 (UI-06) updates the template to render `warnings` and `resolvedGroupName`. Phase 59 may need a no-op controller change to recompile (or a constructor-tweak passes through cleanly).
- **`SeasonManagementService`**: site of the new `findUnique` wrapper. Constructor stays unchanged — adds one method.
- **`TestDataService`**: massive rewrite of `seedSeasons` for the 2023 GROUPS-consolidation. `seedSeasonDrivers`, `seedMatchdaysAndResults`, `seedPlayoffs` are all touched (matchdays per group, playoff source-of-truth via consolidated season). Existing 1127-test suite (Phase 58 baseline) must continue to pass after seed restructure.
- **`DriverSheetImportServiceIT`** (existing IT, v1.8): extended to cover new `2025_S2` tab pattern + `BusinessRuleException`-on-duplicate path + group resolution + warning emission.
- **`SiteGeneratorService`** (Phase 58 D-23 brought it onto phase APIs): not touched in Phase 59. Already group-aware via Phase 58.
- **Existing race-result + standings flow** (Phase 58 SVC-02): standings are computed per-phase / per-group; Phase 59's TestDataService rewire MUST produce the same standings totals as before for the 2023 season (regression invariant — D-23 implicitly tests this via existing test-suite green-ness).

</code_context>

<specifics>
## Specific Ideas

- D-01's tab-pattern union should be a single compiled `Pattern` with two named groups (`year`, `seasonNum`) — avoids running two regex passes per tab name. Recommendation: `^(\d{4})(?:_S(\d+))?$`.
- D-02 wrapper convention: when called with a tab that uses the legacy `^\d{4}$` pattern, `number` is null → wrapper falls back to `findByYear(year)` (existing repo method) instead of `findByYearAndNumber`. The `BusinessRuleException` path applies to both branches when multiple seasons exist.
- D-05's `findByPhaseIdAndTeamId` is called once per row in `buildTabPreview`. With 100+ rows per tab, that's 100+ DB hits — acceptable for an admin-only tool but planner may opt for a one-shot `findByPhaseId(phaseId)` and an in-memory map keyed by `team_id`. Both are acceptable; planner picks based on profiling effort.
- D-09's consolidation completely removes the `playoff2023.getSeasons().add(s1b)` legacy hack from `seedPlayoffs()` — the M:N `playoff_seasons` write becomes obsolete (it's still functional via Phase 58 deprecated bridges, but the seed no longer needs it). This is a small visible cleanup of the seed.
- D-13 / D-14: the consolidated 2023 Playoff is now driven by the GROUPS-layout REGULAR phase's combined standings (Phase 58 D-04 / D-15). `PlayoffSeedingService.autoSeedBracket(playoff)` should produce the same Top-4 (winner-A, runner-up-A, winner-B, runner-up-B) — but the seed code path now goes through Phase 58's `combined-view`, not through manual `s1aSorted` / `s1bSorted` arithmetic in `seedPlayoffs()`. Significant code reduction in `seedPlayoffs()`.
- D-22's `@MockBean` on `GoogleSheetsService` means Phase 59 IT tests don't need any Sheets-API setup — the existing `googleSheetsService.getSheetNames(...)` and `readRangeFromSheet(...)` mocks pattern from v1.8 IT tests is reused verbatim.
- D-24 baseline: Phase 58 closed at 1127 tests / JaCoCo 86.78%. Phase 59 should add ~30-50 tests (importer additions ~10-15, TestDataService smoke ~5, service-wrapper ~10, regression-fixed ~5-10) and stay above the 82% gate.
- D-26 plan-split rationale: Plan 59-01 is small (~50 LoC + tests) but unblocks 59-02 and 59-03; 59-02 and 59-03 are independent of each other and can run in Wave 2 in parallel by two executors.

</specifics>

<deferred>
## Deferred Ideas

- **`UNIQUE (year, number)` DB constraint** — explicit Out-of-Scope per REQUIREMENTS.md. Would require a V5 migration with auto-renumber heuristics, which the project rejects.
- **`CONSOL-FUT-01` UI for manual season-consolidation** — future milestone. Lets admins merge two legacy `(year, number)`-duplicate seasons into a single GROUPS season after the fact.
- **`IMPORT-FUT-01` Phase/Group override column in driver-import sheet** — future milestone. For sheets where a driver only races in specific phases (e.g., only PLAYOFF).
- **Driver-import preview-template redesign** — Phase 60 (UI-06). Group column rendering, warning-badge styling, manual season-selection dropdown for ambiguous tabs are template work.
- **PLAYOFF-FUT-01 sub-group-aware playoff brackets** — future milestone. Modeled, but UI default stays a single shared bracket per PLAYOFF phase.
- **Drop of M:N `playoff_seasons` join table + `Playoff.seasons` collection** — Phase 61 (MIGR-06). Phase 59 stops *writing* to it from `TestDataService.seedPlayoffs` (D-14 cleanup), but the bridge stays functional via Phase 58 `@Deprecated` overloads.
- **Drop of legacy `Season` columns** (`format`, `totalRounds`, `legs`, ...) — Phase 61 (MIGR-06). Phase 59 keeps reading them via Phase 58 D-25 auto-sync.
- **Playwright E2E for the importer** — deferred (Sheets-mocking through Playwright is fragile; v1.8 deferral repeated here per D-21).
- **Real Google Sheets API call in CI** — quote limits + auth setup; D-22 keeps mocking.
- **Phase-level resolution caching in `buildTabPreview`** beyond the per-tab `findRegularPhase` cache — premature optimization; planner may add a per-tab `Map<UUID, PhaseTeam>` if the per-row repo call profiles too slow, but base recommendation is to keep it simple.
- **PhaseTeam writes from the importer execute path** (auto-creating roster on team-first-encounter) — explicitly rejected in D-13 / D-16. Reserved for Phase 60 admin UI.

</deferred>

---

*Phase: 59-import-test-data*
*Context gathered: 2026-04-28*
