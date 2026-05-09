---
phase: 52-alltime-team-standings-and-driver-ranking-pages-for-static-s
plan: "01"
subsystem: testing
tags: [java, junit5, tdd, sitegen, static-site]

# Dependency graph
requires: []
provides:
  - "TDD RED tests for alltime-standings.html and alltime-driver-ranking.html pages"
  - "Updated nav test asserting alltime page links instead of season-specific links"
affects:
  - "52-02 (GREEN phase implementing alltime pages in SiteGeneratorService)"
  - "52-03+ (nav template changes must make whenGenerate_thenNavLinksToAlltimePages pass)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "TDD RED: failing tests written before any implementation"
    - "Test method naming: whenAction_thenResult() without Given prefix (no explicit precondition)"

key-files:
  created: []
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java

key-decisions:
  - "Replaced givenActiveSeason_whenGenerate_thenNavDriverRankingLinksToActiveSeason with whenGenerate_thenNavLinksToAlltimePages — nav contract changes from season-specific to alltime pages"
  - "Worktree-aware build: Maven commands run in worktree directory, not main repo root"

patterns-established:
  - "Alltime pages are root-level: tempDir.resolve('alltime-standings.html'), not under season/"
  - "Nav alltime assertion uses CSS selector: .nav-links a[href*='alltime-standings.html']"

requirements-completed:
  - ALLTIME-01
  - ALLTIME-02
  - ALLTIME-03
  - ALLTIME-04
  - ALLTIME-05

# Metrics
duration: 25min
completed: 2026-04-18
---

# Phase 52 Plan 01: TDD RED Tests for Alltime Standings and Driver Ranking Pages

**Three failing tests establish the contract for alltime-standings.html, alltime-driver-ranking.html, and a nav change from season-specific to alltime links**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-18T07:45:00Z
- **Completed:** 2026-04-18T08:05:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Added `whenGenerate_thenAlltimeStandingsPageExists`: asserts `alltime-standings.html` exists at output root, has `thead th` headers, `tbody tr` rows, and team shortName from test data
- Added `whenGenerate_thenAlltimeDriverRankingPageExists`: asserts `alltime-driver-ranking.html` exists at output root, has `tbody tr` rows, and driver PSN ID from test data
- Replaced `givenActiveSeason_whenGenerate_thenNavDriverRankingLinksToActiveSeason` with `whenGenerate_thenNavLinksToAlltimePages`: asserts `.nav-links` contains hrefs to both alltime pages
- All 3 target tests FAIL (RED) — `generate()` does not yet produce alltime pages, layout.html still uses season-specific nav links
- All 69 other tests still pass (no regressions)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add 2 failing tests for alltime pages + update nav test** - `042f703` (test)

## Files Created/Modified

- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — +45 lines/-5 lines: two new test methods added at end, one test method renamed and rewritten

## Decisions Made

- Replaced the old `givenActiveSeason_whenGenerate_thenNavDriverRankingLinksToActiveSeason` test with `whenGenerate_thenNavLinksToAlltimePages` per plan spec — this changes the navigation contract from season-specific to global alltime pages
- New tests placed at the end of the file for minimal diff/conflict surface
- Ran all Maven commands inside the worktree directory (not main repo) — critical because the worktree has its own `target/` and class files

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Discovered worktree/main-repo build isolation issue**
- **Found during:** Task 1 (test execution)
- **Issue:** Initial Maven runs used `/Users/jegr/Documents/github/ctc-manager/` as cwd instead of the worktree. This caused old `.class` files from the main repo to be loaded, making all tests appear to pass even before the new code was compiled. The surefire report still showed the old method name `givenActiveSeason_whenGenerate_thenNavDriverRankingLinksToActiveSeason`.
- **Fix:** Ran all Maven commands with cwd set to the worktree directory. The worktree has its own `target/` so compilation correctly picked up the changed source.
- **Files modified:** None (build config issue, no code change needed)
- **Verification:** After re-running in worktree: `Tests run: 72, Failures: 3` — exactly the 3 target tests fail as expected
- **Committed in:** 042f703 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 3 - blocking)
**Impact on plan:** No scope change. Build path resolution issue resolved by running Maven in the correct directory.

## Issues Encountered

- Maven Surefire method-level test selection (`-Dtest=ClassName#methodName`) returned 0 tests found. This is likely a JUnit Platform / Surefire version interaction. Workaround: ran the full class and inspected the surefire XML report to identify which specific methods passed or failed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- TDD RED state confirmed: 3 tests fail, 69 pass — implementation phase can begin
- Plan 52-02 (GREEN) must implement `generateAlltimeStandings()` and `generateAlltimeDriverRanking()` in `SiteGeneratorService`
- Plan 52-0x (nav) must update `layout.html` to replace season-specific standings/driver-ranking nav links with `alltime-standings.html` and `alltime-driver-ranking.html`

## Self-Check: PASSED

- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — modified (verified grep matches)
- Commit `042f703` — exists (verified via git log)
- 3 new/updated test methods present, old method name absent
- RED state: 3 failures, 69 passes confirmed in surefire output

---
*Phase: 52-alltime-team-standings-and-driver-ranking-pages-for-static-s*
*Completed: 2026-04-18*
