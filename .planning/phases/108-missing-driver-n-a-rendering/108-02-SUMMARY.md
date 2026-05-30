# Plan 108-02 Summary — Provisional-Scores n/a padding

**Status:** Complete
**Requirements:** LINEUP-03
**Decisions realized:** D-01 (render-time padding, no persistence), D-03 (padding driven by real-result count), D-04 (roster driver with no result yet shows "n/a"), D-06 (reads central `TEAM_DRIVERS`)

## What was built

- `ProvisionalScoresGraphicService.buildContext`: after the loop that fills `homeRows`/`awayRows` from `race.getResults()`, both lists are padded to `AbstractGraphicService.TEAM_DRIVERS` (6) with `emptyRow()` entries.
- Added `private ProvisionalRow emptyRow()` returning `ProvisionalRow("n/a", 0, 0, false, 0, 0, 0, 0)` — driver name `"n/a"`, all numeric fields 0, fastestLap false.
- Padding is applied **before** the overall-sum stream computations; padded rows contribute 0, so `homeOverallTotal`/`awayOverallTotal` and the per-category sums are unchanged.

## Deviations from plan

- The plan's `<interfaces>` referenced `homeTotal`/`awayTotal` accumulators and a `Nick_Home`/`hasSize(2)` test; the **actual** service computes `homeOverallTotal`/`awayOverallTotal` via stream sums after the loop, and the size-asserting test is `whenValidRace_thenTemplateContextIncludesRaceLabelAndExpectedVariables`. Edits were applied to the real code/test.

## Tests

- `ProvisionalScoresGraphicServiceTest`: 8 green. `whenValidRace_...` now asserts `homeRows`/`awayRows` `hasSize(6)`, padded row (index 5) `driverName == "n/a"` with `total == 0`, and `homeOverallTotal == 59` / `awayOverallTotal == 42` (unchanged by padding).

## Acceptance verification

- `TEAM_DRIVERS` referenced in the service, `emptyRow()` helper present, test asserts `hasSize(6)` for both sides, totals pinned unchanged.
