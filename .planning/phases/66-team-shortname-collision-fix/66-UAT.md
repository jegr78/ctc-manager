---
status: partial
phase: 66-team-shortname-collision-fix
source: [66-01-SUMMARY.md]
started: 2026-05-08T05:00:00Z
updated: 2026-05-08T05:25:00Z
---

## Current Test

[testing paused — 1 issue + 1 blocked + 1 skipped; awaiting fix on reported gaps]

## Tests

### 1. Driver Import Preview — No Crash On ShortName Collision
expected: Open admin Driver Import page (e.g. http://localhost:9090/admin/driver-import), select a season whose teams contain a parent team plus at least one sub-team sharing the same `shortName` (e.g. parent `ZFS` + sub `ZFS`), and click "Preview". Page renders the preview without a 500 error / NonUniqueResultException.
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
  artifacts:
    - path: "src/main/java/org/ctc/dataimport/DriverSheetImportService.java"
      issue: "resolveTeamByShortName (lines 411-427) prefers parent (parentTeam == null) unconditionally; ignores PhaseTeam(REGULAR) presence in target season — semantically wrong when parent is organizational-only bucket"
  missing:
    - "Resolver must consider target season context: prefer match that has a PhaseTeam in season's REGULAR phase; fall back to parent only if none"
    - "Or: treat parent-only buckets as non-importable and force sheet to use sub-team-specific shortNames"
  debug_session: ""

- truth: "Group-assignment warnings should not surface for teams in seasons that have no GROUPS layout at all"
  status: failed
  reason: "User reported (Test 1 follow-up): season 2024 has no groups (no GROUPS layout) — every team in the import preview gets a 'No group' warning bubble in the preview, which is noise rather than a real problem. The TEAM_NOT_IN_REGULAR_PHASE warning logic in DriverSheetImportService should skip the PhaseTeam group-resolution branch when the season has no groups configured."
  severity: minor
  test: 1
  artifacts:
    - path: "src/main/java/org/ctc/dataimport/DriverSheetImportService.java"
      issue: "Group-resolution branch (lines 311-325) emits warnings whenever PhaseTeam.group is null/missing, regardless of whether the season actually uses groups"
  missing:
    - "Detect group-less seasons (no SeasonPhaseGroup rows for REGULAR phase, or season layout != GROUPS) and short-circuit group warning emission"
  debug_session: ""
