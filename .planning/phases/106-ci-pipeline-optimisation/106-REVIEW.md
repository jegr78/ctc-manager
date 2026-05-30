---
phase: 106-ci-pipeline-optimisation
reviewed: 2026-05-30T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - .github/workflows/ci.yml
  - .github/workflows/codeql.yml
  - .github/workflows/mariadb-migration-smoke.yml
  - pom.xml
  - docs/ci/FLAKY-TEST-POLICY.md
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
warnings_resolved: 2
status: resolved
resolution: "WR-01 + WR-02 fixed in commit df50f03c (2026-05-30). Info items IN-01..03 accepted (no action)."
---

# Phase 106: Code Review Report

**Reviewed:** 2026-05-30
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

CI/build-configuration-only phase: a new `changes` path-filter job + step-gating in
`ci.yml`, a consolidated single `clean verify -Pe2e` step, a buildx `docker-build` with
GHA cache, a CodeQL `paths-ignore`, a one-line shellcheck fix in the MariaDB smoke, a new
`no-rerun-guard` Maven build guard, and a flaky-test policy doc.

The high-risk items called out in the brief all check out clean:

- **Required-check deadlock — SAFE.** Branch protection on `master` requires exactly
  `build-and-test`, `dockerfile-noble-pin-guard`, `docker-build` (confirmed via API). The
  CI workflow `on:` block has NO `paths`/`paths-ignore` filter, so all three jobs always
  *run*; only their expensive steps are gated with `if: needs.changes.outputs.code == 'true'`.
  `actions/checkout` and the `if: always()` upload steps stay ungated, so each job's overall
  conclusion is `success` (not `skipped`/`pending`) on a docs-only PR. No deadlock.
- **CodeQL gate — UNCHANGED.** The "Gate on new HIGH/CRITICAL" step and its
  `if: github.event_name != 'schedule'` are byte-for-byte unchanged. `schedule` +
  `workflow_dispatch` carry no path filter (full-tree drift detection preserved).
  `paths-ignore` is not combined with `paths:`. CodeQL is NOT a required check, so the
  `paths-ignore` skip does not deadlock branch protection.
- **no-rerun-guard self-trip — DOES NOT TRIP.** Verified: `grep -nE '<(rerunFailingTestsCount|retryCount)' pom.xml`
  returns no match against the committed pom (the guard's own pattern literal is
  `<(rerunFailingTestsCount|retryCount)` — the parenthesis after `<` prevents a literal
  `<rerunFailingTestsCount` / `<retryCount` opening-tag match). It correctly fires (exit 1)
  only on a real `<rerunFailingTestsCount>` / `<retryCount>` element. Surefire/Failsafe
  `forkCount`/`reuseForks` are untouched (lines 288-289, 343-344). XML is well-formed.
- **MariaDB smoke — SAFE NO-OP.** Only `>> $GITHUB_ENV` → `>> "$GITHUB_ENV"` (SC2086).
  Quoting a variable that holds a no-space path is behaviour-preserving. The `services`
  block and Flyway-grep verification steps are untouched.

Two WARNING-level gaps remain in the `changes` path-filter design, plus minor info items.

## Warnings

### WR-01: `code` path-filter is not a complete superset — build-affecting files misclassified as non-code  — ✅ RESOLVED (df50f03c)

> **Resolved 2026-05-30:** the `code` filter was widened to include `lombok.config`, `config/**`, `rewrite.yml`, `.dockerignore`, and `mvnw.cmd` — restoring the T-106-01 safe-superset invariant. `actionlint` green.


**File:** `.github/workflows/ci.yml:34-40`
**Issue:** The `code` filter is `src/**`, `pom.xml`, `Dockerfile`, `.github/workflows/**`,
`mvnw`, `.mvn/**`. Several files that directly affect the build/verify outcome are NOT in
this set, so a PR touching only them yields `code == 'false'` and skips the entire
`clean verify -Pe2e` + Docker build, yet all three required checks report `success`:

- **`lombok.config`** — CLAUDE.md ("`lombok.config` invariant") explicitly warns that
  removing/modifying its two SpotBugs lines re-introduces ~40-80 `EI_EXPOSE_REP*` SpotBugs
  findings, which the SpotBugs gate (Medium+HIGH block the build) would catch. A
  lombok.config-only PR would be waved through green without ever running that gate.
- **`config/spotbugs-exclude.xml`** — directly governs which SpotBugs findings block the
  build. Editing it without re-running verify can silently un-suppress a build-breaker (or
  hide a real one) with a green required check.
