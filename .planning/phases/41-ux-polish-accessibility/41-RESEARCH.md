# Phase 41: UX Polish & Accessibility - Research

**Researched:** 2026-04-16
**Domain:** Static site CSS/HTML — accessibility, UX polish, Thymeleaf templates
**Confidence:** HIGH

## Summary

Phase 41 is a pure front-end polish phase for the CTC static site. It touches two layers: the shared Thymeleaf layout template (`layout.html`) and the site CSS (`style.css`), with minor changes to `matchday.html`, `index.html`, and `driver-profile.html`. No new pages, no service restructuring, and no database changes are required.

The phase addresses eight requirements that divide cleanly into four categories: accessibility (UX-01 skip-link, UX-07 aria-label), visual feedback (UX-04 winner highlight, UX-05 scroll indicator, UX-06 footer links), micro-interactions (UX-08 hover transitions, UX-09 cursor:pointer), and code quality (QUAL-01 inline style removal). All decisions were locked in CONTEXT.md and the UI-SPEC.md was already approved by the UI checker.

The one non-trivial Java change is adding `homeTeamWon` and `awayTeamWon` booleans to `RaceView` and computing them in `toRaceView()` in `SiteGeneratorService`. The winning team is already computed in `toRaceView()` (homeTotal/awayTotal), so the addition is a two-line extension of existing logic. All other changes are HTML attributes and CSS rules.

**Primary recommendation:** Implement in two tasks — (1) Java model + service (`RaceView` winner fields + test assertions in `SiteGeneratorServiceTest`) and (2) all CSS and HTML changes. The Java task must precede the template task because `matchday.html` and `index.html` reference the new `race.homeTeamWon` / `race.awayTeamWon` fields.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** `.match-team-winner` applies `color: var(--accent)` + `background: rgba(79,195,247,0.15)` with `padding: 4px 8px` and `border-radius: var(--radius-sm)`.
**D-02:** CSS class `.match-team-winner` applied conditionally via `th:class` based on pre-computed winner data from the service. No score parsing in Thymeleaf.
**D-03:** Right-edge gradient fade `::after` pseudo-element on `.table-wrap` with `linear-gradient(to right, transparent, var(--bg-card))`. Pure CSS.
**D-04:** Gradient behind `@media (max-width: 768px)` only. Static fade acceptable; no JS scroll detection required.
**D-05:** Footer: top row centered link bar ("Top", "Archive", active season name) separated by `·`. Bottom row: existing CTC branding text.
**D-06:** Footer links use `.footer-link` class, styled like `.breadcrumb-link` (dim color, accent on hover, 0.2s transition).
**D-07:** Add `transition: background-color 0.2s` to `tr`. Add transition to new footer links. Do NOT touch existing transitions on `.nav-links a`, `.entity-link`, `.subnav-link`, `.breadcrumb-link`.
**D-08:** `cursor: pointer` on `a`, `label[for]`, `[role="button"]` globally.
**D-09:** Skip-link as first child of `<body>` in `layout.html`. Visually hidden until focused. Target: `<main id="main-content">`.
**D-10:** `aria-label="Toggle navigation menu"` on the `<label>` (not the `<input>`). `role="button"` on the label. Remove `aria-label` from the `<input>`.
**D-11:** Replace `style="margin-bottom: 24px;"` (line 6) and `style="margin-top: 24px;"` (line 47) in `driver-profile.html` with classes `.driver-header` and `.section-gap`. `archive.html` is already clean.

### Claude's Discretion

