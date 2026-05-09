---
phase: 44-clean-output-directory
plan: "02"
subsystem: sitegen
tags: [tdd, green-phase, clean-output, file-io]
dependency_graph:
  requires: [44-01-failing-tests-clean-output-CLEAN-01-CLEAN-02]
  provides: [CLEAN-01, CLEAN-02]
  affects: [SiteGeneratorService, SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [files-walk-file-tree, simple-file-visitor, bottom-up-deletion]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "cleanOutputDirectory uses Files.walkFileTree with SimpleFileVisitor for bottom-up deletion (D-01)"
  - "Root output directory is preserved via !dir.equals(outPath) guard (D-02)"
  - "Non-existent output dir returns early; Files.createDirectories handles creation (D-03)"
  - "IOException propagates from cleanOutputDirectory to generate() outer catch (no wrapping)"
  - "Logo test moved uploadBase outside tempDir to prevent cleanOutputDirectory deleting test fixtures"
metrics:
  duration: "~8 minutes"
  completed: "2026-04-16T21:50:00Z"
  tasks_completed: 1
  files_modified: 2
---

# Phase 44 Plan 02: TDD GREEN — Implement cleanOutputDirectory Summary

Private `cleanOutputDirectory(Path)` method added to `SiteGeneratorService` using `Files.walkFileTree` with `SimpleFileVisitor` for bottom-up deletion; fulfills CLEAN-01 (stale file/dir removal) and CLEAN-02 (non-existent dir graceful handling).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Implement cleanOutputDirectory(Path) and wire into generate() | 7a6a6ae | SiteGeneratorService.java (+26 lines), SiteGeneratorServiceTest.java (+5 lines fix) |

## Verification Results

- `givenStaleFile_whenGenerate_thenStaleFileIsRemoved` — PASSES (GREEN): stale file deleted before generation
- `givenStaleNestedDirectory_whenGenerate_thenNestedDirectoryIsRemoved` — PASSES (GREEN): nested dirs deleted bottom-up
- `givenNonExistentOutputDir_whenGenerate_thenCreatesAndGeneratesPages` — PASSES: early return + createDirectories handles creation
- Full test run: 977 tests, 0 failures, 0 errors
- JaCoCo: All coverage checks met (>= 82%)

## TDD Gate Compliance

RED gate: `test(44-01)` commit `ffc9ee7` — confirmed from Plan 01 SUMMARY.
GREEN gate: `feat(44-02)` commit `7a6a6ae` — confirmed in this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed logo test regression caused by cleanOutputDirectory**
- **Found during:** Task 1, first test run
- **Issue:** `givenTeamWithLogo_whenGenerate_thenLogoCopiedAndLinkedRelatively` failed because `uploadBase` was set to `tempDir.resolve("uploads")` — a subdirectory of the output dir (`tempDir`). `cleanOutputDirectory` deleted the uploads directory before `generate()` could copy logos to assets.
- **Fix:** Changed the test to use `Files.createTempDirectory("ctc-test-uploads-")` so `uploadBase` is a sibling temp dir outside `tempDir` (the output dir). In production, `uploadDir` and `outputDir` are always separate paths (`data/dev/uploads` vs `docs/site`), so this is a test-isolation fix only — no production behavior changed.
- **Files modified:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`
- **Commit:** 7a6a6ae (same commit as implementation)

## Known Stubs

None — all CLEAN-01 and CLEAN-02 functionality fully implemented.

## Threat Flags

None — `cleanOutputDirectory` operates on the bounded, config-controlled output directory path (`@Value("${ctc.site.output-dir}")`). No new trust boundaries introduced (T-44-02 and T-44-03 accepted per threat model).

## Self-Check: PASSED

- [x] `cleanOutputDirectory` method exists in SiteGeneratorService.java (line 104)
- [x] `cleanOutputDirectory(outPath)` call exists in generate() before Files.createDirectories (line 62)
- [x] `Files.walkFileTree` pattern used (line 109) — D-01 confirmed
- [x] `!dir.equals(outPath)` guard present (line 118) — D-02 confirmed
- [x] `!Files.exists(outPath)` early return present (line 105) — D-03 confirmed
- [x] `import java.nio.file.attribute.BasicFileAttributes` present (line 25)
- [x] Commit 7a6a6ae exists with 2 files changed, 31 insertions
- [x] 977 tests pass, 0 failures — GREEN state confirmed
- [x] JaCoCo coverage checks pass
