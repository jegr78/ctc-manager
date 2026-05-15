---
phase: 79
plan: 09
type: execute
wave: 9
depends_on: [79-08]
files_modified:
  - .planning/STATE.md
  - .planning/ROADMAP.md
  - .planning/PROJECT.md
autonomous: false
requirements: [D-14, D-17]

must_haves:
  truths:
    - "`/gsd-complete-milestone v1.10` has been dispatched ONLY after Plan 08 returned `Wave 9 cleared`"
    - "Milestone archive commits (STATE.md / ROADMAP.md / PROJECT.md updates) land on `gsd/v1.10-platform-and-backup`"
    - "Final Squash-PR `chore(79): v1.10 milestone closer — cleanup, test perf, audit` is created with `--assignee jegr78` per CLAUDE.md §Git Workflow + D-17"
    - "PR body lists every per-package commit + perf reduction % + audit verdict per CONTEXT.md §D-17"
    - "PR merge is performed via `gh pr merge --squash --subject \"chore(79): v1.10 milestone closer — cleanup, test perf, audit\"` per `feedback_squash_merge_message` memory"
    - "Merge happens AFTER CI is green per `feedback_pr_workflow`"
  artifacts:
    - path: ".planning/STATE.md"
      provides: "v1.10 milestone status updated to shipped + Phase 79 marked complete"
      contains: "v1.10.*shipped\\|Phase 79.*complete"
    - path: "Pull Request on GitHub"
      provides: "Single squash-PR for the entire Phase 79 diff"
      pattern: "chore\\(79\\): v1\\.10 milestone closer"
  key_links:
    - from: "Plan 08 Wave-9-cleared signal"
      to: "/gsd-complete-milestone v1.10 dispatch"
      via: "audit-clean gate per D-15"
      pattern: "Wave 9 cleared"
    - from: "PR body"
      to: "Per-package atomic commits + perf + audit"
      via: "narrative aggregation of Phase 79 evidence"
      pattern: "per-package commits|reduction|audit"
---

<objective>
Wave 9 of Phase 79: dispatch `/gsd-complete-milestone v1.10` per D-14 ONLY IF Plan 08 returned `Wave 9 cleared`. Then create the single Squash-PR per D-17 for the entire Phase 79 diff. PR merges (per `feedback_pr_workflow`) only after CI is green.

Purpose: D-14 + D-17 — both milestone-completion commands and the PR creation/merge happen INSIDE Phase 79. Archive commits ride the squash-merge into master.

If Plan 08 returned `Wave 9 BLOCKED`, this plan STOPS at Task 1 and reports `NEEDS_CONTEXT` ("Plan 08 has not cleared Wave 9"). The orchestrator decides next steps (fix-in-79.X / accept-deferred / abandon).

Output: archive commits from `/gsd-complete-milestone`, then PR creation, then post-CI-green merge. End-of-phase.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-MILESTONE-AUDIT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VERIFICATION.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md
@CLAUDE.md

<interfaces>
**Plan-08 clearance gate (D-15 enforcement):**

Read `79-MILESTONE-AUDIT.md` `## Clearance` line. If it contains `Wave 9 cleared` → proceed. If it contains `Wave 9 BLOCKED` → STOP / `NEEDS_CONTEXT`.

**`/gsd-complete-milestone v1.10` expected behavior (Phase 69 v1.9 precedent):**

The slash-command typically:
1. Marks the milestone as `status: shipped` in STATE.md.
2. Updates ROADMAP.md milestone row to `:white_check_mark:` + shipped date.
3. Archives per-phase planning under `.planning/milestones/v1.10-phases/` (moves Phase 71-79 directories there) — though for v1.10 the directories may stay in-place per local convention; confirm against Phase 69 precedent.
4. Updates PROJECT.md "Carried over from v1.10 deferred" section with the explicitly deferred-to-v1.11 items (per CONTEXT.md §"Out of scope": Quality-Gate-Lock, per-group matchday UI, StandingsController:139 lazy-collection, UAT-02 — these are still v1.11 candidates).
5. Creates 1-N commits with `chore: archive v1.10 milestone` (or similar) on the feature branch.

These archive commits land on the feature branch `gsd/v1.10-platform-and-backup` and ride the squash-merge into master.

**PR creation per CLAUDE.md §"Git Workflow" + D-17:**

