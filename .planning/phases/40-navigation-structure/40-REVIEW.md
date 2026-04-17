---
phase: 40-navigation-structure
reviewed: 2026-04-17T14:30:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/java/org/ctc/sitegen/model/RaceView.java
  - src/main/resources/static/site/css/style.css
  - src/main/resources/templates/site/layout.html
  - src/main/resources/templates/site/matchdays.html
  - src/main/resources/templates/site/index.html
  - src/main/resources/templates/site/driver-profile.html
  - src/main/resources/templates/site/matchday.html
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
findings:
  critical: 0
  warning: 3
  info: 5
  total: 8
status: issues_found
---

# Phase 40: Code Review Report

**Reviewed:** 2026-04-17T14:30:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Reviewed the static site generator service, templates, CSS, and test suite across phases 40 (navigation structure) and 41 (UX polish/accessibility). All three warnings from the previous review (2026-04-16) have been resolved: breadcrumb now guards `seasonSlug` with `th:if`, `toRaceView` accepts pre-fetched lineups, and the subnav test uses presence assertions instead of a fragile count.

The codebase is well-structured overall: navigation logic is correct, breadcrumbs render conditionally, the subnav guards playoff links properly, active nav states are correctly applied, and the test suite is comprehensive with 60+ tests covering all page types, active states, breadcrumbs, skip-link, footer, winner highlights, and edge cases. The match-card fragment, RaceView model, and CSS are clean and consistent.

Three new warnings were identified: (1) the opponent-display logic in driver-profile.html breaks for sub-team drivers, (2) a misleading "P0" display when a driver has no race results, and (3) the YouTube iframe is missing a `title` attribute required for WCAG accessibility. Five info-level items cover missing `lang` attribute, missing `aria-label` on navigation landmarks, focus outline removal, CSS `!important` usage, and a hardcoded external URL in the footer.

## Warnings

### WR-01: Opponent display logic breaks for sub-team drivers

**File:** `src/main/resources/templates/site/driver-profile.html:32-38`
**Issue:** The opponent column compares `driverTeamId` (sourced from `SeasonDriver.team` at `SiteGeneratorService.java:320`, which can be a sub-team) against `race.homeTeam.id` and `race.awayTeam.id` (which are always parent teams resolved from Match). When a driver belongs to a sub-team, `driverTeamId` will not match either parent team ID, causing BOTH team names to render as the opponent instead of just one. This violates the project's "Keep Thymeleaf Templates Lean" principle -- the comparison logic should be pre-computed in the service layer.
**Fix:** Resolve to the parent team ID in `generateDriverProfiles()` and pre-compute the opponent name per result, passing it as a model attribute rather than computing it in the template:
```java
// In generateDriverProfiles(), replace passing raw results with a computed list:
var driverParentId = team != null ? team.getParentOrSelf().getId() : null;
var resultEntries = results.stream().map(r -> {
    String opponent;
    if (r.getRace().getAwayTeam() == null) {
        opponent = "Bye";
    } else if (r.getRace().getHomeTeam() != null
            && r.getRace().getHomeTeam().getId().equals(driverParentId)) {
        opponent = r.getRace().getAwayTeam().getShortName();
    } else {
        opponent = r.getRace().getHomeTeam() != null
            ? r.getRace().getHomeTeam().getShortName() : "?";
    }
    return new DriverResultEntry(r, opponent);
}).toList();
ctx.setVariable("resultEntries", resultEntries);
```

### WR-02: "P0" displayed as best position when driver has no results

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:334`
**Issue:** When a driver has no race results, `bestPosition` is set to `0` via `.min().orElse(0)`. The template at `driver-profile.html:64` renders this as `"P0"` (`'P' + ${bestPosition}`), which is misleading -- position 0 does not exist in racing. While the "Race History" section is conditionally hidden when results are empty (line 13: `th:if="${results != null && !results.isEmpty()}"`), the "Statistics" section always renders (lines 51-68) and will display "P0" for drivers with zero results.
**Fix:** Use a sentinel value and conditionally render:
```java
// SiteGeneratorService.java:334
ctx.setVariable("bestPosition", results.stream()
    .mapToInt(r -> r.getPosition()).min().orElse(-1));
