---
plan_id: 61-gap-05
phase: 61-cleanup-quality-gate
title: Javadoc audit — admin.controller + admin.service public APIs
wave: 1
gap_closure: true
autonomous: true
depends_on: []
files_modified:
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/admin/controller/SeasonPhaseController.java
  - src/main/java/org/ctc/admin/controller/SeasonPhaseGroupController.java
  - src/main/java/org/ctc/admin/controller/PlayoffController.java
  - src/main/java/org/ctc/admin/controller/MatchdayController.java
  - src/main/java/org/ctc/admin/controller/RaceController.java
  - src/main/java/org/ctc/admin/controller/StandingsController.java
  - src/main/java/org/ctc/admin/service/SeasonFormService.java
  - src/main/java/org/ctc/admin/service/SeasonPhaseFormService.java
  - src/main/java/org/ctc/admin/service/PlayoffFormService.java
  - src/main/java/org/ctc/admin/service/MatchdayFormService.java
  - src/main/java/org/ctc/admin/service/StandingsViewService.java
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "Controller @GetMapping/@PostMapping methods that have non-obvious URL contracts have Javadoc"
  - "Admin service methods documenting form-binding contracts where non-obvious"
  - "No boilerplate Javadoc added (CLAUDE.md)"
  - "./mvnw test stays GREEN with no behavior change"
---

<objective>
Audit and fix Javadoc on public methods of `admin.controller` + `admin.service`. Controllers in this app
follow CLAUDE.md "thin controllers" — most are short and self-evident. Javadoc additions here are limited to:

1. Endpoints with non-obvious URL contracts (e.g., legacy auto-redirect paths like `/admin/standings?seasonId=...`
   which auto-resolve to the REGULAR phase per Phase 60 D-12)
2. Form-handler methods that bind specific DTOs with validation interactions worth documenting
3. Admin services that orchestrate multi-step operations (e.g., season-with-phase auto-bootstrap)

Purpose: G5 cleanup for the admin layer.

Output: all listed files have accurate Javadoc on public methods where WHY is non-obvious. Test gate stays green.
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

**CLAUDE.md hard rules:**
- "Keep Controllers Thin" — implies controllers seldom warrant Javadoc; only when URL/redirect contract is non-obvious
- "DTOs instead of Entities in Controllers" — form-binding methods may warrant Javadoc explaining the DTO choice
- "default to no comments"

**Scope discipline:** Most controller GET/POST handlers are obvious from `@GetMapping("/seasons")` + method
name. Do NOT add boilerplate. Add Javadoc only on:
- Auto-redirect endpoints (legacy URL → canonical URL)
- Endpoints whose return value's redirect target is non-obvious
- Admin services with multi-step orchestration semantics
</context>

<tasks>

<task id="1" name="Audit + fix Javadoc on admin.controller public handlers">
  <action>
    For each of the 7 controller files in `files_modified`, audit the public `@GetMapping` / `@PostMapping` methods.

    Concrete observed targets (not exhaustive — applies the rules below):

    `StandingsController.java`:
    - The legacy `/admin/standings?season={id}` auto-resolve to REGULAR phase (Phase 60 D-12, D-31) IS a
      non-obvious URL contract — add Javadoc on the GET handler explaining: "Legacy URL parameter `season` is
      auto-resolved to the season's REGULAR phase before delegating to the phase-keyed view. Both `season`
      and `phaseId` query parameters are supported; `phaseId` takes precedence if both are present."

    `PlayoffController.java`:
    - The deleted `/admin/playoffs/{id}/add-season` and `/remove-season` (Phase 61 D-03) — already removed.
      No Javadoc target here, just confirm no orphan references remain.

    `SeasonController.java`, `SeasonPhaseController.java`, `SeasonPhaseGroupController.java`:
    - Form-handler `@PostMapping("/save")` methods — Javadoc should describe the redirect target
      (if non-obvious — e.g., `redirect:/admin/seasons/{id}` after save). If the redirect is the obvious
      "stay on entity detail page", no Javadoc.
    - GET handlers that supply specific model attributes worth documenting (e.g., `combinedView` flag) —
      add Javadoc IF the flag's effect is non-obvious from controller logic.

    `MatchdayController.java`, `RaceController.java`:
    - Convenience-Getter caller sites (`matchday.getSeason()`) — these are now first-class API per D-02.
      No Javadoc needed on caller; the contract is on the entity.

    Apply the rules: add Javadoc only where WHY is non-obvious. Do NOT add `/** GET handler for season list. */`
    -style boilerplate.

    Commit message: `docs(61-gap): add Javadoc on admin.controller endpoints with non-obvious contracts`
  </action>
  <read_first>
    - All 7 controller files in files_modified
    - .planning/phases/61-cleanup-quality-gate/61-CONTEXT.md (D-03 Tracked Behavior Change for legacy URL — informs StandingsController docs)
    - CLAUDE.md "Keep Controllers Thin"
  </read_first>
  <acceptance_criteria>
    - `StandingsController.java` has at least one Javadoc block describing the legacy URL auto-resolve contract
      (`grep -B5 "@GetMapping" src/main/java/org/ctc/admin/controller/StandingsController.java | grep -c "Legacy"` >= 1)
    - `./mvnw test -Dtest='*ControllerTest'` is BUILD SUCCESS
    - No method bodies modified: `git diff HEAD~1 -- 'src/main/java/org/ctc/admin/controller/' | grep '^+' | grep -vE "^\+\+\+|^\+\s*\*|^\+\s*/\*\*|^\+\s*\*/|^\+\s*$|^\+\s*//"` returns 0 lines
    - Test count UNCHANGED (Javadoc-only changes)
  </acceptance_criteria>
