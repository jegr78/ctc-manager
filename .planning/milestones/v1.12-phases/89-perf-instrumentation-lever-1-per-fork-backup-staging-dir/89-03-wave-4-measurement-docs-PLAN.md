---
phase: 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
plan: 03
type: execute
wave: 3
depends_on:
  - 89-01
  - 89-02
files_modified:
  - docs/test-performance.md
  - README.md
autonomous: false
requirements:
  - PERF-01
  - PERF-02
user_setup: []

tags:
  - perf
  - measurement
  - wallclock
  - docs

must_haves:
  truths:
    - "Three local `./mvnw clean verify -Pe2e` runs are recorded per Phase-86 D-09 idle protocol on branch `gsd/v1.12-driver-import-and-test-perf` — Maven wall time + bash `real` time + total context-load count + JaCoCo line coverage (≥ 88.88 % per D-15)"
    - "Wave-4 median is computed honestly: median Maven wall time = middle of the three runs (NOT mean, NOT min); the delta is reported vs. **10:24** Phase-86 post-audit local baseline (D-02 + `specifics` note in CONTEXT.md), NOT vs. the 09:45 pre-audit baseline"
    - "PERF-02 instrumentation is empirically active during each Wave-4 run: primary marker `total <N>` Line-1 format AND sidecar fingerprint lines are inspected after each run as a regression gate (D-08)"
    - "`docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` populated with the 5-column table + bold median lines + JaCoCo number"
    - "`docs/test-performance.md § v1.12 Forward Path` table updated: Lever-1 row carries `DONE (Phase 89)` annotation + cross-reference note pointing to § Post-Optimization Wallclock (Wave 4) and § PERF-02 Forensics"
    - "`README.md § Test Performance` (NEW section) carries a pointer paragraph to the Wave-4 figure and `docs/test-performance.md` — adjacent to the existing `## Documentation` section (RESEARCH PATTERN — no current Test Performance section in README)"
    - "Final phase gate: `./mvnw clean verify -Pe2e --no-transfer-progress` exits 0 with JaCoCo ≥ 88.88 % AND SpotBugs `BugInstance` = 0 AND CodeQL gate-step exit 0 (the last enforced by CI after PR push, not locally — checkpoint covers it)"
    - "No production behavior change: `git diff --stat` against base shows only test-infrastructure + doc files modified across all 3 plans (D-14 invariant)"
  artifacts:
    - path: "docs/test-performance.md"
      provides: "NEW `## Post-Optimization Wallclock (Wave 4)` section with 5-column table + bold median lines + JaCoCo note; updated `## v1.12 Forward Path` Lever-1 row marked DONE"
      contains: "## Post-Optimization Wallclock (Wave 4)"
    - path: "README.md"
      provides: "NEW `## Test Performance` section (post Documentation section) pointing at the new Wave-4 baseline and docs/test-performance.md"
      contains: "## Test Performance"
  key_links:
    - from: "docs/test-performance.md § Post-Optimization Wallclock (Wave 4)"
      to: "docs/test-performance.md § v1.12 Forward Path Lever-1"
      via: "DONE (Phase 89) annotation + cross-reference note"
      pattern: "DONE \\(Phase 89\\)"
    - from: "README.md § Test Performance"
      to: "docs/test-performance.md"
      via: "relative link"
      pattern: "docs/test-performance\\.md"
    - from: "Wave-4 measurement protocol"
      to: "Phase-86 D-09 idle protocol"
      via: "3 idle local runs, median of middle, branch-anchored"
      pattern: "Phase[- ]86|D-09"
---

<objective>
PERF-01 + PERF-02 acceptance measurement: run 3 local `./mvnw clean verify -Pe2e` per the Phase-86 D-09 idle protocol on the branch carrying Plans 89-01 + 89-02, record Maven wall time + bash `real` + total context-load count + JaCoCo line coverage per run, compute the median and delta vs. the **10:24 Phase-86 post-audit local baseline** (D-02 honest reporting per CONTEXT.md `specifics`), populate `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)`, mark Lever-1 as DONE in `§ v1.12 Forward Path`, add a `§ Test Performance` pointer section to `README.md` (D-16 scope), and gate the entire phase with one final `./mvnw clean verify -Pe2e` per [[test-call-optimization]].

