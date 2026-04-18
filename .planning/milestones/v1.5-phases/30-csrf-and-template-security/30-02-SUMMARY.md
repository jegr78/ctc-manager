---
phase: 30-csrf-and-template-security
plan: "02"
subsystem: template-security
tags: [security, tdd, template-validation, spel]
dependency_graph:
  requires: []
  provides: [template-save-validation, context-aware-spel-detection]
  affects: [TemplateEditorController, TemplatePreviewService]
tech_stack:
  added: []
  patterns: [defense-in-depth, context-aware-security-scan]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/service/TemplatePreviewService.java
    - src/main/java/org/ctc/admin/controller/TemplateEditorController.java
    - src/test/java/org/ctc/admin/service/TemplatePreviewServiceTest.java
    - src/test/java/org/ctc/admin/controller/TemplateEditorControllerTest.java
decisions:
  - "Context-aware T() detection (only inside ${...}) eliminates CSS false positives while preserving SpEL injection coverage"
  - "validateTemplateContent() made public to allow cross-package access from controller"
  - "Generic error message 'Template contains unsafe expressions' — no pattern details exposed to client"
metrics:
  duration: ~7min
  completed: 2026-04-13
  tasks_completed: 2
  files_changed: 4
---

# Phase 30 Plan 02: Template Save Validation and T() False-Positive Fix Summary

**One-liner:** Context-aware SpEL T() detection (only inside `${...}`) plus defense-in-depth validation on the save endpoint.

## What Was Built

Two security improvements to the template editor:

1. **Save endpoint validation (defense-in-depth):** `TemplateEditorController.save()` now calls `templatePreviewService.validateTemplateContent(template)` before `saveTemplate()`. A `TemplateSecurityException` is caught and returns a generic error flash attribute without exposing pattern details. The warning is logged server-side with `templateType` and exception message.

2. **Context-aware SpEL T() detection:** `containsSpringElTypeAccess()` was rewritten to scan for `T(` patterns only inside `${...}` expression blocks (same approach as `containsOgnlStaticAccess()`). CSS functions like `translateY()`, `rotateZ()`, `scaleX()` and plain text like `T (Alpha)` outside expression blocks are no longer falsely rejected.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | RED — Write failing tests | 7dbe82a | TemplateEditorControllerTest.java, TemplatePreviewServiceTest.java |
| 2 | GREEN — Implement save-validation, fix T() detection | 798c605 | TemplatePreviewService.java, TemplateEditorController.java |

## Tests Added

- `TemplateEditorControllerTest.givenMaliciousTemplate_whenSave_thenRedirectsWithError` — save endpoint rejects SpEL injection
- `TemplatePreviewServiceTest.givenCssTransformFunction_whenValidate_thenAcceptsTemplate` — CSS transforms no longer trigger rejection
- `TemplatePreviewServiceTest.givenTextWithParenthesisAfterT_whenValidate_thenAcceptsTemplate` — plain text `T (Alpha)` no longer triggers rejection

## Verification Results

- `grep 'public void validateTemplateContent'` — method is public
- `grep 'templatePreviewService.validateTemplateContent'` — validation called in save()
- `grep 'TemplateSecurityException'` — exception caught in save()
- `./mvnw verify` — 866 tests, 0 failures, JaCoCo coverage checks passed

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — all security surface changes were anticipated in the plan's threat model (T-30-02, T-30-03, T-30-05).

## Self-Check: PASSED

- [x] `src/main/java/org/ctc/admin/service/TemplatePreviewService.java` — modified, contains `public void validateTemplateContent` and context-aware `containsSpringElTypeAccess`
- [x] `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` — modified, contains `validateTemplateContent` call and `TemplateSecurityException` catch
- [x] `src/test/java/org/ctc/admin/controller/TemplateEditorControllerTest.java` — contains `givenMaliciousTemplate_whenSave_thenRedirectsWithError`
- [x] `src/test/java/org/ctc/admin/service/TemplatePreviewServiceTest.java` — contains `givenCssTransformFunction_whenValidate_thenAcceptsTemplate`
- [x] Commit 7dbe82a exists (RED tests)
- [x] Commit 798c605 exists (GREEN implementation)
- [x] Full suite: 866 tests, BUILD SUCCESS
