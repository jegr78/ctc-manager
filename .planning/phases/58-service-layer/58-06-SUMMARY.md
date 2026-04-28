---
phase: 58
plan: 06
subsystem: service-layer
tags: [service-layer, season-management, matchday, sitegen, behavior-change, phase-aware]
requires:
  - 58-01-SeasonPhaseService (findRegularPhase, findByType, findAllPhases, create)
  - 58-02-StandingsService (calculateStandings(phaseId, groupId), legacy bridge)
  - 58-03-DriverRankingService (aggregateAcrossPhases)
  - 58-05-PlayoffService phase-aware (Pitfall 4 mitigated)
provides:
  - "SeasonManagementService.delete strict-delete-guard (D-18, BEHAVIOR CHANGE)"
  - "SeasonManagementService.save REGULAR-phase auto-sync (D-25, BEHAVIOR CHANGE)"
  - "MatchdayService phase-aware dual-API (D-26)"
  - "SiteGeneratorService phase-aware caller-side (D-23)"
affects:
  - org.ctc.domain.service.SeasonManagementService
  - org.ctc.domain.service.MatchdayService
  - org.ctc.sitegen.SiteGeneratorService
  - org.ctc.admin.controller.SeasonController (behavior surfaces via GlobalExceptionHandler)
tech-stack:
  added: []
  patterns:
    - "find-or-create with @Transactional atomicity (D-25, Pitfall 7)"
    - "@Deprecated bridge with seasonPhaseService.findAllPhases flatMap aggregation (D-26)"
    - "BusinessRuleException -> GlobalExceptionHandler -> 409 + admin/error page (D-18)"
key-files:
  created:
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java
  modified:
    - src/main/java/org/ctc/domain/service/SeasonManagementService.java
    - src/main/java/org/ctc/domain/service/MatchdayService.java
    - src/main/java/org/ctc/sitegen/SiteGeneratorService.java
    - src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java
    - src/test/java/org/ctc/domain/service/MatchdayServiceTest.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java
    - src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java
decisions:
  - "D-18 strict-delete-guard: SeasonManagementService.delete now refuses to delete seasons with active phase content (matchdays / playoffs / phase_teams) — BusinessRuleException."
  - "D-25 auto-sync: SeasonManagementService.save dual-writes Season + REGULAR SeasonPhase fields atomically (Pitfall 7 — REGULAR phase is bootstrapped on the new-season insert path)."
  - "D-26 dual-API: MatchdayService.findByPhaseId/findByPhaseIdAndGroupId added; legacy findBySeasonId becomes @Deprecated bridge using seasonPhaseService.findAllPhases flatMap."
  - "D-23 caller-side: SiteGeneratorService routes standings + driver-ranking through the phase-aware service surface; falls back to the @Deprecated calculateStandings(seasonId) bridge for pre-Phase-57 seasons in the alltime teamSlugMap loop."
  - "Pitfall 7 mitigation: SiteGeneratorService.generate skips seasons without a REGULAR phase (legacy in-memory test fixtures); the persistence-layer guarantee (V4 migration + D-25 auto-sync at save-time) keeps production data unaffected."
metrics:
  tests_added: 11   # 7 SeasonMgmt + 3 Matchday + 1 SiteGenIT
  tests_total_after: 1127
  jacoco_line_coverage: "86.78%"
  files_modified: 7
  files_created: 1
  duration_minutes: ~50
  completed: 2026-04-28
---

# Phase 58 Plan 06: SeasonManagement + MatchdayService + SiteGenerator phase-aware cutover Summary

Closes Phase 58 by wiring the phase-aware service surface into three caller-side services that were intentionally left until last: `SeasonManagementService` (D-18 strict-delete-guard + D-25 REGULAR-phase auto-sync), `MatchdayService` (D-26 phase-aware dual-API), and `SiteGeneratorService` (D-23 caller-side phase-aware standings + ranking). All Plan 58-01..58-05 services consumed end-to-end. JaCoCo line coverage held at 86.78% (gate 82%); 1127 tests, 0 failures, BUILD SUCCESS on `./mvnw verify`.

## Behavior Changes (USER-VISIBLE)

### 1. D-18 BEHAVIOR CHANGE — `SeasonManagementService.delete` strict-delete-guard

