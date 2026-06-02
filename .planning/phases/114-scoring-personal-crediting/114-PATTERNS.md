# Phase 114: Scoring & Personal Crediting — Pattern Map

**Mapped:** 2026-06-01
**Files analyzed:** 6 (3 modified, 3 new)
**Analogs found:** 6 / 6

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/domain/service/DriverRankingService.java` | service | request-response (read-model) | `src/main/java/org/ctc/domain/service/ScoringService.java` | role-match (same service layer, same RaceLineup-resolution pattern) |
| `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` | service (sitegen) | batch / transform | self (extend existing second-pass iteration) | exact |
| `src/main/java/org/ctc/admin/TestDataService.java` (`seedRaceLineups` extension) | utility / seeder | batch | self (extend existing `seedRaceLineups` method) | exact |
| `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` (new test methods) | test (unit) | — | self (extend existing file) | exact |
| `src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java` (new file) | test (integration) | — | `src/test/java/db/migration/V18MigrationIT.java` + `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` | role-match |
| `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` (new test method) | test (sitegen IT) | — | self (extend existing file) | exact |

---

## Pattern Assignments

### `src/main/java/org/ctc/domain/service/DriverRankingService.java` (modify — unified attribution helper)

**Analog:** `src/main/java/org/ctc/domain/service/ScoringService.java` (canonical RaceLineup resolution pattern)

**Imports pattern** (`DriverRankingService.java` lines 1–13):
```java
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
```
The new unified helper needs `SeasonDriverRepository` (already injected at line 21) — no new imports required.

**Existing sub-team→parent rollup pattern** (`ScoringService.java` lines 218–223 — the canonical model):
```java
public boolean isDriverInTeam(RaceResult result, UUID raceId, UUID teamId) {
    var lineup = raceLineupRepository.findByRaceIdAndDriverId(raceId, result.getDriver().getId());
    if (lineup.isPresent()) {
        UUID lineupTeamId = lineup.get().getTeam().getId();
        return lineupTeamId.equals(teamId)
                || (lineup.get().getTeam().getParentTeam() != null
                && lineup.get().getTeam().getParentTeam().getId().equals(teamId));
    }
    // ...
}
```
The new `resolveAttributedTeam` must call `rl.getTeam().getParentOrSelf()` (not raw `rl.getTeam()`), mirroring the parent-rollup logic here. `getParentOrSelf()` is the `Team` model convenience — confirm its existence at build time; `isDriverInTeam` checks `getParentTeam()` directly, but `DriverRanking` accumulation uses `getParentOrSelf()` (line 144 of `DriverRankingService.java`).

**Home-first attribution — SeasonDriver lookup** (`SeasonDriverRepository` — already in `DriverRankingService` constructor at line 21):
```java
// Existing method — confirmed at SeasonDriverRepository.java:19
Optional<SeasonDriver> findBySeasonIdAndDriverId(UUID seasonId, UUID driverId);
```
The D-01 home-first rule uses `seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, driver.getId())`.

**computeIfAbsent guard pattern** (`DriverRankingService.java` lines 50–56 — guard that ensures attribution helper is called only once per driver):
```java
Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();
for (RaceResult result : all) {
    UUID driverId = result.getDriver().getId();
    rankingMap.computeIfAbsent(driverId, id -> {
        Team team = resolveTeamFromLineup(driverId, result.getRace()); // replace this call
        return new DriverRanking(result.getDriver(), team);
    }).addResult(result);
}
```
The new `resolveAttributedTeam` replaces `resolveTeamFromLineup` here. The `computeIfAbsent` guard is kept intact to avoid N+1 (helper called once per driver, not once per result).

**Alltime driverTeamMap pattern** (`DriverRankingService.java` lines 137–152 — the D-04 gap to augment):
```java
Map<UUID, Team> driverTeamMap = allSeasonDrivers.stream()
        .collect(Collectors.groupingBy(sd -> sd.getDriver().getId()))
        .entrySet().stream()
        .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream()
                        .max(Comparator.comparing(sd -> sd.getSeason().getName()))
                        .map(sd -> sd.getTeam().getParentOrSelf())
                        .orElse(null)));

