# Phase 39: Entity Cross-Linking - Research

**Researched:** 2026-04-16
**Domain:** Static site generation — Thymeleaf template linking, Java record extension, relative URL pre-computation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Add a "Drivers" section to `team-profile.html` below the existing Record table. Show a compact list: PSN-ID as a link to the driver profile + total points in the season.

**D-02:** Use `SeasonDriver` as the data source for the driver-team assignment. Already available via `seasonDriverRepository.findBySeasonId()` and consistent with `generateDriverProfiles()`. Filter by matching team ID.

**D-03:** Pre-compute driver data (PSN-ID, slug, total points) in `generateTeamProfiles()` and pass as a list to the template — no complex SpEL in Thymeleaf.

**D-04:** The index page (`index.html`) gets the same cross-links as the detail pages. Standings table team names link to team profiles, and last matchday result driver names link to driver profiles. Consistent behavior everywhere.

**D-05:** Entity links use the existing `--accent` color (`#4fc3f7`, light blue) as their default color.

**D-06:** Hover state: lighter shade + underline. Add a reusable CSS class (`.entity-link`) for consistent styling across all cross-linked elements.

**D-07:** Links inherit the font weight of their context. Only the color changes.

**D-08:** Pre-compute slugified URLs in `SiteGeneratorService` and pass as template variables or embed in view objects. Consistent with Phase 37 pattern.

**D-09:** For standings: add a `teamProfileUrl` (relative path) to each `TeamStanding` context or pass a team-slug map. For driver ranking and matchday results: add a `driverProfileUrl` or driver-slug to the respective data objects.

**D-10:** All URLs are relative paths from the current page (using the existing `rootPath` mechanism or direct relative path calculation).

### Claude's Discretion

- Exact CSS properties for hover state (opacity, text-decoration style, transition)
- Whether to extend `RaceView.ResultView` with a `driverSlug` field or pass a separate slug map
- Internal refactoring of `generateTeamProfiles()` for driver data loading
- Whether to create a `DriverEntry` record (similar to `SeasonEntry`) for team profile driver data

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONT-02 | Standings table teams link to their team profile pages | `generateStandings()` passes raw `TeamStanding` list — need slug map or wrapper; URL pattern is `season/{seasonSlug}/team/{teamSlug}.html` |
| CONT-03 | Driver ranking entries link to driver profile pages | `generateDriverRanking()` passes `DriverRanking` list — need `driverProfileUrl` on each; URL pattern `season/{seasonSlug}/driver/{driverSlug}.html` |
| CONT-04 | Matchday driver names link to driver profile pages | `toRaceView()` constructs `RaceView.ResultView` records — need `driverProfileUrl` field added to record |
| CONT-08 | Team profile lists team's drivers with links to their profiles | `generateTeamProfiles()` needs to load `SeasonDriver` list filtered by team, compute total points per driver, pass pre-computed list as template variable `drivers` |
</phase_requirements>

---

## Summary

Phase 39 adds cross-navigation links between related entities (teams, drivers) on the static site. No new pages are created — only existing templates are modified to wrap text with `<a>` anchors, and the CSS gains one new `.entity-link` class. The data backbone (slugify, template context variables, relative paths via `rootPath`) is already fully in place from Phase 37.

The primary challenge is **data plumbing**: pre-computing slugified profile URLs in `SiteGeneratorService` before they reach Thymeleaf, since templates must stay logic-free (CLAUDE.md principle). Each generation method that needs links requires either a wrapper record, an extended Java record field, or a parallel slug map passed into the template context. The approach is consistent and precedented by `SeasonEntry` and the `toRaceView()` pattern.

For CONT-08 (team profile driver listing), a small amount of new data loading is required: filter `SeasonDriver` by team ID, sum race points per driver (reusing `raceResultRepository.findByDriverId()` filtered by season), and pass as a typed list to the template.

