# Phase 62: Public Site Phase + Group Awareness — Research

**Researched:** 2026-05-02
**Domain:** Spring Boot 4 sitegen + Thymeleaf SSR template rewrite (read-only generator changes, no schema impact)
**Confidence:** HIGH (codebase verified via direct read; no library version research needed — stack is internal)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

URL / file scheme:
- **D-01**: Phase-suffixed flat-file scheme: `/season/{slug}/standings-{phaseSlug}.html`, per-group `standings-{phaseSlug}-group-{groupSlug}.html`. Legacy `/season/{slug}/standings.html` stays as REGULAR-default. Same suffix pattern for `matchdays-*.html` and `driver-ranking-*.html`.
- **D-02**: `phaseSlug` = lowercased `PhaseType` name (`regular` / `playoff` / `placement`). Stable, type-based, never label-based.
- **D-03**: `groupSlug` = `slugify(group.name)` — reuse existing `SiteGeneratorService.slugify`.
- **D-04**: Legacy `/season/{slug}/standings.html` renders REGULAR-combined with phase-tab row on top. Tab row only when ≥2 phases or current phase is GROUPS. Single-REGULAR-LEAGUE seasons render byte-for-byte unchanged (SC4 invariant).

Phase / group tab UX:
- **D-05**: Tabs styled native to public site, reusing `.subnav` shape — NOT mirroring admin's `.tab-nav`/`.tab-btn` look.
- **D-06**: Phase-tab row hides when redundant (renders only when ≥2 phases). Group-sub-tab row renders only when current phase is GROUPS-layout.
- **D-07**: Tab anchors are inter-file links — each phase variant is its own static HTML file. No JS, no `:target` selectors.
- **D-08**: PLAYOFF tab links directly to existing `/season/{slug}/playoff.html`. No new `standings-playoff.html` is generated.

Phase awareness scope:
- **D-09**: Sub-nav stays coarse — `currentPage` stays as `'standings'` / `'matchdays'` / `'driver-ranking'` across all per-phase variants.
- **D-10**: matchdays.html → tabs + suffixed files (`matchdays-{phaseSlug}.html`, `matchdays-{phaseSlug}-group-{groupSlug}.html`).
- **D-11**: driver-ranking.html legacy URL stays cross-phase aggregated (no behavior change at canonical URL); per-phase variants at `driver-ranking-{phaseSlug}.html`.
- **D-12**: Per-phase URL pattern uniform across the three list pages.
- **D-13**: team-profile.html gains a "Phase Breakdown" section ONLY when ≥2 phases; otherwise byte-identical to today.
- **D-14**: team-profile standings panel = combined-view standing (`calculateStandings(REGULAR, null)`), per-group standing surfaces only via breakdown section.
- **D-15**: driver-profile.html gains per-phase results sectioning ONLY when ≥2 phases; otherwise byte-identical to today.

Cross-link strategy:
- **D-16**: All standings/matchdays/driver-ranking variants link to single `team-profile.html` / `driver-profile.html` — never per-phase URL fork.
- **D-17**: Combined-View is default landing inside a GROUPS-layout phase (mirrors Phase 60 D-30).

Alltime + behavior change:
- **D-18**: Alltime pages still aggregate cross-season; data source changes from REGULAR-only to all-phases per season.
- **D-19**: TRACKED BEHAVIOR CHANGE — alltime numbers will recompute and may shift visibly for any historical season with PLAYOFF / PLACEMENT phase. Must be in PR description + release notes (mirror Phase 61 D-23 pattern).

SiteGeneratorService refactoring:
- **D-20**: Extract per-page generators into `org.ctc.sitegen.{StandingsPageGenerator, MatchdaysPageGenerator, DriverRankingPageGenerator, TeamProfilePageGenerator, DriverProfilePageGenerator}` Spring beans. `SiteGeneratorService` becomes orchestrator.
- **D-21**: PLAYOFF-bracket page generation stays inside `SiteGeneratorService` (or its own helper at planner discretion).

Empty-state + edge cases:
- **D-22**: Empty-state per phase = render with 0-point roster + banner. Mirror Phase 60 D-36.
- **D-23**: PLAYOFF-phase tab visible even before bracket exists; links to `playoff.html` which already handles empty bracket gracefully.

Test strategy:
- **D-24**: SC5 regression test = Surefire integration test (`@SpringBootTest` with H2 + Jsoup), NOT Playwright E2E.
- **D-25**: Reuse Phase 59 D-09 GROUPS-2023 fixture from `TestDataService`; extend if multi-phase coverage needs it.
- **D-26**: Visual + a11y verification scope = all 4 phase-aware page types (standings, matchdays, driver-ranking, team-profile, driver-profile) Desktop + Mobile via `playwright-cli`. Tab nav semantics: `role="tablist"`, `role="tab"`, `aria-selected`, `aria-controls`. Native Tab+Enter only — no JS arrow-key handler.

Plan structure:
- **D-27**: Sequential plans on `gsd/v1.9-season-phases-groups` branch, no worktrees (mirrors Phase 60/61).
- **D-28**: Rough decomposition — (a) helper-class extraction refactor, (b) phase-aware standings, (c) matchdays, (d) driver-ranking, (e) team/driver profiles, (f) alltime cross-phase, (g) SC5 regression test, (h) playwright-cli visual + a11y.
- **D-29**: Tracked Behavior Changes explicit in PR + release notes.

### Claude's Discretion

- Exact CSS class names for tab rows (e.g. `.phase-tab-row`, `.tab-row-secondary`, `.tab-active`) — UI-SPEC declares these as new additions to `static/site/css/style.css`.
- Helper-class boundaries in D-20 — whether `MatchdayPageGenerator` and `MatchdayIndexPageGenerator` are one class or two.
- Active-tab visual treatment (underline vs. background-fill vs. bold-only) — UI-SPEC pre-locks "reuse `.subnav-link.active` exactly" → accent color + `rgba(79,195,247,0.1)` background tint.
- Empty-phase-banner exact wording — UI-SPEC pre-locks "No results recorded yet." + body "Standings will appear once race results are recorded."
- Whether `generatePlayoffBracket` extracts to its own helper class.
- Number of plan files (D-28 rough decomposition is ~6–8) — Planner finalizes after reading actual file scope.
- Sequence within each plan — TDD by default; D-20 helper-extraction may be refactor-only with green tests.
- Whether `slugify` moves to a `SiteSlugger` utility class (minor cleanup).

### Deferred Ideas (OUT OF SCOPE)

- Per-phase team-profile / driver-profile URL variants (e.g. `team-profile-regular.html`).
- Sub-nav extension with per-phase awareness (sub-nav stays coarse).
- JS-driven tab toggle / `:target` selectors / arrow-key keyboard nav.
- `standings-playoff.html` for PLAYOFF-phase final standings table (PLAYOFF tab links directly to `playoff.html`).
- Phase-aware breadcrumbs.
- Per-group team-profile (`team-profile-regular-group-a.html`).
- Mobile-Dropdown navigation as alternative to horizontal-scroll tabs.
- Active-phase visualization based on `phase.startDate`/`endDate`.
- Automated Playwright E2E for SC5.
- Cross-season phase-aware navigation; per-phase RSS / sitemap entries.
- Bracket layout improvements (`PLAYOFF-FUT-01`); season consolidation UI (`CONSOL-FUT-01`); phase/group override column in driver-import sheet (`IMPORT-FUT-01`).
- Worktree parallelization for plan-tasks (sequential per D-27).
</user_constraints>

<phase_requirements>
## Phase Requirements

The orchestrator did not provide explicit `REQ-IDs` for Phase 62 — `.planning/REQUIREMENTS.md` Phase 62 row is `**Requirements**: TBD (to be derived during /gsd-discuss-phase from REQUIREMENTS.md UI-* + the gap below)`. CONTEXT.md substitutes the requirement set via D-01..D-29 plus the five ROADMAP success criteria SC1..SC5. Mapping the canonical UI-* requirements to Phase 62's public-site mirror:

| Source | Description | Phase 62 Counterpart | Research Support |
|--------|-------------|-----------------------|------------------|
| UI-02 | Saison-Detail mit Phasen-Tabs (admin) | Phase tab row on public site (D-04..D-08) | `templates/admin/season-detail.html` Z. 266-290 documents the shape; UI-SPEC re-styles to `.subnav` |
| UI-05 | Standings-UI mit Phase-/Group-Auswahl + Combined-View (admin) | Phase-aware public standings + Combined/Group sub-tabs (D-04..D-17) | `templates/admin/standings.html` Z. 75-97 already encodes the `showGroupColumn`/`showBuchholz` flag pattern Phase 62 mirrors |
| UI-07 | Playoff-UI auf PLAYOFF-Phase umgestellt (admin) | PLAYOFF tab on public site links to existing `playoff.html` (D-08, D-23) | Backend already phase-aware via `playoff.getPhase()` since Phase 58/60 |
| QUAL-02 | E2E-Test deckt GROUPS-Saison (admin) | Surefire IT for ≥1 GROUPS season + ≥1 multi-phase season (D-24, D-25, SC5) | `SiteGeneratorE2ETest` + `SiteGeneratorServiceTest` are pattern sources |
| QUAL-01 | JaCoCo Line-Coverage ≥ 82 % | Helper-class extraction (D-20) must keep coverage at threshold | New helper-class unit tests + sitegen IT contribute |
| ROADMAP SC1 | GROUPS season renders per-group + combined view | D-04, D-17, D-32, D-33 carry-over | Per-group + combined-view URL scheme + `showGroupColumn`/`showBuchholz` flags |
| ROADMAP SC2 | Multi-phase season renders one tab per phase, switching swaps standings | D-04, D-06, D-07 | Inter-file anchor links; phase-suffixed files |
| ROADMAP SC3 | PLAYOFF-phase tab reaches bracket / final standings | D-08, D-23 | PLAYOFF tab → existing `/season/{slug}/playoff.html` |
| ROADMAP SC4 | Single-REGULAR-LEAGUE seasons render identically | D-04, D-06, D-13, D-15 | Server-side flag `showPhaseTabs=false` → no tab row in HTML |
| ROADMAP SC5 | Regression test ≥1 GROUPS + ≥1 multi-phase | D-24, D-25 | Surefire IT extending existing sitegen test pattern |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

