---
phase: 58-service-layer
plan: 05
subsystem: domain-service
tags: [service-layer, playoff, phase-aware, auto-create, tdd, pitfall-4]
dependency_graph:
  requires: [58-01, 58-02, 58-04]
  provides: [SVC-03, PlayoffService-phase-aware, PlayoffSeedingService-Top-N-from-REGULAR]
  affects: [PlayoffController, PlayoffRepository, PlayoffSeedRepository, MatchdayRepository]
tech_stack:
  added: []
  patterns:
    - service-to-service composition in single @Transactional boundary (Pitfall 2 atomicity)
    - find-or-create via findByType().orElseGet(create(...))
    - dual-flow autoSeedBracket — manual PlayoffSeed (legacy) priority + REGULAR-phase Top-N (D-15) fallback
    - PhaseTeam roster auto-population as side-effect of seed creation (D-20 PLAYOFF roster init)
    - @Deprecated on M:N methods kept functional until Phase 61
    - BusinessRuleException replaces IllegalArgumentException for duplicate-resource cases (D-03 consistency)
key_files:
  created: []
  modified:
    - src/main/java/org/ctc/domain/service/PlayoffService.java
    - src/main/java/org/ctc/domain/service/PlayoffSeedingService.java
    - src/main/java/org/ctc/domain/repository/PlayoffRepository.java
    - src/main/java/org/ctc/admin/controller/PlayoffController.java
    - src/test/java/org/ctc/domain/service/PlayoffServiceTest.java
    - src/test/java/org/ctc/domain/service/PlayoffSeedingServiceTest.java
decisions:
  - createPlayoff atomically writes PLAYOFF SeasonPhase + Playoff in a single @Transactional boundary (Pitfall 2 mitigation)
  - autoSeedBracket prefers manually-saved PlayoffSeed rows (legacy admin workflow) and falls back to D-15 REGULAR-phase Top-N when none exist — backward-compatible by design
  - Pitfall 4 mitigation grep-anchored — production code contains exactly one matchday.setPhase(playoff.getPhase()) call site in addRaceToMatchup
  - PlayoffController catches BusinessRuleException in save flow to preserve redirect-with-flash UX after the IllegalArgumentException → BusinessRuleException swap
requirements-completed: [SVC-03]
metrics:
  duration_seconds: 1100
  completed: 2026-04-28
  tasks_completed: 2
  files_changed: 6
---

# Phase 58 Plan 05: PlayoffService + PlayoffSeedingService Phase-Aware Summary

PlayoffService.createPlayoff auto-creates the PLAYOFF SeasonPhase atomically (D-19) and PlayoffService.addRaceToMatchup links new playoff matchdays to the PLAYOFF phase (Pitfall 4 mitigation). PlayoffSeedingService.autoSeedBracket gains a D-15 fallback that pulls Top-N from REGULAR-phase standings (combined-view across groups) when manual seeds are absent, populating the PLAYOFF phase's PhaseTeam roster as a side-effect (D-20). The legacy M:N add-/remove-Season methods are marked @Deprecated but kept functional for Pitfall 5 compatibility until Phase 61 cleanup.

## Public Surface Changes

### PlayoffService

**`createPlayoff(UUID seasonId, String name, int numberOfTeams)` — D-19 auto-create:**
- Inject `SeasonPhaseService`.
- Single `@Transactional` boundary spans the SeasonPhase + Playoff writes (Pitfall 2 atomicity).
- Find-or-create the PLAYOFF phase via `seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF).orElseGet(seasonPhaseService.create(...))`.
- Auto-create defaults: `BRACKET` layout, `LEAGUE` format (D-08 DB-default workaround), `sortIndex=10`, `legs=1`, scoring copied from `Season`.
- Sets `playoff.setPhase(phase)` before saving — playoff entity is bound to the PLAYOFF SeasonPhase from the first write.
- Duplicate guard: throws `BusinessRuleException("Season already has a playoff phase")` instead of the legacy `IllegalArgumentException("Playoff already exists for this season")` for D-03 consistency (BusinessRuleException maps to HTTP 409 via GlobalExceptionHandler).

**`addRaceToMatchup(UUID matchupId, ...)` — Pitfall 4 fix:**
- New playoff matchday created inside this method now calls `matchday.setPhase(playoff.getPhase())` before persisting. Without this, playoff race results would be misattributed to the REGULAR phase by `DriverRankingService.calculateRankingForPhase(REGULAR)` (double-counting).

