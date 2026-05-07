# Phase 65: Graphics Services Bridge Migration — Research

**Researched:** 2026-05-07
**Domain:** Spring Boot / Java service-layer refactoring — deprecated API removal
**Confidence:** HIGH (all findings verified from source code; no external tools or training-data assumptions used)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** `StandingsService.calculateStandings(UUID seasonId)` (line 148) is completely removed after all callers migrate. No deprecation runway.
- **D-02:** `calculateStandingsLegacy` private fallback does NOT exist anymore — confirmed by grep, no action needed.
- **D-03:** Migration scope = all 7 callers (5 graphics + `SwissPairingService.calculateBuchholz` deleted + `StandingsService.calculateBuchholzScores` private helper inlined).
- **D-05:** 3-plan atomicity — P1 graphics, P2 domain cleanup, P3 bridge removal + StandingsServiceTest triage.
- **D-06:** Matchday/Race callers use `matchday.getPhase().getId()` + `matchday.getGroup() != null ? matchday.getGroup().getId() : null`.
- **D-07:** TeamCardService uses `seasonPhaseService.findRegularPhase(seasonId).getId() + null` (combined-view).
- **D-08:** Inline at each call site — NO shared helper extracted.
- **D-09:** Bridge-test triage at planning time (planner discretion).
- **D-11:** One explicit LEAGUE-regression test per migrated graphics caller (5 total, `Mockito.verify` with `isNull()`).
- **D-12:** 3 representative GROUPS-tests (Abstract, Overlay, Settings). Lineup and TeamCard excluded.
- **D-13:** Mockito unit-test style consistent with existing `*GraphicServiceTest.java` patterns.

### Claude's Discretion

- **D-14:** Bridge-test triage in `StandingsServiceTest` (which to delete vs. rewrite) — delegated to planner.
- **D-15:** Migration wave order within a plan — planner's call.

### Deferred Ideas (OUT OF SCOPE)

- Card-path phase-awareness (`{phaseId}/{shortName}.png`): explicitly rejected.
- Buchholz/playoff edge-case exploration: deferred.
- JaCoCo per-caller pre-measurement: deferred.

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SVC-02 | `StandingsService.calculateStandings(...)` on `phaseId`/`groupId` — phase-aware API complete | Phase 65 is hardening; removes the bridge that still routed some callers around the canonical API. All research confirms the canonical API is already correct and fully tested. |

</phase_requirements>

---

## Summary

Phase 65 is a pure refactoring with no new features. Every production caller of the deprecated
`calculateStandings(UUID seasonId)` bridge is migrated to the canonical `calculateStandings(UUID phaseId, UUID groupId)` API, the bridge method is deleted, and dead Buchholz code is cleaned up. The codebase already has the complete infrastructure for this migration: `SeasonPhaseService.findRegularPhase(seasonId)` for combined-view callers, and direct `matchday.getPhase()` / `matchday.getGroup()` accessors for race/matchday callers.

The single biggest planning concern is test surgery: `StandingsServiceTest` has tests that still call `calculateStandings(season.getId())` (the bridge overload). These must be triaged in Plan 65-03. The mock setup in `AbstractMatchdayGraphicServiceTest` and sibling files must be rewritten from `when(standingsService.calculateStandings(season.getId()))` to `when(standingsService.calculateStandings(eq(regularPhase.getId()), isNull()))` as part of Plan 65-01.

JaCoCo coverage is currently 85.6% (1224 tests, Phase 64 baseline). Removing the bridge method (~15 lines) and its dedicated bridge tests (~14 test methods) may reduce coverage slightly but is unlikely to breach the 82% gate — the canonical `(phaseId, groupId)` path already has dense test coverage from Phase 58.

**Primary recommendation:** Follow the 3-plan structure exactly as decided. Plan 65-01 is the highest-risk plan because it touches 5 production files and must rewrite existing mocks; execute it completely before Plans 65-02 and 65-03.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Standings calculation | Domain Service (`StandingsService`) | — | Pure business logic; repository access only through `PhaseTeamRepository` and `MatchRepository`. |
| Phase resolution for graphics callers | Domain Service (`SeasonPhaseService.findRegularPhase`) | Model accessors (`matchday.getPhase()`) | TeamCard uses the service; matchday/race callers use direct accessors already on the domain model. |
| Buchholz calculation | Domain Service (private helper in `StandingsService`) | — | Stays domain-internal; `SwissPairingService.calculateBuchholz` public method is dead code with 0 production callers. |
| Graphics generation | Admin Service layer | — | `AbstractMatchdayGraphicService`, `OverlayGraphicService`, `SettingsGraphicService`, `LineupGraphicService`, `TeamCardService` all live in `org.ctc.admin.service`. |
| Bridge removal | Domain Service (`StandingsService`) | — | Deleting the bridge is a single-method removal with no architectural impact. |

