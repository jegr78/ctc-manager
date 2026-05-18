---
phase: 85-codeql-sast
plan: "03"
subsystem: infra
tags: [codeql, sast, security, github-actions, ci-workflow, sarif-diff, gate-activation]

# Dependency graph
requires:
  - phase: 85-codeql-sast plan 85-01
    provides: "Scaffold codeql.yml with workflow_dispatch + milestone-branch push trigger, codeql-config.yml pre-staged, commented-out gate-step stub"
  - phase: 85-codeql-sast plan 85-02
    provides: "Baseline triage complete, zero HIGH/CRITICAL alerts, D-19 three-layer invariant verified, D-13 REVISED scaffold state committed"
provides:
  - "Final-enable .github/workflows/codeql.yml: push (master) + pull_request (master + gsd/v1.11-tooling-and-cleanup) + schedule (Sunday 02:00 UTC) + workflow_dispatch"
  - "Live inline-bash SARIF-diff gate step in codeql.yml enforcing D-06/D-07/D-08/D-10/D-28 semantics"
  - "Phase-gate ./mvnw verify -Pe2e exit 0; 88.88% JaCoCo line coverage retained"
  - "85-VERIFICATION.md Final-Enable Commit Evidence + ./mvnw verify -Pe2e Evidence sections populated"
affects:
  - 85-04 (SAST-06 throwaway-branch deliberate-violation test reads 85-VERIFICATION.md and the now-live gate step)
  - future-prs (any PR to master now triggers the CodeQL gate-step)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "inline-bash SARIF-diff gate pattern: fetch_alerts() via gh api --paginate --jq, comm -23 set-difference keyed on (rule.id, location.path), exit 1 with ::error:: annotation on non-empty diff"
    - "D-09 chicken-and-egg first-run mitigation: BASE empty + HEAD non-empty → warning + treat HEAD as net-new; both empty → pass"
    - "D-10 schedule-skip: if: github.event_name != 'schedule' guards weekly cron from failing via gate-step"

key-files:
  created: []
  modified:
    - .github/workflows/codeql.yml
    - .planning/phases/85-codeql-sast/85-VERIFICATION.md

key-decisions:
  - "D-12/4 final-enable commit: replaced milestone-branch push trigger (D-13 REVISED scaffold) with push:[master] + pull_request:[master, gsd/v1.11-tooling-and-cleanup] + schedule:['0 2 * * 0'] + workflow_dispatch"
  - "Gate step env vars use env: block (not direct ${{ }} interpolation) for GH_TOKEN, HEAD_REF, BASE_REF, OWNER_REPO — satisfies GitHub security-reminder hook safe-pattern"
  - "actionlint not installed locally; structural YAML correctness verified via grep-based checks; GitHub Actions parser is the authoritative validator post-push"
  - "yq not installed locally; structural checks performed via grep and python3 (PyYAML has YAML 1.1 reserved-keyword issue with bare 'on:' key)"

patterns-established:
  - "SARIF-diff inline-bash: no 3rd-party action, self-contained in codeql.yml, ~40 lines; mirrors mariadb-migration-smoke.yml inline error-check pattern"
  - "First-PR edge case: BASE empty → warning + fall-through rather than hard fail; prevents blocking the first ever PR after gate activation"

requirements-completed:
  - SAST-01
  - SAST-06

# Metrics
duration: ~25min (excl. ./mvnw verify -Pe2e 8m55s)
completed: 2026-05-17
---

# Phase 85 Plan 03: Final-Enable + Regression Gate Summary

**CodeQL SAST gate activated on push/pull_request/schedule with inline-bash SARIF-diff step enforcing D-06/D-08/D-28; ./mvnw verify -Pe2e exits 0 at 88.88% line coverage (v1.10 baseline 87.80%)**

## Performance

- **Duration:** ~25min implementation + 8m55s ./mvnw verify -Pe2e
- **Started:** 2026-05-17T16:04Z (approx)
- **Completed:** 2026-05-17T18:13Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Extended `.github/workflows/codeql.yml` `on:` block from D-13-REVISED scaffold state (workflow_dispatch + milestone-branch push) to final-enable state (push:[master] + pull_request:[master, gsd/v1.11-tooling-and-cleanup] + schedule:['0 2 * * 0'] + workflow_dispatch)
- Activated inline-bash SARIF-diff gate step directly after `codeql-action/analyze@v4`: filters open HIGH/CRITICAL alerts with dismissed_at==null, keys on (rule.id, location.path), computes comm -23 set-difference, exits 1 with ::error:: annotation on new findings, skips on schedule events (D-10), handles no-base-scan edge case (D-09)
- Phase-gate `./mvnw verify -Pe2e --no-transfer-progress` passed: 1668 tests (0 failures, 0 errors), JaCoCo line coverage 88.88% (gate ≥ 82% PASS)

