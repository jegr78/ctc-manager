---
phase: 23-dev-seasons-with-results
plan: 01
subsystem: admin/test-data
tags: [seed-data, season-format, team-structure, dependency-injection, tdd]
dependency_graph:
  requires: []
  provides: [season-formats, team-restructuring, scoring-service-injection, season-drivers-s1-s2]
  affects: [TestDataService, TestDataServiceIntegrationTest]
tech_stack:
  added: []
  patterns: [tdd-red-green, constructor-injection, helper-method-extraction]
key_files:
  created:
    - src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java
  modified:
    - src/main/java/org/ctc/admin/TestDataService.java
decisions:
  - Mapped plan team names (ADR/ICL/SVT/etc) to actual codebase teams (P1R/CLR/TCR/etc) since plan was written with fictional abbreviations
  - Used CLR/TNR/AHR as the 3 parents with subs excluded from S4 match teams (matching actual sub-team structure)
  - SeasonDriver assignments use parent team for sub-team drivers (per RaceLineup Source of Truth pattern)
metrics:
  duration: "3m 42s"
  completed: "2026-04-09T21:11:54Z"
  tasks_completed: 2
  tasks_total: 2
  tests_added: 8
  tests_total: 860
---

# Phase 23 Plan 01: Season Structure Prerequisites Summary

Restructured season entities for correct format assignment, team membership, and dependency injection in TestDataService using TDD.

## One-liner

Season format assignment (ROUND_ROBIN/SWISS/LEAGUE), team restructuring (6/6/14 per season), ScoringService+RaceResultRepository injection, and SeasonDriver records for S1/S2.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 2ba6f03 | TDD RED: 8 failing integration tests for season structure |
| 2 | 7c4d1a2 | TDD GREEN: restructure seasons, inject dependencies, all tests pass |

## Task Details

### Task 1: Integration tests for season structure (TDD RED)

Created `TestDataServiceIntegrationTest.java` with 8 tests verifying:
- S1 Group A/B format = ROUND_ROBIN
- S2 format = SWISS
- S4 format = LEAGUE
- S1 Group A/B each have 6 teams
- S4 has 14 match teams (no CLR/TNR/AHR parents)
- S1 groups contain sub-teams

Added `@Transactional` to avoid LazyInitializationException on seasonTeams collection.

### Task 2: Restructure seasons and inject dependencies (TDD GREEN)

Modified `TestDataService.java`:
- Injected `ScoringService` and `RaceResultRepository` via `@RequiredArgsConstructor`
- S1 2023 Group A: 6 teams (P1R, TCR, ART, MRL, GXR + CLR 1 sub-team), format ROUND_ROBIN
- S1 2023 Group B: 6 teams (DTR, VEZ + CLR 2, TNR A, TNR B, AHR 1 sub-teams), format ROUND_ROBIN
- S2 2024: 10 parent teams, format SWISS
- S4 2026: 14 match teams (removed CLR/TNR/AHR parents, kept 7 sub-teams + 7 standalone parents), format LEAGUE
- Added SeasonDriver records for S1 Group A (36 drivers), S1 Group B (36 drivers), S2 (60 drivers)
- Extracted `assignSeasonDrivers()` helper method

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added @Transactional to integration test class**
- **Found during:** Task 1
- **Issue:** LazyInitializationException when accessing `season.getSeasonTeams()` outside transaction
- **Fix:** Added `@Transactional` annotation to test class (standard Spring test pattern)
- **Files modified:** TestDataServiceIntegrationTest.java

**2. [Rule 3 - Blocking] Mapped fictional team abbreviations to actual codebase teams**
- **Found during:** Task 2
- **Issue:** Plan used team names (ADR, ICL, SVT, NFR, HMS, EGP, PWR, VRX, SGM, TBR) that do not exist in the codebase. Actual teams are P1R, CLR, TCR, ART, AHR, MRL, GXR, DTR, VEZ, TNR.
- **Fix:** Mapped intent to actual teams: VRX->CLR, SGM->TNR, TBR->AHR (parents with subs), ADR->P1R, ICL->TCR, SVT->ART, NFR->MRL, HMS->GXR, EGP->DTR, PWR->VEZ
- **Files modified:** TestDataService.java

## Known Stubs

None - all data is fully wired and functional.

## Verification

```
./mvnw verify: BUILD SUCCESS
Tests run: 860, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
```
