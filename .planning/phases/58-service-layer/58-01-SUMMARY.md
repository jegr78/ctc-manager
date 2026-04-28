---
phase: 58-service-layer
plan: "01"
subsystem: domain-service
tags: [service-layer, season-phase, foundation, tdd, repository-it]
dependency_graph:
  requires: [56-model-schema-foundation, 57-data-migration]
  provides: [SeasonPhaseService, PhaseTestFixtures, SeasonPhaseRepository-finders, PhaseTeamRepository-finders]
  affects: [Wave2-StandingsService, Wave3-PlayoffService, Wave4-DriverRankingService, Wave5-SeasonManagementService]
tech_stack:
  added: [SeasonPhaseService]
  patterns: [TDD-RED/GREEN, @SpringBootTest IT-pattern, Mockito service test, D-22 magic-naming finders]
key_files:
  created:
    - src/main/java/org/ctc/domain/service/SeasonPhaseService.java
    - src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java
    - src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java
    - src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java
    - src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java
  modified:
    - src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java (finders added in 6f72a09)
    - src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java (finder added in 6f72a09)
    - src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java (finders added in 6f72a09)
    - src/test/java/org/ctc/domain/service/PhaseTestFixtures.java (created in 6f72a09)
decisions:
  - "D-02: findRegularPhase throws EntityNotFoundException fail-loud — post-V4 migration, no REGULAR phase is a bug not a normal state"
  - "D-13 override: @SpringBootTest + @ActiveProfiles(dev) + @Transactional used for repository ITs — zero @DataJpaTest precedent in codebase"
  - "D-14: BusinessRuleException thrown before INSERT if duplicate phase type detected — DB UNIQUE is belt, service guard is suspenders"
  - "D-20: REGULAR+LEAGUE auto-derives PhaseTeam from SeasonTeam; PLAYOFF/PLACEMENT/GROUPS start empty"
  - "TDD-RED commit 83f8093 precedes GREEN commit b0bb02b — gate sequence verified"
metrics:
  duration: "~25 minutes (continuation from partial wave executor)"
  completed: "2026-04-28"
---

# Phase 58 Plan 01: Service Layer Foundation Summary

**One-liner:** SeasonPhaseService foundation: findRegularPhase (D-02) + create-with-BusinessRuleException-guard (D-14) + REGULAR-LEAGUE roster auto-derivation (D-20) + 3 repository IT-tests + PhaseTestFixtures + 87.1% JaCoCo line coverage maintained.

## What Was Built

### SeasonPhaseService (NEW, 172 lines)
`src/main/java/org/ctc/domain/service/SeasonPhaseService.java`

Public API surface (all used by Wave 2-5 plans):
- `SeasonPhase findRegularPhase(UUID seasonId)` — D-02 central resolver; `@Transactional(readOnly=true)`; throws `EntityNotFoundException` on miss
- `Optional<SeasonPhase> findByType(UUID seasonId, PhaseType type)` — delegate; used by D-19 + D-25
- `SeasonPhase findById(UUID phaseId)` — throws `EntityNotFoundException` on miss
- `List<SeasonPhase> findAllPhases(UUID seasonId)` — ordered by `sortIndex`; used by D-09 + D-26
- `SeasonPhase create(UUID seasonId, PhaseType type, PhaseLayout layout, int sortIndex, String label, RaceScoring, MatchScoring, SeasonFormat, LocalDate startDate, LocalDate endDate, Integer totalRounds, int legs, Integer eventDurationMinutes)` — D-14 guard + D-20 roster init
- `SeasonPhaseGroup createGroup(UUID phaseId, String name, int sortIndex)` — group CRUD
- `PhaseTeam assignTeamToPhase(UUID phaseId, UUID teamId, UUID groupId)` — phase-team CRUD

Stereotype: `@Slf4j @Service @RequiredArgsConstructor`. Read methods: `@Transactional(readOnly=true)`. Write methods: `@Transactional`.

### Repository Finders (added in 6f72a09 — rescued partial work)

`SeasonPhaseRepository`:
- `Optional<SeasonPhase> findBySeasonIdAndPhaseType(UUID seasonId, PhaseType phaseType)`
- `List<SeasonPhase> findBySeasonIdOrderBySortIndex(UUID seasonId)`

