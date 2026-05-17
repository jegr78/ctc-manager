---
phase: 86
slug: test-wallclock-reduction
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-17
---

# Phase 86 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Maven Surefire/Failsafe + Playwright |
| **Config file** | `pom.xml` (Surefire forkCount=2 reuseForks=true; Failsafe default-it forkCount=1C; e2e profile separate execution) |
| **Quick run command** | `./mvnw test -Dtest=<ClassName>` (single class) or `./mvnw verify -Dit.test=<ITClassName>` (single IT) |
| **Full suite command** | `./mvnw verify -Pe2e --no-transfer-progress` (the one and only Phase 86 final gate per TESTING.md Test Invocation Discipline) |
| **Estimated runtime** | Phase 86 baseline ~670s (~11m 11s); target ≤470s (~7m 50s) |

---

## Sampling Rate

- **After each `@DirtiesContext` removal:** Run `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234, 5678, 9999}` against the affected test class (3 seeds, all green = removal stays)
- **After each `@DataJpaTest` conversion:** Run `./mvnw verify -Dit.test=<ITClassName>` for the converted IT to confirm assertion-parity
- **After each plan wave:** Run targeted Surefire+Failsafe `./mvnw verify` (without -Pe2e) for fast Wallclock direction sense
- **Before `/gsd:verify-work`:** One full `./mvnw verify -Pe2e` final gate (the only allowed full run per phase per TESTING.md D-08)
- **Max feedback latency:** ~120s (per-IT) to ~670s (full suite); per-removal feedback ≤30s

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 86-01-01 | 01 | 1 | PERF-04 | — | Local Phase-86 baseline median recorded in docs/test-performance.md (3 runs, median) | manual | `time ./mvnw clean verify -Pe2e --no-transfer-progress 2>&1 \| tee target/test-perf/baseline-run-{1,2,3}.log` | ✅ existing pom.xml | ⬜ pending |
| 86-01-02 | 01 | 1 | PERF-02 | — | ContextLoadCountListener emits AtomicInteger count to `target/test-perf/context-loads.txt` after JVM shutdown | unit + integration | `./mvnw test -Dtest=ContextLoadCountListenerTest && grep -E '^[0-9]+$' target/test-perf/context-loads.txt` | ❌ W0 | ⬜ pending |
| 86-02-01 | 02 | 2 | PERF-01 | — | All sitegen tests pass without @DirtiesContext using per-class @TempDir for ctc.site.output-dir | integration | `./mvnw test -Dtest='org.ctc.sitegen.*' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` (then 5678, 9999) | ✅ existing | ⬜ pending |
| 86-02-02 | 02 | 2 | PERF-01 | — | Backup ITs pass without @DirtiesContext using ImportLockService reset bean AND BlockingRestoreFailureInjector latch-recreation strategy (RESEARCH §Backup-IT Critical Caveat) OR retain @DirtiesContext with rationale comment | integration | `./mvnw verify -Dit.test='Import*IT' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` | ❌ W0 (reset bean to be created) | ⬜ pending |
| 86-02-03 | 02 | 2 | PERF-01 | — | TestDataServiceIntegrationTest passes without @DirtiesContext after sitegen cluster fix removes the H2-shared-state defensive guard | integration | `./mvnw verify -Dit.test=TestDataServiceIntegrationTest -Dsurefire.runOrder=random` | ✅ existing | ⬜ pending |
| 86-03-01 | 03 | 2 | PERF-03 | — | PhaseTeamRepositoryIT converted to @DataJpaTest with assertion-parity verified; uses @Import(JpaAuditingConfig.class); scoring rows inline-seeded in @BeforeEach (RESEARCH §@DataJpaTest Conversion Gap) | integration | `./mvnw verify -Dit.test=PhaseTeamRepositoryIT` | ✅ existing | ⬜ pending |
| 86-03-02 | 03 | 2 | PERF-03 | — | SeasonPhaseRepositoryIT converted to @DataJpaTest, same pattern | integration | `./mvnw verify -Dit.test=SeasonPhaseRepositoryIT` | ✅ existing | ⬜ pending |
| 86-03-03 | 03 | 2 | PERF-03 | — | SeasonPhaseGroupRepositoryIT converted to @DataJpaTest, same pattern | integration | `./mvnw verify -Dit.test=SeasonPhaseGroupRepositoryIT` | ✅ existing | ⬜ pending |
| 86-04-01 | 04 | 3 | PERF-04 | — | Post-audit local wallclock measured; delta vs Phase-86 baseline recorded | manual | `time ./mvnw clean verify -Pe2e --no-transfer-progress 2>&1 \| tee target/test-perf/post-audit-run-{1,2,3}.log` | ✅ existing | ⬜ pending |
| 86-05-01 | 05 | 3 | PERF-04, PERF-02 | — | docs/test-performance.md drafted with baseline + post wallclock, context-load counts (pre/post), per-decision evidence table, v1.12 top-3 levers section | docs | `test -f docs/test-performance.md && grep -E '## (Baseline\|Post-Optimization\|Context Load\|v1.12 Forward Path)' docs/test-performance.md \| wc -l` (must be ≥4) | ❌ W0 | ⬜ pending |
| 86-06-01 | 06 | 4 | PERF-05 | — | CI median harvested from 5 consecutive runs on master post-merge (drop min+max, median of 3); recorded as new baseline | manual | `gh run list --workflow=ci.yml --branch=master --limit=5 --json conclusion,createdAt,databaseId,name` + record into docs/test-performance.md | ❌ W0 (post-merge) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — custom `ApplicationContextInitializer` with `AtomicInteger` counter + JVM shutdown hook writing to `target/test-perf/context-loads.txt` (PERF-02)
- [ ] `src/test/resources/META-INF/spring.factories` — register `ContextLoadCountListener` under `org.springframework.context.ApplicationContextInitializer=` (PERF-02)
- [ ] `src/test/java/org/ctc/testsupport/JpaAuditingConfig.java` — `@TestConfiguration` with `@EnableJpaAuditing` for `@DataJpaTest` slices (PERF-03)
- [ ] `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` — smoke verifying AtomicInteger increments and marker-file write (PERF-02 sampling)
- [ ] `src/test/java/org/ctc/backup/it/ImportLockServiceResetHelper.java` — `@TestComponent` providing `@AfterEach` reset of `ReentrantLock` AND latch-bean recreation for `BlockingRestoreFailureInjector.Config` (PERF-01)
- [ ] `docs/test-performance.md` — initial skeleton with `## Baseline`, `## Post-Optimization Wallclock`, `## Context Load Counts`, `## Per-Decision Evidence`, `## v1.12 Forward Path` headers (PERF-02, PERF-04)
- [ ] `target/test-perf/` directory created on first test run (auto via Maven Surefire system property + test code)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Local 3-run baseline median calculation | PERF-04 (D-09) | Wallclock measurement intrinsically requires a clean machine state; cannot be asserted by JUnit — operator records numbers and computes median | 1. Ensure no background heavy processes. 2. `./mvnw clean` between runs. 3. `time ./mvnw verify -Pe2e --no-transfer-progress` 3× capturing Maven "Total time" and bash `real` time. 4. Record median in docs/test-performance.md. |
| CI 5-run median (drop min+max, median of 3) | PERF-05 (D-10) | Requires GitHub Actions runs on master post-merge — only the operator can trigger or monitor them | 1. After PR merge to master, watch 5 consecutive CI runs via `gh run list --workflow=ci.yml --branch=master --limit=10`. 2. Extract Maven Total time from each `gh run view <id> --log` (grep "BUILD SUCCESS" + "Total time:"). 3. Drop min+max, median of 3 middle values = new baseline. 4. Record in docs/test-performance.md "Post-Optimization Wallclock" section. |
| Random-seed independence verdict | PERF-01 (D-01) | Operator decides whether 3 green random-seed runs constitute "ordering independent enough" or whether to expand seed set if any flake observed | Per `@DirtiesContext` removal: run `./mvnw test -Dtest=<class> -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=N` for N=1234, N=5678, N=9999. All green → keep removed. Any red → restore annotation with rationale comment. |
| v1.12 forward-path priority ordering | PERF-04 (D-13) | Effort/risk estimation is judgment-driven; the operator weights the 3 levers in docs/test-performance.md based on RESEARCH.md inputs + strategic priorities | Review RESEARCH.md §Open Questions + §Critical Caveats. Order the 3 levers (per-fork backup-staging-dir, shared @ContextConfiguration, Testcontainers reuse) by expected delta × effort × risk in docs/test-performance.md v1.12 section. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (manual gates 86-01-01, 86-04-01, 86-05-01, 86-06-01 interleaved with automated 86-01-02, 86-02-*, 86-03-* — gap-free)
- [ ] Wave 0 covers all MISSING references (7 items listed above)
- [ ] No watch-mode flags (Surefire/Failsafe are batch runners; no `--watch`)
- [ ] Feedback latency < 670s for full gate, < 120s for per-IT, < 30s for per-removal Surefire random-seed run
- [ ] `nyquist_compliant: true` set in frontmatter (after planner adds verification fields and Wave 0 confirms 100% mapping)

**Approval:** pending
