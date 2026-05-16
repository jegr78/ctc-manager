---
phase: 62-public-site-phases-groups
plan: "05"
type: execute
wave: 6
status: complete
completed: 2026-05-07
subsystem: domain-service
tags:
  - alltime
  - service
  - tracked-behavior-change
  - tdd
  - D-19
dependency_graph:
  requires:
    - "62-04"
  provides:
    - alltime-standings-cross-phase
    - alltime-driver-ranking-cross-phase
  affects:
    - SiteGeneratorService (generateAlltimeStandings, generateAlltimeDriverRanking)
    - StandingsPageGenerator (via StandingsService.calculateAlltimeStandings)
    - DriverRankingPageGenerator (via DriverRankingService.calculateAlltimeRanking)
tech_stack:
  added: []
  patterns:
    - TDD RED-GREEN cycle (Mockito-based unit tests)
    - JPQL @Query + @EntityGraph repository finder
    - Tracked Behavior Change documentation in Javadoc + commit body + SUMMARY
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
    - src/main/java/org/ctc/domain/service/StandingsService.java
    - src/main/java/org/ctc/domain/service/DriverRankingService.java
    - src/test/java/org/ctc/domain/service/StandingsServiceTest.java
    - src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java
decisions:
  - "Mockito-based tests added directly to existing *ServiceTest classes (no new IT classes) — both services are injected with all required mocks already and can be stubbed without Spring context."
  - "DriverRankingServiceTest RED test wired to old IsNull finder to prove the RED gate; GREEN test re-wired to new findByRaceMatchdaySeasonIdIn finder returning both REGULAR+PLAYOFF results."
  - "Old findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn and calculateStandings(UUID seasonId) bridge left intact — used by other callers; removal is out of Plan 5 scope."
metrics:
  duration: "~25 minutes"
  completed: "2026-05-07T05:53:51Z"
  tests_added: 2
  tests_total: 1213
  line_coverage: "85.60%"
---

# Phase 62 Plan 5: D-19 Alltime Cross-Phase Aggregation Summary

**One-liner:** Alltime standings and driver ranking now aggregate REGULAR + PLAYOFF + PLACEMENT phases per season (previously REGULAR-only), via `findAllPhases` loop in StandingsService and new `findByRaceMatchdaySeasonIdIn` finder in DriverRankingService.

**Status:** Complete  
**Branch:** gsd/v1.9-season-phases-groups

---

## Files Modified

| File | Change |
|------|--------|
| `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` | New `findByRaceMatchdaySeasonIdIn(List<UUID>)` finder — no IsNull filter |
| `src/main/java/org/ctc/domain/service/StandingsService.java` | `calculateAlltimeStandings` inner loop iterates `seasonPhaseService.findAllPhases` per season |
| `src/main/java/org/ctc/domain/service/DriverRankingService.java` | `calculateAlltimeRanking` swaps to `findByRaceMatchdaySeasonIdIn` (drops IsNull) |
| `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` | New test `givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints` |
| `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` | New test `givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults` |

---

## Public Method Signatures

UNCHANGED:
- `StandingsService.calculateAlltimeStandings(List<Season> seasons): List<TeamStanding>`
- `DriverRankingService.calculateAlltimeRanking(List<UUID> seasonIds): List<DriverRanking>`

---

## Tracked Behavior Changes

> **Tracked Behavior Change (D-19):** Alltime aggregation now spans REGULAR + PLAYOFF + PLACEMENT phases per season instead of REGULAR-only.
>
> Public-site impact:
> - `/alltime-standings.html` totals will recompute. Any historical season with a PLAYOFF or PLACEMENT phase now contributes those phases' points to alltime totals. Per-team alltime points may shift visibly.
> - `/alltime-driver-ranking.html` similarly: drivers' total race count and aggregate points now include PLAYOFF bracket races.
>
> Public-site users with bookmarks or mental-models of today's alltime numbers will see different totals after Phase 62 ships. The change is intentional — alltime should reflect the full season's achievements, not just the regular phase.
>
> No data migration. No URL changes. No public-API changes. Only the internal aggregation path is updated.

---

## Draft v1.9 Release-Notes Bullet

Copy this verbatim into the v1.9 release notes when the milestone ships (post-Phase-62-merge, before final tag):

