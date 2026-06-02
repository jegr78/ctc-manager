# Plan 114-04 — Summary

**Wave:** 3 | **Status:** complete | **Branch:** gsd/v1.17-guest-drivers

## What was built

Replaced the four Plan-01 stub bodies in `DriverRankingServiceGuestIT` with real full-DB (H2) integration tests pinning SCORE-01/02/03 + the alltime edge against the seeded dual-role + pure-guest fixture. This is the Nyquist regression lock for Phase 114.

The class is annotated `@CtcDevSpringBootContext @Tag("integration") @Transactional`. `@Transactional` provides the Hibernate session for lazy associations (match/team-parent proxies) accessed in test bodies and rolls back the D-15 removal mutation so it cannot pollute sibling tests in the shared TCF context (no `@DirtiesContext`). Fixture rows are located via `driverRepository.findByPsnId(...)`, `raceLineupRepository.findByDriverId(...)` (guest lineup → race), and `seasonRepository.findByYearAndNumber(2026, 99)`.

### Task 1 — SCORE-01 + SCORE-02 (D-13, D-14)
- `givenGuestResultInRace_whenAggregateMatchScores_thenCountsForFieldingTeamScore` (D-13): reloads the dual-role guest's race, calls `aggregateMatchScores`, asserts `isDriverInTeam(guestResult, raceId, awayTeamId)` is true and the away match score is `> 0` and `>=` the guest's points.
- `givenPureGuestWithNoSeasonDriver_whenAggregateAcrossPhases_thenAppearsInRankingWithFieldingTeam` (D-14): asserts pure guest `Test_Guest_1` appears with non-null team `T-BRV` (sub-team T-BRV 2 rolled up to parent) and points equal to its seeded RaceResult; and that dual-role `Test_DualRole_1` appears exactly once under home team `T-ALF` (home-first).

### Task 2 — SCORE-03 idempotency + clean removal (D-15) + alltime (D-16); VALIDATION.md
- `givenGuestResultSavedTwice_whenAggregateMatchScores_thenScoresAreIdempotent` (D-15): calls `aggregateMatchScores` twice on the dual-role race, asserts home/away scores equal across both calls (recompute-from-all-legs replaces, never accumulates). Removal half: removes the pure guest's `RaceResult` + `RaceLineup`, re-aggregates, asserts (a) the guest's RaceResult is gone, (b) the away match score dropped by exactly the guest's points, (c) the guest no longer appears in `aggregateAcrossPhases`.
- `givenPureGuestInAlltimeScope_whenCalculateAlltimeRankingForSeason_thenTeamIsNotNull` (D-16): asserts the pure guest appears in `calculateAlltimeRanking(List.of(seasonId))` with non-null team `T-BRV` — proving the Plan-02 D-04 alltime lineup fallback through the real DB.
- `114-VALIDATION.md`: Per-Task Verification Map filled with real task IDs/commands (all ✅), Wave-0 checkboxes ticked, `wave_0_complete: true`, `status: executed`, `nyquist_compliant: true` retained. Manual `/gsd-auto-uat 114` profile-render-smoke row preserved.

## Deviation
The D-15 removal half uses the documented fallback (delete the guest's `RaceResult` + `RaceLineup`, then re-aggregate) rather than invoking the full `RaceLineupService` save path. **Critical detail discovered during execution:** `Race.results` is `@OneToMany(cascade=ALL, orphanRemoval=true)`. Calling `aggregateMatchScores` on the managed race first initializes its `results` collection; deleting a child `RaceResult` via the repository while that parent collection is still managed causes `orphanRemoval`/cascade to **re-persist the row** on flush. Fix: `entityManager.clear()` before the deletion so the deletion operates on fresh entities whose parent collection is not loaded — then the DELETE persists cleanly. Documented so future guest-removal ITs avoid the same trap.

## Verification
- `./mvnw -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false verify` — **4/4 IT green** (`Tests run: 4, Failures: 0, Errors: 0`), full surefire suite green alongside.
  - The run reports `BUILD FAILURE` / exit 1 **only** from the JaCoCo coverage gate — expected artifact of `-Dit.test=` filtering out most ITs (lowers measured coverage). Not a test failure. The authoritative coverage gate is the phase-end `./mvnw clean verify -Pe2e`.

## Acceptance criteria — all met
- `Test_Guest_1|Test_DualRole_1` refs ≥2 (9) ✓ ; `aggregateMatchScores` ≥1 (5) ✓ ; `aggregateAcrossPhases` ≥1 (2) ✓ ; `calculateAlltimeRanking` ≥1 ✓ ; no placeholder stubs remaining (0) ✓ ; idempotency calls `aggregateMatchScores` ≥2 ✓ ; IT exits with 4 green tests ✓ ; VALIDATION.md `nyquist_compliant: true` + `wave_0_complete: true`, no TBD rows ✓

## Files modified
- `src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java`
- `.planning/phases/114-scoring-personal-crediting/114-VALIDATION.md`
