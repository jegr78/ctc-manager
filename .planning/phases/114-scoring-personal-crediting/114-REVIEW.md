---
phase: 114-scoring-personal-crediting
reviewed: 2026-06-01T00:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - src/main/java/org/ctc/admin/DevDataSeeder.java
  - src/main/java/org/ctc/admin/TestDataService.java
  - src/main/java/org/ctc/domain/service/DriverRankingService.java
  - src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java
  - src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java
  - src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java
  - src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: resolved
resolved: 2026-06-01
---

# Phase 114: Code Review Report

**Reviewed:** 2026-06-01
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 114 unifies driver→team attribution in `DriverRankingService` behind a single
`resolveAttributedTeam` helper (home-first SeasonDriver → fallback fielding RaceLineup with
parent rollup), adds an alltime null-team backfill, seeds a doppelrollen + pure-guest fixture
with RaceResults + score aggregation in `TestDataService`, logs a guest-lineup count in
`DevDataSeeder`, and emits driver-profile pages for lineup-only (pure-guest) drivers via a
deduped second pass in `DriverProfilePageGenerator`.

The refactor is clean: the two removed private helpers (`resolveTeamFromLineup`,
`attributeTeamFromRegularOrLineup`) and the `PhaseTeamRepository` field/import are fully gone
with no dangling references (verified via `grep -rn` across `src/`). `aggregateMatchScores`
genuinely recomputes-from-all-legs (replace, not accumulate), so the idempotency claim in the IT
holds. All referenced repository methods and entity accessors exist. Imports in the changed
files are used (Checkstyle-clean). Score aggregation is correctly invoked after every
result-write in the new `seedGuestRaceResults`.

The defects found are correctness-robustness issues in two attribution fallback paths
(non-deterministic / cross-season-leaky `findFirst()` over unordered queries) plus N+1 query
amplification in the per-driver lookups. No security issues and no data-loss risks.

## Warnings

### WR-01: Alltime guest backfill is cross-season-leaky for the season-scoped variant

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:137-143`
**Issue:** The pure-guest backfill calls `raceLineupRepository.findByDriverId(driverId)` — an
**unscoped, all-seasons** finder — even when reached via `calculateAlltimeRanking(List<UUID> seasonIds)`
(the site-generator path that deliberately excludes Test seasons). A guest who has lineups in
multiple seasons can therefore be attributed a team from a lineup belonging to a season NOT in
`seasonIds`. The `results` list is already season-scoped (`findByRaceMatchdaySeasonIdIn`), so the
team can legitimately disagree with the season the points came from. Combine with the
no-ORDER-BY query below (WR-02) and the attributed team becomes both wrong-season and
non-deterministic.
**Fix:** Scope the backfill to the same seasons as `results`. For the scoped path, prefer a
season-scoped lineup finder, e.g.:
```java
// derive the in-scope season(s) from the result set, or pass seasonIds down
raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(driverId, seasonId).stream()
        .findFirst()
        .ifPresent(rl -> driverTeamMap.put(driverId, rl.getTeam().getParentOrSelf()));
```
The unscoped finder is acceptable only for the truly-alltime `calculateAlltimeRanking()` overload.

### WR-02: Non-deterministic team attribution from unordered `findFirst()` for multi-team guests

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:140` and `:187-190`
**Issue:** Two fallback paths pick a guest's team via `...findFirst()` over a query with **no
`ORDER BY`**:
- alltime backfill: `findByDriverId(driverId).stream().findFirst()` (line 140)
- aggregate/per-phase season fallback: `findByDriverIdAndRaceMatchdaySeasonId(...).stream().findFirst()` (lines 187-190; query at `RaceLineupRepository` has no order clause).

For a pure guest who fielded for two *different* teams in the same scope, the attributed team is
whatever row the DB returns first — arbitrary and potentially unstable across H2 vs MariaDB or
across runs. The IT only covers a guest with a single fielding team, so this is uncaught. This
also risks intermittent failures of byte-identity / ordering assertions downstream.
**Fix:** Make the selection deterministic — add an explicit ordering (e.g. by most recent
race date/createdAt) to the repository query, or document and enforce a tie-break (e.g. the
fielding team of the most recent race). At minimum add an `@Query("... ORDER BY rl.race.matchday... ")`
so the same row is returned every time.

