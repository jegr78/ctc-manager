# Phase 49: E2E Site Validation - Context

**Gathered:** 2026-04-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Create a comprehensive E2E validation test class that validates the entire generated static site works end-to-end: all internal links resolve to existing files, navigation is consistent across all pages, no empty content, and all new features (landing tiles, links page, YouTube footer, overview pages) are verified.

</domain>

<decisions>
## Implementation Decisions

### Test Class Structure
- **D-01:** New test class `SiteGeneratorE2ETest.java` in `org.ctc.sitegen` package — separate from `SiteGeneratorServiceTest` (which has 990+ tests focused on individual features).
- **D-02:** Same `@SpringBootTest`, `@ActiveProfiles("dev")`, `@TempDir` pattern as existing tests.
- **D-03:** `@BeforeAll` (or `@BeforeEach`) sets up test data and calls `generate()` once. All tests share the same generated output — no per-test regeneration.
- **D-04:** Use `static` `@TempDir` with `@BeforeAll` for efficiency — generate once, validate many times.

### Internal Link Validation (E2E-01)
- **D-05:** Walk all `.html` files in the output directory with `Files.walk()`.
- **D-06:** Parse each file with JSoup, extract all `a[href]` elements.
- **D-07:** Filter to internal links only — skip `#` anchors, `http://`, `https://`, `javascript:`, `mailto:`.
- **D-08:** Resolve each relative href against the HTML file's parent directory using `Path.resolve()`.
- **D-09:** Assert `Files.exists()` for each resolved path. Collect all broken links and report them in a single assertion failure message.

### Navigation Consistency (E2E-02)
- **D-10:** Parse each generated page and assert `.nav` element exists (from layout.html).
- **D-11:** Assert `.footer` element exists on every page.
- **D-12:** Both checks run on ALL generated .html files, not just a sample.

### Content Validation (E2E-03)
- **D-13:** Assert `#main-content` element has at least one child element on every page — no empty main content.

### Landing Page Tiles (E2E-04)
- **D-14:** Parse `index.html`, find `.tile-card` link elements, resolve each href, assert target file exists.

### Links Page (E2E-05)
- **D-15:** Parse `links.html`, assert it contains `<a>` elements with the configured link URLs from `ctc.site.links`.

### YouTube Footer (E2E-06)
- **D-16:** Check multiple pages (index, archive, standings, teams, drivers) for `a[href*='youtube.com/@CommunityTeamCup']` in the footer.

### Overview Pages Season Filter (bonus)
- **D-17:** Assert `teams.html` and `drivers.html` have `<select id="season-filter">` element.

### Claude's Discretion
- Exact number of pages to sample for YouTube footer check (3-5 is sufficient)
- Whether to parallelize the link crawl or keep it simple single-threaded
- Helper method extraction for common JSoup operations

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Test Pattern
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Existing 990+ tests. Pattern for `@SpringBootTest`, `@TempDir`, JSoup parsing, `setOutputDir()`.

### Site Generator
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — `generate()` method that produces the full site output.
- `src/main/java/org/ctc/sitegen/SiteProperties.java` — Configuration including links and YouTube settings.

### Generated Pages (by phases 44-48)
- `index.html` — Landing page with YouTube hero + 5 tiles
- `archive.html` — Season archive listing
- `links.html` — Configurable links page
- `teams.html` — Teams overview with season filter
- `drivers.html` — Drivers overview with season filter
- `season/{slug}/standings.html` — Season standings
- `season/{slug}/driver-ranking.html` — Driver ranking
- `season/{slug}/matchday/*.html` — Matchday pages
- `season/{slug}/team/*.html` — Team profiles
- `season/{slug}/driver/*.html` — Driver profiles

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- JSoup for HTML parsing — already used extensively in `SiteGeneratorServiceTest`
- `@TempDir` pattern — proven for isolated output dirs
- `SiteProperties` injection — for accessing configured links in assertions
- `Files.walk()` — standard Java NIO for directory traversal

### Established Patterns
- Tests use `siteGeneratorService.setOutputDir(tempDir.toString())` for isolation
- JSoup: `Jsoup.parse(Files.readString(path))` → `doc.select("selector")`
- Given-When-Then test naming: `whenSiteGenerated_thenAllInternalLinksResolve()`

### Integration Points
- Single entry point: `siteGeneratorService.generate()` produces all output
- `SiteProperties.setLinks()` for configuring test links
- `SiteProperties.setOutputDir()` for output directory

</code_context>

<specifics>
## Specific Ideas

- The link crawler should collect ALL broken links before failing — not fail on the first one. This gives a complete picture of issues.
- Use `assertAll()` from JUnit 5 for grouped assertions where appropriate.
- The test class should be runnable independently and produce clear failure messages.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 49-e2e-site-validation*
*Context gathered: 2026-04-17*
