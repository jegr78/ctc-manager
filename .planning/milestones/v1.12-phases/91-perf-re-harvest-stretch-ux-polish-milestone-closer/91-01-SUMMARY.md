---
phase: 91-perf-re-harvest-stretch-ux-polish-milestone-closer
plan: 01
subsystem: testing
tags: [perf, ci, measurement, workflow_dispatch, baseline-swap, milestone-pr]

requires:
  - phase: 86-test-wallclock-reduction
    provides: 23:00 v1.11 CI baseline + 5-run methodology + D-17 trigger-equivalence
  - phase: 89-perf-instrumentation-and-lever-1
    provides: PERF-01 per-fork backup-staging-dir + PERF-02 fingerprint listener
  - phase: 90-perf-consolidation-and-module-split-decision
    provides: PERF-03 composed @CtcDevSpringBootContext + PERF-04 Testcontainers reuse opt-in + PERF-05 module-split defer verdict
provides:
  - v1.12 CI E2E-step baseline 17:39 (1059s)
  - v1.12 milestone Draft PR #129 opened with rolling body
  - STATE.md baseline swap (23:00 → 17:39)
  - PROJECT.md Key Decisions trend row (v1.12 PERF-06 baseline)
  - docs/test-performance.md § PERF-06 Re-Harvest section
affects: [91-02, 91-03, v1.13]

tech-stack:
  added: []
  patterns:
    - "D-17 trigger-equivalence harvest: PR-branch workflow_dispatch ≡ post-merge master CI"
    - "Background nohup harvest harness: gh workflow run + gh run watch --exit-status --interval 30 in sequential loop"
    - "Step-timing extraction via gh run view --json jobs (BSD-grep-safe, more precise than [INFO] Total time:)"

key-files:
  created:
    - .planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-01-SUMMARY.md
  modified:
    - docs/test-performance.md (+37 lines; new § PERF-06 Re-Harvest)
    - .planning/STATE.md (1-line swap; 23:00 → 17:39 baseline)
    - .planning/PROJECT.md (+1 row; Key Decisions trend row)
    - .planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-VALIDATION.md (BSD-grep fix + 4 Plan 91-01 rows ✅ green)

key-decisions:
  - "Step-timing API (gh run view --json jobs) over Maven [INFO] Total time: log parse — more precise, less fragile, no BSD-grep portability issues"
  - "Variance 18.2% within 20% tolerance → no second 5-run block (D-03 honest-observational; explicit override of Phase 86 D-10 auto-retry)"
  - "Median 17:39 materially below 23:00 (Δ−23.3%) → standard outcome, no D-10b No-Improvement subsection"
  - "VALIDATION.md row-2 grep corrected from BSD-incompatible \\s to alternation pattern (Planner defect; corrected in-place since the artifact is part of Plan 91-01 deliverables)"

patterns-established:
  - "Background harvest harness (nohup bash + log file) — survives Claude session boundaries; cleanly resumable via 'done' signal"
  - "Rolling PR body (per [[pr-description-update]]) — placeholder anchors on Task 1, replaced LANDED text on Task 4"

requirements-completed:
  - PERF-06

nyquist_compliant: true

duration: ~3h 15min (mostly CI harvest wallclock)
completed: 2026-05-20
---

# Phase 91 — Plan 01 Summary

**v1.12 CI E2E-step baseline re-harvested at 17:39 (1059s); 23.3 % reduction vs v1.11 23:00 baseline; cumulative effect of Phases 89 + 90 PERF-01..05 validated via D-17 trigger-equivalent 5-run workflow_dispatch harvest on milestone Draft PR #129.**

## Performance

- **Duration:** ~3h 15min (Task 1+3+4+5 active orchestrator work ≈ 15min; remainder = Task 2 CI harvest wallclock — ran detached in background)
- **Started:** 2026-05-20T10:39:32Z (Task 1 PR creation)
- **Task 2 harvest:** 2026-05-20T10:39:32Z → 13:37:53Z (3h 0min, 5 sequential `workflow_dispatch` runs)
- **Completed:** 2026-05-20 (Task 5 SUMMARY commit)
- **Tasks:** 5/5
- **Files modified:** 4 (3 tracked + 1 new SUMMARY)

## Accomplishments

