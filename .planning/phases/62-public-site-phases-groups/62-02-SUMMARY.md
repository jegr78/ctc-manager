---
phase: 62-public-site-phases-groups
plan: 02
subsystem: sitegen
type: execute
wave: 3
tags:
  - sitegen
  - matchdays
  - phase-tabs
  - group-tabs
  - tdd
requires:
  - .planning/REQUIREMENTS.md#UI-02
  - .planning/REQUIREMENTS.md#UI-05
  - .planning/REQUIREMENTS.md#UI-07
  - "phase-62 plan-01 phase-tab-row + group-tab-row CSS classes (commit 8b4cfbb)"
  - "phase-62 plan-01 PhaseTabView / GroupSubTabView records"
  - "phase-62 plan-01 StandingsService deterministic-sort tiebreaker"
provides:
  - "Phase- and group-aware MatchdaysPageGenerator.generateIndex emitting legacy + per-phase + per-group matchdays.html files"
  - "templates/site/matchdays.html with showPhaseTabs / showGroupTabs / phaseTabs / groupTabs flags reusing Plan 1's standings.html markup pattern"
  - "Open Question 2 contract enforcement: legacy /season/{slug}/matchdays.html lists REGULAR-phase matchdays only"
  - "D-08 invariant enforcement: matchdays-playoff.html is NEVER generated; PLAYOFF tab href = playoff.html"
affects:
  - "src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java (rewritten generateIndex; generateDetails unchanged)"
  - "src/main/resources/templates/site/matchdays.html (rewritten phase- and group-aware)"
  - "src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java (new — 8 test methods)"
tech-stack:
  added: []
  patterns:
    - "Server-side feature flags into Thymeleaf (CLAUDE.md keep-templates-lean) — showPhaseTabs / showGroupTabs / phaseTabs / groupTabs"
    - "Static-HTML one-URL-per-state — each phase/group variant is its own HTML file (D-07)"
    - "Inline-conditional <nav> placement (no leading whitespace before th:if=) preserves clean output when flag is false"
    - "Flyway clean+migrate in @BeforeAll for cross-test-class DB isolation (Plan 1 pattern)"
    - "Reuse Plan 1's PhaseTabView / GroupSubTabView records — no new view records needed"
    - "Reuse Plan 1's CSS classes (phase-tab-row, phase-tab-row-inner, phase-tab(.active), group-tab-row, group-tab-row-inner, group-tab(.active)) — no new CSS"
key-files:
  created:
    - src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java
  modified:
    - src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java
    - src/main/resources/templates/site/matchdays.html
decisions:
  - "Open Question 2 lock enforced via the helper test givenMultiPhaseSeason_whenGenerateIndex_thenLegacyContainsOnlyRegularPhaseMatchdays — the legacy /season/{slug}/matchdays.html for Season 2023 lists 6 REGULAR rows (Group A 1-3 + Group B 1-3), NOT 7 (which would include the '2023 Playoffs' PLAYOFF matchday). MatchdaysPageGenerator.generateIndex calls findByPhaseIdOrderBySortIndexAsc(regularPhase.getId()) for the legacy file, never findBySeasonIdOrderBySortIndexAsc(seasonId)."
  - "Per-matchday detail pages stay phase-agnostic (URL pattern /season/{slug}/matchday/{matchday-slug}.html unchanged) — generateDetails() body is byte-identical to the post-Plan-0 implementation. Detail-page slugs are unique per season so no phase-suffixing is needed."
  - "Mirrored Plan 1's standings.html template structure: keep ~{::section} layout selector; place conditional <nav> rows immediately after <section> opening tag with no leading whitespace (preserves clean output when th:if=false on single-LEAGUE seasons)."
  - "ARIA_CONTROLS_ID = 'main-content' — same constant as Plan 1's StandingsPageGenerator. Reuses the layout's existing #main-content id; no template id additions."
  - "Reused Plan 1's deterministic-sort fix on StandingsService transitively — test fixture seed determinism is now reproducible across runs, no new tiebreaker work in Plan 2."
  - "Refined the legacy-only-regular-matchdays assertion to scope on <tbody> rather than full document text. The PLAYOFF phase's display label '2023 Playoffs' (PhaseLabel set by PlayoffService.createPlayoff) is the same string as the PLAYOFF matchday label, so a full-text assertion would falsely fail because the phase tab in the page chrome is allowed (and required by D-04) to surface that label. The tbody-scoped check exactly captures the contract: PLAYOFF matchdays must not appear in the matchday list."
