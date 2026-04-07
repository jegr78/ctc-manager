# Phase 13: Layer Cleanup Recovery - Research

**Researched:** 2026-04-06
**Domain:** Java/Spring Boot layer separation — controller/service/repository three-tier refactoring
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Cherry-pick Phase 7 commits (b733781 + Plan 02/03 commits) and resolve merge conflicts. The code was already reviewed and tested — fastest recovery path.
- **D-02:** Commit grouping at Claude's discretion — logical groups or original commits depending on merge conflict situation.
- **D-03:** 1:1 identical scope with Phase 7 — all 3 areas: DTO-Decoupling (10 services), Repository-Removal (5 controllers), Buchholz-Integration.
- **D-04:** CsvImportController (now at `org.ctc.dataimport.CsvImportController`) is included in the cleanup despite being in a feature module.
- **D-05:** Move Form→Entity conversion from domain services to controllers. Services accept domain primitives (IDs, strings, entities) instead of admin Form DTOs.
- **D-06:** No new domain-layer DTO classes. The 10 service-DTO imports are eliminated by changing service method signatures to accept primitives/entities.
- **D-07:** Affected services: CarService, DriverService, MatchScoringService, MatchdayService, PlayoffSeedingService, RaceScoringService, SeasonManagementService, TeamManagementService, TrackService.
- **D-08:** Add finder/delegation methods to existing services instead of creating new services.
- **D-09:** Repository injections to eliminate per controller (see CONTEXT.md).
- **D-10:** Move Buchholz calculation and Swiss-format sorting from StandingsController to StandingsService. Add a combined method `calculateStandingsWithBuchholz(seasonId)`.
- **D-11:** Existing `calculateStandings()` remains unchanged for non-Swiss formats.
- **D-12:** Cherry-pick Phase 7 tests alongside the implementation. Adapt tests if cherry-pick conflicts arise.
- **D-13:** Verification via `./mvnw verify -Pe2e` — full test suite including Playwright E2E.
- **D-14:** Follow original Phase 7 order: Plan 01: DTO-Decoupling → Plan 02: Repository-Removal → Plan 03: Buchholz-Integration.

### Claude's Discretion
- Commit grouping strategy (logical groups vs individual cherry-picks depending on conflict resolution)
- Method signature design for refactored services
- Whether to use overloaded methods or new method names when replacing Form-accepting methods

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-01 | Domain Services importieren keine Admin DTOs mehr — Entkopplung durch Controller-Konvertierung | 10 services identified with admin.dto imports; Phase 7 commits provide exact refactoring already proven |
| ARCH-02 | Alle 5 Controller (Standings, PowerRankings, Playoff, TeamCard, CsvImport) nutzen Services statt direkte Repository-Injections | 5 controllers confirmed to still have repository injections; service extension methods documented |
| FEAT-02 | StandingsController enthaelt keine Business-Logik mehr — Buchholz-Berechnung und Swiss-Sorting in StandingsService | StandingsService already has calculateStandingsWithBuchholz(); StandingsController has the inline Swiss sorting logic |
</phase_requirements>

---

## Summary

Phase 13 is a recovery refactoring: the worktree file clobber during Phase 10 (commit `5b3a58b`) replaced key files with pre-Phase-7 versions, undoing the controller/service layer cleanup that was already proven working. The regression was detected via the v1.1 milestone audit.

The current codebase has three concrete violations: (1) 10 domain services import `org.ctc.admin.dto` or `org.ctc.admin.service` classes — exactly the same 10 that Phase 7 fixed; (2) five controllers still inject repositories directly; (3) StandingsController contains Swiss-format Buchholz calculation and 4-level sort logic that belongs in StandingsService.

Crucially, StandingsService **already contains** `calculateStandingsWithBuchholz()` (it survived the regression). SeasonManagementService already has `findById()` and `findAll()`. The regression removed `findActiveSeason()`, `findByIdOptional()`, `findSeasonTeamById()`, `findSeasonTeamsBySeasonId()`, and `PlayoffService.findRoundById()` — plus reverted all controller changes and service signature changes. The Phase 7 test additions in `bc7225d` were also wiped.