```bash
gh pr create --assignee jegr78 \
  --title "chore(79): v1.10 milestone closer — cleanup, test perf, audit" \
  --body "$(cat <<'EOF'
## Summary

Phase 79 v1.10 milestone-closer. Three streams: full-codebase code cleanup (Java + tests + config), test-performance optimization via process-level parallelism + flaky-tag quarantine, and milestone audit + completion.

## Cleanup (Wave 2)

Per-package atomic commits across `src/main/java` + `src/test/java` + `pom.xml` + `.github/workflows/ci.yml`. Per-package atomic commits enable `git bisect` (squash-merge collapses them at merge-time; the PR's commit list remains the bisect surface per D-17).

Cleanup classes applied (D-02): comment-thinning (D-09..D-13), dead-code removal (D-04 safe-indicator rule), extract-method (>30/50 LOC per CD-02), logic-simplification (CD-03).

Schutzwortliste (D-13) invariant: ZERO matches across the diff. Flyway invariant: ZERO migration edits. mariadb-migration-smoke.yml invariant: ZERO edits.

## Test Performance (Wave 3)

- Surefire: forkCount=2C, reuseForks=true (process-level parallel for ~1200 unit tests)
- Failsafe default-it: forkCount=1C, reuseForks=true (Testcontainers-safe IT parallel)
- Failsafe e2e-it: UNCHANGED (Playwright single-port constraint)
- @Tag("flaky") quarantine mechanism: excludedGroups=flaky on both plugins, max-5 cap per CD-05

`@{argLine}` late-property evaluation preserved on all 3 argLine entries (JEP 498 + Lombok #3959 + Mockito-agent invariants).

## Wallclock Reduction (D-06)

Baseline (before Wave 3): <Xm Ys>
Final (after Wave 4): <Ym Zs>
Reduction: <NN.N%> — <MEETS | DOES NOT MEET> ≥30% D-06 threshold

## CI Hygiene (Wave 4)

- Workflow-level concurrency: group=$\{\{ github.workflow }}-$\{\{ github.ref }}, cancel-in-progress: true (CD-06)
- --no-transfer-progress on both ./mvnw verify invocations in ci.yml

## Coverage (D-18)

JaCoCo line coverage: <X.XXXX> ≥ 0.82 PASS at HEAD.

## Documentation (Wave 5/6)

- `.planning/codebase/TESTING.md` § Test Invocation Discipline (D-08)
- 17 SUMMARY.md frontmatter normalized for phases 56/57/62/64 (D-16)

## Audit (Wave 8)

`/gsd-audit-milestone v1.10` verdict: <CLEAN / FINDINGS — see 79-MILESTONE-AUDIT.md>

## Completion (Wave 9)

`/gsd-complete-milestone v1.10` archive commits included.

## Test plan
- [ ] CI build-and-test job GREEN
- [ ] CI dockerfile-noble-pin-guard job GREEN
- [ ] CI docker-build job GREEN
- [ ] CI Coverage report PR comment ≥ 0.82

EOF
)"
```

**Squash-merge command per `feedback_squash_merge_message` memory + D-17:**

```bash
# Only after CI is GREEN
gh run list --branch gsd/v1.10-platform-and-backup --limit 5
# Verify all status checks are SUCCESS

gh pr merge --squash --subject "chore(79): v1.10 milestone closer — cleanup, test perf, audit"
```

`feedback_squash_merge_message` rule: `gh pr merge --squash` defaults to using the PR's commit-list as the squash-commit message body — pass `--subject` explicitly so the squash-commit gets a clean Conventional-Commits-prefixed subject line.

**Post-merge cleanup per CLAUDE.md §Git Workflow:**

```bash
git switch master
git pull
git branch -d gsd/v1.10-platform-and-backup
```

These post-merge steps are LOGGED in the SUMMARY but NOT executed by this plan's executor — they are user-action because they involve branch switching (forbidden by `critical_constraints`).
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches. Post-merge `git switch master` is user-action AFTER Phase 79 closes — NOT part of this plan's executor scope.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- D-15 hard-stop: Wave 9 (this plan) runs ONLY IF Plan 08 returned `Wave 9 cleared`. Verify in Task 1.
- `/gsd-complete-milestone v1.10` is a GSD-orchestrator slash-command — same as Plan 08, the user dispatches it. This plan has a `checkpoint:human-action` task.
- `gh pr merge --squash` runs ONLY AFTER CI is GREEN per `feedback_pr_workflow`.
- Schutzwortliste (D-13): N/A — no source-comment edits.
</critical_constraints>

<test_impact>
N/A — milestone archive + PR creation. No Java/XML/YAML source touched. No test added/removed/renamed. The CI gate (PR-build) verifies the Phase 79 diff as a whole.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Verify Plan 08 Wave-9-cleared gate</name>
  <files>(verification-only — no file edits)</files>
  <read_first>
    - `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-MILESTONE-AUDIT.md`
  </read_first>
  <action>
1. Read `79-MILESTONE-AUDIT.md` `## Clearance` line.

2. If the line contains "Wave 9 cleared" → proceed to Task 2.

