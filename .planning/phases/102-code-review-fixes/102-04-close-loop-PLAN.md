---
phase: 102-code-review-fixes
plan: 04
type: execute
wave: 4
depends_on:
  - 102-03
files_modified:
  - .planning/phases/102-code-review-fixes/102-REVIEW.md
autonomous: false
requirements:
  - REVIEW-FIX-01
  - REVIEW-FIX-02
  - REVIEW-FIX-03

must_haves:
  truths:
    - "./mvnw clean verify -Pe2e exits 0 — Surefire + Failsafe + Playwright E2E all green"
    - "SpotBugs BugInstance count remains 0 (gated by the verify run)"
    - "JaCoCo line coverage ≥ 88.88 % (the in-milestone aspiration baseline; the 82 % pom gate is the hard floor)"
    - "gsd-code-reviewer returns clean (zero critical + zero warning) on the full Phase-102 cumulative diff (~30-50 files)"
    - "102-REVIEW.md authored capturing the close-loop result (the Phase-102 historical record per CONTEXT D-13)"
    - "If remediation tasks surface during the close-loop, they land inline in this plan (added during execution) AND a second review pass confirms clean before SUMMARY.md commits"
  artifacts:
    - path: ".planning/phases/102-code-review-fixes/102-REVIEW.md"
      provides: "Close-loop review record for Phase 102 — first pass result, remediation list (if any), final clean confirmation"
      contains: "Phase 102 Close-Loop Review"
  key_links:
    - from: ".planning/phases/102-code-review-fixes/102-REVIEW.md"
      to: "the Phase-102 cumulative diff on gsd/v1.13-discord-integration"
      via: "documents the gsd-code-reviewer pass(es) on git diff origin/master..HEAD limited to Phase-102 commits"
      pattern: "Phase 102 Close-Loop Review"
---

<objective>
Run the milestone-close gate for Phase 102 per CONTEXT D-04 (full-phase close-loop) and D-09 (end-gate). Two acceptance bars MUST be green:

1. `./mvnw clean verify -Pe2e` exits 0 (Surefire + Failsafe + Playwright E2E + SpotBugs + JaCoCo coverage gate).
2. `gsd-code-reviewer` returns `clean` (zero critical + zero warning) on the full Phase-102 cumulative diff (~30-50 files).

Per CONTEXT D-11: this is the ONE AND ONLY `clean verify -Pe2e` for the entire phase. Plans 102-01/02/03 ran targeted tests only.

Per CONTEXT D-13: this plan AUTHORS `.planning/phases/102-code-review-fixes/102-REVIEW.md` — a NEW close-loop review record. It does NOT delete or rewrite the 10 input `*-REVIEW.md` files from phases 92-101 (those are historical record).

`autonomous: false` — this plan has an orchestrator-pause point (Task 2 dispatches `gsd-code-reviewer` and the orchestrator decides whether to proceed to SUMMARY OR to insert remediation tasks).

Output: 1-N commits depending on remediation needs. Minimum: 102-REVIEW.md + SUMMARY.md. If findings surface: per-finding fix commits + a second review pass before SUMMARY.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/102-code-review-fixes/102-CONTEXT.md
@.planning/phases/102-code-review-fixes/102-01-critical-fixes-PLAN.md
@.planning/phases/102-code-review-fixes/102-02-warning-fixes-refactors-PLAN.md
@.planning/phases/102-code-review-fixes/102-03-info-sweep-PLAN.md
@CLAUDE.md

<execution_notes>
Inline-sequential on `gsd/v1.13-discord-integration` per CONTEXT D-05. The `gsd-code-reviewer` subagent dispatch (Task 2) is READ-ONLY per CLAUDE.md "Subagent Rules" — read-only research/review agents are the only permitted subagent category in v1.13.

Per CONTEXT non-goals: this plan does NOT execute `/gsd-complete-milestone v1.13`. That command is the immediate NEXT step AFTER Phase 102 ships. The PR description refresh also happens AFTER milestone close per CLAUDE.md "Milestone PR Already Exists" rolling-summary discipline.

