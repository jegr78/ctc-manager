---
phase: 62-public-site-phases-groups
verified: 2026-05-07T07:33:49Z
status: human_needed
score: 10/10 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: none
  previous_score: n/a
  gaps_closed: []
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Open `/season/{groups-multi-phase-slug}/driver-ranking-regular.html` (e.g. `season/2023-1-season-2023/driver-ranking-regular.html`) and confirm the **Team** column shows actual team short names (not '-')."
    expected: "Each driver row shows their team short name in the Team column (e.g. ADR, VRX, etc.)."
    why_human: "REVIEW W-1 confirms `DriverRankingService.resolveTeamFromLineup` is a dead stub that always returns `null`. The per-phase `driver-ranking-{phaseSlug}.html` template renders `r.team != null ? r.team.shortName : '-'` (driver-ranking.html:32). No automated test asserts on the team column content for per-phase variants — every row likely shows `-`. Phase 62 contract delivers the **page**, but the page may be visibly broken at the data-cell level. Confirm with a real screenshot whether this is acceptable for v1.9 ship or must be repaired."
  - test: "Open the public site for a multi-phase season after Plan-7 sweep regenerated, and confirm `alltime-standings.html` totals visibly differ from a Phase-61-baseline screenshot for at least one team that participated in PLAYOFF/PLACEMENT phases."
    expected: "D-19 TRACKED BEHAVIOR CHANGE: alltime totals now include PLAYOFF + PLACEMENT phase points. Numbers should differ vs. pre-Phase-62."
    why_human: "Tested only via Mockito unit tests in `StandingsServiceTest`/`DriverRankingServiceTest`. Plan 7 SUMMARY claims visual verification was done, but did not record before/after numeric deltas. The user already flagged in REVIEW W-2 that the cross-phase Buchholz path may be REGULAR-only — visible numbers are the only honest gate."
  - test: "Open `/season/{multi-phase-slug}/standings.html` on Mobile (390x844) and confirm the phase-tab row + group-tab row both scroll horizontally without breaking layout, AND that the standings table is independently scrollable."
    expected: "Three independent horizontal-scroll regions: phase-tab row, group-tab row, table. No clipping, no z-index issues."
    why_human: "Phase 62 D-26 a11y assertion (Plan 6 IT) covers role/aria attributes only, not layout behavior. Plan 7 SUMMARY claims this passed but provides no quantitative evidence beyond 'screenshots captured'."
  - test: "Click PLAYOFF tab on `/season/{multi-phase-slug}/standings.html` and `/season/{multi-phase-slug}/matchdays.html`; confirm both navigate to existing `/season/{slug}/playoff.html`."
    expected: "Both PLAYOFF tabs route to the bracket page; no 404; bracket renders."
    why_human: "Plan 6 IT asserts the href value but not the resolution. Plan 7 SUMMARY claims click-through verified, but the IT alone cannot prove a real browser navigation works."
gaps: []
deferred: []
---

# Phase 62: Public Site Phase + Group Awareness — Verification Report

**Phase Goal:** The public static site exposes the same phase/group model that Phase 60 introduced on the admin side. Each season's public page renders one tab per phase (REGULAR / PLAYOFF / PLACEMENT), GROUPS-layout phases render per-group standings plus a combined view, and PLAYOFF-phase content (bracket / final standings) is reachable from the phase tab. SiteGeneratorService no longer assumes LEAGUE shape on a single REGULAR phase, and `templates/site/standings.html` is phase- and group-aware analogous to `templates/admin/season-detail.html`.

