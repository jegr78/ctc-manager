---
phase: 79
plan: 07
type: execute
wave: 7
depends_on: [79-03, 79-04, 79-05, 79-06]
files_modified:
  - .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md
  - .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VERIFICATION.md
autonomous: true
requirements: [D-06, D-18, D-19]

must_haves:
  truths:
    - "Final wallclock measurement recorded in `79-AUTO-UAT.md`; final ≤ baseline × 0.7 (D-06 ≥ 30% reduction)"
    - "JaCoCo line coverage ≥ 0.82 verified at the post-cleanup HEAD (D-18)"
    - "`./mvnw verify -Pe2e` BUILD SUCCESS on H2/dev profile (D-19 final gate)"
    - "Reduction-vs-target verdict line in `79-AUTO-UAT.md` reads either `MEETS ≥30% D-06 threshold` or `DOES NOT MEET — escalate as 79.X`"
    - "`79-VERIFICATION.md` final-gate evidence section records the git SHA, JaCoCo %, and BUILD SUCCESS attestation"
  artifacts:
    - path: ".planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md"
      provides: "Final wallclock row populated; reduction verdict line"
      contains: "Final.*after D-05"
    - path: ".planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VERIFICATION.md"
      provides: "Final-gate evidence + JaCoCo % + BUILD SUCCESS attestation"
      contains: "BUILD SUCCESS"
  key_links:
    - from: "79-AUTO-UAT.md baseline row (Plan 01)"
      to: "79-AUTO-UAT.md final row (this plan)"
      via: "same git-SHA-anchored measurement command"
      pattern: "Baseline.*Final"
    - from: "JaCoCo report at target/site/jacoco/index.html"
      to: "79-VERIFICATION.md percentage row"
      via: "manual extract from `jacoco.csv` LINE coverage"
      pattern: "LINE.*0\\.[8-9]"
---

<objective>
Wave 7 of Phase 79: collect the final-state measurements and write the evidence files for D-06 (≥ 30% wallclock reduction), D-18 (JaCoCo ≥ 0.82), and D-19 (`./mvnw verify -Pe2e` BUILD SUCCESS). This is the verification wave — no source-code or config edits, only measurement + reporting.

Purpose: D-06 is the only Phase 79 success criterion that requires post-cleanup measurement; D-18 verifies the cleanup did not regress coverage; D-19 is the final phase gate. All three are quantitative.

If D-06 is NOT met (final &gt; baseline × 0.7), this plan flags `DOES NOT MEET — escalate as 79.X`. The orchestrator decides whether to escalate (tune `forkCount=2.5C` Surefire or re-audit) or accept the partial reduction.

Output: 2 docs-commits — one for the AUTO-UAT final-row + verdict, one for the VERIFICATION final-gate evidence.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VALIDATION.md
@CLAUDE.md

<interfaces>
**Final wallclock measurement command (verbatim from Plan 01 baseline):**

```
time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true
```

Same command, same flags as the baseline — only difference is the codebase state (post-Wave-2/3/4 cleanup + parallelism).

**Reduction formula (D-06):** `(baseline - final) / baseline ≥ 0.30` → MEETS. Else → DOES NOT MEET.

**JaCoCo % extraction:**

After `./mvnw verify` runs, JaCoCo writes `target/site/jacoco/jacoco.csv`. Extract LINE coverage:

```
awk -F, 'NR>1 {covered+=$5; missed+=$4} END {pct=covered/(covered+missed); printf "%.4f\n", pct}' target/site/jacoco/jacoco.csv
```

OR more readable from `target/site/jacoco/index.html` (search for "Total" row, `Lines` column).

Target: ≥ 0.82 per D-18 + CLAUDE.md §"Constraints" minimum.

**79-AUTO-UAT.md update plan:** The file already has a `## Wallclock Baseline` section with a baseline row + a placeholder Final row + `Target: Final ≤ Baseline × 0.7 (D-06)` line. This plan:
1. Populates the Final row with: measurement timestamp, git SHA (HEAD), exact command, duration.
2. Adds a `## Reduction Verdict` section below with the math (baseline - final) / baseline = X% AND the verdict line.
3. Adds (if not already present from Plan 03) the `## Intermediate Measurements` subsection with the with-parallel-pre-config-cleanup datapoint.

**79-VERIFICATION.md target structure (new file, append-only if exists):**

