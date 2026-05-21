---
phase: 92
plan: 01
slug: carry-forwards-cleanup
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
---

# Plan 92-01 — Validation Slice

> Per-plan slice of `92-VALIDATION.md` per CONTEXT D-08.
> Substance: 6 rows 92-01-01..06 covering UX-01 typed-catch + badge UX on `CsvImportController`.

---

## Sampling Rate

- **Per-task command (after Task 1a, compile gate):** `./mvnw clean test-compile` (~30 s)
- **Per-task command (after Task 1b, test gate):** `./mvnw test -Dtest='CsvImportControllerExceptionTest'` (~35 s, 10 methods)
- **Per-plan command (Task 3, full gate):** `./mvnw verify` (~6:24 min, 1438+ tests + JaCoCo + SpotBugs)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 92-01-01 | 01 | 1 | UX-01 | T-91-02-IL | CsvImportController.previewSheet AUTH path → model `errorMessage="Authentication problem — re-link Google account"` + `errorCategory=AUTH`, no `e.getMessage()` echo | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenAuthFailure_whenPreviewSheet_thenRendersAuthBadge'` | ✅ | ✅ green |
| 92-01-02 | 01 | 1 | UX-01 | T-91-02-IL | previewSheet NOT_FOUND path → `errorMessage="Sheet not found — check ID"` + `errorCategory=NOT_FOUND` | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenNotFound_whenPreviewSheet_thenRendersNotFoundBadge'` | ✅ | ✅ green |
| 92-01-03 | 01 | 1 | UX-01 | T-91-02-IL | previewSheet PERMISSION path → `errorMessage="Access denied — share the sheet with the service account"` + `errorCategory=PERMISSION` | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenPermissionDenied_whenPreviewSheet_thenRendersPermissionBadge'` | ✅ | ✅ green |
| 92-01-04 | 01 | 1 | UX-01 | T-91-02-IL | previewSheet TRANSIENT path (new dedicated test method); the legacy `givenIoException_whenPreviewSheet_thenRedirectsWithError` was renamed/rewritten as `givenTransientFailure_whenPreviewSheet_thenRendersTransientBadge` to assert `errorCategory=TRANSIENT` via `equalTo` | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenTransientFailure_whenPreviewSheet_thenRendersTransientBadge'` | ✅ | ✅ green |
| 92-01-05 | 01 | 1 | UX-01 | T-91-02-IL | execute() — 4 typed-catch arms via redirect+flash (AUTH/NOT_FOUND/PERMISSION/TRANSIENT) | unit (MockMvc) | 4 new `@Test` methods on `CsvImportControllerExceptionTest`: `givenAuthFailure_whenExecuteSheet_thenRedirectsWithAuthBadge`, `givenNotFound_whenExecuteSheet_thenRedirectsWithNotFoundBadge`, `givenPermissionDenied_whenExecuteSheet_thenRedirectsWithPermissionBadge`, `givenTransientFailure_whenExecuteSheet_thenRedirectsWithTransientBadge` | ✅ | ✅ green |
| 92-01-06 | 01 | 1 | UX-01 | T-91-02-IL | Templates `admin/import.html` + `admin/import-preview.html` render badge block (verbatim copy from `driver-import.html`); auto-escaped via `th:text` (XSS mitigation T-92-01-XSS preserved) | Thymeleaf parse + integration (existing) | `./mvnw verify` boots app + renders templates without `TemplateInputException`; visual UAT remains outstanding per STATE.md "Pending UATs UX-01" (post-deploy operator action) | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` — extended in place: 10 `@Test` methods total (1 legacy CSV IOException kept + 1 legacy BusinessRuleException kept + 4 new previewSheet typed permits + 4 new execute typed permits)

---

## Sign-Off

| Field | Value |
|-------|-------|
| Shipper | gsd-executor (inline, sequential) |
| Ship date | 2026-05-21 |
| Commit SHA short | _(filled by commit step)_ |
| `./mvnw verify` exit code | 0 (BUILD SUCCESS, 6:24 min) |
| Tests run | 1438 (Failures: 0, Errors: 0; 1 transient Playwright/Chromium screenshot flake in `DriverProfilePageGeneratorTest.setUp` recovered on isolated rerun) |
| JaCoCo line coverage | 88.2258 % (covered=7643, missed=1020; > 82 % pom gate; v1.11 88.88 % baseline restoration is Plan 92-02's responsibility) |
| SpotBugs BugInstance count | 0 |
| `grep -c "e.getMessage()" CsvImportController.java` | 3 (only in surviving IllegalArgumentException + BusinessRule/Validation/IllegalArgument + IllegalState/IOException/DataAccess catch arms — never on a typed Google catch arm; T-91-02-IL invariant honored) |
| Templates rendered | `admin/import.html`, `admin/import-preview.html` — verbatim error-badge block above `<h1>` body |
| Outstanding manual UAT | 4-badge visual verification on `/admin/import` (post-deploy operator action per STATE.md "Pending UATs UX-01") |
| `nyquist_compliant` | `true` |