**Verified:** 2026-05-07T07:33:49Z
**Status:** human_needed
**Re-verification:** No — initial verification.

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                                                  | Status     | Evidence |
|----|------------------------------------------------------------------------------------------------------------------------|------------|----------|
| 1  | SiteGeneratorService no longer assumes LEAGUE shape on a single REGULAR phase                                          | ✓ VERIFIED | `SiteGeneratorService.java:103-111` builds a `GenerationContext` per season and delegates to 5 helpers; orchestrator went from 868 → 568 LOC; pre-extraction methods (`generateStandings`, `generateDriverRanking`, `generateMatchdays`, `generateMatchdayIndex`, `generateTeamProfiles`, `generateDriverProfiles`) are absent (grep returned no matches). |
| 2  | Each season's public page renders one phase-tab row when ≥2 phases (REGULAR / PLAYOFF / PLACEMENT)                     | ✓ VERIFIED | `StandingsPageGenerator.buildPhaseTabs` (Z. 198-217), `MatchdaysPageGenerator.buildPhaseTabs` (Z. 182-201), `DriverRankingPageGenerator.buildPhaseTabs` (Z. 156-179). `templates/site/standings.html:5-13`, `matchdays.html:5-13`, `driver-ranking.html:5-13` emit `<nav class="phase-tab-row" role="tablist">` gated by `th:if="${showPhaseTabs}"`. SC5 IT `givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisible` asserts `tabRow.attr("role").isEqualTo("tablist")`. |
| 3  | GROUPS-layout phases render per-group standings plus a combined view                                                   | ✓ VERIFIED | `StandingsPageGenerator.generate` (Z. 86-94) writes `standings-{phaseSlug}-group-{groupSlug}.html` per group + `standings-{phaseSlug}.html` combined. `showGroupColumn = isGroupsLayout && isCombinedView` (Z. 162). SC5 IT `givenGroupsLayoutSeason_whenGenerate_thenPerGroupAndCombinedFilesExist` asserts files + Group column conditional. |
| 4  | PLAYOFF-phase tab on the public site reaches the playoff bracket without manual URL                                    | ✓ VERIFIED | `StandingsPageGenerator.buildPhaseTabs:206-207`: `if (p.getPhaseType() == PhaseType.PLAYOFF) href = "playoff.html"`. Same in MatchdaysPageGenerator (Z. 190-191). D-08 enforced: `standings-playoff.html` is never written (no code path emits it). SC5 IT `givenMultiPhaseSeason_whenGenerate_thenPlayoffTabLinksToBracket` asserts both. |
| 5  | `templates/site/standings.html` is phase- and group-aware analogous to `templates/admin/season-detail.html`             | ✓ VERIFIED | Template consumes 7 server-side flags: `showPhaseTabs`, `phaseTabs`, `showGroupTabs`, `groupTabs`, `showGroupColumn`, `showBuchholz`, `emptyState` (lines 5, 14, 25, 33, 35, 49). Includes role=tablist/tab + aria-selected per D-26. |
| 6  | Existing single-REGULAR-LEAGUE seasons render byte-identical to the v1.6 baseline (SC4)                                 | ✓ VERIFIED | Golden snapshots: `single-league-standings.html` (14054 bytes), `single-league-team-profile.html` (6553 bytes), `single-league-driver-profile.html` (8080 bytes). SC5 IT `givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline` (Z. 172-185) does an exact-bytes assertion. Plan-7 SUMMARY: 1215 Surefire + 31 Failsafe = 1246 tests, 0 failures. |
| 7  | A regression test exists covering GROUPS-layout + multi-phase seasons (SC5)                                            | ✓ VERIFIED | `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java` (305 LOC, 9 @Test methods). Surefire (default `./mvnw verify`), reuses `TestDataService.seed()`, `@SpringBootTest @ActiveProfiles("dev")`. Asserts SC1-SC5, D-04, D-08, D-19, D-22, D-26. |
| 8  | D-22 empty-state banner is wired and rendered for PLAYOFF/empty phases                                                 | ✓ VERIFIED | `StandingsPageGenerator` (Z. 124-137) builds 0-point roster from `phaseTeamRepository.findByPhaseId` when standings empty. `templates/site/standings.html:25-28` emits banner. New deterministic fixture `Season 2024 — Empty Phase` (slug `2024-3-season-2024-empty-phase`) added to `TestDataService` (Z. 322-326). SC5 IT `givenEmptyPhaseSeason_whenGenerate_thenEmptyStateBannerAndRosterVisible` (Z. 242-268) asserts heading "No results recorded yet." + ≥4 roster rows. |
| 9  | D-19 alltime aggregation is cross-phase (REGULAR + PLAYOFF + PLACEMENT)                                                 | ✓ VERIFIED | `StandingsService.calculateAlltimeStandings` (Z. 173) loops `seasonPhaseService.findAllPhases(season.getId())` per season. `DriverRankingService.calculateAlltimeRanking(List<UUID>)` (Z. 132-134) calls new finder `raceResultRepository.findByRaceMatchdaySeasonIdIn` (no IsNull filter). `RaceResultRepository.java:43` defines the new finder. Two TDD tests: `givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints`, `givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults`. Tracked-Behavior-Change documented in 62-05-SUMMARY release-notes draft. |
| 10 | JaCoCo line coverage ≥ 82% (QUAL-01 quality gate)                                                                       | ✓ VERIFIED | Plan-7 SUMMARY records `87.24% (5909/6773)` from `./mvnw verify -Pe2e` on commit ancestry of `eb39cf4`. JaCoCo gate passed: "All coverage checks have been met." |