- Exact gradient width and opacity for the scroll indicator fade (UI-SPEC: width 40px)
- Skip-link styling details — colors, font-size, z-index (UI-SPEC: fixed top:8px left:8px, z-index:9999, accent bg, black text, 14px)
- Whether to add `aria-expanded` to the nav toggle for open/close state communication
- Footer link separator styling (dot vs pipe vs dash — UI-SPEC confirms `·`)
- CSS class names for inline style replacements (UI-SPEC: `.driver-header` and `.section-gap`)
- Whether `cursor: pointer` covers `.match-card` elements (UI-SPEC: no, only `a`, `label[for]`, `[role="button"]`)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UX-01 | Skip-to-content link for keyboard navigation | Standard WCAG 2.1 skip-link pattern; layout.html `<body>` as insertion point confirmed; `<main>` needs `id="main-content"` |
| UX-04 | Match winner team is visually highlighted in match cards | `RaceView` homeTotal/awayTotal already computed; needs two boolean fields; both `matchday.html` and `index.html` render `.match-team` spans |
| UX-05 | Mobile tables show scroll indicator when horizontally scrollable | `.table-wrap` has `overflow-x: auto` already; needs `position: relative` + `::after` pseudo-element; 768px breakpoint established |
| UX-06 | Footer contains useful links (back to top, archive, active season) | `activeSeasonSlug` already passed to all templates via `writeTemplate()`; footer is in `layout.html` |
| UX-07 | Nav toggle button has proper aria-label for screen readers | Current `aria-label` is on the hidden `<input>` (AT-invisible); must move to visible `<label>` element |
| UX-08 | Hover transitions on table rows and links (150-300ms) | `tr:hover` rule exists; needs `transition: background-color 0.2s` added; existing link transitions must not be touched |
| UX-09 | cursor:pointer on all clickable elements in site CSS | Single global rule block in `style.css` |
| QUAL-01 | No inline styles in archive.html and driver-profile.html | `archive.html` already clean; `driver-profile.html` lines 6 and 47 confirmed to have inline styles |
</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Skip-link (UX-01) | Frontend (HTML template) | CSS | DOM placement in `layout.html`; styling in `style.css` |
| Winner highlight (UX-04) | Service (Java) | Frontend template + CSS | Win determination is data logic; service pre-computes, template applies class |
| Scroll indicator (UX-05) | CSS | — | Pure CSS pseudo-element; no JS, no server-side data |
| Footer links (UX-06) | Frontend (HTML template) | CSS | `activeSeasonSlug` already available in template context; CSS for styling |
| Nav aria-label (UX-07) | Frontend (HTML template) | — | HTML attribute change only; no CSS needed |
| Hover transitions (UX-08) | CSS | — | Pure CSS transitions on existing rules |
| cursor:pointer (UX-09) | CSS | — | Single global rule; pure CSS |
| Inline style removal (QUAL-01) | Frontend (HTML template) | CSS | Remove attribute from template; add utility class to CSS |

---

## Standard Stack

### Core (all verified by direct file inspection)
| Library / Tool | Version | Purpose | Why Standard |
|----------------|---------|---------|--------------|
| Thymeleaf | Spring Boot 4.x built-in | Server-side HTML templates | Project standard; `layout.html` is shared fragment |
| Custom CSS (style.css) | n/a | All site styling via CSS variables | No build tool; direct CSS file |
| Java records (`RaceView`) | Java 25 | View model for race data passed to templates | Established pattern in this codebase |
| Jsoup | Already on classpath (test) | HTML parsing in integration tests | Used in all 41 existing `SiteGeneratorServiceTest` tests |

[VERIFIED: direct file read of pom.xml, style.css, RaceView.java, SiteGeneratorServiceTest.java]

### No New Dependencies Required

All phase 41 work uses only existing infrastructure. No new Maven dependencies, no npm packages, no JS libraries.

---

## Architecture Patterns

### System Architecture Diagram

```
Service Layer (Java)
  SiteGeneratorService.toRaceView()
    ├── computes homeTotal, awayTotal  [exists]
    ├── NEW: homeTeamWon = hasResults && homeTotal > awayTotal
    └── NEW: awayTeamWon = hasResults && awayTotal > homeTotal
         │
         ▼
RaceView record (Java)
    ├── homeTeamWon: boolean  [NEW field]
    └── awayTeamWon: boolean  [NEW field]
         │
         ▼
Thymeleaf Templates (HTML)
  layout.html
    ├── <body> first child: skip-link [NEW]   → targets #main-content
    ├── <main id="main-content">              [MODIFIED — add id]
    ├── <label role="button" aria-label="..."> [MODIFIED — add attrs, remove from input]
    └── <footer>: link bar + branding         [MODIFIED — add link row]
  matchday.html
    └── .match-team span: th:class with homeTeamWon/awayTeamWon  [MODIFIED]
  index.html
    └── .match-team span: same th:class pattern  [MODIFIED]
  driver-profile.html
    └── Replace 2 inline styles with .driver-header / .section-gap  [MODIFIED]
         │
         ▼
CSS (style.css)
  NEW rules: .skip-link, .match-team-winner, .footer-link, .driver-header, .section-gap
  MODIFIED: tr { transition }, .table-wrap { position: relative }, .table-wrap::after (mobile)
  GLOBAL: html { scroll-behavior: smooth }, a/label[for]/[role="button"] { cursor: pointer }
```

