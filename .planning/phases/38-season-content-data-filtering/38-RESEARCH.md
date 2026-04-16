# Phase 38: Season Content & Data Filtering - Research

**Researched:** 2026-04-16
**Domain:** Spring Boot / Thymeleaf static site generation — template enrichment and service-level data filtering
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Season `name` remains the primary identifier in headings/titles. Year and number are shown as supplementary context — not replacing the name but enriching it.
**D-02:** In page headings (standings, matchday, driver-ranking, etc.), format as `"Team Standings — {season.name}"` (unchanged) but add a subtitle or badge line showing year and season number (e.g., `"2025 | Season #3"`).
**D-03:** In the hero section (index.html), show year as part of the hero label: `"Season {name} — {year}"` or similar compact format.
**D-04:** In the archive table, add a dedicated Year column or integrate year/number into the Season column (e.g., `"{name} (#3, 2025)"`). Use `season.year` and `season.number` fields directly rather than parsing `getDisplayLabel()`.
**D-05:** Pre-compute any formatted display strings in `SiteGeneratorService` and pass as template variables — no complex SpEL in templates.
**D-06:** Filter test seasons (name contains "Test") at the service level in `generate()`, before the per-season page generation loop. This means NO pages are generated for test seasons — no standings, matchday, team-profile, driver-profile, driver-ranking, or playoff pages.
**D-07:** Also filter test seasons from the archive listing. The `generateArchive()` method should receive only non-test seasons.
**D-08:** The filter condition is `season.getName().contains("Test")` — case-sensitive, matching the convention established in REQUIREMENTS.md.
**D-09:** Hide the entire `match-meta` div when both `race.track` and `race.car` are null. Add a `th:if` guard: `th:if="${race.track != null or race.car != null}"`. This applies to `matchday.html` and `index.html` (last matchday section).
**D-10:** When only one of track/car is present, show just that value without the separator dash.
**D-11:** Keep the Period column header visible in the archive table. For seasons without start/end dates, show an em-dash or leave the cell empty — no "null" text.
**D-12:** The existing `th:if` guards on date spans in `archive.html` are mostly correct but should be tightened: when both dates are null, show nothing (not even the dash separator).

### Claude's Discretion

- Exact subtitle/badge CSS styling for season year/number on page headings.
- Whether to add a `seasonDisplayYear` or `seasonNumber` template variable to `writeTemplate()` vs. relying on `season.year` and `season.number` from the entity (OSIV active in Thymeleaf).
- Internal refactoring of the archive season filter (inline stream filter vs. separate method).

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONT-01 | Season year and number are displayed on all pages (archive, standings, hero, profiles) | Season entity has `year` (int) and `number` (int) fields; templates receive `season` variable; new `.season-meta` CSS class needed |
| CONT-06 | Test seasons (name containing "Test") are filtered from the archive | `generate()` loads `allSeasons` list; filter before the per-season loop and pass filtered list to `generateArchive()` |
| CONT-07 | Empty match-meta (track/car) and empty period column are hidden when no data exists | `RaceView` has nullable `track`/`car` String fields; archive.html period guards already partially correct |
</phase_requirements>

---

## Summary

Phase 38 is a content enrichment and data filtering phase targeting the Spring Boot Thymeleaf-based static site generator. Three requirements must be addressed: showing season year and number across all public pages (CONT-01), suppressing test seasons from site generation entirely (CONT-06), and hiding empty match-meta and period cells when no data exists (CONT-07).

The technical domain is well-understood: all changes live in `SiteGeneratorService.java` (Java stream filtering), five site templates (`index.html`, `standings.html`, `matchday.html`, `driver-ranking.html`, `archive.html`), and `style.css` (one new CSS class). No new pages, routes, or DB schema changes are needed. OSIV is enabled, so `season.year` and `season.number` are accessible directly in templates via entity getters — no extra template variable pre-computation required.

