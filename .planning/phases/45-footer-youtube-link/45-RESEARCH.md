# Phase 45: Footer YouTube Link - Research

**Researched:** 2026-04-16
**Domain:** Static site generation — Thymeleaf layout template, CSS, JUnit/JSoup tests
**Confidence:** HIGH

## Summary

This phase adds a single hardcoded external link to the shared footer in `layout.html`. All decisions are locked in CONTEXT.md. No new CSS classes, no new Java code, no new service logic, no configuration changes. The change touches exactly two files: the Thymeleaf layout template and the existing `SiteGeneratorServiceTest.java`.

The footer currently has three link positions: "Top" (anchor `#`), "Archive", and an optional active-season link guarded by `th:if`. The YouTube link is unconditional (no `th:if` guard) and is placed after the last existing link, following the same `footer-sep` + `footer-link` pattern. Because it lives in `layout.html`, it appears on every generated page automatically.

The project enforces TDD: tests must be written first (RED), then the implementation (GREEN). The test file `SiteGeneratorServiceTest.java` already has a footer test (`givenActiveSeason_whenGenerate_thenFooterContainsUsefulLinks`) that uses JSoup selectors — new assertions follow the identical pattern.

**Primary recommendation:** Write a new `@Test` method in `SiteGeneratorServiceTest.java` asserting the YouTube link's `href` and `target` attributes, then add the two HTML lines to the footer in `layout.html`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Footer YouTube link (rendering) | Frontend Server (SSR/Thymeleaf) | — | Static HTML generated from layout.html; no data from DB needed |
| Footer YouTube link (test verification) | Testing layer | — | JSoup parses generated HTML output; no browser needed |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Thymeleaf | Bundled with Spring Boot 4.x | Server-side HTML templating | Project standard; `layout.html` is already Thymeleaf [VERIFIED: codebase grep] |
| JSoup | Project dependency | HTML parsing in tests | Already used in all `SiteGeneratorServiceTest` footer/link tests [VERIFIED: codebase grep] |
| JUnit 5 | Bundled with Spring Boot 4.x | Unit/integration test framework | Project standard [VERIFIED: CLAUDE.md] |

### Supporting

No additional libraries needed. This phase is a pure template + test change.

**Installation:** No new dependencies required. [VERIFIED: codebase inspection]

## Architecture Patterns

### System Architecture Diagram

```
Test (SiteGeneratorServiceTest)
  |
  | calls generate()
  v
SiteGeneratorService.generate()
  |
  | processes layout.html via Thymeleaf
  v
layout.html footer section
  |
  | hardcoded YouTube <a> element
  v
Generated HTML files (index.html, season/*/standings.html, ...)
  |
  | JSoup.parse(html)
  v
Assertions on .footer .footer-link[href*=youtube]
```

### Recommended Project Structure

No structural changes. Files affected:

```
src/main/resources/templates/site/
└── layout.html                  # footer section lines 64-77 — add YouTube link

src/test/java/org/ctc/sitegen/
└── SiteGeneratorServiceTest.java  # add test assertions for LINK-05 and LINK-06
```

### Pattern 1: Footer Link (existing — to be replicated)

**What:** Plain `<a>` with `footer-link` class, preceded by a `footer-sep` dot separator.
**When to use:** Every footer link that is unconditional.
**Example:**
```html
<!-- Source: src/main/resources/templates/site/layout.html lines 64-75 [VERIFIED] -->
<span class="footer-sep" aria-hidden="true">&middot;</span>
<a href="https://www.youtube.com/@CommunityTeamCup"
   class="footer-link"
   target="_blank"
   rel="noopener">YouTube</a>
```

### Pattern 2: JSoup Footer Test (existing — to be replicated)

**What:** Parse generated `index.html` with JSoup, select `.footer .footer-link`, assert on `href` and attributes.
**When to use:** Verifying footer content in generated static HTML.
**Example:**
```java
// Source: SiteGeneratorServiceTest.java line 834 [VERIFIED]
var footerLinks = doc.select(".footer .footer-link");
assertTrue(footerLinks.stream().anyMatch(
    a -> "https://www.youtube.com/@CommunityTeamCup".equals(a.attr("href"))),
    "Footer should contain YouTube link");
```

### Anti-Patterns to Avoid

