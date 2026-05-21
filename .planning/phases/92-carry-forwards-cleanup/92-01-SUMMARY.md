---
phase: 92
plan: 01
slug: carry-forwards-cleanup
status: shipped
shipped: 2026-05-21
requirement: UX-01
---

# Plan 92-01 — UX-01 typed-catch + badge UX for CsvImportController

Re-closed threat T-91-02-IL for the 3rd Google-Sheets consumer (`CsvImportController`) by
propagating the Phase 91 typed-catch + `errorCategory` flash + BEM badge pattern from
`DriverSheetImportController` and `RaceController`.

## Files modified

| File | Change |
|------|--------|
| `src/main/java/org/ctc/dataimport/CsvImportController.java` | 5-arm typed-catch on `previewSheet()` (4 sealed permits + defensive base) above the preserved `IllegalArgumentException|IllegalStateException` arm; 8-arm shape on `execute()` (4 typed permits + defensive base + BusinessRule/Validation/IllegalArgument + DataIntegrityViolation + IllegalState/IOException/DataAccess). IOException relocated from the BusinessRule arm into the IllegalState/DataAccess arm because the typed permits already handle Google-side IOException via the sealed hierarchy. |
| `src/main/resources/templates/admin/import.html` | Verbatim error-badge alert block inserted between `<h1>Import</h1>` and `<!-- Source Tabs -->`. |
| `src/main/resources/templates/admin/import-preview.html` | Same verbatim error-badge block inserted between `<h1>Import Preview</h1>` and the first `<div class="card mb-md">`. Sits above per-preview `preview.hasErrors()` blocks. |
| `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` | Rewritten in place — 10 `@Test` methods total: 1 legacy CSV-IOException (renamed signal-only path), 1 legacy BusinessRuleException-on-execute, 4 new previewSheet typed-permit tests, 4 new execute typed-permit tests. All typed tests use Hamcrest `equalTo` on `errorMessage` + `errorCategory` to encode the T-91-02-IL invariant. |
| `.planning/phases/92-carry-forwards-cleanup/92-01-VALIDATION.md` | Per-plan Nyquist slice per CONTEXT D-08. |

## ./mvnw verify summary

- BUILD SUCCESS — total time 6:24 min
- Tests run: 1438; Failures: 0; Errors: 0; Skipped: 1
- JaCoCo line coverage: **88.2258 %** (covered=7643, missed=1020; > 82 % pom gate). v1.11 88.88 % baseline restoration is Plan 92-02's responsibility (`RaceControllerCalendarTest` + 2 new ITs).
- SpotBugs `BugInstance` count: **0** (per CONTEXT D-07)
- Header guard on `target/site/jacoco/jacoco.csv`: columns 8/9 confirmed `LINE_MISSED`/`LINE_COVERED`
- Surefire/Failsafe routing: untagged `*Test.java` ran under Surefire, `*IT.java` under Failsafe — no tag drift

### Playwright flake note

One transient error in `DriverProfilePageGeneratorTest.setUp` (Chromium `Page.captureScreenshot` Protocol error) recovered on isolated rerun (6/6 green). Not related to UX-01 surface — no template files in the failing-test code path. Captured here per [[feedback-no-flaky-dismissal]] for traceability; root cause is Playwright browser-state under parallel-fork load (recurring environmental signal across this codebase; not a regression).

## T-91-02-IL invariant

`grep -c "e.getMessage()" src/main/java/org/ctc/dataimport/CsvImportController.java` returns **3** — all in surviving non-Google catches:

1. `preview()` (CSV upload, IOException path) — line ~58: `"Error reading CSV: " + e.getMessage()` — client-side input error (multipart parse), not Google-side leakage.
2. `previewSheet()` IllegalArgumentException|IllegalStateException arm — line ~159: `"Error reading Google Sheet: " + e.getMessage()` — `extractSpreadsheetId(URL)` validation error, not Google API content.
3. `execute()` BusinessRule|Validation|IllegalArgument arm — line ~252: `"Import failed: " + e.getMessage()` — domain validation surface, not Google API content.

Zero `e.getMessage()` echoes on any typed Google catch arm. The 4 whitelisted user-message literals appear 4 occurrences each (2 in `previewSheet()` typed + defensive, 2 in `execute()` typed + defensive); `"Connection problem — retry"` appears 4 times total.

## Rolling Draft milestone PR

- URL: _(filled by next step)_
- Title: `feat(v1.13): discord integration & carry-forwards`
- State: Draft
- Body: minimal placeholder (Plan 98-03 owns the final composite body per CONTEXT D-06)
- Assignee: jegr78

## Outstanding manual UAT

UX-01 visual verification — trigger each of the 4 typed `GoogleApiException` categories on `/admin/import` (revoked credentials / nonexistent sheet ID / private sheet / network timeout) and capture Desktop + Mobile screenshots per [[feedback-playwright-cli]] + [[feedback-screenshots-folder]]. Tracked under STATE.md "Pending UATs UX-01" (post-deploy operator action).

## Phase 92 VALIDATION.md row status flips

92-01-01..06: ⬜ → ✅ (all 6 rows green on plan ship).

## Per-plan 92-01-VALIDATION.md

Authored at `.planning/phases/92-carry-forwards-cleanup/92-01-VALIDATION.md` with `nyquist_compliant: true` + 6-row Per-Task Verification Map + Sign-Off block (per CONTEXT D-08).
