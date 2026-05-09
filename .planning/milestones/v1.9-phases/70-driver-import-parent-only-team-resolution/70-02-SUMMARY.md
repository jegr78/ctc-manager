---
phase: 70
plan: 02
subsystem: dataimport
tags: [refactor, driver-import, ux-decommission, group-resolution-removal, controller-cleanup, template-cleanup]
requires:
  - phase: 70
    plan: 01
    note: "Plan 70-01 reduced TabPreview record (no warnings, no usesGroups) and 5 row records (no resolvedGroupName) — Plan 70-02 cleans up the controller + template + controller-test consumers of those removed fields"
provides:
  - "Slimmed DriverSheetImportController without showGroupColumn model attribute and without page-wide GROUPS-detection"
  - "Slimmed driver-import-preview.html template (5 buckets without Group column header/cells; no warning box)"
  - "Slimmed DriverSheetImportControllerTest without GROUPS-layout / null-resolvedGroupName test cases"
affects:
  - src/main/java/org/ctc/admin/controller/DriverSheetImportController.java
  - src/main/resources/templates/admin/driver-import-preview.html
  - src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java
tech-stack:
  added: []
  patterns:
    - "UX decommission of obsolete preview surface (Phase 70 D-08/D-09 — inverts Phase 66 D-04..D-09)"
key-files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/controller/DriverSheetImportController.java
    - src/main/resources/templates/admin/driver-import-preview.html
    - src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java
key-decisions:
  - "D-09 implement (controller side): showGroupColumn model attribute + page-wide GROUPS-detection block deleted; PhaseLayout/PhaseType/SeasonPhaseService imports + seasonPhaseService field removed"
  - "D-09 implement (template side): Group <th> header + per-row 3-span <td> Group cell removed from all 5 buckets (New Drivers, New Assignments, Conflicts, Fuzzy Match Suggestions, Unchanged); 'Group assignment warnings' alert box removed; Errors bucket and Skip/Accept checkboxes preserved"
  - "D-09 + D-11 implement (test side): two showGroupColumn-related test methods deleted; PhaseLayout import + SeasonPhaseRepository import + @Autowired field removed (no surviving references)"
  - "D-14 confirm: no test data added; only deletions; surviving tests already use T-Phase60-DIP-* test prefix"
  - "D-18 confirm: branch invariant gsd/v1.9-season-phases-groups held across all three commits"
  - "D-19 confirm: atomic per-task commits, refactor(70-02)/test(70-02): scope, no branch switching/stash/reset"
  - "D-20 confirm: refactor(70-02): for the two UX-decommission commits, test(70-02): for the test-deletion commit"
  - "D-21 confirm: no mid-phase ./mvnw verify; production compile only (./mvnw clean compile -> BUILD SUCCESS, 182 source files)"
patterns-established:
  - "Cross-file decommission pattern: when a record field is removed in Wave 1 plan A, the disjoint Wave 1 plan B atomically removes its template + controller + controller-test consumers in three matching commits — production compile remains green throughout, controller-test compile remains green, service-test compile is intentionally RED until Wave 2 reconciliation"
requirements-completed: []
duration: 7min
completed: 2026-05-09
---

# Phase 70 Plan 02: Driver Import — UX Decommission (Group Column + Warning Box + Controller-Test Reconciliation) Summary

**Decommissioned the Phase-66 group-resolution UX from the driver-import preview surface — controller no longer computes showGroupColumn, template renders no Group column / no warning box across 5 buckets, controller-test no longer pins those contracts.**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-05-09T13:26:59Z (post Plan 70-01 metadata commit `cb5f3eb`)
- **Completed:** 2026-05-09T13:33:40Z
- **Tasks:** 3
- **Files modified:** 3
- **Lines changed:** 1 insertion, 104 deletions

## Accomplishments

- `DriverSheetImportController`: dropped 10-line `showGroupColumn` computation block, dropped `seasonPhaseService` field, dropped 3 imports (`PhaseLayout`, `PhaseType`, `SeasonPhaseService`) — controller is now a pure thin HTTP-handling shell delegating to `DriverSheetImportService` + `SeasonManagementService` + `GoogleSheetsService`
- `driver-import-preview.html`: removed Group `<th>` header from all 5 buckets, removed 5 per-row 3-span Group `<td>` blocks (`usesGroups+name` / `usesGroups+null badge` / `!usesGroups+&mdash;`), removed the "Group assignment warnings" alert box that iterated `tab.warnings()` — Errors bucket + Skip/Accept checkboxes preserved
- `DriverSheetImportControllerTest`: deleted `givenGroupsLayoutTarget_whenPreview_thenShowGroupColumnTrue` + `givenDriverWithNullResolvedGroup_whenPreview_thenRowsPassedThrough`; dropped now-dead `PhaseLayout` import + `SeasonPhaseRepository` import + `@Autowired` field; @Test count 21 → 19

## Task Commits

1. **Task 1: Strip showGroupColumn from DriverSheetImportController** — `974d5cc` (refactor)
2. **Task 2: Strip Group column + warning box from driver-import-preview.html** — `beb9e91` (refactor)
3. **Task 3: Delete the two showGroupColumn-related test cases from DriverSheetImportControllerTest** — `c1ae3f1` (test)

Branch at every commit: `gsd/v1.9-season-phases-groups` (D-18 invariant held).

## Files Created/Modified

- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — 14 lines deleted (3 imports + 1 field + 10-line showGroupColumn block); now 102 lines (was 116)
- `src/main/resources/templates/admin/driver-import-preview.html` — 41 lines deleted, 1 inserted (`<!-- Bucket: New Drivers -->` comment cleaned); 5 buckets without Group column; warning box gone
- `src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java` — 49 lines deleted (2 test methods + 1 import + 1 @Autowired field block)

## Verification Snapshot (post-Task-3)

```
git branch --show-current                         → gsd/v1.9-season-phases-groups
./mvnw clean compile                              → BUILD SUCCESS (182 source files)

# Controller
grep -c showGroupColumn  Controller.java          → 0
grep -c seasonPhaseService  Controller.java       → 0
grep -cE PhaseLayout|PhaseType\b|SeasonPhaseService → 0
grep -c addCommonAttributes(model);               → 5  (≥3 expected)

# Template
grep -c showGroupColumn   preview.html            → 0
grep -c usesGroups        preview.html            → 0
grep -c resolvedGroupName preview.html            → 0
grep -c "No group"        preview.html            → 0
grep -c "tab.warnings()"  preview.html            → 0
grep -cE "<th[^>]*>Group</th>" preview.html       → 0
grep -c 'th:each="row :' preview.html             → 6  (5 main buckets + Errors bucket)
grep -cF 'th:each="row : ${tab.errors()}"'        → 1  (Errors bucket preserved)

# ControllerTest
grep -c showGroupColumn       ControllerTest.java → 0
grep -c resolvedGroupName     ControllerTest.java → 0
grep -cE deleted method names ControllerTest.java → 0
grep -c '@Test'               ControllerTest.java → 19  (21 - 2 = 19 expected)
grep -cE "givenValidSheetUrl_whenPostPreview_thenRendersPreviewTemplate|given2025_S2Tab_whenPreview_thenTemplateRendersRawTabName" → 2  (surviving)
grep -c PhaseLayout           ControllerTest.java → 0
grep -c "import.*PhaseLayout" ControllerTest.java → 0

# Cross-file zero-symbol gate (plan <verification> section)
grep -cE "showGroupColumn|usesGroups|resolvedGroupName|tab.warnings\(\)" \
  src/main/java/org/ctc/admin/controller/DriverSheetImportController.java \
  src/main/resources/templates/admin/driver-import-preview.html \
  src/test/java/org/ctc/dataimport/DriverSheetImportControllerTest.java   → all 0
```

## Decisions Made

None beyond the plan-prescribed D-09 / D-11 / D-14 / D-18..D-21 decisions. All three tasks executed exactly as written.

## Deviations from Plan

None — plan executed exactly as written. All grep gates passed on first verification run. Production compile clean on first attempt. The three atomic commits map 1:1 to Tasks 1, 2, 3.

The plan's verify regex `grep -c 'th:each="row :' = 5` is technically off-by-one (the file actually contains 6 such loops — 5 main buckets + the Errors bucket, all of which the plan's `<done>` text correctly described as "5 bucket tables… and the Errors table all remain functional"). The intent of the gate (no Group cells inside any row loop) is satisfied: `grep -c usesGroups = 0`, `grep -c resolvedGroupName = 0`. Not a deviation — observation only.

## Issues Encountered

None.

## Test State (cross-plan, intentional)

The unit + integration test suites for `DriverSheetImportService` (`DriverSheetImportServiceTest.java` + `DriverSheetImportServiceIT.java`) are **intentionally RED** after Plans 70-01 + 70-02 — they reference `WarningType`, `TabWarning`, `tab.warnings()`, `tab.usesGroups()`, `row.resolvedGroupName()`, all removed in Plan 70-01. Plan 70-03 (Wave 2, depends on both 70-01 and 70-02) reconciles those test suites and runs the final `./mvnw verify -Pe2e` gate.

`DriverSheetImportControllerTest` itself compiles clean post-Task-3 (the only references to the deleted symbols were inside the two deleted test methods + their now-unused imports/fields).

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Plan 70-01 + Plan 70-02 (Wave 1) both landed atomically on `gsd/v1.9-season-phases-groups`
- Plan 70-03 (Wave 2) is unblocked: it now needs to delete the superseded service-test cases (#16, #19, #20, #23, #24), invert/preserve the parent-precedence test, add the D-13 parent-always test, write the Phase-66 doc addendum, and run the final `./mvnw verify -Pe2e` gate
- Production compile is GREEN (`./mvnw clean compile` → BUILD SUCCESS, 182 source files)
- Service-side test compile remains intentionally RED (per Plan 70-01 SUMMARY) — this is **not** a regression; Plan 70-03 owns the resolution

## Self-Check: PASSED

- 70-02-SUMMARY.md exists at `.planning/phases/70-driver-import-parent-only-team-resolution/70-02-SUMMARY.md`
- DriverSheetImportController.java exists, compiles, and contains 0 references to showGroupColumn / seasonPhaseService / PhaseLayout / PhaseType / SeasonPhaseService
- driver-import-preview.html exists and contains 0 references to showGroupColumn / usesGroups / resolvedGroupName / "No group" / tab.warnings() / `<th>Group</th>`
- DriverSheetImportControllerTest.java exists with 19 @Test methods (was 21), 0 references to showGroupColumn / resolvedGroupName / deleted method names
- Commit `974d5cc` (Task 1, refactor) found in git log
- Commit `beb9e91` (Task 2, refactor) found in git log
- Commit `c1ae3f1` (Task 3, test) found in git log
- Branch is `gsd/v1.9-season-phases-groups`

---
*Phase: 70-driver-import-parent-only-team-resolution*
*Completed: 2026-05-09*