3. If the line contains "Wave 9 BLOCKED" → STOP, report `NEEDS_CONTEXT`:
```
NEEDS_CONTEXT: Plan 08 did not clear Wave 9.

Audit verdict: <FINDINGS | AUDIT-TOOLING-FAILURE>
Blocked dispositions: <copy/paste the disposition list from 79-MILESTONE-AUDIT.md>

Plan 09 cannot proceed until findings are addressed per D-15. Orchestrator decision required:
- fix-in-79.X: add a follow-up plan (e.g., 79-08a-...)
- accept-as-deferred-to-v1.11: requires explicit user approval
- escalate-as-Hotfix-Sub-Phase-79.X: out-of-Phase-79 scope
```

Do NOT proceed to Task 2 in this case.
  </action>
  <verify>
    <automated>grep -q "Wave 9 cleared" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-MILESTONE-AUDIT.md</automated>
  </verify>
  <acceptance_criteria>
    - Clearance line read and verified
    - If cleared → proceed to Task 2
    - If blocked → `NEEDS_CONTEXT` with explicit blocked dispositions
  </acceptance_criteria>
  <done>Gate decision made; either Task 2 proceeds or escalation to orchestrator.</done>
</task>

<task type="checkpoint:human-action" gate="blocking">
  <name>Task 2: Dispatch /gsd-complete-milestone v1.10 (human-invoked)</name>
  <files>(none — human-action checkpoint awaiting orchestrator slash-command invocation)</files>
  <what-built>
Plan 08 cleared Wave 9. Phase 79 has completed Waves 1-8 (baseline / cleanup / perf / config-cleanup / doc / frontmatter / final-verify / audit) with all decisions PASSED.

The next step per D-14 is the milestone-completion command. This is an orchestrator slash-command and must be invoked by you.
  </what-built>
  <how-to-verify>
1. From the GSD-CC interface, run: `/gsd-complete-milestone v1.10`
2. Wait for the orchestrator to:
   - Mark v1.10 as `status: shipped` in STATE.md
   - Update ROADMAP.md milestone row to `:white_check_mark:` + shipped date (today)
   - Update PROJECT.md "Carried over from v1.10 deferred" section with explicit v1.11+ deferrals per CONTEXT.md §"Out of scope"
   - (Optionally) Archive `.planning/phases/71-79*` directories under `.planning/milestones/v1.10-phases/`
   - Create 1-N archive commits with `chore: ...` prefix on `gsd/v1.10-platform-and-backup`
3. Confirm the orchestrator's archive commits via `git log --oneline -10` (should show 1-3 new commits since Plan 08).
4. Signal back to the executor when done.
  </how-to-verify>
  <action>
Pause execution. Display the `<what-built>` and `<how-to-verify>` content to the user. Wait for the user's resume-signal payload (one of: "milestone archived" / "milestone-complete failed: ..."). After resume, capture the new archive-commit SHAs from `git log --oneline -10` for use in Task 3 (PR body) and Task 4 (post-merge SUMMARY). Do NOT modify any file in this task. Do NOT switch branches. The executor is purely awaiting the human-supplied milestone-archive confirmation.
  </action>
  <verify>
    <automated>true</automated>
  </verify>
  <done>User has signaled `milestone archived` and archive commits are visible in `git log`.</done>
  <resume-signal>
Type "milestone archived" once `/gsd-complete-milestone v1.10` has completed and the archive commits are visible in `git log`.
OR
Type "milestone-complete failed: <reason>" if the slash-command failed.
  </resume-signal>
</task>

<task type="checkpoint:human-action" gate="blocking">
  <name>Task 3: Create Squash-PR via gh pr create</name>
  <files>(none — human-action checkpoint awaiting gh CLI invocation by user)</files>
  <what-built>
`/gsd-complete-milestone v1.10` has archived the milestone. All Phase 79 commits are on `gsd/v1.10-platform-and-backup`.

The next step per D-17 is the single Squash-PR. Per CLAUDE.md §"Git Workflow", this MUST be invoked with `--assignee jegr78`.
  </what-built>
  <how-to-verify>
1. From the project root, execute (PASTE the body from `<interfaces>` `gh pr create` block, populating placeholder values from 79-AUTO-UAT.md + 79-VERIFICATION.md + 79-MILESTONE-AUDIT.md):

```bash
gh pr create --assignee jegr78 \
  --title "chore(79): v1.10 milestone closer — cleanup, test perf, audit" \
  --body "$(cat <<'EOF'
[paste the full body from the plan's <interfaces> section here, with <X.XXXX> / <Xm Ys> / <Ym Zs> / <NN.N%> / <CLEAN | FINDINGS> filled in from the evidence files]
EOF
)"
```

2. Confirm the PR is created — the URL is printed by `gh pr create`. Open it to verify:
   - Title is `chore(79): v1.10 milestone closer — cleanup, test perf, audit`
   - Assignee is `jegr78`
   - Body lists per-package commits + perf reduction % + audit verdict + test-plan checklist

