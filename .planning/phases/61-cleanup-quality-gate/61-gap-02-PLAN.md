---
plan_id: 61-gap-02
phase: 61-cleanup-quality-gate
title: Stale comments — admin.controller + admin.service + admin (TestDataService)
wave: 1
gap_closure: true
autonomous: true
depends_on: []
files_modified:
  - src/main/java/org/ctc/admin/TestDataService.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/admin/controller/SeasonPhaseController.java
  - src/main/java/org/ctc/admin/controller/SeasonPhaseGroupController.java
  - src/main/java/org/ctc/admin/controller/PlayoffController.java
  - src/main/java/org/ctc/admin/controller/MatchdayController.java
  - src/main/java/org/ctc/admin/controller/RaceController.java
  - src/main/java/org/ctc/admin/controller/StandingsController.java
  - src/main/java/org/ctc/admin/controller/AdminHomeController.java
  - src/main/java/org/ctc/admin/controller/TeamController.java
  - src/main/java/org/ctc/admin/controller/DriverController.java
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "grep -rn -E 'Phase [56][0-9] MIGR-06|Phase [56][0-9] D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+' src/main/java/org/ctc/admin/ returns 0 lines"
  - "grep -rn -E 'Wave [0-9]+ cascade|cascade migration|transitional bridge' src/main/java/org/ctc/admin/ returns 0 lines"
  - "TestDataService keeps its functional structure; only stale comments are stripped"
  - "./mvnw test stays GREEN with no behavior change"
---

<objective>
Remove stale Phase-narrative comments from the admin layer (controllers + admin services + the central
`TestDataService`). The Phase 60 + 61 cascade left ~200 phase-tagged comments here; this plan covers the admin
controller package + `TestDataService` (the heaviest single file in the codebase regarding `Phase 61 MIGR-06` markers).

Purpose: G2 cleanup for the admin layer. Comment-only changes — zero behavior risk.

Output: all listed files cleaned of phase-narrative comments. Test gate stays green.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/61-cleanup-quality-gate/61-CONTEXT.md
@.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
@CLAUDE.md

Same classification rules as 61-gap-01 (REMOVE stale phase narrative; KEEP genuine WHY).

**TestDataService special handling:** This is the central dev/demo data seeder. Its comments include genuinely
useful structural markers like "// === Season 2024: Swiss + Playoff ===" — those describe data-shape, not
process history, and they STAY. Drop only `// Phase 61 MIGR-06: ...`, `// Phase 61 WR-07: ...`, `// Phase 58 D-XX: ...`
phase-tagged narrative.

**Controllers — remember CLAUDE.md:** Controllers should be thin. Comments here are often process
breadcrumbs ("// Phase 60 D-43 hidden in UI but functional ..."); those are 100% removable now that the cascade
is shipped. Keep `// CSRF token required ...` style comments that name a permanent contract.
</context>

<tasks>

<task id="1" name="Audit + remove stale comments in admin/controller">
  <action>
    For each controller listed in `files_modified`, classify and remove stale phase-narrative comments per the
    rules from 61-gap-01. Concrete observed targets:

    - `SeasonController.java:203, :207, :233` — three `// Phase 61 MIGR-06: totalRounds moved to REGULAR phase.` lines. The surrounding code already shows the REGULAR-phase lookup; the comment is process history. DROP all three.
    - `SeasonPhaseController.java:95` — `// Server-flags (combined/showGroupColumn used by Standings reuse on the same template)` — the "used by X" caller-ref is forbidden by CLAUDE.md. Either drop entirely or replace with a one-line WHY ("// flags driving conditional column rendering") if the code is non-obvious.
    - `SeasonPhaseController.java`, `SeasonPhaseGroupController.java` — both have ~13-18 D-XX markers. Strip phase-tag prefixes; keep only WHY-only residue.
    - `PlayoffController.java`, `MatchdayController.java`, `RaceController.java`, `StandingsController.java` — strip `// Phase 60 D-XX: ...` and `// Phase 61 ...` markers.
    - `AdminHomeController.java`, `TeamController.java`, `DriverController.java` — these are lower density; quick scan + strip.

    Commit message: `docs(61-gap): remove stale phase-narrative comments from admin.controller`
  </action>
  <read_first>
    - All 11 files in src/main/java/org/ctc/admin/controller/ listed in files_modified
    - .planning/phases/61-cleanup-quality-gate/61-CONTEXT.md (D-03 Tracked Behavior Change for legacy URL — comment-context only)
  </read_first>
  <acceptance_criteria>
    - `grep -rn -E "Phase [56][0-9] MIGR-06|Phase [56][0-9] D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+" src/main/java/org/ctc/admin/controller/ | grep -v '^#'` returns 0 lines
    - `grep -rn -E "used by [A-Z][a-zA-Z]+|added for [a-z]+ flow" src/main/java/org/ctc/admin/controller/ | grep -v '^#'` returns 0 lines
    - `./mvnw test -Dtest='org.ctc.admin.controller.*Test'` is BUILD SUCCESS
    - Test count UNCHANGED (no @Test removed): `grep -c '@Test' src/test/java/org/ctc/admin/controller/*.java | awk -F: '{s+=$2} END {print s}'` is identical pre/post
  </acceptance_criteria>
</task>

