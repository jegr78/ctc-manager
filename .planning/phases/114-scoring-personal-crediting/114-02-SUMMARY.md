# Plan 114-02 — Summary

**Wave:** 2 | **Status:** complete | **Branch:** gsd/v1.17-guest-drivers

## What was built

Unified the three divergent team-attribution paths in `DriverRankingService` under one helper implementing the locked home-first / fallback-fielding policy (D-01/D-02/D-03) and closed the alltime null-team gap (D-04). SCORE-02.

### Task 1 — Caller audit (read-only, mandatory pre-refactor)
- `grep -rn "resolveTeamFromLineup|attributeTeamFromRegularOrLineup" src/` → **0 actual callers** outside `DriverRankingService`. The single match was a stale Javadoc comment in `DriverRankingServiceTest` (removed in Task 3). Refactor is fully internal to the service.
- **Production callers of the three public methods** (no signature change needed):
  - `StandingsViewService.java:53` `calculateAlltimeRanking()`, `:112` `calculateRankingForPhase()`
  - `SiteGeneratorService.java:428` `calculateAlltimeRanking(seasonIds)`
  - `DriverRankingPageGenerator.java:54` `calculateRankingForPhase`, `:60` `aggregateAcrossPhases`
- **Test callers / impact sites:** `DriverRankingServiceTest` (16 methods), `DriverRankingPageGeneratorTest`, `SiteGeneratorServiceIT` (mocks), `StandingsViewServiceTest` (mocks).

### Tasks 2 + 3 — RED tests + GREEN implementation (committed together, no red commit)
Per the no-red-commit rule, the 6 new unit tests and the implementation landed in one commit.

**`DriverRankingService` changes:**
- New private `resolveAttributedTeam(Driver, seasonId, raceId)`: home-first `seasonDriverRepository.findBySeasonIdAndDriverId` → `getParentOrSelf()`; else (raceId given) `findByRaceIdAndDriverId` → `getParentOrSelf()`; else season-scoped `findByDriverIdAndRaceMatchdaySeasonId` first → `getParentOrSelf()`; else null. `raceId` is nullable (guarded) for the aggregate path.
- `calculateRankingForPhase`: derives `seasonId` from the already-loaded phase (`phase.getSeason().getId()`), calls `resolveAttributedTeam` in the `computeIfAbsent` lambda.
- `aggregateAcrossPhases`: removed the `regularPhaseTeamIds` / `phaseTeamRepository` / `findByType` plumbing; now calls `resolveAttributedTeam(driver, seasonId, null)`.
- `calculateAlltimeRanking` (private): backfills `driverTeamMap` for result drivers missing from SeasonDriver via `raceLineupRepository.findByDriverId(...).findFirst().getParentOrSelf()` (D-04).
- Removed the two legacy private helpers `resolveTeamFromLineup` + `attributeTeamFromRegularOrLineup` and the now-dead `PhaseTeamRepository` field + import.

**New unit tests (6):** `givenDualRoleGuest...thenAttributedToHomeTeam`, `givenPureGuest...thenAttributedToFieldingTeam`, `givenDualRoleGuestInAggregate...thenSingleRowUnderHomeTeam`, `givenPureGuestInAlltime...thenTeamNotNull`, `givenNormalRosterDriver...thenUnchangedTeam`, `givenDriverWithHomeAndGuestRace...thenSingleRowPointsSummed`.

### Adjusted existing-test assertions/mocks (Mockito STRICT_STUBS)
The unified path no longer calls `seasonPhaseService.findByType` or `phaseTeamRepository.findByPhaseId`, so those stubs became unnecessary (would trip STRICT_STUBS). Removed from:
- `givenMultiPhaseSeason_whenAggregateAcrossPhases_thenRegularTeamGuardsAttribution` — removed `phaseTeamRepository.findByPhaseId` + `findByType` stubs + `PhaseTeam` fixture. Team still resolves to TNR via the season-scoped lineup fallback (no SeasonDriver stubbed → home-first misses → lineup). Assertions unchanged.
- `givenStandInWithoutRegularPhaseTeam_whenAggregateAcrossPhases_thenRaceLineupFallback` — removed `findByType` stub. Team still TNR via lineup fallback.
- `givenAggregateAcrossPhases_whenDriverHasResultsInBothRegularAndPlayoff...` — removed `findByType` stub.
- Removed the `@Mock PhaseTeamRepository` field + import (service no longer depends on it).
- Removed the stale Javadoc referencing the deleted `resolveTeamFromLineup`.
No expected-team assertion changed semantics — all legacy expectations still hold under the unified helper.

## Verification
- `./mvnw -Dtest=DriverRankingServiceTest test` — **22/22 green** (16 existing + 6 new).
- `./mvnw -Dtest=DriverRankingPageGeneratorTest test` — **8/8 green** (real-data consumer of the changed `aggregateAcrossPhases`; row counts unaffected).
- `./mvnw clean test-compile` — exit 0 (no unused imports tree-wide after `PhaseTeamRepository` removal).

## Acceptance criteria — all met
- `resolveAttributedTeam`==1 ✓ ; legacy helpers==0 ✓ ; `getParentOrSelf`≥2 (6) ✓ ; `findBySeasonIdAndDriverId` wired ✓ ; external helper refs==0 ✓ ; unit tests green ✓ ; clean compile ✓

## Deviations
- RED and GREEN committed together (one commit) to honor the project's no-red-commit rule, rather than two separate task commits. Behaviorally TDD-equivalent: tests author the contract, implementation satisfies it.

## Files modified
- `src/main/java/org/ctc/domain/service/DriverRankingService.java`
- `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java`
