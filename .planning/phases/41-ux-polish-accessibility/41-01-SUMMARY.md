---
phase: 41-ux-polish-accessibility
plan: "01"
subsystem: sitegen
tags: [tdd, raceview, data-model, accessibility, site-generator]
dependency_graph:
  requires: []
  provides: [RaceView-winner-booleans, activeSeasonName-template-variable, tdd-red-ux01-ux04-ux06-ux07]
  affects: [SiteGeneratorService, RaceView, SiteGeneratorServiceTest]
tech_stack:
  added: []
  patterns: [TDD-RED, pre-computed-booleans-in-service]
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/sitegen/model/RaceView.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - Winner booleans (homeTeamWon/awayTeamWon) pre-computed in service per D-02 — no template logic
  - activeSeasonName uses getDisplayLabel() (full "year | #N | name" string) not getName()
metrics:
  duration_minutes: 15
  completed_date: "2026-04-16"
  tasks_completed: 2
  files_modified: 3
---

# Phase 41 Plan 01: TDD RED — Winner Booleans and Accessibility Test Contracts Summary

Added winner boolean fields to RaceView record and activeSeasonName context variable to writeTemplate, then wrote four TDD RED integration tests for UX-01 (skip-link), UX-04 (winner highlight), UX-06 (footer links), and UX-07 (nav aria-label).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add winner booleans to RaceView and activeSeasonName to writeTemplate | 417e3c8 | RaceView.java, SiteGeneratorService.java |
| 2 | Write four failing integration tests (TDD RED) | febeedc | SiteGeneratorServiceTest.java |

## What Was Built

### Task 1: Data Model Foundation

**RaceView.java** — Added two boolean fields between `hasResults` and `results`:
- `boolean homeTeamWon` — true when `hasResults && homeTotal > awayTotal`
- `boolean awayTeamWon` — true when `hasResults && awayTotal > homeTotal`
- Draw case: both false when scores are equal

**SiteGeneratorService.java** — Three changes:
1. `toRaceView()` — extracted `hasResults` to local variable, computed winner booleans before RaceView construction
2. `generate()` — added `activeSeasonName = activeSeason.getDisplayLabel()` computation alongside `activeSeasonSlug`
3. `writeTemplate()` — added `String activeSeasonName` parameter; sets `activeSeasonName` context variable
4. All 9 `generate*` method signatures updated with `String activeSeasonName` parameter
5. All 9 `writeTemplate(...)` call sites updated to pass 5 arguments

### Task 2: TDD RED Tests

Four new failing integration tests added to `SiteGeneratorServiceTest.java`:
- `givenLayout_whenGenerate_thenSkipLinkIsFirstBodyChild` — UX-01: checks `<a class="skip-link" href="#main-content">` is first body child
- `givenRaceWithResults_whenGenerate_thenMatchdayShowsWinnerHighlight` — UX-04: checks `.match-team-winner` CSS class presence
- `givenActiveSeason_whenGenerate_thenFooterContainsUsefulLinks` — UX-06: checks `.footer .footer-link` elements with Top and Archive links
- `givenLayout_whenGenerate_thenNavToggleLabelHasAriaLabel` — UX-07: checks `aria-label="Toggle navigation menu"` and `role="button"` on nav toggle label

**TDD RED state confirmed:** All 4 tests fail, 41 pre-existing tests pass (45 total, 4 failures, 0 errors).

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this plan adds data model fields and failing tests only; no rendering stubs introduced.

## Threat Flags

None — winner booleans derived from existing score totals (no new input vector); activeSeasonName is public display data.

## Self-Check: PASSED

- [x] `src/main/java/org/ctc/sitegen/model/RaceView.java` — FOUND, contains `boolean homeTeamWon, boolean awayTeamWon`
- [x] `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — FOUND, contains `boolean homeTeamWon = hasResults && homeTotal > awayTotal` and `context.setVariable("activeSeasonName"`
- [x] `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — FOUND, contains all 4 new test methods
- [x] Commit 417e3c8 — FOUND
- [x] Commit febeedc — FOUND
- [x] `./mvnw compile -q` succeeds
- [x] 4 new tests fail (RED), 41 pre-existing tests pass
