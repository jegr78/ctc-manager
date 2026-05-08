---
phase: 58-service-layer
plan: "03"
subsystem: service-layer
tags: [driver-ranking, phase-aware, tdd, behavior-change, racelineup-fallback, d07]
dependency_graph:
  requires: [58-01, 58-02]
  provides: [per-phase-driver-ranking, season-wide-aggregation, racelineup-team-attribution]
  affects: [DriverRankingService, RaceResultRepository, DriverRankingServiceTest, SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [deprecated-bridge, union-merge-finders, racelineup-source-of-truth, tdd-red-green]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/DriverRankingService.java
    - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
    - src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "5-step magic-name findByRacePlayoffMatchupRoundPlayoffPhaseId resolved at boot — JPQL @Query fallback documented in comment but not activated (Pitfall 1 did not trigger)"
  - "resolveTeamFromLineup in calculateRankingForPhase always returns null (per-phase team attribution is display-only, aggregation handles season-wide attribution)"
  - "SiteGeneratorServiceTest.setUp requires SeasonPhase + PhaseTeam rows so calculateRanking bridge (delegating to findAllPhases) produces non-empty rankings — Rule 1 fix"
  - "D-07 BEHAVIOR CHANGE: PLAYOFF race results now flow into season-wide driver ranking via aggregateAcrossPhases union-merge"
requirements-completed: [SVC-05]
metrics:
  duration_minutes: 110
  completed_date: "2026-04-28"
  tasks_completed: 2
  files_modified: 4
---

# Phase 58 Plan 03: DriverRankingService Phase-Aware Refactor Summary

**One-liner:** DriverRankingService phase-aware refactor — per-phase + season-wide aggregation across all phase types (D-07 BEHAVIOR CHANGE), REGULAR-team attribution with RaceLineup fallback, 84% JaCoCo maintained.

## What Was Built

### DriverRankingService (`src/main/java/org/ctc/domain/service/DriverRankingService.java`)

304 lines. Three new public methods added; two existing public methods preserved unchanged:

- `calculateRankingForPhase(UUID phaseId)` — D-09 primary per-phase entry point. Union-merges `findByRaceMatchdayPhaseId(phaseId)` (REGULAR matchday-linked races) and `findByRacePlayoffMatchupRoundPlayoffPhaseId(phaseId)` (PLAYOFF matchup-linked races), so PLAYOFF phases produce non-empty rankings (D-07).
- `aggregateAcrossPhases(List<UUID> phaseIds, UUID seasonId)` — D-09 season-wide aggregation. Loops phase ids; for each calls `calculateRankingForPhase`; merges per-driver totals (sum points + race count). Attributes team via REGULAR-phase RaceLineup (D-08), with RaceLineup-any-season fallback for stand-ins (D-10).
- `@Deprecated calculateRanking(UUID seasonId)` — D-09 bridge: delegates to `aggregateAcrossPhases(seasonPhaseService.findAllPhases(seasonId).stream().map(SeasonPhase::getId).toList(), seasonId)`.
- `calculateAlltimeRanking()` and `calculateAlltimeRanking(List<UUID>)` — public API structurally unchanged (D-09). Internally use `findByRacePlayoffMatchupIsNull` intentionally (alltime rankings by design cover REGULAR-phase results only — documented in Javadoc).

New injected dependencies: `SeasonPhaseService`, `PhaseTeamRepository`, `RaceLineupRepository`.

### RaceResultRepository (`src/main/java/org/ctc/domain/repository/RaceResultRepository.java`)

Two new finders added (D-22):
- `findByRaceMatchdayPhaseId(UUID phaseId)` — 4-step magic-name navigation via `Race.matchday.phase.id`. Resolved at boot.
- `findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId)` — 5-step magic-name navigation via `Race.playoffMatchup.round.playoff.phase.id`. **Resolved at boot without issue** — no PropertyReferenceException. JPQL `@Query` fallback documented in Javadoc comment but NOT activated.

**Pitfall 1 outcome:** The 5-step magic-name finder resolved correctly. The JPQL fallback is present as a comment for future reference only.

### DriverRankingServiceTest (`src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java`)

Total: 15 tests (8 existing + 7 new). All pass.

New tests added (7):
1. `givenRegularPhase_whenCalculateRankingForPhase_thenAggregatesViaMatchdayPhaseId` (D-07 REGULAR path)
2. `givenPlayoffPhase_whenCalculateRankingForPhase_thenAggregatesViaPlayoffMatchupChain` (D-07 PLAYOFF path)
3. `givenMultiPhaseSeason_whenAggregateAcrossPhases_thenRegularTeamGuardsAttribution` (D-07 + D-08)
4. `givenStandInWithoutRegularPhaseTeam_whenAggregateAcrossPhases_thenRaceLineupFallback` (D-10)
5. `givenSeasonId_whenCalculateRanking_thenDelegatesToAggregateAcrossPhases` (D-09 bridge)
6. `givenPlacementPhase_whenCalculateRankingForPhase_thenIncludesPlacementResults` (D-07 PLACEMENT)
7. `givenAggregateAcrossPhases_whenDriverHasResultsInBothRegularAndPlayoff_thenSinglyListedWithMergedTotalPoints` (D-09 merge)

Existing tests updated (3):
- `givenTiedPointsWithDifferentRaceCounts_whenCalculateRanking_thenFewerRacesRankedFirst` — rewritten to use `setupSingleRegularPhase` helper (old code used undefined `sd1`/`sd2` variables and the old `findByRaceMatchdaySeasonId` mock)
- Three `calculateRankingForPhase` tests had unnecessary `raceLineupRepository` stubs removed (the private `resolveTeamFromLineup` method always returns null without calling the repo)

### SiteGeneratorServiceTest (`src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`)

Rule 1 bug fix: `setUp` now creates a `SeasonPhase` (REGULAR, LEAGUE) and two `PhaseTeam` rows (one per test team), then links the test matchday to the phase. Required because:
- `calculateRanking(seasonId)` now delegates to `findAllPhases(seasonId)` — without a phase, the list is empty and driver rankings are empty
- `StandingsService.calculateStandings(seasonId)` prefers the phase-aware path when a REGULAR phase exists; without `PhaseTeam` rows, standings are empty
- 8 `SiteGeneratorServiceTest` tests would fail without this fix

## D-07 BEHAVIOR CHANGE — CRITICAL CALLOUT

**PLAYOFF race results now contribute to season-wide driver ranking.**

- **Before Phase 58:** `calculateRanking(seasonId)` used `findByRaceMatchdaySeasonId` which only catches results from `Race.matchday` — PLAYOFF races link via `Race.playoffMatchup`, not via `Race.matchday`. PLAYOFF race results were silently excluded from season-wide standings.
- **After Phase 58:** `aggregateAcrossPhases` union-merges both `findByRaceMatchdayPhaseId` (REGULAR) and `findByRacePlayoffMatchupRoundPlayoffPhaseId` (PLAYOFF) for every phase in the season. PLACEMENT phases also contribute.
- **Impact:** For any season with PLAYOFF data, season-wide driver ranking totals WILL shift upward (more races counted). This is intentional per locked decision D-07.

## Decisions Implemented

| Decision | Status | Notes |
|----------|--------|-------|
| D-07 | Done | All phase types contribute to season-wide ranking (behavior change documented above) |
| D-08 | Done | REGULAR-phase team attribution via RaceLineup filtered by REGULAR-phase team IDs |
| D-09 | Done | `calculateRankingForPhase` + `aggregateAcrossPhases` + `@Deprecated calculateRanking` bridge |
| D-10 | Done | RaceLineup fallback in `attributeTeamFromRegularOrLineup` for stand-ins without REGULAR PhaseTeam |
| D-22 | Done | Both finders added; 5-step magic-name resolved without JPQL fallback needed |

## Coverage

- Before: 84% (from 58-02 verify)
- After: 84% instruction coverage — "All coverage checks have been met" (minimum 82%)
- Test count: 1097 total, 0 failures

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed compilation error in givenTiedPointsWithDifferentRaceCounts test**
- **Found during:** Test finalization step
- **Issue:** Test referenced `sd1` and `sd2` (SeasonDriver variables) that were declared as fields in the class but used inside the test with `when(seasonDriverRepository.findBySeasonId(...)).thenReturn(List.of(sd1, sd2))` — these variables were removed when the `@BeforeEach` was refactored but the test body was not updated. Also used old mock `findByRaceMatchdaySeasonId` which no longer applies.
- **Fix:** Rewrote test to use `setupSingleRegularPhase` helper with correct result list. Removed the `seasonDriverRepository` stub (not needed for `calculateRanking` anymore).
- **Files modified:** `DriverRankingServiceTest.java`
- **Commit:** 8220452

**2. [Rule 1 - Bug] Removed unnecessary raceLineupRepository stubs from 3 calculateRankingForPhase tests**
- **Found during:** Test finalization step
- **Issue:** Three new per-phase tests mocked `raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId` but `calculateRankingForPhase` does not call the repo (the private `resolveTeamFromLineup` always returns null). Mockito strict mode threw `UnnecessaryStubbingException`.
- **Fix:** Removed the three unnecessary stubs.
- **Files modified:** `DriverRankingServiceTest.java`
- **Commit:** 8220452

**3. [Rule 1 - Bug] Fixed SiteGeneratorServiceTest — missing SeasonPhase and PhaseTeam setup**
- **Found during:** `./mvnw verify` run (8 test failures across SiteGeneratorServiceTest)
- **Issue:** `calculateRanking(seasonId)` now delegates to `aggregateAcrossPhases(findAllPhases(seasonId), seasonId)`. The SiteGeneratorServiceTest creates test seasons without SeasonPhase rows. With no phases, `findAllPhases` returns empty list → empty driver rankings → 2 driver ranking tests fail. `StandingsService.calculateStandings(seasonId)` also paths through the phase-aware route when a REGULAR phase is found, but needs PhaseTeam rows to build standings → 6 standings/team-profile tests fail.
- **Fix:** Added `SeasonPhaseRepository` + `PhaseTeamRepository` injection to the test class; `setUp` now creates one REGULAR SeasonPhase, two PhaseTeam rows (one per test team), and links the test matchday to the phase.
- **Files modified:** `SiteGeneratorServiceTest.java`
- **Commit:** 8220452

### Execution Context

This plan was executed in two parts. The previous executor was interrupted mid-GREEN (rescue commit `258d6ad`). This continuation executor finalized the test rewrites, fixed the three issues above, ran `./mvnw verify` to confirm full-suite green, and wrote this SUMMARY.

## Wave 3+ Dependencies Confirmed

Plan 58-04 (SwissPairingService) and later plans can use:
- `SeasonPhaseService.findAllPhases(seasonId)` — available
- `DriverRankingService.calculateRankingForPhase(phaseId)` — available
- `DriverRankingService.aggregateAcrossPhases(phaseIds, seasonId)` — available
- `RaceResultRepository.findByRaceMatchdayPhaseId(phaseId)` — available

## Self-Check

### Files Exist
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — modified
- `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` — modified
- `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` — modified
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — modified

### Commits Exist
- `0e8f1f4` — TDD-RED (cherry-picked from previous session)
- `258d6ad` — rescue partial GREEN (from previous session)
- `8220452` — finalize TDD-GREEN (this session)

### Verification
- `./mvnw verify` exit 0: YES
- Tests: 1097/1097 green
- JaCoCo: 84% (>= 82% minimum)
- `CtcManagerApplicationTests` boots: YES (5-step magic-name finder resolved)
- D-07 behavior change documented: YES (section above)
- JPQL fallback path: NOT activated — magic-name resolved at boot

## Self-Check: PASSED