The existing test infrastructure in `SiteGeneratorServiceTest` uses `@SpringBootTest` with `@TempDir` and Jsoup HTML assertions. All three requirements need new test methods following this established integration test pattern. The test season used in `setUp()` is named `"Gen Test " + uniqueSuffix`, which **contains "Test"** — this is a critical pre-existing collision that the implementation and tests must handle explicitly.

**Primary recommendation:** Filter at service level in `generate()` before the for-loop, pass the filtered list to `generateArchive()`, add `th:if` guards on `match-meta` divs in both templates, tighten archive period guards, add `.season-meta` CSS class, and inject year/number into affected templates via the existing `season` context variable (OSIV active, no extra variables needed).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Test season filtering | Backend service (SiteGeneratorService) | — | Data filtering belongs in the service, not templates, per D-06/D-07 and CLAUDE.md "No Fallback Calculations" principle |
| Season year/number display | Template (Thymeleaf) | Backend service | Season entity exposes `year`/`number` via getters; OSIV active; minimal template expression acceptable since it's a simple field access |
| match-meta conditional rendering | Template (Thymeleaf) | — | Presentation decision — `th:if` guard on view data that already carries nullable `track`/`car` fields |
| Period column empty-state | Template (Thymeleaf) | — | Presentation guard on existing `startDate`/`endDate` — no business logic required |
| CSS class for season metadata | Static assets (style.css) | — | Visual concern only; no component library in use |

---

## Standard Stack

### Core (all already in project — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.x | Application framework | Project baseline [VERIFIED: pom.xml] |
| Thymeleaf | Bundled with Spring Boot | Server-side HTML templating | Project standard — all site templates use it [VERIFIED: codebase grep] |
| JUnit 5 | Bundled with Spring Boot Test | Test framework | Project standard [VERIFIED: TESTING.md] |
| Jsoup | Bundled in project | HTML parsing in tests | Already used in SiteGeneratorServiceTest [VERIFIED: SiteGeneratorServiceTest.java] |

### No New Libraries

This phase introduces zero new dependencies. All required capabilities exist in the current stack.

---

## Architecture Patterns

### System Architecture Diagram

```
generate() entry point
        |
        v
seasonRepository.findAll() -> allSeasons (List<Season>)
        |
        v
[FILTER] allSeasons.stream()               <-- CONT-06: new filter step
         .filter(s -> !s.getName()
              .contains("Test"))
         -> productionSeasons
        |
        +---> for each season (productionSeasons):
        |         generateStandings()
        |         generateDriverRanking()
        |         generateMatchdays()      <-- CONT-07: match-meta guard in template
        |         generateTeamProfiles()
        |         generateDriverProfiles()
        |         generatePlayoffBracket()
        |
        +---> generateArchive(productionSeasons)  <-- CONT-06: filtered list
        |         SeasonEntry records with slug
        |         archive.html template            <-- CONT-01: year/number; CONT-07: period guards
        |
        v
writeTemplate() sets context vars:
    assetsPath, rootPath, activeSeasonSlug
    + "season" variable (entity — OSIV)    <-- CONT-01: season.year, season.number accessible
        |
        v
Thymeleaf renders HTML -> file system (docs/site/)
```

### Recommended Project Structure

No structural changes. All changes are in-place edits to existing files:

```
src/main/java/org/ctc/sitegen/
└── SiteGeneratorService.java       # filter logic + generateArchive() signature
src/main/resources/templates/site/
├── index.html                      # hero label + match-meta guard
├── standings.html                  # .season-meta subtitle
├── matchday.html                   # .season-meta subtitle + match-meta guard
├── driver-ranking.html             # .season-meta subtitle
└── archive.html                    # year/number in season col + period guard tightening
src/main/resources/static/site/css/
└── style.css                       # .season-meta class
src/test/java/org/ctc/sitegen/
└── SiteGeneratorServiceTest.java   # new test methods for all 3 requirements
```

### Pattern 1: Service-Level Season Filtering (CONT-06)

**What:** Apply a stream filter in `generate()` before the per-season loop. Pass the filtered list to `generateArchive()`.
**When to use:** When a display rule applies uniformly to ALL page types (not page-by-page).