metrics:
  duration: "~16 minutes"
  completed: 2026-05-06
  tasks: 2
  commits: 2
  files-created: 1
  files-modified: 2
  jacoco-line-coverage: 85.54%
  test-count: 1191
  test-skipped: 3
  test-failures: 0
---

# Phase 62 Plan 02: Phase- and Group-Aware Matchdays (MatchdaysPageGenerator + matchdays.html)

This plan rewrites `MatchdaysPageGenerator.generateIndex` and `templates/site/matchdays.html` to be phase- and group-aware per CONTEXT.md decisions D-04..D-08, D-10, D-12, D-26, reusing the CSS classes and Thymeleaf markup pattern established in Plan 1. The legacy `/season/{slug}/matchdays.html` lists REGULAR-phase matchdays only (Open Question 2 lock — for consistency with `standings.html` legacy = REGULAR-combined). Per-phase variants `matchdays-{phaseSlug}.html` and per-group variants `matchdays-{phaseSlug}-group-{groupSlug}.html` are additive. Per-matchday detail pages stay phase-agnostic.

## Files Modified (3)

| File | Type | Notes |
|------|------|-------|
| `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java` | NEW | 8 test methods, mirrors StandingsPageGeneratorTest pattern |
| `src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java` | MODIFIED | `generateIndex` rewritten (phase + group walk); `generateDetails` byte-identical |
| `src/main/resources/templates/site/matchdays.html` | MODIFIED | Mirrors standings.html — conditional phase-tab-row + group-tab-row + role=tablist/tab + aria-selected |

## Open Question 2 Lock — Confirmation

The legacy `/season/{slug}/matchdays.html` for multi-phase seasons lists REGULAR-phase matchdays only.

**Test method asserting this contract:** `givenMultiPhaseSeason_whenGenerateIndex_thenLegacyContainsOnlyRegularPhaseMatchdays`
(file: `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java`)

It asserts:
- `tbody` text does NOT contain `"2023 Playoffs"` (the PLAYOFF matchday label from TestDataService line 933)
- `tbody tr` count == 6 (Group A — Matchday 1/2/3 + Group B — Matchday 1/2/3 = 6 REGULAR matchdays)

The `MatchdaysPageGenerator.generateIndex` implementation passes `matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regularPhase.getId())` to the legacy `matchdays.html` writer, never `findBySeasonIdOrderBySortIndexAsc(seasonId)`.

## Files Generated by Phase / Layout

| Season Shape | Files Generated | Phase Tabs | Group Tabs |
|--------------|-----------------|-----------|------------|
| Single REGULAR LEAGUE (Season 2026) | `matchdays.html`, `matchdays-regular.html` | hidden (1 phase) | hidden |
| REGULAR LEAGUE + PLAYOFF (Season 2024 — Swiss) | `matchdays.html`, `matchdays-regular.html` (PLAYOFF tab links to existing `playoff.html`, no `matchdays-playoff.html`) | visible (REGULAR + PLAYOFF) | hidden |
| REGULAR GROUPS + PLAYOFF (Season 2023) | `matchdays.html` (REGULAR-only combined), `matchdays-regular.html` (combined), `matchdays-regular-group-group-a.html`, `matchdays-regular-group-group-b.html` (no `matchdays-playoff.html`) | visible (REGULAR + PLAYOFF) | visible (Combined + Group A + Group B) |

Multi-phase Season 2023 generates **4 matchdays-* HTML files** (1 legacy + 1 per-phase + 2 per-group). The PLAYOFF tab in the phase-tab row links directly to `playoff.html`; no `matchdays-playoff.html` is ever generated (D-08 invariant).

## D-08 Invariant Verification

`matchdays-playoff.html` is NEVER generated. The PLAYOFF tab in the phase-tab row at the top of `matchdays.html` (and `matchdays-regular.html`) links directly to `playoff.html`.

**Test methods asserting this:**
- `givenMultiPhaseSeason_whenGenerateIndex_thenPerPhaseVariantsExist` — asserts `matchdays-playoff.html` does NOT exist
- `givenMultiPhaseSeason_whenGenerateIndex_thenPhaseTabRowVisible` — asserts the PLAYOFF tab href ends with `playoff.html`

Implementation: `MatchdaysPageGenerator.generateIndex` skips PLAYOFF in the per-phase loop (`if (phase.getPhaseType() == PhaseType.PLAYOFF) continue;`).