Purpose: Closes Phase 89 by giving the milestone an honest local measurement of Lever-1's wallclock effect. D-02 explicitly forbids a hard local reduction gate — local run-variance (66s spread observed in Phase-86 post-audit) makes a strict numeric gate statistically fragile. The CI-authoritative re-harvest is deferred to Phase 91 PERF-06 (CI 5-run median). This plan's job is honest reporting: record what was measured, compare against the 10:24 post-audit baseline, document the result, gate JaCoCo/SpotBugs/CodeQL invariants.

Output: Two updated doc files (docs/test-performance.md, README.md). NO code changes. NO test changes. The plan is a manual-protocol measurement bookended by automated full-suite gates.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-02-SUMMARY.md
@.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md
@docs/test-performance.md
@CLAUDE.md

<interfaces>
<!-- Measurement protocol + table conventions. Extracted from RESEARCH §RQ-9 + Phase-86 D-09. Executor uses these directly. -->

Phase-86 D-09 idle protocol (canonical 3-run local measurement):
- Close all heavy applications (browsers with many tabs, IDE indexers, Docker desktop UI not strictly required).
- Run `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` three times back-to-back.
- Record per run: Maven "Total time:" line (mm:ss) + bash `real` (full elapsed) + total context-load count (sum across primary marker files) + JaCoCo line coverage % (from `target/site/jacoco/jacoco.csv` after each run).
- Median definition: middle value of the three (NOT mean). Discard min+max → middle.

5-column wallclock table format (existing `## Post-Optimization Wallclock (Wave 3)` analog, docs/test-performance.md lines 122-129):
```markdown
| Run | Maven Total time | bash `real`     | Context loads | Notes                    |
| --- | ---------------- | --------------- | ------------- | ------------------------ |
| 1   | mm:ss            | XXXs (Nm Ns)    | NN            | BUILD SUCCESS, no flakes |
| 2   | mm:ss            | XXXs (Nm Ns)    | NN            | BUILD SUCCESS, no flakes |
| 3   | mm:ss            | XXXs (Nm Ns)    | NN            | BUILD SUCCESS, no flakes |
```
Followed by bold median: `**Median (Maven): mm:ss (run N).** **Median (bash real): XXXs = Nm Ns.**`

PERF-02 instrumentation regression-gate command (after each run):
```bash
head -1 target/test-perf/context-loads-*.txt | head -1 | grep -E '^total [0-9]+$'  # D-08 Line-1 format
grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*-fingerprints.txt | head -1  # PERF-02 active
```

JaCoCo gate extraction (after each run):
```bash
awk -F, 'NR>1 && $3=="" {next} NR>1 {missed+=$8; covered+=$9} END {if(missed+covered>0) printf "%.4f\n", covered/(missed+covered)}' target/site/jacoco/jacoco.csv
```
Result must be ≥ 0.8888 (88.88 % per D-15).

Total-context-load aggregator (post Plan 89-02 D-08 migration):
```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  case "$f" in *-fingerprints.txt) continue ;; esac
  TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
done
echo "Total context loads: $TOTAL"
```

