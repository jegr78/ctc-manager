# Test Performance Log (Phase 86)

**Baseline date:** 2026-05-17
**v1.10 reference:** 11m 11s (Phase 79 verification doc — historical reference only)
**Phase-86 goal:** `./mvnw verify -Pe2e` wallclock ≤ 7m 50s (≥30% reduction from v1.10 reference)
  OR document the specific architectural blocker plus a concrete v1.12 forward path
  (D-04 OR-branch; expected realistic outcome per D-15: 20-25% reduction → 8m30s-9m).

This document is the Phase-86 deliverable for `PERF-02` (ApplicationContext counts),
`PERF-04` (wallclock measurements), and `PERF-05` (CI median validation). Plans 02-05
append into the appropriate sections without rewriting the skeleton.

---

## Baseline (D-09)

Local 3-run baseline established on `worktree-agent-aaf5570d6f34ab708` (branch
`gsd/v1.11-tooling-and-cleanup`, base commit `21dc88db` plus Wave-1
ContextLoadCountListener). Command per run:

```bash
{ time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev; } \
    > .test-perf-logs/baseline-run-<N>.log 2>&1
```

Logs are kept outside `target/` because `mvn clean` wipes `target/` before any redirect
into it can be closed. PID-keyed context-load tallies are copied to
`.test-perf-logs/test-perf-run-<N>/` after each run.

| Run | Maven Total time | bash `real`     | Context loads | Notes                                                       |
| --- | ---------------- | --------------- | ------------- | ----------------------------------------------------------- |
| 1   | 09:01            | 543s (9m 03s)   | 81            | BUILD SUCCESS, no flakes                                    |
| 2   | 09:45            | 586s (9m 46s)   | 81            | BUILD SUCCESS, no flakes (one earlier attempt retried — see below) |
| 3   | 09:53            | 595s (9m 55s)   | 81            | BUILD SUCCESS, no flakes                                    |

**Median (Maven): 09:45 (run 2).** **Median (bash real): 586s = 9m 46s.**
**Baseline context-load count:** 81 (across all Surefire/Failsafe forks, stable across the 3 runs).

