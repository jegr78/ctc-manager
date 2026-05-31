---
phase: 109
plan: "05"
subsystem: graphics
tags: [graphics, playwright, thymeleaf, walkover, visual-checkpoint]
requires: [109-01, 109-04]
provides:
  - homeIsWalkover/awayIsWalkover context flags in 3 graphic services
  - .wo-badge on forfeiter card slot in match-results / lineup / provisional graphics
  - LineupGraphicService walkover guard (no empty-lineup crash)
  - ProvisionalScoresGraphicService walkover guard (clear fail-fast message)
affects:
  - MatchResultsGraphicService
  - LineupGraphicService
  - ProvisionalScoresGraphicService
  - match-results-render.html
  - lineup-render.html
  - provisional-scores-render.html
tech-stack:
  added: []
  patterns: [thymeleaf-context-flag, additive-inline-css, duplicate-selector-position-relative]
key-files:
  created: []
  modified:
    - src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java
    - src/main/java/org/ctc/admin/service/LineupGraphicService.java
    - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
    - src/main/resources/templates/admin/match-results-render.html
    - src/main/resources/templates/admin/lineup-render.html
    - src/main/resources/templates/admin/provisional-scores-render.html
key-decisions:
  - Walkover detection computed in-service as booleans (homeIsWalkover/awayIsWalkover) and injected via ctx.setVariable — templates render in a headless browser with no Spring context, so no entity comparison in the template (RESEARCH §7)
  - LineupGraphicService skips the empty-lineup throw ONLY when the match has a walkover (lineups.isEmpty() && walkoverTeam == null) — non-walkover empty lineups still fail loudly
  - ProvisionalScoresGraphicService fails fast with "Walkover match has no provisional scores" before the generic empty-results throw; the badge is defensive only (real walkover path never renders a provisional graphic)
  - .wo-badge added as additive inline CSS + a duplicate `.team-card-container/.team-card-cell { position: relative; }` selector so the original Phase-108 rule is byte-unchanged and the badge overlays the card slot bottom-centre
requirements-completed: [WO-03]
duration: ~40 min
completed: 2026-05-30
visual_checkpoint: approved (user "Passt", 2026-05-30)
---

# Phase 109 Plan 05: Walkover Graphics Badge Summary

The "w/o" badge now renders on the forfeiter's team-card slot in all three Phase-108 graphics (match-results, lineup, provisional-scores). Each service computes `homeIsWalkover`/`awayIsWalkover` and injects them as context variables; the templates show an additive gold `.wo-badge` over the forfeiter card. The lineup and provisional services gained walkover guards so they no longer crash unhelpfully on a results-less walkover match.

**Tasks:** 2 auto + 1 visual checkpoint | **Files:** 6 modified

## What was built

- **Task 1 — match-results + lineup:** `MatchResultsGraphicService` sets `homeIsWalkover`/`awayIsWalkover` (UUID `.equals`). `LineupGraphicService` computes the same from `race.getMatch().getWalkoverTeam()` and skips the `lineups.isEmpty()` throw only for walkover matches. Both render templates got a `.wo-badge` rule (gold `#f5c542`, 40px, italic, text-shadow, absolute bottom-centre via a duplicate `position: relative` selector) and `th:if`-guarded badge divs on the home and away `.team-card-container`.
- **Task 2 — provisional-scores:** `ProvisionalScoresGraphicService` throws a clear `"Walkover match has no provisional scores"` before the generic empty-results throw, and sets the two walkover flags. `provisional-scores-render.html` got the same additive `.wo-badge` (28px) on the home/away `.team-card-cell`.
- **Task 3 — visual checkpoint (approved):** Rendered match-results + lineup via the live `template-editors/{type}/preview` endpoint (sample context, placeholder cards) with the badge forced visible; provisional via a faithful static mock (placeholder cards + `n/a` rows reflecting the empty walkover result set). Screenshots in `.screenshots/wo-{match-results,lineup,provisional}.png`. User approved ("Passt").

## Verification

- `./mvnw clean test-compile` succeeds (all three services + templates parse).
- grep confirms `homeIsWalkover` (match-results), `getWalkoverTeam` (lineup + provisional), and `wo-badge` ×3 in each of the three templates (1 style rule + 2 badge divs).
- All three graphic services are JaCoCo-excluded (Playwright runtime) — no coverage obligation; correctness verified visually.
- Visual checkpoint approved by the user.

## Deviations from Plan

None. Badge colour: the plan offered `#9a9aa6` (dim) "or the template's existing dim var"; chose the template accent gold `#f5c542` for legibility against the dark card art, with a dark text-shadow. User approved the result at the visual checkpoint.

## Self-Check: PASSED

WO-03 fully complete (standings label in 109-04 + graphics badge here). All five plans of Phase 109 done. Next: phase-end `./mvnw clean verify -Pe2e` gate, then `/gsd-code-review 109`.
