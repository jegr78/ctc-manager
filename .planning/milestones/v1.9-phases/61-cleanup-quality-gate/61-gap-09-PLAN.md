---
plan_id: 61-gap-09
phase: 61-cleanup-quality-gate
title: Final gate — full verify -Pe2e + JaCoCo 82% confirmation + grep-gate audit
wave: 4
gap_closure: true
autonomous: true
depends_on: [61-gap-01, 61-gap-02, 61-gap-03, 61-gap-04, 61-gap-05, 61-gap-06, 61-gap-07, 61-gap-08]
files_modified:
  - .planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
  - .planning/phases/61-cleanup-quality-gate/deferred-items.md
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "./mvnw verify -Pe2e BUILD SUCCESS (the FINAL gate per CLAUDE.md + project memory feedback_e2e_verification)"
  - "JaCoCo line coverage >= 82% (D-21 mandate; pom.xml threshold unchanged at 0.82)"
  - "All four gap-classes (G2 stale comments, G3 dead code, G4 over-validation, G5 javadoc) have grep-verifiable proof of closure"
  - "61-VERIFICATION.md frontmatter updated: gaps[] entries marked 'resolved' with closure narrative"
  - "deferred-items.md updated with any NEEDS_CONTEXT items surfaced during 61-gap-06/07/08"
---

<objective>
Final consolidation gate for the entire Phase 61 gap closure. Run the full `./mvnw verify -Pe2e` (Surefire +
Failsafe + Playwright E2E + JaCoCo) — the single mandatory gate before declaring the phase ready for re-verification.
Confirm all grep gates from 61-gap-01..08 hold. Update 61-VERIFICATION.md to reflect closure.

Purpose: ensure the gap closure does not regress the existing test/coverage gate. Provide the orchestrator
the explicit signal that re-verification (`/gsd-verify-work 61`) is the next step.

Output: full -Pe2e BUILD SUCCESS, JaCoCo >= 82%, all grep gates return 0, 61-VERIFICATION.md updated with
gap-closure narrative.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
@.planning/phases/61-cleanup-quality-gate/61-gap-01-SUMMARY.md
@.planning/phases/61-cleanup-quality-gate/61-gap-02-SUMMARY.md
@.planning/phases/61-cleanup-quality-gate/61-gap-03-SUMMARY.md
@.planning/phases/61-cleanup-quality-gate/61-gap-04-SUMMARY.md
@.planning/phases/61-cleanup-quality-gate/61-gap-05-SUMMARY.md
@.planning/phases/61-cleanup-quality-gate/61-gap-06-SUMMARY.md
@.planning/phases/61-cleanup-quality-gate/61-gap-07-SUMMARY.md
@.planning/phases/61-cleanup-quality-gate/61-gap-08-SUMMARY.md
@CLAUDE.md

**CLAUDE.md mandate:**
- "82% line coverage minimum" — threshold (`pom.xml` `<minimum>0.82</minimum>`) MUST stay unchanged
- "Run tests including Playwright E2E: `./mvnw verify -Pe2e`" — final gate before PR

**Project memory:**
- `feedback_e2e_verification`: "Endverifikation immer mit ./mvnw verify -Pe2e inkl. Playwright"
- `feedback_test_call_optimization`: only ONE final verify-Pe2e — don't repeat

**No code changes in this plan.** Only:
1. Run the final gate
2. Aggregate grep gates
3. Update 61-VERIFICATION.md
4. Update deferred-items.md if needed

**If the gate FAILS:** STOP, do NOT update VERIFICATION.md. Document failure mode in the SUMMARY and report
NEEDS_CONTEXT to the orchestrator. The orchestrator decides whether to revert specific gap-NN commits or open
a follow-up plan.
</context>

<tasks>