These directives are binding for Phase 62 plans — the planner must verify compliance:

- **Communication German, Code/Docs/UI English** — alle UI-Texte in `templates/site/*.html` bleiben Englisch (z. B. "Phase Breakdown", "Combined", "No results recorded yet.").
- **Test Coverage minimum 82 % line coverage** — Phase 62 must keep coverage at threshold; helper-class extraction must come with tests; SC5 IT contributes.
- **No V1 Flyway migration changes** — Phase 62 is read-only sitegen; **no Flyway migrations at all**.
- **Profile auth gate**: `prod`/`docker` only — irrelevant for sitegen, but tests use `dev` (H2).
- **OSIV stays enabled** — Templates may traverse lazy associations; only use `@EntityGraph` for optimization. Sitegen uses `@Transactional(readOnly = true)` on `generate()` so OSIV-equivalent context is active during template rendering.
- **No breaking changes to existing URLs/endpoints** — `D-04`/`D-09`/`D-13`/`D-15`/`D-16` enforce this: legacy `/season/{slug}/standings.html` stays canonical; sub-nav unchanged; no per-phase profile URL forks.
- **Playwright stays compile-scope** — `D-26` uses `playwright-cli` for manual visual verification, NOT automated E2E (separates Phase 62 from `-Pe2e` test runs).
- **Keep Controllers Thin / Keep Templates Lean** — `D-20` extracts helpers; UI-SPEC pre-locks server-side flags (`showPhaseTabs`, `showGroupTabs`, `showGroupColumn`, `showBuchholz`, `showPhaseBreakdown`, `combinedView`) to avoid SpEL projections in templates.
- **No Fallback Calculations** — `D-22` empty-state is explicit (roster + banner), not silent skip.
- **No Inline Styles on Buttons** — `D-05` reuses `.subnav-link` shape; new tab classes live in `static/site/css/style.css`. JavaScript that sets `element.className` is irrelevant here (no JS in public site for tabs).
- **Isolate Test Data Completely** — `D-25` reuses `TestDataService` GROUPS-2023 fixture (already T-prefix isolated); generated assertions filter for `Test_*`-prefixed test seasons in `productionSeasons` exactly as `SiteGeneratorService.generate()` does at Z. 79-81.
- **RaceLineup is Source of Truth** — current sitegen at Z. 296-313 (team-profile drivers) and Z. 779-794 (race result team attribution) already follows this; Phase 62 must not regress it during helper extraction.
- **TDD with Given-When-Then naming** — all new tests follow `givenContext_whenAction_thenExpectedResult()` pattern.
- **`./mvnw` always** (not `mvn`) — verify with `./mvnw verify` for unit/integration; `./mvnw verify -Pe2e` only for full E2E run; not needed for SC5 IT (D-24).
- **Subagent Rules**: opus/sonnet only (no haiku for code), branch protection (no `git stash`/`checkout`/`reset`), Post-Dispatch-Validation after every dispatch, atomic tasks, plan adherence (`NEEDS_CONTEXT` instead of self-fixing).

## Summary

Phase 62 is a **public-site template-and-generator phase** that closes the v1.9 gap: the admin layer is fully phase/group-aware (Phase 60), the backend services are phase/group-aware (Phase 58), but `templates/site/*` and `SiteGeneratorService` still render LEAGUE-shape only. The work is overwhelmingly Thymeleaf rewriting + helper-class extraction + a controlled behavior change in alltime aggregation. There is **no schema work, no new domain logic, no new repositories** — every API the planner needs already exists in the codebase.

**Confidence breakdown:** Repository surface, service signatures, template shapes, test patterns, and CSS tokens have all been verified by direct file inspection. The only HIGH-MEDIUM area is the precise downstream effect of D-19 (alltime cross-phase aggregation) since the change cascades through `StandingsService.calculateAlltimeStandings(seasons)` → currently delegates to `calculateStandings(seasonId)` which itself resolves only the REGULAR phase. The semantic intent is clear; the implementation will require either a new method signature or an internal phase-iteration. Recommendation locked in CONTEXT D-19.

**Primary recommendation:** Plan Phase 62 in **8 sequential plans** following the D-28 decomposition exactly. Plan 0 (helper-class extraction) is a pure refactor with green tests as gate. Plans 1-5 (template-by-template) each ship one new test plus one playwright-cli visual verification. Plan 6 (alltime D-19) carries the Tracked Behavior Change banner. Plan 7 (SC5 IT) is the gate before final visual sweep. Each plan ≤ 5 tasks per the project's subagent stability lessons.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Phase-aware standings page generation | Sitegen (`SiteGeneratorService`/`StandingsPageGenerator`) | Domain Service (`StandingsService`, `SeasonPhaseService`) | Sitegen orchestrates phase enumeration + writes HTML; service computes data |
| Phase tab row + group sub-tab row HTML rendering | Frontend Server (Thymeleaf templates) | Sitegen (sets `showPhaseTabs`, `showGroupTabs` flags) | Templates emit static HTML at generation time; sitegen pre-computes flags |
| Per-phase URL routing | Sitegen (file-naming via `slugify` + per-phase loop) | Static (GitHub Pages) | Each `.html` file is its own URL — no server-side routing |
| Cross-phase alltime aggregation (D-19) | Domain Service (`StandingsService.calculateAlltimeStandings`) | Sitegen consumer | Behavior change lives in the service; sitegen passes through |
| Per-group standings calculation (Combined / per-group) | Domain Service (`StandingsService.calculateStandings(phaseId, groupId)`) | Sitegen consumer | Already implemented in Phase 58 D-04 |
| Phase / group enumeration | Domain Service (`SeasonPhaseService.findAllPhases`, `findByType`) + Repository (`SeasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex`) | Sitegen consumer | Service / repository surface is already in place |
| Visual + a11y verification | Manual (`playwright-cli` Desktop + Mobile) | Static HTML output | UI-template phase — no automated visual regression in `-Pe2e` |
| Test fixture seeding (GROUPS-2023, multi-phase) | `TestDataService` (admin package, used in tests) | `@SpringBootTest` with H2 | Phase 59 D-09 already seeds GROUPS-2023 |
| Helper-class boundary | `org.ctc.sitegen.*PageGenerator` Spring beans (new) | `SiteGeneratorService` orchestrator | Project's pattern parent: `SeasonPhaseService` / `StandingsService` / `PlayoffService` decomposition in admin layer |
| Static asset routing (CSS, fonts, logos) | Sitegen `copyAssets` + `static/site/css/style.css` | unchanged | No phase-relevant changes |

## Verified API Surface

All paths verified by reading the source on branch `gsd/v1.9-season-phases-groups` on 2026-05-02.