**Current code:**
```java
// Source: SiteGeneratorService.java line 65-81
var allSeasons = seasonRepository.findAll();
// ...
for (var season : allSeasons) { ... }
generateArchive(outPath, allSeasons, activeSeasonSlug, result);
```

**Required change:**
```java
var allSeasons = seasonRepository.findAll();
var productionSeasons = allSeasons.stream()
        .filter(s -> !s.getName().contains("Test"))
        .toList();

for (var season : productionSeasons) { ... }
generateArchive(outPath, productionSeasons, activeSeasonSlug, result);
```

Note: `activeSeasonSlug` is computed from `activeSeason` (from `findByActiveTrue()`), which is fetched separately — the filter does not break active season slug computation even if the active season is a test season (edge case).

### Pattern 2: Season Metadata Subtitle (CONT-01)

**What:** Below the existing `.section-title` heading, add a `.season-meta` paragraph showing `{year} | Season #{number}`.
**When to use:** `standings.html`, `matchday.html`, `driver-ranking.html`.

```html
<!-- Source: pattern from UI-SPEC.md -->
<h2 class="section-title" th:text="'Team Standings — ' + ${season.name}"></h2>
<p class="season-meta" th:text="${season.year + ' | Season #' + season.number}"></p>
```

No additional template variable needed — `season` entity is already in context, OSIV active.

### Pattern 3: Hero Label Year Enrichment (CONT-01)

**What:** Append year to the hero-label text in `index.html`.
**Current:** `'Season ' + ${season.name}`
**New:** `'Season ' + ${season.name} + ' — ' + ${season.year}`

```html
<!-- Source: index.html line 7 + UI-SPEC decision -->
<div class="hero-label" th:if="${season}"
     th:text="'Season ' + ${season.name} + ' — ' + ${season.year}"></div>
```

### Pattern 4: Archive Season Column Enrichment (CONT-01)

**What:** Add `.season-meta` line inside the season `<td>` showing `{year} | #{number}`.
**When to use:** `archive.html` season column.

```html
<!-- Source: archive.html line 15 + UI-SPEC -->
<td class="font-bold text-white">
    <span th:text="${entry.season.name}"></span>
    <p class="season-meta" th:text="${entry.season.year + ' | #' + entry.season.number}"></p>
</td>
```

Note: `SeasonEntry` record wraps the `Season` entity — `entry.season.year` and `entry.season.number` are accessible via entity getters (OSIV active for the Thymeleaf rendering context, but `generateArchive` is inside a `@Transactional(readOnly=true)` method, so the session is open for the entire generation).

### Pattern 5: match-meta Guard (CONT-07)

**What:** Wrap the `.match-meta` div with `th:if` to suppress when both track and car are null.
**Files:** `matchday.html` and `index.html` (last matchday section).

**Current (matchday.html lines 16-19, index.html lines 51-55):**
```html
<div class="match-meta">
    <span th:text="${race.track ?: ''}"></span>
    <span th:if="${race.track != null && race.car != null}"> — </span>
    <span th:text="${race.car ?: ''}"></span>
</div>
```

**Required change:**
```html
<div class="match-meta" th:if="${race.track != null or race.car != null}">
    <span th:text="${race.track ?: ''}"></span>
    <span th:if="${race.track != null && race.car != null}"> — </span>
    <span th:text="${race.car ?: ''}"></span>
</div>
```

The inner separator guard (`th:if="${race.track != null && race.car != null}"`) is already correct per D-10.

### Pattern 6: Archive Period Guard Tightening (CONT-07)

**Current archive.html period cell (lines 17-20):**
```html
<td class="text-dim">
    <span th:if="${entry.season.startDate}" th:text="${entry.season.startDate}"></span>
    <span th:if="${entry.season.startDate != null and entry.season.endDate != null}"> — </span>
    <span th:if="${entry.season.endDate}" th:text="${entry.season.endDate}"></span>
</td>
```

**Analysis:** The separator guard is already correct (`both != null`). The individual date guards already use truthy check (null = falsy in Thymeleaf). This is effectively correct behavior already — when both are null, nothing renders. No change required for CONT-07 period guard; the existing code is correct per D-12.

