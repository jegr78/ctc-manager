# Phase 49: E2E Site Validation - Research

**Researched:** 2026-04-17
**Domain:** JUnit 5 integration testing, JSoup HTML parsing, Java NIO file traversal
**Confidence:** HIGH

## Summary

Phase 49 creates a single new test class `SiteGeneratorE2ETest.java` that validates the entire generated static site end-to-end. This is a test-only phase — no production code changes. The class generates the full site once in a `@BeforeAll` setup, then runs 7+ validation tests against the shared output.

The codebase already contains all infrastructure needed: JSoup 1.15.0, Java NIO `Files.walk()`, `@TempDir`, `@SpringBootTest`, and `@ActiveProfiles("dev")` patterns are established and used extensively in `SiteGeneratorServiceTest.java` (69 tests, 1299 lines). The new class is a sibling in the same `org.ctc.sitegen` package.

The critical design challenge is using `static @TempDir` with `@BeforeAll` to generate the site only once and share results across all tests. JUnit 5 supports this via a `static` field annotated with `@TempDir` — `@BeforeAll` runs once and all `@Test` methods share the same generated output. This is distinct from the existing `SiteGeneratorServiceTest` which uses `@BeforeEach` with per-test isolation.

**Primary recommendation:** One `static @TempDir`, one `@BeforeAll` that calls `generate()`, then individual `@Test` methods per E2E requirement. Use `assertAll()` for the link crawler to collect all broken links before failing.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Test Class Structure:**
- D-01: New class `SiteGeneratorE2ETest.java` in `org.ctc.sitegen` package — separate from `SiteGeneratorServiceTest`
- D-02: Same `@SpringBootTest`, `@ActiveProfiles("dev")`, `@TempDir` pattern as existing tests
- D-03: `@BeforeAll` (or `@BeforeEach`) sets up test data and calls `generate()` once. All tests share the same generated output — no per-test regeneration
- D-04: Use `static @TempDir` with `@BeforeAll` for efficiency — generate once, validate many times

**Internal Link Validation (E2E-01):**
- D-05: Walk all `.html` files with `Files.walk()`
- D-06: Parse each file with JSoup, extract all `a[href]` elements
- D-07: Filter internal links only — skip `#` anchors, `http://`, `https://`, `javascript:`, `mailto:`
- D-08: Resolve each relative href against the HTML file's parent directory using `Path.resolve()`
- D-09: Assert `Files.exists()` for each resolved path; collect all broken links and report in single failure

**Navigation Consistency (E2E-02):**
- D-10: Assert `.nav` element exists on each generated page
- D-11: Assert `.footer` element exists on every page
- D-12: Both checks run on ALL generated .html files, not just a sample

**Content Validation (E2E-03):**
- D-13: Assert `#main-content` element has at least one child element on every page

**Landing Page Tiles (E2E-04):**
- D-14: Parse `index.html`, find `.tile-card` link elements, resolve each href, assert target file exists

**Links Page (E2E-05):**
- D-15: Parse `links.html`, assert it contains `<a>` elements with the configured link URLs from `ctc.site.links`

**YouTube Footer (E2E-06):**
- D-16: Check multiple pages (index, archive, standings, teams, drivers) for `a[href*='youtube.com/@CommunityTeamCup']` in the footer

**Overview Pages Season Filter (bonus):**
- D-17: Assert `teams.html` and `drivers.html` have `<select id="season-filter">` element

### Claude's Discretion
- Exact number of pages to sample for YouTube footer check (3-5 is sufficient)
- Whether to parallelize the link crawl or keep it simple single-threaded
- Helper method extraction for common JSoup operations

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| E2E-01 | All internal `href` links resolve to existing files in the output directory | `Files.walk()` + JSoup + `Path.resolve()` + `Files.exists()` — all available |
| E2E-02 | All pages have consistent navigation structure (nav + footer present) | JSoup `.nav` and `.footer` selectors confirmed in layout.html |
| E2E-03 | No generated page has empty main content | JSoup `#main-content` selector confirmed in layout.html (`<main class="main" id="main-content">`) |
| E2E-04 | Landing page tiles link to valid generated pages | JSoup `.tile-card[href]` selector + `Path.resolve()` + `Files.exists()` |
| E2E-05 | Links page renders all configured links | `SiteProperties.getLinks()` available for URL comparison; JSoup `a[href]` selectors |
| E2E-06 | Footer YouTube link present on all page types | JSoup `a[href*='youtube.com/@CommunityTeamCup']` confirmed against layout.html hardcoded href |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Site generation | Test/Spring context | SiteGeneratorService (prod) | `@SpringBootTest` wires the real service; test drives it via `generate()` |
| HTML parsing / link validation | Test (JUnit 5 + JSoup) | — | Pure test-layer concern; JSoup already a test-time dependency |
| File system traversal | Test (Java NIO) | — | `Files.walk()` on `@TempDir` output — no service involvement needed |
| Test data setup | Test (`@BeforeAll`) | H2 in-memory DB | One-time data creation for the shared generated site |

