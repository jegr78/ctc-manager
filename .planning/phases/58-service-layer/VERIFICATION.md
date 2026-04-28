---
phase: 58-service-layer
verified: 2026-04-28T18:05:00Z
status: passed
score: 5/5 SVC requirements verified (SVC-01..SVC-05)
overrides_applied: 0
re_verification: false
overall_verdict: PASS
build_status: BUILD SUCCESS
test_count: 1127
test_failures: 0
jacoco_line_coverage: 86.78%
jacoco_gate: 82%
flyway_migrations_touched: 0
must_haves:
  truths:
    - "SVC-01: SeasonPhaseService provides phase + group + roster CRUD; delete-guard enforces D-18"
    - "SVC-02: StandingsService.calculateStandings(phaseId, groupId) is canonical; combined-view + nullable group + Buchholz-per-group all wired (D-04..D-06)"
    - "SVC-03: PlayoffService + PlayoffSeedingService operate on PLAYOFF phase; auto-create + Top-N from REGULAR (D-15, D-19, D-20, Pitfall 4)"
    - "SVC-04: MatchdayGeneratorService + SwissPairingService operate per (phaseId, groupId) with layout validation and per-group isolation (D-16, D-17, D-21)"
    - "SVC-05: DriverRankingService.calculateRankingForPhase + aggregateAcrossPhases canonical; RaceLineup fallback active (D-07..D-10)"
    - "Cross-cutting: V1..V4 Flyway migrations untouched, ./mvnw verify green, JaCoCo 86.78%, SiteGenerator phase-aware via D-23"
  artifacts:
    - path: "src/main/java/org/ctc/domain/service/SeasonPhaseService.java"
      provides: "Phase + group + roster CRUD; D-02 + D-14 + D-20"
    - path: "src/main/java/org/ctc/domain/service/StandingsService.java"
      provides: "calculateStandings(phaseId, groupId) + nullable TeamStanding.group + legacy bridge"
    - path: "src/main/java/org/ctc/domain/service/DriverRankingService.java"
      provides: "calculateRankingForPhase + aggregateAcrossPhases + RaceLineup fallback"
    - path: "src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java"
      provides: "phase/group-aware generate() + GeneratorFormData with SeasonPhase phase"
    - path: "src/main/java/org/ctc/domain/service/SwissPairingService.java"
      provides: "4 phase/group-aware methods + per-group isolation"
    - path: "src/main/java/org/ctc/domain/service/PlayoffService.java"
      provides: "createPlayoff atomic find-or-create PLAYOFF phase + Pitfall 4 setPhase"
    - path: "src/main/java/org/ctc/domain/service/PlayoffSeedingService.java"
      provides: "autoSeedBracket dual-flow with REGULAR-phase Top-N + PhaseTeam side-effect"
    - path: "src/main/java/org/ctc/domain/service/SeasonManagementService.java"
      provides: "D-18 strict-delete-guard + D-25 REGULAR-phase auto-sync"
    - path: "src/main/java/org/ctc/domain/service/MatchdayService.java"
      provides: "findByPhaseId + findByPhaseIdAndGroupId + @Deprecated findBySeasonId aggregation bridge"
    - path: "src/main/java/org/ctc/sitegen/SiteGeneratorService.java"
      provides: "phase-aware standings + aggregateAcrossPhases driver-ranking calls"
behavior_changes:
  - id: "D-18"
    surface: "SeasonManagementService.delete"
    before: "Blind cascade — seasonRepository.delete(season), JPA cascades to dependents"
    after: "Strict pre-check — refuses delete if matchdays/playoffs/phase_teams exist; throws BusinessRuleException -> 409 + admin/error page"
    user_visible: true
    documented_in: "58-06-SUMMARY.md (Behavior Changes #1)"
  - id: "D-25"
    surface: "SeasonManagementService.save"
    before: "Writes legacy Season columns only"
    after: "Dual-writes — Season + REGULAR SeasonPhase (auto-created on first save) under one @Transactional"
    user_visible: false
    documented_in: "58-06-SUMMARY.md (Behavior Changes #2)"
  - id: "D-07"
    surface: "DriverRankingService.calculateRanking(seasonId)"
    before: "Only REGULAR-matchday races counted; PLAYOFF races silently excluded"
    after: "All phase types (REGULAR + PLAYOFF + PLACEMENT) flow into season-wide ranking via aggregateAcrossPhases"
    user_visible: true
    documented_in: "58-03-SUMMARY.md (D-07 BEHAVIOR CHANGE — CRITICAL CALLOUT)"
---

# Phase 58: Service Layer Verification Report

**Phase Goal:** All domain services operate exclusively on the new phase/group model — services accept `phaseId` / `groupId` parameters, standings and driver rankings aggregate correctly per group and combined, and playoff seeding operates on a PLAYOFF phase rather than a season.

