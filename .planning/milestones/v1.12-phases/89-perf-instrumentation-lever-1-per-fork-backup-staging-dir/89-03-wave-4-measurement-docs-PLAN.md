---
phase: 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
plan: 03
type: execute
wave: 3
depends_on:
  - 89-01-perf-01-per-fork-staging-refactor
  - 89-02-perf-02-fingerprint-listener-aggregator
files_modified:
  - docs/test-performance.md
  - README.md
autonomous: false
requirements: []
must_haves:
  truths:
    - "Three idle-protocol local measurement runs of `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` execute successfully (BUILD SUCCESS exit 0, no flakes) per Phase 86 D-09 protocol."
    - "After each of the 3 measurement runs, `head -1 target/test-perf/context-loads-*.txt` confirms Line 1 matches the new `total <N>` format (proves Plan 89-02 Task 1 listener-fix is in effect)."
    - "After each of the 3 measurement runs, `grep -E '^[0-9a-f]{1,8}\\t' target/test-perf/context-loads-*.txt | head -1` exits 0 (proves Plan 89-02 PERF-02 fingerprint data is actively being recorded)."
    - "`docs/test-performance.md` contains a new H2 section `## Post-Optimization Wallclock (Wave 4)` inserted between `## Post-Optimization Wallclock (Wave 3)` (current lines 115-140) and `## CI Results (PERF-05)` (current line 143)."
    - "The new § Wave 4 section uses the same five-column GFM table format as § Wave 3 (`| Run | Maven Total time | bash real | Context loads | Notes |`) with three populated rows + bold Median line + verdict line."
    - "The median wallclock delta is reported against the 10:24 Phase-86-post-audit baseline (NOT 09:45 pre-audit; D-02 honest-reporting rule); positive or negative delta is recorded as observed — no hard local reduction gate."
    - "`docs/test-performance.md § v1.12 Forward Path` Lever-1 row is updated to mark Lever 1 = DONE with a cross-reference to `## Post-Optimization Wallclock (Wave 4)` and `## PERF-02 Forensics`."
    - "`README.md` gains a `## Test Performance` section (or pointer) referencing `docs/test-performance.md` and citing the new Wave-4 median figure."
    - "Final phase gate `./mvnw clean verify -Pe2e --no-transfer-progress` exits 0 with JaCoCo LINE coverage ratio ≥ 0.8888 in `target/site/jacoco/jacoco.csv` (D-15) AND SpotBugs Medium+HIGH findings = 0."
  artifacts:
    - path: "docs/test-performance.md"
      provides: "Wave-4 measurement table + v1.12 Forward Path Lever-1 DONE update"
      contains: "Post-Optimization Wallclock (Wave 4)"
    - path: "README.md"
      provides: "Test Performance section pointing at docs/test-performance.md Wave-4 figure"
      contains: "docs/test-performance.md"
  key_links:
    - from: "docs/test-performance.md § Post-Optimization Wallclock (Wave 4)"
      to: "docs/test-performance.md § Post-Optimization Wallclock (Wave 3) baseline (10:24)"
      via: "explicit \"Median delta vs 10:24 Phase-86-post-audit baseline\" prose"
      pattern: "10:24"
    - from: "docs/test-performance.md § v1.12 Forward Path"
      to: "§ Post-Optimization Wallclock (Wave 4) + § PERF-02 Forensics"
      via: "in-document anchor references and DONE marker on Lever-1 row"
      pattern: "DONE.*Phase 89"
    - from: "README.md § Test Performance"
      to: "docs/test-performance.md"
      via: "Markdown link"
      pattern: "docs/test-performance\\.md"
---

<objective>
Wave-4 measurement closer for Phase 89: run the Phase-86 D-09 idle-protocol locally three times against the post-89-02 state of the codebase, populate `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)`, update the `## v1.12 Forward Path` table to mark Lever 1 = DONE with cross-references to the new Wave-4 figure + PERF-02 forensics, and add a `README.md § Test Performance` pointer.

