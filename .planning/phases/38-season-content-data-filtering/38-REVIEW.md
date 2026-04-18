---
phase: 38-season-content-data-filtering
reviewed: 2026-04-16T10:30:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/resources/static/site/css/style.css
  - src/main/resources/templates/site/archive.html
  - src/main/resources/templates/site/driver-profile.html
  - src/main/resources/templates/site/driver-ranking.html
  - src/main/resources/templates/site/index.html
  - src/main/resources/templates/site/matchday.html
  - src/main/resources/templates/site/standings.html
  - src/main/resources/templates/site/team-profile.html
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
findings:
  critical: 0
  warning: 5
  info: 4
  total: 9
status: issues_found
---

# Phase 38: Code Review Report

**Reviewed:** 2026-04-16T10:30:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

The site generator implementation is well-structured and the core filtering logic (excluding Test seasons from page generation) is correctly applied. Path traversal protection in `copyLogoToAssets` is sound. Test coverage is broad and follows the Given-When-Then naming convention. Five warnings were found: the most impactful is the German locale applied to all Thymeleaf contexts (causes comma decimal separators in English UI), followed by an NPE risk in `driver-profile.html` when `race.homeTeam` is null, an unfiltered `allSeasons` list passed to the index context, an incorrect away-score fallback capturing "unknown team" results, and a divergent `slugify` helper in the test class that silently produces wrong paths for umlaut season names.

---

## Warnings

### WR-01: German locale causes comma decimal separator in driver ranking

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:100`
**Issue:** Every `Context` is constructed with `Locale.GERMAN`. Thymeleaf's `#numbers.formatDecimal(r.averagePoints, 1, 1)` in `driver-ranking.html` (line 25) respects the context locale. With `Locale.GERMAN` the output will be `"1,5"` instead of `"1.5"`. CLAUDE.md explicitly mandates English for all UI texts. The same locale is used in all seven `generate*` methods.

**Fix:** Use `Locale.ENGLISH` (or `Locale.US`) for all contexts:

```java
var ctx = new Context(Locale.ENGLISH);
```

Apply consistently in `generateIndex`, `generateStandings`, `generateDriverRanking`, `generateMatchdays`, `generateTeamProfiles`, `generateDriverProfiles`, and `generatePlayoffBracket`.

---

### WR-02: NPE in driver-profile template when race has no home team

**File:** `src/main/resources/templates/site/driver-profile.html:32`
**Issue:** The opponent column evaluates `result.race.homeTeam.id` without a null check. The `result` objects are raw JPA `RaceResult` entities (from `raceResultRepository.findByDriverId`), not `RaceView` objects. If `race.homeTeam` is null on a persisted race, SpEL evaluation of `result.race.homeTeam.id` throws a `NullPointerException` and aborts driver profile page rendering entirely. The bye-race test (`givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE`) only tests through `toRaceView()` — it does not cover driver profiles for bye-race participants.

**Fix:** Use null-safe navigation on `homeTeam`:

```html
<span th:if="${result.race.homeTeam?.id != team?.id}"
      th:text="${result.race.homeTeam?.shortName}"></span>
<span th:if="${result.race.awayTeam != null and result.race.awayTeam.id != team?.id}"
      th:text="${result.race.awayTeam.shortName}"></span>
<span th:if="${result.race.awayTeam == null}">Bye</span>
```

---

### WR-03: `allSeasons` (unfiltered) passed to index template context

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:71,101`
**Issue:** `generateIndex` receives `allSeasons` (the raw `seasonRepository.findAll()` result) and exposes it as the `"allSeasons"` template variable. All other page generators (standings, driver ranking, matchdays, archive, profiles) correctly use `productionSeasons`. If `index.html` or `layout.html` is extended to iterate `allSeasons` for a season switcher or dropdown, Test seasons will appear — silently defeating the CONT-06 filtering requirement. The variable is currently unused by the template, making this a latent correctness risk rather than an active bug.

**Fix:** Pass `productionSeasons` to `generateIndex` and update the method signature:

```java
// In generate():
generateIndex(outPath, activeSeason, productionSeasons, activeSeasonSlug, result);

// In generateIndex:
ctx.setVariable("allSeasons", productionSeasons);
```

---

### WR-04: Away score calculation captures "unknown team" results

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:350-353`
**Issue:** `awayTotal` sums all results whose `teamShortName` does NOT equal `homeShortName`:

```java
int awayTotal = results.stream()
        .filter(r -> !r.teamShortName().equals(homeShortName))
        .mapToInt(RaceView.ResultView::pointsTotal).sum();
```

When a driver's team cannot be resolved (the fallback at line 340 returns `"?"`), those points are counted toward the away total regardless of which team the driver actually belongs to. In bye races, any home-team driver whose lookup fails would have their points incorrectly added to the away score, producing a wrong scoreline.

**Fix:** Filter on the resolved `awayShortName` positively (move the `awayShortName` assignment before the totals):

```java
String awayShortName = race.getAwayTeam() != null ? race.getAwayTeam().getShortName() : "Bye";

int homeTotal = results.stream()
        .filter(r -> r.teamShortName().equals(homeShortName))
        .mapToInt(RaceView.ResultView::pointsTotal).sum();
int awayTotal = results.stream()
        .filter(r -> r.teamShortName().equals(awayShortName))
        .mapToInt(RaceView.ResultView::pointsTotal).sum();
```

---

### WR-05: Test `slugify` helper diverges from production implementation

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:152-154`
**Issue:** The test's local `slugify` omits the German character substitutions present in the production service:

```java
// Test slugify (line 152) — missing umlaut handling:
return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");

// Service slugify (lines 363-366):
return input.toLowerCase()
        .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe").replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
```

For a season named `"Frühjahr 2026"`, the test produces path `season/fr-hjahr-2026/` while the service writes `season/fruehjahr-2026/`. All `Files.exists(...)` assertions in such tests would evaluate to `false` silently, masking real failures. Current test seasons use ASCII-only names, so this is latent today.

**Fix:** Either expose `slugify` as package-private in `SiteGeneratorService` for test access, or duplicate the full logic:

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
**Issue:** `results.stream().mapToInt(r -> r.getPointsTotal()).sum()` is computed twice — once for `totalPoints` and once inline for `averagePoints`. The duplicated traversal is error-prone if the filter expression changes.

**Fix:**

```java
int totalPoints = results.stream().mapToInt(RaceResult::getPointsTotal).sum();
ctx.setVariable("totalPoints", totalPoints);
ctx.setVariable("averagePoints", results.isEmpty() ? 0.0 : (double) totalPoints / results.size());
ctx.setVariable("bestPosition", results.stream().mapToInt(RaceResult::getPosition).min().orElse(0));
```

---

### IN-02: Inline styles in `archive.html`

**File:** `src/main/resources/templates/site/archive.html:25,30`
**Issue:** The "Active" badge uses `style="color:#4fc3f7;"` and the "Standings" link uses `style="color:var(--accent); text-decoration:none;"`. The hardcoded hex `#4fc3f7` duplicates the `--accent` CSS variable defined in `style.css`. The inline styles prevent theme-level overrides and are inconsistent with the CSS-class-first convention in the rest of the templates.

**Fix:** Add utility classes to `style.css`:

```css
/* style.css */
.text-accent { color: var(--accent); }
.link-plain  { color: var(--accent); text-decoration: none; }
```

Then update `archive.html`:

```html
<span th:if="${entry.season.active}" class="text-accent">Active</span>
<a th:href="..." class="link-plain">Standings</a>
```

---

### IN-03: Inline styles in `driver-profile.html`

**File:** `src/main/resources/templates/site/driver-profile.html:6,47`
**Issue:** Two wrapper `<div>` elements use `style="margin-bottom: 24px;"` and `style="margin-top: 24px;"` as inline styles, identical spacing values that could be CSS utility classes.

**Fix:** Add utility classes to `style.css`:

```css
/* style.css */
.mt-24 { margin-top: 24px; }
.mb-24 { margin-bottom: 24px; }
```

Then update `driver-profile.html`:

```html
<div class="mb-24">...</div>
<div class="section mt-24">...</div>
```

---

### IN-04: Magic string `"Test"` for season filtering should be a named constant

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:67`
**Issue:** The filter predicate `!s.getName().contains("Test")` uses a bare string literal. The intent is not self-documenting and is easy to miss if the convention changes.

**Fix:**

```java
private static final String TEST_SEASON_NAME_MARKER = "Test";

// Usage:
.filter(s -> !s.getName().contains(TEST_SEASON_NAME_MARKER))
```

---

_Reviewed: 2026-04-16T10:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