**Retry note (Run 2):** The first Run-2 attempt failed at 05:21 inside Surefire
with `TeamProfilePageGeneratorTest.setUp:77 » Playwright Error … Page.captureScreenshot:
Unable to capture screenshot`. This is a pre-existing intermittent Playwright Chromium
screenshot flake already documented in `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-03-SUMMARY.md`
("DriverProfilePageGeneratorTest screenshot failure … pre-existing intermittent flaky
test"). Per Plan 86-01 §Task 2 protocol — *"If any run fails for unrelated reasons
(flake, environment), repeat — D-09 explicitly requires 3 successful baseline runs,
not 3 attempts"* — the run was retried and succeeded.

**Re-baseline finding vs. v1.10:** The 09:45 Phase-86 median is ~13% **faster** than
the Phase-79 11m 11s reference. Phases 80-85 (SpotBugs/find-sec-bugs gate, CodeQL
compile, Renovate config, OpenRewrite recipe runs) did not lengthen the local Surefire+Failsafe
suite. The v1.10 11m 11s number becomes historical reference only; the Phase-86
post-optimization target should be evaluated against **09:45 baseline** (≥30% from
there ≈ ≤6m 49s) rather than against the older 11m 11s number — final goal interpretation
is fixed by D-09 + D-11 in `86-CONTEXT.md` (CI is source of truth, ≤7m 50s is the
gate).

**Per-fork context-load breakdown** (stable across runs 1-3):

| Fork purpose | PID-keyed contribution | Notes |
| ------------ | ---------------------- | ----- |
| Surefire fork A (unit, `forkCount=2`)         | 7-9 | Bootstrap + unit ITs that bring up a Spring context (e.g., `db.migration` tests) |
| Surefire fork B (unit, `forkCount=2`)         | 15-17 | Same fork class, different test partition |
| Failsafe `default-it` fork (`forkCount=1C`)   | 27 | `@SpringBootTest`-driven IT cluster — main `@DirtiesContext` cost center |
| Failsafe `e2e-it` fork (`-Pe2e`)              | 27 | `@SpringBootTest(RANDOM_PORT)` Playwright walkthroughs |
| One short-lived helper fork                   | 3 | Probably the Spring-Boot test resolver running a 3-context bootstrap; persists ≤1s after task |
| **Total**                                     | **81** | Independent of run-to-run variance — reproducible signal |

The 27 + 27 = 54 contexts inside the Failsafe forks are the primary target for Wave-2
work: 11 `@DirtiesContext` sites + 3 `@SpringBootTest` repository ITs slated for
`@DataJpaTest` conversion. The 81-count baseline is the reference against which
Plan 05 will report the post-optimization delta.

---

## Post-Optimization Wallclock (Wave 3)

_(populated by Plan 05)_

| Run | Maven Total time | bash `real` | Context loads | Notes |
| --- | ---------------- | ----------- | ------------- | ----- |
| 1   | —                | —           | —             | —     |
| 2   | —                | —           | —             | —     |
| 3   | —                | —           | —             | —     |

**Median:** _(populated by Plan 05)_

---

## Context Load Counts (PERF-02)

| Measurement Point        | Context Loads | Run Command                              |
| ------------------------ | ------------- | ---------------------------------------- |
| Pre-audit baseline (D-09) | 81            | `./mvnw clean verify -Pe2e --no-transfer-progress` (median across 3 runs) |
| Post-optimization        | —             | _(populated by Plan 05)_                 |

PID-keyed marker files are emitted by `org.ctc.testsupport.ContextLoadCountListener`
(registered via `src/test/resources/META-INF/spring.factories`) at JVM shutdown to
`target/test-perf/context-loads-{PID}.txt`. The total context-load count is computed
as the sum of integer contents across all marker files after the build:

```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  TOTAL=$((TOTAL + $(cat "$f")))
done
echo "Total context loads: $TOTAL"
```

The marker files do not contain trailing newlines, so `paste -sd+ - | bc` reads the
file contents as a single concatenated digit string and produces the wrong number;
the loop form above (or equivalent) is the correct aggregation.

---

## Per-Decision Evidence (D-03 / D-04 / D-06)

_(populated by Plans 02-04 summaries)_

| Decision | Cluster | Evidence | Outcome | Source plan |
| -------- | ------- | -------- | ------- | ----------- |
| D-04     | Sitegen `@DirtiesContext` (7 files)     | —     | — | Plan 02     |
| D-03     | Backup IT `@DirtiesContext` (3 files)   | —     | — | Plan 02     |
| D-05/D-06 | Phase repository `@DataJpaTest` (3 files) | — | — | Plan 03     |
| D-04 (TestDataServiceIntegrationTest)   | Defensive `@DirtiesContext` (1 file) | — | — | Plan 02     |

---

## v1.12 Forward Path

If the ≤7m 50s gate is not reached in Phase 86, the following three structural levers
become candidates for v1.12. Estimated-delta, effort, risks, and required touchpoints
will be populated by Plan 05 once post-audit wallclock data is in. The lever order
is set by D-14 (`data/dev/backup-staging/` per-fork refactor is Top-1) and
`86-CONTEXT.md` §Deferred Ideas.

| Lever | Estimated Wallclock Delta | Effort (S/M/L) | Risks/Dependencies | Required Touchpoints |
| ----- | ------------------------- | -------------- | ------------------ | -------------------- |
| 1. Per-fork `data/dev/backup-staging/` refactor (the Top-1 v1.12 lever per D-14) — unlocks Failsafe `forkCount>1C` for backup ITs without staging-dir races | _(Plan 05)_ | _(Plan 05)_ | _(Plan 05)_ | `BackupImportService`, `BackupStagingCleanup`, Failsafe surefire-fork-numbering system-property propagation |
| 2. Shared `@SpringBootTest` `@ContextConfiguration` strategy — explicit shared configuration classes across IT clusters to maximize Spring TCF cache reuse | _(Plan 05)_ | _(Plan 05)_ | _(Plan 05)_ | All IT clusters (sitegen, backup, admin) |
| 3. Testcontainers MariaDB `withReuse(true)` — `~/.testcontainers.properties` for warm-container startups; only relevant once MariaDB ITs exist (none in v1.11) | _(Plan 05)_ | _(Plan 05)_ | _(Plan 05)_ | Testcontainers setup, `~/.testcontainers.properties`, MariaDB IT introduction |
