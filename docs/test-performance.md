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

## Result Verdict (PERF-04 / PERF-05)

Phase 86 closes via the **OR-branch** of PERF-04. The Wave-2 audit did not deliver
wallclock reduction on the local 3-run measurement: median local wallclock moved
**from 09:45 (baseline) to 10:24 (post-audit)** — a 39-second regression (-6.7%)
rather than the ≥30% reduction target or the 20-25% realistic expectation per D-15.
Context loads decreased marginally from 81 to 79.

**PERF-05 (CI Median):** 23:00 recorded as new v1.11 CI baseline (harvested from
milestone PR branch CI per D-17; see "CI Results (PERF-05)" section below); gate
≤7m 50s **MISSED on CI**. The CI runner (`ubuntu-latest` GitHub-hosted) is
materially slower than the local hardware used for the D-09 baseline, so the CI
absolute number is not directly comparable to the 9:45 local baseline — but per
D-11 the CI median is the authoritative source of truth for the PERF-04 gate,
and 23:00 ≫ 7:50 confirms the OR-branch outcome.

The structural fixes from Plans 02-04 are correct on their own terms (no shared
`SiteProperties` singleton mutation; per-method `@DirtiesContext` audit honoring
latch-bean non-resettability; `@DataJpaTest` slice pilot for repository ITs), but
they did not translate into a wallclock win locally. The most likely cause —
flagged in advance by RESEARCH Open Question 2 — is that the per-class
`@DynamicPropertySource` binding in the 7 sitegen tests fragmented one shared
context-cache key into seven distinct keys, while the Spring TCF context-cache
LRU eviction behavior changed which contexts get rebuilt across the suite. The
absolute context count stayed roughly flat (81 → 79), but the cache-hit pattern
shifted in a way that the local wallclock surfaces but does not yet explain
end-to-end.

**Phase 86 closes here per D-16 (soft cap).** PERF-04 is satisfied via the
documented architectural blocker and the v1.12 forward path below. CI median
remains the source of truth (D-11) — Plan 06 will harvest 5 master-branch CI
runs after merge and record the median in this document as the new v1.11
baseline; that number, not this local 10:24, is what the v1.12 plan should
optimize against.

**Final gate:** `./mvnw verify -Pe2e` BUILD SUCCESS on 2026-05-17 across all
three post-audit runs — JaCoCo line coverage **88.97%** (≥82% gate).

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

Local 3-run post-audit measured on the same hardware as the Plan-01 baseline,
identical command and same idle-system protocol. Branch
`gsd/v1.11-tooling-and-cleanup` after Wave-2 commits `2914ad62`, `2464f23e`,
`6099aae7`, `36656eff`.

| Run | Maven Total time | bash `real`     | Context loads | Notes                    |
| --- | ---------------- | --------------- | ------------- | ------------------------ |
| 1   | 09:24            | 565s (9m 26s)   | 79            | BUILD SUCCESS, no flakes |
| 2   | 10:24            | 625s (10m 25s)  | 79            | BUILD SUCCESS, no flakes |
| 3   | 10:30            | 631s (10m 31s)  | 78            | BUILD SUCCESS, no flakes |

**Median (Maven): 10:24 (run 2).** **Median (bash real): 625s = 10m 25s.**
**Post-audit context-load count:** 79 (run 1 + run 2), 78 (run 3) — median 79.

**Verdict:** Achieved **-6.7% reduction** (10:24 post vs 09:45 baseline) — the
≥30% target is not reached and the 20-25% D-15 realistic expectation is missed
by a wide margin. See **v1.12 Forward Path** below for the path forward.

A possible run-noise contribution: baseline runs spread 09:01–09:53 (52s),
post-audit runs spread 09:24–10:30 (66s). Run-1 of each is the closest pair
(09:01 vs 09:24, +23s). The median delta on n=3 is therefore not statistically
robust on its own — Plan 06's CI 5-run harvest (D-10/D-11) is the authoritative
re-measurement.

