---
phase: 89
slug: perf-instrumentation-lever-1-per-fork-backup-staging-dir
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-19
audited: 2026-05-19
---

# Phase 89 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Covers PERF-01 (per-fork `app.backup.staging-dir` + Failsafe `forkCount=2`) and PERF-02 (`ContextCacheKeyFingerprintListener` + aggregator).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) on Spring Boot 4.0.6, Surefire/Failsafe 3.5.5 |
| **Config file** | `pom.xml` lines 264-309 (Surefire + Failsafe `default-it`); lines 436-465 (Failsafe `e2e-it`) |
| **Quick run command** | `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` |
| **Full suite command** | `./mvnw clean verify --no-transfer-progress` |
| **Phase gate command** | `./mvnw clean verify -Pe2e --no-transfer-progress` |
| **Estimated runtime (quick)** | ~3 minutes (backup IT scope only) |
| **Estimated runtime (phase gate)** | ~10-12 minutes (Phase 86 post-audit baseline 10:24) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` (or narrower `-Dit.test=` scope per task)
- **After every plan wave:** Run `./mvnw clean verify --no-transfer-progress` (no E2E)
- **Before `/gsd:verify-work`:** Run `./mvnw clean verify -Pe2e --no-transfer-progress` (full suite incl. Playwright)
- **Max feedback latency:** 180 seconds for quick run (per [[test-call-optimization]])

---

## Per-Task Verification Map

> Populated from Plan 89-01 / 89-02 / 89-03 PLAN.md files (revision 2, post 2026-05-19 FLAKE-DIAGNOSTIC replan).
> Plan 89-01: 5 tasks (revised from 4 — Task 4 NEW for D-19 lock-timeout investigation per [[no-flaky-dismissal]]). Plan 89-02: 5 tasks. Plan 89-03: 4 tasks.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 89-01-01 | 01 | 1 | PERF-01 | LOW-T1 | pom.xml — THREE `<systemPropertyVariables>` per plugin (staging-dir + import-backups-dir + surefire.forkNumber); no project-property fallback; Failsafe `default-it` forkCount=2/reuseForks=true | source + smoke | `./mvnw -q clean test-compile` + grep gates over pom.xml (positive: 3 entries × 2 plugins; negative: no `<surefire.forkNumber>0</surefire.forkNumber>` in `<properties>`) | ✅ existing pom.xml | ✅ green |
| 89-01-02 | 01 | 1 | PERF-01 | LOW-T1 | Per-fork dir contract enforced (regex + non-vacuous JVM-side `surefire.forkNumber` parity per D-04R.2) | integration | `./mvnw verify -Dit.test='BackupStagingDirPerForkIT' -Djacoco.skip=true -DfailIfNoTests=true` | ❌ W0 | ✅ green |
| 89-01-03 | 01 | 1 | PERF-01 | LOW-T1 | Sweep operates on own fork only (sibling `backup-staging-fork-99` untouched) | integration | `./mvnw verify -Dit.test='BackupStagingCleanupRaceIT' -Djacoco.skip=true -DfailIfNoTests=true` | ❌ W0 | ✅ green |
| 89-01-04 | 01 | 1 | PERF-01 | LOW-T1 | `ImportLockedPostRejectorIT` lock-acquisition-timeout root-cause investigation + fix per D-19 (one of: (a) source patch, (b) `@AfterEach` lock release, (c) deadline bump w/ Javadoc + named constant + follow-up issue) | integration | `./mvnw verify -Dit.test='ImportLockedPostRejectorIT' -Djacoco.skip=true -DfailIfNoTests=true` + Javadoc/comment evidence grep | ✅ existing IT | ✅ green |
| 89-01-05 | 01 | 1 | PERF-01 | LOW-T1 | 3-seed empirical race proof on `org.ctc.backup.**` + legacy `data/*/backup-staging/` `rm -rf` per D-06 | integration | `./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}` (3 runs, zero failures/flakes) | ✅ existing infra | ✅ green |
| 89-02-01 | 02 | 2 | PERF-02 | LOW-T1 | `ContextLoadCountListener` shutdown-hook format extended to Line 1 `total <N>` (D-08); test updated | unit | `./mvnw test -Dtest='ContextLoadCountListenerTest' -DfailIfNoTests=true -Djacoco.skip=true` exits 0 | ✅ existing listener | ✅ green |
| 89-02-02 | 02 | 2 | PERF-02 | LOW-T1 | `ContextCacheKeyFingerprintListener` (new `TestExecutionListener`) + unit test + `spring.factories` registration | unit + smoke | `./mvnw test -Dtest='ContextCacheKeyFingerprintListenerTest' -DfailIfNoTests=true -Djacoco.skip=true` exits 0 | ❌ W0 | ✅ green |
| 89-02-03 | 02 | 2 | PERF-02 | LOW-T1 | Combined listeners cross-check under forkCount=2 (Line 1 `^total \d+$`, Lines 2+ `^[0-9a-f]{1,8}\t.+$`) | integration + format inspection | `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` + grep checks on `target/test-perf/context-loads-*.txt` | ✅ verification-only task | ✅ green |
| 89-02-04 | 02 | 2 | PERF-02 | LOW-T1 | Aggregator script `scripts/test-perf/aggregate-fingerprints.sh` shellcheck-clean + smoke test against fixture markers | shell lint + functional | `shellcheck scripts/test-perf/aggregate-fingerprints.sh` exits 0 + smoke test exits 0 | ❌ W0 | ✅ green |
| 89-02-05 | 02 | 2 | PERF-02 | LOW-T1 | `docs/test-performance.md § PERF-02 Forensics` added; legacy aggregator loop at L233-239 migrated to new marker format | doc + gate | `grep -c '## PERF-02 Forensics' docs/test-performance.md` (=1) + `./mvnw clean verify --no-transfer-progress` exits 0 | ✅ existing docs | ✅ green |
| 89-03-01 | 03 | 3 | PERF-01, PERF-02 | LOW-T1 | Wave-4 wallclock measurement — 3 idle-protocol local runs (Phase-86 D-09 protocol) + PERF-02-active per-run gate | manual (checkpoint:human-verify, blocking) | 3× `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` + per-run `head -1 target/test-perf/context-loads-*.txt \| grep -E '^total [0-9]+$'` + per-run `grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*.txt \| head -1` | ✅ existing infra | ✅ green |
| 89-03-02 | 03 | 3 | PERF-01, PERF-02 | LOW-T1 | `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` populated; `§ v1.12 Forward Path` Lever-1 = DONE; honest delta vs 10:24 baseline (D-02; anti-regression on 09:45 baseline) | doc | `grep -c '## Post-Optimization Wallclock (Wave 4)' docs/test-performance.md` (=1) + Lever-1 DONE marker + no `09:45` baseline reference in the new sections | ✅ existing docs | ✅ green |
| 89-03-03 | 03 | 3 | — | LOW-T1 | `README.md § Test Performance` pointer section | doc | `grep -c '^## Test Performance' README.md` (≥1) + pointer to Wave-4 figure | ✅ existing README | ✅ green |
| 89-03-04 | 03 | 3 | PERF-01, PERF-02 | LOW-T1 | Final phase gate: 3-run idle-protocol measurement IS the final verify (no extra `./mvnw clean verify` run per [[test-call-optimization]]); JaCoCo ≥ 88.88 %; SpotBugs `BugInstance` = 0; CodeQL exit 0; 89-03-SUMMARY.md authored | gate | JaCoCo CSV awk extraction ratio ≥ 0.8888 + SpotBugs report parse + CodeQL gate-step exit 0 + `89-03-SUMMARY.md` present | ✅ existing infra | ✅ green |

*Status: ✅ green · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Files that must exist with at least failing-stub coverage before plan execution begins:

- [ ] `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` — covers SC-1 / PERF-01 (regex + fork-number parity assertion) — created by 89-01-02
- [ ] `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` — covers SC-2 / PERF-01 (sibling-fork-dir-not-touched assertion) — created by 89-01-03
- [ ] `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` — new `TestExecutionListener` implementation (Plan 89-02 Task 1 RED stub + Task 2 GREEN implementation)
- [ ] `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java` — unit test for the new listener (Plan 89-02 Task 1 RED tests)
- [ ] `scripts/test-perf/aggregate-fingerprints.sh` — top-5-cluster reporter (Plan 89-02 Task 4)
- [ ] `src/test/resources/META-INF/spring.factories` — extend with `org.springframework.test.context.TestExecutionListener=...` line (Plan 89-02 Task 2)

Files that already exist and need modification (not Wave 0 stubs — direct edit in plan tasks):

- [ ] `pom.xml` — Surefire + Failsafe `default-it` `<systemPropertyVariables>` blocks (THREE entries each: `app.backup.staging-dir`, `app.backup.import-backups-dir`, `surefire.forkNumber` per D-03/D-04R/D-04R.2/D-18); Failsafe `default-it` `<forkCount>2</forkCount><reuseForks>true</reuseForks>` per D-11; NO project-level `<surefire.forkNumber>` fallback per D-04R (89-01-01)
- [ ] `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — shutdown hook output format change (Line 1 = `total <count>`) (89-02-01)
- [ ] `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` — assertion update for new marker format (89-02-01)
- [ ] `docs/test-performance.md` — new § PERF-02 Forensics + § Post-Optimization Wallclock (Wave 4); update aggregator loop at L233-239 (89-02-05 + 89-03-02)
- [ ] `README.md` — Test-Performance section pointer (89-03-03)

Note: `wave_0_complete: false` remains until execute-phase finishes Wave 0 stubs (the Wave 0 files are CREATED BY Plan 89-01 / 89-02 task execution; this flag flips to `true` only after those tasks land).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Wave-4 wallclock measurement (3 local runs, idle protocol) | SC-5 (Phase 89 acceptance) | Local CPU/IO load varies between runs; deterministic automation impossible without a dedicated CI runner (deferred to Phase 91 PERF-06) | Per Phase 86 D-09 protocol: close all heavy apps, run `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` 3× back-to-back; record wall time + context-load counts; AFTER EACH run: assert `head -1 target/test-perf/context-loads-*.txt \| head -1 \| grep -E '^total [0-9]+$'` exits 0 (Line 1 format) AND `grep -E '^[0-9a-f]{1,8}\t' target/test-perf/context-loads-*.txt \| head -1` exits 0 (PERF-02 active); populate `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` with median + delta vs. 10:24 baseline |
| `forkCount=2` empirical 3-seed proof | SC-2 | Failsafe-orchestrated parallel run cannot be asserted in-process | Run `./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` then `5678` then `9999`; assert zero failures + zero flakes across all 3 runs |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify command or Wave 0 dependency listed (per Per-Task Verification Map above; every row has a concrete command or Wave 0 file dependency)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (Plan 89-03 wallclock measurement is the only manual gate; bounded by JaCoCo automated gate at 89-03-04)
- [x] Wave 0 covers all MISSING references (6 new files listed above — 2 ITs, 1 listener, 1 listener-test, 1 script, 1 spring.factories extension)
- [x] No watch-mode flags (`-Dspring-boot.run.fork=false` not in test invocations)
- [x] Feedback latency < 180s for quick run (`./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` ~3min upper bound; per-task targeted `-Dit.test=BackupStagingDirPerForkIT` <60s)
- [x] `nyquist_compliant: true` set in frontmatter after planner populated concrete task IDs

**Approval:** approved 2026-05-19 (revision 2 post-FLAKE-DIAGNOSTIC replan — per-task verification map refreshed for new 5-task Plan 89-01 incl. Task 4 D-19 lock-timeout investigation; D-04 fallback removed per D-04R; D-18 import-backups-dir entry tracked; nyquist_compliant retained)

---

## Validation Audit 2026-05-19

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Tasks audited | 14 |
| Status: ✅ green | 14 |
| Status: ❌ red | 0 |
| Status: ⚠️ flaky | 0 |

**Phase 89 is Nyquist-compliant.** All 14 tasks across Plans 89-01, 89-02, 89-03 have automated verification covered. No gaps surfaced during audit. The Wave-4 wallclock measurement (89-03-01) remains in Manual-Only by design (local CPU/IO variance — deterministic automation deferred to Phase 91 PERF-06).

### Per-task evidence anchors

| Task | Audit evidence |
|------|----------------|
| 89-01-01 | `pom.xml`: 3 systemPropertyVariables × 2 plugins (Surefire + Failsafe `default-it`); argLine `-Dtest.fork.number=${surefire.forkNumber}`; `<forkCount>2</forkCount>` × 2; inherited Failsafe `default` execution unbound via `<phase>none</phase>`; no `<surefire.forkNumber>` element in `<properties>` (only prohibitive comment). |
| 89-01-02 | `BackupStagingDirPerForkIT.java` exists; `@Tag("integration")`; regex `matches("backup-staging-fork-\\d+")` + non-vacuous fork-number parity via `test.fork.number` system property. |
| 89-01-03 | `BackupStagingCleanupRaceIT.java` exists; sibling-fork-99 isolation via `siblingForkDir`; `@AfterAll` cleans own-fork `unrelated.txt` + sibling tree. |
| 89-01-04 | `LOCK_ACQ_DEADLINE_SECONDS = 20L` named constant + Javadoc explaining `reuseForks=true + forkCount=2` root cause; production `ImportLockService.java` untouched. |
| 89-01-05 | 3-seed Failsafe verification table in `89-01-SUMMARY.md`: seeds 1234/5678/9999, zero failures/errors across all 3 runs (158/158/160 tests completed). |
| 89-02-01 | `ContextLoadCountListener`: `"total " + count.get()` (1 hit), old `String.valueOf(count.get())` form gone (0 hits); test has `matches("^total \\d+$")` assertion. |
| 89-02-02 | `ContextCacheKeyFingerprintListener.java` + test exist; `implements TestExecutionListener`; `Integer.toHexString`; 3 `ReflectionUtils.*` calls; `spring.factories` is 2 lines with TestExecutionListener key. |
| 89-02-03 | Live `target/test-perf/` from final clean-verify: Primary Line 1 gate `^total [0-9]+$` PASS on all 5 fork JVMs; sidecar format gate `^[0-9a-f]{1,8}\t.+$` PASS. |
| 89-02-04 | `aggregate-fingerprints.sh` executable bit set; `shellcheck` exits 0; smoke run on real markers produces Top-5 cluster output. |
| 89-02-05 | `## PERF-02 Forensics` section present (1 hit); legacy aggregator loop migrated to `head -1 \| awk '{print $2}'` (`cat "$f"` form removed — 0 hits). |
| 89-03-01 | `.test-perf-logs/89-03-wave4-run-{1,2,3}.log` + `-metrics.txt` persisted; 3× BUILD SUCCESS, no flakes; per-run PERF-02 active proof in `89-03-SUMMARY.md`. |
| 89-03-02 | `## Post-Optimization Wallclock (Wave 4)` section present (1 hit); `DONE (Phase 89)` annotation on Lever-1 row (1 hit); Wave-4 section content uses 10:24 only — zero `09:45` leakage (anti-regression OK). |
| 89-03-03 | `## Test Performance` section in `README.md` (1 hit) directly after `## Documentation`; links to `docs/test-performance.md`, references v1.12 + Phase 91 PERF-06. |
| 89-03-04 | `89-03-SUMMARY.md` present (6485 bytes); JaCoCo gate (≥ 0.8888 / actual 0.8902) documented; SpotBugs `BugInstance = 0` documented; cumulative D-14 git-clean verified (`git diff origin/master..HEAD -- src/main/resources/application*.yml … BackupStagingCleanup.java ImportLockService.java` is EMPTY). |

### Invariants held cross-phase

- ✓ D-14 — production `application*.yml` + `BackupStagingCleanup.java` + `ImportLockService.java` git-clean across the Phase 89 branch.
- ✓ D-16 — `CLAUDE.md` untouched in Phase 89 (the 17-line diff vs `origin/master` is from Phase 88 commit `d1f5a34d` DOCS-01, not Phase 89).
- ✓ JaCoCo line coverage ≥ 0.8888 (min 0.8902 across 3 Wave-4 runs).
- ✓ SpotBugs `BugInstance` count = 0 on every run.
- ✓ PERF-02 instrumentation alive end-to-end on every measurement.
