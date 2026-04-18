---
phase: 35-site-generator-bye-race-safety
reviewed: 2026-04-14T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
status: issues_found
---

# Phase 35: Code Review Report

**Reviewed:** 2026-04-14
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Phase 35 introduces null safety for bye races in `SiteGeneratorService.toRaceView()` and a corresponding integration test. The core fix — extracting `race.getHomeTeam()` into a local variable and guarding with a ternary — is structurally correct and covers the actual production scenario (bye match where `awayTeam` is null). No critical issues found.

Two findings were identified: a silent miscount of `awayTotal` when `homeShortName` is `"Bye"` (edge case of an orphaned race), and a duplicated `slugify()` in the test class that omits German umlaut handling present in the production version.

---

## Warnings

### WR-01: `awayTotal` counts all results when `homeShortName` is `"Bye"`

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:293-297`

**Issue:** `homeTotal` is calculated by matching `r.getTeamShortName().equals(homeShortName)` and `awayTotal` by the negation. When `homeShortName` is `"Bye"` (an orphaned race — no match, no override, no playoff matchup), no driver's `teamShortName` will ever equal `"Bye"`, so `homeTotal` will be 0 and `awayTotal` will accumulate the points of every driver in the result list. The semantics are inverted for that edge case.

In the current data model this is harmless in practice because an orphaned race (no match, no override, no playoff matchup) would never have associated results. However, the logic is semantically asymmetric: the `"Bye"` sentinel doubles as both a display label and a filtering key without any documentation of the constraint.

**Fix:** Document the invariant, or use a `null` sentinel internally for the home team and only format to `"Bye"` at the `RaceView` construction point:

```java
// Option A — document the invariant (minimal change)
// homeShortName is "Bye" only when race has no match/override/playoff;
// such races never have results, so homeTotal/awayTotal will both be 0.
var homeTeam = race.getHomeTeam();
String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye"; // invariant: results empty when null

// Option B — separate the null check from the display label (preferred)
var homeTeam = race.getHomeTeam();
var results = race.getResults().stream()
        .map(r -> { ... })
        .toList();

int homeTotal = homeTeam != null
    ? results.stream()
        .filter(r -> r.getTeamShortName().equals(homeTeam.getShortName()))
        .mapToInt(RaceView.ResultView::getPointsTotal).sum()
    : 0;
int awayTotal = homeTeam != null
    ? results.stream()
        .filter(r -> !r.getTeamShortName().equals(homeTeam.getShortName()))
        .mapToInt(RaceView.ResultView::getPointsTotal).sum()
    : 0;

String homeShortName = homeTeam != null ? homeTeam.getShortName() : "Bye";
```

---

## Info

### IN-01: `slugify()` duplicated in test class without umlaut handling

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:152-154`

**Issue:** The test helper `slugify()` is a simplified copy of `SiteGeneratorService.slugify()` (lines 306–311). The production version replaces German umlauts (`ä`, `ö`, `ü`, `Ä`, `Ö`, `Ü`, `ß`); the test version does not. This means `seasonDir()` would return an incorrect path if any test season name contained umlauts, causing file-existence assertions to fail silently with a path mismatch rather than an NPE or assertion error.

Current test data (`"Gen Test " + uniqueSuffix`) uses no umlauts, so no test fails today. It is a latent maintenance trap.

**Fix:** Either make `SiteGeneratorService.slugify()` package-private (remove `private`) so the test can call it directly, or extract it to a shared utility:

```java
// In SiteGeneratorService.java — change visibility
String slugify(String input) { ... }  // package-private

// In SiteGeneratorServiceTest.java — remove the duplicated method and call:
private Path seasonDir() {
    return tempDir.resolve("season").resolve(siteGeneratorService.slugify(season.getDisplayLabel()));
}
```

---

_Reviewed: 2026-04-14_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