**Primary recommendation:** Re-apply Phase 7 changes in the order Plan 01 (service finder methods + controller cleanup) → Plan 02 (DTO-decoupling simple services) → Plan 03 (DTO-decoupling complex services). Cherry-pick is the intended mechanism but manual re-application is equally valid since the changes are known.

---

## Regression Scope: Exact Gap Analysis

### What the Regression (5b3a58b) Reverted

The regression commit was `refactor(10-02): extract PlayoffBracketViewService and PlayoffSeedingService from PlayoffService`. The worktree clobber caused a large set of pre-Phase-7 source files to be used instead of post-Phase-7 versions, affecting ~30 source files.

**Verified current violations** [VERIFIED: codebase grep]:

#### Admin DTO Imports in Domain Services (ARCH-01)
| Service | Import | Recovery Action |
|---------|--------|-----------------|
| `CarService` | `CarForm` | Change `save(CarForm)` → `save(UUID, String, String)` |
| `DriverService` | `DriverForm` | Change `save(DriverForm)` → primitives |
| `MatchScoringService` | `MatchScoringForm` | Change `save(MatchScoringForm)` → primitives |
| `MatchdayService` | `MatchdayDto` | Replace `MatchdayDto` return with `MatchdayData` record inside service |
| `PlayoffSeedingService` | `SeedForm` | Replace with `PlayoffSeedingService.SeedEntry` record |
| `RaceScoringService` | `RaceScoringForm` | Change `save(RaceScoringForm)` → primitives |
| `SeasonManagementService` | `SeasonForm` | Change `save(SeasonForm, UUID, UUID)` → primitives |
| `TeamManagementService` | `SeasonDriverGroupDto`, `TeamForm` | Replace `SeasonDriverGroupDto` with inner record; change `save(TeamForm)` → primitives |
| `TrackService` | `TrackForm` | Change `save(TrackForm)` → primitives |
| ~~`RaceService`~~ | `TeamCardService` (admin.service) | **OUT OF SCOPE** — pre-existing concern, not in Phase 7 cleanup |
| ~~`RaceGraphicService`~~ | 4 admin.service imports | **OUT OF SCOPE** — pre-existing concern, not in Phase 7 cleanup |

**Note on PlayoffSeedingService:** This service did not exist during Phase 7. Phase 10 created it by extracting seeding logic from `PlayoffService`. The regression moved `SeedForm` use into this new service. Phase 13 D-07 explicitly includes `PlayoffSeedingService` in the cleanup.

#### Repository Injections in Controllers (ARCH-02)
| Controller | Repository | Service Replacement |
|------------|------------|---------------------|
| `StandingsController` | `SeasonRepository` | `SeasonManagementService.findActiveSeason()` + `findByIdOptional()` + `findAll()` |
| `PowerRankingsController` | `SeasonRepository` | `SeasonManagementService.findAll()` |
| `TeamCardController` | `SeasonRepository` + `SeasonTeamRepository` | `SeasonManagementService` + `TeamManagementService.findSeasonTeamById()` + `findSeasonTeamsBySeasonId()` |
| `PlayoffController` | `PlayoffRoundRepository` | `PlayoffService.findRoundById()` |
| `CsvImportController` | `SeasonRepository` | `SeasonManagementService.findByIdOptional()` |

#### Business Logic in Controller (FEAT-02)
| Location | What it is | Target |
|----------|-----------|--------|
| `StandingsController` lines 48-56 | Swiss-format Buchholz calc + 4-level sort | Move to `StandingsService.calculateStandingsWithBuchholz()` |
| `StandingsController` lines 44-45 | Direct `seasonRepository.findById()` / `findByActiveTrue()` | Replace with `SeasonManagementService.findByIdOptional()` / `findActiveSeason()` |
| `StandingsController` line 67 | Direct `seasonRepository.findAll()` | Replace with `SeasonManagementService.findAll()` |

---

## Standard Stack

No new libraries are added in this phase. The full technology stack is defined in CLAUDE.md. [VERIFIED: CLAUDE.md]

| Tool | Version | Usage in this Phase |
|------|---------|---------------------|
| Spring Boot 4.x | project-defined | `@Service`, `@Controller`, `@RequiredArgsConstructor` patterns |
| JUnit 5 + Mockito | project-defined | Unit tests for service methods; integration tests for controllers |
| Maven Surefire | project-defined | `./mvnw verify` to run all tests |