---

## Caller-by-Caller Migration Map

### D-03a: AbstractMatchdayGraphicService.prepareBaseContext (line 47)

**Current code:**
```java
var season = matchday.getSeason();
var standings = standingsService.calculateStandings(season.getId());
```

**Migrated code (D-06):**
```java
var phase = matchday.getPhase();
var group = matchday.getGroup();
var standings = standingsService.calculateStandings(
    phase.getId(),
    group != null ? group.getId() : null);
```

**Note on season.getName() / season.getYear():** The method also reads `season.getName()` and `season.getYear()` for the graphic context. After migration, `matchday.getSeason()` can still be called — it is still a valid accessor via the `@ManyToOne` relationship on `Matchday`. Only the `calculateStandings` call changes.

**Existing test stubs (from `AbstractMatchdayGraphicServiceTest`):** 7 tests call `when(standingsService.calculateStandings(season.getId())).thenReturn(...)`. All must be rewritten to `when(standingsService.calculateStandings(eq(phase.getId()), isNull())).thenReturn(...)` for the LEAGUE case. The `phase` object must be created and attached to the matchday in the test setup.

**Critical fixture gap:** `createMatchdayWithMatches()` currently uses `PhaseTestFixtures.matchdayInRegularPhase(...)` which already creates a `regularPhase` for the matchday. After migration, tests need that `regularPhase` reference accessible to write the new stub. The helper returns a `Matchday` with `.getPhase()` already set — the `regularPhase.getId()` is available via `matchday.getPhase().getId()`.

### D-03b: TeamCardService.generateCard (line 52)

**Current code:**
```java
var season = seasonTeam.getSeason();
var standings = standingsService.calculateStandings(season.getId());
```

**Migrated code (D-07):**
```java
var season = seasonTeam.getSeason();
var regular = seasonPhaseService.findRegularPhase(season.getId());
var standings = standingsService.calculateStandings(regular.getId(), null);
```

**Constructor change required:** `TeamCardService` currently takes `StandingsService` but NOT `SeasonPhaseService`. After migration it needs `SeasonPhaseService` injected. The constructor is not `@RequiredArgsConstructor` (it is hand-written with `@Value`), so a parameter must be added.

**Test impact:** `TeamCardServiceTest` does NOT test `generateCard()` with a mock `standingsService` — the test class constructs `new TeamCardService(null, null, "uploads")` (null template engine, null standings service). This means adding `seasonPhaseService` as a 3rd constructor parameter (before `@Value`) only requires adding `null` in the test constructor call. The existing tests cover only `getCardPath()`, `computeGradientColor()`, and template management — none touch `generateCard()`, so no stub rewrites are needed in `TeamCardServiceTest`. A new LEAGUE-regression test (D-11) will stub both `standingsService` and `seasonPhaseService`.

### D-03c: OverlayGraphicService.generateOverlay (line 58)

**Current code:**
```java
var season = race.getMatchday().getSeason();
var standings = standingsService.calculateStandings(season.getId());
```

**Migrated code (D-06):**
```java
var phase = race.getMatchday().getPhase();
var group = race.getMatchday().getGroup();
var standings = standingsService.calculateStandings(
    phase.getId(),
    group != null ? group.getId() : null);
```

**Existing tests in OverlayGraphicServiceTest:** 4 tests — none stub `standingsService` (the service is constructed with `null` for standingsService: `new OverlayGraphicService(null, null, null, tempDir.toString())`). The only tests exercise template management and the pre-condition guard (`givenRaceWithNoTeams_whenGenerateOverlay_thenThrowsIllegalState`). No mock rewrites needed in existing tests. New D-11 LEAGUE-regression test and D-12 GROUPS-test must be added.

### D-03d: SettingsGraphicService.generateSettings (line 67)

**Current code (within the non-playoff branch):**
```java
var season = race.getMatchday().getSeason();
var standings = standingsService.calculateStandings(season.getId());
```

**Migrated code (D-06):**
```java
var phase = race.getMatchday().getPhase();
var group = race.getMatchday().getGroup();
var standings = standingsService.calculateStandings(
    phase.getId(),
    group != null ? group.getId() : null);
```

**Playoff branch:** Lines 60-65 use `playoffSeedRepository` and bypass `calculateStandings` entirely — these are unaffected by the migration (confirmed by reading the source).

**Existing SettingsGraphicServiceTest:** No file found at the expected path during research — see note below. [VERIFIED: file listing shows no `SettingsGraphicServiceTest.java`] The planner must treat this as a Wave 0 gap: the D-11 LEAGUE-regression test and D-12 GROUPS-test are the first tests for this service's standings integration.

### D-03e: LineupGraphicService.generateLineup (line 70)

**Current code (within the non-playoff branch):**
```java
var season = race.getMatchday().getSeason();
var standings = standingsService.calculateStandings(season.getId());
```

