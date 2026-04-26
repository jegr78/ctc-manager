---
phase: 50-site-generator-test-robustness
reviewed: 2026-04-17T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/resources/templates/site/teams.html
  - src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase 50: Code Review Report

**Reviewed:** 2026-04-17
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

The OVER-06 fix introduces standings-based filtering in `generateTeamsOverview()` to prevent broken links to non-existent profile pages. The logic is largely correct in intent, but contains a subtle correctness gap: the `profileUrl` selection loop relies on `seasons` (the list built from `seasonTeamRepository`), which can contain seasons where the team is registered but the standings list for that season is empty (no races played). The inner `standingsBySeasonId` guard covers this correctly — however, the `teamsWithProfiles` set that gates `hasProfile` includes a team's ID only when `calculateStandings` returns a non-empty list, which is the right signal. The overall logic is sound, but a one-sided dependency exists at the data-model boundary (see WR-01).

The `teams.html` null guards for `profileUrl` and `logoRelPath` are correct Thymeleaf idioms. No issues found in the template.

`@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`) is the correct annotation for Spring Boot 3.4+ and Spring Boot 4.x; it supersedes the deprecated `@MockBean`. Usage is correct in both test classes.

The new test `givenTeamWithZeroGames_whenGenerate_thenTeamsOverviewDoesNotLinkToMissingProfile` correctly verifies the three required properties: the team appears in the page, no profile file exists, and no link to the non-existent profile exists. The assertions are sound.

Three warnings and two info items are reported below.

---

## Warnings

### WR-01: Profile URL selection ignores seasons built from SeasonTeam vs. sorted seasons mismatch

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:447`

**Issue:** Inside the `teamEntries` stream, `seasons` is built from `seasonTeamRepository.findBySeasonId()` entries (line 429). This list is in insertion order (LinkedHashSet iteration) for the seasons already filtered through `sortedSeasons`. The inner scan at line 447 iterates this list in reverse to find the latest season where standings exist. However, `standingsBySeasonId` is keyed on season IDs from `sortedSeasons` (line 419), and `seasons` is a subset of the same collection — so IDs should always match. The warning is that `seasons` contains all seasons from `SeasonTeam` registrations (including seasons where the team may have zero played games), while `standingsBySeasonId` only contains team IDs with actual standings rows. The guard at line 449 (`standingsBySeasonId.getOrDefault(...).contains(team.getId())`) correctly prevents a broken link for zero-game teams. **However**, there is a subtle issue: if `hasProfile` is `true` (the team appears in standings for _some_ season) but the reverse scan finds no match in `standingsBySeasonId` (all matching seasons have the team only in SeasonTeam but not in standings), `profileUrl` remains `null`. In that case the overview card renders as plain text — no broken link. This is actually the correct fallback behavior. The logic is correct but confusingly structured: `hasProfile` is evaluated once globally, then the inner loop re-checks per-season. Consider consolidating to remove the redundant outer `hasProfile` guard.

**Fix:** Remove the `hasProfile` flag and simplify to a direct null check on `profileUrl`:

```java
String profileUrl = null;
for (int i = seasons.size() - 1; i >= 0; i--) {
    var s = seasons.get(i);
    if (standingsBySeasonId.getOrDefault(s.getId(), java.util.Set.of()).contains(team.getId())) {
        profileUrl = "season/" + slugify(s.getDisplayLabel())
                + "/team/" + slugify(team.getShortName()) + ".html";
        break;
    }
}
// profileUrl == null means no profile page exists — render as plain text (no hasProfile flag needed)
```

This eliminates the dead outer guard and makes the intent self-evident.

---

### WR-02: E2E test shares mutable `SiteProperties` bean across `@BeforeAll` and later tests — `setOutputDir` call in `setUp` can interfere with subsequent isolation

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java:159`

