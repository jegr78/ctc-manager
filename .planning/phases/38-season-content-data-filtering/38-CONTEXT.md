# Phase 38: Season Content & Data Filtering - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Display season metadata (year, number) consistently across all static site pages and filter out test/empty data so the public site shows only real, meaningful content. This phase addresses three requirements: season year/number display (CONT-01), test season filtering (CONT-06), and empty match-meta/period hiding (CONT-07). No new pages, no new navigation, no cross-linking — pure content enrichment and data filtering.

</domain>

<decisions>
## Implementation Decisions

### Season display format (CONT-01)
- **D-01:** Season `name` remains the primary identifier in headings/titles. Year and number are shown as supplementary context — not replacing the name but enriching it.
- **D-02:** In page headings (standings, matchday, driver-ranking, etc.), format as `"Team Standings — {season.name}"` (unchanged) but add a subtitle or badge line showing year and season number (e.g., `"2025 | Season #3"`).
- **D-03:** In the hero section (index.html), show year as part of the hero label: `"Season {name} — {year}"` or similar compact format.
- **D-04:** In the archive table, add a dedicated Year column or integrate year/number into the Season column (e.g., `"{name} (#3, 2025)"`). Use `season.year` and `season.number` fields directly rather than parsing `getDisplayLabel()`.
- **D-05:** Pre-compute any formatted display strings in `SiteGeneratorService` and pass as template variables — no complex SpEL in templates.

### Test season filter scope (CONT-06)
- **D-06:** Filter test seasons (name contains "Test") at the service level in `generate()`, before the per-season page generation loop. This means NO pages are generated for test seasons — no standings, matchday, team-profile, driver-profile, driver-ranking, or playoff pages.
- **D-07:** Also filter test seasons from the archive listing. The `generateArchive()` method should receive only non-test seasons.
- **D-08:** The filter condition is `season.getName().contains("Test")` — case-sensitive, matching the convention established in REQUIREMENTS.md.

### Match-meta visibility (CONT-07)
- **D-09:** Hide the entire `match-meta` div when both `race.track` and `race.car` are null. Add a `th:if` guard: `th:if="${race.track != null or race.car != null}"`. This applies to `matchday.html` and `index.html` (last matchday section).
- **D-10:** When only one of track/car is present, show just that value without the separator dash.

### Archive period column (CONT-07)
- **D-11:** Keep the Period column header visible in the archive table. For seasons without start/end dates, show an em-dash or leave the cell empty — no "null" text.
- **D-12:** The existing `th:if` guards on date spans in `archive.html` are mostly correct but should be tightened: when both dates are null, show nothing (not even the dash separator).

### Claude's Discretion
- Exact subtitle/badge CSS styling for season year/number on page headings
- Whether to add a `seasonDisplayYear` or `seasonNumber` template variable to `writeTemplate()` vs. relying on `season.year` and `season.number` from the entity (OSIV active in Thymeleaf)
- Internal refactoring of the archive season filter (inline stream filter vs. separate method)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Static site generator
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Core generation logic, `generate()` loop, `generateArchive()`, `writeTemplate()`, `toRaceView()`
- `src/main/java/org/ctc/sitegen/model/RaceView.java` — View model with `track` and `car` fields (nullable)

### Site templates (all affected by CONT-01)
- `src/main/resources/templates/site/index.html` — Hero label shows season name; last matchday match-meta
- `src/main/resources/templates/site/standings.html` — Section title with season name
- `src/main/resources/templates/site/matchday.html` — Section title with season name; match-meta div
- `src/main/resources/templates/site/driver-ranking.html` — Section title with season name
- `src/main/resources/templates/site/team-profile.html` — Season context display
- `src/main/resources/templates/site/driver-profile.html` — Season context display
- `src/main/resources/templates/site/archive.html` — Season listing with period column, inline styles (noted for QUAL-01)
- `src/main/resources/templates/site/layout.html` — Shared nav/footer

### Domain model
- `src/main/java/org/ctc/domain/model/Season.java` — `year`, `number`, `name` fields; `getDisplayLabel()` returns "year | #number | name"; `startDate`/`endDate` for archive period

### Existing tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Integration tests with Jsoup HTML parsing, `@TempDir` for output

### Site CSS
- `src/main/resources/static/site/css/style.css` — Public site styles; new classes needed for season metadata display

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Season.getDisplayLabel()` — Returns `"year | #number | name"`, but may be too verbose for page headings. Individual fields (`year`, `number`, `name`) are available directly via entity getters (OSIV active).
- `SeasonEntry` record — Already established pattern in `SiteGeneratorService` for pre-computing display data for archive. Can be extended with year/number or a display format.
- `SiteGeneratorServiceTest` — Comprehensive Jsoup-based HTML assertion tests; pattern to follow for verifying season metadata appears in output.
- `RaceView` record — Already has nullable `track` and `car` fields; template guards can use these directly.

### Established Patterns
- Template variables set in service, not computed in Thymeleaf — `assetsPath`, `rootPath`, `activeSeasonSlug` already follow this pattern.
- `writeTemplate()` sets common variables for ALL pages — ideal place to add season-level display variables if needed.
- Season entity passed as `season` variable to all page contexts — templates can access `season.year` and `season.number` directly.
- `th:if` guards already used for conditional rendering (e.g., `th:if="${race.hasResults}"`).

### Integration Points
- `generate()` loads `allSeasons` once — filtering test seasons here propagates to all downstream methods.
- `generateArchive()` receives the season list — must receive the already-filtered list.
- `match-meta` div appears in both `matchday.html` and `index.html` — both need the same `th:if` guard.

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

*Phase: 38-season-content-data-filtering*
*Context gathered: 2026-04-16*
