---
phase: 29-mass-assignment-fix
reviewed: 2026-04-13T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - src/main/java/org/ctc/admin/dto/MatchdayForm.java
  - src/main/java/org/ctc/admin/controller/MatchdayController.java
  - src/main/resources/templates/admin/matchday-form.html
  - src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java
findings:
  critical: 0
  warning: 2
  info: 1
  total: 3
status: issues_found
---

# Phase 29: Code Review Report

**Reviewed:** 2026-04-13
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

This phase introduces `MatchdayForm` as a dedicated DTO for the matchday create/edit form, replacing direct entity binding — the core mass-assignment protection. The overall approach is correct: the controller extracts individual scalar fields from the DTO and passes them to the service, so no entity fields can be overwritten via form manipulation. The service validates season/matchday existence independently.

Two warnings were found: a missing `@NotNull` constraint on `MatchdayForm.seasonId` that allows a null value to propagate into the service and produce an unhandled `IllegalArgumentException` (rather than a clean validation error), and a redirect string-concatenation that produces a broken URL when `seasonId` is null. One info item covers an uncovered test path.

No critical security issues were found. The mass-assignment fix itself is sound.

---

## Warnings

### WR-01: Missing `@NotNull` on `MatchdayForm.seasonId` allows null to propagate to service

**File:** `src/main/java/org/ctc/admin/dto/MatchdayForm.java:13`

**Issue:** `seasonId` has no `@NotNull` constraint. When a POST to `/admin/matchdays/save` omits the `seasonId` parameter (bypassing the client-side `required` attribute on the `<select>`), `form.getSeasonId()` is null. The controller passes this null directly to `matchdayService.saveMatchday(...)` (controller line 111). Inside the service, `seasonRepository.findById(null)` throws `IllegalArgumentException: id to load is required for loading` — an unhandled exception that yields a 500 response instead of a clean validation error routed back to the form.

**Fix:** Add `@NotNull` to `seasonId` in `MatchdayForm`:

```java
import jakarta.validation.constraints.NotNull;

@NotNull
private UUID seasonId;
```

With this in place, `result.hasErrors()` in the controller will catch the missing season before the service call, and the form will be re-rendered with a field error on `seasonId`.

---

### WR-02: Redirect URL appends literal `"null"` string when `seasonId` is null

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:113`

**Issue:** The redirect uses string concatenation:

```java
return "redirect:/admin/matchdays?seasonId=" + form.getSeasonId();
```

If `form.getSeasonId()` is null, this produces `redirect:/admin/matchdays?seasonId=null`. The `list` endpoint receives `seasonId` as a `UUID` `@RequestParam`, which Spring will fail to bind from the string `"null"`, resulting in a `400 Bad Request` instead of falling back to the default (all-seasons) view. In practice this path is prevented by WR-01 on the happy path (save succeeds only with a valid seasonId), but a defensive fix is still warranted.

**Fix:** Conditionally include the parameter:

```java
String redirectUrl = "/admin/matchdays";
if (form.getSeasonId() != null) {
    redirectUrl += "?seasonId=" + form.getSeasonId();
}
return "redirect:" + redirectUrl;
```

---

## Info

### IN-01: No test covers the null-seasonId validation path

**File:** `src/test/java/org/ctc/admin/controller/MatchdayControllerTest.java`

**Issue:** There is a test for blank `label` validation (line 153), but no corresponding test for a missing `seasonId`. Once `@NotNull` is added (WR-01 fix), a test should verify that submitting without a `seasonId` returns the form with field errors rather than a 500.

**Fix:** Add a test:

```java
@Test
void givenMissingSeasonId_whenSaveMatchday_thenReturnsFormWithErrors() throws Exception {
    mockMvc.perform(post("/admin/matchdays/save")
                    .param("label", "Some Label")
                    .param("sortIndex", "1"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/matchday-form"))
            .andExpect(model().attributeHasFieldErrors("form", "seasonId"));
}
```

---

_Reviewed: 2026-04-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
