---
phase: 59-import-test-data
plan: 3
subsystem: admin/TestDataService
tags: [java, spring-boot, test-data-service, season-phase, season-phase-group, phase-team, groups-layout, playoff-seeding]
dependency_graph:
  requires: [59-01]
  provides: [consolidated-2023-season, phase-team-roster-seed, playoff-autoseed]
  affects: [TestDataServiceIntegrationTest, DevDataSeeder]
tech_stack:
  added: [PhaseTeamRepository (new field), PlayoffSeedingService (new field), PhaseLayout.GROUPS]
  patterns: [cascade-save via Season.phases, direct entity construction per D-27, autoSeedBracket per D-14/D-15]
key_files:
  modified:
    - src/main/java/org/ctc/admin/TestDataService.java
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
decisions:
  - D-09: One consolidated Season 2023 replaces the legacy twin-season pattern
  - D-12/D-13: seedPhaseTeams() populates 12 PhaseTeam rows for GROUPS phase (6/group) + LEAGUE phases (group=null)
  - D-14: autoSeedBracket() replaces manual s1aSorted/s1bSorted playoff seeding
  - Rule-1-Bug: VRX driver deduplication — consolidated season requires VRX 1-10 registered once (was 1-6 + 5-10 in separate seasons)
requirements-completed: [DATA-01, DATA-02]
metrics:
  duration: approx 45 minutes
  completed: 2026-04-29
  tasks_completed: 3
  files_modified: 2
---

# Phase 59 Plan 3: TestDataService 2023 Consolidation Summary

**One-liner:** Consolidated 2023 twin-season into ONE GROUPS-layout season with SeasonPhase / SeasonPhaseGroup / PhaseTeam seeding and autoSeedBracket playoff wiring.

## What Was Built

### Final seed shape after `TestDataService.seed()`:

| Season | Year | Number | Layout | Phase | Groups | PhaseTeams |
|--------|------|--------|--------|-------|--------|------------|
| Season 2023 | 2023 | 1 | GROUPS | REGULAR | Group A (6 teams), Group B (6 teams) | 12 rows |
| Regular Season | 2024 | 2 | LEAGUE | REGULAR | none | 10 rows |
| Regular Season | 2026 | 4 | LEAGUE | REGULAR | none | 14 rows |
| Test-Season 2026 | 2026 | 99 | LEAGUE | REGULAR | none | 4 rows |

The `2023 Group A` and `2023 Group B` seasons no longer exist. Seasons count dropped from 5 to 4 for the "dev seasons" (the 2 test seasons testSeason1/testSeason2 remain unchanged).

### seedRoundRobinSeason signature extension (W-1 fix):

```java
private void seedRoundRobinSeason(Season season, SeasonPhase phase, SeasonPhaseGroup group,
                                  int sortIndexOffset, String groupLabel, ...)
```

- Group A: `sortIndexOffset=0` → sortIndex 1, 2, 3 / label "Group A — Matchday 1/2/3"
- Group B: `sortIndexOffset=3` → sortIndex 4, 5, 6 / label "Group B — Matchday 1/2/3"

Both groups share `Matchday.season_id` and `Matchday.phase_id`; distinguished by `Matchday.group_id`.

### seedPlayoffs 2023 — diff summary:

**Removed (~80 lines):**
- Manual team-score calculation loops for Group A and Group B
- `s1aSorted` / `s1bSorted` manual sort blocks
- `playoff2023.getSeasons().add(s1b)` M:N legacy write
- Manual matchup wiring (matchup0.setTeam1, matchup1.setTeam1, etc.)

**Added (~4 lines):**
```java
var playoff2023 = playoffService.createPlayoff(s1.getId(), "2023 Playoffs", 4);
playoffSeedingService.autoSeedBracket(playoff2023.getId());
```

Net: `seedPlayoffs` shrank by approximately 75 lines.

## Test Delta

| Category | Count |
|----------|-------|
| Tests deleted (legacy Group A / Group B named-season) | 4 |
| Tests rewritten (consolidated-aware) | 3 |
| Tests added (new regression suite) | 6 |
| Net test change | +5 |
| Total test suite (was 1127, now) | 1134 |