```
* **alltime-standings and alltime-driver-ranking now include PLAYOFF + PLACEMENT phase results** —
  Previously these public-site pages aggregated only REGULAR-phase points and races. Multi-phase seasons
  (e.g. 2023 with REGULAR + PLAYOFF) now contribute the full phase set to the alltime totals.
  Bookmarked numbers may differ from before. The change is intentional and consistent with the v1.9
  Season Phases & Groups model. (D-19, Phase 62 Plan 5.)
```

---

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED  | `70d2ce0` | test(62-05): add failing alltime cross-phase tests (TDD-RED) — both tests FAIL on REGULAR-only code path |
| GREEN | `0b55ab0` | feat(62-05): D-19 alltime cross-phase aggregation — TRACKED BEHAVIOR CHANGE (TDD-GREEN) — both tests PASS |

---

## Blocking Constraints Verified

| Constraint | Verification | Result |
|-----------|--------------|--------|
| SC4 byte-identity | `StandingsPageGeneratorTest`, `DriverRankingPageGeneratorTest`, `MatchdaysPageGeneratorTest`, `TeamProfilePageGeneratorTest`, `DriverProfilePageGeneratorTest` — all 37 tests green | PASS |
| SC2 deterministic sort tiebreakers | All 4 sort sites in `StandingsService` retain `.thenComparing(s -> s.getTeam().getShortName())` — verified via grep | PASS |

---

## Test Coverage

- 2 new Mockito-based unit tests added to existing `*ServiceTest` classes.
- Tests prove PLAYOFF data flows into alltime aggregation for both standings and driver ranking.
- Existing alltime tests (REGULAR-only seasons) still pass — the new path is a strict superset.
- **Final verify:** 1213 tests, 0 failures, 4 pre-existing skips.
- **JaCoCo line coverage:** 85.60% (minimum 82% — constraint satisfied).

---

## Commits

| # | SHA | Message |
|---|-----|---------|
| 1 | `70d2ce0` | test(62-05): add failing alltime cross-phase tests (TDD-RED) for D-19 TRACKED BEHAVIOR CHANGE |
| 2 | `0b55ab0` | feat(62-05): D-19 alltime cross-phase aggregation — TRACKED BEHAVIOR CHANGE (TDD-GREEN) |

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] DriverRankingServiceTest RED mock required update for GREEN gate**

- **Found during:** Task 2 (TDD-GREEN)
- **Issue:** The RED test was wired to `findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn` returning only 1 REGULAR result (proving the IsNull filter excludes PLAYOFF). After the D-19 implementation switched to `findByRaceMatchdaySeasonIdIn`, the test called the new method — which had no mock → empty result → assertion "not empty" fails differently.
- **Fix:** Updated the GREEN test to mock `findByRaceMatchdaySeasonIdIn` returning both REGULAR + PLAYOFF results, proving `racesCount == 2` as expected.
- **Files modified:** `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java`
- **Commit:** `0b55ab0` (included in GREEN commit)
- **Why-not-in-scope:** This is a necessary test-fixture update for TDD GREEN — not a new feature or scope expansion. The test logic is correct; only the mock wiring needed to follow the new finder name.

---

## Open Questions Surfaced

- D-19 PLACEMENT semantics: if a future PLACEMENT phase scores reverse (lower placement = more points), the alltime aggregation may produce counter-intuitive totals for that season. Documented as accepted risk in CONTEXT.md Open Question 1; revisit in a future phase if the user reports surprise.

---

## Next Steps

- **Plan 6:** SC5 regression IT (`SiteGeneratorPhaseAwarenessIT`) — adds the multi-phase + GROUPS regression test that asserts the public-site rendering AT THE HTML level, including verification that alltime pages are written.
- **Plan 7:** Visual + a11y final sweep + draft this release-notes bullet into the actual release notes for the v1.9 milestone tag.

---

## Known Stubs

None — all data paths are fully wired. No placeholder values in templates or services.

---

## Self-Check

- [x] `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` — `findByRaceMatchdaySeasonIdIn` present
- [x] `src/main/java/org/ctc/domain/service/StandingsService.java` — `findAllPhases` present in `calculateAlltimeStandings`
- [x] `src/main/java/org/ctc/domain/service/DriverRankingService.java` — `findByRaceMatchdaySeasonIdIn` present (IsNull removed)
- [x] Commits `70d2ce0` (RED) and `0b55ab0` (GREEN) exist in git log
- [x] 1213 tests passing, 85.60% coverage

## Self-Check: PASSED
