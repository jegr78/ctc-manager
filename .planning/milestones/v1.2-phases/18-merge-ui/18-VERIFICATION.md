---
phase: 18-merge-ui
verified: 2026-04-07T14:00:00Z
status: human_needed
score: 5/5 must-haves verified
gaps: []
human_verification:
  - test: "Navigate to any driver detail page and verify the Merge button is visible in the toolbar between Edit and Delete"
    expected: "A 'Merge' button with btn-secondary styling appears between the Edit and Delete buttons"
    why_human: "Visual placement and CSS rendering cannot be verified programmatically"
  - test: "Click Merge, select a target driver from the dropdown, click Select Target, then verify the preview page appears"
    expected: "Preview page shows a table with FK reference counts for SeasonDriver, RaceLineup, RaceResult, and PsnAlias rows; empty state shown if all counts are zero"
    why_human: "Two-state template switching and Thymeleaf rendering require browser verification"
  - test: "On the preview page, click 'Confirm Merge' and verify a JavaScript confirm dialog appears with both driver PSN IDs and 'cannot be undone' text"
    expected: "Browser shows: 'Really merge <sourcePsnId> into <targetPsnId>? This cannot be undone.'"
    why_human: "JS confirm dialog behavior requires manual browser interaction"
  - test: "After confirming the merge dialog, verify redirect to the target driver detail page with a green success flash message"
    expected: "Redirected to /admin/drivers/{targetId}, success flash message reads 'Driver merged: <source> into <target> — N references reassigned, N duplicates resolved'"
    why_human: "Flash message rendering and page redirect require browser verification"
  - test: "After merge, navigate back to the driver list and verify the source driver is no longer listed"
    expected: "Source driver absent from list; target driver still present"
    why_human: "Visual confirmation of post-merge state requires browser verification"
---

# Phase 18: Merge UI Verification Report

**Phase Goal:** Admin can initiate, preview, and confirm a driver merge from the driver detail page
**Verified:** 2026-04-07T14:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A merge button is visible on the driver detail page that opens the merge workflow | VERIFIED | `driver-detail.html` line 13: `<a th:href="@{/admin/drivers/{id}/merge(id=${driver.id})}" class="btn btn-secondary">Merge</a>` between Edit (line 12) and Delete (line 16) |
| 2 | Admin can search for and select the target driver from all existing drivers except the current one | VERIFIED | `DriverController.mergeForm()` filters out source ID, sorts by PSN-ID case-insensitive; `driver-merge.html` renders sorted `<select>` dropdown with all other drivers |
| 3 | Admin sees a preview listing the number of SeasonDriver, RaceLineup, RaceResult, and PsnAlias entries that will be reassigned | VERIFIED | `driver-merge.html` State 2 table renders `preview.seasonDriversToReassign()`, `preview.raceLineupsToReassign()`, `preview.raceResultsToReassign()`, `preview.psnAliasesToReassign()` and corresponding duplicate counts; backed by `DriverMergeService.previewMerge()` |
| 4 | Admin must explicitly confirm the merge before any data is changed | VERIFIED | `driver-merge.html` line 88–89: form has `th:attr="onsubmit='return confirm(\'Really merge ... This cannot be undone.\')"` — browser blocks submission if cancelled |
| 5 | After a successful merge the admin is redirected to the target driver's detail page with a success message | VERIFIED | `DriverController.executeMerge()` returns `"redirect:/admin/drivers/" + targetId` with `successMessage` flash attribute containing source and target PSN-IDs plus counts |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/domain/service/DriverMergeService.java` | MergePreview record and previewMerge() method | VERIFIED | Lines 32–45: `public record MergePreview(...)` with 7 fields and `totalToReassign()`/`totalDuplicates()` computed methods; lines 47–97: `@Transactional(readOnly = true) public MergePreview previewMerge(UUID, UUID)` |
| `src/test/java/org/ctc/domain/service/DriverMergeServiceTest.java` | PreviewMergeTests nested class with 6 tests | VERIFIED | Lines 522–690: `@Nested class PreviewMergeTests` with 6 test methods covering self-merge, non-existent source/target, mixed conflict counts, zero counts, and no-mutation assertion |
| `src/main/java/org/ctc/admin/controller/DriverController.java` | 3 new endpoints: GET /{id}/merge, POST /{id}/merge/preview, POST /{id}/merge | VERIFIED | Lines 108–149: all three endpoints present, `driverMergeService` injected as final field (line 29); error handling via try/catch with flash redirect pattern |
| `src/main/resources/templates/admin/driver-merge.html` | Two-state merge template (select target vs. preview+confirm) | VERIFIED | Lines 7 and 38: `th:if="${preview == null}"` / `th:if="${preview != null}"` controls State 1 (dropdown) and State 2 (preview table + confirm form) |
| `src/main/resources/templates/admin/driver-detail.html` | Merge button in toolbar | VERIFIED | Line 13: `<a th:href="@{/admin/drivers/{id}/merge(id=${driver.id})}" class="btn btn-secondary">Merge</a>` between Edit and Delete |
| `src/test/java/org/ctc/admin/controller/DriverControllerTest.java` | Integration tests for merge endpoints | VERIFIED | Lines 204–278: 5 integration tests covering GET merge form, source exclusion from dropdown, preview state, redirect on confirm, and source driver deletion |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `driver-detail.html` | `/admin/drivers/{id}/merge` | href on Merge button | VERIFIED | Line 13: `th:href="@{/admin/drivers/{id}/merge(id=${driver.id})}"` |
| `DriverController.mergeForm()` | `DriverService.findAll()` | loads driver dropdown | VERIFIED | Line 112: `driverService.findAll().stream()...` |
| `DriverController.mergeForm()` | `admin/driver-merge` | returns template name | VERIFIED | Line 117: `return "admin/driver-merge"` |
| `DriverController.previewMerge()` | `DriverMergeService.previewMerge()` | service delegation | VERIFIED | Line 124: `driverMergeService.previewMerge(id, targetId)` |
| `DriverController.executeMerge()` | `DriverMergeService.merge()` | service delegation | VERIFIED | Line 137: `driverMergeService.merge(id, targetId)` |
| `DriverController.executeMerge()` | `/admin/drivers/{targetId}` | redirect after success | VERIFIED | Line 143: `return "redirect:/admin/drivers/" + targetId` |
| `DriverMergeService.previewMerge()` | `SeasonDriverRepository.findByDriverId()` | read-only query loop | VERIFIED | Line 63: `seasonDriverRepository.findByDriverId(sourceId)` |
| `DriverMergeService.previewMerge()` | `RaceLineupRepository.findByDriverId()` | read-only query loop | VERIFIED | Line 74: `raceLineupRepository.findByDriverId(sourceId)` |
| `DriverMergeService.previewMerge()` | `RaceResultRepository.findByDriverId()` | read-only query loop | VERIFIED | Line 85: `raceResultRepository.findByDriverId(sourceId)` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `driver-merge.html` (State 1) | `allDrivers` | `DriverController.mergeForm()` → `driverService.findAll()` → JPA repository | Yes — live DB query | FLOWING |
| `driver-merge.html` (State 2) | `preview` | `DriverController.previewMerge()` → `driverMergeService.previewMerge()` → FK table queries | Yes — 4 read-only query loops | FLOWING |