Per CONTEXT D-14: no CLAUDE.md edits in Phase 102. If the close-loop reviewer surfaces a new "convention rule" candidate, it's recorded in SUMMARY.md as a memory-promotion candidate for the v1.13 RETROSPECTIVE — not edited into CLAUDE.md here.
</execution_notes>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Run full end-of-phase verification — ./mvnw clean verify -Pe2e</name>
  <files>(no source files modified; this task is a verification gate)</files>
  <read_first>
    - CLAUDE.md "Build & Test Discipline" (the canonical end-gate is `./mvnw clean verify -Pe2e`, never plain verify, never with -DskipTests)
    - CONTEXT.md D-09 + D-11 (end-of-phase gate definition)
  </read_first>
  <action>
    Run `./mvnw clean verify -Pe2e` in the repo root. Expected runtime ~17-20 min (CI median 17:39 ± 20 % per ROADMAP success criterion).

    On failure:
    - Per CLAUDE.md "No Flaky Dismissal": every failure is a REGRESSION until proven otherwise via `git stash` + isolated re-run.
    - Per CLAUDE.md "Build & Test Discipline / Clean Maven Build is the Source of Truth": if Maven reports `"Unresolved compilation problem"`, that's an Eclipse JDT signature (NOT javac) — first diagnostic step is `./mvnw clean test-compile`, never edit source first.
    - Diagnose root cause; fix inline (add a remediation task to this plan; atomic-commit per fix); re-run the full `./mvnw clean verify -Pe2e` until green.

    SpotBugs gate: medium+HIGH `BugInstance` count must be 0 (gated by the verify run via `spotbugs-maven-plugin` 4.9.8.3 + `findsecbugs-plugin` 1.14.0).

    JaCoCo gate: line coverage must remain ≥ 88.88 % (the in-milestone aspiration baseline). If coverage drops, the verify run still passes (the pom hard floor is 82 %), but the SUMMARY.md must record the regression and the executor decides whether to add a coverage-recovery task inline.
  </action>
  <verify>
    <automated>./mvnw clean verify -Pe2e</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw clean verify -Pe2e` exits 0.
    - `target/site/jacoco/index.html` shows line coverage ≥ 88.88 % (verify via `grep -E "Total.*[0-9]+\\s*%" target/site/jacoco/index.html | head -2` OR by parsing `target/site/jacoco/jacoco.csv` per CLAUDE.md COV-01 precedent).
    - No `BugInstance` reported (verify via `target/spotbugsXml.xml` line count of `<BugInstance` elements = 0).
    - Surefire + Failsafe + Playwright e2e suites all green.
  </acceptance_criteria>
  <done>End-of-phase test gate green. No commit in this task (no source files modified). If remediation was needed, those fixes already landed as inline atomic commits before this task's final acceptance.</done>
</task>

<task type="checkpoint:decision" gate="blocking">
  <name>Task 2: Orchestrator dispatches gsd-code-reviewer over Phase-102 cumulative diff</name>
  <files>(no source files modified — orchestrator-driven step)</files>
  <decision>Did the close-loop review return clean (zero critical + zero warning) on the full Phase-102 diff?</decision>
  <context>
    Per CONTEXT D-04, this is the **orchestrator-level dispatch** of `/gsd-code-review 102` over the entire Phase-102 cumulative diff. The plan does NOT spawn the reviewer agent itself; the orchestrator dispatches it and inspects the returned findings.

    Diff scope: `git diff --name-only origin/master..HEAD -- 'src/**' '.planning/phases/102-code-review-fixes/**'` — typically 30-50 files per CONTEXT.md `<specifics>` "Plan 102-04 close-loop expected agent run".

    Expected reviewer runtime: ~10-15 minutes at standard depth.

    Per CONTEXT D-04, the reviewer agent has access to: the entire Phase-102 cumulative diff, the 10 per-phase REVIEW.md files (read-only), CLAUDE.md (the convention source of truth), and the 4 Phase-102 PLAN.md files (so it knows what was intentional).
  </context>
  <options>
    <option id="clean">
      <name>Clean — zero critical + zero warning</name>
      <pros>Phase 102 closes; proceed to Task 3 (write 102-REVIEW.md) + Task 4 (SUMMARY.md commit).</pros>
      <cons>None — this is the goal state.</cons>
    </option>
    <option id="remediation-needed">
      <name>Findings surfaced — inline remediation required</name>
      <pros>Catches cross-plan regressions that per-plan reviews missed (e.g., a 102-02 warning fix that accidentally undid a 102-01 critical fix in a different file).</pros>
      <cons>Phase 102 stays open until remediation lands; the executor (NOT a subagent per CONTEXT D-05) writes the fix inline in this plan, atomic-commits per finding, then the orchestrator re-dispatches `gsd-code-reviewer` for a second pass.</cons>
    </option>
  </options>
  <resume-signal>
    If clean: type "clean — proceed to Task 3".
    If remediation needed: type "findings: <count>; proceed to inline remediation" — the orchestrator will then insert remediation tasks AFTER Task 2 and BEFORE Task 3.
  </resume-signal>
  <action>
    Orchestrator: spawn `/gsd-code-review 102` with the full Phase-102 cumulative diff as scope. Capture the resulting REVIEW output (typically a finding list with severity per item).

    If the output contains ANY critical or warning finding, surface them to the user via this checkpoint and BLOCK until remediation lands. Inline remediation = the orchestrator (working inline-sequential per CONTEXT D-05) adds remediation tasks to this PLAN.md as new `<task>` elements, executes them, atomic-commits per finding, then re-dispatches the reviewer.

    If clean: proceed.

    Per CONTEXT.md `<specifics>` "Plan 102-04 SUMMARY.md records both review passes" — the SUMMARY captures the first-pass findings (if any) AND the final clean pass.
  </action>
  <verify>
    <automated>echo "manual checkpoint — orchestrator dispatches gsd-code-reviewer and inspects the output; no automated test"</automated>
  </verify>
  <acceptance_criteria>
    - `gsd-code-reviewer` returned `clean` (zero critical + zero warning) on the most recent pass.
    - If remediation was needed: every reviewer-reported finding has a corresponding inline commit on the milestone branch BEFORE the clean pass landed.
  </acceptance_criteria>
  <done>Reviewer clean confirmed by orchestrator; proceeds to Task 3.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 3: Author 102-REVIEW.md — the close-loop historical record</name>
  <files>.planning/phases/102-code-review-fixes/102-REVIEW.md</files>
  <read_first>
    - Sample existing REVIEW.md format from phase 101: `.planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md`
    - CONTEXT D-13 (Phase 102's close-loop REVIEW.md is a NEW file separate from the input set)
    - The reviewer output from Task 2 (first pass + second pass if applicable)
  </read_first>
  <action>
    Create `.planning/phases/102-code-review-fixes/102-REVIEW.md` following the existing REVIEW.md template structure. Sections:
    1. **Header** — Phase number (102), title, date, scope ("Close-loop review of the Phase-102 cumulative diff on `gsd/v1.13-discord-integration`").
    2. **Diff Scope** — total files changed (from `git diff --name-only origin/master..HEAD`), commits-included count, top-level directories touched.
    3. **First-Pass Findings** — list of findings from Task 2's first reviewer pass (if any). For each: `### {SEVERITY}-{NN}: {one-line title}` + Location + Description + Suggested Fix + Resolution-status.
    4. **Remediation** — for each first-pass finding, the commit SHA that closed it + a 1-line note.
    5. **Second-Pass Result** — if a second pass was needed, the result (must be clean).
    6. **Final Verification Outcome** — confirmation that `./mvnw clean verify -Pe2e` is green + SpotBugs 0 + JaCoCo ≥ 88.88 % + reviewer clean.
    7. **Memory-Promotion Candidates** — per CONTEXT D-14 + the Deferred Ideas section, any new pattern observations recorded for the v1.13 RETROSPECTIVE (not promoted to CLAUDE.md here).
    8. **Closure Note** — `Phase 102 closed; v1.13 milestone unblocks /gsd-complete-milestone v1.13`.

    Per CLAUDE.md "Documentation Maintenance" — this is a planning document, NOT a source file; the "No Comment Pollution" hard-bans (which target src/main, src/test, Flyway migrations) don't apply here. Phase / Plan markers are LEGITIMATE in `.planning/phases/**/*-REVIEW.md` files.

    Per CLAUDE.md "Language" — DOCS in English (this file is documentation/planning record).

    Commit: `docs(102): author close-loop REVIEW.md`.
  </action>
  <verify>
    <automated>test -f .planning/phases/102-code-review-fixes/102-REVIEW.md && grep -E "^# Phase 102|^## " .planning/phases/102-code-review-fixes/102-REVIEW.md | wc -l | grep -E "^[[:space:]]*[5-9]$|^[[:space:]]*[1-9][0-9]+$"</automated>
  </verify>
  <acceptance_criteria>
    - `.planning/phases/102-code-review-fixes/102-REVIEW.md` exists.
    - The file has at least 5 top-level headings (header + 4 of the 8 sections above; not all 8 are mandatory if e.g. there were no first-pass findings).
    - The "Final Verification Outcome" section explicitly states `./mvnw clean verify -Pe2e` result + JaCoCo coverage % + reviewer clean confirmation.
    - One atomic commit.
  </acceptance_criteria>
  <done>102-REVIEW.md authored and committed; Phase 102 historical record complete.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 4: Commit Plan 102-04 SUMMARY.md and update STATE.md</name>
  <files>.planning/phases/102-code-review-fixes/102-04-SUMMARY.md, .planning/STATE.md</files>
  <read_first>
    - The existing SUMMARY.md files from 102-01/02/03 (template reference)
    - .planning/STATE.md (current position; needs update to reflect Phase 102 closed)
    - $HOME/.claude/get-shit-done/templates/summary.md (template)
  </read_first>
  <action>
    Create `.planning/phases/102-code-review-fixes/102-04-SUMMARY.md` per template:
    - Plan goal (close-loop verification)
    - Tasks completed (Task 1 verify result, Task 2 review result + remediation count, Task 3 REVIEW.md commit SHA)
    - End-gate metrics (`mvn clean verify -Pe2e` exit, JaCoCo coverage %, SpotBugs BugInstance count, test count delta, reviewer result)
    - Final commit list for Phase 102 (all 4 plans combined: count + range of SHAs)

    Update `.planning/STATE.md` Phase 102 entry: position → closed, with the SUMMARY date. Update Phase 102 ROADMAP entry checkbox `[ ]` → `[x]` in `.planning/milestones/v1.13-ROADMAP.md`.

    Per CLAUDE.md "Git Workflow": ONE atomic commit containing both file edits. Subject: `docs(102): close Phase 102 — SUMMARY.md + STATE/ROADMAP checkbox flips`.
  </action>
  <verify>
    <automated>test -f .planning/phases/102-code-review-fixes/102-04-SUMMARY.md && grep -q "\\[x\\] \\*\\*Phase 102:" .planning/milestones/v1.13-ROADMAP.md</automated>
  </verify>
  <acceptance_criteria>
    - `.planning/phases/102-code-review-fixes/102-04-SUMMARY.md` exists with the standard SUMMARY structure.
    - `.planning/STATE.md` reflects Phase 102 closed.
    - `.planning/milestones/v1.13-ROADMAP.md` Phase 102 entry checkbox is `[x]`.
    - One atomic commit.
  </acceptance_criteria>
  <done>Phase 102 closed; `/gsd-complete-milestone v1.13` is unblocked per CLAUDE.md "Code-Review Before Phase Close".</done>
