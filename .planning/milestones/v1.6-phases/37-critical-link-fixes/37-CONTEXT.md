# Phase 37: Critical Link Fixes - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix all broken navigation links and asset references on the static site so that every page is reachable and all images display correctly. This phase addresses four specific bugs: archive slug mismatch (LINK-01), driver ranking nav 404 (LINK-02), absolute paths on root-level pages (LINK-03), and team logo resolution (LINK-04). No new pages, no new features — pure link/path correctness.

</domain>

<decisions>
## Implementation Decisions

### Slug consistency (LINK-01)
- **D-01:** Pass pre-computed season slug from `SiteGeneratorService` to templates as a context variable (e.g., `seasonSlug`), instead of having `archive.html` re-slugify `season.name` with Thymeleaf `#strings`. Single source of truth: the `slugify()` method in the service.
- **D-02:** The archive template link pattern becomes `'season/' + ${seasonSlug} + '/standings.html'` — no Thymeleaf string manipulation in the template.

### Driver Ranking navigation (LINK-02)
- **D-03:** Pass the active season's slug to the layout template as a context variable (e.g., `activeSeasonSlug`). The nav "Driver Ranking" link becomes `rootPath + '/season/' + activeSeasonSlug + '/driver-ranking.html'` instead of the current root-level `rootPath + '/driver-ranking.html'`.
- **D-04:** All pages must receive `activeSeasonSlug` in their template context. This requires `writeTemplate()` or each `generate*()` method to set this variable. The active season is already loaded in `generate()`.

### Root path fix (LINK-03)
- **D-05:** In `writeTemplate()`, when `relativeRoot.toString()` is empty (root-level files), default `rootPath` to `"."` — standard relative path convention. This makes root-level links `"./index.html"` instead of `"/index.html"`.

### Logo resolution (LINK-04)
- **D-06:** During site generation, copy team logo files from the upload directory to the static site assets directory (e.g., `assets/img/logos/`). Rewrite `team.logoUrl` in the template context to a relative path within the static site (e.g., `assetsPath + '/img/logos/' + filename`).
- **D-07:** If a team has no logo (null `logoUrl`), skip the copy — the existing `th:if="${team.logoUrl}"` guard in the template already handles this.
- **D-08:** Logo files that don't exist on disk (e.g., stale URLs) should be silently skipped with a warning log, not cause generation failure.

### Claude's Discretion
- Exact error handling for missing logo files (warn vs. debug log level)
- Whether to also fix the Standings nav link to point to active season (currently points to root index.html which shows standings — may already work correctly)
- Internal refactoring of `writeTemplate()` to reduce repetition in setting context variables

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Static site generator
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Core generation logic, `slugify()` method, `writeTemplate()` path calculation, all `generate*()` methods
- `src/main/java/org/ctc/sitegen/SiteGeneratorController.java` — Trigger endpoint for generation

### Site templates
- `src/main/resources/templates/site/layout.html` — Shared nav/footer with the broken Driver Ranking link and rootPath usage
- `src/main/resources/templates/site/archive.html` — Archive page with the broken season slug link (LINK-01) and inline styles (noted for Phase 41)
- `src/main/resources/templates/site/team-profile.html` — Team profile with `team.logoUrl` usage (LINK-04)
- `src/main/resources/templates/site/index.html` — Index page (root-level, affected by LINK-03)

### Domain model
- `src/main/java/org/ctc/domain/model/Season.java` — `getDisplayLabel()` returns `"year | #number | name"`, used by service slugify

### Existing tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Integration tests with Jsoup HTML parsing, uses `@TempDir` for output

### Configuration
- `application-dev.yml` / `application.yml` — `ctc.site.output-dir` and `app.upload-dir` paths

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SiteGeneratorService.slugify()` — Existing slugification with umlaut handling; single source of truth for directory names
- `SiteGeneratorService.writeTemplate()` — Central method that sets `assetsPath` and `rootPath`; ideal place to also set `activeSeasonSlug`
- `SiteGeneratorServiceTest` — Comprehensive integration test with Jsoup parsing; test pattern to follow for new link-correctness assertions
- `RaceView` record — Encapsulates race display data; pattern for how the service pre-computes display values

### Established Patterns
- Template variables are set in the service, not computed in Thymeleaf — `assetsPath` and `rootPath` are already computed in Java
- `@TempDir` for test isolation of generated output
- Jsoup for HTML assertion in tests (already a dependency)
- `@Value("${ctc.site.output-dir}")` for configurable output path

### Integration Points
- `writeTemplate()` is the single entry point for all template rendering — changes here propagate to all pages
- `generate()` loads `activeSeason` once at the top — can pass slug down to all methods
- `team.logoUrl` comes from the `Team` entity — need to check if this is a relative file path or an external URL
- `app.upload-dir` config determines where uploaded logos live on disk

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

*Phase: 37-critical-link-fixes*
*Context gathered: 2026-04-16*