### Recommended Project Structure (unchanged)

```
src/main/java/org/ctc/sitegen/
  model/RaceView.java          ← add homeTeamWon, awayTeamWon fields
  SiteGeneratorService.java    ← set winner booleans in toRaceView()

src/main/resources/
  templates/site/
    layout.html                ← skip-link, main#id, aria-label, footer links
    matchday.html              ← th:class winner conditional
    index.html                 ← th:class winner conditional
    driver-profile.html        ← remove 2 inline styles
  static/site/css/
    style.css                  ← all new CSS rules

src/test/java/org/ctc/sitegen/
  SiteGeneratorServiceTest.java ← new test methods for UX-01, UX-04, UX-06, UX-07
```

### Pattern 1: Winner Boolean in RaceView Record

**What:** Add two derived boolean fields to the `RaceView` record. The existing `homeTotal` and `awayTotal` are already computed in `toRaceView()`.

**When to use:** Any time a template needs a pre-computed boolean to avoid SpEL arithmetic in Thymeleaf.

```java
// Source: RaceView.java (current) + pattern from existing hasResults field
public record RaceView(String homeTeamShortName, String awayTeamShortName,
                       String track, String car,
                       int homeTotal, int awayTotal,
                       boolean hasResults,
                       boolean homeTeamWon,   // NEW
                       boolean awayTeamWon,   // NEW
                       List<ResultView> results) {
    // homeTeamWon = hasResults && homeTotal > awayTotal
    // awayTeamWon = hasResults && awayTotal > homeTotal
    // draw: neither wins (equal scores with results)
}
```

[VERIFIED: existing RaceView.java uses record syntax; hasResults boolean follows same pattern]

### Pattern 2: Conditional Class via th:class

**What:** Apply `.match-team-winner` conditionally on `.match-team` spans based on boolean fields from `RaceView`.

**When to use:** Whenever a template class depends on pre-computed service data.

```html
<!-- Source: matchday.html existing .match-team pattern -->
<span th:class="${race.homeTeamWon ? 'match-team match-team-winner text-white' : 'match-team text-white'}"
      th:text="${race.homeTeamShortName}"></span>
<span class="match-score" th:if="${race.hasResults}" th:text="${race.score}"></span>
<span class="match-score match-score-draw" th:unless="${race.hasResults}">vs</span>
<span th:class="${race.awayTeamWon ? 'match-team match-team-winner' : 'match-team'}"
      th:text="${race.awayTeamShortName}"></span>
```

Note: `text-white` is already on the home team span in the existing template. Preserve it. The away team span currently has no extra class — the winner class alone provides accent color.

[VERIFIED: matchday.html lines 12-15 inspected]

### Pattern 3: Standard Skip-Link (WCAG 2.1)

**What:** Visually hidden link as first DOM element; slides into view on keyboard focus.

```html
<!-- Source: CONTEXT.md D-09, UI-SPEC.md interaction contract -->
<!-- First child of <body> in layout.html -->
<a href="#main-content" class="skip-link">Skip to main content</a>
```

```css
/* CSS in style.css */
.skip-link {
    position: absolute;
    left: -9999px;
    top: auto;
    width: 1px;
    height: 1px;
    overflow: hidden;
}
.skip-link:focus {
    position: fixed;
    top: 8px;
    left: 8px;
    z-index: 9999;
    padding: 8px 16px;
    background: var(--accent);
    color: #000;
    border-radius: var(--radius-sm);
    font-size: 14px;
    font-weight: 600;
    text-decoration: none;
    width: auto;
    height: auto;
    overflow: visible;
}
```

[VERIFIED: UI-SPEC.md interaction contract confirms exact values; WCAG 2.1 standard pattern [ASSUMED]]

### Pattern 4: Pseudo-Element Scroll Indicator

**What:** `::after` gradient fade on `.table-wrap` to signal horizontal scroll on mobile.

