---
phase: 106-ci-pipeline-optimisation
plan: 04
status: complete
requirements: [CI-01, CI-02, CI-03, CI-04, CI-05]
requirements-completed: [CI-01, CI-02, CI-03, CI-04, CI-05]
files_modified: []
checkpoint: human-verify (approved by user 2026-05-30)
---

# 106-04 Summary — Live-CI Verification Checkpoint

## Decision context

- **Task 1 (local `./mvnw clean verify -Pe2e` pre-gate):** SKIPPED by explicit user
  decision ("Nur pushen, kein lokaler Lauf"). Deviation from plan, user-authorized.
  Coverage rationale: the milestone branch's CI `build-and-test` job runs the exact
  same `./mvnw clean verify -Pe2e` and passed green (see below), so the full suite was
  verified on CI instead of locally — no regression.
- **Docs-only throwaway PR (CI-01/CI-02 empirical check):** user declined ("Nein, nur
  PR #132 beobachten"). CI-01/CI-02 therefore not empirically verified this session.
- **Approval:** user approved the checkpoint with CI-05 left open
  ("Jetzt approven (CI-05 offen)").

## Observed live results — PR #132 (code PR), run 26680554446

All seven checks `pass`:

| Job | Duration | Verdict |
|-----|----------|---------|
| `changes` | 4s | code=true correctly detected; gating works |
| `dockerfile-noble-pin-guard` | 5s | pass |
| `docker-build` (buildx) | 4m5s | pass — COLD cache (first run), populated `scope=ctc-docker` |
| `build-and-test` (single `clean verify -Pe2e`) | **14m55s** | pass, **below 17:39 baseline** |
| `CodeQL` / Analyze (java-kotlin) | pass | correct — code PR, not skipped (paths-ignore did not suppress) |
| `mariadb-migration-smoke` | 1m25s | correct — PR touches `pom.xml` |

Run started 2026-05-30T09:36:48Z, build-and-test completed 09:51:43Z.
Run URL: https://github.com/jegr78/ctc-manager/actions/runs/26680554446

## Requirement verdicts

| Req | Status | Evidence |
|-----|--------|----------|
| **CI-03** (code PR runs full CI, no skip) | ✅ verified | all jobs executed on #132; expensive steps ran |
| **CI-04** (runtime < 17:39, suite runs once) | ✅ verified | build-and-test 14:55 (< 17:39); single `clean verify -Pe2e`, no double Maven run |
| **CI-05** (warm Docker/Maven/Playwright cache) | ⏳ open | first run was cold; needs a 2nd code run to observe CACHED layers. Logic sound: isolated `scope=ctc-docker,mode=max` + `setup-java cache:'maven'` + Playwright `actions/cache` are separate namespaces (no cross-eviction). |
| **CI-01** (docs-only PR → required checks success) | ⚠️ not empirically verified | no docs-only throwaway PR created (user decision). Logic sound: `actions/checkout` + both `if: always()` uploads are ungated, so all three required jobs reach a real `success` even when `code=false`. |
| **CI-02** (CodeQL/MariaDB absent on docs-only) | ⚠️ not empirically verified | no docs-only PR. Logic sound: `codeql.yml` has `paths-ignore` for the D-04 set; `mariadb-smoke` `paths:` inclusion excludes docs. |

## D-02 — concrete E2E/build target (recorded)

**Single-run `build-and-test` median ≈ 14:55, below the 17:39 baseline** (one observed
code run; ~15% reduction vs baseline). This is the D-02 target to hold against in future
runs. The previous (pre-106) full CI run measured 35m57s total with the double Maven
invocation; collapsing to a single `clean verify -Pe2e` is the primary driver.

## Open verification items (within v1.15 — not deferred across milestones)

1. **CI-05 warm-cache empirical proof** — observe a second code run on the milestone
   branch for Docker `CACHED` layers + Maven/Playwright `actions/cache` hits and confirm
   no cross-cache eviction.
2. **CI-01/CI-02 docs-only empirical proof** — confirm on a docs-only commit/PR that the
   three required checks report `success` (not skipped) and CodeQL/MariaDB are absent.

Both will surface naturally as the v1.15 milestone progresses (subsequent code runs warm
the caches; any docs-only commit on the branch exercises the gating). Recorded in
STATE.md so they are closed before milestone close.