---

## Architecture Patterns

### Three-Tier Separation Rule [VERIFIED: ARCHITECTURE.md]
```
Controller Layer  → org.ctc.admin.controller (HTTP only)
Service Layer     → org.ctc.domain.service (business logic, NO admin.dto imports)
Repository Layer  → org.ctc.domain.repository (data access, used ONLY by services)
```

The rule violated by current code: domain services may not import from `org.ctc.admin.*`.

### Pattern 1: Primitive Parameter Delegation (for simple services)
**What:** Replace `save(SomeForm form)` with primitives extracted at controller level.
**When to use:** Services where form fields map 1:1 to entity fields without complex logic.
**Example (from Phase 7 commit c0cabbf):**
```java
// CarService BEFORE (violates ARCH-01):
public Car save(CarForm form) {
    car.setManufacturer(form.getManufacturer());
    car.setName(form.getName());
}

// CarService AFTER (correct):
public Car save(UUID id, String manufacturer, String name) {
    car.setManufacturer(manufacturer);
    car.setName(name);
}

// CarController AFTER:
carService.save(carForm.getId(), carForm.getManufacturer(), carForm.getName());
```

### Pattern 2: Inner Record as Service API Contract (for complex services)
**What:** Define a `public record` inside the domain service that mirrors the form's data contract. Controller maps from Form DTO to the service record.
**When to use:** When the method needs grouped data that would make the primitive signature unwieldy (e.g., SeedForm has a List<SeedEntry> with nested fields).
**Example (from Phase 7 commit ed387cb):**
```java
// PlayoffSeedingService AFTER:
public record SeedEntry(UUID matchupId, int slot, UUID teamId, Integer seedNumber) {}

public void saveSeed(UUID playoffId, List<SeedEntry> seeds) { ... }

// PlayoffController AFTER:
var seeds = form.getSeeds().stream()
    .map(e -> new PlayoffSeedingService.SeedEntry(e.getMatchupId(), e.getSlot(), e.getTeamId(), e.getSeedNumber()))
    .toList();
playoffSeedingService.saveSeed(form.getPlayoffId(), seeds);
```

**Applied to `TeamManagementService.SeasonDriverGroup`** — replaces `SeasonDriverGroupDto` in the return type of `TeamDetailData`.

### Pattern 3: Service Finder Method (for repository removal)
**What:** Add pass-through finder methods to services. Controller injects only service, not repository.
**When to use:** Controller needs a `findById` / `findAll` call that bypasses the service (direct repo injection).
**Example (from Phase 7 commit b733781):**
```java
// SeasonManagementService — add these methods:
@Transactional(readOnly = true)
public Optional<Season> findActiveSeason() {
    return seasonRepository.findByActiveTrue();
}

@Transactional(readOnly = true)
public Optional<Season> findByIdOptional(UUID id) {
    return seasonRepository.findById(id);
}
```

### Pattern 4: Service-Owned Calculation (for FEAT-02)
**What:** Move orchestration logic from controller into service. Controller calls one method, gets back correct result.
**When to use:** Controller contains more than one line of calculation before delegating to model.
**Current state of FEAT-02:** `StandingsService.calculateStandingsWithBuchholz()` **already exists** in the current codebase with the correct implementation. The controller just does not call it — it uses the inline Swiss logic instead. The fix is: replace the 10-line inline block in `StandingsController` with `standingsService.calculateStandingsWithBuchholz(season.getId())`.

### Anti-Patterns to Avoid
- **Direct repository injection in controllers:** Violates single-responsibility — controller should not know about JPA.
- **Form DTO passed to domain service:** Creates circular dependency (domain depends on admin layer).
- **Partial cherry-pick without adapting callers:** If you change a service signature, all callers must be updated in the same task.
- **Forgetting test files:** Phase 7's `bc7225d` test commit was wiped — tests for `findRoundById`, `findActiveSeason`, `findByIdOptional`, `findSeasonTeamById`, and `calculateStandingsWithBuchholz` must be re-added.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Buchholz calculation in service | New algorithm | `StandingsService.calculateBuchholzScores()` already exists (survived regression) | Avoid duplication with SwissPairingService.calculateBuchholz() |
| MatchdayData type | New class file | Inner `record MatchdayData(UUID id, String label, int sortIndex)` inside MatchdayService | Stays co-located with the service that owns it |
| Form-to-primitive mapping | Utility class | Controller inline: `service.save(form.getId(), form.getX(), form.getY())` | That is the established pattern from Phase 7 |