<task id="1" name="Run the full ./mvnw verify -Pe2e gate">
  <action>
    This is the SINGLE authoritative gate for the gap closure.

    ```bash
    ./mvnw verify -Pe2e 2>&1 | tee /tmp/61-gap-09-verify.log
    ```

    Expected output tail:
    ```
    [INFO] BUILD SUCCESS
    [INFO] Total time:  XX:XX min
    ```

    Plus, in the surefire-reports / failsafe-reports section: at least 1172 Surefire tests run (post-CR-01
    baseline) plus 31+ Failsafe IT/E2E tests, with 0 failures and 0 errors.

    If BUILD FAILURE:
    1. Capture the failing test in /tmp/61-gap-09-verify.log
    2. STOP — do NOT proceed to other tasks
    3. Document failure in SUMMARY: which test, which assertion, which gap-NN commit likely caused it (`git log --oneline HEAD~30..HEAD` to identify candidates)
    4. Report NEEDS_CONTEXT to orchestrator with exact failure details

    If BUILD SUCCESS, proceed.
  </action>
  <read_first>
    - .planning/phases/61-cleanup-quality-gate/61-gap-01-SUMMARY.md ... 61-gap-08-SUMMARY.md (full chain)
    - CLAUDE.md "Run tests including Playwright E2E"
  </read_first>
  <acceptance_criteria>
    - `./mvnw verify -Pe2e` returns BUILD SUCCESS
    - Surefire test count >= 1172 (baseline preserved)
    - Failsafe test count >= 31 (E2E baseline preserved)
    - 0 failures, 0 errors in both suites
    - Log saved to /tmp/61-gap-09-verify.log for SUMMARY reference
  </acceptance_criteria>
</task>

<task id="2" name="Confirm JaCoCo coverage >= 82%">
  <action>
    Extract line coverage from the JaCoCo CSV produced by Task 1's verify run:

    ```bash
    awk -F, 'NR>1 { line+=$8+$9; missed+=$8 } END { printf "Line coverage: %.2f%%\n", (1 - missed/line) * 100 }' target/site/jacoco/jacoco.csv
    ```

    Expected: >= 82.00% (baseline pre-gap-closure was 87.05%; expect minor drift but should remain comfortably above threshold).

    Verify pom.xml threshold is unchanged:
    ```bash
    grep -c '<minimum>0.82</minimum>' pom.xml
    ```
    Expected: 1.

    If coverage < 82%:
    - Extract per-package coverage from CSV to identify the regression
    - The regression points to a code path that was removed but was the ONLY thing exercising a code branch
    - STOP, do not proceed; report NEEDS_CONTEXT with the specific package + delta

    If coverage >= 82%, proceed.

    No commit needed in this task. Coverage info is recorded in SUMMARY.
  </action>
  <read_first>
    - target/site/jacoco/jacoco.csv (output of Task 1)
    - pom.xml (threshold)
  </read_first>
  <acceptance_criteria>
    - Line coverage >= 82.00%
    - `grep -c '<minimum>0.82</minimum>' pom.xml` returns 1
    - Per-package coverage delta versus baseline noted in SUMMARY (especially for any package below 82% even if global remains above)
  </acceptance_criteria>
</task>