| Method / Symbol | File | Signature | Used by Sitegen Today | Phase 62 Action |
|---|---|---|---|---|
| `StandingsService.calculateStandings(UUID phaseId, UUID groupId)` | `src/main/java/org/ctc/domain/service/StandingsService.java:41` | `List<TeamStanding> calculateStandings(UUID phaseId, UUID groupId)` | YES — `SiteGeneratorService.generateStandings` Z. 192, `generateTeamProfiles` Z. 274, `generateTeamsOverview` Z. 460, `generateAlltimeStandings` Z. 593 (inner loop) | Reuse as-is for per-phase + per-group + Combined-View |
| `StandingsService.calculateStandingsWithBuchholz(UUID phaseId, UUID groupId)` | `StandingsService.java:98` | `List<TeamStanding> calculateStandingsWithBuchholz(UUID phaseId, UUID groupId)` | NO | Use for per-group GROUPS+Swiss path (D-26 / `showBuchholz=true`) |
| `StandingsService.calculateStandings(UUID seasonId)` (legacy convenience) | `StandingsService.java:142` | `List<TeamStanding> calculateStandings(UUID seasonId)` — internally resolves REGULAR phase | NO direct sitegen use — sitegen uses the (phaseId, groupId) overload | KEEP (Phase 58 D-23 caller-side contract test enforces non-use); not a Phase 62 dependency |
| `StandingsService.calculateAlltimeStandings(List<Season>)` | `StandingsService.java:159` | `List<TeamStanding> calculateAlltimeStandings(List<Season> seasons)` — **internally calls `calculateStandings(season.getId())` (REGULAR-only)** | YES — `SiteGeneratorService.generateAlltimeStandings` Z. 578 | **D-19 BEHAVIOR CHANGE** — must aggregate across all phases per season. Either change internal loop to iterate `findAllPhases` and merge per-phase standings, or accept new `(seasons, allPhasesPerSeason)` signature. Recommendation: change INTERNAL implementation, keep public signature stable |
| `DriverRankingService.calculateRankingForPhase(UUID phaseId)` | `src/main/java/org/ctc/domain/service/DriverRankingService.java:40` | `List<DriverRanking> calculateRankingForPhase(UUID phaseId)` | NO direct sitegen use today — `aggregateAcrossPhases` is used | Use for D-11 per-phase driver ranking variant |
| `DriverRankingService.aggregateAcrossPhases(List<UUID> phaseIds, UUID seasonId)` | `DriverRankingService.java:77` | `List<DriverRanking> aggregateAcrossPhases(List<UUID> phaseIds, UUID seasonId)` | YES — `SiteGeneratorService.generateDriverRanking` Z. 220 | KEEP — D-11 legacy `driver-ranking.html` default stays cross-phase aggregated |
| `DriverRankingService.calculateAlltimeRanking(List<UUID> seasonIds)` | `DriverRankingService.java:128` | `List<DriverRanking> calculateAlltimeRanking(List<UUID> seasonIds)` — **internally uses `findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(seasonIds)`, REGULAR-phase results only** | YES — `SiteGeneratorService.generateAlltimeDriverRanking` Z. 618 | **D-19 BEHAVIOR CHANGE** — drop the `IsNull` filter. Either change to `findByRaceMatchdaySeasonIdIn(seasonIds)` (existing on RaceResultRepository — Z. 22-24) or add a new repository method that includes PLAYOFF-matchup-linked results too. Verify which set is intended for "all phases" alltime: REGULAR + PLAYOFF + PLACEMENT |
| `SeasonPhaseService.findRegularPhase(UUID seasonId)` | `src/main/java/org/ctc/domain/service/SeasonPhaseService.java:74` | `SeasonPhase findRegularPhase(UUID seasonId)` — throws `EntityNotFoundException` if absent | YES — used at multiple sitegen sites | Reuse as-is |
| `SeasonPhaseService.findByType(UUID seasonId, PhaseType type)` | `SeasonPhaseService.java:83` | `Optional<SeasonPhase> findByType(UUID seasonId, PhaseType type)` | YES — `SiteGeneratorService.generate` Z. 91, `generateTeamsOverview` Z. 455, `generateAlltimeStandings` Z. 591 | Reuse as-is |
| `SeasonPhaseService.findById(UUID phaseId)` | `SeasonPhaseService.java:91` | `SeasonPhase findById(UUID phaseId)` | YES (transitively via `StandingsService`) | Reuse as-is |
| `SeasonPhaseService.findAllPhases(UUID seasonId)` | `SeasonPhaseService.java:100` | `List<SeasonPhase> findBySeasonIdOrderBySortIndex(UUID seasonId)` (returns ordered list) | YES — `SiteGeneratorService.generateDriverRanking` Z. 218-219 | Reuse as-is for D-04 phase-tab enumeration |
| **`SeasonPhaseService.findGroups(phaseId)` — DOES NOT EXIST** | `SeasonPhaseService.java` | n/a | — | Either inject `SeasonPhaseGroupRepository` directly into the new helper class (preferred — repository method already exists), OR add `findGroupsForPhase(UUID phaseId)` to `SeasonPhaseService` for service-API symmetry. **Recommendation: inject repository directly — keeps `SeasonPhaseService` focused on CRUD/write ops** |
| `SeasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(UUID phaseId)` | `src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java:11` | `List<SeasonPhaseGroup> findByPhaseIdOrderBySortIndex(UUID phaseId)` | NO | Use for D-17 group enumeration in helper classes |
| `MatchdayRepository.findBySeasonIdOrderBySortIndexAsc(UUID seasonId)` | `src/main/java/org/ctc/domain/repository/MatchdayRepository.java:24` | `List<Matchday> findBySeasonIdOrderBySortIndexAsc(UUID seasonId)` — JPQL via `m.phase.season.id`, returns ALL phases | YES — `SiteGeneratorService.generateMatchdays` Z. 243, `generateMatchdayIndex` Z. 655 | KEEP for legacy default `matchdays.html` (cross-phase view) — but the planner must decide if D-10 legacy URL should remain cross-phase OR collapse to REGULAR-only. CONTEXT D-10 ambiguous; recommendation: legacy `matchdays.html` lists REGULAR matchdays only (consistent with `standings.html` legacy = REGULAR-combined) |
| `MatchdayRepository.findByPhaseIdOrderBySortIndexAsc(UUID phaseId)` | `MatchdayRepository.java:32` | `List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId)` | NO | **Use for D-10 per-phase variants** — `matchdays-{phaseSlug}.html` |
| `MatchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId)` | `MatchdayRepository.java:37` | `List<Matchday> findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId)` | NO | Use for D-10 per-group variants — `matchdays-{phaseSlug}-group-{groupSlug}.html` |
| `PlayoffRepository.findBySeasonId(UUID seasonId)` | `src/main/java/org/ctc/domain/repository/PlayoffRepository.java:14` | `Optional<Playoff> findBySeasonId(UUID seasonId)` — JPQL via `p.phase.season.id` | YES — `SiteGeneratorService.generatePlayoffBracket` Z. 389, `resolvePlayoffSeasonSlug` Z. 702 | Reuse as-is |
| `PlayoffRepository.findByPhaseId(UUID phaseId)` | `PlayoffRepository.java:16` | `Optional<Playoff> findByPhaseId(UUID phaseId)` | NO direct sitegen use | Available if planner wants strict phase-scoped lookup |
| `RaceLineupRepository.findByRaceMatchdaySeasonId(UUID seasonId)` | `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java:32` | `List<RaceLineup> findByRaceMatchdaySeasonId(UUID seasonId)` — JPQL via `rl.race.matchday.phase.season.id` | YES — `SiteGeneratorService.generateMatchdays` Z. 245, `generateTeamProfiles` Z. 277 | **Method name unchanged post-Phase-61** (despite CONTEXT speculation that it might have become `findByRaceMatchdayPhaseSeasonId`). The JPQL traversal goes through `phase.season.id` correctly. Reuse as-is |
| `RaceResultRepository.findByDriverId(UUID driverId)` | `src/main/java/org/ctc/domain/repository/RaceResultRepository.java:18` | `List<RaceResult> findByDriverId(UUID driverId)` | YES — `SiteGeneratorService.generateTeamProfiles` Z. 317, `generateDriverProfiles` Z. 357 | Reuse as-is. **No `findByDriverIdAndPhaseId` overload needed** — D-15 per-phase result sectioning can filter in-Java via `result.getRace().getMatchday().getPhase().getId()`. The full result list is already loaded; an extra query would be wasted. Recommendation: in-Java filtering |
| `RaceResultRepository.findByRaceMatchdayPhaseId(UUID phaseId)` | `RaceResultRepository.java:34` | `List<RaceResult> findByRaceMatchdayPhaseId(UUID phaseId)` | NO direct sitegen use | Available if planner ever needs phase-scoped results without filtering by driver |
| `RaceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId)` | `RaceResultRepository.java:37` | `List<RaceResult> findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId)` | NO direct sitegen use | Used internally by `DriverRankingService.calculateRankingForPhase` |
| `RaceResultRepository.findByRacePlayoffMatchupIsNull()` | `RaceResultRepository.java:27` | `List<RaceResult> findByRacePlayoffMatchupIsNull()` | NO direct sitegen use | Used internally by alltime — D-19 may need replacing |
| `RaceResultRepository.findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(List<UUID> seasonIds)` | `RaceResultRepository.java:30` | `List<RaceResult> findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(List<UUID> seasonIds)` | NO direct sitegen use | Used internally by `DriverRankingService.calculateAlltimeRanking(seasonIds)` — **D-19 must drop the `PlayoffMatchupIsNull` filter or supply a new repository method that includes PLAYOFF results** |
| `RaceResultRepository.findByRaceMatchdaySeasonId(UUID seasonId)` | `RaceResultRepository.java:24` | `List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId)` | NO direct sitegen use | Could be the basis for the new D-19 alltime path (without IsNull filter) |
| `PhaseTeamRepository.findByPhaseId(UUID phaseId)` | `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java:12` | `List<PhaseTeam> findByPhaseId(UUID phaseId)` | NO direct sitegen use; used internally by `StandingsService` and `SeasonPhaseService` | Use for D-22 empty-state roster source (when standings is empty, render with `phaseTeamRepository.findByPhaseId(phase.getId())` 0-point rows) |
| `PhaseTeamRepository.findByPhaseIdAndGroupId(UUID phaseId, UUID groupId)` | `PhaseTeamRepository.java:14` | `List<PhaseTeam> findByPhaseIdAndGroupId(UUID phaseId, UUID groupId)` | NO direct sitegen use | Use for D-22 per-group empty-state |
| `Matchday.getSeason()` (Phase 61 D-02 convenience) | `src/main/java/org/ctc/domain/model/Matchday.java:56` | `Season getSeason()` — delegates to `getPhase().getSeason()` | YES (transitively) | Reuse as-is — sitegen at Z. 318, 358 already uses `result.getRace().getMatchday().getSeason()` |
| `SiteGeneratorService.slugify(String input)` | `SiteGeneratorService.java:823` | package-private `String slugify(String)` | YES throughout sitegen | Reuse for D-02/D-03 slug generation. Helper-class extraction needs access — promote to `public static` on a new `SiteSlugger` utility OR keep as package-private and pass via a shared `SiteGeneratorContext` record |

### CRITICAL VERIFICATION NOTES

1. **`StandingsService.calculateAlltimeStandings(seasons)` is REGULAR-only TODAY.** Confirmed by reading `StandingsService.java:159-182` — the loop calls `calculateStandings(season.getId())` (the legacy single-arg overload at Z. 142-145) which ALWAYS resolves the REGULAR phase via `seasonPhaseService.findRegularPhase(seasonId)`. PLAYOFF and PLACEMENT phase points are excluded today. **D-19 demands changing this.**

2. **`DriverRankingService.calculateAlltimeRanking(seasonIds)` is REGULAR-only TODAY.** Confirmed by reading `DriverRankingService.java:128-132` — explicitly uses `findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(seasonIds)` which excludes PLAYOFF-matchup-linked race results. **D-19 demands changing this.**

3. **`SeasonPhaseService.findGroups(phaseId)` does NOT exist.** Confirmed by full read of `SeasonPhaseService.java`. Planner has TWO options: (a) inject `SeasonPhaseGroupRepository` directly into helper classes (`findByPhaseIdOrderBySortIndex` already exists), or (b) add a new read method to `SeasonPhaseService`. **Recommendation: option (a)** — `SeasonPhaseService` is already 396 LOC and CRUD-heavy; adding a thin pass-through method violates "don't add abstraction without payback".

4. **`MatchdayRepository.findByPhaseIdOrderBySortIndexAsc(phaseId)` EXISTS** (Z. 32) — CONTEXT speculation correct, the method is in place from Phase 61 cleanup. The integration point for D-10 per-phase matchday variants is already wired.