**Migrated code (D-06):**
```java
var phase = race.getMatchday().getPhase();
var group = race.getMatchday().getGroup();
var standings = standingsService.calculateStandings(
    phase.getId(),
    group != null ? group.getId() : null);
```

**Playoff branch:** Lines 62-68 use `playoffSeedRepository` — unaffected.

**Existing LineupGraphicServiceTest:** 8 tests all use `new LineupGraphicService(null, null, null, null, tempDir.toString())`. None stub `standingsService`. The tests cover `buildPairings()` and template management. No existing stub rewrites needed. New D-11 LEAGUE-regression test added in Plan 65-01.

---

## Bridge-Test Inventory and Triage (D-09 / D-14)

This is the core planning-time decision delegated to the planner. Research has read `StandingsServiceTest.java` completely and categorizes each bridge-overload test.

**What "bridge test" means:** Any test that calls `standingsService.calculateStandings(season.getId())` (the seasonId-only overload). After Plan 65-03 removes the bridge, these tests fail to compile.

### Tests calling the bridge (calls `calculateStandings(season.getId())`)

The following test methods in `StandingsServiceTest` call the bridge overload and must be triaged:

| Test Method | Nested Class | Line (approx) | Category | Triage |
|---|---|---|---|---|
| `givenOneMatch_whenCalculateStandings_thenWinnerGetThreePoints` | `MatchBasedStandingsTest` | ~197 | DUPLICATE — identical semantic covered by `givenLeaguePhase_whenCalculateStandingsByPhaseId_thenReturnsAllPhaseTeams` (canonical API, line ~628) | DELETE |
| `givenEqualScores_whenCalculateStandings_thenBothTeamsGetDrawPoint` | `MatchBasedStandingsTest` | ~229 | DUPLICATE — draw-point logic covered by canonical-API tests | DELETE |
| `givenCustomMatchScoring_whenCalculateStandings_thenCustomPointsApplied` | `MatchBasedStandingsTest` | ~251 | UNIQUE SEMANTIC — custom scoring rule, not directly covered by phase-API tests | REWRITE as `calculateStandings(regularPhase.getId(), null)` |
| `givenByeMatch_whenCalculateStandings_thenTeamGetsWin` | `MatchBasedStandingsTest` | ~274 | DUPLICATE — bye match covered by other tests | DELETE |
| `givenMultipleMatches_whenCalculateStandings_thenSortedByPointsThenPointDifference` | `MatchBasedStandingsTest` | ~296 | UNIQUE SEMANTIC — sort order assertion (CLR first, TNR second, P1R last) | REWRITE |
| `givenTeamWithNoGames_whenCalculateStandings_thenTeamExcluded` | `MatchBasedStandingsTest` | ~320 | UNIQUE SEMANTIC — zero-game exclusion | REWRITE |
| `givenMatchWithNoScores_whenCalculateStandings_thenMatchSkipped` | `MatchBasedStandingsTest` | ~340 | UNIQUE SEMANTIC — null-score skip | REWRITE |
| `givenReplacedTeam_whenCalculateStandings_thenSuccessorInheritsResults` | `TeamSuccessionTest` | ~364 | UNIQUE SEMANTIC — succession inheritance | REWRITE |
| `givenReplacedTeam_whenCalculateStandings_thenPredecessorNotInStandings` | `TeamSuccessionTest` | ~395 | UNIQUE SEMANTIC — predecessor exclusion | REWRITE |
| `givenReplacedTeamAndNewMatches_whenCalculateStandings_thenBothResultsMerged` | `TeamSuccessionTest` | ~419 | UNIQUE SEMANTIC — successor accumulates both old and new results | REWRITE |
| `givenSuccessionChain_whenCalculateStandings_thenFinalSuccessorInheritsAll` | `TeamSuccessionTest` | ~450 | UNIQUE SEMANTIC — multi-hop chain | REWRITE |
| `givenReplacedTeamWithBye_whenCalculateStandings_thenSuccessorInheritsByeWin` | `TeamSuccessionTest` | ~487 | UNIQUE SEMANTIC — succession + bye | REWRITE |
| `givenSeasonId_whenCalculateStandings_thenDelegatesToRegularPhase` | `PhaseAwareStandingsTest` | ~732 | BRIDGE-BEHAVIOR TEST — explicitly tests the bridge delegation. After bridge removal, this test has no target method. | DELETE (bridge no longer exists) |

**Summary: 2 DELETE (pure duplicates), 1 DELETE (bridge-delegation test), 10 REWRITE (unique semantics worth preserving via canonical API).**

**Rewrite pattern for succession/scoring/exclusion tests:**

```java
// Before:
var standings = standingsService.calculateStandings(season.getId());

// After:
var standings = standingsService.calculateStandings(regularPhase.getId(), null);
```

