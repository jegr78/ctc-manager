---
plan_id: 65-02
phase: 65
phase_name: graphics-bridge-migration
title: Domain Buchholz cleanup — delete dead API + inline private helper
status: complete
completed: 2026-05-07
wave: 2
depends_on: [65-01]
---

# Phase 65 — Plan 02 Summary: Domain Buchholz cleanup complete

## Outcome

D-04a + D-04b shipped in a single atomic commit (`190110f refactor(65): delete dead Buchholz API + inline calculateBuchholzScores`). The bridge method `StandingsService.calculateStandings(UUID seasonId)` is now reachable from ZERO production call sites — Wave 3's deletion of the method declaration is purely a removal, no further migration needed.

## Tasks

| Task | Decision | Outcome |
|---|---|---|
| 65-02-T1 | D-04a — delete SwissPairingService.calculateBuchholz | Method removed (~22 lines). Collateral cleanup: dead `seasonRepository` field + import removed; orphaned `getPlayedOpponents(UUID)` public overload + `getPlayedOpponents(UUID, Map)` private helper deleted (only-callers were the removed method). Test `givenOneCompletedRound_whenCalculateBuchholz...` deleted from SwissPairingServiceTest. |
| 65-02-T2 | D-04b — inline calculateBuchholzScores | Private 1-arg helper inlined into `calculateBuchholzScoresForPhase(SeasonPhase phase)`. The inlined body now calls `calculateStandings(phase.getId(), null)` directly — eliminates both the seasonId bridge invocation AND the `seasonRepository.findById` roundtrip. |
| 65-02-T3 | Plan-level verification | Production-code grep gate `grep -rn 'calculateStandings(season.getId()\|calculateStandings(seasonId' src/main/java | wc -l` returns **0**. Targeted Surefire green: SwissPairingServiceTest 13/13, StandingsServiceTest 23/23. |

## Files modified

| File | Change |
|---|---|
| `src/main/java/org/ctc/domain/service/SwissPairingService.java` | -53 lines — calculateBuchholz removed; 2 dead getPlayedOpponents overloads removed; SeasonRepository field + import removed |
| `src/main/java/org/ctc/domain/service/StandingsService.java` | -35/+15 lines — calculateBuchholzScores inlined into calculateBuchholzScoresForPhase via canonical (phase.getId(), null) call |
| `src/test/java/org/ctc/domain/service/SwissPairingServiceTest.java` | -19 lines — calculateBuchholz test removed |

## Production callers of the seasonId bridge

| Wave 1 baseline | Wave 2 outcome |
|---|---|
| 2 callers (SwissPairingService.calculateBuchholz line 158, StandingsService.calculateBuchholzScores line 209) | **0 callers** in src/main/java |

## Wave 3 readiness

The bridge method `StandingsService.calculateStandings(UUID seasonId)` (line 148–151) and its Javadoc / section divider (lines 139–146) can now be deleted in Wave 3 with no further callers to migrate. StandingsServiceTest's ~14 dedicated bridge tests still exist and will be triaged per RESEARCH.md (3 DELETE + 10 REWRITE) in Plan 65-03.

## Post-dispatch validation

- `git branch --show-current` = `gsd/v1.9-season-phases-groups` (unchanged)
- Wave 2 commit (`190110f`) on top of Wave 1 commit (`8ad7952`) and Wave 1 summary (`3051417`)
- No production-code drift outside the documented scope (SwissPairingService, StandingsService private helper) — verified via `git diff --stat`

## Deviations

The originally-spawned executor agent returned mid-task (likely token budget cliff or model truncation) after deleting the calculateBuchholz method body and removing the SeasonRepository field/import. The orchestrator picked up the partial state, completed the collateral cleanup (orphaned getPlayedOpponents helpers), inlined calculateBuchholzScores per D-04b, deleted the test, ran targeted Surefire, and committed atomically per the plan's commit cadence. No NEEDS_CONTEXT escalation was needed; all changes stayed within the plan's documented scope.

## Next

Plan 65-03 (Wave 3): delete the bridge method declaration; triage the 13 dedicated bridge tests in StandingsServiceTest (3 DELETE + 10 REWRITE per RESEARCH.md); remove SiteGeneratorServiceIT line 154 negative-verify (won't compile after deletion); SC1 grep gate verifies `calculateStandings(seasonId` returns 0 in src/main/java.