### Behavioral Spot-Checks

Behavioral spot-checks skipped for controller/template endpoints — requires running application server. Integration tests (DriverControllerTest) provide equivalent coverage with MockMvc. All 850 tests pass per confirmed BUILD SUCCESS.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| MERGE-01 | 18-02 | Admin kann auf der Fahrer-Detailseite einen Merge starten | SATISFIED | Merge button in driver-detail.html toolbar links to GET /{id}/merge; integration test `givenExistingDriver_whenGetMergeForm_thenReturnsMergeView` |
| MERGE-02 | 18-02 | Admin kann den Ziel-Fahrer auswaehlen, in den gemergt wird | SATISFIED | Sorted dropdown of all drivers excluding source in driver-merge.html State 1; integration test `givenTwoDrivers_whenGetMergeForm_thenSourceExcludedFromDropdown` |
| MERGE-03 | 18-01, 18-02 | Admin sieht eine Vorschau der betroffenen Referenzen vor dem Merge | SATISFIED | previewMerge() returns per-FK-table counts; driver-merge.html State 2 renders table; integration test `givenTwoDrivers_whenPostPreview_thenReturnsPreviewState` |
| MERGE-04 | 18-02 | Admin muss den Merge explizit bestaetigen | SATISFIED | JS confirm() guard on form onsubmit with dynamic driver names and "cannot be undone" text; separate POST /{id}/merge endpoint only executes after confirmation |

**Orphaned requirements check:** REQUIREMENTS.md maps MERGE-05 through MERGE-14 to Phases 16 and 17 — none orphaned to Phase 18.

### Anti-Patterns Found

No anti-patterns detected in any modified file.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | No issues found |

### Human Verification Required

The visual and interactive aspects of the merge workflow require manual browser testing. The auto-chain mode in Plan 02 skipped the blocking human checkpoint (Task 2). These items must be verified before the phase can be considered fully passed.

#### 1. Merge Button Visibility and Position

**Test:** Start dev server (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`), navigate to any driver detail page at `http://localhost:9090/admin/drivers/{id}`
**Expected:** A secondary-styled "Merge" button appears in the toolbar between the "Edit" button and the "Delete" button
**Why human:** CSS rendering and visual button order require browser verification

#### 2. Merge Form and Target Dropdown

**Test:** Click the "Merge" button from the driver detail page
**Expected:** Merge form loads at `/admin/drivers/{id}/merge` showing: source driver info (PSN-ID + nickname), a sorted dropdown of all other drivers (excluding the source), a "Select Target" submit button, and a "Back to Driver" link
**Why human:** Thymeleaf template rendering and dropdown population require browser verification

#### 3. Preview Page with FK Reference Counts

**Test:** Select any target driver from the dropdown and click "Select Target"
**Expected:** Preview page loads at the same URL showing: a table with rows for SeasonDriver, RaceLineup, RaceResult, PsnAlias with Reassign and Duplicates Dropped counts; an empty-state message if all counts are zero
**Why human:** Two-state template switching and table rendering require browser verification

#### 4. JavaScript Confirm Dialog

**Test:** On the preview page, click "Confirm Merge"
**Expected:** Browser displays a JavaScript confirm dialog with text: "Really merge {sourcePsnId} into {targetPsnId}? This cannot be undone." — clicking Cancel must abort the request
**Why human:** JS confirm dialog behavior requires interactive browser testing

#### 5. Merge Execution, Redirect, and Flash Message

**Test:** On the confirm dialog, click OK
**Expected:** Redirect to `/admin/drivers/{targetId}`, page shows a green success flash message containing both driver PSN-IDs and counts; source driver no longer appears in the driver list
**Why human:** Flash message rendering and post-merge visual state require browser verification

### Gaps Summary

No automated gaps found. All 5 roadmap success criteria are fully wired through the codebase. The only open item is the human visual checkpoint that was auto-skipped during plan execution.

---

_Verified: 2026-04-07T14:00:00Z_
_Verifier: Claude (gsd-verifier)_