**Score:** 10/10 truths verified

### Required Artifacts

| Artifact                                                                       | Expected                                                                  | Status         | Details |
|--------------------------------------------------------------------------------|---------------------------------------------------------------------------|----------------|---------|
| `src/main/java/org/ctc/sitegen/SiteSlugger.java`                               | Spring-injected slug helper                                               | ✓ VERIFIED     | 27 LOC, `@Component`, `slugify(String)` matches pre-Phase-62 algorithm. |
| `src/main/java/org/ctc/sitegen/TemplateWriter.java`                            | Shared Thymeleaf write surface, two write overloads                       | ✓ VERIFIED     | 47 LOC, `@Service @RequiredArgsConstructor`, two `public void write` overloads. |
| `src/main/java/org/ctc/sitegen/StandingsPageGenerator.java`                    | Phase- and group-aware standings page generator                           | ✓ VERIFIED     | 255 LOC, computes 7 server-side flags, walks phases + groups, writes legacy + per-phase + per-group files. |
| `src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java`                    | Phase- and group-aware matchdays index + per-matchday detail              | ✓ VERIFIED     | 321 LOC, `generateIndex` + `generateDetails`, REGULAR-only legacy default per OQ2. |
| `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java`                | Phase-aware driver-ranking generator                                      | ✓ VERIFIED     | 200 LOC, "All Phases" first tab + per-phase variants, PLAYOFF skip rule when empty. |
| `src/main/java/org/ctc/sitegen/TeamProfilePageGenerator.java`                  | Conditional Phase Breakdown section                                       | ✓ VERIFIED     | 253 LOC, `showPhaseBreakdown` flag (Z. 102), `phaseBreakdown` List<PhaseBreakdownEntry>. |
| `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java`                | Conditional per-phase results sectioning                                  | ✓ VERIFIED     | 136 LOC, `resultsByPhase` LinkedHashMap, `showPhaseBreakdown` flag (Z. 83). |
| `src/main/java/org/ctc/sitegen/model/{PhaseTabView,GroupSubTabView,PhaseBreakdownEntry,GenerationContext}.java` | 4 immutable view records | ✓ VERIFIED | All 4 records exist; `PhaseTabView`/`GroupSubTabView` carry label/href/active/ariaControlsId. |
| `src/main/resources/templates/site/standings.html`                             | Phase-/group-aware template                                               | ✓ VERIFIED     | 59 LOC, all 7 flags consumed, role=tablist + aria-selected. |
| `src/main/resources/templates/site/matchdays.html`                             | Phase-/group-aware index                                                  | ✓ VERIFIED     | Phase + group nav blocks present, role=tablist on both. |
| `src/main/resources/templates/site/driver-ranking.html`                        | Phase-aware (no per-group) template                                       | ✓ VERIFIED     | Phase nav present, no group nav (correct per Plan 3 — drivers span groups). |
| `src/main/resources/templates/site/team-profile.html`                          | Conditional Phase Breakdown section                                       | ✓ VERIFIED     | `<div th:if="${showPhaseBreakdown}">` block with table over `${phaseBreakdown}` (Z. 52-67). |
| `src/main/resources/templates/site/driver-profile.html`                        | Conditional per-phase results sectioning                                  | ✓ VERIFIED     | `<div th:if="${showPhaseBreakdown}" th:each="entry : ${resultsByPhase}">` (Z. 53). |
| `src/main/resources/static/site/css/style.css`                                 | New tab-row + banner classes                                              | ✓ VERIFIED     | `.phase-tab-row`, `.phase-tab-row-inner`, `.phase-tab(.active)`, `.group-tab-row`, `.group-tab-row-inner`, `.group-tab(.active)`, `.empty-phase-banner` all present (lines 984-1088). Plan-7 added desktop sticky `<thead>` + removed `body bg-attachment: fixed`. |
| `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java`             | SC5 cross-cutting regression IT                                           | ✓ VERIFIED     | 305 LOC, 9 `@Test` methods covering SC1-SC5, D-04, D-08, D-19, D-22, D-26. Surefire — runs in default `./mvnw verify`. |
| `src/test/resources/sitegen/baseline/single-league-standings.html`             | Pre-Phase-62 golden snapshot for SC4                                      | ✓ VERIFIED     | 14054 bytes, MD5 `5be57f01bba8f1e91f4db4fd95f2eada` (re-captured in Plan 1 with deterministic-sort fix). |
| `src/test/resources/sitegen/baseline/single-league-team-profile.html`          | Pre-Phase-62 golden for team profile                                      | ✓ VERIFIED     | 6553 bytes, byte-identity test in `TeamProfilePageGeneratorTest`. |
| `src/test/resources/sitegen/baseline/single-league-driver-profile.html`        | Pre-Phase-62 golden for driver profile                                    | ✓ VERIFIED     | 8080 bytes, byte-identity test in `DriverProfilePageGeneratorTest`. |
| `src/main/java/org/ctc/admin/TestDataService.java` — Season 2024 Empty Phase fixture | D-22 deterministic fixture                                          | ✓ VERIFIED     | Class Javadoc Z. 67-68 documents the fixture; constructor at Z. 326. |