```
```html
<!-- driver-profile.html:64 -->
<td th:text="${bestPosition > 0 ? 'P' + bestPosition : '-'}"></td>
```

### WR-03: YouTube iframe missing `title` attribute (WCAG 2.1 SC 4.1.2)

**File:** `src/main/resources/templates/site/index.html:13-15`
**Issue:** The `<iframe>` embedding the YouTube video has no `title` attribute. WCAG 2.1 Success Criterion 4.1.2 (Name, Role, Value) requires all iframes to have a descriptive title so screen readers can identify the embedded content. This is a Level A accessibility violation.
**Fix:**
```html
<iframe th:src="'https://www.youtube.com/embed/' + ${videoId}"
        title="Community Team Cup - Latest YouTube Video"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
        allowfullscreen></iframe>
```

## Info

### IN-01: Missing `lang` attribute on `<html>` element

**File:** `src/main/resources/templates/site/layout.html:2`
**Issue:** The `<html>` element does not specify a `lang` attribute. The generated output HTML should include `lang="en"` for accessibility (WCAG 3.1.1 -- Language of Page). Screen readers rely on this to select correct pronunciation rules.
**Fix:**
```html
<html lang="en" xmlns:th="http://www.thymeleaf.org" th:fragment="layout(title, content)">
```

### IN-02: Multiple `<nav>` elements without distinguishing `aria-label`

**File:** `src/main/resources/templates/site/layout.html:13,37`
**Issue:** The page has three `<nav>` elements (main nav at line 13, subnav at line 37, breadcrumb at line 50). The breadcrumb nav correctly has `aria-label="breadcrumb"`, but the main nav and subnav lack `aria-label` attributes. When multiple `<nav>` landmarks exist, each should have a distinct label so assistive technology users can distinguish them.
**Fix:**
```html
<nav class="nav" aria-label="Main navigation">
...
<nav class="subnav" aria-label="Season navigation" th:if="...">
```

### IN-03: Focus outline removed on select without adequate replacement

**File:** `src/main/resources/static/site/css/style.css:617`
**Issue:** `.filter-bar select:focus` sets `outline: none` and relies only on `border-color: var(--accent)` for focus indication. The border color change from `var(--border)` (#2a2a2a) to `var(--accent)` (#4fc3f7) may not meet WCAG 2.1 SC 1.4.11 (Non-text Contrast) minimum 3:1 ratio in all contexts. Consider adding a visible focus ring.
**Fix:**
```css
.filter-bar select:focus {
    border-color: var(--accent);
    outline: 2px solid var(--accent);
    outline-offset: 1px;
}
```

### IN-04: `!important` used in `.nav-link-active` rule

**File:** `src/main/resources/static/site/css/style.css:532`
**Issue:** The `.nav-link-active` rule uses `color: var(--accent) !important` to override `.nav-links a` default color. This creates a specificity escalation. A more maintainable approach would be to increase selector specificity instead.
**Fix:**
```css
.nav-links .nav-link-active {
    color: var(--accent);
    background: rgba(79, 195, 247, 0.1);
}
```

### IN-05: Duplicate `padding` declaration in `.hero` CSS

**File:** `src/main/resources/static/site/css/style.css:139-143`
**Issue:** The `.hero` rule block declares `padding` twice -- `padding: 40px 0` on line 139 and `padding: 48px 32px` on line 143. The second declaration wins and the first has no effect. This is harmless but creates noise. (Carried over from previous review -- not yet addressed.)
**Fix:** Remove the first `padding` declaration:
```css
.hero {
    text-align: center;
    background: linear-gradient(180deg, var(--bg-card) 0%, var(--bg) 100%);
    margin: -32px -32px 32px;
    padding: 48px 32px;
}
```

---

_Reviewed: 2026-04-17T14:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
