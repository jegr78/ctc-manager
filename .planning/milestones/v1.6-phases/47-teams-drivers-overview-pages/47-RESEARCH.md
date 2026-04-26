# Phase 47: Teams & Drivers Overview Pages - Research

**Researched:** 2026-04-16
**Domain:** Static site generation — cross-season overview pages with client-side filtering
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Teams Overview Data**
- D-01: Show parent teams only (filter out sub-teams via `team.isSubTeam()`).
- D-02: Each team card: team short name (link), team logo (if available via `copyLogoToAssets()`), season participation tags.
- D-03: Team name links to most recent season's team profile page (`season/{latest-slug}/team/{team-slug}.html`).
- D-04: Data source: Iterate `seasonTeamRepository.findBySeasonId()` per production season → `Map<Team, Set<Season>>`. Filter sub-teams and test seasons.

**Drivers Overview Data**
- D-05: Each driver card: PSN ID (link), team name(s), season participation tags.
- D-06: Driver name links to most recent season's driver profile page.
- D-07: Data source: Iterate `seasonDriverRepository.findBySeasonId()` per production season → `Map<Driver, List<SeasonDriverInfo>>`.
- D-08: Most recent season = primary team display. Multiple seasons = all teams as tags.

**Client-Side Season Filter**
- D-09: `data-seasons="slug-1 slug-2 ..."` attribute on each card.
- D-10: `<select>` dropdown: "All Seasons" (default) + each production season by display label.
- D-11: Vanilla JS on select change: toggle `display: none` for cards not matching slug.
- D-12: JavaScript inline in each template (no separate .js file).

**Layout & Styling**
- D-13: `.overview-grid`, `.overview-card` CSS classes. 2-col desktop, 1-col mobile.
- D-14: Card style consistent with `.link-card` (dark background, border, hover accent).
- D-15: `.season-tag` CSS class: pills with muted styling.
- D-16: Team logos via `copyLogoToAssets()`, same as team profiles.

**Page Integration**
- D-17: Output root level (same as `index.html`, `archive.html`, `links.html`).
- D-18: Shared layout (nav, footer, breadcrumbs). `currentPage` = "teams" / "drivers".
- D-19: Not in top navigation — reachable via landing page tiles (Phase 48).
- D-20: `generateTeamsOverview()` and `generateDriversOverview()` in `SiteGeneratorService`, called after `generateLinks()`.

**Records for Template Data**
- D-21:
  - `record TeamOverviewEntry(String shortName, String teamSlug, String logoRelPath, String profileUrl, List<String> seasonSlugs, List<String> seasonLabels)`
  - `record DriverOverviewEntry(String psnId, String driverSlug, String teamName, String profileUrl, List<String> seasonSlugs, List<String> seasonLabels)`

### Claude's Discretion
- Exact filter JS implementation details
- Whether to sort teams/drivers alphabetically or by most-recent-season-first
- Breadcrumb text for overview pages
- Additional CSS refinements for season tags

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| OVER-01 | `teams.html` exists in output root listing all teams across all seasons | `generateTeamsOverview()` writes to `outPath.resolve("teams.html")`; reuses existing `generate()` orchestration |
| OVER-02 | `drivers.html` exists in output root listing all drivers across all seasons | `generateDriversOverview()` writes to `outPath.resolve("drivers.html")`; reuses existing pattern |
| OVER-03 | Both pages filterable by season (client-side JS, static site) | Inline `<script>` block + `data-seasons` attribute; ~15 lines vanilla JS |
| OVER-04 | Teams overview shows team name, logo, seasons participated | `TeamOverviewEntry` record; `copyLogoToAssets()` already handles logo path computation |
| OVER-05 | Drivers overview shows PSN ID, team(s), seasons participated | `DriverOverviewEntry` record; `SeasonDriver.getTeam().getShortName()` provides team name |
| OVER-06 | Team/driver names link to season-specific profile pages | URL pattern `season/{slug}/team/{teamSlug}.html` — `slugify()` already exists |
</phase_requirements>

---

## Summary

Phase 47 adds two cross-season overview pages (`teams.html`, `drivers.html`) to the static site output. Both aggregate data across all production seasons to build a single cross-season view, then provide a client-side JavaScript dropdown for per-season filtering.