---

## Common Pitfalls

### Pitfall 1: PlayoffSeedingService vs PlayoffService
**What goes wrong:** Phase 7's `SeedEntry` record was in `PlayoffService`. Phase 10 moved seeding to `PlayoffSeedingService`. The record and the changed `saveSeed()` signature must now go into `PlayoffSeedingService`, not `PlayoffService`.
**How to avoid:** `PlayoffSeedingService.SeedEntry` (not `PlayoffService.SeedEntry`). `PlayoffController` injects `PlayoffSeedingService`, already does so.

### Pitfall 2: TeamDetailData still uses SeasonDriverGroupDto
**What goes wrong:** `TeamManagementService.TeamDetailData` record has a `List<SeasonDriverGroupDto>` field. When `SeasonDriverGroupDto` import is removed from the service, this record breaks.
**How to avoid:** Define `SeasonDriverGroup` as an inner record inside `TeamManagementService` (matching Phase 7 approach). Update `TeamDetailData` to use `List<SeasonDriverGroup>`. The `SeasonDriverGroupDto` can remain in `admin.dto` for other uses.

### Pitfall 3: MatchdayController returns MatchdayDto over HTTP (JSON)
**What goes wrong:** `MatchdayController` has two JSON API endpoints that declare `List<MatchdayDto>` / `ResponseEntity<MatchdayDto>` as return types. When `MatchdayService.getMatchdaysBySeason()` is changed to return `List<MatchdayData>`, the controller must also be updated to use the new type. Since the method is called from JavaScript in the UI, the JSON field names must stay identical (`id`, `label`, `sortIndex`) — and they do, since the field names match.
**How to avoid:** Update `MatchdayController` import and return types in the same task as the service change.

### Pitfall 4: `findActiveSeason()` must use stream filter
**What goes wrong:** `seasonRepository.findByActiveTrue()` returns `Optional<Season>` which fails if multiple active seasons exist (data integrity issue). Phase 7 commit `aa2eedb` fixed this by using stream filter in the controller. The recovered `findActiveSeason()` in the service must use the same stream-filter pattern.
**How to avoid:** [VERIFIED: git show aa2eedb]
```java
public Optional<Season> findActiveSeason() {
    return seasonRepository.findAll().stream()
            .filter(Season::isActive)
            .findFirst();
}
```
Or delegate to `seasonRepository.findByActiveTrue()` — Phase 7 used the repository method and relied on data integrity. Check the final Phase 7 state of `SeasonManagementService` to confirm the exact approach used.

### Pitfall 5: StandingsController still calls calculateAlltimeStandings
**What goes wrong:** Phase 9 (commit `dc26361`) wired `standingsService.calculateAlltimeStandings()` into StandingsController. Phase 10 regression wiped both the controller call (reverting to `List.of()` TODO) AND removed the method from StandingsService. Phase 13 is ARCH-01/ARCH-02/FEAT-02 only — FEAT-01 (alltime standings) is a separate requirement in a future phase. **Do not attempt to restore calculateAlltimeStandings() in this phase.**
**How to avoid:** Keep the `// TODO` placeholder in StandingsController for alltime standings. Only fix the Swiss/Buchholz block.

### Pitfall 6: Test count regression
**What goes wrong:** The regression removed test methods from `PlayoffServiceTest`, `SeasonManagementServiceTest`, `StandingsServiceTest`, and `TeamManagementServiceTest`. If recovered code is not accompanied by test recovery, JaCoCo coverage may drop below the 82% minimum.
**How to avoid:** Re-add Phase 7 test methods from commit `bc7225d` alongside the implementation. Current test count is 785 — recovery should bring this above the pre-regression count.

---

## Code Examples

### Service Finder Method (SeasonManagementService)
```java
// [VERIFIED: git show b733781] — exact code from Phase 7

@Transactional(readOnly = true)
public Optional<Season> findActiveSeason() {
    return seasonRepository.findByActiveTrue();
}

@Transactional(readOnly = true)
public Optional<Season> findByIdOptional(UUID id) {
    return seasonRepository.findById(id);
}
```

