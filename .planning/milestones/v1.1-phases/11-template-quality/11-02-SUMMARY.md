---
phase: 11-template-quality
plan: "02"
subsystem: templates
tags: [css, inline-styles, migration, p2-templates]
dependency_graph:
  requires: ["11-01"]
  provides: ["css-classes-p2", "clean-templates"]
  affects: ["admin.css", "34 P2 templates", "matchday-detail.html"]
tech_stack:
  added: []
  patterns: ["CSS utility classes", "D-04 inline exceptions"]
key_files:
  created: []
  modified:
    - src/main/resources/static/admin/css/admin.css
    - src/main/resources/templates/admin/matchday-detail.html
    - src/main/resources/templates/admin/gt7-sync-preview.html
    - src/main/resources/templates/admin/team-form.html
    - src/main/resources/templates/admin/race-results.html
    - src/main/resources/templates/admin/team-cards.html
    - src/main/resources/templates/admin/import.html
    - src/main/resources/templates/admin/season-form.html
    - src/main/resources/templates/admin/race-scoring-form.html
    - src/main/resources/templates/admin/races.html
    - src/main/resources/templates/admin/standings.html
    - src/main/resources/templates/admin/playoff-bracket.html
    - src/main/resources/templates/admin/race-form.html
    - src/main/resources/templates/admin/power-rankings.html
    - src/main/resources/templates/admin/car-form.html
    - src/main/resources/templates/admin/track-form.html
    - src/main/resources/templates/admin/import-preview.html
    - src/main/resources/templates/admin/match-scoring-form.html
    - src/main/resources/templates/admin/team-detail.html
    - src/main/resources/templates/admin/cars.html
    - src/main/resources/templates/admin/gt7-sync.html
    - src/main/resources/templates/admin/playoff-matchup.html
    - src/main/resources/templates/admin/tracks.html
    - src/main/resources/templates/admin/match-scoring-list.html
    - src/main/resources/templates/admin/race-lineup.html
    - src/main/resources/templates/admin/driver-form.html
    - src/main/resources/templates/admin/matchday-generator.html
    - src/main/resources/templates/admin/drivers.html
    - src/main/resources/templates/admin/swiss-rounds.html
    - src/main/resources/templates/admin/playoff-form.html
    - src/main/resources/templates/admin/teams.html
    - src/main/resources/templates/admin/matchday-form.html
    - src/main/resources/templates/admin/matchdays.html
    - src/main/resources/templates/admin/match-form.html
    - src/main/resources/templates/admin/generate.html
    - src/main/resources/templates/admin/driver-detail.html
decisions:
  - "D-04 applied: 77 inline styles with unique patterns (<3 occurrences) left as-is"
  - "display:none styles (12 total) retained for JavaScript toggle functionality"
  - "25+ new CSS utility classes added to admin.css for reusable patterns"
metrics:
  duration: ~15min
  completed: "2026-04-06"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 11 Plan 02: P2 Template Inline Style Migration Summary

Migrate all 35 P2 admin templates from inline styles to CSS classes, adding 25+ new utility classes to admin.css.

## Task 1: matchday-detail.html Migration (701c31c)

Migrated matchday-detail.html (the most complex P2 template) from 42 inline styles to zero.

- Added matchday-specific CSS classes: `.match-row`, `.match-header`, `.match-team-name`, `.match-score-area`, `.match-score-value`, `.match-bye`, `.leg-row`, `.leg-label`, `.match-list`, `.flex-spacer`, `.match-score-separator`, etc.
- All 42 inline style attributes removed and replaced with semantic CSS classes
- **Commit:** 701c31c

## Task 2: 34 Remaining P2 Templates Migration (4a76590)

Migrated all 34 remaining P2 templates, removing 205 inline style attributes.

### New CSS Classes Added