**`@Deprecated addSeasonToPlayoff` / `@Deprecated removeSeasonFromPlayoff` (D-03, Pitfall 5):**
- Marked `@Deprecated` with Javadoc note pointing to Phase 61 alongside `playoff_seasons` drop.
- Kept functional — `PlayoffController` add-season / remove-season actions still work end-to-end.

### PlayoffSeedingService

**`autoSeedBracket(UUID playoffId)` — D-15 + D-20 dual-flow:**
- Inject `SeasonPhaseService`, `StandingsService`, `PhaseTeamRepository`.
- **Legacy/manual flow (priority):** When `PlayoffSeedRepository.findByPlayoffId(playoffId)` is non-empty, the manually-saved seeds drive the bracket. Preserves the pre-Phase-58 admin workflow `saveSeedNumbers(...)` → `autoSeedBracket(...)`.
- **D-15 flow (fallback):** When no manual seeds exist, derives Top-N teams from `standingsService.calculateStandings(regularPhase.getId(), null)` — combined-view across groups for GROUPS-layout REGULAR phases (D-04). Persists Top-N as new `PlayoffSeed` rows (replacing any prior ones via `deleteByPlayoffId` + flush) so subsequent `getSeedingData` calls and the PlayoffController seed UI stay coherent.
- **D-20 side-effect:** When the D-15 flow runs, each seeded team is added as a `PhaseTeam` row on the PLAYOFF phase (skipping duplicates via `phaseTeamRepository.findByPhaseId(...)` membership check). The PLAYOFF roster therefore starts empty and is populated lazily by the seeding step.
- Empty-source handling preserved: throws `IllegalStateException("No seed numbers assigned yet")` when neither manual seeds nor REGULAR-phase standings yield enough teams.

**`getSeedingData` / `saveSeed` / `saveSeedNumbers` / `seedTeam` — UNCHANGED (Pitfall 5):**
- Manual seeding UI continues to consult the M:N team pool / PlayoffSeed rows. Phase 58 deliberately avoids touching these surfaces — Phase 60 will rewire them.

### PlayoffRepository (D-22)

```java
Optional<Playoff> findBySeasonId(UUID seasonId);                 // existing — kept

Optional<Playoff> findByPhaseId(UUID phaseId);                   // NEW (D-22)
boolean existsByPhaseSeasonId(UUID seasonId);                    // NEW (D-18 prep for 58-06 delete-guard)

@Query("SELECT p FROM Playoff p JOIN p.seasons s WHERE s.id = :seasonId")
Optional<Playoff> findByLinkedSeasonId(UUID seasonId);           // existing — kept
```

### PlayoffController (regression fix)

`PlayoffController.save(...)` now catches `BusinessRuleException` alongside `IllegalArgumentException | IllegalStateException` to preserve the redirect-with-flash UX. Without this, the D-19 exception-type swap would have routed the duplicate-playoff case to `GlobalExceptionHandler` (CONFLICT 409 error page) — `PlayoffControllerTest.givenExistingPlayoff_whenSaveDuplicatePlayoff_thenRedirectsWithError` was the regression sentinel; now green.

## Test Counts

| Test Class | Before | New | After |
|---|---|---|---|
| PlayoffServiceTest | 35 | +6 | 41 |
| PlayoffSeedingServiceTest | 9 | +3 | 12 |
| **Total (Plan 58-05)** | **44** | **+9** | **53** |

All 53 tests pass. Full suite: **1117 tests, 0 failures, 0 errors** (was 1108 at end of 58-04 → +9).

## JaCoCo Coverage

| Metric | Value |
|---|---|
| Line coverage | 86.74% (5252/6055) |
| Gate threshold | 82% |
| Delta vs Wave 3 | +2.74% (84% → 86.74%) |

Coverage rose because the new tests exercise both the legacy and D-15 flows of `autoSeedBracket` and the D-19 auto-create path, which were previously near the 'partial-branch' threshold for these classes.

## Decisions Implemented

