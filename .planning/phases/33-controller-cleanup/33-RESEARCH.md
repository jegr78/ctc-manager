# Phase 33: Controller Cleanup - Research

**Researched:** 2026-04-14
**Domain:** Spring MVC controller delegation, RaceLineup source-of-truth pattern
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** PowerRankingsController.list() (L33-56) contains complex season grouping, team count aggregation, and sorting logic. Extract this into a new method on an appropriate service (e.g., StandingsService or a new PowerRankingsService helper method).
- **D-02:** MatchdayController.detail() (L60-66) contains non-bye match filtering, schedule completeness checking, and missing schedule count calculation. Move to MatchdayService as a helper method that returns the computed data.
- **D-03:** DriverController.mergeForm() (L111-114) has minor stream filtering/sorting. Move to DriverService.
- **D-04:** RaceController.saveResults() DTO-to-service mapping is acceptable controller-layer responsibility (thin adapter). Do NOT move this — it's the controller's job to translate form input.
- **D-05:** SiteGeneratorService.toRaceView() (L272-283) resolves driver-team from SeasonDriver only. Fix to use RaceLineup as primary source with SeasonDriver as fallback — same pattern as ScoringService.isDriverInTeam() and RaceFormDataService.toRaceData().
- **D-06:** SiteGeneratorService.generateDriverProfiles() (L174-182) uses SeasonDriver for profile generation. This is acceptable for season-level profiles (not race-specific). Do NOT change this — it's the correct data source for season-scoped driver lists.
- **D-07:** Inject RaceLineupRepository into SiteGeneratorService for the race-level driver-team lookup.

### Claude's Discretion

- Whether to create new service methods or add to existing services for the extracted controller logic
- Exact method signatures for the extracted service methods
- Test strategy: unit tests for new service methods, update controller tests to verify delegation

### Deferred Ideas (OUT OF SCOPE)

None.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-03 | Controller methods delegate data transformation and business logic to service layer | D-01, D-02, D-03: three identified violations; research maps exact logic to move and target service for each |
| ARCH-04 | Site generator uses RaceLineup as source of truth for driver-team assignment | D-05, D-07: toRaceView() fix identified; canonical pattern documented from ScoringService and RaceFormDataService |
</phase_requirements>

## Summary

Phase 33 addresses three controller methods containing business logic that belongs in the service layer (ARCH-03), plus one service method using the wrong data source for driver-team resolution (ARCH-04).

The controller violations are well-scoped. PowerRankingsController.index() performs season grouping, team count aggregation, and sorted list building — all at L33-55. MatchdayController.detail() computes graphics-button status flags from match/race data at L60-66. DriverController.mergeForm() filters and sorts the driver list at L111-114. None of these touch HTTP concerns; they are pure data queries and transformations.

The SiteGeneratorService.toRaceView() fix is a one-method change: replace the inline SeasonDriver lookup with `raceLineupRepository.findByRaceIdAndDriverId(raceId, driverId)` and fall back to SeasonDriver on empty result. The canonical pattern is already established in two other services (ScoringService and RaceFormDataService), so no design decisions are needed — just apply the same three-line pattern.

**Primary recommendation:** Add `getSeasonGroupOptions()` to `SeasonManagementService` (it already owns `findAll()` and all Season queries); add `getMatchdayGraphicStatus()` to `MatchdayService` returning a record; add `getMergeFormDrivers(UUID excludeId)` to `DriverService`; inject `RaceLineupRepository` into `SiteGeneratorService` and fix `toRaceView()`.

## Standard Stack

All implementation uses the existing project stack. No new dependencies are introduced.

| Component | Current Version | Notes |
|-----------|----------------|-------|
| Spring Boot | 4.0.5 | `@Service`, `@RequiredArgsConstructor`, records as return types |
| Java | 25 | `var`, records, streams — all already used throughout codebase |
| JUnit 5 + Mockito | project standard | `@ExtendWith(MockitoExtension.class)` for unit tests, `@SpringBootTest` for controller integration tests |
| JaCoCo | project standard | 82% minimum; new service methods must be covered |

[VERIFIED: codebase inspection]

## Architecture Patterns

### Established Pattern: Service Record Return Type

Services return purpose-built records for controller-bound data. Example in MatchdayService:

```java
// Source: src/main/java/org/ctc/domain/service/MatchdayService.java
public record MatchdayDetailData(Matchday matchday, Map<String, List<RaceLineup>> lineupsByTeam) {}
```

This is the pattern for D-02. Return a record from `getMatchdayGraphicStatus()` rather than multiple separate method calls.

### Established Pattern: Static Inner Class as Service Output

SeasonManagementService already uses static inner records for multiple service outputs:

```java
// Source: src/main/java/org/ctc/domain/service/SeasonManagementService.java
public record SeasonDetailData(Season season, Playoff playoff, ...) {}
```

The `SeasonGroupOption` record currently lives in the controller. It must move to SeasonManagementService alongside the new method. [VERIFIED: codebase inspection]

### Established Pattern: RaceLineup-First with SeasonDriver Fallback

Two canonical references exist. Use either as the model for `toRaceView()` fix.

**ScoringService.isDriverInTeam()** (src/main/java/org/ctc/domain/service/ScoringService.java L137-152):
```java
// Source: ScoringService.java
var lineup = raceLineupRepository.findByRaceIdAndDriverId(raceId, result.getDriver().getId());
if (lineup.isPresent()) {
    UUID lineupTeamId = lineup.get().getTeam().getId();
    return lineupTeamId.equals(teamId)
            || (lineup.get().getTeam().getParentTeam() != null
                && lineup.get().getTeam().getParentTeam().getId().equals(teamId));
}
// Fallback for legacy data without RaceLineup — filter by current season
var race = raceRepository.findById(raceId).orElse(null);
if (race == null || race.getMatchday() == null) return false;
var seasonId = race.getMatchday().getSeason().getId();
return result.getDriver().getSeasonDrivers().stream()
        .filter(sd -> sd.getSeason().getId().equals(seasonId))
        .anyMatch(sd -> sd.getTeam().getId().equals(teamId));
```

**RaceFormDataService.toRaceData()** (src/main/java/org/ctc/domain/service/RaceFormDataService.java L149-155):
```java
// Source: RaceFormDataService.java — toRaceData() inner loop
String teamShortName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())
        .map(rl -> rl.getTeam().getShortName())
        .orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
                .filter(sd -> sd.getSeason().getId().equals(race.getMatchday().getSeason().getId()))
                .map(sd -> sd.getTeam().getShortName())
                .findFirst().orElse("?"));
```

The `toRaceView()` fix should use the `RaceFormDataService` pattern directly — it resolves `teamShortName` which is exactly what `toRaceView()` needs. [VERIFIED: codebase inspection]

### Existing RaceLineupRepository Query

The required query already exists:

```java
// Source: src/main/java/org/ctc/domain/repository/RaceLineupRepository.java
Optional<RaceLineup> findByRaceIdAndDriverId(UUID raceId, UUID driverId);
```

No new repository method is needed. The raceId is `race.getId()` and driverId is `r.getDriver().getId()` inside the `toRaceView()` lambda. [VERIFIED: codebase inspection]

### Existing Test Patterns

**Controller integration tests** use `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("dev") + @Transactional`. They verify model attributes via MockMvc. Graphic services are `@MockitoBean`. Real service logic runs against H2.

**Service unit tests** use `@ExtendWith(MockitoExtension.class)` with `@Mock` repositories and `@InjectMocks` service. Assertions use AssertJ (`assertThat`) or JUnit 5 (`assertEquals`, `assertThatThrownBy`). Given-When-Then naming: `givenContext_whenAction_thenExpectedResult()`.

[VERIFIED: codebase inspection of PowerRankingsControllerTest, MatchdayControllerTest, DriverControllerTest, DriverServiceTest, MatchdayServiceTest, StandingsServiceTest]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Season grouping by year+number | Custom Map manipulation in controller | Method on SeasonManagementService using existing `findAll()` |
| Schedule completeness check | Inline stream in controller | Method on MatchdayService — it already owns matchday data |
| Driver list for merge form | Controller-level filter+sort | Method on DriverService — it already has `findAll()` |
| RaceLineup lookup | New query method | `RaceLineupRepository.findByRaceIdAndDriverId()` already exists |

## Task Breakdown by Decision

### D-01: PowerRankingsController — Season Grouping Extraction