**Verification:** The only tightening needed is confirming that a null `startDate` with truthy `th:if="${entry.season.startDate}"` evaluates to false — this is standard Thymeleaf behavior. [VERIFIED: existing code pattern in archive.html]

### Pattern 7: New CSS Class (CONT-01)

**What:** Add `.season-meta` to `style.css`.
**Placement:** After `.section-title` definition (around line 148).

```css
/* Source: UI-SPEC.md Component Inventory */
.season-meta {
    font-size: 12px;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-top: 4px;
}
```

### Anti-Patterns to Avoid

- **Complex SpEL in templates:** D-05 forbids it; `season.year` is a plain int getter — no computation needed.
- **Filtering in templates with `th:if` on rows:** D-06 requires service-level filtering, not template-level. Test seasons must not generate ANY pages.
- **Inline styles:** CLAUDE.md and feedback_no_inline_styles.md forbid it. The two existing inline styles in `archive.html` (`style="color:#4fc3f7;"` and `style="color:var(--accent)..."`) are documented in UI-SPEC as a planner-discretion cleanup — do not add new inline styles.
- **Modifying `allSeasons` reference:** The `activeSeasonSlug` is computed before filtering. Keep the full `allSeasons` for the `activeSeasonSlug` lookup; use `productionSeasons` only for the per-season loop and `generateArchive`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML parsing in tests | Custom string matching | `Jsoup.parse()` (already in project) | Robust DOM navigation; already the project pattern |
| CSS styling from scratch | New design system | Extend existing `style.css` with `.season-meta` | Consistency — single CSS file, existing custom properties |
| Template variable pre-computation | `SeasonDisplayHelper` class | Direct entity field access (`season.year`, `season.number`) | OSIV active; entity getters are trivially safe; no SpEL complexity |

---

## Critical Observation: Test Season Naming Collision

**Issue:** The existing `SiteGeneratorServiceTest.setUp()` creates a season named `"Gen Test " + uniqueSuffix`.

This name **contains "Test"** — once CONT-06 filtering (`!s.getName().contains("Test")`) is implemented, the test season used in all 13 existing tests will be **filtered out** and no pages will be generated for it.

**Impact on existing tests:**
- All tests asserting `Files.exists(seasonDir().resolve(...))` will FAIL after the filter is implemented.
- All tests asserting page content from the season directory will FAIL.
- The archive slug test will FAIL (season not in archive).

**Required resolution:** The `setUp()` method must rename the test season to something that does NOT contain "Test". Example: `"Gen Season " + uniqueSuffix` or `"Gen Cup " + uniqueSuffix`.

This is the single most critical pre-condition for implementing CONT-06. The rename must happen before writing new filter tests, or the TDD cycle will be confused by failures from this collision.

**Also note:** The `givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE` test creates a byeMatchday for the same season — it will be fixed automatically when `setUp()` is renamed.

---

## Common Pitfalls

### Pitfall 1: Test Season Name Collision (Critical)
**What goes wrong:** `setUp()` uses `"Gen Test " + uniqueSuffix` → filtered out by CONT-06 → all 13 existing integration tests fail.
**Why it happens:** The test data predates the "Test" name filtering requirement.
**How to avoid:** Rename `setUp()` season to a non-"Test" name before implementing the filter. This is a Wave 0 prerequisite.
**Warning signs:** All `Files.exists(seasonDir().resolve(...))` assertions fail after filter implementation.

### Pitfall 2: activeSeasonSlug Computed from Full List
**What goes wrong:** If `activeSeason` is a test season (unlikely in production, but possible in tests), filtering `allSeasons` before computing `activeSeasonSlug` would produce inconsistency.
**Why it happens:** `activeSeasonSlug` uses `activeSeason` from `findByActiveTrue()` — it's independent of `allSeasons`.
**How to avoid:** Keep `activeSeasonSlug` computation from `activeSeason` (unfetched from separate query), not from the filtered list. Current code already computes it independently on line 64.

