---
phase: 38-season-content-data-filtering
plan: "01"
subsystem: sitegen
tags: [tdd, red-phase, test-only, sitegen, cont-01, cont-06, cont-07]
dependency_graph:
  requires: []
  provides: [tdd-red-baseline-38]
  affects: [SiteGeneratorServiceTest.java]
tech_stack:
  added: []
  patterns: [junit5-bdd-given-when-then, jsoup-html-assertions, tdd-red-green-refactor]
key_files:
  created: []
  modified:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
decisions:
  - "setUp() season renamed 'Gen Test' → 'Gen Season' to prevent CONT-06 filter from excluding test season and breaking 16 existing tests"
  - "2 of 10 new tests pass in RED phase (positive regression guards / existing behavior validated)"
metrics:
  duration_minutes: 12
  completed_date: "2026-04-16"
  tasks_completed: 2
  files_modified: 1
---

# Phase 38 Plan 01: TDD RED — Season Content & Data Filtering Summary

## One-Liner

Renamed setUp() season from "Gen Test" to "Gen Season" and wrote 10 failing TDD RED tests covering season year/number display (CONT-01), test season filtering (CONT-06), and empty match-meta/period cell behavior (CONT-07).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Rename setUp() season from "Gen Test" to "Gen Season" | c666659 | SiteGeneratorServiceTest.java |
| 2 | Write failing RED tests for CONT-01, CONT-06, CONT-07 | 0c2f405 | SiteGeneratorServiceTest.java |

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED (test) | 0c2f405 | PASS — `test(sitegen): add failing tests for CONT-01, CONT-06, CONT-07` |
| GREEN (feat) | — | Pending (plan 38-02) |
| REFACTOR | — | Pending (if needed) |

## Test Results (RED State)

- **Total tests:** 26 (16 existing + 10 new)
- **Failures:** 8 (expected RED failures)
- **Passes:** 18 (16 existing + 2 positive-guard new tests)

### Failing tests (expected RED — no implementation yet)

| Test | Requirement | Expected Failure Reason |
|------|-------------|------------------------|
| `givenSeason_whenGenerate_thenStandingsHasSeasonMeta` | CONT-01 | No `.season-meta` in standings.html |
| `givenSeason_whenGenerate_thenMatchdayHasSeasonMeta` | CONT-01 | No `.season-meta` in matchday.html |
| `givenSeason_whenGenerate_thenDriverRankingHasSeasonMeta` | CONT-01 | No `.season-meta` in driver-ranking.html |
| `givenSeason_whenGenerate_thenHeroLabelContainsYear` | CONT-01 | `.hero-label` text does not include year |
| `givenSeason_whenGenerate_thenArchiveShowsYearAndNumber` | CONT-01 | No `.season-meta` in archive.html |
| `givenTestSeason_whenGenerate_thenNoSeasonPagesCreated` | CONT-06 | Test season still generates pages |
| `givenTestSeason_whenGenerate_thenNotInArchive` | CONT-06 | Test season appears in archive |
| `givenRaceWithNoTrackOrCar_whenGenerate_thenMatchMetaAbsent` | CONT-07 | No `th:if` guard on `.match-meta` div |

### Passing tests (positive guards — expected to pass in RED)

| Test | Requirement | Why Passes in RED |
|------|-------------|-------------------|
| `givenRaceWithOnlyTrack_whenGenerate_thenMatchMetaPresent` | CONT-07 | match-meta already renders with track present (no change needed) |
| `givenSeasonWithNoDates_whenGenerate_thenPeriodCellEmpty` | CONT-07 | Existing archive template already handles null dates via `th:if` guards |

## Deviations from Plan

None — plan executed exactly as written. The 2 tests that passed in RED phase were anticipated by the plan ("1 may PASS or FAIL — either result is valid for TDD baseline").

## Known Stubs

None — this plan modifies only test code. No production stubs introduced.

## Threat Flags

None — only test code modified; no production trust boundaries crossed.

## Self-Check

- [x] `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — modified and committed
- [x] Commit c666659 exists (rename)
- [x] Commit 0c2f405 exists (RED tests)
- [x] 26 tests reported by Maven
- [x] 8 failures (all expected RED tests)
- [x] No "Gen Test " string in test file

## Self-Check: PASSED
