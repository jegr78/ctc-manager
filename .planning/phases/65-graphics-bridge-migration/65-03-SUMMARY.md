---
plan_id: 65-03
phase: 65
phase_name: graphics-bridge-migration
title: Delete bridge method + StandingsServiceTest triage + SiteGeneratorServiceIT cleanup (SC1 = 0)
status: complete
completed: 2026-05-07
wave: 3
depends_on: [65-01, 65-02]
subsystem: domain-service
tags: [refactor, bridge-removal, test-triage, jacoco]
requires: [65-01, 65-02]
provides: [SC1=0, SC4-option-a, W-A-closed]
affects: [StandingsService, StandingsServiceTest, SiteGeneratorServiceIT, MatchdayScheduleGraphicServiceTest]
tech_stack_added: []
tech_stack_patterns: [canonical-phase-aware-API]
key_files_created: []
key_files_modified:
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java
  - src/test/java/org/ctc/admin/service/MatchdayScheduleGraphicServiceTest.java
decisions:
  - "D-01 executed: hard removal of calculateStandings(UUID seasonId) — no deprecation runway, option (a) per ROADMAP SC4"
  - "D-09/D-14 triage: 3 tests deleted (2 duplicate semantics + 1 bridge-delegation) + 10 rewrites to canonical API"
  - "Rule 1 deviation: MatchdayScheduleGraphicServiceTest stub rewritten inline (missed by Wave 1 inventory)"
metrics:
  duration: ~30 min
  completed: 2026-05-07
  tasks: 3/3
  files: 4
---

# Phase 65 — Plan 03 Summary: Bridge removed, SC1 = 0 achieved

## One-liner

Deleted `StandingsService.calculateStandings(UUID seasonId)` bridge and its 3 bridge-only tests; rewrote 10 test call sites to the canonical `(phaseId, null)` API; fixed a missed mock stub in `MatchdayScheduleGraphicServiceTest` — SC1 = 0, JaCoCo 87.8%.

## D-01 / SC1: Bridge Method Deleted

`StandingsService.java` lines 139-151 removed:

- Section-divider comments (`// SeasonId convenience overload ...`)
- Javadoc block (3 lines)
- `@Transactional(readOnly = true) public List<TeamStanding> calculateStandings(UUID seasonId)` method body (4 lines)

Net reduction: ~13 lines. `StandingsService` now exposes only the canonical `calculateStandings(UUID phaseId, UUID groupId)` overload.

**SC1 gate:** `grep -nR "calculateStandings(seasonId" src/main/java | wc -l` = **0**

**SC4 gate:** `grep -c "public List<TeamStanding> calculateStandings(UUID seasonId)" src/main/java/org/ctc/domain/service/StandingsService.java` = **0** — option (a) "removed" chosen per ROADMAP.

## D-09 / D-14 Triage: StandingsServiceTest

13 bridge-overload call sites triaged:

### 3 Deleted Test Methods

| Test Method | Reason |
|---|---|
| `givenOneMatch_whenCalculateStandings_thenWinnerGetThreePoints` (was ~line 197) | DUPLICATE — winner/loser logic covered by canonical-API tests |
| `givenEqualScores_whenCalculateStandings_thenBothTeamsGetDrawPoint` (was ~line 229) | DUPLICATE — draw-point logic covered by canonical-API tests |
| `givenSeasonId_whenCalculateStandings_thenDelegatesToRegularPhase` (was ~line 678) | BRIDGE-DELEGATION test — the bridge no longer exists; test had no residual value |

### 10 Rewritten Call Sites

Pattern: `standingsService.calculateStandings(season.getId())` → `standingsService.calculateStandings(regularPhase.getId(), null)`

The `regularPhase` field already existed in `@BeforeEach` (line 94). No fixture work required — the lenient mock chain `findByMatchdayPhaseId(regularPhase.getId())` already redirected to `findByMatchdaySeasonId(season.getId())`.

| Test Method | Nested Class | Semantic |
|---|---|---|
| `givenCustomMatchScoring_whenCalculateStandings_thenCustomPointsApplied` | MatchBasedStandingsTest | Custom 2-1-0 scoring rule |
| `givenByeMatch_whenCalculateStandings_thenTeamGetsWin` | MatchBasedStandingsTest | Bye match win |
| `givenMultipleMatches_whenCalculateStandings_thenSortedByPointsThenPointDifference` | MatchBasedStandingsTest | Sort order by points then point diff |
| `givenTeamWithNoGames_whenCalculateStandings_thenTeamExcluded` | MatchBasedStandingsTest | Zero-game exclusion |
| `givenMatchWithNoScores_whenCalculateStandings_thenMatchSkipped` | MatchBasedStandingsTest | Null-score skip |
| `givenReplacedTeam_whenCalculateStandings_thenSuccessorInheritsResults` | TeamSuccessionTest | Succession inheritance |
| `givenReplacedTeam_whenCalculateStandings_thenPredecessorNotInStandings` | TeamSuccessionTest | Predecessor exclusion |
| `givenReplacedTeamAndNewMatches_whenCalculateStandings_thenBothResultsMerged` | TeamSuccessionTest | Successor accumulates old + new results |
| `givenSuccessionChain_whenCalculateStandings_thenFinalSuccessorInheritsAll` | TeamSuccessionTest | Multi-hop succession chain |
| `givenReplacedTeamWithBye_whenCalculateStandings_thenSuccessorInheritsByeWin` | TeamSuccessionTest | Succession + bye win |