`SeasonPhaseGroupRepository`:
- `List<SeasonPhaseGroup> findByPhaseIdOrderBySortIndex(UUID phaseId)`

`PhaseTeamRepository`:
- `List<PhaseTeam> findByPhaseId(UUID phaseId)`
- `List<PhaseTeam> findByPhaseIdAndGroupId(UUID phaseId, UUID groupId)` — null groupId derives IS NULL
- `boolean existsByPhaseSeasonId(UUID seasonId)` — D-18 delete-guard prerequisite

### PhaseTestFixtures (NEW, 99 lines — created in 6f72a09)
`src/test/java/org/ctc/domain/service/PhaseTestFixtures.java`

Static factory methods (all assign `UUID.randomUUID()` IDs for Mockito tests):
- `SeasonPhase regularPhase(Season, RaceScoring, MatchScoring)` — LEAGUE, sortIndex=0
- `SeasonPhase groupsRegularPhase(Season, RaceScoring, MatchScoring, String... groupNames)` — GROUPS with children
- `SeasonPhase playoffPhase(Season, String label, RaceScoring, MatchScoring)` — BRACKET, sortIndex=10
- `PhaseTeam assignTeam(SeasonPhase, Team, SeasonPhaseGroup)` — nullable group

## Test Coverage

### SeasonPhaseServiceTest (Mockito, 11 test methods)
`src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java`

| Method | Decision |
|--------|----------|
| `givenSeasonWithRegularPhase_whenFindRegularPhase_thenReturnsPhase` | D-02 happy path |
| `givenMissingRegularPhase_whenFindRegularPhase_thenThrowsEntityNotFound` | D-02 fail-loud |
| `givenSeasonWithPlayoffPhase_whenFindByTypePlayoff_thenReturnsOptionalOfPhase` | findByType happy path |
| `givenSeasonWithoutPlayoffPhase_whenFindByTypePlayoff_thenReturnsEmptyOptional` | findByType empty |
| `givenSeasonWithMultiplePhases_whenFindAllPhases_thenReturnsOrderedBySortIndex` | findAllPhases ordering |
| `givenExistingRegularPhase_whenCreateRegular_thenThrowsBusinessRuleException` | D-14 REGULAR guard |
| `givenExistingPlayoffPhase_whenCreatePlayoff_thenThrowsBusinessRuleException` | D-14 PLAYOFF guard |
| `givenExistingPlacementPhase_whenCreatePlacement_thenThrowsBusinessRuleException` | D-14 PLACEMENT guard |
| `givenSeasonWith3SeasonTeams_whenCreateRegularLeaguePhase_then3PhaseTeamsCreated` | D-20 auto-derive |
| `givenPlayoffType_whenCreatePlayoffPhase_thenNoPhaseTeamsCreated` | D-20 empty roster |
| `givenGroupsLayout_whenCreateRegularGroupsPhase_thenNoPhaseTeamsCreated` | D-20 GROUPS empty |

### SeasonPhaseRepositoryIT (3 test methods)
- `givenFixture_whenFindBySeasonIdAndPhaseType_thenReturnsExpected` — VALIDATION.md row 58-01-03
- `givenSeasonWithMultiplePhases_whenFindBySeasonIdOrderBySortIndex_thenReturnsOrderedList`
- `givenNoPhaseForType_whenFindBySeasonIdAndPhaseType_thenReturnsEmpty`

### SeasonPhaseGroupRepositoryIT (2 test methods)
- `givenPhaseWithGroups_whenFindByPhaseIdOrderBySortIndex_thenReturnsOrderedList`
- `givenPhaseWithNoGroups_whenFindByPhaseIdOrderBySortIndex_thenReturnsEmptyList`

### PhaseTeamRepositoryIT (4 test methods)
- `givenPhaseTeams_whenFindByPhaseId_thenReturnsAll`
- `givenLeagueRosterWithNullGroup_whenFindByPhaseIdAndGroupIdNull_thenReturnsLeagueTeams`
- `givenSeasonWithPhaseTeams_whenExistsByPhaseSeasonId_thenTrue`
- `givenSeasonWithoutPhaseTeams_whenExistsByPhaseSeasonId_thenFalse`

