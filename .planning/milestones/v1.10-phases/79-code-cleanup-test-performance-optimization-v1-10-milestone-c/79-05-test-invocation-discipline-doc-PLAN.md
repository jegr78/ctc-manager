---
phase: 79
plan: 05
type: execute
wave: 5
depends_on: [79-03]
files_modified:
  - .planning/codebase/TESTING.md
autonomous: true
requirements: [D-08]

must_haves:
  truths:
    - "`.planning/codebase/TESTING.md` contains a new `## Test Invocation Discipline` section at the end of the document (before the closing `*Testing analysis: 2026-04-07*` line)"
    - "The new section codifies the `feedback_test_call_optimization` memory rule (one final `./mvnw verify -Pe2e` per phase, targeted `-Dtest`/`-Dit.test` between)"
    - "The section includes the run-order independence-verification idiom (`-Dsurefire.runOrder=reversealphabetical` + 3 random seeds 1234/5678/9999)"
  artifacts:
    - path: ".planning/codebase/TESTING.md"
      provides: "Test Invocation Discipline section"
      contains: "## Test Invocation Discipline"
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Single docs-commit"
      pattern: "docs\\(79\\): codify test invocation discipline"
  key_links:
    - from: "TESTING.md new section"
      to: "feedback_test_call_optimization memory"
      via: "explicit rule codification"
      pattern: "feedback_test_call_optimization|Test Invocation Discipline"
---

<objective>
Wave 5 of Phase 79: codify the existing `feedback_test_call_optimization` user-memory rule into a permanent project-level convention by appending a `## Test Invocation Discipline` section to `.planning/codebase/TESTING.md`. Per D-08: this is NOT a GSD-orchestrator change (out of CTC scope) — only a documented convention that future phase-execution sessions can reference.

Purpose: every Phase-79+ executor that sees the rule in TESTING.md has the same default ("one final `./mvnw verify -Pe2e` per phase, targeted `-Dtest=...`/`-Dit.test=...` between") — without having to discover the rule from the memory index. Lifts the memory from per-user-session knowledge to per-project-document knowledge.

Output: 1 docs-commit appending the section. Trivial. Can run in parallel with Plan 04 (different file).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@CLAUDE.md
@.planning/codebase/TESTING.md

<interfaces>
**Source-of-truth for the section content (RESEARCH §"Test-Invocation-Discipline Section Draft for TESTING.md"):**

The section must contain the following 5 elements:

1. H2 `## Test Invocation Discipline`
2. Attribution sentence: `**Codified from `feedback_test_call_optimization` (Phase 79 D-08).**`
3. `### Rule: One Final Full Run Per Phase` — explains `./mvnw verify -Pe2e` is the phase final gate; between-wave targeted invocations.
4. `### Rule: Do Not Re-Run Full Suite Between Waves` — table with 5 rows (after-task / per-package / pom-or-ci / phase-gate / triage).
5. `### Rule: Run Order for Independence Verification` — `reversealphabetical` + 3 random seeds (1234/5678/9999).

**Insertion point in TESTING.md:**

The file is 603 lines long. The last content line is at line 603: `*Testing analysis: 2026-04-07*`. Insert the new section BEFORE this trailing italic line, AFTER the existing last section (whose closing table is at line 599).

Use a single Edit-tool replacement that anchors on the trailing line: replace
```
*Testing analysis: 2026-04-07*
```
with
```
---

## Test Invocation Discipline

**Codified from `feedback_test_call_optimization` (Phase 79 D-08).**

### Rule: One Final Full Run Per Phase

Each GSD phase uses **one and only one** `./mvnw verify -Pe2e` invocation as its final gate (D-19 / Phase 77 D-13). This is the only invocation that counts for coverage, E2E smoke, and CI GREEN status.

Between waves within a phase, use **targeted invocations** for fast feedback:

```bash
# Run only a single test class
./mvnw test -Dtest=BackupImportServiceTest

# Run only a single IT class
./mvnw verify -Dit.test=BackupRoundTripIT

# Run all tests in a package
./mvnw test -Dtest="org.ctc.backup.service.*"

# Run tests matching a method name pattern
./mvnw test -Dtest="BackupImportServiceTest#givenValid*"
```

### Rule: Do Not Re-Run Full Suite Between Waves

Running `./mvnw verify` (or `./mvnw verify -Pe2e`) after every plan task wastes CI minutes and developer time. The full suite is a GATE, not a development loop.

| Context | Invocation |
|---------|-----------|
| After implementing a single task | `./mvnw test -Dtest=<AffectedTestClass>` |
| After per-package cleanup commit (D-03) | `./mvnw test` (unit + IT, no E2E) |
| After pom.xml / ci.yml change | `./mvnw verify` (full, no E2E) |
| Phase final gate (D-19) | `./mvnw verify -Pe2e` |
| Triage a test failure | `./mvnw verify -fae` (fail-at-end, sees all failures) |

### Rule: Run Order for Independence Verification

Before enabling Surefire/Failsafe parallelism, verify test independence:

```bash
# Reverse alphabetical order — detects setup-dependent ordering
./mvnw test -Dsurefire.runOrder=reversealphabetical

# Random order — three seeds for statistical confidence
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999
```

All three random-seed runs must be GREEN before `forkCount` is increased.

---

*Testing analysis: 2026-04-07 (last updated: 2026-05-15 — Phase 79 D-08 added Test Invocation Discipline section)*
```