Phase-86 baseline for delta calculation (per CONTEXT.md D-02 + `specifics` note):
- LOCAL baseline = **10:24** (Phase-86 post-audit local median, NOT the 09:45 pre-audit median — using 09:45 would inflate apparent improvement).
- CI baseline = 23:00 (Phase-86 CI median; NOT this plan's job — Phase 91 PERF-06 re-harvests CI).

README.md "Test Performance" section anchor (RESEARCH PATTERN — README currently has NO Test Performance section; insert after `## Documentation` at line ~148):
- One-paragraph pointer (2-3 sentences). Plain prose; no table, no code block.
- Link target: `docs/test-performance.md`. Mention "v1.12 Wave-4 baseline" + cross-reference to Phase 91 PERF-06 for the authoritative CI number.
</interfaces>
</context>

<tasks>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 1: Wave-4 wallclock measurement — 3 idle-protocol local runs + PERF-02-active gate</name>
  <files>(no source file modifications — produces measurement evidence for Task 2)</files>
  <requirement>PERF-01</requirement>
  <read_first>
    - .planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md D-09 (3-run idle protocol — verbatim copy-forward)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-02 (honest reporting, NO hard local gate; 10:24 baseline)
    - docs/test-performance.md `## Post-Optimization Wallclock (Wave 3)` (analog table format)
    - CLAUDE.md "Test-Aufrufe optimieren" (single final `./mvnw verify -Pe2e` per phase — Task 1's 3 runs ARE this phase's final verify; no additional finals beyond that)
  </read_first>
  <action>
    Execute the Phase-86 D-09 idle protocol three consecutive times on branch `gsd/v1.12-driver-import-and-test-perf` with Plans 89-01 + 89-02 merged.

    For EACH of the 3 runs:

    1. Close heavy apps (browsers with many tabs, JetBrains IDE indexers if running, VS Code language-server activity). Docker desktop daemon may stay running (Testcontainers idle).
    2. From project root, run: `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev`.
    3. Wait for BUILD SUCCESS.
    4. Capture:
       - **Maven Total time** — from Maven's final summary line `[INFO] Total time:  mm:ss min`.
       - **bash `real`** — from `time`'s output `real    XmYs`. Convert to seconds + mm:ss form.
       - **Total context loads** — run the D-08-aware aggregator: `TOTAL=0; for f in target/test-perf/context-loads-*.txt; do case "$f" in *-fingerprints.txt) continue ;; esac; TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}'))); done; echo "$TOTAL"`. Record the integer.
       - **JaCoCo ratio** — `awk -F, 'NR>1 {missed+=$8; covered+=$9} END {printf "%.4f\n", covered/(missed+covered)}' target/site/jacoco/jacoco.csv`. Record to 4 decimals (e.g. `0.8892`).
       - **PERF-02 active proof** — after each run, before the next `mvn clean` wipes `target/`, verify:
         - `head -1 target/test-perf/context-loads-*.txt | head -1 | grep -E '^total [0-9]+$'` exits 0 (D-08 Line 1).
         - `grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*-fingerprints.txt | head -1` exits 0 (fingerprint sidecar non-empty).
       - **Notes** — `BUILD SUCCESS, no flakes` if clean; otherwise capture the failure detail. If ANY flake or failure surfaces, STOP — do NOT silently rerun. Investigate per [[no-flaky-dismissal]].
    5. Persist the per-run capture in `.test-perf-logs/89-03-wave4-run-{1,2,3}.log` (outside `target/` since `mvn clean` wipes it — Phase-86 D-09 verbatim).
    6. Do not proceed to the next run until the previous one's BUILD SUCCESS + PERF-02 active proof are recorded.

    After all 3 runs complete, compute the median Maven Total time (middle of three sorted by mm:ss) and median bash `real`. Compute the delta against the 10:24 baseline = `(median_seconds - 624) / 624 * 100` (10:24 = 624 seconds) — report as `+X %` (regression) or `-X %` (improvement). Tabulate the JaCoCo ratios per run and report the minimum (must be ≥ 0.8888).

    This task is `checkpoint:human-verify` because: (a) local CPU/IO load varies between runs (Phase-86 D-15 realistic-optimistic framing); (b) the protocol explicitly requires human attention to ensure idle state; (c) `workflow.human_verify_mode` is NOT `end-of-phase` for this repo's config (default `inline` mode applies). After the 3rd run completes, the user inspects the captured numbers and either approves or describes anomalies.

    Resume signal: type `approved` with the measured numbers (run-1 mm:ss, run-2 mm:ss, run-3 mm:ss, median, JaCoCo min ratio) OR describe a regression / flake observation if one surfaced.
  </action>
  <verify>
    <human-check>
      What was built: Plans 89-01 + 89-02 merged; per-fork staging-dir + PERF-02 fingerprint listener active.

      How to verify (per run, 3 total):
      1. `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` → BUILD SUCCESS.
      2. Maven Total time recorded (mm:ss).
      3. bash `real` recorded (Xs / Nm Ns).
      4. Total context loads recorded via D-08-aware aggregator (integer).
      5. JaCoCo ratio recorded via awk one-liner (≥ 0.8888).
      6. PERF-02 active proof: `head -1 target/test-perf/context-loads-*.txt | head -1 | grep -E '^total [0-9]+$'` exits 0; `grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*-fingerprints.txt | head -1` exits 0.
      7. Notes: BUILD SUCCESS, no flakes (or capture flake detail — STOP if any).
      8. Log persisted to `.test-perf-logs/89-03-wave4-run-N.log`.

      Aggregate verification after all 3 runs:
      9. Median Maven Total time and median bash `real` computed.
      10. Delta vs. 10:24 (624s) baseline computed as a percentage.
      11. Minimum JaCoCo ratio ≥ 0.8888.

      Expected outcomes (D-02 honest reporting — no hard local gate):
      - Median may be lower (improvement), comparable, or modestly worse than 10:24. ALL three are acceptable Phase-89 outcomes — the CI-authoritative re-harvest is Phase 91 PERF-06's job.
      - PERF-02 active proof MUST pass on all 3 runs (this IS a gate — the instrumentation must be alive end-to-end).
      - JaCoCo MUST be ≥ 0.8888 on all 3 runs (D-15 invariant gate).

      Anomalies to flag:
      - Any flake or BUILD FAILURE (STOP, investigate per [[no-flaky-dismissal]]).
      - Delta > +20 % vs. 10:24 (Phase-86 D-10 variance tolerance — investigate).
      - JaCoCo < 0.8888 (regression — investigate).
      - PERF-02 marker/sidecar empty or wrong format (instrumentation regression — fix in Plan 89-02 before continuing).
    </human-check>
    <resume-signal>Type `approved` with the 5 numbers (run-1 mm:ss, run-2 mm:ss, run-3 mm:ss, median, JaCoCo-min) OR describe any anomaly.</resume-signal>
  </verify>
  <done>
    Three runs of `./mvnw clean verify -Pe2e` completed in idle state; per-run logs persisted; PERF-02 active proof exits 0 on each run; JaCoCo ≥ 0.8888 on each run; median Maven Total time + median bash `real` + delta-vs-10:24 + JaCoCo-min computed; user has typed `approved` with the numbers OR identified an anomaly.
  </done>
</task>

<task type="auto">
  <name>Task 2: docs/test-performance.md — populate § Post-Optimization Wallclock (Wave 4) + update § v1.12 Forward Path Lever-1</name>
  <files>docs/test-performance.md</files>
  <requirement>PERF-01</requirement>
  <read_first>
    - docs/test-performance.md `## Post-Optimization Wallclock (Wave 3)` section (analog 5-column table — lines ~115-140)
    - docs/test-performance.md `## v1.12 Forward Path` section (Lever-1 row — lines ~329-350; format depends on current file structure post-Plan-89-02 edit)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-02 (delta vs 10:24, honest reporting), D-16 (doc scope: docs/test-performance.md + README updated; CLAUDE.md NOT touched)
    - Task 1's captured numbers (median Maven time, median bash real, per-run context-load counts, JaCoCo-min)
  </read_first>
  <action>
    Edit `docs/test-performance.md` with TWO changes:

    Change 1 — INSERT `## Post-Optimization Wallclock (Wave 4)` section AFTER the existing `## Post-Optimization Wallclock (Wave 3)` and BEFORE `## CI Results (PERF-05)` (or whichever section follows Wave 3 in the current file).

    Section template (fill in actual numbers from Task 1):
    ```markdown
    ## Post-Optimization Wallclock (Wave 4)

    Local 3-run measurement after Phase 89 Plans 89-01 (PERF-01 per-fork staging-dir + Failsafe `default-it` forkCount=2) and 89-02 (PERF-02 cache-key fingerprint listener) merged on branch `gsd/v1.12-driver-import-and-test-perf` (commit SHA: <fill at write time>). Identical command and idle protocol as Phase 86 Wave 3 (D-09).

    | Run | Maven Total time | bash `real`    | Context loads | Notes                    |
    | --- | ---------------- | -------------- | ------------- | ------------------------ |
    | 1   | <mm:ss>          | <XXXs (Nm Ns)> | <NN>          | BUILD SUCCESS, no flakes |
    | 2   | <mm:ss>          | <XXXs (Nm Ns)> | <NN>          | BUILD SUCCESS, no flakes |
    | 3   | <mm:ss>          | <XXXs (Nm Ns)> | <NN>          | BUILD SUCCESS, no flakes |

    **Median (Maven): <mm:ss> (run N).** **Median (bash real): <XXXs = Nm Ns>.**

    **Delta vs. Phase-86 post-audit baseline (10:24 local median):** <+X.X % / -X.X %>. <Brief interpretation: "Lever-1's per-fork-staging refactor enabled Failsafe forkCount=2 on backup ITs, parallelising the largest IT cluster. Local measurement is observational only — Phase 91 PERF-06 will re-harvest the CI median (Phase-86 baseline 23:00) for the authoritative cumulative effect of Phases 88-90.">

    **JaCoCo line coverage:** minimum <0.XXXX> across the 3 runs (gate: ≥ 0.8888 per D-15 — gate held).

    PERF-02 instrumentation active per run (D-08 marker `total <N>` Line 1 + sidecar fingerprint lines present); Top-5 cluster output produced by `scripts/test-perf/aggregate-fingerprints.sh` is recorded in the next Plan-89-03 SUMMARY for Phase 90 PERF-03 consumption.
    ```

    Change 2 — UPDATE `## v1.12 Forward Path` table Lever-1 row.

    Locate the Lever-1 row (text starts with `**1. Per-fork ...backup-staging.../...refactor**` or similar — the exact wording is the current docs/test-performance.md form). In the `Required Touchpoints` column OR as an appended bold note, add `DONE (Phase 89)` annotation. Also add a one-paragraph note BELOW the table:

    ```markdown
    *Lever-1 status:* DONE — see § Post-Optimization Wallclock (Wave 4) for the local median + delta and § PERF-02 Forensics for the cache-key fingerprint data that feeds Phase 90 PERF-03. Phase 91 PERF-06 will re-harvest the CI authoritative median to update the v1.11 23:00 baseline.
    ```

    The exact Lever-2 / Lever-3 rows are NOT this plan's concern — they remain as-is, marked future-phase per the current doc state.

    Touch NO other files in this task. `README.md` is Task 3.
  </action>
  <verify>
    <automated>
      1. `grep -c '^## Post-Optimization Wallclock (Wave 4)' docs/test-performance.md` returns 1.
      2. `grep -c '^## Post-Optimization Wallclock (Wave 3)' docs/test-performance.md` returns 1 (existing section preserved).
      3. Ordering check: `awk '/^## /{print NR, $0}' docs/test-performance.md | grep -A1 'Wave 3' | tail -1 | grep -q 'Wave 4'` — Wave 4 directly follows Wave 3.
      4. Median lines present: `grep -c '^\*\*Median (Maven):' docs/test-performance.md` >= 1 AND `grep -c '^\*\*Median (bash real):' docs/test-performance.md` >= 1.
      5. Delta vs 10:24 baseline referenced: `grep -c -E '10:24|Phase-86 post-audit baseline' docs/test-performance.md` >= 1.
      6. JaCoCo gate present: `grep -c -E '0\.8888|88\.88 ?%' docs/test-performance.md` >= 1.
      7. Lever-1 DONE annotation: `grep -c 'DONE (Phase 89)' docs/test-performance.md` >= 1.
      8. PERF-02 cross-reference: `grep -c 'PERF-02 Forensics' docs/test-performance.md` >= 2 (Plan-89-02 already inserted the section; Plan-89-03 references it at least once in the new Wave-4 prose AND in the Lever-1 status paragraph).
      9. Anti-regression on Phase-86 baseline-reporting honesty: `grep -c '09:45' docs/test-performance.md` should NOT increase (existing references in Wave-3 prose are fine; the new Wave-4 section must compare against 10:24 only — verify via diff: `git diff docs/test-performance.md | grep -c '+.*09:45'` returns 0 unless preserving existing text).
      10. Table format: `grep -cE '^\| Run \| Maven Total time' docs/test-performance.md` >= 2 (one for Wave 3 existing, one for Wave 4 new).
    </automated>
  </verify>
  <done>
    `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` is populated with the 5-column table + bold median lines + delta-vs-10:24 + JaCoCo gate confirmation + PERF-02 instrumentation-active note; `§ v1.12 Forward Path` Lever-1 row carries `DONE (Phase 89)` annotation + status paragraph; ordering relative to Wave-3 + downstream sections is preserved.
  </done>
</task>

<task type="auto">
  <name>Task 3: README.md — new `## Test Performance` pointer section</name>
  <files>README.md</files>
  <requirement>PERF-02</requirement>
  <read_first>
    - README.md entire file (current structure — verify no `## Test Performance` section exists; identify `## Documentation` anchor at line ~148 per RESEARCH PATTERN)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-16 (`README.md` Test-Performance section pointer is in scope; CLAUDE.md is OUT)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md lines 577-589 (README pointer pattern — plain text link, no table/code-block; matches `## Documentation` style)
  </read_first>
  <action>
    Edit `README.md`. Insert a new `## Test Performance` section AFTER the existing `## Documentation` section and BEFORE whichever section follows it. Section body (3-5 lines, plain prose, no table, no code block):

    ```markdown
    ## Test Performance

    Test wallclock metrics, per-phase optimisation history, and the per-fork backup-staging-dir + cache-key fingerprint instrumentation are documented in [`docs/test-performance.md`](docs/test-performance.md). The current local baseline is the v1.12 Wave-4 median (Phase 89, per-fork `app.backup.staging-dir` + Failsafe `default-it` forkCount=2 + PERF-02 cache-key fingerprint listener). The authoritative CI median will be re-harvested in v1.12 Phase 91 (PERF-06).
    ```

    Match the prose style of the adjacent `## Documentation` section (plain bulleted-or-paragraph prose, no extra emphasis, no badges, no tables).

    Touch NO other files in this task.
  </action>
  <verify>
    <automated>
      1. `grep -c '^## Test Performance$' README.md` returns 1.
      2. Ordering: `awk '/^## /{print NR, $0}' README.md | grep -A1 'Documentation' | tail -1 | grep -q 'Test Performance'` — new section appears directly after `## Documentation`.
      3. Link target present: `grep -c '\\[\`docs/test-performance\\.md\`\\](docs/test-performance\\.md)\|docs/test-performance\\.md' README.md` >= 1.
      4. Wave-4 reference: `grep -c -i 'wave[- ]4\|v1\\.12' README.md` >= 1 (anchors the pointer to the new baseline).
      5. Phase 91 cross-reference: `grep -c 'PERF-06\|Phase 91' README.md` >= 1 (sets expectation for the authoritative CI number).
      6. Anti-regression: the existing `## Documentation` section is unchanged: `git diff README.md | grep -cE '^-.*Documentation' ` returns 0.
    </automated>
  </verify>
  <done>
    `README.md` carries a new `## Test Performance` section directly after `## Documentation`; the section points to `docs/test-performance.md`, references the v1.12 Wave-4 median, and forward-references Phase 91 PERF-06 for the CI authoritative number.
  </done>
</task>

<task type="auto">
  <name>Task 4: Final phase gate — `./mvnw clean verify -Pe2e` exits 0; JaCoCo + SpotBugs invariants held; 89-03-SUMMARY.md</name>
  <files>.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md</files>
  <requirement>PERF-01</requirement>
  <read_first>
    - .planning/STATE.md "Baselines to Preserve" (JaCoCo 88.88 %, SpotBugs 0, CodeQL exit 0 on PR head SHA)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md row 89-03-04 (final phase gate definition)
    - $HOME/.claude/get-shit-done/templates/summary.md (SUMMARY template)
    - 89-01-SUMMARY.md + 89-02-SUMMARY.md (cross-link references)
  </read_first>
  <action>
    The 3 Wave-4 idle-protocol runs from Task 1 are this phase's final `./mvnw clean verify -Pe2e` invocations (per [[test-call-optimization]] — no further full-suite verify beyond Task 1's 3 runs). All 3 runs exited 0; JaCoCo ≥ 0.8888 on each; PERF-02 active proof on each.

    Step 1 — Confirm SpotBugs invariant from Task 1's runs: `target/spotbugs-reports/spotbugsXml.xml` (or `target/spotbugs.xml`) shows zero `<BugInstance>` entries on the final run. If the file structure differs in the current pom (spotbugs-maven-plugin 4.9.8.3 writes to `target/spotbugsXml.xml` by default), use that path. Run: `grep -c '<BugInstance' target/spotbugsXml.xml 2>/dev/null || true` — must return 0 OR the file must not exist (in which case the gate fired during `verify` exit 0 implicitly).

    Step 2 — Generate Top-5 cluster output from Plan 89-02's `scripts/test-perf/aggregate-fingerprints.sh` against the final run's `target/test-perf/`: `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5 > /tmp/89-03-top5.txt`. Inspect output — it should list 5 cache-key clusters with hex hashes + occurrence + cluster-size. This Top-5 is the Phase-90 PERF-03 starting point — capture it in the SUMMARY.

    Step 3 — Write `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` per the standard SUMMARY template. Sections to include:
    - Frontmatter (phase, plan: 03, completed date, requirements: [PERF-01, PERF-02], depends_on: [89-01, 89-02], wave: 3).
    - "What Shipped" — bullets: Wave-4 wallclock measurement (3 idle-protocol runs); `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` section; `docs/test-performance.md § v1.12 Forward Path` Lever-1 DONE annotation; `README.md § Test Performance` pointer.
    - "Decisions Honored" — explicit table mapping D-02 (honest reporting, 10:24 baseline), D-15 (JaCoCo ≥ 0.8888), D-16 (docs/test-performance.md + README updated; CLAUDE.md UNTOUCHED — git-diff proof).
    - "Wave-4 Numbers" — table with the actual measured values from Task 1: run-1 / run-2 / run-3 / median Maven / median bash real / context-load total per run / JaCoCo ratio per run / delta vs. 10:24.
    - "PERF-02 Top-5 Cluster Output" — embedded `/tmp/89-03-top5.txt` content (the actual Top-5 from `aggregate-fingerprints.sh`). This becomes Phase-90 PERF-03's targeting data.
    - "Invariants Held" — checklist: JaCoCo ≥ 0.8888 ✓; SpotBugs `BugInstance` = 0 ✓; production yml files git-clean ✓; `BackupStagingCleanup.java` git-clean ✓; `ImportLockService.java` git-clean ✓ (Task 4 of Plan 89-01 was test-scope only).
    - "Phase 89 Closure" — pointer: Phase 90 PERF-03 consumes the Top-5 cluster output; Phase 91 PERF-06 re-harvests CI median.
    - "Open follow-ups" — if Plan 89-01 Task 4 chose option (c) deadline bump (rather than root-cause fix), the follow-up issue ID created at that time gets linked here.
  </action>
  <verify>
    <automated>
      1. `test -f .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md`.
      2. `grep -c '^## Wave-4 Numbers\|^## What Shipped\|^## Decisions Honored' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` >= 3.
      3. `grep -c 'PERF-02 Top-5\|Top.5 Cluster\|aggregate-fingerprints' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` >= 1 (Phase-90 hand-off data embedded).
      4. `grep -c '10:24\|Phase-86 post-audit' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` >= 1 (delta vs honest baseline).
      5. `grep -c -E '0\.88|88\.88' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` >= 1 (JaCoCo gate recorded).
      6. Final git-diff invariants: `git diff --stat src/main/resources/application.yml src/main/resources/application-dev.yml src/main/resources/application-local.yml src/main/resources/application-docker.yml src/main/resources/application-prod.yml src/main/java/org/ctc/backup/service/BackupStagingCleanup.java src/main/java/org/ctc/backup/lock/ImportLockService.java` reports ZERO changes across the entire Phase 89 branch (cumulative D-14 invariant + Task-4-test-scope invariant).
      7. Aggregator output captured: `test -f /tmp/89-03-top5.txt` AND `wc -l /tmp/89-03-top5.txt` > 5 (header line + at least 5 cluster lines).
      8. No new SpotBugs: SpotBugs report shows 0 `<BugInstance>` entries OR the gate exit 0 during Task 1's verify proves invariant held.
    </automated>
  </verify>
  <done>
    Phase 89 closed: `89-03-SUMMARY.md` exists with Wave-4 numbers + Top-5 cluster output + invariants checklist + Phase-90 / Phase-91 hand-off pointers; `application*.yml` + `BackupStagingCleanup.java` + `ImportLockService.java` are git-clean across the entire Phase 89 branch (D-14 invariant); JaCoCo ≥ 0.8888 held; SpotBugs BugInstance = 0 held.
  </done>