| Decision | Where | File:Line |
|---|---|---|
| **D-15** Top-N from REGULAR-phase standings (combined-view) | `PlayoffSeedingService.tryLoadFromRegularStandings` | `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java:139-185` |
| **D-19** auto-create PLAYOFF phase + BusinessRuleException duplicate guard | `PlayoffService.createPlayoff` | `src/main/java/org/ctc/domain/service/PlayoffService.java:46-83` |
| **D-20** PhaseTeam roster on PLAYOFF as side-effect of seeding | `PlayoffSeedingService.tryLoadFromRegularStandings` | `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java:172-181` |
| **D-22** PlayoffRepository phase-aware finders | `PlayoffRepository` | `src/main/java/org/ctc/domain/repository/PlayoffRepository.java:14-18` |
| **D-03 / Pitfall 5** @Deprecated on M:N methods (kept functional) | `PlayoffService` | `src/main/java/org/ctc/domain/service/PlayoffService.java:103-128` |
| **Pitfall 4** matchday.setPhase(playoff.getPhase()) on new playoff matchday | `PlayoffService.addRaceToMatchup` | `src/main/java/org/ctc/domain/service/PlayoffService.java:323-326` |

## Behavior Changes

**None for end-users.** All behavior changes are internal/structural:
- Duplicate-playoff exception type changed from `IllegalArgumentException` to `BusinessRuleException` — the user-visible flash error message is unchanged (`"Error: Season already has a playoff phase"`).
- `addRaceToMatchup` now writes `matchday.phase_id` for playoff matchdays — invisible at the UI but corrects driver-ranking attribution (Pitfall 4).
- `autoSeedBracket` gains an alternative seeding source — only triggers when manual seeds are absent.

**Still pending in Plan 58-06 (Wave 5):**
- D-18 strict delete-guard on `SeasonManagementService.delete` (BEHAVIOR CHANGE — replaces silent cascade).
- D-25 auto-sync of legacy `Season` fields to REGULAR `SeasonPhase` on save (no user-visible change but unblocks Phase 60).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] PlayoffServiceTest compile error from cfd21df rescue**
- **Found during:** Task 1 audit
- **Issue:** `cfd21df` rescue commit added 158 lines of RED tests referencing `playoffRepository.findById(...)` (4 call sites) but did not add the corresponding `@Autowired private PlayoffRepository playoffRepository` field. Test file failed to compile.
- **Fix:** Added the missing autowire next to the existing repository injections.
- **Files modified:** `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java`
- **Commit:** `95dc501`

**2. [Rule 2 - Missing critical functionality] Manual-seed flow preserved as backward-compat**
- **Found during:** Task 3 GREEN design
- **Issue:** Plan 58-05 spec said "GREEN refactor swaps the team source from M:N to REGULAR-phase standings" — but existing tests `givenSeedNumbers_whenAutoSeedBracket_thenMatchupsPopulatedCorrectly`, `givenSeededPlayoff_whenGetBracketView_thenMatchupsContainSeedNumbers`, etc. rely on the legacy `saveSeedNumbers` → `autoSeedBracket` flow with no REGULAR-phase matches played. A pure D-15 swap would break 5 existing tests.
- **Fix:** Implemented dual-flow `autoSeedBracket`: legacy/manual flow has priority (used when `PlayoffSeed` rows exist); D-15 flow is the fallback. Plan 58-05 acceptance criteria said "Existing 35 PlayoffService tests + 9 PlayoffSeedingService tests must continue to pass" — this design satisfies both.
- **Files modified:** `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java`
- **Commit:** `dd795ab`

**3. [Rule 3 - Blocking issue] PlayoffControllerTest regression after D-19 exception-type swap**
- **Found during:** `./mvnw verify` after GREEN commit drafted
- **Issue:** D-19 changed the duplicate-playoff exception from `IllegalArgumentException` to `BusinessRuleException` for D-03 consistency. PlayoffController.save catches `IllegalArgumentException | IllegalStateException` and redirects with flash error — but no longer caught BusinessRuleException, so it propagated to GlobalExceptionHandler (HTTP 409 error page). `PlayoffControllerTest.givenExistingPlayoff_whenSaveDuplicatePlayoff_thenRedirectsWithError` failed: expected 3xx redirect, got 4xx.
- **Fix:** Added `BusinessRuleException` to the catch block in PlayoffController.save with a comment pointing back to D-19.
- **Files modified:** `src/main/java/org/ctc/admin/controller/PlayoffController.java`
- **Commit:** `dd795ab`

## Pitfall 4 Verification (grep-anchored)