</task>

</tasks>

<verification>
After all 4 tasks land:
1. `./mvnw clean verify -Pe2e` exits 0 (the canonical end-gate per CLAUDE.md "Build & Test Discipline").
2. `gsd-code-reviewer` returned `clean` on the most recent Phase-102 cumulative-diff pass.
3. `.planning/phases/102-code-review-fixes/102-REVIEW.md` exists.
4. `.planning/STATE.md` + `.planning/milestones/v1.13-ROADMAP.md` reflect Phase 102 closed.
5. v1.13 milestone is unblocked: the next user action is `/gsd-complete-milestone v1.13`.
</verification>

<success_criteria>
- All 4 ROADMAP success criteria for Phase 102 satisfied:
  1. All 9 critical/blocker findings closed (verified by Plan 102-01 + close-loop).
  2. All 58 warning findings closed (verified by Plan 102-02 + close-loop).
  3. All 52 info findings closed (verified by Plan 102-03 + close-loop).
  4. `gsd-code-reviewer 102` returns clean.
- Baselines preserved: `./mvnw clean verify -Pe2e` green; JaCoCo line coverage ≥ 88.88 %; SpotBugs BugInstance count remains 0; CI E2E median within 17:39 ± 20 %.
- STATE.md Deferred Items section reviewed (Task 4 prompt; any item that turned out to be closable as part of this fix-phase is closed).
- PR description refresh — NOT in this plan per CONTEXT non-goals; happens AFTER milestone close.
</success_criteria>

<output>
Create `.planning/phases/102-code-review-fixes/102-04-SUMMARY.md` capturing the close-loop result + STATE/ROADMAP updates. The NEW Phase-102 historical record lives in `.planning/phases/102-code-review-fixes/102-REVIEW.md` per CONTEXT D-13.
</output>