## Standard Stack

### Core (already in project — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit 5 Jupiter | Via Spring Boot 4.0.5 parent | Test lifecycle, assertions, `@TempDir` | Project standard |
| JSoup | 1.15.0 [VERIFIED: pom.xml line 108] | HTML parsing, CSS selector queries | Already used in `SiteGeneratorServiceTest` |
| Java NIO (`java.nio.file.*`) | Java 25 | File traversal, path resolution | Standard Java; used throughout existing tests |
| Spring Boot Test | 4.0.5 | `@SpringBootTest`, `@ActiveProfiles` | Project standard |
| Mockito | Via Spring Boot parent | Mock injection for `@SpringBootTest` | Not needed in this class — real beans used |

**No new dependencies required.** [VERIFIED: existing test imports confirm all libraries available]

## Architecture Patterns

### System Architecture Diagram

```
Test Context (H2, @ActiveProfiles("dev"))
        |
        v
@BeforeAll: setUp() ─────────────────────────────────────────────┐
  Create test data (Season, Teams, Drivers, Matchday, Race)       |
  siteGeneratorService.setOutputDir(tempDir)                      |
  siteProperties.setLinks([YouTube link])                         |
  siteGeneratorService.generate()  ─────► static Path tempDir    |
                                          (shared across all tests)|
                                                  |                |
     ┌────────────────────────────────────────────┘                |
     v                                                             |
7 @Test methods (share tempDir, no re-generation):                 |
  ├── whenSiteGenerated_thenAllInternalLinksResolve()   [E2E-01]  |
  ├── whenSiteGenerated_thenAllPagesHaveNav()           [E2E-02]  |
  ├── whenSiteGenerated_thenAllPagesHaveFooter()        [E2E-02]  |
  ├── whenSiteGenerated_thenNoPageHasEmptyMain()        [E2E-03]  |
  ├── whenSiteGenerated_thenLandingTilesResolve()       [E2E-04]  |
  ├── whenSiteGenerated_thenLinksPageHasConfiguredLinks()[E2E-05] |
  ├── whenSiteGenerated_thenFooterYouTubePresent()      [E2E-06]  |
  └── whenSiteGenerated_thenOverviewHasSeasonFilter()   [D-17]    |
```

### Recommended Project Structure

```
src/test/java/org/ctc/sitegen/
├── SiteGeneratorServiceTest.java   # Existing: 69 unit/integration tests
├── SiteGeneratorE2ETest.java       # NEW: E2E cross-cutting validation
└── YouTubeScraperServiceTest.java  # Existing
```

### Pattern 1: Static @TempDir with @BeforeAll

**What:** Declare `@TempDir` as a `static` field; JUnit 5 injects it before `@BeforeAll` runs. All tests in the class share the same directory.

**When to use:** When multiple tests validate the same generated artifact and re-generation per test would be slow and redundant.

**Critical constraint:** `@SpringBootTest` creates a new application context per class by default. The `@BeforeAll` static method cannot receive Spring beans via `@Autowired` — only the test instance fields are injected. However, Spring Boot Test supports `@BeforeAll` in non-static form when using `@TestInstance(Lifecycle.PER_CLASS)`. This allows `@Autowired` beans to be available in `@BeforeAll`.

**Two valid approaches:**

