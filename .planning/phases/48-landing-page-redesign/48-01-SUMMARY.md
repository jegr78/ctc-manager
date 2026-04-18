---
phase: 48-landing-page-redesign
plan: 01
subsystem: sitegen-tests
tags: [tdd, red-phase, landing-page, youtube-scraper]
dependency_graph:
  requires: []
  provides: [YouTubeScraperServiceTest, landing-page-test-contract]
  affects: [SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [spy-pattern-for-jsoup, jsoup-html-assertion]
key_files:
  created:
    - src/test/java/org/ctc/sitegen/YouTubeScraperServiceTest.java
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "Adapted hero test to assert .hero h1 contains COMMUNITY TEAM CUP instead of .hero-label year (D-09)"
  - "Used comment markers for removed tests to document Phase 48 rationale"
metrics:
  duration: 5m 19s
  completed: 2026-04-17
  tasks_completed: 2
  tasks_total: 2
  files_changed: 2
---

# Phase 48 Plan 01: Landing Page TDD RED Tests Summary

YouTubeScraperServiceTest created with 4 unit tests using @Spy + doReturn/doThrow pattern on fetchChannelHtml(); SiteGeneratorServiceTest adapted with -3 removed, +6 new, +1 adapted tests covering LAND-01 through LAND-05 and D-19

## Task Results

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create YouTubeScraperServiceTest with 4 failing unit tests | 60a3431 | YouTubeScraperServiceTest.java (new) |
| 2 | Adapt SiteGeneratorServiceTest: -3 old, +6 new, 1 adapted | c28080f | SiteGeneratorServiceTest.java (modified) |

## Changes Made

### Task 1: YouTubeScraperServiceTest (NEW)

Created `src/test/java/org/ctc/sitegen/YouTubeScraperServiceTest.java` with 4 test methods:

1. `givenValidChannelPage_whenScrapeVideoId_thenReturnsFirstVideoId` -- stubs fetchChannelHtml with HTML containing videoId JSON, asserts correct extraction
2. `givenNoVideoIdInPage_whenScrapeVideoId_thenReturnsFallback` -- stubs with HTML containing no videoId, asserts fallback returned
3. `givenIOException_whenScrapeVideoId_thenReturnsFallback` -- throws IOException on fetchChannelHtml, asserts fallback returned
4. `givenMultipleVideoIds_whenScrapeVideoId_thenReturnsFirstMatch` -- stubs with multiple videoId values, asserts first match returned

**RED state:** Compilation fails with `cannot find symbol: class YouTubeScraperService` (class does not exist yet).

### Task 2: SiteGeneratorServiceTest (MODIFIED)

**Removed 3 tests:**
- `givenActiveSeason_whenGenerate_thenIndexStandingsTeamNamesLinkToTeamProfiles` -- standings table removed from index (D-14)
- `givenActiveSeason_whenGenerate_thenIndexDoesNotRenderMatchResults` -- replaced by more specific no-match-grid test (D-15)
- `givenIndexPage_whenGenerate_thenStandingsTopNavActive` -- currentPage changed to "home", index no longer highlights nav (D-19)

**Adapted 1 test:**
- `givenSeason_whenGenerate_thenHeroLabelContainsYear` renamed to `givenSeason_whenGenerate_thenHeroContainsCommunityTeamCupTitle` -- asserts `.hero h1` contains "COMMUNITY TEAM CUP" instead of `.hero-label` year (D-09). This test PASSES because the existing hero already has the h1.

**Added 6 new tests:**
- `givenActiveSeason_whenGenerate_thenIndexHasYouTubeIframe` (LAND-01) -- FAILS: no iframe in current index
- `givenActiveSeason_whenGenerate_thenIndexHasFiveTiles` (LAND-02) -- FAILS: no .tile-card elements
- `whenGenerate_thenIndexHasNoStandingsTable` (LAND-03a) -- FAILS: table still exists
- `whenGenerate_thenIndexHasNoMatchGrid` (LAND-03b) -- FAILS: .match-grid still exists
- `givenActiveSeason_whenGenerate_thenStandingsTileLinkCorrect` (LAND-04) -- FAILS: no tile-card with standings link
- `givenIndexPage_whenGenerate_thenNoTopNavItemActive` (D-19) -- FAILS: "Standings" still active

**RED state confirmed:** 6/6 new tests fail. 63/63 existing tests pass (no regressions).

## TDD Gate Compliance

- RED gate: `test(48-01)` commits at 60a3431 and c28080f -- all new tests fail for the right reasons
- GREEN gate: pending (Plan 48-02 implementation)
- REFACTOR gate: pending

## Deviations from Plan

None -- plan executed exactly as written.

## Decisions Made

1. Used comment markers in place of removed tests to document why they were removed and which Phase 48 decision caused the removal
2. The adapted hero test passes on the current codebase (the existing hero h1 already contains "COMMUNITY TEAM CUP") -- this is acceptable since it validates the contract for Phase 48 implementation

## Verification Results

- YouTubeScraperServiceTest: compilation failure (expected -- class does not exist)
- SiteGeneratorServiceTest (6 new tests): 6/6 FAIL (expected -- index.html has old content)
- SiteGeneratorServiceTest (63 existing tests): 63/63 PASS (no regressions)

## Self-Check: PASSED

- [x] `src/test/java/org/ctc/sitegen/YouTubeScraperServiceTest.java` exists
- [x] `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` exists
- [x] Commit 60a3431 exists
- [x] Commit c28080f exists
