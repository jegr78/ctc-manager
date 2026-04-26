---
phase: 44-clean-output-directory
reviewed: 2026-04-17T14:30:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
  - src/main/java/org/ctc/sitegen/SiteProperties.java
  - src/main/resources/application-dev.yml
  - src/main/resources/application.yml
  - src/main/resources/static/site/css/style.css
  - src/main/resources/templates/site/layout.html
  - src/main/resources/templates/site/links.html
  - src/main/resources/templates/site/drivers.html
  - src/main/resources/templates/site/teams.html
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: issues_found
---

# Phase 44-47: Code Review Report

**Reviewed:** 2026-04-17T14:30:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

Reviewed 10 files spanning four phases: Phase 44 (clean output directory), Phase 45 (footer YouTube link), Phase 46 (configurable links page), and Phase 47 (teams/drivers overview pages). The implementation is solid overall -- `cleanOutputDirectory` correctly protects the root directory from deletion and handles the non-existent case, the new `SiteProperties` configuration is well-structured, the links/teams/drivers pages render correctly with proper Thymeleaf escaping, and test coverage is comprehensive with 50+ integration tests covering all new features including edge cases.

Three warnings were found: an unconditional footer separator that renders adjacent to a conditionally hidden element (producing double dots when no active season exists), an unfixed `writeTemplate` re-read of a mutable field instead of using the already-resolved `outPath`, and an unclosed `Files.list()` stream in a test. Four informational items cover code duplication, a minor test cleanup opportunity, and an over-fetch pattern.

---

## Warnings

### WR-01: Footer renders orphaned separator when no active season exists

**File:** `src/main/resources/templates/site/layout.html:76`

**Issue:** The separator (middot) on line 76 between the active-season link and the YouTube link is rendered unconditionally. The active-season link on lines 72-75 is conditionally rendered via `th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"`. When there is no active season (e.g., all seasons deactivated), the footer will render: `Archive [dot] [dot] YouTube` -- two adjacent separators with nothing between them. This creates a visible layout defect.

**Fix:** Add the same `th:if` guard to the separator on line 76:
```html
<span th:if="${activeSeasonSlug != null and !#strings.isEmpty(activeSeasonSlug)}"
      class="footer-sep" aria-hidden="true">&middot;</span>
<a href="https://www.youtube.com/@CommunityTeamCup"
   class="footer-link"
   target="_blank"
   rel="noopener">YouTube</a>
```

---

### WR-02: `writeTemplate` reads mutable `siteProperties` instead of using resolved `outPath`

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:534`

**Issue:** `writeTemplate` calls `Path.of(siteProperties.getOutputDir())` on line 534 to compute relative asset and root paths. The `generate()` method already resolves this to a local `outPath` variable on line 65. The `setOutputDir()` method is public (line 58) and mutates `siteProperties` directly. If `setOutputDir()` is called between the `generate()` start and a `writeTemplate` invocation (e.g., in a concurrent context or a future refactoring), the relative path calculations will be based on a different root than the actual file output location, producing broken links. While single-threaded today, the design is fragile.

**Fix:** Pass the already-resolved `outPath` as a parameter to `writeTemplate`:
```java
private void writeTemplate(String templateName, Context context, Path outputFile,
                            Path outRoot, String activeSeasonSlug, String activeSeasonName) throws IOException {
    Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
    Path relativeRoot = outputFile.getParent().relativize(outRoot);
    // ... rest unchanged
}
```
All call-sites already have `outPath` in scope and just need to pass it through.

---

### WR-03: `Files.list()` stream not closed in test -- resource leak

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:808`

**Issue:** `Files.list(matchdayDir)` on line 808 returns a `DirectoryStream`-backed `Stream<Path>` that holds a native file handle. Calling `.toList()` does NOT close the underlying stream. Every other `Files.list()` call in this test class correctly uses try-with-resources (e.g., lines 219-224, 234-238, 496-505, 514-523). This instance is the sole exception and leaks a file descriptor per test run.

**Fix:**
```java
List<Path> matchdayFiles;
try (var stream = Files.list(matchdayDir)) {
    matchdayFiles = stream
            .filter(p -> p.toString().endsWith(".html"))
            .toList();
}
```

---

## Info

### IN-01: Duplicate JavaScript for season filter in teams.html and drivers.html

**File:** `src/main/resources/templates/site/teams.html:35-51` and `src/main/resources/templates/site/drivers.html:33-48`

**Issue:** Both templates contain an identical inline `<script>` block implementing the season-filter dropdown behavior. If the filter logic needs updating (e.g., adding URL hash state persistence, accessibility improvements), both files must be edited in lockstep.

**Fix:** Extract the script into a shared JavaScript file in `static/site/js/season-filter.js` and include it via `<script th:src="...">` in both templates. Low priority since the current duplication is small (~15 lines).

---

### IN-02: `generateTeamProfiles` fetches all teams regardless of season

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:240`

**Issue:** `teamRepository.findAll()` on line 240 loads every team in the database, then silently skips teams with no standing via `if (teamStanding == null) continue` (line 252). For a growing database this is an over-fetch. The intent would be clearer by iterating directly over `standings`:
```java
for (var teamStanding : standings) {
    var team = teamStanding.getTeam();
    // ... same logic, teamStanding is always non-null
}
```

---

### IN-03: Temporary upload directory not cleaned up in logo test

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:378`

**Issue:** `Files.createTempDirectory("ctc-test-uploads-")` creates a temp directory outside `@TempDir` management (intentionally, per the comment). This directory is never cleaned up after the test. Over many test runs, temp directories accumulate in the OS temp folder.

**Fix:** Declare a second `@TempDir` field in the test class:
```java
@TempDir
Path uploadBase;   // JUnit manages cleanup; distinct from tempDir
```
Then remove the `Files.createTempDirectory(...)` call from the test body and use `uploadBase` directly.

---

### IN-04: `cleanOutputDirectory` has no safety guard against dangerously short paths

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:117-140`

**Issue:** The `cleanOutputDirectory` method will recursively delete all contents of whatever path is configured in `siteProperties.outputDir`. While the value comes from configuration (not user input), the public `setOutputDir()` method (line 58) allows programmatic mutation. There is no validation that the path is a reasonable subdirectory (e.g., rejecting `/`, `/home`, `/Users`, or paths with fewer than 2 components). This is a defense-in-depth concern rather than an active vulnerability, since only test code and admin endpoints would call `setOutputDir()`.

**Fix:** Add a simple safety check at the start of `cleanOutputDirectory`:
```java
private void cleanOutputDirectory(Path outPath) throws IOException {
    if (outPath.getNameCount() < 2) {
        throw new IllegalArgumentException("Refusing to clean dangerously short path: " + outPath);
    }
    // ... existing logic
}
```

---

_Reviewed: 2026-04-17T14:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
