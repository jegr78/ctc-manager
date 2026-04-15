---
phase: 23-dev-seasons-with-results
verified: 2026-04-09T23:55:00Z
status: human_needed
score: 14/15 must-haves verified
re_verification: false
human_verification:
  - test: "Run full test suite on gsd/v1.3-english-test-data branch"
    expected: "867 tests pass, coverage >= 82%"
    why_human: "Working directory is checked out to gsd/v1.0-milestone. Cannot run mvnw verify against the correct branch without switching checkouts. The SUMMARY claims BUILD SUCCESS / 867 tests / coverage met, but this cannot be confirmed programmatically from a different branch checkout."
---

# Phase 23: dev-seasons-with-results Verification Report

**Phase Goal:** Seed dev profile with fully played-out seasons (League, Swiss, Round Robin) including matchdays, races, results, and scored standings.
**Verified:** 2026-04-09T23:55:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Context Note

The Phase 23 implementation lives on branch `gsd/v1.3-english-test-data`. The local working directory is currently checked out to `gsd/v1.0-milestone`, so the source files visible in the filesystem reflect the pre-Phase-23 state. All artifact verification below was performed against the correct branch via `git show gsd/v1.3-english-test-data:...`.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | S1 2023 Group A and Group B seasons have format ROUND_ROBIN | VERIFIED | `s1a.setFormat(SeasonFormat.ROUND_ROBIN)` and `s1b.setFormat(SeasonFormat.ROUND_ROBIN)` in TestDataService.java lines 176/184 on branch |
| 2 | S2 2024 season has format SWISS | VERIFIED | `s2.setFormat(SeasonFormat.SWISS)` at line 192 |
| 3 | S4 2026 season has format LEAGUE | VERIFIED | `s4.setFormat(SeasonFormat.LEAGUE)` at line 222 |
| 4 | S1 Group A has 6 teams (mix of parents and sub-teams) | VERIFIED | `List.of(P1R, TCR, ART, MRL, GXR, CLR 1).forEach(s1a::addTeam)` — 5 parents + 1 sub-team. Integration test `givenDevSeed_whenStarted_thenS1GroupAHasSixTeams` asserts `hasSize(6)`. UAT confirms: "Teams (6) — P1R, TCR, ART, MRL, GXR, CLR 1 (Sub)" |
| 5 | S1 Group B has 6 teams (mix of parents and sub-teams) | VERIFIED | `List.of(DTR, VEZ, CLR 2, TNR A, TNR B, AHR 1).forEach(s1b::addTeam)` — 2 parents + 4 sub-teams. Integration test asserts `hasSize(6)`. UAT confirms |
| 6 | S4 2026 has exactly 14 match teams (no CLR/TNR/AHR parents) | VERIFIED | 7 sub-teams (CLR 1, CLR 2, TNR A, TNR B, TNR C, AHR 1, AHR 2) + 7 standalone parents (P1R, DTR, MRL, ART, VEZ, GXR, TCR). Integration test asserts `hasSize(14)` and `doesNotContain("CLR", "TNR", "AHR")`. UAT confirms |
| 7 | ScoringService and RaceResultRepository are injected into TestDataService | VERIFIED | `private final RaceResultRepository raceResultRepository;` (line 62) and `private final ScoringService scoringService;` (line 63). Both injected via `@RequiredArgsConstructor` |
| 8 | League season (S4 2026) has 5 matchdays each with 7 matches and 1 race per match | VERIFIED | `seedLeagueSeason()` loops `mdIndex = 0..4` (5 matchdays), inner `matchIdx = 0..6` (7 matches), single `seedRace()` call per match. Integration test asserts `isEqualTo(5)`. UAT confirms 5 matchdays with 7 matches each |
| 9 | Swiss season (S2 2024) has 5 matchdays each with 5 matches and 2 races per match | VERIFIED | `seedSwissSeason()` loops `mdIndex = 0..4` (5 matchdays), inner `matchIdx = 0..4` (5 matches), two `seedRace()` calls per match. Integration test asserts `isEqualTo(5)`. UAT confirms 5 matchdays, 10 races each (5 matches x 2 races) |
| 10 | Round Robin seasons (S1 2023 Group A + B) have 3 matchdays each with 3 matches and 2 races per match | VERIFIED | `seedRoundRobinSeason()` loops `mdIndex = 0..2` (3 matchdays), inner `matchIdx = 0..2` (3 matches), two `seedRace()` calls per match. Integration tests assert `isEqualTo(3)` for each group. UAT confirms Groups A/B each have 3 matchdays with 6 races each |
| 11 | Every race has exactly 12 RaceResult entries with positions 1-12 | VERIFIED | `seedRace()` saves 6 home drivers + 6 away drivers = 12 results per race. Integration test `givenDevSeed_whenStarted_thenLeagueRacesHaveResults` asserts `isEqualTo(12)` per race. UAT confirms "12 Results link" per race |
| 12 | All RaceResults have non-zero pointsTotal (scored by ScoringService) | VERIFIED | `scoringService.calculatePoints(results, raceScoring)` called in `seedRace()` after `raceResultRepository.saveAll(results)`, followed by second save. Integration test `givenDevSeed_whenStarted_thenAllRaceResultsHaveNonZeroPoints` asserts `allMatch(r -> r.getPointsTotal() > 0)` |
| 13 | All Matches have non-null homeScore and awayScore (aggregated by ScoringService) | VERIFIED | JPA flush+detach pattern: `raceResultRepository.flush()`, `entityManager.detach(race)`, reload via `raceRepository.findById()`, then `scoringService.aggregateMatchScores(reloadedRace)`, then `matchRepository.save()`. Integration test asserts `allMatch(m -> m.getHomeScore() != null && m.getAwayScore() != null)`. UAT confirms scores like "65:51" |
| 14 | Standings pages display non-zero points for all three season formats | VERIFIED | UAT evidence: League S4 — 14 teams, top 7 with 9pts; Swiss S2 — 10 teams, scores 9.0; Round Robin S1 Group A — 6 teams, top 3 with 6pts. All confirmed via playwright screenshots |
| 15 | 867 tests pass, coverage >= 82% | UNCERTAIN | SUMMARY claims "Tests run: 867, Failures: 0, All coverage checks met." Cannot verify programmatically — working directory is on a different branch. Requires human to run `./mvnw verify` on `gsd/v1.3-english-test-data` |