</task>

</tasks>

<threat_model>
threats="LOW — measurement + documentation phase; no source code change, no test change, no config change beyond docs. Markdown documents only."

mitigation="The 3-run Wave-4 measurement is observational (D-02 honest reporting, no hard local gate). Production behavior is unchanged across all of Phase 89 (D-14 invariant verified cumulatively in Task 4). `docs/test-performance.md` + `README.md` are markdown documents under version control — no executable content. The `aggregate-fingerprints.sh` invocation in Task 4 reads `target/test-perf/` only and writes `/tmp/89-03-top5.txt` (scratch). No external service interaction, no auth surface, no DB schema."

stride_register="
| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-89-03-01 | Information Disclosure | Wave-4 measurement numbers in docs/test-performance.md | accept | Numbers are wallclock + context-load counts + coverage ratios — no secrets, no PII; standard CI/CD telemetry already committed in v1.11 docs. |
| T-89-03-02 | Tampering | README.md and docs/test-performance.md prose | accept | Plain markdown edits committed through standard git workflow; no executable surface. |
| T-89-03-03 | DoS | Three back-to-back full `./mvnw clean verify -Pe2e` runs stress dev machine | accept | Phase-86 D-09 protocol already validated; user controls when to run (Task 1 is `checkpoint:human-verify`). |
"
</threat_model>

