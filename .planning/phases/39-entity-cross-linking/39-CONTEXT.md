# Phase 39: Entity Cross-Linking - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Add inline navigation links between related entities (teams, drivers) on all content pages of the static site. Teams in standings link to team profiles, drivers in rankings and matchday results link to driver profiles, and team profiles list their drivers with links. This phase covers four requirements: team links in standings (CONT-02), driver links in ranking (CONT-03), driver links in matchday results (CONT-04), and driver listing on team profile (CONT-08). No new pages, no new navigation structure — pure cross-linking of existing entities.

</domain>

<decisions>
## Implementation Decisions

### Team profile driver listing (CONT-08)
- **D-01:** Add a "Drivers" section to `team-profile.html` below the existing Record table. Show a compact list: PSN-ID as a link to the driver profile + total points in the season.
- **D-02:** Use `SeasonDriver` as the data source for the driver-team assignment. Already available via `seasonDriverRepository.findBySeasonId()` and consistent with `generateDriverProfiles()`. Filter by matching team ID.
- **D-03:** Pre-compute driver data (PSN-ID, slug, total points) in `generateTeamProfiles()` and pass as a list to the template — no complex SpEL in Thymeleaf.

### Index page scope
- **D-04:** The index page (`index.html`) gets the same cross-links as the detail pages. Standings table team names link to team profiles, and last matchday result driver names link to driver profiles. Consistent behavior everywhere — users can click entity names regardless of which page they're on.

### Link styling
- **D-05:** Entity links use the existing `--accent` color (`#4fc3f7`, light blue) as their default color. This makes them clearly recognizable as clickable while fitting the established design system.
- **D-06:** Hover state: lighter shade + underline. Add a reusable CSS class (e.g., `.entity-link`) for consistent styling across all cross-linked elements.
- **D-07:** Links inherit the font weight of their context (bold for team short names in standings, regular for driver names in results). Only the color changes.

### Slug computation strategy
- **D-08:** Pre-compute slugified URLs in `SiteGeneratorService` and pass as template variables or embed in view objects. Consistent with Phase 37 pattern where template variables are computed in the service, not in Thymeleaf.
- **D-09:** For standings: add a `teamProfileUrl` (relative path) to each `TeamStanding` context or pass a team-slug map. For driver ranking and matchday results: add a `driverProfileUrl` or driver-slug to the respective data objects.
- **D-10:** All URLs are relative paths from the current page (using the existing `rootPath` mechanism or direct relative path calculation).

### Claude's Discretion
- Exact CSS properties for hover state (opacity, text-decoration style, transition)
- Whether to extend `RaceView.ResultView` with a `driverSlug` field or pass a separate slug map
- Internal refactoring of `generateTeamProfiles()` for driver data loading
- Whether to create a `DriverEntry` record (similar to `SeasonEntry`) for team profile driver data

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Static site generator
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Core generation logic, `slugify()` method, `generateTeamProfiles()`, `generateDriverProfiles()`, `generateStandings()`, `generateDriverRanking()`, `generateMatchdays()`, `generateIndex()`, `writeTemplate()`, `toRaceView()`
- `src/main/java/org/ctc/sitegen/model/RaceView.java` — View model for race display; `ResultView` record has `driverPsnId` and `teamShortName` (need slug fields for links)

### Site templates (all need link additions)
- `src/main/resources/templates/site/standings.html` — Team names in standings table (CONT-02)
- `src/main/resources/templates/site/driver-ranking.html` — Driver PSN-IDs in ranking table (CONT-03)
- `src/main/resources/templates/site/matchday.html` — Driver names in result rows (CONT-04)
- `src/main/resources/templates/site/team-profile.html` — Needs new driver listing section (CONT-08)
- `src/main/resources/templates/site/index.html` — Standings table + last matchday results (D-04: same links as detail pages)

### Domain services
- `src/main/java/org/ctc/domain/service/StandingsService.java` — `TeamStanding` class with `getTeam()` returning Team entity
- `src/main/java/org/ctc/domain/service/DriverRankingService.java` — `DriverRanking` class with `getDriver()` and `getTeam()`

### Domain model
- `src/main/java/org/ctc/domain/model/Driver.java` — `getPsnId()` used for slug and display
- `src/main/java/org/ctc/domain/model/Team.java` — `getShortName()` used for slug and display
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — Links driver to team within a season (D-02: data source for CONT-08)
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` — `findBySeasonId()` query

### Site CSS
- `src/main/resources/static/site/css/style.css` — CSS variables including `--accent: #4fc3f7`; needs new `.entity-link` class (D-06)

### Existing tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Integration tests with Jsoup HTML parsing; pattern for link-correctness assertions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SiteGeneratorService.slugify()` — Existing slugification with umlaut handling; will be used to pre-compute URL slugs for teams and drivers
- `RaceView` / `RaceView.ResultView` records — Can be extended with slug fields for driver/team profile URLs
- `SeasonEntry` record — Pattern for pre-computed display data (used in archive); same pattern for a `DriverEntry` record on team profiles
- `SiteGeneratorServiceTest` — Jsoup-based HTML assertion tests; can verify `<a>` elements and `href` attributes
- CSS `--accent` variable — Already defined, ready for `.entity-link` class

### Established Patterns
- Template variables are computed in the service, not in Thymeleaf — slugs and URLs will follow this pattern
- `writeTemplate()` sets `rootPath` for relative path calculation — can be used for cross-page URLs
- URL schema: `season/{seasonSlug}/team/{teamSlug}.html` for team profiles, `season/{seasonSlug}/driver/{driverSlug}.html` for driver profiles
- `th:if` guards for null-safe rendering (e.g., `th:if="${team != null}"`)

### Integration Points
- `generateStandings()` — passes `standings` (list of `TeamStanding`) to template; needs team slugs added
- `generateDriverRanking()` — passes `driverRanking` (list of `DriverRanking`) to template; needs driver slugs added
- `generateMatchdays()` — passes `races` (list of `RaceView`) to template; `ResultView` needs driver slugs
- `generateTeamProfiles()` — needs to load `SeasonDriver` list filtered by team and pass driver data to template
- `generateIndex()` — passes `standings` and `lastMatchdayRaces`; same slug additions needed as detail pages

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

*Phase: 39-entity-cross-linking*
*Context gathered: 2026-04-16*
