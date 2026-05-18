---
phase: 74
plan: "09"
title: "Templates — backup landing extension + preview + confirm pages"
subsystem: backup-import-ui
tags: [templates, thymeleaf, backup-import, ui, preview, confirm, playwright-cli]
dependency_graph:
  requires: ["74-08"]
  provides: [backup-import-ui-templates]
  affects: [backup-controller, backup-import-confirm-form-validation]
tech_stack:
  added: []
  patterns: [thymeleaf-fragment-layout, form-check-pattern, sibling-forms-actions, th-classappend-delta-pill]
key_files:
  created:
    - src/main/resources/templates/admin/backup-preview.html
    - src/main/resources/templates/admin/backup-confirm.html
  modified:
    - src/main/resources/templates/admin/backup.html
decisions:
  - "backupImportConfirmForm as model attribute name (matches controller, not ${confirmForm} as originally planned)"
  - "Cancel as POST sibling form (CSRF discipline, no GET mutation)"
  - "HTML5 nested-form resolution: sibling forms inside .actions with form=executeForm attribute on Execute button"
  - "span tag for field-error over small tag (avoids browser 80% font-size side-effect)"
metrics:
  duration_minutes: 35
  completed: "2026-05-13T18:25:00Z"
  tasks_completed: 4
  tasks_total: 4
  files_changed: 10
---

# Phase 74 Plan 09: Templates — backup landing extension + preview + confirm pages Summary

Three Thymeleaf SSR templates completing the Phase 74 Backup Import UI: backup landing extended with Import Backup card (D-23), full preview page with 24-card grid and delta pills (D-03/D-04/D-05/D-06/D-07), full confirm page with destructive action gate (D-10). All validated by playwright-cli with 7 screenshots (Desktop + Mobile for all three pages + validation error flow).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extend backup.html with Import Backup card (D-23) | 450c9f1 | src/main/resources/templates/admin/backup.html |
| 2 | Create backup-preview.html full version (D-03/04/05/06/07) | d95a4d2 | src/main/resources/templates/admin/backup-preview.html |
| 3 | Create backup-confirm.html full version (D-10) | 5bb8a14 | src/main/resources/templates/admin/backup-confirm.html |
| 4 | playwright-cli visual verification (checkpoint:human-verify) | 632aec0 | .screenshots/74-09-*.png (7 files) |

## DTO Bindings

| Template | Model Attribute | Type | Source Plan |
|----------|----------------|------|-------------|
| backup-preview.html | `${preview}` | `BackupImportPreview` record | Plan 03 |
| backup-preview.html | `${sizeInMb}` | `String` (pre-formatted) | Plan 05 — NOT YET SET (see Known Stubs) |
| backup-confirm.html | `${backupImportConfirmForm}` | `BackupImportConfirmForm` | Plan 06 |
| backup-confirm.html | `${preview}` | `BackupImportPreview` record | Plan 06 |
| backup-confirm.html | `${entityCount}` | `int` | Plan 06 — NOT YET SET (see Known Stubs) |
| backup-confirm.html | `${sizeInMb}` | `String` (pre-formatted) | Plan 06 — NOT YET SET (see Known Stubs) |

## Key Design Decisions

### Cancel as POST sibling form (not GET link)
Plan 08 defines `POST /admin/backup/import-cancel` for CSRF discipline — a GET that mutates filesystem state would be an anti-pattern. The template uses a `<form method="post">` sibling alongside the Execute form. Both are wrapped in `<div class="actions">` for the 8px flex gap. Keyboard accessibility is preserved: `<button>` inside `<form>` is fully keyboard-focusable.

### HTML5 nested-form constraint resolution
A `<form>` cannot contain another `<form>` (HTML5 §4.10.3). Resolution on `backup-confirm.html`: the Execute form (`id="executeForm"`) contains the checkbox and field-error; it closes before `.actions`. The Execute button uses `form="executeForm"` to associate out-of-form. The Cancel form is a sibling. Both forms sit inside `<div class="actions">` as siblings — valid HTML5.

### `<span>` over `<small>` for field-error
The CSS rule `.form-group .field-error` (admin.css:318) styles by class, not by tag. `<small>` carries a browser-default 80% font-size side-effect that compounds with the CSS rule. `<span>` is semantically neutral and matches the `season-form.html:14` pattern.

### Model attribute name: `backupImportConfirmForm` (not `confirmForm`)
The `09-PLAN.md` interface spec listed `${confirmForm}` as the expected model attribute name. However, `BackupController.importConfirm()` (Plan 06) and `BackupController.importExecute()` (Plan 08) use `"backupImportConfirmForm"` as the attribute name. The template was corrected to use `${backupImportConfirmForm}` to match the controller. This deviation from the plan spec was required for the `BackupImportConfirmFormValidationIT` tests to pass.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Template model attribute name corrected from ${confirmForm} to ${backupImportConfirmForm}**
- **Found during:** Task 3 test run (BackupImportConfirmFormValidationIT failures)
- **Issue:** `BackupController` uses `"backupImportConfirmForm"` as model attribute name, not `"confirmForm"` as the Plan 09 interface spec stated
- **Fix:** Updated `backup-confirm.html` to use `th:object="${backupImportConfirmForm}"` and `${backupImportConfirmForm.stagingId}` for Cancel form
- **Files modified:** `src/main/resources/templates/admin/backup-confirm.html`
- **Commit:** 5bb8a14