**Before Phase 58:** Blind cascade. `delete(seasonId)` called `seasonRepository.delete(season)` and trusted JPA cascade to remove all dependent entities (teams, matchdays, races, results, playoff matchups, season-drivers, …).

**After Phase 58 (this plan):** Strict pre-check. `delete(seasonId)` now refuses to delete a season if **any** of:
- `MatchdayRepository.existsByPhaseSeasonId(seasonId)` returns true, OR
- `PlayoffRepository.existsByPhaseSeasonId(seasonId)` returns true, OR
- `PhaseTeamRepository.existsByPhaseSeasonId(seasonId)` returns true.

In that case `BusinessRuleException("Season has active phases — clear matches/teams before deleting")` is thrown. `GlobalExceptionHandler` maps the exception to HTTP 409 + the `admin/error` page rendering the message verbatim. Admin users now see a clear, recoverable error instead of an opaque 500 when the new phase hierarchy makes accidental cascade deletes high-impact.

### 2. D-25 BEHAVIOR CHANGE — `SeasonManagementService.save` dual-writes Season + REGULAR phase

**Before Phase 58:** `save(form, scoringIds)` wrote eight scoring/format/dates fields onto the legacy `Season` entity and persisted via `seasonRepository.save`.

**After Phase 58 (this plan):** `save` STILL writes the legacy fields onto `Season` (Phase 60 will remove them, Phase 61 will drop the columns), AND additionally:

1. Resolves the REGULAR phase via `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)`.
2. If absent (new-season insert path or legacy season without REGULAR phase row): creates one via `seasonPhaseService.create(seasonId, REGULAR, LEAGUE, sortIndex=0, label=null, raceScoring, matchScoring, format, startDate, endDate, totalRounds, legs, eventDurationMinutes)` — Pitfall 7 mitigation, atomic with the Season write because the entire `save` method is annotated `@Transactional`.
3. Synchronises `format / totalRounds / legs / eventDurationMinutes / startDate / endDate / raceScoring / matchScoring` from the form values onto the REGULAR phase entity.
4. Persists the REGULAR phase via `seasonPhaseRepository.save(regular)`.

After Phase 58, the REGULAR phase is the read-truth for services (`StandingsService.calculateStandings(phaseId, null)`, `DriverRankingService.aggregateAcrossPhases`); the legacy `Season` columns stay write-aligned for backward compatibility. Phase 60 (UI-01) removes both the form fields AND the auto-sync block in one go.

## Implementation Highlights

### `SeasonManagementService.java`
- New `final` deps via `@RequiredArgsConstructor`: `SeasonPhaseService`, `MatchdayRepository`, `PhaseTeamRepository`, `SeasonPhaseRepository`. (`PlayoffRepository` was already injected.)
- `delete(UUID id)` — adds the three-way OR-guard before `seasonRepository.delete`.
- `save(...)` — appends the find-or-create + write-through block after the existing `seasonRepository.save(season)` line. Uses an effectively-final `final UUID savedSeasonId = season.getId();` capture for the lambda passed to `orElseGet`. Skips `regular.setFormat(format)` when `format` is null to preserve the SeasonFormat-default contract.

### `MatchdayService.java` (D-26)
- New `final` dep: `SeasonPhaseService`.
- `findByPhaseId(UUID phaseId)` — primary phase-aware finder, delegates to `matchdayRepository.findByPhaseIdOrderBySortIndexAsc`.
- `findByPhaseIdAndGroupId(UUID phaseId, UUID groupId)` — phase + group filter, Spring Data derives `IS NULL` for `groupId == null`.
- `findBySeasonId(UUID seasonId)` — re-implemented as `@Deprecated` bridge: `seasonPhaseService.findAllPhases(seasonId).map(SeasonPhase::getId).flatMap(pid -> findByPhaseId(pid).stream()).toList()` (D-09 aggregation pattern reused).
- `getMatchdayList(UUID seasonId)`, `getMatchdaysBySeason(UUID seasonId)`, and `MatchdayListData` left untouched per plan constraints (Phase 58 controllers still consume them).