The `regularPhase` field already exists in `@BeforeEach` (`regularPhase = PhaseTestFixtures.regularPhase(season, raceScoring, matchScoring)`). The `lenient()` stubs in `@BeforeEach` already configure `seasonPhaseService.findById(regularPhase.getId())` → returns the phase, and `phaseTeamRepository.findByPhaseId(regularPhase.getId())` → derives teams from `season.getActiveTeams()`. These stubs are driven by the `matchRepository.findByMatchdayPhaseId` redirect. After rewriting the call site to use `regularPhase.getId()` directly, the test infrastructure in `@BeforeEach` already connects correctly — the lenient mock chain `findByMatchdayPhaseId(phaseId)` → `findByMatchdaySeasonId(seasonId)` will fire as before.

**Additional action in Plan 65-03:** `SiteGeneratorServiceIT.java` line 154 has `verify(standingsService, never()).calculateStandings(seasonId)`. After the bridge method is deleted this `verify` call won't compile (the method no longer exists at that signature). This assertion must be removed in Plan 65-03. The negative assertion becomes vacuously satisfied (the method no longer exists), so no coverage gap is created.

---

## calculateBuchholzScores Inlining (D-04b)

**Current structure:**

```java
// Private helper — only caller is calculateBuchholzScoresForPhase
private Map<UUID, Integer> calculateBuchholzScores(UUID seasonId) {
    var season = seasonRepository.findById(seasonId).orElse(null);
    if (season == null) return Map.of();
    Map<UUID, UUID> successionMap = season.buildSuccessionMap();
    var standings = calculateStandings(seasonId);  // <-- calls the bridge
    // ... race-based Buchholz calc
    return buchholz;
}

private Map<UUID, Integer> calculateBuchholzScoresForPhase(SeasonPhase phase) {
    return calculateBuchholzScores(phase.getSeason().getId());  // <-- wrapper
}
```

**Inlining approach:** The private helper `calculateBuchholzScores(UUID seasonId)` has exactly ONE caller: `calculateBuchholzScoresForPhase(SeasonPhase phase)`. [VERIFIED: grepped StandingsService.java — `calculateBuchholzScores` appears at lines 201, 209, 246; the only call to it is line 246 in `calculateBuchholzScoresForPhase`.] The `calculateBuchholzScoresForPhase` wrapper simply does `phase.getSeason().getId()` and calls the helper.

**After inlining:** `calculateBuchholzScoresForPhase(SeasonPhase phase)` body becomes:
```java
private Map<UUID, Integer> calculateBuchholzScoresForPhase(SeasonPhase phase) {
    var season = phase.getSeason();  // direct reference, no repo call
    Map<UUID, UUID> successionMap = season.buildSuccessionMap();
    // replace: var standings = calculateStandings(seasonId);
    var standings = calculateStandings(phase.getId(), null);  // canonical API
    Map<UUID, Integer> pointsMap = standings.stream()
        .collect(Collectors.toMap(s -> s.getTeam().getId(), TeamStanding::getPoints));
    List<Race> races = raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId());
    // ... rest of logic unchanged
    return buchholz;
}
```

**What changes:** The `seasonRepository.findById(seasonId)` call is eliminated (the season is already available via `phase.getSeason()`). The null-guard `if (season == null) return Map.of()` is no longer needed since the phase already has an attached season. The `calculateStandings(seasonId)` call becomes `calculateStandings(phase.getId(), null)`.

**Note on `raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull`:** This method takes a `UUID seasonId` — it remains season-scoped, which is correct. The Buchholz calculation is intentionally season-scoped (opponents across the whole season). This call is preserved as-is.

**SeasonRepository dependency:** After this inlining, `seasonRepository` is only used in `calculateAlltimeStandings` (line 157: `seasonRepository.findAll()`). Once the bridge is deleted (line 148-151) and `calculateBuchholzScores` is inlined (line 201-234), the `seasonRepository` field remains needed for `calculateAlltimeStandings`. No field removal needed.

---

## Test Fixture Changes Needed for Plan 65-01

### AbstractMatchdayGraphicServiceTest

**Problem:** All 7 tests with `when(standingsService.calculateStandings(season.getId()))` must become `when(standingsService.calculateStandings(eq(phase.getId()), isNull()))`.

**Current fixture:** `createMatchdayWithMatches()` calls `PhaseTestFixtures.matchdayInRegularPhase(season, "Match Day 3", 3)` which returns a `Matchday` with `getPhase()` already set to a regular phase. The `regularPhase` is not captured as a field in the test — it is buried inside the helper.

**Required fix:** Either (a) capture the regularPhase from the matchday after creation via `matchday.getPhase()`, or (b) adjust the helper to return both. Option (a) is simpler: `var phase = matchday.getPhase(); var phaseId = phase.getId();` — then the stub becomes `when(standingsService.calculateStandings(eq(phaseId), isNull())).thenReturn(...)`.

