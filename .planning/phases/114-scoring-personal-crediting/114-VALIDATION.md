---
phase: 114
slug: scoring-personal-crediting
status: planned
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-01
---

# Phase 114 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test (H2), Failsafe `*IT` |
| **Config file** | `pom.xml` (Surefire/Failsafe/JaCoCo) |
| **Quick run command** | `./mvnw test -Dtest=DriverRankingServiceTest,ScoringServiceTest` |
| **Full suite command** | `./mvnw clean verify` |
| **Estimated runtime** | ~3–7 min full; ~30s targeted unit |

---

## Sampling Rate

- **After every task commit:** Run targeted `-Dtest=<ClassName>` (Surefire) or `-Dit.test=<ClassName> -DfailIfNoTests=false` (Failsafe)
- **After every plan wave:** Run `./mvnw clean verify`
- **Before `/gsd-verify-work`:** Full suite (`./mvnw clean verify -Pe2e`) must be green at ≥82% line coverage
- **Max feedback latency:** ~30 seconds (targeted unit)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 114-01-T1 | 114-01 | 1 | SCORE-01/02/03 (fixture) | T-114-01 | dev-only seed | seed/IT | `./mvnw -Dtest=DriverProfilePageGeneratorTest test` | ❌ W0 → ✅ | ⬜ pending |
| 114-01-T2 | 114-01 | 1 | SCORE-01/02/03 (scaffold) | T-114-01 | N/A | IT scaffold | `./mvnw -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false verify` | ❌ W0 → ✅ | ⬜ pending |
| 114-02-T2 | 114-02 | 2 | SCORE-02 (D-01/02/03/04) | T-114-02 | N/A | unit (RED) | `./mvnw -Dtest=DriverRankingServiceTest test` | ❌ → red | ⬜ pending |
| 114-02-T3 | 114-02 | 2 | SCORE-02 (unify impl) | T-114-02 | N/A | unit (GREEN) | `./mvnw -Dtest=DriverRankingServiceTest test` | ✅ | ⬜ pending |
| 114-03-T1 | 114-03 | 2 | SCORE-02 (D-05 profile RED) | T-114-03 | N/A | sitegen IT (RED) | `./mvnw -Dtest=DriverProfilePageGeneratorTest test` | ❌ → red | ⬜ pending |
| 114-03-T2 | 114-03 | 2 | SCORE-02 (D-05 profile GREEN) | T-114-03 | N/A | sitegen IT (GREEN) | `./mvnw -Dtest=DriverProfilePageGeneratorTest test` | ✅ | ⬜ pending |
| 114-04-T1 | 114-04 | 3 | SCORE-01, SCORE-02 (D-13/D-14) | T-114-04 | N/A | IT | `./mvnw -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false verify` | ✅ | ⬜ pending |
| 114-04-T2 | 114-04 | 3 | SCORE-03 (D-15) + alltime (D-16) | T-114-04 | N/A | IT | `./mvnw -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false verify` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky · (per-task IDs assigned by planner)*

---

## Wave 0 Requirements

- [ ] Guest scenario in `TestDataService.seedRaceLineups` — doppelrollen guest (roster Team A + guest Team B) + pure guest (no SeasonDriver), `T-`/`Test_`/`Test-Season` prefixed, with RaceResults + aggregated match score (Plan 114-01 Task 1)
- [ ] `DriverRankingServiceGuestIT` skeleton with `@Tag("integration")` + four green stub methods (Plan 114-01 Task 2) — bodies filled by Plan 114-04
- [ ] New unit test methods in `DriverRankingServiceTest` for D-01/D-02/D-03/D-04 (Plan 114-02 Task 2)
- [ ] New sitegen test in `DriverProfilePageGeneratorTest` for D-05 profile existence (Plan 114-03 Task 1)

*Existing JUnit/Spring Boot Test infrastructure covers the framework; only fixtures + new test classes/methods are needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pure-guest driver-profile page renders with the guest race | SCORE-02 (D-05) | Public site-gen render smoke; data hook only (visual mark is Phase 115) | `/gsd-auto-uat 114` against `dev,demo` seed guest example |

*All scoring/crediting behaviors have automated verification; only page-existence render smoke is UAT.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (`DriverRankingServiceGuestIT` scaffold + `TestDataService` guest fixture in Plan 114-01)
- [x] No watch-mode flags
- [x] Feedback latency < 30s (targeted unit)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** Phase gate — `./mvnw clean verify -Pe2e` green at ≥82% line coverage; `-Dit.test=DriverRankingServiceGuestIT` exits 0. `wave_0_complete` flips to true after Plan 114-01 executes.
