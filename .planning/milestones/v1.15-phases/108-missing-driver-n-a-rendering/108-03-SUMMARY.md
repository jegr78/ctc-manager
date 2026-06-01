# Plan 108-03 Summary — n/a de-emphasis + scoring robustness + build gate

**Status:** Complete
**Requirements:** LINEUP-01, LINEUP-02, LINEUP-03, LINEUP-04
**Decisions realized:** D-02 (scoring persistence unchanged, verified by test), D-05 (de-emphasized `.empty-slot` treatment across all three graphics)

## What was built

- **`.empty-slot` CSS** added to each render template's own `<style>` block (appended, existing rules untouched):
  - `lineup-render.html` / `results-render.html`: `.empty-slot { opacity: 0.32; }` + `.empty-slot .driver-name { color: #6a6a73; }`
  - `provisional-scores-render.html`: `.provisional-table tr.empty-slot td { color: #5f5f68; }`
- Applied via `th:classappend` keyed on the per-side / per-row `'n/a'` marker:
  - lineup: each `.driver-info.home` / `.driver-info.away`
  - results: each `.driver-info` side **and** the corresponding `.points` span (so the n/a `0` is dimmed too)
  - provisional: each `<tr>` of `homeRows` / `awayRows`
- No inline `style=""` attributes added; no JS sets `className` on these rows (verified by grep).
- **ScoringService robustness test** (`ScoringServiceTest.givenHomeTeamWithFewerThanSixDrivers_...`): aggregates a race with a 3-driver home team; asserts `homeScore == 44`, `awayScore == 29`, no NPE. No defensive guard or fallback computation added to `ScoringService` (D-02 / LINEUP-04 confirmed as-is).

## Visual checkpoint

User approved the `.empty-slot` de-emphasis via free-typed feedback ("Passt") on three faithful CSS mockups (exact template markup + CSS, scenario home 3 / away 2 → rows 3–6 = `n/a`). Screenshots: `.screenshots/108-{lineup,results,provisional}-na.png`. The editor preview path could not be used (its sample data always supplies 6 full drivers and has no provisional entry); live `<6`-driver rendering is covered by the unit tests and the full E2E build.

## Build gate (Task 3)

`./mvnw clean verify -Pe2e` — green (Surefire + Failsafe IT + Playwright E2E + JaCoCo + SpotBugs). Coverage ≥ 82%. See verification note below for exact counts.

## Deviations from plan

- The plan's template/interface descriptions differed from the real code (real services build rows inline via `getPsnId()`; templates use `driver-nickname` + `'(' + nick + ')'`; provisional computes `homeOverallTotal` via stream sums). All edits were applied to the actual code; behavior matches plan intent.