<verification>
After all 4 tasks complete:
- 3 Wave-4 runs each exited 0 with PERF-02 active proof + JaCoCo ≥ 0.8888 (Task 1).
- `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` populated with table + median + delta vs. 10:24 + JaCoCo note (Task 2).
- `docs/test-performance.md § v1.12 Forward Path` Lever-1 row marked DONE (Task 2).
- `README.md § Test Performance` section added directly after `## Documentation` (Task 3).
- `89-03-SUMMARY.md` records numbers + Top-5 cluster + invariants checklist (Task 4).
- Cumulative D-14 invariant: `application.yml`, `application-*.yml`, `BackupStagingCleanup.java`, `ImportLockService.java` are git-clean across Phase 89.
</verification>

<success_criteria>
- 3 idle-protocol runs of `./mvnw clean verify -Pe2e` recorded with Maven Total time + bash `real` + context-load count + JaCoCo ratio per run.
- Median Maven Total time + median bash `real` + delta vs. 10:24 Phase-86 post-audit baseline documented in `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)`.
- `§ v1.12 Forward Path` Lever-1 carries `DONE (Phase 89)` annotation + status paragraph cross-referencing `§ Post-Optimization Wallclock (Wave 4)` and `§ PERF-02 Forensics`.
- `README.md § Test Performance` pointer paragraph exists directly after `## Documentation`.
- JaCoCo line coverage ≥ 0.8888 held on all 3 runs (D-15 invariant).
- SpotBugs `BugInstance` count remains 0 (D-15 invariant, gate held during `./mvnw verify`).
- Production yml files + `BackupStagingCleanup.java` + `ImportLockService.java` cumulatively git-clean across Phase 89 (D-14 + Task-4-test-scope invariants).
- `89-03-SUMMARY.md` exists; Phase 90 PERF-03 Top-5 cluster hand-off data captured.
</success_criteria>

<output>
Create `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` per the standard SUMMARY template, including the Wave-4 numbers table, the embedded Top-5 cluster output from `aggregate-fingerprints.sh`, the invariants checklist, and the hand-off pointers to Phase 90 PERF-03 and Phase 91 PERF-06.
</output>