### Pitfall 3: Thymeleaf `th:if` on `match-meta` Breaks Existing Tests
**What goes wrong:** Tests asserting `match-meta` content exist for races WITH track/car data — adding `th:if` does not break these since track and car are set in `setUp()`. But if a test checks the `match-meta` is absent for a bye race, it now needs to assert absence.
**Why it happens:** The bye race test (`givenByeRaceInSeason`) does not assert on `match-meta` — but the `index.html` last-matchday section renders the last matchday's races. If the last matchday is the bye matchday, the null-track race will have no `match-meta`.
**How to avoid:** Add a specific CONT-07 test with a race that has null track and car, asserting `.match-meta` is absent from the generated HTML.

### Pitfall 4: `SeasonEntry` Record vs. Template Access
**What goes wrong:** `archive.html` accesses `entry.season.year` — this works via Thymeleaf property access on the record's `Season` field. OSIV is active for the `@Transactional(readOnly=true)` `generate()` method duration.
**Why it happens:** Java records use accessor methods (`season()`) not getters (`getSeason()`). Thymeleaf 3.x resolves record accessors correctly.
**How to avoid:** No action needed — Thymeleaf handles record accessors. Confirmed by existing `entry.season.name` usage in archive.html.

### Pitfall 5: Period Cell Content When Both Dates are Null
**What goes wrong:** The archive already renders an empty `<td>` when both dates are null (existing guards evaluate to false). The behavior is correct. Changing the guards unnecessarily may introduce regressions.
**Why it happens:** Developer assumes "tighten the guards" means significant structural change.
**How to avoid:** Verify the existing behavior is already correct with a test. Only add a guard if a "null" string or orphaned separator is actually rendering.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Jsoup |
| Config file | `pom.xml` (Surefire) |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONT-01 | Hero label contains season year | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenHeroLabelContainsYear` | Wave 0 |
| CONT-01 | Standings page has season-meta subtitle | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenStandingsHasSeasonMeta` | Wave 0 |
| CONT-01 | Matchday page has season-meta subtitle | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenMatchdayHasSeasonMeta` | Wave 0 |
| CONT-01 | Driver ranking page has season-meta subtitle | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenDriverRankingHasSeasonMeta` | Wave 0 |
| CONT-01 | Archive season column shows year and number | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenArchiveShowsYearAndNumber` | Wave 0 |
| CONT-06 | Test season generates no pages | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenTestSeason_whenGenerate_thenNoSeasonPagesCreated` | Wave 0 |
| CONT-06 | Test season absent from archive | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenTestSeason_whenGenerate_thenNotInArchive` | Wave 0 |
| CONT-07 | match-meta absent when track and car both null | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenRaceWithNoTrackOrCar_whenGenerate_thenMatchMetaAbsent` | Wave 0 |
| CONT-07 | match-meta present when only track set | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenRaceWithOnlyTrack_whenGenerate_thenMatchMetaPresent` | Wave 0 |
| CONT-07 | Period cell empty when both dates null | Integration (Jsoup) | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeasonWithNoDates_whenGenerate_thenPeriodCellEmpty` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] All 10 new test methods above — to be added to `SiteGeneratorServiceTest.java`
- [ ] Rename `setUp()` season name from `"Gen Test ..."` to `"Gen Season ..."` — MUST happen before implementing CONT-06 filter

---

## Code Examples

### Filter Implementation (SiteGeneratorService.java)
```java
// Source: analysis of SiteGeneratorService.java lines 65-81
var allSeasons = seasonRepository.findAll();
var productionSeasons = allSeasons.stream()
        .filter(s -> !s.getName().contains("Test"))
        .toList();

// Use productionSeasons for page generation:
for (var season : productionSeasons) {
    generateStandings(outPath, season, activeSeasonSlug, result);
    generateDriverRanking(outPath, season, activeSeasonSlug, result);
    generateMatchdays(outPath, season, activeSeasonSlug, result);
    generateTeamProfiles(outPath, season, activeSeasonSlug, result);
    generateDriverProfiles(outPath, season, activeSeasonSlug, result);
    generatePlayoffBracket(outPath, season, activeSeasonSlug, result);
}

generateArchive(outPath, productionSeasons, activeSeasonSlug, result);
```

