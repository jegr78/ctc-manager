# Phase 46: Configurable Links Page - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Create a new `links.html` static page that displays a list of external links configured via Spring application properties (`ctc.site.links`). This introduces `@ConfigurationProperties` to the project for the first time and adds a new page type to the site generator.

</domain>

<decisions>
## Implementation Decisions

### Configuration Pattern
- **D-01:** Create `SiteProperties` class with `@ConfigurationProperties(prefix = "ctc.site")`. This is the first use of `@ConfigurationProperties` in the project — all prior config uses `@Value`.
- **D-02:** `SiteProperties` contains `private String outputDir` (migrated from `@Value` on `SiteGeneratorService`) and `private List<LinkEntry> links = List.of()`. Defaults to empty list.
- **D-03:** `LinkEntry` is a nested static class (not record — Spring Boot config binding needs setters): `public static class LinkEntry { private String name; private String url; /* getters/setters */ }`.
- **D-04:** Register via `@EnableConfigurationProperties(SiteProperties.class)` on `SiteGeneratorService` or a dedicated `@Configuration` class.
- **D-05:** Migrate `@Value("${ctc.site.output-dir}")` and `@Value("${app.upload-dir}")` from `SiteGeneratorService` to inject `SiteProperties` instead. Keep `@Value` for `app.upload-dir` since it's a different prefix.

### Links Page Layout
- **D-06:** Card-based layout consistent with existing match-card styling — dark background, border, hover effect, accent color links.
- **D-07:** Each link renders as a card with the link name as title and URL as clickable element. External links open in new tab (`target="_blank" rel="noopener"`).
- **D-08:** Page title: "Links". Uses shared layout (nav, footer, breadcrumbs).
- **D-09:** Empty state: If no links configured, show an informational message ("No links configured.") — page is still generated.

### Navigation Integration
- **D-10:** links.html is NOT added to the top navigation bar. It will be reachable via the landing page tile (Phase 48). This keeps the nav focused.
- **D-11:** currentPage is set to "links" for potential future nav integration.

### Default Configuration
- **D-12:** `application.yml` (prod/default): YouTube channel as single default link.
- **D-13:** `application-dev.yml`: Same YouTube link for dev consistency.

### Generation Integration
- **D-14:** Add `generateLinks(Path, ...)` private method in `SiteGeneratorService`, called from `generate()` after `generateArchive()`.
- **D-15:** links.html is generated in the output root directory (same level as index.html, archive.html).

### Claude's Discretion
- CSS class naming for link cards (e.g., `.link-card`, `.link-grid`)
- Exact breadcrumb text for links page
- Whether to show URL below link name in the card

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Site Generator Service
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Main service. `generate()` method is the entry point. New `generateLinks()` method goes here. Currently uses `@Value("${ctc.site.output-dir}")` which will be migrated to `SiteProperties`.

### Configuration
- `src/main/resources/application.yml` — Add `ctc.site.links` list under existing `ctc.site` section (line 40-42).
- `src/main/resources/application-dev.yml` — Add `ctc.site.links` under existing `ctc.site` section (line 36-37).

### Templates
- `src/main/resources/templates/site/archive.html` — Closest existing analog for a simple listing page. Pattern to follow for links.html.
- `src/main/resources/templates/site/layout.html` — Shared layout with `th:replace` pattern.

### CSS
- `src/main/resources/static/site/css/style.css` — `.match-card` pattern (line 249) as visual reference for link cards.

### Tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — All existing tests. New tests follow same `@SpringBootTest` + `@TempDir` + JSoup pattern.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `writeTemplate()` method in `SiteGeneratorService` — handles all template rendering with relative path computation. Reuse for links page.
- `archive.html` template — simplest listing page, good pattern for links.html structure.
- `.match-card` CSS — card styling pattern to adapt for link cards.
- `GenerationResult` — already tracks pages and errors. `generateLinks()` calls `result.incrementPages()`.

### Established Patterns
- All `generate*()` methods follow: create Context, set variables, call `writeTemplate()`, increment result.
- Templates use `th:replace="~{site/layout :: layout(title, ~{::section})}"` for shared layout.
- Navigation context: `currentPage`, `seasonSlug`, `seasonName`, `breadcrumbCurrent` variables.

### Integration Points
- `SiteGeneratorService.generate()` — call `generateLinks()` after `generateArchive()` (line ~89).
- `SiteProperties` injection replaces `@Value("${ctc.site.output-dir}")` on the service.
- YAML config files — add `links` list under existing `ctc.site` prefix.

</code_context>

<specifics>
## Specific Ideas

- YAML format for links configuration:
  ```yaml
  ctc:
    site:
      links:
        - name: "YouTube"
          url: "https://www.youtube.com/@CommunityTeamCup"
  ```
- Link cards should have subtle hover animation consistent with existing `.match-card:hover` pattern

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 46-configurable-links-page*
*Context gathered: 2026-04-16*
