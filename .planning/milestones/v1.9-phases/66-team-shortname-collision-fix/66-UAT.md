---
status: diagnosed
phase: 66-team-shortname-collision-fix
source: [66-01-SUMMARY.md]
started: 2026-05-08T05:00:00Z
updated: 2026-05-08T06:30:00Z
---

## Current Test

[testing complete — 2 gaps diagnosed, awaiting fix plans]

## Tests

### 1. Driver Import Preview — No Crash On ShortName Collision
expected: Open admin Driver Import page (e.g. http://localhost:9091/admin/drivers/import), select a season whose teams contain a parent team plus at least one sub-team sharing the same `shortName` (e.g. parent `ZFS` + sub `ZFS`), and click "Preview". Page renders the preview without a 500 error / NonUniqueResultException.
result: pass
note: User confirmed "Der ursprüngliche Fehler ist weg" — original NonUniqueResultException no longer occurs across seasons 2023 (MRL), 2024 (no groups), 2025 (P1R, ZFS).

### 2. Driver Import Resolves To Parent Team On Collision
expected: In the preview from Test 1, drivers whose sheet shortName matches the colliding `shortName` are assigned to the **parent** team (not the sub-team). Visible in New Drivers / New Assignments / Conflicts buckets — team name column shows the parent team's full name.
result: issue
reported: "PArent Team wird verwendet, jedoch mit Warning in der Preview. Dies führt dann am Ende möglicherweise zu einem unvollstöndigen Import"
severity: major

### 3. Driver Import Execute (After Preview With Collision) Succeeds
expected: After confirming the preview from Test 1, click "Execute" / "Import". Import completes without exceptions; affected drivers persist with the parent team set as their team. Re-opening the season's roster confirms the parent team assignment.
result: blocked
blocked_by: other
reason: "User: 'Solange der Import noch die Warnungen mit den Parent Teams und Gruppenzuordnungen bei Saisons ohne Gruppen hat, werde ich diesen Schritt nicht ausführen. Muss auf Fix warten' — execute step gated on resolution of Test-2 gap (parent-precedence revision + group warnings for seasons without groups)."

### 4. Driver Import Without Collision Still Works (Regression Guard)
expected: Open a preview against a season whose teams do **not** share `shortName`. Buckets render exactly as before — no warnings, no errors, drivers route to the correct unique team. No regression compared to pre-Phase-66 behavior.
result: skipped
reason: "User: 'Diesen Fall kann ich so nicht nachstellen. Es wird nur ein Sheet für den Import mit allen Importen haben und in jeder Saison gibt es mindestens 1 Team mit Sub-Teams und dadurch auch kollidierenden Short Names. Daher macht dieser Test aus meiner Sicht keinen Sinn' — real-world data shape: every season has at least one parent+sub-team shortName collision, so the no-collision path is not reachable from production sheets."

## Summary

total: 4
passed: 1
issues: 1
pending: 0
skipped: 1
blocked: 1

## Gaps

- truth: "Driver import resolves shortName collision to a team that has a PhaseTeam in the target season's REGULAR phase, so group assignment succeeds without warnings and the import is complete"
  status: failed
  reason: "User reported (Test 1 + Test 2): parent-precedence resolver picks parent team (e.g. MRL, P1R, ZFS) which has no PhaseTeam(REGULAR) for the target season → 'Team X has no PhaseTeam in REGULAR phase of target season' warning surfaces in preview AND may lead to incomplete import. Verbatim: 'PArent Team wird verwendet, jedoch mit Warning in der Preview. Dies führt dann am Ende möglicherweise zu einem unvollstöndigen Import'. Sub-team is the entity actually racing in the season. Confirmed via /admin/teams: MRL has sub-teams MRL 1 + MRL 2; P1R has sub-teams P1R + P1Rx; ZFS has 2 sub-teams. Parent acts as organizational bucket only."
  severity: major
  test: 2
  root_cause: "DriverSheetImportService.resolveTeamByShortName (lines 411-427) is a season-blind resolver. On multi-match it picks parentTeam == null unconditionally per CONTEXT.md D-06. The user's actual data inverts D-06's assumption: parents (MRL, P1R, ZFS) are organisational buckets without PhaseTeam(REGULAR) entries; sub-teams are the racing entities. The resolver picks the bucket parent → line 314-315 phaseTeamRepository.findByPhaseIdAndTeamId(regularPhase.id, parent.id) misses → TEAM_NOT_IN_REGULAR_PHASE warning fires → resolvedGroupName stays null → on execute, SeasonDriver.team persists the wrong (parent) team. UAT Test 4 was skipped because every real season has at least one such collision, confirming this is a dominant-path bug, not a corner case."
  artifacts:
    - path: "src/main/java/org/ctc/dataimport/DriverSheetImportService.java"
      issue: "resolveTeamByShortName (lines 411-427) is season-blind; 5 call sites at lines 135, 146, 166, 195, 296 pass only shortName"
    - path: "src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java"
      issue: "TDD tests givenTeamsWithSameShortNameParentAndSub_whenPreview_thenResolvesParentTeam (D-11) and the multi-parent edge test (D-12) codify the wrong contract — assert parent wins; need fixture revision (add PhaseTeam stubs) and inverted assertion (sub-team wins when it has PhaseTeam in target REGULAR phase)"
    - path: ".planning/phases/66-team-shortname-collision-fix/66-CONTEXT.md"
      issue: "Decision D-06 (parent precedence) rests on a false assumption; needs revision to sub-team-with-PhaseTeam-in-target-season precedence with parent precedence as last fallback only"
  missing:
    - "Resolver becomes season-aware: signature `resolveTeamByShortName(String shortName, SeasonPhase regularPhase)`; new algorithm prefers candidate with PhaseTeam in target REGULAR phase, falls back to parent only when none of the candidates have a PhaseTeam (legacy/no-REGULAR-phase data)"
    - "All 5 call sites pass regularPhase: line 296 (preview) already has it cached at line 252; the four execute call sites (135, 146, 166, 195) resolve once per season via seasonPhaseService.findRegularPhase(season.getId()) with EntityNotFoundException → null graceful fallback"
    - "Existing TDD tests (D-11, D-12) inverted: assert sub-team-with-PhaseTeam wins; multi-parent edge becomes the no-PhaseTeam fallback case"
    - "Revise CONTEXT.md D-06 to document new precedence (sub-team-with-PhaseTeam-in-target-REGULAR-phase first, parent precedence as legacy/no-PhaseTeam fallback)"
  debug_session: ".planning/debug/shortname-resolver-picks-parent-without-phaseteam.md"

- truth: "Group-assignment warnings should not surface for teams in seasons that have no GROUPS layout at all"
  status: failed
  reason: "User reported (Test 1 follow-up): season 2024 has no groups (no GROUPS layout) — every team in the import preview gets a 'No group' warning bubble in the preview, which is noise rather than a real problem. The TEAM_NOT_IN_REGULAR_PHASE warning logic in DriverSheetImportService should skip the PhaseTeam group-resolution branch when the season has no groups configured."
  severity: minor
  test: 1
  root_cause: "DriverSheetImportService.buildTabPreview lines 311-325 fires the TEAM_NOT_IN_REGULAR_PHASE warning whenever a team has no PhaseTeam row in the season's REGULAR phase, guarded only by `if (regularPhase != null)`. For LEAGUE-layout REGULAR phases (e.g. season 2024) every team naturally has PhaseTeam.group == null, so the warning fires for every team. The canonical signal `regularPhase.getLayout() == PhaseLayout.GROUPS` is already in scope (regularPhase loaded 8 lines above) but never consulted. Secondary surface: DriverSheetImportController.preview (lines 57-64) computes `showGroupColumn` page-wide as 'any resolved season uses GROUPS' — so even after the warning is suppressed, the per-row '⚠ No group' badge still renders on rows belonging to LEAGUE tabs in mixed multi-tab previews. No per-tab gate exists."
  artifacts:
    - path: "src/main/java/org/ctc/dataimport/DriverSheetImportService.java"
      issue: "Group-resolution branch (lines 311-325) emits warnings without checking layout; TabPreview record (lines 433-446) lacks `usesGroups` field needed for per-tab template gating"
    - path: "src/main/java/org/ctc/admin/controller/DriverSheetImportController.java"
      issue: "showGroupColumn (lines 57-64) is page-wide via anyMatch — fine for column-header gate but row-cell needs additional per-tab gate"
    - path: "src/main/resources/templates/admin/driver-import-preview.html"
      issue: "Five buckets render `<td th:if=\"${showGroupColumn}\">` with the '⚠ No group' badge; needs `${showGroupColumn and tab.usesGroups()}` gating + '—' fallback for non-GROUPS tabs"
    - path: "src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java"
      issue: "Tests #16 (givenTeamMissingFromRegularPhase_whenPreview_thenWarningEmitted) and #17 use PhaseTestFixtures.regularPhase(...) which hardcodes PhaseLayout.LEAGUE — these tests currently codify the bug as the contract, masking the regression"
  missing:
    - "Service: change gate at line 313 to `if (regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS)`; add `boolean usesGroups` field to TabPreview record populated as `regularPhase != null && regularPhase.getLayout() == PhaseLayout.GROUPS`"
    - "Template: add `tab.usesGroups()` per-tab gate on row-level Group `<td>` cells; render '—' when false; keep page-level showGroupColumn for column-header decision"
    - "Tests: invert tests #16 and #17 to use PhaseTestFixtures.groupsRegularPhase(...); add new test `givenLeagueLayoutRegularPhaseAndTeamWithoutGroup_whenPreview_thenNoWarningAndUsesGroupsFalse` asserting empty warnings + usesGroups=false + no PhaseTeam lookup invocation"
  debug_session: ".planning/debug/group-warnings-for-non-groups-seasons.md"