### Deleted tests:
1. `givenDevSeed_whenStarted_thenS1GroupAHasFormatRoundRobin`
2. `givenDevSeed_whenStarted_thenS1GroupBHasFormatRoundRobin`
3. `givenDevSeed_whenStarted_thenS1GroupAHasSixTeams`
4. `givenDevSeed_whenStarted_thenS1GroupBHasSixTeams`

### Rewritten tests (consolidation-aware):
1. `givenDevSeed_whenStarted_thenConsolidated2023ContainsSubTeams` (was: thenS1GroupsContainSubTeams)
2. `givenDevSeed_whenStarted_thenConsolidated2023HasSixMatchdays` (was: thenRoundRobinGroupAHasThreeMatchdays)
3. `givenDevSeed_whenStarted_thenConsolidated2023MatchdaysSplitEvenlyByGroup` (was: thenRoundRobinGroupBHasThreeMatchdays)

### Added regression tests:
1. `givenDevSeed_whenStarted_thenConsolidated2023HasOneRegularGroupsPhase`
2. `givenDevSeed_whenStarted_thenConsolidated2023HasTwoNamedGroupsInOrder`
3. `givenDevSeed_whenStarted_thenConsolidated2023HasTwelvePhaseTeamsSplitSixSix`
4. `givenDevSeed_whenStarted_thenLeagueSeasonsHavePhaseTeamsWithNullGroup`
5. `givenDevSeed_whenStarted_thenConsolidated2023MatchdaySortIndicesDoNotCollide`

## Verification

```
[INFO] Tests run: 1134, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The existing 1127-test baseline (Phase 58) passes with 7 additional tests (net).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed UK_SEASON_DRIVER unique constraint violation on consolidated 2023 season**
- **Found during:** Task 1+2 integration test execution
- **Issue:** Legacy `seedSeasonDrivers` registered VRX drivers 1-6 for Group A and 5-10 for Group B. With two separate seasons, this was valid. On the consolidated single 2023 season, drivers 5 and 6 would be registered twice for `(season_id, driver_id)`, violating the `UK_SEASON_DRIVER UNIQUE(season_id, driver_id)` constraint.
- **Fix:** Changed VRX SeasonDriver assignments for 2023 to `driverIds("VRX", 1, 10)` — all 10 unique VRX drivers registered once per consolidated season. The matchday-level driver assignment (via `s4TeamDrivers` map) is unchanged — VRX A still uses drivers 1-6, VRX B uses 5-10 for race lineup purposes (RaceLineup is Source of Truth per CLAUDE.md).
- **Files modified:** `src/main/java/org/ctc/admin/TestDataService.java`
- **Commit:** 6dcf652

**2. [Rule 2 - Auto-add] Added REGULAR SeasonPhase to Test-Season 2026 in seedRaceLineups**
- **Found during:** Task 1 (seedPhaseTeams coverage analysis)
- **Issue:** The test seasons created in `seedRaceLineups` (Test-Season 2026, Test-Season 2025) would not have REGULAR phases if only `seedSeasons` was patched. Plan D-12 requires all seeded seasons to have PhaseTeam rows.
- **Fix:** Added LEAGUE-layout REGULAR phase + PhaseTeam rows for Test-Season 2026 inside `seedRaceLineups`. Test-Season 2025 has no race data, so no PhaseTeam seeding was needed there (only test-data isolation, not a GROUP-showcase season).
- **Files modified:** `src/main/java/org/ctc/admin/TestDataService.java`
- **Commit:** 6dcf652

## Known Stubs

None. All seeded data is fully wired — PhaseTeam rows are present, playoff bracket is auto-seeded via `autoSeedBracket`, matchday labels and sort indices are non-colliding.

## Threat Flags

None. No new attack surface introduced. `TestDataService` remains `@Profile("dev")`-gated with early-exit guard (`seasonRepository.count() > 0`). Changes are bounded entirely within the dev seed path.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| src/main/java/org/ctc/admin/TestDataService.java | FOUND |
| src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java | FOUND |
| .planning/phases/59-import-test-data/59-03-SUMMARY.md | FOUND |
| commit 6dcf652 (feat: consolidate 2023 season) | FOUND |
| commit 892fc32 (test: rewrite integration tests) | FOUND |
| Test suite: 1134 tests, 0 failures | PASSED |
