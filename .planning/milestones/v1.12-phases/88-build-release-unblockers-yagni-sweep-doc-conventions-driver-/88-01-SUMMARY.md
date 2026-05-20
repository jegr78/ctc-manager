---
phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver
plan: 01
subsystem: testing
tags: [jacoco, spotbugs, maven, ci-baseline, jdt-cache]

requires:
  - phase: 80-openrewrite-integration
    provides: 2026-05-16 JDT-cache diagnosis documenting that the BackupSchemaExclusionIT.java:40 "compile error" is an Eclipse JDT IDE-cache artifact, not a javac failure
provides:
  - Verified v1.12-baseline build (./mvnw clean verify -Pe2e exit 0, LINE coverage 89.01 %, SpotBugs 0 findings)
  - CLEAN-01 closed in REQUIREMENTS.md with cross-ref to Phase-80 deferred-items.md
affects: [88-02, 88-03, 88-04, 88-05, 88-06, 89, 90, 91]

tech-stack:
  added: []
  patterns:
    - "Plan acceptance: when a plan's `./mvnw clean verify` claim references a documented baseline measured under -Pe2e, run with -Pe2e too — e2e tests contribute ~0.5 pp LINE coverage"

key-files:
  created:
    - .planning/milestones/v1.12-phases/88-build-release-unblockers-yagni-sweep-doc-conventions-driver-/88-01-SUMMARY.md
  modified:
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Plan awk-command targets columns 4/5 (INSTRUCTION) but acceptance text says LINE coverage — used columns 8/9 (LINE) for the threshold check; the awk inconsistency in the plan is documented here but not silently corrected in the plan file"
  - "Plan task asked for `./mvnw clean verify` (no profile); v1.11 ship-baseline 88.88 % was measured under -Pe2e, so verification re-ran with -Pe2e to honour the acceptance numeric (89.01 % LINE ≥ 88.88 %)"

patterns-established:
  - "Pattern: when a plan acceptance claim cites a STATE.md-documented baseline, verify the baseline-measurement profile (e2e/non-e2e) BEFORE flagging a regression"

requirements-completed:
  - CLEAN-01

duration: 19min
completed: 2026-05-19
---

# Phase 88-01: CLEAN-01 Closure Summary

**v1.12 PR-branch HEAD verified clean (./mvnw clean verify -Pe2e exit 0, JaCoCo LINE 89.01 %, SpotBugs 0); CLEAN-01 status flipped to Resolved with Phase-80 JDT-cache cross-ref.**

## Performance

- **Duration:** 19 min (2× full verify cycles — first without -Pe2e, second with)
- **Started:** 2026-05-18T20:46:00Z
- **Completed:** 2026-05-19T05:14:00Z
- **Tasks:** 2
- **Files modified:** 1 (`.planning/REQUIREMENTS.md`)

## Accomplishments
- Confirmed Phase-80 JDT-cache diagnosis still applies — no javac-level compile error in `BackupSchemaExclusionIT.java:40` at v1.12 PR-branch HEAD
- Established v1.12 clean-verify baseline: LINE 89.01 %, INSTRUCTION 88.06 %, BRANCH 76.68 %, 1683 tests (1403 Surefire + 280 Failsafe, of which 38 e2e), build 9:18 min, SpotBugs `BugInstance size is 0`
- Closed CLEAN-01 in REQUIREMENTS.md (`[ ]` → `[x]` + parenthetical with cross-ref) and updated traceability row (`Pending` → `Resolved`)

## Task Commits

Each task was committed atomically:

1. **Task 88-01-01: Run clean verify and capture JaCoCo baseline** — no commit (verification-only, source tree unchanged)
2. **Task 88-01-02: Flip REQUIREMENTS.md CLEAN-01 status to Resolved** — pending commit (this task)

## Files Created/Modified
- `.planning/REQUIREMENTS.md` — CLEAN-01 bullet checkbox `[ ]`→`[x]` + parenthetical with Phase-80 cross-ref + traceability row `Pending`→`Resolved`
- `.planning/milestones/v1.12-phases/88-build-release-unblockers-yagni-sweep-doc-conventions-driver-/88-01-SUMMARY.md` — this file

## Decisions Made
- **Plan awk-command vs. plan-text mismatch:** Plan task action says "column 4 (LINE_MISSED) + column 5 (LINE_COVERED)", but those columns hold INSTRUCTION counts in the JaCoCo CSV (LINE_MISSED/LINE_COVERED are columns 8/9). Treated the textual "LINE coverage" requirement as authoritative and used columns 8/9 for the threshold check. The plan's awk formula is left untouched (no source edits per D-02).
- **Profile choice:** First `./mvnw clean verify` (no profile) gave LINE 88.47 %, INSTRUCTION 87.40 %, 1645 tests — under the ≥88.88 % acceptance numeric. v1.11 STATE.md-quoted 88.88 % is implicitly the e2e measurement (CI runs `./mvnw verify -Pe2e`), so re-ran with `-Pe2e` and got 89.01 % LINE, 1683 tests. User approved the re-run path.

## Deviations from Plan

### Auto-fixed Issues

**1. [Profile-Drift] Switched verify command from `./mvnw clean verify` → `./mvnw clean verify -Pe2e`**
- **Found during:** Task 1 acceptance-numeric check
- **Issue:** Plan task action ran `./mvnw clean verify` without `-Pe2e`. That produces LINE 88.47 % (−0.41 pp vs. STATE.md-documented v1.11 baseline of 88.88 %), which fails the plan's ≥88.88 % acceptance.
- **Fix:** Re-ran `./mvnw clean verify -Pe2e` per user approval; LINE 89.01 % ≥ 88.88 % satisfied. The plan acceptance was implicitly written against the e2e baseline (CI runs `-Pe2e`).
- **Files modified:** none (verification-only)
- **Verification:** `awk -F, 'NR>1 {m+=$8; c+=$9} END {printf "%.2f%%\n", c/(m+c)*100}' target/site/jacoco/jacoco.csv` → `89.01%`
- **Committed in:** N/A (this deviation has no code-side commit)

---

**Total deviations:** 1 auto-fixed (profile choice)
**Impact on plan:** Plan acceptance honoured. Recorded the awk vs. text inconsistency as a finding for the user.

## Issues Encountered
- **JaCoCo CSV awk-column ambiguity in plan text:** The plan's task-action describes columns 4/5 as `LINE_MISSED`/`LINE_COVERED`, but the JaCoCo CSV format places those at columns 8/9 (4/5 are INSTRUCTION). Resolved by using the textual "LINE coverage" semantic over the column-number formula. Not fixed in the plan (frozen artifact).

## User Setup Required
None — verification-only + 1 doc edit. No new dependencies, no environment changes.

## Next Phase Readiness
- Clean v1.12 baseline established and verified: `./mvnw clean verify -Pe2e` exits 0 at HEAD `890181d2` + this plan's REQUIREMENTS.md commit. Ready for Plan 88-02 (CLEAN-02 + CLEAN-03 YAGNI sweep) against this baseline.
- Coverage numbers above (89.01 % LINE / 88.06 % INSTRUCTION / 76.68 % BRANCH / 1683 tests / 9:18 min build) form the pre-CLEAN-02 reference; CLEAN-02 deletes ~3 disabled tests and should not regress these.

---
*Phase: 88-build-release-unblockers-yagni-sweep-doc-conventions-driver*
*Completed: 2026-05-19*
