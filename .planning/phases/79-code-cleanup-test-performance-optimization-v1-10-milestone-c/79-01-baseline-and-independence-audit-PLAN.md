---
phase: 79
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md
  - .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md
autonomous: true
requirements: [D-05, D-06]

must_haves:
  truths:
    - "A reproducible wallclock baseline for `./mvnw clean verify -Pe2e` is recorded with git SHA + date"
    - "All ~1200 unit tests pass when executed in reverse-alphabetical Surefire order"
    - "All unit tests pass with three distinct random seeds (1234, 5678, 9999)"
    - "All integration tests pass when Failsafe executes in reverse-alphabetical order"
    - "The ACTUAL `@DirtiesContext` count in `src/test/` is measured (Task 3 pre-flight grep), recorded in 79-INDEPENDENCE-AUDIT.md, and every annotation found is documented with a KEEP-mandatory verdict (per RESEARCH §`@DirtiesContext Audit Decision Tree`; RESEARCH expected 10, but CONTEXT.md §`Code Context` mentioned 13 — reality-check resolves the discrepancy)"
  artifacts:
    - path: ".planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md"
      provides: "Wallclock baseline row + placeholder for final-measurement row"
      contains: "## Wallclock Baseline"
    - path: ".planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md"
      provides: "Per-annotation @DirtiesContext audit verdict + reverse-order + random-seed run records + measured-vs-expected count reconciliation"
      contains: "@DirtiesContext Audit"
  key_links:
    - from: "79-AUTO-UAT.md"
      to: "Plan 07 (final wallclock comparison)"
      via: "baseline row that Plan 07 will diff against"
      pattern: "Baseline.*time.*verify"
    - from: "79-INDEPENDENCE-AUDIT.md"
      to: "Plan 03 (parallelization gate)"
      via: "all-green attestation that gates Surefire forkCount=2C / Failsafe forkCount=1C"
      pattern: "all.*green"
---

<objective>
Wave 1 of Phase 79: establish the reproducible wallclock baseline for the v1.10 milestone-closer test suite AND prove test-independence under reverse-order + random-seed execution BEFORE any parallelization change lands. Per D-05 sequence rationale: parallelization atop unverified independence yields false-positive regressions that look like configuration bugs but are actually test-isolation bugs. This plan is the gate for Wave 3 (Plan 03 parallelization).

Purpose: Without a measured baseline, D-06's "≥ 30% wallclock reduction" cannot be evaluated. Without an independence attestation, Plan 03's `forkCount=2C` change is unreviewable. Both artifacts unblock the rest of the phase.

Output:
- `79-AUTO-UAT.md` with a populated `## Wallclock Baseline` table (git SHA, command, duration, date)
- `79-INDEPENDENCE-AUDIT.md` with reverse-order run log + 3 random-seed run logs + per-annotation `@DirtiesContext` verdict table (ALL annotations found by the Task 3 pre-flight grep — RESEARCH expects 10; CONTEXT.md mentioned 13. The pre-flight grep determines the actual number and the table covers ALL of them — no annotation may go unaudited.)
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-VALIDATION.md
@CLAUDE.md

<interfaces>
<!-- Key facts the executor needs verbatim. No exploration required. -->

**RESEARCH-expected 10 `@DirtiesContext` annotations (zero removals — all KEEP per RESEARCH §"@DirtiesContext Audit Decision Tree"):**

> NOTE: CONTEXT.md §`Code Context` previously cited "13 @DirtiesContext annotations" as a starting count. RESEARCH narrowed the audit to the 10 below after deduplication. Task 3 starts with a `grep -rc "@DirtiesContext" src/test/` reality-check — the ACTUAL count in the codebase is the source of truth. If `> 10`, the additional annotations MUST be audited and added to the table; if `< 10` or `== 10`, the RESEARCH baseline holds. The expected count `10` is documented here as the prior, not as a ceiling.