## SC4 Backward-Compat Verification

Single-REGULAR-LEAGUE seasons render `matchdays.html` with no tab rows. The `givenLeagueOnlySeason_whenGenerateIndex_thenNoTabRowAndNoGroupRow` test asserts neither `phase-tab-row` nor `group-tab-row` substrings appear in the rendered HTML for Season 2026.

## D-26 A11y Attribute Verification

Phase-tab and group-sub-tab rows carry `role="tablist"` on the `<nav>`; each anchor carries `role="tab"` and `aria-selected` ("true" | "false"). Verified by `givenMultiPhaseSeason_whenGenerateIndex_thenTabRowHasA11yAttributes`.

## JaCoCo Line Coverage

```
[INFO] Tests run: 1191, Failures: 0, Errors: 0, Skipped: 3
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

Line coverage: **85.54%** (5686 covered / 6647 total) — well above the 82% project minimum (CLAUDE.md constraint).

## Commits in chronological order

| # | SHA | Message |
|---|-----|---------|
| 1 | `60fb262` | `test(62-02): add failing MatchdaysPageGeneratorTest (TDD-RED) for D-10 phase-aware matchdays` |
| 2 | `38f7f4a` | `feat(62-02): phase- and group-aware matchdays template + MatchdaysPageGenerator.generateIndex (TDD-GREEN)` |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] TDD-RED test compilation: `IOException` not thrown by `siteGeneratorService.generate()`**

- **Found during:** Task 1 (first `./mvnw -Dtest=MatchdaysPageGeneratorTest test` run)
- **Issue:** The plan's Task 1 skeleton uses `try { siteGeneratorService.generate(); } catch (IOException e) { ... }`. After Plan 0's helper-class extraction, `SiteGeneratorService.generate()` declares `throws Exception` (not `throws IOException`), so `catch (IOException e)` raises `exception java.io.IOException is never thrown in body of corresponding try statement` (compile error).
- **Fix:** Changed `catch (IOException e)` to `catch (Exception e)` (matches Plan 1's StandingsPageGeneratorTest pattern at line 82).
- **Files modified:** `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java`
- **Commit:** `60fb262`

**2. [Rule 1 — Bug] Test assertion overspecified for `givenMultiPhaseSeason_whenGenerateIndex_thenLegacyContainsOnlyRegularPhaseMatchdays`**

- **Found during:** Task 2 (TDD-GREEN run — 7 of 8 tests passed; this test failed)
- **Issue:** The plan's skeleton asserts `doc.text()` does not contain `"2023 Playoffs"`. After implementation, the page chrome (specifically the PLAYOFF phase tab in the phase-tab row) renders the PLAYOFF phase's display label, which is `"2023 Playoffs"` (set by `PlayoffService.createPlayoff(s1.getId(), "2023 Playoffs", 4)` at TestDataService line 929). The phase-label string and the PLAYOFF matchday label are coincidentally identical because both derive from the same source. The full-document `doc.text()` check would falsely fail because the phase tab in the chrome is allowed (and REQUIRED by D-04) to surface that label.
- **Fix:** Refined the assertion to scope on `tbody` only, plus an exact-row-count assertion (`tbody tr` size == 6 = Group A 1-3 + Group B 1-3). The contract — "PLAYOFF matchdays must not appear in the matchday LIST" — is now precisely captured.
- **Files modified:** `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java`
- **Commit:** `38f7f4a`
- **Test impact:** All 8 MatchdaysPageGeneratorTest methods pass; assertion is more precise than the original.

### Authentication Gates

None — pure refactor + test. No external services.

### Departures from Plan Task Action Text

**Task 2 PART B (template structure):** The plan's proposed template uses `<th:block th:fragment="content">` with a layout call of `~{::content}`. I instead **kept the existing `~{::section}` selector** and placed all conditional content inside the existing `<section>`. This mirrors what Plan 1 did for standings.html (per Plan 1 SUMMARY §"Departures from Plan Task Action Text"). Avoids:
- Whitespace differences from `<th:block>`-replacement vs. `<section>`-replacement.
- A layout-fragment-call signature change (the plan's spirit was to mirror Plan 1's pattern, which kept `~{::section}`).

The functional contract is identical — the same server-side flags drive the rendering — only the layout-selector token is unchanged.

**Task 2 PART A (MatchdaysPageGenerator):** Two minor adjustments aligned with Plan 1's StandingsPageGenerator:
- `aria-controls` value uses the layout's existing `id="main-content"` rather than a new `id="main-matchdays"`. Adding the id would not have hurt SC4 (matchdays.html doesn't have a strict byte-identity baseline) but staying consistent with Plan 1 keeps both helpers symmetric.
- The `aria-selected` attribute renders as the literal string `"true"` / `"false"` via `th:attr="aria-selected=${flag} ? 'true' : 'false'"` per WAI-ARIA spec.

## Note for Plan 7 (Release Notes)

Document this user-visible behavior change in the release notes (mirrors Plan 1 standings legacy URL change):

> **Multi-phase season `matchdays.html` now lists REGULAR-phase matchdays only.** The legacy URL `/season/{slug}/matchdays.html` previously listed all matchdays across all phases (REGULAR + PLAYOFF). After Phase 62 it lists REGULAR-phase matchdays only — for consistency with `standings.html` (which is REGULAR-combined). Multi-phase seasons should use the new variants:
> - `/season/{slug}/matchdays-regular.html` — REGULAR-phase matchdays (combined for GROUPS-layout)
> - `/season/{slug}/matchdays-regular-group-{groupSlug}.html` — per-group matchdays (GROUPS layout only)
> - PLAYOFF matchdays are surfaced inside `/season/{slug}/playoff.html` (the bracket page, no separate matchdays-playoff.html).

Single-REGULAR-LEAGUE seasons (today's typical production data shape) render `matchdays.html` with no behavior change.

## Branch Verification

Active branch: `gsd/v1.9-season-phases-groups` — unchanged throughout execution. No `git stash`, `git checkout`, `git reset`, or `git clean` was used.

## Self-Check: PASSED

- [x] `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java` exists with 8 test methods, all required Given-When-Then names present (verified via grep)
- [x] `src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java` references `showPhaseTabs`, `showGroupTabs`, `findByPhaseIdOrderBySortIndexAsc`, `findByPhaseIdAndGroupIdOrderBySortIndexAsc`, injects `SeasonPhaseService` + `SeasonPhaseGroupRepository` (verified via grep)
- [x] `src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java` skips PLAYOFF in the per-phase loop (`continue` keyword in PLAYOFF branch), never generates `matchdays-playoff.html`
- [x] `src/main/java/org/ctc/sitegen/MatchdaysPageGenerator.java` `generateDetails` body is byte-identical to post-Plan-0 implementation (only signature surrounds; per-matchday detail pages remain phase-agnostic)
- [x] `src/main/resources/templates/site/matchdays.html` contains `th:if="${showPhaseTabs}"`, `th:if="${showGroupTabs}"`, `role="tablist"`, `role="tab"`, `aria-selected`, `phase-tab-row`, `group-tab-row` (verified via grep)
- [x] `./mvnw -Dtest=MatchdaysPageGeneratorTest test` passes (Tests run: 8, Failures: 0, Errors: 0, Skipped: 0)
- [x] `./mvnw -Dtest='SiteGenerator*,StandingsPageGeneratorTest,MatchdaysPageGeneratorTest' test` passes (Tests run: 111, Failures: 0, Errors: 0, Skipped: 2 — including SC4 byte-identity test from Plan 1 still green)
- [x] `./mvnw verify` passes (Tests run: 1191, Failures: 0, Errors: 0, Skipped: 3, BUILD SUCCESS, JaCoCo "All coverage checks have been met")
- [x] JaCoCo line coverage = 85.54% (≥ 82% project minimum)
- [x] Two atomic commits exist on `gsd/v1.9-season-phases-groups`: `60fb262` (TDD-RED), `38f7f4a` (TDD-GREEN)
- [x] Branch unchanged: `git branch --show-current` returns `gsd/v1.9-season-phases-groups`
- [x] No `standings-playoff.html` generated (Plan 1 invariant preserved); no `matchdays-playoff.html` generated (Plan 2 invariant)
- [x] Multi-phase phase-tab row visible with PLAYOFF tab linking to `playoff.html` — verified by `givenMultiPhaseSeason_whenGenerateIndex_thenPhaseTabRowVisible`
- [x] Per-group variants exist for GROUPS-layout — verified by `givenGroupsLayoutSeason_whenGenerateIndex_thenPerGroupVariantsExist`
- [x] No commits modify STATE.md / ROADMAP.md (orchestrator-owned)
- [x] No `git stash`, `git checkout`, `git reset`, or `git clean` used
