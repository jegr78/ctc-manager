---
phase: 61-cleanup-quality-gate
plan: 05
subsystem: testing

tags: [playwright, e2e, jacoco, sql-fixtures, regression, quality-gate, qual-03, qual-01]

# Dependency graph
requires:
  - phase: 61-cleanup-quality-gate (Plan 61-04)
    provides: GROUPS-Saison E2E gate (QUAL-02) — establishes the 2nd E2E test sibling that runs alongside the new regression test in `-Pe2e`.
  - phase: 61-cleanup-quality-gate (Plan 61-03)
    provides: V6 SQL migration cleanup — fixtures here build on the post-V6 schema (no `seasons.format/legs/...`, no `playoff_seasons`, no bridge `season_id` columns).
  - phase: 60-admin-ui (D-12, D-31)
    provides: Legacy `?seasonId=` -> REGULAR-phase server-side resolution in StandingsController.
  - phase: 57-data-migration (D-06)
    provides: V4 backfill — every legacy season has a REGULAR phase post-migration; fixtures simulate that exact post-V4-then-V6 shape.

provides:
  - QUAL-03 regression E2E test class with TWO @Test methods covering legacy migrated seasons (with + without playoff) per ROADMAP-SC4.
  - First @Sql-driven fixture pattern in the codebase (`src/test/resources/sql/`) — establishes the convention for future pre-insert E2E fixtures.
  - QUAL-01 final coverage gate met: JaCoCo line coverage 87.05% (well above 82% threshold). No coverage-repair tests required.
  - End-of-phase signal: ./mvnw verify -Pe2e BUILD SUCCESS with all 31 E2E tests + full Surefire suite + JaCoCo check passing.

affects:
  - milestone v1.9 wrap-up (`/gsd-verify-work 61` -> `/gsd-audit-milestone v1.9` -> `/gsd-complete-milestone v1.9` -> `/gsd-ship`).
  - future regression tests for migrated seasons — the @Sql + post-V6 fixture pattern can be reused.

