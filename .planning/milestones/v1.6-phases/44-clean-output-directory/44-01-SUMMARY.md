---
phase: 44-clean-output-directory
plan: "01"
subsystem: sitegen
tags: [tdd, red-phase, testing, clean-output]
dependency_graph:
  requires: []
  provides: [failing-tests-clean-output-CLEAN-01-CLEAN-02]
  affects: [SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [given-when-then, tdd-red-phase, tempdir-isolation]
key_files:
  created: []
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "Test 3 (CLEAN-02: non-existent dir) passes in RED phase — Files.createDirectories already handles this; documents contract without requiring implementation change"
metrics:
  duration: "~3 minutes"
  completed: "2026-04-16T21:44:05Z"
  tasks_completed: 1
  files_modified: 1
---

# Phase 44 Plan 01: TDD RED — Output Directory Cleanup Tests Summary

Three failing integration tests added to SiteGeneratorServiceTest for output directory cleanup (CLEAN-01: stale file/dir removal; CLEAN-02: non-existent dir graceful handling).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Write three failing tests for output directory cleanup | ffc9ee7 | SiteGeneratorServiceTest.java (+50 lines) |

## Verification Results

- `givenStaleFile_whenGenerate_thenStaleFileIsRemoved` — FAILS (RED): stale file remains after `generate()` (expected behavior — no cleanup implemented yet)
- `givenStaleNestedDirectory_whenGenerate_thenNestedDirectoryIsRemoved` — FAILS (RED): stale nested directory remains after `generate()`
- `givenNonExistentOutputDir_whenGenerate_thenCreatesAndGeneratesPages` — PASSES: `Files.createDirectories` in `generate()` already handles non-existent dirs; documents CLEAN-02 contract

Test run result: 3 run, 2 failures, 0 errors — confirms RED state for CLEAN-01 tests.

## TDD Gate Compliance

RED gate: test commit `ffc9ee7` exists with `test(44-01):` prefix. GREEN gate will be provided by plan 44-02.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this is a test-only plan (no production code modified).

## Threat Flags

None — tests operate on @TempDir paths owned by the test process; no new trust boundaries introduced.

## Self-Check: PASSED

- [x] `givenStaleFile_whenGenerate_thenStaleFileIsRemoved` exists in SiteGeneratorServiceTest.java (line 937)
- [x] `givenStaleNestedDirectory_whenGenerate_thenNestedDirectoryIsRemoved` exists (line 951)
- [x] `givenNonExistentOutputDir_whenGenerate_thenCreatesAndGeneratesPages` exists (line 968)
- [x] `// --- Phase 44: Clean Output Directory ---` section comment exists (line 932)
- [x] Commit ffc9ee7 exists and contains 50 new lines
- [x] Tests 1+2 FAIL, Test 3 PASSES — RED state confirmed
