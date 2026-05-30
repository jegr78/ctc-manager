# Flaky-Test Policy

Stabilisation in this project is **root-cause only**. Symptom-hotfixes that mask
non-determinism are forbidden and enforced by the build itself.

## Permanently forbidden

The following are **never** acceptable and are blocked by the `no-rerun-guard`
`validate`-phase build guard in `pom.xml` (fires locally and in CI on every
`./mvnw verify`):

- Surefire/Failsafe `rerunFailingTestsCount` (or any retry-count config).
- A `retryCount` test configuration of any kind.
- GitHub Actions retry steps / `nick-fields/retry`-style wrappers around the test run.
- Timeout bumps or sleep increases added *to make a flaky test pass* — these are
  symptom-hotfixes, not stabilisation.

If a real `<rerunFailingTestsCount>` or `<retryCount>` element ever appears in
`pom.xml`, the guard prints `[CI-06 build-guard] FAIL` and exits 1 — it cannot be
merged silently without editing the guard.

## The only accepted temporary suppression

A genuinely non-deterministic test may be quarantined with `@Tag("flaky")` (Surefire
and Failsafe already exclude this tag — see `pom.xml` `excludedGroups`). This is
allowed **only** when:

1. A root-cause issue is linked in the quarantine commit/PR, and
2. The fix lands **within the same milestone** (CLAUDE.md "In-Milestone Polish" —
   no deferral across milestones).

## What "flaky" actually means

A test is only "flaky" after a `git stash` + isolated re-run (without your own
changes) demonstrates inconsistent results (CLAUDE.md "No Flaky Dismissal"). Until
that is proven:

- A test that was green in the previous phase and fails now is a **regression**
  caused by current changes — investigate immediately. Never "pre-existing flaky"
  or "out of scope".
- "Pre-existing" is valid only after a pre-phase commit shows the same failure.

## Rationale

Rerun loops and timeout bumps hide real concurrency, ordering, or resource bugs and
erode the CI gate over time. Fixing the root cause (often by making a test design
synchronous instead of timing-dependent) keeps the suite a trustworthy signal.

See also: CLAUDE.md "Build & Test Discipline" → *No Flaky Dismissal*, *No Skip Flags*.