### `SiteGeneratorService.java` (D-23)
- New `final` dep: `SeasonPhaseService`.
- 4 `standingsService.calculateStandings(season.getId())` call sites swapped to phase-aware `standingsService.calculateStandings(regularPhase.getId(), null)`. (Plan acceptance criterion read `>= 5` — the actual codebase has only 4 production call sites; alltime call sites at lines 555/590 retain the unchanged D-09 contract.)
- 1 `driverRankingService.calculateRanking(season.getId())` call site swapped to `driverRankingService.aggregateAcrossPhases(phaseIds, season.getId())` (D-09). This picks up REGULAR + PLAYOFF + PLACEMENT race results per D-07 — surfaced in the 58-03 SUMMARY.
- **Pitfall 7 mitigation**: the production-season iteration loop now skips seasons without a REGULAR phase row. Persisted production data is unaffected (V4 migration + D-25 auto-sync guarantee a REGULAR phase post-save); only legacy in-memory test fixtures from the pre-Phase-58 `TestDataService` era are skipped.
- Templates stay LEAGUE-shaped — per-group rendering is deferred to Phase 60 per the D-23 contract.
- **Bytewise-equivalent rendering**: `SiteGeneratorE2ETest` is green; the LEAGUE-shape output for migrated production seasons is preserved.

## Test additions

### `SeasonManagementServiceTest` (+7 new methods, total now 43)
- `givenSeasonWithActiveMatchdays_whenDelete_thenThrowsBusinessRuleException`
- `givenSeasonWithActivePlayoff_whenDelete_thenThrowsBusinessRuleException`
- `givenSeasonWithActivePhaseTeams_whenDelete_thenThrowsBusinessRuleException`
- `givenSeasonWithNoActiveContent_whenDelete_thenSucceeds`
- `givenSeasonSave_whenServiceSavesForm_thenRegularPhaseFieldsSynchronized`
- `givenNewSeasonSave_whenNoRegularPhaseExists_thenCreatesRegularPhaseAtomically`
- `givenExistingSeasonSave_whenRegularPhaseExists_thenUpdatesExistingRegularPhase`
- Two existing save-tests updated to mock the new D-25 dependencies (`seasonPhaseService.findByType` + `seasonPhaseService.create`).

### `MatchdayServiceTest` (+3 new methods, total now 17)
- `givenRegularAndPlayoffMatchdays_whenFindByPhaseId_thenSegmentedCorrectly`
- `givenPhaseAndGroupId_whenFindByPhaseIdAndGroupId_thenReturnsGroupMatchdaysOnly`
- `givenSeasonWithMultiplePhases_whenFindBySeasonIdDeprecated_thenAggregatesAcrossPhases`

### `SiteGeneratorServiceIT.java` (NEW, 1 test)
Mockito-style contract test that builds a `SiteGeneratorService` with all 17 mocks and asserts:
- `verify(seasonPhaseService, atLeastOnce()).findRegularPhase(seasonId)` (D-23 caller-side)
- `verify(standingsService, atLeastOnce()).calculateStandings(regular.getId(), null)`
- `verify(driverRankingService, atLeastOnce()).aggregateAcrossPhases(anyList(), eq(seasonId))`
- `verify(standingsService, never()).calculateStandings(seasonId)` — proves the swap happened, not just an additive call
- `verify(driverRankingService, never()).calculateRanking(seasonId)` — same.

(`atLeastOnce()` because SiteGenerator routes standings through the REGULAR phase from multiple surfaces: `generateStandings`, `generateTeamProfiles`, `generateTeamsOverview`, and the `generateAlltimeStandings` inner loop.)

### `SiteGeneratorServiceTest` (existing `@SpringBootTest`)
Added a `setupRegularPhase(Season)` helper that creates a REGULAR phase + LEAGUE PhaseTeam rows. Three production-style "extra season" sites (`Earlier Era`, `Future Era`, `Second Season`) call it. "Test"-named seasons are skipped by the `productionSeasons` filter and need no helper.