// ... then for each result:
DriverRanking ranking = rankingMap.computeIfAbsent(driverId,
        id -> new DriverRanking(result.getDriver(), driverTeamMap.get(id)));
```
After building `driverTeamMap`, augment it with a lineup fallback for drivers missing from SeasonDriver (D-04 fix). Use `raceLineupRepository.findByDriverId(UUID driverId)` (line 21 of `RaceLineupRepository.java` — no `@EntityGraph`, lazy-loads `team`; safe because `calculateAlltimeRanking` is `@Transactional(readOnly = true)` and OSIV is active).

**SeasonId derivation for calculateRankingForPhase** (`DriverRankingService.java` line 40):
```java
seasonPhaseService.findById(phaseId); // validate phase exists — already loaded
```
The `SeasonPhase` entity returned here carries `getSeason().getId()`. Assign to a local variable to derive `seasonId` without adding a parameter to the public method signature.

**Logging pattern** (`DriverRankingService.java` lines 61, 98, 159):
```java
log.debug("Calculated driver ranking for phase {}: {} drivers", phaseId, rankings.size());
```
Use parameterized `{}` format. No inline comments referencing phase numbers or plans.

---

### `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java` (modify — pure-guest second pass for D-05)

**Analog:** self (lines 50–132)

**Existing dedup set pattern** (`DriverProfilePageGenerator.java` lines 53–54):
```java
var seasonDrivers = seasonDriverRepository.findBySeasonId(season.getId());
var generatedDriverIds = new java.util.HashSet<java.util.UUID>();
```

**Existing first-pass loop guard** (`DriverProfilePageGenerator.java` lines 60–63):
```java
for (var sd : seasonDrivers) {
    var driver = sd.getDriver();
    if (!generatedDriverIds.add(driver.getId())) {
        continue;
    }
```
The second pass uses the same `generatedDriverIds.add(driver.getId())` check — returns `false` for drivers already generated by the first pass (dual-role dedup). Use `add` not `contains`, because `add` simultaneously records and checks.

**Lineup-scoped query available** (`RaceLineupRepository.java` lines 29–31):
```java
@EntityGraph(attributePaths = {"driver", "team"})
@Query("SELECT rl FROM RaceLineup rl WHERE rl.race.matchday.phase.season.id = :seasonId")
List<RaceLineup> findByRaceMatchdaySeasonId(UUID seasonId);
```
Use `raceLineupRepository.findByRaceMatchdaySeasonId(season.getId())` to discover all drivers who appeared in the season, then stream `.map(RaceLineup::getDriver).distinct()` to get the unique set. This method is already injected at line 47 of the generator.

**Team-from-lineup resolution for second pass** (`RaceLineupRepository.java` lines 37–39):
```java
@EntityGraph(attributePaths = {"driver", "team"})
@Query("SELECT rl FROM RaceLineup rl WHERE rl.driver.id = :driverId AND rl.race.matchday.phase.season.id = :seasonId")
List<RaceLineup> findByDriverIdAndRaceMatchdaySeasonId(UUID driverId, UUID seasonId);
```
For each pure guest, call `.stream().findFirst().map(rl -> rl.getTeam().getParentOrSelf()).orElse(null)` to get the fielding team with parent rollup. Guard `team == null` in the second pass.

**Context population pattern** (`DriverProfilePageGenerator.java` lines 103–128 — copy this structure exactly for the second pass):
```java
var context = new Context(Locale.ENGLISH);
context.setVariable("season", season);
context.setVariable("driver", driver);
context.setVariable("team", team);   // lineup-resolved team for pure guest
context.setVariable("results", results);
int total = results.stream().mapToInt(RaceResult::getPointsTotal).sum();
context.setVariable("totalRaces", results.size());
context.setVariable("totalPoints", total);
context.setVariable("averagePoints", results.isEmpty() ? 0.0 : (double) total / results.size());
context.setVariable("bestPosition", results.isEmpty() ? null :
        results.stream().mapToInt(RaceResult::getPosition).min().orElse(0));
context.setVariable("currentPage", "driver");
context.setVariable("seasonSlug", siteSlugger.slugify(season.getDisplayLabel()));
// ... all remaining variables ...
templateWriter.write("site/driver-profile", context, dir.resolve(...), ...);
result.incrementPages();
```
Second-pass page is structurally identical to first-pass — only `team` differs (lineup-resolved). `showPhaseBreakdown` and `resultsByPhase` are computed identically for the guest driver using the same logic from lines 77–100.

---

### `src/main/java/org/ctc/admin/TestDataService.java` (`seedRaceLineups` — guest fixture D-11)

**Analog:** self (`seedRaceLineups`, lines 827–923)

**Test-prefix isolation pattern** (`TestDataService.java` lines 835–846):
```java
var testAlpha = teamRepository.save(new Team("Test Alpha Racing", "T-ALF"));
var testBravo = teamRepository.save(new Team("Test Bravo Racing", "T-BRV"));
var testBravo1 = teamRepository.save(new Team("Test Bravo Racing 1", "T-BRV 1", testBravo));
// ...
var tda1 = driver("Test_Alpha_1", "Test Alpha Driver 1");
var tdb1 = driver("Test_Bravo1_1", "Test Bravo1 Driver 1");
```
New guest drivers must follow the same prefix conventions: PSN IDs like `"Test_Guest_1"` (pure guest) and `"Test_DualRole_1"` (dual-role). No real teams, no real drivers.

**Inline lineup creation pattern** (`TestDataService.java` lines 877–880):
```java
raceLineupRepository.save(new RaceLineup(race1, tda1, testAlpha));       // normal — 3-arg constructor
raceLineupRepository.save(new RaceLineup(race1, tda2, testAlpha));
raceLineupRepository.save(new RaceLineup(race1, tdb1, testBravo1));
```
Guest lineup rows use the 4-arg constructor (`RaceLineup.java` line 47):
```java
// 4-arg constructor: (race, driver, team, boolean guest)
raceLineupRepository.save(new RaceLineup(race1, testGuestDriver, testBravo1, true));
```

**SeasonDriver for dual-role home-team** (`TestDataService.java`, `seedSeasonDrivers` at line 450 — pattern for adding home-team roster entry):
```java
seasonDriverRepository.save(new SeasonDriver(testSeason1, tdDualRoleDriver, testAlpha)); // home team = T-ALF
// + guest lineup for T-BRV 1 (is_guest=true) — wired inline in seedRaceLineups
```
For the dual-role scenario: create a `SeasonDriver` for the driver in `testAlpha` (home team), and also save a `RaceLineup(race, driver, testBravo1, true)`. For the pure guest: no `SeasonDriver`, only a `RaceLineup(race, driver, testBravo2, true)`.

**RaceResult seeding with scoring** — existing races in `seedRaceLineups` do NOT add `RaceResult` rows for the test season. To make D-13/D-14/D-15 ITs testable, `seedRaceLineups` should add `RaceResult` rows for the guest races using the `ScoringService` (already injected at line 57). Pattern from `seedMatchdaysAndResults` (not shown above, but injection confirms `scoringService` is available for `calculatePoints` + `scoringService.aggregateMatchScores(race)`).

**log.info pattern** (`TestDataService.java` line 921):
```java
log.info("Created test data: {} test-teams, {} test-drivers, {} races, {} lineups",
        4, 6, raceRepository.count(), raceLineupRepository.count());
```
Update the count after adding guest rows — adjust the static counts or use `raceLineupRepository.count()`.

---

### `src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java` (extend — new unit test methods)

**Analog:** self (existing file, lines 1–577)

**Class-level annotations** (`DriverRankingServiceTest.java` lines 24–25 — no `@Tag` needed for plain unit tests):
```java
@ExtendWith(MockitoExtension.class)
class DriverRankingServiceTest {
```
Plain unit test — no `@Tag`, no `@SpringBootTest`. This matches CLAUDE.md "Tag Tests by Category": unit tests stay untagged.

**Mock setup pattern** (lines 27–43):
```java
@Mock private RaceResultRepository raceResultRepository;
@Mock private SeasonDriverRepository seasonDriverRepository;
@Mock private SeasonPhaseService seasonPhaseService;
@Mock private PhaseTeamRepository phaseTeamRepository;
@Mock private RaceLineupRepository raceLineupRepository;
@InjectMocks private DriverRankingService driverRankingService;
```
The new unified helper uses `SeasonDriverRepository.findBySeasonIdAndDriverId` — already mocked via `@Mock private SeasonDriverRepository seasonDriverRepository`.

**dual-role test pattern** — closest existing analog is `givenPerPhaseRanking_whenRaceLineupExists_thenTeamPopulatedFromLineup` (lines 349–377). New test structure for D-01/D-03:
```java
@Test
void givenDualRoleGuest_whenCalculateRankingForPhase_thenAttributedToHomeTeam() {
    // given
    var homeTeam = ...; // Team A (SeasonDriver team)
    var guestTeam = ...; // Team B (RaceLineup team for this race, is_guest=true)
    var driver = ...;
    var race = ...; race.setId(UUID.randomUUID());
    var result = createResult(race, driver, 20, 1);
    var lineup = new RaceLineup(race, driver, guestTeam, true); // guest for guestTeam

    // SeasonDriver: driver rostered in homeTeam for this season
    var sd = new SeasonDriver(season, driver, homeTeam);

    when(seasonPhaseService.findById(regularPhase.getId())).thenReturn(regularPhase);
    when(raceResultRepository.findByRaceMatchdayPhaseId(regularPhase.getId())).thenReturn(List.of(result));
    when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regularPhase.getId())).thenReturn(List.of());
    when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId()))
            .thenReturn(Optional.of(lineup));
    // Home-first: SeasonDriver lookup returns homeTeam
    when(seasonDriverRepository.findBySeasonIdAndDriverId(any(UUID.class), eq(driver.getId())))
            .thenReturn(Optional.of(sd));

    // when
    var rankings = driverRankingService.calculateRankingForPhase(regularPhase.getId());

    // then — D-01: attributed to home team (SeasonDriver), not guest team
    assertThat(rankings).hasSize(1);
    assertThat(rankings.get(0).getTeam()).isEqualTo(homeTeam);
}
```

**Pure guest alltime pattern** — closest existing analog is `givenDriverAcrossMultipleSeasons_whenCalculateAlltimeRanking_thenPointsAggregated` (lines 199–228). New D-04 test mocks `seasonDriverRepository.findAll()` with empty list for the guest driver and `raceLineupRepository.findByDriverId` returning a lineup entry.

**Test naming convention** (CLAUDE.md "Test Naming Given-When-Then"):
```
givenDualRoleGuest_whenCalculateRankingForPhase_thenAttributedToHomeTeam()
givenPureGuest_whenCalculateRankingForPhase_thenAttributedToFieldingTeam()
givenPureGuestInAlltime_whenCalculateAlltimeRanking_thenTeamNotNull()
givenDualRoleGuestInAggregate_whenAggregateAcrossPhases_thenSingleRowUnderHomeTeam()
givenDriverWithHomeAndGuestRace_whenAggregateAcrossPhases_thenPointsSummedAdditively()
```

**createResult helper** (`DriverRankingServiceTest.java` lines 567–576 — reuse directly):
```java
private RaceResult createResult(Race race, Driver driver, int totalPoints, int position) {
    var result = new RaceResult();
    result.setRace(race);
    result.setDriver(driver);
    result.setPosition(position);
    result.setQualiPosition(position);
    result.setPointsTotal(totalPoints);
    result.setPointsRace(totalPoints);
    return result;
}
```

---

### `src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java` (new file — integration test)

**Analog:** `src/test/java/db/migration/V18MigrationIT.java` (structure, annotations) + `DriverRankingServiceTest.java` (content conventions)

**Class-level annotations** (`V18MigrationIT.java` lines 17–19 — exact pattern to copy):
```java
@CtcDevSpringBootContext
@Tag("integration")
class DriverRankingServiceGuestIT {
```
`@CtcDevSpringBootContext` = `@SpringBootTest(classes = CtcManagerApplication.class)` + `@ActiveProfiles("dev")`. `@Tag("integration")` is mandatory for `*IT.java` (CLAUDE.md "Tag Tests by Category"). No `@DirtiesContext` — shares the TCF cache.

**Imports** (`CtcDevSpringBootContext.java` lines 1–13 — import pattern):
```java
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
```
Additional imports: `org.ctc.admin.TestDataService`, `org.ctc.domain.service.DriverRankingService`, `org.ctc.domain.service.ScoringService`, relevant repository and model classes.

**Autowired injection pattern** (`V18MigrationIT.java` lines 21–24):
```java
@Autowired private DataSource dataSource;
// → replace with:
@Autowired private DriverRankingService driverRankingService;
@Autowired private ScoringService scoringService;
@Autowired private TestDataService testDataService;    // accesses test fixture data
@Autowired private RaceLineupRepository raceLineupRepository;
@Autowired private RaceResultRepository raceResultRepository;
```
Use `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` + `@BeforeAll` if a shared seed is needed across tests (same pattern as `DriverProfilePageGeneratorTest.java` lines 43, 61–83). However: `TestDataService.seed()` is idempotent (`count() > 0` guard at line 76) — the shared dev-profile context seeds once; ITs can rely on the already-seeded data.

**Test method structure** (CLAUDE.md "Test Naming Given-When-Then"):
```java
@Test
void givenGuestResultInRace_whenAggregateMatchScores_thenCountsForFieldingTeamScore() {
    // given — locate test race and lineup from TestDataService fixture
    // when — scoringService.aggregateMatchScores(race)
    // then — match.getHomeScore() or match.getAwayScore() includes guest points
}

@Test
void givenPureGuestWithNoSeasonDriver_whenAggregateAcrossPhases_thenAppearsInRankingWithFieldingTeam() {
    // given
    // when
    // then — assertThat(rankings).anyMatch(r -> r.getDriver().getPsnId().equals("Test_Guest_1"))
    //         assertThat(...getTeam()).isNotNull()
}

@Test
void givenGuestResultSavedTwice_whenAggregateMatchScores_thenScoresAreIdempotent() {
    // given
    // when — call aggregateMatchScores twice
    // then — same score both times (SCORE-03)
}

@Test
void givenPureGuestInAlltimeScope_whenCalculateAlltimeRankingForSeason_thenTeamIsNotNull() {
    // given
    // when — driverRankingService.calculateAlltimeRanking(List.of(testSeasonId))
    // then — pure guest ranking row has team != null (D-04/D-16)
}
```

---

### `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` (extend — pure-guest profile page test)

**Analog:** self (existing file, lines 1–191)

**Class-level setup** (lines 40–83 — no change needed; `@BeforeAll` already runs `testDataService.seed()` + `siteGeneratorService.generate()`):
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DriverProfilePageGeneratorTest {
    // ...
    @BeforeAll
    void setUp() { /* Flyway clean+migrate + testDataService.seed() + siteGeneratorService.generate() */ }
```
The `@BeforeAll` re-seeds and re-generates. After extending `TestDataService.seedRaceLineups()` with the guest fixture, the new test can resolve the guest's profile page from the same `tempDir`.

**New test method pattern** — closest analog is `givenLeagueOnlySeasonDriver_whenGenerate_thenNoPhaseHeadings` (lines 92–101):
```java
@Test
void givenPureGuestDriver_whenGenerate_thenProfilePageExists() throws IOException {
    // given — Test-Season 2026 pure guest driver "Test_Guest_1" seeded by TestDataService
    Path driverProfile = tempDir.resolve("season")
            .resolve("2026-99-test-season-2026")   // slug based on year=2026, number=99
            .resolve("driver")
            .resolve("test-guest-1.html");          // PSN ID slugified

    // then
    assertThat(driverProfile).exists();
    String html = Files.readString(driverProfile);
    assertThat(html).contains("Test_Guest_1"); // or driver's PsnId
}
```
Season slug pattern for `Test-Season 2026` (year=2026, number=99): use `SiteSlugger.slugify("99 Test-Season 2026")` pattern. Confirm the exact slug by tracing `siteSlugger.slugify(season.getDisplayLabel())` — `Season.getDisplayLabel()` format is `"{number} {name}"` based on existing slug examples (`2026-4-regular-season`).

**Jsoup assertion pattern** — for more semantic validation (lines 127–136):
```java
Document doc = Jsoup.parse(Files.readString(driverProfile));
// assert driver name present, team present, race results present
```

---

## Shared Patterns

### RaceLineup as Source of Truth (sub-team→parent rollup)
**Source:** `src/main/java/org/ctc/domain/service/ScoringService.java` lines 217–223
**Apply to:** `DriverRankingService.java` (new `resolveAttributedTeam` helper), `DriverProfilePageGenerator.java` (second-pass team resolution)
```java
var lineup = raceLineupRepository.findByRaceIdAndDriverId(raceId, driverId);
if (lineup.isPresent()) {
    // sub-team→parent rollup: call getParentOrSelf() not getTeam()
    Team team = lineup.get().getTeam().getParentOrSelf();
}
```
Note: `ScoringService.isDriverInTeam` uses `getParentTeam()` directly for equality check; `DriverRankingService` uses `getParentOrSelf()` for attribution assignment. Both express the same invariant — team attribution always resolves to the parent.

### @Transactional(readOnly = true) on read-model methods
**Source:** `src/main/java/org/ctc/domain/service/DriverRankingService.java` lines 38, 71, 111, 126
**Apply to:** New private `resolveAttributedTeam` helper lives inside already-transactional public methods — no additional `@Transactional` needed on the private method itself.
```java
@Transactional(readOnly = true)
public List<DriverRanking> calculateRankingForPhase(UUID phaseId) { ... }
```

### computeIfAbsent guard (prevents N+1 in attribution helper)
**Source:** `src/main/java/org/ctc/domain/service/DriverRankingService.java` lines 49–57
**Apply to:** All three ranking paths; the new helper is called only inside the `computeIfAbsent` lambda, never per-result.
```java
rankingMap.computeIfAbsent(driverId, id -> {
    Team team = resolveAttributedTeam(result.getDriver(), seasonId, result.getRace().getId());
    return new DriverRanking(result.getDriver(), team);
}).addResult(result);
```

### `@CtcDevSpringBootContext` + `@Tag("integration")` for *IT.java files
**Source:** `src/test/java/db/migration/V18MigrationIT.java` lines 17–19
**Apply to:** `DriverRankingServiceGuestIT.java` (new file)
```java
@CtcDevSpringBootContext
@Tag("integration")
class DriverRankingServiceGuestIT {
```
`@CtcDevSpringBootContext` is defined in `org.ctc.testsupport` — import it, not the raw `@SpringBootTest` + `@ActiveProfiles`.

### Test naming (Given-When-Then)
**Source:** CLAUDE.md "Test Naming (Given-When-Then)" + existing `DriverRankingServiceTest.java` method names
**Apply to:** All new test methods in `DriverRankingServiceTest.java`, `DriverRankingServiceGuestIT.java`, `DriverProfilePageGeneratorTest.java`
```java
// Pattern:
givenContext_whenAction_thenExpectedResult()
// Body structure:
// given
// when
// then
```

### Test-prefix isolation
**Source:** `src/main/java/org/ctc/admin/TestDataService.java` lines 835–846
**Apply to:** All new fixture entities in `seedRaceLineups()` extension
```java
// Teams: "T-…" short names, "Test … Racing" full names
// Drivers: "Test_…" PSN IDs
// Seasons: "Test-Season YYYY"
```
No real teams, no real driver names. New drivers for the guest scenario: `"Test_DualRole_1"` (dual-role) and `"Test_Guest_1"` (pure guest).

### No comment pollution
**Source:** CLAUDE.md "No Comment Pollution"
**Apply to:** All modified/new files — no Phase/Plan/Wave/UAT/Task reference comments in source. Single-line WHY comments for non-obvious constraints only.

### Unused import gate
**Source:** CLAUDE.md "Checkstyle Unused-Import Gate"
**Apply to:** All modified/new files — verify imports at creation time; the `validate` phase fails on any unused or redundant import.

---

## No Analog Found

None — all six files have either exact or role-match analogs in the codebase.

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/domain/service/`, `src/main/java/org/ctc/sitegen/`, `src/main/java/org/ctc/admin/`, `src/test/java/org/ctc/domain/service/`, `src/test/java/org/ctc/sitegen/`, `src/test/java/db/migration/`, `src/test/java/org/ctc/testsupport/`
**Files scanned:** 14 source files read directly
**Pattern extraction date:** 2026-06-01