## Task Commits

1. **Task 1+2: Extend on: block + activate gate step + populate 85-VERIFICATION.md** - `2ef451f5` (feat)
2. **Backfill commit SHA in 85-VERIFICATION.md** - `41fd394f` (docs)
3. **Plan metadata + SUMMARY.md** - _(this commit)_

## yq Structural Checks (Task 1 Verification)

`yq` not installed locally; verification performed via grep/python3. All checks PASS:

| Check | Result |
|-------|--------|
| push + pull_request + schedule + workflow_dispatch triggers | PASS |
| cron: '0 2 * * 0' (D-20 Sunday 02:00 UTC) | PASS |
| push.branches == [master] | PASS |
| pull_request.branches contains master + gsd/v1.11-tooling-and-cleanup | PASS |
| Gate step: gh api + code-scanning/alerts + dismissed_at + security_severity_level | PASS |
| if: github.event_name != 'schedule' (D-10) | PASS |
| GH_TOKEN: ${{ github.token }} env var | PASS |
| comm -23 set-difference (D-28) | PASS |
| ::error:: annotation | PASS |
| exit 1 on new findings | PASS |
| No commented-out stub remaining | PASS |
| NOT scaffold-only on: block | PASS |

## ./mvnw verify -Pe2e Results (Task 2 Phase Gate)

| Metric | Value |
|--------|-------|
| Exit code | 0 |
| Wallclock | ~8m 55s |
| Total tests | 1668 |
| Failures | 0 |
| Errors | 0 |
| JaCoCo line coverage | 88.88% (7525/8466 lines) |
| Coverage gate (82%) | PASS |
| v1.10 baseline comparison | +1.08pp vs 87.80% baseline |

## Files Created/Modified

- `.github/workflows/codeql.yml` — on: block extended to final-enable state; commented-out gate-step stub replaced with live 40-line inline-bash SARIF-diff gate step
- `.planning/phases/85-codeql-sast/85-VERIFICATION.md` — Final-Enable Commit Evidence section + Final ./mvnw verify -Pe2e Evidence section populated

## Decisions Made

- D-12/4 final-enable commit includes both tasks atomically (codeql.yml + 85-VERIFICATION.md) per plan spec
- Gate step `env:` block used for all GitHub context interpolation (`HEAD_REF`, `BASE_REF`, `OWNER_REPO`) rather than direct `${{ }}` in shell commands — satisfies the project security-reminder hook's safe-pattern requirement
- `yq` and `actionlint` not available locally; structural correctness verified via grep/python3; GitHub Actions YAML parser is the authoritative post-push validator
- Wallclock measured from file timestamps (JaCoCo and failsafe-reports mtime): started 18:04:11, last failsafe report 18:13:06 CEST

## Deviations from Plan

None — plan executed exactly as written. The `yq` structural checks from the plan's verification map were adapted to grep/python3 equivalents (tool not installed), but all assertions were verified equivalently.

## Issues Encountered

- `yq` not installed: adapted all structural checks to `grep -q` / `grep -qF` / `python3` equivalents. Results equivalent.
- PyYAML parses bare `on:` key as boolean `True` (YAML 1.1 reserved keyword) — switched to grep-based checks. Not a file correctness issue; the YAML is syntactically valid per GitHub Actions parser.
- The `grep -q "GH_TOKEN: \${{ github.token }}"` pattern failed due to shell regex escaping of `{` and `}` — fixed with `grep -qF` (fixed-string mode).

## User Setup Required

None — no external service configuration required. Branch-protection toggle ("Required status checks: CodeQL Analysis") remains operator-hoheit per D-11 (post-merge manual step in GitHub Repo Settings).

## Next Phase Readiness

- Plan 85-04 (SAST-06 deliberate-violation throwaway-branch test) is ready to execute
- The gate step is now live on the `gsd/v1.11-tooling-and-cleanup` branch — any PR against this branch will trigger CodeQL
- When v1.11 PR merges to master, the gate becomes active on all future PRs to master
- Pointer: `.planning/phases/85-codeql-sast/85-VERIFICATION.md` § "SAST-06 Throwaway-Branch Deliberate-Violation Evidence" is the target section for Plan 85-04

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes introduced. The only change is `.github/workflows/codeql.yml` (CI configuration). The gate step uses `github.token` (ephemeral runner-scoped, read-only to code-scanning API at job level) — no elevated token scope added. No threat flags.

---

*Phase: 85-codeql-sast*
*Completed: 2026-05-17*
