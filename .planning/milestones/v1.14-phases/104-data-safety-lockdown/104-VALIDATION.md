---
phase: 104
slug: data-safety-lockdown
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-29
---

# Phase 104 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Reconstructed from artifacts (104-01-PLAN.md, 104-01-SUMMARY.md) after execution — State B audit.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test + AssertJ |
| **Config file** | `pom.xml` (Surefire/Failsafe `@Tag`-based routing) |
| **Quick run command** | `./mvnw verify -Dit.test=LocalProfileDataSafetyIT -DfailIfNoTests=false -Djacoco.skip=true -Dspotbugs.skip=true` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | ~4 min isolated IT · ~9:39 min full `-Pe2e` |

---

## Sampling Rate

- **After every task commit:** Run quick command (isolated `LocalProfileDataSafetyIT`)
- **After every plan wave:** Run full suite (`./mvnw clean verify -Pe2e`)
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~240 seconds (isolated IT)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 104-01-01 | 01 | 1 | SAFE-01 | — | `DevDataSeeder` + `TestDataService` scoped to `@Profile("dev")`; absent from `local`/`docker`/`prod` contexts so no test-data seeding against real MariaDB | integration | `./mvnw verify -Dit.test=LocalProfileDataSafetyIT -DfailIfNoTests=false` | ✅ | ✅ green |
| 104-01-02 | 01 | 1 | SAFE-02 | — | Regression IT under `@ActiveProfiles("local")` asserts both seeder beans absent; build goes RED on any future `@Profile` re-widening, `@ConditionalOnProperty` flip, or sibling `@Component` re-introduction | integration | `./mvnw verify -Dit.test=LocalProfileDataSafetyIT -DfailIfNoTests=false` | ✅ | ✅ green |
| 104-01-03 | 01 | 1 | SAFE-01 + SAFE-02 (end-of-phase gate) | — | Full lifecycle green; baselines preserved (JaCoCo ≥ 89.42 %, SpotBugs 0, ≥ 2394 tests) | full | `./mvnw clean verify -Pe2e` | n/a | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No Wave 0 stub/install work was required — JUnit 5 + Spring Boot Test + Failsafe were already wired; the regression IT replicates the `SecurityIntegrationTest.ProdProfileSecurityTest` H2-override pattern.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real-MariaDB visual smoke that the `local` profile boots without instantiating either seeder bean | SAFE-01 (operator confirmation) | `local` profile binds to `jdbc:mariadb://localhost:3306/ctcdb`, unavailable in CI; the automated SAFE-02 IT proves bean-absence under the same profile against H2, so this is a redundant operator sanity check, not a coverage gap | Carry-forward QUAL-02 in STATE.md "Pending UATs": boot `--spring.profiles.active=local` against real MariaDB, confirm no `T-*`/`Test_*` rows appear and no demo logos land in `data/local/uploads/teams/` |

*Note: `docker` and `prod` bean-absence is guaranteed by the same `@Profile("dev")` filter proven for `local`; those contexts are not booted in CI (cloud/network DB binding), so they are covered by-extension rather than by a dedicated IT. The actual regression risk surface was `local` (the only profile the v1.11 drift widened into), and it is automated.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none — no gaps)
- [x] No watch-mode flags
- [x] Feedback latency < 240s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-29

---

## Validation Audit 2026-05-29

| Metric | Count |
|--------|-------|
| Requirements | 2 (SAFE-01, SAFE-02) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Manual-only | 1 (redundant operator smoke — automated equivalent already green) |

State B reconstruction: VALIDATION.md was absent; rebuilt from `104-01-PLAN.md` + `104-01-SUMMARY.md`. Both requirements classified COVERED — `LocalProfileDataSafetyIT` re-run in isolation on 2026-05-29 (`tests=1 errors=0 skipped=0 failures=0`, BUILD SUCCESS). No test files generated; existing coverage is complete.