The implementation follows the exact same pattern as existing `generate*()` methods in `SiteGeneratorService`: build a Thymeleaf `Context`, populate it with view records, call `writeTemplate()`. All infrastructure (output directory, asset copying, relative path computation, `slugify()`, shared layout) already exists and is battle-tested.

The only net-new concerns are: (1) injecting `SeasonTeamRepository` — currently absent from the service; (2) the cross-season aggregation logic using `LinkedHashMap` to preserve ordering; (3) two new Thymeleaf templates; (4) new CSS classes (`.overview-grid`, `.overview-card`, `.season-tag`); and (5) ~15 lines of inline JS per template.

**Primary recommendation:** Follow the `generateLinks()` method as the closest structural template. Add the two new methods immediately after the `generateLinks()` call in `generate()`. Inject `SeasonTeamRepository` as a new `final` field.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Cross-season data aggregation | Backend (SiteGeneratorService) | — | Data is aggregated at generation time; static HTML output |
| Season filtering UI | Browser (inline JS) | — | Static site — no server request on filter change |
| Template rendering | Frontend Server (Thymeleaf SSG) | — | `writeTemplate()` produces static HTML at generation time |
| Asset/logo copying | Backend (SiteGeneratorService) | — | `copyLogoToAssets()` already handles this |
| CSS layout/styling | Static (style.css) | — | New CSS classes alongside existing `.link-card` pattern |

---

## Standard Stack

### Core (all already in project)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Thymeleaf | Spring Boot 4.x managed | HTML template rendering | Project-wide SSR/SSG template engine [VERIFIED: pom.xml] |
| Spring Data JPA | Spring Boot 4.x managed | Repository queries | Used by all existing `generate*()` methods [VERIFIED: codebase] |
| Jsoup | Spring Boot managed | HTML parsing in tests | Used in all existing `SiteGeneratorServiceTest` assertions [VERIFIED: codebase] |
| JUnit 5 + `@SpringBootTest` | Spring Boot 4.x managed | Integration tests | Established pattern in `SiteGeneratorServiceTest` [VERIFIED: codebase] |

### No new dependencies needed

All required libraries are already on the classpath. No `npm install` or new Maven coordinates required.

---

## Architecture Patterns

### System Architecture Diagram

```
productionSeasons (filtered: !name.contains("Test"))
        |
        v
[generateTeamsOverview()]               [generateDriversOverview()]
        |                                         |
seasonTeamRepository.findBySeasonId()    seasonDriverRepository.findBySeasonId()
        |                                         |
  filter: !team.isSubTeam()              group by driver.id
        |                                         |
Map<Team, LinkedHashSet<Season>>         Map<Driver, List<(Season, Team)>>
        |                                         |
  sort (alphabetical or recent-first)    sort (alphabetical or recent-first)
        |                                         |
 determine "most recent season"          determine "most recent season"
        |                                         |
 copyLogoToAssets() for each team        N/A
        |                                         |
 build List<TeamOverviewEntry>           build List<DriverOverviewEntry>
        |                                         |
 Thymeleaf Context + writeTemplate()     Thymeleaf Context + writeTemplate()
        |                                         |
   docs/site/teams.html               docs/site/drivers.html
```

### Recommended Project Structure

No new directories needed. Changes are:

```
src/main/java/org/ctc/sitegen/
├── SiteGeneratorService.java    # add SeasonTeamRepository field + 2 new methods + 2 new records
src/main/resources/
├── templates/site/
│   ├── teams.html               # new template
│   └── drivers.html             # new template
├── static/site/css/
│   └── style.css                # new .overview-grid, .overview-card, .season-tag classes
src/test/java/org/ctc/sitegen/
└── SiteGeneratorServiceTest.java # new tests following existing JSoup pattern
```

### Pattern 1: generate*() Method Structure

All page generation methods in `SiteGeneratorService` follow this exact pattern:

```java
// Source: SiteGeneratorService.java (verified)
private void generateLinks(Path outPath, List<SiteProperties.LinkEntry> links,
                            String activeSeasonSlug, String activeSeasonName,
                            GenerationResult result) throws IOException {
    var ctx = new Context(Locale.ENGLISH);
    ctx.setVariable("links", links);
    ctx.setVariable("currentPage", "links");
    ctx.setVariable("seasonSlug", null);       // null = no subnav
    ctx.setVariable("seasonName", null);
    ctx.setVariable("breadcrumbCurrent", "Links");
    writeTemplate("site/links", ctx, outPath.resolve("links.html"), activeSeasonSlug, activeSeasonName);
    result.incrementPages();
}
```

For the two new methods, follow this same structure:
- `currentPage` = `"teams"` / `"drivers"`
- `seasonSlug` = `null` (root-level pages, no subnav)
- `seasonName` = `null`
- `breadcrumbCurrent` = `"Teams"` / `"Drivers"`

### Pattern 2: Cross-Season Aggregation

Since `SeasonTeamRepository` is not yet injected, it must be added as a new `final` field alongside the existing repository fields:

```java
// Source: SiteGeneratorService.java — existing field injection pattern (verified)
private final SeasonTeamRepository seasonTeamRepository;   // ADD THIS
```

The aggregation loop follows the production-season filter already established in `generate()`:

```java
// Source: SiteGeneratorService.java lines 73-75 (verified)
var productionSeasons = allSeasons.stream()
        .filter(s -> !s.getName().contains("Test"))
        .toList();
```

Aggregation to build `Map<Team, LinkedHashSet<Season>>` (ordered by insertion = season iteration order):

```java
// [ASSUMED] — standard Java collections pattern
var teamToSeasons = new java.util.LinkedHashMap<org.ctc.domain.model.Team, java.util.LinkedHashSet<Season>>();
for (var season : productionSeasons) {
    for (var st : seasonTeamRepository.findBySeasonId(season.getId())) {
        var team = st.getTeam();
        if (!team.isSubTeam()) {
            teamToSeasons.computeIfAbsent(team, k -> new java.util.LinkedHashSet<>()).add(season);
        }
    }
}
```

For drivers via `seasonDriverRepository.findBySeasonId()` (already injected):

```java
// [ASSUMED] — mirrors the team aggregation pattern above
var driverToSeasonTeams = new java.util.LinkedHashMap<Driver, java.util.List<SeasonDriverInfo>>();
// SeasonDriverInfo = record(Season season, Team team)
for (var season : productionSeasons) {
    for (var sd : seasonDriverRepository.findBySeasonId(season.getId())) {
        driverToSeasonTeams.computeIfAbsent(sd.getDriver(), k -> new java.util.ArrayList<>())
                           .add(new SeasonDriverInfo(season, sd.getTeam()));
    }
}
```

### Pattern 3: `copyLogoToAssets()` for Root-Level Pages

For root-level pages (`teams.html` at `outPath/teams.html`), the `assetsPath` is `"assets"` (root-relative). `writeTemplate()` computes this automatically from the output file location, so no manual computation is needed. However, `copyLogoToAssets()` requires the `assetsPath` string to construct the relative logo path returned:

```java
// Source: SiteGeneratorService.java lines 301-306 (verified)
Path teamDir = outPath.resolve("season").resolve(slugify(season.getDisplayLabel())).resolve("team");
String assetsPath = teamDir.relativize(outPath.resolve("assets")).toString().replace('\\', '/');
String teamLogoRelPath = copyLogoToAssets(team.getLogoUrl(), outPath, assetsPath);
```

For root-level `teams.html`, the analogous computation is:
```java
// [ASSUMED] — derived from writeTemplate() path logic
String assetsPath = "assets";   // root page: outPath.relativize(outPath.resolve("assets")) = "assets"
String teamLogoRelPath = copyLogoToAssets(team.getLogoUrl(), outPath, assetsPath);
```

### Pattern 4: Most-Recent Season URL Construction

"Most recent season" = last element in the ordered `Set<Season>` / `List<SeasonDriverInfo>` (productionSeasons is in `findAll()` order — confirm sorting behavior):

```java
// [ASSUMED] — standard Java list/set access
Season latestSeason = seasonList.getLast();   // Java 21+ List.getLast()
String profileUrl = "season/" + slugify(latestSeason.getDisplayLabel())
                  + "/team/" + slugify(team.getShortName()) + ".html";
```

### Pattern 5: Inline JavaScript for Season Filter