## Pitfall 3: SiteGeneratorServiceIT Cleanup

Lines 153-154 removed from `SiteGeneratorServiceIT.java`:

```java
// explicitly verify the legacy bridges are NOT invoked (proves swap happened, not just an additive call)
verify(standingsService, never()).calculateStandings(seasonId);
```

The `never` import was also removed (now unused). The assertion was vacuously satisfied after bridge deletion — removing it prevents a compile error.

## Verification Results

### SC1
```
grep -nR "calculateStandings(seasonId" src/main/java | wc -l
0
```

### SC4
```
grep -c "public List<TeamStanding> calculateStandings(UUID seasonId)" src/main/java/org/ctc/domain/service/StandingsService.java
0
```

### SC3 / QUAL-01: ./mvnw verify
- Tests run: **1229**, Failures: 0, Errors: 0, Skipped: 4
- JaCoCo line coverage: **87.8%** (5925/6748 lines covered) — Phase 64 baseline was 85.6%; net gain of +2.2 pp (the 8 new D-11/D-12 tests from Plan 65-01 more than offset the 3 deleted bridge tests)
- BUILD SUCCESS

### Phase-closing E2E Gate: ./mvnw verify -Pe2e
- Surefire: 1229 tests, 0 failures, 0 errors
- Failsafe E2E: **31 tests**, 0 failures, 0 errors
- BUILD SUCCESS

### SC2 (LEAGUE behavior preserved)
Satisfied by the 5 D-11 LEAGUE-regression tests added in Plan 65-01, each verifying `calculateStandings(eq(phaseId), isNull())`. All green.

## Atomic Commit

```
6523959 refactor(65): remove deprecated calculateStandings(seasonId) bridge
```

4 files changed, 14 insertions(+), 108 deletions(-)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] MatchdayScheduleGraphicServiceTest missed stub rewrite**

- **Found during:** Task 3 verification (`./mvnw verify` → Unresolved compilation problem at line 95)
- **Issue:** `MatchdayScheduleGraphicServiceTest.givenMatchesWithDifferentDateTimes_whenPrepareBaseContext_thenSortedByDateTime` was not in Plan 65-01's Wave 1 test inventory. It called `when(standingsService.calculateStandings(season.getId())).thenReturn(List.of())` which became a compile error after the bridge method was deleted in Task 2.
- **Fix:** Rewrote stub from `season.getId()` to `matchday.getPhase().getId(), null` — the same canonical API rewrite pattern used throughout Wave 1.
- **Files modified:** `src/test/java/org/ctc/admin/service/MatchdayScheduleGraphicServiceTest.java`
- **Included in:** Same atomic commit `6523959`
- **Impact on SC1:** None — this was a test file (`src/test/java`), not a production caller. SC1 gate scopes only `src/main/java`.

**2. [Rule 1 - Bug] Stale Javadoc comment in StandingsServiceTest**

- **Found during:** Task 1 sanity check (grep -c "calculateStandings(season.getId())" returned 1)
- **Issue:** D-19 Javadoc at line 813 referenced `calculateStandings(season.getId())` in its description of legacy RED-gate behavior. After rewrites, this was the only remaining match to the bridge pattern.
- **Fix:** Updated comment to describe the behavior without naming the deleted method signature.
- **Files modified:** `src/test/java/org/ctc/domain/service/StandingsServiceTest.java`
- **Included in:** Same atomic commit `6523959`

**3. [Rule 1 - Bug] Unused imports removed**

- `org.mockito.Mockito.verify` removed from `StandingsServiceTest` (only user was the deleted `givenSeasonId_whenCalculateStandings_thenDelegatesToRegularPhase` test)
- `org.mockito.Mockito.never` removed from `SiteGeneratorServiceIT` (only user was the deleted negative verify at line 154)

## Final v1.9 Milestone Scoreboard

| Phase | Name | Status |
|---|---|---|
| 55 | season-phases | Complete |
| 56 | groups-layout | Complete |
| 57 | group-standings | Complete |
| 58 | phase-standings | Complete |
| 59 | swiss-pairing | Complete |
| 60 | graphics-generation | Complete |
| 61 | cleanup-quality-gate | Complete |
| 62 | site-generation | Complete |
| 63 | settings-graphic | Complete |
| 64 | nyquist-validation-sweep | Complete |
| **65** | **graphics-bridge-migration** | **Complete** |

**Audit item W-A** (`StandingsService.calculateStandings(UUID seasonId)` bridge remaining in production code): **CLOSED at the source** — option (a) removal chosen per ROADMAP SC4.

## Known Stubs

None — all data sources are wired; no placeholder values introduced.

## Threat Flags

None — refactor only; no new network endpoints, auth paths, file access patterns, or schema changes.

## Self-Check

- `src/main/java/org/ctc/domain/service/StandingsService.java` — bridge method absent: confirmed
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — 10 rewrites present, 3 methods absent: confirmed
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java` — negative verify absent: confirmed
- `src/test/java/org/ctc/admin/service/MatchdayScheduleGraphicServiceTest.java` — stub rewritten: confirmed
- Commit `6523959` exists: confirmed (`git log --oneline -1` = `6523959 refactor(65): remove deprecated calculateStandings(seasonId) bridge`)

## Self-Check: PASSED
