---
phase: 109
slug: walkover-handling
status: validated
nyquist_compliant: true
nyquist_status: compliant
wave_0_complete: not_applicable
open_validation_items: 0
created: 2026-05-30
validated: 2026-05-31
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

| Req ID | Behavior | Plan / Wave | Test Type | Test Method | Status |
|--------|----------|-------------|-----------|-------------|--------|
| WO-02 | V17 migration adds nullable, indexed `walkover_team_id` FK on `matches` (H2) | 109-01 / W1 | integration | `V17MigrationIT` ×3 (`…ColumnThenExists`, `…NullabilityThenNullable`, `…IndexThenCovered`) | ✅ green |
| WO-01/WO-02 | `Match.isWalkoverFor(team)` null-safe UUID compare (entity helper) | 109-01 | unit | `MatchWalkoverTest` ×4 (no-walkover, home-only, away-only, null-team) | ✅ green |
| WO-01 | `recomputeMatchScoresFromAllLegs` / `aggregateMatchScores` skip walkover races (critical guard) | 109-01 / W1 | unit | `ScoringServiceTest.givenWalkoverMatchRace_…thenScoresRemainUnchanged`, `givenWalkoverMatchRaceWithResults_…thenScoresRemainNull` | ✅ green |
| WO-01 | Home-forfeit walkover: opponent wins (addWin+pointsWin), forfeiter loses (addLoss+0) | 109-02 / W2 | unit | `StandingsServiceTest.givenWalkoverMatchHomeForfeit_…thenOpponentWinsAndForfeiterLoses` | ✅ green |
| WO-01 | Away-forfeit walkover: opponent (home) wins, forfeiter (away) loses | 109-02 / W2 | unit | `StandingsServiceTest.givenWalkoverMatchAwayForfeit_…thenOpponentWinsAndForfeiterLoses` | ✅ green |
| WO-01 | Walkover precedes partial race results (D-08) — scores ignored | 109-02 / W2 | unit | `StandingsServiceTest.givenWalkoverMatchWithPartialScores_…thenWalkoverTakesPrecedence` | ✅ green |
| WO-01 | Winner gets full team-race score; no synthetic diff (D-07) | 109-02 / W2 | unit | `StandingsServiceTest.givenWalkoverMatch_…thenWinnerGetsFullTeamRaceScore` | ✅ green |
| WO-03 | `hasWalkover` flag surfaced on `TeamStanding` (drives site/standings.html `(w/o)`) | 109-02 / W2 | unit | `StandingsServiceTest.givenWalkoverMatch_…thenHasWalkoverFlagSet` | ✅ green |
| WO-02/WO-04 | MatchController save-edit persists `walkover_team_id`; scores cleared on mark | 109-03 / W3 | integration | `MatchControllerTest.givenMatchEditForm_…thenWalkoverPersisted`, `givenScoredMatch_…thenScoresCleared` | ✅ green |
| WO-04 | Admin can clear walkover (null team) through edit form | 109-03 / W3 | integration | `MatchControllerTest.givenWalkoverMatch_…WithNullWalkoverTeam_thenWalkoverCleared` | ✅ green |
| WO-04 | Validation: team ∉ {home,away} or bye match → `errorMessage` flash | 109-03 / W3 | integration | `MatchControllerTest.givenByeMatch_…thenErrorFlash`, `givenMatchEditFormWithUnrelatedTeam_…thenErrorFlash` | ✅ green |
| WO-03 | "w/o" label next to forfeiter in matchday-detail after marking | 109-04 / W4 | e2e | `WalkoverE2ETest.givenMatchEditForm_whenMarkWalkover_thenWoLabelAppearsInMatchdayDetail` | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Evidence (re-run 2026-05-31):** unit + Spring-context walkover tests re-confirmed live —
`StandingsServiceTest`/`ScoringServiceTest`/`MatchWalkoverTest` (50 tests, 0 fail) and
`MatchControllerTest` (10 tests, 0 fail, incl. 5 walkover admin tests), both BUILD SUCCESS.
`V17MigrationIT` (3) and `WalkoverE2ETest` (1, `@Tag("e2e")`, test-prefix isolated) were
authored green with their implementation and proven by the phase's `clean verify -Pe2e`
gate (109-01/109-04 SUMMARY; 2 resolved REVIEW passes; STATE.md).

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

- [x] All requirements have an automated verify authored in the same plan as their implementation (green on commit)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] No test committed `@Disabled` or red — new test files landed green with their implementation (V17MigrationIT in 109-01, WalkoverE2ETest in 109-04)
- [x] No watch-mode flags
- [x] Feedback latency < 30s (unit loop)
- [x] `nyquist_compliant: true` set in frontmatter (every requirement has an automated verify authored green with its implementation)

**Approval:** approved 2026-05-31

---

## Validation Audit 2026-05-31

Re-audit during `/gsd-audit-milestone v1.15` follow-up. The 2026-05-30 file was a
pre-execution **draft** (all statuses ⬜ pending, sign-off unchecked); the phase has since
been fully executed (5 plans, 2 resolved REVIEW passes, `clean verify -Pe2e` green).

| Metric | Count |
|--------|-------|
| Gaps found (automatable, unfilled) | 0 |
| Requirements with green automated verify | 4 (WO-01..04) |
| Test methods mapped | 20 (3 IT + 4 entity + 2 scoring + 5 standings + 5 controller + 1 E2E) |
| Generated test files this audit | 0 (all tests pre-existing, authored in-phase) |
| Escalated impl bugs | 0 |
| Manual-only items | 1 (WO-03 graphic-badge **visual** styling — Playwright-rendered, JaCoCo-excluded; data-flag + matchday label are automated) |

**Outcome:** `status: draft → validated`, `nyquist_compliant: false → true`. Every WO
requirement has a green automated verify; the only manual-only item is the graphic badge's
visual fidelity (inherently non-unit-assertable, already documented). No
`gsd-nyquist-auditor` spawn warranted — nothing to generate.

**Observation (out of scope, not fixed here):** `MatchControllerTest` is a `@SpringBootTest`
+ `@AutoConfigureMockMvc` Spring-context test that is **untagged** (runs in Surefire rather
than Failsafe). Per CLAUDE.md "Tag Tests by Category", Spring-context tests are conventionally
`@Tag("integration")`. It runs green either way; flagging as a minor pre-existing
tag-convention drift for a future cleanup, not a validation gap.