Per D-11, D-12. Minimal vanilla JS (~15 lines) placed in `<script>` block at end of template body:

```javascript
// [ASSUMED] — derived from D-09 through D-12 decisions
(function() {
    var select = document.getElementById('season-filter');
    var cards = document.querySelectorAll('.overview-card');
    select.addEventListener('change', function() {
        var slug = this.value;
        cards.forEach(function(card) {
            if (!slug) {
                card.style.display = '';
            } else {
                var seasons = card.getAttribute('data-seasons') || '';
                card.style.display = seasons.split(' ').indexOf(slug) >= 0 ? '' : 'none';
            }
        });
    });
})();
```

The `<select>` filter block (above `.overview-grid`):

```html
<!-- [ASSUMED] — derived from D-10, D-13 decisions -->
<div class="filter-bar">
    <select id="season-filter">
        <option value="">All Seasons</option>
        <option th:each="entry : ${seasonEntries}"
                th:value="${entry.slug}"
                th:text="${entry.season.displayLabel}">Season Label</option>
    </select>
</div>
```

### Pattern 6: Template Structure (analog: links.html)

```html
<!-- Source: links.html (verified) -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{site/layout :: layout('Teams', ~{::section})}">
<body>
<section>
    <div class="section">
        <h2 class="section-title">Teams</h2>
        <!-- filter dropdown -->
        <div class="overview-grid">
            <div th:each="entry : ${teamEntries}"
                 class="overview-card"
                 th:attr="data-seasons=${#strings.listJoin(entry.seasonSlugs, ' ')}">
                <!-- card content -->
            </div>
        </div>
    </div>
    <script> <!-- inline JS --> </script>
</section>
</body>
</html>
```

### Pattern 7: New CSS Classes

The new CSS should follow the exact same structure as `.link-card` in `style.css` lines 301-335:

```css
/* Overview pages (Phase 47) */
/* [VERIFIED: style.css lines 302-335 for .link-card base pattern] */
.overview-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
}

.overview-card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 20px;
    transition: border-color 0.2s;
}

.overview-card:hover {
    border-color: var(--accent);
}

.season-tag {
    display: inline-block;
    background: rgba(79, 195, 247, 0.1);
    color: var(--accent);
    border-radius: 12px;
    padding: 2px 8px;
    font-size: 11px;
    margin: 2px 2px 0 0;
}

/* Responsive: 1-col on mobile (mirrors .match-grid) */
@media (max-width: 768px) {
    .overview-grid { grid-template-columns: 1fr; }
}
```

### Anti-Patterns to Avoid

- **Using `teamRepository.findAll()` for overview instead of per-season aggregation:** `findAll()` returns every team ever created including those in test seasons. Always filter via `seasonTeamRepository.findBySeasonId()` + test season filter.
- **Building profile URLs pointing to `./season/...` from root pages:** Root pages use no `./` prefix. `writeTemplate()` sets `rootPath = "."` for root-level pages, which is correct — templates reference `${rootPath + '/season/...'}` only for nav; standalone profile URLs should be relative: `"season/" + slug + "..."`.
- **Forgetting `isSubTeam()` filter on teams:** Sub-teams (e.g., `Alpha-A`, `Alpha-B`) share a parent. The overview shows only the parent team.
- **N+1 query problem:** The per-season loop already makes one `findBySeasonId()` call per season, which is accepted. Do NOT add per-team or per-driver lazy loads inside the loop.
- **`display: none` vs CSS class toggle:** Keep the JS simple — `style.display` direct manipulation is safer for static files than toggling class names that might conflict with other CSS.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Relative path computation | Custom path logic | `writeTemplate()` (already handles assetsPath, rootPath) | Already proven, handles OS path separators |
| Season slug generation | Custom URL encoder | `slugify()` (already in service) | Handles umlauts, consistent with existing page URLs |
| Logo file copying | Custom file copy | `copyLogoToAssets()` (already in service) | Handles path traversal, missing files, collision avoidance |
| HTML generation | String concatenation | Thymeleaf template + `writeTemplate()` | Consistent escaping, shared layout (nav/footer/breadcrumbs) automatic |
| Test assertions on HTML | Custom string parsing | Jsoup (already in test classpath) | Established pattern across all existing test methods |