Option A — `static @TempDir` + `@TestInstance(PER_CLASS)`:
```java
// Source: JUnit 5 docs — https://junit.org/junit5/docs/current/user-guide/#writing-tests-lifecycle
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SiteGeneratorE2ETest {

    @TempDir
    Path tempDir;  // instance field, but shared via PER_CLASS lifecycle

    @Autowired
    SiteGeneratorService siteGeneratorService;

    @Autowired
    SiteProperties siteProperties;

    @BeforeAll
    void setUpOnce() throws Exception {
        // configure + generate
        siteGeneratorService.setOutputDir(tempDir.toString());
        // ... test data setup ...
        siteGeneratorService.generate();
    }

    @Test
    void whenSiteGenerated_thenAllInternalLinksResolve() { ... }
}
```

Option B — `static @TempDir` + `static @BeforeAll` (instance fields not available):
```java
// Requires extracting all Spring bean access to per-test @Autowired fields
// and passing data through static fields — more complex, avoid.
```

**Recommendation:** Use `@TestInstance(Lifecycle.PER_CLASS)` (Option A). This allows `@Autowired` beans in `@BeforeAll` without static awkwardness. [ASSUMED — PER_CLASS compatibility with Spring Boot 4.x @SpringBootTest should work as in 3.x, but not verified against Spring Boot 4.0.5 docs]

### Pattern 2: Bulk Link Crawler with Collected Failures

**What:** Walk all HTML files, collect all broken links, report them all in one assertion failure.

**When to use:** Link validation — fail fast on first broken link loses diagnostic value.

**Example:**
```java
// Source: based on CONTEXT.md D-09 specification
@Test
void whenSiteGenerated_thenAllInternalLinksResolve() throws Exception {
    var brokenLinks = new ArrayList<String>();

    try (var htmlFiles = Files.walk(tempDir)
            .filter(p -> p.toString().endsWith(".html"))) {

        htmlFiles.forEach(htmlFile -> {
            try {
                var doc = Jsoup.parse(Files.readString(htmlFile));
                for (var link : doc.select("a[href]")) {
                    var href = link.attr("href");
                    if (isInternal(href)) {
                        var resolved = htmlFile.getParent().resolve(href);
                        if (!Files.exists(resolved)) {
                            brokenLinks.add(htmlFile.getFileName() + " -> " + href);
                        }
                    }
                }
            } catch (IOException e) {
                brokenLinks.add("Parse error: " + htmlFile);
            }
        });
    }

    assertTrue(brokenLinks.isEmpty(),
        "Broken internal links found:\n" + String.join("\n", brokenLinks));
}

private boolean isInternal(String href) {
    return !href.startsWith("http://")
        && !href.startsWith("https://")
        && !href.startsWith("#")
        && !href.startsWith("javascript:")
        && !href.startsWith("mailto:");
}
```

### Pattern 3: All-Pages Structural Check

**What:** Walk all HTML files and assert structural elements are present on each.

**Example:**
```java
// Source: CONTEXT.md D-10, D-11, D-12
@Test
void whenSiteGenerated_thenAllPagesHaveNavAndFooter() throws Exception {
    var violations = new ArrayList<String>();

    try (var htmlFiles = Files.walk(tempDir)
            .filter(p -> p.toString().endsWith(".html"))) {

        htmlFiles.forEach(htmlFile -> {
            try {
                var doc = Jsoup.parse(Files.readString(htmlFile));
                var rel = tempDir.relativize(htmlFile).toString();
                if (doc.selectFirst("nav.nav") == null) {
                    violations.add(rel + ": missing nav.nav");
                }
                if (doc.selectFirst("footer.footer") == null) {
                    violations.add(rel + ": missing footer.footer");
                }
            } catch (IOException e) {
                violations.add("Parse error: " + htmlFile.getFileName());
            }
        });
    }

    assertTrue(violations.isEmpty(),
        "Navigation/footer violations:\n" + String.join("\n", violations));
}
```

### Anti-Patterns to Avoid

