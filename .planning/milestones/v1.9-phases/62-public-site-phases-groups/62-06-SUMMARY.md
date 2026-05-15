---
phase: 62-public-site-phases-groups
plan: "06"
type: execute
wave: 7
status: complete
completed: 2026-05-07
subsystem: sitegen
tags:
  - sitegen
  - regression-test
  - SC5
  - integration-test
  - D-22
  - D-26
dependency_graph:
  requires:
    - "62-05"
  provides:
    - "SC5 regression IT (SiteGeneratorPhaseAwarenessIT) — canonical cross-phase gate for Phase 62"
    - "D-22 deterministic empty-phase fixture (Season 2024 — Empty Phase, slug 2024-3-season-2024-empty-phase)"
  affects:
    - "src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java (new)"
    - "src/main/java/org/ctc/admin/TestDataService.java (new fixture + class Javadoc)"
tech-stack:
  added: []
  patterns:
    - "Surefire IT with @SpringBootTest + @ActiveProfiles('dev') + @TempDir + Flyway clean+migrate"
    - "Jsoup HTML parsing for structural assertion without Playwright (D-24)"
    - "Flyway clean+migrate in @BeforeAll for cross-test-class DB isolation under H2 DB_CLOSE_DELAY=-1"
key-files:
  created:
    - src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java
  modified:
    - src/main/java/org/ctc/admin/TestDataService.java
decisions:
  - "Used org.springframework.test.context.bean.override.mockito.MockitoBean (not the deprecated boot.test.mock import) — matches StandingsPageGeneratorTest pattern."
  - "Flyway clean+migrate in @BeforeAll mirrors StandingsPageGeneratorTest (Plan 1) to guarantee fresh DB state when multiple @SpringBootTest classes share the H2 in-memory DB under DB_CLOSE_DELAY=-1."
  - "Empty-phase fixture assigned year=2024 number=3 (not number=2, which is already the Swiss season). Slug computed as slugify('2024 | #3 | Season 2024 — Empty Phase') = '2024-3-season-2024-empty-phase'."
  - "SC4 byte-identity assertion uses slug '2026-4-regular-season' — confirmed from StandingsPageGeneratorTest line 97 and Season displayLabel '2026 | #4 | Regular Season'."
  - "D-22 assertion verifies banner heading 'No results recorded yet.' against the UI-SPEC copywriting contract and asserts >=4 tbody rows (4 teams seeded via PhaseTeam)."
metrics:
  duration: "~30 minutes"
  completed: 2026-05-07
  tasks: 2
  commits: 2
  files-created: 1
  files-modified: 1
  test-count: 1213
  test-failures: 0
  test-skipped: 4
  jacoco-line-coverage: 85.60%
---

# Phase 62 Plan 06: SC5 Regression IT — SiteGeneratorPhaseAwarenessIT

Unified SC5 regression integration test covering SC1..SC5, D-22, D-26, D-04, and D-19 in a single Surefire IT class. Runs in default `./mvnw verify` (not Failsafe, not Playwright). This is the canonical regression gate for Phase 62: Plans 1-5 ship per-page-type tests; Plan 6 consolidates cross-cutting assertions.

## Summary

Two atomic commits on `worktree-agent-a5598250a9d436297` (target: `gsd/v1.9-season-phases-groups`):

1. `216d00f` — `test(62-06): add Season 2024 — Empty Phase fixture for D-22 empty-state coverage`
2. `4ec31a9` — `test(62-06): SC5 regression IT SiteGeneratorPhaseAwarenessIT covering SC1-SC5 + D-22/D-26`

## Test Methods Added (9 total)

