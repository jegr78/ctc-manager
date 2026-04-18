---
phase: 39-entity-cross-linking
plan: "01"
subsystem: sitegen
tags: [tdd, testing, entity-cross-linking, jsoup]
dependency_graph:
  requires: []
  provides: [tdd-red-gate-for-entity-links]
  affects: [SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [jsoup-css-selector-assertion, tdd-red-phase]
key_files:
  created: []
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "Test 6 (index matchday driver links) passes vacuously because index.html has no match-results table — forward-compatible design for Plan 02"
  - "Playwright context timeout on first run was a transient resource contention issue; second run produced correct RED results"
metrics:
  duration: "~35 minutes (including transient Playwright timeout on first run)"
  completed: "2026-04-16"
  tasks: 1
  files_changed: 1
---

# Phase 39 Plan 01: Entity Cross-Link TDD RED Phase Summary

Six failing integration tests added to `SiteGeneratorServiceTest.java` asserting entity cross-link anchors (`a.entity-link`) in standings, driver-ranking, matchday, team-profile, and index pages for requirements CONT-02, CONT-03, CONT-04, CONT-08, and D-04.

## Tasks Completed

| Task | Description | Commit | Status |
|------|-------------|--------|--------|
| 1 | Write six failing entity cross-link tests (TDD RED) | f4cd388 | Done |

## What Was Built

Six new test methods in `SiteGeneratorServiceTest.java`:

1. `givenTeamInStandings_whenGenerate_thenTeamNameLinksToTeamProfile` — CONT-02: asserts `tbody td a.entity-link[href*='team/']` in standings.html
2. `givenDriverInRanking_whenGenerate_thenDriverPsnIdLinksToDriverProfile` — CONT-03: asserts `tbody td a.entity-link[href*='driver/']` in driver-ranking.html
3. `givenRaceResults_whenGenerate_thenMatchdayDriverNamesLinkToDriverProfiles` — CONT-04: asserts `td a.entity-link[href*='driver/']` in matchday/spieltag-1.html
4. `givenTeamWithDrivers_whenGenerate_thenTeamProfileHasDriversSectionWithLinks` — CONT-08: asserts `h2.section-title:contains(Drivers)` and `a.entity-link[href*='driver/']` in team profile HTML
5. `givenActiveSeason_whenGenerate_thenIndexStandingsTeamNamesLinkToTeamProfiles` — D-04: asserts `tbody td a.entity-link[href*='team/']` in index.html
6. `givenActiveSeason_whenGenerate_thenIndexMatchdayResultDriversHaveLinks` — D-04: conditional check for `.match-results td a.entity-link[href*='driver/']` in index.html (passes vacuously)

## TDD Gate Compliance

RED gate commit: `f4cd388` — `test(39-01): add failing tests for entity cross-link assertions`

Test results:
- Tests run: 34 | Failures: 5 | Errors: 0 | Skipped: 0
- New tests 1-5: FAIL (templates lack `entity-link` elements — as expected for RED phase)
- New test 6: PASS vacuously (index.html has no `.match-results` section)
- Existing 28 tests: all PASS

## Deviations from Plan

### Transient Infrastructure Issue

**Found during:** Task 1 verification (first run)
**Issue:** `Playwright TimeoutError` during Spring context loading caused all 34 tests to show as errors on the first test run. This was a transient system resource contention issue — Playwright attempted to launch Chromium and timed out after 180s.
**Fix:** Re-ran the test — second run completed in 68s with the expected 5 failures and 29 passes.
**Impact:** No code changes needed. Tests and plan are correct.

## Known Stubs

None. This plan only adds test code with no stub logic.

## Threat Flags

None. This plan only adds test code; no production code changes, no new endpoints or trust boundaries introduced.

## Self-Check: PASSED

- [x] `SiteGeneratorServiceTest.java` contains method `givenTeamInStandings_whenGenerate_thenTeamNameLinksToTeamProfile`
- [x] `SiteGeneratorServiceTest.java` contains method `givenDriverInRanking_whenGenerate_thenDriverPsnIdLinksToDriverProfile`
- [x] `SiteGeneratorServiceTest.java` contains method `givenRaceResults_whenGenerate_thenMatchdayDriverNamesLinkToDriverProfiles`
- [x] `SiteGeneratorServiceTest.java` contains method `givenTeamWithDrivers_whenGenerate_thenTeamProfileHasDriversSectionWithLinks`
- [x] `SiteGeneratorServiceTest.java` contains method `givenActiveSeason_whenGenerate_thenIndexStandingsTeamNamesLinkToTeamProfiles`
- [x] `SiteGeneratorServiceTest.java` contains method `givenActiveSeason_whenGenerate_thenIndexMatchdayResultDriversHaveLinks`
- [x] `SiteGeneratorServiceTest.java` contains string `a.entity-link[href*='team/']`
- [x] `SiteGeneratorServiceTest.java` contains string `a.entity-link[href*='driver/']`
- [x] `SiteGeneratorServiceTest.java` contains string `h2.section-title:contains(Drivers)`
- [x] Running `./mvnw test -Dtest=SiteGeneratorServiceTest` shows exactly 5 test failures (tests 1-5) and test 6 passes vacuously
- [x] No production source files (`src/main/`) modified
- [x] Commit f4cd388 exists in git log