```markdown
# Phase 79 — Verification (Final Gate)

## Decision verifications

| Decision | Behavior | Status | Evidence |
|---|---|---|---|
| D-06 | Final wallclock ≤ baseline × 0.7 | <PASS / FAIL> | See 79-AUTO-UAT.md `## Reduction Verdict` |
| D-18 | JaCoCo line coverage ≥ 0.82 | <PASS / FAIL> | <X.XXXX> at <git_sha> (see target/site/jacoco/index.html) |
| D-19 | `./mvnw verify -Pe2e` BUILD SUCCESS | <PASS / FAIL> | Local run at <git_sha> on <date> |

## Final-gate command

```
time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true
```

**Result:** <BUILD SUCCESS / BUILD FAILURE>
**Duration:** <Xm Ys>
**Git SHA:** <abc1234>
**Date:** <YYYY-MM-DD>

## Schutzwortliste invariant (cumulative across all Wave-2 commits)

```
git diff $(git merge-base HEAD origin/master) HEAD -- src/ pom.xml .github/workflows/ci.yml | \
  grep '^-' | \
  grep -E "MariaDB|H2|JEP|CVE|Lombok|OSIV|Unsafe|pitfall|auditing|AuditingEntityListener|TODO|HACK|WORKAROUND|FIXME|deadlock|transitiv|transitive|auto-commit|race|thread-safe"
```

**Result:** <ZERO matches / N matches at lines ...>

## Flyway invariant

```
git diff $(git merge-base HEAD origin/master) HEAD -- src/main/resources/db/migration/
```

**Result:** <empty / non-empty (this is a failure)>

## mariadb-migration-smoke.yml invariant

```
git diff $(git merge-base HEAD origin/master) HEAD -- .github/workflows/mariadb-migration-smoke.yml
```

**Result:** <empty / non-empty (this is a failure)>
```
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- The final `./mvnw verify -Pe2e` invocation MUST run on a CLEAN tree (no uncommitted source edits) per D-19. If the working tree is dirty → STOP / `NEEDS_CONTEXT`.
- Schutzwortliste (D-13): N/A — this plan adds docs, no source comments touched.
- Dead-code rule (D-04): N/A — docs-only.
- D-06 verdict (MEETS / DOES NOT MEET) is the orchestrator's signal for Wave 8 vs. an in-Phase-79 escalation step. State it explicitly.
</critical_constraints>

<test_impact>
N/A — measurement + docs only. No Java/XML/YAML source edited. The `./mvnw verify -Pe2e` run is the measurement itself; if it fails, this plan reports the failure but does NOT attempt to fix it (escalation back to the orchestrator).
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Verify clean tree + run final ./mvnw verify -Pe2e + extract JaCoCo %</name>
  <files>(measurement only — no file edits in this task)</files>
  <read_first>
    - `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md` (baseline row + Target formula)
    - 79-RESEARCH.md §"Wallclock Baseline Measurement" (command shape) + §"Phase Requirements → Test Map" (D-06 / D-18 / D-19 rows)
  </read_first>
  <action>
1. **Clean-tree verification:** `git status --short`. Must show NO uncommitted files. If dirty → STOP / `NEEDS_CONTEXT` ("dirty tree blocks final measurement per D-19").

2. **Capture HEAD SHA:** `HEAD_SHA=$(git rev-parse --short HEAD)`. Save to a temp note.

3. **Capture baseline data:** Read the baseline row from `79-AUTO-UAT.md` `## Wallclock Baseline` table — extract baseline SHA, baseline duration. Save to a temp note.

4. **Run final-gate measurement:** Execute:
```
time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true 2>&1 | tee /tmp/79-final-build.log
```

Wait for completion. Extract from the log:
- `BUILD SUCCESS` or `BUILD FAILURE` (last 5 lines)
- `real Xm Ys` from the `time` output (last line)

5. **Extract JaCoCo %:** After `verify` completes, run:
```
awk -F, 'NR>1 {covered+=$5; missed+=$4} END {pct=covered/(covered+missed); printf "%.4f\n", pct}' target/site/jacoco/jacoco.csv
```
Save the percentage. Also note from `target/site/jacoco/index.html` the human-readable percentage and the missed-instructions count.

