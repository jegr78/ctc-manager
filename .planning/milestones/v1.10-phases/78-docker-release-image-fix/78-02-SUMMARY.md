---
phase: 78-docker-release-image-fix
plan: 02
subsystem: infra
tags: [ci, github-actions, docker, build-guard, dockerfile, noble, eclipse-temurin]

# Dependency graph
requires:
  - "78-01 (Dockerfile pinned to eclipse-temurin :25-jdk-noble / :25-jre-noble — the state the new build-guard asserts on)"
provides:
  - "Structural ci.yml dockerfile-noble-pin-guard job (Phase 71-05 grep -E + grep -F idiom) that fails any PR with an unpinned FROM eclipse-temurin: line"
  - "Full ci.yml docker-build job (docker build .) that exercises both Dockerfile stages including the Playwright install chromium step on every PR + push to master"
affects: [78-03, release.yml]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Two-stage cross-platform build-guard idiom: grep -E to extract candidate lines, grep -F -e '<token>' to filter (mirrors pom.xml PLAT-07 / commit f451ff4)"
    - "needs:-gated heavy CI job (cheap structural guard gates the slow docker-build) for runner-minute economy"
    - "Failure-message tagging convention `[Phase N build-guard]` for grep-ability in CI logs"

key-files:
  created:
    - ".planning/phases/78-docker-release-image-fix/78-02-SUMMARY.md"
  modified:
    - ".github/workflows/ci.yml"

key-decisions:
  - "D-05 honored: whitelist-on-suffix structural guard fails any unpinned FROM eclipse-temurin: line, with Phase-78-named failure message pointing at 78-CONTEXT.md"
  - "D-06 honored: both new jobs are structural — no path filters, no if-conditionals, no opt-outs; they run on every PR and every push to master via the workflow-level on: trigger"
  - "D-07 honored: full docker build . on every PR (+1-3 min CI accepted by user); no Playwright install skipped — exercises the exact failing path from release run 25609204039"
  - "D-08 honored: single full docker-build job (no separate path-filtered or container-smoke job)"
  - "Planner-discretion choices: no docker/setup-buildx-action, no docker/login-action, no caching action (mirrors release.yml lines 133-141 simplicity)"

patterns-established:
  - "GitHub Actions build-guard pattern: separate fast structural job (parallel with build-and-test), heavy verification job gated by `needs:` on the structural job, failure log uploaded as artifact"
  - "Cross-platform grep pattern for CI: `grep -E` for regex extraction, `grep -F -e '<leading-dash-token>'` for fixed-string filter (the `-e` is mandatory or BSD grep parses `-noble` as flags)"

requirements-completed: [PLAT-CI-02]

# Metrics
duration: 4min
completed: 2026-05-11
---

# Phase 78 Plan 02: CI Dockerfile Pin Guard + Full docker-build Job Summary

**Two new ci.yml jobs added — `dockerfile-noble-pin-guard` (fast structural whitelist-on-suffix check) and `docker-build` (full `docker build .` exercising both Dockerfile stages including Playwright chromium install) — so future Dockerfile/base-image regressions fail on PR instead of on release.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-05-11T13:51:20Z
- **Completed:** 2026-05-11T13:55:06Z
- **Tasks:** 2 / 2
- **Files modified:** 1 (`.github/workflows/ci.yml`)
- **Commits (per-task):** 2 (ci × 2)

## Accomplishments

- New `dockerfile-noble-pin-guard` job added to `.github/workflows/ci.yml` as a sibling of `build-and-test`. The job:
  - Uses the two-stage extract-then-filter idiom from pom.xml PLAT-07 (commit f451ff4): `grep -E '^FROM eclipse-temurin:'` to gather candidates, `grep -v -F -e '-noble'` to filter to the offending set.
  - Emits `[Phase 78 build-guard]` tagged log lines for grep-ability in CI logs (OK + FAIL paths).
  - Failure message names Phase 78 explicitly, names the `-noble` rationale, references release run `25609204039` as forensic context, and points the maintainer at `.planning/phases/78-docker-release-image-fix/78-CONTEXT.md`.
  - Runs in parallel with `build-and-test` (no `needs:`) so it fails fast (< 10 s) on regressions without waiting for the full Maven/Playwright suite.
- New `docker-build` job added as a third sibling. The job:
  - Declares `needs: dockerfile-noble-pin-guard` so the heavy docker build (1-3 min) is skipped when the cheap structural guard has already failed.
  - Performs a full `docker build .` (no buildx, no caching, no registry login, no push) — mirrors `release.yml` lines 133-141.
  - Tags the local image as `ctc-manager:ci-${GITHUB_SHA}` and inspects it (`docker image inspect`) for runner-log traceability.
  - Uploads `/tmp/docker-build.log` via `actions/upload-artifact@v7` on failure for forensic analysis of future upstream Temurin retag / apt-package rename breakage.
