# Phase 7: Layer Cleanup - Research

**Researched:** 2026-04-04
**Domain:** Spring Boot three-tier refactoring (Controller/Service/Repository layer separation)
**Confidence:** HIGH

## Summary

Phase 7 addresses three interrelated layer violations: (1) 10 domain services importing admin DTOs (ARCH-01), (2) 5 controllers injecting repositories directly (ARCH-02), and (3) business logic (Buchholz/Swiss sorting) living in StandingsController instead of StandingsService (FEAT-02). All three are pure Java refactoring with no new dependencies, no schema changes, and no UI changes.

The codebase already has established patterns from v1.0 phases (service delegation, `findById().orElseThrow()`, `@RequiredArgsConstructor` injection). The same patterns apply here. SeasonManagementService already has `findAll()` and `findById()` methods; it just needs `findActiveSeason()`. PlayoffService already has `getSeasonIdForRound()` but needs a `findRoundById()` method. The DTO decoupling requires changing service method signatures from accepting Form objects to accepting primitives/entities, then moving the Form-to-Entity mapping logic into the controllers.

**Primary recommendation:** Tackle in three waves: (1) add missing service finder methods + Buchholz integration, (2) remove repository injections from 5 controllers, (3) decouple DTOs from 10 domain services. Each wave is independently testable.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Move Form-to-Entity conversion from domain services to controllers. Services accept domain primitives (IDs, strings, entities) instead of admin Form DTOs. Controllers do the conversion before calling service methods.
- **D-02:** No new domain-layer DTO classes. The 12 service-DTO imports are eliminated by changing service method signatures to accept primitives/entities, and moving the mapping logic to controller methods.
- **D-03:** Affected services (10 total): CarService, DriverService, MatchScoringService, MatchdayService, PlayoffService, RaceScoringService, RaceService, SeasonManagementService, TeamManagementService, TrackService.
- **D-04:** Add finder/delegation methods to existing services instead of creating new services. Each controller already injects relevant services -- extend those services with the needed query methods.
- **D-05:** Target controllers and their repository injections to eliminate:
  - StandingsController -> SeasonRepository (use SeasonManagementService)
  - PowerRankingsController -> SeasonRepository (use SeasonManagementService)
  - TeamCardController -> SeasonRepository + SeasonTeamRepository (use SeasonManagementService + TeamManagementService)
  - PlayoffController -> PlayoffRoundRepository (use PlayoffService)
  - CsvImportController -> SeasonRepository (use SeasonManagementService)
- **D-06:** Extend StandingsService with a combined method (e.g., `calculateStandingsWithBuchholz(seasonId)`) that integrates Buchholz scores from SwissPairingService and applies Swiss-format sorting. Controller calls one method instead of orchestrating calculation + sorting.
- **D-07:** The existing `calculateStandings()` remains unchanged for non-Swiss formats. The new method extends it for Swiss-format seasons.

### Claude's Discretion
- Method signature design for refactored services (which primitives to accept)
- Whether to use overloaded methods or new method names when replacing Form-accepting methods
- Ordering of refactoring (controllers first vs services first)
- Test strategy: unit tests for new service methods, integration tests for controller behavior

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-01 | Domain Services importieren keine Admin DTOs mehr -- Entkopplung durch Controller-Konvertierung | 12 DTO imports across 10 services identified; each service's `save()` method accepts Form, must change to primitives |
| ARCH-02 | Alle 5 Controller nutzen Services statt direkte Repository-Injections | 5 controllers with 7 repository injections identified; service finder methods partially exist, gaps documented |
| FEAT-02 | StandingsController enthaelt keine Business-Logik mehr -- Buchholz-Berechnung und Swiss-Sorting in StandingsService | Buchholz logic (lines 48-56 of StandingsController) must move to StandingsService; SwissPairingService.calculateBuchholz() already exists |
</phase_requirements>

## Architecture Patterns

### Current Layer Violations (Inventory)

**ARCH-02: Repository injections in controllers (7 total):**

| Controller | Repository | Usage | Service to Use |
|------------|-----------|-------|----------------|
| StandingsController | SeasonRepository | `findById()`, `findByActiveTrue()`, `findAll()` | SeasonManagementService (has `findById`, `findAll`; needs `findActiveSeason`) |
| PowerRankingsController | SeasonRepository | `findAll()` | SeasonManagementService (has `findAll`) |
| TeamCardController | SeasonRepository | `findAll()`, `findById()` | SeasonManagementService (has both) |
| TeamCardController | SeasonTeamRepository | `findBySeasonId()`, `findById()` | TeamManagementService (needs `findSeasonTeamById`, `findSeasonTeamsBySeasonId`) |
| PlayoffController | PlayoffRoundRepository | `findById()` x3 (graphic downloads) | PlayoffService (needs `findRoundById`) |
| CsvImportController | SeasonRepository | `findById()` (for display label in preview) | SeasonManagementService (has `findById`) |

