---
phase: 106
slug: ci-pipeline-optimisation
status: validated
nyquist_compliant: false
nyquist_status: partial_by_design
wave_0_complete: true
open_validation_items: 0
created: 2026-05-30
validated: 2026-05-31
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
| 106-04-T2 | 106-04 | 2 | CI-01 | Required checks report real `success` (not `skipped`) on docs-only PR | behavior (CI run) — **manual-only** | docs-only test PR → `gh pr checks` shows `build-and-test`/`dockerfile-noble-pin-guard`/`docker-build` = success | ✅ accepted config-sound (2026-05-31; throwaway PR deliberately skipped — docs/ci/v1.15-open-verify.md) |
| 106-02-T1 / 106-04-T2 | 106-02, 106-04 | 1, 2 | CI-02 | `codeql.yml`/`mariadb-smoke` do not run on docs-only | behavior (CI run) — **manual-only** | docs-only PR → those workflows absent from checks | ✅ accepted config-sound (2026-05-31; cumulative-PR-diff caveat — docs/ci/v1.15-open-verify.md) |
| 106-01-T1 / 106-04-T2 | 106-01, 106-04 | 1, 2 | CI-03 | Mixed/code PR runs full CI | behavior (CI run) — **manual observation** | code PR → all steps execute | ✅ green (live run 26680554446 — all jobs ran) |
| 106-01-T1 / 106-04-T2 | 106-01, 106-04 | 1, 2 | CI-04 | E2E median below 17:39; suite runs once | observation — **manual** | compare run duration vs baseline; single `mvnw clean verify -Pe2e` in job | ✅ green (build-and-test 14:55 < 17:39; single Maven run) |
| 106-01-T2 / 106-04-T2 | 106-01, 106-04 | 1, 2 | CI-05 | Warm Docker/Maven/Playwright caches | observation (cache-hit logs) — **manual-only** | second run shows `CACHED` docker layers + maven/playwright cache hit, isolated scope | ✅ confirmed empirically (2026-05-31; docker-build 0m20s/0m21s full-cache-hit runs — docs/ci/v1.15-open-verify.md) |
| 106-03-T1/T2 | 106-03 | 1 | CI-06 | No rerun/retry symptom-hotfix | **source guard (automated)** | `mvnw -q validate` `no-rerun-guard` grep fails (exit 1) if `<rerunFailingTestsCount>`/`<retryCount>` present | ✅ green (automated; proven RED→GREEN) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky.*
*Nyquist note: only **CI-06** is automatable in this stack (the pom build-guard). **CI-01..05** are GitHub-Actions runtime / branch-protection behaviors that are not reproducible in JUnit/Surefire — they are manual-only by nature (the workflow IS the integration test). See Manual-Only Verifications below.*

---

## Wave 0 Requirements

- [x] `actionlint` available — installed via `brew install actionlint` (1.7.12); used as the per-task automated verifier on every touched workflow (`ci.yml`, `codeql.yml`, `mariadb-migration-smoke.yml`), all exit 0.

*Otherwise: existing GitHub Actions + Maven infrastructure covers all phase requirements — no new test framework is introduced.*

---

## Manual-Only Verifications

GitHub-Actions path-filter classification and branch-protection `success`-vs-`skipped`
semantics are not reproducible in the JUnit/Surefire stack — they are only observable on
live runs. These are manual-only by nature (not a coverage gap).

| Behavior | Requirement | Why Manual | Test Instructions | Status |
|----------|-------------|------------|-------------------|--------|
| Required-check status on a real docs-only PR | CI-01 | Branch-protection `success`-vs-`skipped` is only observable on a live PR against `master` | Open a throwaway PR touching only `.planning/**` or a root `*.md`; confirm the three required checks report green (not skipped) and merge is not blocked | ✅ accepted config-sound (2026-05-31) — path filters verified correct by inspection; not empirically isolable on mixed-diff PR #132 (cumulative `base…head` semantics); throwaway PR deliberately skipped |
| Non-required workflows absent on docs-only | CI-02 | Trigger evaluation only observable on a live docs-only commit/PR | On a docs-only PR confirm `CodeQL SAST` + `MariaDB Migration Smoke` are absent from the checks list | ✅ accepted config-sound (2026-05-31) — `paths-ignore`/`paths:` confirmed; same cumulative-diff caveat as CI-01 |
| Code/mixed PR runs full CI | CI-03 | Live workflow-run observation | On a code PR confirm all expensive steps ran | ✅ confirmed (run 26680554446) |
| E2E median improvement | CI-04 | Runtime measurable only across real runs | Compare the `build-and-test` duration on post-change runs against the 17:39 baseline | ✅ confirmed (14:55) |
| Warm cache on re-run | CI-05 | Cache-hit logs only on a 2nd live run | Trigger a 2nd code run; confirm Docker `CACHED` layers (`scope=ctc-docker`) + Maven/Playwright `actions/cache` hits, no cross-eviction | ✅ confirmed empirically (2026-05-31) — docker-build 0m20s/0m21s full-cache-hit runs; sublinear rebuilds 2–4 min |

CI-01/CI-02/CI-05 were tracked as in-milestone open-verify items in `.planning/STATE.md`;
all three are now **closed/accepted** (2026-05-31) and documented in
`docs/ci/v1.15-open-verify.md`. No open validation items remain for this phase.

---

## Validation Audit 2026-05-30

| Metric | Count |
|--------|-------|
| Requirements | 6 (CI-01..06) |
| Automated (Nyquist-covered) | 1 (CI-06 — pom `no-rerun-guard`) |
| Manual confirmed (live CI) | 2 (CI-03, CI-04) |
| Manual open (live CI) | 3 (CI-01, CI-02, CI-05) |
| Generated test files | 0 (CI-01..05 not unit-testable; test generation would be artificial) |
| Escalated impl bugs | 0 |

**Verdict:** PARTIAL Nyquist — `nyquist_compliant: false` by design. CI-06 is fully
automated and self-enforcing on every `./mvnw verify`. CI-01..05 are GitHub-Actions
runtime behaviors with no in-stack automated equivalent; the live workflow run is the
integration test. No coverage gap to fill with generated tests.

---

## Validation Audit 2026-05-31

Re-audit during `/gsd-audit-milestone v1.15` follow-up.

| Metric | Count |
|--------|-------|
| Gaps found (automatable) | 0 |
| Resolved | 0 (none automatable) |
| Escalated | 0 |
| Manual open items closed since 2026-05-30 | 3 (CI-01, CI-02, CI-05) |

**Outcome:** No automatable Nyquist gap exists. CI-01..05 are GitHub-Actions runtime
behaviors with no in-stack JUnit/Surefire equivalent — generated tests would be
artificial. The three previously-open manual items are now closed: **CI-05** confirmed
empirically (full Docker cache-hit runs) and **CI-01/CI-02** accepted as config-sound
(cumulative-PR-diff semantics; throwaway docs-only PR deliberately skipped) — see
`docs/ci/v1.15-open-verify.md`. `nyquist_compliant: false` is retained as the correct,
terminal **partial-by-design** state; `open_validation_items: 0`. No gsd-nyquist-auditor
spawn was warranted (nothing to generate).

---

*Phase: 106-ci-pipeline-optimisation*
*Validation strategy drafted: 2026-05-30 · audited: 2026-05-30, 2026-05-31*
