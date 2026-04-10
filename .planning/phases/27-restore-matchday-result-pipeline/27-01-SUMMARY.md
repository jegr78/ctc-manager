---
phase: 27-restore-matchday-result-pipeline
plan: 01
subsystem: test-data
tags: [seed-data, fictive-teams, matchday-pipeline, dev-profile]
dependency_graph:
  requires: []
  provides: [fictive-team-seed, matchday-result-pipeline, dev-profile-guard]
  affects: [TestDataService, TestDataServiceIntegrationTest]
tech_stack:
  added: []
  patterns: [fictive-team-naming, driver-id-pattern]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/TestDataService.java
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
decisions:
  - "S4 2026 uses 14 match teams (7 parents + 7 sub-teams), excluding VRX/SGM/TBR parents"
  - "Combined SGM sub-team driver assignments to avoid unique constraint violation"
  - "Removed S3 2025 seasons entirely (P1Rx/P1R sub-teams have no fictive equivalent)"
metrics:
  duration: "~6min"
  completed: "2026-04-10"
  tasks: 2
  files: 2
---

# Phase 27 Plan 01: Adapt TestDataService to Fictive Teams Summary

Complete data-substitution of TestDataService from real CTC teams (P1R, CLR, TCR, etc.) to fictive teams (VRX, SGM, ADR, etc.) with seedMatchdaysAndResults() pipeline intact across League, Swiss, and Round Robin formats.

## What Was Done

### Task 1: Adapt TestDataService to fictive teams with matchday/result pipeline
**Commit:** `9d123a2`

- Replaced 10 real parent teams with 10 fictive teams (VRX, SGM, ADR, TBR, ICL, SVT, NFR, EGP, HMS, PWR)
- Replaced 9 real sub-teams (CLR 1/2, TNR A/B/C, AHR 1/2, P1Rx, P1R) with 7 fictive sub-teams (VRX A/B, SGM B/S, TBR R/B/G)
- Replaced ~130 real PSN IDs with 100 fictive drivers using pattern `{TEAM}_Driver01-10`
- Updated seedSeasons() with fictive team references for S1 2023 (Group A/B), S2 2024, S4 2026
- Removed S3 2025 seasons (P1Rx/P1R sub-teams have no fictive equivalent)
- Updated seedSeasonDrivers() with fictive driver assignments
- Updated seedMatchdaysAndResults() driver maps for all 4 seasons
- Updated seedAliases() with fictive driver examples
- Added `@Profile("dev")` annotation (threat mitigation T-27-01)
- Preserved seedRace() JPA flush/detach/reload pattern verbatim
- Preserved seedRaceLineups() E2E test data (T-ALF/T-BRV) completely untouched
- Preserved seedLeagueSeason(), seedSwissSeason(), seedRoundRobinSeason() algorithms unchanged

### Task 2: Update integration tests for fictive team assertions
**Commit:** `c5fa214`

- Updated S4 14-match-team assertion: `doesNotContain("VRX", "SGM", "TBR")` (was CLR, TNR, AHR)
- All 15 integration tests pass
- Full suite: 867 tests, BUILD SUCCESS, all coverage checks met

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed unique constraint violation in seedSeasonDrivers()**
- **Found during:** Task 1 verification (./mvnw verify)
- **Issue:** S1 Group B had overlapping driver ranges for SGM B (Driver01-06) and SGM S (Driver05-10), causing duplicate entries for SGM_Driver05 and SGM_Driver06 in the same season with the same parent team SGM. The unique constraint on `season_drivers(season_id, driver_id)` rejected these duplicates.
- **Fix:** Combined SGM sub-team driver assignments into a single range (Driver01-10) for the parent team SGM in S1 Group B, avoiding duplicates while still providing all needed drivers for both sub-teams' race lineups.
- **Files modified:** `src/main/java/org/ctc/admin/TestDataService.java`
- **Commit:** `9d123a2` (included in Task 1 commit)

## Verification Results

- `./mvnw verify`: BUILD SUCCESS (867 tests, 0 failures, 0 errors)
- JaCoCo coverage checks: All met (82% minimum maintained)
- `@Profile("dev")` present on TestDataService class
- No real team references remain (P1R, CLR, TNR, AHR, etc. all removed)
- T-ALF/T-BRV test data preserved intact
- seedMatchdaysAndResults() pipeline intact with aggregateMatchScores()

## Self-Check: PASSED