**ARCH-01: DTO imports in domain services (12 imports, 10 services):**

| Service | DTO Import | Method Using It | Replacement Strategy |
|---------|-----------|-----------------|---------------------|
| CarService | CarForm | `save(CarForm)` | `save(UUID id, String name, ...)` |
| DriverService | DriverForm | `save(DriverForm)` | `save(UUID id, String psnId, String name, ...)` |
| MatchScoringService | MatchScoringForm | `save(MatchScoringForm)` | `save(UUID id, String name, int pointsWin, ...)` |
| MatchdayService | MatchdayDto | `getMatchdaysBySeason()`, `createInline()` return type | Move MatchdayDto to domain or use inline record |
| PlayoffService | SeedForm | `saveSeed(UUID, SeedForm)` | `saveSeed(UUID, List<SeedEntry>)` with domain SeedEntry record |
| RaceScoringService | RaceScoringForm | `save(RaceScoringForm)` | `save(UUID id, String name, String points, ...)` |
| RaceService | RaceForm, RaceResultForm | `saveRace(RaceForm)`, `saveResults(UUID, List<RaceResultForm>)`, `getNewRaceFormData()`, `getRaceFormData()` | Most complex -- RaceService both reads and writes Forms |
| SeasonManagementService | SeasonForm | `save(SeasonForm, UUID, UUID)` | `save(UUID id, String name, int year, int number, ...)` |
| TeamManagementService | TeamForm, SeasonDriverGroupDto | `save(TeamForm)`, `getTeamDetailData()` returns `SeasonDriverGroupDto` | TeamForm -> primitives; SeasonDriverGroupDto -> move to domain or inline record |

**FEAT-02: Business logic in StandingsController (lines 48-56):**

```java
// This orchestration logic belongs in StandingsService:
var buchholzMap = swissPairingService.calculateBuchholz(season.getId());
var standingsList = standingsService.calculateStandings(season.getId());
standingsList.forEach(s -> s.setBuchholz(buchholzMap.getOrDefault(s.getTeam().getId(), 0)));
standingsList.sort(Comparator
    .<TeamStanding, Integer>comparing(TeamStanding::getPoints, reverseOrder())
    .thenComparing(TeamStanding::getBuchholz, reverseOrder())
    .thenComparing(TeamStanding::getPointDifference, reverseOrder())
    .thenComparing(TeamStanding::getPointsFor, reverseOrder()));
```

### Recommended Refactoring Approach

**Wave 1: Service layer extensions (FEAT-02 + ARCH-02 prep)**
- Add `findActiveSeason()` to SeasonManagementService
- Add `findSeasonTeamById()` and `findSeasonTeamsBySeasonId()` to TeamManagementService
- Add `findRoundById()` to PlayoffService
- Add `calculateStandingsWithBuchholz(UUID seasonId)` to StandingsService (inject SwissPairingService)
- Unit tests for all new methods

**Wave 2: Controller repository removal (ARCH-02)**
- Remove SeasonRepository from StandingsController, PowerRankingsController, CsvImportController
- Remove SeasonRepository + SeasonTeamRepository from TeamCardController
- Remove PlayoffRoundRepository from PlayoffController
- Remove SwissPairingService from StandingsController (now used internally by StandingsService)
- Existing integration tests must continue to pass unchanged

**Wave 3: DTO decoupling from domain services (ARCH-01)**
- Change service `save()` signatures from Form to primitives
- Move Form-to-Entity mapping into controllers
- Handle special cases: MatchdayDto (return type), SeasonDriverGroupDto (return type), RaceService (complex form assembly)

### Pattern: Service Method Signature Change

Before (domain service accepts admin DTO):
```java
// SeasonManagementService.java
public Season save(SeasonForm form, UUID raceScoringId, UUID matchScoringId) {
    Season season = form.getId() != null ? findById(form.getId()) : new Season();
    season.setName(form.getName());
    season.setYear(form.getYear());
    // ... 10 more setters from form fields
    return seasonRepository.save(season);
}
```

