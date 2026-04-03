# Phase 2: Service Layer Extraction - Research

**Researched:** 2026-04-03
**Domain:** Spring Boot MVC service layer refactoring
**Confidence:** HIGH

## Summary

This phase extracts all direct repository access from 7 controllers into service classes. The codebase already has a well-established service layer pattern (DriverService, TeamManagementService, SeasonManagementService) with `@Transactional`, `@RequiredArgsConstructor`, and typed exceptions from Phase 1. The task is mechanical: identify repository calls in controllers, create or extend service methods, rewire controllers to delegate.

The controllers split into three complexity tiers: (1) simple CRUD-only controllers (RaceScoring, MatchScoring) that need entirely new services, (2) medium controllers (Driver, Track, Car) needing a mix of new services and extensions, and (3) the SeasonController mega-controller with 8 repository injections that needs consolidation into SeasonManagementService.

**Primary recommendation:** Start with the simplest controllers (RaceScoring, MatchScoring) to establish the pattern, then work through medium controllers, and finish with SeasonController.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: Hybrid-Ansatz: Bestehende Services erweitern wo moeglich (TeamManagementService, DriverService, SeasonManagementService), neue Services nur wo kein Service existiert (CarService, TrackService, RaceScoringService, MatchScoringService)
- D-03: Alle 8 Repository-Injections aus SeasonController in den bestehenden SeasonManagementService migrieren (hat schon Team/Car/Track Pool-Verwaltung)
- D-05: Neue Services (Car, Track, RaceScoring, MatchScoring) in `org.ctc.domain.service` -- konsistent mit bestehenden Services
- D-06: Naming: Einfache Entity-Services als `{Entity}Service` (CarService, TrackService), Management-Services behalten ihren Namen (TeamManagementService, SeasonManagementService)

### Claude's Discretion
- D-02: TeamController Repository-Zugriffe in TeamManagementService oder aufteilen -- Claude analysiert die Methoden
- D-04: Maximale Anzahl Service-Injections pro Controller -- Claude entscheidet was sinnvoll ist
- Migrations-Reihenfolge (welcher Controller zuerst)
- Test-Strategie waehrend Migration (bestehende Tests anpassen vs. neue)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SRVC-01 | DriverController Repository-Zugriffe (assign, delete) in DriverService extrahieren | DriverService exists, needs 4 new methods: findAll, findById, assignToSeason, delete. Requires injecting SeasonDriverRepository, SeasonRepository, TeamRepository |
| SRVC-02 | TrackController Repository-Zugriffe (save, delete, image upload) in TrackService extrahieren | New TrackService needed with: findAll, findById, save, delete, uploadImage. Requires TrackRepository, RaceRepository, SeasonRepository, FileStorageService |
| SRVC-03 | CarController Repository-Zugriffe (image upload) in CarService extrahieren | New CarService needed with: findAll, findById, save, delete, uploadImage. Requires CarRepository, RaceRepository, SeasonRepository, FileStorageService |
| SRVC-04 | RaceScoringController Repository-Zugriffe in RaceScoringService extrahieren | New RaceScoringService needed with: findAll, findById, save, delete. Requires RaceScoringRepository only |
| SRVC-05 | MatchScoringController Repository-Zugriffe in MatchScoringService extrahieren | New MatchScoringService needed with: findAll, findById, save, delete. Requires MatchScoringRepository only |
| SRVC-06 | TeamController Repository-Zugriffe in TeamManagementService extrahieren | TeamManagementService exists, needs: findAll/findParentTeams, findById, save, delete, uploadLogo, addSubTeam, removeSubTeam. Requires adding FileStorageService injection |
| SRVC-07 | SeasonController 8 Repository-Injections durch Service-Aufrufe ersetzen | SeasonManagementService exists with pool ops. Needs: findAll, findById, save, delete, getRaceScorings, getMatchScorings, swiss round data. RaceScoringRepository + MatchScoringRepository injections move here |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Controller duenn halten:** Controller sind nur fuer HTTP-Handling zustaendig. Keine Business-Logik, keine direkten Repository-Zugriffe.
- **DTOs statt Entities in Controllern:** Form-Eingaben ueber Form-DTOs binden, nie JPA Entities direkt per @ModelAttribute.
- **@Transactional auf Service-Methoden** -- bestehender Standard.
- **TDD:** Tests zuerst schreiben, dann Implementierung. Red -> Green -> Refactor.
- **Test-Naming:** `givenContext_whenAction_thenExpectedResult()`
- **Test-Coverage minimum:** 82% Line Coverage (Build bricht bei Unterschreitung)
- **Conventional Commits:** English messages, `refactor:` prefix for this phase