```css
/* style.css — add position: relative to .table-wrap */
.table-wrap {
    /* existing rules ... */
    position: relative;   /* NEW: enables ::after positioning */
}

/* Mobile only */
@media (max-width: 768px) {
    .table-wrap::after {
        content: '';
        position: absolute;
        right: 0;
        top: 0;
        width: 40px;
        height: 100%;
        background: linear-gradient(to right, transparent, var(--bg-card));
        pointer-events: none;
        border-radius: 0 8px 8px 0;
    }
}
```

[VERIFIED: UI-SPEC.md confirms width 40px; `.table-wrap` existing CSS verified at style.css lines 160-166]

### Pattern 5: Footer Link Bar with Smooth Scroll

**What:** Two-row footer. Top row is a centered link bar with `·` separators. The `#top` anchor is a standard browser scroll target requiring `id="top"` on `<body>` or using CSS `scroll-behavior: smooth` with `href="#"`.

**Implementation note:** `href="#"` scrolls to top of page without a named anchor. `href="#top"` requires `id="top"` on `<html>` or `<body>`. The simplest approach is `href="#"` with `scroll-behavior: smooth` on `html`.

```html
<!-- layout.html footer section -->
<footer class="footer">
    <div class="footer-links">
        <a href="#" class="footer-link">Top</a>
        <span class="footer-sep" aria-hidden="true">·</span>
        <a th:href="${rootPath + '/archive.html'}" class="footer-link">Archive</a>
        <span th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"
              class="footer-sep" aria-hidden="true">·</span>
        <a th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"
           th:href="${rootPath + '/season/' + activeSeasonSlug + '/standings.html'}"
           th:text="${activeSeasonName}"
           class="footer-link"></a>
    </div>
    <p>Community Team Cup — Gran Turismo Racing League</p>
</footer>
```

**Context variable note:** `activeSeasonSlug` is already passed in `writeTemplate()`. `activeSeasonName` (the display label string) is NOT currently passed as a separate variable. The service will need to also pass `activeSeasonName` (from `activeSeason.getDisplayLabel()`) or it can be derived from `activeSeasonSlug` — but that loses readability. Recommend passing `activeSeasonName` explicitly.

[VERIFIED: writeTemplate() signature and context variables inspected in SiteGeneratorService.java lines 378-392]

### Anti-Patterns to Avoid

- **Score parsing in Thymeleaf:** Never use SpEL arithmetic to determine the winner from the score string. Pre-compute in the service (D-02).
- **Animating the skip-link:** The skip-link uses CSS `position: fixed` on `:focus`. A CSS transition on position would create WCAG motion issues. No transition on the skip-link.
- **`aria-label` on hidden inputs:** `<input type="checkbox">` with `display: none` is ignored by assistive technology. The visible `<label>` is the correct target for `aria-label`.
- **Gradient overlay blocking clicks:** The `::after` scroll indicator must have `pointer-events: none` to avoid blocking table interaction.
- **`href="#top"` without anchor:** Either use `href="#"` or add `id="top"` to `<html>`. Using `href="#top"` without the id causes a 404-style navigation error in some browsers.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Winner determination | SpEL score parsing in templates | Boolean fields in RaceView pre-computed in service | Template logic violates CLAUDE.md "Keep Thymeleaf Templates Lean" |
| Scroll detection JS | JavaScript scroll event listener | CSS `::after` pseudo-element gradient | D-04 explicitly allows static fade; no JS needed |
| Custom icon font | Icon font for hamburger | Existing inline SVG | Already implemented; no dependency needed |
| CSS animation library | Animate.css or similar | Native CSS `transition` property | 200ms transitions need no library |
| Third-party accessibility library | axe-core or similar | Native HTML attributes (aria-label, role, id) | Phase scope is minimal; native attrs sufficient |

---

## Common Pitfalls

### Pitfall 1: RaceView Record Immutability
**What goes wrong:** Java records are immutable. Adding fields to the record requires updating all call sites where `new RaceView(...)` is constructed.
**Why it happens:** Records generate canonical constructors; adding parameters breaks existing instantiation in `toRaceView()` and in tests.
**How to avoid:** Add `homeTeamWon` and `awayTeamWon` as the last two positional parameters before `results`. Update the single `new RaceView(...)` call in `toRaceView()` (line 500) and any test data that directly constructs a `RaceView`.
**Warning signs:** Compilation error "wrong number of arguments to RaceView constructor".

