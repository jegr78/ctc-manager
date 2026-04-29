---
status: partial
phase: 59-import-test-data
source: [59-01-SUMMARY.md, 59-02-SUMMARY.md, 59-03-SUMMARY.md, 59-04-SUMMARY.md, 59-VERIFICATION.md]
started: 2026-04-29T18:20:12Z
updated: 2026-04-29T18:30:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: |
  Kill any running ctc-manager instance, then start it fresh:
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
  The server boots without errors. `DevDataSeeder` runs and `TestDataService.seed()`
  creates the consolidated 2023 GROUPS season (year=2023, number=1) with two named
  groups (Group A, Group B). Browse to http://localhost:9090/admin/seasons —
  exactly ONE 2023 entry appears in the season list (no longer two pseudo-seasons
  for "Group A"/"Group B").
result: pass

### 2. TabWarning Badge Rendering in Driver Import Preview
expected: |
  In the running dev server, navigate to /admin/drivers/import and paste a Google
  Sheet URL (or use a fresh sheet) that contains a tab named `2023` (or `2023_S1`)
  where the team list includes at least one team code that has NO `PhaseTeam` row
  in the consolidated 2023 REGULAR phase. After clicking "Preview", the per-tab
  card displays a yellow/amber warning block titled "Group assignment warnings"
  listing the offending team(s) with the message
  "Team has no PhaseTeam in REGULAR phase".
  Each affected team appears once (deduplicated) regardless of how many drivers
  belong to it in the sheet.
result: issue
reported: "500 — Internal Error: UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only. Log shows tab 2025 was processed (116 errors, 0 warnings) but outer @Transactional commit on DriverSheetImportService.preview() fails. Root cause likely: SeasonManagementService.findUnique() inner @Transactional(readOnly=true) throws BusinessRuleException, which marks the shared tx rollback-only; preview() catches the exception for control flow but Spring still aborts the commit."
severity: blocker

### 3. Group Resolution in Driver Import Preview
expected: |
  Using a sheet tab with teams that DO have PhaseTeam rows in the consolidated
  2023 REGULAR phase (Group A: ADR/AKR/ALF; Group B: BMR/CFC/EGP — see
  TestDataService seedPhaseTeams), the preview rows show the resolved group
  in the New Drivers / New Assignments tables. Drivers from Group A teams
  resolve to Group A; drivers from Group B teams resolve to Group B.
  No manual group override appears in the sheet — group is purely derived
  from PhaseTeam(REGULAR) of the target season.
result: blocked
blocked_by: prior-test
reason: "Same DriverSheetImportService.preview() code path as Test 2 — UnexpectedRollbackException blocks any preview rendering until Test 2's transaction-propagation bug is fixed."

### 4. 2023 Standings shows Group A + Group B under a single season
expected: |
  Navigate to /admin/standings and select the 2023 season. The page shows
  drivers grouped by Group A and Group B (or offers a group selector with
  both options). Drivers seeded for Group A teams (ADR/AKR/ALF/...) appear
  in the Group A roster; drivers for Group B teams (BMR/CFC/EGP/...) appear
  in the Group B roster. Both groups exist under the SAME season entry, not
  under two separate "2023 Group A" / "2023 Group B" pseudo-seasons.
result: skipped
reason: "Test expectation was overscoped for Phase 59. The DATA shape is correct (user confirmed: single consolidated 2023 season visible in the dropdown, no longer two pseudo-seasons — SC#5's data-layer requirement met). The UI rendering of groups (group selector + Combined view in standings) is explicitly deferred to Phase 60 UI-05 per ROADMAP Phase 60 SC5. The user observed 'Ich sehe keine Gruppen' — correctly reflecting that the standings page is still flat; that affordance ships in Phase 60."

### 5. 2023 Playoff Bracket auto-seeded from REGULAR phase Top-4
expected: |
  Navigate to /admin/playoffs and open the 2023 playoff. The bracket is
  populated with the Top-4 drivers/teams derived from the combined REGULAR-phase
  standings via PlayoffSeedingService.autoSeedBracket. The legacy "two seasons
  joined via M:N hack" pattern is gone — there is no `playoff.seasons` mapping
  to "2023 Group B"; the playoff is anchored to the consolidated 2023 season's
  PLAYOFF SeasonPhase.
result: pass

## Summary

total: 5
passed: 2
issues: 1
pending: 0
skipped: 1
blocked: 1

## Gaps

- truth: "The driver-import preview page renders without server error and displays a yellow/amber 'Group assignment warnings' block listing teams with no PhaseTeam in the REGULAR phase, deduplicated per team"
  status: failed
  reason: "User reported: 500 — Internal Error: UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only. Log shows tab 2025 was processed (116 errors, 0 warnings) but outer @Transactional commit on DriverSheetImportService.preview() fails."
  root_cause: "SeasonManagementService.findUnique(int year) and findUnique(int year, int number) carry @Transactional(readOnly=true) with default REQUIRED propagation, joining the outer preview() transaction. When findByYear(2025) returns multiple seasons (dev profile seeds Test-Season 2025 alongside the user's real 2025 season), findUnique throws BusinessRuleException. Spring's TransactionInterceptor marks the shared tx as rollback-only BEFORE the exception returns to Java. buildTabPreview catches BusinessRuleException to populate ambiguousReason (D-03 / D-18), but the tx is already poisoned. When preview() returns normally, Spring's AOP commit aborts with UnexpectedRollbackException."
  why_tests_miss_it: "DriverSheetImportServiceIT is annotated @Transactional on the class — Spring auto-rolls-back the test tx, so commit-time UnexpectedRollbackException never fires."
  recommended_fix: "Option C from debug session: remove @Transactional annotations from both findUnique overloads in SeasonManagementService. Mirrors Phase 58 precedent (Bridge uses findByType (Optional) instead of findRegularPhase to avoid transaction rollback-only poisoning). The methods already run inside the caller's transaction context; the readOnly=true annotation is redundant for these single-select methods."
  severity: blocker
  test: 2
  debug_session: ".planning/debug/59-tx-rollback-on-preview.md"
  artifacts:
    - "src/main/java/org/ctc/domain/service/SeasonManagementService.java"
  missing:
    - "Integration test that exercises preview() WITHOUT class-level @Transactional, so commit-time exceptions surface (recommended path: new DriverSheetImportServiceTransactionIT)"
