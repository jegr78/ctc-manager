---
phase: 18-merge-ui
plan: "02"
subsystem: admin-ui
tags: [merge, controller, template, thymeleaf, integration-tests]
dependency_graph:
  requires: [18-01]
  provides: [merge-ui-endpoints, merge-template, merge-button]
  affects: [DriverController, driver-detail.html]
tech_stack:
  added: []
  patterns: [two-state-template, flash-redirect, confirm-guard]
key_files:
  created:
    - src/main/resources/templates/admin/driver-merge.html
  modified:
    - src/main/java/org/ctc/admin/controller/DriverController.java
    - src/main/resources/templates/admin/driver-detail.html
    - src/test/java/org/ctc/admin/controller/DriverControllerTest.java
decisions:
  - Two-state template with th:if preview null vs present (no separate templates)
  - JS confirm guard on form onsubmit (consistent with existing delete pattern)
  - Merge button as btn-secondary between Edit and Delete
metrics:
  duration: ~5min
  completed: "2026-04-07T13:07:00Z"
  tasks_completed: 2
  tasks_total: 2
requirements: [MERGE-01, MERGE-02, MERGE-03, MERGE-04]
---

# Phase 18 Plan 02: Merge UI Controller + Template Summary

Complete driver merge UI with 3 controller endpoints, two-state Thymeleaf template, merge button on detail page, and 5 integration tests.

## What Was Done

### Task 1: Controller endpoints + template + merge button + integration tests

**DriverController.java** -- Added `DriverMergeService` dependency and 3 new endpoints:
- `GET /{id}/merge` -- loads source driver and sorted target dropdown (excluding source)
- `POST /{id}/merge/preview` -- delegates to `DriverMergeService.previewMerge()`, returns preview state
- `POST /{id}/merge` -- executes merge, redirects to target with success flash; catches exceptions with error flash

**driver-merge.html** -- New two-state template:
- State 1 (select target): source driver info, dropdown of all other drivers sorted by PSN-ID, "Select Target" button
- State 2 (preview + confirm): FK reference counts table (SeasonDriver, RaceLineup, RaceResult, PsnAlias), empty state for zero references, "Confirm Merge" danger button with JS confirm guard

**driver-detail.html** -- Added "Merge" button (btn-secondary) in toolbar between Edit and Delete.

**DriverControllerTest.java** -- 5 new integration tests:
1. `givenExistingDriver_whenGetMergeForm_thenReturnsMergeView`
2. `givenTwoDrivers_whenGetMergeForm_thenSourceExcludedFromDropdown`
3. `givenTwoDrivers_whenPostPreview_thenReturnsPreviewState`
4. `givenTwoDrivers_whenConfirmMerge_thenRedirectsToTarget`
5. `givenTwoDrivers_whenConfirmMerge_thenSourceDriverDeleted`

### Task 2: Visual verification (auto-approved)

Auto-approved in auto-chain mode.

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 | 68ef4eb | feat(18-02): add merge UI with controller, template, and integration tests |

## Test Results

- `./mvnw test -Dtest=DriverControllerTest,DriverMergeServiceTest` -- 41 tests, all green
- DriverControllerTest: 19 tests (14 existing + 5 new merge tests)
- DriverMergeServiceTest: 22 tests (all existing, still green)

## Deviations from Plan

None -- plan executed exactly as written.

## Known Stubs

None -- all endpoints are fully wired to DriverMergeService.

## Self-Check: PASSED