6. **Compute reduction:** Reduction = (baseline_duration_seconds - final_duration_seconds) / baseline_duration_seconds. Express as percentage. Verdict: ≥ 30% → MEETS. Else → DOES NOT MEET.

7. **Save measurements to a temp note** for use in Tasks 2 and 3. DO NOT edit any tracked file in this task.
  </action>
  <verify>
    <automated>git status --short | wc -l | grep -q "^0$" &amp;&amp; test -f target/site/jacoco/jacoco.csv &amp;&amp; tail -10 /tmp/79-final-build.log | grep -q "BUILD SUCCESS"</automated>
  </verify>
  <acceptance_criteria>
    - Working tree was clean before measurement
    - `./mvnw clean verify -Pe2e` BUILD SUCCESS
    - `target/site/jacoco/jacoco.csv` exists
    - JaCoCo % computed and ≥ 0.82
    - Baseline duration extracted from 79-AUTO-UAT.md
    - Final duration extracted from `time` output
    - Reduction percentage computed and verdict assigned
  </acceptance_criteria>
  <done>Measurement complete; data captured in temp notes for Tasks 2-3.</done>
</task>

<task type="auto">
  <name>Task 2: Update 79-AUTO-UAT.md with final row + reduction verdict (1 commit)</name>
  <files>.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md</files>
  <read_first>
    - Current `79-AUTO-UAT.md` (Plan 01 baseline + Plan 03 intermediate measurements if any)
    - Temp notes from Task 1 (baseline duration, final duration, reduction %, verdict, HEAD SHA)
  </read_first>
  <action>
