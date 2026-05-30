---
phase: 106
slug: ci-pipeline-optimisation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-30
---

# Phase 106 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> This phase modifies CI configuration (`.github/workflows/*.yml`, `pom.xml`) only — the
> "test framework" is the GitHub Actions runner plus the existing Maven build guards.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | GitHub Actions (workflow runs) + Maven build (`./mvnw clean verify -Pe2e`) + maven-enforcer/grep guards |
| **Config file** | `.github/workflows/ci.yml`, `codeql.yml`, `mariadb-migration-smoke.yml`, `deploy-site.yml`; `pom.xml` |
| **Quick run command** | `actionlint .github/workflows/*.yml` (YAML/expression lint) + `./mvnw -q validate` (pom guard) |
| **Full suite command** | `./mvnw clean verify -Pe2e` (locally) + observed GitHub Actions run on the PR |
| **Estimated runtime** | local pom/lint ~30 s; full CI run is the 17:39-baseline E2E job |

---

## Sampling Rate

- **After every task commit:** `actionlint` the changed workflow file (syntax + `needs.*.outputs.*` reference validity) and/or `./mvnw -q validate` for pom changes.
- **After every plan wave:** Push the branch and observe the live CI run — the workflow IS the integration test.
- **Before `/gsd:verify-work`:** A docs-only test PR and a code+docs mixed PR must both behave per success criteria; `./mvnw clean verify -Pe2e` green locally.
- **Max feedback latency:** lint/pom < 60 s; full CI observation = one workflow run.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Secure Behavior | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------------|-----------|-------------------|--------|
| 106-xx | TBD | TBD | CI-01 | Required checks report real `success` (not `skipped`) on docs-only PR | behavior (CI run) | docs-only test PR → `gh pr checks` shows `build-and-test`/`dockerfile-noble-pin-guard`/`docker-build` = success | ⬜ pending |
| 106-xx | TBD | TBD | CI-02 | `codeql.yml`/`mariadb-smoke` do not run on docs-only | behavior (CI run) | docs-only PR → those workflows absent from checks | ⬜ pending |
| 106-xx | TBD | TBD | CI-03 | Mixed code+docs PR runs full CI | behavior (CI run) | mixed PR → all steps execute | ⬜ pending |
| 106-xx | TBD | TBD | CI-04 | E2E median below 17:39; suite runs once | observation | compare run duration vs baseline; single `mvnw clean verify -Pe2e` in job | ⬜ pending |
| 106-xx | TBD | TBD | CI-05 | Warm Docker/Maven/Playwright caches | observation (cache-hit logs) | second run shows `CACHED` docker layers + maven/playwright cache hit, isolated scope | ⬜ pending |
| 106-xx | TBD | TBD | CI-06 | No rerun/retry symptom-hotfix | source guard | `mvnw -q validate` enforcer/grep fails if `rerunFailingTestsCount` present | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Concrete task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] `actionlint` available (or documented install) for workflow-syntax validation.

*Otherwise: existing GitHub Actions + Maven infrastructure covers all phase requirements — no new test framework is introduced.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Required-check status on a real docs-only PR | CI-01 | Branch-protection `success`-vs-`skipped` behavior is only observable on a live PR against `master` | Open a throwaway PR touching only `.planning/**` or a root `*.md`; confirm the three required checks report green and merge is not blocked |
| E2E median improvement | CI-04 | Runtime is measurable only across real runs | Compare the E2E-job duration on 2-3 post-change runs against the 17:39 baseline |

---

*Phase: 106-ci-pipeline-optimisation*
*Validation strategy drafted: 2026-05-30*