# Tech tracking
tech-stack:
  added:
    - org.springframework.test.context.jdbc.Sql (already on classpath via spring-boot-test; this plan establishes the project's first usage)
  patterns:
    - "@Sql BEFORE_TEST_METHOD pre-insert fixture pattern for E2E tests against the live web stack"
    - "Deterministic UUID range partitioning per fixture (0000-0061-0000-* vs 0000-0061-1000-*) to defensively avoid cross-fixture PK collisions"
    - "Read-only legacy-shape regression test idiom: Saison-Detail auto-redirect to REGULAR phase, phase-tab visibility asserts, Standings legacy-?seasonId= bridge verification"

key-files:
  created:
    - src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java
    - src/test/resources/sql/legacy-season-without-playoff.sql
    - src/test/resources/sql/legacy-season-with-playoff.sql
  modified: []

key-decisions:
  - "Selectors aligned with real templates (.tab-nav .tab-btn, section#matchdays, section#bracket) — plan template referenced placeholder selectors that don't exist in the codebase."
  - "Standings ?seasonId= bridge verified via .tab-active phase tab + selectedSeason text in toolbar — NOT via HTTP redirect URL pattern (StandingsController resolves server-side without redirecting; the legacy URL stays in the browser)."
  - "Fixtures intentionally seed 0 race-results — read-only path (D-18) does not require results, and skipping them keeps the fixture minimal. The empty-state path through standings.html is exercised instead."
  - "Playoff-bracket assertion is lenient: <h1>Playoffs</h1> + selected-season dropdown contains the test season name. The fixture seeds no playoff_rounds/matchups, so <h2 th:text='${bracket.name}'> is not emitted; the bracket page still renders the toolbar/dropdown without 500."
  - "No coverage-repair tests required — JaCoCo line coverage 87.05% comfortably above the 82% threshold (D-21 path skipped)."

patterns-established:
  - "@Sql + post-V6 fixture pattern: src/test/resources/sql/ + @Sql(executionPhase=BEFORE_TEST_METHOD) — reusable for future migrated-season regression tests."
  - "Read-only legacy regression idiom: navigate to canonical URL, assert phase-tab visibility, exercise legacy bridge URL, assert server-side resolution markers (selected season name, active phase tab)."

requirements-completed: [QUAL-01, QUAL-03]

# Metrics
duration: 22min
completed: 2026-05-01
---

# Phase 61 Plan 5: Legacy Regression E2E + Coverage Gate Summary

**QUAL-03 regression: 2 @Sql-driven Playwright tests prove legacy V4-migrated seasons (with + without playoff) open cleanly in the post-Phase-60 admin UI; QUAL-01 closed at 87.05% JaCoCo line coverage with the 82% threshold preserved.**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-05-01T17:50:00Z (approx)
- **Completed:** 2026-05-01T18:09:00Z
- **Tasks:** 2 commit-producing tasks (Task 1 fixtures, Task 2 test class) + Task 3 verification (no commit)
- **Files created:** 3
- **Files modified:** 0

## Accomplishments

- **QUAL-03 closed:** `LegacyMigratedSeasonE2ETest` with 2 @Test methods proves a season migrated by V4 and cleaned by V6 still opens correctly in the post-Phase-60 admin UI (both with-playoff and without-playoff variants per D-19).
- **QUAL-01 closed:** Full `./mvnw verify -Pe2e` BUILD SUCCESS with JaCoCo line coverage **87.05%** (5634/6472 lines) — well above the unchanged 82% threshold. Instruction coverage 84.41%, branch coverage 74.32%.
- **First @Sql fixture pattern in the codebase:** `src/test/resources/sql/` directory created, two fixture scripts establish the deterministic-UUID + T-prefix convention for legacy-shape regression fixtures.

## Task Commits

Each task was committed atomically:

1. **Task 1: SQL fixtures** — `6590a09` (test) — `src/test/resources/sql/legacy-season-{without,with}-playoff.sql` created with post-V6 schema + deterministic UUIDs.
2. **Task 2: LegacyMigratedSeasonE2ETest** — `df2c601` (test) — Playwright E2E class with 2 @Sql-driven @Test methods, verified GREEN via targeted run.
3. **Task 3: Final coverage gate** — no commit (verification-only). `./mvnw verify -Pe2e` BUILD SUCCESS, JaCoCo line coverage 87.05% ≥ 82%. No coverage-repair tests required.

**Plan metadata commit:** to be made by orchestrator (this SUMMARY only).

## Files Created/Modified

- `src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java` — QUAL-03 regression E2E with 2 @Sql-driven @Test methods. Read-only path (D-18) through Saison-Detail (auto-redirect to REGULAR phase tab), matchday-list, matchday-detail, legacy ?seasonId= -> REGULAR-phase server-side resolution, and (with-playoff variant) PLAYOFF phase tab + bracket-page reachability.
- `src/test/resources/sql/legacy-season-without-playoff.sql` — Pre-insert fixture: 1 Saison + 1 REGULAR phase + 2 phase_teams + 2 matchdays + 2 races + 2 drivers + 2 season_drivers + scoring rows. Deterministic UUIDs in `00000000-0000-0061-0000-*` range.
- `src/test/resources/sql/legacy-season-with-playoff.sql` — Same structure plus 1 PLAYOFF phase + 1 playoff row (post-V6 schema, no `playoffs.season_id` column anymore). Deterministic UUIDs in `00000000-0000-0061-1000-*` range.

## Selectors Used (Verified Against Real DOM)

| Plan Placeholder | Real Selector |
|------------------|---------------|
| `.phase-tab` | `.tab-nav .tab-btn` (Phase 60 D-29 two-row tab nav) |
| `.matchday-row` | `section#matchdays ul li` (season-detail.html lines 389-397) |
| `.race-row` | (not asserted — race list lives on matchday-detail.html, plan didn't need direct race-row assertion) |
| `.standings-row` | `#standingsTable tbody tr.data-row` (standings.html line 85) |
| URL pattern `phaseId=` after legacy redirect | `.tab-nav .tab-btn.tab-active` text containing "REGULAR" (StandingsController D-12 resolves server-side, does NOT HTTP-redirect) |

## JaCoCo Coverage Result

```
INSTRUCTION coverage: 28574/33853 = 84.4061%
BRANCH coverage:       1716/2309  = 74.3179%
LINE coverage:         5634/6472  = 87.0519%
```

**Threshold check (pom.xml `<minimum>0.82</minimum>` LINE counter):** met with 5.05 percentage-point headroom.
**`git diff --stat pom.xml`:** empty — pom.xml unchanged per D-21.

## Decisions Made

- **Selectors deferred to real-template inspection** — plan template used placeholder class names; real templates use `.tab-nav .tab-btn` (Phase 60 D-29). All assertions adapted to the live DOM during Task 2 implementation.
- **Standings legacy-bridge verified via active-tab + toolbar text, not URL pattern** — StandingsController's D-12 path resolves server-side and does NOT issue an HTTP redirect, so the original URL `?seasonId=...` stays in the browser. The functional contract is "season -> REGULAR phase resolved, page renders", which is asserted via the active phase-tab class and the selected-season text in the toolbar.
- **Playoff-bracket assertion intentionally lenient** — fixture seeds no playoff_rounds/matchups (out-of-scope for read-only regression), so the `<h2 th:text='${bracket.name}'>` element is not emitted. The test asserts `<h1>Playoffs</h1>` plus the selected-season dropdown text to confirm the page rendered without 500.
- **No coverage-repair tests added (D-21 skipped)** — line coverage 87.05% provides 5.05 pp headroom above the 82% threshold; the targeted Surefire-test plan-step is unnecessary.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Adjusted final playoff-detail assertion to match real template output**

- **Found during:** Task 2 first targeted run (`./mvnw verify -Pe2e -Dit.test=LegacyMigratedSeasonE2ETest`).
- **Issue:** Initial assertion was `assertThat(page.locator("h1")).containsText("Test-Legacy-Playoff")`, but the playoff-bracket template (`playoff-bracket.html` line 7) renders `<h1>Playoffs</h1>` statically; the playoff name lives inside the bracket card's `<h2 th:text="${bracket.name}">`, which is only emitted when bracket data is non-null. The fixture seeds no rounds/matchups, so the bracket card is absent.
- **Fix:** Changed the assertion to `containsText("Playoffs")` plus a follow-up assertion on the selected-season dropdown option to confirm seasonId resolution: `assertThat(page.locator("select[name='seasonId'] option[selected]")).containsText("Test-Legacy-Season-2097")`.
- **Files modified:** `src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java`.
- **Verification:** Re-run of `./mvnw verify -Pe2e -Dit.test=LegacyMigratedSeasonE2ETest` returned `Tests run: 2, Failures: 0, Errors: 0`.
- **Committed in:** `df2c601` (Task 2 commit — fixed before commit).

---

**Total deviations:** 1 auto-fixed (Rule 1, in-task bug — selector mismatch caught by initial run, fixed before the commit).
**Impact on plan:** Plan template used placeholder selectors and assumed an HTTP redirect that does not occur. Adjustments aligned the test with the live DOM and Phase 60's server-side resolution pattern. No scope creep; both @Test methods cover ROADMAP-SC4 sub-cases as planned.

## Behavior Changes Shipped

**None** — this plan only adds tests + SQL fixtures; no production code paths altered. Phase 61's tracked behavior changes (Schema-drop irreversibility, `/admin/playoffs/{id}/add-season` 404, bridge column drop) were shipped in Plans 61-02 and 61-03 and are unaffected by this plan.

## Issues Encountered

- **Plan placeholder selectors** vs. real DOM: resolved during Task 2 implementation by inspecting `season-detail.html` (lines 266-290 phase-tabs row), `standings.html` (lines 28-34 phase-tabs + lines 70-100 standings table), and `playoff-bracket.html` (line 7 static h1). Documented in the Decisions table above for future regression tests.
- **Standings legacy bridge does not redirect** (Phase 60 D-12 actually resolves server-side, no 302 issued). Plan asserted on URL containing `phaseId=`; corrected to active-tab + toolbar-text assertion. Documented under Decisions.

## User Setup Required

None — no external service configuration required.

## Phase 61 Closure

All 4 Phase-61 requirements are now met across plans 61-01..61-05:

- **MIGR-06** (Plan 61-03): V6 SQL migration drops the 8 legacy season columns + `playoff_seasons` M:N + `matchdays.season_id` + `playoffs.season_id` bridge columns.
- **QUAL-01** (Plan 61-05, this plan): JaCoCo line coverage 87.05% ≥ 82% threshold (unchanged in pom.xml).
- **QUAL-02** (Plan 61-04): `GroupsSeasonE2ETest` proves the full GROUPS-Saison workflow end-to-end.
- **QUAL-03** (Plan 61-05, this plan): `LegacyMigratedSeasonE2ETest` proves V4-migrated legacy seasons still open cleanly (with + without playoff).

## Next Phase Readiness

- **Milestone wrap-up follow-on (per Phase 61 D-26):**
  1. `/gsd-verify-work 61` (UAT)
  2. `/gsd-audit-milestone v1.9` (cross-phase audit)
  3. `/gsd-complete-milestone v1.9` (archive + bump)
  4. `/gsd-ship` (PR to master + release)

**No blockers.** Branch `gsd/v1.9-season-phases-groups` is ready for milestone wrap-up.

## Self-Check: PASSED

- Created files exist:
  - `src/test/resources/sql/legacy-season-without-playoff.sql` — FOUND
  - `src/test/resources/sql/legacy-season-with-playoff.sql` — FOUND
  - `src/test/java/org/ctc/e2e/LegacyMigratedSeasonE2ETest.java` — FOUND
- Commits exist on `gsd/v1.9-season-phases-groups`:
  - `6590a09` (test fixtures) — FOUND
  - `df2c601` (test class) — FOUND
- `./mvnw verify -Pe2e` BUILD SUCCESS — confirmed.
- JaCoCo line coverage 87.05% ≥ 82% — confirmed via target/site/jacoco/jacoco.csv.
- pom.xml unchanged (D-21) — `git diff --stat pom.xml` empty.
- Branch is `gsd/v1.9-season-phases-groups` — confirmed.

---
*Phase: 61-cleanup-quality-gate*
*Completed: 2026-05-01*
