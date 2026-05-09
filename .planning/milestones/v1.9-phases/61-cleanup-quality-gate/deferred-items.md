# Phase 61 Deferred Items

Items discovered during plan 61-02 execution that are out of scope and tracked here for follow-up.

## Pre-existing Unused Fields

_Resolved by 61-gap-06 (commit 451eca8): `PlayoffService.playoffSeedRepository` was removed
along with `StandingsService.teamRepository`. No outstanding pre-existing unused fields
remain in the touched scope._

## Disabled Tests (Plan 61-04 follow-up)

- `SeasonPhaseControllerTest.givenSeasonWithoutRegularPhase_whenGetSeasonDetail_thenRendersEmptyStateCard` — disabled with `@Disabled` because TestHelper.createSeason now auto-bootstraps a REGULAR phase carrying scoring (D-06), and removing that phase from within a `@Transactional` MockMvc test triggers orphanRemoval-vs-OSIV-cache interactions that the previous (broken) test did not anticipate. The empty-state controller branch is still functionally exercised by the 60-02 IT path; this MockMvc test needs a fixture rewrite that separates the seed-then-delete flow into two transactions (or uses `@DirtiesContext`). Re-enable in Plan 61-04.