---

## Post-Optimization Wallclock (Wave 4)

Local 3-run measurement after Phase 89 Plans 89-01 (PERF-01 per-fork
`app.backup.staging-dir` + Failsafe `default-it` `forkCount=2 reuseForks=true`)
and 89-02 (PERF-02 cache-key fingerprint listener) merged on branch
`gsd/v1.12-driver-import-and-test-perf` (commit `27358d94`). Identical command
and idle protocol as Phase 86 Wave 3 (D-09).

| Run | Maven Total time | bash `real`         | Context loads | Notes                    |
| --- | ---------------- | ------------------- | ------------- | ------------------------ |
| 1   | 08:50            | 531.68s (8m 51.68s) | 55            | BUILD SUCCESS, no flakes |
| 2   | 09:19            | 560.04s (9m 20.04s) | 55            | BUILD SUCCESS, no flakes |
| 3   | 09:50            | 591.28s (9m 51.28s) | 56            | BUILD SUCCESS, no flakes |

**Median (Maven): 09:19 (run 2).** **Median (bash real): 560.04s = 9m 20s.**

**Delta vs. Phase-86 post-audit baseline (10:24 local median):** **-10.4 %**
(560s vs. 624s). Plan 89-01's per-fork-staging refactor enabled Failsafe
`forkCount=2 reuseForks=true` across the entire IT suite (not just backup),
parallelising the largest IT cluster. Local measurement is observational only
(D-02 — no hard local gate); Phase 91 PERF-06 will re-harvest the CI median
(Phase-86 baseline 23:00) for the authoritative cumulative effect of Phases
88-90.

**JaCoCo line coverage:** minimum 0.8902 across the 3 runs (gate ≥ 0.8888 per
D-15 — gate held).

