---
phase: 58-service-layer
plan: 04
subsystem: domain-service
tags: [service-layer, matchday, swiss, phase-aware, group-aware, tdd]
dependency_graph:
  requires: [58-01, 58-02]
  provides: [SVC-04, MatchdayGeneratorService-phase-aware, SwissPairingService-phase-aware]
  affects: [SeasonController, SwissRoundsController, MatchdayRepository]
tech_stack:
  added: []
  patterns:
    - per-group-isolation via phaseId+groupId canonical signatures
    - layout-validation guard (LEAGUE/GROUPS vs groupId presence)
    - @Deprecated bridge with findByType fallback for pre-Phase-57 compat
    - legacy fallback methods for seasons without REGULAR phase row
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java
    - src/main/java/org/ctc/domain/service/SwissPairingService.java
    - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
    - src/test/java/org/ctc/domain/service/MatchdayGeneratorServiceTest.java
    - src/test/java/org/ctc/domain/service/SwissPairingServiceTest.java
decisions:
  - GeneratorFormData carries both Season (template compat) and SeasonPhase (Phase-60 prep) per A7 min-churn
  - @Deprecated bridges use findByType (Optional) fallback not findRegularPhase to preserve SeasonControllerTest compat
  - Legacy fallback methods added to SwissPairingService for pre-Phase-57 seasons without REGULAR phase row
  - New phase-aware test helpers create fresh seasons to avoid UNIQUE(season_id, phase_type) constraint conflicts
requirements-completed: [SVC-04]
metrics:
  duration_seconds: 1436
  completed: 2026-04-28
  tasks_completed: 2
  files_changed: 5
---

# Phase 58 Plan 04: MatchdayGeneratorService + SwissPairingService Phase/Group-Aware Summary

MatchdayGeneratorService and SwissPairingService refactored to operate per (phaseId, groupId) with layout validation, per-group isolation, and @Deprecated seasonId bridges. MatchdayRepository gains three new finders including the existsByPhaseSeasonId prerequisite for Plan 58-06 D-18 delete-guard.

## Public Surface Changes

### MatchdayGeneratorService

**Canonical (new):**
```java
public void generate(UUID phaseId, UUID groupId, int numberOfRounds, boolean homeAndAway)
```
- D-16 layout validation: LEAGUE requires `groupId=null`; GROUPS requires non-null `groupId`
- Teams sourced from `PhaseTeamRepository.findByPhaseId/findByPhaseIdAndGroupId` (not `season.getEligibleTeams()`)
- Generated matchdays have `setPhase(phase)` + (if GROUPS) `setGroup(group)` — T-58-04-01/02 mitigations

**@Deprecated bridge (D-01, D-03):**
```java
@Deprecated
public void generate(UUID seasonId, int numberOfRounds, boolean homeAndAway)
// → generate(seasonPhaseService.findRegularPhase(seasonId).getId(), null, ...)
```

**GeneratorFormData record reshaped (A7 min-churn):**
```java
// Before:
public record GeneratorFormData(Season season, int teamCount, int optimalRounds)
// After:
public record GeneratorFormData(Season season, SeasonPhase phase, int teamCount, int optimalRounds)
```
Keeps `Season` for backward-compat template references; adds `SeasonPhase phase` for Phase-60 cutover. Phase 60 removes `Season season` from this record.

### SwissPairingService

**Canonical (new) — all 4 methods:**
```java
public Matchday generateNextRound(UUID phaseId, UUID groupId)
public Set<UUID>  getByeTeams(UUID phaseId, UUID groupId)
public int        getCurrentRound(UUID phaseId, UUID groupId)
public boolean    isCurrentRoundComplete(UUID phaseId, UUID groupId)
```
All validate layout vs groupId presence via `validateLayoutAndGroupId(phase, groupId)`.

**D-21 per-group isolation:** Each group has its own bye list, round counter, and completion status. Different groups can be at different rounds — no implicit cross-group sync.

**Pitfall 6 (dependency on Plan 58-02):** `generateNextRound` calls `standingsService.calculateStandings(phaseId, groupId)` — the phase-aware signature shipped in Plan 58-02. Verified working.

**@Deprecated bridges — all 4 (D-01, D-03):**
```java
@Deprecated public Matchday generateNextRound(UUID seasonId)
@Deprecated public Set<UUID>  getByeTeams(UUID seasonId)
@Deprecated public int        getCurrentRound(UUID seasonId)
@Deprecated public boolean    isCurrentRoundComplete(UUID seasonId)
```
Bridges use `findByType` (Optional) not `findRegularPhase` — falls back to legacy season-based path when no REGULAR phase exists. This preserves `SeasonControllerTest` which tests Swiss round pages on seasons created without the V4 migration (no REGULAR phase row).

### MatchdayRepository (D-22)

```java
@EntityGraph(attributePaths = {"season", "phase"})
List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId);

@EntityGraph(attributePaths = {"season", "phase", "group"})
List<Matchday> findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId);

boolean existsByPhaseSeasonId(UUID seasonId);  // D-18 delete-guard prerequisite for Plan 58-06
```

## Test Counts

