# Phase 106: CI Pipeline Optimisation - Context

**Gathered:** 2026-05-30
**Status:** Ready for planning

<domain>
## Phase Boundary

The CI pipeline runs expensive work only when code or build files actually changed; non-required workflows skip documentation-only commits; caches are warm on repeat runs; E2E runtime drops below the 17:39 baseline; and no stable test is marked flaky without a root-cause fix.

**In scope:** `.github/workflows/*.yml` and `pom.xml` CI configuration only. Path-aware gating (approach A + C, already locked in REQUIREMENTS.md), E2E build-structure consolidation, Docker layer caching, flaky/rerun defensive hardening.

**Out of scope:** No Java source changes. No branch-protection reconfiguration (approach B / single-aggregation-gate explicitly rejected — required-check contract `build-and-test` / `dockerfile-noble-pin-guard` / `docker-build` stays intact). No new test infrastructure beyond CI config.

</domain>

<decisions>
## Implementation Decisions

### CI-04 — E2E Build Structure
- **D-01:** Collapse the double Maven run in `build-and-test` into a single `./mvnw clean verify -Pe2e` invocation. Today the job runs `./mvnw verify` (unit + IT) and then `./mvnw verify -Pe2e` (re-compiles and re-runs unit + IT plus E2E) — the non-E2E suite executes twice. Surefire (unit) runs in the `test` phase before Failsafe (IT + E2E) in `integration-test`/`verify`, so unit failures still abort before E2E — fail-fast is preserved within the single lifecycle.
- **D-02:** The concrete E2E median target ("below 17:39") is set at plan time after measuring the single-run median — not pre-committed here. REQUIREMENTS.md CI-04 defers the number deliberately.

### CI-01 — Required-Check Green on Docs-Only PRs
- **D-03:** Add a `changes` job using `dorny/paths-filter` that classifies the diff as code vs docs-only. The three required jobs (`build-and-test`, `dockerfile-noble-pin-guard`, `docker-build`) ALWAYS run; their expensive steps are guarded with `if: needs.changes.outputs.code == 'true'`. On a docs-only PR the jobs no-op but still report a real `success` status under their unchanged check names. Rationale: a job skipped via job-level `if:` reports `skipped`, which is a known branch-protection deadlock risk; running the job with no-op steps guarantees a genuine success. (Job-level-`if:` + separate always-green gate-job was rejected for name-mapping fragility.)

### CI-02 / CI-03 — Path Filters
- **D-04:** Docs/planning ignore set: `.planning/**`, root `*.md`, `docs/**` (excluding `docs/site/**`), `.gitmessage`. A PR touching both an ignored file and a code file runs full CI (mixed code+docs commit triggers all steps).
- **D-05:** Changes to `.github/workflows/**`, `pom.xml`, `Dockerfile`, `src/**`, and `mvnw`/`.mvn/**` ALWAYS trigger full CI — CI-config and build changes must validate themselves.
- **D-06:** Non-required workflows use top-level `paths-ignore` (approach C): `codeql.yml` (currently runs on every PR/push with no filter → add `paths-ignore`) and `mariadb-migration-smoke.yml` (already `paths:`-gated → reconcile to the same ignore semantics). `deploy-site.yml` (`paths: ['docs/site/**']`) MUST remain untouched — it is the GitHub Pages trigger.

### CI-05 — Docker Layer Caching
- **D-07:** Add `docker/setup-buildx-action` + `cache-from`/`cache-to: type=gha` to the `docker-build` job, using an **isolated cache scope/key** so it does not evict the Maven and Playwright caches under the shared ~10 GB GHA cache budget. Benefit applies on code PRs only (docker-build is gated to code changes via D-03). `setup-java` Maven cache and the existing Playwright browser cache (`~/.cache/ms-playwright`, keyed on Playwright 1.59.0) stay as-is.