- **5/5 CI runs success** on `gsd/v1.12-driver-import-and-test-perf` HEAD SHA `b63a2be1`. All triggered via `workflow_dispatch`; serialized via `gh run watch --exit-status` to honor `concurrency.cancel-in-progress: true` (Pitfall 1).
- **CI E2E-step median 17:39 (1059s)** — median of the 3 kept values (1015/1059/1072) after dropping min (929s, Run 2) and max (1122s, Run 4).
- **Variance 18.2 %** ((1122 − 929) / 1059) — within D-10 20 % tolerance, no second 5-run block (D-03 explicit override of Phase 86 D-10 auto-retry).
- **Δ−23.3 % vs v1.11 baseline (23:00 → 17:39)** — materially below, no D-10b No-Improvement subsection needed.
- **Cumulative levers landed:** Phase 89 PERF-01 (per-fork backup-staging-dir) + PERF-02 (fingerprint listener); Phase 90 PERF-03 (composed `@CtcDevSpringBootContext`) + PERF-04 (Testcontainers `.withReuse(true)` opt-in) + PERF-05 (module-split defer verdict).

## CI Harvest Detail (Task 2)

PR_NUMBER: **129** — PR_HEAD_SHA: **`b63a2be102640c26c826e5cecf4d52825f51db75`** (`b63a2be1`)

