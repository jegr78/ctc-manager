---
phase: 114
slug: scoring-personal-crediting
status: draft
nyquist_compliant: false
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
- **Before `/gsd:verify-work`:** Full suite (`./mvnw clean verify -Pe2e`) must be green at ≥82% line coverage
- **Max feedback latency:** ~30 seconds (targeted unit)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | SCORE-01 | — | N/A | unit/IT | `./mvnw test -Dtest=ScoringServiceTest` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | SCORE-02 | — | N/A | unit/IT | `./mvnw test -Dtest=DriverRankingServiceTest` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | SCORE-03 | — | N/A | IT | `./mvnw test -Dtest=ScoringServiceTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky · (per-task IDs assigned by planner)*

---

## Wave 0 Requirements

- [ ] Guest scenario in `TestDataService` / `TestHelper.createFullSeasonFixture()` — doppelrollen guest (roster Team A + guest Team B) + pure guest (no SeasonDriver), `T-`/`Test_`/`Test-Season` prefixed
- [ ] Regression test classes for SCORE-01/02/03 + alltime + profile (D-13..D-16)

*Existing JUnit/Spring Boot Test infrastructure covers the framework; only fixtures + new test classes are needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pure-guest driver-profile page renders with the guest race | SCORE-02 (D-05) | Public site-gen render smoke; data hook only (visual mark is Phase 115) | `/gsd-auto-uat 114` against `dev,demo` seed guest example |

*All scoring/crediting behaviors have automated verification; only page-existence render smoke is UAT.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
