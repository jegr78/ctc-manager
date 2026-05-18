---
phase: 74
plan: "08"
subsystem: backup-import
tags: [backup, import, controller, security, validation, csrf, multipart]
dependency_graph:
  requires:
    - "74-02 (BackupArchiveException Reason enum — 8 values for exhaustive switch)"
    - "74-03 (BackupImportPreview, BackupImportConfirmForm DTOs)"
    - "74-05 (BackupImportService.stage / reparse / deleteStagingFile)"
    - "74-06 (BackupUploadExceptionHandler — MaxUploadSizeExceededException Flash)"
  provides:
    - "POST /admin/backup/import-preview (multipart upload entry, redirects on error)"
    - "POST /admin/backup/import-confirm (stagingId → reparse → confirm page)"
    - "POST /admin/backup/import-execute (Phase-74 STUB: @Valid bind, D-09 reparse, D-02#5 flash)"
    - "POST /admin/backup/import-cancel (deleteStagingFile + redirect)"
    - "mapReason(BackupArchiveException) private helper — exhaustive 8-case switch"
    - "BackupImportControllerSecurityIT — 20-test profile-matrix"
    - "BackupImportConfirmFormValidationIT — 4 binding-chain tests incl. D-08 seam"
  affects:
    - "Plan 09 (backup-preview.html + backup-confirm.html full templates replace stubs)"
    - "Plan 10 (E2E import flow — controller endpoints are the HTTP surface)"
    - "Plan 75 (import-execute implementation inherits staging file)"
tech_stack:
  added: []
  patterns:
    - "BackupArchiveException.Reason exhaustive switch (Java 25) — compile-enforced coverage"
    - "IOException caught at all reparse() call sites (checked exception discipline)"
    - "@Valid @ModelAttribute immediately before BindingResult (Spring binding chain requirement)"
    - "Two-@Nested-class profile-matrix security IT shape (mirrors BackupControllerSecurityIT)"
    - "Service-layer stageValidZip() helper in IT — bypasses HTTP layer to avoid template dependency"
key_files:
  created:
    - src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java
    - src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java
    - src/main/resources/templates/admin/backup-preview.html
    - src/main/resources/templates/admin/backup-confirm.html
  modified:
    - src/main/java/org/ctc/backup/BackupController.java
decisions:
  - "IOException caught at all reparse() call sites — reparse() declares throws IOException; the plan spec showed only BackupArchiveException catches but the compiler enforced the fix (Rule 1)"
  - "Stub templates backup-preview.html and backup-confirm.html added to unblock BackupImportConfirmFormValidationIT — full templates ship in Plan 09 and will replace these byte-for-byte (Rule 3)"
  - "stageValidZip() in IT uses BackupImportService.stage() directly (service layer) instead of HTTP multipart endpoint — avoids the template rendering dependency in the test helper"
  - "D-08 seam test (Test 4) explicitly calls Files.deleteIfExists after assertion to keep test environment clean"
metrics:
  duration_seconds: ~600
  completed_date: "2026-05-13"
  tasks_completed: 3
  commits: 3
  test_methods: 24
---

# Phase 74 Plan 08: BackupController — 4 import endpoints + security/validation ITs

`BackupController` extended (NOT duplicated) with four Phase-74 import endpoints, a private `mapReason()` helper with exhaustive Java 25 switch over all 8 `BackupArchiveException.Reason` values, and two integration test classes covering the security matrix (20 tests) and the Spring binding chain (4 tests).

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Extend BackupController with 4 @PostMappings + mapReason helper | abcb6b6 | BackupController.java |
| 2 | BackupImportControllerSecurityIT (20 tests, two-@Nested profile matrix) | bf00160 | BackupImportControllerSecurityIT.java |
| 3 | BackupImportConfirmFormValidationIT (4 tests, D-08 seam) + stub templates | 727ea30 | BackupImportConfirmFormValidationIT.java, backup-preview.html, backup-confirm.html |