| Test Class | Before | New | After |
|---|---|---|---|
| MatchdayGeneratorServiceTest | 12 | +5 | 17 |
| SwissPairingServiceTest | 8 | +6 | 14 |
| **Total** | **20** | **+11** | **31** |

All 31 tests pass. Full suite: **1108 tests, 0 failures**.

## JaCoCo Coverage

| Metric | Value |
|---|---|
| Line coverage | 84% (26676/31680) |
| Gate threshold | 82% |
| Delta vs Wave 2 | +0% (held stable) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Existing tests broke after @Deprecated bridge change**
- **Found during:** Task 2 GREEN
- **Issue:** Existing tests called `generate(seasonId, ...)` / `generateNextRound(seasonId)` which became bridges routing through `findRegularPhase`. The old `@BeforeEach setUp()` did not create a REGULAR phase, so the bridge threw `EntityNotFoundException` → 404 on the Swiss rounds page endpoint test.
- **Fix:** (a) Updated `setUp()` in both test files to create a REGULAR phase alongside the season. (b) Updated `addTeams()` helpers to also register teams as `PhaseTeam` entries on the REGULAR phase. (c) Updated specific tests that manually add teams to also add PhaseTeam rows. (d) Made bridges use `findByType` (Optional) with legacy fallback instead of hard `findRegularPhase`, consistent with the `StandingsService` bridge pattern from Plan 58-02.
- **Files modified:** `MatchdayGeneratorServiceTest.java`, `SwissPairingServiceTest.java`, `SwissPairingService.java`
- **Commits:** `f557678`

**2. [Rule 1 - Bug] New Swiss group-isolation tests failed: missing dummy results before round 2**
- **Found during:** Task 2 GREEN
- **Issue:** `givenSwissGroupsPhase_whenGenerateNextRoundForGroupA_thenOnlyGroupAAdvances` and `givenGroupsPhase_whenGroupAAtRound2GroupBAtRound1_thenIsCurrentRoundCompleteIsPerGroup` tried to generate round 2 for group A without completing round 1 first → `IllegalState: Current round has incomplete races`.
- **Fix:** Added `addDummyResults(r1A.getId())` (and `r1B.getId()` for the isolation test) between round 1 and round 2 generation.
- **Files modified:** `SwissPairingServiceTest.java`
- **Commits:** `f557678`

**3. [Rule 1 - Bug] UNIQUE(season_id, phase_type) constraint conflict in new phase-aware test helpers**
- **Found during:** Task 2 GREEN (analysis)
- **Issue:** Phase-aware test helpers (`buildLeaguePhase`, `buildGroupsPhase`, `buildSwissLeaguePhase`, `buildSwissGroupsPhase`) created `PhaseType.REGULAR` phases on the same `season` used by `setUp()` which already had a REGULAR phase — DB UNIQUE constraint would fire.
- **Fix:** Test helpers create a fresh season via `buildTestSeason()` (year=9999, number=99, name with `Phase58-Test-` prefix) to avoid constraint conflict. The `givenSeasonId_whenGenerate_thenDelegatesToRegularPhase` test was updated to use the `regularPhase` from `setUp()` directly.
- **Files modified:** `MatchdayGeneratorServiceTest.java`, `SwissPairingServiceTest.java`
- **Commits:** `f557678`

**4. [Rule 1 - Bug] givenSwissSeason_whenGenerate_thenThrowsException failed**
- **Found during:** Task 2 GREEN
- **Issue:** Test set `season.setFormat(SWISS)` but the canonical path checks `phase.getFormat()`, not `season.getFormat()`. The REGULAR phase created in `setUp()` had `format=LEAGUE`.
- **Fix:** Test also sets `regularPhase.setFormat(SeasonFormat.SWISS)` and saves.
- **Files modified:** `MatchdayGeneratorServiceTest.java`
- **Commits:** `f557678`

## Pitfall 6 Confirmation

Pitfall 6 (SwissPairingService injects StandingsService) was avoided by the wave ordering:
- Plan 58-02 shipped `calculateStandings(UUID phaseId, UUID groupId)` in Wave 2a
- Plan 58-04 (this plan) ships in Wave 3, safely calling the phase-aware signature

grep confirms: `standingsService.calculateStandings(phaseId, groupId)` appears exactly once in `SwissPairingService.java` (line in `generateSubsequentRoundPairings`).

## Known Stubs

None — all new methods are fully wired. The `GeneratorFormData.phase` field may be null for very old code paths (pre-Phase-57 `getFormData` when no REGULAR phase exists) but this does not affect any current UI rendering since the templates do not yet reference `formData.phase` (Phase 60 will add that).

## Self-Check: PASSED

| Check | Result |
|---|---|
| MatchdayGeneratorService.java exists | FOUND |
| SwissPairingService.java exists | FOUND |
| MatchdayRepository.java exists | FOUND |
| MatchdayGeneratorServiceTest.java exists | FOUND |
| SwissPairingServiceTest.java exists | FOUND |
| 58-04-SUMMARY.md exists | FOUND |
| RED commit 7dc4773 exists | FOUND |
| GREEN commit f557678 exists | FOUND |
| 1108 tests pass | CONFIRMED |
| JaCoCo line coverage 84% ≥ 82% | CONFIRMED |