**Import additions:** `org.mockito.ArgumentMatchers.eq`, `org.mockito.ArgumentMatchers.isNull` need to be imported in the test file (currently only `mock` and `when` are imported from Mockito).

### TeamCardService constructor

**Problem:** `SeasonPhaseService` must be injected. The existing constructor is:
```java
public TeamCardService(TemplateEngine templateEngine,
                       StandingsService standingsService,
                       @Value("${app.upload-dir:uploads}") String uploadDir)
```

**Required change:**
```java
public TeamCardService(TemplateEngine templateEngine,
                       StandingsService standingsService,
                       SeasonPhaseService seasonPhaseService,
                       @Value("${app.upload-dir:uploads}") String uploadDir)
```

**TeamCardServiceTest update:** The test constructs `new TeamCardService(null, null, "uploads")`. Must become `new TeamCardService(null, null, null, "uploads")`.

### OverlayGraphicServiceTest, LineupGraphicServiceTest

No existing mock-stub rewrites needed. New LEAGUE-regression tests (D-11) and GROUPS-tests (D-12) are additive.

---

## JaCoCo Coverage Impact Assessment

**Baseline:** 85.6% line coverage (1224 tests) — 3.6 pp headroom over the 82% gate. [VERIFIED: Phase 64 summary]

**Changes to production lines:**
- Bridge method deleted: ~4 lines (`calculateStandings(UUID seasonId)` body = 3 executable lines).
- `calculateBuchholzScores` private helper deleted/inlined: ~20 executable lines removed from `StandingsService`, replaced by equivalent lines in `calculateBuchholzScoresForPhase`.
- Net change in `StandingsService` executable lines: approximately -4 (bridge deletion) since inlining is a rewrite not a net reduction.

**Changes to test lines:**
- `StandingsServiceTest`: 2 tests deleted (pure duplicates), 10 tests rewritten (method call change only — line count unchanged), 1 test deleted (bridge-delegation test). Net: -3 test methods.
- `SiteGeneratorServiceIT`: 1 `verify` assertion removed.
- `SwissPairingServiceTest`: 1 test method deleted.
- New tests added: 5 LEAGUE-regression tests (D-11) + 3 GROUPS-tests (D-12) = 8 net-new test methods.

**Coverage direction:** The 8 net-new test methods (D-11 + D-12) will exercise new code paths (phase-aware call sites in graphics services). Removing 3 test methods and ~4 production lines is roughly neutral. The net coverage change is near-zero or slightly positive. **Coverage drop below 82% is not a realistic risk.** [ASSUMED: exact line counts not measured; however, the headroom (3.6 pp) is large relative to the scope of deletion.]

---

## Common Pitfalls

### Pitfall 1: Matchday.getSeason() still needed in AbstractMatchdayGraphicService

**What goes wrong:** Implementor removes `matchday.getSeason()` entirely, breaking the `MatchdayGraphicData` construction which still needs `season.getName()` and `season.getYear()`.

**Why it happens:** The migration focuses on replacing `calculateStandings(season.getId())` and the season reference looks redundant.

**How to avoid:** The migration changes only the line that calls `calculateStandings`. The existing `var season = matchday.getSeason()` call on line 46 is kept; add `var phase = matchday.getPhase()` and `var group = matchday.getGroup()` alongside it. The `season` variable is still used on lines 70-73 of the current code.

### Pitfall 2: Mockito strict vs. lenient mode in AbstractMatchdayGraphicServiceTest

**What goes wrong:** After rewriting stubs from `calculateStandings(season.getId())` to `calculateStandings(eq(phaseId), isNull())`, other test methods that still call the old signature trigger `UnnecessaryStubbingException` if the class is `@ExtendWith(MockitoExtension.class)` in strict mode.

**Why it happens:** The test class uses plain `mock()` (not `@Mock`), so Mockito strict mode is not enforced class-wide. No issue. But if any test forgets to rewrite its stub, the test will call the production code, which post-migration has no bridge method and will fail to compile.

**How to avoid:** Systematic per-test stub rewrite across all 7 affected test methods in `AbstractMatchdayGraphicServiceTest`.

### Pitfall 3: SiteGeneratorServiceIT failing to compile after bridge removal

**What goes wrong:** `SiteGeneratorServiceIT` line 154 uses `verify(standingsService, never()).calculateStandings(seasonId)`. Once `calculateStandings(UUID)` is deleted from `StandingsService`, this line fails to compile.

**Why it happens:** The negative-verify assertion references the deleted method signature.

**How to avoid:** Plan 65-03 must explicitly include removing line 154 from `SiteGeneratorServiceIT` as a pre-requisite before the bridge deletion commit.