**Issue:** `SiteGeneratorE2ETest` uses `@TestInstance(Lifecycle.PER_CLASS)` and `@BeforeAll` to generate the site once. The `siteGeneratorService.setOutputDir(tempDir.toString())` is called inside `@BeforeAll`, which is correct for the shared-output pattern. **However**, `SiteGeneratorServiceTest` (a separate test class in the same Spring context — both use `@SpringBootTest` with `dev` profile) mutates `siteGeneratorService.setOutputDir(...)` in `@BeforeEach`. Both test classes share the same Spring application context unless isolated by `@DirtiesContext`. `SiteGeneratorE2ETest` carries `@DirtiesContext` (line 13); `SiteGeneratorServiceTest` does not. If the test runner happens to execute `SiteGeneratorServiceTest` after `SiteGeneratorE2ETest` (within the same context before dirty-context reload), the `outputDir` mutation in the latter's `@BeforeEach` could affect a concurrently loaded context. In practice, `@DirtiesContext` on the E2E test ensures the context is torn down after it, so a fresh context starts for the service test. The risk is low but the dependency on execution order is implicit.

**Fix:** Add `@DirtiesContext` to `SiteGeneratorServiceTest` as well, or document why it is safe to omit. Alternatively, keep `outputDir` resets inside each test class's setup using a local field copy pattern that does not rely on context ordering.

---

### WR-03: `givenTeamWithZeroGames` test has a fragile slug assumption for the team short name

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:1333`

**Issue:** The test constructs the expected profile path as:
```java
slugify("GZGT" + uniqueSuffix)
```
`uniqueSuffix` is a UUID substring that is exclusively alphanumeric lowercase hex characters, so `slugify()` will produce `"gzgt" + uniqueSuffix` (lowercased, no separators). This is fine in practice. However, if the `uniqueSuffix` generation ever changes (e.g., to include uppercase or non-alphanumeric characters), the path derivation could diverge from what `generateTeamProfiles` actually writes, causing the `assertFalse(Files.exists(profilePath))` to be vacuously true (the path never existed anyway for a different reason). This would make the test pass while not actually exercising the guard.

A more robust form would derive the path using the same `slugify` call on the team's persisted `shortName`, retrieved from the saved entity:

**Fix:**
```java
var profilePath = tempDir.resolve(
    "season/" + seasonSlug + "/team/" + slugify(zeroGameTeam.getShortName()) + ".html");
```
This is already what the test does at line 1333, but `zeroGameTeam.getShortName()` would be cleaner than `slugify("GZGT" + uniqueSuffix)` only because the former uses the actual persisted value rather than reconstructing it. In this specific case both are equivalent, but the pattern is worth noting for maintainability.

---

## Info

### IN-01: `generateTeamsOverview` calls `standingsService.calculateStandings` for every production season — N+1 service calls not cached

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:419`

**Issue:** `calculateStandings` is called once per season in `generateTeamsOverview` (lines 419-424), and then called again per season in `generateStandings` (line 170), `generateTeamProfiles` (line 244), etc. For each full site generation, standings are computed multiple times per season. This is an info-level note only (performance is out of v1 scope), but it is worth flagging: if standings calculation is expensive (aggregation queries), adding a `Map<UUID, List<StandingsEntry>>` computed once at the top of `generate()` and passed down would eliminate redundant work. Currently out of scope, but the OVER-06 fix added a third call site (`generateTeamsOverview`) where previously there were two.

**Fix (future):** Compute standings once per season at the start of `generate()` and pass as a parameter to each private method.

---

### IN-02: The `@BeforeAll` in `SiteGeneratorE2ETest` silently mutates seasons with names not already containing "Test"

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java:93`

**Issue:** The setUp renames pre-existing seasons to `"Test_" + name` to exclude them from `productionSeasons`. This mutation is permanent within the test lifecycle (the DB is in-memory H2, so it resets between test class contexts only if `@DirtiesContext` propagates). This is the intended pattern (see `CLAUDE.md: Isolate Test Data Completely`), and `@DirtiesContext` on the class ensures the context is dirtied after E2E tests complete. However, if other `@SpringBootTest` tests run in the same context window before `@DirtiesContext` kicks in, they would see all real season names prefixed with `Test_`. The `@DirtiesContext` annotation at the class level applies after all tests in the class finish, so parallel execution within the same Spring context could cause cross-test contamination. Acceptable for `@TestInstance(PER_CLASS)` + sequential execution (default), but worth noting for future parallelism.

**Fix (future):** No action required for sequential execution. If test parallelism is ever enabled, switch to inserting isolated test data only, without mutating existing records.

---

_Reviewed: 2026-04-17_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
