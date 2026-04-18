---
phase: 41-ux-polish-accessibility
plan: "02"
subsystem: static-site
tags: [ux, accessibility, css, templates, tdd-green]
dependency_graph:
  requires: [41-01]
  provides: [UX-01, UX-04, UX-05, UX-06, UX-07, UX-08, UX-09, QUAL-01]
  affects: [layout.html, matchday.html, index.html, driver-profile.html, style.css]
tech_stack:
  added: []
  patterns:
    - Skip-link visually hidden / shown on focus pattern
    - Thymeleaf conditional th:class for winner highlighting
    - CSS pseudo-element scroll indicator inside media query
    - CSS utility classes replacing inline styles
key_files:
  created: []
  modified:
    - src/main/resources/templates/site/layout.html
    - src/main/resources/templates/site/matchday.html
    - src/main/resources/templates/site/index.html
    - src/main/resources/templates/site/driver-profile.html
    - src/main/resources/static/site/css/style.css
decisions:
  - Skip-link positioned absolute off-screen, revealed on :focus with fixed positioning
  - Footer active season link uses same conditional pattern as nav Driver Ranking link
  - table-wrap::after scroll indicator scoped to mobile media query only
  - tr transition added as separate rule before existing tr:hover (not merged)
metrics:
  duration: "8 minutes"
  completed: "2026-04-16"
  tasks_completed: 2
  files_modified: 5
---

# Phase 41 Plan 02: UX Polish & Accessibility (TDD GREEN) Summary

**One-liner:** Implemented skip-link, winner highlight, scroll indicator, footer links, aria improvements, hover transitions, cursor:pointer, and inline style removal across four site templates and style.css.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Update all HTML templates | 9eb32b8 | layout.html, matchday.html, index.html, driver-profile.html |
| 2 | Add all CSS rules and verify tests pass | 4296a32 | style.css |

## What Was Built

### Task 1 — HTML Template Changes

**layout.html:**
- Skip-link `<a href="#main-content" class="skip-link">` inserted as first child of `<body>` (UX-01)
- `id="main-content"` added to `<main>` element (UX-01)
- `aria-label` removed from nav-toggle `<input>`, moved to `<label>` as `aria-label="Toggle navigation menu"` + `role="button"` (UX-07)
- Footer replaced with two-row layout: `.footer-links` bar (Top, Archive, active season) + existing tagline (UX-06)

**matchday.html:** `.match-teams` block updated with `th:class` conditionals — home/away team spans get `match-team-winner` class when `race.homeTeamWon`/`race.awayTeamWon` is true (UX-04)

**index.html:** Same `th:class` pattern applied to last matchday section match cards (UX-04)

**driver-profile.html:** Two inline styles replaced with CSS classes — `style="margin-bottom: 24px;"` → `class="driver-header"`, `style="margin-top: 24px;"` → `class="section section-gap"` (QUAL-01)

### Task 2 — CSS Additions (style.css)

| Rule | Location | Requirement |
|------|----------|-------------|
| `html { scroll-behavior: smooth; }` | After reset block | UX-06 prereq |
| `a, label[for], [role="button"] { cursor: pointer; }` | After html rule | UX-09 |
| `.skip-link` + `.skip-link:focus` | Before Navigation section | UX-01 |
| `tr { transition: background-color 0.2s; }` | Before existing `tr:hover` | UX-08 |
| `.match-team-winner { color: var(--accent); ... }` | After `.match-meta` block | UX-04 |
| `position: relative` added to `.table-wrap` block | In existing block | UX-05 |
| `.table-wrap::after` gradient | Inside first mobile media query | UX-05 |
| `.footer-links`, `.footer-link`, `.footer-link:hover`, `.footer-sep` | After `.footer` rule | UX-06 |
| `.driver-header`, `.section-gap` | After `.team-logo` rule | QUAL-01 |

## Test Results

- **970 tests** — 0 failures, 0 errors
- **JaCoCo coverage check** — All checks met (>=82%)
- **TDD GREEN gate** — The four failing tests from Plan 01 now pass:
  - `givenLayout_whenRendered_thenSkipLinkIsFirstBodyChild`
  - `givenMatchdayWithResults_whenRendered_thenWinnerHasHighlightClass`
  - `givenLayout_whenRendered_thenFooterContainsArchiveAndSeasonLinks`
  - `givenLayout_whenRendered_thenNavToggleLabelHasAriaLabelAndRole`

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all template context variables (`rootPath`, `activeSeasonSlug`, `activeSeasonName`) are already wired in `SiteGeneratorService` from prior phases.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. All template output uses `th:text` (auto-escaped by Thymeleaf) as specified in the threat model.

## Self-Check: PASSED

- `src/main/resources/templates/site/layout.html` — FOUND
- `src/main/resources/templates/site/matchday.html` — FOUND
- `src/main/resources/templates/site/index.html` — FOUND
- `src/main/resources/templates/site/driver-profile.html` — FOUND
- `src/main/resources/static/site/css/style.css` — FOUND
- Commit `9eb32b8` — FOUND (feat(41-02): update HTML templates)
- Commit `4296a32` — FOUND (feat(41-02): add all UX polish CSS rules)
- `./mvnw verify` — BUILD SUCCESS, 970 tests, all coverage checks met