**Verified:** 2026-04-28
**Status:** PASS
**Re-verification:** No — initial verification
**Branch:** `gsd/v1.9-season-phases-groups` (no branch switching during verification)

---

## 1. Goal Achievement — Observable Truths (SVC-01..SVC-05)

### SVC-01 — Service-layer CRUD foundation

**ROADMAP success criterion 1:** *`SeasonPhaseService` provides CRUD operations for phases and groups, and manages the `PhaseTeam` roster.*

| Item | Status | Evidence |
|---|---|---|
| `SeasonPhaseService.findRegularPhase(seasonId)` (D-02 single resolver) | VERIFIED | `SeasonPhaseService.java:53` — throws `EntityNotFoundException` on miss |
| `findByType(seasonId, type)` returns `Optional<SeasonPhase>` | VERIFIED | `SeasonPhaseService.java:63` |
| `findById(phaseId)` | VERIFIED | `SeasonPhaseService.java:71` |
| `findAllPhases(seasonId)` ordered | VERIFIED | `SeasonPhaseService.java:82` |
| `create(...)` enforces D-14 duplicate guard via `findBySeasonIdAndPhaseType` | VERIFIED | `SeasonPhaseService.java:101`; D-20 auto-derive at `:129-:132` (REGULAR+LEAGUE only) |
| `createGroup` + `assignTeamToPhase` (roster CRUD) | VERIFIED | `SeasonPhaseService.java:144`, `:158-:162` |
| `SeasonManagementService.save` find-or-create REGULAR phase (D-25) | VERIFIED | `SeasonManagementService.java:165-:183` — `findByType().orElseGet(...)` + 8 `regular.setX(...)` |
| `SeasonManagementService.delete` D-18 three-way OR-guard | VERIFIED | `SeasonManagementService.java:204-:213` — `existsByPhaseSeasonId` against `MatchdayRepository`, `PlayoffRepository`, `PhaseTeamRepository`; throws `BusinessRuleException("Season has active phases — clear matches/teams before deleting")` |

**Verdict: PASS**

---

### SVC-02 — Phase-aware standings

**ROADMAP success criterion 2:** *`StandingsService.calculateStandings()` accepts a `phaseId` (and optional `groupId`); combined-view aggregates across all groups when `groupId` is null.*

