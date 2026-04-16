---
phase: 38-season-content-data-filtering
reviewed: 2026-04-16T00:00:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/resources/static/site/css/style.css
  - src/main/resources/templates/site/archive.html
  - src/main/resources/templates/site/driver-ranking.html
  - src/main/resources/templates/site/index.html
  - src/main/resources/templates/site/matchday.html
  - src/main/resources/templates/site/standings.html
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase 38: Code Review Report

**Reviewed:** 2026-04-16
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

The site generator implementation is well-structured and the core filtering logic (excluding Test seasons from page generation) is correctly applied. Path traversal protection in `copyLogoToAssets` is sound. Tests cover the key scenarios. Four warnings were found — the most impactful is the German locale being applied to all Thymeleaf contexts, which will cause decimal numbers (e.g., average points in the driver ranking) to render with a comma separator (`1,5`) instead of a dot (`1.5`) on the live static site. Two additional logic warnings relate to `allSeasons` being passed unfiltered to the index template and to the away-score calculation including "unknown team" results.

---

## Warnings

### WR-01: German locale causes comma decimal separator in driver ranking

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:100`
**Issue:** Every `Context` is constructed with `Locale.GERMAN`:
```java
var ctx = new Context(Locale.GERMAN);
```
Thymeleaf's `#numbers.formatDecimal(r.averagePoints, 1, 1)` in `driver-ranking.html` (line 25) respects the context locale. With `Locale.GERMAN` the output will be `"1,5"` (German comma decimal) instead of `"1.5"`. CLAUDE.md explicitly mandates English for all UI texts. The same locale is applied in all seven `generate*` methods.

**Fix:** Use `Locale.ENGLISH` (or `Locale.US`) for all contexts:
```java
var ctx = new Context(Locale.ENGLISH);
```
This change must be applied consistently in `generateIndex`, `generateStandings`, `generateDriverRanking`, `generateMatchdays`, `generateTeamProfiles`, `generateDriverProfiles`, and `generatePlayoffBracket`.

---

### WR-02: `allSeasons` (unfiltered) passed to index template — Test seasons visible in nav/archive dropdown

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:71` and `101`
**Issue:** `generateIndex` receives `allSeasons` (the raw `seasonRepository.findAll()` result) and exposes it as the `allSeasons` template variable. If `index.html` or `layout.html` ever iterates `allSeasons` to build a season list or dropdown, Test seasons will appear — defeating the CONT-06 filtering requirement. Currently the index template does not render `allSeasons` directly, but the variable is present in the context unnecessarily. This is a latent correctness risk that will silently break filtering if the template is extended.

```java
// Line 66
var allSeasons = seasonRepository.findAll();          // includes Test seasons
var productionSeasons = allSeasons.stream()
        .filter(s -> !s.getName().contains("Test"))
        .toList();

// Line 71 — passes allSeasons, not productionSeasons
generateIndex(outPath, activeSeason, allSeasons, activeSeasonSlug, result);

// Line 101 — sets unfiltered list in context
ctx.setVariable("allSeasons", allSeasons);
```

**Fix:** Pass `productionSeasons` instead of `allSeasons` to `generateIndex`, and update the method signature accordingly:
```java
generateIndex(outPath, activeSeason, productionSeasons, activeSeasonSlug, result);
```
And in `generateIndex`:
```java
ctx.setVariable("allSeasons", productionSeasons);
```

---

### WR-03: Away score calculation captures "unknown team" results

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:350-353`
**Issue:** `awayTotal` is computed as the sum of all results whose `teamShortName` does NOT equal `homeShortName`:
```java
int awayTotal = results.stream()
        .filter(r -> !r.teamShortName().equals(homeShortName))
        .mapToInt(RaceView.ResultView::pointsTotal).sum();
```
When `toRaceView` cannot resolve a driver's team (line 340 fallback returns `"?"`), that result's points are included in `awayTotal` regardless of which team the driver actually belongs to. In Bye races (`awayShortName = "Bye"`) this also means any home-team driver whose lookup fails will have their points counted toward the away score, producing an incorrect scoreline.