### Pitfall 2: index.html Also Renders Match Cards
**What goes wrong:** Only updating `matchday.html` for the winner highlight and forgetting that `index.html` renders `.match-team` spans for the last matchday.
**Why it happens:** CONTEXT.md D-02 mentions both files but they're easy to miss if only looking at the matchday template.
**How to avoid:** The canonical refs in CONTEXT.md lists both `matchday.html` and `index.html`. Update both in the same commit.
**Warning signs:** Matchday pages show winner highlight; index page last-matchday section does not.

### Pitfall 3: Footer Active Season Name Variable
**What goes wrong:** The footer active season link needs a display label string (e.g. "2026 | #1 | Season Name"). `activeSeasonSlug` (the URL-safe slug) is already passed, but the human-readable name is not.
**Why it happens:** `writeTemplate()` passes `activeSeasonSlug` but not `activeSeasonName`.
**How to avoid:** Add `ctx.setVariable("activeSeasonName", activeSeason != null ? activeSeason.getDisplayLabel() : "")` in `writeTemplate()` alongside the existing `activeSeasonSlug` assignment.
**Warning signs:** `th:text="${activeSeasonName}"` renders empty in the footer.

### Pitfall 4: `position: relative` on `.table-wrap` Breaking Border-Radius
**What goes wrong:** Adding `position: relative` to `.table-wrap` is needed for the `::after` pseudo-element. This is safe but worth noting that `border-radius: 8px` and `overflow: hidden` are already set — the `::after` needs matching border-radius on its right corners.
**Why it happens:** Without `border-radius: 0 8px 8px 0` on `::after`, the gradient overflows the rounded corners.
**How to avoid:** Add `border-radius: 0 8px 8px 0` to `.table-wrap::after` (UI-SPEC already specifies this).
**Warning signs:** Gradient visible outside the card corners on mobile.

### Pitfall 5: `aria-label` Move Requires Careful diff
**What goes wrong:** The `<input>` currently has `aria-label="Toggle navigation"`. The `<label>` needs `aria-label="Toggle navigation menu"` (different text per D-10) plus `role="button"`. The `aria-label` must be removed from the `<input>`.
**Why it happens:** Both the old and new aria-label must be changed; it's easy to add to the label without removing from the input.
**How to avoid:** Edit both elements in one change. The input becomes `<input type="checkbox" id="nav-toggle" class="nav-toggle-input">` with no aria-label.
**Warning signs:** Accessibility audit shows duplicate or conflicting aria-labels.

### Pitfall 6: Jsoup Assertions for New Tests
**What goes wrong:** New test assertions for skip-link, winner class, footer links, and aria-labels must use Jsoup selectors correctly.
**Why it happens:** Jsoup uses CSS selector syntax; `aria-label` attribute selectors use `[aria-label="value"]` syntax.
**How to avoid:** Use established Jsoup patterns from existing tests (e.g. `doc.select(".skip-link")`, `doc.select("a[href='#']")`, `doc.select("label[aria-label='Toggle navigation menu']")`).
**Warning signs:** Test assertions pass vacuously because the selector matches nothing.

---

## Code Examples

### Winner Fields in toRaceView()

```java
// Source: SiteGeneratorService.java line 500 — current RaceView construction
// CURRENT:
return new RaceView(homeShortName, awayShortName,
        trackName, carName, homeTotal, awayTotal, !race.getResults().isEmpty(), results);

// UPDATED (add homeTeamWon, awayTeamWon before results):
boolean hasResults = !race.getResults().isEmpty();
boolean homeTeamWon = hasResults && homeTotal > awayTotal;
boolean awayTeamWon = hasResults && awayTotal > homeTotal;
return new RaceView(homeShortName, awayShortName,
        trackName, carName, homeTotal, awayTotal, hasResults,
        homeTeamWon, awayTeamWon, results);
```

[VERIFIED: SiteGeneratorService.java lines 497-501 inspected]

### Test Assertion for Skip-Link

