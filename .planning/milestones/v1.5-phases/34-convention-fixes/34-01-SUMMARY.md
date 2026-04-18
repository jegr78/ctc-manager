---
phase: 34-convention-fixes
plan: "01"
subsystem: admin/controller
tags: [validation, form, convention, tdd]
dependency_graph:
  requires: []
  provides: [CONV-01]
  affects: [PlayoffController, PlayoffForm]
tech_stack:
  added: []
  patterns: ["@Valid + BindingResult", "Bean Validation"]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/dto/PlayoffForm.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/test/java/org/ctc/admin/controller/PlayoffControllerTest.java
decisions:
  - "Follow SeasonController pattern: @Valid before @ModelAttribute, BindingResult immediately after, repopulate model on error"
  - "Use @NotNull on UUID seasonId and @NotBlank on String name in PlayoffForm"
metrics:
  duration: "~3 minutes"
  completed: "2026-04-14"
  tasks_completed: 1
  files_modified: 3
  tests_added: 2
---

# Phase 34 Plan 01: PlayoffController @Valid + BindingResult Validation Summary

**One-liner:** Bean Validation on PlayoffForm with @Valid + BindingResult in save() following the SeasonController convention.

## What Was Built

Added form validation to `PlayoffController.save()` following the project convention (D-01, D-02):

1. **PlayoffForm** — Added `@NotNull` on `seasonId` and `@NotBlank` on `name` (jakarta.validation.constraints).
2. **PlayoffController.save()** — Added `@Valid` before `@ModelAttribute("playoffForm")`, `BindingResult bindingResult` immediately after (Spring MVC requirement), and `Model model` parameter. On validation failure, repopulates the `seasons` model attribute and returns the form view (no redirect, no 500).
3. **PlayoffControllerTest** — Two new integration tests prove validation failure behavior.

## Tasks

| # | Task | Commit | Status |
|---|------|--------|--------|
| RED | Add failing validation tests | 790fdde | Done |
| GREEN | Implement @Valid + @NotNull/@NotBlank | 025ec98 | Done |

## Test Results

- `givenBlankName_whenSavePlayoff_thenReturnsFormViewWithErrors` — PASS (status 200, form view, field error on name, seasons in model)
- `givenMissingSeasonId_whenSavePlayoff_thenReturnsFormViewWithErrors` — PASS (status 200, form view, field error on seasonId, seasons in model)
- `givenValidPlayoffForm_whenSavePlayoff_thenRedirectsAndPersists` — PASS (no regression)
- Full suite: **906 tests, 0 failures**, JaCoCo coverage check passed

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints or auth paths introduced.

## Self-Check: PASSED

- `src/main/java/org/ctc/admin/dto/PlayoffForm.java` — modified (contains @NotBlank, @NotNull)
- `src/main/java/org/ctc/admin/controller/PlayoffController.java` — modified (contains @Valid, BindingResult)
- `src/test/java/org/ctc/admin/controller/PlayoffControllerTest.java` — modified (contains givenBlankName_whenSavePlayoff)
- Commit 790fdde — exists
- Commit 025ec98 — exists