| # | Test class | Annotation form | Rationale (one-line) |
|---|-----------|----------------|---------------------|
| 1 | `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | `BlockingRestoreFailureInjector.Config` exposes a non-resettable `CountDownLatch`; new context per method is the only reset path. |
| 2 | `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | Same CountDownLatch rationale; comment in source explains it. |
| 3 | `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | Same CountDownLatch rationale. |
| 4 | `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` | `@DirtiesContext` | `siteProperties.setOutputDir(tempDir.toString())` mutates `SiteProperties` `@ConfigurationProperties` singleton. |
| 5 | `src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation. |
| 6 | `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation. |
| 7 | `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java` | `@DirtiesContext` | `siteGeneratorService.setOutputDir(...)` + `siteProperties.setLinks(...)` mutate singletons. |
| 8 | `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation. |
| 9 | `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation. |
| 10 | `src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java` | `@DirtiesContext` | Same `SiteProperties.outputDir` mutation. |

**Pre-flight reality-check grep (Task 3, MANDATORY):**

```
grep -rc "@DirtiesContext" src/test/ | grep -v ':0$'
TOTAL=$(grep -r "@DirtiesContext" src/test/ | grep -v '^\s*//\|^\s*\*' | wc -l)
echo "TOTAL @DirtiesContext annotations (non-comment): $TOTAL"
```

The `TOTAL` value is the source of truth for the audit table size. Branching:
- `TOTAL == 10` → audit the 10 rows above verbatim; verdict matches RESEARCH expectation.
- `TOTAL > 10` → audit the 10 known rows PLUS the additional `TOTAL - 10` annotations. Locate each extra annotation via `grep -rn "@DirtiesContext" src/test/`, read its source-file context, and record a KEEP-or-REMOVE verdict in the same table. CONTEXT.md §`Code Context` cited 13 — if `TOTAL == 13`, expect 3 extras.
- `TOTAL < 10` → some of the 10 RESEARCH rows have been removed since RESEARCH was authored. Record the missing rows in the table with `Verdict: ALREADY REMOVED — n/a` and explain how (likely Wave 1 of an earlier hotfix).

Commands the executor must run verbatim:

Reverse-order Surefire unit run:
`./mvnw test -Dsurefire.runOrder=reversealphabetical -Dspring.profiles.active=dev`

Random-seed unit runs (3 distinct seeds):
`./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 -Dspring.profiles.active=dev`
`./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678 -Dspring.profiles.active=dev`
`./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999 -Dspring.profiles.active=dev`

Reverse-order Failsafe IT run:
`./mvnw verify -Dsurefire.runOrder=reversealphabetical -Dfailsafe.runOrder=reversealphabetical -Dspring.profiles.active=dev -Ddocker.available=true`

Wallclock baseline command:
`time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true`

Git SHA capture: `git rev-parse --short HEAD`
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After each per-package commit, run `./mvnw test` and verify it passes BEFORE moving to the next package.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04): Only delete when (a) IDE + grep find zero references AND (b) no Spring/JPA/Jackson lifecycle annotation AND (c) not a JPA no-arg constructor / Jackson public setter. Reflection-invoked methods survive automatically by this rule. On uncertainty → leave it.
- Task 3 reality-check is MANDATORY. The `@DirtiesContext` audit table covers ALL annotations found in `src/test/` by the pre-flight grep — no annotation may go unaudited because RESEARCH "only expected 10".
</critical_constraints>

<test_impact>
N/A — this plan does NOT delete or rename any production symbol. It only RUNS tests and records measurements. No source-code edit (Java/XML/YAML) is performed. Only `.planning/phases/79-.../*.md` files are written.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Record wallclock baseline in 79-AUTO-UAT.md</name>
  <files>.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md</files>
  <read_first>
    - .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md §"Wallclock Baseline Measurement" (output-format template + command shape)
    - 79-RESEARCH.md §"Open Questions" Q1 (local-vs-CI note — local measurement first per recommendation)
  </read_first>
  <action>
Create `.planning/phases/79-.../79-AUTO-UAT.md` with the H1 `# Phase 79 — Auto UAT` and a `## Wallclock Baseline` section. Run `git rev-parse --short HEAD` to capture the current SHA. Run `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true` ONCE on a clean working tree (no uncommitted Java edits) and capture the final `real Xm Ys` line from the time output. Populate the baseline row of the table per the RESEARCH §"Wallclock Baseline Measurement" output-format template (columns: Measurement, Git SHA, Invocation, Duration, Date). Add a placeholder row for the final measurement that Plan 07 will fill ("Final (after D-05) | _TBD by Plan 07_ | _same command_ | _TBD_ | _TBD_"). End the section with the formula line: "Target: Final ≤ Baseline × 0.7 (D-06)". Do NOT include `--offline` (RESEARCH §"Wallclock Baseline Measurement" rejects it explicitly; use `--no-transfer-progress` instead). If `./mvnw clean verify -Pe2e` fails (RED), STOP — Wave 1 cannot proceed on a red baseline; report `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>test -f .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md &amp;&amp; grep -q "## Wallclock Baseline" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md &amp;&amp; grep -Eq "Baseline.*\| \`[a-f0-9]{7,}\`" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md</automated>
  </verify>
  <acceptance_criteria>
    - 79-AUTO-UAT.md exists with H1 `# Phase 79 — Auto UAT`
    - `## Wallclock Baseline` heading present
    - Baseline row contains a 7+ character git SHA, exact command string, and `Xm Ys` duration
    - Placeholder row for final measurement is present
    - "Target: Final ≤ Baseline × 0.7" formula line is present
    - The `./mvnw clean verify -Pe2e` invocation that produced the baseline was BUILD SUCCESS
  </acceptance_criteria>
  <done>Baseline row populated with real measurement; `./mvnw clean verify -Pe2e` was BUILD SUCCESS at the captured SHA.</done>
</task>

<task type="auto">
  <name>Task 2: Run reverse-order + random-seed independence audits and record results</name>
  <files>.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md</files>
  <read_first>
    - 79-RESEARCH.md §"`runOrder` for Independence Audit" (commands + 3 random seeds)
    - 79-VALIDATION.md table rows for D-05 Wave 1
  </read_first>
  <action>
Create `.planning/phases/79-.../79-INDEPENDENCE-AUDIT.md` with H1 `# Phase 79 — Test Independence Audit (D-05 Wave 1)`. Add a `## Surefire reverse-order run` section, execute `./mvnw test -Dsurefire.runOrder=reversealphabetical -Dspring.profiles.active=dev`, and record: command, duration, BUILD SUCCESS/FAILURE, tests-run count, failures-count. If RED → STOP and report `NEEDS_CONTEXT` (independence audit must be clean before Wave 3 parallelization). Add a `## Surefire random-seed runs` section with three sub-tables (seeds 1234, 5678, 9999), executing each in turn: `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=&lt;SEED&gt; -Dspring.profiles.active=dev` and recording the same five columns. All three random-seed runs MUST be BUILD SUCCESS — any RED → STOP / `NEEDS_CONTEXT`. Add a `## Failsafe reverse-order run` section, execute `./mvnw verify -Dsurefire.runOrder=reversealphabetical -Dfailsafe.runOrder=reversealphabetical -Dspring.profiles.active=dev -Ddocker.available=true`, record the same five columns. Same RED-STOP rule. Conclude with a `## Verdict` line: "Independence audit GREEN on reverse-order + 3 random seeds + Failsafe reverse-order. Parallelization (Plan 03) is cleared to proceed."
  </action>
  <verify>
    <automated>test -f .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -q "## Surefire reverse-order run" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -q "seed=1234" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -q "seed=5678" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -q "seed=9999" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -Eq "## Verdict.*GREEN|Independence audit GREEN" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md</automated>
  </verify>
  <acceptance_criteria>
    - 79-INDEPENDENCE-AUDIT.md exists with the four required sections (Surefire reverse-order, Surefire random-seed × 3, Failsafe reverse-order, Verdict)
    - Each section records: command, duration, BUILD SUCCESS, tests-run count, failures = 0
    - All 5 runs (1 reverse + 3 random + 1 Failsafe-reverse) are BUILD SUCCESS
    - Verdict line states GREEN
  </acceptance_criteria>
  <done>All independence runs GREEN; verdict line records the clearance for Plan 03.</done>
</task>

<task type="auto">
  <name>Task 3: Append @DirtiesContext audit verdict (reality-check + verdict per annotation) to 79-INDEPENDENCE-AUDIT.md</name>
  <files>.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md</files>
  <read_first>
    - 79-RESEARCH.md §"@DirtiesContext Audit Decision Tree" (10-row verdict table — RESEARCH expectation)
    - 79-CONTEXT.md §"Code Context" (mentions "13 @DirtiesContext annotations")
    - The source files listed in `<interfaces>` above (read for confirm-only; do NOT modify)
  </read_first>
  <action>
**Step 1 — Reality-check grep (MANDATORY, run BEFORE writing the audit table):**

Run the pre-flight grep from `<interfaces>`:
```
grep -rn "@DirtiesContext" src/test/ > /tmp/79-dirtiescontext-raw.txt
grep -v '^\s*//\|^\s*\*' /tmp/79-dirtiescontext-raw.txt > /tmp/79-dirtiescontext-effective.txt
TOTAL=$(wc -l < /tmp/79-dirtiescontext-effective.txt)
echo "TOTAL effective (non-comment) @DirtiesContext annotations: $TOTAL"
echo "Per-file:"
awk -F: '{print $1}' /tmp/79-dirtiescontext-effective.txt | sort -u | xargs -I{} sh -c 'echo "  {} : $(grep -c "@DirtiesContext" {})"'
```

Record the `TOTAL` value. This is the SOURCE-OF-TRUTH count for the audit table size.

**Step 2 — Branch on reality-check result:**

- **If `TOTAL == 10`:** RESEARCH baseline holds. The 10 rows in `<interfaces>` cover everything. Proceed to Step 3 (write the 10-row table verbatim).

- **If `TOTAL > 10` (e.g., `TOTAL == 13` matching CONTEXT.md):** RESEARCH undercounted. For each annotation NOT in the `<interfaces>` 10-row table:
  1. Locate it: `grep -n "@DirtiesContext" <file>` to get the line number.
  2. Read the surrounding 20 lines of context: `sed -n '<line-10>,<line+10>p' <file>`.
  3. Classify the rationale (e.g., singleton mutation / non-resettable resource / cargo-cult / unknown).
  4. Add a new row to the audit table with: file path, annotation form, one-line rationale, verdict (KEEP-mandatory if a singleton/resource is mutated; REMOVE-CANDIDATE if no clear rationale — flag for explicit Wave-2 cleanup OR conservative KEEP under D-04 "on uncertainty → leave it").

- **If `TOTAL < 10`:** Some of the 10 RESEARCH rows no longer exist in the codebase. For each missing row, add a verdict `ALREADY REMOVED — n/a` with a one-line explanation of likely cause (e.g., "deleted in hotfix XYZ").

**Step 3 — Write the audit table:**

Append a `## @DirtiesContext Audit (CD-04)` section to `79-INDEPENDENCE-AUDIT.md`. Open with:
```
**Reality-check (pre-flight grep):** TOTAL effective `@DirtiesContext` annotations in `src/test/` = <TOTAL>.
- RESEARCH expectation: 10
- CONTEXT.md §`Code Context` prior: 13
- Measured: <TOTAL>
- Discrepancy resolution: <verbatim explanation based on the Step-2 branch taken>
```