**Key insight:** Phase 47 adds two new pages to an already complete generation pipeline. The heavy lifting — template engine, asset pipeline, layout fragment, relative path math — is done. New code should be data transformation and template markup only.

---

## Common Pitfalls

### Pitfall 1: `SeasonTeamRepository` Missing from Service Constructor

**What goes wrong:** Compilation error — `seasonTeamRepository` field undefined.
**Why it happens:** `SeasonTeamRepository` is used in `Season.addTeam()` / admin controllers but was never injected into `SiteGeneratorService` because previous generation phases used `teamRepository.findAll()` instead.
**How to avoid:** Add `private final SeasonTeamRepository seasonTeamRepository;` as a new `final` field in `SiteGeneratorService`. `@RequiredArgsConstructor` will inject it automatically.
**Warning signs:** The field is absent from the current service — confirmed by grep.

### Pitfall 2: `SeasonTeamRepository` `@EntityGraph` does NOT eager-load `team.parentTeam`

**What goes wrong:** `team.isSubTeam()` triggers lazy load of `parentTeam` inside `@Transactional(readOnly = true)` — works under OSIV but is an N+1 if called many times.
**Why it happens:** `SeasonTeamRepository.findBySeasonId()` only declares `attributePaths = {"team"}`. The `parentTeam` relationship on `Team` is `FetchType.LAZY`.
**How to avoid:** Since OSIV is enabled and the `generate()` method is `@Transactional(readOnly = true)`, lazy loads will succeed. For correctness, call `team.isSubTeam()` immediately after iterating `findBySeasonId()` results — do not defer into a separate loop or stream. Accept the lazy loads as the OSIV approach mandates.
**Warning signs:** `LazyInitializationException` only appears if called outside the transaction, which will not happen inside `generate()`.

### Pitfall 3: Most-Recent Season Order Depends on `seasonRepository.findAll()` Order

**What goes wrong:** The "latest season" for a team or driver is determined by iterating `productionSeasons`. If `findAll()` does not return seasons in chronological order, the "most recent" computation is wrong.
**Why it happens:** `JpaRepository.findAll()` has no guaranteed order.
**How to avoid:** Two options: (a) use `seasonRepository.findAllByOrderByYearDescNumberDesc()` for a definite order, or (b) sort `productionSeasons` by year/number before iterating. Option (b) requires no new repository method. The `Season` model has `year` and `number` fields for sorting.
**Warning signs:** Profile URLs pointing to older seasons instead of the most recent one.

### Pitfall 4: `data-seasons` Attribute Mismatch with Filter JS

**What goes wrong:** Filter shows/hides nothing or hides everything.
**Why it happens:** The slugs in `data-seasons` are built by `slugify(season.getDisplayLabel())` in Java; the `<option value="">` in the template must use the same slug. If the template uses `season.name` instead of `entry.slug`, the comparison fails.
**How to avoid:** The `<select>` options must use the same `SeasonEntry` record (or analogous structure) with the pre-computed slug. Pass a `List<SeasonEntry>` (or equivalent) alongside the `teamEntries` so the template can build `<option value="${entry.slug}">`.

### Pitfall 5: Logo `assetsPath` Computation for Root-Level Page

**What goes wrong:** `copyLogoToAssets()` returns a path prefixed with the wrong number of `..` traversals, causing 404s on logos.
**Why it happens:** In `generateTeamProfiles()`, `assetsPath` is computed by relativizing from the `team/` directory. For root pages, the relative path from `outPath` to `outPath/assets` is simply `"assets"` (no traversal needed).
**How to avoid:** For root-level overview pages, use `assetsPath = "assets"` directly. Do not use the `teamDir.relativize(...)` pattern — that formula produces the wrong result for root pages.
**Warning signs:** Logo `src` attributes contain `../../assets/...` instead of `assets/...`.

### Pitfall 6: Duplicate Driver Entries Across Seasons

**What goes wrong:** A driver who participated in 3 seasons appears 3 times in the overview.
**Why it happens:** Using `seasonDriverRepository.findBySeasonId()` in a loop adds one entry per season-driver record.
**How to avoid:** Use `computeIfAbsent()` with driver identity as key (by driver `UUID`) to accumulate seasons into a list rather than creating one entry per season record.

