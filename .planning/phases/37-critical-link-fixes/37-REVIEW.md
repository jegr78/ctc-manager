---
phase: 37-critical-link-fixes
reviewed: 2026-04-16T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/resources/templates/site/archive.html
  - src/main/resources/templates/site/layout.html
  - src/main/resources/templates/site/team-profile.html
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
findings:
  critical: 0
  warning: 4
  info: 4
  total: 8
status: issues_found
---

# Phase 37: Code Review Report

**Reviewed:** 2026-04-16
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

The phase introduces static-site generation with relative-path link building, logo copying, and a comprehensive integration test suite. The security-relevant path traversal guard in `copyLogoToAssets` is correctly implemented. The core link-building logic in `writeTemplate` (relative `assetsPath` / `rootPath` computation) is sound for the current deployment model.

Four warnings are raised: an archive template link that bypasses the established `rootPath` pattern; an incorrect test `slugify` that silently diverges from the service implementation; an over-broad `teamRepository.findAll()` call that is not season-scoped; and a missing `setUploadDir` restoration in the logo test. Four info items cover dead null-guard branches in the layout, the misleading absolute fallback path, a style inconsistency, and test entity accumulation.

---

## Warnings

### WR-01: Archive link bypasses `rootPath` — will break if archive moves to subdirectory

**File:** `src/main/resources/templates/site/archive.html:26`
**Issue:** The standings link is built as a bare relative path `'season/' + ${entry.slug} + '/standings.html'` without the `${rootPath}` prefix used by every other link in the site (layout.html lines 14, 25, 27, 28). This works today because `archive.html` is at the output root (where `rootPath` = `.`), but it is inconsistent with the established link-building pattern. Any future move of the archive page to a subdirectory would silently produce broken links.
**Fix:**
```html
<a th:href="${rootPath + '/season/' + entry.slug + '/standings.html'}"
   style="color:var(--accent); text-decoration:none;">Standings</a>
```

---

### WR-02: Test `slugify` diverges from service `slugify` — latent path resolution failure

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:152-154`
**Issue:** The test helper `slugify` does not handle German umlauts (`ä→ae`, `ö→oe`, `ü→ue`, `ß→ss`), while the service's `slugify` (SiteGeneratorService.java:360-363) does. `seasonDir()` uses the test version to build the expected output path. If a season name ever contains an umlaut, the expected path will not match the actual generated path, making all file-existence assertions silently pass against a non-existent directory (`Files.exists` returns `false` → assertion fails with a confusing message, or the season/matchday-label assertions pass vacuously). Current test data (`"Gen Test " + uniqueSuffix`, `"Spieltag 1"`) has no umlauts, so this is a latent defect.
**Fix:** Mirror the service implementation exactly:
```java
private String slugify(String input) {
    return input.toLowerCase()
            .replaceAll("[äÄ]", "ae").replaceAll("[öÖ]", "oe")
            .replaceAll("[üÜ]", "ue").replaceAll("ß", "ss")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
}
```

---

### WR-03: `teamRepository.findAll()` fetches all teams, not season-scoped teams

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:162`
**Issue:** `generateTeamProfiles` calls `teamRepository.findAll()`, which returns every team in the database regardless of season membership. The guard on line 170 (`if (teamStanding == null) continue`) prevents crashes but silently queries standings for every team on every season iteration. In a multi-season database this performs redundant `calculateStandings` lookups and generates no output for the extra teams — a correctness smell since the intent is clearly season-scoped.
**Fix:** Filter to only teams participating in the season:
```java
var teams = season.getTeams(); // or teamRepository.findBySeasonId(season.getId())
```
If a direct repository method does not exist, use `season.getTeams()` (the bidirectional association is already maintained by `season.addTeam()`).

---