```
$ grep -c "matchday.setPhase(playoff.getPhase()" src/main/java/org/ctc/domain/service/PlayoffService.java
1
```
Exactly one call site, inside `addRaceToMatchup` immediately before `matchdayRepository.save(matchday)`. Test `givenPlayoffMatchup_whenAddRaceToMatchup_thenNewMatchdayLinkedToPlayoffPhase` enforces the assertion `newMatchday.getPhase().getPhaseType() == PhaseType.PLAYOFF`.

## Acceptance Criteria Walkthrough

| Criterion | Status | Evidence |
|---|---|---|
| `private final SeasonPhaseService seasonPhaseService` in PlayoffService | green | `PlayoffService.java:44` |
| `seasonPhaseService.findByType(...PhaseType.PLAYOFF)` in createPlayoff | green | `PlayoffService.java:62` |
| `seasonPhaseService.create(...)` invocation | green | `PlayoffService.java:63` |
| `playoff.setPhase(phase)` before save | green | `PlayoffService.java:78` |
| `BusinessRuleException("Season already has a playoff phase")` | green | `PlayoffService.java:54` |
| `matchday.setPhase(playoff.getPhase())` in addRaceToMatchup | green | `PlayoffService.java:325` |
| `@Deprecated` on `addSeasonToPlayoff` | green | `PlayoffService.java:104` |
| `@Deprecated` on `removeSeasonFromPlayoff` | green | `PlayoffService.java:117` |
| `seasonPhaseService` + `standingsService` injected in PlayoffSeedingService | green | `PlayoffSeedingService.java:34-35` |
| `standingsService.calculateStandings(regularPhase.getId(), null)` | green | `PlayoffSeedingService.java:153` |
| `phaseTeamRepository.save(new PhaseTeam(...))` side-effect | green | `PlayoffSeedingService.java:178` |
| `PlayoffRepository.findByPhaseId` added | green | `PlayoffRepository.java:14` |
| `PlayoffRepository.existsByPhaseSeasonId` added | green | `PlayoffRepository.java:17` |
| `./mvnw verify` exits 0 | green | 1117 tests pass |
| JaCoCo line coverage ≥ 82% | green | 86.74% |
| VALIDATION.md rows 58-05-01..04 marked green | green | all 4 RED tests now passing |

## Known Stubs

None. PlayoffSeedingService.tryLoadFromRegularStandings returns `null` (sentinel for "fall back to manual seeds") — this is intentional flow control, not a stub.

## Threat Flags

No new threat surfaces introduced. T-58-05-01..05 from PLAN.md threat model all mitigated as planned (single @Transactional boundary, combined-view standings, matchday.setPhase(playoff.getPhase()), @Deprecated kept functional).

## Next Up

**Plan 58-06 (Wave 5):** SeasonManagementService delete-guard (D-18 BEHAVIOR CHANGE) + auto-sync (D-25) + MatchdayService dual-API (D-26) + SiteGenerator phase-aware (D-23) + new SiteGeneratorServiceIT.

After 58-06: gsd-verifier runs phase-wide check, then transition to Phase 59.

## TDD Gate Compliance

- **RED gate:** `test(58-05): complete TDD-RED tests` — commit `95dc501` (also superseding partial RED `cfd21df`).
- **GREEN gate:** `feat(58-05): refactor PlayoffService + PlayoffSeedingService to phase-aware` — commit `dd795ab`.
- **REFACTOR gate:** Not needed — GREEN code is idiomatic on first pass.

## Self-Check: PASSED

| Check | Result |
|---|---|
| `src/main/java/org/ctc/domain/service/PlayoffService.java` exists | FOUND |
| `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` exists | FOUND |
| `src/main/java/org/ctc/domain/repository/PlayoffRepository.java` exists | FOUND |
| `src/main/java/org/ctc/admin/controller/PlayoffController.java` exists | FOUND |
| `src/test/java/org/ctc/domain/service/PlayoffServiceTest.java` exists | FOUND |
| `src/test/java/org/ctc/domain/service/PlayoffSeedingServiceTest.java` exists | FOUND |
| `.planning/phases/58-service-layer/58-05-SUMMARY.md` exists | FOUND |
| RED commit 95dc501 exists | FOUND |
| GREEN commit dd795ab exists | FOUND |
| Partial-RED rescue commit cfd21df exists (history preserved) | FOUND |
| 1117 tests pass | CONFIRMED |
| JaCoCo line coverage 86.74% ≥ 82% | CONFIRMED |
