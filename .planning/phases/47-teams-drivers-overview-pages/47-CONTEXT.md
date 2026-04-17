# Phase 47: Teams & Drivers Overview Pages - Context

**Gathered:** 2026-04-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Generate two new cross-season overview pages: `teams.html` listing all teams and `drivers.html` listing all drivers across all production seasons. Both pages include a client-side season filter dropdown that shows/hides entries based on the selected season. Team/driver names link to their most recent season-specific profile pages.

</domain>

<decisions>
## Implementation Decisions

### Teams Overview Data
- **D-01:** Show parent teams only (filter out sub-teams via `team.isSubTeam()`). Sub-teams are organizational branches, not user-facing entities.
- **D-02:** Each team card displays: team short name (as link), team logo (if available via `copyLogoToAssets()`), and season participation tags.
- **D-03:** Team name links to the most recent season's team profile page (e.g., `season/{latest-slug}/team/{team-slug}.html`).
- **D-04:** Data source: Iterate `seasonTeamRepository.findBySeasonId()` per production season to build `Map<Team, Set<Season>>`. Filter out sub-teams and test seasons.

### Drivers Overview Data
- **D-05:** Each driver card displays: PSN ID (as link), team name(s) they drove for, and season participation tags.
- **D-06:** Driver name links to the most recent season's driver profile page (e.g., `season/{latest-slug}/driver/{driver-slug}.html`).
- **D-07:** Data source: Iterate `seasonDriverRepository.findBySeasonId()` per production season to build `Map<Driver, List<SeasonDriverInfo>>` where `SeasonDriverInfo` captures season + team.
- **D-08:** Show the team from the most recent season as the primary team display. If a driver was on multiple teams across seasons, show all as tags.

### Client-Side Season Filter
- **D-09:** Each team/driver card gets a `data-seasons="slug-1 slug-2 ..."` attribute with space-separated season slugs.
- **D-10:** A `<select>` dropdown at the top of each page lists "All Seasons" (default) plus each production season by display label.
- **D-11:** Vanilla JavaScript: on select change, iterate all cards, toggle `display: none` for cards whose `data-seasons` does not contain the selected slug. "All Seasons" shows all cards.
- **D-12:** The JavaScript is inline in each template (small enough, no separate .js file needed).

### Layout & Styling
- **D-13:** Responsive grid layout like existing `.match-grid` — 2 columns on desktop, 1 column on mobile. New CSS classes: `.overview-grid`, `.overview-card`.
- **D-14:** Card style consistent with `.link-card` from Phase 46 — dark background (`var(--bg-card)`), border, hover effect, accent color for links.
- **D-15:** Season tags displayed as small pills/badges (`.season-tag` CSS class) with muted styling.
- **D-16:** Team logos in overview cards use the same `copyLogoToAssets()` approach as team profiles.

### Page Integration
- **D-17:** Both pages generated in output root (same level as index.html, archive.html, links.html).
- **D-18:** Both pages use shared layout (nav, footer, breadcrumbs). `currentPage` = "teams" / "drivers".
- **D-19:** Not added to top navigation — reachable via landing page tiles (Phase 48).
- **D-20:** `generateTeamsOverview()` and `generateDriversOverview()` methods in `SiteGeneratorService`, called from `generate()` after `generateLinks()`.

### Records for Template Data
- **D-21:** Create view records in `SiteGeneratorService` for template binding:
  - `record TeamOverviewEntry(String shortName, String teamSlug, String logoRelPath, String profileUrl, List<String> seasonSlugs, List<String> seasonLabels)`
  - `record DriverOverviewEntry(String psnId, String driverSlug, String teamName, String profileUrl, List<String> seasonSlugs, List<String> seasonLabels)`

### Claude's Discretion
- Exact filter JS implementation details
- Whether to sort teams/drivers alphabetically or by most-recent-season-first
- Breadcrumb text for overview pages
- Additional CSS refinements for season tags

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Site Generator Service
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Main service. New `generateTeamsOverview()` and `generateDriversOverview()` methods. Existing `generateTeamProfiles()` (line 211) shows how to gather team data per season. `slugify()` for URL generation.

### Repositories
- `src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java` — `findBySeasonId(UUID)` returns teams in a season.
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` — `findBySeasonId(UUID)` returns drivers in a season.
- `src/main/java/org/ctc/domain/repository/TeamRepository.java` — `findAll()` for all teams.

### Domain Model
- `src/main/java/org/ctc/domain/model/Team.java` — `isSubTeam()`, `getShortName()`, `getLogoUrl()`, `getParentOrSelf()`.
- `src/main/java/org/ctc/domain/model/Driver.java` — `getPsnId()`.
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — Links driver to season and team.
- `src/main/java/org/ctc/domain/model/SeasonTeam.java` — Links team to season with metadata.

### Templates (analogs)
- `src/main/resources/templates/site/links.html` — Closest analog for a simple card-based listing page (Phase 46).
- `src/main/resources/templates/site/archive.html` — Archive listing pattern.

### CSS
- `src/main/resources/static/site/css/style.css` — `.link-grid`/`.link-card` from Phase 46 as base pattern for overview cards.

### Tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Existing tests. New tests follow same JSoup pattern.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `writeTemplate()` — handles all rendering with relative path computation
- `slugify()` — URL-safe slug generation
- `copyLogoToAssets()` — team logo copying to output assets directory
- `SiteProperties` (Phase 46) — already injected into service
- `.link-card` CSS (Phase 46) — visual base for overview cards
- `SeasonEntry` record — existing pattern for template data records

### Established Patterns
- All `generate*()` methods: create Context, set variables, call `writeTemplate()`, increment result
- Templates use `th:replace="~{site/layout :: layout(title, ~{::section})}"` for shared layout
- Navigation context: `currentPage`, `seasonSlug`, `seasonName`, `breadcrumbCurrent`
- Production seasons filtered by `!s.getName().contains("Test")`

### Integration Points
- `SiteGeneratorService.generate()` — call new methods after `generateLinks()` (after line ~90)
- `SeasonTeamRepository` — may need injection if not already present on the service (check existing fields)
- New records for template data binding (inner classes/records in SiteGeneratorService)

</code_context>

<specifics>
## Specific Ideas

- Season filter dropdown should be styled consistently with the site's dark theme
- Season tags as small pills: `background: rgba(79, 195, 247, 0.1); color: var(--accent); border-radius: 12px; padding: 2px 8px; font-size: 11px;`
- Filter JS should be minimal — ~15 lines of vanilla JS in a `<script>` block at the bottom of the template

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 47-teams-drivers-overview-pages*
*Context gathered: 2026-04-17*
