---
phase: 92
plan: 03
slug: carry-forwards-cleanup
status: shipped
shipped: 2026-05-21
requirement: CLEAN-01
---

# Plan 92-03 — CLEAN-01 assumptions-fence build-guard

Added a Maven `validate`-phase build-guard fence (`<execution id="assumptions-fence">`)
parallel to the existing `template-fragment-call-guard` execution in the
`exec-maven-plugin` block at `pom.xml:430-458`. The tightened grep predicate
matches `org.junit.jupiter.api.Assumptions` only (NOT AssertJ
`Assumptions.assumeThat` introduced by Phase 89 PERF-01 in
`BackupStagingDirPerForkIT.java:12,37`), and excludes
`src/test/java/org/ctc/build/` so the fence's own predicate test does not flag itself.

## Files modified

| File | Change |
|------|--------|
| `pom.xml` | Added a new sibling `<execution id="assumptions-fence">` after the closing tag of `template-fragment-call-guard`, bound to the `validate` phase. CDATA-wrapped bash predicate with the JUnit-only regex + excluded fixture-package + remediation message referencing CONTEXT D-04. |
| `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java` | NEW greenfield package `org.ctc.build` (first use of this package): 2 `@TempDir` + `ProcessBuilder` tests verifying the predicate end-to-end (positive: JUnit Assumptions import matches; negative: AssertJ Assumptions import does NOT match). `FENCE_REGEX` constant locks the test predicate visually against the pom predicate. |
| `.planning/phases/92-carry-forwards-cleanup/92-03-VALIDATION.md` | NEW — per-plan Nyquist slice per CONTEXT D-08. |

## ./mvnw verify summary

- BUILD SUCCESS — total time 7:18 min
- Fence OK echo: `[CLEAN-01 build-guard] OK - no JUnit-Jupiter Assumptions offenders.` (confirmed in build log during the validate phase)
- Tests run: **1454** (Failures: 0, Errors: 0, Skipped: 1) — Δ +2 vs Plan 92-02 ship state (the 2 new predicate methods)
- JaCoCo line coverage: **88.8838 %** (preserved — Plan 92-03 is build-tooling + test-only; no coverage delta expected)
- SpotBugs `BugInstance` count: **0** (per CONTEXT D-07)
- `git diff --stat src/main/`: empty (D-10 invariant: pom.xml is build config, not src/main)

## Clean-rebuild signature

The first `./mvnw verify` after Task 2's pom.xml edit surfaced one `BackupSchemaExclusionIT`
compilation failure with "Unresolved compilation problem" — the canonical IDE/JDT-cache
signature per [[feedback-clean-maven-build-authority]]. Resolved by `./mvnw clean test-compile`
(forced javac re-compile) followed by a fresh `./mvnw verify` which then ran clean.
No code change required; the IT was already correct. Captured here for traceability.

## Negative-fence verification

The fence MUST NOT flag the legitimate AssertJ `Assumptions.assumeThat` import at
`BackupStagingDirPerForkIT.java:12` (different package, intentional per Phase 89
PERF-01). Verified by `./mvnw validate` exit 0 on the current codebase + the
`SyntheticNegative` test in `AssumptionsFencePredicateTest` (AssertJ import → grep exit 1).

## Rolling Draft milestone PR

- URL: https://github.com/jegr78/ctc-manager/pull/130
- State: Draft (preserved per CONTEXT D-06; Plan 98-03 owns the Draft → Ready flip)
- Body update appends: "Plan 92-03 shipped — CLEAN-01 assumptions-fence (JUnit-Jupiter only)."

## Phase 92 VALIDATION.md row status flips

92-03-01..03: ⬜ → ✅ (all 3 rows green on plan ship).

## Per-plan 92-03-VALIDATION.md

Authored at `.planning/phases/92-carry-forwards-cleanup/92-03-VALIDATION.md` with
`nyquist_compliant: true` + 3-row Per-Task Verification Map + Sign-Off block (CONTEXT D-08).