**Primary recommendation:** Extend `RaceView.ResultView` with a `driverProfileUrl` field (cleanest for matchday + index); pass a `teamSlugMap` for standings (avoids modifying `TeamStanding`); introduce a `DriverEntry` record inside `SiteGeneratorService` for team profile driver data.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| URL slug computation | Service (`SiteGeneratorService`) | — | Existing `slugify()` method; CLAUDE.md forbids logic in templates |
| Relative path construction | Service (`SiteGeneratorService`) | — | `rootPath` is set in `writeTemplate()` and available to all templates |
| Template link rendering | Frontend (Thymeleaf templates) | — | Purely presentational: wrap text in `<a th:href="">` |
| Driver data for team profile | Service (`SiteGeneratorService`) | Repository (`SeasonDriverRepository`, `RaceResultRepository`) | Pre-computation principle D-03 |
| CSS `.entity-link` style | Static asset (`style.css`) | — | Plain CSS; no build tool |

---

## Standard Stack

### Core (all already in use — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Thymeleaf | Already in `pom.xml` | Server-side HTML templating | Project stack; `th:href` for links |
| Spring Boot 4.x | Already in `pom.xml` | Application framework | Project stack |
| Jsoup | Already in `pom.xml` | HTML parsing in tests | Used in `SiteGeneratorServiceTest` for link assertions |
| Java records | Java 25 | View model extension (`RaceView.ResultView`) | Existing pattern in `RaceView`, `SeasonEntry` |

**No new dependencies required for this phase.** [VERIFIED: direct codebase inspection]

---

## Architecture Patterns

### System Architecture Diagram

```
SiteGeneratorService.generate()
        │
        ├─ generateIndex()
        │       ├─ calculateStandings() ──► [{TeamStanding + teamProfileUrl map}]
        │       ├─ raceRepository.findByMatchdayId() ──► toRaceView() ──► RaceView.ResultView (+ driverProfileUrl)
        │       └─ writeTemplate("site/index", ctx) ──► index.html
        │
        ├─ generateStandings()
        │       ├─ calculateStandings() ──► [{TeamStanding}]
        │       ├─ build teamSlugMap: Map<UUID, String>  [NEW]
        │       └─ writeTemplate("site/standings", ctx) ──► standings.html
        │
        ├─ generateDriverRanking()
        │       ├─ calculateRanking() ──► [{DriverRanking}]
        │       ├─ enrich with driverProfileUrl [NEW]
        │       └─ writeTemplate("site/driver-ranking", ctx) ──► driver-ranking.html
        │
        ├─ generateMatchdays()
        │       ├─ toRaceView() ──► ResultView(driverPsnId, teamShortName, …, driverProfileUrl) [NEW FIELD]
        │       └─ writeTemplate("site/matchday", ctx) ──► matchday/{label}.html
        │
        └─ generateTeamProfiles()
                ├─ seasonDriverRepository.findBySeasonIdAndTeamId() [EXISTING repo method]
                ├─ compute totalPoints per driver from raceResultRepository.findByDriverId()
                ├─ build List<DriverEntry> [NEW record]
                └─ writeTemplate("site/team-profile", ctx) ──► team/{teamSlug}.html
```

### Recommended Project Structure

No structural changes. All modifications are in-place:

```
src/main/java/org/ctc/sitegen/
├── SiteGeneratorService.java     # Add teamSlugMap, driverProfileUrl, DriverEntry record
├── model/
│   └── RaceView.java             # Add driverProfileUrl to ResultView record
src/main/resources/
├── templates/site/
│   ├── standings.html            # Wrap team shortName in <a class="entity-link">
│   ├── driver-ranking.html       # Wrap driver PSN-ID in <a class="entity-link">
│   ├── matchday.html             # Wrap driverPsnId in <a class="entity-link">
│   ├── team-profile.html         # Add Drivers section below Record table
│   └── index.html                # Same changes as standings + matchday (D-04)
└── static/site/css/
    └── style.css                 # Add .entity-link and .entity-link:hover rules
src/test/java/org/ctc/sitegen/
└── SiteGeneratorServiceTest.java # Add Jsoup-based link assertion tests for each requirement
```

### Pattern 1: Pre-compute URL in Service, Inject into Template Context

**What:** Calculate slug-based relative URLs in `SiteGeneratorService` before the template context is built. Pass URLs as strings in the context, not computed in Thymeleaf.

**When to use:** All four requirements — any time a link is needed in a template.