**Current code (L33-55 in PowerRankingsController):**
```java
// Groups seasons by year+number, counts teams per group, sorts descending
List<Season> allSeasons = seasonManagementService.findAll();
var seasonGroups = allSeasons.stream()
        .collect(Collectors.groupingBy(
                s -> s.getYear() + "|" + s.getNumber(),
                LinkedHashMap::new,
                Collectors.toList()))
        .entrySet().stream()
        .map(entry -> {
            var seasons = entry.getValue();
            var first = seasons.getFirst();
            int teamCount = seasons.stream()
                    .mapToInt(s -> s.getSeasonTeams().size())
                    .sum();
            return new SeasonGroupOption(
                    first.getYear(),
                    first.getNumber(),
                    "Season " + first.getNumber() + " (" + first.getYear() + ") — " + teamCount + " Teams",
                    teamCount
            );
        })
        .sorted(Comparator.comparingInt(SeasonGroupOption::year).reversed()
                .thenComparingInt(SeasonGroupOption::number).reversed())
        .toList();
```

**Target service:** `SeasonManagementService` — it already owns `findAll()` and all Season data access. No new repository injection needed.

**New method signature:**
```java
// In SeasonManagementService
public record SeasonGroupOption(int year, int number, String label, int teamCount) {}

@Transactional(readOnly = true)
public List<SeasonGroupOption> getSeasonGroupOptions() { ... }
```

**After extraction, controller becomes:**
```java
model.addAttribute("seasonGroups", seasonManagementService.getSeasonGroupOptions());
```

**Record relocation:** `SeasonGroupOption` moves from `PowerRankingsController` (where it is a local record) to `SeasonManagementService` as a public static inner record. The template references `seasonGroups` — no template change needed since it uses the same field names. The controller test uses `model().attributeExists("seasonGroups")` — no change needed. The test for the new service method is a new unit test in `SeasonManagementServiceTest`.

[VERIFIED: codebase inspection]

### D-02: MatchdayController — Schedule Status Extraction

**Current code (L60-66 in MatchdayController):**
```java
var nonByeMatches = matchday.getMatches().stream().filter(m -> !m.isBye()).toList();
model.addAttribute("hasMatches", !nonByeMatches.isEmpty());
model.addAttribute("hasSchedule", nonByeMatches.stream()
        .anyMatch(m -> m.getRaces().stream().anyMatch(r -> r.getDateTime() != null)));
long matchesWithDateTime = nonByeMatches.stream()
        .filter(m -> m.getRaces().stream().anyMatch(r -> r.getDateTime() != null)).count();
model.addAttribute("scheduleMissingCount", nonByeMatches.size() - matchesWithDateTime);
model.addAttribute("hasResults", nonByeMatches.stream()
        .anyMatch(m -> m.getHomeScore() != null && m.getAwayScore() != null));
```

**Target service:** `MatchdayService` — already owns matchday detail lookup and the `MatchdayDetailData` record.

**Option A: Extend MatchdayDetailData** to include the graphic status fields directly. This is the cleanest approach since `getMatchdayDetail()` already returns the matchday entity.

**Option B: Separate method** `getGraphicStatus(Matchday matchday)` returning a new record. Less disruption to existing `MatchdayDetailData` callers.

**Recommended: Option A** — extend `MatchdayDetailData`. The existing record already returns the matchday and lineupsByTeam. Add three boolean/int fields:

```java
public record MatchdayDetailData(
    Matchday matchday,
    Map<String, List<RaceLineup>> lineupsByTeam,
    boolean hasMatches,
    boolean hasSchedule,
    long scheduleMissingCount,
    boolean hasResults
) {}
```

Controller becomes:
```java
var data = matchdayService.getMatchdayDetail(id);
model.addAttribute("matchday", data.matchday());
model.addAttribute("lineupsByTeam", data.lineupsByTeam());
model.addAttribute("hasMatches", data.hasMatches());
model.addAttribute("hasSchedule", data.hasSchedule());
model.addAttribute("scheduleMissingCount", data.scheduleMissingCount());
model.addAttribute("hasResults", data.hasResults());
```

**Impact on existing MatchdayControllerTest:** The test `givenMatchdayWithMatches_whenGetDetail_thenGraphicFlagsPresent()` already verifies `hasMatches`, `hasSchedule`, `hasResults` exist in the model — it passes through unchanged. The test `givenMatchdayWithNoMatches_whenGetDetail_thenHasMatchesFalse()` verifies `hasMatches` is false — passes through unchanged. The logic moves but the contract stays identical.

