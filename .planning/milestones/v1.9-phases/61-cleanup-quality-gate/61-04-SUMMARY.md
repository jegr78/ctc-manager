---
phase: 61-cleanup-quality-gate
plan: 04
subsystem: testing
tags: [playwright, e2e, groups, season-phase, qual-02, hybrid-asserts]

# Dependency graph
requires:
  - phase: 56-model-schema-foundation
    provides: SeasonPhase / SeasonPhaseGroup / PhaseTeam tables (V3)
  - phase: 58-service-layer
    provides: phaseId-canonical APIs, Combined-View standings (D-04), PhaseTeam roster auto-init (D-20)
  - phase: 59-import-test-data
    provides: PhaseTeam-driven group resolution in driver-import (D-05), TabPreview.warnings (D-08)
  - phase: 60-admin-ui
    provides: slim season form (UI-01), GROUPS sub-tab UI, multi-select Roster Editor (D-20), legacy /admin/standings?seasonId= bridge (D-12)
provides:
  - "End-to-end Playwright test giving QUAL-02 acceptance: full GROUPS-Saison workflow from UI"
  - "Synthetic GoogleSheetsService stub pattern (year-tab '2099', 12 driver rows, no Group column) reusable by future driver-import E2E tests"
  - "Hybrid UI + DB-state assertion convention for PhaseTeam / SeasonPhase repositories (D-13)"
affects: [phase-61-05, phase-62, future-e2e-tests-for-groups-layout]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@TestConfiguration @Bean @Primary GoogleSheetsService stub (analog ImportE2eTest, D-12)"
    - "Hybrid UI Playwright assertions + Repository DB-state checks (D-13)"
    - "Idempotent @BeforeEach test-data cleanup against UNIQUE(year,number) and team short_name collisions"
    - "Test-data isolation by T-prefix + year=2099 (D-14, CLAUDE.md hard rule)"

key-files:
  created:
    - "src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java"
  modified: []

key-decisions:
  - "Repository-level matchday/race/lineup setup retained from D-15 — no UI affordance exists for group-bound matchday or race generation as of Phase 60. The D-15 mandate (UI-driven race-result entry) is honoured strictly via /admin/races/{id}/results."
  - "Driver-import preview/execute uses HTML5-valid URL placeholder (https://docs.google.com/spreadsheets/d/test-spreadsheet-id) because the form input has type=url; the stubbed GoogleSheetsService.extractSpreadsheetId returns a constant regardless."
  - "Race-result point distribution chosen for non-tied per-group standings: GA-1 = 6 match-points, GA-2 = 0; GB-1 = 6 match-points, GB-2 = 0. Combined-view renders exactly 4 rows per D-13."
  - "Roster bulk save reuses the existing rendered Edit Roster form (collapsed inside <details>); test opens all <details> via JS evaluation, then iterates the rendered assignments[i].teamId hidden inputs to set Include + Group select (no DOM injection)."

patterns-established:
  - "Pattern: idempotent test cleanup via SeasonRepository.findByYearAndNumber + try/catch — each E2E run drops leftover Test-GROUPS Season 2099 + T-prefix teams up front."
  - "Pattern: GROUPS-layout flip via /admin/seasons/{sid}/phases/{pid}/edit form (Phase 60 D-22 phaseId-canonical URL); requires raceScoring + matchScoring + legs + format set in the same submit."
  - "Pattern: PhaseTeam-driven group resolution exercised end-to-end (no group column in synthetic sheet — group inferred via T-GA-* / T-GB-* team-shortName → PhaseTeam.group lookup per Phase 59 D-05)."

requirements-completed: [QUAL-02]

# Metrics
duration: ~75min
completed: 2026-05-01
---

# Phase 61 Plan 04: GROUPS-Saison E2E Test (QUAL-02) Summary

**Single Playwright @Test method covering ROADMAP-SC3 verbatim — full GROUPS-Saison workflow from UI: season-create, REGULAR→GROUPS layout flip, group setup, team-roster, driver-import via stubbed GoogleSheetsService, race-results UI entry, per-group + combined-view standings.**

## Performance

- **Duration:** ~75 min (including UI inspection + 2 verify cycles)
- **Started:** 2026-05-01T17:30Z
- **Completed:** 2026-05-01T19:45Z
- **Tasks:** 1 (61-04-T01)
- **Files created:** 1
- **E2E test runtime:** 33 s (single test method)
- **Full `./mvnw verify -Pe2e`:** 4m 56s (29 E2E tests green, JaCoCo gate held)

## Accomplishments