Purpose: Honest local reporting of the per-fork backup-staging-dir + PERF-02 instrumentation delta (D-02). NO hard local reduction gate — local 3-run variance is ~66s on this machine (per Phase 86 SUMMARY), so the local median is treated as a directional indicator; the authoritative CI 5-run re-harvest is deferred to Phase 91 PERF-06 (D-11 from Phase 86, also surfaced in CONTEXT D-02).

Output:
- 3 measurement runs (one task — `autonomous: false`, manual idle protocol).
- `§ Post-Optimization Wallclock (Wave 4)` populated with 3 rows + median + delta vs 10:24 baseline + JaCoCo ratio.
- `§ v1.12 Forward Path` Lever-1 row updated to DONE state.
- `README.md § Test Performance` pointer.
- Final phase gate: `./mvnw clean verify -Pe2e` green; JaCoCo ≥ 88.88 %; SpotBugs 0.

Depends on Plan 89-01 (PERF-01 must be merged — needs elevated Failsafe `forkCount=2` so the measurement reflects the actual Lever-1 delta) AND Plan 89-02 (PERF-02 must be merged — context-load counts column populated from the new `total <count>` Line 1 of the marker files, aggregated via the migrated `head -1 | awk '{print $2}'` loop).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-perf-01-per-fork-staging-refactor-PLAN.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-02-perf-02-fingerprint-listener-aggregator-PLAN.md
@CLAUDE.md
@docs/test-performance.md
@README.md

<interfaces>
<!-- Anchors and templates for the doc-surface updates. -->

`docs/test-performance.md § Post-Optimization Wallclock (Wave 3)` template (lines 115-140, this is the structural model for the new § Wave 4 section):

```markdown
## Post-Optimization Wallclock (Wave 3)

Local 3-run post-audit measured on the same hardware as the Plan-01 baseline,
identical command and same idle-system protocol. Branch
`gsd/v1.11-tooling-and-cleanup` after Wave-2 commits ...

| Run | Maven Total time | bash `real`     | Context loads | Notes                    |
| --- | ---------------- | --------------- | ------------- | ------------------------ |
| 1   | 09:24            | 565s (9m 26s)   | 79            | BUILD SUCCESS, no flakes |
| 2   | 10:24            | 625s (10m 25s)  | 79            | BUILD SUCCESS, no flakes |
| 3   | 10:30            | 631s (10m 31s)  | 78            | BUILD SUCCESS, no flakes |

**Median (Maven): 10:24 (run 2).** **Median (bash real): 625s = 10m 25s.**
**Post-audit context-load count:** 79 (run 1 + run 2), 78 (run 3) — median 79.

**Verdict:** Achieved **-X% reduction** ... See **v1.12 Forward Path** below ...
```

`docs/test-performance.md § v1.12 Forward Path` Lever-1 row to update (currently at line 340):

```markdown
| **1. Per-fork `data/dev/backup-staging/` refactor** (Top-1 per D-14) — replace the global singleton staging-dir path with a per-fork variant ... | ~60-90s | M | (a) Surefire/Failsafe fork-numbering API ... | `src/main/java/org/ctc/backup/service/BackupImportService.java`, ... |
```

`README.md` current `## Documentation` section (lines 148-150):
```
## Documentation

See the [Wiki](../../wiki) for detailed documentation on architecture, features, setup, and configuration.
```

Idle-protocol command (Phase 86 D-09):
```
time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev
```
Run 3 times in idle conditions (close heavy apps, no concurrent IDE/browser builds, no spotlight indexing storm). Each run cleans `target/` first via `mvn clean`, so `target/test-perf/context-loads-*.txt` is regenerated per run.

Total context loads per run = sum of Line 1 (`total <count>`) across all marker files. Use the migrated aggregator from Plan 89-02 (docs/test-performance.md updated lines 233-239):
```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
done
echo "Total context loads: $TOTAL"
```

10:24 baseline reference (D-02 honest-reporting anchor): Phase-86 post-audit median = 10:24 (Maven), 625s (bash real), 79 context loads. NOT the 09:45 pre-audit baseline (would inflate apparent improvement).
</interfaces>
</context>

