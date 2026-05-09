# Phase 40: Navigation & Structure - Research

**Researched:** 2026-04-16
**Domain:** Thymeleaf static site navigation — subnav, active state, breadcrumbs, matchday index
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Subnav placement & structure (CONT-05)**
- D-01: Subnav lives in `layout.html` as a shared component, rendered conditionally when season context is present. Single source of truth — no duplication across templates.
- D-02: Pill-style horizontal link bar, visually matching the existing top-nav design. Items: Standings, Matchdays, Driver Ranking, Playoff. Links point to the respective pages within `season/{slug}/`.
- D-03: The subnav requires template variables passed via `writeTemplate()`: season slug, season name, and current page identifier. Pre-computed in the service, not in Thymeleaf.

**Subnav scope**
- D-04: Claude's Discretion — whether the index page and archive page also show the subnav (depends on whether they have a season context).

**Active state (UX-02)**
- D-05: Active navigation item gets accent color (`#4fc3f7`) plus a subtle background (`rgba(79,195,247,0.1)`). Inactive items remain `text-dim` (#888). Consistent for both top-nav and subnav.
- D-06: Both top-nav AND subnav receive active state highlighting. Top-nav shows which section the user is in (e.g., "Standings" active on standings pages), subnav shows the current page within the season.
- D-07: Active state is determined by a `currentPage` template variable set in each `generate*()` method and passed through `writeTemplate()`. Layout template conditionally adds an `active` CSS class based on this variable.

**Breadcrumbs (UX-03)**
- D-08: Three-level hierarchy: `Home > {Season Name} > {Page Title}`. Compact, matching the flat site structure.
- D-09: Placed between the subnav and the content area (inside `layout.html`), rendered conditionally when breadcrumb data is present.
- D-10: All breadcrumb levels are clickable except the last (current page). Home links to `index.html`, Season links to `standings.html` of that season. Current page is plain text.
- D-11: Breadcrumb data (labels and URLs) is pre-computed in `SiteGeneratorService` and passed as template variables. Separator: `>` (chevron).

**Matchday index page (CONT-05)**
- D-12: Create a new `matchdays.html` page per season at `season/{slug}/matchdays.html`. Subnav "Matchdays" link target.
- D-13: Compact matchday list — each entry shows the matchday label as a clickable link to the individual matchday page, plus optional date if available.
- D-14: New `generateMatchdayIndex()` method in `SiteGeneratorService`, called within the per-season generation loop.

### Claude's Discretion
- Whether the index page (root) shows subnav for the active season (D-04)
- Exact CSS properties for the subnav (padding, gap, border, font-size) — should match top-nav proportions
- Breadcrumb CSS class naming and styling (font-size, color, separator rendering)
- Whether to add the matchday date from `Matchday.matchDate` or leave it as label-only if date is null
- Mobile responsive behavior for the subnav (stack vertically or horizontal scroll)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONT-05 | Season subnavigation shows links to standings, matchdays, driver ranking, playoff | Subnav in layout.html + new matchdays.html page + generateMatchdayIndex() method |
| UX-02 | Active navigation item is visually highlighted | `currentPage` template variable + `.nav-link-active` CSS class + accent color #4fc3f7 |
| UX-03 | Breadcrumbs on subpages (Home > Season > Page) | Breadcrumb block in layout.html + pre-computed breadcrumb map variables from service |
</phase_requirements>

---

## Summary

Phase 40 adds three navigational improvements to the static site: a season subnavigation bar (CONT-05), active state highlighting for nav links (UX-02), and breadcrumbs on subpages (UX-03). A new matchday index page is also introduced as the subnav's "Matchdays" target.

The implementation is entirely within the existing static site layer: `SiteGeneratorService.java`, `layout.html`, and `style.css`. No database changes, no new domain entities, no admin UI changes. The core mechanism is already in place: `writeTemplate()` already injects `rootPath`, `assetsPath`, and `activeSeasonSlug` into every page context. Extending it with `currentPage`, `seasonSlug`, and breadcrumb variables is a minimal, low-risk addition.

All new variables must be pre-computed in the service (per the established pattern and CLAUDE.md: "Keep Thymeleaf Templates Lean"). Thymeleaf renders conditionally with `th:if` — if a variable is absent/null, the block does not render. This means archive and index pages safely skip subnav/breadcrumbs without special-casing in the templates.

**Primary recommendation:** Extend `writeTemplate()` with optional season context parameters, add `currentPage` and breadcrumb variables per `generate*()` call, then add subnav + breadcrumb blocks to `layout.html`. Add CSS for active state and subnav styling. Create `matchdays.html` template and `generateMatchdayIndex()`.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Subnav rendering | Frontend Server (SSR / Thymeleaf) | — | Pure template concern; layout.html fragment handles it |
| Active state computation | Backend Service (SiteGeneratorService) | — | `currentPage` variable set in service, not template logic |
| Breadcrumb URL computation | Backend Service (SiteGeneratorService) | — | URL path logic belongs in service per CLAUDE.md conventions |
| Breadcrumb rendering | Frontend Server (SSR / Thymeleaf) | — | layout.html conditional block |
| Matchday index page | Backend Service + Frontend Server | — | New `generateMatchdayIndex()` + new `matchdays.html` template |
| Active state CSS | CDN / Static (style.css) | — | CSS class `.nav-link-active` defines the visual treatment |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Thymeleaf | 3.x (Spring Boot 4.x) | Server-side template rendering | Project's established UI layer; no frontend build tool |
| Spring Boot | 4.x | Application framework | Project standard |
| JUnit 5 | 5.x | Test framework | Project standard per CLAUDE.md |
| Jsoup | 1.x | HTML assertion in tests | Already used in SiteGeneratorServiceTest |

[VERIFIED: codebase grep — all libraries already in use]

### No New Dependencies Required

All work stays within the existing stack. No new libraries needed.

---

## Architecture Patterns

### System Architecture Diagram

```
generate() loop (SiteGeneratorService)
  |
  +-- generateStandings()     -- sets currentPage="standings",  breadcrumb=["Home","Season Name","Standings"]
  +-- generateDriverRanking() -- sets currentPage="driver-ranking", breadcrumb=...
  +-- generateMatchdays()     -- sets currentPage="matchdays",  breadcrumb=...   (each matchday page)
  +-- generateMatchdayIndex() -- sets currentPage="matchdays",  breadcrumb=...   (NEW: matchdays.html)
  +-- generateTeamProfiles()  -- sets currentPage=null (no subnav item), breadcrumb=...
  +-- generateDriverProfiles()-- sets currentPage=null (no subnav item), breadcrumb=...
  +-- generatePlayoffBracket()-- sets currentPage="playoff",   breadcrumb=...
  +-- generateArchive()       -- no season context, no subnav, no breadcrumb
  +-- generateIndex()         -- D-04: discretion (active season subnav or not)
        |
        v
  writeTemplate(templateName, ctx, outputFile, activeSeasonSlug)
     |-- ctx already has: rootPath, assetsPath, activeSeasonSlug
     |-- new additions:   currentPage, seasonSlug, seasonName, breadcrumbs (Map)
        |
        v
  layout.html
     |-- <nav> (top-nav, active class on nav link matching currentPage)
     |-- <nav class="subnav"> (NEW, th:if seasonSlug present)
     |      Standings | Matchdays | Driver Ranking | Playoff
     |      each link gets "active" class if currentPage matches
     |-- <nav class="breadcrumb"> (NEW, th:if breadcrumbs present)
     |      Home > Season Name > Current Page
     |-- <main>
            page-specific content fragment
```

### Recommended Project Structure

No structural changes — all files are within existing directories:

```
src/main/java/org/ctc/sitegen/
└── SiteGeneratorService.java    # extend writeTemplate(), add generateMatchdayIndex()

src/main/resources/templates/site/
├── layout.html                  # add subnav + breadcrumb blocks
└── matchdays.html               # NEW: matchday index template

src/main/resources/static/site/css/
└── style.css                    # add .subnav, .nav-link-active, .breadcrumb classes

src/test/java/org/ctc/sitegen/
└── SiteGeneratorServiceTest.java  # add tests for subnav, active state, breadcrumbs, matchday index
```

### Pattern 1: Passing Navigation Context via writeTemplate()

**What:** Extend the existing `writeTemplate()` to accept or derive additional navigation context variables.
**When to use:** When adding new template-wide variables that every page might use.

**Current signature:**
```java
// Source: codebase — SiteGeneratorService.java line 310-324
private void writeTemplate(String templateName, Context context, Path outputFile,
                            String activeSeasonSlug) throws IOException {
    // already sets: assetsPath, rootPath, activeSeasonSlug
}
```

**Recommended approach — add a NavigationContext record:**
```java
// [ASSUMED] pattern — fits the established codebase conventions
record NavigationContext(String currentPage, String seasonSlug, String seasonName,
                          Map<String, String> breadcrumbs) {
    static NavigationContext none() {
        return new NavigationContext(null, null, null, null);
    }
    static NavigationContext of(String page, Season season, String seasonSlug, String pageTitle, String pageUrl) {
        var crumbs = new java.util.LinkedHashMap<String, String>();
        crumbs.put("Home", ""); // relative root — caller fills in rootPath
        crumbs.put(season.getName(), "standings.html"); // relative to season dir
        crumbs.put(pageTitle, null); // null = current page, not clickable
        return new NavigationContext(page, seasonSlug, season.getName(), crumbs);
    }
}
```

The simpler alternative (no overload): just set variables directly in each `generate*()` method before calling `writeTemplate()`, since Context is mutable. This avoids a signature change to `writeTemplate()` and is equally valid.

### Pattern 2: Subnav in layout.html

**What:** Conditional subnav block rendered only when `seasonSlug` is present.

```html
<!-- Source: [ASSUMED] — based on existing layout.html nav structure -->
<nav class="subnav" th:if="${seasonSlug != null and !#strings.isEmpty(seasonSlug)}">
    <div class="subnav-inner">
        <a th:href="${rootPath + '/season/' + seasonSlug + '/standings.html'}"
           th:class="${currentPage == 'standings'} ? 'subnav-link active' : 'subnav-link'">Standings</a>
        <a th:href="${rootPath + '/season/' + seasonSlug + '/matchdays.html'}"
           th:class="${currentPage == 'matchdays'} ? 'subnav-link active' : 'subnav-link'">Matchdays</a>
        <a th:href="${rootPath + '/season/' + seasonSlug + '/driver-ranking.html'}"
           th:class="${currentPage == 'driver-ranking'} ? 'subnav-link active' : 'subnav-link'">Driver Ranking</a>
        <a th:href="${rootPath + '/season/' + seasonSlug + '/playoff.html'}"
           th:class="${currentPage == 'playoff'} ? 'subnav-link active' : 'subnav-link'">Playoff</a>
    </div>
</nav>
```

### Pattern 3: Top-nav Active State

The existing top-nav links also need active highlighting per D-06. The top-nav links are "Standings" (index), "Driver Ranking", "Archive". Map `currentPage` values to top-nav items:

| currentPage value | Top-nav active item |
|-------------------|---------------------|
| `standings` | Standings (index.html link) |
| `driver-ranking` | Driver Ranking |
| `archive` | Archive |
| `matchdays`, `playoff`, team/driver profile pages | none (or "Standings" as section) |

In layout.html, add `th:class` conditionals to the existing `.nav-links a` elements:
```html
<a th:href="${rootPath + '/index.html'}"
   th:class="${currentPage == 'standings' or currentPage == 'index'} ? 'nav-link-active' : ''">Standings</a>
```

### Pattern 4: Breadcrumbs in layout.html

```html
<!-- Source: [ASSUMED] — consistent with D-08 through D-11 -->
<nav class="breadcrumb" th:if="${breadcrumbItems != null and !breadcrumbItems.isEmpty()}">
    <a class="breadcrumb-link" th:href="${rootPath + '/index.html'}">Home</a>
    <span class="breadcrumb-sep"> > </span>
    <a class="breadcrumb-link" th:href="${rootPath + '/season/' + seasonSlug + '/standings.html'}"
       th:text="${seasonName}"></a>
    <span class="breadcrumb-sep"> > </span>
    <span class="breadcrumb-current" th:text="${breadcrumbCurrent}"></span>
</nav>
```

Since there are only 3 levels and the structure is always the same, using explicit variables (`seasonName`, `breadcrumbCurrent`) is simpler than a dynamic list. Pass `breadcrumbCurrent` (the page title string) from each `generate*()` method.

### Pattern 5: generateMatchdayIndex()

```java
// Source: [ASSUMED] — mirrors generateMatchdays() pattern
private void generateMatchdayIndex(Path outPath, Season season, String activeSeasonSlug,
                                    GenerationResult result) throws IOException {
    var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
    var ctx = new Context(Locale.ENGLISH);
    ctx.setVariable("season", season);
    ctx.setVariable("matchdays", matchdays);
    // pre-compute slug map for matchday links
    var matchdayLinkMap = new java.util.LinkedHashMap<java.util.UUID, String>();
    for (var md : matchdays) {
        matchdayLinkMap.put(md.getId(), "matchday/" + slugify(md.getLabel()) + ".html");
    }
    ctx.setVariable("matchdayLinkMap", matchdayLinkMap);
    ctx.setVariable("currentPage", "matchdays");
    ctx.setVariable("seasonSlug", slugify(season.getDisplayLabel()));
    ctx.setVariable("seasonName", season.getName());
    ctx.setVariable("breadcrumbCurrent", "Matchdays");

    var dir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel()));
    Files.createDirectories(dir);
    writeTemplate("site/matchdays", ctx, dir.resolve("matchdays.html"), activeSeasonSlug);
    result.incrementPages();
}
```

**Note on Matchday.matchDate:** CONTEXT D-13 mentions "optional date if available" from `Matchday.matchDate`. However, the `Matchday` entity has **no `matchDate` field** — verified by reading the entity class. The matchday index will show label-only. This is consistent with D-13's "optional" phrasing and requires no database change. [VERIFIED: codebase read]

### Anti-Patterns to Avoid

- **Computing URLs in Thymeleaf:** Never construct season slug or relative paths inside templates with SpEL. Always pre-compute in the service.
- **Hard-coding active state in templates:** The active check must use `${currentPage == 'standings'}` — a simple string comparison on a service-provided variable. Never compare against `request.URI` or similar (no HTTP context in static generation).
- **Duplicating subnav in each template:** Only `layout.html` contains the subnav. Individual templates never duplicate nav elements.
- **Playoff subnav link without guard:** `playoff.html` is only generated when a playoff exists. The subnav always shows the Playoff link — if no playoff page exists, clicking the link returns 404. This is acceptable per the phase scope. Do NOT add conditional hiding of the Playoff subnav item; that complexity is out of scope.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| String comparison in Thymeleaf | Custom tag/dialect | `th:class="${currentPage == 'x'} ? 'active' : ''"` | Standard Thymeleaf expression |
| URL construction | Manual string concat in template | Pre-computed map in service | CLAUDE.md: lean templates |
| HTML assertions in tests | Custom HTML parser | Jsoup (already in use) | Established test pattern |

---

## Common Pitfalls

### Pitfall 1: rootPath is empty string at root level

**What goes wrong:** On the index and archive pages, `rootPath` resolves to `"."` (not empty string). Navigation links computed as `${rootPath + '/index.html'}` yield `"./index.html"` at root but `"../../index.html"` from deep pages.
**Why it happens:** `writeTemplate()` uses `relativeRoot.toString()` which is empty at root; the service substitutes `"."`. Links like `${rootPath + '/season/...'}` yield `"./season/..."` at root and `"../../season/..."` from `season/{slug}/team/` — both valid relative paths.
**How to avoid:** Always use the `rootPath` variable as the prefix (never hardcode `"../../"` or `"/"`). Verify with the existing test `givenActiveSeason_whenGenerate_thenRootPagesHaveNoAbsolutePaths()`.
**Warning signs:** If a generated link contains `/index.html` or `//`, or if a deep page shows a relative path starting with `./`.

### Pitfall 2: seasonSlug variable unavailable on non-season pages

**What goes wrong:** Archive and index pages call `writeTemplate()` without season context. If `seasonSlug` is not set in the context, Thymeleaf throws an exception when the subnav block tries to evaluate `${seasonSlug}`.
**Why it happens:** Thymeleaf variables are null-safe by default with `th:if` — but only if the variable is set to null, not if it is absent entirely.
**How to avoid:** Always set `ctx.setVariable("seasonSlug", null)` (or empty string) in `generateIndex()` and `generateArchive()`. The `th:if="${seasonSlug != null}"` guard then correctly suppresses the subnav.
**Warning signs:** `TemplateInputException: Could not parse as expression` at generation time.

### Pitfall 3: Matchday pages are in a subdirectory — relative path depth differs

**What goes wrong:** Subnav links on matchday pages (at `season/{slug}/matchday/{label}.html`) go one level deeper than standings (at `season/{slug}/standings.html`). Using the same `rootPath` prefix for season-level pages works, but subnav links to sibling pages need `../standings.html`, not `rootPath + '/season/{slug}/standings.html'` (which would compute as `../../../season/{slug}/standings.html`).
**Why it happens:** `rootPath` is the relative path back to the site root. From `matchday/`, `rootPath` is `"../../"`. Then `rootPath + '/season/' + slug + '/standings.html'` becomes `"../..//season/{slug}/standings.html"` — double slash and wrong depth... Actually `writeTemplate()` already handles this: `rootPath` from `matchday/{file}.html` is `../../` and `rootPath + '/season/' + slug + '/standings.html'` is `../../season/{slug}/standings.html` — correct!
**Actual risk:** The subnav links in `layout.html` use `rootPath` as prefix, which is already correct from any depth. However verify that the `seasonSlug` used in subnav links is the **current season's** slug (passed as a variable), not `activeSeasonSlug` (which is always the active season). For non-active season pages, these differ.
**How to avoid:** Use a dedicated `seasonSlug` variable (set per `generate*()` call for that season) in subnav links. Never use `activeSeasonSlug` in subnav links.
**Warning signs:** Subnav links on archived season pages pointing to the active season's pages.

### Pitfall 4: currentPage must be set on ALL season page generators

**What goes wrong:** If `generateTeamProfiles()` or `generateDriverProfiles()` do not set `currentPage`, the top-nav active state shows no active item. This is acceptable (no subnav item for these). But if `seasonSlug` is set without `currentPage`, the subnav renders but nothing is highlighted.
**Why it happens:** Team and driver profiles live inside a season directory but don't correspond to a subnav item.
**How to avoid:** Set `currentPage = null` (or a value like `"team"` / `"driver"`) for profile pages. The subnav renders (showing the season context) but no pill is highlighted. This is correct UX — the user sees which season they're in.

### Pitfall 5: Breadcrumb separator as HTML entity vs. literal character

**What goes wrong:** If breadcrumb separator is rendered via `th:text`, the `>` symbol renders correctly as a `>` in the browser but appears as `&gt;` in Jsoup assertions.
**Why it happens:** `th:text` HTML-escapes content. Using `th:utext` or CSS `::after { content: " > "}` avoids the issue.
**How to avoid:** Use a CSS pseudo-element for the separator, or use `th:utext` with an HTML entity, or use a literal `>` character in a `<span>` (not via `th:text`). Best: use a `<span class="breadcrumb-sep" aria-hidden="true"> > </span>` as a literal span with literal text — this does not go through `th:text` and renders correctly.

---

## Code Examples

### Verified Pattern: Context variable injection (from existing code)

```java
// Source: SiteGeneratorService.java line 310-324 [VERIFIED: codebase read]
private void writeTemplate(String templateName, Context context, Path outputFile,
                            String activeSeasonSlug) throws IOException {
    Path outRoot = Path.of(outputDir);
    Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
    Path relativeRoot = outputFile.getParent().relativize(outRoot);
    context.setVariable("assetsPath", relativeAssets.toString().replace('\\', '/'));
    String rootStr = relativeRoot.toString().replace('\\', '/');
    context.setVariable("rootPath", rootStr.isEmpty() ? "." : rootStr);
    context.setVariable("activeSeasonSlug", activeSeasonSlug != null ? activeSeasonSlug : "");
    // NEW variables to add:
    // context.setVariable("seasonSlug", ...)       — current page's season slug (null for archive/index)
    // context.setVariable("seasonName", ...)       — current season's name for breadcrumb
    // context.setVariable("currentPage", ...)      — identifier string, e.g. "standings"
    // context.setVariable("breadcrumbCurrent", ...) — page title for breadcrumb last item
}
```

### Verified Pattern: Thymeleaf conditional class (established in project)

```html
<!-- Source: archive.html line 26 [VERIFIED: codebase read] -->
<span th:if="${entry.season.active}" class="text-accent">Active</span>
<span th:unless="${entry.season.active}" class="text-dim">Completed</span>

<!-- For active nav state, use th:class instead: -->
<a th:class="${currentPage == 'standings'} ? 'subnav-link active' : 'subnav-link'">Standings</a>
```

### Verified Pattern: Jsoup-based HTML test assertion

```java
// Source: SiteGeneratorServiceTest.java lines 235-245 [VERIFIED: codebase read]
var html = Files.readString(seasonDir().resolve("standings.html"));
var doc = Jsoup.parse(html);
var rows = doc.select("tbody tr");
assertFalse(rows.isEmpty(), "Standings table should have rows");

// New test pattern for subnav:
var subnav = doc.select(".subnav");
assertFalse(subnav.isEmpty(), "Season pages should have subnav");
var activeLinks = doc.select(".subnav-link.active");
assertEquals(1, activeLinks.size(), "Exactly one subnav item should be active");
assertEquals("Standings", activeLinks.first().text());
```

---

## CSS Design Reference

### Existing CSS variables (verified from style.css)

```css
/* Source: style.css lines 13-24 [VERIFIED: codebase read] */
--bg-card: #111;
--border: #2a2a2a;
--text-dim: #888;
--accent: #4fc3f7;
--radius-sm: 6px;
```

### Existing nav link pattern (subnav should mirror this)

```css
/* Source: style.css lines 69-80 [VERIFIED: codebase read] */
.nav-links a {
    color: var(--text-dim);
    text-decoration: none;
    padding: 8px 14px;
    border-radius: 4px;
    font-size: 13px;
    text-transform: uppercase;
    letter-spacing: 1px;
    transition: all 0.2s;
}
.nav-links a:hover { color: var(--white); background: rgba(255,255,255,0.05); }
```

### New CSS to add (Claude's Discretion for exact values)

```css
/* Active nav state — per D-05 [ASSUMED: values from D-05 spec] */
.nav-link-active,
.subnav-link.active {
    color: #4fc3f7 !important;          /* var(--accent) */
    background: rgba(79,195,247,0.1);
}

/* Subnav bar — matches top-nav visual weight */
.subnav {
    background: var(--bg-card);
    border-bottom: 1px solid var(--border);
    padding: 0 32px;
}
.subnav-inner {
    max-width: 1100px;
    margin: 0 auto;
    display: flex;
    gap: 4px;
    height: 44px;            /* slightly smaller than top-nav 60px */
    align-items: center;
}
.subnav-link {
    color: var(--text-dim);
    text-decoration: none;
    padding: 6px 12px;
    border-radius: 4px;
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 1px;
    transition: all 0.2s;
}
.subnav-link:hover { color: var(--white); background: rgba(255,255,255,0.05); }

/* Breadcrumb */
.breadcrumb {
    padding: 8px 32px;
    font-size: 12px;
    color: var(--text-muted);
    max-width: 1100px;
    margin: 0 auto;
}
.breadcrumb-link {
    color: var(--text-dim);
    text-decoration: none;
}
.breadcrumb-link:hover { color: var(--accent); }
.breadcrumb-sep { margin: 0 6px; color: var(--text-muted); }
.breadcrumb-current { color: var(--text); }

/* Mobile: subnav scrolls horizontally */
@media (max-width: 768px) {
    .subnav { padding: 0 16px; overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .subnav-inner { height: auto; padding: 8px 0; flex-wrap: nowrap; }
    .breadcrumb { padding: 8px 16px; }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| No subnav | Season-scoped pill-nav in layout.html | Phase 40 | Users can navigate within a season |
| No active state | Active class computed from `currentPage` var | Phase 40 | Visual orientation at all times |
| No breadcrumbs | Breadcrumb block in layout.html | Phase 40 | Orientation on deep pages |
| No matchday index | `matchdays.html` per season | Phase 40 | Subnav has a valid target for "Matchdays" |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Setting `ctx.setVariable("seasonSlug", null)` for archive/index prevents Thymeleaf exception with `th:if` guard | Common Pitfalls #2 | Minor — easy fix if null vs. absent behaves differently; test catches it |
| A2 | Subnav CSS dimensions (height 44px, font-size 12px, gap 4px) match visual proportions | CSS Design Reference | Low — pure visual; can be adjusted without logic changes |
| A3 | `Matchday.matchDate` does not exist (no date field) — matchday index shows label-only | Pattern 5 | Verified by reading entity class — field genuinely absent; no risk |
| A4 | Playoff subnav link shows always, even when no playoff page was generated for the season | Anti-Patterns | Low — acceptable 404 behavior; if unacceptable, need `hasPlayoff` flag per season |
| A5 | Index page (root) does NOT get subnav (D-04: discretion) — recommended to omit for simplicity | Architecture | Low — if user later wants it, trivial to add |

**A3 is verified, not assumed.** `Matchday` entity has: `id`, `season`, `label`, `sortIndex`, `matches`, `races` — no date field. [VERIFIED: codebase read of Matchday.java]

---

## Open Questions

1. **Should index page show subnav? (D-04)**
   - What we know: Index already shows the active season's standings. Adding subnav would give users navigation to other season pages.
   - What's unclear: Whether the index is the "home" and should feel global (no subnav), or is effectively the active season's standings page (should have subnav).
   - Recommendation: **Omit subnav on index**. The index hero section clearly shows the season. Top-nav "Standings" link points to index — if index had subnav, clicking "Standings" in subnav would appear to navigate to the same page. Cleaner to treat index as the global home page.

2. **Top-nav active state: which items map to which pages?**
   - What we know: Top-nav has 3 links: Standings (→ index.html), Driver Ranking (→ active season), Archive.
   - What's unclear: On a non-active season's standings page, should "Standings" in top-nav be active?
   - Recommendation: Only highlight a top-nav item if the `currentPage` maps exactly to one of the 3 top-nav destinations. Season-context pages (standings for non-active season, matchday, team, driver) show no top-nav active item. This is clean and avoids ambiguity.

---

## Environment Availability

Step 2.6: SKIPPED — Phase is code/config-only changes within the existing project. No external tools, services, runtimes, or CLI utilities beyond the existing Java/Maven stack.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test |
| Config file | `pom.xml` (Surefire + Failsafe plugins) |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONT-05 | Season pages have subnav with 4 links | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenStandingsHasSubnav` | Wave 0 |
| CONT-05 | matchdays.html generated per season | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenCreatesMatchdayIndexPage` | Wave 0 |
| CONT-05 | Subnav Matchdays link points to matchdays.html | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenSubnavMatchdaysLinkCorrect` | Wave 0 |
| UX-02 | Active subnav item has .active class | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenStandingsPage_whenGenerate_thenStandingsNavItemActive` | Wave 0 |
| UX-02 | Only one subnav item active per page | Integration (Jsoup) | (part of active-state test above) | Wave 0 |
| UX-03 | Season subpages have breadcrumb block | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenStandingsHasBreadcrumb` | Wave 0 |
| UX-03 | Breadcrumb last item is plain text (not link) | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenBreadcrumbCurrentNotLink` | Wave 0 |
| UX-03 | Archive page has no breadcrumb | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenArchivePage_whenGenerate_thenNoBreadcrumb` | Wave 0 |

All tests go into the existing `SiteGeneratorServiceTest.java`. No new test files needed.

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` green before `/gsd-verify-work`

### Wave 0 Gaps

All new tests are Wave 0 items (TDD: write tests first). The test infrastructure already exists — `SiteGeneratorServiceTest.java` is the target file for all new tests.

- [ ] Test: `givenSeason_whenGenerate_thenStandingsHasSubnav` — covers CONT-05
- [ ] Test: `givenSeason_whenGenerate_thenCreatesMatchdayIndexPage` — covers CONT-05
- [ ] Test: `givenSeason_whenGenerate_thenSubnavMatchdaysLinkCorrect` — covers CONT-05
- [ ] Test: `givenStandingsPage_whenGenerate_thenStandingsNavItemActive` — covers UX-02
- [ ] Test: `givenSeason_whenGenerate_thenStandingsHasBreadcrumb` — covers UX-03
- [ ] Test: `givenSeason_whenGenerate_thenBreadcrumbCurrentNotLink` — covers UX-03
- [ ] Test: `givenArchivePage_whenGenerate_thenNoBreadcrumb` — covers UX-03

---

## Security Domain

No security concerns for this phase. Changes are confined to:
- Static HTML generation (no user input, no HTTP request processing)
- Read-only template rendering
- No auth changes, no new endpoints, no user data exposure

ASVS categories V2, V3, V4 are not applicable. V5 (input validation) not applicable — no form inputs. V6 (cryptography) not applicable.

---

## Sources

### Primary (HIGH confidence)
- `SiteGeneratorService.java` — verified all existing generate*() methods, writeTemplate() signature, variable injection pattern [VERIFIED: codebase read]
- `layout.html` — verified existing nav structure, Thymeleaf fragment syntax, activeSeasonSlug usage [VERIFIED: codebase read]
- `style.css` — verified CSS variables, .nav-links styling, responsive breakpoints [VERIFIED: codebase read]
- `Matchday.java` — verified fields; `matchDate` does NOT exist [VERIFIED: codebase read]
- `SiteGeneratorServiceTest.java` — verified Jsoup assertion pattern, @TempDir usage, test naming conventions [VERIFIED: codebase read]
- `40-CONTEXT.md` — all locked decisions D-01 through D-14 [VERIFIED: file read]

### Secondary (MEDIUM confidence)
- Thymeleaf `th:class` with conditional expression — standard Thymeleaf 3.x feature, consistent with patterns used in admin templates elsewhere in this project [ASSUMED: training knowledge, consistent with existing usage]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in use, no new dependencies
- Architecture: HIGH — implementation pattern is identical to existing generate*() methods; all extension points are identified
- CSS design: MEDIUM — exact pixel values are Claude's discretion; functional correctness is high, visual polish is approximate
- Pitfalls: HIGH — derived from deep reading of actual code paths

**Research date:** 2026-04-16
**Valid until:** 2026-05-16 (stable codebase; static site pattern will not change within v1.6)