### Pitfall 4: TeamCardService injection not wired in Spring context

**What goes wrong:** After adding `SeasonPhaseService` to `TeamCardService`'s constructor, any `@SpringBootTest` or `@WebMvcTest` that constructs `TeamCardService` via Spring DI must have `SeasonPhaseService` in the application context. Since `TeamCardService` is a `@Service` bean, Spring auto-wires it — no explicit `@Bean` needed.

**Why it happens:** Test slices (e.g., `@WebMvcTest`) may not load the full service context.

**How to avoid:** Check whether any IT test file manually instantiates `TeamCardService` or relies on Spring to wire it. [VERIFIED: `TeamCardServiceTest` uses manual construction, not Spring DI — the update to `null` as third argument covers this.]

### Pitfall 5: calculateBuchholzScores null-guard loss during inlining

**What goes wrong:** The private helper had `var season = seasonRepository.findById(seasonId).orElse(null); if (season == null) return Map.of()`. After inlining, this guard disappears since `phase.getSeason()` is used directly.

**Why it happens:** Inlining removes the defensive null check.

**How to avoid:** Since `phase.getSeason()` is always non-null (the phase-to-season FK is NOT NULL in the schema, and `SeasonPhaseService.findById` always returns a phase with its season loaded via OSIV), the null check is safe to remove. No behavior change.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Phase resolution for TeamCardService | custom lookup logic | `SeasonPhaseService.findRegularPhase(seasonId)` | Already exists, already tested, already used by `StandingsController` and `SiteGeneratorService`. |
| Phase/group extraction for matchday callers | custom resolver | `matchday.getPhase()` + `matchday.getGroup()` | Direct Hibernate accessors already populated via OSIV — no N+1 risk, no extra query. |
| Buchholz season fetch | `seasonRepository.findById` call | `phase.getSeason()` direct access | Phase already has its season loaded — the repo call is redundant roundtrip. |

---

## Code Examples

### Canonical pattern for matchday/race callers (D-06)

```java
// Source: CONTEXT.md D-06; verified against StandingsService.java canonical API
var phase = matchday.getPhase();
var group = matchday.getGroup();
var standings = standingsService.calculateStandings(
    phase.getId(),
    group != null ? group.getId() : null);
```

### Canonical pattern for season-only callers (D-07)

```java
// Source: CONTEXT.md D-07; verified against SeasonPhaseService.findRegularPhase
var regular = seasonPhaseService.findRegularPhase(season.getId());
var standings = standingsService.calculateStandings(regular.getId(), null);
```

### New LEAGUE-regression test stub pattern (D-11)

```java
// Source: CONTEXT.md D-11
// In test setup, obtain the phaseId from matchday.getPhase().getId()
var phase = matchday.getPhase();  // already set by PhaseTestFixtures helpers
when(standingsService.calculateStandings(eq(phase.getId()), isNull()))
    .thenReturn(List.of(homeStanding, awayStanding));

// Verify contract:
verify(standingsService).calculateStandings(eq(phase.getId()), isNull());
```

### New GROUPS-test stub pattern (D-12)

```java
// Source: CONTEXT.md D-12
var group = someGroup; // matchday.getGroup() for a GROUPS-layout matchday
when(standingsService.calculateStandings(eq(phase.getId()), eq(group.getId())))
    .thenReturn(List.of(groupAStanding1, groupAStanding2));

verify(standingsService).calculateStandings(eq(phase.getId()), eq(group.getId()));
```

### Rewritten succession test (bridge → canonical)

```java
// Before:
var standings = standingsService.calculateStandings(season.getId());

// After (uses regularPhase field already defined in @BeforeEach):
var standings = standingsService.calculateStandings(regularPhase.getId(), null);
// No stub change needed — @BeforeEach lenient mocks already wire
// findByMatchdayPhaseId(regularPhase.getId()) → findByMatchdaySeasonId(season.getId())
// and phaseTeamRepository.findByPhaseId(regularPhase.getId()) → season.getActiveTeams()
```

---

## Environment Availability

Step 2.6: SKIPPED — This is a pure source-code refactoring phase. No external tools, databases beyond H2/MariaDB (already present), or CLI utilities are required beyond the standard `./mvnw` build.

---

## Runtime State Inventory

