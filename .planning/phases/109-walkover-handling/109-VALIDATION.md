---
phase: 109
slug: walkover-handling
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-30
---

# Phase 109 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `109-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test + Mockito + Playwright |
| **Config file** | `pom.xml` (Surefire ~266–309; Failsafe ~291–308; E2E `-Pe2e` ~440–460) |
| **Quick run command** | `./mvnw -Dtest=StandingsServiceTest,ScoringServiceTest test` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | unit ~30s · `clean verify` minutes · `-Pe2e` ~17min |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -Dtest=StandingsServiceTest,ScoringServiceTest test` (unit only, fast)
- **After every plan wave:** Run `./mvnw clean verify` (excludes E2E)
- **Before `/gsd:verify-work`:** `./mvnw clean verify -Pe2e` must exit 0 (all existing tests green — WO-02 / SC4)
- **Max feedback latency:** ~30s for the unit loop

---

## Per-Task Verification Map

| Req ID | Behavior | Wave | Test Type | Automated Command | File Exists | Status |
|--------|----------|------|-----------|-------------------|-------------|--------|
| WO-01 | Home-forfeit walkover: opponent wins (addWin+pointsWin), forfeiter loses (addLoss+0) | 1 | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ add nested test | ⬜ pending |
| WO-01 | Away-forfeit walkover: opponent (home) wins, forfeiter (away) loses | 1 | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ add nested test | ⬜ pending |
| WO-01 | Walkover precedes partial race results (D-08) — scores ignored | 1 | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ add nested test | ⬜ pending |
| WO-01 | No synthetic point difference / no Buchholz from walkover (D-07) | 1 | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ add nested test | ⬜ pending |
| WO-01 | `recomputeMatchScoresFromAllLegs` skips walkover races (critical guard) | 1 | unit | `./mvnw -Dtest=ScoringServiceTest test` | ✅ add test | ⬜ pending |
| WO-02 | V17 migration adds nullable `walkover_team_id` FK on `matches` (H2) | 0 | integration | `./mvnw -Dit.test=V17MigrationIT -DfailIfNoTests=false verify` | ❌ Wave 0 | ⬜ pending |
| WO-02 | MatchController save-edit persists `walkover_team_id` FK | 1 | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | ✅ add test method | ⬜ pending |
| WO-04 | Admin can mark AND clear walkover through edit form | 1 | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | ✅ add test method | ⬜ pending |
| WO-04 | Validation: team ∉ {home,away} or bye match → `errorMessage` flash | 1 | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | ✅ add test method | ⬜ pending |
| WO-03 | "w/o" label next to forfeiter in matchday-detail after marking | 2 | e2e | `./mvnw verify -Pe2e` (WalkoverE2ETest) | ❌ Wave 0 | ⬜ pending |
| WO-03 | "w/o" label surfaced on `TeamStanding` (site/standings.html) | 1 | unit | `./mvnw -Dtest=StandingsServiceTest test` | ✅ add assertion | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/.../V17MigrationIT.java` — covers WO-02 (Flyway V17 H2 schema: column exists, nullable, FK present)
- [ ] `src/test/java/org/ctc/e2e/WalkoverE2ETest.java` — covers WO-03/WO-04 E2E (admin marks walkover → "w/o" appears) — `@Tag("e2e")`, test-prefix isolation per CLAUDE.md

*Existing infrastructure (`StandingsServiceTest`, `ScoringServiceTest`, `MatchControllerTest`, `TestHelper.createFullSeasonFixture()`) covers all remaining requirements via added test methods.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| "w/o" badge visual styling across the 3 graphics (match-results, lineup, provisional-scores) | WO-03 | Graphic services are Playwright-rendered + JaCoCo-excluded; visual fidelity not unit-assertable | Visual checkpoint via `playwright-cli` on each graphic preview; screenshot to `.screenshots/` |
| V17 on MariaDB (local/prod) | WO-02 | H2 is auto-tested; MariaDB dialect parity is a post-deploy smoke | `local` profile startup smoke (carry-over operator action class) |

---

## Validation Sign-Off

- [ ] All requirements have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (V17MigrationIT, WalkoverE2ETest)
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s (unit loop)
- [ ] `nyquist_compliant: true` set in frontmatter (after Wave 0 stubs exist)

**Approval:** pending
