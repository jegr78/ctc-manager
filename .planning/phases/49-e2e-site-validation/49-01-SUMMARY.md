---
phase: 49-e2e-site-validation
plan: 01
subsystem: testing
tags: [junit5, jsoup, e2e, site-generator, link-crawler, per-class-lifecycle]

# Dependency graph
requires:
  - phase: 44-clean-output-directory
    provides: cleanOutputDirectory for fresh site generation
  - phase: 45-configurable-links
    provides: links.html page with configured link URLs
  - phase: 46-overview-pages
    provides: teams.html and drivers.html with season filter
  - phase: 47-youtube-footer
    provides: YouTube footer link on all page types
  - phase: 48-landing-page-redesign
    provides: index.html with tile cards and YouTube hero
provides:
  - E2E cross-cutting validation test covering all generated static site pages
  - Internal link crawler verifying all href attributes resolve to existing files
  - Structural consistency checks (nav, footer, main-content) across all pages
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@TestInstance(PER_CLASS) with @TempDir parameter injection in @BeforeAll for shared site output"
    - "Bulk link crawler with collected failures pattern (ArrayList + single assertTrue)"
    - "Season isolation via Test_ prefix rename in @BeforeAll to prevent cross-test contamination"

key-files:
  created:
    - src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
  modified: []

key-decisions:
  - "Used @TempDir as @BeforeAll parameter (not instance field) to ensure injection before setup"
  - "Mark all pre-existing seasons with Test_ prefix to isolate from site generator productionSeasons filter"
  - "Single generate() call in @BeforeAll shared by all 8 tests for performance"

patterns-established:
  - "PER_CLASS + @TempDir parameter injection: reliable pattern for shared @BeforeAll test output"
  - "Cross-test data isolation via Test_ prefix rename for productionSeasons filter"

requirements-completed: [E2E-01, E2E-02, E2E-03, E2E-04, E2E-05, E2E-06]

# Metrics
duration: 17min
completed: 2026-04-17
---

# Phase 49 Plan 01: E2E Site Validation Summary

**8 E2E tests validating all internal links resolve, nav/footer/content present on every page, tile cards link correctly, links page renders configured URLs, YouTube footer across page types, and season filter on overview pages**

## Performance

- **Duration:** 17 min
- **Started:** 2026-04-17T15:36:15Z
- **Completed:** 2026-04-17T15:53:26Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Created SiteGeneratorE2ETest with 8 passing E2E validation tests covering E2E-01 through E2E-06 plus D-17
- Link crawler walks all generated .html files, parses with JSoup, resolves internal hrefs via Path.resolve, collects all broken links before asserting
- Full test suite passes: 1006 tests, 0 failures, 87.5% line coverage (above 82% minimum)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create SiteGeneratorE2ETest with 8 validation tests** - `3aef6d6` (test)

## Files Created/Modified
- `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java` - E2E cross-cutting validation test class with 8 tests covering link resolution, nav/footer presence, content non-emptiness, tile card links, configured links page, YouTube footer, and season filter

## Decisions Made
- **@TempDir injection strategy:** Used `@TempDir Path` as a parameter to `@BeforeAll` rather than an instance field, because JUnit 5 does not inject `@TempDir` instance fields before `@BeforeAll` even with `PER_CLASS` lifecycle in the project's JUnit version. This is a deviation from the plan's specification (D-04) but required for correct behavior.
- **Cross-test isolation:** Added season renaming (prefix with "Test_") in `@BeforeAll` to prevent data from other test classes (particularly `SiteGeneratorServiceTest`) from contaminating the E2E site generation. Without this, the overview pages would include teams from stale seasons without team profiles, causing broken internal links.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] @TempDir not injected as instance field before @BeforeAll**
- **Found during:** Task 1 (first test run)
- **Issue:** `@TempDir Path tempDir` as instance field was `null` in `@BeforeAll` despite `@TestInstance(PER_CLASS)`, causing NullPointerException
- **Fix:** Changed to `@TempDir Path injectedTempDir` as parameter on `@BeforeAll` method, assigned to instance field
- **Files modified:** src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
- **Verification:** `./mvnw test -Dtest=SiteGeneratorE2ETest` passes all 8 tests
- **Committed in:** 3aef6d6

**2. [Rule 1 - Bug] Cross-test data contamination causing broken links in full suite**
- **Found during:** Task 1 (full suite verify)
- **Issue:** `SiteGeneratorServiceTest` creates non-"Test" seasons with teams but no race results. These persist in H2 and appear in `generateTeamsOverview` with links to team profiles that don't exist (because `generateTeamProfiles` skips teams without standings)
- **Fix:** In `@BeforeAll`, renamed all pre-existing seasons to include "Test_" prefix so `productionSeasons` filter excludes them
- **Files modified:** src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
- **Verification:** `./mvnw verify` passes: 1006 tests, 0 failures, 87.5% coverage
- **Committed in:** 3aef6d6

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes necessary for test correctness. No scope creep.

## Issues Encountered
- Attempted `@DirtiesContext(BEFORE_CLASS)` as an alternative for test isolation, but it caused Flyway migration failures when recreating the Spring context. Reverted to the season-rename approach which works cleanly within the shared context.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All E2E validation tests pass, confirming phases 44-48 features work end-to-end
- v1.6 milestone capstone testing complete
- No blockers or concerns

---
*Phase: 49-e2e-site-validation*
*Completed: 2026-04-17*
