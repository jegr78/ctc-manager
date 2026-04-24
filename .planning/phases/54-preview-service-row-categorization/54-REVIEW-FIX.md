---
phase: 54-preview-service-row-categorization
fixed_at: 2026-04-24T23:53:00Z
review_path: .planning/phases/54-preview-service-row-categorization/54-REVIEW.md
iteration: 1
fix_scope: critical_warning
status: complete
findings_in_scope: 1
fixed: 1
skipped: 3
findings_fixed:
  - id: WR-01
    title: Missing test for EXACT match when suggestedSeasonId == null
    files_modified:
      - src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java
    commit: fa8f7d7
findings_skipped:
  - id: IR-01
    title: Redundant .trim() on already-trimmed PSN
    reason: info-level, out of default scope (critical_warning)
  - id: IR-02
    title: ErrorRow.rawPsnId / rawTeamCode are actually trimmed, not raw
    reason: info-level, out of default scope (critical_warning)
  - id: IR-03
    title: UnchangedRow omits existingSeasonDriverId
    reason: info-level, out of default scope (critical_warning)
tests_before: 1041
tests_after: 1042
commits:
  - hash: fa8f7d7
    subject: "test(54): add EXACT-match + ambiguous-season coverage for WR-01"
---

# Phase 54: Code Review Fix Report

**Fixed at:** 2026-04-24T23:53:00Z
**Source review:** `.planning/phases/54-preview-service-row-categorization/54-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope (CRITICAL + WARNING): 1
- Fixed: 1
- Skipped (out of scope, INFO): 3
- Tests before: 1041
- Tests after: 1042 (+1, all green)
- JaCoCo coverage gate: held

## Fixed Issues

### WR-01: Missing test for EXACT match when `suggestedSeasonId == null`

**Files modified:** `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java`
**Commit:** `fa8f7d7`
**Applied fix:** Added one new `@Test` method
`givenExistingDriverAndAmbiguousSeason_whenPreview_thenCategorisedAsNewAssignment`
near the existing NEW_ASSIGNMENT tests (after the
`givenExistingDriverNoSeasonDriver_whenPreview_thenCategorisedAsNewAssignment`
test, before the CONFLICT section). The test:

1. Stubs `seasonRepository.findByYear(2024)` with two seasons so
   `suggestedSeasonId` becomes `null` and `ambiguousReason` becomes
   `"Multiple seasons for year 2024"`.
2. Stubs `teamRepository.findByShortName("AHR")` to return `teamAhr`.
3. Stubs `driverMatchingService.findDriver("existing_psn")` to return
   `MatchResult.exact("existing_psn", existingDriver)`.
4. Asserts the row lands in `newAssignments` (size 1, correct
   `existingDriverId` and `teamShortName`).
5. Asserts `unchanged` and `conflicts` are both empty.
6. Asserts `verifyNoInteractions(seasonDriverRepository)` — pinning the
   D-12 step-6 short-circuit invariant: the `Optional.empty()` /
   ambiguous-season path must NOT consult `SeasonDriverRepository`. This
   covers the previously un-exercised `else` branch at
   `DriverSheetImportService.java:174-177`.

**Verification:**
- `./mvnw test -Dtest=DriverSheetImportServiceTest` → 17 tests, 0 failures.
- `./mvnw verify` → 1042 tests, 0 failures, JaCoCo `check` reports
  "All coverage checks have been met."
- No production-code changes; only the test file was modified.

## Skipped Issues

The following findings are INFO-level and therefore out of the default
`critical_warning` fix scope. They are not bugs and are documented in
`54-REVIEW.md` as forward-looking hygiene notes.

### IR-01: Redundant `.trim()` on already-trimmed PSN

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:129`
**Reason:** info-level, out of default scope (critical_warning). Cosmetic
duplication; no behavioral defect.

### IR-02: `ErrorRow.rawPsnId` / `rawTeamCode` are actually trimmed, not raw

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:256-260`
**Reason:** info-level, out of default scope (critical_warning). Naming
asymmetry; flag for Phase 55 when error rendering surfaces the need.

### IR-03: `UnchangedRow` omits `existingSeasonDriverId`

**File:** `src/main/java/org/ctc/dataimport/DriverSheetImportService.java:250-254`
**Reason:** info-level, out of default scope (critical_warning). Optional
ergonomics for Phase 55 audit trail; no current consumer requires it.

---

_Fixed: 2026-04-24T23:53:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