- **Per-test `generate()` calls:** Calling `siteGeneratorService.generate()` in each `@Test` method is slow (full Thymeleaf render + file I/O). Use `@BeforeAll` once.
- **Fail-fast on first broken link:** Use collected failures pattern so all broken links are surfaced in one run.
- **Mutating `siteProperties` in individual tests:** `SiteProperties` is a Spring singleton bean. Mutations in one test affect others. All configuration must happen in `@BeforeAll` before `generate()`.
- **Hardcoded page paths without `tempDir`:** Always resolve against `tempDir`, not a fixed string.
- **Checking only `index.html` for structural elements:** E2E-02 and E2E-03 require ALL generated pages — use `Files.walk()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML parsing | Custom regex/string matching | JSoup `doc.select()` | Already in project; handles malformed HTML, CSS selectors |
| File traversal | Manual recursive listing | `Files.walk()` | Standard Java NIO; handles nested dirs, lazy stream |
| Path resolution | String concatenation | `Path.resolve()` + `Files.exists()` | Handles `../` and relative segments correctly |
| Multi-failure assertion | Throwing on first failure | Collect list + `assertTrue(list.isEmpty(), list.toString())` | Diagnostic value; see CONTEXT.md D-09 |

## Common Pitfalls

### Pitfall 1: @BeforeAll with Spring @Autowired Beans

**What goes wrong:** Using a `static @BeforeAll` method (JUnit 5 default) means Spring cannot inject `@Autowired` fields because the test instance doesn't exist yet.

**Why it happens:** JUnit 5 creates a new test instance per test method by default. Static `@BeforeAll` runs before any instance is created.

**How to avoid:** Add `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` to the class. This makes JUnit reuse the same test instance, allowing `@BeforeAll` to be non-static and access `@Autowired` fields. [ASSUMED: Spring Boot 4.x supports this — used successfully in Spring Boot 3.x]

**Warning signs:** `NullPointerException` in `@BeforeAll` when accessing `siteGeneratorService` or `siteProperties`.

### Pitfall 2: @TempDir Scope with PER_CLASS

**What goes wrong:** With `@TestInstance(PER_CLASS)`, `@TempDir` is an instance field (not static). JUnit injects it before any test method but after the instance is created — before `@BeforeAll` runs.

**Why it happens:** JUnit guarantees `@TempDir` injection happens before `@BeforeAll` when using PER_CLASS lifecycle.

**How to avoid:** Declare `@TempDir Path tempDir;` as an instance field (not static). It will be available in `@BeforeAll`. No extra action needed.

**Warning signs:** `tempDir` is null in `@BeforeAll` if accidentally used with a static `@BeforeAll` without PER_CLASS.

### Pitfall 3: href Fragments on .tile-card Elements

**What goes wrong:** The `.tile-card` elements in `index.html` use the `href` attribute directly on `<a class="tile-card">` elements (not inside them). Standard `doc.select(".tile-card[href]")` finds them. But if tiles are `<div class="tile-card">` containing an `<a>`, the selector needs to be `.tile-card a[href]`.

**Why it happens:** Template structure may vary. Confirmed from existing test `givenActiveSeason_whenGenerate_thenStandingsTileLinkCorrect()` which uses `.tile-card[href*='...']` — tiles are `<a>` elements themselves.

**How to avoid:** Use `doc.select(".tile-card[href]")` (confirmed by line 1283 of existing test).

**Warning signs:** Empty elements list when checking tile links.

### Pitfall 4: SiteProperties Bean Mutation Isolation

**What goes wrong:** `SiteProperties` is a Spring singleton. The existing `SiteGeneratorServiceTest.setUp()` resets links to a default YouTube entry. If the E2E test class loads the same Spring context and runs after `SiteGeneratorServiceTest`, the links state depends on execution order.

**Why it happens:** `@SpringBootTest` may reuse an application context across test classes if configuration matches.

**How to avoid:** Explicitly set `siteProperties.setLinks(...)` in `@BeforeAll` before calling `generate()`. This mirrors the pattern in `SiteGeneratorServiceTest.setUp()` (line 158-161).

**Warning signs:** `links.html` contains unexpected or zero link cards.

### Pitfall 5: Asset Files (.css, .js, .png) Breaking Link Validation

**What goes wrong:** Link crawl may encounter `<link href="assets/css/style.css">` or `<script src="...js">` which do exist in the output. These are not `<a href>` elements, so JSoup selector `a[href]` correctly excludes them. But if using a broader `[href]` selector, CSS link tags are also picked up and may fail path resolution.

**Why it happens:** HTML `<link>` elements have `href` but are not navigation links.

**How to avoid:** Use `doc.select("a[href]")` (specifically the `a` element type) as specified in D-06. Do not broaden to `[href]`.

### Pitfall 6: `#` anchor hrefs in .tile-card "Top" footer link