| Run | Run ID | Started (UTC) | E2E step end (UTC) | mm:ss | Sec | Disposition |
| --- | ------ | ------------- | ------------------ | ----- | --- | ----------- |
| 1 | [26157245962](https://github.com/jegr78/ctc-manager/actions/runs/26157245962) | 2026-05-20T10:58:23Z | 11:16:02Z | 17:39 | 1059 | kept (median) |
| 2 | [26159013829](https://github.com/jegr78/ctc-manager/actions/runs/26159013829) | 2026-05-20T11:32:26Z | 11:47:55Z | 15:29 |  929 | dropped — min |
| 3 | [26160533478](https://github.com/jegr78/ctc-manager/actions/runs/26160533478) | 2026-05-20T12:05:29Z | 12:22:24Z | 16:55 | 1015 | kept |
| 4 | [26162245258](https://github.com/jegr78/ctc-manager/actions/runs/26162245258) | 2026-05-20T12:41:31Z | 13:00:13Z | 18:42 | 1122 | dropped — max |
| 5 | [26164197273](https://github.com/jegr78/ctc-manager/actions/runs/26164197273) | 2026-05-20T13:19:12Z | 13:37:04Z | 17:52 | 1072 | kept |

**Sorted seconds:** 929 / 1015 / 1059 / 1072 / 1122
**Kept (middle 3):** 1015 / 1059 / 1072 — median **1059s = 17:39**
**Variance:** (1122 − 929) / 1059 = 18.2 % (within D-10 20 % tolerance)
**vs 23:00 v1.11:** −321s (−23.3 % reduction)

## Task Commits

1. **Task 1 — Open Draft PR #129:** no commit (remote-only PR creation; `gh pr create`)
2. **Task 2 — 5 sequential workflow_dispatch CI runs:** no commit (out-of-process CI harvest)
3. **Task 3 — docs/test-performance.md § PERF-06 Re-Harvest:** [`b206d34c`](https://github.com/jegr78/ctc-manager/commit/b206d34c) (`docs(91-01): land PERF-06 5-run CI re-harvest § (median 17:39, was 23:00 v1.11)`)
4. **Task 4 — STATE.md baseline swap + PROJECT.md trend row + PR body LANDED edit:** [`c9dfcbf1`](https://github.com/jegr78/ctc-manager/commit/c9dfcbf1) (`docs(91-01): swap CI E2E baseline 23:00→17:39 in STATE.md + append v1.12 trend row in PROJECT.md (PERF-06 D-04)`)
5. **Task 5 — Nyquist self-validation + SUMMARY:** _this commit_

## D-13 Invariant — `src/main/java` git-clean

Plan 91-01 introduced **zero** changes under `src/main/java`. Verified via:

```bash
$ git diff --name-only HEAD~3..HEAD -- src/main/java
(no output)
```

The Plan 91-01 commits land only in `docs/`, `.planning/STATE.md`, `.planning/PROJECT.md`, and `.planning/milestones/v1.12-phases/91-…/` (PR body + remote PR are out-of-tree). D-13 invariant held — PERF measurements ran against an unchanged production baseline; the −23.3 % CI reduction is attributable to Phase 89 + 90 work that landed before Plan 91-01 opened.

## Nyquist Self-Validation

All 4 Plan 91-01 per-task verification commands from `91-VALIDATION.md` Plan 91-01 section returned 0:

1. ✅ `## PERF-06 Re-Harvest` heading + median text present in `docs/test-performance.md`
2. ✅ `v1.12 baseline` text present in `.planning/STATE.md` (BSD-grep-safe pattern after VALIDATION.md fix)
3. ✅ 5 `workflow_dispatch` runs on `gsd/v1.12-driver-import-and-test-perf` — all `conclusion: success`
4. ✅ PR #129 is Draft and on the correct head ref

VALIDATION.md row statuses for Plan 91-01 flipped from `⬜ pending` → `✅ green`. Frontmatter `nyquist_compliant: true` declared at SUMMARY scope; phase-level frontmatter remains `false` until Plans 91-02 + 91-03 also pass.

**Planner-defect fix landed in this plan:** `91-VALIDATION.md` row-2 grep used `\s` which BSD grep (Darwin) does not support — replaced with alternation pattern (`v1\.12 baseline|v1\.12.*CI median|CI median.*v1\.12 baseline`) which is portable across BSD + GNU grep. Future plans should prefer `[[:space:]]*` or literal-space patterns over `\s`.

## Decisions Made

- **Step-timing API over log parse (Pitfall 2 mitigation).** Plan called for `gh run view <id> --log | grep '[INFO] Total time:'`. Switched to `gh run view <id> --json jobs --jq '.jobs[] | select(.name == "build-and-test") | .steps[] | select(.name == "E2E Tests") | …'` because (a) it returns precise ISO-8601 timestamps, not parsed mm:ss strings, (b) `gh run view --log` downloads ~50 MB per run and is slow, (c) it avoids BSD-grep portability issues. Result aligns with v1.11 PERF-05 methodology (E2E-step wallclock per Step duration).
- **18.2 % variance accepted in-tolerance (D-03 in action).** (1122 − 929) / 1059 = 18.2 %. Phase 86 D-10 would have triggered a 2nd 5-run block at this level; Phase 91 D-03 explicitly overrides — accept and continue. No retry; ~2h saved.
- **Background harvest harness over interactive watching.** User chose "Variant A (orchestrator drives)" → `nohup bash /tmp/perf-06-harvest.sh > /tmp/perf-06-harvest.log 2>&1 &` survived the conversation pause window between Task 1 and "done" signal.

## Deviations from Plan

- **Plan Task 5 step (2) — Nyquist validation via SDK call.** `gsd-sdk query validate.plan --phase 91 --plan 01` is not implemented (`Unknown validate subcommand`). Fallback per plan: ran the 4 per-task grep commands from `91-VALIDATION.md` directly; all returned 0. Also fixed a BSD-incompatible grep in row 2 (`\s` → alternation) so the validation is reproducible across operator environments. Net effect: Nyquist self-validation succeeds; planner defect captured and corrected.
- **Plan Task 3 reference to insertion location.** Plan said "after the existing § CI Results (PERF-05) section (line 183-end)". Actual insertion landed between line 234 (existing `---` separator after PERF-05) and line 235 (next `## Context Load Counts (PERF-02)` section). Preserved append-only convention (37 additions, 0 deletions).

## Issues Encountered

- **Markdown-lint MD060 (table column style "compact") + MD032 (list spacing).** First-pass table rows had trailing padding spaces and one Cumulative-levers continuation line began with `+ PERF-04 …` which markdown-lint interpreted as a list-item marker. Both fixed in-place (compact padding + re-worded continuation without leading `+`); no behavior change.
- **24 unpushed commits before Task 1.** Local branch was 24 commits ahead of `origin/gsd/v1.12-driver-import-and-test-perf` (Phase 90 + Phase 91 planning work). Pushed before `gh pr create` per Plan Pre-condition. All commits passed conventional-commit format.

## User Setup Required

None — Plan 91-01 is docs/CI/PR-mechanics only.

## Next Phase Readiness

**Plan 91-02 (UX-01 typed-exception hierarchy + flash UX badges) is unblocked.** PR #129 rolling body documents Plan 91-02 as the next landing.

**Operator pause per [[wave-pause]]:** Orchestrator stops here for user feedback before opening Plan 91-02. No blockers; Plan 91-01 closes cleanly.

---
*Phase: 91-perf-re-harvest-stretch-ux-polish-milestone-closer*
*Plan: 01 — PERF-06 CI Re-Harvest*
*Completed: 2026-05-20*
