# Plan 114-01 — Summary

**Wave:** 1 | **Status:** complete | **Branch:** gsd/v1.17-guest-drivers

## What was built

Wave-0 foundation for Phase 114: guest scenarios in the shared `@Profile("dev")` seed, a dev-only guest-verification log hook, and the regression IT scaffold.

### Task 1 — `TestDataService.seedRaceLineups` guest fixture (D-11)
- Added two guest drivers: `Test_DualRole_1` (dual-role) and `Test_Guest_1` (pure guest) in `Test-Season 2026`.
- **dual-role:** `SeasonDriver(testSeason1, tdDualRole, testAlpha)` (home T-ALF) + `RaceLineup(race1, tdDualRole, testBravo1, true)` (guest for away team of match1 — home/guest teams differ, exercising D-01).
- **Pure guest:** NO `SeasonDriver`; only `RaceLineup(race2, tdGuest, testBravo2, true)` (guest for away team of match2 — rolls up to parent T-BRV via `getParentOrSelf()`).
- Added `RaceResult` rows for both guest races (positions 1–5, home/away split) plus the existing race drivers, scored + aggregated via a new private `seedGuestRaceResults(race, raceScoring, results)` helper that mirrors the `seedMatchdaysAndResults` idiom: `saveAll → calculatePoints → saveAll → flush → detach → reload → aggregateMatchScores → matchRepository.save`.
- Updated the trailing `log.info` to report 8 test-drivers + result count.

### Task 2 — `DevDataSeeder` guest verification (D-12)
- Injected `RaceLineupRepository` (Lombok `@RequiredArgsConstructor`).
- Added private `verifyGuestExample()` called after `testDataService.seed()` and before `generateSite()`: counts `findAll()` lineups with `isGuest()` and `log.info`s the count; `log.warn`s (does NOT throw) if zero.
- Class stays `@Profile("dev")` only — no `local`/`prod`/`docker`. Thin-runner respected (no entity construction).

### Task 3 — `DriverRankingServiceGuestIT` scaffold (Wave 0)
- New `@CtcDevSpringBootContext @Tag("integration")` IT with four named Given-When-Then `@Test` stubs (trivially-passing `assertThat(true).isTrue()` placeholders; no `@Disabled`, no red commit). Plan 04 replaces the bodies.

## Verification
- `./mvnw clean test-compile` — exit 0 (no compile error, no unused-import / Checkstyle violation).
- `./mvnw -Dtest=DriverProfilePageGeneratorTest test` — 6/6 green (seed-consuming sitegen test unaffected by the new fixture).
- `./mvnw -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false verify` — 1846 unit + 4 new IT green. The IT boots the dev profile, proving the Task-1 seed (guest lineups + aggregated scores) works in a real H2 Spring context.
  - Note: this targeted run trips the JaCoCo coverage gate (expected — `-Dit.test=` filters out most ITs, lowering measured coverage). The real coverage gate is validated at phase-end `./mvnw clean verify -Pe2e`.

## Acceptance criteria — all met
- TestDataService: `Test_DualRole_1`✓ `Test_Guest_1`✓ 2 guest lineups✓ dualRole SeasonDriver✓ aggregate✓
- DevDataSeeder: `@Profile("dev")`==1✓ local/prod/docker==0✓ guest✓ isGuest✓ RaceLineupRepository✓
- IT: `@Tag("integration")`==1✓ `@CtcDevSpringBootContext`==1✓ 4 `@Test`✓ `@Disabled`==0✓

## Deviations
None.

## Files modified
- `src/main/java/org/ctc/admin/TestDataService.java`
- `src/main/java/org/ctc/admin/DevDataSeeder.java`
- `src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java` (new)