### Key Link Verification

| From                                            | To                                                                  | Via                                            | Status     | Details |
|-------------------------------------------------|---------------------------------------------------------------------|------------------------------------------------|------------|---------|
| `SiteGeneratorService.generate()`               | 5 helper beans                                                      | Spring constructor injection                   | ✓ WIRED    | Fields at Z. 45-49; per-season loop calls all 5 helpers (Z. 106-111). |
| `StandingsPageGenerator.generate`               | `standingsService.calculateStandings(phaseId, groupId)`             | Spring service call                            | ✓ WIRED    | Z. 122. Buchholz variant at Z. 121. |
| `StandingsPageGenerator.generate` (empty state) | `phaseTeamRepository.findByPhaseId / ByPhaseIdAndGroupId`           | Spring repository call                         | ✓ WIRED    | Z. 127-129 — D-22 path. |
| `templates/site/standings.html`                 | server-side flags (`showPhaseTabs`, `phaseTabs`, etc.)              | Thymeleaf `th:if` + `th:each`                  | ✓ WIRED    | All 7 flags consumed. |
| `MatchdaysPageGenerator.generateIndex`          | `matchdayRepository.findByPhaseIdOrderBySortIndexAsc` + group var.  | Spring repository call                         | ✓ WIRED    | Z. 80, 94, 104. Legacy default REGULAR-only enforced. |
| `DriverRankingPageGenerator.generate`           | `driverRankingService.aggregateAcrossPhases / calculateRankingForPhase` | Spring service call                        | ✓ WIRED    | Z. 71, 77. |
| `TeamProfilePageGenerator.generate`             | `seasonPhaseService.findAllPhases` for breakdown gating             | Spring service call                            | ✓ WIRED    | Plan 4 SUMMARY confirms `phaseTeamRepository.findByPhaseId` PLAYOFF fallback wired. |
| `DriverProfilePageGenerator.generate`           | per-phase result `Map<PhaseType, List<RaceResult>>`                 | in-Java filter on `RaceResult.race.matchday.phase` | ✓ WIRED    | Plan 4 SUMMARY confirms; template emits `th:each` over `resultsByPhase`. |
| PLAYOFF tab on standings/matchdays              | `playoff.html`                                                      | Thymeleaf `th:href` rendered href              | ✓ WIRED    | `buildPhaseTabs` D-08 branch in both generators. SC5 IT asserts. |
| `StandingsService.calculateAlltimeStandings`    | All-phase loop                                                      | `seasonPhaseService.findAllPhases`             | ✓ WIRED    | Z. 173. |
| `DriverRankingService.calculateAlltimeRanking(seasonIds)` | `raceResultRepository.findByRaceMatchdaySeasonIdIn`        | Spring repository call                         | ✓ WIRED    | Z. 132-134. New finder at `RaceResultRepository.java:43`. |
| `DriverRankingService.calculateRankingForPhase` | `resolveTeamFromLineup(driverId, race)` for per-row team attribution | private method                                | ⚠️ DEAD-STUB | `resolveTeamFromLineup` (Z. 201-203) returns `null` unconditionally. Per-phase rankings ship with Team-column = `-`. See Human-Verification #1. **Not a Phase 62 contract gap** — the legacy aggregated `driver-ranking.html` uses `attributeTeamFromRegularOrLineup` (Z. 91-93) which IS wired. The per-phase variants are a Phase 62 deliverable but the team column is essentially empty there. |

