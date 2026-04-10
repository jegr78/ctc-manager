---
phase: 18-merge-ui
plan: "01"
subsystem: domain/service
tags: [tdd, merge, preview, read-only]
dependency_graph:
  requires: []
  provides: [DriverMergeService.previewMerge, DriverMergeService.MergePreview]
  affects: [DriverMergeService]
tech_stack:
  added: []
  patterns: [TDD Red-Green, @Transactional(readOnly=true), record computed methods]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/DriverMergeService.java
    - src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java
decisions:
  - "MergePreview declared as inner record inside DriverMergeService (mirrors MergeResult pattern)"
  - "@Transactional(readOnly=true) applied to previewMerge() per threat model T-18-01"
  - "Loop structure mirrors merge() exactly but replaces save/delete with counter increments"
metrics:
  duration: "~2.5 minutes"
  completed: "2026-04-07T13:03:04Z"
  tasks_completed: 2
  files_modified: 2
---

# Phase 18 Plan 01: previewMerge() with MergePreview Record Summary

Read-only `previewMerge()` method and `MergePreview` record added to `DriverMergeService`, providing per-FK-table reassign vs. duplicate counts without executing any mutations.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | RED: Failing tests for previewMerge() | a514d3c | DriverMergeServiceTest.java |
| 2 | GREEN: Implement MergePreview and previewMerge() | 817ae65 | DriverMergeService.java |

## What Was Built

**`MergePreview` record** (inner record in `DriverMergeService`):
- 7 fields: `seasonDriversToReassign`, `seasonDriversDuplicate`, `raceLineupsToReassign`, `raceLineupsDuplicate`, `raceResultsToReassign`, `raceResultsDuplicate`, `psnAliasesToReassign`
- 2 computed methods: `totalToReassign()` (sum of all toReassign fields), `totalDuplicates()` (sum of all duplicate fields)

**`previewMerge(UUID sourceId, UUID targetId)`** method:
- Annotated `@Transactional(readOnly = true)`
- Identical validation to `merge()`: self-merge check, source/target existence
- Read-only loop structure mirroring `merge()`: iterates FK tables, checks conflicts, increments counters
- Returns `MergePreview` with exact counts — no `save()` or `delete()` called

**Tests added** (`PreviewMergeTests` nested class, 6 methods):
1. `givenSourceEqualsTarget_whenPreviewMerge_thenThrowsBusinessRuleException`
2. `givenNonExistentSource_whenPreviewMerge_thenThrowsEntityNotFoundException`
3. `givenNonExistentTarget_whenPreviewMerge_thenThrowsEntityNotFoundException`
4. `givenMixedConflictsAcrossAllFkTables_whenPreviewMerge_thenReturnsCorrectCounts`
5. `givenNoReferences_whenPreviewMerge_thenReturnsAllZeroCounts`
6. `givenValidPreview_whenPreviewMerge_thenNoMutationsExecuted`

## Test Results

- 25 tests total (6 new + 19 existing), all passing
- No regressions in existing merge() tests

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — `@Transactional(readOnly = true)` applied per T-18-01 as planned. No new network endpoints or auth paths introduced.

## Self-Check: PASSED

- `src/main/java/org/ctc/domain/service/DriverMergeService.java` — exists, contains `MergePreview` record and `previewMerge()` method
- `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java` — exists, contains `PreviewMergeTests` nested class
- Commit `a514d3c` — test(18-01): add failing tests for previewMerge()
- Commit `817ae65` — feat(18-01): implement previewMerge() with MergePreview record