<tasks>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 1: Wave-4 measurement — 3 idle-protocol local runs (manual)</name>
  <read_first>
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-02 (locks: honest reporting, no hard local gate; CI re-harvest deferred to Phase 91 PERF-06).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § Manual-Only Verifications row 1 (full protocol).
    - .planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md D-09 (full original idle protocol).
    - docs/test-performance.md § Post-Optimization Wallclock (Wave 3) lines 115-140 (the protocol template — same hardware, same command, same idle conditions; replicate exactly).
    - .planning/STATE.md § Baselines to Preserve (JaCoCo ≥ 88.88 %, SpotBugs 0).
  </read_first>
  <what-built>
    Phase 89 implementation is complete (Plans 89-01 + 89-02 merged). The branch is now ready for honest local wallclock measurement. This is the Wave-4 measurement step that proves the cumulative effect of PERF-01 + PERF-02 against the 10:24 Phase-86-post-audit baseline.

    Per D-02, this MUST be done manually because:
    - Local CPU/IO load varies unpredictably between runs.
    - The idle protocol requires the human operator to close heavy apps, suspend Spotlight indexing, disconnect the laptop charger / connect it consistently (whichever matches the Phase 86 baseline), etc.
    - Deterministic CI measurement is deferred to Phase 91 PERF-06 (D-17 trigger-equivalence).

    No automation can compress this into a single command — the human MUST observe the system state between runs.
  </what-built>
  <how-to-verify>
    Execute the Phase 86 D-09 idle protocol THREE times. Capture the output of each run to `.test-perf-logs/wave-4-{1,2,3}.log` (NOTE: `.test-perf-logs/` is gitignored — line 3 of `.gitignore` — these logs are ephemeral and MUST NOT be committed).

    Step 1 — prepare the system:
    - Close all heavy apps (browsers with many tabs, video calls, large IDE projects unrelated to ctc-manager).
    - Ensure no background `./mvnw` invocations are running: `pgrep -fl 'mvn|java.*surefire' || echo "clean"` should report "clean".
    - Verify the working tree is post-Plan-89-02: `git log --oneline -3` should show the 89-02 commit on top. `git status` clean.

    Step 2 — Run 1:
    ```
    mkdir -p .test-perf-logs
    time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev 2>&1 | tee .test-perf-logs/wave-4-1.log
    ```
    Record from the log:
    - Maven "Total time" line (format `MM:SS`, e.g., `10:24`).
    - `time` builtin output (bash `real` time at the very end, format `Xm Y.Zs` → convert to seconds).
    - Total context loads via:
      ```
      TOTAL=0
      for f in target/test-perf/context-loads-*.txt; do
        TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
      done
      echo "Total context loads: $TOTAL"
      ```
    - BUILD SUCCESS / BUILD FAILURE flag and any flake mentions (search the log for "FAILED" or "FLAKY").
    - **PERF-02-active gate (per WARNING 3, revision 1):**
      ```
      # Line 1 format gate — proves Plan 89-02 Task 1 listener-fix is in effect
      head -1 target/test-perf/context-loads-*.txt | head -1 | grep -E '^total [0-9]+$'
      # Must exit 0.
      # Hash-line presence gate — proves PERF-02 fingerprint data is being recorded
      grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*.txt | head -1
      # Must exit 0 (at least one hash line exists across all marker files).
      ```
      If EITHER gate fails, STOP and report — PERF-02 is not active and the Wave-4 measurement is invalid (rerunning with Plan 89-02 patched is required before continuing).

    Step 3 — wait ~30 seconds for the system to settle, then run again:
    ```
    time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev 2>&1 | tee .test-perf-logs/wave-4-2.log
    # ...record values as above, INCLUDING the PERF-02-active gates...
    head -1 target/test-perf/context-loads-*.txt | head -1 | grep -E '^total [0-9]+$'
    grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*.txt | head -1
    # Both must exit 0.
    ```

    Step 4 — wait ~30 seconds, then run a third time:
    ```
    time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev 2>&1 | tee .test-perf-logs/wave-4-3.log
    # ...record values as above, INCLUDING the PERF-02-active gates...
    head -1 target/test-perf/context-loads-*.txt | head -1 | grep -E '^total [0-9]+$'
    grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*.txt | head -1
    # Both must exit 0.
    ```

    Step 5 — record the median (Maven Total time field), the median (bash real), the median (context loads), and the delta vs the 10:24 / 625s Phase-86-post-audit baseline. JaCoCo coverage ratio should be inspected from `target/site/jacoco/jacoco.csv` after the LAST run (Maven `clean` wipes prior reports; verifying the most recent is sufficient).

    Step 6 — report back the values in a single reply to the orchestrator. Suggested format:
    ```
    Run 1: Maven 09:42, bash 582s, 79 context loads, BUILD SUCCESS, PERF-02 gates PASS
    Run 2: Maven 09:55, bash 595s, 80 context loads, BUILD SUCCESS, PERF-02 gates PASS
    Run 3: Maven 10:08, bash 608s, 79 context loads, BUILD SUCCESS, PERF-02 gates PASS
    Median: 09:55 / 595s / 79 context loads
    JaCoCo LINE coverage: 0.89XX (≥ 0.8888 — pass)
    SpotBugs: 0 findings
    Flakes across all 3 runs: 0
    ```

    If ANY run fails (BUILD FAILURE / flaky test surfaces), STOP and report the failure stack. Per [[no-flaky-dismissal]] (CLAUDE.md): a test that was green in Plan 89-02's full-suite verify but fails now is a regression, not a flake — find the root cause before continuing.
  </how-to-verify>
  <acceptance_criteria>
    - Behavior: All 3 idle-protocol runs exit 0 with BUILD SUCCESS and zero flakes.
    - Behavior: After EACH of the 3 runs, `head -1 target/test-perf/context-loads-*.txt | head -1 | grep -E '^total [0-9]+$'` exits 0 (confirms Plan 89-02 Task 1 format migration is in effect).
    - Behavior: After EACH of the 3 runs, `grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*.txt | head -1` exits 0 (confirms PERF-02 fingerprint data is being recorded — proves Plan 89-02 GREEN implementation is integrated).
    - Behavior: JaCoCo LINE coverage ratio ≥ 0.8888 across all runs (LAST-run JaCoCo CSV inspection is sufficient since `mvn clean` wipes prior reports).
    - Behavior: SpotBugs findings = 0 across all runs.
    - Documentation: 3-run summary reported back to the orchestrator with per-run Maven Total time, bash real, context loads, PERF-02 gate PASS/FAIL flag, build status; median + JaCoCo + SpotBugs + flake totals.
  </acceptance_criteria>
  <resume-signal>Reply with the 3-run summary (Run 1, Run 2, Run 3, Median, JaCoCo, SpotBugs, Flakes, PERF-02 gates). Then Task 2 populates `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` with those exact values.</resume-signal>