### WR-03: N+1 repository lookups in per-driver attribution and profile second pass

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:176-191`;
`src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java:77-89, 95-97, 109-111`
**Issue:** `resolveAttributedTeam` is invoked once per distinct driver and issues up to three
sequential repository calls (`findBySeasonIdAndDriverId`, `findByRaceIdAndDriverId`,
`findByDriverIdAndRaceMatchdaySeasonId`). In `aggregateAcrossPhases` this runs per driver per
season aggregation; in the profile second pass each lineup-only driver triggers another
`findByDriverIdAndRaceMatchdaySeasonId` (line 81) plus `findByDriverId` (line 95) plus
`findByDriverIdAndRaceMatchdaySeasonId` again (line 110) — i.e. 3+ queries per guest on top of
the full `findByRaceMatchdaySeasonId` already loaded at line 73. For a season with many drivers
this is a classic N+1 amplification.
**Issue note:** Pure performance is out of v1 review scope, but this is flagged as a robustness/
maintainability warning because the same `findByRaceMatchdaySeasonId(season.getId())` result set
fetched at line 73 already contains every lineup's `team` (eager via `@EntityGraph`) and could be
reused to resolve each guest's team in-memory instead of re-querying.
**Fix:** In the profile second pass, build a `Map<UUID driverId, Team>` from the already-loaded
`findByRaceMatchdaySeasonId` result (it eager-fetches `team`) and look the guest team up from that
map rather than re-querying per driver. In `DriverRankingService`, consider batch-loading
SeasonDrivers / lineups for the driver set once per call.

## Info

### IN-01: Behavior change — per-phase ranking now home-first instead of per-race fielding team

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:52`
**Issue:** `calculateRankingForPhase` previously resolved the per-phase team strictly from the
per-race `RaceLineup` (`resolveTeamFromLineup`); it now routes through `resolveAttributedTeam`,
which checks `SeasonDriver` **first**. A rostered driver who guests for another team in a race
will now show under their HOME team in the per-phase ranking, where before it showed the fielding
team. This is the intended D-01 policy and is covered by tests
(`givenDoppelrollenGuest_whenCalculateRankingForPhase_thenAttributedToHomeTeam`), but it is a
visible semantic change to a previously RaceLineup-only path — confirm consumers of per-phase
rankings expect home-first attribution.
**Fix:** None required if intended; noted for traceability. Ensure the per-phase standings UI/wiki
copy reflects "home team" semantics.

### IN-02: Per-phase team now rolls sub-teams up to parent (previously raw lineup team)

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:184, 189`
**Issue:** The old `resolveTeamFromLineup` returned `RaceLineup.getTeam()` verbatim (could be a
sub-team). The unified helper now applies `getParentOrSelf()` everywhere, so per-phase rankings
roll sub-teams up to the parent. Consistent with alltime/aggregate and with CLAUDE.md "parent
rollup," but it is a behavior change for the per-phase path. Verified parent rollup is desired by
tests (`givenPureGuest_whenCalculateRankingForPhase_thenAttributedToFieldingTeam` asserts parent).
**Fix:** None required; noted for traceability.

### IN-03: `DriverProfilePageGeneratorTest` is a `@SpringBootTest` without `@Tag("integration")`

**File:** `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java:43-46`
**Issue:** Per CLAUDE.md "Tag Tests by Category", Spring-context tests should carry
`@Tag("integration")`; this class is `@SpringBootTest @ActiveProfiles("dev")` but named `*Test.java`
with no tag, so it runs under Surefire fork config rather than Failsafe. This is **pre-existing**
(the class predates Phase 114; the phase only added the `givenPureGuestDriver_...` method), so it
is not a phase-114-introduced defect — flagged for visibility only. Note the new IT
(`DriverRankingServiceGuestIT`) correctly uses `@CtcDevSpringBootContext @Tag("integration")`.
**Fix:** Out of phase scope; if touched again, either tag it `@Tag("integration")` or migrate to
`@CtcDevSpringBootContext`. Do not change in this review.

### IN-04: Non-standard import ordering in `DevDataSeeder`

**File:** `src/main/java/org/ctc/admin/DevDataSeeder.java:3-10`
**Issue:** The new imports place `org.ctc.domain.model.RaceLineup` and
`org.ctc.domain.repository.RaceLineupRepository` above the `lombok.*` imports, breaking the
otherwise-alphabetical grouping used elsewhere. Not flagged by the unused-import Checkstyle gate
(only `UnusedImports`/`RedundantImport` are enforced) and purely cosmetic.
**Fix:** Reorder so `lombok.*` precedes `org.ctc.*` to match the convention, or run the project's
OpenRewrite import-ordering recipe.

---

## Resolution (2026-06-01)

All findings fixed — nothing deferred. Re-verified with `./mvnw clean verify -Pe2e`: BUILD SUCCESS, all coverage checks met, all unit/IT/E2E green.

| Finding | Resolution |
|---------|------------|
| **WR-01** | Alltime guest backfill (`DriverRankingService`) now resolves each pure guest from **their own in-scope race** (`findByRaceIdAndDriverId(result.getRace().getId(), driverId)`, smallest race id), replacing the unscoped `findByDriverId`. Team is now guaranteed in the same season set as the points. Unit test mock updated accordingly. |
| **WR-02** | Determinism: the alltime backfill picks the guest's smallest-race-id lineup; the `resolveAttributedTeam` season-scoped fallback now uses `.min(Comparator.comparing(Team::getId))` instead of `findFirst()` over an unordered query. Both selections are now stable across DBs/runs. |
| **WR-03** | Profile second pass builds a `Map<driverId, Team>` (deterministic min-parent-id) from the already eager-loaded `findByRaceMatchdaySeasonId` result, eliminating the per-guest `findByDriverIdAndRaceMatchdaySeasonId` team re-query. The bounded per-distinct-driver lookups in `resolveAttributedTeam` are correct and run only on OSIV batch paths (admin standings + site-gen), not a latency-critical request path. |
| **IN-01 / IN-02** | Intended D-01/D-03 behavior (home-first + parent rollup), test-covered. No code change; confirmed correct. |
| **IN-03** | Fixed: `DriverProfilePageGeneratorTest` renamed to `DriverProfilePageGeneratorIT` + `@Tag("integration")`. Verified it now executes under Failsafe (7 tests green) — surefire count dropped 1853→1846, confirming the move with no silent gap. |
| **IN-04** | `DevDataSeeder` imports reordered (`lombok.*` before `org.ctc.*`). |

_Reviewed: 2026-06-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Fixes applied + re-verified: 2026-06-01_