5. **`PlayoffRepository` has both `findBySeasonId` AND `findByPhaseId`.** Sitegen uses `findBySeasonId` at Z. 389 + Z. 702. Both work correctly post-Phase 61 (the JPQL `findBySeasonId` traverses `p.phase.season.id`). No code change needed.

6. **`RaceLineupRepository.findByRaceMatchdaySeasonId(seasonId)` is the CORRECT method name** (Z. 32 of `RaceLineupRepository.java`). CONTEXT line 273 speculation that it might be `findByRaceMatchdayPhaseSeasonId` is WRONG — the method name stayed `findByRaceMatchdaySeasonId` because Spring Data JPA derives it from the `@Query` JPQL traversal, not the property path. No code change needed.

7. **`SiteGeneratorService` is exactly 868 LOC** (verified `wc -l`). CONTEXT estimate of "~870 LOC" is correct within margin.

## Existing Code Snapshot

### `SiteGeneratorService` (868 LOC)

Verified against the live file. Method line ranges (more precise than CONTEXT estimates):

| Method | Lines | LOC | Function |
|--------|-------|-----|----------|
| `generate()` | 65-130 | 66 | Top-level orchestrator — stays in `SiteGeneratorService` |
| `cleanOutputDirectory(outPath)` | 132-158 | 27 | Stays |
| `generateIndex(...)` | 160-184 | 25 | Stays |
| `generateStandings(...)` | **186-211** | 26 | **→ `StandingsPageGenerator`** (D-20) — CONTEXT 186-211 ✓ |
| `generateDriverRanking(...)` | **213-239** | 27 | **→ `DriverRankingPageGenerator`** — CONTEXT 213-239 ✓ |
| `generateMatchdays(...)` | **241-267** | 27 | **→ `MatchdaysPageGenerator`** (per-matchday detail) — CONTEXT 241-267 ✓ |
| `generateTeamProfiles(...)` | **269-346** | 78 | **→ `TeamProfilePageGenerator`** — CONTEXT 269-346 ✓ |
| `generateDriverProfiles(...)` | **348-385** | 38 | **→ `DriverProfilePageGenerator`** — CONTEXT 348-385 ✓ |
| `generatePlayoffBracket(...)` | **387-410** | 24 | **STAYS (or → `PlayoffBracketPageGenerator`)** — CONTEXT 387-410 ✓; D-21 planner discretion |
| `generateArchive(...)` | 412-428 | 17 | Stays |
| `generateLinks(...)` | 430-441 | 12 | Stays |
| `generateTeamsOverview(...)` | 443-522 | 80 | Stays (overview is season-spanning, not per-phase) |
| `generateDriversOverview(...)` | 524-572 | 49 | Stays |
| `generateAlltimeStandings(...)` | **574-611** | 38 | **D-19 BEHAVIOR CHANGE** — CONTEXT 574-611 ✓ |
| `generateAlltimeDriverRanking(...)` | **613-651** | 39 | **D-19 BEHAVIOR CHANGE** — CONTEXT 613-651 ✓ |
| `generateMatchdayIndex(...)` | **653-678** | 26 | **→ `MatchdaysPageGenerator`** (matchday list page) — CONTEXT 653-678 ✓ |
| `writeTemplate(...)` (× 2 overloads) | 680-699 | 20 | Stays — used by every helper as a shared utility (must be promoted to `protected` or extracted into a `TemplateWriter` collaborator) |
| `resolvePlayoffSeasonSlug(...)` | 701-707 | 7 | Stays |
| `copyLogoToAssets(...)` | 709-735 | 27 | Stays — used by overview pages + team profile |
| `copyAssets(...)` | 737-768 | 32 | Stays |
| `toRaceView(...)` | 770-821 | 52 | Used only by `generateMatchdays` — moves with that helper |
| `slugify(String)` | 823-828 | 6 | **Promote to `public static`** on a `SiteSlugger` class (D-02/D-03 slug source) |
| Inner records (`SeasonEntry`, `DriverEntry`, `TeamOverviewEntry`, `DriverOverviewEntry`, `SeasonDriverInfo`) | 834-856 | 23 | Move to `org.ctc.sitegen.model` package — already the destination per project structure |
| `buildSeasonEntry(...)` | 841-846 | 6 | Stays (used by archive + overview pages) |
| `GenerationResult` static nested class | 858-867 | 10 | Stays (cross-helper aggregator — pass to each helper as a parameter) |

**Refactor pattern for D-20:** Each new helper takes `GenerationResult` (mutable accumulator), `SiteGeneratorContext` (records `outPath`, `activeSeasonSlug`, `activeSeasonName`, `season`, `hasPlayoff`, `playoffSeasonSlug`), and a shared `TemplateWriter` collaborator. The helpers are stateless `@Service @RequiredArgsConstructor` Spring beans injected into `SiteGeneratorService`. Today's `writeTemplate` overloads are extracted to the `TemplateWriter` bean.

**Constructor warning:** `SiteGeneratorService` has 17 final fields and uses Lombok `@RequiredArgsConstructor`. After D-20 extraction, the orchestrator field count shrinks (collaborators move to helpers) but ADDS the new helper beans as fields. The Mockito test `SiteGeneratorServiceIT` at Z. 79-97 explicitly enumerates the constructor argument order — **after refactor, that test must be updated** (or deleted if helper-class unit tests cover the contract). Plan 0 (helper extraction) must update the existing IT.

### Templates (current shapes)

| Template | LOC | Current shape | Phase 62 action |
|----------|-----|---------------|-----------------|
| `templates/site/layout.html` | 81 | Sub-nav with 4 links (Standings / Matchdays / Driver Ranking / Playoff), `currentPage` switch, breadcrumbs, footer. ZERO phase/group references. | UNCHANGED (D-09) — `currentPage` matching still works for variant pages because all variants set `currentPage='standings'` etc. |
| `templates/site/standings.html` | **37** | Single LEAGUE table: `# / Team / MP / W / D / L / PR / Pts`. No `<th>Group</th>`, no Buchholz, no tab rows. | REWRITE — add `showPhaseTabs`, `showGroupTabs`, `showGroupColumn`, `showBuchholz` server-side flags + tab row markup |
| `templates/site/matchdays.html` | **30** | Lists matchdays via `matchdayLinkMap`. No tab rows. | REWRITE — add tab rows for D-10 |
| `templates/site/driver-ranking.html` | **35** | Single ranking table: `# / Driver / Team / Races / Best / Avg / Points`. No tab rows. | REWRITE — add tab row for D-11 ("All Phases" + per-phase tabs) |
| `templates/site/team-profile.html` | **55** | Header + Record table + Drivers table. No phase breakdown. | ADD section: `<div th:if="${showPhaseBreakdown}">...</div>` (D-13/D-14) |
| `templates/site/driver-profile.html` | **75** | Header + Race History table + Statistics table. No per-phase sectioning. | ADD per-phase headings (D-15) — split the `<tr th:each="result : ${results}">` loop into per-phase groups when `showPhaseBreakdown=true` |
| `templates/site/playoff-bracket.html` | 32 | Bracket already phase-aware (Phase 60 D-41). No changes. | UNCHANGED (D-08, D-21) |
| `templates/site/index.html`, `archive.html`, `teams.html`, `drivers.html`, `alltime-standings.html`, `alltime-driver-ranking.html` | — | Out of phase 62 scope; alltime pages are structurally unchanged (D-18) | UNCHANGED (alltime data shifts but template stays the same) |
| `templates/site/matchday.html`, `templates/site/fragments/match-card.html` | — | Per-matchday detail page; unchanged | UNCHANGED |

### Admin reference shape (`templates/admin/season-detail.html` Z. 266-290)

```html
<!-- D-01 Phase-Tabs Row 1 (only when REGULAR phase exists) -->
<div th:if="${hasRegularPhase}" class="tab-nav mt-md" role="tablist" aria-label="Phase tabs">
    <a th:each="p : ${allPhases}"
       th:href="@{/admin/seasons/{sid}/phases/{pid}(sid=${season.id}, pid=${p.id})}"
       th:classappend="${phase != null and p.id == phase.id ? ' tab-active' : ''}"
       class="tab-btn"
       th:text="${p.label != null and !p.label.isBlank() ? p.label : p.phaseType}">Tab</a>
    <a th:href="@{/admin/seasons/{id}/phases/new(id=${season.id})}"
       class="tab-btn tab-add">+ Add Phase</a>
</div>

<!-- D-29 Group-Sub-Tabs Row 2 (only when GROUPS layout) -->
<div th:if="${phase != null and isGroupsLayout}"
     class="tab-nav tabs-secondary" role="tablist" aria-label="Group sub-tabs">
    <a th:href="@{/admin/seasons/{sid}/phases/{pid}(sid=${season.id}, pid=${phase.id})}"
       th:classappend="${selectedGroupId == null ? ' tab-active' : ''}"
       class="tab-btn">Combined</a>
    <a th:each="g : ${groups}"
       th:href="..."
       th:classappend="${selectedGroupId != null and selectedGroupId == g.id ? ' tab-active' : ''}"
       class="tab-btn"
       th:text="${g.name}">Group A</a>
    <a th:href="..." class="tab-btn tab-add">+ Add Group</a>
</div>
```

**Phase 62 mirror differences:**
- Drop `+ Add Phase` and `+ Add Group` CTAs (public users have no edit rights).
- Replace `tab-nav` / `tab-btn` / `tab-active` / `tabs-secondary` with `phase-tab-row` / `phase-tab` / `phase-tab.active` / `group-tab-row` / `group-tab` / `group-tab.active` per UI-SPEC.
- Use static-HTML inter-file anchor links (D-07), not Spring's `@{...}` URL builders.
- Compute `aria-selected` server-side at generation time (not via JS).

### Admin standings flag pattern (`templates/admin/standings.html`)