<task id="3" name="Run all consolidated grep gates">
  <action>
    Re-run every grep gate established by 61-gap-01..08 in one batch:

    ```bash
    echo "=== G2: stale phase narrative across src/main + src/test ===" 
    grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-0?[0-9]+|CR-0?[0-9]+|IN-0?[0-9]+)" src/main/java src/test/java | grep -v '^#' | wc -l
    echo "Expected: 0 (or all hits documented in SUMMARYs as legitimate WHY)"
    echo

    echo "=== G2: cascade migration narrative ===" 
    grep -rn -E "Wave [0-9]+ cascade|cascade migration|transitional bridge|bridge field" src/main/java src/test/java | grep -v '^#' | wc -l
    echo "Expected: 0"
    echo

    echo "=== G2: caller-ref comments ===" 
    grep -rn -E "used by [A-Z][a-zA-Z]+|added for [a-z]+ flow" src/main/java src/test/java | grep -v '^#' | wc -l
    echo "Expected: 0"
    echo

    echo "=== G3: legacy fallback / dead suppress-warnings ===" 
    grep -rn -E "@SuppressWarnings\\(\\s*\"deprecation\"" src/main/java | wc -l
    echo "Expected: 0 (WR-05 + 61-gap-07 should have removed all)"
    echo

    echo "=== G4: defensive validation imports ===" 
    grep -rn "Objects\.requireNonNull\|Validate\.notNull\|Assert\.notNull\|Preconditions\.checkNotNull" src/main/java | wc -l
    echo "Expected: 0 (or all hits are KEEP-boundary in 61-gap-08-SUMMARY.md)"
    echo

    echo "=== G4: deferred-items.md unused field reference ===" 
    grep -rn "playoffSeedRepository" src/main src/test | wc -l
    echo "Expected: 0 (61-gap-06 removed)"
    echo

    echo "=== G5: domain.repository finder Javadoc ===" 
    grep -B1 "List<Matchday> findByPhaseIdOrderBySortIndexAsc" src/main/java/org/ctc/domain/repository/MatchdayRepository.java | grep -c "*\|/"
    echo "Expected: >= 1 (Javadoc block above the method)"
    ```

    Each line MUST match its expected outcome. If any does not:
    - The relevant gap is not fully closed
    - Document in SUMMARY which gap is open and the residue
    - Report NEEDS_CONTEXT to orchestrator (verifier will catch this anyway during /gsd-verify-work; better to surface here)

    Save results to /tmp/61-gap-09-grep-gates.txt for SUMMARY reference.
  </action>
  <read_first>
    - All 8 SUMMARYs from 61-gap-01..08
    - 61-VERIFICATION.md frontmatter `gaps:` array
  </read_first>
  <acceptance_criteria>
    - Every grep gate returns its expected count (0 or >= 1 for the Javadoc gate)
    - Any residue is explicitly documented in 61-gap-09-SUMMARY.md as either justified-KEEP or NEEDS_CONTEXT
    - /tmp/61-gap-09-grep-gates.txt saved for traceability
  </acceptance_criteria>
</task>

<task id="4" name="Update 61-VERIFICATION.md frontmatter to reflect gap closure">
  <action>
    Edit `.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` frontmatter:

    1. Set `status: gaps_closed_pending_reverification` (the new value reflecting that the gap closure is
       complete but a fresh /gsd-verify-work pass is owed before declaring `passed`).
    2. Update the `re_verification:` block: append a new entry with `previous_status: gaps_found` →
       `gaps_remaining: []` after this gap closure. List the 4 closed gaps (G2/G3/G4/G5) with their closure narrative
       referencing the relevant 61-gap-NN-SUMMARY.md files.
    3. In the `gaps:` array (which currently has 4 `failed` entries G2/G3/G4/G5 plus 1 already-resolved CR-01):
       set `status: resolved` on each of the 4 currently-failed gaps. Add a `resolution:` block to each:
       ```yaml
       resolution: |
         Closed via Phase 61 gap-closure plans 61-gap-01..08 (executed 2026-05-01..02).
         See 61-gap-NN-SUMMARY.md for per-plan details.
         Final gate: ./mvnw verify -Pe2e BUILD SUCCESS, JaCoCo line coverage XX.XX%.
       ```
       (Replace XX.XX% with the actual measured coverage from Task 2.)
    4. Add a new dated section at the bottom: `## Re-Verification Update — 2026-05-XX (Post-Gap-Closure)`
       summarizing the gap closure (4 issue classes addressed, X plans executed, Y files touched, Z commits).

    Do NOT modify the original `## Goal Achievement` snapshot — it's preserved as the historical record.

    Commit message: `docs(61): update VERIFICATION.md after gap closure (gaps G2-G5 resolved)`
  </action>
  <read_first>
    - .planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md (full file — frontmatter + body)
    - All 8 61-gap-NN-SUMMARY.md files (referenced in the closure narrative)
    - Outputs of Tasks 1-3 (BUILD SUCCESS confirmation, coverage %, grep counts)
  </read_first>
  <acceptance_criteria>
    - `grep -c "status: resolved" .planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` >= 5 (CR-01 already resolved + 4 newly resolved)
    - `grep -c "status: failed" .planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` == 0
    - `grep "status: gaps_closed_pending_reverification" .planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` returns 1 line
    - The new `## Re-Verification Update — 2026-05-XX (Post-Gap-Closure)` section is present
  </acceptance_criteria>
