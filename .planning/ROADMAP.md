# Roadmap: CTC Manager

## Milestones

- :white_check_mark: **v1.0 Technical Debt Cleanup** — Phases 1-5 (shipped 2026-04-04)
- :white_check_mark: **v1.1 Codebase Concerns Cleanup** — Phases 6-15 (shipped 2026-04-07)
- :white_check_mark: **v1.3 English Test Data** — Phases 20-27 (shipped 2026-04-10)
- :white_check_mark: **v1.5 Code Review Fixes** — Phases 28-36 (shipped 2026-04-15)
- **v1.6 Static Site Quality** — Phases 37-43 (in progress)

## Phases

<details>
<summary>v1.0 Technical Debt Cleanup (Phases 1-5) -- SHIPPED 2026-04-04</summary>

- [x] Phase 1: Exception Infrastructure (2/2 plans) -- completed 2026-04-03
- [x] Phase 2: Service Layer Extraction (4/4 plans) -- completed 2026-04-04
- [x] Phase 3: God Service Split (2/2 plans) -- completed 2026-04-04
- [x] Phase 4: Database Optimization (1/1 plan) -- completed 2026-04-04
- [x] Phase 5: Security (3/3 plans) -- completed 2026-04-04

</details>

<details>
<summary>v1.1 Codebase Concerns Cleanup (Phases 6-15) -- SHIPPED 2026-04-07</summary>

- [x] Phase 6: Security Hardening (1/1 plan) -- completed 2026-04-04
- [x] Phase 7: Layer Cleanup (3/3 plans) -- completed 2026-04-05
- [x] Phase 8: Exception Refinement (2/2 plans) -- completed 2026-04-05
- [x] Phase 9: Alltime Standings (1/1 plan) -- completed 2026-04-05
- [x] Phase 10: Service Refactoring (3/3 plans) -- completed 2026-04-06
- [x] Phase 11: Template Quality (3/3 plans) -- completed 2026-04-06
- [x] Phase 12: Security Hardening Recovery (1/1 plan) -- completed 2026-04-06
- [x] Phase 13: Layer Cleanup Recovery (3/3 plans) -- completed 2026-04-06
- [x] Phase 14: Exception Refinement Recovery (2/2 plans) -- completed 2026-04-07
- [x] Phase 15: Alltime Standings Recovery (1/1 plan) -- completed 2026-04-07

See: milestones/v1.1-ROADMAP.md for full details

</details>

<details>
<summary>v1.3 English Test Data (Phases 20-27) -- SHIPPED 2026-04-10</summary>

- [x] Phase 20: English Messages -- completed 2026-04-08
- [x] Phase 21: English Code -- completed 2026-04-09
- [x] Phase 22: Dev Teams & Drivers -- completed 2026-04-09
- [x] Phase 23: Dev Seasons with Results -- completed 2026-04-10
- [x] Phase 24: Restore Fictive Dev Data -- completed 2026-04-10
- [x] Phase 25: Fix I18N Regressions -- completed 2026-04-10
- [x] Phase 26: Restore Fictive Team Logos -- completed 2026-04-10
- [x] Phase 27: Restore Matchday/Result Seed Pipeline -- completed 2026-04-10

</details>

<details>
<summary>v1.5 Code Review Fixes (Phases 28-36) -- SHIPPED 2026-04-15</summary>

- [x] Phase 28: RaceAttachment Security (1/1 plan) -- completed 2026-04-13
- [x] Phase 29: Mass Assignment Fix (1/1 plan) -- completed 2026-04-13
- [x] Phase 30: CSRF and Template Security (2/2 plans) -- completed 2026-04-13
- [x] Phase 31: Null Safety and Transaction Fix (2/2 plans) -- completed 2026-04-13
- [x] Phase 32: Layering and Exception Fix (2/2 plans) -- completed 2026-04-13
- [x] Phase 33: Controller Cleanup (2/2 plans) -- completed 2026-04-14
- [x] Phase 34: Convention Fixes (2/2 plans) -- completed 2026-04-14
- [x] Phase 35: Site Generator Bye-Race Null Safety (1/1 plan) -- completed 2026-04-14
- [x] Phase 36: Audit Remediation (1/1 plan) -- completed 2026-04-14

See: milestones/v1.5-ROADMAP.md for full details

</details>

### v1.6 Static Site Quality

**Milestone Goal:** Fix broken links, add missing content, improve navigation and cross-linking, and deliver a polished, accessible static site with professional UX.

- [x] **Phase 37: Critical Link Fixes** - Fix all broken navigation and asset links that block every other page (completed 2026-04-16)
- [x] **Phase 38: Season Content & Data Filtering** - Display season metadata on all pages and hide missing/test data (gap closure) (completed 2026-04-16)
- [x] **Phase 39: Entity Cross-Linking** - Add inline links from standings, rankings, matchdays, and team profiles (completed 2026-04-16)
- [x] **Phase 40: Navigation & Structure** - Season subnavigation, active nav state, and breadcrumbs (completed 2026-04-16)
- [x] **Phase 41: UX Polish & Accessibility** - Skip link, winner highlight, mobile scroll, footer, aria, hover transitions (completed 2026-04-16)
- [x] **Phase 42: Navigation Gap Closure** - Fix top-nav active state for index/archive and add playoff subnav guard (completed 2026-04-16)
- [x] **Phase 43: Code Quality Cleanup** - Extract match card fragment, fix vacuous test, remove dead code (completed 2026-04-16)