### CI-06 — Flaky / Rerun Hardening
- **D-08:** Defensive scope — there is no known flaky test and `pom.xml` has NO `rerunFailingTestsCount` (forkCount=2, reuseForks=true). CI-06 ensures no rerun-count / retry action is ever introduced as a symptom hotfix (consistent with CLAUDE.md "No Flaky Dismissal" / "Keine Symptom-Hotfixes"). Consider a lightweight guard (e.g. assert absence of `rerunFailingTestsCount` in the Surefire/Failsafe config) and document the quarantine policy. Stabilisation is always root-cause, never timeout-bump or retry-loop.

### Claude's Discretion
- Exact `dorny/paths-filter` filter syntax and output-name wiring.
- Whether the `changes` job is a separate job or folded into a reusable filter step.
- Concrete `cache-to` mode (`type=gha,mode=max` vs `min`) and scope key string.
- Whether the CI-06 guard is a pom enforcer rule, a grep-based workflow guard, or doc-only.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope & requirements
- `.planning/ROADMAP.md` → `### Phase 106: CI Pipeline Optimisation` — goal + 5 success criteria
- `.planning/REQUIREMENTS.md` → CI-01..CI-06 + Out-of-Scope table (approach A+C locked; approach B / branch-protection reconfig rejected; E2E hard sub-target not pre-committed)

### CI configuration (files to modify)
- `.github/workflows/ci.yml` — 3 required jobs; double `mvnw verify` (D-01), no `changes` filter (D-03), plain `docker build` in `docker-build` (D-07)
- `.github/workflows/codeql.yml` — push/PR to master + Sunday cron, NO path filter (D-06)
- `.github/workflows/mariadb-migration-smoke.yml` — already `paths:`-gated (D-06 reconcile)
- `.github/workflows/deploy-site.yml` — `paths: ['docs/site/**']`, MUST stay untouched (D-06)
- `.github/workflows/release.yml` — release pipeline (do not regress; tags after merge)
- `pom.xml` — Surefire/Failsafe `forkCount=2`, no rerun config (D-08); E2E via `-Pe2e` profile

### Project conventions
- `CLAUDE.md` → "Build & Test Discipline" (clean build authority, `clean verify -Pe2e` is the authoritative run, no skip flags), "No Flaky Dismissal", "CI/CD"
- `.planning/codebase/TESTING.md` → `@Tag`-based Surefire/Failsafe routing, fork config
- `docs/security/sast-acceptance.md` + `.github/codeql/codeql-config.yml` — CodeQL gate context (do not weaken the security-severity gate-step when adding `paths-ignore`)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Playwright browser cache (`actions/cache@v4`, key `${{ runner.os }}-playwright-1.59.0`) already present in `build-and-test` — keep; do not let the new Docker gha-cache evict it.
- `setup-java@v5` with `cache: 'maven'` already provides Maven dependency caching.
- `dockerfile-noble-pin-guard` is a cheap structural grep guard (`FROM eclipse-temurin:` must end `-noble`) — its step is trivial, so guarding it via D-03 is about reporting status, not runtime savings.

### Established Patterns
- `concurrency: { group, cancel-in-progress: true }` already on `ci.yml` and `codeql.yml`.
- Required-check contract = `build-and-test`, `dockerfile-noble-pin-guard`, `docker-build` (branch protection). These names must keep reporting `success`.
- `docker-build` `needs: dockerfile-noble-pin-guard`; both run on every PR + push to master.

### Integration Points
- `dorny/paths-filter` `changes` job becomes an upstream dependency (`needs: changes`) for the conditional steps in the three required jobs.
- The `madrapps/jacoco-report` PR-comment step (`if: github.event_name == 'pull_request'`) lives inside `build-and-test` after the E2E step — it must still run on code PRs after the single-run consolidation (D-01).

</code_context>

<specifics>
## Specific Ideas

- Single authoritative test command: `./mvnw clean verify -Pe2e` (mirrors CLAUDE.md "Always `./mvnw clean verify -Pe2e` for full runs").
- Baseline to beat: CI E2E median **17:39** (from STATE.md baselines).
- Caches share the GHA ~10 GB budget — Docker gha-cache MUST use an isolated scope so Maven + Playwright caches are not evicted.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope (CI configuration only).

</deferred>

---

*Phase: 106-ci-pipeline-optimisation*
*Context gathered: 2026-05-30*
