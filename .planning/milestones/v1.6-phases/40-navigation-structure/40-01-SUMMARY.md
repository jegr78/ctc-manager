---
phase: 40-navigation-structure
plan: "01"
subsystem: testing
tags: [tdd, jsoup, sitegen, subnav, breadcrumbs, navigation]

requires:
  - phase: 39-entity-cross-linking
    provides: SiteGeneratorServiceTest infrastructure with 34 passing Jsoup-based HTML assertion tests

provides:
  - Seven new failing TDD tests (RED gate) for subnav, active nav state, breadcrumbs, and matchday index page features

affects:
  - 40-02 (GREEN phase — must implement features to make these 6 tests pass)

tech-stack:
  added: []
  patterns:
    - "TDD RED gate: append failing @Test methods to existing SpringBootTest class before implementing features"
    - "Jsoup CSS selector assertions for static HTML: doc.select('.subnav-link.active') pattern"
    - "Negative-condition test (archive has NO breadcrumb) passes in RED phase — this is valid TDD for absence assertions"

key-files:
  created: []
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java

key-decisions:
  - "givenArchivePage_whenGenerate_thenNoBreadcrumb passes in RED phase because absence-of-breadcrumb is already true — this is correct TDD behavior for negative-condition tests"
  - "6 of 7 new tests fail with assertion errors (not compile errors) — RED gate confirmed"

patterns-established:
  - "Negative-condition tests (asserting absence of a feature) may pass in RED phase and should not be treated as failures"

requirements-completed:
  - CONT-05
  - UX-02
  - UX-03

duration: 3min
completed: 2026-04-16
---

# Phase 40 Plan 01: Navigation Structure — TDD RED Gate Summary

**Seven Jsoup-based HTML assertion tests for subnav, active state, breadcrumbs, and matchday index page — 6 fail (RED gate confirmed), 34 existing tests unaffected**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-16T14:11:51Z
- **Completed:** 2026-04-16T14:15:40Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Appended 7 new `@Test` methods to `SiteGeneratorServiceTest.java` (34 → 41 total)
- RED gate confirmed: 6 of 7 new tests fail with assertion errors (not compilation errors)
- All 34 existing tests continue to pass — zero regressions
- Tests cover all three requirements: CONT-05 (3 tests), UX-02 (1 test), UX-03 (3 tests)

## Task Commits

1. **Task 1: Write seven failing tests for navigation features** - `ce4c7ca` (test)

## Files Created/Modified

- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` - Added 7 new @Test methods for subnav/breadcrumb/matchday-index assertions (95 lines inserted)

## Decisions Made

- The `givenArchivePage_whenGenerate_thenNoBreadcrumb` test passes in RED phase because archive.html never had a `.breadcrumb` element. This is valid TDD — the test asserts an absence condition that is already correct and must remain true after GREEN phase implementation. It is not treated as a RED gate failure.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- Initial edit was applied to the main repo path (`/Users/jegr/Documents/github/ctc-manager/src/test/...`) instead of the worktree path. Corrected by re-applying the edit to the correct worktree path (`/Users/jegr/Documents/github/ctc-manager/.claude/worktrees/agent-abd0cd66/src/test/...`). No functional impact — the worktree file was updated correctly before the Maven test run.

## Known Stubs

None — this plan only adds test code. No production code stubs exist.

## Threat Flags

None — test-only code, no production surface added.

## Next Phase Readiness

- Plan 40-01 (RED gate) complete
- Plan 40-02 (GREEN phase) must implement: subnav in layout.html, active state via `currentPage` context variable in SiteGeneratorService, breadcrumb block in layout.html, `generateMatchdayIndex()` method, new `matchdays.html` template, and CSS for `.subnav`, `.subnav-link`, `.breadcrumb`, `.nav-link-active`
- 6 tests currently failing will drive the GREEN implementation

---
*Phase: 40-navigation-structure*
*Completed: 2026-04-16*
