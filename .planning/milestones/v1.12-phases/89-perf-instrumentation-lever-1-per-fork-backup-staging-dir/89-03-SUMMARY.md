---
phase: 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
plan: 03
slug: wave-4-measurement-docs
status: complete
completed: 2026-05-19
wave: 3
depends_on:
  - 89-01
  - 89-02
requirements:
  - PERF-01
  - PERF-02
---

# Plan 89-03 — Wave-4 acceptance measurement + docs + README

## Objective recap

PERF-01 + PERF-02 acceptance: 3 local `./mvnw clean verify -Pe2e` runs per the Phase-86 D-09 idle protocol, honest delta-vs-10:24 reporting (D-02 — no hard local gate), `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` populated, Lever-1 in `§ v1.12 Forward Path` annotated DONE, `README.md § Test Performance` pointer added.

## What shipped

- 3-run Wave-4 wallclock measurement with PERF-02 active proof per run.
- `docs/test-performance.md` — new `## Post-Optimization Wallclock (Wave 4)` section + Lever-1 DONE annotation + status paragraph.
- `README.md` — new `## Test Performance` pointer section after `## Documentation`.
- Phase 90 PERF-03 hand-off data: Top-5 cache-key clusters captured below.

## Wave-4 numbers

| Run | Maven Total time | bash `real`         | Context loads | JaCoCo | Notes |
|-----|------------------|---------------------|---------------|--------|-------|
| 1   | 08:50            | 531.68s (8m 51.68s) | 55            | 0.8902 | BUILD SUCCESS, no flakes |
| 2   | 09:19            | 560.04s (9m 20.04s) | 55            | 0.8905 | BUILD SUCCESS, no flakes |
| 3   | 09:50            | 591.28s (9m 51.28s) | 56            | 0.8902 | BUILD SUCCESS, no flakes |

- **Median (Maven):** 09:19 (run 2)
- **Median (bash real):** 560.04 s = 9m 20s
- **Delta vs. Phase-86 post-audit 10:24 (624s) baseline:** **-10.4 %** improvement
- **JaCoCo min:** 0.8902 (gate ≥ 0.8888 held)
- **Context-load count median:** 55 (Phase-86 post-audit median: 79 — net -30 %)

Per-run logs persisted under `.test-perf-logs/89-03-wave4-run-{1,2,3}.log` (+ `…-metrics.txt`).

## PERF-02 Top-5 cluster output (Phase 90 PERF-03 hand-off)

```text
# Top 5 cache-key clusters by occurrence x cluster-size
# Source: 5 sidecar file(s) in target/test-perf

1. 9cefac4c -- 29 occurrences across 29 classes (score=841)
   [WebMergedContextConfiguration@... testClass = db.migration.V5MigrationTest
2. 499c01dd -- 12 occurrences across 12 classes (score=144)
   [WebMergedContextConfiguration@... testClass = org.ctc.backup.exception.Bac
3. 2cb78737 -- 12 occurrences across 12 classes (score=144)
   [WebMergedContextConfiguration@... testClass = org.ctc.admin.SecurityIntegr
4. f524774b -- 10 occurrences across 10 classes (score=100)
   [WebMergedContextConfiguration@... testClass = db.migration.V4MigrationSmoke
5. 5ff2b420 --  7 occurrences across  7 classes (score=49)
   [WebMergedContextConfiguration@... testClass = org.ctc.e2e.AdminWorkflowE2E
```

Targets identified for Phase 90 PERF-03: db.migration cluster (V5+V4 = 39 combined occurrences) is the standout consolidation candidate; the backup-exception and admin-security clusters share secondary positions.

## Decisions honored

| Decision | Honored? | Evidence |
|----------|----------|----------|
| D-02 — honest reporting vs. 10:24 baseline, no hard local gate | ✓ | Wave-4 delta computed as `(560-624)/624` = -10.4 % vs. 10:24 (NOT vs. 09:45 pre-audit). Documented as observational. |
| D-08 — primary marker `total <N>` Line 1, sidecar fingerprint file | ✓ | All 3 runs: primary `head -1 \| grep '^total \d+$'` exit 0; sidecar `grep '^[0-9a-f]{1,8}\t' \| head -1` exit 0. |
| D-14 — production yml + BackupStagingCleanup.java + ImportLockService.java untouched | ✓ | `git diff origin/master..HEAD -- src/main/resources/application*.yml src/main/java/org/ctc/backup/service/BackupStagingCleanup.java src/main/java/org/ctc/backup/lock/ImportLockService.java` is EMPTY. |
| D-15 — JaCoCo ≥ 0.8888 | ✓ | Min 0.8902 across 3 runs. |
| D-16 — doc scope: docs/test-performance.md + README.md; CLAUDE.md untouched | ✓ | `git diff origin/master..HEAD -- CLAUDE.md` is EMPTY. |

## Invariants held

- ✓ JaCoCo line coverage ≥ 0.8888 on all 3 runs (min 0.8902).
- ✓ SpotBugs `BugInstance` count = 0 (verified `[INFO] BugInstance size is 0` in each run log).
- ✓ Production yml files git-clean across Phase 89 branch (D-14).
- ✓ `BackupStagingCleanup.java` git-clean (D-14).
- ✓ `ImportLockService.java` git-clean (Plan 89-01 Task 4 was test-scope only — deadline bumped in `ImportLockedPostRejectorIT.java`, not the production service).
- ✓ `CLAUDE.md` git-clean (D-16).
- ✓ PERF-02 instrumentation alive on all 3 runs (D-08 marker + sidecar gate exit 0 each time).

## Phase 89 closure

- **Phase 90 PERF-03** consumes the Top-5 cluster output (above) for targeted-consolidation work. Primary candidate: `db.migration.V5MigrationTest` cluster (29 classes sharing one cache key → consolidation onto a shared `@ContextConfiguration` could compress 29 individual context bootstraps into fewer cache buckets).
- **Phase 91 PERF-06** re-harvests the CI authoritative median against Phase-86's 23:00 CI baseline. Local Wave-4 median (09:19, -10.4 %) is observational; CI is the source of truth for cumulative v1.12 wallclock claims.

## Open follow-ups

- **DevDataSeeder Chromium pressure** (recorded in Plan 89-01 SUMMARY) — `@DirtiesContext` rebuild + 14+ `Playwright.create()` calls per Spring-context rebuild stretches MVC dispatch latency. Plan 89-01 Task 4 chose option (c) deadline bump (`LOCK_ACQ_DEADLINE_SECONDS = 20L`) with named constant + Javadoc explaining the root cause. Root-cause fix (cache rendered PNGs / move team-card generation off the per-context rebuild path) is Phase 90+ tech debt.
- **`BackupImportServiceIT` `Files.list(stagingDir).count() == 0` fragility** (recorded in Plan 89-01 SUMMARY) — tightening to `.filter(p -> p.getFileName().toString().startsWith("upload-"))` is out-of-scope for Phase 89.

## Files modified (Plan 89-03 only)

- `docs/test-performance.md` — new Wave-4 section + Lever-1 DONE.
- `README.md` — new `## Test Performance` pointer section.
- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` (this file).

## Reference

- [[89-CONTEXT]] — D-02, D-08, D-14, D-15, D-16
- [[89-01-SUMMARY]] — predecessor: PERF-01 per-fork refactor
- [[89-02-SUMMARY]] — predecessor: PERF-02 instrumentation
- [[89-FLAKE-DIAGNOSTIC]] — Attempt-1 + Attempt-2 findings consumed
- [[86-CONTEXT]] — D-09 idle protocol used verbatim
