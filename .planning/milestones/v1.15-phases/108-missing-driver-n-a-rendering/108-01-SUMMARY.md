# Plan 108-01 Summary — Central TEAM_DRIVERS + Lineup/Results n/a padding

**Status:** Complete
**Requirements:** LINEUP-01, LINEUP-02
**Decisions realized:** D-01 (render-time padding, no persistence/Flyway), D-03 (padding driven by real-row count), D-06 (single central `TEAM_DRIVERS` constant)

## What was built

- Added `protected static final int TEAM_DRIVERS = 6;` to `AbstractGraphicService` alongside the existing `FONT_CLASSPATH`/`CTC_LOGO_CLASSPATH` constants — the single source of truth for team size.
- `LineupGraphicService.buildPairings`: replaced `Math.max(home,away)` loop bound with `TEAM_DRIVERS`; absent driver slots now fill `"n/a"` (driver name) with suppressed (empty) nickname instead of `""`.
- `ResultsGraphicService.buildResultRows`: same `Math.max` → `TEAM_DRIVERS` swap; absent slots fill `"n/a"` + 0 points, nickname suppressed.
- `lineup-render.html`: both nickname `th:if` guards switched from `!= ''` to `!= 'n/a'`.
- `results-render.html`: both nickname guards switched to `!= 'n/a'`; both points ternaries switched to `!= 'n/a'` with the false-branch now the literal `0` (D-05) instead of empty string.

## Deviations from plan

- The plan's `<interfaces>` block described a `DriverEntry` record and a `nickname`-class/`!= ''` bare-text template shape; the **actual** code builds rows inline via `getPsnId()`/`resolveNickname(...)` and the templates use the `driver-nickname` class with `'(' + nickname + ')'` formatting. Edits were applied against the real code; behavior matches the plan intent exactly.
- Test method `givenUnevenTeamSizes_whenBuildPairings_thenPairsUpToMinimumWithEmptySlots` keeps its historical name (now pads to 6); renaming was out of scope and the plan references it by name.

## Tests

- `LineupGraphicServiceTest`: 11 green. All four `buildPairings` size asserts → `hasSize(6)`; identity asserts preserved; padded slots assert `"n/a"`.
- `ResultsGraphicServiceTest`: 11 green. All four `buildResultRows` size asserts → `hasSize(6)`; padded slots assert `"n/a"` + 0 points.

## Acceptance verification

- `TEAM_DRIVERS = 6` present (×1), `Math.max` absent in both services, `!= ''` absent in both templates, `"n/a"` present in both templates, no old `hasSize(1|2|3)` remain.