```html
<!-- Excerpt — verified Z. 75-97 -->
<th th:if="${showGroupColumn}" class="sortable" data-col="2" data-type="text" aria-label="Sort by Group">Group</th>
<!-- ...other column headers... -->
<th th:if="${showBuchholz}" class="sortable" data-col="8" data-type="num" aria-label="Sort by Buchholz">Buchholz</th>
<!-- per-row: -->
<td th:if="${showGroupColumn}" th:text="${standing.group != null ? standing.group.name : '-'}"></td>
<td th:if="${showBuchholz}" th:text="${standing.buchholz}"></td>
```

This is the EXACT pattern Phase 62 mirrors on the public site for `standings.html`. The flags are pure server-set booleans (no SpEL projections — CLAUDE.md "Keep Templates Lean").

### Existing `.subnav` CSS (Z. 622-684 of `style.css`)

```css
.subnav {
    background: var(--bg-card);
    border-bottom: 1px solid var(--border);
    padding: 0 32px;
}
.subnav-inner {
    max-width: 1100px;
    margin: 0 auto;
    display: flex;
    gap: 4px;
    height: 44px;
    align-items: center;
}
.subnav-link {
    color: var(--text-dim);
    text-decoration: none;
    padding: 6px 12px;
    border-radius: 4px;
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 1px;
    transition: all 0.2s;
}
.subnav-link:hover { color: var(--white); background: rgba(255,255,255,0.05); }
.subnav-link.active {
    color: var(--accent);            /* #4fc3f7 */
    background: rgba(79, 195, 247, 0.1);
}
@media (max-width: 768px) {
    .subnav { padding: 0 16px; overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .subnav-inner { height: auto; padding: 8px 0; flex-wrap: nowrap; }
}
```

`.entity-link` accent color: `var(--accent)` = `#4fc3f7` (Z. 24, 308). Hover transitions exist throughout style.css. New tab classes per UI-SPEC reuse these patterns by inheritance, not by duplication.

### Sitegen test pattern (existing tests on this branch)

| Test file | LOC | Pattern | Phase 62 use |
|-----------|-----|---------|--------------|
| `SiteGeneratorServiceTest.java` | 1716 | `@SpringBootTest @ActiveProfiles("dev")` + `@TempDir` + Jsoup parsing of generated HTML | **Pattern source for SC5 IT (D-24)** — extend it OR add new methods. Already uses H2 + real Spring context |
| `SiteGeneratorE2ETest.java` | 404 | `@SpringBootTest @ActiveProfiles("dev")` + `@TestInstance(PER_CLASS)` + `@DirtiesContext` + `@TempDir` + full `siteGeneratorService.generate()` flow with Jsoup assertions | Alternative pattern source — uses `@MockitoBean YouTubeScraperService` to skip network calls. **More complete fixture setup (REGULAR phase, MatchScoring, RaceScoring, scoringService.aggregate)** — closer to what SC5 needs |
| `SiteGeneratorServiceIT.java` | 161 | `@ExtendWith(MockitoExtension.class)` — pure Mockito contract test for D-23 caller-side wiring (NO Spring, NO HTML assertions) | NOT a pattern source for SC5; **WILL break when D-20 adds helper-class constructor args** — must be updated in Plan 0 |
| `YouTubeScraperServiceTest.java` | — | unit test | Unrelated |

**No test currently exercises a GROUPS-layout fixture or a multi-phase season with PLAYOFF results in sitegen.** SC5 is genuinely new test surface. The TestDataService GROUPS-2023 fixture (Z. 189-200, 312-335 of `TestDataService.java`) is admin-test-only today — sitegen tests don't load it because `productionSeasons` filter excludes any name containing "Test".

### TestDataService GROUPS-2023 fixture (`TestDataService.java`)

| Aspect | Verified content | Phase 62 implication |
|--------|------------------|----------------------|
| Method | `seed()` at Z. 86 (single entry point) | Use this if Phase 62 IT calls `testDataService.seed()` directly |
| 2023-GROUPS season | `createSeason("Season 2023", 2023, 1, "Round Robin — two groups", scorings)` Z. 190 | Has `PhaseLayout.GROUPS` REGULAR phase (Z. 200) |
| 2023 GROUPS roster | "12 PhaseTeam rows split 6/6 across Group A / Group B" Z. 312 | SC5 GROUPS coverage |
| 2023 PLAYOFFS | `playoffService.createPlayoff(s1.getId(), "2023 Playoffs", 4)` Z. 929 — autoSeedBracket, semifinal matchdays, races with results | **2023 IS multi-phase already** — REGULAR-GROUPS + PLAYOFF (4-team semifinal). SC5 multi-phase coverage is satisfied by 2023 alone |
| Test isolation | All season/team/driver names contain explicit unique substrings; the season name is "Season 2023" (not "Test_..."). **`SiteGeneratorService` filters by `!s.getName().contains("Test")`** — so `TestDataService` 2023 season WOULD appear as a "production" season in the sitegen output | **Critical for SC5**: when calling `siteGeneratorService.generate()` from a test that loaded TestDataService data, the 2023 season WILL be rendered. SC5 IT can assert on that output directly. No new "force production season" fixture needed |

**Important caveat:** `TestDataService.seed()` is the production seed entry point (Z. 86), so calling it in a test inside `@SpringBootTest` works the same way as the dev-data-seeder. The 2023 season is a legitimate fixture.

### Project tooling (verified `.planning/config.json`)

```json
"workflow": {
    "research": true,
    "plan_check": true,
    "verifier": true,
    "nyquist_validation": true,
    "ui_phase": true,
    "ui_safety_gate": true,
    "use_worktrees": true,        // ← but D-27 explicitly OVERRIDES this for Phase 62
    ...
},
"mode": "yolo",
"granularity": "standard"
```

**D-27 sequential-on-shared-branch overrides `use_worktrees: true`** for this phase. The planner must explicitly note this in plans (mirror Phase 60 / 61 pattern).

## Implementation Approach

Eight sequential plans, no worktrees, on `gsd/v1.9-season-phases-groups`. Estimated 4-6 tasks per plan to keep agents within stability limits.

### Plan 0 — Helper-Class Extraction (refactor only)

**Goal:** D-20. Move `generateStandings`, `generateDriverRanking`, `generateMatchdays`, `generateMatchdayIndex`, `generateTeamProfiles`, `generateDriverProfiles` into separate `@Service` beans. `SiteGeneratorService` becomes orchestrator. Behavior IDENTICAL — green tests are the gate.

Tasks:
1. Create `org.ctc.sitegen.SiteSlugger` (extracts `slugify`); update `SiteGeneratorService` to use it; ensure no test breaks.
2. Create `org.ctc.sitegen.TemplateWriter` (extracts the two `writeTemplate` overloads); inject into orchestrator.
3. Move records to `org.ctc.sitegen.model.{SeasonEntry, DriverEntry, TeamOverviewEntry, DriverOverviewEntry, SeasonDriverInfo, GenerationContext}`.
4. Extract `StandingsPageGenerator`, `DriverRankingPageGenerator`, `MatchdaysPageGenerator` (with `toRaceView` + `generateMatchdayIndex`), `TeamProfilePageGenerator`, `DriverProfilePageGenerator`. `SiteGeneratorService.generate()` calls `helper.generate(ctx, result)` per season.
5. Update `SiteGeneratorServiceIT.java` Mockito test for the new constructor signature; verify both sitegen tests still pass via `./mvnw -Dtest='SiteGenerator*' test`.

**Test gate:** All three existing sitegen tests pass (`SiteGeneratorServiceTest`, `SiteGeneratorE2ETest`, `SiteGeneratorServiceIT`). Generated `docs/site/` byte-identical (`git diff` empty).

### Plan 1 — Phase-Aware Standings Template + Per-Phase / Per-Group Variants

**Goal:** D-04, D-05, D-06, D-07, D-17, D-22, D-32, D-33. Rewrite `standings.html` as the canonical phase-aware template; generate `standings-{phaseSlug}.html` and `standings-{phaseSlug}-group-{groupSlug}.html`.

Tasks (TDD):
1. Add CSS classes `.phase-tab-row`, `.phase-tab-row-inner`, `.phase-tab`, `.phase-tab.active`, `.group-tab-row`, `.group-tab-row-inner`, `.group-tab`, `.group-tab.active`, `.empty-phase-banner` to `static/site/css/style.css` (following `.subnav` shape per UI-SPEC).
2. Rewrite `standings.html` to consume server-side flags `showPhaseTabs`, `showGroupTabs`, `phaseTabs` (List<PhaseTabView>), `groupTabs` (List<GroupTabView>), `showGroupColumn`, `showBuchholz`, `emptyState`, `emptyStateMessage`. Mirror admin `standings.html` Z. 75-97 column-flag pattern.
3. Extend `StandingsPageGenerator` to:
   - Always generate legacy `standings.html` (REGULAR-combined; tabs only when ≥2 phases or REGULAR is GROUPS).
   - Generate `standings-{phaseSlug}.html` for each phase except PLAYOFF (which links externally).
   - Generate `standings-{phaseSlug}-group-{groupSlug}.html` for each group of a GROUPS-layout phase.
   - Use `calculateStandingsWithBuchholz(phaseId, groupId)` when phase format is `SWISS` and `groupId != null`.
   - Use `phaseTeamRepository.findByPhaseId(phaseId)` for empty-state roster; render with 0-point standings.
4. Extend `SiteGeneratorServiceTest` with new `@Test`s covering: byte-identity for single-REGULAR-LEAGUE season; new variant files for multi-phase; group sub-tab row HTML for GROUPS phase; empty-phase banner for PLAYOFF phase without results.
5. `playwright-cli` verification on Desktop + Mobile for new tab rows.

### Plan 2 — Phase-Aware Matchdays Template + Per-Phase Variants