```java
// Pattern: add to givenActiveSeason_whenGenerate_thenCreatesIndexPage or new dedicated test
// Source: existing Jsoup test pattern in SiteGeneratorServiceTest.java

@Test
void givenLayout_whenGenerate_thenSkipLinkIsFirstBodyChild() throws IOException {
    // when
    siteGeneratorService.generate();

    // then
    var html = Files.readString(tempDir.resolve("index.html"));
    var doc = Jsoup.parse(html);
    var firstBodyChild = doc.body().children().first();
    assertNotNull(firstBodyChild, "Body should have children");
    assertEquals("a", firstBodyChild.tagName(), "First body child should be <a> skip-link");
    assertEquals("#main-content", firstBodyChild.attr("href"), "Skip-link should target #main-content");
    assertTrue(firstBodyChild.hasClass("skip-link"), "Skip-link should have class 'skip-link'");
}
```

### Test Assertion for Winner Class

```java
@Test
void givenRaceWithResults_whenGenerate_thenMatchdayShowsWinnerHighlight() throws IOException {
    // given — setUp() race has driver1+driver2 for GTNR vs driver3+driver4 for GP1R
    // homeTotal (GTNR): P1=20, P3=14 = 34; awayTotal (GP1R): P2=17+FL3=14+3=17 so check actual
    siteGeneratorService.generate();

    // then
    var html = Files.readString(seasonDir().resolve("matchday/spieltag-1.html"));
    var doc = Jsoup.parse(html);
    // At least one .match-team-winner class should appear
    var winners = doc.select(".match-team-winner");
    assertFalse(winners.isEmpty(), "Matchday should show at least one winner highlight when race has results");
}
```

### Test Assertion for Footer Links

```java
@Test
void givenActiveSeason_whenGenerate_thenFooterContainsUsefulLinks() throws IOException {
    siteGeneratorService.generate();

    var html = Files.readString(tempDir.resolve("index.html"));
    var doc = Jsoup.parse(html);
    var footerLinks = doc.select(".footer .footer-link");
    assertFalse(footerLinks.isEmpty(), "Footer should contain .footer-link elements");
    assertTrue(footerLinks.stream().anyMatch(a -> "#".equals(a.attr("href"))),
            "Footer should have a 'Top' link with href='#'");
    assertTrue(footerLinks.stream().anyMatch(a -> a.attr("href").contains("archive.html")),
            "Footer should have an Archive link");
}
```

### Test Assertion for Nav Toggle aria-label