1. Replace the placeholder Final row in `## Wallclock Baseline` with the actual measurement (HEAD SHA, command, duration, today's date).

2. After the `## Wallclock Baseline` section, append (or replace if it already exists from Plan 03 as Intermediate) a `## Reduction Verdict` section:

```markdown
## Reduction Verdict

| Metric | Value |
|---|---|
| Baseline duration | `<Xm Ys>` (at git SHA `<baseline_sha>`) |
| Final duration    | `<Ym Zs>` (at git SHA `<HEAD_SHA>`) |
| Absolute reduction | `<Δm Δs>` |
| Percentage reduction | `<NN.N%>` |
| D-06 threshold | ≥ 30% |
| **Verdict** | **<MEETS ≥30% D-06 threshold | DOES NOT MEET — escalate as 79.X>** |

If MEETS: Phase 79 advances to Wave 8 (`/gsd-audit-milestone v1.10`).
If DOES NOT MEET: orchestrator decides whether to tune `forkCount=2.5C` Surefire (RESEARCH §3 syntax reference allows this) and re-measure, OR accept the partial reduction and continue to Wave 8 with documented gap.
```

3. Stage `git add .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md`. Commit:
```
docs(79): record final wallclock + reduction verdict (D-06)

- Final: <Ym Zs> at HEAD <HEAD_SHA>
- Baseline: <Xm Ys> at <baseline_sha>
- Reduction: <NN.N%> (<MEETS / DOES NOT MEET> ≥30% D-06 threshold)
```
  </action>
  <verify>
    <automated>grep -q "## Reduction Verdict" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md &amp;&amp; grep -qE "MEETS|DOES NOT MEET" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md &amp;&amp; git log -1 --pretty=%B | grep -q "docs(79): record final wallclock"</automated>
  </verify>
  <acceptance_criteria>
    - `## Reduction Verdict` section present
    - Final row populated with actual HEAD SHA + duration + date
    - Verdict line is one of: `MEETS ≥30% D-06 threshold` or `DOES NOT MEET — escalate as 79.X`
    - Commit `docs(79): record final wallclock + reduction verdict (D-06)` lands
  </acceptance_criteria>
  <done>AUTO-UAT updated + committed; verdict is explicit.</done>
</task>

<task type="auto">
  <name>Task 3: Create 79-VERIFICATION.md final-gate evidence + invariants (1 commit)</name>
  <files>.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VERIFICATION.md</files>
  <read_first>
    - Temp notes from Task 1 (HEAD SHA, JaCoCo %, BUILD result)
    - This plan's `<interfaces>` 79-VERIFICATION.md template
    - `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md` (just-committed by Task 2)
  </read_first>
  <action>
1. Create `79-VERIFICATION.md` per the `<interfaces>` template. Populate:

   - **D-06 row:** Status = MEETS / DOES NOT MEET per Task 2 verdict; Evidence = "See 79-AUTO-UAT.md `## Reduction Verdict`"
   - **D-18 row:** Status = PASS if JaCoCo % ≥ 0.82, else FAIL; Evidence = `<X.XXXX>` (e.g., `0.8534`) at git SHA `<HEAD_SHA>`
   - **D-19 row:** Status = PASS (Task 1 confirmed BUILD SUCCESS); Evidence = "Local run at `<HEAD_SHA>` on `<YYYY-MM-DD>`"

2. **Run the Schutzwortliste invariant grep:**
```
git diff $(git merge-base HEAD origin/master) HEAD -- src/ pom.xml .github/workflows/ci.yml | \
  grep '^-' | \
  grep -E "MariaDB|H2|JEP|CVE|Lombok|OSIV|Unsafe|pitfall|auditing|AuditingEntityListener|TODO|HACK|WORKAROUND|FIXME|deadlock|transitiv|transitive|auto-commit|race|thread-safe" | head -20
```
Record the result (expected: ZERO matches). If non-zero → DOCUMENT in `79-VERIFICATION.md` (do NOT undo — that's the orchestrator's call), and flag this as a Wave 2 cleanup gap.

3. **Run the Flyway invariant grep:**
```
git diff $(git merge-base HEAD origin/master) HEAD -- src/main/resources/db/migration/
```
Record. Expected: empty.

4. **Run the mariadb-migration-smoke.yml invariant grep:**
```
git diff $(git merge-base HEAD origin/master) HEAD -- .github/workflows/mariadb-migration-smoke.yml
```
Record. Expected: empty.

5. Stage `git add .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VERIFICATION.md`. Commit:
```
docs(79): final-gate verification evidence (D-18, D-19, Schutzwortliste/Flyway/mariadb-smoke invariants)

- D-06: <MEETS / DOES NOT MEET> (see 79-AUTO-UAT.md)
- D-18 (JaCoCo): <X.XXXX> ≥ 0.82 PASS at HEAD <HEAD_SHA>
- D-19 (verify -Pe2e): BUILD SUCCESS at HEAD <HEAD_SHA>
- Schutzwortliste invariant: <ZERO matches | N matches — see 79-VERIFICATION.md>
- Flyway invariant: <empty | non-empty>
- mariadb-migration-smoke.yml invariant: <empty | non-empty>
```
  </action>
  <verify>
    <automated>test -f .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VERIFICATION.md &amp;&amp; grep -q "## Decision verifications" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VERIFICATION.md &amp;&amp; grep -qE "D-18.*PASS|D-18.*FAIL" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VERIFICATION.md &amp;&amp; git log -1 --pretty=%B | grep -q "docs(79): final-gate verification evidence"</automated>
  </verify>
  <acceptance_criteria>
    - 79-VERIFICATION.md created with `## Decision verifications` table (3 rows for D-06, D-18, D-19)
    - D-18 status = PASS (JaCoCo ≥ 0.82)
    - D-19 status = PASS (BUILD SUCCESS)
    - Final-gate command + duration + HEAD SHA recorded
    - Schutzwortliste / Flyway / mariadb-smoke invariants recorded (each with result)
    - Commit `docs(79): final-gate verification evidence` lands
  </acceptance_criteria>
  <done>VERIFICATION.md created + committed; all 3 final-gate decisions have explicit pass/fail evidence.</done>
</task>

</tasks>

<verification>
- 2 atomic docs-commits land on `gsd/v1.10-platform-and-backup` (`docs(79): record final wallclock` + `docs(79): final-gate verification evidence`)
- 79-AUTO-UAT.md has populated Final row + Reduction Verdict
- 79-VERIFICATION.md has D-06/D-18/D-19 + invariants
- `./mvnw verify -Pe2e` BUILD SUCCESS at HEAD
- JaCoCo ≥ 0.82
</verification>

<success_criteria>
- 2 atomic commits land
- D-06 verdict explicit (MEETS / DOES NOT MEET — orchestrator-actionable)
- D-18 (JaCoCo ≥ 0.82) PASS recorded
- D-19 (BUILD SUCCESS) PASS recorded
- Schutzwortliste / Flyway / mariadb-smoke invariants recorded
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-07-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: both commit SHAs, baseline / final / reduction numbers, JaCoCo %, D-06 verdict, invariant-check results.
</output>