</task>

<task type="auto">
  <name>Task 2: Populate `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` + update § v1.12 Forward Path Lever-1 row</name>
  <files>docs/test-performance.md</files>
  <read_first>
    - docs/test-performance.md (full file — § Post-Optimization Wallclock (Wave 3) at lines 115-140 is the structural template; § v1.12 Forward Path at lines 329-350 contains the Lever-1 row to update).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-02 (honest delta, no gate), D-16 (doc surfaces touched).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md § `docs/test-performance.md (three modifications)` Analog B + Analog C.
    - The 3-run summary returned by the human operator in Task 1 — values go directly into the new table rows.
  </read_first>
  <action>
    Two distinct edits in `docs/test-performance.md`.

    EDIT 1 — Insert a new H2 section `## Post-Optimization Wallclock (Wave 4)` AFTER `## Post-Optimization Wallclock (Wave 3)` (which currently ends at line 140 with the `---` rule) and BEFORE `## CI Results (PERF-05)` (line 143). Use the SAME five-column GFM table format as § Wave 3.

    Template (executor substitutes `<R1_*>`, `<R2_*>`, `<R3_*>`, `<MEDIAN_*>`, `<DELTA_*>`, `<JACOCO>` with the values from Task 1):

    ```markdown
    ## Post-Optimization Wallclock (Wave 4)

    Local 3-run measurement after Phase 89 closed (Plans 89-01 PERF-01 per-fork
    `app.backup.staging-dir` + Plans 89-02 PERF-02 cache-key fingerprint listener).
    Same hardware and same idle-system protocol as Wave 3 (per Phase 86 D-09).
    Branch `gsd/v1.12-driver-import-and-test-perf` after Plans 89-01 and 89-02 merged.

    | Run | Maven Total time | bash `real`     | Context loads | Notes                    |
    | --- | ---------------- | --------------- | ------------- | ------------------------ |
    | 1   | <R1_MAVEN>       | <R1_BASH>       | <R1_CTX>      | BUILD SUCCESS, no flakes |
    | 2   | <R2_MAVEN>       | <R2_BASH>       | <R2_CTX>      | BUILD SUCCESS, no flakes |
    | 3   | <R3_MAVEN>       | <R3_BASH>       | <R3_CTX>      | BUILD SUCCESS, no flakes |

    **Median (Maven): <MEDIAN_MAVEN>.** **Median (bash real): <MEDIAN_BASH>.**
    **Wave-4 context-load count median: <MEDIAN_CTX>.**

    **Delta vs Phase-86-post-audit baseline (10:24 Maven / 625s bash / 79 context loads):**
    Maven <DELTA_MAVEN> (<DELTA_PCT_MAVEN>); bash real <DELTA_BASH> (<DELTA_PCT_BASH>);
    context loads <DELTA_CTX>.

    **JaCoCo LINE coverage:** <JACOCO> (≥ 0.8888 baseline preserved — D-15).
    **SpotBugs findings:** 0 (gate preserved — D-15).

    Per D-02 (honest reporting), this is a local 3-run median; the authoritative CI
    5-run re-harvest is deferred to Phase 91 PERF-06 (D-17 trigger-equivalence).
    Local run variance was ~Xs (max-min spread) — see `## Post-Optimization
    Wallclock (Wave 3)` for the prior-baseline variance discussion (66s spread on n=3).

    For the per-cluster cache-key fingerprint breakdown that informs PERF-03
    (Phase 90 consolidation), see `## PERF-02 Forensics — Cache-Key Fingerprint Analysis`.
    ```

    Replace the angle-bracket placeholders with the values from Task 1's report.
    - `<DELTA_MAVEN>` = signed integer seconds (e.g., `-29s` if Wave-4 is faster, `+11s` if slower).
    - `<DELTA_PCT_MAVEN>` = `-X.X%` or `+X.X%` relative to 624s (= 10m 24s).
    - `<DELTA_CTX>` = signed integer (e.g., `-12` if PERF-02 reduced loads, `+2` if unchanged).
    - If the delta is positive (regression), report it honestly with a one-sentence prose note (e.g., "Wave-4 ran +11s slower than Wave-3 baseline — within the ~66s local-variance band documented in § Wave 3"); per D-02 this is acceptable and the CI re-harvest in Phase 91 is the authoritative measure.

    EDIT 2 — Update `§ v1.12 Forward Path` Lever-1 row (currently the first row of the table at line 340). Replace the existing single-cell `Required Touchpoints` content with a two-paragraph version: original touchpoint list PLUS a new status indicator. Also add a paragraph immediately AFTER the table (after line 343) explaining Lever-1 closure.

    Specifically:
    - In the existing `**1. Per-fork ...**` table row, prepend `**[DONE Phase 89]**` to the `**1. Per-fork** ...` cell content. Example: `**1. Per-fork `data/dev/backup-staging/` refactor** [DONE Phase 89] (Top-1 per D-14) — ...`
    - Add a new paragraph between the table (ending line ~343) and the "Lever 1 is the largest single delta..." sentence (line 344):

      ```
      **Phase 89 closure note:** Lever 1 landed via Plan 89-01 (per-fork
      `app.backup.staging-dir` resolved through Surefire/Failsafe
      `<systemPropertyVariables>` with `${surefire.forkNumber}` substitution; Failsafe
      `default-it` permanently `forkCount=2 reuseForks=true`). The measured local delta
      is recorded in `## Post-Optimization Wallclock (Wave 4)` above. PERF-02
      instrumentation (Plan 89-02) supplies the per-context cache-key fingerprint data
      that drives the Phase 90 PERF-03 consolidation target identification — see
      `## PERF-02 Forensics — Cache-Key Fingerprint Analysis`.
      ```

    No other section changes (do NOT modify `## Baseline`, `## Result Verdict`, `## CI Results`, `## Context Load Counts`, `## Per-Decision Evidence`, or the Lever 2 / Lever 3 rows in `## v1.12 Forward Path`).
  </action>
  <verify>
    <automated>
      grep -c '## Post-Optimization Wallclock (Wave 4)' docs/test-performance.md
      # Must equal 1 (the new H2 heading).
      grep -c '\[DONE Phase 89\]' docs/test-performance.md
      # Must equal 1 (Lever-1 row updated).
      grep -c '10:24' docs/test-performance.md
      # Must be >= 2 (one in § Wave 3 baseline + one in § Wave 4 delta line).
      # Verify no placeholder leftovers
      grep -c '<R[123]_\|<MEDIAN_\|<DELTA_\|<JACOCO>' docs/test-performance.md
      # Must equal 0 (all template placeholders replaced with measured values).
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: `docs/test-performance.md` contains a new H2 section `## Post-Optimization Wallclock (Wave 4)` between § Wave 3 and § CI Results (PERF-05).
    - Source: the new § Wave 4 table has 3 populated data rows + bold Median line + delta-vs-10:24 line + JaCoCo + SpotBugs gate confirmations.
    - Source: no template placeholders (`<R1_*>`, `<DELTA_*>`, `<JACOCO>`, etc.) remain in the file.
    - Source: `## v1.12 Forward Path` Lever-1 row carries `[DONE Phase 89]` annotation.
    - Source: a new "Phase 89 closure note" paragraph appears between the v1.12 Forward Path table and the "Lever 1 is the largest single delta..." sentence.
    - Behavior: no other H2 sections altered; § Baseline, § Result Verdict, § CI Results, § Context Load Counts, § Per-Decision Evidence remain byte-identical to their pre-edit state.
  </acceptance_criteria>
  <done>
    `docs/test-performance.md` carries the Wave-4 measurement table with median + delta-vs-baseline + JaCoCo gate, and the v1.12 Forward Path table marks Lever 1 as DONE with cross-references to the new § Wave 4 and § PERF-02 Forensics sections.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add `README.md § Test Performance` pointer</name>
  <files>README.md</files>
  <read_first>
    - README.md (full file; § Documentation at lines 148-150 is the structural model; no existing § Test Performance section).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-16 (locks: README pointer to Wave-4 figure; NO Wiki / NO operations runbook touched).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md § `README.md` analog (pattern: short text + link, no table, no code block, consistent with § Documentation style).
    - docs/test-performance.md (post-Task-2 state — the file the new README section links to).
  </read_first>
  <action>
    Insert a new H2 section `## Test Performance` in `README.md` AFTER the existing `## Documentation` section (lines 148-150) and BEFORE the end of the file. Use the Edit tool.

    Insertion content (substitute `<MEDIAN_MAVEN>` with the actual Wave-4 median value from Task 1, e.g., `09:55` or `10:08`):

    ```markdown

    ## Test Performance

    See [`docs/test-performance.md`](docs/test-performance.md) for the test-wallclock
    optimization history and current baseline.

    **Current local median (Wave 4, post Phase 89):** `<MEDIAN_MAVEN>` Maven Total time
    on `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev`
    (idle protocol, 3-run median). The authoritative CI 5-run median re-harvest is
    deferred to Phase 91 (PERF-06).
    ```

    Keep tone consistent with the adjacent `## Documentation` section — short and pointer-focused; no table, no code block per PATTERNS § README.md analog.

    Do NOT modify any other README content (Tech Stack, Features, Backup & Restore, Quick Start, Playwright Setup, Development, OpenRewrite, Documentation).
  </action>
  <verify>
    <automated>
      grep -c '^## Test Performance$' README.md
      # Must equal 1.
      grep -c 'docs/test-performance.md' README.md
      # Must be >= 1 (the new pointer link).
      # Section ordering: § Test Performance must come AFTER § Documentation
      awk '/^## Documentation$/{doc=NR} /^## Test Performance$/{tp=NR} END{exit (tp > doc ? 0 : 1)}' README.md
      # Must exit 0.
      grep -c '<MEDIAN_MAVEN>' README.md
      # Must equal 0 (placeholder replaced).
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: README.md contains a new H2 section `## Test Performance` placed after `## Documentation`.
    - Source: section contains a Markdown link to `docs/test-performance.md`.
    - Source: section cites the Wave-4 local median figure.
    - Source: no placeholder leftover (`<MEDIAN_MAVEN>` replaced with actual value).
    - Behavior: README.md remains valid Markdown; other sections byte-identical to pre-edit state.
  </acceptance_criteria>
  <done>
    `README.md § Test Performance` exists and points at the new Wave-4 figure in `docs/test-performance.md`.
  </done>