```java
@Test
void givenLayout_whenGenerate_thenNavToggleLabelHasAriaLabel() throws IOException {
    siteGeneratorService.generate();

    var html = Files.readString(tempDir.resolve("index.html"));
    var doc = Jsoup.parse(html);
    var label = doc.selectFirst("label.nav-toggle-label");
    assertNotNull(label, "Nav toggle label should exist");
    assertEquals("Toggle navigation menu", label.attr("aria-label"),
            "Nav toggle label should have correct aria-label");
    assertEquals("button", label.attr("role"),
            "Nav toggle label should have role=button");
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `aria-label` on hidden checkbox input | `aria-label` on visible `<label>` element | This phase | AT can now discover the nav toggle |
| No skip-link | Skip-link as first `<body>` child | This phase | Keyboard navigation to main content |
| Inline `style="margin-*"` in driver-profile | CSS utility classes `.driver-header` / `.section-gap` | This phase | Passes QUAL-01 |
| Static winner display (no visual distinction) | `.match-team-winner` class with accent highlight | This phase | Meets UX-04 |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `href="#"` scrolls to top of page with `scroll-behavior: smooth` on `html` in all target browsers | Footer Links pattern | May need `href="#top"` with `id="top"` on html element instead; low risk, trivial fix |
| A2 | WCAG 2.1 skip-link position:absolute resting state is accessible to all screen readers | Skip-link pattern | Some very old screen readers prefer `clip` technique; modern browsers handle position:absolute fine |

**All other claims verified by direct file inspection of the repository.**

---

## Open Questions

1. **`activeSeasonName` variable availability in footer**
   - What we know: `activeSeasonSlug` is passed via `writeTemplate()` for all pages
   - What's unclear: Should the footer show the full `displayLabel` (e.g. "2026 | #1 | Season Name") or just a short label?
   - Recommendation: Pass `activeSeason.getDisplayLabel()` as `activeSeasonName` in `writeTemplate()`. The display label is already used throughout the templates.

2. **Draw scenario for `.match-team-winner`**
   - What we know: `homeTeamWon = hasResults && homeTotal > awayTotal`; when scores are equal, neither wins
   - What's unclear: No decision in CONTEXT.md for how a draw is styled on match cards
   - Recommendation: Draw = neither `.match-team-winner` class applied; both teams display in default style. This is consistent with `match-score-draw` class handling.

---

## Environment Availability

Step 2.6: SKIPPED — phase 41 is a code/CSS/HTML-only change. No external tools, services, CLI utilities, or new runtimes are required beyond the existing Java/Maven build infrastructure.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Jsoup |
| Config file | `pom.xml` (Surefire + Failsafe plugins) |
| Quick run command | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UX-01 | Skip-link is first child of `<body>`, targets `#main-content` | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenLayout_whenGenerate_thenSkipLinkIsFirstBodyChild` | Wave 0 |
| UX-04 | `.match-team-winner` class on winning team span | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenRaceWithResults_whenGenerate_thenMatchdayShowsWinnerHighlight` | Wave 0 |
| UX-06 | Footer has `.footer-link` elements with Top + Archive + season links | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenActiveSeason_whenGenerate_thenFooterContainsUsefulLinks` | Wave 0 |
| UX-07 | `<label>` has `aria-label="Toggle navigation menu"` and `role="button"` | Integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenLayout_whenGenerate_thenNavToggleLabelHasAriaLabel` | Wave 0 |
| UX-05 | CSS rule present (gradient + position:relative) | Manual / CSS inspection | `./mvnw verify` (build-level) | — |
| UX-08 | CSS `transition: background-color 0.2s` on `tr` | Manual / CSS inspection | `./mvnw verify` | — |
| UX-09 | CSS `cursor: pointer` on `a`, `label[for]`, `[role="button"]` | Manual / CSS inspection | `./mvnw verify` | — |
| QUAL-01 | No `style=` attributes in `driver-profile.html` | Manual verification | `./mvnw verify` + grep | — |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` green (82% minimum coverage maintained)

### Wave 0 Gaps
- [ ] 4 new test methods needed in `SiteGeneratorServiceTest.java` (UX-01, UX-04, UX-06, UX-07 assertions above)

---

## Security Domain

This phase introduces no authentication, no user input, no data persistence changes, and no server-side logic beyond reading existing data. The static site is public-read, admin-write.

| ASVS Category | Applies | Note |
|---------------|---------|------|
| V2 Authentication | No | No auth in this phase |
| V3 Session Management | No | Static site pages |
| V4 Access Control | No | Static site pages |
| V5 Input Validation | No | No new user inputs |
| V6 Cryptography | No | No cryptographic operations |

No new threat vectors introduced.

---

## Sources

### Primary (HIGH confidence — verified by direct file inspection)
- `src/main/resources/templates/site/layout.html` — nav toggle structure, footer, body order
- `src/main/resources/templates/site/matchday.html` — `.match-team` span structure
- `src/main/resources/templates/site/index.html` — match-card rendering on index page
- `src/main/resources/templates/site/driver-profile.html` — inline styles at lines 6, 47
- `src/main/resources/templates/site/archive.html` — confirmed no inline styles
- `src/main/resources/static/site/css/style.css` — all existing CSS variables, rules, breakpoints
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — toRaceView(), writeTemplate()
- `src/main/java/org/ctc/sitegen/model/RaceView.java` — record definition
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — test infrastructure, 41 existing tests
- `.planning/phases/41-ux-polish-accessibility/41-CONTEXT.md` — all locked decisions D-01 through D-11
- `.planning/phases/41-ux-polish-accessibility/41-UI-SPEC.md` — approved UI design contract
- `pom.xml` — JaCoCo minimum 0.82 confirmed

### Tertiary (LOW confidence — training knowledge)
- WCAG 2.1 skip-link pattern (standard technique; project-specific implementation verified against UI-SPEC)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified against actual source files
- Architecture: HIGH — all integration points confirmed by file inspection
- Pitfalls: HIGH — derived from actual code structure and record immutability constraints
- Test patterns: HIGH — 41 existing Jsoup-based tests provide clear template

**Research date:** 2026-04-16
**Valid until:** 2026-05-16 (CSS/HTML work is stable; no fast-moving dependencies)
