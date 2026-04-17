# Requirements: CTC Manager

**Defined:** 2026-04-16
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.6 Requirements

Requirements for the Static Site Quality milestone. Each maps to roadmap phases.

### Bugfixes

- [x] **LINK-01**: Archive page links use slugified displayLabel matching actual directory names
- [x] **LINK-02**: Nav "Driver Ranking" link resolves to active season's driver-ranking page
- [x] **LINK-03**: All navigation links use relative paths (not absolute /index.html)
- [x] **LINK-04**: Team logo images resolve correctly on static site pages

### Content

- [x] **CONT-01**: Season year and number are displayed on all pages (archive, standings, hero, profiles)
- [x] **CONT-02**: Standings table teams link to their team profile pages
- [x] **CONT-03**: Driver ranking entries link to driver profile pages
- [x] **CONT-04**: Matchday driver names link to driver profile pages
- [x] **CONT-05**: Season subnavigation shows links to standings, matchdays, driver ranking, playoff
- [x] **CONT-06**: Test seasons (name containing "Test") are filtered from the archive
- [x] **CONT-07**: Empty match-meta (track/car) and empty period column are hidden when no data exists
- [x] **CONT-08**: Team profile lists the team's drivers with links to their profiles

### Code Quality

- [x] **QUAL-01**: No inline styles in archive.html and driver-profile.html (CSS classes instead)

### UX/Design

- [x] **UX-01**: Skip-to-content link for keyboard navigation
- [x] **UX-02**: Active navigation item is visually highlighted
- [x] **UX-03**: Breadcrumbs on subpages (Home > Season > Page)
- [x] **UX-04**: Match winner team is visually highlighted in match cards
- [x] **UX-05**: Mobile tables show scroll indicator when horizontally scrollable
- [x] **UX-06**: Footer contains useful links (back to top, archive, active season)
- [x] **UX-07**: Nav toggle button has proper aria-label for screen readers
- [x] **UX-08**: Hover transitions on table rows and links (150-300ms)
- [x] **UX-09**: cursor:pointer on all clickable elements in site CSS

### Infrastructure

- [x] **CLEAN-01**: Output directory is emptied before page generation begins
- [x] **CLEAN-02**: Clean operation handles non-existent output directory gracefully

### Landing Page

- [x] **LAND-01**: Index page displays a YouTube embed iframe as the hero element (video ID scraped from channel page)
- [x] **LAND-02**: Index page shows tile navigation cards (Seasons, Standings, Drivers, Teams, Links)
- [x] **LAND-03**: Standings table and last matchday section are removed from the index page
- [x] **LAND-04**: All tile links resolve to valid generated pages
- [x] **LAND-05**: YouTube channel URL is configurable; video ID is scraped automatically via Jsoup

### Links (extended)

- [x] **LINK-05**: Footer contains a YouTube link to `https://www.youtube.com/@CommunityTeamCup`
- [x] **LINK-06**: YouTube footer link is present on all generated pages
- [x] **LINK-07**: A `links.html` page is generated as part of the static site
- [x] **LINK-08**: Links are configurable via `ctc.site.links` application properties (list of name/url pairs)
- [x] **LINK-09**: The links page renders all configured links as clickable elements
- [x] **LINK-10**: The links page uses the shared layout (nav, footer, consistent styling)

### Overview Pages

- [x] **OVER-01**: A `teams.html` page lists all teams across all seasons
- [x] **OVER-02**: A `drivers.html` page lists all drivers across all seasons
- [x] **OVER-03**: Both overview pages can be filtered by season (client-side JS, static site)
- [x] **OVER-04**: Teams overview shows team name, logo, and seasons participated
- [x] **OVER-05**: Drivers overview shows PSN ID, team(s), and seasons participated
- [x] **OVER-06**: Team/driver names link to their season-specific profile pages

### E2E Validation

