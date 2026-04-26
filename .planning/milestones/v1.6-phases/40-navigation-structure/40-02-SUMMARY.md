---
phase: 40-navigation-structure
plan: "02"
subsystem: sitegen
tags: [tdd, green-gate, sitegen, subnav, breadcrumbs, navigation, thymeleaf, css]

requires:
  - phase: 40-navigation-structure
    plan: "01"
    provides: Seven failing TDD tests for subnav, active nav state, breadcrumbs, and matchday index page

provides:
  - Season subnavigation with 4 pill-style links in layout.html (CONT-05)
  - Active navigation state via currentPage context variable in SiteGeneratorService (UX-02)
  - Breadcrumbs on all season subpages via breadcrumbCurrent context variable (UX-03)
  - generateMatchdayIndex() method creating matchdays.html per season
  - New matchdays.html template listing all matchdays as clickable entity-links
  - CSS classes for subnav, active states, breadcrumbs

affects:
  - 40-03 (if any further UX polish targets subnav or breadcrumb refinements)
  - Phase 41 (UX Polish — will add skip-link, aria improvements, etc. on top of this navigation foundation)

tech-stack:
  added: []
  patterns:
    - "TDD GREEN gate: implement against pre-written failing tests, verify 41/41 pass"
    - "Thymeleaf th:if guard on seasonSlug/breadcrumbCurrent prevents subnav/breadcrumb on archive+index pages"
    - "currentPage variable drives both top-nav active state and subnav active state simultaneously"
    - "seasonSlug (current page's season) distinct from activeSeasonSlug (globally active season) — critical for archive pages"

key-files:
  created:
    - src/main/resources/templates/site/matchdays.html
  modified:
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/resources/templates/site/layout.html
    - src/main/resources/static/site/css/style.css

key-decisions:
  - "seasonSlug vs activeSeasonSlug: subnav links always use seasonSlug (the page's own season slug) not activeSeasonSlug. For non-active season archive pages these differ."
  - "generateIndex() and generateArchive() explicitly set all four nav variables to null so Thymeleaf th:if guards suppress subnav/breadcrumb without TemplateInputException"
  - "generateMatchdayIndex() uses LinkedHashMap<UUID,String> for matchdayLinkMap to preserve insertion order"
  - "matchdays.html breadcrumbCurrent is 'Matchdays' (index page) vs individual matchday label (matchday.html)"

duration: 5min
completed: 2026-04-16
---

# Phase 40 Plan 02: Navigation Structure — TDD GREEN Gate Summary

**Subnav, active nav state, breadcrumbs, and matchday index page implemented — all 41 SiteGeneratorServiceTest tests pass with coverage >= 82%**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-16T16:20:07+02:00
- **Completed:** 2026-04-16T16:23:51+02:00
- **Tasks:** 2
- **Files modified:** 3
- **Files created:** 1

## Accomplishments

- Added nav context variables (`currentPage`, `seasonSlug`, `seasonName`, `breadcrumbCurrent`) to all 9 `generate*()` methods in `SiteGeneratorService.java`
- Created new `generateMatchdayIndex()` method called in the per-season generation loop
- Updated `layout.html` with subnav block (4 pill links, `th:if` guard), breadcrumb block (3-level, `th:if` guard), and active state `th:class` on top-nav links
- Created `matchdays.html` template listing all matchdays as clickable entity-links with `matchdayLinkMap`
- Added CSS: `.nav-link-active`, `.subnav`, `.subnav-inner`, `.subnav-link`, `.subnav-link.active`, `.breadcrumb`, `.breadcrumb-link`, `.breadcrumb-sep`, `.breadcrumb-current`, mobile responsive overrides
- GREEN gate confirmed: 41/41 tests pass, 0 failures, `./mvnw verify` BUILD SUCCESS, all JaCoCo coverage checks met

## Task Commits

1. **Task 1: Add navigation context variables to all generate methods and create generateMatchdayIndex** - `635df27` (feat)
2. **Task 2: Add subnav, breadcrumb, active state to layout.html + create matchdays.html + add CSS** - `95d8924` (feat)

## Files Created/Modified

- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — 64 lines added: 9×4 nav context variable blocks + new generateMatchdayIndex() method + loop call
- `src/main/resources/templates/site/layout.html` — subnav nav block (7 lines), breadcrumb nav block (8 lines), th:class active state on 3 top-nav links
- `src/main/resources/templates/site/matchdays.html` — NEW: matchday index template with entity-link table and empty-state fallback
- `src/main/resources/static/site/css/style.css` — 72 lines added: subnav, active state, breadcrumb classes plus mobile media query

## Decisions Made

- `seasonSlug` (the current page's season slug) is always used in subnav links, NOT `activeSeasonSlug` (globally active season). These differ on non-active season archive pages — using `activeSeasonSlug` would create broken cross-season links.
- `generateIndex()` and `generateArchive()` explicitly set `currentPage`, `seasonSlug`, `seasonName`, and `breadcrumbCurrent` all to `null`. Omitting them would cause `TemplateInputException` from Thymeleaf's `th:if` expressions even with null guards.
- `generateMatchdayIndex()` placed after `generateMatchdays()` in the per-season loop to maintain logical ordering.
- `matchdays.html` breadcrumb current shows "Matchdays" (index page title), while individual `matchday.html` pages show the specific matchday label.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all navigation features are fully wired with real data from the service layer.

## Threat Flags

None — this plan adds only template/CSS presentation changes and context variables. No new network endpoints, auth paths, file access patterns, or schema changes introduced.

## TDD Gate Compliance

- RED gate: `ce4c7ca` (test commit from Plan 01) — 6 of 7 tests failing
- GREEN gate: `635df27` + `95d8924` (feat commits from Plan 02) — all 41 tests passing
- REFACTOR: not needed — code is clean and consistent

## Self-Check

- [x] `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — exists, 9 currentPage variables, generateMatchdayIndex present
- [x] `src/main/resources/templates/site/layout.html` — exists, 4 subnav-link elements, breadcrumb-current present
- [x] `src/main/resources/templates/site/matchdays.html` — exists, matchdayLinkMap referenced
- [x] `src/main/resources/static/site/css/style.css` — exists, .subnav-link.active present
- [x] Commits `635df27` and `95d8924` exist in git log

## Self-Check: PASSED