---

## Code Examples

### Injecting `SeasonTeamRepository`

```java
// Source: SiteGeneratorService.java — @RequiredArgsConstructor pattern (verified)
// Add this field alongside existing repository fields at top of class:
private final SeasonTeamRepository seasonTeamRepository;
// No @Autowired needed — Lombok @RequiredArgsConstructor handles constructor injection.
```

### `generate()` Orchestration Hook

```java
// Source: SiteGeneratorService.java lines 96-97 (verified)
// Insert after generateLinks():
generateLinks(outPath, siteProperties.getLinks(), activeSeasonSlug, activeSeasonName, result);
generateTeamsOverview(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, result);
generateDriversOverview(outPath, productionSeasons, activeSeasonSlug, activeSeasonName, result);
```

### Record Definitions (inner records in `SiteGeneratorService`)

```java
// Source: SiteGeneratorService.java lines 572-574 — existing record pattern (verified)
record TeamOverviewEntry(
    String shortName, String teamSlug, String logoRelPath,
    String profileUrl, List<String> seasonSlugs, List<String> seasonLabels) {}

record DriverOverviewEntry(
    String psnId, String driverSlug, String teamName,
    String profileUrl, List<String> seasonSlugs, List<String> seasonLabels) {}

record SeasonDriverInfo(Season season, Team team) {}
```

### `th:attr` for `data-seasons` in Thymeleaf

```html
<!-- [ASSUMED] — standard Thymeleaf attribute syntax -->
<div class="overview-card"
     th:attr="data-seasons=${#strings.listJoin(entry.seasonSlugs, ' ')}">
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `teamRepository.findAll()` for per-season rendering | `seasonTeamRepository.findBySeasonId()` for cross-season aggregation | Phase 47 (new) | Correct team membership per season; enables season tags |

**Nothing deprecated for this phase.** All existing infrastructure remains unchanged.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `assetsPath = "assets"` is the correct string for root-level pages | Pitfall 5, Pattern 3 | Wrong string causes 404s on logos; fixable by testing |
| A2 | Aggregation loop uses `computeIfAbsent()` on driver/team UUID key for dedup | Pattern 2 | Duplicate entries in overview; detectable by test |
| A3 | JS filter uses `split(' ').indexOf(slug)` for slug matching | Pattern 5 | Filter does not work; 15-line JS, trivially testable |
| A4 | Most-recent season = `List.getLast()` of sorted productionSeasons | Pattern 4 | Profile links point to wrong season; test must assert correct URL |

---

## Open Questions

1. **Sort order for teams/drivers: alphabetical vs. most-recent-season-first?**
   - What we know: D context says "Claude's Discretion"
   - What's unclear: User preference
   - Recommendation: Alphabetical (`Comparator.comparing(Team::getShortName)`) — predictable, testable, consistent with `Season.getTeams()` which sorts by shortName.

2. **Should `seasonRepository.findAll()` be replaced with an ordered query?**
   - What we know: `findAll()` has no guaranteed order; `Season` has `year` + `number` int fields.
   - What's unclear: Whether current data always happens to return seasons in correct order.
   - Recommendation: Sort `productionSeasons` by `Comparator.comparing(Season::getYear).thenComparing(Season::getNumber)` before the aggregation loop. No new repository method needed.

3. **Breadcrumb text for `teams.html` and `drivers.html`?**
   - What we know: D context says "Claude's Discretion". `breadcrumbCurrent = "Links"` is the analog.
   - Recommendation: `"Teams"` / `"Drivers"` — matches section titles, standard web convention.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 47 is pure Java/Thymeleaf/CSS changes. No external tools, services, CLIs, or runtimes beyond the existing project stack are required.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (SpringBootTest) |
| Config file | `pom.xml` (Surefire + Failsafe) |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest -Dspring.profiles.active=dev` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| OVER-01 | `teams.html` exists in output root | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#whenGenerate_thenCreatesTeamsOverviewPage` | ❌ Wave 0 |
| OVER-02 | `drivers.html` exists in output root | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#whenGenerate_thenCreatesDriversOverviewPage` | ❌ Wave 0 |
| OVER-03 | Season filter dropdown present; cards have `data-seasons` | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenMultipleSeasons_whenGenerate_thenTeamsPageHasSeasonFilter` | ❌ Wave 0 |
| OVER-04 | Teams overview contains team short name, logo attribute, season tags | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenTeams_whenGenerate_thenTeamsOverviewShowsNamesAndSeasons` | ❌ Wave 0 |
| OVER-05 | Drivers overview shows PSN ID, team name, season tags | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenDrivers_whenGenerate_thenDriversOverviewShowsPsnIdAndTeams` | ❌ Wave 0 |
| OVER-06 | Team/driver links point to season-specific profile pages | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenTeamsAndDrivers_whenGenerate_thenOverviewLinksResolveToSeasonProfiles` | ❌ Wave 0 |

