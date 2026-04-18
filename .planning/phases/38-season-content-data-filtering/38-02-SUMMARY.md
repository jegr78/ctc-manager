---
phase: 38-season-content-data-filtering
plan: "02"
subsystem: sitegen
tags: [tdd-green, sitegen, templates, css, data-filtering]
dependency_graph:
  requires: ["38-01"]
  provides: ["CONT-01", "CONT-06", "CONT-07"]
  affects: ["SiteGeneratorService", "site-templates", "site-css"]
tech_stack:
  added: []
  patterns: ["productionSeasons stream filter", "th:if guard on match-meta", "season-meta CSS class"]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/main/resources/templates/site/standings.html
    - src/main/resources/templates/site/matchday.html
    - src/main/resources/templates/site/driver-ranking.html
    - src/main/resources/templates/site/index.html
    - src/main/resources/templates/site/archive.html
    - src/main/resources/static/site/css/style.css
decisions:
  - "Use stream filter on allSeasons (not a repository query) for test season exclusion — keeps single DB call, filter is cheap in-memory"
  - "season.year and season.number accessed directly via entity getters in Thymeleaf (OSIV active) — no extra template variables needed"
  - "th:if guard uses Thymeleaf 'or' operator (not '||') for null-safe evaluation on match-meta div"
metrics:
  duration: "~6 minutes"
  completed: "2026-04-16T09:05:44Z"
  tasks_completed: 3
  files_modified: 7
---

# Phase 38 Plan 02: Season Content & Data Filtering — TDD GREEN Summary

TDD GREEN phase implementing CONT-01 (season year/number display), CONT-06 (test season filter), and CONT-07 (empty match-meta guard) across SiteGeneratorService and 5 site templates.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | CONT-06: productionSeasons filter in generate() | a323ea4 | SiteGeneratorService.java |
| 2 | CONT-01 + CONT-07: templates + CSS | 003168a | 5 templates + style.css |
| 3 | Full suite verification | 003168a | (same commit as Task 2) |

## What Was Built

### CONT-06: Test Season Filtering

In `SiteGeneratorService.generate()`, after `seasonRepository.findAll()`, a stream filter creates `productionSeasons` excluding any season whose name contains "Test". The per-season page loop and `generateArchive()` now receive `productionSeasons`. The `generateIndex()` call retains `allSeasons` (index logic is not affected).

### CONT-01: Season Metadata Display

Added a `<p class="season-meta">` subtitle beneath each page section title:
- **standings.html**: `2025 | Season #3` format after "Team Standings — {name}"
- **matchday.html**: same format after matchday label heading
- **driver-ranking.html**: same format after "Driver Ranking — {name}"
- **index.html** hero label: enriched from `Season {name}` to `Season {name} — {year}`
- **archive.html** season column: name + separate `<p class="season-meta">` with `{year} | #{number}`

### CONT-07: Empty Match-Meta Guard

Added `th:if="${race.track != null or race.car != null}"` to the `.match-meta` div in both `matchday.html` and `index.html`. When both track and car are null, the div is absent from the DOM entirely. The inner separator span guard was already correct and left unchanged.

### style.css

Added `.season-meta` class immediately after `.section-title` block:
```css
.season-meta {
    font-size: 12px;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-top: 4px;
}
```

## Test Results

- **SiteGeneratorServiceTest**: 26/26 passing (0 failures)
- **Full suite**: 951 tests, 0 failures, BUILD SUCCESS
- **JaCoCo**: coverage >= 82% confirmed (BUILD SUCCESS)

## TDD Gate Compliance

- RED gate: `test(sitegen): add failing tests for CONT-01, CONT-06, CONT-07` — commit `0c2f405` (Plan 38-01)
- GREEN gate: `feat(sitegen): add season metadata, filter test seasons, hide empty match-meta` — commit `003168a`
- REFACTOR gate: not needed

## Deviations from Plan

None — plan executed exactly as written.

The test count was 26 (not 24 as predicted) because the test class already had 18 passing tests before the RED phase, not 16. All tests pass regardless.

## Known Stubs

None — all season.year, season.number, and name values flow from real JPA entity fields. No hardcoded placeholders.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The `productionSeasons` filter at T-38-04 (Information Disclosure) is fully implemented: test seasons produce no pages and are absent from the archive.

## Self-Check: PASSED

- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — modified, committed in a323ea4
- `src/main/resources/templates/site/standings.html` — modified, committed in 003168a
- `src/main/resources/templates/site/matchday.html` — modified, committed in 003168a
- `src/main/resources/templates/site/driver-ranking.html` — modified, committed in 003168a
- `src/main/resources/templates/site/index.html` — modified, committed in 003168a
- `src/main/resources/templates/site/archive.html` — modified, committed in 003168a
- `src/main/resources/static/site/css/style.css` — modified, committed in 003168a
- Commits a323ea4 and 003168a both present in git log
- 951 tests pass, BUILD SUCCESS
