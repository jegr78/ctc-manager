---
phase: 115-guest-marking-visibility
plan: 02
subsystem: ui
tags: [graphics, thymeleaf, playwright-render, racelineup, guest-marker, jacoco-excluded]

requires:
  - phase: 115-01
    provides: "guest accent #f59e0b convention (graphics inline their own .guest-marker, no fragment/var)"
  - phase: 113-guest-assignment-foundation
    provides: "RaceLineup.guest — Source of Truth resolved per row"
provides:
  - "Per-row isGuest flag on the three driver-name graphics (Scorecard, Provisional Scores, Lineup)"
  - "Inline amber star marker rendered in results-render / provisional-scores-render / lineup-render"
affects: [115-06]

tech-stack:
  added: []
  patterns:
    - "Graphic data services resolve guest flag via RaceLineupRepository.findByRaceIdAndDriverId(...).map(RaceLineup::isGuest).orElse(false)"
    - "Graphic render templates inline the .guest-marker rule (#f59e0b hardcoded) — NO admin.css var, NO th:replace fragment (string-template-mode safe)"

key-files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/service/ResultsGraphicService.java
    - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
    - src/main/java/org/ctc/admin/service/LineupGraphicService.java
    - src/main/resources/templates/admin/results-render.html
    - src/main/resources/templates/admin/provisional-scores-render.html
    - src/main/resources/templates/admin/lineup-render.html
    - src/test/java/org/ctc/admin/service/ResultsGraphicServiceTest.java
    - src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java
    - src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java

key-decisions:
  - "ResultsGraphicService + ProvisionalScoresGraphicService gained RaceLineupRepository constructor injection (LineupGraphicService already had it)"
  - "DriverResultRow / DriverPairing carry homeIsGuest+awayIsGuest; ProvisionalRow carries isGuest (all trailing fields)"
  - "buildPairings now retains List<RaceLineup> (dropped .map(RaceLineup::getDriver)) so the guest flag survives to the row"
  - "Home column: star before name; away column (lineup, right-aligned): star after name"

patterns-established:
  - "Inline span gated by th:if on the row guest flag — never SpEL flag computation in the template"

requirements-completed: [MARK-01, MARK-02, MARK-03]

duration: 25min
completed: 2026-06-01
---

# Phase 115 Plan 02: Graphics Guest Markers Summary

**The three graphics that render individual driver names (Scorecard, Provisional Scores, Lineup) now surface a per-row guest flag resolved from RaceLineup.guest and render an inline amber star before/after each guest driver name.**

## Performance

- **Tasks:** 3 completed (TDD Red→Green each)
- **Files modified:** 9 (3 services, 3 templates, 3 test classes)

## Accomplishments

- **Task 1 (MARK-01, Scorecard):** Injected `RaceLineupRepository` into `ResultsGraphicService`; added `homeIsGuest`/`awayIsGuest` to `DriverResultRow`; `buildResultRows` resolves each side via a `resolveGuest(raceId, result)` helper. `results-render.html` gets the inline `.guest-marker` rule + star spans before home and away names. Test class: 11→12 green.
- **Task 2 (MARK-02, Provisional Scores):** Injected `RaceLineupRepository`; added trailing `isGuest` to `ProvisionalRow`; `buildContext` resolves per result and threads through `toRow(result, isGuest)`; `emptyRow()` passes `false`. Template `col-driver` cell now wraps a star span + name span. Test class: 8→9 green.
- **Task 3 (MARK-03, Lineup):** Added `homeIsGuest`/`awayIsGuest` to `DriverPairing`; `buildPairings` now retains `List<RaceLineup>` (dropped the `.map(RaceLineup::getDriver)` that discarded the flag) and reads `.getDriver().getPsnId()` / `.isGuest()` by index. Template: star before home name, after away name (right-aligned column). Test class: 11→12 green.

## Verification

- Per-task targeted tests green: ResultsGraphicServiceTest (12), ProvisionalScoresGraphicServiceTest (9), LineupGraphicServiceTest (12).
- Wave gate `./mvnw clean verify`: **1430 surefire + 523 failsafe tests, 0 failures, 0 errors**; JaCoCo line coverage **89.03%** (≥82% gate); SpotBugs + Checkstyle guards OK.
- Construction-site uniqueness confirmed: exactly one production `new DriverResultRow` / `new ProvisionalRow` (+emptyRow) / `new DriverPairing` site each — no missed callers.
- The other nine `*-render` templates remain unmarked (team-only; VERIFIED audit in RESEARCH).

## Visual Note (for Plan 06)

The marker spans are siblings inside `.driver-info` flex-column cells (results/lineup). The Plan-06 playwright-cli visual approval confirms/fine-tunes exact glyph placement and inline alignment per CONTEXT D-01/D-03 — treatment type (icon+accent) is locked, only placement polish is open.

## Self-Check: PASSED

No out-of-scope changes. All edits confined to the plan's `files_modified` whitelist.