</task>

<task id="5" name="Update deferred-items.md (if needed)">
  <action>
    Read the deferred-items registered during 61-gap-06/07/08 (each SUMMARY.md should list any NEEDS_CONTEXT
    items). Aggregate them into deferred-items.md.

    For each NEEDS_CONTEXT item:
    - File:line
    - Why deferred (e.g., "removal would drop JaCoCo coverage below 82% — code IS reached by tests but no production caller; investigate test obsolescence")
    - Suggested follow-up (e.g., "evaluate in Phase 62; either remove the test if path is truly unreachable, or restore code if test catches a regression")

    Format as Markdown bullet list under existing `## Pre-existing Unused Fields` and `## Disabled Tests (Plan 61-04 follow-up)` sections, OR add a new section `## Phase 61 Gap-Closure Deferrals` if the items don't fit existing categories.

    The PlayoffService.playoffSeedRepository entry (if 61-gap-06 successfully removed it) MUST be removed from deferred-items.md (it's no longer deferred).

    Commit message: `docs(61): update deferred-items.md after gap closure`
  </action>
  <read_first>
    - .planning/phases/61-cleanup-quality-gate/deferred-items.md (current state)
    - All 8 61-gap-NN-SUMMARY.md (NEEDS_CONTEXT items)
  </read_first>
  <acceptance_criteria>
    - If `playoffSeedRepository` was removed in 61-gap-06: it's no longer in deferred-items.md
    - Every NEEDS_CONTEXT item from gap-NN SUMMARYs has a row in deferred-items.md
    - File still parses as Markdown (`head -1` shows `# Phase 61 Deferred Items`)
  </acceptance_criteria>
</task>

</tasks>

<verification>
- ./mvnw verify -Pe2e BUILD SUCCESS (the single mandatory gate)
- JaCoCo line coverage >= 82.00%
- pom.xml `<minimum>0.82</minimum>` unchanged
- All consolidated grep gates pass
- 61-VERIFICATION.md gaps[] all marked resolved
- deferred-items.md updated
</verification>

<success_criteria>
1. Full `./mvnw verify -Pe2e` BUILD SUCCESS
2. Coverage >= 82%
3. All 4 gap classes (G2/G3/G4/G5) have grep-verifiable closure
4. 61-VERIFICATION.md reflects closure, ready for /gsd-verify-work re-pass
5. deferred-items.md current
</success_criteria>

<output>
Create `.planning/phases/61-cleanup-quality-gate/61-gap-09-SUMMARY.md` with:
- Final test gate output (Surefire + Failsafe counts, BUILD SUCCESS confirmation)
- Final JaCoCo line coverage percentage (with delta vs. pre-gap-closure baseline 87.05%)
- All grep-gate results
- Total commits across all 9 gap plans (count from `git log --oneline | grep -c "61-gap"`)
- Any deferred items handed off to deferred-items.md

Then announce to the orchestrator: gap closure complete; recommend `/gsd-verify-work 61` for fresh
re-verification, after which `/gsd-audit-milestone v1.9` and `/gsd-complete-milestone v1.9` can proceed.
</output>