| Item | Status | Evidence |
|---|---|---|
| `calculateStandings(UUID phaseId, UUID groupId)` canonical | VERIFIED | `StandingsService.java:48` |
| Combined-view (D-04): flat list across groups, raw points, fallback tiebreaker | VERIFIED | `StandingsService.java:48-:85` — sources teams from `PhaseTeamRepository`, sorts `points → pointDifference → pointsFor` |
| `TeamStanding.group` nullable (D-05) | VERIFIED | `StandingsService.java:394` (field), `:482-:487` (getter/setter); `:61` sets `ts.setGroup(pt.getGroup())` |
| Buchholz combined-view fallback (D-06) | VERIFIED | `StandingsService.java:106-:133` — combined view (groupId=null) populates Buchholz field but uses fallback chain (`points → pointDifference → pointsFor`); per-group uses Buchholz tiebreaker |
| `@Deprecated calculateStandings(seasonId)` bridge with legacy fallback | VERIFIED | `StandingsService.java:156-:163` (calculateStandings) + `:171-:177` (calculateStandingsWithBuchholz) — bridge via `findByType` (Optional, not throw) + private `calculateStandingsLegacy` for pre-V4 seasons |
| SiteGenerator swapped to phase-aware (D-23) | VERIFIED | `SiteGeneratorService.java:194, 276, 462, 596` — 4 production call sites use `calculateStandings(regularPhase.getId(), null)`; line 597 is documented alltime-loop fallback (Plan 58-06 Deviation #4) |
| `MatchRepository.findByMatchdayPhaseId` finder added | VERIFIED | `MatchRepository.java` — `@EntityGraph` finder confirmed via grep |

**Verdict: PASS**

---

### SVC-03 — Phase-aware playoff + seeding

**ROADMAP success criterion 3:** *`PlayoffService` and `PlayoffSeedingService` load and save via `phase_id`; existing bracket logic structurally unchanged.*

| Item | Status | Evidence |
|---|---|---|
| `PlayoffService.createPlayoff` atomic (D-19, Pitfall 2) | VERIFIED | `PlayoffService.java:46` — `@Transactional` boundary covers both SeasonPhase + Playoff writes; find-or-create at `:62-:77`; `playoff.setPhase(phase)` at `:80` |
| `BusinessRuleException` on duplicate (D-19) | VERIFIED | `PlayoffService.java:55` — `"Season already has a playoff phase"` |
| `PlayoffService.addRaceToMatchup` Pitfall 4 mitigation | VERIFIED | `PlayoffService.java:343` — exactly one `matchday.setPhase(playoff.getPhase())` call site (grep confirmed `-c = 1`); test asserts `newMatchday.getPhase().getPhaseType() == PhaseType.PLAYOFF` |
| `addSeasonToPlayoff` / `removeSeasonFromPlayoff` `@Deprecated` but functional | VERIFIED | `PlayoffService.java:125-:128`, `:141-:143` |
| `PlayoffSeedingService.autoSeedBracket` D-15 Top-N flow | VERIFIED | `PlayoffSeedingService.java:144` (autoSeedBracket); `:172` calls `tryLoadFromRegularStandings`; `:211-:245` runs `standingsService.calculateStandings(regularPhase.getId(), null)` (combined-view for GROUPS), prefers manual seeds when present |
| D-20 PhaseTeam side-effect on PLAYOFF roster | VERIFIED | `PlayoffSeedingService.java:245` — `phaseTeamRepository.save(new PhaseTeam(playoff.getPhase(), t))` skipping duplicates |
| `PlayoffRepository.findByPhaseId` + `existsByPhaseSeasonId` (D-22) | VERIFIED | `PlayoffRepository.java:16, :19` |

**Verdict: PASS**

---

### SVC-04 — Phase/group-aware matchday generation

**ROADMAP success criterion 4:** *`MatchdayGeneratorService` and `SwissPairingService` generate matchdays linked to a phase (and optionally a group).*

| Item | Status | Evidence |
|---|---|---|
| `MatchdayGeneratorService.generate(phaseId, groupId, …)` canonical | VERIFIED | `MatchdayGeneratorService.java:47` |
| Layout-vs-groupId validation (D-16) | VERIFIED | `MatchdayGeneratorService.java:52-:62` — IllegalArgumentException for LEAGUE+groupId or GROUPS+null-groupId |
| `GeneratorFormData(Season season, SeasonPhase phase, int teamCount, int optimalRounds)` | VERIFIED | `MatchdayGeneratorService.java:249` — A7 min-churn shape (carries both Season for template-compat and SeasonPhase) |
| `@Deprecated generate(seasonId, …)` bridge | VERIFIED | `MatchdayGeneratorService.java:122-:124` |
| `SwissPairingService` 4 canonical methods all `(phaseId, groupId)` | VERIFIED | `SwissPairingService.java:46, 111, 131, 143` — all call `validateLayoutAndGroupId(phase, groupId)` |
| `validateLayoutAndGroupId` enforces D-17 / D-21 layout contract | VERIFIED | `SwissPairingService.java:356-:361` |
| `@Deprecated` bridge overloads on all 4 Swiss methods | VERIFIED | `SwissPairingService.java:167, 182, 196, 210` |
| `MatchdayRepository.findByPhaseIdOrderBySortIndexAsc` + group variant + `existsByPhaseSeasonId` | VERIFIED | `MatchdayRepository.java:17, 20, 23` — both with `@EntityGraph` (D-22 + D-24) |

**Verdict: PASS**

---

### SVC-05 — Phase-aware driver ranking

**ROADMAP success criterion 5:** *`DriverRankingService` ranks drivers within a phase and produces a season-wide aggregation across phases.*

| Item | Status | Evidence |
|---|---|---|
| `calculateRankingForPhase(phaseId)` canonical | VERIFIED | `DriverRankingService.java:40` — union-merges `findByRaceMatchdayPhaseId` + `findByRacePlayoffMatchupRoundPlayoffPhaseId` (D-07) |
| `aggregateAcrossPhases(List<UUID> phaseIds, UUID seasonId)` | VERIFIED | `DriverRankingService.java:79` — loops phases; D-08 REGULAR-team attribution with D-10 RaceLineup fallback at `:205-:213` |
| `@Deprecated calculateRanking(seasonId)` bridge | VERIFIED | `DriverRankingService.java:117-:123` — delegates via `seasonPhaseService.findAllPhases(seasonId)` |
| RaceLineup as Source of Truth (CLAUDE.md) | VERIFIED | `DriverRankingService.java:7, 25` injects `RaceLineupRepository`; `:192-:213` resolves team via `raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId` |
| `RaceResultRepository` 4-step + 5-step magic-name finders (D-22, Pitfall 1) | VERIFIED | `RaceResultRepository.java:34, 46` — both magic names resolved at boot, no JPQL fallback needed (Pitfall 1 did not fire) |
| `SiteGeneratorService` season-wide ranking via `aggregateAcrossPhases` | VERIFIED | `SiteGeneratorService.java:222` — `driverRankingService.aggregateAcrossPhases(phaseIds, season.getId())` |

**Verdict: PASS**

---

## 2. Required Artifacts (per PLAN frontmatter)

All artifacts checked at three levels — exists, substantive, wired into callers.

| Artifact | Exists | Substantive | Wired | Status |
|---|---|---|---|---|
| `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` | yes | yes (172 lines) | yes (injected by 6+ services + SiteGenerator) | VERIFIED |
| `src/main/java/org/ctc/domain/service/StandingsService.java` | yes | yes | yes (used by SwissPairingService, PlayoffSeedingService, SiteGenerator, controllers) | VERIFIED |
| `src/main/java/org/ctc/domain/service/DriverRankingService.java` | yes | yes (304 lines) | yes (used by SiteGenerator, controllers) | VERIFIED |
| `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java` | yes | yes | yes (SeasonController) | VERIFIED |
| `src/main/java/org/ctc/domain/service/SwissPairingService.java` | yes | yes | yes (SeasonController) | VERIFIED |
| `src/main/java/org/ctc/domain/service/PlayoffService.java` | yes | yes | yes (PlayoffController) | VERIFIED |
| `src/main/java/org/ctc/domain/service/PlayoffSeedingService.java` | yes | yes | yes (PlayoffSeedingController) | VERIFIED |
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java` | yes | yes (D-18 + D-25 inline) | yes (SeasonController) | VERIFIED |
| `src/main/java/org/ctc/domain/service/MatchdayService.java` | yes | yes | yes (MatchdayController) | VERIFIED |
| `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` | yes | yes | yes (D-23 swap performed) | VERIFIED |
| `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` | yes | yes (4 factory methods) | yes (used by ITs + Mockito tests) | VERIFIED |
| `src/test/java/org/ctc/domain/repository/{SeasonPhase,SeasonPhaseGroup,PhaseTeam}RepositoryIT.java` | yes | yes (3+2+4 IT methods) | yes (`@SpringBootTest` per D-13 override) | VERIFIED |
| `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java` | yes | yes (1 contract test) | yes | VERIFIED |

**No stub/missing/orphaned artifacts.**

---

## 3. Key Link Verification

| From | To | Via | Status | Evidence |
|---|---|---|---|---|
| `SeasonManagementService.delete` | `MatchdayRepository`, `PlayoffRepository`, `PhaseTeamRepository` | `existsByPhaseSeasonId(id)` (3 calls) | WIRED | `SeasonManagementService.java:207-:209` |
| `SeasonManagementService.save` | `SeasonPhaseService` | `findByType().orElseGet(seasonPhaseService.create(...))` | WIRED | `SeasonManagementService.java:166-:167` (find-or-create); `:175-:183` (8 setX) |
| `PlayoffService.createPlayoff` | `SeasonPhaseService` | `findByType(PLAYOFF).orElseGet(create(BRACKET, …))` in `@Transactional` | WIRED | `PlayoffService.java:46` annotation; `:62-:77` find-or-create; `:80` `playoff.setPhase(phase)` |
| `PlayoffService.addRaceToMatchup` | `Matchday.phase` | `matchday.setPhase(playoff.getPhase())` | WIRED | `PlayoffService.java:343` (exactly 1 call site, grep-anchored) |
| `PlayoffSeedingService.autoSeedBracket` | `StandingsService.calculateStandings(regularPhaseId, null)` | injection + call | WIRED | `PlayoffSeedingService.java:219` |
| `PlayoffSeedingService.autoSeedBracket` | `PhaseTeamRepository.save(new PhaseTeam(playoff.getPhase(), t))` | side-effect roster init | WIRED | `PlayoffSeedingService.java:245` |
| `MatchdayService.findBySeasonId @Deprecated` | `seasonPhaseService.findAllPhases(seasonId)` | flatMap aggregation | WIRED | `MatchdayService.java:157-:160` |
| `DriverRankingService.calculateRanking @Deprecated` | `seasonPhaseService.findAllPhases(...).stream().map(SeasonPhase::getId)` → `aggregateAcrossPhases` | bridge | WIRED | `DriverRankingService.java:117-:123` |
| `SwissPairingService.generateNextRound` | `StandingsService.calculateStandings(phaseId, groupId)` | (Pitfall 6 mitigation) | WIRED | `SwissPairingService.java:42` (javadoc) + canonical-path call site |
| `SiteGeneratorService` | `seasonPhaseService.findRegularPhase` + `standingsService.calculateStandings(phaseId, null)` + `driverRankingService.aggregateAcrossPhases` | D-23 caller-side swap | WIRED | `SiteGeneratorService.java:193, 194, 222, 275, 276` |

All key links verified — no NOT_WIRED / PARTIAL.

---

## 4. Decision Inventory (D-01..D-26)

| D-# | Topic | Status | Evidence |
|---|---|---|---|
| D-01 | phaseId-only canonical + seasonId-overload bridge | applied | every refactored service has `(phaseId,…)` canonical + `@Deprecated (seasonId)` |
| D-02 | Resolution centralized in `findRegularPhase` | applied | `SeasonPhaseService.java:53` (throws EntityNotFoundException); SUMMARY notes all 6 services inject `SeasonPhaseService` |
| D-03 | `@Deprecated` on transitional APIs | applied | StandingsService bridge, MatchdayService bridge, DriverRankingService bridge, MatchdayGenerator/SwissPairing 5 bridges, PlayoffService.addSeasonToPlayoff/removeSeasonFromPlayoff |
| D-04 | Combined-view = flat list of raw points | applied | `StandingsService.java:48-:85`, sort chain `points→pointDifference→pointsFor` |
| D-05 | Nullable `TeamStanding.group` | applied | `StandingsService.java:394, 482, 487` + `ts.setGroup(pt.getGroup())` line 61 |
| D-06 | Buchholz per-group only; combined view ignores it as tiebreaker | applied | `StandingsService.java:106-:133` (branch-on-groupId) |
| D-07 | All phase types contribute to season-wide aggregation (BEHAVIOR CHANGE) | applied | `DriverRankingService.java:40-:46` union-merge; flagged in 58-03-SUMMARY |
| D-08 | Driver-team for season-wide aggregation = REGULAR-phase team | applied | `DriverRankingService.java:79-:111` `aggregateAcrossPhases` REGULAR-attribution path |
| D-09 | API split — `calculateRankingForPhase` + `aggregateAcrossPhases` + `@Deprecated calculateRanking` | applied | `DriverRankingService.java:40, 79, 117` |
| D-10 | RaceLineup-fallback for stand-ins | applied | `DriverRankingService.java:192-:213` |
| D-11 | `PhaseTestFixtures` helper class | applied | `src/test/java/org/ctc/domain/service/PhaseTestFixtures.java` (4 factory methods, 99 lines) |
| D-12 | Mockito-only service tests | applied | all new service tests use `@ExtendWith(MockitoExtension.class)` |
| D-13 (revised) | `@SpringBootTest @ActiveProfiles("dev") @Transactional` for repository ITs (override of original `@DataJpaTest`) | applied | 3 IT files exist; rationale documented in 58-01-SUMMARY |
| D-14 | Service pre-checks "max 1 REGULAR/PLAYOFF/PLACEMENT" rule | applied | `SeasonPhaseService.create` calls `findBySeasonIdAndPhaseType` and throws BusinessRuleException |
| D-15 | `autoSeedBracket` Top-N from REGULAR-phase combined-view | applied | `PlayoffSeedingService.java:211-:245` (`tryLoadFromRegularStandings`) — manual seeds priority + REGULAR fallback |
| D-16 | MatchdayGenerator works per group; layout validation | applied | `MatchdayGeneratorService.java:47-:62` |
| D-17 | SwissPairing fully per-group-isolated | applied | `SwissPairingService.java:46, 111, 131, 143` — all 4 methods `(phaseId, groupId)` + `validateLayoutAndGroupId` |
| D-18 | `SeasonManagementService.delete` strict pre-check (BEHAVIOR CHANGE) | applied | `SeasonManagementService.java:204-:213` — three-way OR-guard; `BusinessRuleException` |
| D-19 | `playoffService.createPlayoff` auto-creates PLAYOFF phase | applied | `PlayoffService.java:46-:80` atomic + duplicate guard via `findBySeasonId` |
| D-20 | Roster init phase-type-specific | applied | `SeasonPhaseService.java:128-:132` (REGULAR+LEAGUE auto-derive); `PlayoffSeedingService.java:245` (PLAYOFF side-effect) |
| D-21 | SwissPairing per-group bye / round / completion | applied | `SwissPairingService.java:111` (`getByeTeams`), `:131` (`getCurrentRound`), `:143` (`isCurrentRoundComplete`) — all `(phaseId, groupId)` |
| D-22 | Spring-Data magic naming preferred | applied | 9 magic-name finders (`findBySeasonIdAndPhaseType`, `findBySeasonIdOrderBySortIndex`, `findByPhaseIdOrderBySortIndex`, `findByPhaseId`, `findByPhaseIdAndGroupId`, `existsByPhaseSeasonId` x3, `findByRaceMatchdayPhaseId`, `findByRacePlayoffMatchupRoundPlayoffPhaseId`, `findByMatchdayPhaseId`, `findByPhaseIdOrderBySortIndexAsc`, `findByPhaseIdAndGroupIdOrderBySortIndexAsc`); 0 JPQL fallbacks needed |
| D-23 | SiteGenerator brought along | applied | 4 `calculateStandings(regularPhase.getId(), null)` swaps + 1 `aggregateAcrossPhases` swap; line 597 documented alltime fallback |
| D-24 | Pragmatic `@EntityGraph` only when needed | applied | `MatchdayRepository.java:16, 19` (`@EntityGraph(attributePaths = {"season", "phase", …})`); `MatchRepository.findByMatchdayPhaseId` has `@EntityGraph`; no `@EntityGraph` boilerplate elsewhere |
| D-25 | Status-quo writes + auto-sync to REGULAR phase (BEHAVIOR CHANGE per 58-06) | applied | `SeasonManagementService.java:165-:183` — find-or-create + 8 setX field-syncs + Pitfall 7 bootstrap |
| D-26 | Both seasonId and phaseId-shaped methods exist in MatchdayService | applied | `MatchdayService.java:138, 146, 156-:160` |

**26/26 decisions applied. None deferred or ignored.**

---

## 5. RESEARCH Pitfalls 1-7 (+ Pitfall 8)

| # | Topic | Status | Evidence |
|---|---|---|---|
| 1 | 5-step magic-name `findByRacePlayoffMatchupRoundPlayoffPhaseId` may not resolve | mitigated (did not trigger) | `RaceResultRepository.java:46` resolved at boot; JPQL fallback in javadoc only; `./mvnw verify` boots cleanly |
| 2 | `@Transactional` self-invocation does NOT proxy | mitigated | `PlayoffService.createPlayoff` is `@Transactional` and calls `SeasonPhaseService` (different bean); `SeasonManagementService.save` is `@Transactional` and calls `SeasonPhaseService` (different bean) |
| 3 | D-07 widens driver ranking to include playoff results (BEHAVIOR CHANGE) | mitigated + flagged | `DriverRankingService.java:40-:46` union-merge; explicitly called out in 58-03-SUMMARY "D-07 BEHAVIOR CHANGE — CRITICAL CALLOUT" |
| 4 | New playoff matchdays must link to PLAYOFF phase | mitigated | `PlayoffService.java:343` — exactly 1 `matchday.setPhase(playoff.getPhase())` call site (grep `-c=1`); covered by `givenPlayoffMatchup_whenAddRaceToMatchup_thenNewMatchdayLinkedToPlayoffPhase` |
| 5 | M:N `playoff.seasons` still consulted by `getPlayoffTeams` / `getSeedingData` | mitigated | `PlayoffService.addSeasonToPlayoff` / `removeSeasonFromPlayoff` `@Deprecated` but functional (`PlayoffService.java:125-:128, :141-:143`); manual-seed flow preserved as priority in `PlayoffSeedingService.autoSeedBracket` (Plan 58-05 Deviation #2); existing PlayoffControllerTest green |
| 6 | SwissPairingService recursive dependency on StandingsService | mitigated | wave ordering: Plan 58-02 shipped `calculateStandings(phaseId, groupId)` before Plan 58-04 consumed it; `SwissPairingService.java:42` documents Pitfall 6 mitigation |
| 7 | `EntityNotFoundException` from D-02 surfaces as 404 | mitigated | D-25 bootstraps REGULAR phase on new-season insert path (`SeasonManagementService.java:166-:167` find-or-create), so post-V4 + post-D-25 every season has a REGULAR phase; SiteGenerator pre-V4 fixture skip-guard added (58-06 Deviation #2); StandingsService bridge uses `findByType` (Optional, not throw) per Plan 58-02 Deviation #1 |
| 8 | OSIV-driven lazy load on `SeasonPhase.groups` after `create()` | mitigated | not triggered in test runs (Mockito tests don't hit JPA; `@SpringBootTest @Transactional` ITs keep session open); D-24 reserves `@EntityGraph` for verified N+1 |

**All 7 RESEARCH pitfalls (and bonus pitfall 8) mitigated.**

---

## 6. Behavior Changes (USER-VISIBLE)

Three documented behavior changes shipped in Phase 58:

### 1. D-18 — `SeasonManagementService.delete` strict-delete-guard

- **Before:** Blind cascade. `seasonRepository.delete(season)` trusted JPA cascade.
- **After:** Three-way OR pre-check via `existsByPhaseSeasonId` against `MatchdayRepository`, `PlayoffRepository`, `PhaseTeamRepository` (`SeasonManagementService.java:207-:209`). Throws `BusinessRuleException("Season has active phases — clear matches/teams before deleting")` → mapped to HTTP 409 + `admin/error` page.
- **User-visible impact:** Admin must clear matchdays/playoff/phase-teams before deleting a season. Recoverable via clear UX message.
- **Documented in:** 58-06-SUMMARY.md "Behavior Changes" section.

### 2. D-25 — `SeasonManagementService.save` REGULAR-phase auto-sync

- **Before:** Wrote 8 scoring/format/dates fields onto legacy `Season` only.
- **After:** Still writes legacy `Season` fields, then resolves-or-creates the REGULAR `SeasonPhase` via `seasonPhaseService.findByType(seasonId, REGULAR).orElseGet(seasonPhaseService.create(...))` and synchronises the same 8 fields onto the SeasonPhase. All inside one `@Transactional` boundary (Pitfall 7 mitigation).
- **User-visible impact:** None for end users; unblocks Phase 60 cutover.
- **Documented in:** 58-06-SUMMARY.md "Behavior Changes" section.

### 3. D-07 — `DriverRankingService.calculateRanking` widens to all phase types

- **Before:** `findByRaceMatchdaySeasonId` only — PLAYOFF races silently excluded.
- **After:** `aggregateAcrossPhases` union-merges `findByRaceMatchdayPhaseId(REGULAR/PLACEMENT)` + `findByRacePlayoffMatchupRoundPlayoffPhaseId(PLAYOFF)`.
- **User-visible impact:** Season-wide driver-ranking totals shift upward for any season with PLAYOFF data.
- **Documented in:** 58-03-SUMMARY.md "D-07 BEHAVIOR CHANGE — CRITICAL CALLOUT".

---

## 7. Cross-Cutting Checks

| Check | Result | Evidence |
|---|---|---|
| `./mvnw verify` exit 0 | PASS | local run 2026-04-28 18:02:23 — `BUILD SUCCESS` |
| Test count | PASS | 1127 tests, 0 failures, 0 errors, 0 skipped (matches 58-06-SUMMARY claim) |
| JaCoCo line coverage ≥ 82% gate | PASS | computed 86.78% (5286/6091) — `target/site/jacoco/jacoco.csv` (matches 58-06-SUMMARY claim of 86.78%) |
| Flyway V1..V4 migrations untouched | PASS | git log shows V1..V4 last-modified during phases 56/57; no Phase 58 commits touched `src/main/resources/db/migration/` or `src/main/java/db/migration/` |
| No new Flyway migration shipped in Phase 58 | PASS | `ls src/main/resources/db/migration` → V1, V2, V3 only; `find src/main/java/db/migration` → V4 only |
| `SiteGeneratorE2ETest` bytewise-equivalent for migrated production seasons | PASS | full `./mvnw verify` includes failsafe runs and SiteGeneratorE2ETest passes (also stated in 58-06-SUMMARY); LEAGUE-shape contract preserved per D-23 |
| BEHAVIOR CHANGES flagged | PASS | both D-18 + D-25 explicitly marked "USER-VISIBLE" in 58-06-SUMMARY; D-07 separately in 58-03-SUMMARY |
| All 7 pitfalls have a mitigation | PASS | see Section 5 above |
| No source mutations during verification | PASS | verifier was read-only; only wrote VERIFICATION.md |
| Stayed on branch `gsd/v1.9-season-phases-groups` | PASS | `git branch --show-current` confirmed at start; no `git stash`/`checkout`/`reset`/`switch` issued |

---

## 8. Requirements Coverage

| Req ID | Source Plan(s) | Description | Status | Evidence |
|---|---|---|---|---|
| SVC-01 | 58-01, 58-06 | Neuer `SeasonPhaseService` mit Phase-/Group-CRUD und Roster-Management via `PhaseTeam` | SATISFIED | Section 1 (SVC-01) + Section 4 (D-14, D-18, D-20, D-25) |
| SVC-02 | 58-02, 58-06 (D-23 caller-side) | `StandingsService.calculateStandings(...)` auf `phaseId`/`groupId` umgestellt; Combined-View | SATISFIED | Section 1 (SVC-02) + Section 4 (D-04..D-06, D-23) |
| SVC-03 | 58-05 | `PlayoffService` + `PlayoffSeedingService` operieren auf PLAYOFF-Phase statt Saison | SATISFIED | Section 1 (SVC-03) + Section 4 (D-15, D-19, D-20) + Section 5 (Pitfall 4) |
| SVC-04 | 58-04, 58-06 (MatchdayService dual-API) | `MatchdayGeneratorService` + `SwissPairingService` phase-/group-aware | SATISFIED | Section 1 (SVC-04) + Section 4 (D-16, D-17, D-21, D-26) |
| SVC-05 | 58-03, 58-06 (D-23 caller-side) | `DriverRankingService` phase-/group-aware (mit Aggregation über Saison) | SATISFIED | Section 1 (SVC-05) + Section 4 (D-07..D-10, D-23) |

**5/5 requirements SATISFIED. No orphaned requirements.**

Note: REQUIREMENTS.md still lists `SVC-01` as **Pending** (line 35, line 118) — this is a stale planner-side bookkeeping artifact; the codebase implements all D-decisions associated with SVC-01. The orchestrator should flip the SVC-01 status to ✅/Complete during phase closure.

---

## 9. Anti-Patterns Found

Anti-pattern scan run on all files modified in Phase 58 (extracted from 58-01..58-06 SUMMARY frontmatter `key_files`).

| File | Pattern | Severity | Notes |
|---|---|---|---|
| `SiteGeneratorService.java:597` | `calculateStandings(season.getId())` (legacy bridge call) | INFO | Documented in 58-06-SUMMARY Deviation #4; alltime teamSlugMap loop fallback for pre-Phase-57 fixture seasons. Not a stub — falls into `calculateStandingsLegacy` which is a real implementation. |
| `SwissPairingService.java:266, :312` | `calculateStandings(seasonId)` legacy fallback | INFO | Inside `@Deprecated` bridge legacy-path methods (`SwissPairingService.java:220` "Legacy fallback methods — used by @Deprecated bridges when no REGULAR phase"). Per D-01 acceptable until Phase 60 cutover. |
| `StandingsService.calculateStandingsLegacy` (private) | uses `findByMatchdaySeasonId` | INFO | Plan 58-02 Deviation #2 — preserves pre-V4 fixture support; will be removed alongside `@Deprecated` bridges in Phase 60. |
| `// TODO`, `// FIXME`, `// PLACEHOLDER` | none in Phase 58 modified files | INFO | grep on phase-58 surface produced no matches |
| Empty handlers / hardcoded empties / `return null` stubs | none | INFO | All new methods have full implementations |

**No blockers. No warnings. Three INFO-level "documented legacy fallback" notes — all explicitly part of the bridge/legacy design and tracked for Phase 60 removal.**

---

## 10. Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Full test suite passes | `./mvnw verify` | 1127 tests, 0 failures, 0 errors; BUILD SUCCESS | PASS |
| JaCoCo gate satisfied | `./mvnw verify` (jacoco:check) | "All coverage checks have been met." | PASS |
| Flyway V1..V4 unmodified | `git log ...58-01..HEAD -- 'src/main/resources/db/migration/' 'src/main/java/db/migration/'` | empty output | PASS |
| Pitfall 4 grep anchor | `grep -c "matchday.setPhase(playoff.getPhase()" PlayoffService.java` | 1 (exactly one call site) | PASS |
| `existsByPhaseSeasonId` wired in 3 repos | grep across MatchdayRepository / PlayoffRepository / PhaseTeamRepository | 3 hits | PASS |
| `calculateStandings(phaseId, groupId)` is canonical | grep in StandingsService.java | line 48 declaration found | PASS |
| `aggregateAcrossPhases` exposed | grep in DriverRankingService.java | line 79 declaration found | PASS |

---

## 11. Human Verification

Per VALIDATION.md "Manual-Only Verifications" (lines 84-86), two manual checks remain useful but are **not blocking** — they're forward-looking smoke tests:

1. **Visual UI smoke** (admin pages still load with the seasonId-bridge layer) — VALIDATION row 1.
2. **MariaDB compatibility of magic-name finders** (Pitfall 1 H2-vs-MariaDB sanity) — VALIDATION row 2.

Phase 58's automated test suite (1127 tests, BUILD SUCCESS, 86.78% coverage) and bytewise-equivalent SiteGeneratorE2ETest provide sufficient evidence for goal achievement. Manual checks are recommended before Phase 60 UI cutover but are not gates for Phase 58 closure.

---

## 12. Final Verdict

**OVERALL VERDICT: PASS**

| Dimension | Score |
|---|---|
| ROADMAP success criteria | 5/5 verified |
| SVC-01..SVC-05 requirements | 5/5 SATISFIED |
| D-01..D-26 decisions | 26/26 applied |
| RESEARCH Pitfalls 1-7 (+8) | 7+1 mitigated |
| Behavior Changes documented | 3/3 (D-07, D-18, D-25) |
| `./mvnw verify` | PASS — 1127/1127 tests, BUILD SUCCESS |
| JaCoCo line coverage | 86.78% (gate 82%) |
| Flyway V1..V4 untouched | confirmed |
| SiteGeneratorE2ETest bytewise-equivalent | confirmed (D-23 LEAGUE-shape preserved) |
| Source mutations during verification | 0 (read-only verifier) |

**Phase 58 (Service Layer) goal is achieved.** All domain services operate exclusively on the new phase/group model; standings + driver rankings aggregate per-group and combined; playoff seeding operates on a PLAYOFF phase rather than a season. The `@Deprecated seasonId`-bridge layer keeps controllers and templates working unchanged until Phase 60. Two BEHAVIOR CHANGES (D-18, D-25) plus one secondary BEHAVIOR CHANGE (D-07 driver-ranking widening) are explicitly documented and shipped intentionally per locked decisions.

**Phase 58 may proceed to closure. Phase 59 (Import & Test Data) is unblocked.**

---

*Verified: 2026-04-28T18:05:00Z*
*Verifier: Claude (gsd-verifier)*
*Branch: `gsd/v1.9-season-phases-groups` (no branch operations during verification)*