| Method | Covers | Fixture |
|--------|--------|---------|
| `givenGroupsLayoutSeason_whenGenerate_thenPerGroupAndCombinedFilesExist` | SC1 — per-group + combined files, Group column conditional (D-32) | Season 2023 (GROUPS) |
| `givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisible` | SC2 — phase-tab-row with role=tablist + ≥2 tabs | Season 2023 |
| `givenMultiPhaseSeason_whenGenerate_thenPlayoffTabLinksToBracket` | SC3 — PLAYOFF tab → playoff.html; standings-playoff.html never generated (D-08) | Season 2023 |
| `givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline` | SC4 — byte-identity against Plan 0 golden baseline | Season 2026 (LEAGUE-only) |
| `givenMultiPhaseSeason_whenGenerate_thenTabRowHasA11yAttributes` | D-26 — role=tablist/tab + aria-selected on phase + group tab rows | Season 2023 |
| `givenMultiPhaseSeason_whenGenerate_thenAllPageTypesEmitPhaseTabRow` | SC2 cross-cut — phase-tab-row on standings + matchdays + driver-ranking | Season 2023 |
| `givenEmptyPhaseSeason_whenGenerate_thenEmptyStateBannerAndRosterVisible` | D-22 — empty-state banner heading + ≥4 roster rows at 0 points | Season 2024 — Empty Phase |
| `givenGeneratedSite_whenParseArchiveLinks_thenAllSeasonLinksResolveToExistingFiles` | D-04 — archive.html cross-link integrity | all seasons |
| `givenGeneratedSite_whenGenerate_thenAlltimePagesExist` | D-19 sanity — alltime pages still exist after Plan 5 cross-phase aggregation | all seasons |

## D-22 Empty-State Fixture (Task 0)

`TestDataService.seed()` extended with a deterministic `Season 2024 — Empty Phase` fixture:
- Season name: `Season 2024 — Empty Phase` (no "Test" substring → survives productionSeasons filter)
- year=2024, number=3 (number=2 already occupied by Swiss season)
- Slug: `2024-3-season-2024-empty-phase`
- ONE REGULAR phase with PhaseLayout.LEAGUE, SeasonFormat.LEAGUE
- 4 PhaseTeam rows (ADR/ICL/SVT/NFR), ZERO Matchday/Race/RaceResult rows
- Shared source of truth for Plan 6 D-22 IT method and Plan 7 visual sweep

Class-level Javadoc added to TestDataService listing all 4 seeded fixtures with slugs.

## SC4 Byte-Identity

The SC4 assertion targets slug `2026-4-regular-season` (Season 2026, name="Regular Season", year=2026, number=4 → displayLabel "2026 | #4 | Regular Season"). This is the same fixture captured by Plan 0 Task 0. The golden baseline (`src/test/resources/sitegen/baseline/single-league-standings.html`) was NOT modified in this plan — confirmed via `git log -- src/test/resources/sitegen/baseline/single-league-standings.html`.

## Behavior Changes Shipped

None — test-only plan. No production code was modified.

## Deviations from Plan

None. Plan executed exactly as written.

- SC4 byte-identity assert: **in this IT** (not deferred), using fixture-aligned slug confirmed from StandingsPageGeneratorTest line 97.
- D-22 empty-state assert: **in this IT** via the new deterministic fixture added in Task 0.
- SC4 baselines: untouched (blocking constraint satisfied).
- Deterministic sort: not touched (blocking constraint satisfied — tiebreaker fix from Plan 62-01 commit `1ffd559` still in place).

## Final Verification

```
Tests run: 1213, Failures: 0, Errors: 0, Skipped: 4
JaCoCo line coverage: 85.60% (≥ 82% minimum)
BUILD SUCCESS
```

Branch: `worktree-agent-a5598250a9d436297` → merges to `gsd/v1.9-season-phases-groups`

## Self-Check: PASSED

- `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java`: FOUND
- Commit `216d00f`: FOUND (git log)
- Commit `4ec31a9`: FOUND (git log)
- Baseline `single-league-standings.html`: unchanged (last touched in Plan 01 commit `1ffd559`)
- Branch: `worktree-agent-a5598250a9d436297` (targets `gsd/v1.9-season-phases-groups`)
- 1213 tests pass, 0 failures, JaCoCo 85.60% ≥ 82%
