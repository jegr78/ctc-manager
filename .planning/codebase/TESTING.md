# Testing Patterns

**Analysis Date:** 2026-04-04

## Test Framework

**Runner:**

- JUnit 5 (Jupiter) via Spring Boot 4.x starter
- Config: `pom.xml` (Maven Surefire for unit/integration, Maven Failsafe for E2E)

**Assertion Libraries:**

- JUnit 5 `Assertions` (`assertEquals`, `assertTrue`, `assertFalse`, `assertThrows`)
- AssertJ (`assertThat(...).isEqualTo(...)`, `assertThatThrownBy(...)`) -- used in some service tests
- Mixed usage: both libraries coexist, no strict preference enforced

**Mocking:**

- Mockito 5.x via `mockito-core` and `mockito-junit-jupiter`
- `@ExtendWith(MockitoExtension.class)` for unit tests
- `@Mock` / `@InjectMocks` pattern for dependency injection

**E2E:**

- Playwright 1.58.0 (Chromium, headless)
- PlaywrightAssertions for page-level assertions

**Run Commands:**

```bash
./mvnw verify                 # Unit + Integration + JaCoCo coverage check
./mvnw verify -Pe2e           # All above + Playwright E2E tests
open target/site/jacoco/index.html  # View coverage report
```

## Test Statistics

- **Test files:** 72 total (69 unit/integration + 3 E2E)
- **Test methods:** 754+ (across all test files)
- **Coverage minimum:** 82% line coverage (JaCoCo enforced, build fails below)
- **Project version:** 1.2.0-SNAPSHOT

## Test File Organization

**Location:** Mirror package structure under `src/test/java/org/ctc/`

**Naming:** `{ClassName}Test.java` for all test types. E2E tests use `{Feature}E2ETest.java` or `{Feature}E2eTest.java` suffix.

**Structure:**

```
src/test/java/org/ctc/
  TestHelper.java                     # Shared test fixture factory (Spring @Component)
  admin/
    SecurityIntegrationTest.java      # Security config tests (prod + dev profiles)
    controller/
      GlobalExceptionHandlerTest.java # Exception handler unit test
      SeasonControllerTest.java       # Integration tests (MockMvc)
      TeamControllerTest.java
      MatchdayControllerTest.java
      RaceControllerTest.java
      PlayoffControllerTest.java
      ...14 controller test files
    service/
      TeamCardServiceTest.java
      AbstractMatchdayGraphicServiceTest.java
      TemplatePreviewServiceTest.java
      ...12 service test files
  dataimport/
    CsvImportServiceTest.java         # Unit tests (Mockito)
    GoogleSheetsServiceTest.java
    ScorecardParserTest.java
    DriverMatchingServiceTest.java
    GoogleCalendarServiceTest.java
  domain/
    exception/
      EntityNotFoundExceptionTest.java
    model/
      BaseEntityAuditTest.java
      RaceScoringTest.java
      RaceSettingsTest.java
      SeasonTest.java
      SeasonTeamTest.java
      RaceTest.java
    service/
      ScoringServiceTest.java         # Unit tests (Mockito)
      StandingsServiceTest.java
      MatchdayServiceTest.java
      MatchdayGeneratorServiceTest.java
      SwissPairingServiceTest.java
      PlayoffServiceTest.java
      MatchScoringServiceTest.java
      RaceScoringServiceTest.java
      ...17 service test files
  e2e/
    PlaywrightConfig.java             # Base class for E2E tests
    AdminWorkflowE2ETest.java         # Playwright E2E
    ScoringE2ETest.java
    ImportE2eTest.java
  gt7sync/
    Gt7ScraperServiceTest.java
    Gt7SyncControllerTest.java
    Gt7SyncServiceTest.java
  sitegen/
    SiteGeneratorServiceTest.java
```

## Test Categories

### Unit Tests (Mockito)

- **Annotation:** `@ExtendWith(MockitoExtension.class)`
- **Pattern:** `@Mock` dependencies + `@InjectMocks` subject under test
- **No Spring context loaded** -- fast execution
- **Used for:** Service logic, parsers, domain model behavior, exception handler
- **Examples:** `ScoringServiceTest`, `StandingsServiceTest`, `CsvImportServiceTest`, `GlobalExceptionHandlerTest`

### Integration Tests (SpringBootTest + MockMvc)

- **Annotations:** `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("dev")`
- **Profile:** `dev` (H2 in-memory database)
- **Most controller tests use `@Transactional`** for automatic rollback after each test
- **Exception:** `TeamControllerTest` does NOT use `@Transactional` (manages its own test data)
- **Used for:** Controller endpoint testing, database-dependent service tests
- **Examples:** `SeasonControllerTest`, `MatchdayControllerTest`, `RaceLineupControllerTest`