### CONT-06 Test Pattern
```java
// Source: SiteGeneratorServiceTest.java pattern + new requirement
@Test
void givenTestSeason_whenGenerate_thenNoSeasonPagesCreated() {
    // given — a season whose name contains "Test" (already in DB from a previous setUp in a parallel test,
    // or create explicitly)
    var testSeason = new Season("Test Throwaway " + uniqueSuffix, 2025, 99);
    // ... set scoring refs, save
    var testSeasonDir = tempDir.resolve("season").resolve(slugify(testSeason.getDisplayLabel()));

    // when
    siteGeneratorService.generate();

    // then
    assertFalse(Files.exists(testSeasonDir),
            "Test season should not generate any pages");
}

@Test
void givenTestSeason_whenGenerate_thenNotInArchive() {
    // given — test season exists in DB
    // when
    siteGeneratorService.generate();

    // then
    var html = Files.readString(tempDir.resolve("archive.html"));
    var doc = Jsoup.parse(html);
    assertFalse(doc.select("tbody tr").stream()
            .anyMatch(row -> row.text().contains("Test Throwaway")),
            "Test season should not appear in archive");
}
```

### CONT-07 Test Pattern
```java
@Test
void givenRaceWithNoTrackOrCar_whenGenerate_thenMatchMetaAbsent() throws IOException {
    // given — modify the existing race to remove track and car
    testRace.setTrack(null);
    testRace.setCar(null);
    raceRepository.save(testRace);

    // when
    siteGeneratorService.generate();

    // then
    var html = Files.readString(seasonDir().resolve("matchday/spieltag-1.html"));
    var doc = Jsoup.parse(html);
    assertTrue(doc.select(".match-meta").isEmpty(),
            "match-meta should not render when both track and car are null");
}
```