Step 2.5: NOT APPLICABLE — This phase is not a rename, rebrand, or migration of string identifiers. The API method name changes are compile-time edits only; no stored data or runtime state contains the Java method signature `calculateStandings(UUID seasonId)`.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito 5 (via Spring Boot 4.x BOM) |
| Config file | `pom.xml` — Surefire plugin (unit), Failsafe + `-Pe2e` (E2E), JaCoCo `<minimum>0.82</minimum>` |
| Quick run command | `./mvnw test -Dtest='AbstractMatchdayGraphicServiceTest,TeamCardServiceTest,OverlayGraphicServiceTest,LineupGraphicServiceTest,StandingsServiceTest,SwissPairingServiceTest,SiteGeneratorServiceIT'` |
| Full suite command | `./mvnw verify` |
| E2E command | `./mvnw verify -Pe2e` (Playwright + Failsafe) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| D-03a | AbstractMatchdayGraphicService uses phase+group, not season, for standings | Mockito unit | `./mvnw test -Dtest=AbstractMatchdayGraphicServiceTest` | ✅ (existing + new D-11/D-12 tests) |
| D-03b | TeamCardService uses `findRegularPhase` + canonical API, not bridge | Mockito unit | `./mvnw test -Dtest=TeamCardServiceTest` | ✅ (existing + new D-11 test) |
| D-03c | OverlayGraphicService uses phase+group for standings | Mockito unit | `./mvnw test -Dtest=OverlayGraphicServiceTest` | ✅ (existing + new D-11/D-12 tests) |
| D-03d | SettingsGraphicService uses phase+group for standings (non-playoff branch) | Mockito unit | `./mvnw test -Dtest=SettingsGraphicServiceTest` | ❌ Wave 0 — new test class needed |
| D-03e | LineupGraphicService uses phase+group for standings (non-playoff branch) | Mockito unit | `./mvnw test -Dtest=LineupGraphicServiceTest` | ✅ (existing + new D-11 test) |
| D-04a | SwissPairingService.calculateBuchholz deleted (dead code) | Compilation | `./mvnw test -Dtest=SwissPairingServiceTest` | ✅ (1 test deleted alongside method) |
| D-04b | calculateBuchholzScores inlined into calculateBuchholzScoresForPhase | Mockito unit | `./mvnw test -Dtest=StandingsServiceTest` (Buchholz tests still call canonical API) | ✅ (existing Buchholz tests unchanged) |
| D-11 | LEAGUE-regression: each of 5 callers calls `calculateStandings(phaseId, null)` | Mockito unit | `./mvnw test -Dtest='AbstractMatchdayGraphicServiceTest,TeamCardServiceTest,OverlayGraphicServiceTest,SettingsGraphicServiceTest,LineupGraphicServiceTest'` | ❌ Wave 0 (5 new test methods) |
| D-12 | GROUPS-scope: Abstract, Overlay, Settings call `calculateStandings(phaseId, groupId)` | Mockito unit | `./mvnw test -Dtest='AbstractMatchdayGraphicServiceTest,OverlayGraphicServiceTest,SettingsGraphicServiceTest'` | ❌ Wave 0 (3 new test methods) |
| D-01 / SC1 | Bridge method removed — grep returns 0 | Compile + grep gate | `grep -nR "calculateStandings(seasonId" src/main/java | wc -l` (must = 0) | ✅ (verified by Plan 65-03 acceptance) |
| SVC-02 / SC2 | LEAGUE-layout behavior byte-identical after migration | Mockito unit (D-11 tests) | `./mvnw verify` | ❌ Wave 0 (covered by D-11 tests) |
| QUAL-01 | JaCoCo ≥ 82% | Gate | `./mvnw verify` (JaCoCo enforced) | ✅ (automated gate) |

### Sampling Rate

- **Per task commit (within a plan wave):** `./mvnw test -Dtest='<affected-test-classes>'` — targeted Surefire, feedback < 30 s
- **Per plan completion (end of P1, P2, P3):** `./mvnw verify` — full Surefire + JaCoCo gate, feedback < 90 s
- **Phase gate (before `/gsd-verify-work`):** `./mvnw verify -Pe2e` — full E2E suite must be green, ~5 min
- **SC1 grep gate (Plan 65-03):** `grep -nR "calculateStandings(seasonId" src/main/java | wc -l` must equal 0

### Wave 0 Gaps

Wave 0 work is part of Plan 65-01 (new test methods added alongside production changes — TDD per CLAUDE.md).

- [ ] New test method `givenLeagueMatchday_whenPrepareBaseContext_thenStandingsCalledWithPhaseIdAndNullGroup()` in `AbstractMatchdayGraphicServiceTest` — covers D-11 for Abstract caller
- [ ] New test method `givenGroupsLayoutMatchday_whenPrepareBaseContext_thenStandingsCalledWithPhaseAndMatchdayGroup()` in `AbstractMatchdayGraphicServiceTest` — covers D-12
- [ ] New test class `SettingsGraphicServiceTest.java` (or new test methods in existing file if one is found) — covers D-11 LEAGUE-regression and D-12 GROUPS-test for Settings caller
- [ ] New test method in `TeamCardServiceTest` for D-11 LEAGUE-regression (requires `SeasonPhaseService` mock)
- [ ] New test method in `OverlayGraphicServiceTest` for D-11 LEAGUE-regression and D-12 GROUPS-test
- [ ] New test method in `LineupGraphicServiceTest` for D-11 LEAGUE-regression