**Fix:** Filter on both home AND away team short names explicitly:
```java
int awayTotal = results.stream()
        .filter(r -> r.teamShortName().equals(awayShortName))
        .mapToInt(RaceView.ResultView::pointsTotal).sum();
```
Note that `awayShortName` must be computed before this statement (move the line 346 computation upward).

---

### WR-04: Test `slugify` helper diverges from production implementation — latent false-negative risk

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:152-154`
**Issue:** The test's local `slugify` method omits the German character substitutions present in the service:
```java
// Test slugify (line 152):
return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");

// Service slugify (line 362):
return input.toLowerCase()
        .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
```
If any season name, matchday label, or display label contains an umlaut (e.g., a season named "Frühjahr 2026"), the test `slugify` produces `"fr-hjahr-2026"` while the service produces `"fruehjahr-2026"`. Tests like `givenMatchdayData_whenGenerate_thenCreatesMatchdayPage` (line 186) would then silently fail to find the generated file and pass `Files.exists(...)` assertions with `false`, hiding real failures.

**Fix:** Either extract `slugify` into a shared test utility / the public API of `SiteGeneratorService` (package-visible for tests), or duplicate the full logic faithfully in the test helper:
```java
private String slugify(String input) {
    return input.toLowerCase()
            .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
}
```

---

## Info

### IN-01: Duplicate `mapToInt` stream evaluation in `generateDriverProfiles`

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:211-212`
**Issue:** `results.stream().mapToInt(r -> r.getPointsTotal()).sum()` is computed twice — once to set `totalPoints` and once inline in the `averagePoints` calculation:
```java
ctx.setVariable("totalPoints", results.stream().mapToInt(r -> r.getPointsTotal()).sum());
ctx.setVariable("averagePoints", results.isEmpty() ? 0.0 :
    (double) results.stream().mapToInt(r -> r.getPointsTotal()).sum() / results.size());
```

**Fix:** Extract to a local variable:
```java
int totalPoints = results.stream().mapToInt(RaceResult::getPointsTotal).sum();
ctx.setVariable("totalPoints", totalPoints);
ctx.setVariable("averagePoints", results.isEmpty() ? 0.0 : (double) totalPoints / results.size());
```

---

### IN-02: Inline styles in `archive.html` (convention)

**File:** `src/main/resources/templates/site/archive.html:25,30`
**Issue:** Two inline `style` attributes are used in the archive template:
```html
<span th:if="${entry.season.active}" style="color:#4fc3f7;">Active</span>
<a ... style="color:var(--accent); text-decoration:none;">Standings</a>
```
CLAUDE.md mandates CSS classes over inline styles on button elements. While these are `<span>` and `<a>` (not `.btn`), the pattern is inconsistent with the rest of the site templates and the project's CSS-first convention. The accent color `#4fc3f7` is already defined as `--accent` in `style.css`.

**Fix:** Add utility classes to `style.css`:
```css
.text-accent { color: var(--accent); }
.link-plain { color: var(--accent); text-decoration: none; }
```
And update `archive.html`:
```html
<span th:if="${entry.season.active}" class="text-accent">Active</span>
<a ... class="link-plain">Standings</a>
```

---

### IN-03: Magic number `"Test"` string for season filtering is not a named constant

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:67-68`
**Issue:** The filter predicate `!s.getName().contains("Test")` uses a bare string literal. If this convention changes (e.g., prefix becomes `"T-"` or `"[TEST]"`), there is only one place to update now, but the intent is not self-documenting and could be missed in future changes.

**Fix:** Extract to a named constant:
```java
private static final String TEST_SEASON_PREFIX = "Test";

// Usage:
.filter(s -> !s.getName().contains(TEST_SEASON_PREFIX))
```

---

_Reviewed: 2026-04-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