### Data-Flow Trace (Level 4)

| Artifact                                  | Data Variable        | Source                                                          | Produces Real Data | Status |
|-------------------------------------------|----------------------|-----------------------------------------------------------------|--------------------|--------|
| `templates/site/standings.html`           | `${standings}`       | `StandingsService.calculateStandings(phaseId, groupId)` or roster fallback | YES (RaceResult-derived per Plan 5) | ✓ FLOWING |
| `templates/site/standings.html`           | `${phaseTabs}`       | `seasonPhaseService.findAllPhases(season.getId())`             | YES                | ✓ FLOWING |
| `templates/site/standings.html`           | `${groupTabs}`       | `seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phaseId)` | YES (when GROUPS layout) | ✓ FLOWING |
| `templates/site/matchdays.html`           | `${matchdays}`       | `matchdayRepository.findByPhaseIdOrderBySortIndexAsc` + group variant | YES               | ✓ FLOWING |
| `templates/site/driver-ranking.html`      | `${driverRanking}`   | Legacy: `aggregateAcrossPhases`. Per-phase: `calculateRankingForPhase`. | Mostly YES — per-phase rankings have `team=null` rows (W-1) | ⚠️ STATIC for Team column |
| `templates/site/team-profile.html`        | `${phaseBreakdown}`  | `seasonPhaseService.findAllPhases` + `standingsService.calculateStandings` per phase | YES | ✓ FLOWING |
| `templates/site/driver-profile.html`      | `${resultsByPhase}`  | In-Java filter on `RaceResult.race.matchday.phase`              | YES                | ✓ FLOWING |
| `alltime-standings.html`                  | `${alltimeStandings}`| `calculateAlltimeStandings` cross-phase loop                    | YES                | ✓ FLOWING |
| `alltime-driver-ranking.html`             | `${alltimeRanking}`  | `calculateAlltimeRanking(seasonIds)` w/ `findByRaceMatchdaySeasonIdIn` | YES         | ✓ FLOWING |

### Behavioral Spot-Checks

Skipped — Phase 62 produces static HTML files only via `./mvnw verify` test cycle. The IT class `SiteGeneratorPhaseAwarenessIT` runs `siteGeneratorService.generate()` against a `@TempDir` and asserts on the produced files; this is the canonical behavioral verification. No long-running server is required for verification.

