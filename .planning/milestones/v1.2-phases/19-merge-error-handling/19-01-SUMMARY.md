---
phase: 19-merge-error-handling
plan: 01
status: complete
started: 2026-04-07T18:40:00+02:00
completed: 2026-04-07T18:42:00+02:00
commits:
  - hash: 00e3829
    message: "test(19-01): add failing tests for previewMerge error handling"
  - hash: ad0df97
    message: "fix(19-01): add error handling to previewMerge controller method"
---

## Summary

Added error handling to `previewMerge()` in `DriverController` so it catches `EntityNotFoundException` and `BusinessRuleException` with a flash redirect to the merge form, matching the existing `executeMerge()` pattern. Closes GAP-01 and FLOW-GAP-01 from v1.2 audit.

## What Changed

- `previewMerge()` now wraps its body in a try/catch block
- Added `RedirectAttributes` parameter to the method signature
- Catch block sets `errorMessage` flash attribute with "Merge failed: " prefix
- Redirects to `/admin/drivers/{id}/merge` (the merge form) on error

## Key Files

### Modified
- `src/main/java/org/ctc/admin/controller/DriverController.java` — Added try/catch error handling to `previewMerge()`
- `src/test/java/org/ctc/admin/controller/DriverControllerTest.java` — Added 2 integration tests for error paths

## Test Results

- 852 tests pass (2 new)
- All JaCoCo coverage checks met
- TDD approach: RED (both new tests failed with 409/404) → GREEN (all 18 controller tests pass)

## Self-Check: PASSED

- [x] Self-merge preview redirects with flash error (was 409, now 3xx redirect)
- [x] Non-existent target preview redirects with flash error (was 404, now 3xx redirect)
- [x] Existing preview success path unchanged
- [x] Pattern matches executeMerge() error handling

## Deviations

None — plan executed exactly as specified.
