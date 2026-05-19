---
phase: 89
slug: perf-instrumentation-lever-1-per-fork-backup-staging-dir
status: draft
nyquist_compliant: false
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

> Populated by planner during Plan 89-01 / 89-02 / 89-03 generation. Entries below reflect the validation architecture and are advisory until PLAN.md task IDs are assigned.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 89-01-XX | 01 | 1 | PERF-01 | — | Per-fork dir contract enforced | integration | `./mvnw verify -Dit.test='BackupStagingDirPerForkIT'` | ❌ W0 | ⬜ pending |
| 89-01-XX | 01 | 1 | PERF-01 | — | Sweep operates on own fork only | integration | `./mvnw verify -Dit.test='BackupStagingCleanupRaceIT'` | ❌ W0 | ⬜ pending |
| 89-01-XX | 01 | 1 | PERF-01 | — | Full backup IT suite stable @ `forkCount=2` | integration | `./mvnw verify -Dit.test='org.ctc.backup.**'` | ✅ | ⬜ pending |
| 89-01-XX | 01 | 1 | PERF-01 | — | 3-seed empirical race proof | integration | `./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}` | ✅ | ⬜ pending |
| 89-02-XX | 02 | 1 | PERF-02 | — | Fingerprint listener counts + hashes | unit | `./mvnw test -Dtest='ContextCacheKeyFingerprintListenerTest'` | ❌ W0 | ⬜ pending |
| 89-02-XX | 02 | 1 | PERF-02 | — | Marker file format correctness | integration (side-effect) | embedded in `ContextCacheKeyFingerprintListenerTest` | ❌ W0 | ⬜ pending |
| 89-02-XX | 02 | 1 | PERF-02 | — | Aggregator script shellcheck-clean | shell lint | `shellcheck scripts/test-perf/aggregate-fingerprints.sh` | ❌ W0 | ⬜ pending |
| 89-03-XX | 03 | 1 | PERF-01, PERF-02 | — | Wave-4 wallclock measurement | manual | 3× `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` (idle protocol per Phase 86 D-09) | ✅ existing infra | ⬜ pending |
| 89-03-XX | 03 | 1 | — | — | JaCoCo coverage ≥ 88.88 % preserved | gate | `./mvnw clean verify -Pe2e` → `target/site/jacoco/jacoco.csv` LINE ratio ≥ 0.8888 | ✅ existing infra | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Files that must exist with at least failing-stub coverage before plan execution begins:

- [ ] `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` — covers SC-1 / PERF-01 (regex + fork-number parity assertion)
- [ ] `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` — covers SC-2 / PERF-01 (sibling-fork-dir-not-touched assertion)
- [ ] `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` — new `TestExecutionListener` implementation (Plan 89-02)
- [ ] `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java` — unit test for the new listener
- [ ] `scripts/test-perf/aggregate-fingerprints.sh` — top-5-cluster reporter (Plan 89-02)
- [ ] `src/test/resources/META-INF/spring.factories` — extend with `org.springframework.test.context.TestExecutionListener=...` line (Plan 89-02)

Files that already exist and need modification (not Wave 0 stubs — direct edit in plan tasks):

- [ ] `pom.xml` — Surefire + Failsafe `default-it` `<systemPropertyVariables>` blocks; Failsafe `default-it` `<forkCount>2</forkCount><reuseForks>true</reuseForks>`; project-level `<properties><surefire.forkNumber>0</surefire.forkNumber></properties>`
- [ ] `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — shutdown hook output format change (Line 1 = `total <count>`)
- [ ] `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` — assertion update for new marker format
- [ ] `docs/test-performance.md` — new § PERF-02 Forensics + § Post-Optimization Wallclock (Wave 4); update aggregator loop at L233-239
- [ ] `README.md` — Test-Performance section pointer update

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Wave-4 wallclock measurement (3 local runs, idle protocol) | SC-5 (Phase 89 acceptance) | Local CPU/IO load varies between runs; deterministic automation impossible without a dedicated CI runner (deferred to Phase 91 PERF-06) | Per Phase 86 D-09 protocol: close all heavy apps, run `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` 3× back-to-back; record wall time + context-load counts; populate `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` with median + delta vs. 10:24 baseline |
| `forkCount=2` empirical 3-seed proof | SC-2 | Failsafe-orchestrated parallel run cannot be asserted in-process | Run `./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` then `5678` then `9999`; assert zero failures + zero flakes across all 3 runs |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify command or Wave 0 dependency listed
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (Plan 89-03 wallclock measurement is the only manual gate; bounded by JaCoCo automated gate)
- [ ] Wave 0 covers all MISSING references (5 new files listed above)
- [ ] No watch-mode flags (`-Dspring-boot.run.fork=false` not in test invocations)
- [ ] Feedback latency < 180s for quick run
- [ ] `nyquist_compliant: true` set in frontmatter after planner populates concrete task IDs

**Approval:** pending (will flip to approved after planner emits per-plan PLAN.md and task IDs are inserted into the table above)
