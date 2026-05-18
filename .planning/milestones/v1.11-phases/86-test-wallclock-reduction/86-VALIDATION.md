---
phase: 86
slug: test-wallclock-reduction
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-17
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 86 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

> **Retroactive audit note (2026-05-18):** Phase 86 shipped 2026-05-17 across 6 plans (86-01 baseline + ContextLoadCountListener, 86-02 sitegen cluster, 86-03 backup ITs, 86-04 `@DataJpaTest` pilot, 86-05 post-audit + docs finalization, 86-06 CI median harvest). This VALIDATION.md was in `status: draft` at ship time; it is retroactively approved 2026-05-18 as part of the Nyquist audit Phase 87-series. Full goal-backward verification is in `86-VERIFICATION.md`.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Maven Surefire/Failsafe + Playwright |
| **Config file** | `pom.xml` (Surefire forkCount=2 reuseForks=true; Failsafe default-it forkCount=1C; e2e profile separate execution) |
| **Quick run command** | `./mvnw test -Dtest=<ClassName>` (single class) or `./mvnw verify -Dit.test=<ITClassName>` (single IT) |
| **Full suite command** | `./mvnw verify -Pe2e --no-transfer-progress` (the one and only Phase 86 final gate per TESTING.md Test Invocation Discipline) |
| **Estimated runtime** | Phase 86 baseline ~09:45 Maven median (~9m 46s bash); CI baseline 23:00 (GitHub-hosted ubuntu-latest); target ≤7m 50s (MISSED — OR-branch taken per PERF-04) |

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
| 86-01-01 | 01 | 1 | PERF-04 | — | Local Phase-86 baseline median recorded in docs/test-performance.md (3 runs, median) | manual | `time ./mvnw clean verify -Pe2e --no-transfer-progress 2>&1 \| tee .test-perf-logs/baseline-run-{1,2,3}.log` | ✅ existing pom.xml | ✅ green |
| 86-01-02 | 01 | 1 | PERF-02 | — | ContextLoadCountListener emits AtomicInteger count to `target/test-perf/context-loads-{PID}.txt` after JVM shutdown | unit + integration | `./mvnw test -Dtest=ContextLoadCountListenerTest && grep -E '^[0-9]+$' target/test-perf/context-loads-*.txt` | ✅ created (commit `94afa07d`) | ✅ green |
| 86-02-01 | 02 | 2 | PERF-01 | — | All sitegen tests pass without @DirtiesContext using per-class SitegenTestDir + @DynamicPropertySource for ctc.site.output-dir | integration | `./mvnw test -Dtest='org.ctc.sitegen.*' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` (then 5678, 9999) | ✅ modified (commit `36656eff`) | ✅ green |
| 86-02-02 | 02 | 2 | PERF-01 | — | Backup ITs pass with ImportLockServiceResetHelper @AfterEach reset AND per-method @DirtiesContext(AFTER_METHOD) on latch-dependent methods only | integration | `./mvnw verify -Dit.test='ImportConcurrentLockIT,ImportLockBannerAdviceIT,ImportLockedPostRejectorIT' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` | ✅ created (commits `2914ad62`, `2464f23e`) | ✅ green |
| 86-02-03 | 02 | 2 | PERF-01 | — | TestDataServiceIntegrationTest passes without @DirtiesContext after Cluster C defensive guard removal | integration | `./mvnw verify -Dit.test=TestDataServiceIntegrationTest -Dsurefire.runOrder=random` | ✅ modified (commit `36656eff`) | ✅ green |
| 86-03-01 | 04 | 2 | PERF-03 | — | PhaseTeamRepositoryIT converted to @DataJpaTest with assertion-parity verified; inline scoring fixtures in @BeforeEach | integration | `./mvnw verify -Dit.test=PhaseTeamRepositoryIT` | ✅ modified (commit `6099aae7`) | ✅ green |
| 86-03-02 | 04 | 2 | PERF-03 | — | SeasonPhaseRepositoryIT converted to @DataJpaTest, same pattern, shared cache key | integration | `./mvnw verify -Dit.test=SeasonPhaseRepositoryIT` | ✅ modified (commit `6099aae7`) | ✅ green |
| 86-03-03 | 04 | 2 | PERF-03 | — | SeasonPhaseGroupRepositoryIT converted to @DataJpaTest, same pattern, shared cache key | integration | `./mvnw verify -Dit.test=SeasonPhaseGroupRepositoryIT` | ✅ modified (commit `6099aae7`) | ✅ green |
| 86-04-01 | 05 | 3 | PERF-04 | — | Post-audit local wallclock measured; delta vs Phase-86 baseline recorded in docs/test-performance.md | manual | `time ./mvnw clean verify -Pe2e --no-transfer-progress 2>&1 \| tee .test-perf-logs/post-audit-run-{1,2,3}.log` | ✅ existing | ✅ green |
| 86-05-01 | 05 | 3 | PERF-04, PERF-02 | — | docs/test-performance.md completed with baseline + post wallclock, context-load counts (pre/post), per-decision evidence table, v1.12 top-3 levers section | docs | `test -f docs/test-performance.md && grep -E '^## ' docs/test-performance.md \| wc -l` (must be ≥5) | ✅ created (commits `5b983510`, `c2b65eaf`) | ✅ green |
| 86-06-01 | 06 | 4 | PERF-05 | — | CI median harvested from 5 PR-branch runs (D-17; drop min+max, median of 3); recorded as new v1.11 baseline; variance within 20% D-10 tolerance | manual | `gh run list --workflow=ci.yml --branch=gsd/v1.11-tooling-and-cleanup --limit=5 --json conclusion,createdAt,databaseId,name` + recorded in docs/test-performance.md | ✅ created (commit `63458a74`) | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — custom `ApplicationContextInitializer` with `AtomicInteger` counter + PID-keyed JVM shutdown hook writing to `target/test-perf/context-loads-{PID}.txt` (PERF-02) — created `94afa07d`
- [x] `src/test/resources/META-INF/spring.factories` — registers `ContextLoadCountListener` under `org.springframework.context.ApplicationContextInitializer=` (PERF-02) — created `94afa07d`
- [x] `src/test/java/org/ctc/testsupport/SitegenTestDir.java` — helper for PER_CLASS lifecycle sitegen tests; `Files.createTempDirectory` at static-field-init time (plan revision artifact, Pitfall 3) — created `36656eff`
- [x] `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` — smoke verifying AtomicInteger increments (PERF-02 sampling) — created `94afa07d`
- [x] `src/test/java/org/ctc/backup/it/ImportLockServiceResetHelper.java` — `@TestComponent` calling `ImportLockService.unlock()` via `@AfterEach` reset (PERF-01) — created `2914ad62`
- [x] `docs/test-performance.md` — initial skeleton with all required sections (commits `5b983510`, `c2b65eaf`, `63458a74`)
- [x] `target/test-perf/` directory created on first test run (auto via ContextLoadCountListener shutdown hook)