- [x] **E2E-01**: All internal `href` links resolve to existing files in the output directory
- [x] **E2E-02**: All pages have consistent navigation structure (nav + footer present)
- [x] **E2E-03**: No generated page has empty main content
- [x] **E2E-04**: Landing page tiles link to valid generated pages
- [x] **E2E-05**: Links page renders all configured links
- [x] **E2E-06**: Footer YouTube link present on all page types

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Typography/Branding

- **TYPO-01**: Replace system font body with gaming font pairing (e.g. Russo One / Chakra Petch)
- **TYPO-02**: Secondary accent color for winners/CTAs (e.g. orange or green)

### Advanced Features

- **ADV-01**: Matchday navigation (prev/next matchday links)
- **ADV-02**: Driver statistics charts on driver profile pages
- **ADV-03**: Season comparison view across multiple seasons

## Out of Scope

| Feature | Reason |
|---------|--------|
| Admin UI changes | This milestone focuses exclusively on the static site |
| Database schema changes | No new data model needed for site improvements |
| New Flyway migrations | All data already exists, just needs better presentation |
| Authentication changes | Static site is public, auth is admin-only |
| Font/branding overhaul | Conthrax font works well, major typography changes deferred to v2 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| LINK-01 | Phase 37 | Complete |
| LINK-02 | Phase 37 | Complete |
| LINK-03 | Phase 37 | Complete |
| LINK-04 | Phase 37 | Complete |
| CONT-01 | Phase 38 | Complete |
| CONT-06 | Phase 38 | Complete |
| CONT-07 | Phase 38 | Complete |
| CONT-02 | Phase 39 | Complete |
| CONT-03 | Phase 39 | Complete |
| CONT-04 | Phase 39 | Complete |
| CONT-08 | Phase 39 | Complete |
| CONT-05 | Phase 42 | Complete |
| UX-02 | Phase 42 | Complete |
| UX-03 | Phase 40 | Complete |
| UX-01 | Phase 41 | Complete |
| UX-04 | Phase 41 | Complete |
| UX-05 | Phase 41 | Complete |
| UX-06 | Phase 41 | Complete |
| UX-07 | Phase 41 | Complete |
| UX-08 | Phase 41 | Complete |
| UX-09 | Phase 41 | Complete |
| QUAL-01 | Phase 41 | Complete |
| CLEAN-01 | Phase 44 | Complete |
| CLEAN-02 | Phase 44 | Complete |
| LINK-05 | Phase 45 | Complete |
| LINK-06 | Phase 45 | Complete |
| LINK-07 | Phase 46 | Complete |
| LINK-08 | Phase 46 | Complete |
| LINK-09 | Phase 46 | Complete |
| LINK-10 | Phase 46 | Complete |
| OVER-01 | Phase 47 | Complete |
| OVER-02 | Phase 47 | Complete |
| OVER-03 | Phase 47 | Complete |
| OVER-04 | Phase 47 | Complete |
| OVER-05 | Phase 47 | Complete |
| OVER-06 | Phase 50 | Complete |
| LAND-01 | Phase 48 | Complete |
| LAND-02 | Phase 48 | Complete |
| LAND-03 | Phase 48 | Complete |
| LAND-04 | Phase 48 | Complete |
| LAND-05 | Phase 48 | Complete |
| E2E-01 | Phase 49 | Complete |
| E2E-02 | Phase 49 | Complete |
| E2E-03 | Phase 49 | Complete |
| E2E-04 | Phase 49 | Complete |
| E2E-05 | Phase 49 | Complete |
| E2E-06 | Phase 49 | Complete |

**Coverage:**

- v1.6 requirements: 48 total (22 original + 26 new)
- Mapped to phases: 48
- Complete: 48, Pending: 0

---

*Requirements defined: 2026-04-16*
*Last updated: 2026-04-17 after milestone audit (35 checkboxes Pending → Complete, OVER-06 → Phase 50)*