## Architecture Patterns

### Established Service Pattern (from existing codebase)

All existing services follow this pattern consistently:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ExampleService {

    private final ExampleRepository exampleRepository;

    @Transactional(readOnly = true)
    public List<Example> findAll() {
        return exampleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Example findById(UUID id) {
        return exampleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Example", id));
    }

    @Transactional
    public Example save(ExampleForm form) {
        // map form -> entity, validate, persist
    }

    @Transactional
    public void delete(UUID id) {
        var entity = findById(id);
        exampleRepository.delete(entity);
    }
}
```

### Thin Controller Pattern (target state)

```java
@Slf4j
@Controller
@RequestMapping("/admin/examples")
@RequiredArgsConstructor
public class ExampleController {

    private final ExampleService exampleService;  // Only services, no repositories

    @GetMapping
    public String list(Model model) {
        model.addAttribute("examples", exampleService.findAll());
        return "admin/examples";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute ExampleForm form, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) return "admin/example-form";
        exampleService.save(form);
        redirectAttributes.addFlashAttribute("successMessage", "Saved");
        return "redirect:/admin/examples";
    }
}
```

### Exception Handling in Services

Services throw typed exceptions. The GlobalExceptionHandler (from Phase 1) catches them centrally. Controllers should NOT have try-catch blocks for service calls -- with one exception: `DataIntegrityViolationException` for unique constraint violations should be caught either in the service (preferred) or controller, since it needs a user-friendly redirect.

**Pattern for DataIntegrityViolationException:**
```java
// In service -- catch and rethrow as BusinessRuleException
@Transactional
public Track save(TrackForm form) {
    try {
        // ... save logic
        return trackRepository.save(track);
    } catch (DataIntegrityViolationException e) {
        throw new BusinessRuleException("A track with this name already exists");
    }
}
```

### Anti-Patterns to Avoid

- **Repository injection in controllers:** Every `@Autowired FooRepository` in a controller is a violation. Remove all.
- **Business logic in controllers:** Filtering, sorting, existence checks, cascading operations belong in services.
- **try-catch in controllers for service calls:** Let exceptions propagate to GlobalExceptionHandler.
- **Creating entities in controllers:** Entity construction from form data belongs in the service.

## Detailed Extraction Analysis

### Controller 1: RaceScoringController (SRVC-04) -- Simplest

**Current state:** Only `RaceScoringRepository` injected. Pure CRUD.

**New service: RaceScoringService**
| Method | From Controller Line | Logic |
|--------|---------------------|-------|
| `findAll()` | 28 | `raceScoringRepository.findAll()` |
| `findById(UUID)` | 40-41 | findById + EntityNotFoundException |
| `save(RaceScoringForm)` | 53-83 | Validation (isValid), create/update, DataIntegrityViolation |
| `delete(UUID)` | 87-101 | Delete with referential integrity catch |

**Key detail:** The `isValid()` check on line 63 validates monotonically decreasing points. This business logic currently lives in the entity (`RaceScoring.isValid()`). The service should call it and throw `BusinessRuleException`.

**Key detail:** Delete catches `Exception` for referential integrity (scoring referenced by season). Service should catch `DataIntegrityViolationException` and throw `BusinessRuleException`.

**Controller after: 1 injection** (RaceScoringService)

### Controller 2: MatchScoringController (SRVC-05) -- Simplest

**Current state:** Only `MatchScoringRepository` injected. Pure CRUD. Near-identical to RaceScoringController.

**New service: MatchScoringService**
| Method | From Controller Line | Logic |
|--------|---------------------|-------|
| `findAll()` | 28 | `matchScoringRepository.findAll()` |
| `findById(UUID)` | 40-41 | findById + EntityNotFoundException |
| `save(MatchScoringForm)` | 53-69 | Create/update |
| `delete(UUID)` | 77-91 | Delete with referential integrity catch |

**Controller after: 1 injection** (MatchScoringService)

### Controller 3: TrackController (SRVC-02) -- Medium

**Current state:** `TrackRepository`, `RaceRepository`, `SeasonRepository`, `FileStorageService` injected.

**New service: TrackService**
| Method | From Controller Line | Logic |
|--------|---------------------|-------|
| `findAllSorted()` | 36 | `trackRepository.findAllByOrderByNameAsc()` |
| `findById(UUID)` | 48-49 | findById + EntityNotFoundException |
| `save(TrackForm)` | 79-103 | Create/update, DataIntegrityViolation for duplicate name |
| `delete(UUID)` | 106-122 | Check usage in races + season pools, then delete |
| `uploadImage(UUID, MultipartFile)` | 59-76 | Delete old image, store new, update entity |

**Key detail:** `delete()` checks both `raceRepository.existsByTrackId()` and iterates all seasons for pool membership. This business logic MUST move to the service. The `seasonRepository.findAll().stream().anyMatch(...)` is inefficient but correct for current data volume -- move as-is, do not optimize.

**Controller after: 1 injection** (TrackService)

### Controller 4: CarController (SRVC-03) -- Medium

**Current state:** `CarRepository`, `RaceRepository`, `SeasonRepository`, `FileStorageService` injected. Near-identical structure to TrackController.

**New service: CarService**
| Method | From Controller Line | Logic |
|--------|---------------------|-------|
| `findAllSorted()` | 36 | `carRepository.findAllByOrderByManufacturerAscNameAsc()` |
| `findById(UUID)` | 48-49 | findById + EntityNotFoundException |
| `save(CarForm)` | 79-103 | Create/update, DataIntegrityViolation for duplicate |
| `delete(UUID)` | 106-122 | Check usage in races + season pools, then delete |
| `uploadImage(UUID, MultipartFile)` | 59-74 | Delete old image, store new, update entity |

**Controller after: 1 injection** (CarService)

### Controller 5: DriverController (SRVC-01) -- Medium

**Current state:** `DriverRepository`, `DriverService`, `SeasonDriverRepository`, `SeasonRepository`, `TeamRepository` injected. Already has a DriverService but it only handles save + alias validation.

**Extend existing: DriverService**
| Method | From Controller Line | Logic |
|--------|---------------------|-------|
| `findAll()` | 37 | `driverRepository.findAll()` |
| `findById(UUID)` | 43-44 | findById + EntityNotFoundException |
| `getEditFormData(UUID)` | 57-69 | Returns driver + seasonDrivers + all seasons + all teams for the edit form |
| `assignToSeason(UUID, UUID, UUID)` | 91-116 | Find driver/season/team, check existing, create/update SeasonDriver |
| `delete(UUID)` | 118-126 | Find + delete |

**Key detail:** `getEditFormData()` needs SeasonDriverRepository, SeasonRepository, TeamRepository. These inject into DriverService. Consider returning a record with all data the edit form needs.

**DriverService additional injections:** SeasonDriverRepository, SeasonRepository, TeamRepository (3 new)

**Controller after: 1 injection** (DriverService)

### Controller 6: TeamController (SRVC-06) -- Medium-Complex

**Current state:** `TeamRepository`, `FileStorageService`, `TeamManagementService` injected.

**Claude's Discretion analysis (D-02):** TeamManagementService already handles team detail data and color/logo propagation. The CRUD operations (save, delete, logo upload, sub-team management) are all team management operations. **Recommendation: All operations into TeamManagementService.** Creating a separate TeamService would duplicate repository access and create confusing naming.

**Extend existing: TeamManagementService**
| Method | From Controller Line | Logic |
|--------|---------------------|-------|
| `findParentTeamsSorted()` | 33-38 | Filter parent teams, sort by shortName |
| `findById(UUID)` | Already exists implicitly in getTeamDetailData, but need standalone |
| `save(TeamForm)` | 75-97 | Create/update + propagateColors |
| `delete(UUID)` | 150-157 | Find + delete |
| `uploadLogo(UUID, MultipartFile)` | 99-117 | Delete old, store new, propagateLogo |
| `addSubTeam(UUID, String, String)` | 119-135 | Validate, create sub-team |
| `removeSubTeam(UUID)` | 137-147 | Find + delete sub-team |

**TeamManagementService additional injections:** FileStorageService (1 new)

**Controller after: 1 injection** (TeamManagementService)

### Controller 7: SeasonController (SRVC-07) -- Most Complex

**Current state:** 11 injections total: `SeasonRepository`, `TeamRepository`, `CarRepository`, `TrackRepository`, `PlayoffRepository`, `RaceScoringRepository`, `MatchScoringRepository`, `ScoringService`, `SwissPairingService`, `SeasonManagementService`, `MatchdayGeneratorService`.

Of these, 7 are repositories. The goal is to replace repository injections with service calls.

**Extend existing: SeasonManagementService**
| Method | From Controller Line | Logic |
|--------|---------------------|-------|
| `findAll()` | 61 | `seasonRepository.findAll()` |
| `findById(UUID)` | 45-46 | findById + EntityNotFoundException |
| `getDetailData(UUID)` | 44-56 | Load season + playoff + computed flags |
| `getEditFormData(UUID)` | 73-96 | Load season + all teams/cars/tracks/scorings |
| `save(SeasonForm, UUID raceScoring, UUID matchScoring)` | 98-150 | Create/update season with scoring lookups |
| `delete(UUID)` | 239-246 | Find + delete |
| `getAllRaceScorings()` | 329 | `raceScoringRepository.findAll()` |
| `getAllMatchScorings()` | 330 | `matchScoringRepository.findAll()` |
| `getSwissRoundData(UUID)` | 249-279 | Load season + compute race scores + current round |

**SeasonManagementService additional injections:** PlayoffRepository, RaceScoringRepository, MatchScoringRepository, ScoringService (4 new)

**Claude's Discretion analysis (D-04):** After refactoring, SeasonController will have these service injections:
1. `SeasonManagementService` -- season CRUD, detail data, scoring lookups
2. `SwissPairingService` -- Swiss round generation (already service)
3. `MatchdayGeneratorService` -- matchday generation (already service)
4. `ScoringService` -- only used in `isHomeTeamDriver()` private helper

The `isHomeTeamDriver()` helper (lines 324-326) uses `scoringService.isDriverInTeam()`. This should move into the service layer (into `getSwissRoundData()` response). **Recommendation: 3 service injections maximum.** The ScoringService usage can be encapsulated in SeasonManagementService.

**Controller after: 3 injections** (SeasonManagementService, SwissPairingService, MatchdayGeneratorService)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Unique constraint violation handling | Custom pre-check queries | Catch `DataIntegrityViolationException` in service | Race condition between check and save; DB constraint is the source of truth |
| Entity-Form mapping | Manual mapping code in every service method | Keep inline mapping (project has no MapStruct) | Adding MapStruct for simple DTOs is over-engineering |
| Transaction management | Manual transaction begin/commit | `@Transactional` on service methods | Spring-managed transactions handle rollback on exceptions |

## Common Pitfalls

### Pitfall 1: Breaking Existing Controller Tests
**What goes wrong:** Controller tests use `@SpringBootTest` + `@AutoConfigureMockMvc` with real repositories. After extracting to services, tests still work because Spring wires services automatically. But some tests directly `@Autowired` repositories to set up test data.
**Why it happens:** Tests need to create entities for setup regardless of whether controllers use services.
**How to avoid:** Controller tests that inject repositories for TEST DATA SETUP are fine -- only controller production code should not inject repositories. Do not change test setup code.
**Warning signs:** Test failures after removing repository injection from controller.

### Pitfall 2: Forgetting @Transactional on New Service Methods
**What goes wrong:** Service methods that read-and-write without `@Transactional` may have partial execution on failure.
**Why it happens:** Copy-paste from controller (which has no transaction annotation) forgets to add it.
**How to avoid:** Every service method that writes gets `@Transactional`. Every read-only method gets `@Transactional(readOnly = true)`.
**Warning signs:** LazyInitializationException in service methods (outside OSIV scope in tests).

### Pitfall 3: DataIntegrityViolationException Requires flush()
**What goes wrong:** `DataIntegrityViolationException` is thrown at flush time, not at `save()` time. Without explicit flush, the exception occurs outside the catch block.
**Why it happens:** JPA defers SQL execution until flush/commit.
**How to avoid:** Call `repository.saveAndFlush()` or `repository.flush()` after save when catching DataIntegrityViolationException. The existing RaceScoringController already does `flush()` after delete (line 92).
**Warning signs:** Uncaught DataIntegrityViolationException at transaction commit.

### Pitfall 4: SeasonController Swiss Round Data Complexity
**What goes wrong:** The `swissRounds()` method (lines 249-279) has complex score calculation logic with `isHomeTeamDriver()` delegation. Moving this naively creates circular dependencies.
**Why it happens:** ScoringService is used for team detection, and SeasonManagementService would need it too.
**How to avoid:** SeasonManagementService already can depend on ScoringService (no circular dependency risk since ScoringService has no dependency on SeasonManagementService). Inject ScoringService into SeasonManagementService.
**Warning signs:** Circular bean dependency errors at startup.

### Pitfall 5: Controller Image Upload try-catch
**What goes wrong:** Image upload methods (TrackController:59-76, CarController:59-74, TeamController:99-117) catch `Exception` to show user-friendly errors. If moved to service without catch, IOException propagates to GlobalExceptionHandler showing technical error page instead of redirect with flash message.
**Why it happens:** File I/O is inherently exception-prone and needs graceful degradation at the upload point.
**How to avoid:** Service methods for image upload should catch IOException and rethrow as `BusinessRuleException("Image upload failed: " + details)`. The GlobalExceptionHandler then handles it. Alternatively, the controller can catch BusinessRuleException for upload-specific redirect behavior. **Recommended approach:** Service throws BusinessRuleException on upload failure, controller catches it for redirect with flash message. This is the ONE acceptable case for try-catch in controllers.
**Warning signs:** Image upload failures showing error page instead of redirecting back to edit form.

## Migration Order (Recommended)

1. **RaceScoringService + MatchScoringService** (SRVC-04, SRVC-05) -- Simplest, establishes pattern
2. **TrackService + CarService** (SRVC-02, SRVC-03) -- Near-identical, adds image upload pattern
3. **DriverService extension** (SRVC-01) -- Extends existing service, adds assignToSeason
4. **TeamManagementService extension** (SRVC-06) -- Extends existing service, adds CRUD + logo
5. **SeasonManagementService extension** (SRVC-07) -- Most complex, many new injections

## Test Strategy

### Existing Tests (must stay green)
All 7 affected controllers have `@SpringBootTest` integration tests:
- `RaceScoringControllerTest` -- 7 tests
- `MatchScoringControllerTest` -- similar count
- `TrackControllerTest` -- exists
- `CarControllerTest` -- exists
- `DriverControllerTest` -- exists
- `TeamControllerTest` -- exists
- `SeasonControllerTest` -- exists

### Approach: Service Tests First, Then Rewire Controller
For each service extraction:
1. **Write unit tests for new service** (or extend existing service test) using Mockito
2. **Implement the service methods**
3. **Rewire controller** to use service instead of repository
4. **Verify existing controller tests still pass** (they use real DB, so service layer is transparent)
5. **Remove repository injection from controller**

### Service Test Pattern (from existing DriverServiceTest)
```java
@ExtendWith(MockitoExtension.class)
class CarServiceTest {
    @Mock private CarRepository carRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private FileStorageService fileStorageService;
    @InjectMocks private CarService carService;