- **`.dockerignore`** — alters the Docker build context; a regression here is exactly the
  class `docker-build` exists to catch, but the job's build step would be skipped.
- Minor: `rewrite.yml`, `config/rewrite-validate-hasText.yml`, `mvnw.cmd`.

This contradicts the phase's own safety invariant ("no code-changing PR can be
misclassified as docs-only"). The blast radius is bounded (these files change rarely and
the `master` push build still runs full), so this is a WARNING, not a BLOCKER — but the
filter should be widened.

**Fix:** Add the build-relevant root/config paths to the `code` filter:
```yaml
            code:
              - 'src/**'
              - 'pom.xml'
              - 'lombok.config'
              - 'config/**'
              - 'rewrite.yml'
              - '.dockerignore'
              - 'Dockerfile'
              - '.github/workflows/**'
              - 'mvnw'
              - 'mvnw.cmd'
              - '.mvn/**'
```

### WR-02: Stale `docker-build.log` artifact upload — references a file the new build step never creates  — ✅ RESOLVED (df50f03c)

> **Resolved 2026-05-30:** the `if: failure()` "Upload docker build log" step was removed (buildx no longer writes `/tmp/docker-build.log`; buildx logs remain in the failed step's own output). `actionlint` green.


**File:** `.github/workflows/ci.yml:200-206`
**Issue:** The "Build Docker image" step was rewritten from the old inline
`docker build ... 2>&1 | tee /tmp/docker-build.log` to `docker/build-push-action@v7.2.0`,
which does not write `/tmp/docker-build.log`. The downstream `if: failure()` step still
uploads `path: /tmp/docker-build.log`. On a real Docker build failure the upload finds no
file: `upload-artifact@v7` warns "No files were found" and (with default
`if-no-files-found: warn`) the step succeeds but produces an empty/absent artifact — the
diagnostic log it promises is gone exactly when it is needed. Dead/misleading config, not a
correctness break of the gate itself.

**Fix:** Either drop the now-useless upload step, or capture the buildx output. The
cleanest option is to remove it (buildx logs are visible in the failed step's own log):
```yaml
      # delete the "Upload docker build log on failure" step entirely
```
If a log artifact is still desired, add `outputs: type=docker` is not it — instead pipe
buildx logs is non-trivial with build-push-action; removal is recommended.

## Info

### IN-01: `docs` filter output is declared but never consumed

**File:** `.github/workflows/ci.yml:41-46`
**Issue:** The `docs` filter is defined inside `dorny/paths-filter` but the `changes` job
only exposes `outputs.code`; nothing reads `steps.filter.outputs.docs`. Harmless, but it
is dead configuration that implies a `docs`-based gate that does not exist.
**Fix:** Remove the `docs:` filter block, or add `docs: ${{ steps.filter.outputs.docs }}`
to job `outputs` if a future doc-only path is intended.

### IN-02: `.github/workflows/**` in `code` filter forces full verify on any workflow edit

**File:** `.github/workflows/ci.yml:38`
**Issue:** Including `.github/workflows/**` in `code` means a PR that only edits an
unrelated workflow (e.g. a Pages-deploy YAML) triggers the full `clean verify -Pe2e` +
Docker build. This is the safe/conservative direction (over-running, not under-running), so
it is intentional and acceptable — noted only so it is not mistaken for a bug later.
**Fix:** None required; documented as intentional.

### IN-03: FLAKY-TEST-POLICY.md is accurate but references a CI-step ban the guard cannot enforce

**File:** `docs/ci/FLAKY-TEST-POLICY.md:14`
**Issue:** The doc lists "GitHub Actions retry steps / `nick-fields/retry`-style wrappers"
under "Permanently forbidden", but the `no-rerun-guard` only greps `pom.xml` for
`<rerunFailingTestsCount>`/`<retryCount>` — it does not scan `.github/workflows/**` for
retry-action wrappers. The doc is correct about the policy intent and the pom-level guard,
but the workflow-level prohibition is convention-only, not machine-enforced. Accurate as
written (it says "blocked by the ... build guard in `pom.xml`" for the first two bullets);
just be aware the GHA-retry bullet has no automated fence.
**Fix:** Optional — add a future guard step that greps `.github/workflows/` for
`uses: nick-fields/retry`, or annotate the doc that the GHA-retry rule is review-enforced.

---

_Reviewed: 2026-05-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
