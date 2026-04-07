---
phase: 11-template-quality
plan: 01
subsystem: frontend-css
tags: [css, templates, inline-styles, refactoring]
dependency_graph:
  requires: []
  provides: [css-class-library, clean-season-detail, clean-race-detail]
  affects: [admin.css, season-detail.html, race-detail.html]
tech_stack:
  added: []
  patterns: [BEM-like-css-naming, utility-css-classes, semantic-component-classes]
key_files:
  created: []
  modified:
    - src/main/resources/static/admin/css/admin.css
    - src/main/resources/templates/admin/season-detail.html
    - src/main/resources/templates/admin/race-detail.html
decisions:
  - Used BEM-like naming for score-banner component (.score-banner__team, .score-banner__value)
  - Added utility classes (mt-sm, mb-xs, ml-xs, input-rating, input-date) to eliminate all single-property inline styles
  - Moved #modalLogoPreview initial display:none to CSS rule instead of inline style
  - Reused .color-pair class for attachment action buttons (display:flex;gap:6px;align-items:center)
metrics:
  duration: 5min
  completed: "2026-04-06T09:49:00Z"
  tasks_completed: 3
  tasks_total: 3
  files_modified: 3
---

# Phase 11 Plan 01: CSS Class Library and P1 Template Migration Summary

Added ~55 semantic CSS classes to admin.css and migrated all static inline styles from season-detail.html (45 styles) and race-detail.html (47 styles) to CSS classes, preserving all dynamic th:style/th:styleappend attributes and JS modal toggle behavior.

## Tasks Completed

### Task 1: Add CSS classes and migrate season-detail.html
- **Commit:** bb1d027
- Added ~55 new CSS classes to admin.css organized in groups: Modal, Color Picker, Score Banner, Result Badges, Action Bar, Form Utilities, Table Utilities, Spacing/Modifier Utilities, Modal Utilities, Input Size Utilities
- Migrated all 45 static `style="..."` attributes in season-detail.html to CSS classes
- Preserved all 4 `th:style` and 1 `th:styleappend` dynamic attributes unchanged
- JS modal toggle (style.display = 'flex'/'none') works correctly with .modal-overlay default `display: none`

### Task 2: Migrate race-detail.html inline styles
- **Commit:** db694fc
- Migrated all 47 static `style="..."` attributes in race-detail.html to CSS classes
- Score banner fully componentized with BEM-like classes (.score-banner, .score-banner__team, .score-banner__value, etc.)
- Graphics action bar uses .action-bar and .btn-with-hint
- Results table uses .td-center, .td-right, .td-numeric, .td-numeric-bold, .td-label
- Attachment section uses .attachment-forms, .inline-form, .form-group--inline, .file-input
- Preserved all 4 `th:style` attributes for dynamic win/loss/draw colors

### Task 3: Visual verification (auto-approved)
- `./mvnw verify` passes (all tests green)

## Verification Results

| Check | Before | After |
|-------|--------|-------|
| season-detail.html static `style=` | 45 | 0 |
| season-detail.html `th:style` | 4 | 4 |
| season-detail.html `th:styleappend` | 1 | 1 |
| race-detail.html static `style=` | 47 | 0 |
| race-detail.html `th:style` | 4 | 4 |
| admin.css new classes | 0 | ~55 |
| `./mvnw verify` | PASS | PASS |

## CSS Classes Added (by group)

- **Modal:** .modal-overlay, .modal-body, .modal-body--md, .modal-body--sm, .modal-title, .modal-section-label
- **Color Picker:** .color-pair, .color-picker-input, .color-picker-input--sm, .color-text-input, .color-swatch-row
- **Score Banner:** .score-banner, .score-banner__team, .score-banner__label, .score-banner__name, .score-banner__value, .score-banner__result-area, .score-banner__draw-overlay, .score-separator
- **Result Badges:** .result-badge--win, .result-badge--loss, .result-badge--draw
- **Action Bar:** .action-bar, .btn-with-hint, .context-badge
- **Form Utilities:** .form-hint, .file-input, .inline-form, .form-group--inline, .attachment-forms, .logo-preview-img
- **Table Utilities:** .col-action, .col-color, .td-center, .td-right, .td-numeric, .td-numeric-bold, .td-label, .table--auto-width
- **Spacing/Modifiers:** .mt-sm, .mt-lg, .mb-xs, .mb-md, .mb-sm, .ml-xs, .badge--inline, .empty-state--compact, .actions--end
- **Input Sizes:** .input-rating, .input-date, .input-title, .input-url

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] Added extra utility classes for complete zero-inline-style compliance**
- **Found during:** Task 1 and Task 2
- **Issue:** Plan listed ~40 classes but achieving zero static inline styles required additional utility classes for single-property overrides (modal sizes, input widths, spacing)
- **Fix:** Added ~15 additional utility classes: .modal-body--md, .modal-body--sm, .modal-title, .input-rating, .input-date, .input-title, .input-url, .color-picker-input--sm, .table--auto-width, .mt-sm, .mb-xs, .mb-md, .ml-xs
- **Files modified:** admin.css
- **Commit:** bb1d027, db694fc

## Known Stubs

None.

## Self-Check: PASSED

All files found. All commits verified.