- **`GroupsSeasonE2ETest` shipped** — one `@Test` method `givenGroupsLayout_whenFullSeasonWorkflow_thenStandingsCorrect()` exercises the complete QUAL-02 path end-to-end (~520 lines, 9 STEPs).
- **Stub pattern matches ImportE2eTest** — `@TestConfiguration TestGoogleSheetsConfig` overrides `extractSpreadsheetId`, `getSheetNames`, `readRange`, `readRangeFromSheet` to return a synthetic year-tab "2099" with 12 driver rows.
- **Hybrid asserts (D-13)** — Playwright UI locators on `.alert-success`, `#standingsTable tbody tr`, `h1` text PLUS direct repository checks on `phaseTeamRepository.findByPhaseId(...)`, `phaseTeamRepository.findByPhaseIdAndGroupId(...)`, `seasonPhaseRepository.findBySeasonIdAndPhaseType(...)`.
- **Test-data isolation (D-14)** — Test-GROUPS Season 2099, T-GA-1/T-GA-2/T-GB-1/T-GB-2 teams, T_groups_drv01..drv12 PSN IDs. No collision with DevDataSeeder fixtures (2024-2026) or any other E2E test (which uses 2026 + T-ALF/T-BRV).
- **No sibling-test regression** — `./mvnw verify -Pe2e` BUILD SUCCESS with 29 tests across `AdminWorkflowE2ETest`, `ImportE2eTest`, `ScoringE2ETest`, `GroupsSeasonE2ETest` plus the unit/integration suite. JaCoCo coverage gate at 82% remained satisfied.

## Task Commits

1. **Task 1: Author GroupsSeasonE2ETest with one full-workflow @Test method** — `28c1204` (test)

_(No plan-metadata commit yet — orchestrator owns STATE.md / ROADMAP.md updates per execution prompt.)_

## Files Created/Modified

- `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` (CREATED, 521 lines) — Single E2E test class with one `@Test` method covering 9 workflow STEPs + helper methods (`persistRaceWithLineups`, `fillRaceResultsByPsnIdOrder`) + `@TestConfiguration TestGoogleSheetsConfig` static class.

## Decisions Made

1. **Repository-layer matchday/race/lineup setup retained from D-15.** — No UI exists for group-bound matchday or race generation as of Phase 60. The canonical `MatchdayController` only binds matchdays via `seasonPhaseService.findRegularPhase(seasonId)` without group-awareness; there is no `/admin/season-phases/{phaseId}/groups/{groupId}/matchdays/generate` endpoint that the plan template assumed. The structural matchday + match + race + lineup creation runs against repositories inside a `TransactionTemplate.executeWithoutResult` block, while the **D-15-mandated race-result entry is strictly UI-driven** via `/admin/races/{id}/results` for all 4 races. This is the smallest deviation that preserves D-15's verification value (race-result form bindings + scoring service + match aggregation are all exercised in the browser).

2. **Race-result point distribution targeting non-tied standings.** — CTC Standard race scoring (20,17,14,12,10,8) with 6 lineup drivers × 4 races. Group A: GA-1 sweeps top 3 in both matchdays → match-points 6 vs 0. Group B: GB-1 sweeps top 3 in both matchdays → match-points 6 vs 0. Per-group leader assertions are deterministic. Combined-view assertion checks row-count (4) only, since both groups have identical 6:0 splits.

3. **Roster bulk save via existing rendered form, no DOM injection.** — The roster form lives inside a collapsed `<details>` element; the test opens all details via `page.evaluate("() => document.querySelectorAll('details').forEach(d => d.open = true)")`, then iterates the rendered `assignments[i].teamId` hidden inputs to set the matching Include checkbox + Group `<select>`. This stays faithful to the Phase 60 D-20 indexed-properties pattern.

4. **HTML5 url validation.** — The driver-import form input is `<input type="url" required>`, which means the browser blocks form submission on bare strings. The test fills `https://docs.google.com/spreadsheets/d/test-spreadsheet-id` even though the stubbed `GoogleSheetsService.extractSpreadsheetId` ignores the input.

5. **Idempotent `@BeforeEach` cleanup.** — `seasonRepository.findByYearAndNumber(2099, 1)` is queried first and any prior season is deleted via `seasonManagementService.delete(...)`. Likewise leftover T-prefix teams are deleted via `teamRepository.delete(...)`. Best-effort `try/catch` so a guarded deletion does not abort the run. Aligns with project memory `feedback_test_data_isolation` + the `SeasonManagementService` Phase 56 D-03 UNIQUE(year, number) constraint.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Group-bound matchday/race generation has no UI**
- **Found during:** Task 1, while mapping plan-template URLs against actual controllers.
- **Issue:** Plan template referenced `/admin/season-phases/{phaseId}/groups/{groupId}/matchdays/generate` and analogous race-generation endpoints. These endpoints do not exist; `MatchdayController` always binds matchdays to `findRegularPhase(seasonId)` with no `groupId` parameter, and there is no race-pairing UI on group-level. Building such UI is architectural (would belong in a future plan), not a plan-61-04 deliverable.
- **Fix:** Performed structural matchday + match + race + lineup creation via repositories inside a `TransactionTemplate.executeWithoutResult` block. Honoured D-15 strictly: race-result entry runs through the actual `/admin/races/{id}/results` UI form, exercising `RaceService.saveResults`, `ScoringService.calculatePoints`, and `ScoringService.aggregateMatchScores` end-to-end via the browser.
- **Files modified:** `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` STEP 6.
- **Verification:** Test passes; per-group + combined-view standings render correct rows; DB-state asserts confirm 4 PhaseTeam rows with non-null group_ids and PhaseLayout.GROUPS persistence.
- **Committed in:** 28c1204.

