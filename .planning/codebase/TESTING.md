# Testing Patterns

**Analysis Date:** 2026-04-07

## Test Framework

**Runner:**
- JUnit 5 (Jupiter)
- Maven Surefire (unit/integration tests)
- Maven Failsafe (E2E tests, activated via `-Pe2e` profile)
- Config: `pom.xml` (lines 184-194 for Surefire, lines 256-278 for Failsafe)

**Assertion Library:**
- AssertJ (primary, for fluent assertions)
- JUnit Jupiter assertions (legacy, gradually phased out)
- Mockito assertions (for mock verification)
- Playwright assertions (for E2E)

**Run Commands:**
```bash
./mvnw verify                    # Run all unit + integration tests
./mvnw verify -Pe2e              # Run all tests including Playwright E2E
./mvnw test                      # Unit tests only (Surefire)
./mvnw verify -Pe2e -Dtest=AdminWorkflowE2ETest   # Single E2E test
```

**Coverage:**
```bash
./mvnw verify                    # Generates target/site/jacoco/index.html
open target/site/jacoco/index.html
```

## Test File Organization

**Location:**
- Unit/integration: `src/test/java/org/ctc/{domain,admin,dataimport,gt7sync}/...`
- E2E tests: `src/test/java/org/ctc/e2e/`
- Test data: `src/test/resources/` (logback-test.xml, GT7 fixtures)
- Test helper: `src/test/java/org/ctc/TestHelper.java`

**Naming:**
- Class: `{Classname}Test.java` (unit) or `{Feature}E2ETest.java` (E2E)
- Method: BDD pattern `givenContext_whenAction_thenResult()` or `whenAction_thenResult()` for simple cases

**File Count:**
- 78 test classes across ~14 directories
- ~773 total tests (per CLAUDE.md)
- Excludes: Graphic services (Playwright compilation dependency, not testable), TestDataService, DemoDataSeeder

**Test Distribution:**
- Unit tests: `src/test/java/org/ctc/domain/service/` (24 service tests)
- Integration: `src/test/java/org/ctc/admin/controller/` (20+ controller tests)
- Domain models: `src/test/java/org/ctc/domain/model/` (6 model tests)
- Exceptions: `src/test/java/org/ctc/domain/exception/` (1+ exception tests)
- Admin services: `src/test/java/org/ctc/admin/service/` (10+ graphic/template tests)
- Import: `src/test/java/org/ctc/dataimport/` (3+ import tests)
- GT7 sync: `src/test/java/org/ctc/gt7sync/` (3+ sync tests)
- E2E: `src/test/java/org/ctc/e2e/` (4 E2E tests)

## Test Structure

**Unit Test Pattern (Mockito with @ExtendWith):**
```java
@ExtendWith(MockitoExtension.class)
class RaceServiceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private MatchRepository matchRepository;

    @InjectMocks
    private RaceService service;

    @Test
    void givenMatchdayId_whenGetRaceListData_thenReturnsFilteredRaces() {
        // given
        var matchdayId = UUID.randomUUID();
        var race = createRaceWithScore(10, 5);
        
        when(raceRepository.findByMatchdayId(matchdayId)).thenReturn(List.of(race));

        // when
        var result = service.getRaceListData(matchdayId, null);

        // then
        assertThat(result.races()).hasSize(1);
        verify(raceRepository).findByMatchdayId(matchdayId);
    }
}
```
Source: `src/test/java/org/ctc/domain/service/RaceServiceTest.java`

**Integration Test Pattern (@SpringBootTest + @Transactional):**
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SeasonControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TestHelper testHelper;

    @Test
    void givenValidScoringRefs_whenSaveSeason_thenRedirects() throws Exception {
        // given
        var rs = testHelper.createSeason("Dummy").getRaceScoring();

        // when
        mockMvc.perform(post("/admin/seasons/save")
                .param("name", "Test Season")
                .param("raceScoring", rs.getId().toString()))
                
        // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seasons"));
    }
}
```
Source: `src/test/java/org/ctc/admin/controller/SeasonControllerTest.java`

**Test Sections:**
- `// given` — Precondition setup (mocks, database state, fixtures)
- `// when` — Action invocation
- `// then` — Assertion/verification
- For simple tests or exceptions: `// when / then` combined is acceptable

## Mocking

**Framework:**
- Mockito Core (3.x, included in Spring Boot Test)
- Mockito JUnit Jupiter integration (`mockito-junit-jupiter`)