### WR-04: `uploadDir` not restored after logo test — cross-test contamination

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:367`
**Issue:** `givenTeamWithLogo_whenGenerate_thenLogoCopiedAndLinkedRelatively` calls `siteGeneratorService.setUploadDir(uploadBase.toString())` but never restores the original value. Because `@SpringBootTest` shares the application context and `setUploadDir` mutates service state, any subsequent test execution (within the same context) that relies on the default upload dir will silently use the stale temp path from a previous run.
**Fix:** Save and restore the original value, or reset in `@BeforeEach`/`@AfterEach`:
```java
private String originalUploadDir;

@BeforeEach
void setUp() {
    // ... existing setup ...
    originalUploadDir = /* inject or track default */ "data/dev/uploads";
}

@AfterEach
void tearDown() {
    siteGeneratorService.setUploadDir(originalUploadDir);
}
```
Alternatively, set `uploadDir` in `@BeforeEach` alongside `outputDir` so every test starts from a known state.

---

## Info

### IN-01: Dead null-guard branches in layout produce misleading absolute fallback path

**File:** `src/main/resources/templates/site/layout.html:9,15`
**Issue:** Both `assetsPath` null-checks (e.g., `${assetsPath != null ? assetsPath + '/css/style.css' : '/assets/css/style.css'}`) are dead code — `writeTemplate` always sets `assetsPath` before processing. The fallback value `/assets/css/style.css` is an absolute path that would be incorrect in a static site deployed under a subdirectory. The dead branches create confusion about whether the absolute path is intentional.
**Fix:** Remove the ternary and use the variable directly:
```html
<link rel="stylesheet" th:href="${assetsPath + '/css/style.css'}">
<img th:src="${assetsPath + '/img/ctc-logo-white.png'}" ...>
```

---

### IN-02: `awayTotal` sums all non-home drivers — fragile for bye/multi-team scenarios

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:347-350`
**Issue:** The away total is computed as all results where `teamShortName` does not equal `homeShortName`. In a bye race (where `awayShortName` is "Bye"), this still sums all non-home driver points into "away". The logic is correct for two-team races but is semantically fragile — future additions of neutral/bye drivers could corrupt the score display without a compile error.
**Suggestion:** Explicitly filter on `awayShortName` rather than the negation of `homeShortName`:
```java
int awayTotal = results.stream()
        .filter(r -> r.teamShortName().equals(awayShortName))
        .mapToInt(RaceView.ResultView::pointsTotal).sum();
```

---

### IN-03: Test entity accumulation across test methods within session

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:76-146`
**Issue:** `@BeforeEach` creates new seasons, teams, drivers, and races on every test method run, but there is no `@AfterEach` cleanup. Entities accumulate in the H2 database across all test methods in the class (each test adds 1 season, 2 teams, 4 drivers, etc.). While the unique suffix prevents data collisions, `teamRepository.findAll()` in the service grows with each test. For 14 test methods, the final test runs with ~28 teams in the database — not a correctness failure, but increasingly slow and harder to debug.
**Suggestion:** Add `@Transactional` on the test class (rollback after each test) or add `@AfterEach` cleanup that deletes by `uniqueSuffix`. Note: `@Transactional` on `@SpringBootTest` with service calls that use `@Transactional(readOnly=true)` may require care.

---

### IN-04: Commented-out style inconsistency — inline `style=` on active season badge

**File:** `src/main/resources/templates/site/archive.html:22`
**Issue:** `<span th:if="${entry.season.active}" style="color:#4fc3f7;">Active</span>` uses an inline style on a non-button element. The CLAUDE.md convention forbids inline styles on `.btn` elements; this span is not a button, so it is not a strict violation. However, the hardcoded hex color `#4fc3f7` duplicates the `--accent` CSS variable (already used on line 27 via `style="color:var(--accent)"`). Using the CSS variable is preferred for consistency.
**Suggestion:**
```html
<span th:if="${entry.season.active}" style="color:var(--accent);">Active</span>
```
Or extract to a CSS class (e.g., `class="text-accent"`) in `style.css`.

---

_Reviewed: 2026-04-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