<task id="2" name="Audit + remove stale comments in admin/TestDataService.java">
  <action>
    `TestDataService.java` (the central dev/demo seeder) has the highest single-file `Phase 61 MIGR-06` density
    in the codebase. Concrete observed targets:

    - Line 191, 232, 262: `// Phase 61 MIGR-06: format moved to REGULAR phase (s1Regular.setFormat below).` — drop the phase prefix; the parenthetical explanation IS useful (points at a non-obvious sibling line), so KEEP it as: `// format moved to REGULAR phase (s1Regular.setFormat below).` OR just delete entirely if the next line `s1Regular.setFormat(...)` is self-evident.
    - Line 411: `// Phase 61 MIGR-06: scoring moved to SeasonPhase. The `scorings` argument is preserved` — same treatment; keep the substantive WHY ("argument preserved for backward-compat with TestDataServiceIntegrationTest"), drop phase tag.
    - Line 898: `// Phase 61 MIGR-06: matchdays bind via REGULAR phase, so attach one to test-season-2.` — drop phase tag, keep behavioral note if non-obvious.
    - Line 933: `// (Phase 58 D-15 + D-19 + D-20). Eliminates the legacy M:N season-link hack (Phase 61 MIGR-06).` — this is pure history. DROP entirely.
    - Line 941, 969: `// Phase 61 MIGR-06: matchday bound to PLAYOFF phase (auto-created by createPlayoff above).` — the parenthetical is useful (callers may not know `createPlayoff` auto-creates the phase). KEEP the WHY, drop the phase prefix.
    - Line 958: `// Phase 61 WR-07: align with the 2023 branch — autoSeedBracket pulls Top-N from the ...` — drop the WR-07 tag; KEEP the algorithmic rationale (it's genuinely useful — autoSeedBracket's Top-N behavior is non-obvious).

    **DO NOT TOUCH:**
    - Section headers like `// === Season 2024: Swiss + Playoff ===` (data-shape markers, not process history)
    - The functional code itself (this is a comment-only pass)
    - Any `T-` prefixed test names (they are CLAUDE.md-mandated isolation markers)

    Commit message: `docs(61-gap): remove stale phase-narrative comments from TestDataService`
  </action>
  <read_first>
    - src/main/java/org/ctc/admin/TestDataService.java (full file — large, ~1000 lines)
    - CLAUDE.md "Isolate Test Data Completely" rule — must not break T-prefix isolation
    - .planning/phases/61-cleanup-quality-gate/61-REVIEW-FIX.md (WR-07 fix narrative — the algorithmic point in line 958 is real)
  </read_first>
  <acceptance_criteria>
    - `grep -c -E "Phase [56][0-9]\s|MIGR-06|WR-0?[0-9]+|CR-0?[0-9]+" src/main/java/org/ctc/admin/TestDataService.java` returns 0
    - `grep -c "T-" src/main/java/org/ctc/admin/TestDataService.java` is UNCHANGED pre/post (T-prefix isolation markers must remain)
    - `grep -c "=== Season" src/main/java/org/ctc/admin/TestDataService.java` is UNCHANGED pre/post (section headers stay)
    - `./mvnw test -Dtest='TestDataServiceIntegrationTest,DevDataSeederIntegrationTest'` is BUILD SUCCESS
    - The functional method count is unchanged: `grep -c "private.*[a-zA-Z]\+(.*)" src/main/java/org/ctc/admin/TestDataService.java` is identical pre/post (no method removal — comment-only)
  </acceptance_criteria>
</task>

<task id="3" name="Audit + final sweep across admin/service + admin/* (other)">
  <action>
    Sweep the remaining admin files NOT explicitly listed in `files_modified` (the ~19 files in
    `admin/service/` and 5 other top-level `admin/*.java` files). Run:

    ```bash
    grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+)" src/main/java/org/ctc/admin/service src/main/java/org/ctc/admin/*.java
    ```

    For each hit, apply the same classification (drop phase narrative; keep substantive WHY without phase prefix).
    If `admin/service/` has fewer than 10 stale markers, fold this into the same commit as Task 2.
    If 10+ markers, separate commit.

    Add the touched files to `files_modified` post-hoc by appending an entry to `.planning/phases/61-cleanup-quality-gate/61-gap-02-SUMMARY.md` (do NOT modify the PLAN frontmatter at execute time — record actuals in the SUMMARY).

    Run final test gate:
    ```bash
    ./mvnw test -Dtest='org.ctc.admin.**'
    ```
    Expected: BUILD SUCCESS.

    Commit message (if separate): `docs(61-gap): remove stale comments from admin.service`
  </action>
  <read_first>
    - All files in src/main/java/org/ctc/admin/service/
    - Top-level files in src/main/java/org/ctc/admin/ (CtcSecurityConfig, etc.)
    - Output of the grep commands above
  </read_first>
  <acceptance_criteria>
    - `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/main/java/org/ctc/admin/ | grep -v '^#'` returns 0 lines
    - `./mvnw test -Dtest='org.ctc.admin.**'` returns BUILD SUCCESS
    - admin layer test count unchanged
  </acceptance_criteria>
</task>

</tasks>

<verification>
- Surefire `./mvnw test -Dtest='org.ctc.admin.**'` BUILD SUCCESS
- All grep gates above return 0
- Test count delta = 0 (comment-only changes)
- TestDataService section-headers (`=== Season XYZ ===`) still present
</verification>

<success_criteria>
1. All admin-layer files have phase-narrative comments removed
2. TestDataService data-shape section headers PRESERVED
3. ./mvnw test BUILD SUCCESS, test count unchanged
4. No accidental code-line modifications (git diff confirms comment-only edits)
</success_criteria>

<output>
After completion, create `.planning/phases/61-cleanup-quality-gate/61-gap-02-SUMMARY.md`.
</output>