| Behavior                                                  | Evidence                                                              | Status |
|-----------------------------------------------------------|-----------------------------------------------------------------------|--------|
| Site generation produces phase-aware standings files       | SC5 IT method `givenGroupsLayoutSeason_whenGenerate_thenPerGroupAndCombinedFilesExist` | ✓ PASS |
| Multi-phase season emits phase-tab row with role=tablist  | SC5 IT method `givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisible` | ✓ PASS |
| PLAYOFF tab href = "playoff.html"                         | SC5 IT method `givenMultiPhaseSeason_whenGenerate_thenPlayoffTabLinksToBracket` | ✓ PASS |
| LEAGUE-only standings byte-identical to baseline           | SC5 IT method `givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline` | ✓ PASS |
| Empty-phase banner + roster                               | SC5 IT method `givenEmptyPhaseSeason_whenGenerate_thenEmptyStateBannerAndRosterVisible` | ✓ PASS |
| `./mvnw verify -Pe2e` green                                | Plan-7 SUMMARY: 1215 Surefire + 31 Failsafe = 1246, 0 failures, JaCoCo 87.24% | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan         | Description                                                                             | Status         | Evidence |
|-------------|--------------------|-----------------------------------------------------------------------------------------|----------------|----------|
| **UI-02**   | 62-01, 62-02, 62-04 | Saison-Detail mit Phasen-Tabs; bei GROUPS-Phase zweite Tab-Ebene (mirrored on public site) | ✓ SATISFIED    | `templates/site/standings.html`, `matchdays.html` emit phase-tab + group-tab rows. SC5 IT cross-cutting assertion. |
| **UI-05**   | 62-01, 62-02, 62-03, 62-04 | Standings-UI mit Phase-/Group-Auswahl + Combined-View-Tab (mirrored)             | ✓ SATISFIED    | StandingsPageGenerator computes `phaseTabs`+`groupTabs`+`showGroupColumn`; "Combined" sub-tab is the first entry in `buildGroupTabs` (Z. 234). |
| **UI-07**   | 62-01, 62-02, 62-06 | Playoff-UI auf PLAYOFF-Phase umgestellt (mirrored — PLAYOFF tab routes to `playoff.html`, no `standings-playoff.html`) | ✓ SATISFIED    | D-08 enforced. SC5 IT method `givenMultiPhaseSeason_whenGenerate_thenPlayoffTabLinksToBracket` asserts both branches. |
| **QUAL-01** | 62-00, 62-05        | JaCoCo Line-Coverage ≥ 82%                                                              | ✓ SATISFIED    | Plan-7 final verify: 87.24% line coverage. JaCoCo gate passed. |
| **QUAL-02** | 62-06, 62-07        | SC5 cross-cutting regression IT (and visual sweep)                                      | ✓ SATISFIED    | `SiteGeneratorPhaseAwarenessIT.java` (305 LOC, 9 tests) consolidates SC1-SC5 + D-22 + D-26 + D-04 + D-19 sanity. Plan-7 visual sweep done. |

No orphaned requirements — all 5 IDs from the phase prompt resolve to passing must-haves.

### Anti-Patterns Found