    @Test
    void givenExistingCar_whenDelete_thenRemoves() { ... }

    @Test
    void givenCarUsedInRace_whenDelete_thenThrowsBusinessRuleException() { ... }
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test |
| Config file | `pom.xml` (surefire + failsafe + JaCoCo) |
| Quick run command | `./mvnw test -pl .` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SRVC-04 | RaceScoringService CRUD | unit | `./mvnw test -Dtest=RaceScoringServiceTest -pl .` | Wave 0 |
| SRVC-05 | MatchScoringService CRUD | unit | `./mvnw test -Dtest=MatchScoringServiceTest -pl .` | Wave 0 |
| SRVC-02 | TrackService CRUD + image | unit | `./mvnw test -Dtest=TrackServiceTest -pl .` | Wave 0 |
| SRVC-03 | CarService CRUD + image | unit | `./mvnw test -Dtest=CarServiceTest -pl .` | Wave 0 |
| SRVC-01 | DriverService assign + delete | unit | `./mvnw test -Dtest=DriverServiceTest -pl .` | Exists (extend) |
| SRVC-06 | TeamManagementService CRUD + logo | unit | `./mvnw test -Dtest=TeamManagementServiceTest -pl .` | Exists (extend) |
| SRVC-07 | SeasonManagementService season CRUD | unit | `./mvnw test -Dtest=SeasonManagementServiceTest -pl .` | Exists (extend) |
| ALL | Controllers still work after rewiring | integration | `./mvnw verify` | Exists |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=<ServiceTest> -pl .`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/org/ctc/domain/service/RaceScoringServiceTest.java` -- covers SRVC-04
- [ ] `src/test/java/org/ctc/domain/service/MatchScoringServiceTest.java` -- covers SRVC-05
- [ ] `src/test/java/org/ctc/domain/service/TrackServiceTest.java` -- covers SRVC-02
- [ ] `src/test/java/org/ctc/domain/service/CarServiceTest.java` -- covers SRVC-03

## Code Examples

### New Service: RaceScoringService (complete skeleton)

```java
// Source: derived from RaceScoringController.java lines 26-102
package org.ctc.domain.service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceScoringService {

    private final RaceScoringRepository raceScoringRepository;

    @Transactional(readOnly = true)
    public List<RaceScoring> findAll() {
        return raceScoringRepository.findAll();
    }

    @Transactional(readOnly = true)
    public RaceScoring findById(UUID id) {
        return raceScoringRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RaceScoring", id));
    }

    @Transactional
    public RaceScoring save(RaceScoringForm form) {
        var scoring = new RaceScoring(form.getName(), form.getRacePoints(),
                form.getQualiPoints(), form.getFastestLapPoints());
        scoring.setId(form.getId());

        if (!scoring.isValid()) {
            throw new BusinessRuleException(
                    "Points must be monotonically decreasing (equal values allowed)");
        }

        if (form.getId() != null) {
            var existing = findById(form.getId());
            existing.setName(form.getName());
            existing.setRacePoints(form.getRacePoints());
            existing.setQualiPoints(form.getQualiPoints());
            existing.setFastestLapPoints(form.getFastestLapPoints());
            return raceScoringRepository.save(existing);
        }
        return raceScoringRepository.save(scoring);
    }

    @Transactional
    public void delete(UUID id) {
        var scoring = findById(id);
        try {
            raceScoringRepository.delete(scoring);
            raceScoringRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("Cannot delete -- still referenced by a season");
        }
    }
}
```

### Extending TeamManagementService: save method

```java
// Source: derived from TeamController.java lines 75-97
@Transactional
public Team save(TeamForm form) {
    if (form.getId() != null) {
        var existing = findById(form.getId());
        existing.setName(form.getName());
        existing.setShortName(form.getShortName());
        existing.setPrimaryColor(form.getPrimaryColor());
        existing.setSecondaryColor(form.getSecondaryColor());
        existing.setAccentColor(form.getAccentColor());
        var saved = teamRepository.save(existing);
        propagateColorsToSubTeams(saved);
        return saved;
    }
    return teamRepository.save(new Team(form.getName(), form.getShortName()));
}
```

### SeasonController save after refactoring

```java
// Source: target state for SeasonController.save()
@PostMapping("/save")
public String save(@Valid @ModelAttribute("seasonForm") SeasonForm form, BindingResult result,
                   @RequestParam UUID raceScoring,
                   @RequestParam UUID matchScoring,
                   RedirectAttributes redirectAttributes, Model model) {
    if (result.hasErrors()) {
        model.addAttribute("allRaceScorings", seasonManagementService.getAllRaceScorings());
        model.addAttribute("allMatchScorings", seasonManagementService.getAllMatchScorings());
        return "admin/season-form";
    }
    var season = seasonManagementService.save(form, raceScoring, matchScoring);
    redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + season.getName());
    return "redirect:/admin/seasons";
}
```

## Open Questions

1. **Image upload error handling pattern**
   - What we know: Current controllers catch Exception for uploads and redirect with flash error. GlobalExceptionHandler would show error page instead.
   - What's unclear: Whether BusinessRuleException in GlobalExceptionHandler redirects or shows error page (currently shows error page with CONFLICT status).
   - Recommendation: For upload methods, service throws BusinessRuleException on failure. Controller catches BusinessRuleException specifically and redirects with flash message. This is the one acceptable try-catch in controllers. Alternatively, add a redirect-aware exception type -- but that's over-engineering for 3 upload endpoints.

2. **SeasonManagementService growing large**
   - What we know: SeasonManagementService is already 240 lines. Adding season CRUD, scoring lookups, swiss data, and detail data will roughly double it.
   - What's unclear: Whether this creates a new "God Service" problem.
   - Recommendation: Acceptable for now. The service manages a single aggregate (Season) and its relationships. If it grows beyond 500 lines, split into SeasonCrudService + SeasonManagementService in a future phase. Not in scope now.

## Sources

### Primary (HIGH confidence)
- Codebase analysis: All 7 controllers read and analyzed line-by-line
- Codebase analysis: All 3 existing services (DriverService, TeamManagementService, SeasonManagementService) read
- Codebase analysis: All 4 repositories (Car, Track, RaceScoring, MatchScoring) read
- Codebase analysis: GlobalExceptionHandler from Phase 1 verified
- Codebase analysis: Exception classes (EntityNotFoundException, BusinessRuleException, ValidationException) verified
- Codebase analysis: Test patterns verified from RaceScoringControllerTest, TeamControllerTest

### Secondary (MEDIUM confidence)
- `.planning/research/ARCHITECTURE.md` -- target architecture patterns
- `.planning/codebase/CONCERNS.md` -- list of affected controllers

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new libraries needed, pure refactoring using existing Spring patterns
- Architecture: HIGH -- pattern already established in codebase (DriverService, TeamManagementService)
- Pitfalls: HIGH -- based on direct code reading of all 7 controllers and their test classes
- Migration order: HIGH -- based on dependency analysis and complexity assessment

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable -- no external dependencies, pure internal refactoring)