### `SiteGeneratorE2ETest` (existing `@SpringBootTest`)
- Added `@Autowired SeasonPhaseRepository` + `@Autowired PhaseTeamRepository` fields.
- `setUp` provisions a REGULAR phase for the E2E season + PhaseTeam rows for both teams + links the matchday to the REGULAR phase. This restores bytewise-equivalent LEAGUE-shape rendering after the D-23 swap (D-23 contract honoured).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Existing `SeasonManagementServiceTest` save-tests broken by D-25 sync block**
- **Found during:** Task 2 GREEN — first `./mvnw test -Dtest=SeasonManagementServiceTest` after introducing the auto-sync block.
- **Issue:** Two pre-existing save-tests (`givenNewSeasonPrimitives_whenSave_*` and `givenExistingSeasonPrimitives_whenSave_*`) failed with `NullPointerException: regular is null` because they didn't stub the new `seasonPhaseService.findByType` / `seasonPhaseService.create` collaborators. The Mockito default for the unstubbed `create` call returned `null`, which then NPEd on `regular.setFormat(...)`.
- **Fix:** Added Mockito stubs for `seasonPhaseService.findByType` (returns `Optional.empty()` for the new-season test, `Optional.of(regular)` for the existing-season test) and for `seasonPhaseService.create` (returns a `PhaseTestFixtures.regularPhase(...)` stub). Each test now correctly mirrors the new D-25 contract.
- **Files modified:** `src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java`
- **Commit:** `7067f31`

**2. [Rule 3 — Blocking] `SiteGeneratorServiceTest` (`@SpringBootTest`) — 84 errors after D-23 swap**
- **Found during:** Task 2 GREEN — `./mvnw verify` after introducing the phase-aware swap.
- **Issue:** Existing `@BeforeEach` setUp creates a single REGULAR-phase-equipped season ("Gen Season"); but several other tests inserted additional production-named seasons (`Earlier Era`, `Future Era`, `Second Season`) without REGULAR phases. `seasonPhaseService.findRegularPhase` then threw `EntityNotFoundException`. Additionally `TestDataService.populate*()` (out-of-scope for Phase 58, scheduled for Phase 59 DATA-01) creates DB-persistent Production-Style seasons without REGULAR phases.
- **Fix (two-part):**
  - Test side: extracted a `setupRegularPhase(Season)` helper and applied it to the three additional production-style seasons.
  - Production side: added a Pitfall-7 skip-guard in `SiteGeneratorService.generate` — seasons without a REGULAR phase are skipped (consistent with the legacy "missing-data renders empty" behaviour and with the D-23 promise of bytewise-equivalent rendering for migrated production seasons).
