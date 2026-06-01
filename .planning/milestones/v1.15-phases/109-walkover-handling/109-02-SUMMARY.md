---
phase: 109
plan: "02"
subsystem: standings-scoring
tags: [standings, scoring, walkover]
requires: [109-01]
provides:
  - processMatch walkover auto-win branch (read-time)
  - TeamStanding.hasWalkover flag (merge-propagating)
affects: [StandingsService, calculateStandings, calculateStandingsWithBuchholz, site-standings-data]
tech-stack:
  added: []
  patterns: [read-time-scoring, succession-resolved-ids, or-merge-flag]
key-files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/StandingsService.java
    - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
key-decisions:
  - Walkover branch inserted AFTER bye return (line 340) and BEFORE the score-null guard (now line 363) so it takes precedence over partial scores (D-08)
  - Opponent (home OR away, resolved via succession map) gets addWin()+addMatchPoints(pointsWin); forfeiter gets addLoss()+setHasWalkover(true); no addPointsFor/addPointsAgainst (D-07)
  - hasWalkover added to TeamStanding (manual getter/setter, not Lombok) and OR-propagated in merge()
requirements-completed: [WO-01]
duration: ~20 min
completed: 2026-05-30
---

# Phase 109 Plan 02: Walkover Standings Scoring Summary

`StandingsService.processMatch()` now awards the read-time auto-win for walkover matches — the opponent (home or away) wins with full match points, the forfeiter takes a loss with 0 points, no point difference and no Buchholz contribution. `TeamStanding` carries a merge-propagating `hasWalkover` flag for the standings template.

**Tasks:** 2 | **Files:** 2 modified

## What was built

- **Task 1 — TeamStanding.hasWalkover:** `private boolean hasWalkover;` + manual `isHasWalkover()`/`setHasWalkover(boolean)` (TeamStanding is not Lombok-annotated); `merge()` ORs the flag (`this.hasWalkover = this.hasWalkover || other.hasWalkover`) so a forfeiter's flag survives sub-team succession merges.
- **Task 2 — processMatch walkover branch (TDD):** inserted between the bye `return;` and the `getHomeScore() == null` guard. Resolves `forfeiterRaw/homeRaw/awayRaw`, computes the opponent as "the side that is not the forfeiter" (succession-resolved), then awards per D-06. Five unit tests written first (all 5 RED without the branch), then GREEN: home-forfeit, away-forfeit, partial-scores-precedence, no-point-difference, hasWalkover-flag.

## Grep-all-usages audit (CLAUDE.md)

`processMatch` is called only from `StandingsService:171` (inside `calculateStandings`). `calculateStandingsWithBuchholz` (line 203) funnels through the same `calculateStandings` computation, so the walkover branch is picked up by both entry points and by all 16 downstream callers (graphic services, sitegen, SwissPairing, PlayoffSeeding, StandingsViewService) automatically — no per-caller change. Buchholz is computed from races, not walkover state → no Buchholz change needed (D-07).

## Verification

- `./mvnw -Dtest=StandingsServiceTest test` → 25 tests green (20 pre-existing + 5 new walkover); 0 failures.
- TDD: the 5 walkover tests confirmed RED before the branch (no win/loss awarded; forfeiter filtered by removeIf), GREEN after.
- Branch order verified: `isBye` (332) → `getWalkoverTeam() != null` (342) → `getHomeScore() == null` guard (363).

## Deviations from Plan

None - plan executed exactly as written. (Tasks 1 and 2 both modify StandingsService.java and are interdependent — the flag is asserted by Task 2's tests — so they landed in one cohesive green commit rather than two; no intermediate broken state.)

## Self-Check: PASSED

WO-01 complete. WO-03 data side (hasWalkover flag) ready; the visible "w/o" label lands in 109-04 (where WO-03 is marked complete). Ready for 109-03 (admin marking flow).