**New unit tests** required in `MatchdayServiceTest` to verify the new logic in isolation.

[VERIFIED: codebase inspection]

### D-03: DriverController — Merge Form Driver List Extraction

**Current code (L111-114 in DriverController):**
```java
var allDrivers = driverService.findAll().stream()
        .filter(d -> !d.getId().equals(id))
        .sorted(Comparator.comparing(Driver::getPsnId, String.CASE_INSENSITIVE_ORDER))
        .toList();
```

**Target service:** `DriverService` — owns `findAll()` and all driver data.

**New method signature:**
```java
// In DriverService
@Transactional(readOnly = true)
public List<Driver> getMergeFormDrivers(UUID excludeDriverId) {
    return driverRepository.findAll().stream()
            .filter(d -> !d.getId().equals(excludeDriverId))
            .sorted(Comparator.comparing(Driver::getPsnId, String.CASE_INSENSITIVE_ORDER))
            .toList();
}
```

Controller becomes:
```java
var allDrivers = driverService.getMergeFormDrivers(id);
```

**Impact on existing DriverControllerTest:** The test `givenTwoDrivers_whenGetMergeForm_thenSourceExcludedFromDropdown()` directly verifies that source is absent and target is present in `allDrivers`. This test continues to pass since the model attribute is populated by the new service method with identical behavior.

**New unit test** in `DriverServiceTest`: `givenDrivers_whenGetMergeFormDrivers_thenExcludesIdAndSortsByCaseInsensitivePsnId()`.

[VERIFIED: codebase inspection]

### D-05 + D-07: SiteGeneratorService — toRaceView() RaceLineup Fix

**Current code (L273-280 in SiteGeneratorService):**
```java
var teamShortName = r.getDriver().getSeasonDrivers().stream()
        .filter(sd -> sd.getSeason().getId().equals(season.getId()))
        .map(sd -> sd.getTeam().getShortName())
        .findFirst().orElse("?");
```

**Fix (identical to RaceFormDataService.toRaceData() pattern):**
```java
String teamShortName = raceLineupRepository
        .findByRaceIdAndDriverId(race.getId(), r.getDriver().getId())
        .map(rl -> rl.getTeam().getShortName())
        .orElseGet(() -> r.getDriver().getSeasonDrivers().stream()
                .filter(sd -> sd.getSeason().getId().equals(season.getId()))
                .map(sd -> sd.getTeam().getShortName())
                .findFirst().orElse("?"));
```

**Required injection:** Add `private final RaceLineupRepository raceLineupRepository;` to `SiteGeneratorService`. `@RequiredArgsConstructor` handles constructor injection automatically.

**Impact on SiteGeneratorServiceTest:** The existing test setUp creates `SeasonDriver` entries but no `RaceLineup` entries. The fix preserves the SeasonDriver fallback path, so all existing tests continue to pass (they exercise the fallback branch). To specifically test the RaceLineup-primary path, a new test case should be added that creates a `RaceLineup` entry and verifies the team name comes from the lineup rather than the SeasonDriver.

**Note on homeTotal/awayTotal calculation (L286-292):** The score aggregation in `toRaceView()` uses `r.getTeamShortName().equals(race.getHomeTeam().getShortName())` to partition results into home vs. away. After the fix, `teamShortName` may now come from a RaceLineup for a sub-team. If a sub-team's short name differs from the parent team's short name, the home/away score split will now reflect the actual lineup team — which is correct behavior. This is a fix, not a regression.

[VERIFIED: codebase inspection]

## Common Pitfalls

### Pitfall 1: MatchdayDetailData Record Expansion Breaks Existing Callers

**What goes wrong:** Java records are immutable value types. Adding fields to `MatchdayDetailData` requires updating every constructor call site. Search for `new MatchdayDetailData(` in the codebase before implementing.

**Why it happens:** Records have no default field values; all fields are required in the constructor.

**How to avoid:** Before modifying `MatchdayDetailData`, grep for `new MatchdayDetailData(` to find all construction sites. Update all of them. If there are too many call sites, use Option B (separate method) instead of Option A.

**Current callers to check:** `MatchdayService.getMatchdayDetail()` (creates it) and `MatchdayControllerTest` and any other test that calls `matchdayService.getMatchdayDetail()`.