- Existing `build-and-test` job left fully untouched (no edits, no reformatting).
- YAML structurally valid (3 top-level jobs confirmed via `ruby -ryaml -e "YAML.load_file(...)"`).

## Task Commits

Each task was committed atomically against `gsd/v1.10-platform-and-backup`:

1. **Task 1: Add `dockerfile-noble-pin-guard` job** — `c446b43` (ci)
2. **Task 2: Add `docker-build` job + cross-platform portability refactor of Task 1's guard** — `72ba72e` (ci)

## Files Created/Modified

- `.github/workflows/ci.yml` — Added two top-level jobs (`dockerfile-noble-pin-guard` and `docker-build`) after the existing `build-and-test` job. Net +72 lines, -0 deletions to existing job.

## Decisions Made

- **No new architectural decisions during execution.** All locked decisions from `78-CONTEXT.md` honored verbatim (D-05/D-06/D-07/D-08).
- **Planner-discretion choices taken** (all stated as defensible in the plan):
  - No `docker/setup-buildx-action` — matches release.yml's simple `docker build` shape; if PR build time becomes a complaint, revisit (CONTEXT.md `<deferred>`).
  - No `docker/login-action` — this is CI, not release; no registry push.
  - No `paths:` filter, no `if:` conditional — D-06 requires structural-not-advisory.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Cross-platform-grep portability trap in initial guard implementation**

- **Found during:** Task 2 acceptance-check phase.
- **Issue:** The Task-1 guard initially used `grep -nE '^FROM eclipse-temurin:'` followed by `grep -vE ':[^[:space:]]*-noble([[:space:]]|$)'`. Two problems surfaced when I simulated against an unpinned Dockerfile on macOS (BSD grep):
  1. The `grep -[FE]` acceptance check from the executor task context expected bare `grep -E` / `grep -F` invocations (single flag after the dash), not the bundled `-nE` / `-vE` forms — the check returned `0`, blocking the plan's success-criteria contract.
  2. More importantly, when I refactored to `grep -v -F '-noble'`, BSD grep parsed the pattern `-noble` as flags (`-n -o -b -l -e`) and silently returned empty, meaning the guard would not catch an unpinned Dockerfile on a BSD-grep host. This is the exact portability trap commit `f451ff4` documented for the Phase 71-05 build-guard.
- **Fix:** Refactored Task 1's guard to the two-stage extract-then-filter shape that mirrors pom.xml PLAT-07 (commit f451ff4) more faithfully:
  - Stage 1 (extract): `grep -E '^FROM eclipse-temurin:' Dockerfile` — bare `-E` for cross-platform regex.
  - Stage 2 (filter): `grep -v -F -e '-noble'` — bare `-F` for fixed-string match, `-e` separator so the leading-dash pattern is not misparsed as flags.
- **Verification:** Re-ran the guard logic on macOS against both the currently-pinned Dockerfile (PASS) and a simulated unpinned variant (FAIL with the correct offending lines). Both behaviors correct.
- **Files modified:** `.github/workflows/ci.yml` (refactored guard step; comment block updated to explain the `-e` requirement).
- **Commit:** rolled into Task 2 commit `72ba72e` (the same commit also adds the docker-build job; the guard refactor is too tightly coupled with the docker-build introduction to merit a separate commit, and the original Task 1 commit `c446b43` still stands as the introduction-point of the guard job).

No architectural deviations. No Rule 4 (architectural) issues. No checkpoints encountered (plan was fully autonomous as declared).

## Issues Encountered

- The macOS BSD-grep portability trap (documented above as Rule 1 deviation) cost ~1 minute of iteration. The Phase 71-05 SDK lesson (commit f451ff4) caught this before I committed — exactly the value the prior-art reference in the plan was meant to provide.
- `python3 -c "import yaml; ..."` is not available on the local toolchain (PyYAML not installed). Used `ruby -ryaml -e "YAML.load_file(...)"` as an equivalent cross-platform YAML validator. Both Task 1 and Task 2 verified structurally valid via Ruby; the executor task context's `python3 yaml.safe_load` will succeed on `ubuntu-latest` runners where PyYAML is in the base image.

## Acceptance Check Results

After both commits, run from project root:

| Check | Expected | Actual | Pass |
|-------|----------|--------|------|
| `grep -cE 'dockerfile-noble-pin-guard\|docker-build' .github/workflows/ci.yml` | >= 2 | 6 | yes |
| `grep -c 'Phase 78' .github/workflows/ci.yml` | >= 1 | 6 | yes |
| `grep -c 'noble' .github/workflows/ci.yml` | >= 3 | 12 | yes |
| `grep -cE 'grep -[FE]' .github/workflows/ci.yml` | >= 1 | 3 | yes |
| `grep -c '25609204039' .github/workflows/ci.yml` | >= 1 | 2 | yes |
| `grep -c 'pull_request' .github/workflows/ci.yml` | >= 1 | 2 | yes |
| `grep -c '78-CONTEXT' .github/workflows/ci.yml` | >= 1 | 1 | yes |
| YAML structurally valid (Ruby YAML.load_file) | exit 0 | exit 0 | yes |
| `grep -c '^  build-and-test:$' .github/workflows/ci.yml` (preserved) | 1 | 1 | yes |
| `grep -c '^  dockerfile-noble-pin-guard:$' .github/workflows/ci.yml` | 1 | 1 | yes |
| `grep -c '^  docker-build:$' .github/workflows/ci.yml` | 1 | 1 | yes |
| `grep -c 'needs: dockerfile-noble-pin-guard' .github/workflows/ci.yml` | 1 | 1 | yes |
| `grep -c 'docker build -t' .github/workflows/ci.yml` | >= 1 | 1 | yes |
| `grep -c 'playwright install chromium' .github/workflows/ci.yml` | >= 1 | 1 | yes |
| `grep -c 'docker-build-log' .github/workflows/ci.yml` | >= 1 | 1 | yes |
| `grep -c 'docker/setup-buildx-action' .github/workflows/ci.yml` (NOT introduced) | 0 | 0 | yes |
| `grep -c 'docker/login-action' .github/workflows/ci.yml` (NOT introduced) | 0 | 0 | yes |
| `grep -c 'set -euo pipefail' .github/workflows/ci.yml` | >= 1 | 2 | yes |
| `grep -c '\[Phase 78 build-guard\]' .github/workflows/ci.yml` | >= 2 | 3 | yes |

Post-commit deletion check: no files deleted across either of the two commits.

Functional smoke-test of the guard logic (run locally on macOS / BSD grep, simulating what the GH runner will do on `ubuntu-latest` / GNU grep):

| Scenario | Expected | Observed | Pass |
|----------|----------|----------|------|
| Current Dockerfile (both stages -noble pinned) | guard passes | PASS, both FROM lines logged | yes |
| Simulated unpinned both stages (`:25-jdk`, `:25-jre`) | guard fails, both lines reported | FAIL, both lines correctly flagged | yes |
| Simulated mixed (`:25-jdk-noble`, `:25-jre`) | guard fails, only the unpinned line reported | FAIL, only `:25-jre` flagged | yes |

## User Setup Required

None. The two new jobs run automatically on the existing workflow trigger (`on.push: master` / `on.pull_request: master`). No GitHub secrets needed (no registry push). No branch-protection update required by this plan (a separate operations step, if the user wants to make the guard a required check, can add `dockerfile-noble-pin-guard` and `docker-build` to the branch-protection required-status-checks set — explicitly out of scope per phase boundary).

## Next Phase Readiness

- **Plan 78-03 can proceed:** it handles REQUIREMENTS.md / ROADMAP.md updates (PLAT-CI-02 traceability) and orchestrates the post-merge release-pipeline verification of criterion 3. The CI surface that Plan 03 references is now in place.
- **No post-merge action is required between Plan 02 and Plan 03** — the new ci.yml jobs are inert until the first PR is opened, and the phase orchestrator can chain straight into 78-03 without waiting for CI feedback on this branch.
- **The eventual PR (containing all three phase commits) will exercise both new jobs on itself**, which is the first end-to-end signal that the guard regex is correct and the `docker build .` on a `-noble` base actually succeeds on ubuntu-latest. If `dockerfile-noble-pin-guard` or `docker-build` fail red on the phase PR, that is in itself critical signal (Plan 03 verification surface) — but it would also indicate a Plan 78-01 / 78-02 regression that needs a fix commit before merge.

## Threat Surface Scan

Per the plan's `<threat_model>` STRIDE register:

- **T-78-05** (Tampering — CI bypass via guard removal): **accept** — guard removal is a visible diff under `.github/workflows/`, caught in code review.
- **T-78-06** (Tampering — silent base-image rotation within Noble): **mitigate (partial)** — `-noble` suffix narrows the surface; the docker-build job catches functional breaks within the Noble line on PR.
- **T-78-07** (DoS — release pipeline regression): **mitigate** — the new docker-build job runs the same `docker build .` as release.yml on every PR, so the exact failure mode of run `25609204039` is caught pre-merge.
- **T-78-08** (Info disclosure via docker-build-log artifact): **accept** — no secrets enter the log path (no login, no env-var printing). The image is public-repo build context only.

No new threat surface introduced beyond the plan's register. No `threat_flag:` entries needed.

## Self-Check: PASSED

- File `.github/workflows/ci.yml` exists and contains both new jobs (verified by greps above).
- Commit `c446b43` exists in `git log` on branch `gsd/v1.10-platform-and-backup`.
- Commit `72ba72e` exists in `git log` on branch `gsd/v1.10-platform-and-backup`.
- File `.planning/phases/78-docker-release-image-fix/78-02-SUMMARY.md` will be committed via the plan-metadata commit appended after this self-check.

---
*Phase: 78-docker-release-image-fix*
*Completed: 2026-05-11*