**Score:** 14/15 truths verified (1 requires human confirmation)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/admin/TestDataService.java` | Season format assignment, team restructuring, dependency injection, seedMatchdaysAndResults() | VERIFIED | Exists on branch. 948 lines. Contains `ScoringService`, `RaceResultRepository`, `SeasonFormat`, `setFormat()` calls, `seedMatchdaysAndResults()`, `seedLeagueSeason()`, `seedSwissSeason()`, `seedRoundRobinSeason()`, `seedRace()` |
| `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` | Integration tests for season formats, team counts, matchdays, results, scoring | VERIFIED | Exists on branch (not in working tree checkout). 15 `@Test` methods. Contains `ROUND_ROBIN`, `SWISS`, `LEAGUE`, matchday count assertions, non-zero points assertion, match score assertions |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `TestDataService.seedSeasons()` | `SeasonFormat enum` | `season.setFormat()` | VERIFIED | `setFormat(SeasonFormat.ROUND_ROBIN)` called for s1a and s1b; `setFormat(SeasonFormat.SWISS)` for s2; `setFormat(SeasonFormat.LEAGUE)` for s4 |
| `TestDataService` | `ScoringService` | constructor injection | VERIFIED | `private final ScoringService scoringService;` injected via `@RequiredArgsConstructor` |
| `TestDataService` | `RaceResultRepository` | constructor injection | VERIFIED | `private final RaceResultRepository raceResultRepository;` injected via `@RequiredArgsConstructor` |
| `TestDataService.seedMatchdaysAndResults()` | `ScoringService.calculatePoints()` | direct call after saving RaceResult | VERIFIED | `scoringService.calculatePoints(results, raceScoring)` at line 839 in `seedRace()` |
| `TestDataService.seedMatchdaysAndResults()` | `ScoringService.aggregateMatchScores()` | direct call after JPA flush+detach reload | VERIFIED | `scoringService.aggregateMatchScores(reloadedRace)` at line 846 in `seedRace()`, preceded by `entityManager.detach(race)` and `raceRepository.findById()` reload |
| `TestDataService.seed()` | `seedMatchdaysAndResults()` | method call after seedSeasonDrivers() | VERIFIED | `seedMatchdaysAndResults()` called at line 80 in `seed()`, between `seedSeasonDrivers()` and `seedRaceLineups()` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `TestDataService.seedRace()` | `results` (List of RaceResult) | Position arrays (deterministic 1-12), `scoringService.calculatePoints()` | Yes — 12 results per race with positions computed from hard-coded rotation arrays; points computed by production ScoringService using `RaceScoring("CTC Standard", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2)` | FLOWING |
| Standings pages | Match scores (homeScore/awayScore) | `scoringService.aggregateMatchScores()` after JPA detach/reload pattern | Yes — aggregation runs against actual persisted results, sets real non-null scores (UAT evidence: "65:51") | FLOWING |

### Behavioral Spot-Checks

Skipped — working directory is on a different branch (`gsd/v1.0-milestone`). Running tests against the working tree would test the pre-Phase-23 code. Build verification must be done on the correct branch.

### Requirements Coverage

The plan's `requirements` field references DATA-04 through DATA-07. These IDs are **not defined in `.planning/REQUIREMENTS.md`** (which tracks only MERGE requirements for v1.2). DATA-04 through DATA-07 are defined exclusively within the phase's own documents (23-CONTEXT.md, 23-RESEARCH.md). They are phase-internal requirements, not entries in the central requirements registry.

| Requirement | Source | Description | Status | Evidence |
|-------------|--------|-------------|--------|----------|
| DATA-04 | 23-CONTEXT.md / 23-RESEARCH.md | Dev profile creates League format season with matchdays, races, and results | SATISFIED | S4 2026 has LEAGUE format, `seedLeagueSeason()` creates 5 matchdays x 7 matches x 1 race = 35 races, 420 results. Confirmed by integration test + UAT |
| DATA-05 | 23-CONTEXT.md / 23-RESEARCH.md | Dev profile creates Swiss format season with matchdays, races, and results | SATISFIED | S2 2024 has SWISS format, `seedSwissSeason()` creates 5 matchdays x 5 matches x 2 races = 50 races, 600 results. Confirmed by integration test + UAT |
| DATA-06 | 23-CONTEXT.md / 23-RESEARCH.md | Dev profile creates Round Robin format season (2 groups) with matchdays, races, and results | SATISFIED | S1 2023 Group A + Group B each have ROUND_ROBIN format, `seedRoundRobinSeason()` creates 3 matchdays x 3 matches x 2 races = 18 races each. Confirmed by integration test + UAT |
| DATA-07 | 23-CONTEXT.md / 23-RESEARCH.md | Race results use actual existing scoring system for point calculation | SATISFIED | `ScoringService.calculatePoints()` called for every race, `aggregateMatchScores()` sets match scores, integration test `givenDevSeed_whenStarted_thenAllRaceResultsHaveNonZeroPoints` verifies all results have `pointsTotal > 0`. UAT confirms non-zero standings |

Note: DATA-04 through DATA-07 are orphaned from the central REQUIREMENTS.md. This is not a gap in the implementation — the requirements were defined locally for this phase — but the central registry does not track them. No action required for phase goal achievement.

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `TestDataService.java` (working tree) | Working tree is on `gsd/v1.0-milestone` — shows pre-Phase-23 version without `ScoringService`, `seedMatchdaysAndResults()`, or format assignments | Info | Not a code anti-pattern; the correct implementation exists on `gsd/v1.3-english-test-data` |

No stub patterns found in the Phase 23 implementation on the branch. The `seedRace()` method is fully implemented with real position arrays, real ScoringService calls, and real persistence. The `seedMatchdaysAndResults()` method is fully wired into `seed()`.

### Human Verification Required

#### 1. Full Test Suite on gsd/v1.3-english-test-data

**Test:** Switch to branch `gsd/v1.3-english-test-data` and run `./mvnw verify`
**Expected:** BUILD SUCCESS, Tests run: 867, Failures: 0, Errors: 0, Skipped: 0, all coverage checks met (>= 82%)
**Why human:** The working directory is currently checked out to `gsd/v1.0-milestone`. Automated verification of test results requires switching branches, which is outside the scope of a read-only verification run. The SUMMARY documents claim 867 passing tests and coverage met — this needs confirmation from a test run on the actual branch.

### Gaps Summary

No functional gaps found. All phase must-haves are implemented and verified via code inspection on `gsd/v1.3-english-test-data`:

- Season formats are correctly set (ROUND_ROBIN for S1, SWISS for S2, LEAGUE for S4)
- Team counts are correct (6/6 for S1 groups, 14 for S4 with no CLR/TNR/AHR parents)
- `ScoringService` and `RaceResultRepository` are injected
- `seedMatchdaysAndResults()` creates the full race pipeline with correct matchday/match/race counts per format
- `scoringService.calculatePoints()` and `aggregateMatchScores()` are called correctly with JPA flush+detach pattern
- 15 integration tests verify all observable truths
- UAT passed 10/11 tests (1 skipped: format display not in UI scope)

The single open item (truth #15) is a build-verification constraint, not a code deficiency. The code and tests are correctly implemented; only the test execution result needs human confirmation from the correct branch.

---

_Verified: 2026-04-09T23:55:00Z_
_Verifier: Claude (gsd-verifier)_
