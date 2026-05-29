---
phase: 105-team-card-visual-redesign
plan: 02
status: complete
completed: 2026-05-29
requirements: [CARD-02, CARD-03]
---

# Plan 105-02 Summary: Composites — 5 Carbon templates + ProvisionalScores raceLabel

## What was built

The 5 composite/matchup graphics now render in the Carbon/Gold "Carbon HUD"
system, and `ProvisionalScoresGraphicService` sets `raceLabel` only for
multi-race matches (D-05).

## Tasks completed

1. **ProvisionalScores raceLabel conditional (D-05, CARD-03)** —
   `buildContext` now computes `totalRaces = race.getMatch() != null ?
   race.getMatch().getRaces().size() : 0` and sets
   `raceLabel = totalRaces > 1 ? "Race " + raceIndex : null`. The
   `.race-chip` (`th:if="${raceLabel != null}"`) therefore disappears on
   single-race matches. Test-impact handled (both branches asserted):
   - `createValidRace` fixture extended to ≥2 races so the existing
     `whenValidRace…` ("Race 3") and `givenSameRaceTwice…` ("Race 2")
     assertions stay valid.
   - New `createSingleRaceMatch` fixture (separate from `createValidRace`)
     + new test `givenSingleRaceMatch_whenBuildContext_thenRaceLabelIsNull`.
   - `ProvisionalScoresGraphicServiceTest`: 8 tests green.

2. **5 Carbon composite templates (CARD-03)** — drop-in replacement of
   settings/lineup/results/match-results/provisional-scores render templates
   with the handoff versions. Verified every handoff `th:*` binding is a
   subset of the variables the rendering service sets (no new mandatory model
   variable; `p`/`row` are `th:each` iteration vars). race-chip gated on
   `raceLabel != null`; no `invert` filter on `ctcLogoBase64`.
   `TemplateRenderingSmokeIT`: 70 tests green.

## Deviation — scorecard winner-gold (operator visual feedback)

The handoff `results-render.html` rendered both teams' per-driver points
neutral (`#e6e6ec`). The operator's approved Claude-Design presentation
showed the **winning team's** per-driver points in gold (`#f5c542`), loser
neutral. The byte-identical drop-in had dropped this detail.

Fix: added `.points.winner { color: #f5c542; }` and `th:classappend` on the
existing `homeIsWinner`/`awayIsWinner` flags (the service already sets them
via `homeTotal > awayTotal`). No new model variable. Confirmed in the
rendered HTML (6 winner-class point spans for the 95:82 home win) and via
screenshot. Provisional-scores intentionally keeps gold on the OVERALL row
only — matches its reference `03-provisional-scores.png` (operator confirmed
no change needed there).

## Visual verification

Operator-reviewed render screenshots in `.screenshots/105-composites/`:
- `match-results.png` (real PNG via `download-match-results`) vs reference
  `02-composite-match-results.png` — 1:1 layout/design match.
- `render-race-results-v2.png` — scorecard with gold winner points (approved).
- `render-lineup.png`, `render-settings.png` — Carbon system confirmed.

## Commits

- `c02fdb94` feat(provisional-scores): raceLabel only for multi-race matches (D-05)
- `9d60b93d` feat(graphics): apply Carbon HUD to 5 composite render templates
- `a30f9613` fix(scorecard): gold winner points in results graphic

## Tests

- `ProvisionalScoresGraphicServiceTest`: 8 green (both raceLabel branches).
- `TemplateRenderingSmokeIT`: 70 green (no render exception on admin GETs).
- Dev-server boot: clean (0 EL1079E, 0 TemplateProcessingException).

## Note for downstream plans

The "byte-identical drop-in" assumption from the plan held for 4/5 templates;
results-render.html needed the winner-gold detail re-added because the handoff
template did not carry it. When reviewing the remaining handoff templates in
105-03/105-04, compare against the approved Claude-Design presentation
screenshots (not just the handoff template file) for such dropped accents.
