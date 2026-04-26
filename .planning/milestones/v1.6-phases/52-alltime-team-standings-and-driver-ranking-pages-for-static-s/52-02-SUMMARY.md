---
phase: 52-alltime-team-standings-and-driver-ranking-pages-for-static-s
plan: "02"
subsystem: sitegen
tags: [java, thymeleaf, tdd-green, sitegen, static-site, alltime-standings]

# Dependency graph
requires:
  - "52-01 (TDD RED tests for alltime pages)"
provides:
  - "alltime-standings.html static page (cross-season team standings)"
  - "alltime-driver-ranking.html static page (cross-season driver ranking)"
  - "SiteGeneratorService.generateAlltimeStandings() and generateAlltimeDriverRanking() methods"
  - "Updated layout.html nav: unconditional alltime page links replace season-specific conditional links"
affects:
  - "All static site pages (nav change via layout.html)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "TDD GREEN: implementation makes 3 RED tests pass"
    - "Alltime pages are root-level (outPath.resolve('alltime-standings.html'))"
    - "seasonSlug set to null for alltime pages — subnav is not rendered"
    - "No entity-links on alltime templates (plain span instead of a.entity-link)"

key-files:
  created:
    - src/main/resources/templates/site/alltime-standings.html
    - src/main/resources/templates/site/alltime-driver-ranking.html
  modified:
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/resources/templates/site/layout.html

key-decisions:
  - "Nav links changed from conditional (th:if activeSeasonSlug) to unconditional — Standings and Driver Ranking always visible"
  - "alltime pages are root-level files, not under season/{slug}/ subdirectory"
  - "seasonSlug=null in context ensures subnav is not shown on alltime pages"

patterns-established:
  - "generateAlltimeStandings/generateAlltimeDriverRanking follow same Context pattern as generateArchive/generateLinks"
  - "hero-sub CSS class used for subtitle on alltime pages (vs season-meta on season-specific pages)"

requirements-completed:
  - ALLTIME-01
  - ALLTIME-02
  - ALLTIME-03

# Metrics
duration: 7min
completed: 2026-04-18
---

# Phase 52 Plan 02: TDD GREEN — Alltime Standings and Driver Ranking Pages

**Two new root-level static pages and nav update: alltime team standings and driver ranking now always reachable from top navigation**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-04-18T08:05:00Z
- **Completed:** 2026-04-18T08:12:00Z
- **Tasks:** 2
- **Files modified:** 4 (2 new, 2 existing)

## Accomplishments

- Created `alltime-standings.html` template: table with rank, team shortName (no entity-link), MP/W/D/L/PR/Pts columns, "All seasons combined" subtitle via `hero-sub` class
- Created `alltime-driver-ranking.html` template: table with rank, driver PSN ID (no entity-link), team (null-guard kept), Races/Best/Avg/Points columns, "All seasons combined" subtitle
- Added `generateAlltimeStandings()` private method to `SiteGeneratorService` — calls `standingsService.calculateAlltimeStandings()`, sets `seasonSlug=null`, writes root-level `alltime-standings.html`
- Added `generateAlltimeDriverRanking()` private method — calls `driverRankingService.calculateAlltimeRanking()`, sets `seasonSlug=null`, writes root-level `alltime-driver-ranking.html`
- Both methods wired into `generate()` after `generateDriversOverview`
- Updated `layout.html` nav: removed 2 conditional `th:if` links, replaced with 2 unconditional `alltime-standings.html`/`alltime-driver-ranking.html` links
- All 3 RED tests from Plan 52-01 now GREEN
- Full test suite: **1011 tests, 0 failures, 0 errors** — BUILD SUCCESS
- JaCoCo coverage check: **All coverage checks have been met** (82% minimum maintained)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create alltime templates and service methods** - `5bfd27c` (feat)
2. **Task 2: Update layout nav links to alltime pages** - `db4fe39` (feat)

## Files Created/Modified

- `src/main/resources/templates/site/alltime-standings.html` — NEW: 38 lines, alltime team standings table
- `src/main/resources/templates/site/alltime-driver-ranking.html` — NEW: 38 lines, alltime driver ranking table
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — +32 lines: two new private methods + 2 wiring calls in generate()
- `src/main/resources/templates/site/layout.html` — -6/+4 lines: nav link replacement

## Decisions Made

- `seasonSlug` set to `null` in both alltime method contexts — ensures the subnav (which is `th:if="${seasonSlug != null}"`) is not rendered on alltime pages, consistent with the plan's "alltime pages do NOT show subnav" requirement
- `breadcrumbCurrent` set to `"Alltime Standings"` / `"Alltime Driver Ranking"` — breadcrumb renders as "Home > Alltime Standings" because `seasonSlug=null` suppresses the middle breadcrumb segment
- Nav `currentPage` values changed from `'standings'`/`'driver-ranking'` to `'alltime-standings'`/`'alltime-driver-ranking'` to correctly highlight the active nav link

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — both pages render live data from `standingsService.calculateAlltimeStandings()` and `driverRankingService.calculateAlltimeRanking()`.

## Threat Flags

None — no new network endpoints, auth paths, or trust boundaries introduced. Static file output only.

## TDD Gate Compliance

- RED gate: `test(52-01)` commit `042f703` — 3 failing tests established
- GREEN gate: `feat(52-02)` commits `5bfd27c` + `db4fe39` — all 3 tests now pass
- REFACTOR: not needed

## Self-Check: PASSED

- `src/main/resources/templates/site/alltime-standings.html` — FOUND
- `src/main/resources/templates/site/alltime-driver-ranking.html` — FOUND
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` contains `generateAlltimeStandings` — FOUND (4 occurrences)
- `src/main/resources/templates/site/layout.html` contains `alltime-standings.html` — FOUND (2 occurrences)
- Commit `5bfd27c` — exists (git log confirmed)
- Commit `db4fe39` — exists (git log confirmed)
- `./mvnw verify` — 1011 tests, 0 failures, BUILD SUCCESS, JaCoCo checks passed

---
*Phase: 52-alltime-team-standings-and-driver-ranking-pages-for-static-s*
*Completed: 2026-04-18*