**Activation:**
- `@ExtendWith(MockitoExtension.class)` for unit tests
- Automatically available in `@SpringBootTest` integration tests

**Common Patterns:**

```java
// Basic mock setup
@Mock
private RaceScoringRepository raceScoringRepository;

// Inject mocks into service
@InjectMocks
private RaceScoringService raceScoringService;

// Set up behavior
when(raceScoringRepository.findById(id))
    .thenReturn(Optional.of(scoring));

// Answer with function
when(raceScoringRepository.saveAndFlush(any(RaceScoring.class)))
    .thenAnswer(inv -> inv.getArgument(0));

// Verify interactions
verify(raceScoringRepository).findById(id);
verify(raceRepository, never()).findAll();

// Verify call order
InOrder inOrder = inOrder(repo1, repo2);
inOrder.verify(repo1).findAll();
inOrder.verify(repo2).save(any());
```
Source: `src/test/java/org/ctc/domain/service/RaceScoringServiceTest.java`

**What to Mock:**
- Repositories (database calls)
- External services (Google Calendar, Google Sheets, Jsoup GT7)
- File storage operations
- Slow or side-effect operations (TeamCardService, graphic generators)

**What NOT to Mock:**
- Entity classes (use real constructors)
- Spring autoconfigured components in @SpringBootTest
- Exception classes (test real throw/catch)
- JPA EntityManager (use @Transactional to manage)

## Fixtures and Factories

**TestHelper Component:**
Location: `src/test/java/org/ctc/TestHelper.java`

Provides convenient factory methods for test data creation:
```java
public Season createSeason(String name)
public Season createSeason(String name, int year, int number)

public Matchday createMatchday(Season season, String label, int sortIndex)
public Team createTeam(String name, String shortName)
public Match createMatch(Matchday matchday, Team homeTeam, Team awayTeam)
public Race createRace(Matchday matchday, Match match)
public Driver createDriver(String psnId, String nickname)
public SeasonDriver createSeasonDriver(Season season, Driver driver, Team team)

// Composite fixture: creates full season, teams, matchday, match, race
public SeasonFixture createFullSeasonFixture(String prefix)
```

**Fixture Data Isolation:**
- Use unique names with UUID suffixes: `"RS " + suffix` (line 31)
- For E2E tests: Use test-prefixed data (`T-ALF`, `Test_Alpha_1`, `Test-Season 2026`)
- Never use real team/driver/season names in automated tests
- **Why:** Prevent collisions with manual testing on imported real data

**Database State:**
- H2 in-memory (`dev` profile) auto-initializes via Flyway migrations
- Transactional tests auto-rollback after each test
- No manual cleanup required for unit/integration tests

## Integration Testing

**Configuration:**
- `@SpringBootTest` — Full application context, real beans
- `@AutoConfigureMockMvc` — Enables `MockMvc` for HTTP testing
- `@ActiveProfiles("dev")` — Use H2 in-memory database
- `@Transactional` — Rollback after test, preserves isolation

**Security Testing with Nested Classes:**
```java
@Nested
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:sectest;DB_CLOSE_DELAY=-1",
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileSecurityTest {
    
    @Autowired private MockMvc mockMvc;

    @Test
    void givenNoCredentials_whenAccessAdmin_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/seasons"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void givenValidCredentials_whenAccessAdmin_thenOk() throws Exception {
        mockMvc.perform(get("/admin/seasons"))
            .andExpect(status().isOk());
    }
}
```
Source: `src/test/java/org/ctc/admin/SecurityIntegrationTest.java`

**MockMvc Patterns:**
```java
// GET with assertions
mockMvc.perform(get("/admin/seasons"))
    .andExpect(status().isOk())
    .andExpect(view().name("admin/seasons"))
    .andExpect(model().attributeExists("seasons"));

// POST with form parameters and redirect verification
mockMvc.perform(post("/admin/seasons/save")
        .param("name", "Season Name")
        .param("raceScoring", id.toString()))
    .andExpect(status().is3xxRedirection())
    .andExpect(redirectedUrl("/admin/seasons"));

// Error response expectations
mockMvc.perform(post("/admin/seasons/save")
        .param("name", ""))
    .andExpect(status().isOk())
    .andExpect(view().name("admin/season-form"))
    .andExpect(model().hasErrors());
```

## Error Testing

**Exception Assertion Pattern:**
```java
@Test
void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException() {
    // given
    var id = UUID.randomUUID();
    when(raceScoringRepository.findById(id)).thenReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> raceScoringService.findById(id))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("RaceScoring")
            .hasMessageContaining(id.toString());
}
```
Source: `src/test/java/org/ctc/domain/service/RaceScoringServiceTest.java`