- **Adding `th:if` guard on the YouTube link:** CONTEXT.md D-05 explicitly prohibits this. The link is unconditional.
- **Using `th:href` for a hardcoded external URL:** `th:href` is for dynamic Thymeleaf expressions. Static external URLs use plain `href`.
- **Omitting `rel="noopener"`:** All external links opening in a new tab must include `rel="noopener"` per CONTEXT.md D-03.
- **Placing YouTube link before the season link:** CONTEXT.md D-04 specifies placement after the last existing link (after the active season link).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| External link opening in new tab | Custom JS click handler | `target="_blank" rel="noopener"` HTML attribute | Standard HTML — no JS needed |
| Link styling | Inline `style=` attribute | `.footer-link` CSS class (style.css line 442) | Project constraint: no inline styles; class already provides hover transition |

**Key insight:** This is a pure HTML addition. No Java, no service logic, no CSS changes needed.

## Common Pitfalls

### Pitfall 1: footer-sep duplication when activeSeasonSlug is null
**What goes wrong:** The existing active-season block has its own `footer-sep` inside a `th:if`. If YouTube link is placed after it, the separator before YouTube is always rendered even when the season link is absent — this is intentional per D-05 (YouTube link is always visible, so its separator must always be visible too).
**Why it happens:** The season link uses a conditional `footer-sep`; the YouTube link must have its own unconditional `footer-sep` before it.
**How to avoid:** Add the YouTube `footer-sep` and `<a>` outside any `th:if` block.
**Warning signs:** If the YouTube `footer-sep` is placed inside the season's `th:if` block, it only renders when a season exists.

### Pitfall 2: Testing only index.html but not season subpages
**What goes wrong:** LINK-06 requires the link on *all generated pages*, not just `index.html`. Tests that only check `index.html` miss coverage.
**Why it happens:** `index.html` is the easiest page to test; season subpages are overlooked.
**How to avoid:** Add at least one assertion on a season subpage (e.g., `season/{slug}/standings.html`) in the test for LINK-06. Since the link is in `layout.html`, it renders on all pages — but the test must demonstrate this.
**Warning signs:** Test covers LINK-05 but not LINK-06 separately.

## Code Examples

### Full footer after change (lines 64-77 target state)

```html
<!-- Source: layout.html footer, updated for Phase 45 [VERIFIED: existing lines 64-75] -->
<footer class="footer">
    <div class="footer-links">
        <a href="#" class="footer-link">Top</a>
        <span class="footer-sep" aria-hidden="true">&middot;</span>
        <a th:href="${rootPath + '/archive.html'}" class="footer-link">Archive</a>
        <span th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"
              class="footer-sep" aria-hidden="true">&middot;</span>
        <a th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"
           th:href="${rootPath + '/season/' + activeSeasonSlug + '/standings.html'}"
           th:text="${activeSeasonName}"
           class="footer-link"></a>
        <span class="footer-sep" aria-hidden="true">&middot;</span>
        <a href="https://www.youtube.com/@CommunityTeamCup"
           class="footer-link"
           target="_blank"
           rel="noopener">YouTube</a>
    </div>
    <p>Community Team Cup &mdash; Gran Turismo Racing League</p>
</footer>
```

### Test methods (TDD RED phase — both requirements)

```java
// LINK-05: YouTube link present in footer
@Test
void givenLayout_whenGenerate_thenFooterContainsYouTubeLink() throws IOException {
    // when
    siteGeneratorService.generate();

    // then
    var html = Files.readString(tempDir.resolve("index.html"));
    var doc = Jsoup.parse(html);
    var footerLinks = doc.select(".footer .footer-link");
    assertTrue(footerLinks.stream().anyMatch(
            a -> "https://www.youtube.com/@CommunityTeamCup".equals(a.attr("href"))),
            "Footer should contain YouTube link");
}

// LINK-06: YouTube link present on all page types (season subpage check)
@Test
void givenSeasonPage_whenGenerate_thenFooterContainsYouTubeLink() throws IOException {
    // when
    siteGeneratorService.generate();

    // then — verify on a season subpage to confirm layout inheritance
    var seasonDir = Files.list(tempDir.resolve("season")).findFirst()
            .orElseThrow(() -> new AssertionError("No season directory generated"));
    var html = Files.readString(seasonDir.resolve("standings.html"));
    var doc = Jsoup.parse(html);
    var footerLinks = doc.select(".footer .footer-link");
    assertTrue(footerLinks.stream().anyMatch(
            a -> "https://www.youtube.com/@CommunityTeamCup".equals(a.attr("href"))),
            "Season subpage footer should contain YouTube link");
}
```

Additionally, the external link attributes (`target="_blank"`, `rel="noopener"`) can be asserted:

```java
var youtubeLink = doc.select(".footer .footer-link[href='https://www.youtube.com/@CommunityTeamCup']").first();
assertNotNull(youtubeLink, "YouTube footer link must exist");
assertEquals("_blank", youtubeLink.attr("target"), "YouTube link must open in new tab");
assertEquals("noopener", youtubeLink.attr("rel"), "YouTube link must have rel=noopener");
assertEquals("YouTube", youtubeLink.text(), "YouTube link text must be 'YouTube'");
```

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via Spring Boot Test) |
| Config file | `pom.xml` (Surefire + Failsafe) |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest -q` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LINK-05 | Footer contains YouTube link on index.html | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenLayout_whenGenerate_thenFooterContainsYouTubeLink -q` | ❌ Wave 0 |
| LINK-06 | YouTube link present on season subpages (layout inheritance) | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeasonPage_whenGenerate_thenFooterContainsYouTubeLink -q` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest -q`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `SiteGeneratorServiceTest.java` — add `givenLayout_whenGenerate_thenFooterContainsYouTubeLink` (LINK-05)
- [ ] `SiteGeneratorServiceTest.java` — add `givenSeasonPage_whenGenerate_thenFooterContainsYouTubeLink` (LINK-06)

*(No new files needed — both tests extend the existing test class)*

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | no | Static hardcoded URL, no user input |
| V6 Cryptography | no | — |

No security concerns. The YouTube URL is a hardcoded constant with no user input surface. External link security is addressed by `rel="noopener"` (prevents opener access from the new tab).

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — pure template and test file change).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Season subpages are generated under `tempDir/season/{slug}/standings.html` in tests | Code Examples (LINK-06 test) | Test would throw AssertionError before assertion; low risk — pattern matches existing test setup |

## Open Questions

None — all decisions are locked in CONTEXT.md. The implementation path is fully determined.

## Sources

### Primary (HIGH confidence)

- `src/main/resources/templates/site/layout.html` — actual footer HTML at lines 64-77 [VERIFIED: Read tool]
- `src/main/resources/static/site/css/style.css` — `.footer-link` class at line 442, `.footer-sep` at line 450 [VERIFIED: Read tool]
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — footer test pattern at lines 826-840 [VERIFIED: Read tool]
- `.planning/phases/45-footer-youtube-link/45-CONTEXT.md` — all locked decisions [VERIFIED: Read tool]
- `CLAUDE.md` — project constraints (TDD, test naming, coverage minimum 82%) [VERIFIED: Read tool]

### Secondary (MEDIUM confidence)

None needed — all findings are from direct codebase inspection.

## Project Constraints (from CLAUDE.md)

- **TDD:** Tests written first (RED), then implementation (GREEN). Feature sequence: Unit Tests → Implementation → Integration Tests.
- **Test Naming:** `givenContext_whenAction_thenExpectedResult()` with `// given / when / then` comments.
- **Test Coverage:** Minimum 82% line coverage. Adding tests for new behavior maintains coverage.
- **No Inline Styles:** `.footer-link` CSS class must be used, not `style=` attribute.
- **Backward Compatibility:** No breaking changes to existing URLs. The YouTube link is additive — no existing links change.
- **No Flyway Changes:** Not applicable (no DB changes).
- **Thymeleaf Templates Lean:** No complex SpEL in template. The YouTube link is a static `<a>` — no expressions needed.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified from direct codebase inspection
- Architecture: HIGH — existing patterns directly observed in layout.html and test file
- Pitfalls: HIGH — derived from direct reading of current footer structure

**Research date:** 2026-04-16
**Valid until:** 2026-05-16 (stable — pure template/test change, no external dependencies)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Use plain text "YouTube" as the link label (consistent with text-based footer links).
- **D-02:** Use the `footer-link` CSS class (style.css line 442) — no new CSS needed.
- **D-03:** Add `target="_blank" rel="noopener"` (external link).
- **D-04:** Place after the last existing footer link (after active season link), with a `footer-sep` dot separator before it.
- **D-05:** The YouTube link is always visible — no `th:if` guard (unconditional).
- **D-06:** Hardcoded URL `https://www.youtube.com/@CommunityTeamCup` — not configurable.

### Claude's Discretion

- Whether to add a small CSS modifier for external links (e.g., subtle icon) — not required but acceptable.

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LINK-05 | Footer contains a YouTube link to `https://www.youtube.com/@CommunityTeamCup` | Hardcoded `<a>` in layout.html footer; `footer-link` CSS class already styled; `footer-sep` pattern established |
| LINK-06 | YouTube footer link is present on all generated pages | Placing the link in `layout.html` (shared layout) guarantees it renders on every generated page; verified by testing a season subpage in addition to index.html |
</phase_requirements>
