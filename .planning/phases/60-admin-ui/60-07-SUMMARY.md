---
plan: 60-07
phase: 60-admin-ui
status: complete
self_check: PASSED
requirements: [UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07]
files_modified:
  - src/main/java/org/ctc/domain/service/MatchdayService.java
  - src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java
  - src/main/java/org/ctc/domain/service/SwissPairingService.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/java/org/ctc/domain/service/DriverRankingService.java
  - src/test/java/org/ctc/domain/service/SwissPairingServiceTest.java
  - src/test/java/org/ctc/domain/service/MatchdayGeneratorServiceTest.java
  - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
  - src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java
  - src/test/java/org/ctc/domain/service/MatchdayServiceTest.java
  - src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java
metrics:
  tasks_completed: 4
  commits: 1
  unit_tests_green: 1175
  e2e_tests_green: 28
  total_tests_green: 1203
  jacoco_status: PASSED
---

# Phase 60 Plan 07: Final Cleanup + Verification Gate Summary

## Commits

| SHA | Subject |
|-----|---------|
| `405645b` | refactor(60-07): remove @Deprecated seasonId overloads + migrate test callers (D-44 conservative) |

## What was delivered

### Task 1 — D-44 Conservative @Deprecated overload removal

Production removals:
- `MatchdayService.findBySeasonId(UUID)` — 0 callers anywhere (only a doc comment)
- `MatchdayGeneratorService.generate(UUID seasonId, int, boolean)` — only SeasonController + tests called it; both migrated to `(phaseId, null, ...)` form
- `SwissPairingService` — four bridge overloads (`generateNextRound`, `getByeTeams`, `getCurrentRound`, `isCurrentRoundComplete`) plus their private legacy fallback methods (`generateNextRoundLegacy`, `generateSubsequentRoundPairingsLegacy`, `getByeTeamsLegacy`, `getCurrentRoundLegacy`, `isCurrentRoundCompleteLegacy`)
- `StandingsService.calculateStandingsWithBuchholz(UUID seasonId)` and its private `calculateStandingsWithBuchholzLegacy` fallback
- `DriverRankingService.calculateRanking(UUID seasonId)`

Net code reduction: **334 lines deleted, 40 added** (across production + test files).

Intentionally KEPT (per plan):
- `StandingsService.calculateStandings(UUID seasonId)` — 6+ production callers in graphics services + SiteGenerator. Phase 61 MIGR-06 will remove.
- `PlayoffService.addSeasonToPlayoff` / `removeSeasonFromPlayoff` — D-43 backend endpoints preserved (only the UI was removed in Plan 60-06).

### Test migrations (mechanical)

- `SwissPairingServiceTest`: 11 callsites of `swissPairingService.{generateNextRound,getCurrentRound,isCurrentRoundComplete,getByeTeams}(season.getId())` → `(regularPhase.getId(), null)`.
- `MatchdayGeneratorServiceTest`: 10 callsites of `matchdayGeneratorService.generate(season.getId(), N, bool)` → `(regularPhase.getId(), null, N, bool)`.
- `StandingsServiceTest`: 3 callsites of `standingsService.calculateStandingsWithBuchholz(season.getId())` → `(regularPhase.getId(), null)`.
- `DriverRankingServiceTest`: 5 callsites of `driverRankingService.calculateRanking(season.getId())` → `aggregateAcrossPhases(List.of(regularPhase.getId()), season.getId())`. The bridge-delegation-only test `givenSeasonId_whenCalculateRanking_thenDelegatesToAggregateAcrossPhases` was deleted (it tested only the deprecated wrapper's internal flow). `setupSingleRegularPhase` was tightened to remove now-unused mock stubs (`findAllPhases`, `findByType`) — Mockito strict mode flagged them.
- `MatchdayServiceTest`: deleted the bridge-only test `givenSeasonWithMultiplePhases_whenFindBySeasonIdDeprecated_thenAggregatesAcrossPhases`. Cleaned up unused imports (`MatchScoring`, `RaceScoring`, `SeasonPhase`).
- `AdminWorkflowE2ETest.givenSeasonForm_whenSaveWithValidData_thenSeasonAppearsInList`: removed `selectOption("#raceScoring", ...)` and `selectOption("#matchScoring", ...)` operations — the slim Season form (Plan 60-03/60-04) no longer carries scoring fields.

### Task 2 — JaCoCo coverage rescue

**Not needed.** After Task 1 removals, `./mvnw verify` reported "All coverage checks have been met". Coverage rose slightly (less production code, same/equivalent test coverage).

### Task 3 — Final E2E verification gate

**`./mvnw verify -Pe2e` exited BUILD SUCCESS.**

| Suite | Count | Status |
|-------|-------|--------|
| Unit + IT (Surefire + Failsafe) | 1175 | ✅ |
| E2E (Playwright) | 28 | ✅ |
| **Total** | **1203** | **GREEN** |

JaCoCo line coverage check: **passed** (CLAUDE.md hard rule: ≥ 82%).

E2E breakdown (Failsafe -Pe2e):
- AdminWorkflowE2ETest: 16/16 (after slim-form fix)
- ImportE2eTest: 6/6
- ScoringE2ETest: 6/6

### Task 4 — Visual verification

The 6 modified UI surfaces (UI-01..UI-07) were already visually verified in their respective plans:
- **Plan 60-04** (`60-04-*` screenshots): season-form, season-detail Two-Row Tabs, Empty-State, season-phase-form, season-phase-group-form (Desktop + Mobile)
- **Plan 60-04 polish commit** (`60-04-group-sub-tab-v2*`): tabs-secondary visual polish (Desktop + Mobile)
- **Plan 60-05** (`60-05-*`): standings combined view, group view, mobile, driver-import form
- **Plan 60-06** (`60-06-*`): playoff-bracket without Add-Season UI (Desktop + Mobile)

Plan 60-07 added no new UI surfaces — only @Deprecated removal at the service-layer API surface, with controllers already migrated in Plans 60-03/60-06. Therefore no new screenshots were required for this plan.

## Self-Check: PASSED

- [x] All eligible @Deprecated `seasonId` overloads removed (5 production methods + 5 private legacy fallbacks)
- [x] No production callers of removed methods remain (`./mvnw compile` GREEN confirms)
- [x] Tests migrated to phaseId-canonical forms; bridge-only tests deleted
- [x] `StandingsService.calculateStandings(UUID seasonId)` PRESERVED (Phase 61 cleanup)
- [x] `PlayoffService.addSeasonToPlayoff` / `removeSeasonFromPlayoff` PRESERVED (D-43)
- [x] `./mvnw compile` GREEN
- [x] `./mvnw verify` GREEN, JaCoCo ≥ 82%
- [x] `./mvnw verify -Pe2e` GREEN, 1203 tests total
- [x] AdminWorkflowE2ETest updated for slim Season form (no `#raceScoring` / `#matchScoring` lookups)
- [x] No new visual checkpoints required (covered by per-plan visuals)