## Phase Details

### Phase 37: Critical Link Fixes

**Goal**: All navigation links and asset references on the static site resolve correctly
**Depends on**: Phase 36 (previous milestone complete)
**Requirements**: LINK-01, LINK-02, LINK-03, LINK-04

**Success Criteria** (what must be TRUE):

1. Clicking a season link in the archive navigates to that season's directory without a 404
2. Clicking "Driver Ranking" in the nav opens the active season's driver-ranking page
3. All navigation links work when the static site is opened from any subdirectory (relative paths)
4. Team logo images display correctly on all static site pages (standings, team-profile, matchday)

**Plans:** 2/2 plans complete

Plans:
- [x] 37-01-PLAN.md — TDD RED: Write failing tests for LINK-01..04 + add uploadDir field
- [x] 37-02-PLAN.md — TDD GREEN: Implement all four link fixes (archive slugs, nav links, relative paths, team logos)

**UI hint**: yes

### Phase 38: Season Content & Data Filtering

**Goal**: Every page shows the season's year and number, and the archive shows only real seasons
**Depends on**: Phase 37
**Requirements**: CONT-01, CONT-06, CONT-07

**Success Criteria** (what must be TRUE):

1. Season year and number (e.g. "2025 | #3 | CTC Season 3") appear in hero, archive, standings, and profile pages
2. Seasons whose name contains "Test" do not appear in the public archive listing
3. Match cards with no track or car data do not display empty match-meta sections
4. Period columns are hidden on match rows that have no period data

**Plans:** 3/3 plans complete

Plans:
- [x] 38-01-PLAN.md — TDD RED: Rename test season, write failing tests for CONT-01, CONT-06, CONT-07
- [x] 38-02-PLAN.md — TDD GREEN: Implement season filter, season metadata display, match-meta guards
- [x] 38-03-PLAN.md — Gap closure: Add .season-meta to team-profile and driver-profile templates

**UI hint**: yes

### Phase 39: Entity Cross-Linking

**Goal**: Users can navigate between related entities (teams, drivers) directly from content pages
**Depends on**: Phase 38
**Requirements**: CONT-02, CONT-03, CONT-04, CONT-08

**Success Criteria** (what must be TRUE):

1. Each team in the standings table is a link that opens that team's profile page
2. Each driver in the driver ranking is a link that opens that driver's profile page
3. Driver names on matchday result rows link to their driver profile pages
4. A team's profile page lists the team's drivers, each linking to their driver profile

**Plans:** 2/2 plans complete

Plans:
- [x] 39-01-PLAN.md — TDD RED: Write failing tests for entity cross-link assertions (CONT-02, CONT-03, CONT-04, CONT-08)
- [x] 39-02-PLAN.md — TDD GREEN: Implement service data plumbing, template links, and CSS entity-link class

**UI hint**: yes

### Phase 40: Navigation & Structure

**Goal**: Season content is reachable through a consistent subnavigation and visual feedback shows the current page
**Depends on**: Phase 39
**Requirements**: CONT-05, UX-02, UX-03

**Success Criteria** (what must be TRUE):

1. Season pages show a subnavigation bar with links to Standings, Matchdays, Driver Ranking, and Playoff for that season
2. The active navigation item is visually distinct from inactive items (highlighted/underlined/different color)
3. Subpages display breadcrumbs (e.g. "Home > Season 2025 > Standings") for orientation

**Plans:** 2/2 plans complete

Plans:
- [x] 40-01-PLAN.md — TDD RED: Write failing tests for subnav, active state, breadcrumbs, matchday index
- [x] 40-02-PLAN.md — TDD GREEN: Implement service nav context, layout subnav/breadcrumbs, matchdays.html, CSS

**UI hint**: yes

### Phase 41: UX Polish & Accessibility

**Goal**: The static site is accessible to keyboard and screen reader users and delivers polished visual feedback
**Depends on**: Phase 40
**Requirements**: UX-01, UX-04, UX-05, UX-06, UX-07, UX-08, UX-09, QUAL-01

**Success Criteria** (what must be TRUE):

1. A skip-to-content link is reachable as the first focusable element on every page
2. The winning team in a match card is visually highlighted (distinct background or badge)
3. Wide tables on mobile show a visual indicator that horizontal scrolling is available
4. The footer contains working links (back to top, archive, active season)
5. Hovering over table rows and links triggers a smooth transition (150-300ms); all clickable elements show cursor:pointer
6. The nav toggle button has a descriptive aria-label; inline styles are removed from archive.html and driver-profile.html

**Plans:** 2/2 plans complete

Plans:
- [x] 41-01-PLAN.md — TDD RED: Add winner booleans to RaceView, activeSeasonName to writeTemplate, write 4 failing tests
- [x] 41-02-PLAN.md — TDD GREEN: Implement all HTML template changes and CSS rules for UX polish and accessibility