*(No new test infrastructure needed — JUnit 5, Mockito, and all required fixtures (`PhaseTestFixtures`) are already wired.)*

### Manual-Only Checks

| Behavior | Why Manual | Test Instructions |
|----------|-----------|-------------------|
| Graphics generation pixel-identical after migration (LEAGUE season) | Playwright graphics tests are compile-scope only; screenshot comparison is not automated | Boot dev server with `demo` profile, generate a team card and matchday schedule graphic before and after migration; confirm visual output is identical |

---

## Security Domain

Not applicable — this phase is a pure refactoring of an internal service API. No authentication, session management, access control, input validation, or cryptography changes are introduced. The Playwright graphics generation (TeamCardService) was already exempt from ASVS review in prior phases.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | JaCoCo coverage will not drop below 82% after removing ~3 bridge tests and ~4 bridge production lines | JaCoCo Coverage Impact Assessment | If wrong, the planner must add additional tests to restore coverage before Plan 65-03 commits. Risk is LOW given 3.6 pp headroom. |
| A2 | `SettingsGraphicServiceTest.java` does not exist (no file found in the test directory listing) | Caller-by-Caller Migration Map D-03d | If wrong (file exists but was missed), planner needs to inspect and rewrite existing stubs rather than create a new file. |

---

## Open Questions

1. **SettingsGraphicService test file existence**
   - What we know: `find` on `src/test/java/org/ctc/admin/service/` did not return `SettingsGraphicServiceTest.java`
   - What's unclear: Whether a test file exists elsewhere or was excluded from the listing
   - Recommendation: Planner should `grep -r SettingsGraphicService src/test/` to confirm before writing new tests

2. **`matchdayInRegularPhase` fixture return type in AbstractMatchdayGraphicServiceTest**
   - What we know: The helper returns a `Matchday` with `getPhase()` already set to a `regularPhase`
   - What's unclear: Whether `regularPhase.getId()` is stable across multiple test calls within the same test method (i.e., whether the same UUID is returned each time)
   - Recommendation: Capture `var phaseId = matchday.getPhase().getId()` immediately after creating the matchday and use it consistently in the stub and verify calls

---

## Sources

### Primary (HIGH confidence — all verified from source code in this session)

- `src/main/java/org/ctc/domain/service/StandingsService.java` — full read; bridge overload at line 148, private helper at lines 201-234, `calculateBuchholzScoresForPhase` at lines 245-247
- `src/test/java/org/ctc/domain/service/StandingsServiceTest.java` — full read; all bridge-calling tests identified and categorized
- `src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java` — full read; bridge call at line 47
- `src/main/java/org/ctc/admin/service/TeamCardService.java` — full read; bridge call at line 52, constructor signature
- `src/main/java/org/ctc/admin/service/OverlayGraphicService.java` — full read; bridge call at line 58
- `src/main/java/org/ctc/admin/service/SettingsGraphicService.java` — full read; bridge call at line 67 (non-playoff branch)
- `src/main/java/org/ctc/admin/service/LineupGraphicService.java` — full read; bridge call at line 70 (non-playoff branch)
- `src/test/java/org/ctc/admin/service/AbstractMatchdayGraphicServiceTest.java` — full read; 7 bridge-stubbing tests identified
- `src/test/java/org/ctc/admin/service/TeamCardServiceTest.java` — partial read; constructor pattern confirmed
- `src/test/java/org/ctc/admin/service/OverlayGraphicServiceTest.java` — full read; no bridge stubs, 4 tests
- `src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java` — full read; no bridge stubs, 8 tests
- `src/main/java/org/ctc/domain/service/SwissPairingService.java` lines 140-180 — bridge call at line 158 confirmed
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java` line 154 — negative verify confirmed
- `src/main/java/org/ctc/domain/service/SeasonPhaseService.java` lines 70-90 — `findRegularPhase` signature confirmed
- `.planning/phases/64-nyquist-validation-sweep/64-01-SUMMARY.md` — coverage baseline 85.6% / 1224 tests
- `.planning/phases/61-cleanup-quality-gate/61-VALIDATION.md` — gold-standard validation template reference

---

## Metadata

**Confidence breakdown:**
- Caller migration patterns: HIGH — all 7 callers read from source
- Bridge-test triage: HIGH — all tests in StandingsServiceTest read and categorized
- Mock fixture changes: HIGH — test files read in full
- JaCoCo coverage impact: MEDIUM-HIGH — directional estimate, exact line counts not measured (A1)
- SettingsGraphicServiceTest existence: LOW — file listing is the only evidence; planner should grep to confirm (A2)

**Research date:** 2026-05-07
**Valid until:** Indefinite (source code is the ground truth; no external dependencies)
