# Phase 58: Service Layer - Research

**Researched:** 2026-04-27
**Domain:** Spring Boot 4 / JPA service-layer refactor on the new SeasonPhase / SeasonPhaseGroup / PhaseTeam model
**Confidence:** HIGH (codebase-internal evidence)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**API cutover (D-01..D-03):**
- D-01: phaseId-only canonical signatures with seasonId-overload bridge — overloads are 1-line delegates via `seasonPhaseService.findRegularPhase(seasonId).getId()`.
- D-02: Resolution centralized in `SeasonPhaseService.findRegularPhase(UUID)` — throws `EntityNotFoundException` on missing.
- D-03: `@Deprecated` annotation on every transitional API; M:N `addSeasonToPlayoff`/`removeSeasonFromPlayoff` stays functional but deprecated.

**Combined-view Standings (D-04..D-06):**
- D-04: Combined-view = flat `List<TeamStanding>` of raw points, sorted by `points → pointDifference → pointsFor`.
- D-05: `TeamStanding` gains nullable `SeasonPhaseGroup group` field (single return type for LEAGUE + GROUPS).
- D-06: Buchholz is per-group only; combined-view ignores it as tiebreaker.

**Driver-Ranking aggregation (D-07..D-10):**
- D-07: All phase types (REGULAR + PLAYOFF + PLACEMENT) count in season-wide aggregation. **Behavior change vs today.**
- D-08: Driver-team for season-wide aggregation = REGULAR-phase team.
- D-09: API split — `calculateRankingForPhase(phaseId)` primary + `aggregateAcrossPhases(phaseIds, seasonId)` named aggregation method + `@Deprecated calculateRanking(seasonId)` bridge.
- D-10: RaceLineup-fallback for stand-ins without REGULAR-phase `PhaseTeam`.

**Test fixtures (D-11..D-13):**
- D-11: New `PhaseTestFixtures` helper class.
- D-12: Mockito-only service tests.
- D-13: `@DataJpaTest` per new repository.

**SeasonPhaseService CRUD (D-14):** Service pre-checks "max 1 REGULAR/PLAYOFF/PLACEMENT" rule before INSERT (belt + suspenders to DB UNIQUE).

**Playoff seeding pool (D-15):** `autoSeedBracket` pulls from REGULAR-phase standings (combined-view for GROUPS layout).

**Matchday + Swiss group granularity (D-16, D-17):**
- D-16: MatchdayGenerator works per group; phaseId required, groupId required at GROUPS layout, NULL at LEAGUE.
- D-17: SwissPairingService fully per-group-isolated (own bye list, current round, progress per group).

**Season delete-cascade (D-18):** `SeasonManagementService.delete` enforces strict pre-check; throws `BusinessRuleException` on active matchdays/playoff/phase_teams. **Behavior change vs today.**

**Playoff-phase auto-creation (D-19):** `playoffService.createPlayoff(seasonId, ...)` auto-creates PLAYOFF phase via `seasonPhaseService.create`; throws `BusinessRuleException` on duplicate.

**PhaseTeam roster init (D-20):** REGULAR-LEAGUE auto-derives from `SeasonTeam`; PLAYOFF/PLACEMENT/REGULAR-GROUPS start empty.

**SwissPairing bye in GROUPS (D-21):** Per-group isolation extends to bye-handling; no phase-level convenience.

**Repository finder naming (D-22):** Spring-Data magic-naming preferred; `@Query` JPQL fallback only when ambiguous, JOIN FETCH needed, or method name > ~60 chars.

**SiteGenerator phase update (D-23):** SiteGenerator brought along; per-group standings NOT yet rendered (deferred to Phase 60).

**EntityGraph strategy (D-24):** Pragmatic — `@EntityGraph` only when measurable N+1 found.

**SeasonManagementService.save Form-cleanup (D-25):** Status-quo writes + auto-sync to REGULAR phase. Phase 60 removes form fields and sync-block in one go.

**MatchdayService phase-filter (D-26):** Both `seasonId`- and `phaseId`-shaped methods exist in parallel; controllers stay on `seasonId`.

### Claude's Discretion
- Exact `MatchdayGeneratorService.GeneratorFormData` shape (`SeasonPhase phase` only vs. carry both `Season` and `SeasonPhase`).
- `aggregateAcrossPhases` placement: Java interface default-method vs. plain class method.
- "Active matchdays/playoff/phase-team" detection inside Season.delete guard.
- Order of service migration within the phase.
- `findByRaceMatchdayPhaseId` magic-name vs. explicit `@Query`.
- Test-method count per service.

### Deferred Ideas (OUT OF SCOPE)
- Per-group playoff brackets (`PLAYOFF-FUT-01`).
- Controller cutover to phase-aware URL params — Phase 60.
- `SeasonForm` slimming — Phase 60 (UI-01).
- `TestDataService` rebuild — Phase 59 (DATA-01).
- `DevDataSeeder` rebuild — Phase 59 (DATA-02).
- Driver-import refactor — Phase 59 (IMPORT-01..04).
- Drop of M:N `playoff_seasons`, `Playoff.seasons`, legacy Season columns — Phase 61 (MIGR-06).
- E2E test for full GROUPS-season workflow — Phase 61 (QUAL-02).
- PLACEMENT-phase concrete behavior — only stub support (D-07 includes PLACEMENT in aggregation; D-14 enforces "max 1").
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SVC-01 | Neuer `SeasonPhaseService` mit Phase-/Group-CRUD und Roster-Management via `PhaseTeam` | New service file; D-14 (duplicate guard); D-20 (roster init); custom finders D-22; CRUD pattern from `SeasonManagementService` (lines 117-162) |
| SVC-02 | `StandingsService.calculateStandings(...)` auf `phaseId`/`groupId` umgestellt; Combined-View-Aggregation über Sub-Gruppen | Existing service `StandingsService.java` (lines 27-53); D-04 (flat list); D-05 (nullable group field on TeamStanding); D-06 (Buchholz per-group only) |
| SVC-03 | `PlayoffService` + `PlayoffSeedingService` operieren auf PLAYOFF-Phase statt Saison | Existing services `PlayoffService.java` + `PlayoffSeedingService.java`; D-19 (auto-create PLAYOFF phase); D-15 (Top-N from REGULAR standings); existing `PlayoffRepository.findBySeasonId` stays as deprecated bridge |
| SVC-04 | `MatchdayGeneratorService` + `SwissPairingService` phase-/group-aware | Existing services; D-16 (generator per group); D-17 (Swiss per group); needs new `MatchdayRepository.findByPhaseIdOrderBySortIndex` and `findByPhaseIdAndGroupIdOrderBySortIndex` |
| SVC-05 | `DriverRankingService` phase-/group-aware (mit Aggregation über Saison) | Existing service `DriverRankingService.java`; D-07 (all-phase aggregation); D-09 (API split); D-10 (RaceLineup fallback for stand-ins); needs `RaceResultRepository.findByRaceMatchdayPhaseId` + `findByRacePlayoffMatchupRoundPlayoffPhaseId` |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

The planner MUST verify each plan honours these binding directives:

1. **Communication:** German for chat with the user. **Code, comments, tests, UI texts, and commit messages: English only.**
2. **Coverage gate:** JaCoCo line coverage ≥ 82% — measured at BUNDLE level (`pom.xml`). New services raise the absolute number of lines; new tests must keep the ratio above 0.82.
3. **Flyway:** Phase 58 ships **NO new SQL migrations**. The schema is frozen as V1+V2+V3+V4 (Phase 56+57). `[VERIFIED: ROADMAP.md, REQUIREMENTS.md SVC-01..SVC-05]`
4. **OSIV stays enabled.** New services may use `@EntityGraph` for N+1 mitigation. No lazy-init workarounds allowed.
5. **No breaking endpoint changes.** `StandingsController`, `PlayoffController`, `MatchdayController`, `SeasonController` URLs and form-binding stay unchanged in Phase 58 — UI cutover is Phase 60.
6. **TDD mandatory.** Tests precede implementation. `givenX_whenY_thenZ` BDD naming.
7. **Keep controllers thin.** New behavior lives in services. New UI controllers are Phase 60.
8. **DTOs (not entities) on POST.** Phase 58 doesn't add new POST surfaces, so this only matters if a plan needs an internal `*Form` for `SeasonPhaseService.create()` parameters — record-shape preferred.
9. **No fallback calculations.** D-02's "throw `EntityNotFoundException` on missing REGULAR phase" is the canonical example. Don't silently skip or substitute.
10. **RaceLineup is Source of Truth** for driver-team assignments — D-10 codifies this for stand-ins.
11. **No inline styles, but Phase 58 is backend-only — N/A here.**
12. **Subagent rules:** when implementation is dispatched to subagents, prompts must name the active branch and forbid `git stash`/`git checkout`/`git reset`. Atomic per-task scope.

## Summary

Phase 58 cuts six domain services over from the flat `Season → Matchday/Playoff` model to the new three-level `Season → SeasonPhase → SeasonPhaseGroup` hierarchy backed by `PhaseTeam` rosters. Phase 56 already shipped the entities (`SeasonPhase`, `SeasonPhaseGroup`, `PhaseTeam`) plus parallel-additive bidirectional fields (`Season.phases`, `Matchday.phase`, `Matchday.group`, `Playoff.phase`); Phase 57 backfilled all production data and flipped `matchdays.phase_id` and `playoffs.phase_id` to NOT NULL. Phase 58 ships:

- **One new service** (`SeasonPhaseService` with CRUD + roster operations + the canonical resolver `findRegularPhase`).
- **Six refactored services** with phaseId-canonical signatures and seasonId-overload bridges (`StandingsService`, `PlayoffService`, `PlayoffSeedingService`, `MatchdayGeneratorService`, `SwissPairingService`, `DriverRankingService`).
- **Two integration-affected services** (`SeasonManagementService.save` auto-syncs to REGULAR phase per D-25; `SeasonManagementService.delete` enforces strict guard per D-18; `MatchdayService` adds `findByPhaseId` API).
- **One caller-side update** (`SiteGeneratorService` routes through `seasonPhaseService.findRegularPhase` per D-23).
- **Custom Spring Data finders** on the three new repositories plus two new finders on `RaceResultRepository` and `MatchdayRepository`.

The behavior changes vs today are intentional and locked: D-07 widens driver-ranking source to include PLAYOFF results (today they're invisible because `findByRaceMatchdaySeasonId` only catches Matchday-linked races); D-18 introduces a delete-guard where today there's none. Both must surface in tests and in the SUMMARY at execute time.

**Primary recommendation:** Wave the work as `SeasonPhaseService` first (foundational dependency), then `StandingsService` + `DriverRankingService` + `MatchdayGenerator/SwissPairing` in parallel, then `PlayoffService`/`PlayoffSeedingService` (depends on Standings + SeasonPhaseService), then SeasonManagementService caller-side updates + SiteGenerator + MatchdayService dual-API.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Phase/Group CRUD + roster management | API/Backend (Service) | Repository | New `SeasonPhaseService` is pure domain logic; templates/controllers added in Phase 60 [CITED: CONTEXT.md D-14, D-20] |
| REGULAR-phase resolution from seasonId | API/Backend (Service) | — | D-02 centralises in `SeasonPhaseService.findRegularPhase` — only this method talks to `SeasonPhaseRepository.findBySeasonIdAndPhaseType` for REGULAR lookup [CITED: D-02] |
| Per-phase / per-group standings calculation | API/Backend (Service) | Repository | `StandingsService` is the canonical consumer of phase-/group-aware data; no UI changes in Phase 58 [CITED: CONTEXT.md D-04..D-06] |
| Phase-aware match-data queries | Repository | API/Backend | `MatchRepository`, `RaceRepository`, `RaceResultRepository`, `MatchdayRepository`, `PlayoffRepository` get new finders; services consume them [CITED: CONTEXT.md D-22] |
| PLAYOFF-phase auto-creation | API/Backend (Service) | — | `playoffService.createPlayoff` writes to `SeasonPhase` + `Playoff` in one transaction — single `@Transactional` boundary [CITED: D-19] |
| Roster auto-derivation (REGULAR-LEAGUE) | API/Backend (Service) | — | `SeasonPhaseService.create` writes `PhaseTeam` rows from `SeasonTeam`; encapsulated in service [CITED: D-20] |
| Top-N seed pool for playoff bracket | API/Backend (Service-to-service) | — | `PlayoffSeedingService.autoSeedBracket` calls `StandingsService.calculateStandings(regularPhaseId, null)` and `seasonPhaseService.findRegularPhase` [CITED: D-15] |
| Static-site standings/rankings rendering | API/Backend (`org.ctc.sitegen`) | Service | `SiteGeneratorService` calls phase-aware service APIs; templates stay LEAGUE-shaped (no per-group rendering) [CITED: D-23] |
| Save-time data sync (Season ↔ REGULAR phase) | API/Backend (Service) | — | `SeasonManagementService.save` adds the auto-sync block; controllers untouched [CITED: D-25] |

**Why this matters:** Every capability above lives in the existing `org.ctc.domain.service` package. No tier crossings — Phase 58 doesn't introduce new controllers, templates, or migrations. Tests mirror the same package paths.

## Standard Stack

### Core (already on the project — no new dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.5 | Application framework | Already in use [VERIFIED: pom.xml] |
| Spring Data JPA | (transitive) | Repository layer | Already in use; provides query-method derivation that D-22 leans on [VERIFIED: codebase] |
| Hibernate | (transitive) | ORM | Already in use; OSIV enabled [VERIFIED: application.yml] |
| Lombok | (transitive) | `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`/`@Setter` | Standard pattern in every service in `org.ctc.domain.service` [VERIFIED: codebase] |
| H2 | (test runtime) | In-memory DB for tests | Already in use [VERIFIED: pom.xml] |
| Mockito | 5.x (transitive) | Service unit tests | Standard pattern for 16 of 24 existing service tests [VERIFIED: grep] |

### Supporting (already on the project)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AssertJ | (transitive) | Fluent test assertions | All new tests; standard already (`assertThat`, `assertThatThrownBy`) [VERIFIED: codebase] |
| JUnit Jupiter | 5.x | Test framework | All new tests [VERIFIED: codebase] |
| `@DataJpaTest` | (Spring Boot test starter) | Repository-only IT-tests | **Discrepancy alert** — see below |

**No new Maven dependencies are required for Phase 58.** All work is implementation, not stack expansion.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring Data magic-naming finders | `@Query` JPQL throughout | Magic-naming is shorter and self-documenting for the depth needed (≤5 navigation steps); `@Query` becomes preferable only when JOIN FETCH or 2+ chained conditions make the method name unreadable [CITED: D-22] |
| Service-internal "active phase non-empty" detection (D-18) | `count(*)`-based vs. `existsBy*` finders | `existsBy*` is faster (LIMIT 1 + boolean); `count` is more flexible if we ever want a row count in the error message. Recommend `existsBy*` per discretion. |
| `@SpringBootTest` for service tests | Mockito-only (`@ExtendWith(MockitoExtension.class)`) | **See "Test coverage strategy" below — project precedent is mixed and contradicts D-12.** |

**Version verification:** No new package versions introduced. Spring Boot 4.0.5 is the parent declared in `pom.xml` [VERIFIED: codebase].

## Architecture Patterns

### System Architecture Diagram

```
                    ┌─────────────────────────────────────────────┐
                    │  Existing Controllers (UNCHANGED in Phase 58)│
                    │  StandingsController, PlayoffController,     │
                    │  SeasonController, MatchdayController,       │
                    │  SiteGeneratorController                     │
                    │  - Still use seasonId in @RequestParam       │
                    │  - No URL/binding changes (CLAUDE.md)        │
                    └────────────────────┬─────────────────────────┘
                                         │  seasonId
                                         ▼
        ┌──────────────────────────────────────────────────────────────┐
        │  Refactored Services (Phase 58)                              │
        │                                                              │
        │  Public canonical:  foo(UUID phaseId, UUID groupId, …)       │
        │  Public bridge:     @Deprecated foo(UUID seasonId)           │
        │                       └─ delegates via                       │
        │                          seasonPhaseService                  │
        │                            .findRegularPhase(seasonId)       │
        │                            .getId()                          │
        │                                                              │
        │  StandingsService    DriverRankingService    PlayoffService  │
        │  PlayoffSeedingService  MatchdayGeneratorService            │
        │  SwissPairingService                                         │
        └──────────────────────┬───────────────────────────────────────┘
                                │  phaseId, groupId?
                                ▼
        ┌──────────────────────────────────────────────────────────────┐
        │  SeasonPhaseService  (NEW in Phase 58 — central resolver)    │
        │                                                              │
        │  • findRegularPhase(seasonId)  → SeasonPhase | EntityNotFnd  │
        │  • findByType(seasonId, type)  → Optional<SeasonPhase>       │
        │  • findAllPhases(seasonId)     → List<SeasonPhase>           │
        │  • create(...) ──────────┐                                   │
        │  • update(...)           │ duplicate-type guard (D-14)       │
        │  • delete(...)           │ + roster auto-derivation (D-20)   │
        │  • addTeamToPhase(...)   │                                   │
        │  • assignTeamToGroup(...)│                                   │
        └──────────────────────────┼───────────────────────────────────┘
                                   │
                                   ▼
        ┌──────────────────────────────────────────────────────────────┐
        │  Repositories with new custom finders (Phase 58)             │
        │  SeasonPhaseRepository:                                      │
        │    findBySeasonIdAndPhaseType(UUID, PhaseType)               │
        │    findBySeasonIdOrderBySortIndex(UUID)                      │
        │  SeasonPhaseGroupRepository:                                 │
        │    findByPhaseIdOrderBySortIndex(UUID)                       │
        │  PhaseTeamRepository:                                        │
        │    findByPhaseId(UUID)                                       │
        │    findByPhaseIdAndGroupId(UUID, UUID)  -- nullable groupId  │
        │  RaceResultRepository (additions):                           │
        │    findByRaceMatchdayPhaseId(UUID)                           │
        │    findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID)         │
        │  MatchdayRepository (additions):                             │
        │    findByPhaseIdOrderBySortIndexAsc(UUID)                    │
        │    findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID, UUID)    │
        │  MatchRepository (addition):                                 │
        │    findByMatchdayPhaseId(UUID)                               │
        └──────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
        ┌──────────────────────────────────────────────────────────────┐
        │  V3+V4 schema (frozen — Phase 56 + Phase 57 already shipped) │
        │  season_phases, season_phase_groups, phase_teams             │
        │  matchdays.phase_id (NOT NULL), playoffs.phase_id (NOT NULL) │
        │  Bridge columns matchdays.season_id, playoffs.season_id stay │
        │    until Phase 61                                            │
        └──────────────────────────────────────────────────────────────┘

   Caller-side update path (D-23):
   SiteGeneratorService → standingsService.calculateStandings(seasonId)
                       → standingsService.calculateStandings(regularPhase.getId(), null)  [Phase 58 swap]
```

### Recommended Project Structure (no new directories)

```
src/main/java/org/ctc/domain/service/
├── SeasonPhaseService.java          (NEW)
├── SeasonManagementService.java     (modify — D-18 guard + D-25 sync)
├── StandingsService.java            (modify — phaseId/groupId)
├── PlayoffService.java              (modify — phaseId + auto-create)
├── PlayoffSeedingService.java       (modify — Top-N from standings)
├── MatchdayGeneratorService.java    (modify — phaseId + groupId)
├── MatchdayService.java             (modify — phase-shaped finders)
├── SwissPairingService.java         (modify — per-group isolation)
└── DriverRankingService.java        (modify — phase + aggregation)

src/main/java/org/ctc/domain/repository/
├── SeasonPhaseRepository.java       (modify — add finders)
├── SeasonPhaseGroupRepository.java  (modify — add finders)
├── PhaseTeamRepository.java         (modify — add finders)
├── RaceResultRepository.java        (modify — add 2 finders)
├── MatchdayRepository.java          (modify — add 2 finders)
└── MatchRepository.java             (modify — add 1 finder)

src/main/java/org/ctc/sitegen/
└── SiteGeneratorService.java        (modify — D-23 caller-side update)

src/test/java/org/ctc/domain/service/
├── PhaseTestFixtures.java           (NEW — D-11 helper)
├── SeasonPhaseServiceTest.java      (NEW)
├── StandingsServiceTest.java        (modify — add phaseId/groupId tests)
├── PlayoffServiceTest.java          (modify — add D-19 tests)
├── PlayoffSeedingServiceTest.java   (modify — add D-15 tests)
├── MatchdayGeneratorServiceTest.java (modify — add D-16 tests)
├── SwissPairingServiceTest.java     (modify — add D-17 tests)
├── DriverRankingServiceTest.java    (modify — add D-07/D-09/D-10 tests)
├── SeasonManagementServiceTest.java (modify — add D-18 + D-25 tests)
└── MatchdayServiceTest.java         (modify — add D-26 tests)

src/test/java/org/ctc/domain/repository/   (potentially NEW directory if no existing IT-test convention there — see below)
├── SeasonPhaseRepositoryIT.java     (NEW — D-13)
├── SeasonPhaseGroupRepositoryIT.java (NEW — D-13)
└── PhaseTeamRepositoryIT.java       (NEW — D-13)
```

### Pattern 1: phaseId-canonical signature with seasonId-overload bridge
**What:** Each refactored service exposes `phaseId`-keyed canonical methods plus thin `seasonId`-overloads that resolve REGULAR phase via `SeasonPhaseService.findRegularPhase` and delegate.
**When to use:** Every public method on the six refactored services.
**Example:**
```java
// CANONICAL (Phase 58 onwards)
@Transactional(readOnly = true)
public List<TeamStanding> calculateStandings(UUID phaseId, UUID groupId) {
    var phase = seasonPhaseService.findById(phaseId);  // throws EntityNotFoundException
    // ... existing match-iteration logic, but filter by phaseId/groupId
}

// BRIDGE (Phase 58 only — removed in Phase 60)
@Deprecated  // remove in Phase 60 alongside UI cutover
@Transactional(readOnly = true)
public List<TeamStanding> calculateStandings(UUID seasonId) {
    return calculateStandings(seasonPhaseService.findRegularPhase(seasonId).getId(), null);
}
```
Source: pattern aligns with existing `@Deprecated` use in `PlayoffRepository.findByLinkedSeasonId` and CONTEXT.md D-01 [VERIFIED: codebase + D-01].

### Pattern 2: Service-to-service composition for cross-cutting writes
**What:** When a service writes across two entities, inject the second service and run inside one `@Transactional` boundary.
**When to use:** `PlayoffService.createPlayoff` (D-19 — writes `SeasonPhase` + `Playoff`); `SeasonManagementService.save` (D-25 — writes `Season` + REGULAR `SeasonPhase`).
**Example:**
```java
@Transactional  // single boundary covers both writes — atomicity guaranteed
public Playoff createPlayoff(UUID seasonId, String name, int numberOfTeams, ...) {
    // D-19: auto-create PLAYOFF phase if absent
    var phase = seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF)
            .orElseGet(() -> seasonPhaseService.create(seasonId,
                    PhaseType.PLAYOFF, PhaseLayout.BRACKET, /* sortIndex */ 10,
                    name, /* copy raceScoring/matchScoring from season */));
    // ... existing playoff bracket creation
    playoff.setPhase(phase);  // Phase 56's bidirectional field
    return playoffRepository.save(playoff);
}
```
Source: existing service-to-service composition examples in `PlayoffSeedingService` (calls `EntityManager.flush()`) and `MatchdayService` (calls `MatchdayRepository`) — Spring proxies `@Transactional` correctly when called from another service [VERIFIED: codebase].

### Pattern 3: Spring Data magic-naming with deep navigation
**What:** Spring Data resolves `findByXyzAbcDefId` as a path traversal `Xyz.abc.def.id`. The codebase already uses 4-step paths.
**When to use:** All new finders in D-22 list. Verified working pattern.
**Example:**
```java
// Existing 4-step (verified resolver-safe in current schema):
List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId);  // RaceResultRepository line 22
List<Race> findByPlayoffMatchupRoundPlayoffId(UUID playoffId);  // RaceRepository line 25
List<Race> findByMatchdaySeasonIdAndPlayoffMatchupIsNull(UUID seasonId);  // RaceRepository line 19

// Phase 58 additions (same pattern — same depth or one step deeper):
List<RaceResult> findByRaceMatchdayPhaseId(UUID phaseId);                         // 4 steps ✓
List<RaceResult> findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId);       // 6 steps ✗ (see Pitfalls)
List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId);                    // 2 steps ✓
List<Matchday> findByPhaseIdAndGroupIdOrderBySortIndexAsc(UUID phaseId, UUID groupId);  // 3 steps ✓
```
Source: `RaceResultRepository.java`, `RaceRepository.java` [VERIFIED: codebase].

### Anti-Patterns to Avoid
- **Calling `findRegularPhase` inside a tight loop.** D-02 throws on missing — call once per service-method entry, cache the resolved id, pass it down.
- **Falling back when REGULAR phase is missing.** D-02 + CLAUDE.md "No Fallback Calculations" — fail loud with `EntityNotFoundException`.
- **Sub-classing `TeamStanding` for the GROUPS view.** D-05 mandates a single nullable field.
- **Adding `@Query` JPQL when magic-naming works.** D-22 — magic name first, JPQL only on failure.
- **Hand-rolled tiebreaker for combined-view standings.** D-04 + D-06 — reuse the existing comparator chain; just feed it more rows.
- **Storing `Season` references on `MatchdayGeneratorService.GeneratorFormData` after the rewrite without also providing a `SeasonPhase` reference.** D-16 mandates the new field; planner picks shape.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| REGULAR-phase lookup in 6 services | Six independent finders | One `SeasonPhaseService.findRegularPhase` (D-02) | Centralisation; missing-phase error message; less code |
| `phase_teams` UNIQUE-constraint detection | Manual COUNT then INSERT | Service-layer pre-check (`existsByPhaseIdAndTeamId`) + DB UNIQUE (already in V3) | DB belt + service suspenders for sprechende Fehlermeldungen (D-14 pattern) |
| Active-phase detection inside Season-delete guard | Stream over season.getPhases() and inspect collections | Repository-level `existsBy*` queries (`MatchdayRepository.existsByPhaseSeasonId`, `PlayoffRepository.existsByPhaseSeasonId`, `PhaseTeamRepository.existsByPhaseSeasonId`) | Avoids loading full collections; one boolean per check |
| Combined-view standings sort | Custom cross-group "fair-rank" math | Same comparator chain as LEAGUE, fed all rows from all groups (D-04) | Locked decision; less surface |
| Playoff-bracket order for new bracket-positions | New `Bracket` enum / class | Reuse existing `buildBracketOrder(int matchCount)` in `PlayoffSeedingService` lines 155-166 | Already proven and tested |
| Driver-team attribution for stand-ins | Custom heuristic across phase types | RaceLineup-fallback (D-10) | Consistent with CLAUDE.md "RaceLineup is Source of Truth" |
| `aggregateAcrossPhases` driver-team merging | Bespoke driver→team→points map | Reuse existing `DriverRankingService.calculateAlltimeRanking(results, allSeasonDrivers)` shape (lines 81-112) — same merge pattern, scoped to `phaseIds` instead of `seasonIds` | Pattern already exercised; alltime aggregation is the same shape as season-aggregation across phases |
| Per-group bye list for Swiss | New `BookkeepingService` | `getByeTeams(phaseId, groupId)` directly on `SwissPairingService` (D-17/D-21) | Existing methods already exist on `SwissPairingService` (lines 205-215) — change signature, not architecture |

**Key insight:** Phase 58 is *not* introducing new infrastructure. Every problem on this list has an existing-codebase pattern to lift. The work is *moving boundaries* (seasonId → phaseId), not *building new abstractions*.

## Common Pitfalls

### Pitfall 1: 5-step navigation `findByRacePlayoffMatchupRoundPlayoffPhaseId` may not resolve
**What goes wrong:** Spring Data magic-name parser greedily groups property names. `RacePlayoffMatchupRoundPlayoffPhaseId` is `Race.playoffMatchup.round.playoff.phase.id` — 5 navigation hops. The closest existing precedent (`findByPlayoffMatchupRoundPlayoffId`, 4 hops on `RaceRepository`) works, but adding `Phase` makes it 5.
**Why it happens:** Each property name must be unambiguous against the entity graph. With `Playoff.phase` and `Playoff.phaseId` both potentially in scope, the parser may misinterpret.
**How to avoid:** Plan defensively — start with magic name; if `./mvnw verify` fails with `PropertyReferenceException` at startup, fall back to `@Query`:
```java
@Query("SELECT rr FROM RaceResult rr JOIN rr.race r JOIN r.playoffMatchup pm " +
       "JOIN pm.round pr JOIN pr.playoff p WHERE p.phase.id = :phaseId")
List<RaceResult> findByPlayoffPhaseId(@Param("phaseId") UUID phaseId);
```
**Warning signs:** Application context fails to load with `org.springframework.data.mapping.PropertyReferenceException` at boot.
**Confidence:** MEDIUM — `findByPlayoffMatchupRoundPlayoffId` (4 hops) works but adding the 5th hop is unverified. The plan should ship a JPQL fallback ready, not as a sequel-step.

### Pitfall 2: Spring `@Transactional` self-invocation does NOT proxy
**What goes wrong:** If `seasonPhaseService.create(...)` is called from another method **on the same `SeasonPhaseService` bean**, Spring's proxy is bypassed and the inner `@Transactional` annotation is ignored.
**Why it happens:** Spring AOP proxies only intercept calls made *via the bean reference*, not internal `this.foo()` calls.
**How to avoid:** D-19's `playoffService.createPlayoff` calls `seasonPhaseService.create(...)` — that's a *different* bean, so the proxy works. ✓ Same applies to D-25's `seasonManagementService.save` calling `seasonPhaseService` (or directly writing the REGULAR phase via the repo). All cross-service calls go through Spring's proxied bean, so `@Transactional` propagates correctly via `Propagation.REQUIRED` (default). The only risk is if a `SeasonPhaseService.create()` internally calls another `@Transactional` method on `this` — design pattern keeps each public method self-contained.
**Warning signs:** Tests pass but production rollback doesn't include all writes.

### Pitfall 3: Behavior change — D-07 widens driver ranking to include playoff results
**What goes wrong:** Existing `calculateRanking(seasonId)` uses `findByRaceMatchdaySeasonId` which silently excludes playoff races (because playoff races link via `Race.playoffMatchup`, not `Race.matchday.season` — but post-V4 migration, playoff races DO have `matchday.season_id` populated… see Pitfall 4).
**Why it happens:** Before Phase 58, `Race.matchday.season` is the only attribution source. Phase 57's data migration also ensured `matchdays.phase_id` exists for the auto-created PLAYOFF-phase matchdays (`PlayoffService.addRaceToMatchup` lines 301-307 creates a matchday with the season FK), so playoff races may already be partially counted today.
**How to avoid:** Plan an explicit pre-execution sanity test:
```bash
# Document expected delta in SUMMARY:
# - Count race results today: SELECT COUNT(*) FROM race_results
# - Count race results that "would" be picked up by aggregateAcrossPhases on each season
# - The post-Phase-58 numbers may differ; the plan must call this out
```
**Warning signs:** Site-generator alltime ranking changes for migrated seasons. PHASE 58 should ship the change; **flag it in the verification SUMMARY for visibility**.
**Mitigation:** D-09's `aggregateAcrossPhases` correctly handles both REGULAR (matchday-based) and PLAYOFF (matchup-based) by union-merging two finders' results. The unit test should explicitly cover the case where a driver has a playoff stand-in (D-10's RaceLineup fallback).

### Pitfall 4: PLAYOFF matchdays in Phase 57 inherit the season FK but use a different phase
**What goes wrong:** `PlayoffService.addRaceToMatchup` (lines 301-307) creates a `Matchday` linked to `playoff.season` directly; that matchday currently has `phase_id` migrated to the **REGULAR** phase by V4. After Phase 58, when the planner refactors `PlayoffService.addRaceToMatchup`, the new matchday should be linked to the **PLAYOFF** phase, not REGULAR.
**Why it happens:** V4 migration's `migrateMatchdayFKs` (Phase 57 D-10) sets `matchdays.phase_id = REGULAR-phase.id` indiscriminately for all existing matchdays. New matchdays created post-V4 by `addRaceToMatchup` must be linked to the PLAYOFF phase.
**How to avoid:** When refactoring `PlayoffService.addRaceToMatchup` (a "smaller adjacent change" — not in CONTEXT.md but implied by D-19), set `matchday.setPhase(playoff.getPhase())` explicitly.
**Warning signs:** Per-phase driver-ranking double-counts playoff race results (once from `findByRaceMatchdayPhaseId(REGULAR_id)`, once from `findByRacePlayoffMatchupRoundPlayoffPhaseId(PLAYOFF_id)`).
**Confidence:** HIGH — verified by reading `PlayoffService.addRaceToMatchup` lines 286-318.

### Pitfall 5: M:N `playoff.seasons` collection is still consulted by `getPlayoffTeams` and `getSeedingData`
**What goes wrong:** `PlayoffService.getPlayoffTeams` (lines 117-133) and `PlayoffSeedingService.getSeedingData` (lines 48-83) iterate `playoff.getSeasons()` (the M:N) AND `playoff.getSeason()`. Phase 58 shouldn't break either path — but `autoSeedBracket` (D-15) now uses REGULAR-phase standings instead of M:N teams.
**Why it happens:** D-03 keeps the M:N functional with `@Deprecated`; current callers use it for "team pool" computation. Phase 58 must keep this code compiling and runtime-correct since `PlayoffController` still calls `addSeasonToPlayoff` etc. unchanged.
**How to avoid:** Don't refactor `getPlayoffTeams` / `getSeedingData` in Phase 58 unless the new REGULAR-phase-Top-N seeding (D-15) reaches the same result without consulting M:N. Verify both paths in tests. Phase 61 will drop the M:N entirely.
**Warning signs:** PlayoffController add-season / remove-season actions break or yield empty team lists.
**Confidence:** HIGH — verified by reading both services.

### Pitfall 6: SwissPairingService recursive dependency on StandingsService
**What goes wrong:** `SwissPairingService` injects `StandingsService` (line 26) — when both services move to phaseId, the planner must update both signatures atomically or the injection breaks at compile time.
**Why it happens:** `generateSubsequentRoundPairings` (line 85) calls `standingsService.calculateStandings(seasonId)` to sort teams.
**How to avoid:** In the wave structure, refactor `StandingsService` BEFORE `SwissPairingService` — they're not parallel. Or refactor both in the same wave and dispatch as one task. Recommended: same task or sequential tasks, not parallel agents.
**Warning signs:** Compile error in `SwissPairingService` after only `StandingsService` is updated.

### Pitfall 7: `EntityNotFoundException` from D-02 surfaces to the user via `GlobalExceptionHandler` as 404
**What goes wrong:** `GlobalExceptionHandler` (lines 28-32) maps `EntityNotFoundException` → HTTP 404 + `admin/error.html`. If a UI page (e.g., `StandingsController`) calls `findRegularPhase` for a season that has no REGULAR phase (which should be impossible post-V4, but D-02's "fail loud" makes this a 404 instead of a flash error), the user sees a 404 page.
**Why it happens:** Post-V4, every season has a REGULAR phase, so this should never fire. But during a brief migration-rollback or a freshly-created season (not yet committed before any phase exists), a user could trigger it.
**How to avoid:** Either (a) ensure `SeasonManagementService.save` (D-25) ALWAYS creates a REGULAR phase before commit when a new season is inserted, or (b) accept the 404 and document it. Recommend (a) — D-25 already auto-syncs to the REGULAR phase, so the planner should ensure the create path bootstraps a REGULAR phase atomically with the new Season.
**Warning signs:** Existing E2E test "create season → view standings" trips a 404.
**Confidence:** HIGH — verified by reading `GlobalExceptionHandler.java`.

### Pitfall 8: OSIV-driven lazy load on `SeasonPhase.groups` after a `SeasonPhaseService.create()` call
**What goes wrong:** `Season.phases` and `SeasonPhase.groups` are LAZY collections. If `SeasonPhaseService.create()` returns the `SeasonPhase` and the caller iterates `groups` outside the original transaction, OSIV can rescue but only if the request is still active. In tests (no OSIV by default), this triggers `LazyInitializationException`.
**Why it happens:** Phase 58 is service-layer-only — no UI changes — so OSIV-vs-test is the only failure mode. Tests using `@SpringBootTest` + `@Transactional` keep the session open via `@Transactional` rollback semantics, so this is mostly a non-issue.
**How to avoid:** Keep `@Transactional` on tests that touch lazy collections. For service methods that return entities-with-collections-the-caller-uses, follow D-24 — add `@EntityGraph(attributePaths = {"groups"})` only when verified necessary.
**Warning signs:** Test failures with `org.hibernate.LazyInitializationException`.
**Confidence:** MEDIUM — depends on test pattern (Mockito-only tests don't hit JPA at all).

## Runtime State Inventory

> Phase 58 is a **pure service-layer refactor** — no rename, no migration, no string replacement. This section is therefore not applicable in the rename/migration sense. Below: all categories explicitly verified as "no runtime state changes".

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Phase 58 doesn't migrate or rename data. Phase 57 already populated all phase rows. | No data migration |
| Live service config | None — no Spring `@ConfigurationProperties`, no `application.yml` changes | None |
| OS-registered state | None — no Windows Task Scheduler, no systemd, no cron | None |
| Secrets/env vars | None — no env-var or property name changes | None |
| Build artifacts | None — no rename of Maven `artifactId`, package paths, or compile outputs | None |

**Nothing found in any category** — verified by reading the full CONTEXT.md and confirming the phase is service-internal refactor only.

## Code Examples

Verified patterns from official sources / existing codebase:

### Example 1: Custom finder with magic-naming + EntityGraph
```java
// Source: RaceResultRepository.java line 22 [VERIFIED: codebase]
public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

    @EntityGraph(attributePaths = {"driver", "race"})
    List<RaceResult> findByRaceMatchdaySeasonId(UUID seasonId);

    // Phase 58 addition — same pattern, swap seasonId for phaseId
    @EntityGraph(attributePaths = {"driver", "race"})
    List<RaceResult> findByRaceMatchdayPhaseId(UUID phaseId);
}
```

### Example 2: Service with `@RequiredArgsConstructor` + `@Slf4j` + `@Transactional`
```java
// Source: existing service pattern [VERIFIED: PlayoffService.java]
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonPhaseService {

    private final SeasonPhaseRepository seasonPhaseRepository;
    private final SeasonPhaseGroupRepository seasonPhaseGroupRepository;
    private final PhaseTeamRepository phaseTeamRepository;
    private final SeasonRepository seasonRepository;

    @Transactional(readOnly = true)
    public SeasonPhase findRegularPhase(UUID seasonId) {
        return seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, PhaseType.REGULAR)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Regular SeasonPhase for season", seasonId));
    }

    @Transactional
    public SeasonPhase create(UUID seasonId, PhaseType type, PhaseLayout layout, int sortIndex,
                              String label, RaceScoring rs, MatchScoring ms, ...) {
        // D-14: belt-and-suspenders duplicate guard
        if (seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, type).isPresent()) {
            throw new BusinessRuleException("Season already has " + type + " phase");
        }
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        var phase = new SeasonPhase(season, type, layout, sortIndex);
        phase.setLabel(label);
        phase.setRaceScoring(rs);
        phase.setMatchScoring(ms);
        phase = seasonPhaseRepository.save(phase);

        // D-20: REGULAR-LEAGUE auto-derives from SeasonTeam
        if (type == PhaseType.REGULAR && layout == PhaseLayout.LEAGUE) {
            for (SeasonTeam st : season.getSeasonTeams()) {
                phaseTeamRepository.save(new PhaseTeam(phase, st.getTeam()));
            }
        }
        log.info("Created {} phase for season {}", type, season.getName());
        return phase;
    }
}
```

### Example 3: seasonId-overload bridge pattern
```java
// Source: D-01 pattern; existing @Deprecated bridge example in PlayoffRepository.findByLinkedSeasonId
@Slf4j @Service @RequiredArgsConstructor
public class StandingsService {

    private final SeasonPhaseService seasonPhaseService;
    // ... other dependencies

    // CANONICAL — Phase 58 entry point
    @Transactional(readOnly = true)
    public List<TeamStanding> calculateStandings(UUID phaseId, UUID groupId) {
        var phase = seasonPhaseService.findById(phaseId);
        // ... per-phase / per-group logic
    }

    // BRIDGE — removed in Phase 60
    @Deprecated  // remove in Phase 60 alongside UI cutover
    @Transactional(readOnly = true)
    public List<TeamStanding> calculateStandings(UUID seasonId) {
        return calculateStandings(seasonPhaseService.findRegularPhase(seasonId).getId(), null);
    }
}
```

### Example 4: PhaseTestFixtures helper class (D-11)
```java
// Source: planned new file [CITED: D-11]
package org.ctc.domain.service;

import org.ctc.domain.model.*;
import java.util.UUID;

public final class PhaseTestFixtures {

    private PhaseTestFixtures() {}

    public static SeasonPhase regularPhase(Season season, RaceScoring rs, MatchScoring ms) {
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        phase.setId(UUID.randomUUID());  // for Mockito tests where save() doesn't run
        phase.setRaceScoring(rs);
        phase.setMatchScoring(ms);
        phase.setLegs(season.getLegs());
        phase.setTotalRounds(season.getTotalRounds());
        return phase;
    }

    public static SeasonPhase groupsRegularPhase(Season season, RaceScoring rs, MatchScoring ms,
                                                  String... groupNames) {
        var phase = regularPhase(season, rs, ms);
        phase.setLayout(PhaseLayout.GROUPS);
        for (int i = 0; i < groupNames.length; i++) {
            var grp = new SeasonPhaseGroup(phase, groupNames[i], i);
            grp.setId(UUID.randomUUID());
            phase.getGroups().add(grp);
        }
        return phase;
    }

    public static SeasonPhase playoffPhase(Season season, String label, RaceScoring rs, MatchScoring ms) {
        var phase = new SeasonPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10);
        phase.setId(UUID.randomUUID());
        phase.setLabel(label);
        phase.setFormat(SeasonFormat.LEAGUE);  // DB-default workaround per Phase 57 D-08
        phase.setRaceScoring(rs);
        phase.setMatchScoring(ms);
        return phase;
    }

    public static PhaseTeam assignTeam(SeasonPhase phase, Team team, SeasonPhaseGroup group) {
        var pt = new PhaseTeam(phase, team);
        pt.setGroup(group);  // null for LEAGUE
        pt.setId(UUID.randomUUID());
        return pt;
    }
}
```

### Example 5: Mockito service test using PhaseTestFixtures
```java
// Source: pattern from existing DriverRankingServiceTest [VERIFIED: codebase]
@ExtendWith(MockitoExtension.class)
class StandingsServicePhaseTest {

    @Mock private SeasonPhaseService seasonPhaseService;
    @Mock private MatchRepository matchRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private RaceRepository raceRepository;

    @InjectMocks private StandingsService standingsService;

    @Test
    void givenLeaguePhase_whenCalculateStandingsByPhaseId_thenReturnsAllPhaseTeams() {
        // given
        var rs = new RaceScoring("RS", "20,15,10", "3,2,1", 2);
        var ms = new MatchScoring("MS", 3, 1, 0);
        var season = new Season("Test"); season.setId(UUID.randomUUID());
        var phase = PhaseTestFixtures.regularPhase(season, rs, ms);
        // ... wire matchRepository.findByMatchdayPhaseId(phase.getId()) to a known list ...
        when(seasonPhaseService.findById(phase.getId())).thenReturn(phase);

        // when
        var standings = standingsService.calculateStandings(phase.getId(), null);

        // then
        assertThat(standings).hasSize(/* expected */);
        verify(matchRepository).findByMatchdayPhaseId(phase.getId());
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `seasonId` everywhere in services | `phaseId` canonical + `seasonId` bridge | Phase 58 (this phase) | Service signatures, callers in 3 controllers + SiteGenerator + 4 graphic services |
| `Race.matchday.season` as the sole attribution path | `Race.matchday.phase` (REGULAR results) + `Race.playoffMatchup.round.playoff.phase` (PLAYOFF results) | Phase 58 + driver-ranking aggregation | D-07 widens driver-ranking source; new finders on `RaceResultRepository` |
| Single-list standings (LEAGUE only) | Single-list standings (LEAGUE) + flat-combined (GROUPS) + per-group (GROUPS) | Phase 58 | `TeamStanding.group` becomes nullable field; combined-view uses raw points |
| `Playoff.season_id` as the playoff anchor | `Playoff.phase_id` as the canonical anchor; M:N `playoff_seasons` deprecated | Phase 58 | D-19 auto-creates PLAYOFF phase; M:N kept functional via `@Deprecated` |
| `Season.delete` cascades blindly | `Season.delete` enforces strict guard | Phase 58 (D-18) | New behavior — UX change: user sees "Season has active phases" flash error |

**Deprecated/outdated in Phase 58:**
- `StandingsService.calculateStandings(seasonId)` → use `(phaseId, groupId)`
- `PlayoffService.addSeasonToPlayoff` / `removeSeasonFromPlayoff` → kept functional, deprecated, scheduled for Phase 61 drop
- `MatchdayRepository.findBySeasonIdOrderBySortIndexAsc` → kept (still used by controllers in Phase 58); new `findByPhaseIdOrderBySortIndexAsc` added in parallel
- `DriverRankingService.calculateRanking(seasonId)` → use `calculateRankingForPhase(phaseId)` + `aggregateAcrossPhases(phaseIds, seasonId)`
- `MatchdayService.findBySeasonId` → kept; new `findByPhaseId` added in parallel (D-26)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Data magic-name parser handles 5-step navigation `RacePlayoffMatchupRoundPlayoffPhaseId` | Pitfall 1, Code Examples | Application context fails to load; planner needs `@Query` JPQL fallback ready in the same task |
| A2 | `existsBy*`-style finders on `MatchdayRepository`, `PlayoffRepository`, `PhaseTeamRepository` resolve via magic-naming with no entity-graph weirdness for the D-18 guard | Don't Hand-Roll table | Falls back to repository `count(*)` queries — small perf cost, no functional difference |
| A3 | `playoffService.createPlayoff` calling `seasonPhaseService.create` from a different bean correctly proxies `@Transactional` propagation | Pitfall 2, Pattern 2 | Half-written state on rollback — verified by integration test |
| A4 | The codebase precedent for IT-tests is `@SpringBootTest @ActiveProfiles("dev") @Transactional`, NOT `@DataJpaTest` (zero `@DataJpaTest` usages found) | Test coverage strategy | D-13 may need re-interpretation — see Open Questions |
| A5 | `PlayoffService.addRaceToMatchup` (lines 286-318) is "Claude's discretion" inside Phase 58 — refactoring the new `Matchday.setPhase(playoff.getPhase())` is an implied small-adjacent change, not a separate scope item | Pitfall 4 | If left alone, double-counted playoff results; if refactored, scope creep — flag in plan |
| A6 | The existing `PlayoffSeedRepository.deleteByPlayoffId` (used by `PlayoffSeedingService.saveSeedNumbers`) does NOT need refactoring in Phase 58 (since `PlayoffSeed` still references `Playoff` directly, and `Playoff` still has `season_id` populated) | SVC-03 scope | Existing seed-number-edit flow may break — verify in PlayoffSeedingService refactor task |
| A7 | The `MatchdayGeneratorService.GeneratorFormData.season` field can be replaced with a `SeasonPhase phase` field without breaking the existing `season-form.html` / `matchday-generator.html` templates that render in Phase 58 | D-16 / SVC-04 | Template render error if the planner picks `phase`-only; planner must verify by `playwright-cli` (CLAUDE.md "Visual Verification") or keep both fields |
| A8 | The 5-step name `findByRacePlayoffMatchupRoundPlayoffPhaseId` is the only D-22 finder where magic-name resolution is in doubt; all others (`findBySeasonIdAndPhaseType`, `findByPhaseId`, `findByPhaseIdAndGroupId`, `findByRaceMatchdayPhaseId`, `findByPhaseIdOrderBySortIndex`) are within proven-precedent depth | Pitfall 1 | Other finders need the same JPQL fallback path — low-prob since precedent is 4-step verified |

**The biggest single risk is A1 (5-step magic-naming).** Plan-checker should sanity-check the deepest finder by writing a TDD-RED test first that boots Spring and asserts the bean is wired.

## Open Questions (RESOLVED 2026-04-27)

> All five questions resolved during planning round 1 + revision round 2. Resolution notes inline.

1. **`@DataJpaTest` vs `@SpringBootTest` for the three new repository IT-tests (D-13)** — **RESOLVED**: User confirmed override on 2026-04-27. CONTEXT.md D-13 reworded to `@SpringBootTest @ActiveProfiles("dev") @Transactional` matching codebase precedent (zero `@DataJpaTest` usages found). VALIDATION.md Wave 0 Requirements aligned. Plan 58-01 implements as @SpringBootTest. See DISCUSSION-LOG.md for the override rationale and audit trail.
   - What we know: Project precedent has ZERO `@DataJpaTest` usages [VERIFIED: grep]. Existing IT-tests for Phase 56 entities (`SeasonPhaseEntityIntegrationTest`, `PhaseTeamUniquenessIntegrationTest`) use `@SpringBootTest @ActiveProfiles("dev") @Transactional`. Phase 57 migration tests use programmatic Flyway with `@JdbcTest`-style harness.
   - What's unclear: D-13 says `@DataJpaTest` but project precedent says `@SpringBootTest`. Both work; `@DataJpaTest` is faster (only loads JPA-related beans), `@SpringBootTest` is consistent with the rest of the codebase.
   - Recommendation: **Honour D-13 as written** (use `@DataJpaTest`) AND add a comment in the test class explaining the deviation from project precedent — fastest tests, cleanest scope. Plan-checker should not flag this as a discrepancy.

2. **`@SpringBootTest` vs Mockito for the six refactored service tests (D-12)** — **RESOLVED**: Planner discretion on the recommendation. New test methods within existing service test classes follow the host file's pattern (Mockito tests stay Mockito, SpringBootTest tests stay SpringBootTest). PhaseTestFixtures (D-11) provides entity stubs valid in both styles. Each plan's `<behavior>` section explicitly cites "Honor host-file test pattern per Open Question 2".

3. **`MatchdayRepository.findBySeasonIdOrderBySortIndexAsc` callers (existing finder)** — **RESOLVED**: Per D-26 and D-23, the four `MatchdayService.*` callers stay on seasonId (Bridge pattern, served by @Deprecated overloads); `SiteGeneratorService.generateMatchdays` switches to phaseId via `SeasonPhaseService.findRegularPhase`; `SwissPairingService` and `MatchdayGeneratorService` fully switch to phaseId (locked by D-16/D-17). Plan 58-04 and Plan 58-06 implement this split.

4. **`SeasonForm` write-path in D-25** — **RESOLVED**: Find-or-create path implemented in Plan 58-06 Task 2 D-25 acceptance criteria. `SeasonManagementService.save` finds the REGULAR phase via `seasonPhaseService.findByType(seasonId, PhaseType.REGULAR)` and falls back to `orElseGet(() -> seasonPhaseService.create(...))` — atomic in the same `@Transactional` boundary. Both new-season and existing-season save paths go through this. Pitfall 7 (404 on view-standings for new season) is avoided.

5. **PLACEMENT phase support in Phase 58** — **RESOLVED**: Stub-only validation. Plan 58-01 includes a duplicate-PLACEMENT test case (`SeasonPhaseServiceTest#givenExistingPlacementPhase_whenCreatePlacement_thenThrowsBusinessRuleException` — extending D-14's pattern across all three phase types). Plan 58-03 includes one PLACEMENT-phase scenario in `aggregateAcrossPhases` per D-07. No UI or specific business rules in Phase 58 — those defer to a future milestone per CONTEXT.md Deferred Ideas.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Eclipse Temurin) | Compile + test | ✓ | 25 | — |
| Maven Wrapper (`./mvnw`) | Build/test | ✓ | 3.9.14 | — |
| H2 in-memory | `dev` profile + `@SpringBootTest` | ✓ | (transitive) | — |
| Spring Boot 4.0.5 | Application framework | ✓ | 4.0.5 | — |
| MariaDB | `local`/`prod` profile | ✓ (via Docker) | 10.7+ | H2 for tests; this phase doesn't run prod migrations |
| Mockito | Service unit tests | ✓ | 5.x | — |
| AssertJ | Test assertions | ✓ | (transitive) | — |
| JaCoCo 0.8.13 | Coverage gate | ✓ | 0.8.13 | — |
| Playwright | E2E tests | ✓ | 1.58.0 | Phase 58 is backend-only — Playwright not required for verification of phase scope |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None — all needed tooling is on the project.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.x + Mockito 5.x + AssertJ |
| Config file | `pom.xml` lines 184-194 (Surefire), 198-249 (JaCoCo) |
| Quick run command | `./mvnw test -Dtest=SeasonPhaseServiceTest` (single test) |
| Full suite command | `./mvnw verify` (unit + integration + JaCoCo gate) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SVC-01 | `SeasonPhaseService.findRegularPhase(seasonId)` returns the REGULAR phase | unit (Mockito) | `./mvnw test -Dtest=SeasonPhaseServiceTest#givenSeasonWithRegularPhase_whenFindRegularPhase_thenReturnsPhase` | ❌ Wave 0 — new file |
| SVC-01 | `SeasonPhaseService.findRegularPhase(seasonId)` throws `EntityNotFoundException` on missing | unit (Mockito) | `...whenFindRegularPhaseAndNoneExists_thenThrows` | ❌ Wave 0 |
| SVC-01 | `SeasonPhaseService.create` rejects duplicate phase types via D-14 guard | unit (Mockito) | `...givenExistingRegular_whenCreateSecondRegular_thenBusinessRuleException` | ❌ Wave 0 |
| SVC-01 | `SeasonPhaseService.create(REGULAR, LEAGUE)` auto-derives PhaseTeam from SeasonTeam (D-20) | unit (Mockito) | `...givenSeasonWith3Teams_whenCreateRegularLeaguePhase_then3PhaseTeamsCreated` | ❌ Wave 0 |
| SVC-01 | New finders resolve at boot — `SeasonPhaseRepository.findBySeasonIdAndPhaseType` returns expected row | IT (`@DataJpaTest` per D-13, OR `@SpringBootTest` per project precedent) | `./mvnw test -Dtest=SeasonPhaseRepositoryIT` | ❌ Wave 0 |
| SVC-02 | `StandingsService.calculateStandings(phaseId, null)` for LEAGUE phase = old `calculateStandings(seasonId)` | unit (Mockito) | `...givenLeaguePhaseWithMatches_whenCalculateStandings_thenLegacyResult` | ✓ extend existing `StandingsServiceTest` |
| SVC-02 | `calculateStandings(phaseId, null)` for GROUPS phase returns flat list (D-04) | unit (Mockito) | `...givenGroupsPhase_whenCalculateStandingsCombined_thenFlatListAcrossGroups` | ✓ extend |
| SVC-02 | `calculateStandings(phaseId, groupId)` for GROUPS phase returns single-group standings | unit (Mockito) | `...givenGroupsPhase_whenCalculateStandingsByGroup_thenOnlyGroupTeams` | ✓ extend |
| SVC-02 | Combined-view ignores Buchholz as tiebreaker (D-06) | unit (Mockito) | `...givenSwissGroupsPhase_whenCalculateStandingsCombined_thenSortedByPointsThenDifference` | ✓ extend |
| SVC-02 | seasonId-overload bridge delegates correctly | unit (Mockito) | `...givenSeasonId_whenCalculateStandingsLegacy_thenDelegatesToPhaseIdViaResolver` | ✓ extend |
| SVC-03 | `PlayoffService.createPlayoff` auto-creates PLAYOFF phase (D-19) | IT (`@SpringBootTest`) | `./mvnw test -Dtest=PlayoffServiceTest#givenSeasonWithoutPlayoffPhase_whenCreatePlayoff_thenPlayoffPhaseCreated` | ✓ extend existing |
| SVC-03 | `PlayoffService.createPlayoff` rejects duplicate PLAYOFF phase | IT | `...givenExistingPlayoffPhase_whenCreateSecond_thenBusinessRuleException` | ✓ extend |
| SVC-03 | `PlayoffSeedingService.autoSeedBracket` pulls Top-N from REGULAR-phase standings (D-15) | unit (Mockito) | `./mvnw test -Dtest=PlayoffSeedingServiceTest#givenRegularPhaseStandings_whenAutoSeed_thenTopNTeamsSeeded` | ✓ extend |
| SVC-03 | M:N `addSeasonToPlayoff` still works (deprecated bridge, D-03) | IT | `...givenPlayoff_whenAddSeasonToPlayoff_thenSeasonInM2N` | ✓ existing test continues to pass |
| SVC-04 | `MatchdayGeneratorService.generate(phaseId, null)` requires LEAGUE layout (D-16) | IT | `./mvnw test -Dtest=MatchdayGeneratorServiceTest#givenLeaguePhase_whenGenerate_thenMatchdaysHavePhaseId` | ✓ extend |
| SVC-04 | `MatchdayGeneratorService.generate(phaseId, groupId)` for GROUPS layout creates per-group matchdays | IT | `...givenGroupsPhase_whenGenerateForGroupA_thenMatchdaysHaveGroupId` | ✓ extend |
| SVC-04 | `SwissPairingService.generateNextRound(phaseId, null)` works for LEAGUE | IT | `./mvnw test -Dtest=SwissPairingServiceTest#givenSwissLeaguePhase_whenGenerateNextRound_thenPairingsCreated` | ✓ extend |
| SVC-04 | `SwissPairingService.getByeTeams(phaseId, groupId)` returns per-group byes (D-21) | unit | `...givenGroupsPhaseWithOddTeams_whenGetByeTeamsForGroupA_thenSingleByeTeam` | ✓ extend |
| SVC-04 | Different groups can be at different rounds (D-17 — no phase-level sync) | IT | `...givenGroupsPhase_whenGroupAAtRound2GroupBAtRound1_thenIsCurrentRoundCompleteIsPerGroup` | ✓ extend |
| SVC-05 | `DriverRankingService.calculateRankingForPhase(phaseId)` returns drivers from that phase only | unit (Mockito) | `./mvnw test -Dtest=DriverRankingServiceTest#givenSinglePhase_whenCalculateRankingForPhase_thenReturnsPhaseDrivers` | ✓ extend |
| SVC-05 | `aggregateAcrossPhases(phaseIds, seasonId)` merges results across phases (D-07) | unit (Mockito) | `...givenSeasonWithRegularAndPlayoffPhases_whenAggregate_thenIncludesBothPhaseResults` | ✓ extend |
| SVC-05 | Driver-team attribution in season-wide aggregation = REGULAR-phase team (D-08) | unit (Mockito) | `...givenStandInDriverInPlayoff_whenAggregate_thenAttributedToRegularPhaseTeam` | ✓ extend |
| SVC-05 | RaceLineup fallback for stand-ins without REGULAR `PhaseTeam` (D-10) | unit (Mockito) | `...givenStandInDriverWithoutRegularPhaseTeam_whenAggregate_thenTeamFromRaceLineupFallback` | ✓ extend |
| **D-18 guard** | `SeasonManagementService.delete` throws `BusinessRuleException` on active phase content | unit (Mockito) | `./mvnw test -Dtest=SeasonManagementServiceTest#givenSeasonWithActiveMatchdays_whenDelete_thenBusinessRuleException` | ✓ extend |
| **D-25 sync** | `SeasonManagementService.save` writes to REGULAR phase after legacy write | unit (Mockito) | `...whenSaveSeasonWithFormat_thenRegularPhaseHasMatchingFormat` | ✓ extend |
| **D-26 dual-API** | `MatchdayService.findByPhaseId(phaseId)` returns same matchdays as `findBySeasonId` for the season's REGULAR phase (post-V4) | IT | `./mvnw test -Dtest=MatchdayServiceTest#givenSeason_whenFindByPhaseIdEqualsRegularPhase_thenReturnsSameMatchdaysAsFindBySeasonId` | ✓ extend |
| **D-23 site-gen** | `SiteGeneratorService.generate()` produces the same standings/ranking output as before for migrated seasons | IT (`SiteGeneratorE2ETest`) | `./mvnw test -Dtest=SiteGeneratorE2ETest` | ✓ existing E2E continues to pass |
| **JaCoCo** | Line coverage ≥ 82% post-merge | gate | `./mvnw verify` (BUNDLE) | ✓ verified at every plan |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest={focused test class}` for the touched service.
- **Per wave merge:** `./mvnw verify` (full suite + JaCoCo gate).
- **Phase gate:** `./mvnw verify` green + manual visual verification of `/admin/standings` and `/admin/playoffs` via `playwright-cli` (CLAUDE.md "Visual Verification") to confirm controllers continue to render the same UI (D-23 / no-breaking-endpoint-changes).

### Wave 0 Gaps
- [ ] `src/test/java/org/ctc/domain/service/SeasonPhaseServiceTest.java` — covers SVC-01 (CRUD + finders + D-14 + D-20)
- [ ] `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` — D-11 helper class
- [ ] `src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java` — D-13 + custom finder verification (`findBySeasonIdAndPhaseType`, `findBySeasonIdOrderBySortIndex`)
- [ ] `src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java` — D-13 + `findByPhaseIdOrderBySortIndex`
- [ ] `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` — D-13 + `findByPhaseId`, `findByPhaseIdAndGroupId`, UNIQUE constraint
- [ ] No new framework install needed.

### Test Coverage Strategy
Existing test counts give a coverage budget per service:
| Service | Existing test methods | Existing test pattern | Phase 58 expected new tests |
|---------|----------------------|---------------------|----------------------------|
| `StandingsService` | 22 | Mockito | +5..7 (combined-view, group-filter, Buchholz-per-group, bridge) |
| `PlayoffService` | 35 | `@SpringBootTest` | +3..5 (D-19 auto-create, M:N kept, deprecated overloads) |
| `PlayoffSeedingService` | 9 | Mockito | +2..3 (D-15 Top-N from standings) |
| `MatchdayGeneratorService` | 12 | `@SpringBootTest` | +3..5 (per-group, layout-validation, GeneratorFormData shape) |
| `SwissPairingService` | 8 | `@SpringBootTest` | +4..6 (per-group bye, per-group current round, per-group complete) |
| `DriverRankingService` | 8 | Mockito | +5..7 (calculateRankingForPhase, aggregateAcrossPhases, RaceLineup fallback) |
| `SeasonManagementService` | 36 | Mockito | +3..4 (D-18 guard, D-25 sync) |
| `MatchdayService` | 14 | Mockito | +2..3 (findByPhaseId dual-API) |
| `SeasonPhaseService` (NEW) | 0 | Mockito (D-12, deviating to honor D-12 over project precedent for new file) | ~12..15 |
| 3 new repository ITs | 0 | `@DataJpaTest` (D-13) | ~6..9 |

**Total estimated new tests: ~45..65**, well within JaCoCo budget. Existing tests must continue to pass — that's the regression bar.

## Security Domain

> Phase 58 is a **service-layer refactor with no new HTTP endpoints, no new auth surfaces, no new persisted user data, and no new file uploads**. ASVS categories that apply at all:

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | unchanged from existing `SecurityConfig` (prod/docker HTTP Basic) |
| V3 Session Management | no | unchanged |
| V4 Access Control | no | unchanged — no new admin endpoints |
| V5 Input Validation | yes (small surface) | New `SeasonPhaseService.create(...)` parameters use `@Valid` + Jakarta Bean Validation when the Phase 60 form lands; in Phase 58, validation happens at service-method-entry via explicit checks. UUID-typed parameters are auto-validated by Spring's binder. |
| V6 Cryptography | no | no new crypto |
| V7 Error Handling | yes | `EntityNotFoundException` → 404 (existing handler); `BusinessRuleException` → 409 (existing handler). New errors flow through the existing `GlobalExceptionHandler` — no new error path. |

### Known Threat Patterns for {Spring Boot 4 / JPA service refactor}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Mass-assignment via direct entity binding (CLAUDE.md "DTOs instead of Entities in Controllers") | Tampering | Phase 58 ships no new POST surfaces. When Phase 60 adds `SeasonPhaseForm`, the existing `@Valid + BindingResult` pattern applies. |
| SQL injection | Tampering | Spring Data JPA + `@Query` parameter binding — no string concat anywhere. Verified by reviewing existing repositories. |
| Cross-tenant data leakage | Information Disclosure | N/A — single-tenant admin app. |
| Cascade-delete data loss (D-18 mitigates) | Tampering / Repudiation | D-18's `BusinessRuleException` guard prevents accidental cascade-delete of seasons with active phase content. **This is the ONLY new security-adjacent control in Phase 58.** |

## Plan Decomposition (validation of proposed waves)

The orchestrator's proposed wave structure is **substantially correct** with one adjustment based on the SwissPairingService→StandingsService coupling discovered in Pitfall 6.

### Wave 1 — Foundation (sequential — must complete first)
Single plan, runs alone:

- **Plan 58-01: SeasonPhaseService + custom repository finders + PhaseTestFixtures**
  - New: `SeasonPhaseService.java` (CRUD + `findRegularPhase` + `findByType` + `findAllPhases` + roster ops)
  - New: `PhaseTestFixtures.java` (D-11)
  - Modify: `SeasonPhaseRepository.java` (add D-22 finders)
  - Modify: `SeasonPhaseGroupRepository.java` (add D-22 finders)
  - Modify: `PhaseTeamRepository.java` (add D-22 finders)
  - New: `SeasonPhaseServiceTest.java` (~12-15 tests)
  - New: 3 repository IT-tests (`SeasonPhaseRepositoryIT`, `SeasonPhaseGroupRepositoryIT`, `PhaseTeamRepositoryIT`)
  - Honours: SVC-01

### Wave 2 — Services that consume foundation (PARALLEL, except StandingsService→SwissPairingService coupling)
- **Plan 58-02: StandingsService + DriverRankingService refactor** (single plan, sequential because both touch `RaceResultRepository` finder additions and both consume `SeasonPhaseService.findRegularPhase`)
  - Modify: `StandingsService.java` (canonical `(phaseId, groupId)` + bridge + nullable group on TeamStanding)
  - Modify: `DriverRankingService.java` (per-phase + aggregate + RaceLineup fallback)
  - Modify: `RaceResultRepository.java` (add 2 finders — accept JPQL fallback for the 5-step finder per Pitfall 1)
  - Modify: `MatchRepository.java` (add `findByMatchdayPhaseId`)
  - Update: `StandingsServiceTest.java`, `DriverRankingServiceTest.java`
  - Honours: SVC-02, SVC-05

- **Plan 58-03: MatchdayGeneratorService + SwissPairingService refactor** (single plan — they share a dependency on `MatchdayRepository.findByPhaseId*`)
  - Modify: `MatchdayGeneratorService.java` (`(phaseId, groupId, ...)` + GeneratorFormData reshape + LEAGUE/GROUPS layout-validation)
  - Modify: `SwissPairingService.java` (per-group isolation across all 4 methods + per-group bye + per-group complete)
  - Modify: `MatchdayRepository.java` (add 2 finders)
  - Update: `MatchdayGeneratorServiceTest.java`, `SwissPairingServiceTest.java`
  - Honours: SVC-04

### Wave 3 — Services that consume Wave 1+2 (sequential within wave)
- **Plan 58-04: PlayoffService + PlayoffSeedingService refactor**
  - Modify: `PlayoffService.java` (D-19 auto-create + canonical `phaseId` lookup + bridge for `getPlayoffListData(seasonId)`)
  - Modify: `PlayoffSeedingService.java` (D-15 Top-N from REGULAR-phase standings via `StandingsService.calculateStandings(regularPhase.id, null)`)
  - Modify: `PlayoffRepository.java` (add `findByPhaseId(UUID)`; existing `findBySeasonId` stays as deprecated bridge)
  - Modify: `PlayoffService.addRaceToMatchup` to set `matchday.phase = playoff.phase` (Pitfall 4 mitigation — small adjacent change)
  - Update: `PlayoffServiceTest.java`, `PlayoffSeedingServiceTest.java`
  - Honours: SVC-03

### Wave 4 — Caller-side updates and parallel API additions (PARALLEL)
- **Plan 58-05: SeasonManagementService + MatchdayService dual-API**
  - Modify: `SeasonManagementService.java` (D-18 strict-delete guard + D-25 auto-sync block)
  - Modify: `MatchdayService.java` (D-26 add `findByPhaseId` and `findByPhaseIdAndGroupId` parallel to `findBySeasonId`)
  - Update: `SeasonManagementServiceTest.java`, `MatchdayServiceTest.java`
  - Honours: D-18, D-25, D-26 (these support SVC-01..SVC-05 indirectly)

- **Plan 58-06: SiteGenerator caller-side update** (D-23)
  - Modify: `SiteGeneratorService.java` — replace direct `seasonId` calls with `findRegularPhase(seasonId).getId()` for standings/ranking calls; per-group standings NOT yet rendered.
  - Verification: existing `SiteGeneratorE2ETest` continues to pass — output bytewise-equal for migrated production seasons.
  - Honours: D-23 (cross-cutting; supports SVC-02 + SVC-05 in production)

### Wave 5 (optional gate) — Phase verification
Phase summary commit + JaCoCo gate + visual verification via `playwright-cli` (CLAUDE.md).

**Validation of orchestrator's proposed structure:**
- ✅ Wave 1 SeasonPhaseService first — correct (D-02 makes it the resolver everyone needs).
- ⚠️ "Wave 2 (parallel): StandingsService refactor, DriverRankingService refactor, MatchdayGeneratorService + SwissPairingService refactor" — adjusted: StandingsService and DriverRankingService share `RaceResultRepository` finder additions, so combine into one plan. SwissPairingService depends on StandingsService. Splitting into Plans 58-02 and 58-03 with `MatchdayGenerator+SwissPairing` together avoids merge conflicts.
- ✅ Wave 3 PlayoffService + PlayoffSeedingService — correct, depends on both Wave 1 (SeasonPhaseService) and Wave 2 (StandingsService).
- ✅ Wave 4 SeasonManagementService + MatchdayService + SiteGenerator — correct as the cleanup wave.

## Sources

### Primary (HIGH confidence — codebase-internal evidence)
- `.planning/phases/58-service-layer/58-CONTEXT.md` — locked decisions D-01..D-26.
- `.planning/REQUIREMENTS.md` — SVC-01..SVC-05 + traceability table.
- `.planning/STATE.md` — milestone progress + Key Technical Context.
- `.planning/ROADMAP.md` — phase boundary, success criteria.
- `.planning/phases/56-model-schema-foundation/56-CONTEXT.md` — Phase 56 entity contract.
- `.planning/phases/57-data-migration/57-CONTEXT.md` — data state Phase 58 inherits.
- `/Users/jegr/.claude/plans/ich-bin-mit-dem-pure-gem.md` — foundation document.
- `CLAUDE.md` — binding project constraints.
- `.planning/codebase/{ARCHITECTURE,CONVENTIONS,STACK,TESTING}.md` — existing patterns.
- `src/main/java/org/ctc/domain/service/{StandingsService,PlayoffService,PlayoffSeedingService,MatchdayGeneratorService,SwissPairingService,DriverRankingService,SeasonManagementService,MatchdayService}.java` — current implementations.
- `src/main/java/org/ctc/domain/model/{SeasonPhase,SeasonPhaseGroup,PhaseTeam,Race,Matchday,Playoff,Season,PhaseType,PhaseLayout}.java` — entity contracts.
- `src/main/java/org/ctc/domain/repository/{SeasonPhase,SeasonPhaseGroup,PhaseTeam,Race,RaceResult,Matchday,Match,Playoff,Season}Repository.java` — current finder contracts.
- `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — exception → HTTP mapping.
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — D-23 caller surface.
- `src/test/java/org/ctc/domain/service/*Test.java` — existing test pattern survey.
- `src/test/java/org/ctc/domain/model/SeasonPhaseEntityIntegrationTest.java` — Phase 56 IT-test pattern (precedent).
- `src/main/resources/db/migration/V3__add_season_phase_tables.sql` — schema reference.
- `.planning/config.json` — Nyquist gate enabled (`nyquist_validation: true`).

### Secondary (MEDIUM confidence — pattern inference from codebase)
- Spring Data JPA query-method derivation reference (D-22 magic-naming) — inferred from existing 4-step navigation finders working in production.
- Spring Boot 4 `@Transactional` propagation semantics — inferred from existing service-to-service composition examples.

### Tertiary (LOW confidence — flagged for validation)
- 5-step magic-name resolution `findByRacePlayoffMatchupRoundPlayoffPhaseId` — needs runtime verification at boot. Plan must include a JPQL fallback ready (Pitfall 1).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all standard from current codebase.
- Architecture: HIGH — pattern lifted from existing services; no new abstractions.
- Pitfalls: HIGH — verified by reading the affected source files; one MEDIUM-confidence item (Pitfall 1, 5-step magic-naming) flagged with concrete fallback.
- Plan decomposition: HIGH — adjusted from orchestrator's proposal based on Pitfall 6 (StandingsService↔SwissPairingService coupling).
- Validation Architecture: HIGH — test counts derived from `wc -l` and `@Test` grep over existing service tests.

**Research date:** 2026-04-27
**Valid until:** 2026-05-27 (30 days — stable codebase, no fast-moving framework upgrades expected)
