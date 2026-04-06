---
phase: 10-service-refactoring
plan: "01"
subsystem: admin/service, admin/controller
tags: [refactoring, interface, dispatch, template-editor, arch-03]
dependency_graph:
  requires: []
  provides: [TemplateManageable interface, generic TemplateEditorController dispatch]
  affects: [TemplateEditorController, all 10 graphic service classes]
tech_stack:
  added: []
  patterns: [Spring Map<String, T> autowiring for polymorphic dispatch]
key_files:
  created:
    - src/main/java/org/ctc/admin/service/TemplateManageable.java
  modified:
    - src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java
    - src/main/java/org/ctc/admin/service/AbstractPlayoffRoundGraphicService.java
    - src/main/java/org/ctc/admin/service/TeamCardService.java
    - src/main/java/org/ctc/admin/service/LineupGraphicService.java
    - src/main/java/org/ctc/admin/service/SettingsGraphicService.java
    - src/main/java/org/ctc/admin/service/ResultsGraphicService.java
    - src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java
    - src/main/java/org/ctc/admin/service/OverlayGraphicService.java
    - src/main/java/org/ctc/admin/service/PowerRankingsGraphicService.java
    - src/main/java/org/ctc/admin/controller/TemplateEditorController.java
    - src/test/java/org/ctc/admin/controller/TemplateEditorControllerTest.java
decisions:
  - "Added implements TemplateManageable to 6 concrete services (not just 3 abstract bases) because LineupGraphicService, SettingsGraphicService, ResultsGraphicService, MatchResultsGraphicService, OverlayGraphicService, and PowerRankingsGraphicService all extend AbstractGraphicService directly"
  - "Spring Map<String, TemplateManageable> autowiring resolves beans by their default Spring bean name (lowercase class name), so TEMPLATE_TYPE_TO_BEAN maps URL path segments to exact bean names"
metrics:
  duration: ~15min
  completed: "2026-04-06T08:12:12Z"
  tasks_completed: 2
  files_changed: 11
  tests_added: 2
---

# Phase 10 Plan 01: TemplateEditorController Generic Dispatch Summary

**One-liner:** Replaced 20 copy-paste save/reset endpoints with 2 generic dispatch methods using Spring `Map<String, TemplateManageable>` autowiring and a `TemplateManageable` interface on all 10 graphic service classes.

## What Was Built

### Task 1: TemplateManageable Interface

Created `TemplateManageable` interface with 4 methods (`loadTemplate()`, `saveTemplate(String)`, `resetTemplate()`, `hasCustomTemplate()`) and added `implements TemplateManageable` to all 10 graphic service classes (3 abstract base classes + 6 concrete services extending `AbstractGraphicService` directly).

### Task 2: TemplateEditorController Refactor + Tests

- Replaced 10 individual service fields with `Map<String, TemplateManageable> templateServices` (Spring autowires all `TemplateManageable` beans by bean name)
- Added 3 static lookup maps: `TEMPLATE_TYPE_TO_BEAN` (URL segment → bean name), `TEMPLATE_TYPE_TO_ATTR` (URL segment → model attribute prefix), `TEMPLATE_TYPE_TO_LABEL` (URL segment → display name for flash messages)
- `index()` method refactored from 10 try-catch blocks to a single loop
- 20 `saveXxxTemplate()` / `resetXxxTemplate()` methods replaced by 2 generic `save()` / `reset()` endpoints using `@PathVariable String templateType`
- Invalid `templateType` is blocked via `TEMPLATE_TYPE_TO_BEAN.get(templateType) == null` check → redirects with `errorMessage: "Unknown template type"` (T-10-01 STRIDE threat mitigation)
- Added 2 new integration tests for invalid templateType save/reset
- All 783 tests pass, no regressions

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added TemplateManageable to 6 concrete services beyond the 3 abstract bases**

- **Found during:** Task 2 (test run — templateServices.get("lineupGraphicService") returned null)
- **Issue:** The plan specified adding `implements TemplateManageable` only to `AbstractMatchdayGraphicService`, `AbstractPlayoffRoundGraphicService`, and `TeamCardService`. However, 6 services extend `AbstractGraphicService` directly (not the abstract subclasses): `LineupGraphicService`, `SettingsGraphicService`, `ResultsGraphicService`, `MatchResultsGraphicService`, `OverlayGraphicService`, `PowerRankingsGraphicService`. These were not in the `Map<String, TemplateManageable>` Spring context.
- **Fix:** Added `implements TemplateManageable` to all 6 concrete services.
- **Files modified:** LineupGraphicService.java, SettingsGraphicService.java, ResultsGraphicService.java, MatchResultsGraphicService.java, OverlayGraphicService.java, PowerRankingsGraphicService.java
- **Commit:** ffc20b4

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | c9e74d8 | feat(10-01): create TemplateManageable interface and implement on service classes |
| Task 2 | ffc20b4 | refactor(10-01): replace 20 save/reset endpoints with 2 generic dispatch methods |

## Known Stubs

None.

## Threat Flags

No new threat surface introduced. The `templateType` path variable allowlist via `TEMPLATE_TYPE_TO_BEAN` null-check fully mitigates T-10-01 (tampering via arbitrary templateType).

## Self-Check

- [x] `TemplateManageable.java` created at correct path
- [x] All 10 graphic services implement `TemplateManageable`
- [x] `TemplateEditorController.java` contains exactly 2 save/reset POST handlers
- [x] Old methods `saveTeamCardTemplate`, `resetLineupTemplate` are gone
- [x] All 783 tests pass
- [x] Commits c9e74d8 and ffc20b4 exist

## Self-Check: PASSED
