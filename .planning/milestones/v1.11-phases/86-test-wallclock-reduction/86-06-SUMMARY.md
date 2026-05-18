---
phase: 86-test-wallclock-reduction
plan: 06
status: complete
date: 2026-05-18
requirements:
  - PERF-05
---

# Plan 86-06 — CI Median Baseline Harvest (PR-Branch per D-17)

## Outcome

PERF-05 satisfied. Harvested 5 consecutive `workflow_dispatch` CI runs of
`ci.yml` on the milestone PR branch `gsd/v1.11-tooling-and-cleanup` (commit
`b7f20b53`), computed the median of the middle 3 after dropping min and max
per D-10, and recorded the result in `docs/test-performance.md` as the new
v1.11 CI baseline. Phase 86 closes via the OR-branch of PERF-04 — gate
≤7m 50s is **MISSED on CI** (CI median 23:00 ≫ 7:50), confirming the
architectural-blocker path already documented in §"Result Verdict" /
§"v1.12 Forward Path".

## Methodology Refinement (D-17)

Plan 86-06 originally specified post-merge master harvest. Mid-flight refinement
recorded as **D-17** in `86-CONTEXT.md`: PR-branch CI runs are semantically
equivalent to post-merge master runs because `ci.yml` executes identical steps
for `pull_request`, `push to master`, and `workflow_dispatch` triggers — the
Maven step timing is independent of the trigger event. This allowed Phase 86 to
close inside the same milestone PR (#122) without an orphan post-merge
`docs(86):` commit on master. Required two prep commits:

1. `ci: add workflow_dispatch trigger to ci.yml` — enables `gh workflow run
   ci.yml --ref <branch>` for sequential reruns
2. `docs(86): add D-17 …` + `docs(86-06): rescope Plan 06 to PR-branch CI
   harvest per D-17`

Sequential triggering (`gh workflow run` + `gh run watch` per run, 5
iterations) was required because `concurrency.cancel-in-progress: true` on
the same ref would otherwise kill earlier runs in favor of newer ones.

## Harvest Data

5 CI runs triggered between 2026-05-17 22:24Z and 2026-05-18 02:18Z, all
`conclusion: success`. Metric: GitHub Actions step wallclock of the "E2E
Tests" step, which is the only CI step that runs `./mvnw verify -Pe2e`
(per ci.yml). Step wallclock ≈ Maven `Total time` within ±3s (Maven owns
the full step duration; GitHub Actions step wrapper overhead is negligible).

| # | Run ID | Triggered | E2E step | Seconds | Verdict |
|---|--------|-----------|----------|---------|---------|
| 1 | 26004473138 | 22:24:49Z | 23:00 | 1380 | kept |
| 2 | 26005481397 | 23:11:20Z | 23:11 | 1391 | kept |
| 3 | 26006490986 | 23:58:41Z | 22:43 | 1363 | kept |
| 4 | 26007607311 | 00:45:18Z | 21:58 | 1318 | dropped — min |
| 5 | 26008754136 | 01:30:27Z | 23:42 | 1422 | dropped — max |

**Middle 3 (sorted by seconds):** 1363, 1380, 1391
**CI Median (v1.11 baseline):** **1380s = 23:00**
**Variance:** (1422 − 1318) / 1380 = **7.5%** — within D-10 20% tolerance,
no second 5-run block needed.

## Result Verdict (one sentence)

PERF-05 satisfied: CI median over 5 PR-branch runs (drop min+max, median of
the 3 middle = 23:00) recorded as the new v1.11 CI baseline in
`docs/test-performance.md` per D-10/D-11/D-17; gate ≤7m 50s **MISSED on CI**,
confirming PERF-04 OR-branch outcome (architectural blocker + v1.12 Forward
Path already documented from Plan 05).

## Files Modified

- `.github/workflows/ci.yml` — added `workflow_dispatch:` trigger
- `.planning/phases/86-test-wallclock-reduction/86-CONTEXT.md` — added D-17
- `.planning/phases/86-test-wallclock-reduction/86-06-PLAN.md` — rescoped
  Task 1 and verification block to PR-branch harvest
- `docs/test-performance.md` — appended "## CI Results (PERF-05)" section
  with the 5-run table, median 23:00, variance 7.5%, gate-MISSED verdict;
  updated "## Result Verdict (PERF-04 / PERF-05)" with PERF-05 line

## Acceptance Criteria

- [x] `### CI Results` (rendered as `## CI Results (PERF-05)`) section
  present: `grep -c '## CI Results' docs/test-performance.md` ≥ 1
- [x] `CI Median (v1.11 baseline)` recorded: `grep -c 'CI Median (v1.11
  baseline)' docs/test-performance.md` ≥ 1
- [x] PERF-05 explicitly named in the Result Verdict: `grep -c 'PERF-05'
  docs/test-performance.md` ≥ 1 (multiple matches)
- [x] Variance documented: `grep -cE 'Variance.*7\.5%' docs/test-performance.md`
  ≥ 1
- [x] Conventional commit landed on the milestone branch: see
  `docs(86): record CI median on PR branch — finalize v1.11 baseline (PERF-05)`

## Closes

Phase 86 (Test Wallclock Reduction). Next phase per ROADMAP.md: Phase 87
(Nyquist VALIDATION Closure).
