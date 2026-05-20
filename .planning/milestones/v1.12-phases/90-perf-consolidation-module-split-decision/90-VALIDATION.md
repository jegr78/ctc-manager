---
phase: 90
slug: perf-consolidation-module-split-decision
status: verified
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-20
verified: 2026-05-20
---

# Phase 90 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test + Testcontainers + Playwright (existing) |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo + SpotBugs) |
| **Quick run command** | `./mvnw -q test -Dtest='db.migration.**'` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~9–11 min full (`-Pe2e`); ~30–60 s quick (`db.migration.**` only) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -q test -Dtest='db.migration.**'` (Plan 90-01 only) or `./mvnw -q test-compile` (Plans 90-02/90-03 docs-heavy tasks where compile-clean is the only meaningful gate).
- **After every plan wave:** Run targeted Failsafe — Plan 90-01: `./mvnw -q verify -Pe2e -Dit.test='db.migration.**' -DskipUnit=false`; Plan 90-02: `./mvnw -q verify -Pe2e -Dit.test='org.ctc.backup.service.Backup*IT'` (docker.available gate is dev-only; CI cold-starts as today); Plan 90-03: `./mvnw -q test-compile` (docs-only, no test surface).
- **Before phase close (single final run):** `./mvnw clean verify -Pe2e` — Wave-5 idle measurement (Plan 90-01 D-07) is the same command run 3× per CLAUDE.md §Test-Aufrufe-Optimieren and Phase 86 D-09 idle protocol.
- **Max feedback latency:** ~60 s for quick gate; ~10 min for `-Pe2e` full gate.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 90-01-XX (aggregator audit) | 01 | 1 | PERF-03 | — | aggregator output enumerates every class in hash buckets `9cefac4c` + `f524774b` against fresh `target/test-perf/` | manual + script | `./mvnw clean verify -Pe2e && scripts/test-perf/aggregate-fingerprints.sh target/test-perf 10` | ✅ `.test-perf-logs/90-01-aggregator-before.txt` (131 events, 5 sidecar files, Wave-5 Run-1 BUILD SUCCESS) | ✅ green |
| 90-01-XX (annotation create) | 01 | 1 | PERF-03 | — | `@CtcDevSpringBootContext` exists at `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` with `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` + `@Retention(RUNTIME)` + `@Target(TYPE)` | unit | `./mvnw -q test-compile` | ✅ file present; 9 outer classes carry the annotation (sample scan: V3/V5/V6, Backup\*IT × 5, PlayoffServiceTest) | ✅ green |
| 90-01-XX (refactor db.migration.**) | 01 | 1 | PERF-03 | — | every class in the audited buckets wears `@CtcDevSpringBootContext` (no remaining `@SpringBootTest + @ActiveProfiles` pairs in the cluster) | source + behavior | `grep -L '@CtcDevSpringBootContext' $(grep -lE '@SpringBootTest.*CtcManagerApplication\|@ActiveProfiles' <audited list>)` returns empty | ✅ 19 outer classes refactored (13 Surefire + 6 Failsafe) per 90-01-SUMMARY.md Bucket A/B tables | ✅ green |
| 90-01-XX (3-seed Failsafe) | 01 | 2 | PERF-03 | — | `db.migration.**` ITs pass under seeds 1234, 5678, 9999 | integration | `./mvnw -q verify -Pe2e -Dit.test='db.migration.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` × 3 | ✅ `.test-perf-logs/90-01-failsafe-seed-{1234,5678,9999}.log` all BUILD SUCCESS (06:05 / 05:47 / 05:42 min) | ✅ green |
| 90-01-XX (Surefire seed-stable) | 01 | 2 | PERF-03 | — | `db.migration.**` `*Test.java` (Surefire) passes under one random seed | unit | `./mvnw -q test -Dtest='db.migration.**'` | ✅ `.test-perf-logs/90-01-surefire-seed-stable.log` BUILD SUCCESS (46.8 s) | ✅ green |
| 90-01-XX (cache-key diff) | 01 | 3 | PERF-03 | — | aggregator Top-N after refactor collapses `9cefac4c` + `f524774b` into a single shared hash bucket containing every audited class | observation + script | `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 10` compared to baseline | ✅ Top-5 before/after table in 90-01-SUMMARY.md — Surefire `9cefac4c`→`baafff8e` collapse (2 annotation-shape variants → 1 shared hash, 13 outer classes preserved); Failsafe mixed outcome documented honestly per D-07 | ✅ green |
| 90-01-XX (Wave-5 idle) | 01 | 3 | PERF-03 | — | 3 idle `./mvnw clean verify -Pe2e` runs logged to `.test-perf-logs/90-01-wave5-run-{1,2,3}.log`; Maven `Total time` median + JaCoCo ≥ 88.88 % + context-load count recorded in SUMMARY | observation | `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` × 3 | ✅ Run-1 07:24, Run-2 08:27, Run-3 09:31; median **08:27 min**, Δ vs Phase 89 09:19 = -52 s ≈ -9.3 %; context-loads 56/55/55; logs persisted | ✅ green |
| 90-01-XX (gates) | 01 | 3 | PERF-03 | T-90-SEC-01 | JaCoCo ≥ 88.88 %, SpotBugs `BugInstance` = 0, `EXPORT_ORDER` = 24, `BackupSchema.SCHEMA_VERSION` = 1 | full | `./mvnw verify -Pe2e` (CI gate on push) | ✅ JaCoCo 0.8902 on all 3 Wave-5 runs; SpotBugs 0; `BackupSchema initialized: SCHEMA_VERSION=1, exportOrder size=24`; D-09 `src/main/java/**` git-clean across phase baseline | ✅ green |
| 90-02-XX (withReuse) | 02 | 1 | PERF-04 | T-90-TC-01, T-90-TC-02 | both `MariaDBContainer<>` declarations carry `.withReuse(true)`; CI without `~/.testcontainers.properties` cold-starts as today | unit + integration | `./mvnw -q verify -Pe2e -Dit.test='org.ctc.backup.service.Backup*IT'` (dev-machine with `docker.available=true`); CI passes without flag | ✅ `.withReuse(true)` × 1 in `BackupImportMariaDbSmokeIT.java` (line 108) + `BackupRoundTripIT.java` (line 522, nested `MariaDbRoundTripTests`); `@EnabledIfSystemProperty(docker.available=true)` preserved on both | ✅ green |
| 90-02-XX (docs + README) | 02 | 1 | PERF-04 | T-90-TC-01 | `docs/test-performance.md § PERF-04 Testcontainers Reuse` populated with opt-in line, verification command, defensive cleanup hint; `README.md § Test Performance` extended with one pointer paragraph | source | `grep -F '## PERF-04 Testcontainers Reuse' docs/test-performance.md` and `grep -F 'testcontainers.reuse.enable=true' README.md docs/test-performance.md` both return matches | ✅ `## PERF-04 Testcontainers Reuse` exists at docs/test-performance.md:407 with 5 required elements; README.md sentence pointer present; T-90-TC-01 seed-defensively + T-90-TC-02 cleanup-hint paragraphs both present | ✅ green |
| 90-02-XX (compile gate) | 02 | 1 | PERF-04 | — | `./mvnw verify -Pe2e` green on PR HEAD; CI unchanged | full | `./mvnw verify -Pe2e` | ✅ `.test-perf-logs/90-02-verify.log` BUILD SUCCESS, Maven Total 07:35 min; both MariaDB ITs SKIP without `-Ddocker.available=true` as designed | ✅ green |
| 90-03-XX (verdict prose) | 03 | 1 | PERF-05 | — | `docs/test-performance.md § Test-Module-Split Decision` contains the verdict line (`Defer`), the three blockers (TestDataService cross-boundary, IDE-friction-risk, no hard cumulative-effect data), the re-evaluation trigger (v1.13 + PERF-06 CI median), and a "Why not reject?" paragraph | source | `grep -F '## Test-Module-Split Decision' docs/test-performance.md && grep -F 'Defer' docs/test-performance.md` both return matches | ✅ § Test-Module-Split Decision exists exactly once at end of file with all 6 required structural elements per 90-03-SUMMARY.md | ✅ green |
| 90-03-XX (docs-compile gate) | 03 | 1 | PERF-05 | — | `./mvnw verify -Pe2e` green (docs change only, no source delta expected) | full | `./mvnw verify -Pe2e` | ✅ `.test-perf-logs/90-03-verify.log` BUILD SUCCESS, Maven Total 07:36 min; `src/**/*.java` git-clean across Plan 90-03 (D-09) | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` — new composed annotation; must compile before Plan 90-01 refactor commits. **Shipped commit `d2d2f8a9 test(90-01): create @CtcDevSpringBootContext composed annotation`.**
- [x] `docs/test-performance.md § PERF-03 Cluster` — new section header for Plan 90-01 cache-key-diff prose + Top-5 before/after table. **Shipped commit `82ef2ecf docs(90-01): PERF-03 Cluster Consolidation section + Plan SUMMARY closure`.**
- [x] `docs/test-performance.md § PERF-04 Testcontainers Reuse` — new section header for Plan 90-02 opt-in documentation. **Shipped commit `e316ea02 docs(perf-04): document Testcontainers reuse opt-in + verification + cleanup`.**
- [x] `docs/test-performance.md § Test-Module-Split Decision` — new section header for Plan 90-03 verdict. **Shipped commit `cf0ad918 docs(perf-05): test-module-split verdict — defer with explicit blockers`.**
- [x] `.test-perf-logs/` directory — used during execution (10 Plan 90-01 logs + 1 Plan 90-02 verify + 1 Plan 90-03 verify); gitignored.

*Existing infrastructure (Surefire/Failsafe/JaCoCo/SpotBugs/CodeQL/Testcontainers/aggregator) covers all phase requirements — no framework install needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Testcontainers reuse actually re-attaches across consecutive `./mvnw verify -Pe2e` invocations on a dev machine | PERF-04 | Requires `~/.testcontainers.properties` operator file with `testcontainers.reuse.enable=true` (per-machine, never CI); `docker ps` between runs is the only direct observation channel | (1) Set `~/.testcontainers.properties` to contain `testcontainers.reuse.enable=true`. (2) `docker.available=true ./mvnw -q verify -Pe2e -Dit.test='org.ctc.backup.service.BackupImportMariaDbSmokeIT'`. (3) `docker ps --filter label=org.testcontainers.reuse.enable=true` should list a long-lived `mariadb:11` container + a labelled ryuk companion (or ryuk-absent confirmation). (4) Re-run step 2; the container should be re-attached (Testcontainers logs "Reusing container" instead of starting fresh). Record observation in Plan 90-02 SUMMARY. |
| Cache-key cluster collapse observable in aggregator Top-N output (visual diff) | PERF-03 | Aggregator is a shell script that produces human-readable Top-N; diff is structural (one hash row replaces two) | (1) `./mvnw clean verify -Pe2e` pre-refactor → run aggregator → snapshot Top-5 in Plan 90-01 SUMMARY as "Before". (2) Apply `@CtcDevSpringBootContext` refactor + commit. (3) `./mvnw clean verify -Pe2e` post-refactor → run aggregator → snapshot Top-5 as "After". (4) Confirm `9cefac4c` + `f524774b` (or their JVM-run-equivalent counterparts) merge into a single new hash with combined occurrences. |
| `docs/test-performance.md § Test-Module-Split Decision` reads as a coherent decision record alongside the existing §PERF-02 Forensics / §Post-Optimization Wallclock structure | PERF-05 | Tone + structure consistency is a human-readable concern; no machine gate beyond presence-grep | Spot-check Plan 90-03 commit diff: verdict line, three numbered blockers, re-evaluation trigger paragraph, "Why not reject?" paragraph — wording matches existing §sections in voice. |

---

## Validation Architecture (Critical Behaviours)

Mirrors RESEARCH.md §Validation Architecture; consolidated here so VALIDATION.md is self-sufficient.

1. **Cache-key collision** — Spring TCF `MergedContextConfiguration.hashCode()` produces an identical bucket for every class wearing `@CtcDevSpringBootContext` (locations + classes + activeProfiles + initialiser-classes + customizers + property-source-descriptors + parent + ContextLoader). Failure mode: a residual `@DirtiesContext`, `@DynamicPropertySource`, or `@TestPropertySource` on a refactored class would keep that class in its own bucket. Distinguisher: aggregator Top-N still shows the class on a separate hash row (real failure) vs. a per-method `@DirtiesContext` rebuild that registers extra `beforeTestClass` events against the SAME hash (noise — count discrepancy with class-count = 0).
2. **Testcontainers reuse survives JVM exit** — With operator opt-in, the MariaDB container's label hash stays stable across `./mvnw verify` invocations and re-attaches. Failure mode: a non-string-literal config knob (e.g. random port, dynamic property) on either `MariaDBContainer<>` declaration changes the hash, breaking reuse. Distinguisher: `docker ps` between runs (no container = real failure) vs. cold-start in absence of the operator file (expected, NOT a failure).
3. **CI cold-start preservation** — Without `~/.testcontainers.properties`, `.withReuse(true)` is a silent no-op. Failure mode: CI E2E runtime increases or the IT skips become hangs. Distinguisher: CI run on PR HEAD must not regress against the 23:00 v1.11 baseline by more than its natural ±2 % noise (the gate is "no statistically meaningful regression", not "0 % delta").
4. **3-seed Failsafe isolation** — Under elevated `forkCount` and `@CtcDevSpringBootContext` consolidation, `db.migration.**` ITs must remain order-independent. Failure mode: shared DB state, shared singletons (e.g. `TestDataService` seed state), or latch beans leak between consolidated tests. Distinguisher: 3-seed runs (1234/5678/9999) — all three pass = real isolation; any seed fails = real regression (per `[[no-flaky-dismissal]]`, do NOT vertage as "flaky").

---

## Sampling Cadence

- **Per task** — `./mvnw -q test-compile` (annotation create, doc edits) or `./mvnw -q test -Dtest='db.migration.**'` (refactor commits) — ~30–60 s each.
- **Per wave** — Targeted `-Dit.test=` Failsafe slice — ~2–4 min each.
- **Per phase gate** — Wave-5 measurement: 3 × `./mvnw clean verify -Pe2e` idle runs (~9–11 min each = ~30 min total). Aligns with Phase 86 D-09 idle protocol + Phase 89 Wave-4 evidence retention pattern.
- **CI gate** — `gh run watch` on PR HEAD post-push; CodeQL + JaCoCo + SpotBugs all green.

---

## Threat-Model Cross-References

(Recommended threats for planner `<threat_model>` blocks per ASVS L1; details in RESEARCH.md §Security Threat Model Input.)

| Threat ID | Plan | Description | Mitigation | Residual Severity |
|-----------|------|-------------|------------|-------------------|
| T-90-SEC-01 | 90-01 | Test-only annotation accidentally referenced from production classpath | `src/test/java/` is not on the production classpath; CodeQL `java-kotlin` query suite would surface any prod-side import — no new suppression needed | Negligible |
| T-90-TC-01 | 90-02 | Testcontainers reuse leaks DB state between consecutive dev-machine runs (NOT a CI risk — gate `docker.available=true` excludes CI) | Document defensive `testDataService.seed()` per `@BeforeEach` for future MariaDB ITs; current `Backup*IT` set re-creates fixtures each test | Low (dev-only) |
| T-90-TC-02 | 90-02 | Orphan-container disk pressure if developer rotates between projects without cleanup | Document `docker container prune --filter "label=org.testcontainers.reuse.enable=true"` in `docs/test-performance.md § PERF-04` defensive section | Low (dev-only) |

Plan 90-03 has no threat model rows — pure-docs verdict; zero code surface.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags (`./mvnw` invocations are one-shot, not `-Dspring-boot.run.fork=true` watch-mode)
- [x] Feedback latency: ~60 s quick gate, ~10 min full gate — within CLAUDE.md §Test-Aufrufe-Optimieren budget
- [x] `nyquist_compliant: true` set in frontmatter (Wave 0 complete; all 13 Per-Task rows verified green)

**Approval:** verified 2026-05-20

---

## Validation Audit 2026-05-20

| Metric | Count |
|--------|-------|
| Per-Task rows audited | 13 |
| COVERED (green) | 13 |
| PARTIAL | 0 |
| MISSING | 0 |
| Escalated to manual-only | 0 |
| Manual-only verifications (carried forward as design choice) | 3 |

**Audit method:** Goal-backward audit against `90-{01,02,03}-SUMMARY.md` shipped-evidence sections (no auditor agent spawned — short-circuit applied per Step 3: "No gaps → skip to Step 6, set `nyquist_compliant: true`").

**Cross-checks performed inline:**

- `ls .test-perf-logs/90-01-*` → 10 logs present (3-seed Failsafe × 3, Surefire seed-stable, aggregator before/after, Wave-5 runs × 3, Failsafe seed-1234 tail).
- `ls .test-perf-logs/90-0{2,3}-verify.log` → both present (07:35 / 07:36 min BUILD SUCCESS).
- `grep -c "^## PERF-03 Cluster Consolidation\|^## PERF-04 Testcontainers Reuse\|^## Test-Module-Split Decision" docs/test-performance.md` → 3 (one per docs section).
- `test -f src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` → exists; `ls src/main/java/org/ctc/testsupport/` → does NOT exist (D-09 + T-90-SEC-01 mitigation preserved).
- `grep -c "\.withReuse(true)" src/test/java/org/ctc/backup/service/Backup{ImportMariaDbSmoke,RoundTrip}IT.java` → 1 each.
- `git diff e0fa53ee..36e6a70a -- 'src/main/java/**' | wc -l` → 0 (D-09 invariant across Phase 90 commit range).

The 3 Manual-Only Verifications (Testcontainers reuse re-attach observation, aggregator cluster-collapse visual diff, decision-record tone consistency) are intentional design choices — automated gates cannot meaningfully replace them. They remain in scope for periodic spot-checks; not gaps.
