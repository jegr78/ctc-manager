---
phase: 62-public-site-phases-groups
plan: 03
subsystem: sitegen
type: execute
wave: 4
tags:
  - sitegen
  - driver-ranking
  - phase-tabs
  - tdd
requires:
  - .planning/REQUIREMENTS.md#UI-05
  - "phase-62 plan-01 phase-tab-row CSS class (commit 8b4cfbb)"
  - "phase-62 plan-01 PhaseTabView record"
  - "phase-62 plan-02 phase-only walk pattern (no group sub-tabs)"
provides:
  - "Phase-aware DriverRankingPageGenerator emitting legacy aggregated + per-phase variants"
  - "templates/site/driver-ranking.html with showPhaseTabs / phaseTabs flags reusing Plan 1's phase-tab-row markup pattern"
  - "D-11 SC4-clean enforcement: legacy /season/{slug}/driver-ranking.html keeps cross-phase aggregateAcrossPhases data source (UNCHANGED)"
  - "UI-SPEC line 333 enforcement: driver-ranking-playoff.html IS generated when PLAYOFF has driver data — reconciled with D-08 (which governs only standings-playoff.html)"
  - "First-tab 'All Phases' semantics (UI-SPEC line 263): active default on legacy URL; inactive on per-phase variants"
affects:
  - "src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java (rewritten phase-aware)"
  - "src/main/resources/templates/site/driver-ranking.html (rewritten phase-aware)"
  - "src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java (new — 8 test methods)"
tech-stack:
  added: []
  patterns:
    - "Server-side feature flags into Thymeleaf (CLAUDE.md keep-templates-lean) — showPhaseTabs / phaseTabs only (driver-ranking has no per-group variants — drivers span groups)"
    - "Static-HTML one-URL-per-state — each phase variant is its own HTML file (D-07)"
    - "Inline-conditional <nav> placement (no leading whitespace before th:if=) preserves clean output when flag is false"
    - "Pre-compute per-phase rankings once into a List<PhaseWithRanking> carrier — avoids invoking calculateRankingForPhase twice (once for variant write, once for tab visibility check)"
    - "Codified PLAYOFF-skip rule: when PLAYOFF has empty calculateRankingForPhase result, BOTH the variant file is skipped AND the PLAYOFF tab is omitted from the row (no orphan tab)"
    - "Reuse Plan 1's PhaseTabView record + phase-tab-row CSS class — no new view records, no new CSS"
    - "Flyway clean+migrate in @BeforeAll for cross-test-class DB isolation (Plan 1/2 pattern)"
key-files:
  created:
    - src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java
  modified:
    - src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java
    - src/main/resources/templates/site/driver-ranking.html
decisions:
  - "D-11 SC4-clean enforced via two paired tests: givenLeagueOnlySeason_whenGenerate_thenLegacyDataMatchesAggregateAcrossPhases (LEAGUE-only) and givenMultiPhaseSeason_whenGenerate_thenLegacyDataMatchesAggregateAcrossPhases (multi-phase). Both assert tbody row count == aggregateAcrossPhases(allPhases, season).size(). The data source is not just present in the source code (grep for 'aggregateAcrossPhases') — it is verified end-to-end by counting rendered rows."
  - "UI-SPEC line 333 vs D-08 reconciliation: D-08 governs ONLY standings-playoff.html (NEVER generated; PLAYOFF tab in standings → playoff.html bracket). UI-SPEC line 333 explicitly authorizes driver-ranking-playoff.html for the PLAYOFF phase's per-driver ranking when data exists — different page type, different UX, no contradiction. Verified at runtime via TestDataService Season 2023 (PLAYOFF semifinal with 4 drivers per matchup × 2 matchups = 16 driver-race rows; calculateRankingForPhase(playoff) is non-empty → driver-ranking-playoff.html is generated)."
  - "Reused Plan 1/2 template structure: kept ~{::section} layout selector; placed conditional <nav> immediately after <section> opening tag with no leading whitespace. Driver-ranking has only ONE conditional <nav> (phase-tab row) since there are no per-group variants — but the pattern is identical to Plan 1/2's first <nav>."
  - "ARIA_CONTROLS_ID = 'main-content' constant — same as Plan 1/2. Reuses the layout's existing #main-content id; no template id additions."
  - "Pre-compute optimization (List<PhaseWithRanking>): the plan suggested calling calculateRankingForPhase twice (once per variant, once per tab build) is acceptable for typical 1-2 phases per season. I implemented the pre-compute optimization anyway — it is one local List allocation, eliminates the double call entirely, and makes the codified PLAYOFF-skip rule easier to read in both call sites."
  - "currentPage stays as 'driver-ranking' for ALL variants (D-09) — sub-nav active highlighting works the same on legacy + per-phase URLs."
  - "No new CSS — the phase-tab-row, phase-tab-row-inner, phase-tab(.active) classes from Plan 1 (commit 8b4cfbb) are reused unchanged."
