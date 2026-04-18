# Phase 48: Landing Page Redesign - Context

**Gathered:** 2026-04-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Transform `index.html` from a content page (standings table + last matchday) into a landing page with a YouTube hero video and 5 tile navigation cards. The YouTube video ID is scraped from the channel page at generation time. The top navigation is adjusted so "Standings" links to the active season standings page instead of index.html.

</domain>

<decisions>
## Implementation Decisions

### YouTube Video Scraping
- **D-01:** New `YouTubeScraperService` in `org.ctc.sitegen` package — `@Slf4j @Service @RequiredArgsConstructor`.
- **D-02:** Method `String scrapeVideoId(String channelUrl, String fallbackVideoId)` — fetches channel page with Jsoup, extracts first `videoId` via regex `"videoId":"([a-zA-Z0-9_-]{11})"` from page source.
- **D-03:** Returns `fallbackVideoId` on any failure (IOException, no match, timeout). Never throws — always returns a usable video ID.
- **D-04:** Jsoup connection timeout: 10 seconds. User-Agent header to avoid bot blocking.
- **D-05:** Configuration in `SiteProperties`: `youtubeChannelUrl` (default: `https://www.youtube.com/@CommunityTeamCup`) and `youtubeVideoId` (fallback, default: empty string or a known trailer ID).

### Hero Video Layout
- **D-06:** Responsive YouTube iframe embed using 16:9 aspect ratio container. CSS class `.landing-hero`.
- **D-07:** Iframe attributes: `allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen`.
- **D-08:** Iframe src: `https://www.youtube.com/embed/{videoId}` — videoId from scraper.
- **D-09:** Hero section replaces the current standings-based hero. Title "COMMUNITY TEAM CUP" and subtitle remain above the video.

### Tile Navigation (5 Kacheln)
- **D-10:** 5 tiles in a responsive grid below the hero:

| Tile | Label | Target | Condition |
|------|-------|--------|-----------|
| Seasons | "Seasons" | `archive.html` | Always |
| Standings | "Standings" | `season/{active}/standings.html` | If active season exists |
| Drivers | "Drivers" | `drivers.html` | Always |
| Teams | "Teams" | `teams.html` | Always |
| Links | "Links" | `links.html` | Always |

- **D-11:** CSS classes: `.tile-grid` (3+2 centered layout), `.tile-card` (consistent with `.overview-card` styling).
- **D-12:** Each tile has a short description below the label (e.g., "Browse all seasons", "Current standings").
- **D-13:** Tiles without an active season target (Standings when no active season) are hidden or link to archive as fallback.

### Index Page Content Removal
- **D-14:** Remove standings table from index.html — no more `<table>` element.
- **D-15:** Remove last matchday section from index.html — no more `.match-grid` element.
- **D-16:** Remove all standings/matchday data preparation from `generateIndex()` in `SiteGeneratorService` — simplify the method significantly.

### Navigation Adjustment
- **D-17:** In `layout.html`: "Standings" nav link changes from `rootPath + '/index.html'` to `rootPath + '/season/' + activeSeasonSlug + '/standings.html'` (only if active season exists).
- **D-18:** Nav brand (logo + text) keeps linking to `rootPath + '/index.html'` — the landing page.
- **D-19:** `currentPage` for index is "home" (changed from "index") to differentiate from standings.

### Existing Test Adaptation
- **D-20:** Remove/adapt tests that assert standings content on index page (e.g., `givenActiveSeason_whenGenerate_thenIndexStandingsTeamNamesLinkToTeamProfiles`).
- **D-21:** Add new tests for: iframe presence, tile count, tile targets, no-standings-table, no-match-grid.
- **D-22:** `YouTubeScraperService` tests are pure unit tests (mock Jsoup response) — separate test class `YouTubeScraperServiceTest.java`.

### Claude's Discretion
- Exact tile descriptions/subtitles
- Hero section padding/spacing
- Whether to keep the season label in the hero or remove it
- CSS transition effects on tiles

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Current Index Page
- `src/main/resources/templates/site/index.html` — Current template to be rewritten.
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — `generateIndex()` method (line 102) to be simplified.

### Layout Navigation
- `src/main/resources/templates/site/layout.html` — Top nav links (lines 26-33). "Standings" link target changes.

### Configuration
- `src/main/java/org/ctc/sitegen/SiteProperties.java` — Add `youtubeChannelUrl` and `youtubeVideoId` fields.
- `src/main/resources/application.yml` — Add YouTube config under `ctc.site`.
- `src/main/resources/application-dev.yml` — Add YouTube config for dev.

### CSS
- `src/main/resources/static/site/css/style.css` — `.hero` section (line 137), add `.landing-hero`, `.tile-grid`, `.tile-card`.

### Tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Existing index tests to adapt/remove + new landing page tests.

### Jsoup (for scraping)
- Already a dependency in `pom.xml` — used for GT7 scraping and test HTML parsing.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `writeTemplate()` — handles rendering with relative paths
- `SiteProperties` (Phase 46) — already has `outputDir` and `links`. Add YouTube fields here.
- `slugify()` — URL generation
- `.overview-card` CSS (Phase 47) — visual pattern for tiles
- Jsoup — already in classpath for scraping

### Established Patterns
- `generateIndex()` creates a Thymeleaf Context, sets variables, calls `writeTemplate()`
- Templates use `th:replace="~{site/layout :: layout(title, ~{::section})}"` for shared layout
- Test-season filtering: `!s.getName().contains("Test")`

### Integration Points
- `SiteGeneratorService.generateIndex()` — major simplification (remove standings/matchday logic)
- `layout.html` nav links — change "Standings" target
- `SiteProperties` — add 2 new fields
- New `YouTubeScraperService` — injected into `SiteGeneratorService`

</code_context>

<specifics>
## Specific Ideas

- YouTube channel URL: `https://www.youtube.com/@CommunityTeamCup` (from user requirement)
- Hero video should be prominent but not auto-playing with sound (YouTube embed respects this by default)
- Tiles should feel like a "dashboard" entry point — clean, scannable
- The 3+2 tile layout centers the bottom row for visual balance

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 48-landing-page-redesign*
*Context gathered: 2026-04-17*