| File                                                                            | Line     | Pattern                                                                            | Severity   | Impact |
|---------------------------------------------------------------------------------|----------|------------------------------------------------------------------------------------|------------|--------|
| `src/main/java/org/ctc/domain/service/DriverRankingService.java`                | 201-203  | `private Team resolveTeamFromLineup(...) { return null; }` — dead stub             | ⚠️ Warning | Per-phase `driver-ranking-{phaseSlug}.html` shows `-` in the Team column for every row. The page is generated and routed, but its central data column is hollow. **Not a Phase 62 contract failure** — the legacy `driver-ranking.html` and the phase-tab routing work. But the per-phase variant page is visibly broken. REVIEW W-1. |
| `src/main/java/org/ctc/domain/service/StandingsService.java`                    | 240-247  | `calculateBuchholzScoresForPhase` delegates to `calculateBuchholzScores(seasonId)` (REGULAR-only) | ⚠️ Warning | If a future caller invokes Buchholz on a non-REGULAR phase, the result is silently REGULAR-only. **Currently no caller does this on alltime cross-phase**, so the bug is dormant. REVIEW W-2. |
| `src/main/java/org/ctc/domain/service/DriverRankingService.java`                | 185-193  | `attributeTeamFromRegularOrLineup` falls back to `lineups.get(0)` w/o ORDER BY     | ℹ️ Info     | Cross-team stand-ins may produce flaky alltime/season-aggregated team attribution. REVIEW W-3. |
| `src/main/java/org/ctc/domain/service/DriverRankingService.java`                | 151-153  | `Comparator.comparing(sd -> sd.getSeason().getName())` — lexicographic season sort | ℹ️ Info     | "Most recent" team for cross-season drivers depends on Season.name string ordering, not year/number. REVIEW W-4. |
| 6 phase-aware ITs each `Flyway.clean()+migrate()` per `@BeforeAll`             | various  | CI-time multiplier                                                                 | ℹ️ Info     | 30+s overhead on `./mvnw verify`. Functionally correct. REVIEW W-5. |
| `SiteGeneratorService` + `TeamProfilePageGenerator` both contain `copyLogoToAssets` | x2 27 LOC | Code duplication                                                                  | ℹ️ Info     | Documented design decision (RESEARCH.md choice b). Future maintenance must touch both copies. REVIEW IN-2. |
| `templates/site/standings.html`, `matchdays.html`                                | 5, 14, 25 | No whitespace between adjacent tags inside `<section>`                            | ℹ️ Info     | Required to satisfy SC4 byte-identity. Documented; not a defect. REVIEW IN-6. |

**Critical anti-patterns: 0 blockers.** All findings are at most ⚠️ Warning severity and none invalidate the Phase 62 phase contract.

### Human Verification Required

See `human_verification:` block in frontmatter. Four items, all visual / behavioral judgment calls that automated grep / structural assertions cannot resolve:

1. **Driver-Ranking Team column on per-phase variants** — REVIEW W-1 confirms the dead stub. Open one rendered `driver-ranking-regular.html` and confirm the Team column is dashes only. If acceptable → ship; if not → micro-fix to `resolveTeamFromLineup` is the right scope (one call to `raceLineupRepository.findByRaceIdAndDriverId`).
2. **D-19 numeric delta on alltime pages** — confirm with a real screenshot that PLAYOFF/PLACEMENT points are now flowing.
3. **Mobile layout sanity for two stacked tab rows** — Plan 7 claims green, no quantitative evidence.
4. **Real-browser PLAYOFF tab click-through** — IT asserts href, not navigation.

### Gaps Summary

**No must-have FAILED.** All 10 truths verify against codebase evidence. All 5 requirements (UI-02, UI-05, UI-07, QUAL-01, QUAL-02) are satisfied with code + test artifacts. The phase goal — "the public static site exposes the same phase/group model that Phase 60 introduced on the admin side" — is delivered.

**However, status is `human_needed` because:**

- REVIEW.md surfaces W-1 (dead-stub `resolveTeamFromLineup`) which means the per-phase `driver-ranking-{phaseSlug}.html` Team column is likely all dashes. The page is generated and routed (Phase 62 contract delivered), but the page content quality is unclear without a human visual judgment.
- Plan 7 SUMMARY claims visual sweep across 41 screenshots delivered the "phase 62 contract" but does not record before/after numeric deltas for D-19, nor concrete click-through evidence for PLAYOFF tab navigation. The orchestrator should confirm these visually before merging the v1.9 release.
- W-2 (PLAYOFF Buchholz) is dormant — does not affect Phase 62 contract today, but the user should be aware before the next phase potentially reaches it.

The gap items are NOT phase-blocking — they are quality-bar judgment calls that need human eyes on the rendered site before the v1.9 milestone tag.

---

_Verified: 2026-05-07T07:33:49Z_
_Verifier: Claude (gsd-verifier)_