### TeamManagementService Finder Methods
```java
// [VERIFIED: git show b733781]

@Transactional(readOnly = true)
public SeasonTeam findSeasonTeamById(UUID id) {
    return seasonTeamRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("SeasonTeam", id));
}

@Transactional(readOnly = true)
public List<SeasonTeam> findSeasonTeamsBySeasonId(UUID seasonId) {
    return seasonTeamRepository.findBySeasonId(seasonId);
}
```
Note: `TeamManagementService` must also inject `SeasonTeamRepository` (a dependency removed by the regression).

### PlayoffService.findRoundById
```java
// [VERIFIED: git show b733781]

@Transactional(readOnly = true)
public PlayoffRound findRoundById(UUID id) {
    return playoffRoundRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("PlayoffRound", id));
}
```

### StandingsController — Clean State
```java
// [VERIFIED: git show aa2eedb] — controller after Phase 7 P01

private final StandingsService standingsService;
private final DriverRankingService driverRankingService;
private final SeasonManagementService seasonManagementService;
// No SeasonRepository, no SwissPairingService

var season = parsedId != null
        ? seasonManagementService.findByIdOptional(parsedId).orElse(null)
        : seasonManagementService.findActiveSeason().orElse(null);

if (season != null) {
    if (season.getFormat() == SeasonFormat.SWISS) {
        model.addAttribute("standings", standingsService.calculateStandingsWithBuchholz(season.getId()));
    } else {
        model.addAttribute("standings", standingsService.calculateStandings(season.getId()));
    }
    ...
}
model.addAttribute("seasons", seasonManagementService.findAll());
```

### MatchdayService Inner Record (replaces MatchdayDto)
```java
// [VERIFIED: git show 0dcfe24]

public record MatchdayData(UUID id, String label, int sortIndex) {}

public List<MatchdayData> getMatchdaysBySeason(UUID seasonId) {
    return matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId).stream()
            .map(md -> new MatchdayData(md.getId(), md.getLabel(), md.getSortIndex()))
            .toList();
}
```

---

## Phase 7 Commit Reference

All changes in this phase replicate (or adapt) Phase 7 work. Original commits for reference:

| Commit | Label | Files | Status in Current Codebase |
|--------|-------|-------|---------------------------|
| `bc7225d` | test(07-01): service finder + Buchholz tests | StandingsServiceTest, SeasonManagementServiceTest, TeamManagementServiceTest, PlayoffServiceTest | **Wiped** — test methods removed |
| `b733781` | feat(07-01): service finder methods + Buchholz integration | StandingsService, SeasonManagementService, TeamManagementService, PlayoffService | **Partially wiped** — `calculateStandingsWithBuchholz()` survived; `findActiveSeason()`, `findByIdOptional()`, `findSeasonTeamById()`, `findSeasonTeamsBySeasonId()`, `findRoundById()` removed |
| `aa2eedb` | refactor(07-01): remove repository injections from 5 controllers | All 5 controllers + SeasonManagementService fix | **Wiped** — all controllers reverted |
| `c0cabbf` | refactor(07-02): decouple Car/Track/Driver | CarService, DriverService, TrackService + 3 controllers + tests | **Wiped** |
| `2883adf` | refactor(07-02): decouple RaceScoring/MatchScoring | RaceScoringService, MatchScoringService + 2 controllers + tests | **Wiped** |
| `ed387cb` | refactor(07-03): decouple Season/Team/Playoff | SeasonManagementService, TeamManagementService, PlayoffService + controllers + tests | **Wiped** (PlayoffService target is now PlayoffSeedingService) |
| `0dcfe24` | refactor(07-03): decouple Matchday/Race | MatchdayService (2 files only) | **Wiped** |

**What survived the regression:**
- `StandingsService.calculateStandingsWithBuchholz()` and `calculateBuchholzScores()` — present in current code
- `SeasonManagementService.findById()` (the throwing variant) — present
- `RaceService.RaceData`, `RaceResultData`, `saveRace(UUID, ...)` with primitive signature — present
- `RaceController` mapping RaceForm to RaceData — present

---