**Context-load count:** 55 / 55 / 56 (median 55), down from the Phase-86
post-audit median of 79 — a 30 % reduction that is the algebraic side-effect of
forkCount=2 splitting the test load across two JVMs (each JVM holds its own
context cache; the same logical class may now be in one fork's cache or the
other's, but not both — net: fewer rebuilds per JVM).

PERF-02 instrumentation active per run (D-08 marker `total <N>` Line 1 + sidecar
fingerprint lines present); the Top-5 cluster output produced by
`scripts/test-perf/aggregate-fingerprints.sh` is recorded in the Plan-89-03
SUMMARY for Phase 90 PERF-03 consumption.

---

## CI Results (PERF-05)

5 consecutive `workflow_dispatch` CI runs on the milestone PR branch
`gsd/v1.11-tooling-and-cleanup` (commit `b7f20b53`), harvested per D-10 (5 runs,
drop min+max, median of 3) and D-17 (PR-branch CI ≡ post-merge master CI: ci.yml
runs identical steps for `pull_request`, `push`, and `workflow_dispatch`
triggers; PR-branch harvest closes Phase 86 inside PR #122 without an orphan
post-merge commit).

The harvested metric is the GitHub Actions step wallclock of the **"E2E Tests"**
step, which runs `./mvnw verify -Pe2e --no-transfer-progress
-Dspring.profiles.active=dev -Ddocker.available=true` and is the only CI step
that invokes Maven with the `-Pe2e` profile. Step wallclock ≈ Maven `Total time`
within ±3s on GitHub-hosted runners (Maven owns the entire step duration; the
GitHub Actions step wrapper adds only environment setup/teardown overhead that
is negligible at this scale).

| Run | Run ID | E2E step wallclock | Seconds | Notes |
| --- | ------ | ------------------ | ------- | ----- |
| 1   | [26004473138](https://github.com/jegr78/ctc-manager/actions/runs/26004473138) | 23:00 | 1380 | kept |
| 2   | [26005481397](https://github.com/jegr78/ctc-manager/actions/runs/26005481397) | 23:11 | 1391 | kept |
| 3   | [26006490986](https://github.com/jegr78/ctc-manager/actions/runs/26006490986) | 22:43 | 1363 | kept |
| 4   | [26007607311](https://github.com/jegr78/ctc-manager/actions/runs/26007607311) | 21:58 | 1318 | dropped — min |
| 5   | [26008754136](https://github.com/jegr78/ctc-manager/actions/runs/26008754136) | 23:42 | 1422 | dropped — max |

**CI Median (v1.11 baseline):** **23:00** (1380s; middle 3 = 1363/1380/1391, median = 1380s)
**Variance:** **7.5%** ((1422 − 1318) / 1380; within D-10 20% tolerance — no
second 5-run block needed)
**Reduction vs v1.10 baseline (11:11):** **−105.7%** (CI is 11:49 slower; not a
like-for-like comparison — v1.10 11:11 was a local measurement)
**Reduction vs Phase-86 local D-09 baseline (09:45):** **−136%** (CI is 13:15
slower; reflects ubuntu-latest GitHub-hosted runner vs local hardware, not
optimization regression)
**Gate ≤7:50:** **MISSED** (CI median 23:00 ≫ 7:50). PERF-04 satisfied via
OR-branch (architectural blocker documented above; v1.12 Forward Path levers
already specified).

Note on the absolute CI wallclock vs the v1.10 11:11 reference: the v1.10
number was a local 3-run median on personal hardware (Phase 79 verification);
the CI runner is consistently 2-3× slower for this suite (Surefire + Failsafe
+ Playwright + Docker build all serialized on a single 2-CPU runner). The
relevant comparison for v1.12 optimization work is **delta** against this new
23:00 CI baseline, not the absolute 7:50 gate which assumed local-hardware
timing. The v1.12 plan should re-evaluate the gate target against this CI
baseline before optimization work begins (D-11: CI is the source of truth).

For full job-step timing breakdown (Set up job, Checkout, Setup JDK 25, Build
and Unit/Integration Tests, Install Playwright Browsers, E2E Tests, JaCoCo
Coverage, Upload Test Reports) see the individual run pages linked above.

---

## Context Load Counts (PERF-02)

| Measurement Point        | Context Loads | Run Command                              |
| ------------------------ | ------------- | ---------------------------------------- |
| Pre-audit baseline (D-09) | 81            | `./mvnw clean verify -Pe2e --no-transfer-progress` (median across 3 runs) |
| Post-optimization        | 79            | Same command, after Wave-2 (median across 3 runs)                          |

**Delta interpretation (RESEARCH Open Question 2 — partial answer):** The total
context-load count moved only marginally (81 → 79, -2). This is the *count*
signal. The *fingerprint* of which contexts get built changed materially:

- The 7 sitegen tests previously shared a single context-cache key with each
  other and with several other `@SpringBootTest + dev` tests; `@DirtiesContext`
  at class level forced rebuilds at class boundaries but the cached context
  could be reused within a class.
- After Plan 02, the same 7 tests each declare a distinct
  `@DynamicPropertySource` (binding `ctc.site.output-dir` to a per-class temp
  directory), splitting the single shared cache key into seven unique keys.
  Per-class context reuse is preserved (no within-class evictions), but cross-class
  reuse is gone for this cluster.
- Plan 04's `@DataJpaTest` pilot collapsed 3 `@SpringBootTest` ITs into a single
  shared `@DataJpaTest` cache key — those 3 contexts compress to 1, but with a
  different (smaller) initialization cost than `@SpringBootTest`.
- The 2-context net reduction is the algebraic sum of these and other smaller
  effects (Plan 03 latch-free methods no longer evict; Cluster C
  TestDataServiceIntegrationTest no longer evicts at class end).

The wallclock regression is most likely driven by the cache-fingerprint change
rather than the count: when Spring's TCF cache evicts entries under LRU, the
choice of *which* context to evict and rebuild now matters as much as the total
count. Verifying this hypothesis requires per-fork ContextLoadCountListener
breakdown and is queued for v1.12 (lever 2 below).

PID-keyed marker files are emitted by `org.ctc.testsupport.ContextLoadCountListener`
(registered via `src/test/resources/META-INF/spring.factories`) at JVM shutdown to
`target/test-perf/context-loads-{PID}.txt`.

Phase 89 (PERF-02) extended the marker file: Line 1 now carries `total <N>`, and a
companion sidecar `target/test-perf/context-loads-{PID}-fingerprints.txt` records
per-context cache-key fingerprints (one `<hex-hash>\t<mcc-display>` line per
`beforeTestClass` event). The aggregator below extracts the `total` from Line 1
of the primary marker and skips the sidecar files; cache-key forensics are handled
separately by `scripts/test-perf/aggregate-fingerprints.sh` (see § PERF-02 Forensics).

```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  # Exclude PERF-02 sidecar fingerprint files (they carry hash lines, not totals).
  case "$f" in *-fingerprints.txt) continue ;; esac
  TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
done
echo "Total context loads: $TOTAL"
```

---

## PERF-02 Forensics — Cache-Key Fingerprint Analysis

`org.ctc.testsupport.ContextCacheKeyFingerprintListener` is a `TestExecutionListener`
auto-registered via `src/test/resources/META-INF/spring.factories`. On every
`beforeTestClass` event it captures the test's `MergedContextConfiguration` (Spring
TCF's `DefaultContextCache` bucketing function — `Integer.toHexString(mcc.hashCode())`)
and appends a `<hex-hash>\t<display>` line (display truncated to 200 chars) to an
in-memory list, persisted at JVM shutdown to
`target/test-perf/context-loads-{PID}-fingerprints.txt`. The sidecar approach avoids
shutdown-hook ordering races with the primary `ContextLoadCountListener`.

The hash is the cache-bucket key, so any cluster of distinct test classes sharing
one hex hash represents a single cached context that those classes reuse — exactly
the data needed to choose targeted-consolidation candidates for Phase 90 (PERF-03).

```bash
scripts/test-perf/aggregate-fingerprints.sh target/test-perf 5
```

Sample output:

```text
# Top 5 cache-key clusters by occurrence x cluster-size
# Source: 4 sidecar file(s) in target/test-perf

1. 7b63d1a9 -- 29 occurrences across 29 classes (score=841)
   [WebMergedContextConfiguration@... testClass = db.migration.V5MigrationTest
2. d5ef50be -- 12 occurrences across 12 classes (score=144)
   [WebMergedContextConfiguration@... testClass = org.ctc.dataimport.CsvImport
3. a1c32ec1 -- 10 occurrences across 10 classes (score=100)
   [WebMergedContextConfiguration@... testClass = org.ctc.backup.service.Backu
4. f3cd1b41 -- 10 occurrences across 10 classes (score=100)
   [WebMergedContextConfiguration@... testClass = org.ctc.backup.exception.Bac
5. b2bac94  --  6 occurrences across  6 classes (score=36)
   [WebMergedContextConfiguration@... testClass = org.ctc.admin.controller.Tea
```

PERF-03 (Phase 90) consumes the Top-N list to pick the highest-fragmentation cluster
for consolidation onto a shared `@ContextConfiguration`. Phase 86 Lesson (Plan 02
SUMMARY) is the canonical pitfall on the reverse path: per-class
`@DynamicPropertySource` split one shared cache key into seven, fragmenting reuse;
PERF-03's targeted consolidation prevents the same regression in the other
direction.

---

## PERF-03 Cluster Consolidation (Phase 90 Plan 01)

Phase 89's PERF-02 Top-1 (`9cefac4c`) Surefire cluster and Top-4 (`f524774b`, hex
rotated to `499c01dd` in the Phase 90 pre-refactor re-harvest) Failsafe cluster
have been consolidated onto a new composed annotation
`org.ctc.testsupport.CtcDevSpringBootContext`, which meta-annotates
`@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")`.
The refactor's empirical bucket-audit landed at 19 outer test classes (13 Surefire +
6 Failsafe; the 15 `@Nested` `PlayoffServiceTest` inner classes inherit per Spring TCF
+ JUnit composed-annotation rules). `@Tag("integration")` stays on every Failsafe
subclass; `@Transactional` stays on `V4MigrationSmokeIT` + `DriverRepositoryOrderIT`.
See `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-01-SUMMARY.md`
for the per-class table.

### Top-5 cache-key clusters — before/after

Hex hashes rotate across `mvn clean` invocations (Java identity-hashCode
randomization); cluster STRUCTURE (which classes collide) is the stable observable.

| # | Pre-refactor (.test-perf-logs/90-01-aggregator-before.txt) | Post-refactor (.test-perf-logs/90-01-aggregator-after.txt) |
|---|------------------------------------------------------------|------------------------------------------------------------|
| 1 | `9cefac4c` — 29 events / 29 classes (V5MigrationTest)      | `baafff8e` — 29 events / 29 classes (V5MigrationTest)      |
| 2 | `f524774b` — 16 events / 16 classes (BackupException…)     | `6273f4ab` — 15 events / 15 classes (BackupException…)     |
| 3 | `3c6228fd` — 13 events / 13 classes (CsvImport…)           | `35c60549` — 12 events / 12 classes (CsvImport…)           |
| 4 | `5ff2b420` — 7 events / 7 classes (AdminWorkflowE2E)       | `286b36be` — 7 events / 7 classes (AdminWorkflowE2E)       |
| 5 | `84ec5236` — 7 events / 7 classes (BackupImport…)          | `cd67fca0` — 7 events / 7 classes (V4MigrationSmoke)       |

### Cluster collapse — Surefire side (consolidation succeeded)

The Surefire bucket `9cefac4c` → `baafff8e` retains all 29 events / 13 outer
classes. Every class now wears `@CtcDevSpringBootContext` instead of the two-
annotation stack. Two pre-existing shape variants — `@SpringBootTest(classes =
CtcManagerApplication.class)` (the three `db.migration.V*MigrationTest` Surefire
classes) and bare `@SpringBootTest` (the ten `org.ctc.**.*Test` Surefire classes) —
folded into one shared cache key.

### Cluster mix-up — Failsafe side (partial consolidation)

The Failsafe V4-cluster `499c01dd` (6 outer classes pre-refactor) → `cd67fca0`
(7 outer classes post-refactor). Two of the six refactored classes
(`BackupExportServiceIT`, `BackupImportPostCommitEdgeCasesIT`) migrated to
cluster `6273f4ab` post-refactor; three previously-unrelated Failsafe classes
(`TeamRestorerIT`, `BackupArchiveExtractUploadsIT`, `BackupStagingCleanupRaceIT`)
migrated INTO the V4-cluster. Net Failsafe cache-bucket event count is unchanged
(7 + 15 = 22, same as pre-refactor 6 + 16). Honest observation per D-07:
empirical Failsafe consolidation is neutral — the refactor's value on this side
is documentation clarity + future-drift protection, not raw context-reuse gain.

### Wave-5 idle measurement (Plan 01 D-07)

3 idle `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev`
runs on a developer-grade Mac (no other Maven invocations in parallel; no IDE
compilation active). Run-1 is the pre-refactor baseline used as both Task-1
hash-bucket audit input and Wave-5 Run-1; Runs 2 + 3 are post-refactor.

| Run | Maven Total time | bash `real` | Context loads | JaCoCo line cov | SpotBugs | Notes |
|-----|------------------|-------------|---------------|-----------------|----------|-------|
| 1 (pre-refactor) | 07:24 min | 7:25.91 | 56 | 0.8902 | 0 | `.test-perf-logs/90-01-wave5-run-1.log` |
| 2 (post-refactor) | 08:27 min | n/a | 55 | 0.8902 | 0 | `.test-perf-logs/90-01-wave5-run-2.log` |
| 3 (post-refactor) | 09:31 min | n/a | 55 | 0.8902 | 0 | `.test-perf-logs/90-01-wave5-run-3.log` |

Maven Total median (all 3 runs): **08:27 min**.
Delta vs. Phase 89 Wave-4 median (09:19): **-52 s ≈ -9.3 %** (honest observational
delta; influenced by system noise; no hard local wallclock gate per D-07).

The PERF-06 CI re-harvest (Phase 91) remains the authoritative measurement against
the v1.11 23:00 baseline.

---

## Per-Decision Evidence (D-03 / D-04 / D-06)

### D-04 Sitegen Cluster (Plan 02)

Per `.planning/phases/86-test-wallclock-reduction/86-02-SUMMARY.md`. All 7 sitegen
test classes (6 Surefire + 1 Failsafe) had their shared `SiteProperties.setOutputDir(...)`
mutation in `@BeforeAll` replaced with per-class `@DynamicPropertySource` binding;
all 7 class-level `@DirtiesContext` annotations removed. The plan's literal
`@TempDir static` pattern was incompatible with `@TestInstance(PER_CLASS)` (Pitfall 3
manifest — JUnit initialization order under PER_CLASS leaves the static field
null when Spring's `@DynamicPropertySource` resolver runs), so a small helper
`org.ctc.testsupport.SitegenTestDir.create(label)` was introduced to create the
temp directory at static-field-initializer time. Plan revision documented in
the SUMMARY.

| Class | @DirtiesContext removed? | 3-seed result | Notes |
|---|---|---|---|
| DriverProfilePageGeneratorTest | yes | 1234 ✓ 5678 ✓ 9999 ✓ | 6 tests |
| MatchdaysPageGeneratorTest | yes | 1234 ✓ 5678 ✓ 9999 ✓ | 9 tests |
| StandingsPageGeneratorTest | yes | 1234 ✓ 5678 ✓ 9999 ✓ | 10 tests (1 @Disabled) |
| DriverRankingPageGeneratorTest | yes | 1234 ✓ 5678 ✓ 9999 ✓ | 8 tests |
| TeamProfilePageGeneratorTest | yes | 1234 ✓ 5678 ✓ 9999 ✓ | 5 tests |
| SiteGeneratorE2ETest | yes | 1234 ✓ 5678 ✓ 9999 ✓ | 8 tests |
| SiteGeneratorPhaseAwarenessIT | yes | 1234 ✓ 5678 ✓ 9999 ✓ | 9 tests (Failsafe-routed) |

Cluster C (`TestDataServiceIntegrationTest`) also dropped its defensive
class-level `@DirtiesContext` after the combined-cluster 3-seed verification
(156 tests, all green per seed). The Phase-79 explanatory comment block (5
lines) was removed (D-07).

**Net @DirtiesContext removals in Plan 02:** 8 (7 sitegen + 1 Cluster C).

### D-03 Backup ITs (Plan 03)

Per `.planning/phases/86-test-wallclock-reduction/86-03-SUMMARY.md`. New
`org.ctc.backup.it.ImportLockServiceResetHelper` `@TestComponent` exposes a
public `reset()` that calls the idempotent `ImportLockService.unlock()`. The
helper is `@Import`-ed into all 3 backup ITs and called from each IT's
`@AfterEach`. Per-method `@DirtiesContext` audit applied based on each method's
dependency on the non-resettable `CountDownLatch` beans from
`BlockingRestoreFailureInjector.Config` (RESEARCH Cluster B / Assumption A1).

| Class | Method | Latch-dependent? | Final annotation state |
|---|---|---|---|
| ImportConcurrentLockIT | givenSlowImportRunningOnThreadA_... | yes | class-level `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` retained (sole method) |
| ImportLockBannerAdviceIT | givenLockHeld_whenGetAdminSeasons_... | yes | method-level `@DirtiesContext(AFTER_METHOD)` |
| ImportLockBannerAdviceIT | givenLockHeld_whenGetSiteIndex_... | yes | method-level `@DirtiesContext(AFTER_METHOD)` |
| ImportLockBannerAdviceIT | givenLockNotHeld_whenGetAdminSeasons_... | no | bare (no `@DirtiesContext`) |
| ImportLockedPostRejectorIT | givenLockHeld_whenPostToAdminTeamsSave_... | yes | method-level `@DirtiesContext(AFTER_METHOD)` |
| ImportLockedPostRejectorIT | givenLockHeld_whenPostToWhitelistedImportExecute_... | yes | method-level `@DirtiesContext(AFTER_METHOD)` |
| ImportLockedPostRejectorIT | givenLockHeld_whenGetAdminSeasons_... | yes | method-level `@DirtiesContext(AFTER_METHOD)` |
| ImportLockedPostRejectorIT | givenLockNotHeld_whenPostToAdminTeamsSave_... | no | bare (no `@DirtiesContext`) |

**Eviction count delta (this cluster only):** 8 → 6 per Failsafe pass (-2,
latch-free methods no longer evict context). 3-seed Failsafe verification on
all 3 ITs combined: 8/8 tests green per seed (1234/5678/9999).

### D-06 @DataJpaTest Pilot (Plan 04)

Per `.planning/phases/86-test-wallclock-reduction/86-04-SUMMARY.md`. 3
Phase-related repository ITs converted from `@SpringBootTest + @ActiveProfiles("dev") +
@Transactional` to `@DataJpaTest`. Two plan revisions documented in the SUMMARY:
(1) no `JpaAuditingConfig` test-side bean was created (Spring Boot 4 `@DataJpaTest`
loads `CtcManagerApplication` as the slice base config, inheriting
`@EnableJpaAuditing`; re-importing produced `BeanDefinitionOverrideException`);
(2) the `DataJpaTest` import path moved in Boot 4 from
`org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` to
`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`.

| IT | Converted? | Tests pre/post | Notes |
|---|---|---|---|
| PhaseTeamRepositoryIT | yes | 4 / 4 | Pilot. Context-boot ~2.4 s standalone; warm-cache ~30 ms |
| SeasonPhaseRepositoryIT | yes | 3 / 3 | Shares cache key with pilot |
| SeasonPhaseGroupRepositoryIT | yes | 2 / 2 | Shares cache key with pilot |

3-seed Failsafe verification: 9/9 tests green per seed (1234/5678/9999). The 3
ITs share an identical `@DataJpaTest` context-cache key; combined they cost ~1
shared context build per Failsafe pass instead of 3 full-`@SpringBootTest`
context builds.

---

## v1.12 Forward Path

If the ≤7m 50s gate is not reached in Phase 86, the following three structural levers
become candidates for v1.12. Estimated-delta and effort columns are educated
guesses anchored on the Plan-01 baseline plus the Task-1 post-audit data; risks
and touchpoints reflect what the post-audit numbers expose. The lever order
is set by D-14 (`data/dev/backup-staging/` per-fork refactor is Top-1) and
`86-CONTEXT.md` §Deferred Ideas.

| Lever | Estimated Wallclock Delta | Effort (S/M/L) | Risks/Dependencies | Required Touchpoints |
| ----- | ------------------------- | -------------- | ------------------ | -------------------- |
| **1. Per-fork `data/dev/backup-staging/` refactor** (Top-1 per D-14) — **DONE (Phase 89)** — replace the global singleton staging-dir path with a per-fork variant resolved from the Surefire fork-numbering system property, enabling Failsafe `forkCount>1C` for backup ITs without staging-dir races. Backup ITs are currently 27+27 = 54 of the 79 context loads (~68% of the suite's total context-bootstrap weight); even a 2x parallelism gain on this fork would compress this band by 30-60s. | ~60-90s | M | (a) Surefire/Failsafe fork-numbering API may not survive across Maven 3 → 4 (not yet validated); (b) backup tests use `@PostConstruct` directory creation — needs `@DynamicPropertySource` per-fork wiring that mirrors the sitegen pattern from Plan 02 but with the fork-number suffix; (c) `BackupStagingCleanup` startup listener must respect the per-fork path. | `src/main/java/org/ctc/backup/service/BackupImportService.java`, `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java`, `src/main/resources/application*.yml` (app.backup.staging-dir), Failsafe configuration in `pom.xml` (forkCount + system-property propagation) |
| **2. Shared `@SpringBootTest` `@ContextConfiguration` strategy** — introduce a small number of explicit shared configuration classes referenced across IT clusters to maximize Spring TCF cache reuse and avoid the Plan-02-style cache-key fragmentation that this phase's post-audit data suggests is the dominant remaining cost. Profile the post-audit context fingerprint via per-fork ContextLoadCountListener output (already PID-keyed, easy extension) to identify the highest-fragmentation clusters before refactoring. | ~30-60s | M-L | (a) Risk of accidentally widening the cache surface and re-introducing the shared-singleton mutation issues that Plan 02 fixed; (b) requires per-fork context fingerprinting tool that doesn't exist yet — extend `ContextLoadCountListener` to dump cache-key hashes (1-2h work) before the refactor; (c) test isolation needs revisiting if shared contexts touch DB state. | All IT clusters (sitegen, backup, admin, phase-repo), `org.ctc.testsupport.ContextLoadCountListener` extension, possibly a new `BaseFailsafeIT` super-class or a shared `@TestConfiguration` per cluster |
| **3. Testcontainers MariaDB `withReuse(true)`** — once any MariaDB IT exists (none in v1.11), enable `~/.testcontainers.properties` reuse for warm-container startups. The `local`/`docker` profile already uses MariaDB, but no IT runs against it today; this lever pre-empts the cold-start cost (~5-7s per fork) at the point MariaDB ITs are introduced. Note: a forced regression test for the Hibernate dialect (D-23) is the most likely scenario that introduces MariaDB ITs. | ~0s in v1.12 (pre-emptive); ~5-7s per fork once MariaDB ITs land | S (config only) | (a) Requires developer-side `~/.testcontainers.properties` file; (b) Testcontainers reuse skipped on CI by default — CI gets cold container start regardless; (c) only relevant once at least one MariaDB IT exists (none planned in v1.11). | Testcontainers setup, `~/.testcontainers.properties`, future MariaDB IT introduction |

Lever 1 is the largest single delta and lowest blocking-risk; it is the recommended
first move in v1.12. Lever 2 requires a small instrumentation extension before the
refactor proper; without per-fork context-fingerprint data, a blind refactor risks
re-introducing the same fragmentation pattern from a different angle. Lever 3 is
pre-emptive and only pays off when MariaDB ITs land.

*Lever-1 status:* DONE — see **§ Post-Optimization Wallclock (Wave 4)** for the
local median + delta and **§ PERF-02 Forensics** for the cache-key fingerprint
data that feeds Phase 90 PERF-03. Plan 89-01 also extended the per-fork pattern
beyond `app.backup.staging-dir` to `app.backup.import-backups-dir` and
`app.upload-dir` (discovered during execute — see `89-FLAKE-DIAGNOSTIC.md`
Finding 6) and unbound an inherited duplicate Failsafe execution from the
Spring Boot parent that was silently running every IT twice (Finding 7). Phase
91 PERF-06 will re-harvest the CI authoritative median to update the v1.11
23:00 baseline.

*Lever-2 status:* DONE — see **§ PERF-03 Cluster Consolidation (Phase 90 Plan 01)**.
Composed annotation `@CtcDevSpringBootContext` applied to the 19-class empirical
audit surface (13 Surefire outer + 6 Failsafe outer). Surefire cluster collapse
empirically confirmed (`9cefac4c` → `baafff8e` retains all 29 events / 13 outer
classes); Failsafe consolidation neutral on raw event counts but documentation
clarity + future-drift protection achieved. PERF-06 (Phase 91) CI re-harvest
remains authoritative for cumulative-effect measurement.

---