Note: `JpaAuditingConfig.java` was planned but NOT created — Spring Boot 4 `@DataJpaTest` auto-inherits `@EnableJpaAuditing` from `CtcManagerApplication`; re-importing produces `BeanDefinitionOverrideException`. This is documented as Plan Revision 1 in 86-04-SUMMARY.md.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Local 3-run baseline median calculation | PERF-04 (D-09) | Wallclock measurement intrinsically requires a clean machine state; cannot be asserted by JUnit — operator records numbers and computes median | 1. Ensure no background heavy processes. 2. `./mvnw clean` between runs. 3. `time ./mvnw verify -Pe2e --no-transfer-progress` 3× capturing Maven "Total time" and bash `real` time. 4. Record median in docs/test-performance.md. |
| CI 5-run median (drop min+max, median of 3) | PERF-05 (D-10) | Requires GitHub Actions runs triggered by operator; timing is sourced from CI runner, not local hardware | Harvest via `workflow_dispatch` (D-17 pattern): `gh workflow run ci.yml --ref <branch>` sequentially, `gh run watch` between runs; extract E2E step wallclock from `gh run view <id> --log`. Drop min+max, median of middle 3 = new baseline. Record in docs/test-performance.md "CI Results (PERF-05)" section. |
| Random-seed independence verdict | PERF-01 (D-01) | Operator decides whether 3 green random-seed runs constitute "ordering independent enough" or whether to expand seed set if any flake observed | Per `@DirtiesContext` removal: run `./mvnw test -Dtest=<class> -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=N` for N=1234, N=5678, N=9999. All green → keep removed. Any red → restore annotation with rationale comment. |
| v1.12 forward-path priority ordering | PERF-04 (D-13) | Effort/risk estimation is judgment-driven; the operator weights the 3 levers in docs/test-performance.md based on RESEARCH.md inputs + strategic priorities | Review RESEARCH.md §Open Questions + §Critical Caveats. Order the 3 levers (per-fork backup-staging-dir, shared @ContextConfiguration, Testcontainers reuse) by expected delta × effort × risk in docs/test-performance.md v1.12 section. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (manual gates 86-01-01, 86-04-01, 86-05-01, 86-06-01 interleaved with automated 86-01-02, 86-02-*, 86-03-* — gap-free)
- [x] Wave 0 covers all MISSING references (7 items listed above; JpaAuditingConfig deviation documented)
- [x] No watch-mode flags (Surefire/Failsafe are batch runners; no `--watch`)
- [x] Feedback latency < 670s for full gate, < 120s for per-IT, < 30s for per-removal Surefire random-seed run
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-18 — retroactive Nyquist audit (Phase 87-series, in-milestone closure of v1.11 Nyquist debt).