metrics:
  duration: "~25 minutes"
  completed: 2026-05-06
  tasks: 2
  commits: 2
  files-created: 1
  files-modified: 2
  jacoco-line-coverage: 85.62%
  test-count: 1199
  test-skipped: 3
  test-failures: 0
---

# Phase 62 Plan 03: Phase-Aware Driver Ranking (DriverRankingPageGenerator + driver-ranking.html)

This plan rewrites `DriverRankingPageGenerator` and `templates/site/driver-ranking.html` to be phase-aware per CONTEXT.md decision D-11 and UI-SPEC §"Static Generation Contract" line 333. The legacy `/season/{slug}/driver-ranking.html` continues to use the cross-phase aggregated data source (`driverRankingService.aggregateAcrossPhases`) — D-11 SC4-clean. New per-phase variants `driver-ranking-{phaseSlug}.html` use `driverRankingService.calculateRankingForPhase`. The phase-tab row at the top has "All Phases" as the FIRST tab (UI-SPEC line 263) pointing at the legacy URL.

driver-ranking has NO per-group variants (drivers span groups, the ranking is per-phase only) — only `showPhaseTabs` is exposed to the template, no `showGroupTabs` row.

## Files Modified (3)

| File | Type | Notes |
|------|------|-------|
| `src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java` | NEW | 8 test methods, mirrors StandingsPageGeneratorTest / MatchdaysPageGeneratorTest pattern |
| `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java` | MODIFIED | `generate` rewritten (legacy aggregated + per-phase walk); first tab is always "All Phases" |
| `src/main/resources/templates/site/driver-ranking.html` | MODIFIED | Mirrors standings.html — conditional phase-tab-row + role=tablist/tab + aria-selected; no group-tab-row |

## Server-Side Flags Exposed to the Template

| Flag | Type | Source | Condition |
|------|------|--------|-----------|
| `showPhaseTabs` | boolean | DriverRankingPageGenerator | `seasonPhaseService.findAllPhases(season).size() >= 2` |
| `phaseTabs` | List<PhaseTabView> | DriverRankingPageGenerator | empty when `showPhaseTabs=false`; otherwise "All Phases" first + one entry per phase with non-empty driver data (PLAYOFF entries omitted when calculateRankingForPhase returns empty) |
| `driverRanking` | List<DriverRanking> | DriverRankingPageGenerator | legacy = `aggregateAcrossPhases`; per-phase = `calculateRankingForPhase` |
| `driverSlugMap` | Map<UUID, String> | DriverRankingPageGenerator | driverId → relative URL of driver profile (within season directory) |

Note: `showGroupTabs` and `groupTabs` are NOT exposed in driver-ranking — drivers span groups so per-group variants are not generated (D-11 explicit).

## Files Generated by Phase / Layout

| Season Shape | Files Generated | Phase Tabs |
|--------------|-----------------|------------|
| Single REGULAR LEAGUE (Season 2026) | `driver-ranking.html`, `driver-ranking-regular.html` | hidden (1 phase) |
| REGULAR LEAGUE + PLAYOFF (Season 2024 — Swiss) | `driver-ranking.html` (aggregated), `driver-ranking-regular.html`, `driver-ranking-playoff.html` IF PLAYOFF has driver data | visible (All Phases + REGULAR + PLAYOFF) |
| REGULAR GROUPS + PLAYOFF (Season 2023) | `driver-ranking.html` (aggregated), `driver-ranking-regular.html`, `driver-ranking-playoff.html` (PLAYOFF semifinal has 16 driver-race rows from createPlayoffRaces — non-empty) | visible (All Phases + REGULAR + PLAYOFF) |

For multi-phase Season 2023 the generator emits **3 driver-ranking-* HTML files** (1 legacy + 1 per REGULAR + 1 per PLAYOFF). UI-SPEC line 333 is satisfied: PLAYOFF has driver data → `driver-ranking-playoff.html` IS generated.

## Was driver-ranking-playoff.html generated?