**Example (standings — pass slug map):**
```java
// Source: SiteGeneratorService.java (existing slugify pattern + SeasonEntry precedent)
var teamSlugMap = standings.stream()
    .collect(Collectors.toMap(
        s -> s.getTeam().getId(),
        s -> rootPath + "/season/" + seasonSlug + "/team/" + slugify(s.getTeam().getShortName()) + ".html"
    ));
ctx.setVariable("standings", standings);
ctx.setVariable("teamSlugMap", teamSlugMap);
```

**In template:**
```html
<!-- Source: 39-UI-SPEC.md Component Inventory -->
<a class="entity-link font-bold"
   th:href="${teamSlugMap.get(s.team.id)}"
   th:text="${s.team.shortName}"></a>
```

**Why slug map over extending TeamStanding:** `TeamStanding` is a domain service class; adding a site-gen URL to it would violate separation of concerns. A parallel map passed to the context is cleaner. [VERIFIED: StandingsService.java inspection — TeamStanding has no URL concept]

### Pattern 2: Extend Java Record with URL Field

**What:** Add a `driverProfileUrl` field to `RaceView.ResultView` record at construction time in `toRaceView()`.

**When to use:** CONT-04 (matchday results) and all index page result rows (D-04).

**Example:**
```java
// Source: RaceView.java — existing record pattern; toRaceView() in SiteGeneratorService.java
public record ResultView(String driverPsnId, String teamShortName, int position,
                         int qualiPosition, boolean fastestLap, int pointsTotal,
                         String driverProfileUrl) {}  // NEW FIELD
```

Construction in `toRaceView()` (needs `seasonSlug` parameter added):
```java
String driverSlug = slugify(r.getDriver().getPsnId());
String driverProfileUrl = "../driver/" + driverSlug + ".html";
// For index page: rootPath + "/season/" + seasonSlug + "/driver/" + driverSlug + ".html"
return new RaceView.ResultView(r.getDriver().getPsnId(), teamShortName,
    r.getPosition(), r.getQualiPosition(), r.isFastestLap(), r.getPointsTotal(),
    driverProfileUrl);
```

**Path depth note:** Matchday pages live at `season/{seasonSlug}/matchday/{label}.html` — relative path to driver is `../driver/{driverSlug}.html`. Index page lives at root `index.html` — path differs. Two options:
1. Pass `seasonSlug` into `toRaceView()` and compute absolute-relative path from root (use `rootPath`)
2. Use absolute-from-root path in both cases via `rootPath` mechanism

**Recommendation:** Use `rootPath + "/season/" + seasonSlug + "/driver/" + driverSlug + ".html"` — consistent with how `layout.html` nav links work. This requires `toRaceView()` to accept `seasonSlug` and `rootPathFromMatchday` — or compute it in the calling generate method.

**Alternative (simpler):** Pass a separate `driverSlugMap: Map<String, String>` (PSN-ID → profile URL) to the template context, avoiding record modification. Tradeoff: two separate variables in context. Either is valid — CONTEXT.md leaves this to discretion.

### Pattern 3: DriverEntry Record for Team Profile

**What:** A private record inside `SiteGeneratorService` (like `SeasonEntry`) to carry pre-computed driver data for the team profile template.

**When to use:** CONT-08 — team profile driver listing.

**Example:**
```java
// Source: SiteGeneratorService.java — SeasonEntry record precedent (line 369)
record DriverEntry(String psnId, String driverProfileUrl, int totalPoints) {}
```

Construction in `generateTeamProfiles()`:
```java
// Source: SeasonDriverRepository.findBySeasonIdAndTeamId() — already exists in repo
var teamDrivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), team.getId());
var driverEntries = teamDrivers.stream().map(sd -> {
    var driver = sd.getDriver();
    var results = raceResultRepository.findByDriverId(driver.getId()).stream()
        .filter(r -> r.getRace().getMatchday().getSeason().getId().equals(season.getId()))
        .toList();
    int totalPoints = results.stream().mapToInt(r -> r.getPointsTotal()).sum();
    String driverProfileUrl = "../driver/" + slugify(driver.getPsnId()) + ".html";
    return new DriverEntry(driver.getPsnId(), driverProfileUrl, totalPoints);
}).toList();
ctx.setVariable("drivers", driverEntries);
```

**Path note:** Team profile pages live at `season/{seasonSlug}/team/{teamSlug}.html`. Driver profiles at `season/{seasonSlug}/driver/{driverSlug}.html`. Relative path from team to driver: `../driver/{driverSlug}.html`. This is a fixed relative depth — safe to hardcode relative path. [VERIFIED: SiteGeneratorService.java lines 180-190]

