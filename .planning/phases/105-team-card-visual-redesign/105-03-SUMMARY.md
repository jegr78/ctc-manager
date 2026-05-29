---
phase: 105-team-card-visual-redesign
plan: 03
status: complete
completed: 2026-05-29
requirements: [CARD-02, CARD-04]
---

# Plan 105-03 Summary: Matchday/List — 5 Carbon templates

## What was built

The 5 matchday/list graphics now render in the Carbon/Gold "Carbon HUD"
system as drop-in replacements: matchday-schedule, matchday-overview,
standings, matchday-results, power-rankings.

## Task completed

**5 Carbon matchday/list templates (CARD-04, CARD-02)** — byte-identical
drop-in of the handoff versions. Verified every handoff `th:*` binding is a
subset of what the rendering service sets (no new mandatory model variable):
- matchday-schedule/overview/results → `data` (`MatchdayGraphicData`), `m`/`entry`
  are `th:each` iteration vars.
- standings → `standings` + `rowHeightPx/fontSizePx/logoSizePx/posFontSizePx`
  + `phaseLabel/seasonYear/ctcLogoBase64/fontBase64`. **Dynamic row-height
  preserved** (`StandingsGraphicService` unchanged — it computes the px sizes
  from row count so all teams always fit).
- power-rankings → `data` (`PowerRankingsGraphicData`).
No inline base64 in any of the 5 (maxline ≤ 173), so no Spring Boot 4 SpEL
10k-limit risk like the team card had.

## Visual verification

Operator-approved render screenshots in `.screenshots/105-matchday/`:
- `view-matchday-overview.jpg`, `view-matchday-schedule.jpg`,
  `view-matchday-results.jpg`, `view-power-rankings.jpg` (via preview API
  data-URL render at 1920×1080).
- `view-standings.jpg` — standings has no preview key / GET download, so it
  was rendered via a **throwaway** `standings` case temporarily added to
  `TemplatePreviewService` (reverted via `git checkout HEAD` after the
  screenshot; never committed). Carbon look confirmed: gold ranks + PTS
  column, gold-highlighted leader row, team-color row edges + logo coins,
  zebra rows, dynamic row-height.

## Commit

- `ac8a13c4` feat(graphics): apply Carbon HUD to 5 matchday/list render templates (CARD-04)

## Tests

- `TemplateRenderingSmokeIT`: 70 green (no render exception on admin GETs).
- Dev-server boot: clean (0 EL1079E, 0 TemplateProcessingException).

## Note

`StandingsGraphicService.java` was briefly suspected of corruption during a
`cat -A` macOS flag error — confirmed intact (130 lines, byte-identical to
HEAD). No source change there.