**Custom Exception Testing:**
```java
@Test
void givenCustomException_whenHandled_thenReturnsProperHttpStatus() {
    // given
    var ex = new ValidationException("Name is required");

    // when
    var mav = handler.handleValidation(ex);

    // then
    assertThat(mav.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(mav.getModel().get("message")).isEqualTo("Name is required");
}
```
Source: `src/test/java/org/ctc/admin/controller/GlobalExceptionHandlerTest.java`

**Illegal State Testing:**
```java
assertThatThrownBy(() -> service.generateLineup(race))
    .isInstanceOf(IllegalStateException.class)
    .hasMessageContaining("No teams");
```

## Parameterized Testing

**Framework:**
- JUnit 5 `@ParameterizedTest` + `@ValueSource`

**Pattern:**
```java
@ParameterizedTest
@ValueSource(strings = {"team-cards", "lineup", "settings", "race-results"})
void givenTemplateType_whenRenderPreview_thenContainsExpectedData(String template) {
    // when
    String html = service.renderPreview(template, sampleHtml);

    // then
    assertThat(html).contains("Expected data for " + template);
}
```
Source: `src/test/java/org/ctc/admin/service/TemplatePreviewServiceTest.java`

## Nested Test Classes

**Usage:**
- `@Nested` for organizing related test groups within a single test class
- Commonly used for profile-specific or feature-specific grouping

**Example:**
```java
class SecurityIntegrationTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("prod")
    class ProdProfileSecurityTest {
        // Tests requiring prod authentication
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    class DevProfileSecurityTest {
        // Tests with dev profile (no auth)
    }
}
```
Source: `src/test/java/org/ctc/admin/SecurityIntegrationTest.java`

## E2E Testing (Playwright)

**Framework:**
- Playwright Browser Automation 1.58.0
- JUnit 5 integration
- Headless Chromium

**Base Class:**
Location: `src/test/java/org/ctc/e2e/PlaywrightConfig.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public abstract class PlaywrightConfig {

    static Playwright playwright;
    static Browser browser;

    @LocalServerPort
    int port;

    BrowserContext context;
    Page page;

    @BeforeAll
    static void setupBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void teardownBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    protected void setupPage() {
        context = browser.newContext();
        page = context.newPage();
    }

    protected void teardownPage() {
        if (context != null) context.close();
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
```

**E2E Test Pattern:**
```java
class AdminWorkflowE2ETest extends PlaywrightConfig {

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    @Test
    void givenSeasonForm_whenSaveWithValidData_thenSeasonAppearsInList() {
        // given
        page.navigate(url("/admin/seasons/new"));

        // when
        page.getByRole(AriaRole.TEXTBOX, 
            new Page.GetByRoleOptions().setName("Name").setExact(true))
            .fill("E2E Season");
        page.fill("#year", "2026");
        page.selectOption("#raceScoring", 
            new SelectOption().setLabel("CTC Standard"));
        page.getByRole(AriaRole.BUTTON, 
            new Page.GetByRoleOptions().setName("Save"))
            .click();

        // then
        assertThat(page).hasTitle("CTC Admin - Seasons");
        assertThat(page.getByRole(AriaRole.CELL, 
            new Page.GetByRoleOptions().setName("E2E Season")))
            .isVisible();
        assertThat(page.locator(".alert-success"))
            .containsText("Season saved");
    }
}
```
Source: `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java`

**E2E Test Files:**
- `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` — Navigation, CRUD workflows
- `src/test/java/org/ctc/e2e/ScoringE2ETest.java` — Scoring calculations
- `src/test/java/org/ctc/e2e/ImportE2eTest.java` — CSV import workflows

**Locators & Interaction:**
```java
// Find by role (ARIA-compliant, accessible)
page.getByRole(AriaRole.BUTTON, 
    new Page.GetByRoleOptions().setName("Save"))
    .click();

// Find by text
page.locator("text=My Text").click();

// Find by selector
page.fill("#year", "2026");

// Assertions
assertThat(page).hasTitle("Expected Title");
assertThat(element).isVisible();
assertThat(element).containsText("text");
```

**DevDataSeeder (Test Data Provider):**
Runs at startup in `dev` profile to create predictable test fixtures (scoring presets, GT7 cars/tracks).
- Never edit or run this for specific test needs — use TestHelper fixtures instead
- E2E tests select from DevDataSeeder presets (e.g., "CTC Standard" race scoring)