### Pattern 4: CSS `.entity-link` Class

**What:** New CSS class in `style.css` for all entity anchor elements. Fully specified in UI-SPEC.

**When to use:** Every `<a>` element wrapping a team or driver name.

**Exact CSS from 39-UI-SPEC.md:**
```css
/* Source: 39-UI-SPEC.md Component Inventory */
.entity-link {
    color: var(--accent);
    text-decoration: none;
    transition: all 0.2s;
}

.entity-link:hover {
    color: #b3e5fc;
    text-decoration: underline;
}
```

Add after the existing `tr:hover` rule block.

### Pattern 5: Jsoup Test Assertions for Link Correctness

**What:** Extend `SiteGeneratorServiceTest` with assertions using Jsoup selectors to verify `<a>` elements have correct `href` values.

**When to use:** One test per CONT requirement.

**Example:**
```java
// Source: SiteGeneratorServiceTest.java — existing Jsoup pattern (lines 264-274, 318-330)
@Test
void givenTeamInStandings_whenGenerate_thenTeamNameLinksToTeamProfile() throws IOException {
    // when
    siteGeneratorService.generate();

    // then
    var html = Files.readString(seasonDir().resolve("standings.html"));
    var doc = Jsoup.parse(html);
    var teamLinks = doc.select("tbody td a.entity-link[href*='team/']");
    assertFalse(teamLinks.isEmpty(), "Standings should contain team profile links");
    assertTrue(teamLinks.stream().anyMatch(a -> a.attr("href").contains("gtnr")),
               "Team link href should contain slugified team short name");
}
```

### Anti-Patterns to Avoid

- **SpEL concatenation in Thymeleaf:** `th:href="'/season/' + ${slugify(team.shortName)} + '...'"` — `slugify` is not a Thymeleaf utility; calling Java methods from templates violates CLAUDE.md's "Keep Thymeleaf Templates Lean" rule.
- **Accessing entities in template for URL computation:** `th:href="${'season/' + s.team.shortName.toLowerCase()}"` — no umlaut handling, no special char normalization.
- **Absolute paths:** `th:href="'/season/...'` — violates LINK-03 (all nav links relative) and breaks GitHub Pages deployment in subdirectory.
- **Modifying `StandingsService.TeamStanding`:** Adding `teamProfileUrl` to a domain service class couples site-gen concerns into domain logic. Pass a separate context variable instead.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| URL slug computation | Custom slug method per template | `SiteGeneratorService.slugify()` | Already handles umlauts (äöü→ae/oe/ue), ß→ss, non-alphanumeric normalization |
| Relative path depth | Custom path traversal logic | Fixed relative strings (`"../driver/"`) or `rootPath` mechanism | Template nesting depth is fixed and known at generation time |
| HTML link assertions in tests | Custom string scanning | Jsoup `doc.select("a[href*='...']")` | Already used in `SiteGeneratorServiceTest`; handles HTML parsing correctly |
| Driver total points | Re-implementing scoring | `raceResultRepository.findByDriverId()` + `.stream().mapToInt(r -> r.getPointsTotal()).sum()` | `pointsTotal` already calculated and stored on `RaceResult` by `ScoringService` |

**Key insight:** The entire infrastructure (slugify, rootPath, writeTemplate, Jsoup test assertions) already exists. This phase is additive wiring, not new infrastructure.

---

## Runtime State Inventory

Step 2.6: SKIPPED — this phase is purely code/template/CSS changes. No external dependencies, no external tools beyond the standard Java/Maven build already installed.

**No runtime state affected.** Static site is regenerated from scratch on each `generate()` call — no incremental state.

---

## Common Pitfalls

### Pitfall 1: Wrong Relative Path Depth

**What goes wrong:** Using `"season/{seasonSlug}/driver/{driverSlug}.html"` from within a matchday page that already lives inside `season/{seasonSlug}/matchday/` — produces a broken link (resolves to `season/.../matchday/season/.../driver/...`).

**Why it happens:** Forgetting that relative URLs resolve from the *current* file's directory, not from the site root.