Then a Markdown table with columns `# | Test class | Annotation form | Rationale | Verdict | Source-of-truth grep line`. The table MUST have exactly `<TOTAL>` rows (or `max(TOTAL, 10)` if RESEARCH rows were removed and need a placeholder).

For ImportConcurrentLockIT/ImportLockBannerAdviceIT/ImportLockedPostRejectorIT: verdict = "KEEP — non-resettable CountDownLatch".
For the 7 sitegen tests: verdict = "KEEP — SiteProperties.outputDir singleton mutation".
For any extra annotations (if `TOTAL > 10`): verdict per Step 2 classification.

Close with:
```
Per CD-04 discretion area: <X> KEEP-mandatory annotations, <Y> REMOVE-CANDIDATE (deferred / handled in Wave 2), <Z> ALREADY REMOVED (n/a). The remaining `@DirtiesContext` annotations are unchanged by this plan. This audit document is the verification that no defensive-cargo annotations were inflating the suite. Plan 03 parallelization is unaffected by these annotations (they continue to function under `reuseForks=true`).
```

Fill in `<X>`, `<Y>`, `<Z>` from the table.
  </action>
  <verify>
    <automated>grep -q "## @DirtiesContext Audit" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -Eq "TOTAL effective .@DirtiesContext. annotations in .src/test/. = [0-9]+" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -q "ImportConcurrentLockIT" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -q "SiteGeneratorE2ETest" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md &amp;&amp; grep -Eq "Measured: [0-9]+" .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md</automated>
  </verify>
  <acceptance_criteria>
    - Pre-flight grep was executed and TOTAL was captured
    - `## @DirtiesContext Audit (CD-04)` section appended to 79-INDEPENDENCE-AUDIT.md
    - Header includes the measured TOTAL count (numeric, from the pre-flight grep)
    - Header explicitly reconciles RESEARCH-expected (10), CONTEXT.md-cited (13), and Measured value
    - Audit table has exactly `<TOTAL>` rows (or `max(TOTAL, 10)` with placeholders if rows were removed)
    - Every row has a Verdict column populated (KEEP / REMOVE-CANDIDATE / ALREADY REMOVED)
    - All 3 ImportLock*IT classes are present with CountDownLatch rationale (if still in src/test/)
    - All 7 sitegen tests are present with SiteProperties.outputDir rationale (if still in src/test/)
    - Closing paragraph counts <X> KEEP / <Y> REMOVE-CANDIDATE / <Z> ALREADY REMOVED summing to `<TOTAL>` (or rationalized count)
    - No source-code edits performed in this task
  </acceptance_criteria>
  <done>Reality-check executed; audit table covers ALL annotations found by the grep; verdict per annotation recorded; Plan 03 cleared.</done>
