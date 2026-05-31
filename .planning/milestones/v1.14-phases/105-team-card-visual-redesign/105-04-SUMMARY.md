---
phase: 105-team-card-visual-redesign
plan: 04
status: complete
completed: 2026-05-29
requirements: [CARD-02, CARD-04]
---

# Plan 105-04 Summary: Overlay (geometry-locked) + 4 analogy rebuilds + end-of-phase gate

## What was built

The stream overlay now renders in the Carbon/Gold system with geometry and
transparency exactly preserved, and the 4 non-handoff analogy templates
(matchday-pairings + 3 playoff-round) are rebuilt to the Carbon system. This
completes all 16 admin graphics for the phase.

## Tasks completed

1. **Carbon overlay (CARD-04, CARD-02)** — drop-in of the handoff
   `overlay-render.html`. HARD-LOCKED geometry verified byte-exact:
   top-bar 921×120 @ 500/0, bottom-wrapper 1275×148 @ 218/924,
   ctc-logo-corner 80×80 @ 16/16, skewX ±13°, `background: transparent`.
   Bindings are a subset of `OverlayGraphicService` context (no new var).

2. **4 analogy rebuilds (D-06, CARD-04)** — rebuilt from the landed Carbon
   matchday siblings using only each service's exposed bindings:
   - `matchday-pairings` ← matchday-overview (keeps `m.homeRecord/awayRecord`
     + `#N` seeds; title "Pairings").
   - `playoff-round-overview` ← matchday-overview (keeps `homeSeed/awaySeed`;
     **record dropped** — `PlayoffRoundOverviewGraphicService` exposes none).
   - `playoff-round-schedule` ← matchday-schedule (record dropped).
   - `playoff-round-results` ← matchday-results (`m.homeScore/awayScore`;
     record dropped).
   All 4 carry the Carbon vignette + gold keyline tokens; no new mandatory var.

3. **End-of-phase gate** — `./mvnw clean verify -Pe2e` → **BUILD SUCCESS**.

## Overlay transparency verification (operator MUST)

Proven empirically, not just visually: the real overlay PNG generated via
`OverlayGraphicService` (`renderScreenshotTransparent` → `setOmitBackground(true)`)
is **8-bit RGBA**; alpha = 0 across all empty regions; **85.1 % of the frame
is fully transparent** (only the two bars are opaque). Composited over a
magenta/green checkerboard the board shows through everywhere except the bars
(`.screenshots/105-overlay-analogy/overlay-on-checkerboard.png`). The dark
background seen during the first review was an injected screenshot backdrop,
not the overlay's own background.

## Visual verification

Operator-approved render screenshots in `.screenshots/105-overlay-analogy/`:
`view-pairings.jpg`, `view-playoff-round-{schedule,results,overview}.jpg`,
`view-overlay.jpg` + `overlay-on-checkerboard.png`.

## End-of-phase gate results

- `./mvnw clean verify -Pe2e`: **BUILD SUCCESS** (exit 0).
- Surefire (unit + IT): 529 run, 0 failures, 0 errors, 2 skipped.
- Failsafe (E2E `-Pe2e`): 115 run, 0 failures, 0 errors.
- JaCoCo: **All coverage checks met** (~89.42 % line coverage, ≥ 82 % gate).
- SpotBugs: **BugInstance size is 0**.
- Discord WireMock / multipart ITs green (DiscordWebhookClientMultipartEditIT,
  DiscordPostService* suites). Team-card attachment names intact:
  `team-card-home.png` / `team-card-away.png` (DiscordPostService:920/922).

## Commits

- `fbde42f0` feat(overlay): apply Carbon HUD to stream overlay (geometry/transparency preserved)
- `5b7016c7` feat(graphics): rebuild 4 analogy templates to Carbon HUD (D-06, CARD-04)

## Phase outcome

All 16 admin graphics now render in the Carbon HUD system: team card (+ the
TeamCardService color-robustness patch), 5 composites (+ ProvisionalScores
raceLabel conditional + scorecard winner-gold), 5 matchday/list, stream overlay
(geometry/transparency preserved), and 4 analogy templates. No model-variable
changes beyond the two named backend tweaks; every consumer path (Discord
auto-post, Re-Post/Refresh, admin previews, template editors) unchanged.
