---
phase: 115-guest-marking-visibility
plan: 01
subsystem: ui
tags: [css, thymeleaf, fragment, guest-marker, design-token]

requires:
  - phase: 113-guest-assignment-foundation
    provides: "RaceLineup.guest flag (Source of Truth for the per-race guest marker)"
provides:
  - "Central --guest #f59e0b accent token in admin.css and site style.css :root"
  - ".guest-marker and .guest-label CSS classes (identical in both stylesheets)"
  - "Shared guestMarker(isGuest) Thymeleaf fragment (admin/fragments/guest-marker.html) for admin + site templates"
affects: [115-02, 115-03, 115-04, 115-05]

tech-stack:
  added: []
  patterns:
    - "Single-source design token (--guest) mirrored across admin + site CSS per D-02/D-12"
    - "Shared boolean-gated display fragment (th:if isGuest) reusable by both admin and site TemplateEngine paths"

key-files:
  created:
    - src/main/resources/templates/admin/fragments/guest-marker.html
  modified:
    - src/main/resources/static/admin/css/admin.css
    - src/main/resources/static/site/css/style.css

key-decisions:
  - "Glyph = U+2605 BLACK STAR via &#x2605; (D-01/D-03 icon-primary; consistent with existing badge entities)"
  - "Accent color #f59e0b held as central CSS variable, value identical in both stylesheets (D-02/D-12)"
  - "Both stylesheets already had --text-dim — no token substitution required for .guest-label"

patterns-established:
  - "Append-only edits to shared CSS files (no rewrite) — admin.css 2114->2130, style.css 1089->1105"
  - "guest-marker fragment lives under admin/fragments/ and is classpath-resolvable from site templates too (RESEARCH #10)"

requirements-completed: [MARK-04, MARK-05, MARK-06]

duration: 8min
completed: 2026-06-01
---

# Phase 115 Plan 01: Shared Visual Foundation Summary

**Established the single-source guest marker treatment — one amber accent token, two marker CSS classes, and a reusable star-glyph Thymeleaf fragment — that every Wave-2 surface will reference for consistent guest marking.**

## Performance

- **Duration:** ~8 min
- **Tasks:** 3 completed
- **Files modified:** 3 (1 created, 2 appended)

## Accomplishments

- **Task 1:** Appended `--guest: #f59e0b` to the `admin.css` `:root` block and `.guest-marker` (color `var(--guest)`) + `.guest-label` (color `var(--text-dim)`) rule blocks at end of file. Append-only: 2114 → 2130 lines.
- **Task 2:** Mirrored the identical token and both classes into site `style.css`. Values match admin.css exactly (D-02). `--text-dim` already existed in the site `:root` — no substitution needed. Append-only: 1089 → 1105 lines.
- **Task 3:** Created `src/main/resources/templates/admin/fragments/guest-marker.html` with `th:fragment="guestMarker(isGuest)"` wrapping a `span.guest-marker` gated by `th:if="${isGuest}"`, `aria-label="Guest driver"`, rendering `&#x2605;`. Mirrors the `match-card.html` document scaffold.

## Verification

- `./mvnw clean test-compile` succeeded; `target/classes/templates/admin/fragments/guest-marker.html` confirmed copied; `target/test-classes` present.
- grep counts on both stylesheets returned the appended token + classes; both files grew (append-only, no section loss).
- Visual confirmation of glyph/color is deferred to Plan 115-06 (playwright-cli visual approval gate, SC-1).

## Self-Check: PASSED

No deviations. Presentation-only scaffold; no Java, no schema, no migration. All three artifacts in place for Wave 2.