**How to avoid:** Use `rootPath` for all cross-section links (rootPath is computed in `writeTemplate()` as the relative path from the current file's directory back to the site root). Then construct: `rootPath + "/season/" + seasonSlug + "/driver/" + driverSlug + ".html"`. This works from any page depth.

**Warning signs:** Links work from index page but 404 from matchday or team profile pages.

### Pitfall 2: rootPath Not Available in toRaceView()

**What goes wrong:** `toRaceView()` is a private method called before `writeTemplate()` sets `rootPath` in the context — it doesn't have access to `rootPath`.

**Why it happens:** `rootPath` is computed per-output-file inside `writeTemplate()` from the current file's location. `toRaceView()` is called before that.

**How to avoid:** Compute the driver profile URL using a relative path that is valid for the specific template being rendered. For matchday pages: `"../driver/{slug}.html"` is always correct (one directory up from `/matchday/` into the same season dir). Pass `seasonSlug` and/or the relative prefix as a parameter to `toRaceView()` if the index page also needs to call it with different path context — OR compute URL in the generate method and pass a slug map instead.

**Alternative:** Add `driverSlug` (not full URL) to `ResultView`, and compute the full URL in each template using `rootPath`. But this reintroduces URL logic in templates — violates D-08.

**Recommendation:** Separate the URL computation: have `toRaceView()` produce `driverSlug` only (a pure data field), then the generate method wraps it with the correct path prefix before building context. Or pass a `urlPrefix` string to `toRaceView()`.

### Pitfall 3: findBySeasonIdAndTeamId Missing for Sub-Teams

**What goes wrong:** `seasonDriverRepository.findBySeasonIdAndTeamId()` filters by the exact team ID. If a driver is registered under a sub-team, filtering by the parent team ID will miss them.

**Why it happens:** SeasonDriver stores the exact team (which may be a sub-team), not the parent team.

**How to avoid:** For CONT-08, filter by checking if `sd.getTeam().getId().equals(team.getId())` OR `sd.getTeam().getParentOrSelf().getId().equals(team.getId())`. Alternatively, query `findBySeasonId()` and filter in Java. CONTEXT.md D-02 uses `findBySeasonId()` and filters by team ID — follow that approach with explicit check.

**Warning signs:** Team profile driver list is empty for teams that have sub-teams with drivers registered under the sub-team.

### Pitfall 4: Index Page Uses lastMatchdayRaces Without driverProfileUrl

**What goes wrong:** `generateIndex()` calls `toRaceView()` but if `ResultView` gains a `driverProfileUrl` field, the index page call must also provide the correct URL (not leave it null).

**Why it happens:** Index page has a different path depth (`/index.html` at root) versus matchday pages.

**How to avoid:** Ensure `toRaceView()` (or its wrapper) receives the correct URL prefix for the index page context. Use `rootPath + "/season/" + activeSeasonSlug + "/driver/" + slug + ".html"` — since `rootPath` from index.html resolves to `"."`, this becomes `"./season/{slug}/driver/{slug}.html"`.

### Pitfall 5: Record Field Addition Breaks Constructor Calls

**What goes wrong:** Adding `driverProfileUrl` to `RaceView.ResultView` record breaks all existing `new RaceView.ResultView(...)` constructor calls — records don't have default values.

**Why it happens:** Java records generate canonical constructors from all fields.

**How to avoid:** Find all usages of `new RaceView.ResultView(...)` before modifying the record. In this codebase there is exactly one construction point: `toRaceView()` in `SiteGeneratorService.java` (line 341). Confirm with grep before changing. [VERIFIED: Grep of codebase — only one construction site]

---

## Code Examples

Verified patterns from codebase inspection:

### Existing slugify usage (CONT-02, CONT-03, CONT-04)
```java
// Source: SiteGeneratorService.java line 362-367
private String slugify(String input) {
    return input.toLowerCase()
        .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
}
```

### SeasonEntry record as DriverEntry template (CONT-08)
```java
// Source: SiteGeneratorService.java line 369 — pattern for DriverEntry
record SeasonEntry(Season season, String slug) {}
// New:
record DriverEntry(String psnId, String driverProfileUrl, int totalPoints) {}
```

### Existing findBySeasonIdAndTeamId (CONT-08)
```java
// Source: SeasonDriverRepository.java line 17
@EntityGraph(attributePaths = {"driver", "team"})
List<SeasonDriver> findBySeasonIdAndTeamId(UUID seasonId, UUID teamId);
```

### Jsoup link assertion pattern (test)
```java
// Source: SiteGeneratorServiceTest.java lines 318-330 — existing href assertion pattern
var links = doc.select("a[href*='season/" + expectedSlug + "/standings.html']");
assertFalse(links.isEmpty(), "Expected link with slug in href but found: "
    + doc.select("a[href*='season/']").stream().map(e -> e.attr("href")).toList());
```

### UI-SPEC confirmed template patterns

**standings.html team link:**
```html
<!-- Source: 39-UI-SPEC.md Component Inventory -->
<a class="entity-link font-bold" th:href="${teamSlugMap.get(s.team.id)}"
   th:text="${s.team.shortName}"></a>
<span class="text-dim" th:text="' ' + ${s.team.name}"></span>
```

**driver-ranking.html driver link:**
```html
<!-- Source: 39-UI-SPEC.md Component Inventory -->
<td class="font-bold">
    <a class="entity-link" th:href="${r.driverProfileUrl}" th:text="${r.driver.psnId}"></a>
</td>
```

**matchday.html result driver link:**
```html
<!-- Source: 39-UI-SPEC.md Component Inventory -->
<td class="font-bold">
    <a class="entity-link" th:href="${result.driverProfileUrl}" th:text="${result.driverPsnId}"></a>
</td>
```

**team-profile.html drivers section:**
```html
<!-- Source: 39-UI-SPEC.md Component Inventory -->
<div class="section" th:if="${not #lists.isEmpty(drivers)}">
    <h2 class="section-title">Drivers</h2>
    <div class="table-wrap">
        <table>
            <thead><tr><th>Driver</th><th class="text-right">Points</th></tr></thead>
            <tbody>
                <tr th:each="d : ${drivers}">
                    <td>
                        <a class="entity-link font-bold" th:href="${d.driverProfileUrl}"
                           th:text="${d.psnId}"></a>
                    </td>
                    <td class="text-right font-bold text-white" th:text="${d.totalPoints}"></td>
                </tr>
            </tbody>
        </table>
    </div>
</div>
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Plain text entity names in tables | Clickable `<a>` entity links | Phase 39 | Navigation between entities without manual URL editing |
| `text-white` on entity cells | `entity-link` accent color | Phase 39 | Visual distinction: clickable vs. non-clickable content |

**No deprecated patterns in this phase.** All changes are additive.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `RaceView.ResultView` is constructed in exactly one place (`toRaceView()`) | Pitfall 5 | Adding field breaks other construction sites; must re-verify with grep |
| A2 | Driver total points can be computed by filtering `raceResultRepository.findByDriverId()` by season | Pattern 3 | If race→matchday→season lazy chain fails, NPE; use `@EntityGraph` or check OSIV transaction boundary |

**Note on A2:** `generateTeamProfiles()` runs inside `@Transactional(readOnly = true)` on `generate()`. OSIV is enabled, so lazy traversal `r.getRace().getMatchday().getSeason().getId()` is safe within the transaction. [VERIFIED: SiteGeneratorService.java line 54, CLAUDE.md OSIV section, SiteGeneratorService.java lines 195-211 — same pattern used in `generateDriverProfiles()`]

---

## Open Questions

1. **URL approach for `toRaceView()` (index vs. matchday)**
   - What we know: Index page is at root depth; matchday pages are at depth 3 (`season/{slug}/matchday/`). `toRaceView()` is called from both `generateIndex()` and `generateMatchdays()`.
   - What's unclear: Whether to use `rootPath`-based absolute-from-root URLs (cleaner, requires passing `rootPath` computed value to `toRaceView()`) or use the calling generate method to compute URLs before/after `toRaceView()`.
   - Recommendation: Compute driver URL in each generate method using a local `urlPrefix` string, pass it to `toRaceView()` as a new parameter. For index: `urlPrefix = "./season/" + activeSeasonSlug + "/driver/"`. For matchday: `urlPrefix = "../driver/"`. This keeps `toRaceView()` generic.

2. **Sub-team filtering for CONT-08**
   - What we know: `findBySeasonIdAndTeamId()` filters by exact team ID; sub-teams have their own IDs.
   - What's unclear: Whether the test data in `SiteGeneratorServiceTest` covers sub-teams for team profile driver listing.
   - Recommendation: Use `findBySeasonId()` and filter in Java with `sd.getTeam().getId().equals(team.getId())` — matches the approach in `generateDriverProfiles()`.

---

## Environment Availability

Step 2.6: SKIPPED — phase is code/template/CSS changes only. Java 25, Maven, H2 (test profile) all confirmed present from prior phases. No new external dependencies.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Jsoup |
| Config file | `pom.xml` (Surefire plugin) |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONT-02 | Standings team names are `<a>` links pointing to team profile | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*StandingsTeamLink*` | ❌ Wave 0 |
| CONT-03 | Driver ranking PSN-IDs are `<a>` links pointing to driver profile | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*DriverRankingLink*` | ❌ Wave 0 |
| CONT-04 | Matchday result driver names are `<a>` links pointing to driver profile | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*MatchdayDriverLink*` | ❌ Wave 0 |
| CONT-08 | Team profile has Drivers section with driver links | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*TeamProfileDrivers*` | ❌ Wave 0 |
| CONT-04 (index) | Index page last matchday result driver names are links | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*IndexDriverLink*` | ❌ Wave 0 |
| CONT-02 (index) | Index page standings team names are links | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*IndexTeamLink*` | ❌ Wave 0 |

**All tests go in existing `SiteGeneratorServiceTest.java`** — no new test class needed. This is the established pattern for site generator tests.

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` full green before `/gsd-verify-work`

### Wave 0 Gaps
All 6 test methods listed above are new; add them in Wave 0 (TDD — red before implementation). No new test infrastructure needed — `SiteGeneratorServiceTest` already has all fixtures (season, teams, drivers, race with results).

---

## Security Domain

This phase adds only `<a href="...">` elements with pre-computed static relative paths. No user input, no form processing, no authentication changes. All URLs are computed server-side in Java from database slugs — no injection vector. ASVS V5 input validation is not applicable (no external input processed). Security posture unchanged.

---

## Project Constraints (from CLAUDE.md)

| Constraint | Impact on Phase 39 |
|------------|-------------------|
| Keep Thymeleaf Templates Lean | URLs must be pre-computed in service; no `slugify()` calls or path arithmetic in templates |
| No Inline Styles on Buttons | Not applicable — adding `<a>` elements, not buttons; use `.entity-link` CSS class |
| TDD (Write tests first) | All 6 link-assertion tests written before implementation code |
| Test Coverage ≥ 82% | New code paths in `generateTeamProfiles()` and `toRaceView()` must be covered by the new integration tests |
| DTOs instead of Entities in Controllers | Not applicable — site generator is service-to-template, no controller binding |
| OSIV Enabled | `generateTeamProfiles()` lazy traversal safe within `@Transactional(readOnly = true)` on outer `generate()` |
| Do Not Modify Flyway Migrations | No schema changes — no migration needed |
| Backward Compatibility | No existing URL endpoints changed; only new links within static HTML |

---

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection: `SiteGeneratorService.java`, `RaceView.java`, `SiteGeneratorServiceTest.java`, `StandingsService.java`, `DriverRankingService.java`, `SeasonDriverRepository.java`, `RaceResultRepository.java`, `SeasonDriver.java` — all verified by Read tool
- All 5 site templates: `standings.html`, `driver-ranking.html`, `matchday.html`, `team-profile.html`, `index.html`, `layout.html` — verified by Read tool
- `style.css` — CSS custom properties verified, `--accent: #4fc3f7` confirmed
- `39-CONTEXT.md` — locked decisions D-01 through D-10 verified
- `39-UI-SPEC.md` — exact CSS and HTML patterns verified

### Secondary (MEDIUM confidence)
- `ARCHITECTURE.md` — corroborates OSIV, service layer patterns, static site generation flow

### Tertiary (LOW confidence)
- None — all claims in this research are verified from codebase or CONTEXT.md

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all libraries verified in codebase
- Architecture: HIGH — all patterns verified from direct code inspection
- Pitfalls: HIGH — path depth issues verified by inspecting actual file locations in SiteGeneratorService; record constructor issue verified by grep

**Research date:** 2026-04-16
**Valid until:** 2026-05-16 (stable stack — no fast-moving dependencies)