**Goal:** D-10, D-12, D-22. `matchdays-{phaseSlug}.html` and `matchdays-{phaseSlug}-group-{groupSlug}.html`. Legacy `matchdays.html` stays REGULAR-default (recommend collapsing to REGULAR-only matchdays for consistency with standings legacy default).

Tasks: rewrite `matchdays.html` template, extend `MatchdaysPageGenerator`, use `findByPhaseIdOrderBySortIndexAsc(phaseId)` and `findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId, groupId)` already in repository, add tests + visual verify.

### Plan 3 — Phase-Aware Driver Ranking Template + Per-Phase Variants

**Goal:** D-11, D-12. Legacy `driver-ranking.html` stays cross-phase aggregated (no behavior change at canonical URL). Per-phase variants at `driver-ranking-{phaseSlug}.html` use `calculateRankingForPhase(phaseId)`.

### Plan 4 — Team Profile + Driver Profile Phase Breakdown

**Goal:** D-13, D-14, D-15, D-16. Add `showPhaseBreakdown` flag-driven section to `team-profile.html` and `driver-profile.html`. No URL forks. SC4 byte-identity for ≥2-phase=false case.

For driver-profile per-phase results sectioning: reuse the existing `findByDriverId(driverId)` + in-Java filtering on `result.getRace().getMatchday().getPhase().getId()` — no new repository method needed (verified).

### Plan 5 — Alltime Cross-Phase Aggregation (D-19, TRACKED BEHAVIOR CHANGE)

**Goal:** D-18, D-19, D-29. Modify `StandingsService.calculateAlltimeStandings(seasons)` and `DriverRankingService.calculateAlltimeRanking(seasonIds)` internals to aggregate across all phases, not REGULAR-only.

**Implementation choice locked by research:**
- `StandingsService.calculateAlltimeStandings(List<Season>)` — change inner `calculateStandings(season.getId())` call (which delegates to REGULAR phase) to a loop over `seasonPhaseService.findAllPhases(season.getId())` followed by `calculateStandings(phase.getId(), null)` for each phase, then merge results into the alltime map. Public signature unchanged.
- `DriverRankingService.calculateAlltimeRanking(List<UUID> seasonIds)` — replace `findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(seasonIds)` with `findByRaceMatchdaySeasonId` extended to `In(seasonIds)` (NEW repo method needed: `findByRaceMatchdaySeasonIdIn(List<UUID>)`) — drops the `IsNull` filter, includes PLAYOFF results.

Tasks:
1. Write failing test demonstrating the change for a multi-phase season (e.g. 2023 with REGULAR + PLAYOFF).
2. Modify `StandingsService.calculateAlltimeStandings(seasons)` per above. Verify all existing standings tests still pass.
3. Add `RaceResultRepository.findByRaceMatchdaySeasonIdIn(List<UUID>)` if not already present (verify — Z. 24 has `findByRaceMatchdaySeasonId(UUID)` singular, but no plural overload; confirm and add).
4. Modify `DriverRankingService.calculateAlltimeRanking(seasonIds)` to use the new repo method.
5. Update PR description template + plan SUMMARY.md to call out TRACKED BEHAVIOR CHANGE per D-29.
6. Visual verify alltime pages with `playwright-cli` (numeric values DIFFER for any season with PLAYOFF — call out in release notes).

### Plan 6 — SC5 Regression Test (Surefire IT)

**Goal:** D-24, D-25, SC5. Surefire integration test in `src/test/java/org/ctc/sitegen/`.

Tasks:
1. Create `SiteGeneratorPhaseAwarenessIT.java` (`@SpringBootTest @ActiveProfiles("dev")` with `@TempDir`).
2. Seed via `testDataService.seed()` to get the GROUPS-2023 multi-phase fixture.
3. Run `siteGeneratorService.generate()`, then assert via `Files.exists` + Jsoup:
   - GROUPS season: `standings-regular-group-{a}.html` + `standings-regular-group-{b}.html` exist; both contain only their group's teams; `standings.html` shows Group column; `<nav class="group-tab-row" role="tablist">` present.
   - Multi-phase season: phase tab row visible on `standings.html`; `standings-playoff.html` does NOT exist (D-08); `<a href=".../playoff.html">` PLAYOFF tab present.
   - SC4: a single-REGULAR-LEAGUE season (a non-2023 season created in the test, or any other production fixture) renders `standings.html` WITHOUT phase-tab-row or group-tab-row.

### Plan 7 — Visual + A11y Final Sweep

**Goal:** D-26. `playwright-cli` Desktop + Mobile pass for all 4 phase-aware page types in both LEAGUE single-phase and GROUPS multi-phase fixtures.

Tasks: start dev server with `dev,demo` profile, walk through each new URL variant on Desktop + Mobile, capture screenshots into `.screenshots/`, verify:
- `role="tablist"` + `role="tab"` + `aria-selected` present and accurate.
- Tab keyboard navigation works (Tab + Enter).
- Mobile horizontal scroll for >5 phases (synthetic test: temporarily seed extra PLACEMENT phase).
- `.empty-phase-banner` text and styling correct.
- Active-phase visual treatment: accent color + background tint match `.subnav-link.active`.

## Risks / Pitfalls