This insertion preserves the original document structure and adds an updated-date note inline with the trailing italic line.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After the commit, the only verification needed is that the section exists and Markdown parses cleanly — no `./mvnw` invocation required for this docs-only plan.
- Schutzwortliste (D-13): N/A — this plan ADDS a new section, deletes nothing.
- Dead-code rule (D-04): N/A — docs-only.
- Single commit per D-08.
</critical_constraints>

<test_impact>
N/A — docs-only change. No Java/XML/YAML source code touched. No test added/removed/renamed. JaCoCo impact: 0. CI impact: 0.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Append Test Invocation Discipline section to TESTING.md + commit</name>
  <files>.planning/codebase/TESTING.md</files>
  <read_first>
    - `.planning/codebase/TESTING.md` lines 590-603 (current trailing content to confirm anchor `*Testing analysis: 2026-04-07*`)
    - This plan's `<interfaces>` block (verbatim final section content)
    - 79-CONTEXT.md §D-08 (rule codification rationale)
  </read_first>
  <action>
1. **Anchor verification:** `tail -3 .planning/codebase/TESTING.md` MUST show `*Testing analysis: 2026-04-07*` as the last non-empty line. If different → STOP / `NEEDS_CONTEXT` (the file changed since RESEARCH).

2. **Apply the Edit-tool replacement** per the `<interfaces>` block. Replace the single line `*Testing analysis: 2026-04-07*` with the full multi-section block from `<interfaces>`. The replacement adds:
   - An `---` separator line
   - The `## Test Invocation Discipline` section with attribution, 3 sub-rules (One Final Full Run, Do Not Re-Run, Run Order), and 1 table (5 rows)
   - The updated trailing italic line `*Testing analysis: 2026-04-07 (last updated: 2026-05-15 — Phase 79 D-08 added Test Invocation Discipline section)*`

3. **Post-edit verification:**
```
grep -q "## Test Invocation Discipline" .planning/codebase/TESTING.md
grep -q "feedback_test_call_optimization" .planning/codebase/TESTING.md
grep -q "Run Order for Independence Verification" .planning/codebase/TESTING.md
grep -q "seed=1234" .planning/codebase/TESTING.md
grep -q "seed=5678" .planning/codebase/TESTING.md
grep -q "seed=9999" .planning/codebase/TESTING.md
grep -q "reversealphabetical" .planning/codebase/TESTING.md
```
All MUST be present.

4. **Markdown sanity check (no broken fence):**
```
[ "$(grep -c '^```' .planning/codebase/TESTING.md)" -eq "$(( $(grep -c '^```' .planning/codebase/TESTING.md) ))" ]
```
(Trivial: just confirm the count is even — code fences come in pairs.) Run `awk 'BEGIN{c=0} /^```/{c++} END{exit (c%2)}' .planning/codebase/TESTING.md` — exit code 0 means even count.

5. **Stage + commit:** `git add .planning/codebase/TESTING.md`. Verify `git status` shows ONLY this file. Commit:
```
docs(79): codify test invocation discipline in TESTING.md (D-08)

Lifts `feedback_test_call_optimization` user-memory rule into a permanent
project-level convention. Adds three sub-rules:
- One final full run per phase (./mvnw verify -Pe2e is the gate)
- Do not re-run full suite between waves (targeted -Dtest/-Dit.test instead)
- Run order for independence verification (reversealphabetical + 3 random seeds)

Updates trailing analysis-date line to note the Phase 79 amendment.

No GSD-orchestrator change (out of CTC scope per CONTEXT.md §D-08).
```

Do NOT run `./mvnw verify` — this is a docs-only commit; the build is unaffected.
  </action>
  <verify>
    <automated>grep -q "## Test Invocation Discipline" .planning/codebase/TESTING.md &amp;&amp; grep -q "feedback_test_call_optimization" .planning/codebase/TESTING.md &amp;&amp; grep -q "seed=1234" .planning/codebase/TESTING.md &amp;&amp; grep -q "seed=9999" .planning/codebase/TESTING.md &amp;&amp; awk 'BEGIN{c=0} /^```/{c++} END{exit (c%2)}' .planning/codebase/TESTING.md &amp;&amp; git log -1 --pretty=%B | grep -q "docs(79): codify test invocation discipline"</automated>
  </verify>
  <acceptance_criteria>
    - `.planning/codebase/TESTING.md` contains `## Test Invocation Discipline` heading
    - 3 sub-rules present (`### Rule: One Final Full Run Per Phase`, `### Rule: Do Not Re-Run Full Suite Between Waves`, `### Rule: Run Order for Independence Verification`)
    - `feedback_test_call_optimization` attribution present
    - 3 random seeds (1234, 5678, 9999) present
    - `reversealphabetical` keyword present
    - 5-row table present (`After implementing a single task` ... `Triage a test failure`)
    - Code fences come in pairs (even count)
    - Trailing italic line updated with the 2026-05-15 Phase-79 amendment note
    - Commit `docs(79): codify test invocation discipline` lands on `gsd/v1.10-platform-and-backup`
  </acceptance_criteria>
  <done>Single commit lands; TESTING.md has the new section; Markdown parses cleanly.</done>
</task>

</tasks>

<verification>
- 1 atomic commit `docs(79): codify test invocation discipline in TESTING.md (D-08)` exists on `gsd/v1.10-platform-and-backup`
- `.planning/codebase/TESTING.md` contains the new section with 3 sub-rules, 1 table, 3 random seeds, `reversealphabetical` idiom
- No other file touched
</verification>

<success_criteria>
- 1 atomic commit lands
- `## Test Invocation Discipline` section is present in TESTING.md
- All 3 sub-rules + table + idioms documented per `<interfaces>` block
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-05-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: commit SHA, TESTING.md line-count delta, section-content presence proofs.
</output>