</task>

<task type="auto">
  <name>Task 4: Commit Wave 1 artifacts</name>
  <files>.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md, .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md</files>
  <read_first>
    - CLAUDE.md §"Git Workflow" (Conventional Commits prefix `docs` for docs-only commit)
  </read_first>
  <action>
Stage only the two artifacts: `git add .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-AUTO-UAT.md .planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-INDEPENDENCE-AUDIT.md`. Verify with `git status` that exactly these two files are staged (no source files). Create a single commit on the current branch `gsd/v1.10-platform-and-backup` with the message `docs(79): baseline wallclock + independence audit (D-05 Wave 1, D-06)`. Body lines:
- Baseline: <Xm Ys> at SHA <abc1234>
- Reverse-order + 3 random-seed Surefire runs GREEN
- Failsafe reverse-order GREEN
- @DirtiesContext audit: TOTAL=<N> measured, RESEARCH expected 10, CONTEXT.md cited 13, reconciliation in 79-INDEPENDENCE-AUDIT.md
- <X>/<N> verdicts = KEEP-mandatory (zero removals per CD-04 default)

Do NOT push. Do NOT switch branches. Do NOT run `git stash`/`reset`/`checkout`.
  </action>
  <verify>
    <automated>git log -1 --pretty=%B | grep -Eq "docs\(79\): baseline wallclock \+ independence audit"</automated>
  </verify>
  <acceptance_criteria>
    - Latest commit message matches the prefix `docs(79): baseline wallclock + independence audit`
    - Commit body contains the five bullet points (baseline duration + SHA, reverse-order GREEN, Failsafe-reverse GREEN, @DirtiesContext TOTAL + reconciliation, KEEP-count summary)
    - `git log --name-only -1` shows exactly the two `.planning/phases/79-...` Markdown files
    - Branch is still `gsd/v1.10-platform-and-backup`
  </acceptance_criteria>
  <done>Wave 1 artifacts committed on the correct branch; gate for Wave 3 (Plan 03) is now in place.</done>