### Pitfall 2: SeasonGroupOption Record Visibility

**What goes wrong:** The `SeasonGroupOption` record moves from `PowerRankingsController` to `SeasonManagementService`. Any code referencing `PowerRankingsController.SeasonGroupOption` (e.g., in templates via Thymeleaf or in tests) breaks.

**Why it happens:** Record moves from one class to another; fully-qualified name changes.

**How to avoid:** Search for `SeasonGroupOption` across the codebase before moving. The template uses it implicitly via `th:each` with field access — Thymeleaf calls getters, so template changes are not needed. The controller test uses `model().attributeExists("seasonGroups")` without type checking the record — no test change needed.

**Current references:** Only in `PowerRankingsController.java` itself (construction site and type usage). [VERIFIED: codebase inspection]

### Pitfall 3: SiteGeneratorService Transaction Scope

**What goes wrong:** `SiteGeneratorService.generate()` is annotated `@Transactional(readOnly = true)`. Adding `raceLineupRepository.findByRaceIdAndDriverId()` calls inside the same transaction is safe. The concern is N+1: `toRaceView()` is called once per race, and for each race it calls `findByRaceIdAndDriverId()` once per result. With N drivers per race and M races, this is N×M queries.

**Why it happens:** The existing SeasonDriver lookup is also N+1 (stream over `r.getDriver().getSeasonDrivers()` which is lazy-loaded). Replacing one N+1 with another is not worse, but it is worth noting.

**How to avoid:** The fix is architecturally correct. The N+1 performance concern is pre-existing and out of scope for this phase. Do not attempt to batch the RaceLineup queries.

### Pitfall 4: Controller Test Coverage for Extracted Logic

**What goes wrong:** After extraction, the controller test `givenMatchdayWithMatches_whenGetDetail_thenGraphicFlagsPresent()` still passes but no longer tests the logic — it tests delegation. The logic itself must have its own service-layer unit test. Missing this means coverage appears maintained but the logic is untested.

**How to avoid:** For each extracted piece of logic, write a dedicated unit test in the corresponding service test class. Coverage check: run `./mvnw verify` and confirm JaCoCo stays at or above 82%.

## Code Examples

### Verified: How to Extend MatchdayDetailData and Populate It

```java
// Source: MatchdayService.java — getMatchdayDetail() method (current)
public MatchdayDetailData getMatchdayDetail(UUID id) {
    var matchday = matchdayRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Matchday", id));
    var lineups = raceLineupRepository.findByRaceMatchdayId(id);
    var lineupsByTeam = lineups.stream()
            .collect(Collectors.groupingBy(
                    lu -> lu.getTeam().getShortName(),
                    LinkedHashMap::new,
                    Collectors.toList()));
    return new MatchdayDetailData(matchday, lineupsByTeam);
    // After change: add status fields computed here from matchday.getMatches()
}
```

### Verified: SeasonManagementService Has findAll() Already

```java
// Source: SeasonManagementService.java L47-49
@Transactional(readOnly = true)
public List<Season> findAll() {
    return seasonRepository.findAll();
}
```

The new `getSeasonGroupOptions()` method can call `findAll()` internally or be annotated with its own `@Transactional(readOnly = true)` and use the repository directly.

### Verified: RaceFormDataService Pattern to Copy for toRaceView() Fix

```java
// Source: RaceFormDataService.java L149-155 — toRaceData() inner loop
String teamShortName = raceLineupRepository.findByRaceIdAndDriverId(race.getId(), result.getDriver().getId())
        .map(rl -> rl.getTeam().getShortName())
        .orElseGet(() -> result.getDriver().getSeasonDrivers().stream()
                .filter(sd -> sd.getSeason().getId().equals(race.getMatchday().getSeason().getId()))
                .map(sd -> sd.getTeam().getShortName())
                .findFirst().orElse("?"));
```

