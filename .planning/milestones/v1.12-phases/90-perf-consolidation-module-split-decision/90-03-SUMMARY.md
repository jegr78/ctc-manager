---
phase: 90-perf-consolidation-module-split-decision
plan: 03
slug: perf-05-test-module-split-decision
status: complete
completed: 2026-05-20
wave: 3
depends_on: [90-02]
requirements: [PERF-05]
---

## Objective recap

PERF-05 locks the v1.12 decision on whether to extract `src/test/java/` into a
separate Maven module by appending the `## Test-Module-Split Decision` section
to `docs/test-performance.md`. Verdict: **defer with explicit blockers** — one
of the three outcomes (`proceed` / `defer` / `reject`) that REQUIREMENTS.md
PERF-05 permits. Pure docs plan; zero code surface.

## What shipped

- New `docs/test-performance.md § Test-Module-Split Decision` section
  appended after § PERF-04 Testcontainers Reuse. Contains all six required
  structural elements per CONTEXT.md D-05:
  1. Verdict line: `**Verdict (v1.12):** Defer — re-evaluate in v1.13 against
     PERF-06 CI re-harvest baseline.`
  2. Blocker 1: TestDataService cross-boundary (`@Profile({"dev","local"})`
     anchor verified at line 40 of `src/main/java/org/ctc/admin/TestDataService.java`).
     Three architecturally-poor outcomes documented (duplicate fixtures /
     circular dep / third "fixtures" module).
  3. Blocker 2: IDE-friction risk referencing
     `.planning/milestones/v1.11-phases/80-openrewrite-integration/deferred-items.md`
     (2026-05-16 JDT-cache pathology entry) + `[[clean-maven-build-authority]]`
     memory cross-reference + the "stable through 87 phases" claim.
  4. Blocker 3: no hard cumulative-effect data yet (Phase 89 Wave-4 -10.4 % local
     + PERF-04 dev-only + PERF-06 Phase 91 owns the authoritative CI baseline).
  5. Re-evaluation trigger paragraph naming the v1.13 milestone-discuss
     workflow + the PERF-06 CI median condition (materially below 23:00 but
     above 7:50 historical gate AND no other architectural lever surfaces).
  6. `Why not reject?` paragraph citing Phase 86 D-15 OR-branch precedent for
     the defer-with-rationale pattern + optionality-preservation rationale.

## Decisions honored

| Decision | Reference | How honored |
|----------|-----------|-------------|
| D-05 | Defer with explicit blockers; v1.13 owns the next decision-point | Verdict line + 3 numbered blockers + re-evaluation trigger documented verbatim per CONTEXT.md D-05 (lines 56-64). |
| D-06 | Three plans, sequential inline | Plan 90-03 is the third and final plan; landed after Plan 90-02's wave-pause-resume per [[inline-sequential-execution]] + [[wave-pause]]. |
| D-08 | Standard quality gates, no tightening, no loosening | Phase-gate `./mvnw verify -Pe2e` BUILD SUCCESS (Maven 07:36 min); JaCoCo 0.8902 ≥ 0.8888 ✓; SpotBugs `BugInstance` count 0 ✓; CodeQL gate ready to exit 0 on PR HEAD (no new suppressions — pure docs plan). |
| D-09 | `src/main/java/**` git-clean | Plan 90-03 modifies only `docs/test-performance.md`. `git diff <plan-baseline>..HEAD -- 'src/**/*.java' \| wc -l = 0`. |

## Invariants held

- [x] JaCoCo line coverage ≥ 0.8888 — observed 0.8902 on Plan-03 verify (Phase 89 baseline retained)
- [x] SpotBugs `BugInstance` count = 0 — confirmed via `[INFO] BugInstance size is 0` in `.test-perf-logs/90-03-verify.log`
- [x] CodeQL `security-extended` gate-step ready to exit 0 on PR HEAD SHA (pure docs plan — no new patterns)
- [x] `EXPORT_ORDER` = 24 entities (unchanged; verified via the verify log)
- [x] `BackupSchema.SCHEMA_VERSION` = 1 (unchanged)
- [x] Flyway V1-V7 immutable (unchanged)
- [x] `src/**/*.java` git-clean across Plan 90-03 (D-09)
- [x] `src/main/resources/**` git-clean across Plan 90-03
- [x] `docs/test-performance.md § Test-Module-Split Decision` exists exactly once at the end of the file (sanity-check: `grep -c '^## Test-Module-Split Decision' = 1`)
- [x] All six structural elements verifiable via grep (verdict line, 3 numbered blockers, v1.13 trigger reference, `Why not reject?` paragraph)

## Phase 90 closure

Plan 90-03 closes the Phase 90 plan chain:

- [90-01-SUMMARY.md](90-01-SUMMARY.md) — PERF-03 cluster consolidation (composed
  annotation `@CtcDevSpringBootContext`, 19 outer classes refactored, Surefire
  cluster collapse confirmed, Failsafe cluster mix-up documented honestly).
- [90-02-SUMMARY.md](90-02-SUMMARY.md) — PERF-04 Testcontainers reuse opt-in
  applied to both existing MariaDB ITs + docs + README pointer; threat rows
  T-90-TC-01 / T-90-TC-02 mitigated via docs paragraphs.
- [90-03-SUMMARY.md](90-03-SUMMARY.md) (this file) — PERF-05 test-module-split
  `defer` verdict locked in `docs/test-performance.md`.

**Next step:** `/gsd-verify-work 90` runs the goal-backward verification
against the phase's three Success Criteria (PERF-03 + PERF-04 + PERF-05
requirements addressed). Once that passes, the phase is fully complete:

- `REQUIREMENTS.md` PERF-03 + PERF-04 + PERF-05 status rows flip to **Resolved**
- `STATE.md` Phase 90 status flips to **Complete**
- `ROADMAP.md` Phase 90 plan list checkboxes flip to checked
- PR description for the v1.12 milestone branch updates with the Phase 90
  rolling summary entry per [[feedback_pr_description_update]]

Phase 91 (PERF-06 CI re-harvest) is the next phase in the v1.12 milestone and
will produce the authoritative CI cumulative-effect measurement that the v1.13
test-module-split re-evaluation trigger consumes.