3. Signal back to the executor with the PR URL.
  </how-to-verify>
  <action>
Pause execution. Display the `<what-built>` and `<how-to-verify>` content to the user. Hand the user a ready-to-paste `gh pr create` command (filling all placeholders from `79-AUTO-UAT.md`, `79-VERIFICATION.md`, and `79-MILESTONE-AUDIT.md` so the user only has to copy/paste). Wait for the user's resume-signal ("PR created: <url>" or "PR creation failed: <reason>"). After resume, capture the PR URL for use in Task 4. Do NOT modify any file in this task. Do NOT switch branches. The executor is purely awaiting the human-supplied PR-creation confirmation.
  </action>
  <verify>
    <automated>true</automated>
  </verify>
  <done>User has signaled `PR created: <url>` and the URL has been captured for Task 4.</done>
  <resume-signal>
Type "PR created: &lt;url&gt;" once `gh pr create` has returned the PR URL.
OR
Type "PR creation failed: &lt;reason&gt;" if `gh pr create` failed (e.g., branch not pushed, auth missing).
  </resume-signal>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 4: Wait for CI green + Squash-merge</name>
  <files>(none — human-verify checkpoint awaiting CI completion + gh pr merge invocation by user)</files>
  <what-built>
PR is created. Per `feedback_pr_workflow` memory + CLAUDE.md §"Git Workflow", merge MUST wait for CI green.
  </what-built>
  <how-to-verify>
1. Wait for the CI run on the PR to complete. Monitor with:
```bash
gh run list --branch gsd/v1.10-platform-and-backup --limit 5
```

2. Confirm ALL status checks are SUCCESS:
   - `build-and-test`
   - `dockerfile-noble-pin-guard`
   - `docker-build` (from Phase 78)
   - JaCoCo Coverage Report PR comment shows ≥ 0.82

3. If any check FAILS:
   - Analyze logs: `gh run view --log-failed`
   - Decide whether to fix on the branch (push a follow-up commit) OR escalate to orchestrator
   - Do NOT merge until all checks are GREEN

4. Once GREEN, perform the squash-merge per `feedback_squash_merge_message`:
```bash
gh pr merge --squash --subject "chore(79): v1.10 milestone closer — cleanup, test perf, audit"
```

5. After merge:
   - The feature branch is auto-deleted by GitHub (or `gh pr merge --delete-branch`).
   - User performs local cleanup (NOT this plan's executor scope):
     ```
     git switch master
     git pull
     git branch -d gsd/v1.10-platform-and-backup
     ```
  </how-to-verify>
  <action>
Pause execution. Display the `<what-built>` and `<how-to-verify>` content to the user. Hand the user the ready-to-paste `gh run list` polling command and the `gh pr merge --squash --subject "chore(79): v1.10 milestone closer — cleanup, test perf, audit"` command. Wait for the user's resume-signal ("merged" / "merge blocked: ..." / "merged with caveats: ..."). After resume, capture the squash-merge commit SHA on master for the final SUMMARY. Do NOT modify any file in this task. Do NOT switch branches. The post-merge `git switch master` cleanup is user-action AFTER Phase 79 closes.
  </action>
  <verify>
    <automated>true</automated>
  </verify>
  <done>User has signaled `merged` and the squash-merge commit SHA on master has been captured for the SUMMARY.</done>
  <resume-signal>
Type "merged" once `gh pr merge --squash` has completed successfully on a GREEN CI run.
OR
Type "merge blocked: &lt;reason&gt;" if any check failed and requires escalation.
OR
Type "merged with caveats: &lt;notes&gt;" if minor follow-up commits were pushed before merge.
  </resume-signal>
</task>

</tasks>

<verification>
- Plan 08 Wave-9-cleared gate verified before any Wave 9 action
- `/gsd-complete-milestone v1.10` has been dispatched (human-invoked, signaled "milestone archived")
- Squash-PR created via `gh pr create --assignee jegr78` per CLAUDE.md
- CI on the PR is GREEN (build-and-test + dockerfile-noble-pin-guard + docker-build)
- Squash-merge executed via `gh pr merge --squash --subject "chore(79): v1.10 milestone closer — cleanup, test perf, audit"`
- v1.10 milestone is SHIPPED
</verification>

<success_criteria>
- Wave 9 clearance gate honored (D-15)
- Milestone archive commits land on `gsd/v1.10-platform-and-backup`
- Single Squash-PR created with correct title + assignee + body (D-17 + CLAUDE.md)
- PR merged ONLY AFTER CI green (`feedback_pr_workflow`)
- Phase 79 complete; v1.10 shipped
</success_criteria>

<output>
After completion (after the merge), create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-09-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: PR URL, squash-merge commit SHA on master, CI status summary, list of archive commits from `/gsd-complete-milestone`, post-merge cleanup steps (for user reference).
</output>
