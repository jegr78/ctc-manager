---
phase: 106
type: verification
verdict: PASS
verified: 2026-05-31
note: backfilled retroactively during /gsd-audit-milestone v1.15 follow-up
---

# Phase 106 Verification — CI Pipeline Optimisation

Goal-backward verification: does the codebase deliver the phase goal — CI runs expensive
work only when code/build files change, non-required workflows skip docs-only commits,
caches are warm on repeat runs, E2E runtime is below the 17:39 baseline, and no stable
test is "flaky" without a root-cause fix?

## Requirement coverage

| Req | Criterion | Verdict | Evidence |
|-----|-----------|---------|----------|
| CI-01 | `ci.yml` expensive steps gated on code changes; required checks always report a status (never deadlock) | ✅ PASS (config-sound) | `dorny/paths-filter` `changes` job splits `code`/`docs`; the three required jobs gate real steps with `if: needs.changes.outputs.code == 'true'` while checkout + `if: always()` uploads run unconditionally → `success`, not `skipped`. Path filters verified correct by inspection; cumulative-PR-diff caveat accepted (docs/ci/v1.15-open-verify.md). |
| CI-02 | `codeql.yml` / `mariadb-migration-smoke.yml` skip docs/planning-only changes | ✅ PASS (config-sound) | `paths-ignore: ['.planning/**','*.md','docs/**','.gitmessage']` on codeql; positive `paths:` inclusion on mariadb-smoke. Accepted config-sound (same cumulative-diff caveat as CI-01). |
| CI-03 | Mixed code+docs PR runs full CI | ✅ PASS | Live run `26680554446` — all jobs executed on a code PR. |
| CI-04 | E2E median below 17:39 baseline; suite runs once | ✅ PASS | Single `./mvnw clean verify -Pe2e` step (double-invocation removed); `build-and-test` 14:55 < 17:39 on live run. |
| CI-05 | Warm Docker/Maven/Playwright caches on repeat runs | ✅ PASS | `docker-build` 0m20s/0m21s full-cache-hit runs + sublinear rebuilds (2–4 min vs ~6–10 cold), `scope=ctc-docker`; Maven (`cache: maven`) + Playwright (`actions/cache`) warm. docs/ci/v1.15-open-verify.md. |
| CI-06 | No flaky-rerun symptom hotfix; stabilise at root cause | ✅ PASS | `no-rerun-guard` exec bound to `validate` in `pom.xml` fails (exit 1) on `<rerunFailingTestsCount>`/`<retryCount>`; proven RED→GREEN. Automated, self-enforcing on every `./mvnw verify`. |

## Strategy fidelity
- **Approach A + C** (per REQUIREMENTS.md out-of-scope): required-check contract preserved
  (no branch-protection change); `paths-filter` for required jobs, `paths-ignore` for
  non-required workflows.
- **Phase touches only CI config** (`.github/workflows/*.yml`, `pom.xml`) — no Java source;
  zero cross-phase surface.

## Validation note
Nyquist `partial_by_design` (`106-VALIDATION.md`): CI-06 is fully automated; CI-01..05 are
GitHub-Actions runtime behaviours with no in-stack JUnit equivalent (the live workflow run
is the integration test). The three previously-open manual items (CI-01/02/05) are now
closed/accepted. `open_validation_items: 0`.

## Verdict: PASS — all of CI-01..06 satisfied (CI-01/CI-02 accepted config-sound).