</task>

<task id="2" name="Audit + fix Javadoc on admin.service public methods">
  <action>
    For each of the 5 admin.service files in `files_modified`, audit public methods.

    Concrete observed targets:

    `SeasonFormService.java`:
    - `save(SeasonForm form)` — if it auto-creates the REGULAR phase atomically (per Phase 58 D-19), Javadoc
      MUST document this contract: "Persists the season and atomically bootstraps a REGULAR SeasonPhase with
      LEAGUE layout. The phase carries default scoring inherited from the form's race/match scoring fields."
    - `buildForm(UUID seasonId)` — if it pre-populates from the REGULAR phase (post-cascade), document.

    `SeasonPhaseFormService.java`:
    - Multi-step orchestrations (creating a phase + its initial roster + groups in one call) deserve Javadoc.

    `PlayoffFormService.java`:
    - Auto-PLAYOFF-phase creation contract.

    `MatchdayFormService.java`, `StandingsViewService.java`:
    - Audit; add Javadoc only where WHY is non-obvious.

    There are 19 files in admin.service total — only the 5 listed in `files_modified` are explicit targets.
    For the remaining 14, run a quick `grep -L "/\*\*" src/main/java/org/ctc/admin/service/*.java` to find
    files with NO Javadoc; if any contain non-obvious public methods, add them to the SUMMARY as audit findings.
    Do NOT touch them in this plan unless trivially obvious (1-line missing Javadoc).

    Commit message: `docs(61-gap): add Javadoc on admin.service public methods with non-obvious contracts`
  </action>
  <read_first>
    - All 5 admin.service files in files_modified
    - .planning/codebase/CONVENTIONS.md (admin layer naming + DTO patterns)
  </read_first>
  <acceptance_criteria>
    - `SeasonFormService.save(...)` has Javadoc documenting the REGULAR-phase auto-bootstrap contract
      (`grep -B5 "public.*save" src/main/java/org/ctc/admin/service/SeasonFormService.java | grep -ic "REGULAR\|phase"` >= 1)
    - `./mvnw test -Dtest='*FormServiceTest,*ViewServiceTest'` is BUILD SUCCESS
    - Test count UNCHANGED (Javadoc-only changes)
    - `git diff HEAD~1 -- 'src/main/java/org/ctc/admin/service/' | grep '^+' | grep -vE "^\+\+\+|^\+\s*\*|^\+\s*/\*\*|^\+\s*\*/|^\+\s*$|^\+\s*//"` returns 0 lines (Javadoc-only)
  </acceptance_criteria>
</task>

<task id="3" name="Verify javadoc tooling integrity">
  <action>
    Run a Javadoc generation pass:

    ```bash
    ./mvnw javadoc:javadoc -Dquiet=true 2>&1 | tail -50
    ```

    Expected: no new errors compared to the baseline established in 61-gap-04-Task-3.

    Run final test gate:
    ```bash
    ./mvnw test
    ```
    Expected: BUILD SUCCESS, 1172 tests (or post-CR-01 baseline — must not decrease).

    No commit if no fixes needed. If fixes needed:
    Commit message: `docs(61-gap): repair Javadoc tooling regressions from admin G5 sweep`
  </action>
  <read_first>
    - Outputs of Tasks 1 + 2
  </read_first>
  <acceptance_criteria>
    - `./mvnw javadoc:javadoc -Dquiet=true` returns BUILD SUCCESS
    - `./mvnw test` returns BUILD SUCCESS
    - JaCoCo threshold unchanged: `grep -c '<minimum>0.82</minimum>' pom.xml` returns 1
  </acceptance_criteria>
</task>

</tasks>

<verification>
- ./mvnw test BUILD SUCCESS
- ./mvnw javadoc:javadoc returns no new errors
- StandingsController legacy URL contract documented
- SeasonFormService REGULAR-phase auto-bootstrap documented
</verification>

<success_criteria>
1. Admin controller endpoints with non-obvious URL contracts have Javadoc
2. Admin service multi-step orchestrations are documented
3. No boilerplate added; obvious methods unchanged
4. Javadoc tooling builds clean
</success_criteria>

<output>
Create `.planning/phases/61-cleanup-quality-gate/61-gap-05-SUMMARY.md`.
</output>