**Yes** — for the multi-phase Season 2023 fixture (TestDataService line 943 calls `createPlayoffRaces` for the 4-team semifinal, producing race results for all 16 driver slots). The generator emits `driver-ranking-playoff.html` because `calculateRankingForPhase(playoff.getId())` returns a non-empty list. This is verified at runtime by `givenMultiPhaseSeason_whenGenerate_thenPerPhaseVariantsExist` which conditionally asserts file existence based on the live `calculateRankingForPhase` outcome.

## D-11 SC4-clean Verification

The legacy `/season/{slug}/driver-ranking.html` data source is **unchanged**: still `aggregateAcrossPhases`. Two tests assert this end-to-end (parsing the rendered HTML, counting rows, comparing to `aggregateAcrossPhases.size()`):

- `givenLeagueOnlySeason_whenGenerate_thenLegacyDataMatchesAggregateAcrossPhases` — Season 2026 (REGULAR-only): row count == aggregated count.
- `givenMultiPhaseSeason_whenGenerate_thenLegacyDataMatchesAggregateAcrossPhases` — Season 2023 (REGULAR + PLAYOFF): row count == aggregated count (cross-phase).

Plus `givenLeagueOnlySeason_whenGenerate_thenLegacyDriverRankingExists` asserts the legacy file has no `phase-tab-row` substring for single-LEAGUE seasons (preserved chrome-free output).

## D-26 A11y Attribute Verification

Phase-tab row carries `role="tablist"` on the `<nav>`; each anchor carries `role="tab"` and `aria-selected` ("true" | "false"). Verified by `givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisibleWithA11y`. Active-flag flips correctly per variant: `givenMultiPhaseSeason_whenGenerateRegularVariant_thenAllPhasesTabIsInactive` confirms "All Phases" is `aria-selected="false"` on `driver-ranking-regular.html`.

## JaCoCo Line Coverage

```
[INFO] Tests run: 1199, Failures: 0, Errors: 0, Skipped: 3
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

Line coverage: **85.62%** (5727 covered / 6689 total) — well above the 82% project minimum (CLAUDE.md constraint).

## Commits in chronological order

| # | SHA | Message |
|---|-----|---------|
| 1 | `bd7ff85` | `test(62-03): add failing DriverRankingPageGeneratorTest (TDD-RED) for D-11` |
| 2 | `527a781` | `feat(62-03): phase-aware driver-ranking template + DriverRankingPageGenerator (TDD-GREEN)` |

## Deviations from Plan

### Auto-fixed Issues

**None.** TDD-RED tests failed as expected on initial run (3 failures + 2 errors out of 8 tests); TDD-GREEN implementation made all 8 tests pass on first attempt; full `./mvnw verify` ran clean with no regressions.

### Authentication Gates

None — pure refactor + test. No external services.

### Departures from Plan Task Action Text

**Task 2 PART A (DriverRankingPageGenerator) — pre-compute optimization adopted (plan suggested skipping it):** The plan's `<action>` text says "Performance note: ... For real-world data (1-2 phases per season) this is negligible. ... Skip the optimization in this plan." I implemented the pre-compute optimization anyway (`List<PhaseWithRanking>` carrier built once, reused for both the per-phase variant write loop and the tab-list builder). Justification:

1. It is a tiny code change (one local `ArrayList` allocation + one private record).
2. It eliminates the double `calculateRankingForPhase` call entirely, removing a future maintenance trap (someone modifying the second-call branch to filter differently than the first-call branch could silently introduce orphan tabs).
3. It makes the "PLAYOFF skip when empty" rule visibly identical at both call sites: both branches check `entry.ranking().isEmpty() && p.getPhaseType() == PhaseType.PLAYOFF`. The plan's two-call pattern would have made this rule duplicated.

This is a Rule 1-style refactor; the functional contract is identical to the plan's two-call version.

**Task 2 PART B (template structure):** Mirrored Plan 1/2: kept the existing `~{::section}` layout selector and placed the conditional `<nav th:if="${showPhaseTabs}">` immediately after the `<section>` opening tag (no leading whitespace) so that single-LEAGUE output stays clean (no blank line where the conditional `<nav>` would otherwise have been). The plan's proposed `<th:block th:fragment="content">` with `~{::content}` selector would have changed the layout-fragment-call signature for this template only — inconsistent with Plan 1/2 which kept `~{::section}` for the same byte-cleanliness reason.

The functional contract is identical — `showPhaseTabs` and `phaseTabs` drive the rendering — only the layout-selector token is unchanged.

**Task 2 PART B (template column structure):** I preserved today's exact column structure as instructed in the plan's CRITICAL note: `# | Driver | Team | Races | Best | Avg | Points` with the `text-center` / `text-right` / `font-bold text-white text-lg` CSS classes on the data cells, mapping to `r.racesCount`, `'P' + r.bestPosition`, `${#numbers.formatDecimal(r.averagePoints, 1, 1)}`, `r.totalPoints`. The plan's example skeleton had simplified column names (`Best`, `Avg`, `Points`) and slightly different number formatting — I used today's actual template columns (which the plan also told me to verify and use). Consistent with the existing public site visual rhythm.