## Known Stubs

| Stub | File | Line | Reason |
|------|------|------|--------|
| `${sizeInMb}` renders empty | backup-preview.html | 12 | BackupController.importPreview() (Plan 05) does not add `sizeInMb` to model. Preview page shows "Size: MB" instead of "Size: 12.4 MB" |
| `${sizeInMb}` renders empty | backup-confirm.html | 27 | BackupController.importConfirm() (Plan 06) does not add `sizeInMb` to model. Confirm recap shows "( MB)" |
| `${entityCount}` renders empty | backup-confirm.html | 23 | BackupController.importConfirm() (Plan 06) does not add `entityCount` to model. Confirm recap shows "across tables" |

These stubs block correct display but do NOT block the functional flow (preview → confirm → execute). Plans 05/06 must add `model.addAttribute("sizeInMb", ...)` and `model.addAttribute("entityCount", ...)` to the respective controller methods.

## Test Impact (Plan 11/12)

Locked strings and selectors that Plans 11/12 depend on:
- Button text `"Import Backup"` → `page.getByRole(AriaRole.BUTTON, name="Import Backup")`
- Button text `"Proceed to Confirm"` → submit form on preview page
- Button text `"Execute Import"` → destructive submit on confirm page
- Button text `"Cancel"` → cancel submit on both pages
- CSS selector `.card-grid > .card` → 24-card grid on preview page
- CSS selector `.field-error` → validation error message on confirm page
- `#acknowledged` → checkbox ID on confirm page

## Visual Verification Results

Screenshots captured via playwright-cli (v1.59.0-alpha) against dev server (`spring-boot:run -Pdev`):

| Screenshot | Path | Status |
|------------|------|--------|
| Landing Desktop 1280x800 | `.screenshots/74-09-backup-landing-desktop.png` | PASS — Two stacked cards visible (Export + Import Backup) |
| Landing Mobile 375x667 | `.screenshots/74-09-backup-landing-mobile.png` | PASS — Single-column layout, sidebar toggle visible |
| Preview Desktop 1280x800 | `.screenshots/74-09-backup-preview-desktop.png` | PASS — h1, header card, green schema banner, 4-column card grid |
| Preview Mobile 375x667 | `.screenshots/74-09-backup-preview-mobile.png` | PASS — Single-column card stack |
| Confirm Desktop 1280x800 | `.screenshots/74-09-backup-confirm-desktop.png` | PASS — Yellow warning, recap (entityCount/sizeInMb empty), checkbox, Cancel + Execute Import buttons |
| Confirm Mobile 375x667 | `.screenshots/74-09-backup-confirm-mobile.png` | PASS — Full-width layout |
| Confirm Validation Error 1280x800 | `.screenshots/74-09-backup-confirm-validation-error.png` | PASS — Red .field-error text inline below checkbox |

**Observation:** `entityCount` and `sizeInMb` show empty on both preview and confirm pages because the controller (Plans 05/06) does not yet add these attributes to the model. All other content is correct.

**JS confirm() dialog:** Verified — clicking Execute Import without checking the checkbox triggers the JS `confirm()` dialog (accepted by playwright). Server-side `@AssertTrue` re-renders the page with the field-error inline.

## Verification Gates

- [x] D-02 locked strings: "I am an admin and I understand all operational data will be deleted." present verbatim in backup-confirm.html
- [x] Zero new CSS classes: admin.css diff is empty
- [x] Zero inline `style="..."` attributes in all three templates
- [x] English-only UI: no German strings (grep clean)
- [x] No nested `<form>` elements (HTML5 valid)
- [x] BackupControllerIT: 2/2 PASS
- [x] BackupImportConfirmFormValidationIT: 4/4 PASS
- [x] TemplateRenderingSmokeIT: 65/65 PASS
- [x] `./mvnw verify`: 203 tests, 0 failures, BUILD SUCCESS

## Self-Check: PASSED

- [x] `src/main/resources/templates/admin/backup.html` — FOUND and correct
- [x] `src/main/resources/templates/admin/backup-preview.html` — FOUND, replaces 26-line stub with 43-line full version
- [x] `src/main/resources/templates/admin/backup-confirm.html` — FOUND, replaces 34-line stub with 51-line full version
- [x] Commit 450c9f1 — FOUND (backup.html extension)
- [x] Commit d95a4d2 — FOUND (backup-preview.html full version)
- [x] Commit 5bb8a14 — FOUND (backup-confirm.html full version)
- [x] Commit 632aec0 — FOUND (playwright-cli screenshots)
- [x] STATE.md — NOT modified (per plan instructions)
- [x] ROADMAP.md — NOT modified (per plan instructions)