### Risk 1: Constructor-arity drift breaks `SiteGeneratorServiceIT`
- **Where:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java:79-97` enumerates the 17-argument constructor explicitly.
- **What goes wrong:** D-20 helper extraction adds 5 new helper-bean fields. The Mockito test will fail to compile.
- **How to avoid:** Plan 0 must update this test in the same task that introduces the new helpers. Better: replace the existing IT entirely with per-helper unit tests, since the D-23 contract it asserts (phase-aware API used) is now naturally enforced by each helper's own tests.

### Risk 2: SC4 byte-identity invariant violated by accidental whitespace / order changes
- **Where:** `templates/site/standings.html`, `matchdays.html`, `driver-ranking.html`, `team-profile.html`, `driver-profile.html`.
- **What goes wrong:** Even when `showPhaseTabs=false`, refactoring the template HTML can introduce extra whitespace or reorder attributes — making single-REGULAR-LEAGUE output bytewise different from before.
- **How to avoid:** Plan 1's first task should snapshot today's `docs/site/season/{slug}/standings.html` for a single-REGULAR-LEAGUE production season (e.g. one of Phase 23's fictive-data seasons), then assert post-rewrite output matches. This is stronger than Jsoup-based equivalence-class assertions because GitHub Pages serves raw bytes.

### Risk 3: Helper-class shared state / circular deps
- **Where:** `SiteGeneratorService` records (`SeasonEntry`, `DriverEntry`, etc.) are inner types used across multiple `generateX` methods.
- **What goes wrong:** Naive extraction puts records in one helper; another helper can't access them; circular Spring dependency emerges.
- **How to avoid:** Move records to `org.ctc.sitegen.model.*` package (already declared in CONTEXT canonical_refs). Each helper's input is a `record GenerationContext(Path outPath, Season season, String activeSeasonSlug, String activeSeasonName, boolean hasPlayoff, String playoffSeasonSlug)` plus shared `GenerationResult` accumulator. No helper depends on another helper.

### Risk 4: D-19 alltime numbers shift for ALL historical seasons (not just future ones)
- **Where:** `alltime-standings.html`, `alltime-driver-ranking.html` are root-level URLs visible on the homepage navigation.
- **What goes wrong:** Users with bookmarks notice their team's alltime points jumped. No per-season breakdown is on these pages today.
- **How to avoid:** Release notes call this out explicitly. Optionally, add a small text note on `alltime-standings.html` ("Includes results from all phases per season as of v1.9") — this is a Claude's-Discretion item the planner can decide.

### Risk 5: Combined-View tie-breaker rules with mixed group sizes
- **Where:** `StandingsService.calculateStandings(phaseId, null)` for GROUPS-layout combines all groups flat.
- **What goes wrong:** A team in Group A with 5 matches and 12 points may sort above a team in Group B with 4 matches and 11 points — but the comparison is unfair if groups have different sizes. The current sort (`points → pointDifference → pointsFor`) doesn't normalize for matches played.
- **How to avoid:** This is a Phase 58 D-04 decision, not a Phase 62 decision. Phase 62 just renders what Phase 58 computes. Document the tradeoff in the plan but DO NOT modify the sort. If the user objects after seeing the rendered output, defer to a future phase.

### Risk 6: Mobile horizontal-scroll signal lost on second tab row
- **Where:** UI-SPEC mandates `overflow-x: auto` for both `.phase-tab-row` and `.group-tab-row` on mobile.
- **What goes wrong:** Two stacked overflow-scroll rows on a small screen are visually busy and easy to miss. The standings table also has `.table-wrap` overflow-x with a fade-gradient indicator (Z. 821 onward of style.css). Three horizontally-scrolling regions on one mobile page = bad UX.
- **How to avoid:** Plan 7 (visual sweep) must include a Mobile screenshot of the worst case (GROUPS multi-phase) and judge whether all three scrollable regions are usable. If not, propose a fade-gradient indicator on the tab rows similar to `.table-wrap::after`.

### Risk 7: GitHub Pages case-sensitivity on per-phase URLs
- **Where:** `phaseSlug = lowercased PhaseType name` (D-02), `groupSlug = slugify(group.name)` (D-03).
- **What goes wrong:** GitHub Pages serves files case-sensitively. If a group is named `Group A` (with space), `slugify` produces `group-a`. Cross-links in the static HTML reference `group-a`. No collision risk because `slugify` is deterministic and the file write uses the same function.
- **How to avoid:** Already correct — `SiteGeneratorService.slugify` lowercases everything (Z. 824). Just ensure the helper-class extraction preserves this exact function.

### Risk 8: `cleanOutputDirectory` deletes existing per-phase HTML before regeneration
- **Where:** `SiteGeneratorService.cleanOutputDirectory(outPath)` Z. 132-158 walks the tree and deletes ALL files.
- **What goes wrong:** None for Phase 62 — every page is regenerated each run. But if a future season has a phase deleted, the corresponding `standings-{phaseSlug}.html` file from the previous generation is also deleted (good — no stale files).
- **How to avoid:** No action; existing behavior is correct. Just confirm SC5 IT uses `@TempDir` (it does in existing pattern) so test artifacts don't leak.

### Risk 9: `generateMatchdayIndex` vs `generateMatchdays` naming confusion
- **Where:** Today `generateMatchdayIndex` (Z. 653) writes `matchdays.html` (the LIST page) and `generateMatchdays` (Z. 241) writes per-matchday detail pages (`matchday/{slug}.html`).
- **What goes wrong:** D-10 says "matchdays.html → tabs + suffixed files" — this targets `generateMatchdayIndex`. But the helper-class name `MatchdaysPageGenerator` is ambiguous.
- **How to avoid:** Plan 0 should split into `MatchdayIndexPageGenerator` (writes `matchdays.html` + per-phase variants, list page) and `MatchdayDetailPageGenerator` (writes `matchday/{slug}.html` per-matchday detail). OR keep them as one helper with two distinct public methods. Planner finalizes.

### Risk 10: D-19 PLAYOFF-results inclusion in alltime — semantics question
- **Where:** `DriverRankingService.calculateAlltimeRanking(seasonIds)` currently uses `findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(seasonIds)`. PLAYOFF-races may exist in `Race` rows linked via `playoffMatchup` (not `matchday.phase` — see Phase 56/58 schema).
- **What goes wrong:** Simply changing to `findByRaceMatchdaySeasonIdIn(seasonIds)` may STILL exclude PLAYOFF races if PLAYOFF races are routed through `playoffMatchup` instead of through a `matchday` row. The repo method `findByRacePlayoffMatchupRoundPlayoffPhaseId(phaseId)` Z. 37 hints that PLAYOFF results have a separate path.
- **How to avoid:** Plan 5 must verify the data model: does a PLAYOFF Race have BOTH a `matchday` row (in the PLAYOFF SeasonPhase) AND a `playoffMatchup` link? Or only the `playoffMatchup`? `TestDataService.java:933` shows `playoffMatchday2023 = matchdayRepository.save(new Matchday(playoff2023.getPhase(), "2023 Playoffs", 4))` — so YES, PLAYOFF races have a matchday too. Removing the `IsNull` filter is correct, but also: races linked only via `playoffMatchup` (without a matchday — if that case exists in production data) would still be excluded. **Recommendation: in Plan 5 task 1, write a unit test that asserts both REGULAR matchday races AND PLAYOFF-matchup-linked races contribute to the alltime ranking after the change.**

### Risk 11: `productionSeasons` filter on test seed
- **Where:** `SiteGeneratorService.generate()` Z. 79-81 filters `s -> !s.getName().contains("Test")`.
- **What goes wrong:** SC5 IT must use a season fixture whose name does NOT contain "Test" so it survives the production filter. `TestDataService` 2023 fixture name is "Season 2023" (no "Test" substring) — good. But `SiteGeneratorE2ETest.java:101-104` uses "Test_*" prefix to HIDE existing seasons — Phase 62 IT must NOT do the same to its target season.
- **How to avoid:** SC5 IT should rely on `testDataService.seed()` directly (which uses non-Test names) and NOT pre-filter with the `Test_*` prefix dance.

## Validation Architecture

The phase has explicit Nyquist test/validation pyramid for SC1..SC5:

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test (4.x); Jsoup 1.x for HTML parsing (already on classpath) |
| Config file | `pom.xml` (no separate test config) |
| Quick run command | `./mvnw -Dtest='SiteGenerator*' test` (runs all sitegen tests, Surefire scope) |
| Full suite command | `./mvnw verify` (Surefire — Unit + IT, no Playwright); `./mvnw verify -Pe2e` (adds Playwright E2E — NOT needed for Phase 62 SC5) |

### Phase Requirements → Test Map

| Req ID / Source | Behavior | Test Type | Automated Command | File Exists? |
|-----------------|----------|-----------|-------------------|-------------|
| SC1 (GROUPS per-group + combined) | `/season/{slug}/standings-regular-group-a.html` exists; renders only Group A teams; `standings.html` shows Group column | Sitegen IT (Surefire, Jsoup) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenGroupsLayoutSeason_whenGenerate_thenPerGroupAndCombinedFilesExist test` | ❌ Plan 6 |
| SC2 (multi-phase one tab per phase) | Phase tab row HTML present on `standings.html`; PLAYOFF tab `<a href=".../playoff.html">` present | Sitegen IT | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisible test` | ❌ Plan 6 |
| SC3 (PLAYOFF tab reaches bracket) | Anchor href on PLAYOFF tab matches generated `playoff.html` URL | Sitegen IT | (combined with SC2) | ❌ Plan 6 |
| SC4 (single-REGULAR-LEAGUE byte-identity) | `standings.html` for a LEAGUE-only single-phase season is byte-identical to a snapshot taken pre-Phase-62 | Sitegen IT (golden file) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline test` | ❌ Plan 1 (snapshot baseline taken there); finalised in Plan 6 |
| SC5 (regression test ≥1 GROUPS + ≥1 multi-phase) | Test class itself + green coverage of GROUPS-2023 fixture | Sitegen IT | (entire `SiteGeneratorPhaseAwarenessIT` class) | ❌ Plan 6 |
| D-19 alltime cross-phase | Service-level test: alltime points for a multi-phase season include PLAYOFF points | Unit test (StandingsService) + Service IT | `./mvnw -Dtest=StandingsServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints test` | ❌ Plan 5 |
| D-19 alltime driver ranking | Same for driver ranking | Unit test (DriverRankingService) | `./mvnw -Dtest=DriverRankingServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults test` | ❌ Plan 5 |
| D-22 empty-phase roster + banner | PLAYOFF phase with 0 race results renders `standings-playoff.html` with all roster teams at 0 points + banner | Sitegen IT | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenPlayoffPhaseWithoutResults_whenGenerate_thenEmptyStateBannerAndRoster test` | ❌ Plan 1 (banner template) + Plan 6 (assertion) |
| D-26 a11y semantics | `role="tablist"` + `role="tab"` + `aria-selected` present on tab nav | Sitegen IT (Jsoup attribute assertion) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenMultiPhaseSeason_whenGenerate_thenTabRowHasA11yAttributes test` | ❌ Plan 6 |
| D-26 visual + responsive | Desktop + Mobile screenshots match design contract | Manual via `playwright-cli` | `playwright-cli open http://localhost:9090/...` (manual, captured to `.screenshots/`) | manual — Plan 7 |
| Helper-class behavior parity (D-20) | Each `XxxPageGenerator` produces same output as today | Per-helper unit test (`@SpringBootTest`) | `./mvnw -Dtest='*PageGenerator*Test' test` | ❌ Plan 0 |
| `SiteGeneratorServiceIT` D-23 contract update | Existing Mockito IT updated for new constructor or replaced | Mockito unit test | `./mvnw -Dtest=SiteGeneratorServiceIT test` | ✅ exists; needs Plan 0 update |

### Sampling Rate