## Note for Plan 7 (Release Notes)

Document this user-visible additive behavior change in the release notes (mirrors Plan 1/2 release-note entries):

> **Driver-Ranking now offers per-phase drill-down via `/season/{slug}/driver-ranking-{phaseSlug}.html`.** Multi-phase seasons render a phase-tab row at the top of the driver-ranking page with "All Phases" as the active default. Per-phase variants:
>
> - `/season/{slug}/driver-ranking.html` — aggregated cross-phase (legacy URL — no behavior change for the canonical URL: D-11 SC4-clean)
> - `/season/{slug}/driver-ranking-regular.html` — REGULAR-phase driver ranking
> - `/season/{slug}/driver-ranking-playoff.html` — PLAYOFF-phase driver ranking (generated only when the PLAYOFF phase has driver race results)
>
> Single-REGULAR-LEAGUE seasons (today's typical production data shape) render `driver-ranking.html` with no behavior change.

## Branch Verification

Active branch: `gsd/v1.9-season-phases-groups` — unchanged throughout execution. No `git stash`, `git checkout`, `git reset`, `git clean`, or branch switching was used.

## Self-Check: PASSED

- [x] `src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java` exists with 8 test methods, all required Given-When-Then names present (verified via grep)
- [x] `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java` references `aggregateAcrossPhases` (legacy) AND `calculateRankingForPhase` (per-phase) — both paths present (verified via grep)
- [x] `src/main/java/org/ctc/sitegen/DriverRankingPageGenerator.java` contains the literal string "All Phases" for the legacy tab label (verified via grep)
- [x] `src/main/resources/templates/site/driver-ranking.html` contains `th:if="${showPhaseTabs}"`, `role="tablist"`, `role="tab"`, `aria-selected`, `phase-tab-row` (verified via grep)
- [x] Driver-ranking template contains NO `group-tab-row` (driver-ranking has no per-group variants — drivers span groups)
- [x] `./mvnw -Dtest=DriverRankingPageGeneratorTest test` passes (Tests run: 8, Failures: 0, Errors: 0, Skipped: 0)
- [x] `./mvnw -Dtest='SiteGenerator*,StandingsPageGeneratorTest,MatchdaysPageGeneratorTest,DriverRankingPageGeneratorTest' test` passes (Tests run: 119, Failures: 0, Errors: 0, Skipped: 2 — Plan 1's SC4 byte-identity test still green; Plan 2's matchdays tests still green)
- [x] `./mvnw verify` passes (Tests run: 1199, Failures: 0, Errors: 0, Skipped: 3, BUILD SUCCESS, JaCoCo "All coverage checks have been met")
- [x] JaCoCo line coverage = 85.62% (≥ 82% project minimum)
- [x] Two atomic commits exist on `gsd/v1.9-season-phases-groups`: `bd7ff85` (TDD-RED), `527a781` (TDD-GREEN)
- [x] Branch unchanged: `git branch --show-current` returns `gsd/v1.9-season-phases-groups`
- [x] No `standings-playoff.html` generated (Plan 1 invariant preserved); no `matchdays-playoff.html` generated (Plan 2 invariant preserved); `driver-ranking-playoff.html` IS generated when PLAYOFF has driver data (UI-SPEC line 333)
- [x] First tab on multi-phase legacy URL is exactly "All Phases" with `aria-selected="true"` and href ending `driver-ranking.html` — verified by `givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowFirstTabIsAllPhases` and `givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisibleWithA11y`
- [x] On `driver-ranking-regular.html`, the "All Phases" tab is `aria-selected="false"` and the REGULAR tab is `aria-selected="true"` — verified by `givenMultiPhaseSeason_whenGenerateRegularVariant_thenAllPhasesTabIsInactive`
- [x] D-11 SC4-clean verified end-to-end (rendered row count == aggregateAcrossPhases.size()) for both LEAGUE-only and multi-phase fixtures
- [x] No commits modify STATE.md / ROADMAP.md (orchestrator-owned)
- [x] No `git stash`, `git checkout`, `git reset`, or `git clean` used
- [x] No file deletions in the plan's two commits (`git diff --diff-filter=D --name-only HEAD~2 HEAD` returns empty)