</task>

</tasks>

<verification>
- `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true` was BUILD SUCCESS at the baseline SHA (recorded in 79-AUTO-UAT.md).
- `./mvnw test -Dsurefire.runOrder=reversealphabetical` and three `surefire.runOrder=random` seeds (1234/5678/9999) were all BUILD SUCCESS.
- `./mvnw verify -Dsurefire.runOrder=reversealphabetical -Dfailsafe.runOrder=reversealphabetical` was BUILD SUCCESS.
- 79-INDEPENDENCE-AUDIT.md `## @DirtiesContext Audit` table has `<TOTAL>` rows (TOTAL measured by Task 3 pre-flight grep); the discrepancy between RESEARCH (10), CONTEXT.md (13), and the measured value is reconciled in-document.
- `git log -1` shows the `docs(79): baseline wallclock + independence audit` commit on `gsd/v1.10-platform-and-backup`.
</verification>

<success_criteria>
- `79-AUTO-UAT.md` baseline row populated with git SHA + command + duration
- `79-INDEPENDENCE-AUDIT.md` contains: reverse-order Surefire run, 3 random-seed Surefire runs, Failsafe reverse-order run, `@DirtiesContext` reality-check + reconciliation + per-annotation verdict table, GREEN Verdict line
- Both files committed via `docs(79): baseline wallclock + independence audit (D-05 Wave 1, D-06)` on `gsd/v1.10-platform-and-backup`
- No production source files (Java, XML, YAML) were modified
- Plan 03 (parallelization) is now unblocked
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-01-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: measured baseline duration, all 5 independence-run verdicts, `@DirtiesContext` reality-check result (TOTAL value + reconciliation with RESEARCH/CONTEXT.md), KEEP/REMOVE-CANDIDATE/ALREADY-REMOVED counts, exact commit SHA.
</output>
