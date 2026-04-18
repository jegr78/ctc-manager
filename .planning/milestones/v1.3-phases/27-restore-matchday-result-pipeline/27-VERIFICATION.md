---
phase: 27-restore-matchday-result-pipeline
verified: 2026-04-10T22:20:00Z
status: passed
score: 7/7 must-haves verified
re_verification: false
---

# Phase 27: Restore Matchday/Result Pipeline — Verification Report

**Phase Goal:** Adapt TestDataService to use fictive teams and restore seedMatchdaysAndResults() pipeline for all three season formats
**Verified:** 2026-04-10T22:20:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                  | Status     | Evidence                                                                                                |
|----|----------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------|
| 1  | Dev seed creates League season S4 2026 with 5 matchdays, 7 matches per matchday, and race results | ✓ VERIFIED | `seedLeagueSeason()` iterates 5 matchdays × 7 matches (14 teams, homeIdx 0-6, awayIdx 7+rotation). Integration test `thenLeagueSeasonHasFiveMatchdays` and `thenLeagueRacesHaveResults` pass. |
| 2  | Dev seed creates Swiss season S2 2024 with 5 matchdays, 5 matches per matchday, 2 races per match | ✓ VERIFIED | `seedSwissSeason()` iterates 5 matchdays × 5 matches × 2 races. Integration test `thenSwissSeasonHasFiveMatchdays` passes. |
| 3  | Dev seed creates Round Robin S1 2023 Group A and Group B with 3 matchdays each         | ✓ VERIFIED | `seedRoundRobinSeason()` called for both s1a and s1b with 3 matchdays × 3 matches. Tests `thenRoundRobinGroupAHasThreeMatchdays` and `thenRoundRobinGroupBHasThreeMatchdays` pass. |
| 4  | All race results have non-zero points calculated by ScoringService                      | ✓ VERIFIED | `scoringService.calculatePoints(results, raceScoring)` called in `seedRace()`. Integration test `thenAllRaceResultsHaveNonZeroPoints` asserts `pointsTotal > 0` for all dev season results. |
| 5  | All matches have non-null home/away scores from aggregateMatchScores()                 | ✓ VERIFIED | JPA flush+detach+reload pattern in `seedRace()` calls `scoringService.aggregateMatchScores(reloadedRace)`. Test `thenAllMatchesHaveNonNullScores` asserts non-null for both scores. |
| 6  | All team/driver references use fictive names (VRX, SGM, ADR, etc.), no real CTC teams | ✓ VERIFIED | No P1R, CLR, TNR, AHR, France-k88 found in TestDataService. 10 fictive parent teams (VRX/SGM/ADR/TBR/ICL/SVT/NFR/EGP/HMS/PWR) and 100 fictive drivers (_Driver01-10 pattern) confirmed. |
| 7  | TestDataService has @Profile("dev") annotation                                          | ✓ VERIFIED | Line 47: `@Profile("dev")` present on class. Threat T-27-01 mitigated. |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact                                                              | Expected                                                          | Status     | Details                                                                                  |
|-----------------------------------------------------------------------|-------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------|
| `src/main/java/org/ctc/admin/TestDataService.java`                   | Complete seed pipeline with fictive teams and matchday/result seeding | ✓ VERIFIED | 746 lines. Contains `seedMatchdaysAndResults()` (line 393), `seedLeagueSeason()` (536), `seedSwissSeason()` (557), `seedRoundRobinSeason()` (583), `seedRace()` (610). All fictive team names. |
| `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java`    | Integration tests verifying seed data with fictive team assertions | ✓ VERIFIED | 15 test methods. `doesNotContain("VRX", "SGM", "TBR")` at line 126. Matchday/result/scoring tests present. All 867 tests pass. |

### Key Link Verification