**What goes wrong:** The footer contains `<a href="#">Top</a>` which would match the internal link filter if the `#` prefix check is incomplete.

**Why it happens:** `href="#"` is a fragment-only link with no target file.

**How to avoid:** Filter condition in D-07 explicitly skips `href` values starting with `#`. Confirmed: `!href.startsWith("#")`.

## Code Examples

Verified patterns from codebase:

### Test class skeleton (PER_CLASS lifecycle)
```java
// Pattern: SiteGeneratorServiceTest.java lines 1-23 + CONTEXT.md D-01..D-04
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SiteGeneratorE2ETest {

    @TempDir
    Path tempDir;

    @Autowired SiteGeneratorService siteGeneratorService;
    @Autowired SiteProperties siteProperties;
    // ... other repositories for data setup ...

    @BeforeAll
    void setUpOnce() {
        // test data creation (UUID suffix for isolation)
        // siteGeneratorService.setOutputDir(tempDir.toString());
        // siteProperties.setLinks(...);
        // siteGeneratorService.generate();
    }
}
```

### Confirmed CSS selectors (from layout.html, line-verified)
```java
// nav element: line 13 — <nav class="nav">
doc.selectFirst("nav.nav")

// footer element: line 65 — <footer class="footer">
doc.selectFirst("footer.footer")

// main content: line 62 — <main class="main" id="main-content">
doc.getElementById("main-content")
doc.selectFirst("#main-content")

// YouTube footer link: line 77 — href="https://www.youtube.com/@CommunityTeamCup"
doc.selectFirst("a[href*='youtube.com/@CommunityTeamCup']")

// tile cards: confirmed by existing test line 1283 — .tile-card is an <a> element
doc.select(".tile-card[href]")

// season filter dropdown: confirmed by existing test line 1111
doc.selectFirst("select#season-filter")
```

### Slugify helper (existing pattern)
```java
// Source: SiteGeneratorServiceTest.java line 168
private String slugify(String input) {
    return siteGeneratorService.slugify(input);
}
```