## Execution Order (Plan Mapping)

**Plan 01: Service Finders + Controller Cleanup (ARCH-02 + FEAT-02)**
1. Add `findActiveSeason()`, `findByIdOptional()` to `SeasonManagementService`
2. Add `findSeasonTeamById()`, `findSeasonTeamsBySeasonId()` to `TeamManagementService` (inject `SeasonTeamRepository`)
3. Add `findRoundById()` to `PlayoffService`
4. Refactor `StandingsController` — remove SeasonRepository, remove SwissPairingService, use `calculateStandingsWithBuchholz()`
5. Refactor `PowerRankingsController` — remove SeasonRepository, use SeasonManagementService
6. Refactor `TeamCardController` — remove SeasonRepository + SeasonTeamRepository, use service methods
7. Refactor `PlayoffController` — remove PlayoffRoundRepository, use `PlayoffService.findRoundById()`
8. Refactor `CsvImportController` — remove SeasonRepository, use SeasonManagementService
9. Re-add tests from `bc7225d` for new service methods

**Plan 02: DTO Decoupling Simple Services (ARCH-01 — Phase 7 P02 services)**
1. `CarService.save(CarForm)` → `save(UUID, String, String)` + update CarController
2. `TrackService.save(TrackForm)` → primitives + update TrackController
3. `DriverService.save(DriverForm)` → primitives + update DriverController
4. `RaceScoringService.save(RaceScoringForm)` → primitives + update RaceScoringController
5. `MatchScoringService.save(MatchScoringForm)` → primitives + update MatchScoringController
6. Re-add service unit tests using primitive-based calls

**Plan 03: DTO Decoupling Complex Services (ARCH-01 — Phase 7 P03 services)**
1. `SeasonManagementService.save(SeasonForm, UUID, UUID)` → primitives + update SeasonController
2. `TeamManagementService` — inner `SeasonDriverGroup` record replaces `SeasonDriverGroupDto`; `save(TeamForm)` → primitives + update TeamController
3. `PlayoffSeedingService` — inner `SeedEntry` record replaces `SeedForm`; `saveSeed(UUID, SeedForm)` → `saveSeed(UUID, List<SeedEntry>)` + update PlayoffController
4. `MatchdayService` — inner `MatchdayData` record replaces `MatchdayDto`; update MatchdayController return types
5. Re-add service unit tests using primitive/record-based calls
6. Full verification: `./mvnw verify -Pe2e`

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (unit), Spring Boot Test MockMvc (integration), Playwright (E2E) |
| Config file | `pom.xml` (Surefire for unit/integration, Failsafe + `-Pe2e` for E2E) |
| Quick run command | `./mvnw test` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ARCH-01 | Domain services have zero admin.dto imports | unit | `./mvnw test -pl . -Dtest=CarServiceTest,DriverServiceTest,TrackServiceTest,RaceScoringServiceTest,MatchScoringServiceTest,SeasonManagementServiceTest,TeamManagementServiceTest,MatchdayServiceTest,PlayoffSeedingServiceTest` | ✅ existing (need updated test signatures) |
| ARCH-02 | 5 controllers delegate to services only | integration | `./mvnw test -Dtest=StandingsControllerTest,PlayoffControllerTest,TeamCardControllerTest` | ✅ StandingsControllerTest exists; others need review |
| FEAT-02 | StandingsService.calculateStandingsWithBuchholz called for SWISS format | unit + integration | `./mvnw test -Dtest=StandingsServiceTest,StandingsControllerTest` | ✅ both exist |

### Sampling Rate
- **Per task commit:** `./mvnw test`
- **Per plan completion:** `./mvnw verify`
- **Phase gate:** `./mvnw verify -Pe2e` green before `/gsd-verify-work`

### Wave 0 Gaps
None — existing test infrastructure covers all phase requirements. Test content updates (not new files) are needed within existing test classes.

---

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — pure Java refactoring, no new tools required).

---

## Security Domain

This phase performs internal Java refactoring only (method signatures, import removal). No new endpoints, no new data flows, no security-relevant changes. ASVS categories V2, V3, V4, V5, V6 are not applicable to this refactoring. [ASSUMED: standard refactoring scope]

---

## Project Constraints (from CLAUDE.md)

