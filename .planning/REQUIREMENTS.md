# Requirements: CTC Manager

**Defined:** 2026-04-16
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.6 Requirements

Requirements for the Static Site Quality milestone. Each maps to roadmap phases.

### Bugfixes

- [ ] **LINK-01**: Archive page links use slugified displayLabel matching actual directory names
- [ ] **LINK-02**: Nav "Driver Ranking" link resolves to active season's driver-ranking page
- [ ] **LINK-03**: All navigation links use relative paths (not absolute /index.html)
- [ ] **LINK-04**: Team logo images resolve correctly on static site pages

### Content

- [ ] **CONT-01**: Season year and number are displayed on all pages (archive, standings, hero, profiles)
- [ ] **CONT-02**: Standings table teams link to their team profile pages
- [ ] **CONT-03**: Driver ranking entries link to driver profile pages
- [ ] **CONT-04**: Matchday driver names link to driver profile pages
- [ ] **CONT-05**: Season subnavigation shows links to standings, matchdays, driver ranking, playoff
- [ ] **CONT-06**: Test seasons (name containing "Test") are filtered from the archive
- [ ] **CONT-07**: Empty match-meta (track/car) and empty period column are hidden when no data exists
- [ ] **CONT-08**: Team profile lists the team's drivers with links to their profiles

### Code Quality

- [ ] **QUAL-01**: No inline styles in archive.html and driver-profile.html (CSS classes instead)

### UX/Design

- [ ] **UX-01**: Skip-to-content link for keyboard navigation
- [ ] **UX-02**: Active navigation item is visually highlighted
- [ ] **UX-03**: Breadcrumbs on subpages (Home > Season > Page)
- [ ] **UX-04**: Match winner team is visually highlighted in match cards
- [ ] **UX-05**: Mobile tables show scroll indicator when horizontally scrollable
- [ ] **UX-06**: Footer contains useful links (back to top, archive, active season)
- [ ] **UX-07**: Nav toggle button has proper aria-label for screen readers
- [ ] **UX-08**: Hover transitions on table rows and links (150-300ms)
- [ ] **UX-09**: cursor:pointer on all clickable elements in site CSS

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
| LINK-01 | Phase 37 | Pending |
| LINK-02 | Phase 37 | Pending |
| LINK-03 | Phase 37 | Pending |
| LINK-04 | Phase 37 | Pending |
| CONT-01 | Phase 38 | Pending |
| CONT-06 | Phase 38 | Pending |
| CONT-07 | Phase 38 | Pending |
| CONT-02 | Phase 39 | Pending |
| CONT-03 | Phase 39 | Pending |
| CONT-04 | Phase 39 | Pending |
| CONT-08 | Phase 39 | Pending |
| CONT-05 | Phase 40 | Pending |
| UX-02 | Phase 40 | Pending |
| UX-03 | Phase 40 | Pending |
| UX-01 | Phase 41 | Pending |
| UX-04 | Phase 41 | Pending |
| UX-05 | Phase 41 | Pending |
| UX-06 | Phase 41 | Pending |
| UX-07 | Phase 41 | Pending |
| UX-08 | Phase 41 | Pending |
| UX-09 | Phase 41 | Pending |
| QUAL-01 | Phase 41 | Pending |

**Coverage:**

- v1.6 requirements: 22 total
- Mapped to phases: 22
- Unmapped: 0

---

*Requirements defined: 2026-04-16*
*Last updated: 2026-04-16 after roadmap creation*