## Endpoints Delivered

| Endpoint | Method | Behavior |
|----------|--------|----------|
| `POST /admin/backup/import-preview` | multipart `file` | `service.stage()` → `admin/backup-preview`; on error → redirect with Flash |
| `POST /admin/backup/import-confirm` | `stagingId` UUID | `service.reparse()` → `admin/backup-confirm` + fresh `BackupImportConfirmForm`; on error → redirect |
| `POST /admin/backup/import-execute` | `@Valid BackupImportConfirmForm` | STUB: binding errors → re-render confirm; else `service.reparse()` (D-09) → D-02#5 Flash redirect |
| `POST /admin/backup/import-cancel` | `stagingId` UUID | `service.deleteStagingFile()` (idempotent) → redirect with "Import canceled." Flash |

## D-02 Flash Strings (verbatim)

- **D-02#3 safety-checks:** `Backup archive failed safety checks (size or path) and was rejected.`
- **D-02#5 stub success:** `Validation succeeded. Import execution will be enabled in Phase 75.`
- **Cancel:** `Import canceled.`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] IOException must be caught at all reparse() call sites**
- **Found during:** Task 1 (Maven compile)
- **Issue:** `BackupImportService.reparse()` declares `throws IOException` (checked exception); the plan spec showed only `BackupArchiveException` catches in `importConfirm` and `importExecute`.
- **Fix:** Added `catch (IOException ex)` at all three `reparse()` call sites in `importConfirm` and `importExecute`, logging via `log.error(...)` and emitting the D-02#3 Flash string.
- **Files modified:** `BackupController.java`
- **Commit:** abcb6b6

**2. [Rule 3 - Blocking] Stub Thymeleaf templates added to unblock BackupImportConfirmFormValidationIT**
- **Found during:** Task 3 (first IT run)
- **Issue:** `admin/backup-preview.html` and `admin/backup-confirm.html` do not exist yet (Plan 09 ships the full templates). Thymeleaf threw `TemplateInputException` during the security IT and the validation IT even though `stageValidZip()` was refactored to use the service layer, because `importPreview` and the binding-error re-render path still return these view names.
- **Fix:** Created minimal stub templates in `src/main/resources/templates/admin/`. Both stubs use the existing layout fragment and expose the form fields needed for `th:field="*{...}"` rendering. Plan 09 will replace these files with the full design-spec templates.
- **Files modified (new):** `backup-preview.html`, `backup-confirm.html`
- **Commit:** 727ea30

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `backup-preview.html` stub content | `src/main/resources/templates/admin/backup-preview.html` | Minimal layout-fragment wrapper; full template ships in Plan 09 per 74-UI-SPEC |
| `backup-confirm.html` stub content | `src/main/resources/templates/admin/backup-confirm.html` | Minimal layout-fragment wrapper with `th:field` bindings; full template ships in Plan 09 per 74-UI-SPEC |

## Phase-73 Byte-Identity Verification

`git diff 8d8e5ac..HEAD -- BackupController.java | grep "^-"` produced no output — zero deletions in the Phase-73 export endpoint code (lines 51-120 of the original file). Only additions.

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| `BackupController.java` exists | FOUND |
| `BackupImportControllerSecurityIT.java` exists | FOUND |
| `BackupImportConfirmFormValidationIT.java` exists | FOUND |
| `backup-preview.html` stub exists | FOUND |
| `backup-confirm.html` stub exists | FOUND |
| `08-SUMMARY.md` exists | FOUND |
| Commit abcb6b6 (feat: controller extension) | FOUND |
| Commit bf00160 (test: security IT) | FOUND |
| Commit 727ea30 (test: validation IT + stubs) | FOUND |
| Phase-73 lines byte-identical (no deletions in diff) | VERIFIED |
| 5 @PostMapping methods in BackupController (/export + 4 import) | VERIFIED |
| 24 IT tests pass (20 security + 4 validation) | VERIFIED |