All tests go into the existing `SiteGeneratorServiceTest.java` file. The existing `@BeforeEach` setUp() creates two teams and four drivers with one season — sufficient for basic assertions. A multi-season test needs a second production season (no "Test" in name) added in `given` blocks.

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` green (82% JaCoCo minimum) before `/gsd-verify-work`

### Wave 0 Gaps

The following test methods do not yet exist and must be written before (or alongside) the implementation:

- [ ] `whenGenerate_thenCreatesTeamsOverviewPage` — OVER-01
- [ ] `whenGenerate_thenCreatesDriversOverviewPage` — OVER-02
- [ ] `givenMultipleSeasons_whenGenerate_thenTeamsPageHasSeasonFilter` — OVER-03
- [ ] `givenTeams_whenGenerate_thenTeamsOverviewShowsNamesAndSeasons` — OVER-04
- [ ] `givenDrivers_whenGenerate_thenDriversOverviewShowsPsnIdAndTeams` — OVER-05
- [ ] `givenTeamsAndDrivers_whenGenerate_thenOverviewLinksResolveToSeasonProfiles` — OVER-06
- [ ] `givenSubTeam_whenGenerate_thenSubTeamExcludedFromTeamsOverview` — D-01 guard
- [ ] `givenTestSeason_whenGenerate_thenTestSeasonNotInOverviewFilter` — D-04 guard

---

## Security Domain

Security enforcement is not applicable to this phase. The changes are purely static site generation (write-only file I/O inside a `@Transactional(readOnly = true)` method). No new HTTP endpoints, no input validation requirements, no authentication surfaces, no data mutations.

---

## Sources

### Primary (HIGH confidence)
- `SiteGeneratorService.java` (verified) — all `generate*()` method patterns, `writeTemplate()`, `copyLogoToAssets()`, `slugify()`, existing field list
- `SeasonTeamRepository.java` (verified) — `findBySeasonId()` signature, `@EntityGraph`
- `SeasonDriverRepository.java` (verified) — `findBySeasonId()` signature
- `Team.java` (verified) — `isSubTeam()`, `getShortName()`, `getLogoUrl()`, `getParentOrSelf()`
- `SeasonDriver.java` (verified) — `getDriver()`, `getTeam()`
- `SeasonTeam.java` (verified) — `getTeam()`, `isReplaced()`
- `Season.java` (verified) — `getDisplayLabel()`, `getYear()`, `getNumber()`
- `Driver.java` (verified) — `getPsnId()`
- `links.html` (verified) — direct structural analog for new templates
- `layout.html` (verified) — `currentPage` variable, nav/footer/breadcrumb structure
- `style.css` (verified) — `.link-card` CSS base pattern lines 301-335; CSS variable definitions
- `SiteGeneratorServiceTest.java` (verified) — all test patterns, Jsoup assertion idioms
- `pom.xml` (verified) — JaCoCo minimum `0.82`
- `.planning/config.json` (verified) — `nyquist_validation: true`

### Secondary (MEDIUM confidence)
- `47-CONTEXT.md` (verified) — locked decisions D-01 through D-21

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all verified from codebase
- Architecture: HIGH — direct extension of verified patterns
- Pitfalls: HIGH (injection gap, assetsPath) / MEDIUM (sort order) — most verified from code
- Test patterns: HIGH — verified from existing test file

**Research date:** 2026-04-16
**Valid until:** 2026-05-16 (stable framework, 30-day window)
