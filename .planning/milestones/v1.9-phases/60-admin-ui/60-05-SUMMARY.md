---
plan: 60-05
phase: 60-admin-ui
status: complete
self_check: PASSED
requirements: [UI-05, UI-06]
requirements-completed: [UI-05, UI-06]
files_modified:
  - src/main/java/org/ctc/admin/controller/StandingsController.java
  - src/main/resources/templates/admin/standings.html
  - src/main/java/org/ctc/admin/controller/DriverSheetImportController.java
  - src/main/resources/templates/admin/driver-import-preview.html
  - src/main/resources/static/admin/css/admin.css
metrics:
  tasks_completed: 4
  commits: 3
  tests_green: 30
---

# Phase 60 Plan 05: Standings + Driver Import Preview Summary

## Commits

| SHA | Subject |
|-----|---------|
| `a891c81` | feat(60-05): refactor StandingsController to phase-canonical params with legacy bridge (UI-05) |
| `d7da63c` | feat(60-05): rewrite standings.html with Two-Row Tabs + conditional columns (UI-05) |
| `d556287` | feat(60-05): driver-import-preview shows raw tab name + conditional Group column (UI-06) |

## What was delivered

### StandingsController (UI-05)
Slim resolution priority: explicit `?phase={phaseId}&group={groupId}` (canonical) → legacy `?seasonId={uuid}` (D-12 bridge auto-resolving to REGULAR phase) → active season fallback. Pitfall 4 honored — `findByType` (Optional) used in legacy bridge so missing REGULAR doesn't 404.

Server flags exposed (D-32 / D-33 / D-40):
- `combinedView` — true when GROUPS layout and no group is selected
- `showGroupColumn` — true in combined view
- `showBuchholz` — true when SWISS format AND a group is selected
- `phase`, `allPhases`, `groups`, `selectedGroupId`, `hasRegularPhase` — model attributes for the Two-Row tabs.

Behavior: when no season can be resolved, the controller does NOT emit `selectedSeason` / `standings` / `driverRanking` model attributes (tests assert `attributeDoesNotExist`).

### standings.html (UI-05)
Replaces dual league/swiss tables with one server-flag-driven table:
- Phase tabs (Row 1) + Group sub-tabs (Row 2 with Combined option), markup mirrors `season-detail.html` for visual consistency.
- Conditional `Group` column (showGroupColumn) and `Buchholz` column (showBuchholz).
- D-08 empty-state when season selected but no REGULAR phase yet, with "+ Add Regular Phase" CTA.
- D-36 empty-state when phase exists but has no race results yet.
- All inline styles removed in favor of `.td-right`, `.td-numeric-bold`, and the new `.hidden` utility classes.

### DriverSheetImportController + driver-import-preview.html (UI-06)
Single boolean per preview (B-4): `showGroupColumn` is true if ANY resolved target season has a REGULAR phase with GROUPS layout. Tabs without a resolved season contribute nothing to the decision.

Template updates:
- D-37 / Pitfall 10: H2 heading uses `tab.tabName()` (e.g. `2025_S2`) — not the year. Form input names also use `tabName` for stable per-tab identification.
- D-38: ambiguous-tab plain banner replaces the small caption — clear `alert-warning` block with rename hint to `{year}_S{N}`.
- D-40: conditional Group column added to newDrivers, newAssignments, conflicts, fuzzySuggestions, unchanged buckets.
- D-39: inline `⚠ No group` badge when `row.resolvedGroupName()` is null.
- TabWarning banner preserved (Phase 59 D-08).

### admin.css additions
- `.badge-warning` — matches alert-warning palette (#3b2e0e bg / #ffb74d fg) for the inline No-group badge
- `.hidden` — replaces `style="display:none"` in standings.html JS pagination

## Test results

`./mvnw test -Dtest='StandingsControllerTest,DriverSheetImportControllerTest'` — 30/30 GREEN
- StandingsControllerTest: 9/9 (slim form, legacy bridge, combined view, group selection, alltime, no-active-season state, swiss season, Buchholz on group selection)
- DriverSheetImportControllerTest: 21/21 (raw tabName, GROUPS-layout target → showGroupColumn=true, null resolvedGroup passthrough, all pre-existing exception/preview tests preserved)

## Visual verification (playwright-cli)

Screenshots in `.screenshots/60-05-*`:
- `60-05-standings-combined-desktop.png` — Combined view, Group column visible
- `60-05-standings-group-a-desktop.png` — Group A active, Group column hidden, 6 Group-A teams + Driver Ranking
- `60-05-standings-mobile.png` — 375px viewport, tabs scroll horizontally
- `60-05-driver-import-form-desktop.png` — Initial import form (URL + Preview button)

Driver-Import preview rendering with real Sheet data was not screenshotted (requires Google service account credentials); the controller logic is verified by 21 controller tests including the GROUPS-layout-target → showGroupColumn=true assertion.

## Open follow-ups

- Plan 60-06: playoff-bracket template + SeasonController D-44 conservative cleanup (swissRounds/generate/generateSwissRound endpoints).
- Plan 60-07: final verification gate (full `./mvnw verify -Pe2e`, JaCoCo, regression coverage).

## Self-Check: PASSED

- [x] StandingsController + DriverSheetImportController follow server-flag conditional-rendering pattern
- [x] Templates consume `.tabs-secondary` / `.badge-warning` / `.hidden` utility classes — no custom styling
- [x] Wave 0 RED tests for both controllers turn GREEN (30/30)
- [x] No inline styles, all UI text English
- [x] No modifications outside scope (4 production files + admin.css additive utilities)
- [x] Visual checkpoint approved by user (2026-04-30)
- [x] SUMMARY.md committed