After (domain service accepts primitives):
```java
// SeasonManagementService.java
public Season save(UUID id, String name, int year, int number, String description,
                   LocalDate startDate, LocalDate endDate, boolean active,
                   SeasonFormat format, Integer totalRounds, int legs,
                   Integer eventDurationMinutes, UUID raceScoringId, UUID matchScoringId) {
    Season season = id != null ? findById(id) : new Season();
    season.setName(name);
    season.setYear(year);
    // ... setters from parameters
    return seasonRepository.save(season);
}

// SeasonController.java (controller does the extraction)
public String save(@Valid SeasonForm form, ...) {
    seasonManagementService.save(
        form.getId(), form.getName(), form.getYear(), form.getNumber(),
        form.getDescription(), form.getStartDate(), form.getEndDate(),
        form.isActive(), form.getFormat(), form.getTotalRounds(), form.getLegs(),
        form.getEventDurationMinutes(), form.getRaceScoringId(), form.getMatchScoringId());
}
```

### Pattern: MatchdayDto Return Type (Special Case)

`MatchdayDto` is used as a return type from `MatchdayService.getMatchdaysBySeason()` and `createInline()`. It is a simple projection record (id, label, sortIndex). Two options:

**Recommended: Move to domain layer.** Create `org.ctc.domain.service.MatchdayService.MatchdayData` as a nested record (same pattern as `PlayoffService.PlayoffListData`). This is NOT a new domain DTO class (it is a service return record) and aligns with D-02.

### Pattern: SeasonDriverGroupDto Return Type (Special Case)

`SeasonDriverGroupDto` is used as a return type from `TeamManagementService.getTeamDetailData()`. It is a projection record (Season, Map<Team, List<Driver>>).

**Recommended: Convert to nested record inside TeamManagementService.** Same pattern as `TeamDetailData` which is already a nested record.

### Pattern: RaceService Form Assembly (Most Complex Case)

RaceService is the most complex case -- it both creates Forms (in `getNewRaceFormData()`, `getRaceFormData()`, `toForm()`) and consumes them (`saveRace()`). The `RaceFormData` record returns a `RaceForm` to the controller.

**Recommended approach:**
1. Change `saveRace(RaceForm)` to accept primitives
2. Keep `RaceFormData` but replace `RaceForm` with a domain record (`RaceData`) containing the same fields
3. Controller maps `RaceData` to model attributes and `RaceForm` POST data to service primitives

### Anti-Patterns to Avoid
- **Creating a parallel domain DTO hierarchy:** Decision D-02 explicitly forbids new domain-layer DTO classes. Use primitives or nested service records.
- **Breaking existing tests:** All 5 controller test classes use MockMvc integration tests. Refactoring must not change HTTP behavior.
- **Changing too many signatures at once:** Refactor one service at a time, verify tests pass after each.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Season lookup by active flag | Custom query in controller | `SeasonManagementService.findActiveSeason()` wrapping `seasonRepository.findByActiveTrue()` | Consistent with service delegation pattern |
| Buchholz-integrated standings | Custom sorting in controller | `StandingsService.calculateStandingsWithBuchholz()` | Business logic belongs in service; already has `calculateStandings()` and `SwissPairingService.calculateBuchholz()` to compose |
| Season group options for PowerRankings | N/A | Move grouping logic to SeasonManagementService or PowerRankingsGraphicService | PowerRankingsController builds season groups from raw Season list -- this is data preparation, not HTTP handling |

## Common Pitfalls

### Pitfall 1: Parameter Explosion in Service Methods
**What goes wrong:** Replacing a Form with 10+ individual parameters creates unreadable method signatures.
**Why it happens:** Direct 1:1 translation of Form fields to method parameters.
**How to avoid:** For services with >6 parameters (SeasonManagementService, RaceService), group related parameters. E.g., `save(UUID id, String name, int year, int number, ...)` is acceptable for Season because callers always provide all fields. For truly complex cases, create a domain record (not an admin DTO).
**Warning signs:** Methods with >8 parameters. Consider a builder or domain record if this happens.

### Pitfall 2: Breaking CsvImportController's SeasonRepository.findById Usage
**What goes wrong:** CsvImportController uses `seasonRepository.findById(seasonId).ifPresent(...)` for display label enrichment in preview. Replacing with `SeasonManagementService.findById()` changes behavior -- the service throws `EntityNotFoundException` instead of returning Optional.
**Why it happens:** Service `findById()` uses `.orElseThrow()`.
**How to avoid:** Either (a) add a `findByIdOptional()` method to SeasonManagementService, or (b) use try-catch in controller, or (c) use the existing `findById()` and handle EntityNotFoundException. Option (a) is cleanest.
**Warning signs:** `ifPresent()` calls being replaced with non-Optional returns.