### Jsoup assertion for .season-meta
```java
@Test
void givenSeason_whenGenerate_thenStandingsHasSeasonMeta() throws IOException {
    // when
    siteGeneratorService.generate();

    // then
    var html = Files.readString(seasonDir().resolve("standings.html"));
    var doc = Jsoup.parse(html);
    var meta = doc.select(".season-meta");
    assertFalse(meta.isEmpty(), ".season-meta element should exist on standings page");
    assertTrue(meta.text().contains("2026"), "season-meta should contain year 2026");
    assertTrue(meta.text().contains("#1"), "season-meta should contain season number #1");
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| allSeasons passed to generateArchive unchanged | Filter before generateArchive, pass productionSeasons | Phase 38 | Test seasons no longer appear in archive |
| match-meta always rendered | match-meta guarded with th:if | Phase 38 | No empty divs for bye races or data-less races |
| Season heading shows name only | Name + .season-meta subtitle | Phase 38 | Users see year and number context |

**Deprecated/outdated:**
- None — this phase adds behavior, does not deprecate existing patterns.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Thymeleaf resolves Java record accessor methods (`season()`) as property expressions in `entry.season.year` | Architecture Patterns — Pattern 4 | Archive template fails to render year/number; fix: convert SeasonEntry to a plain class with getters, or add explicit template variable |
| A2 | The existing archive period guards already produce correct empty output when both dates are null | Common Pitfalls — Pitfall 5 | Period cell may show orphaned content; fix: verify with a test and tighten guard if needed |
| A3 | `@Transactional(readOnly=true)` on `generate()` keeps the JPA session open for the full generation, making lazy fields accessible in the Thymeleaf context | Architecture Patterns — Responsibility Map | LazyInitializationException in template rendering; fix: use @EntityGraph on repository calls |

---

## Open Questions

1. **Inline styles in archive.html**
   - What we know: Two inline styles exist (`style="color:#4fc3f7;"` on Active badge, `style="color:var(--accent)..."` on Standings link). QUAL-01 is Phase 41.
   - What's unclear: Whether the planner should clean them up in Phase 38 as a zero-cost cleanup while touching the file.
   - Recommendation: UI-SPEC deferred this to the planner. Include it as an optional sub-task if it does not risk scope creep. Requires adding `.text-accent` and `.link-accent` CSS classes.

2. **Team-profile and driver-profile season context enrichment**
   - What we know: D-01/D-02 scopes season metadata to "headings". UI-SPEC explicitly says team-profile and driver-profile season context is unchanged.
   - What's unclear: The copywriting contract in UI-SPEC says `"Season {season.name}"` in team-profile is unchanged and `"(Season {season.name})"` in driver-profile inline text is unchanged.
   - Recommendation: Per UI-SPEC, no year/number enrichment on these two templates in Phase 38.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 38 is code and template changes only. No external tools, services, or CLI utilities beyond the Java/Maven project build are required.

---

## Security Domain

No new endpoints, authentication, or user input handling in this phase. All changes are:
- A stream filter on an in-memory Java List (no SQL injection risk)
- Thymeleaf `th:if` guards on existing template variables (no XSS risk — data comes from own DB)
- CSS class addition (no security surface)

ASVS V5 Input Validation: not applicable — no new user input path.
Security enforcement: not applicable to this phase.

---

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Full service code, generate() flow, generateArchive() signature [VERIFIED: read in session]
- `src/main/java/org/ctc/domain/model/Season.java` — `year` (int), `number` (int), `name` (String) fields confirmed [VERIFIED: read in session]
- `src/main/java/org/ctc/sitegen/model/RaceView.java` — `track` and `car` are nullable Strings [VERIFIED: read in session]
- `src/main/resources/templates/site/*.html` — All 7 affected templates read and analyzed [VERIFIED: read in session]
- `src/main/resources/static/site/css/style.css` — Full CSS read; no `.season-meta` class exists yet [VERIFIED: read in session]
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — All 13 existing tests read; naming collision identified [VERIFIED: read in session]
- `.planning/phases/38-season-content-data-filtering/38-CONTEXT.md` — User decisions D-01 through D-12 [VERIFIED: read in session]
- `.planning/phases/38-season-content-data-filtering/38-UI-SPEC.md` — Full UI contract; `.season-meta` CSS spec [VERIFIED: read in session]
- `CLAUDE.md` — Project constraints: no inline styles, TDD, BDD test naming, 82% coverage [VERIFIED: read in session]
- `.planning/codebase/TESTING.md` — Test framework, patterns, JaCoCo exclusions [VERIFIED: read in session]

### Secondary (MEDIUM confidence)
- None required — all needed information was available directly from the codebase.

### Tertiary (LOW confidence)
- None.

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on Phase 38 |
|-----------|-------------------|
| Minimum 82% line coverage | New service filter logic and any helper methods must be covered by new tests |
| TDD: Write tests first | New `SiteGeneratorServiceTest` methods must be written before filter/template implementation |
| BDD test naming: `givenContext_whenAction_thenResult()` | All new test methods must follow this pattern |
| No inline styles on any element | New template elements must use `.season-meta` CSS class; do not add `style=` |
| Keep Thymeleaf templates lean | Year/number display is simple field access — no computed SpEL expressions |
| No complex SpEL in templates | Acceptable: `${season.year + ' | Season #' + season.number}` — simple concatenation |
| No Flyway migration changes | Not applicable — no schema changes in this phase |
| No breaking URL changes | Not applicable — no URLs change |
| Do not modify V1 Flyway migration | Not applicable |
| OSIV remains enabled | Season entity fields accessible in templates without @EntityGraph |
| Git: feature branches, PRs | Implementation must happen on current `gsd/v1.6-static-site-quality` branch |
| Subagent: never Haiku for code | Planner must use Sonnet/Opus for implementation tasks |

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all verified from codebase; no new libraries
- Architecture: HIGH — full service and template code read; filter insertion point unambiguous
- Pitfalls: HIGH — naming collision identified from actual test code; no speculation
- Test patterns: HIGH — existing test class read in full; new test patterns directly derived

**Research date:** 2026-04-16
**Valid until:** 2026-05-16 (stable codebase, no external dependencies)