</task>

<task type="auto">
  <name>Task 4: Final phase gate — `./mvnw clean verify -Pe2e` + JaCoCo / SpotBugs assertion</name>
  <files>(verification only — no source files)</files>
  <read_first>
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-15 (locks gates: JaCoCo ≥ 88.88 %, SpotBugs 0, CodeQL exit 0).
    - .planning/STATE.md § Baselines to Preserve.
    - CLAUDE.md § "Test-Aufrufe optimieren" + § "Clean Build/Test Only" + § "E2E-Tests bei Verifikation".
  </read_first>
  <action>
    Final-pass phase gate. This is the SINGLE authoritative `verify -Pe2e` run for Phase 89 per [[test-call-optimization]] (one final verify per phase suffices; Task 1's 3 runs were measurement, not gate).

    Run:
    ```
    ./mvnw clean verify -Pe2e --no-transfer-progress
    ```
    Must exit 0 with:
    - All unit, integration, AND Playwright E2E tests green.
    - SpotBugs Medium+HIGH findings = 0 (per D-15 + CLAUDE.md SpotBugs gate).
    - JaCoCo report generated.

    Then extract and assert the JaCoCo LINE coverage ratio:
    ```
    awk -F, 'NR==1 {for(i=1;i<=NF;i++){if($i=="LINE_COVERED")c=i;if($i=="LINE_MISSED")m=i}} NR>1 {tot_c+=$c; tot_m+=$m} END {ratio=tot_c/(tot_c+tot_m); printf "JaCoCo LINE ratio: %.4f\n", ratio; if(ratio<0.8888) exit 1}' target/site/jacoco/jacoco.csv
    ```
    Must exit 0 (ratio ≥ 0.8888 per D-15 + v1.11 baseline in STATE.md).

    Record in the plan SUMMARY:
    - Total test count (1014+ — 1011 v1.11 baseline + 3 new tests from Plans 89-01 / 89-02).
    - JaCoCo LINE coverage ratio (must be ≥ 0.8888).
    - SpotBugs findings (must be 0).
    - The final wallclock from this run (for archive — NOT a 4th measurement run; just the value observed during the gate).
    - Whether the E2E step (`@Tag("e2e")` Playwright tests) ran clean — that is the Phase 89 milestone gate per CLAUDE.md § E2E-Tests bei Verifikation.
  </action>
  <verify>
    <automated>
      ./mvnw clean verify -Pe2e --no-transfer-progress
      # Must exit 0 — unit + integration + Playwright E2E all green.
      awk -F, 'NR==1 {for(i=1;i<=NF;i++){if($i=="LINE_COVERED")c=i;if($i=="LINE_MISSED")m=i}} NR>1 {tot_c+=$c; tot_m+=$m} END {ratio=tot_c/(tot_c+tot_m); printf "JaCoCo LINE ratio: %.4f\n", ratio; if(ratio<0.8888) exit 1}' target/site/jacoco/jacoco.csv
      # Must exit 0 — JaCoCo LINE coverage ratio ≥ 0.8888.
    </automated>
  </verify>
  <acceptance_criteria>
    - Behavior: `./mvnw clean verify -Pe2e --no-transfer-progress` exits 0.
    - Behavior: JaCoCo LINE coverage ratio in `target/site/jacoco/jacoco.csv` ≥ 0.8888.
    - Behavior: SpotBugs build does NOT fail (Medium+HIGH findings = 0).
    - Behavior: Playwright E2E suite passes (no skipped/failed E2E tests).
    - Documentation: plan SUMMARY records test count, JaCoCo ratio, SpotBugs count, and observed wallclock.
  </acceptance_criteria>
  <done>
    Phase 89 final gate passes. All v1.11 baselines preserved (JaCoCo ≥ 88.88 %, SpotBugs 0, CodeQL governed by separate workflow). Plan 89-03 SUMMARY records the closing metrics; Phase 89 is ready to flip to Complete in STATE.md + ROADMAP.md + REQUIREMENTS.md (post-merge by the orchestrator's close step).
  </done>
</task>

</tasks>

<verification>
Full-plan gate:
- Task 1: 3 idle-protocol runs complete with BUILD SUCCESS + 0 flakes each; PERF-02-active gates exit 0 after each run.
- Task 2: `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` populated with measured values; § v1.12 Forward Path Lever-1 row carries `[DONE Phase 89]`.
- Task 3: `README.md § Test Performance` exists with link + Wave-4 median figure.
- Task 4: Final `./mvnw clean verify -Pe2e` exits 0; JaCoCo LINE ≥ 0.8888; SpotBugs 0.
- No `CLAUDE.md` change (D-16).
- No `docs/operations/import-runbook.md` change (D-16).
- No `application*.yml` change (D-14).
- `.test-perf-logs/wave-4-{1,2,3}.log` exist locally but are NOT committed (gitignored).
</verification>

<success_criteria>
Phase 89 Wave-4 closure complete when:
- `docs/test-performance.md` has the new § Post-Optimization Wallclock (Wave 4) table with 3 measured rows + median + delta vs 10:24 baseline + JaCoCo gate confirmation.
- `docs/test-performance.md § v1.12 Forward Path` marks Lever 1 = DONE Phase 89 with cross-references to § Wave 4 and § PERF-02 Forensics.
- `README.md § Test Performance` cites the Wave-4 median and links to `docs/test-performance.md`.
- Final `./mvnw clean verify -Pe2e` exits 0; v1.11 baselines preserved (JaCoCo ≥ 88.88 %, SpotBugs 0, all 1014+ tests green).
- Phase 89 ROADMAP/STATE/REQUIREMENTS files ready for the orchestrator's close step to flip PERF-01 + PERF-02 status to Resolved.
</success_criteria>

<output>
Create `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` when done, including:

- `## Changes` — `docs/test-performance.md` two edits (§ Wave 4 + § Forward Path Lever-1 update), `README.md` § Test Performance pointer.
- `## Wave 4 Measurement` — the 3-run table with measured values (copy of the table from § Wave 4) + median + delta-vs-baseline + PERF-02-gate confirmation.
- `## Quality Gates` — JaCoCo ratio, SpotBugs count, total test count, final wallclock.
- `## Local Logs` — note that `.test-perf-logs/wave-4-{1,2,3}.log` are gitignored ephemeral measurement logs (NOT committed).
- `## Phase 89 Closure` — confirm all 5 Phase-89 Success Criteria (ROADMAP.md lines 201-206) are met; PERF-01 and PERF-02 ready for REQUIREMENTS.md status flip to Resolved.
- `## Forward Path` — Phase 90 (PERF-03 + PERF-04 + PERF-05) now unblocked; will consume PERF-02 fingerprint data from § PERF-02 Forensics.
</output>