### Pitfall 3: StandingsService Circular Dependency
**What goes wrong:** StandingsService needs SwissPairingService for Buchholz. SwissPairingService already depends on StandingsService. This creates a circular dependency.
**Why it happens:** `SwissPairingService.calculateBuchholz()` calls `standingsService.calculateStandings()`. Adding `swissPairingService` to StandingsService creates a cycle.
**How to avoid:** Extract the Buchholz logic differently -- either (a) inject SwissPairingService into StandingsService via `@Lazy`, (b) move `calculateBuchholz()` to StandingsService directly (it only uses matchRepository and standings data), or (c) pass Buchholz map as parameter. Option (b) is cleanest -- move the Buchholz calculation method into StandingsService since it fundamentally operates on standings data.
**Warning signs:** Spring "circular dependency" error at startup.

### Pitfall 4: PowerRankingsController Season Grouping Logic
**What goes wrong:** PowerRankingsController has significant data transformation logic (grouping seasons, counting teams, sorting). Simply removing the SeasonRepository import without moving this logic leaves business logic in the controller.
**Why it happens:** The grouping/counting logic accesses `season.getSeasonTeams().size()` which is a lazy collection -- it works because OSIV is active.
**How to avoid:** Move the season grouping logic to a service method. Since `PowerRankingsGraphicService` already provides `loadTeamsForSeasonGroup()`, extend it with a `getSeasonGroupOptions()` method, or add to SeasonManagementService.
**Warning signs:** Controller method with stream().collect().groupingBy() chains.

### Pitfall 5: TeamCardController Uses SeasonTeamRepository in Multiple Endpoints
**What goes wrong:** TeamCardController uses SeasonTeamRepository in 5 places (index, generate, download, download-all). Missing even one replacement breaks the endpoint.
**Why it happens:** Repository is used across many handler methods.
**How to avoid:** Grep for ALL usages of the repository field before removing it. Add all needed delegation methods to TeamManagementService before touching the controller.
**Warning signs:** Removing a repository injection and getting compile errors in multiple methods.

## Code Examples

### New StandingsService Method (FEAT-02)

```java
// StandingsService.java -- new method
@Transactional(readOnly = true)
public List<TeamStanding> calculateStandingsWithBuchholz(UUID seasonId) {
    var standings = calculateStandings(seasonId);
    var buchholzMap = calculateBuchholzScores(seasonId);
    
    standings.forEach(s -> s.setBuchholz(
        buchholzMap.getOrDefault(s.getTeam().getId(), 0)));
    
    standings.sort(Comparator
        .<TeamStanding, Integer>comparing(TeamStanding::getPoints, Comparator.reverseOrder())
        .thenComparing(TeamStanding::getBuchholz, Comparator.reverseOrder())
        .thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
        .thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));
    
    return standings;
}

// Either move calculateBuchholz here or delegate to SwissPairingService
// NOTE: Moving here avoids circular dependency (see Pitfall 3)
```

### Refactored StandingsController (ARCH-02 + FEAT-02)

```java
// StandingsController.java -- after refactoring
@RequiredArgsConstructor
public class StandingsController {
    private final StandingsService standingsService;
    private final DriverRankingService driverRankingService;
    private final SeasonManagementService seasonManagementService;
    // No more: SeasonRepository, SwissPairingService

    @GetMapping
    public String standings(@RequestParam(required = false) String seasonId, Model model) {
        boolean isAlltime = "alltime".equals(seasonId);
        if (isAlltime) {
            model.addAttribute("standings", java.util.List.of());
            model.addAttribute("driverRanking", driverRankingService.calculateAlltimeRanking());
        } else {
            UUID parsedId = parseSeasonId(seasonId);
            var season = parsedId != null
                ? seasonManagementService.findByIdOptional(parsedId).orElse(null)
                : seasonManagementService.findActiveSeason().orElse(null);

            if (season != null) {
                var standings = season.getFormat() == SeasonFormat.SWISS
                    ? standingsService.calculateStandingsWithBuchholz(season.getId())
                    : standingsService.calculateStandings(season.getId());
                model.addAttribute("standings", standings);
                model.addAttribute("driverRanking", driverRankingService.calculateRanking(season.getId()));
                model.addAttribute("selectedSeason", season);
            }
        }
        model.addAttribute("isAlltime", isAlltime);
        model.addAttribute("seasons", seasonManagementService.findAll());
        model.addAttribute("selectedSeasonId", seasonId);
        return "admin/standings";
    }
}
```