| From                                    | To                              | Via                                       | Status     | Details                                          |
|-----------------------------------------|---------------------------------|-------------------------------------------|------------|--------------------------------------------------|
| `TestDataService.seedMatchdaysAndResults()` | `ScoringService.calculatePoints()` | `scoringService.calculatePoints(results, raceScoring)` | ✓ WIRED | Line 637 — called once per race in seedRace() |
| `TestDataService.seedRace()`            | `ScoringService.aggregateMatchScores()` | flush+detach+reload pattern            | ✓ WIRED | Lines 639-645 — entityManager.detach, reload, aggregateMatchScores, matchRepository.save |
| `TestDataService.seedTeams()`           | `TestDataService.seedMatchdaysAndResults()` | findParent/findSub lambdas using fictive shortNames | ✓ WIRED | 18 occurrences of `findParent.apply("ADR"/"ICL"/"SVT"/etc.)` and `findSub.apply("VRX A"/"VRX B"/etc.)` |

### Data-Flow Trace (Level 4)

Not applicable — TestDataService is a seed pipeline writing to DB, not a component rendering dynamic data.

### Behavioral Spot-Checks

| Behavior                              | Command                                            | Result                             | Status  |
|---------------------------------------|----------------------------------------------------|------------------------------------|---------|
| All 867 tests pass, 0 failures        | `./mvnw verify`                                    | BUILD SUCCESS, 867 tests, 0 failures, 0 errors | ✓ PASS |
| Coverage checks met                   | JaCoCo check phase                                 | "All coverage checks have been met." | ✓ PASS |
| @Profile("dev") present               | grep '@Profile("dev")' TestDataService.java        | Match at line 47                   | ✓ PASS |
| No real team references               | grep P1R/CLR/TNR/AHR/France-k88 TestDataService.java | 0 matches                        | ✓ PASS |
| doesNotContain fictive parents in S4  | grep in TestDataServiceIntegrationTest.java        | Line 126 confirmed                 | ✓ PASS |
| Code review fix (% 3 in RoundRobin)   | grep "% 3\|% 5" TestDataService.java               | seedRoundRobin uses % 3 (line 602), seedSwiss uses % 5 (line 575) | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description                                                         | Status      | Evidence                                                                 |
|-------------|-------------|---------------------------------------------------------------------|-------------|--------------------------------------------------------------------------|
| DATA-04     | 27-01-PLAN  | Dev profile creates League format season with matchdays, races, and results | ✓ SATISFIED | S4 2026 uses `SeasonFormat.LEAGUE`, `seedLeagueSeason()` creates 5×7×1=35 races. Test `thenLeagueSeasonHasFiveMatchdays` + `thenLeagueRacesHaveResults` pass. |
| DATA-05     | 27-01-PLAN  | Dev profile creates Swiss format season with matchdays, races, and results | ✓ SATISFIED | S2 2024 uses `SeasonFormat.SWISS`, `seedSwissSeason()` creates 5×5×2=50 races. Test `thenSwissSeasonHasFiveMatchdays` passes. |
| DATA-06     | 27-01-PLAN  | Dev profile creates Round Robin format season (2 groups) with matchdays, races, and results | ✓ SATISFIED | S1 2023 Group A + Group B use `SeasonFormat.ROUND_ROBIN`, each with 3×3×2=18 races. Tests `thenRoundRobinGroupAHasThreeMatchdays` and `thenRoundRobinGroupBHasThreeMatchdays` pass. |
| DATA-07     | 27-01-PLAN  | Race results use actual existing scoring system for point calculation | ✓ SATISFIED | `ScoringService.calculatePoints()` + `aggregateMatchScores()` called for every race. Test `thenAllRaceResultsHaveNonZeroPoints` (pointsTotal > 0) and `thenAllMatchesHaveNonNullScores` pass. |

**Note:** DATA-04 through DATA-07 are phase-internal requirements, not entries in a central requirements registry (confirmed in Phase 23 verification). All four are fully satisfied.

### Anti-Patterns Found

None. No TODO/FIXME/PLACEHOLDER comments. No stub implementations. No real team references. No S3 2025 references in tests.

### Human Verification Required

None. All must-haves are programmatically verifiable and confirmed by the test suite.

### Gaps Summary

No gaps. All 7 observable truths verified, all artifacts substantive and wired, all key links confirmed, all 4 requirements satisfied. Full test suite passes with BUILD SUCCESS (867 tests, 0 failures). Code review fix for round-robin modulus (`% 3` instead of `% 5`) is committed and tests confirm correctness.

---

_Verified: 2026-04-10T22:20:00Z_
_Verifier: Claude (gsd-verifier)_