In `SiteGeneratorService.toRaceView()`, the equivalent variables are:
- `race.getId()` — available as the outer `race` parameter
- `r.getDriver().getId()` — `r` is the `RaceResult` loop variable
- `season.getId()` — available as the outer `season` parameter

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + MockMvc |
| Config file | `src/test/resources/logback-test.xml` (test logging suppressed) |
| Quick run command | `./mvnw test -pl . -Dtest=PowerRankingsControllerTest,MatchdayControllerTest,DriverControllerTest,SiteGeneratorServiceTest -Dsurefire.failIfNoSpecifiedTests=false` |
| Full suite command | `./mvnw verify` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ARCH-03 | SeasonManagementService.getSeasonGroupOptions() returns correct groups | unit | `./mvnw test -Dtest=SeasonManagementServiceTest` | ✅ (add test method) |
| ARCH-03 | PowerRankingsController delegates to service, model contains seasonGroups | integration | `./mvnw test -Dtest=PowerRankingsControllerTest` | ✅ (update test) |
| ARCH-03 | MatchdayService.getMatchdayDetail() includes graphic status fields | unit | `./mvnw test -Dtest=MatchdayServiceTest` | ✅ (add test method) |
| ARCH-03 | MatchdayController delegates graphic status to service | integration | `./mvnw test -Dtest=MatchdayControllerTest` | ✅ (existing tests pass) |
| ARCH-03 | DriverService.getMergeFormDrivers() excludes id and sorts by psnId | unit | `./mvnw test -Dtest=DriverServiceTest` | ✅ (add test method) |
| ARCH-03 | DriverController delegates allDrivers to service | integration | `./mvnw test -Dtest=DriverControllerTest` | ✅ (existing tests pass) |
| ARCH-04 | SiteGeneratorService.toRaceView() uses RaceLineup when available | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ✅ (add test method) |
| ARCH-04 | SiteGeneratorService.toRaceView() falls back to SeasonDriver | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ✅ (existing tests cover fallback) |

### Sampling Rate

- **Per task commit:** Quick run targeting affected test class
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** `./mvnw verify` green before `/gsd-verify-work`

### Wave 0 Gaps

None — existing test infrastructure covers all phase requirements. The four identified test classes exist. New test methods are needed within existing classes, not new files.

## Security Domain

No new HTTP endpoints. No new authentication/authorization surfaces. No file I/O changes. No user input handling changes.

ASVS categories not applicable to this phase. The changes are:
- Internal service method extraction (no security boundary changes)
- Repository injection (existing repository, no new data access pattern)
- Bug fix in data source selection (improves correctness, not security)

## Environment Availability

Step 2.6: SKIPPED — this phase is code-only refactoring. No external dependencies beyond the existing project stack (Java 25, Maven, H2 for tests). All dependencies are already verified by the running test suite on this branch.

## Assumptions Log

All claims in this research were verified by codebase inspection. No assumed claims.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| — | — | — | — |

**This table is empty:** All claims in this research were verified or cited — no user confirmation needed.

## Open Questions

None. All required information was found in the codebase.

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java` — season grouping logic L33-55, SeasonGroupOption record L87
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` — graphic status logic L60-66
- `src/main/java/org/ctc/admin/controller/DriverController.java` — merge form driver list L111-114
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — toRaceView() L272-299, current SeasonDriver-only resolution L275-279
- `src/main/java/org/ctc/domain/service/ScoringService.java` — isDriverInTeam() RaceLineup-first pattern L137-152
- `src/main/java/org/ctc/domain/service/RaceFormDataService.java` — toRaceData() RaceLineup fallback pattern L149-155
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — findByRaceIdAndDriverId() L19
- `src/main/java/org/ctc/domain/service/MatchdayService.java` — MatchdayDetailData record, getMatchdayDetail() method
- `src/main/java/org/ctc/domain/service/DriverService.java` — findAll(), getMergeFormDrivers target
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — findAll(), getSeasonGroupOptions target
- Test files: PowerRankingsControllerTest, MatchdayControllerTest, DriverControllerTest, SiteGeneratorServiceTest, DriverServiceTest, MatchdayServiceTest, StandingsServiceTest
- `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/CONVENTIONS.md`, `./CLAUDE.md`

## Metadata

**Confidence breakdown:**
- Task decomposition (D-01, D-03): HIGH — logic is simple, target service obvious, existing tests cover the contract
- Task decomposition (D-02): HIGH — Option A (extend MatchdayDetailData) is clean; only risk is call site count (grep needed before implementing)
- Task decomposition (D-05, D-07): HIGH — canonical pattern already exists in two services; no design decisions needed
- Test strategy: HIGH — test patterns are established throughout the codebase

**Research date:** 2026-04-14
**Valid until:** Until SeasonManagementService, MatchdayService, DriverService, or SiteGeneratorService are significantly refactored