### Service Finder Method Pattern (ARCH-02)

```java
// SeasonManagementService.java -- add these
@Transactional(readOnly = true)
public Optional<Season> findActiveSeason() {
    return seasonRepository.findByActiveTrue();
}

@Transactional(readOnly = true)
public Optional<Season> findByIdOptional(UUID id) {
    return seasonRepository.findById(id);
}

// PlayoffService.java -- add this
@Transactional(readOnly = true)
public PlayoffRound findRoundById(UUID roundId) {
    return playoffRoundRepository.findById(roundId)
        .orElseThrow(() -> new EntityNotFoundException("PlayoffRound", roundId));
}

// TeamManagementService.java -- add these
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

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + MockMvc |
| Config file | `pom.xml` (Surefire/Failsafe plugins) |
| Quick run command | `./mvnw test -pl .` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ARCH-01 | Domain services have zero admin.dto imports | grep check | `grep -r "import org.ctc.admin.dto" src/main/java/org/ctc/domain/service/ \| wc -l` (must be 0) | N/A (grep) |
| ARCH-02 | 5 controllers inject only services | unit/integration | `./mvnw test -Dtest="StandingsControllerTest,PowerRankingsControllerTest,TeamCardControllerTest,PlayoffControllerTest,CsvImportControllerTest" -pl .` | All 5 exist |
| FEAT-02 | Buchholz/Swiss sorting in StandingsService | unit | `./mvnw test -Dtest="StandingsServiceTest" -pl .` | Exists, needs new test methods |

### Sampling Rate
- **Per task commit:** `./mvnw test -pl .`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green + grep verification for ARCH-01

### Wave 0 Gaps
- [ ] `StandingsServiceTest` needs new test for `calculateStandingsWithBuchholz()`
- [ ] New unit tests for service finder methods (findActiveSeason, findSeasonTeamById, etc.)
- [ ] Verify no circular dependency in StandingsService after SwissPairingService/Buchholz integration

## Open Questions

1. **RaceService complexity**
   - What we know: RaceService has the deepest Form coupling -- it creates RaceForm objects (in `getNewRaceFormData`, `getRaceFormData`, `toForm`) and also consumes them (`saveRace`, `saveResults`). It uses `RaceFormData(RaceForm, ...)` as a return record.
   - What's unclear: Whether to extract a domain record parallel to RaceForm or to split the form-assembly responsibility.
   - Recommendation: Create a `RaceData` domain record in RaceService (nested record, same as TeamDetailData pattern). Move form assembly logic to controller. This is the most complex single service refactoring and should be its own task.

2. **MatchdayDto as return type vs. Form DTO**
   - What we know: MatchdayDto is used as a return type from MatchdayService (not as form input). It is a simple 3-field record.
   - What's unclear: Whether converting to a nested service record counts as "new domain DTO" per D-02.
   - Recommendation: Nested records inside services are NOT domain DTOs (they are service API contracts). Convert to `MatchdayService.MatchdayData`.

3. **PowerRankingsController grouping logic**
   - What we know: The controller builds `SeasonGroupOption` records from raw Season data, including lazy-loaded `getSeasonTeams().size()` calls.
   - What's unclear: Whether this data preparation logic should move to a service (strict interpretation) or stay in controller (it is presentation-layer data shaping).
   - Recommendation: Move to SeasonManagementService as `getSeasonGroupOptions()`. It accesses entity relationships and counts -- this is query/aggregation logic, not HTTP handling.

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection of all 5 target controllers
- Direct codebase inspection of all 10 target domain services
- Existing test files for all 5 controllers verified present
- SeasonManagementService already has `findAll()` and `findById()` -- confirmed by reading source
- SwissPairingService.calculateBuchholz() -- confirmed by reading source
- PlayoffService already has `getSeasonIdForRound()` -- confirmed by reading source

### Secondary (MEDIUM confidence)
- Circular dependency risk (StandingsService <-> SwissPairingService) -- inferred from import analysis

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - pure Java refactoring, no new libraries
- Architecture: HIGH - patterns established in v1.0 phases, direct code inspection
- Pitfalls: HIGH - circular dependency and Optional/throw mismatch identified from code

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable -- no external dependencies)