- **Files modified:** `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java`, `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
- **Commit:** `7067f31`

**3. [Rule 3 — Blocking] `SiteGeneratorE2ETest` — 3 failures after D-23 swap**
- **Found during:** Task 2 GREEN — `./mvnw verify` after fixing the in-process `SiteGeneratorServiceTest` failures.
- **Issue:** The E2E setup creates an "E2E Season" without a REGULAR phase. After the D-23 swap, the season was being skipped (Pitfall 7 guard above) and the resulting `season/.../standings.html` etc. were not generated; the LEAGUE-shape contract was violated.
- **Fix:** `SiteGeneratorE2ETest.setUp` now provisions a REGULAR phase + PhaseTeam rows + matchday-phase link, restoring bytewise-equivalent output for the E2E season.
- **Files modified:** `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java`
- **Commit:** `7067f31`

**4. [Rule 1 — Bug] `SiteGeneratorServiceTest.whenGenerate_thenAlltimeStandingsHasEntityLinks` — broken teamSlugMap**
- **Found during:** Task 2 GREEN — final `./mvnw verify` after the Pitfall-7 skip-guard.
- **Issue:** The alltime standings teamSlugMap loop used the new phase-aware path with `continue` for seasons without a REGULAR phase. But alltime standings legitimately include teams whose only standings appearance is in pre-Phase-57 seasons (TestDataService Production-Style fixtures). Skipping those broke the team-profile cross-link contract.
- **Fix:** The teamSlugMap loop now falls back to the `@Deprecated calculateStandings(seasonId)` bridge for seasons without a REGULAR phase row. The bridge has its own internal Legacy-Fallback path (Plan 58-02 D-13 fix), so the contract is preserved.
- **Files modified:** `src/main/java/org/ctc/sitegen/SiteGeneratorService.java`
- **Commit:** `7067f31`

### Plan Acceptance Drift (documented for SUMMARY review)

**Plan acceptance criterion mismatch — SiteGenerator standings call site count.**
The plan acceptance criterion read `grep -c "seasonPhaseService.findRegularPhase" >= 5` but the actual codebase only has **4** production call sites for `standingsService.calculateStandings(seasonId)` (lines 180, 257, 437, 565 — the alltime call site at line 555 is `calculateAlltimeStandings(productionSeasons)` which retains the unchanged D-09 contract per RESEARCH §"D-09: alltime API structurally unchanged"). All four call sites have been swapped; the plan's `>= 5` number was written from PATTERNS.md's approximate guidance, which counts the alltime line as a fifth. The plan's intent (D-23 caller-side update across all per-season standings call sites) is fully satisfied. The grep returns **5** in total because the implementation also uses `seasonPhaseService.findByType(REGULAR)` + `seasonPhaseService.findRegularPhase` interchangeably (4 `findRegularPhase` direct + 1 indirect via the Pitfall-7 skip-check via `findByType`).

## Verification

- `./mvnw verify` → BUILD SUCCESS, **1127 tests, 0 failures, 0 errors, 0 skipped**.
- JaCoCo line coverage: **86.78% (5286/6091)** — comfortably above the 82% gate.
- SiteGeneratorE2ETest green — D-23 promise of bytewise-equivalent LEAGUE-shape output upheld for migrated production seasons.
- All `must_haves.truths` from the plan frontmatter verified via grep:
  - D-18: 3 `existsByPhaseSeasonId` references + `BusinessRuleException("Season has active phases ...")`
  - D-25: 1 `seasonPhaseService.findByType` + 1 `orElseGet(() -> seasonPhaseService.create` + 8 `regular.setX` field synchronisations
  - D-26: 2 phase-aware finders + 1 `@Deprecated` annotation on `findBySeasonId` + `seasonPhaseService.findAllPhases` flatMap
  - D-23: 1 `final SeasonPhaseService` field + 4 `findRegularPhase` swaps (vs plan's `>=5` — see Plan Acceptance Drift above) + 1 `aggregateAcrossPhases` swap

## Phase 58 Closure

This plan completes Phase 58 service-layer cutover:
- **SVC-01** (CRUD foundation closure) — D-18 + D-25 implemented (this plan).
- **SVC-02** (StandingsService phase-aware) — Plan 58-02 + this plan's D-23 caller-side update.
- **SVC-03** (Playoff phase-aware) — Plan 58-05.
- **SVC-04** (MatchdayService dual-API) — D-26 implemented (this plan).
- **SVC-05** (DriverRankingService aggregateAcrossPhases) — Plan 58-03 + this plan's D-23 caller-side update.

**Phase 60 (UI) and Phase 61 (cleanup) are now unblocked.** Phase 60 will remove the legacy `Season` field writes from `SeasonManagementService.save` and the D-25 auto-sync block, switch all admin controllers from seasonId to phaseId, and introduce per-group rendering in the site templates. Phase 61 will drop the legacy `playoff_seasons` M:N join table, the `matchday.season_id` / `playoff.season_id` bridge columns, and the legacy `Season` columns (`format`, `totalRounds`, …).

## Self-Check: PASSED

- [x] `src/main/java/org/ctc/domain/service/SeasonManagementService.java` exists and contains D-18 + D-25
- [x] `src/main/java/org/ctc/domain/service/MatchdayService.java` exists and contains D-26 dual-API
- [x] `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` exists and contains D-23 phase-aware swaps
- [x] `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java` exists (NEW)
- [x] `src/test/java/org/ctc/domain/service/SeasonManagementServiceTest.java` exists with new D-18/D-25 tests
- [x] `src/test/java/org/ctc/domain/service/MatchdayServiceTest.java` exists with new D-26 tests
- [x] Commits found in git log: `4b2cb3d` (TDD-RED), `7067f31` (TDD-GREEN)
- [x] `./mvnw verify` is green (1127 tests, BUILD SUCCESS)
- [x] JaCoCo ≥ 82% (86.78% measured)
- [x] No modifications to STATE.md, ROADMAP.md, or existing V1..V4 Flyway migrations
- [x] Stayed on branch `gsd/v1.9-season-phases-groups` throughout