**Total new tests: 20** (11 Mockito + 9 IT). **Total suite: 1083 tests** (was 1063 before this plan).

## JaCoCo Coverage

- **Before (post-Wave-0 commit):** ~82% (pre-baseline at rescue commit 6f72a09)
- **After (post-GREEN):** **87.1%** (5029/5773 lines covered)
- **Gate:** >= 82% — PASSED. "All coverage checks have been met."

## Decisions Implemented

| Decision | Implementation |
|----------|----------------|
| D-02 | `findRegularPhase` throws `EntityNotFoundException("Regular SeasonPhase for season", seasonId)` — single resolution point |
| D-11 | `PhaseTestFixtures` final class with 4 static factory methods; all IDs pre-assigned for Mockito |
| D-13 (revised) | `@SpringBootTest @ActiveProfiles("dev") @Transactional` for all 3 repository ITs — zero `@DataJpaTest` in codebase |
| D-14 | `create()` calls `findBySeasonIdAndPhaseType` before INSERT; throws `BusinessRuleException("Season already has " + type + " phase")` |
| D-20 | REGULAR+LEAGUE: loop `season.getSeasonTeams()` → save `new PhaseTeam(phase, st.getTeam())`. PLAYOFF/PLACEMENT/GROUPS: no PhaseTeam rows |
| D-22 | Spring Data magic-naming used for all 6 new finders; no `@Query` JPQL needed (all resolved successfully) |

## Deviations from Plan

### Rescued Partial Work (prior executor interruption)

The previous Wave-1 executor was interrupted mid-task. Repository finders and PhaseTestFixtures were committed in `6f72a09` (wip commit) before the executor stopped. This continuation agent:
1. Verified the rescued files were correct and complete
2. Created the remaining test files (SeasonPhaseServiceTest + 3 IT files) as Task 1 (TDD-RED)
3. Created SeasonPhaseService as Task 2 (TDD-GREEN)

The wip commit (`6f72a09`) is a deviation from the standard `test(...)` + `feat(...)` commit sequence. It precedes both RED and GREEN commits but does not break the TDD gate integrity — the RED commit (`83f8093`) still predates the GREEN commit (`b0bb02b`).

### No other deviations

The plan executed exactly as written. No architectural changes were required.

## Commits

| Hash | Type | Description |
|------|------|-------------|
| `6f72a09` | wip | rescue partial work (repository finders + PhaseTestFixtures) |
| `83f8093` | test | TDD-RED — SeasonPhaseServiceTest + 3 Repository IT-scaffolds |
| `b0bb02b` | feat | SeasonPhaseService TDD-GREEN |

## Known Stubs

None. All methods have full implementations. No hardcoded empty values or placeholder text.

## Threat Flags

No new network endpoints, auth paths, file access patterns, or schema changes introduced. SeasonPhaseService is admin-only (no new HTTP surface). T-58-01-03 (race condition on duplicate REGULAR phase) is mitigated by D-14 service guard + existing DB UNIQUE constraint from Phase 56.

## Ready for Wave 2

Wave 2-5 plans can now:
- `@Autowired`/inject `SeasonPhaseService` via `@RequiredArgsConstructor`
- Use `PhaseTestFixtures.regularPhase(...)` / `playoffPhase(...)` / `groupsRegularPhase(...)` in their own Mockito tests and IT-tests
- Rely on `findRegularPhase(seasonId)` as the single REGULAR-phase resolution point (D-02)
- Use `findByType`, `findAllPhases`, `findById` in service bridges

## Self-Check: PASSED

All created files verified present on disk. All commits verified in git log.

| Check | Result |
|-------|--------|
| SeasonPhaseService.java | FOUND |
| SeasonPhaseServiceTest.java | FOUND |
| SeasonPhaseRepositoryIT.java | FOUND |
| SeasonPhaseGroupRepositoryIT.java | FOUND |
| PhaseTeamRepositoryIT.java | FOUND |
| PhaseTestFixtures.java | FOUND |
| RED commit 83f8093 | FOUND |
| GREEN commit b0bb02b | FOUND |
| JaCoCo 82% gate | PASSED (87.1%) |
| Total tests green | 1083/1083 |