These directives are binding for this phase: [VERIFIED: CLAUDE.md]

| Constraint | Impact on Phase 13 |
|------------|-------------------|
| Test Coverage: minimum 82% line coverage | Must re-add Phase 7 test methods; current count is 785, recovery must not drop coverage |
| TDD: Write tests first | Plan 01/02/03 must start with test commits (red) before implementation (green) |
| BDD test naming: `givenContext_whenAction_thenExpectedResult()` | All new test methods follow this pattern |
| Feature Sequence: Unit Tests → Implementation → Integration Tests | Adhere to this order within each plan |
| Controllers thin: no business logic, no direct repository access | This is exactly what ARCH-01/02 enforce |
| DTOs instead of Entities in Controllers: bind POST via Form DTOs | Form DTOs remain in controllers; only domain services must not import them |
| Keep Thymeleaf Templates Lean | Templates are not touched in this phase |
| No breaking changes to existing URLs/endpoints | No URL or controller method signature changes — only internal refactoring |
| OSIV enabled — only @EntityGraph for optimization | Not relevant to this phase |
| Do Not Modify Flyway Migrations | Not relevant to this phase |
| Subagent Rules: use opus/sonnet, branch protection | Implementation subagents must use sonnet or opus |

---

## Open Questions

1. **PlayoffController SeedForm usage for template binding**
   - What we know: `PlayoffController.saveSeed()` receives `@ModelAttribute SeedForm form`. The form is bound from Thymeleaf. The controller can map this to `PlayoffSeedingService.SeedEntry` before calling the service.
   - What's unclear: Whether `@ModelAttribute SeedForm` binding can be cleanly kept in the controller while the service uses `SeedEntry` records.
   - Recommendation: Keep `SeedForm` as the `@ModelAttribute` parameter in the controller; map to `List<PlayoffSeedingService.SeedEntry>` before the service call. This is the Phase 7 pattern from commit `ed387cb`.

2. **findActiveSeason() stream-filter vs findByActiveTrue()**
   - What we know: Phase 7 commit `aa2eedb` comment says "Fix findActiveSeason() to use stream filter (tolerates multiple active seasons)". This suggests the recovered method should use stream filter, not the repository method.
   - Recommendation: Implement as `seasonRepository.findAll().stream().filter(Season::isActive).findFirst()` for robustness.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `RaceService.RaceData`, `saveRace()` with primitive signature survived the regression | Regression Scope table | If wrong, RaceService also needs DTO decoupling — add to Plan 03 scope |
| A2 | `StandingsService.calculateStandingsWithBuchholz()` implementation is correct and does not need changes | Code Examples | If wrong, implementation work needed beyond controller wiring |
| A3 | The `./mvnw verify` baseline of 785 tests will rise (not fall) after re-adding Phase 7 test methods | Validation Architecture | If wrong, JaCoCo threshold may be violated — measure after Plan 01 |

---

## Sources

### Primary (HIGH confidence)
- Git commit `5b3a58b` — regression commit, confirmed scope via `git show --stat`
- Git commits `b733781`, `aa2eedb`, `c0cabbf`, `2883adf`, `ed387cb`, `0dcfe24`, `bc7225d` — Phase 7 commits, exact changes verified via `git show`
- Current source files `StandingsController.java`, `PowerRankingsController.java`, `TeamCardController.java`, `PlayoffController.java`, `CsvImportController.java` — verified with grep and Read
- Current domain service files — admin.dto imports verified via `grep -rn "import org.ctc.admin"`
- `.planning/codebase/ARCHITECTURE.md`, `CONCERNS.md`, `CONVENTIONS.md` — project documentation
- `.planning/phases/13-layer-cleanup-recovery/13-CONTEXT.md` — locked decisions
- `CLAUDE.md` — project constraints

---

## Metadata

**Confidence breakdown:**
- Regression scope: HIGH — confirmed via git show on each affected file
- Recovery approach: HIGH — Phase 7 commits exist and are accessible; exact diffs known
- Execution order: HIGH — matches Phase 7 order per CONTEXT.md D-14
- Test recovery: HIGH — Phase 7 test commit `bc7225d` identified, content verifiable

**Research date:** 2026-04-06
**Valid until:** 2026-04-20 (stable — no external dependencies, pure refactoring)