| Class | Purpose | Uses |
|-------|---------|------|
| `.search-input` | Search text input field styling | 5 |
| `.pagination-bar` | Pagination flex container | 5 |
| `.thumb-sm` | Small thumbnail image (50px) | 3 |
| `.col-thumb` | 60px thumbnail column width | 6 |
| `.overlay` | Fullscreen overlay base | 3 |
| `.overlay-panel` | Overlay inner content panel | 3 |
| `.overlay-title` | Overlay title text | 3 |
| `.empty-state--md` | Empty state with 24px padding | 3 |
| `.form-group--max-200` | Form group with max-width 200px | 4 |
| `.color-picker-input--lg` | Large color picker input | 3 |
| `.color-text-input--sm` | Color text input (100px) | 3 |
| `.sub-team-row` | Sub-team row opacity | 1 |
| `.td-indent` | Indented table cell | 1 |
| `.radio-label` | Inline radio/checkbox label | 2 |
| `.filter-bar` | Card with inline filter controls | 1 |
| `.info-hint` | Info callout with accent border | 2 |
| `.info-hint__label` | Info hint label text | 2 |
| `.info-hint__content` | Info hint content text | 2 |
| `.scoring-card` | Scoring section with accent border | 1 |
| `.card-grid` | Auto-fill card grid layout | 1 |
| `.card--compact` | Card with compact padding + centered | 1 |
| `.img-placeholder` | Image placeholder (aspect-ratio box) | 1 |
| `.card-img` | Full-width rounded card image | 1 |
| `.logo-preview` | Logo preview image | 1 |
| `.preview-img` | Car/track preview image | 2 |
| `.mt-xs` | Margin-top 8px | multiple |
| `.mt-xl` | Margin-top 24px | 4 |
| `.actions--tight` | Actions with 4px gap | 2 |
| `.tab-nav` | Tab navigation (no-gap flex) | 1 |
| `.card--tab-content` | Card without top-left radius | 1 |
| `.text-sm` | Font-size 13px | 8 |
| `.text-nowrap` | White-space nowrap | 3 |
| `.scoring-input` | Large centered scoring input | 3 |
| `.form-hint--sm` | Small form hint text | 2 |
| `.section-header` | Flex header with space-between | 2 |

### Migration Statistics

| Metric | Count |
|--------|-------|
| Total inline styles removed (Task 1 + Task 2) | 247 |
| Task 1 (matchday-detail.html) | 42 |
| Task 2 (34 templates) | 205 |
| New CSS classes added | 35+ |
| D-04 exceptions (inline styles kept) | 77 |
| display:none styles retained (JS) | 12 |
| Templates at zero inline styles | 17 of 35 |

### Templates Reaching Zero Inline Styles

car-form, track-form, team-detail, driver-detail, driver-form, matchday-form, matchday-detail,
matchdays, match-form, generate, playoff-form, race-lineup, matchday-generator, drivers,
cars, gt7-sync, tracks

### D-04 Inline Style Exceptions (77 total)

Per decision D-04, single-property styles appearing fewer than 3 times across the codebase are left as inline styles. Examples:

- `margin-bottom:24px` on cards (2 uses, only gt7-sync-preview)
- `font-size:18px` on h2 elements (2 uses)
- `width:40px` / `width:80px` on table headers (2 uses each)
- Badge color overrides (`background:var(--bg-hover)`) (2 uses)
- race-scoring-form unique grid layout styles (14 instances, all unique to one template)
- Various unique 2-property combinations appearing once each

### Verification

- All 773 tests pass (`./mvnw verify` - BUILD SUCCESS)
- No `*-render.html` or `template-editors.html` files modified
- No `th:style` or `th:styleappend` attributes removed
- JavaScript `element.style.*` references in `<script>` blocks left untouched

**Commit:** 4a76590

## Deviations from Plan

None - plan executed as written. D-04 exceptions are by design.

## Self-Check: PASSED

- Commits 701c31c and 4a76590 verified in git log
- admin.css and SUMMARY.md exist
- All 12 key CSS classes verified present in admin.css
