---
phase: 44-clean-output-directory
reviewed: 2026-04-16T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
  - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: issues_found
---

# Phase 44: Code Review Report

**Reviewed:** 2026-04-16
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Two files reviewed: the `SiteGeneratorService` implementation (new `cleanOutputDirectory` logic for Phase 44) and its accompanying integration test. The `cleanOutputDirectory` method itself is correct — it properly guards the root directory from deletion, handles the non-existent-dir case, and the path-traversal guard in `copyLogoToAssets` is sound.

Two warnings were found: a `Files.list()` stream left unclosed in the test (resource leak), and a state-field re-read inside `writeTemplate` instead of using the already-resolved `outPath` (thread-safety / correctness risk under concurrent access). Two informational items cover over-fetching of teams and a missing temp-directory cleanup in tests.

---

## Warnings

### WR-01: `Files.list()` stream not closed — resource leak in test

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:813`

**Issue:** `Files.list(matchdayDir)` returns a `DirectoryStream`-backed `Stream<Path>` that holds a native file-handle. Calling `.toList()` on the stream does NOT close it. Every other `Files.list()` call in this test class correctly wraps the stream in try-with-resources (lines 206–210, 220–225, 482–491, 499–508, 656–664). This instance at line 813 is the sole exception and will leak a file descriptor per test run.

**Fix:**
```java
// replace lines 813-815 with:
List<Path> matchdayFiles;
try (var stream = Files.list(matchdayDir)) {
    matchdayFiles = stream
            .filter(p -> p.toString().endsWith(".html"))
            .toList();
}
```

---

### WR-02: `writeTemplate` reads mutable field `outputDir` instead of using the passed `outPath`

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:418`

**Issue:** `writeTemplate` calls `Path.of(outputDir)` (line 418) to compute relative asset paths. `outputDir` is a mutable `@lombok.Setter`-annotated field. The `generate()` method receives an already-resolved `outPath = Path.of(outputDir)` at line 59, but this resolved value is never threaded into `writeTemplate`. If `setOutputDir()` is called concurrently (or in a future multi-threaded context), `writeTemplate` will compute paths relative to the new directory while writing files to the old one, producing broken relative links. Even today, in tests the two calls are sequential, but the design is fragile.

**Fix:** Accept `outPath` as a parameter in `writeTemplate` (or capture it as a local `final` variable in `generate()` and use a lambda/inner-method):
```java
// In generate(), capture once:
final Path outPath = Path.of(outputDir);

// Change writeTemplate signature:
private void writeTemplate(String templateName, Context context, Path outputFile,
                            Path outRoot,   // <-- add this
                            String activeSeasonSlug, String activeSeasonName) throws IOException {
    Path relativeAssets = outputFile.getParent().relativize(outRoot.resolve("assets"));
    Path relativeRoot   = outputFile.getParent().relativize(outRoot);
    // ... rest unchanged
}
```
All call-sites already have `outPath` in scope and just need to pass it through.

---

## Info

### IN-01: `generateTeamProfiles` fetches all teams regardless of season

**File:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java:240`

**Issue:** `teamRepository.findAll()` loads every team in the database (line 240), then silently skips teams with no standing via `if (teamStanding == null) continue` (line 252). For a growing database this is an over-fetch. More importantly, teams from other seasons that happen to have a matching standing entry could theoretically be included (though current `standingsService` scopes by season ID, making this safe today). The intent would be clearer with a season-scoped query.

**Fix:** Add a repository method such as `teamRepository.findBySeasons_Id(season.getId())` or derive the team list directly from `standings`:
```java
// derive teams from the already-computed standings list — no extra DB call
for (var teamStanding : standings) {
    var team = teamStanding.getTeam();
    // ... same logic, teamStanding is always non-null here
}
```

---

### IN-02: Temporary upload directory not cleaned up in `givenTeamWithLogo` test

**File:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java:366`

**Issue:** `Files.createTempDirectory("ctc-test-uploads-")` creates a temp directory outside `@TempDir` (intentionally, per the comment). JUnit's `@TempDir` auto-deletes `tempDir`, but the manually created `uploadBase` directory is never deleted. Over many test runs this accumulates temp directories in the OS temp folder.

**Fix:** Register it for cleanup with a `@AfterEach` field or use `@TempDir` with a second field annotated `@TempDir Path uploadBase` (the note in the comment says it must be outside `tempDir`, which a second `@TempDir` satisfies):
```java
@TempDir
Path uploadBase;   // JUnit manages cleanup; distinct from tempDir
```
Then remove the `Files.createTempDirectory(...)` call from the test body.

---

_Reviewed: 2026-04-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