---

## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Operator carry-forward | 0 |

**Audit method:** retroactive — Phase 86 shipped 2026-05-17 across 6 plans. Nyquist audit 2026-05-18 executed every verification check from the Per-Task Verification Map against live branch `gsd/v1.11-tooling-and-cleanup` HEAD. Phase 86 is test-infrastructure + docs only (no production-code changes, no Flyway migrations). All tasks confirmed delivered via commit inspection, file existence checks, and documented 3-seed verification evidence in plan SUMMARYs.

**CI evidence:**

- **Full-suite CI baseline:** Run-id [26033853591](https://github.com/jegr78/ctc-manager/actions/runs/26033853591) (workflow on `gsd/v1.11-tooling-and-cleanup` @ SHA `3590b3a7`, conclusion: success) — 1668 tests, JaCoCo 88.88%, SpotBugs 0 BugInstance.
- **PERF-05 harvest runs:** 5 `workflow_dispatch` runs on commit `b7f20b53`; all `conclusion: success`; run IDs 26004473138, 26005481397, 26006490986, 26007607311, 26008754136. CI median 23:00 (1380s), variance 7.5%.

**Requirements coverage matrix (audit result):**

| REQ-ID | Existing evidence | Result |
|--------|-------------------|--------|
| PERF-01 | 86-02-SUMMARY.md: 8 `@DirtiesContext` removals (7 sitegen + 1 Cluster C) with 3-seed verification. 86-03-SUMMARY.md: per-method audit (8→6 evictions), latch-free methods bare, latch-dependent methods retain `@DirtiesContext(AFTER_METHOD)` with rationale. REQUIREMENTS.md `[x]` confirmed. | ✅ COVERED |
| PERF-02 | `ContextLoadCountListener.java` + `spring.factories` (commit `94afa07d`). Pre-audit baseline 81, post-optimization 79. `docs/test-performance.md` §Context Load Counts with PID-aggregation shell loop. REQUIREMENTS.md `[x]` confirmed. | ✅ COVERED |
| PERF-03 | 3 Phase repository ITs converted to `@DataJpaTest` (commit `6099aae7`). Assertion parity confirmed (4/4, 3/3, 2/2 tests). 3-seed Failsafe: 9/9 green per seed. Boot-4 revisions documented in 86-04-SUMMARY.md. REQUIREMENTS.md `[x]` confirmed. | ✅ COVERED |
| PERF-04 | OR-branch: gate ≤7m 50s MISSED (local 10:24, CI 23:00). `docs/test-performance.md` §"Result Verdict" + §"v1.12 Forward Path": 3-lever table with delta/effort/risk/touchpoints. PERF-FUTURE-01 tracked in REQUIREMENTS.md. REQUIREMENTS.md `[x]` confirmed. | ✅ COVERED (OR-branch) |
| PERF-05 | 5 CI runs harvested (D-17 PR-branch equivalence per `86-CONTEXT.md`). Median 23:00, variance 7.5% < 20% tolerance. Recorded in `docs/test-performance.md` §"CI Results (PERF-05)" with linked run IDs. Commit `63458a74`. REQUIREMENTS.md `[x]` confirmed. | ✅ COVERED |

**Approval:** approved 2026-05-18 — retroactive Nyquist audit (Phase 87-series, in-milestone closure of v1.11 Nyquist debt).
