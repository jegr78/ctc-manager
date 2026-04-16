# Phase 40: Navigation & Structure - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Add season subnavigation, active navigation state, and breadcrumbs to the static site so users can navigate within a season and always know where they are. This phase addresses three requirements: season subnavigation with links to all section pages (CONT-05), active navigation highlighting for both top-nav and subnav (UX-02), and breadcrumbs on subpages (UX-03). Additionally, a new matchday index page is introduced as the subnav target for "Matchdays". No admin UI changes, no database changes — purely static site navigation improvements.

</domain>

<decisions>
## Implementation Decisions

### Subnav placement & structure (CONT-05)
- **D-01:** Subnav lives in `layout.html` as a shared component, rendered conditionally when season context is present. Single source of truth — no duplication across templates.
- **D-02:** Pill-style horizontal link bar, visually matching the existing top-nav design. Items: Standings, Matchdays, Driver Ranking, Playoff. Links point to the respective pages within `season/{slug}/`.
- **D-03:** The subnav requires template variables passed via `writeTemplate()`: season slug, season name, and current page identifier. Pre-computed in the service, not in Thymeleaf.

### Subnav scope
- **D-04:** Claude's Discretion — whether the index page and archive page also show the subnav (depends on whether they have a season context). Season subpages always show it.

### Active state (UX-02)
- **D-05:** Active navigation item gets accent color (`#4fc3f7`) plus a subtle background (`rgba(79,195,247,0.1)`). Inactive items remain `text-dim` (#888). Consistent for both top-nav and subnav.
- **D-06:** Both top-nav AND subnav receive active state highlighting. Top-nav shows which section the user is in (e.g., "Standings" active on standings pages), subnav shows the current page within the season.
- **D-07:** Active state is determined by a `currentPage` template variable set in each `generate*()` method and passed through `writeTemplate()`. Layout template conditionally adds an `active` CSS class based on this variable.

### Breadcrumbs (UX-03)
- **D-08:** Three-level hierarchy: `Home > {Season Name} > {Page Title}`. Compact, matching the flat site structure.
- **D-09:** Placed between the subnav and the content area (inside `layout.html`), rendered conditionally when breadcrumb data is present.
- **D-10:** All breadcrumb levels are clickable except the last (current page). Home links to `index.html`, Season links to `standings.html` of that season (the main landing page per season). Current page is plain text.
- **D-11:** Breadcrumb data (labels and URLs) is pre-computed in `SiteGeneratorService` and passed as template variables. Separator: `>` (chevron).

### Matchday index page (CONT-05)
- **D-12:** Create a new `matchdays.html` page per season at `season/{slug}/matchdays.html`. This is the subnav "Matchdays" link target.
- **D-13:** Compact matchday list — each entry shows the matchday label (e.g., "Matchday 5") as a clickable link to the individual matchday page, plus optional date if available. No score details on the index — those belong on the individual matchday pages.
- **D-14:** New `generateMatchdayIndex()` method in `SiteGeneratorService`, called within the per-season generation loop alongside the existing `generateMatchdays()`.

### Claude's Discretion
- Whether the index page (root) shows subnav for the active season (D-04)
- Exact CSS properties for the subnav (padding, gap, border, font-size) — should match top-nav proportions
- Breadcrumb CSS class naming and styling (font-size, color, separator rendering)
- Whether to add the matchday date from `Matchday.matchDate` or leave it as label-only if date is null
- Mobile responsive behavior for the subnav (stack vertically or horizontal scroll)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Static site generator
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Core generation logic, `writeTemplate()` (sets rootPath, assetsPath, activeSeasonSlug), all `generate*()` methods, `slugify()` method
- `src/main/java/org/ctc/sitegen/model/RaceView.java` — View model for race display

### Site templates (all affected)
- `src/main/resources/templates/site/layout.html` — Shared nav/footer fragment; subnav, breadcrumbs, and active state all go here
- `src/main/resources/templates/site/standings.html` — Season standings (subnav item: Standings)
- `src/main/resources/templates/site/matchday.html` — Individual matchday page (subnav context: Matchdays)
- `src/main/resources/templates/site/driver-ranking.html` — Driver ranking (subnav item: Driver Ranking)
- `src/main/resources/templates/site/team-profile.html` — Team profile (subnav context: within season)
- `src/main/resources/templates/site/driver-profile.html` — Driver profile (subnav context: within season)
- `src/main/resources/templates/site/index.html` — Index/home page (may get subnav per D-04)
- `src/main/resources/templates/site/archive.html` — Archive page (no subnav expected)

### Site CSS
- `src/main/resources/static/site/css/style.css` — CSS variables (--accent, --bg-card, --border, --text-dim), existing .nav-links styles, responsive breakpoints at 768px

### Domain model
- `src/main/java/org/ctc/domain/model/Season.java` — `name`, `year`, `number` fields for breadcrumb labels
- `src/main/java/org/ctc/domain/model/Matchday.java` — `label`, `matchDate` fields for matchday index page

### Existing tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Integration tests with Jsoup HTML parsing, `@TempDir` for output

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `writeTemplate()` — Central method setting `rootPath`, `assetsPath`, `activeSeasonSlug`; ideal extension point for `currentPage`, breadcrumb data, and season name
- `.nav-links a` CSS — Existing nav link styling with hover transitions (0.2s); subnav can reuse this pattern
- `.entity-link` CSS class — Established link styling with accent color; breadcrumb links can follow similar pattern
- `.season-meta` CSS class — Season metadata display; already shown on all pages
- `slugify()` — Existing slugification for directory names; reusable for URL computation

### Established Patterns
- Template variables are computed in the service, not in Thymeleaf — all new variables (currentPage, breadcrumbs, matchday list) follow this pattern
- Conditional rendering via `th:if` guards — subnav and breadcrumbs render only when data is present
- `@TempDir` + Jsoup for HTML assertion tests — new subnav/breadcrumb assertions follow this pattern
- Each `generate*()` method already receives `season` and `activeSeasonSlug` — adding `currentPage` is straightforward

### Integration Points
- `layout.html` `<nav>` block — subnav inserts below existing top-nav
- `layout.html` `<main>` block — breadcrumbs insert before content
- `generate()` per-season loop — new `generateMatchdayIndex()` call goes alongside existing generate methods
- Each `generate*()` method — must set `currentPage` in the template context map

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 40-navigation-structure*
*Context gathered: 2026-04-16*
