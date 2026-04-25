---
phase: 55-admin-import-ui-transactional-execute
fixed_at: 2026-04-25T07:45:00Z
review_path: .planning/phases/55-admin-import-ui-transactional-execute/55-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 55: Code Review Fix Report

**Fixed at:** 2026-04-25T07:45:00Z
**Source review:** .planning/phases/55-admin-import-ui-transactional-execute/55-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (CR-01, WR-01, WR-02, WR-03)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### CR-01: crossTabCreatedDrivers Cache Ignores Accept Override on Second Tab

**Files modified:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`, `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java`
**Commit:** `3971b6c`
**Applied fix:** Changed the cache key for the FUZZY/accept path from `row.psnId()` to `row.psnId() + "_accept_" + tab.year()`. The no-accept (create-new-driver) branch retains the raw `psnId` key for valid cross-tab deduplication (D-07). Added regression test `givenSameFuzzyPsnInTwoTabsWithDifferentAcceptUuids_whenExecute_thenEachTabLinksToItsOwnDriver` using sheet PSN `fz_xa` (5 chars) vs DB drivers `fz_xb`/`fz_xc` (Levenshtein dist=1 each, similarity=0.8), with years 2021/2022 to avoid DevDataSeeder collision. Test verifies season2021 links to driverA and season2022 links to driverB independently.

### WR-01: Exception Message Leaked Directly into Flash Attribute

**Files modified:** `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`
**Commit:** `58ca0d0`
**Applied fix:** Split the combined catch block in the preview handler into two: `IOException` now returns a generic "Could not read the Google Sheet..." message; `IllegalArgumentException | IllegalStateException` keeps `e.getMessage()` (controlled text from the service). In the execute handler, `BusinessRuleException | ValidationException | IllegalArgumentException` keep controlled messages; `IllegalStateException | DataAccessException` get a generic "Import failed due to an internal error..." message. All branches log full exception detail via `log.error(..., e)`.

### WR-02: preview() Has No @Transactional(readOnly = true)

**Files modified:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java`
**Commit:** `f7445c9`
**Applied fix:** Added `@Transactional(readOnly = true)` to the `preview(String sheetUrl)` method. Calls from `execute()` join the outer read-write transaction via Spring's default REQUIRED propagation — no nested-transaction issues.

### WR-03: givenFuzzyRowWithoutAccept Test Has Unreachable Dead Setup

**Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java`
**Commit:** `4801b33`
**Applied fix:** Removed the orphaned `fuzzyDriver` creation and `stubSheets("https://sheets.test/d/abc", 2021, ...)` block that was never exercised (mockMvc used URL `abc2`). Kept the `fz_noacc`/`fz_noac0` pair with `abc2`. Added a second assertion confirming that the original `fz_noacc` driver is not modified, aligning with the reviewer's suggested test body.

## Skipped Issues

None.

---

**Final verification:** `./mvnw verify` — 1064 tests, 0 failures, 0 errors, JaCoCo check passed (BUILD SUCCESS).

_Fixed: 2026-04-25T07:45:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
