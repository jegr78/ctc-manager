---
phase: 29-mass-assignment-fix
fixed_at: 2026-04-13T18:26:00Z
review_path: .planning/phases/29-mass-assignment-fix/29-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase 29: Code Review Fix Report

**Fixed at:** 2026-04-13T18:26:00Z
**Source review:** .planning/phases/29-mass-assignment-fix/29-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2
- Fixed: 2
- Skipped: 0

## Fixed Issues

### WR-01: Missing `@NotNull` on `MatchdayForm.seasonId` allows null to propagate to service

**Files modified:** `src/main/java/org/ctc/admin/dto/MatchdayForm.java`
**Commit:** 7c314e9
**Applied fix:** Added `import jakarta.validation.constraints.NotNull;` and `@NotNull` annotation on the `seasonId` field. With this constraint in place, a POST that omits `seasonId` triggers a validation error caught by `result.hasErrors()` in the controller before the service call, returning the form with a field error instead of a 500.

### WR-02: Redirect URL appends literal `"null"` string when `seasonId` is null

**Files modified:** `src/main/java/org/ctc/admin/controller/MatchdayController.java`
**Commit:** bda273e
**Applied fix:** Replaced the string-concatenation redirect `"redirect:/admin/matchdays?seasonId=" + form.getSeasonId()` with a conditional build: construct the base URL, then append `?seasonId=...` only when `form.getSeasonId() != null`. This prevents the `?seasonId=null` 400-Bad-Request edge case on the defensive code path.

---

_Fixed: 2026-04-13T18:26:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