**Playwright Visual Inspection:**
For UI changes, verify with: `playwright-cli open http://localhost:9090/admin/seasons`
(Requires dev server running: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`)

## Coverage

**JaCoCo Configuration:**
Location: `pom.xml` (lines 198-249)

**Minimum Threshold:**
- Line coverage: 82% (enforced by `<minimum>0.82</minimum>`)
- Checked at verify phase

**Excluded from Coverage:**
- `CtcManagerApplication` — Spring Boot entry point
- `TestDataService` — Test fixture provider
- `DemoDataSeeder` — Development data initializer
- Graphic services: `TeamCardService`, `LineupGraphicService`, `ResultsGraphicService`, `SettingsGraphicService`, `OverlayGraphicService`, `MatchResultsGraphicService`, `PowerRankingsGraphicService`, `AbstractGraphicService`, `PlayoffRoundOverviewGraphicService`, `PlayoffRoundScheduleGraphicService`, `PlayoffRoundResultsGraphicService`
- **Why:** Graphic services use Playwright for rendering (runtime compile dependency) — not practical to unit test

**Coverage Measurement:**
```bash
./mvnw verify
# Report: target/site/jacoco/index.html
open target/site/jacoco/index.html

# CI automatically posts coverage comment to PR via madrapps/jacoco-report
```

## Logging in Tests

**Configuration:**
- Log level: WARN (suppresses debug/info during test runs)
- Config: `src/test/resources/logback-test.xml`

**Pattern:**
```xml
<root level="WARN">
    <appender-ref ref="CONSOLE"/>
</root>
```

**Usage in Tests:**
- Log only state-changing operations (saves, deletes)
- Use SLF4J via `@Slf4j` (Lombok)
- Parameterized format: `log.info("Created season {}", season.getId())`

## Test Naming Convention (BDD Style)

**Required Pattern:**
`givenContext_whenAction_thenExpectedResult()`

**Breakdown:**
- `given` — Preconditions (setup state, mocks)
- `when` — Action invoked (method call)
- `then` — Expected outcome (assertions)

**Examples:**
```java
void givenValidScoringRefs_whenSaveSeason_thenRedirects()
void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException()
void givenMatchdayId_whenGetRaceListData_thenReturnsFilteredRaces()
```

**Simplification:**
For tests without preconditions, `whenAction_thenResult()` is acceptable:
```java
void whenGetSeasons_thenReturnsSeasonsView()
```

**Exception Tests:**
Combine `when/then` for exception assertions:
```java
void givenBlankName_whenSaveSeason_thenReturnsFormWithErrors()
```

## Transactional Boundaries

**@Transactional in Tests:**
- Automatically rolls back after each test
- Maintains database isolation between tests
- Prevents test order dependencies
- Required for integration tests with `@SpringBootTest`

**Use Case:**
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional  // <-- Auto-rollback after test
class SeasonControllerTest {
    
    @Test
    void givenValidScoringRefs_whenSaveSeason_thenRedirects() throws Exception {
        // Changes are committed during test but rolled back after
    }
}
```

**OSIV (Open Session in View) Implication:**
- Enabled in Spring Boot config (`spring.jpa.open-in-view=true`)
- Lazy-loaded associations can be accessed in tests without explicit fetch
- No need for `@EntityGraph` workarounds in tests (unlike controllers)

## Common Patterns Summary

| Scenario | Approach | Example |
|----------|----------|---------|
| Unit test (no Spring) | `@ExtendWith(MockitoExtension.class)` | `RaceServiceTest` |
| Integration test | `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional` | `SeasonControllerTest` |
| Profile-specific | `@Nested` + `@ActiveProfiles("prod"/"dev")` | `SecurityIntegrationTest` |
| Exception handling | `assertThatThrownBy(...).isInstanceOf(...)` | `RaceScoringServiceTest` |
| Data fixtures | `TestHelper.createX()` methods | All integration tests |
| HTTP testing | `mockMvc.perform(get/post(...))` | `SeasonControllerTest` |
| Security testing | `@WithMockUser` + unauthorized assertions | `SecurityIntegrationTest` |
| Parameterized | `@ParameterizedTest`, `@ValueSource` | `TemplatePreviewServiceTest` |
| UI/E2E | Extends `PlaywrightConfig`, `page.navigate()`, `PlaywrightAssertions` | `AdminWorkflowE2ETest` |

---

*Testing analysis: 2026-04-07*
