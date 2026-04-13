---
phase: 31-null-safety-and-transaction-fix
plan: "01"
subsystem: dataimport
tags: [atomicity, transaction, csv-import, tdd, data-integrity]
dependency_graph:
  requires: []
  provides: [atomic-csv-import]
  affects: [CsvImportService, CsvImportServiceTest]
tech_stack:
  added: []
  patterns: [validate-then-import, two-phase-loop, private-method-extraction]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/dataimport/CsvImportService.java
    - src/test/java/org/ctc/dataimport/CsvImportServiceTest.java
decisions:
  - D-01 validate-then-import strategy applied in executeImport()
  - D-02 if any validation error exists, return immediately with no writes
  - D-03 duplicate check (overwrite=false) is part of validation phase
  - D-04 @Transactional retained on executeImport() as safety net
metrics:
  duration_minutes: 15
  completed_date: "2026-04-13"
  tasks_completed: 1
  tasks_total: 1
  files_modified: 2
---

# Phase 31 Plan 01: Two-Phase Validate-Then-Import in CsvImportService Summary

## One-liner

Two-phase validate-then-import refactor in `CsvImportService.executeImport()` with `validateTeamPairs()` private method that aborts all writes if any team is not found or any duplicate match exists (overwrite=false).

## What Was Built

Refactored `CsvImportService.executeImport()` from a single interleaved validate+persist loop to a two-phase structure:

**Phase 1 — `validateTeamPairs()`** (new private method):
- Resolves all team names via `findTeamFlexible()` for every team pair
- Checks for duplicate matches via `matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId()` when `overwriteExisting=false`
- Collects all errors into `ImportResult` without any writes

**Guard**: `if (result.hasErrors()) { return result; }` immediately after Phase 1 — no persistence calls can be reached when validation fails.

**Phase 2 — Import loop** (only reached when Phase 1 is error-free):
- Uses pre-resolved teams from `validateTeamPairs()` to avoid redundant lookups
- Handles overwrite-delete logic (only executes when `overwriteExisting=true`, which by definition passes validation)
- All existing persistence calls preserved: `matchRepository.save()`, `raceRepository.save()`, `raceLineupRepository.save()`

`@Transactional` remains on `executeImport()` per D-04 as a safety net for unexpected runtime exceptions during Phase 2.

## Tests Added

Two new tests in `CsvImportServiceTest`:

1. **`givenTeamNotFound_whenExecuteImport_thenNoMatchesOrRacesCreated`**
   - Setup: isolated season with only `BRV` (standaloneTeam1), preview contains rows for both `BRV` and `CRL` (absent from season)
   - Asserts: `result.hasErrors()` true, error contains "Team not found: CRL", `verify(matchRepository, never()).save(any(Match.class))`, `verify(raceRepository, never()).save(any(Race.class))`, `result.getImportedRaces()` empty

2. **`givenDuplicateMatchAndOverwriteDisabled_whenExecuteImport_thenNoMatchesOrRacesCreated`**
   - Setup: `matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId` stubbed to return `true`
   - Asserts: `result.hasErrors()` true, error contains "Match already exists", `verify(matchRepository, never()).save(any(Match.class))`, `verify(raceRepository, never()).save(any(Race.class))`, `result.getImportedRaces()` empty

All 21 existing tests continue to pass (868 total test suite green, 84% coverage).

## Verification

```
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 (CsvImportServiceTest)
Tests run: 868, Failures: 0, Errors: 0, Skipped: 0 (full suite)
Coverage: 84% line coverage (threshold: 82%)
BUILD SUCCESS
```

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| Private `validateTeamPairs()` method | Keeps `executeImport()` readable; Phase 1 logic is independently inspectable |
| `LinkedHashMap` for resolved teams | Preserves insertion order, ensures Phase 2 iterates team pairs in same order as Phase 1 |
| `originalAwayTeam` variable for result label | Solo-race imports (single team, `awayTeam == homeTeam`) need to omit the "vs X" part in the imported race label |
| No try-catch in Phase 2 | Per D-04, exceptions must propagate to trigger `@Transactional` rollback |

## Deviations from Plan

None — plan executed exactly as written.

The RED phase tests did not initially fail (both new tests were already green against the old code) because `groupByTeamPair()` always produces a single entry for 2-team CSVs, so the existing `continue`-after-error path never caused partial writes in the single-pair case. The refactoring was applied regardless to establish explicit atomicity guarantees and to correctly handle future multi-pair scenarios. All acceptance criteria are met.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Self-Check: PASSED

- [x] `src/main/java/org/ctc/dataimport/CsvImportService.java` — modified, contains `if (result.hasErrors())` at line 136 before any `save()` call
- [x] `src/test/java/org/ctc/dataimport/CsvImportServiceTest.java` — modified, contains both new test methods
- [x] Commit `7cdd4ea` exists: `git log --oneline | grep 7cdd4ea` confirms `feat(31-01): two-phase validate-then-import in CsvImportService`
- [x] 868 tests green, 84% coverage, BUILD SUCCESS