### JSoup file parsing (existing pattern)
```java
// Source: SiteGeneratorServiceTest.java line 256
var html = Files.readString(path);
var doc = Jsoup.parse(html);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Per-test site generation (`@BeforeEach`) | Shared generation (`@BeforeAll` + PER_CLASS) | Phase 49 (new class) | 7x faster test suite |
| Individual file checks in unit tests | Cross-cutting E2E crawl | Phase 49 (new class) | Catches broken links between pages |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `@TestInstance(PER_CLASS)` works with Spring Boot 4.0.5 `@SpringBootTest` for `@BeforeAll` + `@Autowired` | Architecture Patterns, Pitfall 1 | If incompatible, must use static fields + static `@BeforeAll` + `@Autowired` workaround via `ApplicationContext`; alternative: use `@BeforeEach` with flag to skip re-generation |

**Note:** PER_CLASS + @SpringBootTest is well-documented for Spring Boot 3.x and the pattern is identical in 4.x — risk is LOW but not verified against 4.0.5 release notes in this session.

## Open Questions

1. **`@TestInstance(PER_CLASS)` vs `@BeforeEach` with skip-flag**
   - What we know: PER_CLASS + non-static `@BeforeAll` is the clean approach. Alternative: `@BeforeEach` with a `static AtomicBoolean generated` flag to run `generate()` only once.
   - What's unclear: Whether Spring Boot 4.x has any behavioral difference for PER_CLASS.
   - Recommendation: Use PER_CLASS (cleaner). If CI fails, fall back to `@BeforeEach` + skip-flag pattern.

2. **Whether phases 45-47 are fully implemented**
   - What we know: REQUIREMENTS.md marks LINK-05, LINK-06, LINK-07..10, OVER-01..06 as "Pending" (phases 45-47). STATE.md says "Phase 48 — Landing Page Redesign" is current focus.
   - What's unclear: Are phases 45-47 actually implemented in code? The E2E test asserts their output.
   - Recommendation: The E2E test is a forward-looking validation — if phases 44-48 are done, the tests will pass. If not, they are the acceptance criteria. No impact on writing the test class.

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — test-only Java code with existing Spring Boot test stack).

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via Spring Boot 4.0.5 |
| Config file | `pom.xml` (Surefire lines 184-194) |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorE2ETest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| E2E-01 | All internal hrefs resolve to existing files | integration | `./mvnw test -Dtest=SiteGeneratorE2ETest#whenSiteGenerated_thenAllInternalLinksResolve` | Wave 0 |
| E2E-02 | All pages have nav + footer elements | integration | `./mvnw test -Dtest=SiteGeneratorE2ETest#whenSiteGenerated_thenAllPagesHaveNavAndFooter` | Wave 0 |
| E2E-03 | No page has empty main content | integration | `./mvnw test -Dtest=SiteGeneratorE2ETest#whenSiteGenerated_thenNoPageHasEmptyMain` | Wave 0 |
| E2E-04 | Landing page tile links resolve | integration | `./mvnw test -Dtest=SiteGeneratorE2ETest#whenSiteGenerated_thenLandingTilesResolve` | Wave 0 |
| E2E-05 | Links page has configured link URLs | integration | `./mvnw test -Dtest=SiteGeneratorE2ETest#whenSiteGenerated_thenLinksPageHasConfiguredLinks` | Wave 0 |
| E2E-06 | YouTube footer link on multiple page types | integration | `./mvnw test -Dtest=SiteGeneratorE2ETest#whenSiteGenerated_thenFooterYouTubePresentOnAllPageTypes` | Wave 0 |
| D-17 | Overview pages have season filter dropdown | integration | `./mvnw test -Dtest=SiteGeneratorE2ETest#whenSiteGenerated_thenOverviewPagesHaveSeasonFilter` | Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorE2ETest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java` — entire new class, covers all E2E-01..06 + D-17

*(One file; all 7+ tests live inside it)*

## Security Domain

Security enforcement not applicable — this is a test-only class. No user input, no endpoints, no authentication. Static site output is validated for structure/links only. [ASSUMED: `security_enforcement` absent from config.json — treated as enabled, but this phase introduces no production security surface]

No ASVS categories apply to a test-only file validation class.

## Project Constraints (from CLAUDE.md)

| Directive | Impact on Phase 49 |
|-----------|-------------------|
| Minimum 82% line coverage | New test class ADDS coverage; verify with `./mvnw verify` that threshold holds |
| TDD: write tests first, then implementation | This IS the implementation — no production code; write tests directly |
| BDD naming: `givenContext_whenAction_thenResult()` | All 7+ test methods must follow this; for E2E with `@BeforeAll` setup, `whenAction_thenResult()` is acceptable |
| No Flyway migration changes | Not applicable — test-only phase |
| No breaking URL/endpoint changes | Not applicable — test-only phase |
| `@Slf4j` + parameterized `{}` logging | Not needed in test class |
| Isolate test data completely | Test data must use UUID-suffixed entities (e.g., `"E2E Season " + uniqueSuffix`) |
| OSIV enabled | Lazy-loaded associations accessible in tests without `@EntityGraph` — no workarounds needed |
| Git: `test:` commit prefix | Commit message: `test(sitegen): add SiteGeneratorE2ETest for E2E site validation` |

## Sources

### Primary (HIGH confidence)
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Established `@SpringBootTest`, `@TempDir`, JSoup patterns; verified lines 1-1299
- `src/main/resources/templates/site/layout.html` — CSS selectors for `.nav`, `.footer`, `#main-content`, YouTube link confirmed; lines 13, 62, 65, 77
- `src/main/java/org/ctc/sitegen/SiteProperties.java` — Bean fields `links`, `youtubeChannelUrl`; full file verified
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — `generate()` method, `setOutputDir()`, page list; verified lines 1-250

### Secondary (MEDIUM confidence)
- JUnit 5 `@TestInstance(Lifecycle.PER_CLASS)` pattern — documented in JUnit 5 User Guide; used with Spring Boot 3.x in many projects [ASSUMED compatible with 4.x]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project; no new dependencies
- Architecture: HIGH — direct translation of CONTEXT.md decisions; CSS selectors confirmed against layout.html
- Pitfalls: HIGH — derived from reading existing test code and layout.html; one assumption flagged (PER_CLASS lifecycle)

**Research date:** 2026-04-17
**Valid until:** 2026-05-17 (stable domain — JUnit 5 APIs are stable)
