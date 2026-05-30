---
phase: 109
slug: walkover-handling
status: draft
nyquist_compliant: false
wave_0_complete: not_applicable
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

Each test is authored in the same plan as the implementation that makes it pass — committed green, never @Disabled or red (no separate "Wave 0" red-stub plan; per CLAUDE.md "Clean Maven Build is the Source of Truth").

| Req ID | Behavior | Plan / Wave | Test Type | Automated Command | New File | Status |
|--------|----------|-------------|-----------|-------------------|----------|--------|
| WO-02 | V17 migration adds nullable `walkover_team_id` FK on `matches` (H2) | 109-01 / W1 | integration | `./mvnw -Dit.test=V17MigrationIT -DfailIfNoTests=false verify` | new `V17MigrationIT` (green w/ V17) | ⬜ pending |
| WO-01 | `recomputeMatchScoresFromAllLegs` skips walkover races (critical guard) | 109-01 / W1 | unit | `./mvnw -Dtest=ScoringServiceTest test` | add test | ⬜ pending |
| WO-01 | Home-forfeit walkover: opponent wins (addWin+pointsWin), forfeiter loses (addLoss+0) | 109-02 / W2 | unit | `./mvnw -Dtest=StandingsServiceTest test` | add nested test | ⬜ pending |
| WO-01 | Away-forfeit walkover: opponent (home) wins, forfeiter (away) loses | 109-02 / W2 | unit | `./mvnw -Dtest=StandingsServiceTest test` | add nested test | ⬜ pending |
| WO-01 | Walkover precedes partial race results (D-08) — scores ignored | 109-02 / W2 | unit | `./mvnw -Dtest=StandingsServiceTest test` | add nested test | ⬜ pending |
| WO-01 | No synthetic point difference / no Buchholz from walkover (D-07) | 109-02 / W2 | unit | `./mvnw -Dtest=StandingsServiceTest test` | add nested test | ⬜ pending |
| WO-03 | "w/o" label surfaced on `TeamStanding` (site/standings.html) | 109-02 / W2 | unit | `./mvnw -Dtest=StandingsServiceTest test` | add assertion | ⬜ pending |
| WO-02 | MatchController save-edit persists `walkover_team_id` FK | 109-03 / W3 | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | add test method | ⬜ pending |
| WO-04 | Admin can mark AND clear walkover through edit form | 109-03 / W3 | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | add test method | ⬜ pending |
| WO-04 | Validation: team ∉ {home,away} or bye match → `errorMessage` flash | 109-03 / W3 | integration | `./mvnw -Dit.test=MatchControllerTest -DfailIfNoTests=false verify` | add 2 test methods | ⬜ pending |
| WO-03 | "w/o" label next to forfeiter in matchday-detail after marking | 109-04 / W4 | e2e | `./mvnw verify -Pe2e` (WalkoverE2ETest) | new `WalkoverE2ETest` (green) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## New Test Files (authored green with their implementation)

- `src/test/java/db/migration/V17MigrationIT.java` — WO-02; created in **plan 109-01 together with V17** (green on commit, not a red stub).
- `src/test/java/org/ctc/e2e/WalkoverE2ETest.java` — WO-03/WO-04 E2E; created **enabled in plan 109-04**, after the dropdown (109-03) and the matchday-detail label (109-04) exist (green, never `@Disabled`). `@Tag("e2e")`, test-prefix isolation per CLAUDE.md.

*Existing infrastructure (`StandingsServiceTest`, `ScoringServiceTest`, `MatchControllerTest`, `TestHelper.createFullSeasonFixture()`) covers all remaining requirements via added test methods. No separate Wave-0 stub plan.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| "w/o" badge visual styling across the 3 graphics (match-results, lineup, provisional-scores) | WO-03 | Graphic services are Playwright-rendered + JaCoCo-excluded; visual fidelity not unit-assertable | Visual checkpoint via `playwright-cli` on each graphic preview; screenshot to `.screenshots/` |
| V17 on MariaDB (local/prod) | WO-02 | H2 is auto-tested; MariaDB dialect parity is a post-deploy smoke | `local` profile startup smoke (carry-over operator action class) |

---

## Validation Sign-Off

- [ ] All requirements have an automated verify authored in the same plan as their implementation (green on commit)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] No test committed `@Disabled` or red — new test files land green with their implementation (V17MigrationIT in 109-01, WalkoverE2ETest in 109-04)
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s (unit loop)
- [ ] `nyquist_compliant: true` set in frontmatter (every requirement has an automated verify authored green with its implementation)

**Approval:** pending
