---
phase: 89
slug: perf-instrumentation-lever-1-per-fork-backup-staging-dir
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-19
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

> Populated from Plan 89-01 / 89-02 / 89-03 PLAN.md files (revision 1, post 2026-05-19 task-renumbering).
> Plan 89-01: 4 tasks. Plan 89-02: 5 tasks (revised from 7 — Task 1+2 merged into TDD RED; Task 3+4 merged into GREEN; Task 7 collapsed into Task 5). Plan 89-03: 4 tasks.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 89-01-01 | 01 | 1 | PERF-01 | — | pom.xml per-fork staging-dir + Failsafe forkCount=2 | source + smoke | `./mvnw -q clean test-compile` + grep gates over pom.xml | ✅ existing pom.xml | ⬜ pending |
| 89-01-02 | 01 | 1 | PERF-01 | — | Per-fork dir contract enforced (regex + fork-number parity) | integration | `./mvnw verify -Dit.test='BackupStagingDirPerForkIT' -Djacoco.skip=true -DfailIfNoTests=true` | ❌ W0 | ⬜ pending |
| 89-01-03 | 01 | 1 | PERF-01 | — | Sweep operates on own fork only (sibling fork-99 untouched) | integration | `./mvnw verify -Dit.test='BackupStagingCleanupRaceIT' -Djacoco.skip=true -DfailIfNoTests=true` | ❌ W0 | ⬜ pending |
| 89-01-04 | 01 | 1 | PERF-01 | — | 3-seed empirical race proof on org.ctc.backup.** | integration | `./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` (+ 5678, 9999) | ✅ existing infra | ⬜ pending |
| 89-02-01 | 02 | 2 | PERF-02 | — | Count-listener format `total <N>` + fingerprint-listener skeleton stub + RED tests | unit (RED phase) | `./mvnw test -Dtest='ContextLoadCountListenerTest' -DfailIfNoTests=true -Djacoco.skip=true` (GREEN) + `! ./mvnw test -Dtest='ContextCacheKeyFingerprintListenerTest' ...` (RED expected) | ❌ W0 | ⬜ pending |
| 89-02-02 | 02 | 2 | PERF-02 | — | Fingerprint-listener GREEN implementation + spring.factories registration | unit (GREEN phase) | `./mvnw test -Dtest='ContextLoadCountListenerTest,ContextCacheKeyFingerprintListenerTest' -DfailIfNoTests=true -Djacoco.skip=true` | partial: depends on 89-02-01 | ⬜ pending |
| 89-02-03 | 02 | 2 | PERF-02 | — | Combined listeners validated under forkCount=2 (REFACTOR / cross-plan) | integration | `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` + marker-file format inspection (Line 1 `^total \d+$`, Lines 2+ `^[0-9a-f]{1,8}\t.+$`) | ✅ | ⬜ pending |
| 89-02-04 | 02 | 2 | PERF-02 | — | Aggregator script shellcheck-clean + smoke test | shell lint + functional | `shellcheck scripts/test-perf/aggregate-fingerprints.sh` + smoke test against `/tmp/agg-smoke` | ❌ W0 | ⬜ pending |
| 89-02-05 | 02 | 2 | PERF-02 | — | `docs/test-performance.md` § PERF-02 Forensics + aggregator-loop migration + full-suite gate | doc + gate | `grep -c 'PERF-02 Forensics' docs/test-performance.md` (>=2) + `./mvnw clean verify --no-transfer-progress` exits 0 | ✅ existing docs | ⬜ pending |
| 89-03-01 | 03 | 3 | PERF-01, PERF-02 | — | Wave-4 wallclock measurement (3 idle-protocol runs) + PERF-02-active gates | manual (checkpoint:human-verify) | 3× `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` + per-run `head -1 ... \| grep -E '^total [0-9]+$'` + per-run `grep -E '^[0-9a-f]{1,8}\t' target/test-perf/*.txt \| head -1` | ✅ existing infra | ⬜ pending |
| 89-03-02 | 03 | 3 | — | — | `docs/test-performance.md § Wave 4` populated + § v1.12 Forward Path Lever-1 = DONE | doc | `grep -c '## Post-Optimization Wallclock (Wave 4)' docs/test-performance.md` (=1) + `grep -c '\[DONE Phase 89\]' docs/test-performance.md` (=1) | ✅ existing docs | ⬜ pending |
| 89-03-03 | 03 | 3 | — | — | `README.md § Test Performance` pointer added | doc | `grep -c '^## Test Performance$' README.md` (=1) + section-ordering awk check | ✅ existing README | ⬜ pending |
| 89-03-04 | 03 | 3 | — | — | Final phase gate JaCoCo ≥ 88.88 % + SpotBugs 0 | gate | `./mvnw clean verify -Pe2e --no-transfer-progress` exits 0 + JaCoCo CSV awk extraction ratio ≥ 0.8888 | ✅ existing infra | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

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

- [ ] `pom.xml` — Surefire + Failsafe `default-it` `<systemPropertyVariables>` blocks; Failsafe `default-it` `<forkCount>2</forkCount><reuseForks>true</reuseForks>`; project-level `<properties><surefire.forkNumber>0</surefire.forkNumber></properties>` (89-01-01)
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

**Approval:** approved 2026-05-19 (revision 1 post-checker — all 4 blockers + 4 warnings addressed; per-task verification map populated with actual task IDs from PLAN.md files; nyquist_compliant flipped to true)