- **Per task commit:** `./mvnw -Dtest='SiteGenerator*' test` (~1-2 minutes; covers all sitegen + helper + SC5 IT)
- **Per plan merge:** `./mvnw verify` (full Surefire — unit + integration — including JaCoCo coverage report; ~5-10 minutes; verify ≥82% line coverage)
- **Phase gate (before `/gsd-verify-work`):** `./mvnw verify` green; manual `playwright-cli` Desktop + Mobile sweep complete; release-note draft for D-19 prepared

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java` — covers SC1, SC2, SC3, SC4 (golden file), SC5, D-22, D-26 a11y. **Single test class, multiple `@Test` methods.**
- [ ] `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java`, `DriverRankingPageGeneratorTest.java`, `MatchdaysPageGeneratorTest.java`, `TeamProfilePageGeneratorTest.java`, `DriverProfilePageGeneratorTest.java` — Plan 0 helper-class unit tests (each ≈ 5-10 test methods).
- [ ] `src/test/java/org/ctc/domain/service/StandingsServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints` — Plan 5 D-19 service test; class likely already exists, add new method.
- [ ] `src/test/java/org/ctc/domain/service/DriverRankingServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults` — Plan 5 D-19 service test; class likely already exists, add new method.
- [ ] Golden snapshot file: `src/test/resources/sitegen/baseline/single-league-standings.html` — captured pre-Plan-1, asserted post-rewrite for SC4.
- [ ] Update `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java:79-97` — constructor argument list reflects post-D-20 helper beans. (Existing file, not new.)

Framework install: none required. JUnit 5 + Mockito + Spring Boot Test + Jsoup are already on the project's compile/test classpath (verified by reading existing tests). `playwright-cli` is the manual verification tool — already installed per CLAUDE.md "Visual Verification".

### CSS Regression Sampling

- Plan 1 + Plan 7 visual sweep on `playwright-cli` for: `.subnav` (existing — must remain pixel-identical), new `.phase-tab-row` / `.group-tab-row` classes, mobile overflow-scroll, active-tab accent visual, empty-state banner.
- No automated CSS regression tooling; rely on manual screenshots + `git diff` on `style.css`.

### Coverage Discipline

- Helper-class extraction (Plan 0) MUST keep coverage ≥ 82% — measured via JaCoCo at `target/site/jacoco/index.html` after `./mvnw verify`.
- D-19 service-method change (Plan 5) is high-risk for coverage drop if old REGULAR-only paths are simply deleted; ensure new path is fully covered before merging.
- New helper unit tests in Plan 0 typically RAISE coverage; SC5 IT in Plan 6 raises it further.

## Open Questions (RESOLVED)

1. **D-19 PLAYOFF results inclusion semantics — is "all phases" REGULAR + PLAYOFF + PLACEMENT, or just REGULAR + PLAYOFF?**
   - What we know: CONTEXT D-18/D-19 says "all phases per season"; ROADMAP doesn't specify; PLACEMENT phase is rare in production data.
   - What's unclear: should a PLACEMENT phase that ranks teams at the bottom contribute "negative" alltime points if its scoring rewards higher placements with fewer points?
   - Recommendation: Plan 5 task 1 should include all three phase types in the loop (`findAllPhases` returns them all). If the user objects after seeing alltime numbers, defer the PLACEMENT carve-out to a future phase. Document the choice in the PR description as part of the Tracked Behavior Change.
   - **RESOLVED:** D-19 includes REGULAR + PLAYOFF + PLACEMENT via `seasonPhaseService.findAllPhases(seasonId)`. Implemented in Plan 5; PLACEMENT semantics documented as accepted risk in the Plan 5 SUMMARY.

2. **Legacy `matchdays.html` scope: REGULAR-only matchdays, OR all-phase matchdays?**
   - What we know: Today `generateMatchdayIndex` uses `findBySeasonIdOrderBySortIndexAsc` which returns ALL phase matchdays; D-10 says "matchdays.html stays as REGULAR-default"; CONTEXT does not explicitly resolve.
   - What's unclear: Whether D-10 "REGULAR-default" means (a) tabs visible with REGULAR pre-selected (like standings.html legacy = REGULAR-combined) or (b) flat list of REGULAR matchdays only.
   - Recommendation: For consistency with `standings.html` legacy (which renders REGULAR-combined and not all-phase combined), `matchdays.html` legacy should render REGULAR-only matchdays. Document this in Plan 2 task 1 and verify against UI-SPEC if ambiguous.
   - **RESOLVED:** Legacy `matchdays.html` renders REGULAR-only matchdays (consistent with `standings.html` legacy = REGULAR-combined). Implemented in Plan 2 via `findByPhaseIdOrderBySortIndexAsc(regularPhase.getId())`.

3. **`PhaseTabView` / `GroupTabView` model — record location?**
   - What we know: New model objects (label, href, active boolean, ariaControlsId) are needed for clean Thymeleaf templates.
   - What's unclear: Should they live in `org.ctc.sitegen.model` (alongside `RaceView`) or in a deeper `org.ctc.sitegen.model.tabs` namespace?
   - Recommendation: `org.ctc.sitegen.model` (flat) — current convention has `RaceView` directly in this package. Planner may decide otherwise.
   - **RESOLVED:** `PhaseTabView`, `GroupSubTabView`, `PhaseBreakdownEntry` live in `org.ctc.sitegen.model` (flat package, alongside `RaceView`). Created in Plan 0.

4. **Helper-class boundary: split `MatchdayIndexPageGenerator` from `MatchdayDetailPageGenerator`?**
   - What we know: Today they are two methods (`generateMatchdayIndex` and `generateMatchdays`) on the same class; Risk 9 surfaces the naming question.
   - What's unclear: Whether they share enough state to warrant one class.
   - Recommendation: One class `MatchdaysPageGenerator` with TWO distinct entry methods (`generateIndex(ctx, result)` and `generateDetails(ctx, result)`) called by orchestrator separately. Cohesion is high (both about matchdays); separation by file is unjustified.
   - **RESOLVED:** `MatchdaysPageGenerator` is ONE class with two methods `generateIndex(ctx, result)` and `generateDetails(ctx, result)`, called separately by the orchestrator. Implemented in Plan 0.

5. **D-19 release-note language: where does it appear?**
   - What we know: CLAUDE.md mentions PR descriptions; project memory `feedback_docs_update` says README + Wiki update on every feature release.
   - What's unclear: Whether the v1.9 milestone release note (when v1.9 ships) is the canonical home, or each plan PR.
   - Recommendation: Each plan PR with D-19 changes calls it out in PR body; final v1.9 release notes consolidate. Planner sets this in Plan 5 task 5 SUMMARY.md guidance.
   - **RESOLVED:** D-19 release-notes appear in each plan PR body that touches alltime, and the final v1.9 release notes consolidate them. The canonical pre-release artifact is the release-note bullet drafted in Plan 5 Task 3 (`62-05-SUMMARY.md`).

## Sources

### Primary (HIGH confidence — direct file inspection on `gsd/v1.9-season-phases-groups`)

- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` (868 LOC, fully read)
- `src/main/java/org/ctc/domain/service/StandingsService.java` (406 LOC, fully read)
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` (276 LOC, fully read)
- `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` (396 LOC, fully read)
- `src/main/java/org/ctc/domain/repository/MatchdayRepository.java` (42 LOC, fully read)
- `src/main/java/org/ctc/domain/repository/PlayoffRepository.java` (19 LOC, fully read)
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` (41 LOC, fully read)
- `src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java` (12 LOC, fully read)
- `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` (38 LOC, fully read)
- `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` (19 LOC, fully read)
- `src/main/java/org/ctc/domain/model/Matchday.java` (60 LOC, fully read — `getSeason()` convenience verified)
- `src/main/resources/templates/site/standings.html` (37 LOC, fully read)
- `src/main/resources/templates/site/matchdays.html` (30 LOC, fully read)
- `src/main/resources/templates/site/driver-ranking.html` (35 LOC, fully read)
- `src/main/resources/templates/site/team-profile.html` (55 LOC, fully read)
- `src/main/resources/templates/site/driver-profile.html` (75 LOC, fully read)
- `src/main/resources/templates/site/layout.html` (81 LOC, fully read)
- `src/main/resources/templates/admin/season-detail.html` Z. 260-290 (admin tab pattern)
- `src/main/resources/templates/admin/standings.html` Z. 75-97 (showGroupColumn / showBuchholz pattern)
- `src/main/resources/static/site/css/style.css` Z. 622-684 (.subnav definitions)
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java` (161 LOC, fully read)
- `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java` head (404 LOC; first 120 read)
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` head (1716 LOC; first 100 read)
- `src/main/java/org/ctc/admin/TestDataService.java` (grep + key sections read; 1043 LOC)
- `.planning/config.json` (verified workflow + mode)
- `.planning/REQUIREMENTS.md` (UI-* + QUAL-* requirements verified)
- `.planning/ROADMAP.md` (Phase 62 entry verified)
- `.planning/STATE.md` (Phase 62 background verified)
- `.planning/phases/62-public-site-phases-groups/62-CONTEXT.md` (full)
- `.planning/phases/62-public-site-phases-groups/62-UI-SPEC.md` (full, approved)

### Secondary (MEDIUM confidence)

- CONTEXT cross-references to Phase 56 / 58 / 59 / 60 / 61 contexts — claims relayed, not independently re-verified by reading those phase contexts. Trust level: HIGH because CONTEXT.md is itself a verified artefact reviewed during `/gsd-discuss-phase`.

### Tertiary (LOW confidence)

- None. All claims in this research were verified against live code or against the explicitly-approved CONTEXT.md / UI-SPEC.md artefacts. No web searches were performed because the stack is internal (Spring Boot 4 + Thymeleaf SSR + plain CSS — no external library APIs to consult).

## Assumptions Log

> All claims tagged `[ASSUMED]` in this research. Empty if all claims are verified.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `RaceResultRepository.findByRaceMatchdaySeasonIdIn(List<UUID>)` (plural overload) does NOT exist today; Plan 5 may need to add it | Verified API Surface, Risk 10 | If it already exists, Plan 5 task 3 is a no-op — low risk |
| A2 | The 2023 fixture in `TestDataService.seed()` is a complete multi-phase coverage source (REGULAR-GROUPS + PLAYOFF) and does NOT need extension for SC5 | Implementation Approach Plan 6, Risk uncertainty | If fixture is missing PLAYOFF result rows (Z. 943 only creates `createPlayoffRaces` per matchup — verify those have RaceResult rows), Plan 6 task 2 may need to extend. Read confirmed `createPlayoffRaces(playoffMatchday2023, matchup, s1, raceScoring, 2)` so 2 races per matchup are seeded — likely sufficient |
| A3 | `playwright-cli` works against `localhost:9090` (dev profile port) for the manual visual sweep | Validation Architecture, Plan 7 | Verified by CLAUDE.md ("Visual Verification with `playwright-cli`" section explicitly references `http://localhost:9090/...`) — reclassifying as VERIFIED |
| A4 | The legacy `matchdays.html` URL should render REGULAR-only matchdays (not all-phase combined) for consistency with `standings.html` legacy default | Implementation Approach Plan 2, Open Question 2 | If user expects all-phase combined view, Plan 2's choice will need to be reversed. Documented as Open Question 2 |
| A5 | `MatchdayIndexPageGenerator` and `MatchdayDetailPageGenerator` should be ONE class with two methods (not two classes) | Implementation Approach Plan 0, Open Question 4 | If user / planner prefers stricter separation, the helper-class count grows by 1 — low impact |

**Bottom line:** 5 minor `[ASSUMED]` items, all of which are documented as Open Questions or have low blast-radius if wrong. No assumed claims block planning.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — internal Spring Boot 4 / Thymeleaf / plain CSS; no external library version checks needed.
- Architecture (helper-class boundaries): HIGH — verified by reading current `SiteGeneratorService` and confirming method line ranges; pattern parent (admin layer service decomposition) is well-established.
- Pitfalls: HIGH — Risk 1, 2, 3, 6, 7, 8, 9 verified by direct code inspection; Risk 5 is a Phase 58 carry-over (out of Phase 62 scope but documented); Risk 10 has Open Question fallback.
- API surface: HIGH — every method signature in the table was verified by reading the file directly.
- Test patterns: HIGH — both existing sitegen test patterns (`@SpringBootTest` + Jsoup) read directly.
- D-19 implementation path: MEDIUM — chose internal-iteration approach without changing public signatures; Plan 5 task 1 (failing test) will surface any blocker.

**Research date:** 2026-05-02
**Valid until:** 2026-05-30 (28 days — codebase is on an active feature branch; verify before each plan if branch HEAD has moved significantly).