### Security Integration Tests

- **File:** `src/test/java/org/ctc/admin/SecurityIntegrationTest.java`
- **Uses `@Nested` classes** with different profiles (prod vs dev)
- **Prod tests:** Verify auth is required (`status().isUnauthorized()`), `@WithMockUser` for authenticated access
- **Dev tests:** Verify no auth required (`status().isOk()` without credentials)

```java
@Nested
@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:sectest;..." })
@ActiveProfiles("prod")
class ProdProfileSecurityTest {
    @Test
    void givenNoCredentials_whenAccessAdmin_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/seasons")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser
    void givenValidCredentials_whenAccessAdmin_thenOk() throws Exception {
        mockMvc.perform(get("/admin/seasons")).andExpect(status().isOk());
    }
}
```

### E2E Tests (Playwright)

- **Base class:** `src/test/java/org/ctc/e2e/PlaywrightConfig.java`
- **Annotations:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("dev")`
- **Execution:** Only via `-Pe2e` Maven profile (Failsafe plugin)
- **Excluded from Surefire:** `**/e2e/**` pattern in `pom.xml`
- **Browser:** Chromium headless, managed via `@BeforeAll`/`@AfterAll`
- **Page lifecycle:** `@BeforeEach setupPage()` / `@AfterEach teardownPage()`
- **3 test files:** `AdminWorkflowE2ETest`, `ScoringE2ETest`, `ImportE2eTest`

## Test Naming Convention (Given-When-Then)

**Strict BDD pattern enforced:**

```java
// Full form: given context, when action, then expected result
@Test
void givenFastestLap_whenCalculatePoints_thenFastestLapPointsIncluded() {

// Short form (no precondition needed):
@Test
void whenCalculateTeamTotal_thenSumsAllResults() {

// Exception form: when/then combined in body
@Test
void givenPositionBeyondScale_whenCalculatePoints_thenZeroPoints() {
```

**Body structure with `// given`, `// when`, `// then` comments:**

```java
@Test
void givenOneMatch_whenCalculateStandings_thenWinnerGetThreePoints() {
    // given
    var matchday = new Matchday(season, "Spieltag 1", 1);
    var match = createMatchWithScore(matchday, tnr, p1r, 70, 46);
    season.addTeam(tnr);
    when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));

    // when
    var standings = standingsService.calculateStandings(season.getId());

    // then
    assertEquals(2, standings.size());
    var tnrStanding = findStanding(standings, tnr);
    assertEquals(1, tnrStanding.getWins());
    assertEquals(3, tnrStanding.getPoints());
}
```

**Combined when/then for MockMvc chains:**

```java
@Test
void whenGetSeasons_thenReturnsSeasonsView() throws Exception {
    // when
    mockMvc.perform(get("/admin/seasons"))
            // then
            .andExpect(status().isOk())
            .andExpect(view().name("admin/seasons"));
}
```

**Combined when/then for exception assertions:**

```java
@Test
void givenResponseStatusException_whenHandled_thenRethrown() {
    // given
    var ex = new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate label");

    // when / then
    assertThatThrownBy(() -> handler.handleResponseStatus(ex)).isSameAs(ex);
}
```

## Nested Test Classes

`@Nested` classes group related tests within a test file:

```java
@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Nested
    class CalculatePointsWithScoringTest { ... }

    @Nested
    class TeamTotalTest { ... }

    @Nested
    class AggregateMatchScoresTest { ... }
}
```

```java
class RaceScoringTest {

    @Nested
    class RacePointsArrayTest { ... }

    @Nested
    class QualiPointsArrayTest { ... }
}
```

## Test Data Patterns

### TestHelper (Integration Tests)

`src/test/java/org/ctc/TestHelper.java` -- a Spring `@Component` injected into integration tests:

```java
@Component
@RequiredArgsConstructor
public class TestHelper {
    private final RaceScoringRepository raceScoringRepository;
    private final SeasonRepository seasonRepository;
    // ... other repositories

    public Season createSeason(String name) { ... }
    public Team createTeam(String name, String shortName) { ... }
    public Match createMatch(Matchday matchday, Team homeTeam, Team awayTeam) { ... }
    public Driver createDriver(String psnId, String nickname) { ... }
    public SeasonDriver createSeasonDriver(Season season, Driver driver, Team team) { ... }

    // Full fixture setup:
    public SeasonFixture createFullSeasonFixture(String prefix) { ... }

    public record SeasonFixture(Season season, Matchday matchday, Match match, Race race,
                                Team homeTeam, Team awayTeam) {}
}
```

**Usage in controller tests:**

```java
@Autowired private TestHelper testHelper;

@Test
void givenExistingSeason_whenGetEditForm_thenReturnsSeasonForm() throws Exception {
    var season = testHelper.createSeason("Edit Test");
    mockMvc.perform(get("/admin/seasons/" + season.getId() + "/edit"))
            .andExpect(status().isOk());
}
```

### Private Helper Methods (Unit Tests)

Unit tests define private factory methods at the bottom of the test class:

```java
private Team createTeam(String name, String shortName, String primary, String secondary, String accent) {
    var team = new Team(name, shortName);
    team.setId(UUID.randomUUID());
    team.setPrimaryColor(primary);
    return team;
}

private Match createMatchWithScore(Matchday matchday, Team home, Team away, int homeScore, int awayScore) {
    var match = new Match(matchday, home, away);
    match.setId(UUID.randomUUID());
    match.setHomeScore(homeScore);
    match.setAwayScore(awayScore);
    return match;
}
```

### Static Factory Methods for Presets

```java
private static RaceScoring standardScoring() {
    return new RaceScoring("CTC Standard", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);
}
```

### @TempDir for File System Tests

```java
@TempDir
Path tempDir;

@BeforeEach
void setUp() {
    fileStorageService = new FileStorageService(tempDir.toString());
}
```

### Test Data Isolation (E2E)

- `TestDataService` creates entities with test-prefixed names (`T-ALF`, `Test_Alpha_1`, `Test-Season 2026`)
- Never use real team/driver names in automated tests
- E2E tests rely on `DevDataSeeder` data (scoring presets like "CTC Standard", "Standard 3-1-0")

## Mocking Patterns

**Standard Mockito setup:**

```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock private SomeRepository repository;
    @InjectMocks private SomeService service;
}
```

**Stubbing:**

```java
when(repository.findById(id)).thenReturn(Optional.of(entity));
when(repository.findByRaceIdAndDriverId(raceId, driverId))
        .thenReturn(Optional.of(new RaceLineup(race, driver, team)));
when(repository.findByRaceIdAndDriverId(any(), any()))
        .thenReturn(Optional.empty());
```

**Verification:**

```java
verify(seasonRepository).save(season);
verify(scoringService).calculatePoints(any(), eq(raceScoring));
```

**ArgumentCaptor for complex assertions:**

```java
ArgumentCaptor<RaceLineup> captor = ArgumentCaptor.forClass(RaceLineup.class);
verify(raceLineupRepository).save(captor.capture());
assertThat(captor.getValue().getTeam()).isEqualTo(expectedTeam);
```

**E2E Test Mocking** -- use `@TestConfiguration` + `@Primary` beans:

```java
@Import(ImportE2eTest.TestGoogleSheetsConfig.class)
class ImportE2eTest extends PlaywrightConfig {
    @TestConfiguration
    static class TestGoogleSheetsConfig {
        @Bean @Primary
        GoogleSheetsService googleSheetsService() {
            return new GoogleSheetsService("") {
                @Override public boolean isAvailable() { return true; }
            };
        }
    }
}
```

**Testing abstract classes** -- create testable concrete subclass:

```java
static class TestableMatchdayGraphicService extends AbstractMatchdayGraphicService {
    TestableMatchdayGraphicService(...) { super(null, standingsService, ...); }
    @Override protected String getTemplateFileName() { return "test-template.html"; }
    @Override protected String getDefaultTemplatePath() { return "templates/admin/test-render.html"; }
}
```

## Controller Test Patterns (MockMvc)

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private TestHelper testHelper;

    @Test
    void givenValidScoringRefs_whenSaveSeason_thenRedirects() throws Exception {
        // given
        var rs = testHelper.createSeason("Dummy").getRaceScoring();

        // when
        mockMvc.perform(post("/admin/seasons/save")
                        .param("name", "MockMvc Test Season")
                        .param("raceScoring", rs.getId().toString()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"));
    }
}
```

**MockMvc assertions used:**

- `status().isOk()`, `status().is3xxRedirection()`, `status().isUnauthorized()`
- `view().name("admin/entity")`, `redirectedUrl("/admin/entities")`
- `model().attributeExists("entity")`, `model().attribute("entity", hasProperty(...))`
- `flash().attributeExists("successMessage")`

## E2E Test Patterns (Playwright)

**Base class** (`src/test/java/org/ctc/e2e/PlaywrightConfig.java`):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public abstract class PlaywrightConfig {
    static Playwright playwright;
    static Browser browser;
    @LocalServerPort int port;
    BrowserContext context;
    Page page;

    @BeforeAll static void setupBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }
    @AfterAll static void teardownBrowser() { ... }
    protected void setupPage() { context = browser.newContext(); page = context.newPage(); }
    protected void teardownPage() { if (context != null) context.close(); }
    protected String url(String path) { return "http://localhost:" + port + path; }
}
```

**Test pattern:**

```java
class AdminWorkflowE2ETest extends PlaywrightConfig {
    @BeforeEach void setUp() { setupPage(); }
    @AfterEach void tearDown() { teardownPage(); }

    @Test
    void givenSeasonForm_whenSaveWithValidData_thenSeasonAppearsInList() {
        // given
        page.navigate(url("/admin/seasons/new"));

        // when
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Name")).fill("E2E Season");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();

        // then
        assertThat(page).hasTitle("CTC Admin - Seasons");
        assertThat(page.locator(".alert-success")).containsText("Season saved");
    }
}
```

**Locator strategy:** Prefer accessible role selectors (`getByRole`) over CSS selectors. Fall back to `page.locator("css=...")` when roles are insufficient.

## Coverage Configuration

**JaCoCo plugin** in `pom.xml`:

- **Minimum:** 82% line coverage (`COVEREDRATIO 0.82`)
- **Enforcement:** Build fails if coverage drops below minimum
- **Report:** `target/site/jacoco/index.html` after `./mvnw verify`
- **CI:** Automatic PR comment via `madrapps/jacoco-report` GitHub Action

**Excluded classes** (Playwright-dependent or bootstrap code, not unit-testable):

- `org/ctc/CtcManagerApplication.class`
- `org/ctc/admin/TestDataService.class`
- `org/ctc/admin/DemoDataSeeder.class`
- `org/ctc/admin/service/TeamCardService.class`
- `org/ctc/admin/service/LineupGraphicService.class`
- `org/ctc/admin/service/ResultsGraphicService.class`
- `org/ctc/admin/service/SettingsGraphicService.class`
- `org/ctc/admin/service/OverlayGraphicService.class`
- `org/ctc/admin/service/MatchResultsGraphicService.class`
- `org/ctc/admin/service/PowerRankingsGraphicService.class`
- `org/ctc/admin/service/AbstractGraphicService.class`
- `org/ctc/admin/service/PlayoffRoundOverviewGraphicService.class`
- `org/ctc/admin/service/PlayoffRoundScheduleGraphicService.class`
- `org/ctc/admin/service/PlayoffRoundResultsGraphicService.class`

## Test Execution Configuration

**Surefire (unit + integration):**

- Excludes `**/e2e/**`
- Custom argLine for Mockito agent: `-javaagent:...mockito-core...jar`
- Runs during `test` phase

**Failsafe (E2E only):**

- Activated via `-Pe2e` Maven profile
- Includes only `**/e2e/**/*Test.java`
- Same Mockito agent argLine
- Runs during `integration-test` phase

**Test logging:** Suppressed to WARN level via `src/test/resources/logback-test.xml`

## Writing New Tests

**Unit test for a new service:**

1. Create `src/test/java/org/ctc/{package}/{Service}Test.java`
2. Annotate with `@ExtendWith(MockitoExtension.class)`
3. `@Mock` all dependencies, `@InjectMocks` the service
4. Use `@Nested` classes to group related test scenarios
5. Follow `givenContext_whenAction_thenExpectedResult` naming
6. Use `// given` / `// when` / `// then` comments in test body

**Integration test for a new controller:**

1. Create `src/test/java/org/ctc/admin/controller/{Controller}Test.java`
2. Annotate with `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("dev")`, `@Transactional`
3. Inject `MockMvc` and `TestHelper`
4. Use `TestHelper` to create test data, then `mockMvc.perform(...)` to test endpoints

**E2E test for a new workflow:**

1. Create `src/test/java/org/ctc/e2e/{Feature}E2ETest.java`
2. Extend `PlaywrightConfig`
3. Add `@BeforeEach setupPage()` / `@AfterEach teardownPage()`
4. Use `page.navigate(url("/admin/..."))` and Playwright assertions
5. Test data comes from `DevDataSeeder` (scoring presets) and `TestDataService` (entities with test prefixes)

**Test for a new domain model:**

1. Create `src/test/java/org/ctc/domain/model/{Entity}Test.java`
2. No annotations needed (plain JUnit 5)
3. Test entity logic methods, array parsing, convenience methods
4. Use `@Nested` to group by method under test

---

*Testing analysis: 2026-04-04*