**UI hint**: yes

### Phase 42: Navigation Gap Closure

**Goal**: Top-nav active state works on all pages and playoff subnav link only appears when playoff data exists
**Depends on**: Phase 41
**Requirements**: UX-02, CONT-05
**Gap Closure**: Closes partial gaps from v1.6 audit

**Success Criteria** (what must be TRUE):

1. The top-nav "Standings" link is visually active on the index (home) page
2. The top-nav "Archive" link is visually active on the archive page
3. The Playoff subnav link only renders for seasons that have playoff data
4. Seasons without playoff data show exactly 3 subnav links (no Playoff)

### Phase 43: Code Quality Cleanup

**Goal**: Remove code duplication, fix misleading tests, and remove dead code from static site generator
**Depends on**: Phase 42
**Requirements**: —
**Gap Closure**: Closes tech debt from v1.6 audit (IN-01, IN-03, IN-04)

**Success Criteria** (what must be TRUE):

1. Match card markup exists in a single Thymeleaf fragment, reused by index.html and matchday.html
2. Driver link test uses explicit negative assertion (no vacuous always-pass)
3. Dead null guards on activeSeasonSlug/activeSeasonName in writeTemplate are removed

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Exception Infrastructure | v1.0 | 2/2 | Complete | 2026-04-03 |
| 2. Service Layer Extraction | v1.0 | 4/4 | Complete | 2026-04-04 |
| 3. God Service Split | v1.0 | 2/2 | Complete | 2026-04-04 |
| 4. Database Optimization | v1.0 | 1/1 | Complete | 2026-04-04 |
| 5. Security | v1.0 | 3/3 | Complete | 2026-04-04 |
| 6. Security Hardening | v1.1 | 1/1 | Complete | 2026-04-04 |
| 7. Layer Cleanup | v1.1 | 3/3 | Complete | 2026-04-05 |
| 8. Exception Refinement | v1.1 | 2/2 | Complete | 2026-04-05 |
| 9. Alltime Standings | v1.1 | 1/1 | Complete | 2026-04-05 |
| 10. Service Refactoring | v1.1 | 3/3 | Complete | 2026-04-06 |
| 11. Template Quality | v1.1 | 3/3 | Complete | 2026-04-06 |
| 12. Security Hardening Recovery | v1.1 | 1/1 | Complete | 2026-04-06 |
| 13. Layer Cleanup Recovery | v1.1 | 3/3 | Complete | 2026-04-06 |
| 14. Exception Refinement Recovery | v1.1 | 2/2 | Complete | 2026-04-07 |
| 15. Alltime Standings Recovery | v1.1 | 1/1 | Complete | 2026-04-07 |
| 20. English Messages | v1.3 | — | Complete | 2026-04-08 |
| 21. English Code | v1.3 | — | Complete | 2026-04-09 |
| 22. Dev Teams & Drivers | v1.3 | — | Complete | 2026-04-09 |
| 23. Dev Seasons with Results | v1.3 | — | Complete | 2026-04-10 |
| 24. Restore Fictive Dev Data | v1.3 | 1/1 | Complete | 2026-04-10 |
| 25. Fix I18N Regressions | v1.3 | 1/1 | Complete | 2026-04-10 |
| 26. Restore Fictive Team Logos | v1.3 | 1/1 | Complete | 2026-04-10 |
| 27. Restore Matchday/Result Seed Pipeline | v1.3 | 1/1 | Complete | 2026-04-10 |
| 28. RaceAttachment Security | v1.5 | 1/1 | Complete | 2026-04-13 |
| 29. Mass Assignment Fix | v1.5 | 1/1 | Complete | 2026-04-13 |
| 30. CSRF and Template Security | v1.5 | 2/2 | Complete | 2026-04-13 |
| 31. Null Safety and Transaction Fix | v1.5 | 2/2 | Complete | 2026-04-13 |
| 32. Layering and Exception Fix | v1.5 | 2/2 | Complete | 2026-04-13 |
| 33. Controller Cleanup | v1.5 | 2/2 | Complete | 2026-04-14 |
| 34. Convention Fixes | v1.5 | 2/2 | Complete | 2026-04-14 |
| 35. Site Generator Bye-Race Null Safety | v1.5 | 1/1 | Complete | 2026-04-14 |
| 36. Audit Remediation | v1.5 | 1/1 | Complete | 2026-04-14 |
| 37. Critical Link Fixes | v1.6 | 2/2 | Complete    | 2026-04-16 |
| 38. Season Content & Data Filtering | v1.6 | 3/3 | Complete    | 2026-04-16 |
| 39. Entity Cross-Linking | v1.6 | 2/2 | Complete    | 2026-04-16 |
| 40. Navigation & Structure | v1.6 | 2/2 | Complete    | 2026-04-16 |
| 41. UX Polish & Accessibility | v1.6 | 2/2 | Complete    | 2026-04-16 |
| 42. Navigation Gap Closure | v1.6 | 1/1 | Complete | 2026-04-16 |
| 43. Code Quality Cleanup | v1.6 | 1/1 | Complete | 2026-04-16 |
