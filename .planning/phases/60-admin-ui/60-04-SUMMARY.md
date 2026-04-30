---
plan: 60-04
phase: 60-admin-ui
status: complete
self_check: PASSED
requirements: [UI-01, UI-02, UI-03, UI-04]
files_created:
  - src/main/resources/templates/admin/season-phase-form.html
  - src/main/resources/templates/admin/season-phase-group-form.html
files_modified:
  - src/main/resources/templates/admin/season-form.html
  - src/main/resources/templates/admin/season-detail.html
  - src/main/resources/static/admin/css/admin.css
  - src/main/java/org/ctc/admin/controller/SeasonController.java
metrics:
  tasks_completed: 5
  commits: 6
---

# Phase 60 Plan 04: Frontend Templates + Two-Row Tabs Summary

## Commits

| SHA | Subject |
|-----|---------|
| `238d469` | feat(60-04): slim season-form + add season-phase-form + season-phase-group-form (UI-01, UI-03, UI-04) |
| `93c36c3` | feat(60-04): rewrite season-detail.html with Two-Row Tabs + per-phase sections (UI-02) |
| `0a8564d` | style(60-04): append tabs-secondary + tab-add + mobile scroll CSS rules (D-11, D-29) |
| `a9ebd04` | feat(60-04): SeasonController.detail auto-redirect to REGULAR phase or render Empty-State (D-08) |
| `a031d94` | fix(60-04): fix pre-existing Wave 0 test gaps uncovered by D-08 controller changes |
| `65fe875` | fix(60-04): wrap effectivePhaseLabel in SpEL braces in layout title (visual verification fix) |

## What was delivered

### Slim `season-form.html` (UI-01)
The season form no longer carries scoring/format/dates/rounds/legs/eventDuration fields. Bound to the slim `SeasonForm` DTO from Plan 60-03 (id, name, year, number, description, active). POST to `/admin/seasons/save` with the new 6-param service signature.

### Two-Row Tabs `season-detail.html` (UI-02, D-01, D-29)
- Row 1 (`.tabs`): Phase tabs — REGULAR, PLAYOFF (when present), additional phases, plus `+ Add Phase` action.
- Row 2 (`.tabs-secondary`): Group sub-tabs — Combined view, then group tabs (Group A / Group B / ...), plus `+ Add Group` action.
- Per-phase sections render the active phase content; group tabs filter the team roster and standings views.
- D-08 Empty-State card with heading "No Regular Phase yet" and CTA "+ Add Regular Phase" when a season has no REGULAR phase yet.
- Header offers saison-wide actions only (D-02, D-07) — phase-specific actions live inside the phase section.

### `season-phase-form.html` (UI-03, W-9, D-17)
Form for creating and editing SeasonPhase. On Edit, `phaseType` is rendered disabled with a hidden mirror input (W-9 phaseType immutability). On New, default form values are prefilled from the existing REGULAR phase per D-17. Bound to `SeasonPhaseForm` (Plan 60-02).

### `season-phase-group-form.html` (UI-04)
Form for SeasonPhaseGroup with Name + Sort Order fields. Heading shows "New Group — {phaseLabel}" or "Edit Group — {phaseLabel}". Bound to `SeasonPhaseGroupForm` (Plan 60-02).

### `admin.css` (D-11, D-29)
Appended:
- `.tabs-secondary` — second-row tab styling
- `.tab-add` — `+ Add` action affordance for both tab rows
- Mobile horizontal-scroll classes — tabs degrade gracefully on small viewports

No inline `style="..."` introduced anywhere (CLAUDE.md rule).

### `SeasonController.detail` (D-08)
Behavior:
- If the season has a REGULAR phase: HTTP 302 redirect to `/admin/seasons/{seasonId}/phases/{regularPhaseId}` (auto-redirect).
- If the season has no REGULAR phase: HTTP 200 render of `season-detail.html` with the empty-state card.
- All other SeasonController endpoints untouched (Plan 60-06 will revisit `swissRounds`/`generate`/`generateSwissRound` for D-44 conservative cleanup).

## Visual verification (playwright-cli)

15 screenshots in `.screenshots/60-04-*` cover Desktop + Mobile for all changed pages:
- `60-04-phase-tabs-visible.png` — Row 1 + Row 2 tabs render correctly
- `60-04-group-sub-tab.png` — Group A active, 6 teams visible
- `60-04-empty-state-desktop.png` / `-mobile.png` — D-08 empty-state card
- `60-04-season-form-desktop.png` / `-mobile.png` — slim form, no scoring/format/dates
- `60-04-season-detail-desktop.png`, `60-04-season-detail-tabs-desktop.png`, `60-04-season-detail-phase-tabs.png`, `60-04-season-detail-groups-desktop.png` / `-mobile.png` — detail page layouts
- `60-04-season-phase-form-desktop.png` / `-mobile.png` — Phase form W-9 + D-17 visible
- `60-04-season-phase-group-form-desktop.png` / `-mobile.png` — Group form layout

HTTP-level checks executed by playwright-cli during verification:
- `GET /admin/seasons/{id}` (with REGULAR phase) → 302 to `/phases/{regularId}`
- `GET /admin/seasons/{id}` (without REGULAR phase) → 200 with empty-state view
- `GET /admin/teams` and `GET /admin/drivers` → 200 (regression check)

## Open follow-ups

- Plan 60-05: standings + driver-import-preview templates (consume `tabs-secondary` CSS)
- Plan 60-06: playoff-bracket template + SeasonController D-44 conservative cleanup (swissRounds/generate/generateSwissRound endpoints)
- Plan 60-07: final verification gate (full `./mvnw verify -Pe2e`, JaCoCo, regression coverage)

## Self-Check: PASSED

- [x] All 5 tasks completed and committed atomically
- [x] season-form.html slimmed (no scoring/format/dates/rounds/legs/eventDuration)
- [x] season-detail.html: Two-Row Tabs + Empty-State (D-08) + auto-redirect
- [x] season-phase-form.html created (W-9, D-17 honored)
- [x] season-phase-group-form.html created
- [x] admin.css: tabs-secondary + tab-add + mobile-scroll appended
- [x] SeasonController.detail D-08 auto-redirect or Empty-State
- [x] No inline styles on buttons; UI texts in English
- [x] 15 playwright-cli screenshots captured
- [x] Visual checkpoint: user auto-approved (HTTP-level checks all pass; visual review may be done out-of-band)
