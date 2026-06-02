# Phase 106: CI Pipeline Optimisation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-30
**Phase:** 106-ci-pipeline-optimisation
**Areas discussed:** E2E build structure (CI-04), Required-check green mechanism (CI-01), Docker layer caching (CI-05), Flaky/rerun scope (CI-06)

---

## E2E Build Structure (CI-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Single `verify -Pe2e` run | One `./mvnw clean verify -Pe2e`; Surefire (unit) runs before Failsafe (IT+E2E) so fail-fast preserved; ~halves doubled test wallclock | ✓ |
| Keep split | Two stages (unit/IT, then E2E) for stage visibility — pays unit/IT runtime twice | |
| You decide | Planner chooses after measuring stage times | |

**User's choice:** Single `verify -Pe2e` run
**Notes:** Biggest CI-04 lever — `build-and-test` currently runs `mvnw verify` then `mvnw verify -Pe2e`, executing the non-E2E suite twice.

---

## Required-Check Green Mechanism (CI-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Conditional steps in existing jobs | `dorny/paths-filter` `changes` job + expensive steps guarded `if: needs.changes.outputs.code=='true'`; jobs always run, no-op on docs → real `success` under unchanged names | ✓ |
| Job-level if + separate gate-job | Skip whole jobs via `if:` + always-green status job mapped to required names | |
| You decide | Planner chooses after inspecting branch protection | |

**User's choice:** Conditional steps in existing jobs
**Notes:** Avoids the `skipped required check` deadlock; preserves the `build-and-test` / `dockerfile-noble-pin-guard` / `docker-build` required-check contract.

---

## Docker Layer Caching (CI-05)

| Option | Description | Selected |
|--------|-------------|----------|
| buildx + gha cache | `docker/setup-buildx` + `cache-from/to: type=gha` with isolated cache scope (no Maven/Playwright eviction); benefit on code PRs | ✓ |
| Leave as-is | Plain `docker build` (+1-3 min, code PRs only); focus caching on existing Maven/Playwright | |
| You decide | Planner weighs gha-cache eviction vs build speedup | |

**User's choice:** buildx + gha cache (isolated scope)
**Notes:** `docker-build` currently uses plain `docker build` with no layer cache.

---

## Flaky / Rerun Scope (CI-06)

| Option | Description | Selected |
|--------|-------------|----------|
| Preventive guard, no known flaky | No flaky test today; ensure no rerun-count/retry symptom-hotfix is ever added (pom guard) + document quarantine policy | ✓ |
| Concrete flaky test exists | A specific unstable test to root-cause fix | |
| Harden existing @Tag('flaky') scheme | Extend an existing quarantine/tag scheme | |

**User's choice:** Preventive guard, no known flaky
**Notes:** `pom.xml` has no `rerunFailingTestsCount`. CI-06 is defensive — aligns with CLAUDE.md "No Flaky Dismissal" / "Keine Symptom-Hotfixes".

---

## Claude's Discretion

- Exact `dorny/paths-filter` syntax and output-name wiring; separate `changes` job vs folded filter step.
- `cache-to` mode (`mode=max` vs `min`) and the Docker gha-cache scope key string.
- Whether the CI-06 guard is a pom enforcer rule, a grep-based workflow guard, or doc-only.
- Concrete E2E median target (set at plan time after measuring the single-run median).

## Deferred Ideas

None — discussion stayed within phase scope (CI configuration only).