**2. [Rule 1 — Bug] HTML5 `<input type="url">` rejected the bare placeholder string**
- **Found during:** First targeted run of the test (`./mvnw verify -Pe2e -Dit.test=GroupsSeasonE2ETest`) — preview button click did not redirect; `h1` stayed on "Import Drivers from Google Sheet" instead of "Driver Import Preview".
- **Issue:** `driver-import.html` declares `<input type="url" id="sheetUrl" required>`; the browser enforces URL validation client-side, so `"test-spreadsheet-id"` is silently rejected and `Preview` does not POST.
- **Fix:** Pass a fully-formed URL `https://docs.google.com/spreadsheets/d/test-spreadsheet-id`. The `TestGoogleSheetsConfig.extractSpreadsheetId` override returns a constant regardless of input.
- **Files modified:** `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` STEP 5.
- **Verification:** Re-ran `./mvnw verify -Pe2e -Dit.test=GroupsSeasonE2ETest` after the fix → BUILD SUCCESS.
- **Committed in:** 28c1204 (single test commit, both fixes in one atomic test file).

**3. [Rule 1 — Bug] Roster form submit button hidden inside collapsed `<details>`**
- **Found during:** Second targeted run — `form[action$='/groups/roster'] button[type='submit']` was not visible because Phase 60 wraps the editor inside a `<details>` element; Playwright's actionability-check timed out at 30s.
- **Issue:** `season-detail.html` renders the multi-select Roster Editor inside `<details><summary>Edit Roster</summary>...</details>`; the submit button is rendered into the DOM but invisible until the disclosure is open.
- **Fix:** `page.evaluate("() => document.querySelectorAll('details').forEach(d => d.open = true)")` opens every collapsed disclosure before clicking the Save Roster button.
- **Files modified:** `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` STEP 4c.
- **Verification:** Same targeted run after the fix → roster save succeeded, alert-success contained "Roster updated".
- **Committed in:** 28c1204.

---

**Total deviations:** 3 auto-fixed (1 blocking architectural workaround, 2 UI-affordance bugs)
**Impact on plan:** All three were necessary to make the test pass faithfully. The blocking issue (no group-aware matchday UI) is a known Phase 60 limitation already captured under the "Explicitly out of scope for Phase 60" section of `60-CONTEXT.md` — building such UI would be a follow-up plan, not a prerequisite for shipping QUAL-02.

## Issues Encountered

- **Plan template URLs vs. actual controllers** — The plan-skeleton URL `/admin/season-phases/{phaseId}/...` would have hit a missing-handler 404; the canonical Phase 60 D-22 form uses `/admin/seasons/{seasonId}/phases/{phaseId}/edit`. Test now matches reality.
- **Standings query parameters** — Plan-skeleton used `?phaseId=&groupId=`; actual `StandingsController` uses `?phase=&group=`. Test uses the actual names.
- **Driver-import preview row counter** — Plan used `.driver-row` CSS class which does not exist; the rendered template emits plain `<tr>` rows inside the New Drivers `<table>`. Test asserts `table tbody tr` instead.

## TDD Gate Compliance

This plan's frontmatter declared `type: execute` (not `tdd: true`). The test is itself the deliverable; no production code change accompanies it. Single `test(61-04)` commit is the appropriate gate.

## Threat Flags

None — the plan adds tests only; no new endpoints, auth flows, or trust boundaries were introduced. The `@TestConfiguration` GoogleSheetsService stub is test-scope (`@Bean @Primary` only loaded via `@Import` on this class) and cannot leak into production.

## Known Stubs

None — the test exercises real PhaseTeam/Matchday/Race/RaceResult/Match aggregation chains end-to-end. No placeholder data flows to UI rendering.

## User Setup Required

None — no external service configuration required. Playwright Chromium installation is already present (`~/Library/Caches/ms-playwright/chromium-1217`); `./mvnw verify -Pe2e` runs unattended.

## Next Phase Readiness

- **Plan 61-05 (QUAL-03 Regression-E2E + Coverage-Repair) is unblocked.** — JaCoCo coverage check still passes at 82% threshold; this plan added test-only code (no production-code coverage delta).
- **No sibling test regression.** — Full `./mvnw verify -Pe2e` BUILD SUCCESS confirms `AdminWorkflowE2ETest`, `ImportE2eTest`, `ScoringE2ETest` continue to pass alongside the new test.
- **Worktree state clean.** — Branch `gsd/v1.9-season-phases-groups`; only `28c1204` pushed; STATE.md / ROADMAP.md untouched (orchestrator-owned).

## Self-Check: PASSED

- File `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java`: FOUND
- Commit `28c1204`: FOUND in git log

```
$ ls -1 src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java
src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java

$ git log --oneline | grep 28c1204
28c1204 test(61-04): add GROUPS-Saison E2E test (QUAL-02)

$ ./mvnw verify -Pe2e            # → BUILD SUCCESS, 29 E2E tests, JaCoCo gate held
```

---
*Phase: 61-cleanup-quality-gate*
*Completed: 2026-05-01*
