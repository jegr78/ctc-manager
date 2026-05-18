# Testing Patterns

**Analysis Date:** 2026-05-18

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) via Spring Boot 4.0.6 default test starter
- Config: `pom.xml` lines 266–309 (Surefire plugin)

**Assertion Library:**
- AssertJ for fluent assertions (e.g., `assertThat(...).isEqualTo(...)`)
- Spring Test assertions for web tests (e.g., `MockMvc.andExpect(status().isOk())`)
- Playwright assertions for E2E tests (e.g., `assertThat(page.getByRole(...)).isVisible()`)

**Mock Framework:**
- Mockito 4.x (Spring Boot managed version)
- `@Mock` for dependency injection in unit tests
- `@InjectMocks` for service-under-test instantiation
- `when(...).thenReturn(...)` for stubbing

**Run Commands:**
```bash
# Run all tests (unit + integration, excludes E2E)
./mvnw verify

# Run with E2E (full test suite)
./mvnw verify -Pe2e

# Run specific test class
./mvnw -Dtest=Gt7SyncServiceTest test

# Run specific test method
./mvnw -Dtest=Gt7SyncServiceTest#givenMixOfNewAndExistingCarsAndTracks_whenFetchAndPreview_thenReturnsCorrectCounts test

# Run only integration tests
./mvnw -Dit.test=V4MigrationSmokeIT verify

# Watch mode / repeated runs
./mvnw -f /path/to/pom.xml verify -Drepeat=0  # Not natively supported; use IDE watch

# Coverage report
./mvnw verify
open target/site/jacoco/index.html
```

## Test File Organization

**Location:**
- Unit tests: `src/test/java/{package}/` (same package structure as main)
- Integration tests (`*IT.java`): Same directory as unit tests
- E2E tests: `src/test/java/org/ctc/e2e/{test-name}E2ETest.java` or `*Test.java` (not `*IT.java`)
- Migration tests: `src/test/java/db/migration/{version}MigrationTest.java` or `*IT.java`

**Naming:**
- Unit test: `{ClassName}Test.java` (no `@Tag`, no Spring context)
- Integration test: `{ClassName}IT.java` with `@Tag("integration")`
- E2E test: `{ClassName}E2ETest.java` or `*Test.java` in `org.ctc.e2e.*` with `@Tag("e2e")`
- Example files:
  - `src/test/java/org/ctc/gt7sync/Gt7SyncServiceTest.java` (unit test, no tag)
  - `src/test/java/db/migration/V4MigrationSmokeIT.java` (integration test with `@Tag("integration")`)
  - `src/test/java/org/ctc/e2e/ScoringE2ETest.java` (E2E test with `@Tag("e2e")`)

**Directory Pattern:**
```
src/test/java/
├── org/ctc/
│   ├── domain/
│   │   ├── service/
│   │   │   ├── {Service}Test.java          # Unit tests
│   │   │   └── {Service}IT.java            # Integration tests (@Tag("integration"))
│   ├── admin/
│   │   ├── controller/
│   │   │   ├── {Controller}Test.java
│   │   │   └── {Controller}IT.java
│   │   └── service/
│   │       └── {Service}Test.java
│   ├── e2e/
│   │   ├── {Feature}E2ETest.java           # Playwright tests (@Tag("e2e"))
│   │   └── PlaywrightConfig.java           # Base class for E2E
│   └── testsupport/
│       └── {Helper}Test.java
└── db/
    └── migration/
        ├── V4MigrationSmokeIT.java         # Integration with Flyway
        └── V3MigrationTest.java            # Unit test of SQL
```

## Test Categorization (`@Tag`)

**Tag-based Routing (not filename-based):**

The Maven Surefire and Failsafe plugins route tests by `@Tag` annotations, NOT by filename patterns. This is critical because `@Nested` inner classes compile to `ClassName$InnerName.class` and would match `*IT.java` glob patterns even if the parent is a unit test.

**Tag Categories:**

| Tag | File Pattern | Plugin | Spring Context | Notes |
|-----|--------------|--------|-----------------|-------|
| (none) | `*Test.java` | Surefire | ✗ No | Pure unit tests; mocked dependencies |
| `@Tag("integration")` | `*IT.java` | Failsafe | ✓ Yes (`@SpringBootTest`) | Spring-context ITs; databases; repositories |
| `@Tag("e2e")` | `*Test.java` in `org.ctc.e2e.*` OR `*E2ETest.java` | Failsafe + `-Pe2e` profile | ✓ Yes | Playwright browser tests; live server |
| `@Tag("flaky")` | Any | Excluded from CI | — | Known intermittent failures; skip in CI |

**Surefire Configuration** (lines 266–279):
```xml
<excludedGroups>integration,e2e,flaky</excludedGroups>
```
Runs unit tests WITHOUT these tags (pure Mockito tests).

**Failsafe Configuration for Integration** (lines 291–308):
```xml
<groups>integration</groups>
<excludedGroups>e2e,flaky</excludedGroups>
```
Runs ONLY `@Tag("integration")` tests, excludes E2E.

**Failsafe Configuration for E2E** (lines 440–460, with `-Pe2e` profile):
```xml
<includes>
    <include>**/e2e/**/*Test.java</include>
</includes>
<groups>e2e</groups>
```
Runs ONLY `@Tag("e2e")` tests from `org.ctc.e2e.*` package.

**`@Nested` Inheritance:**
- Inner classes automatically inherit the parent's `@Tag`
- Example from `SecurityIntegrationTest.java` (lines 17–69):
  - Parent class: unmarked (unit test context is implicit)
  - `@Nested class ProdProfileSecurityTest @Tag("integration")` at line 17 → all methods inherit `@Tag("integration")`
  - `@Nested class DevProfileSecurityTest @Tag("integration")` at line 54 → all methods inherit `@Tag("integration")`
  - This prevents accidentally discovering nested ITs via filename glob patterns

**Example: Proper Test Tagging**

Unit test (no Spring context):
```java
@ExtendWith(MockitoExtension.class)
class Gt7SyncServiceTest {
    @Mock Gt7ScraperService scraperService;
    @InjectMocks Gt7SyncService syncService;
    
    @Test
    void givenMixOfNewAndExistingCarsAndTracks_whenFetchAndPreview_thenReturnsCorrectCounts() { ... }
}
```

Integration test (Spring context + JPA):
```java
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class V4MigrationSmokeIT {
    @Autowired SeasonRepository seasonRepository;
    
    @Test
    void whenSpringBootContextLoads_thenSeasonsCanBeQueried() { ... }
}
```

E2E test (Playwright):
```java
@Tag("e2e")
class ScoringE2ETest extends PlaywrightConfig {
    @Test
    void givenRaceScoringForm_whenSaveWithValidData_thenScoringAppearsInList() { ... }
}
```

## Test Structure

**BDD Test Naming Pattern:**
All test methods follow Given-When-Then naming:
- `givenContext_whenAction_thenExpectedResult()`
- For simple tests without preconditions: `whenAction_thenResult()` is allowed
- Exception tests: `// when / then` combined with `assertThatThrownBy(...)`

**Example from `Gt7SyncServiceTest.java` (lines 47–78):**
```java
@Test
void givenMixOfNewAndExistingCarsAndTracks_whenFetchAndPreview_thenReturnsCorrectCounts() throws IOException {
    // given
    when(scraperService.scrapeCars()).thenReturn(SCRAPED_CARS);
    when(scraperService.scrapeTracks()).thenReturn(SCRAPED_TRACKS);
    when(carRepository.existsByGt7Id("car102")).thenReturn(true);
    
    // when
    Gt7SyncPreview preview = syncService.fetchAndPreview();
    
    // then
    assertThat(preview.getNewCarCount()).isEqualTo(1);
    assertThat(preview.getExistingCarCount()).isEqualTo(2);
}
```

**Suite Organization with `@Nested`:**
Multiple test scenarios grouped inside a single outer class:

```java
@Tag("integration")
class SecurityIntegrationTest {
    
    @Nested
    @SpringBootTest(properties = { ... })
    @ActiveProfiles("prod")
    class ProdProfileSecurityTest {
        @Test void givenNoCredentials_whenAccessAdmin_thenUnauthorized() { ... }
        @Test void givenValidCredentials_whenAccessAdmin_thenOk() { ... }
    }
    
    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    class DevProfileSecurityTest {
        @Test void givenDevProfile_whenAccessAdmin_thenOk() { ... }
    }
}
```

**Setup/Teardown:**
- `@BeforeEach` for per-test setup (e.g., `setupPage()` in E2E, entity creation)
- `@AfterEach` for cleanup (e.g., `teardownPage()` in E2E)
- `@BeforeAll` / `@AfterAll` for class-level one-time setup (e.g., static resource initialization)
- Spring Boot integration tests: `@Transactional` rolls back all DB changes automatically

## Mocking

**Framework:**
- Mockito 4.x via `@ExtendWith(MockitoExtension.class)` (not Spring Boot test context)

**Dependency Injection:**
```java
@ExtendWith(MockitoExtension.class)
class Gt7SyncServiceTest {
    @Mock Gt7ScraperService scraperService;       // Mocked dependency
    @Mock CarRepository carRepository;            // Mocked repository
    @InjectMocks Gt7SyncService syncService;      // Service under test
    
    @Test
    void testName() {
        when(scraperService.scrapeCars()).thenReturn(SCRAPED_CARS);
        // ... assertions
    }
}
```

**Patterns (from actual code):**

Stub simple return values:
```java
when(carRepository.existsByGt7Id("car102")).thenReturn(true);
```

Throw exceptions:
```java
when(someService.someMethod()).thenThrow(new IOException("Test error"));
```

Capture arguments:
```java
ArgumentCaptor<Car> carCaptor = ArgumentCaptor.forClass(Car.class);
verify(carRepository).save(carCaptor.capture());
Car savedCar = carCaptor.getValue();
```

**What to Mock:**
- External services (API clients, file storage)
- Repositories (in unit tests; use real DB in integration tests)
- Dependencies injected into the service-under-test

**What NOT to Mock:**
- The service being tested itself (use `@InjectMocks`)
- Value objects (e.g., `UUID`, `String` — just construct them)
- Utility classes (e.g., `Collections`, `LocalDateTime`)
- In integration tests, do NOT mock repositories — use `@Autowired` and let Spring manage the real H2 database

## Test Data Management

**TestDataService** (for dev/demo profiles):
- Location: `src/main/java/org/ctc/admin/TestDataService.java`
- Seeds deterministic fixtures into H2 in-memory database
- Excluded from code coverage analysis (`pom.xml` line 321)
- Fixtures include: Season 2023, Season 2024 (Regular + Empty), Season 2026
- Auto-seeded on startup when `spring.profiles.active` includes `dev` or `local`

**Test Entity Naming Prefix** (CRITICAL for E2E Test Isolation):
- All E2E test entities MUST use a test prefix to avoid collision with manual test data
- Prefixes: `T-ALF`, `Test_Alpha_1`, `Test-Season 2026`, `Phase57-Smoke-Season`
- Example from `V4MigrationSmokeIT.java` (lines 45–47):
```java
private static final UUID SMOKE_RACE_SCORING_ID = UUID.fromString("00000000-0000-0057-0000-000000000001");
private static final UUID SMOKE_SEASON_ID = UUID.fromString("00000000-0000-0057-0000-000000000010");
```
- Why: DevDataSeeder populates real seasons; test data seeded with distinct names prevents accidental merges/overwrites

**Spring Boot Test Profiles:**
- `@ActiveProfiles("dev")` → H2 in-memory, DevDataSeeder runs, full test data available
- `@ActiveProfiles("local")` → MariaDB on localhost, no seeding (for manual testing)
- `@ActiveProfiles("prod")` → Simulates production (auth enabled), requires environment variables

## Fixtures and Factories

**Test Data Patterns:**

Static constants for unit tests (from `Gt7SyncServiceTest.java`, lines 26–34):
```java
private static final List<ScrapedCar> SCRAPED_CARS = List.of(
    new ScrapedCar("car102", "Nissan", "Skyline GTS-R (R31) '87", "https://example.com/car102.png"),
    new ScrapedCar("car205", "Toyota", "Sports 800 '65", "https://example.com/car205.png")
);
```

Entity creation in integration tests (from `V4MigrationSmokeIT.java`, lines 76–80):
```java
@BeforeEach
void seedSmokeTestData() {
    jdbcTemplate.update(
        "INSERT INTO seasons (id, name, season_year, season_number, active, created_at, updated_at) "
        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        SMOKE_SEASON_ID, "Phase57-Smoke-Season", 2099, 99, false);
}
```

**Location:**
- Inline fixture constants: Within test class (common for small datasets)
- Shared fixtures: `src/test/java/org/ctc/testsupport/` package (for reusable test data)
- TestDataService seeding: `src/main/java/org/ctc/admin/TestDataService.java` (dev profile)

## Coverage Requirements

**Minimum Coverage:**
- **Line coverage: 82%** (configured in `pom.xml` lines 351–363)
- Gate: `./mvnw verify` fails if coverage < 82%

**Excluded Classes** (from `pom.xml` lines 319–334):
- `CtcManagerApplication.class` — Boot entry point
- `TestDataService.class` — dev-profile seeder
- `DemoDataSeeder.class` — demo-profile seeder
- All `*GraphicService` classes (Playwright service classes for team-card/graphics generation)
  - `TeamCardService`
  - `LineupGraphicService`
  - `ResultsGraphicService`
  - `SettingsGraphicService`
  - `OverlayGraphicService`
  - `MatchResultsGraphicService`
  - `PowerRankingsGraphicService`
  - `AbstractGraphicService`
  - `PlayoffRoundOverviewGraphicService`
  - `PlayoffRoundScheduleGraphicService`
  - `PlayoffRoundResultsGraphicService`

**Why Graphics Are Excluded:**
- Playwright's browser automation cannot be reliably unit-tested
- Visual correctness is validated via `playwright-cli` manual inspection (separate from JaCoCo)
- E2E tests verify the endpoints work; coverage measurement is not applicable

**Coverage Report:**
```bash
./mvnw verify
open target/site/jacoco/index.html
```

Report shows line/branch/instruction coverage per package and class.

## Playwright E2E Tests

**Framework:**
- Playwright Java 1.59.0 (managed in `pom.xml` line 18)
- Scope: `compile` (used at runtime for graphics) + test scope for E2E tests
- Config: `src/test/java/org/ctc/e2e/PlaywrightConfig.java` (base class for all E2E tests)

**Test Location & Naming:**
- Package: `org.ctc.e2e.*`
- Class naming: `{Feature}E2ETest.java` or `*Test.java` (NOT `*IT.java`)
- Example: `src/test/java/org/ctc/e2e/ScoringE2ETest.java` (line 11: extends `PlaywrightConfig`)

**Run E2E Tests:**
```bash
# Full test suite including E2E
./mvnw verify -Pe2e

# E2E only
./mvnw -Dit.test='**/e2e/**/*Test' verify -Pe2e
```

**Base Class (`PlaywrightConfig`):**
Provides:
- `page` field (shared Playwright Page instance)
- `setupPage()` → initializes page before each test
- `teardownPage()` → closes page after each test
- `url(String path)` → constructs full URL (e.g., `http://localhost:9090/admin/seasons`)

Example from `ScoringE2ETest.java` (lines 13–21):
```java
@Tag("e2e")
class ScoringE2ETest extends PlaywrightConfig {
    @BeforeEach void setUp() { setupPage(); }
    @AfterEach void tearDown() { teardownPage(); }
    
    @Test
    void whenNavigateToRaceScoringPage_thenScoringNavigationLinksAreVisible() {
        page.navigate(url("/admin/race-scorings"));
        assertThat(page.getByRole(...)).isVisible();
    }
}
```

**Assertions (Playwright Assertions):**
```java
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

assertThat(page).hasTitle("CTC Admin - Race-Scorings");
assertThat(page.locator("h1")).containsText("Race-Scorings");
assertThat(page.locator("table")).containsText("E2E Test Scoring");
assertThat(page.getByRole(AriaRole.LINK, ...)).isVisible();
```

**Visual Verification with `playwright-cli`:**
After making UI changes (templates, CSS), verify visually using the Playwright CLI:
```bash
# Start dev server in one terminal
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# In another terminal, open Playwright inspector on a specific page
playwright-cli open http://localhost:9090/admin/match-scorings

# Playwright will open an interactive inspector in a browser
# Check both Desktop and Mobile viewports
```

This ensures visual rendering is correct before committing.

**Screenshot Storage:**
- Screenshots from E2E tests go to `.screenshots/` directory (never repo root)
- Example path: `.screenshots/test-name-2026-05-18-123456.png`
- Gitignore `.screenshots/` (transient test artifacts)

## Integration Testing

**Spring Boot Test Context:**
- `@SpringBootTest` loads the full application context (all beans)
- `classes = CtcManagerApplication.class` required for tests outside `org.ctc` package (e.g., `db.migration` package)
- `@ActiveProfiles("dev")` uses H2 in-memory database with DevDataSeeder
- `@Transactional` rolls back changes after each test (database state is clean)

Example from `V4MigrationSmokeIT.java` (lines 38–42):
```java
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class V4MigrationSmokeIT { ... }
```

**Database Access:**
```java
@Autowired SeasonRepository seasonRepository;  // Real repository
@Autowired JdbcTemplate jdbcTemplate;          // Direct SQL access

// Use repository for ORM testing
seasonRepository.save(new Season(...));

// Use JdbcTemplate for Flyway/schema testing
jdbcTemplate.update("INSERT INTO seasons (...) VALUES (...)", params);
```

**Testcontainers for MariaDB** (optional smoke tests):
- Dependency: `testcontainers:2.0.5` (supports Docker API 1.44 for Docker Engine 29+)
- Used in `BackupImportMariaDbSmokeIT` to verify backup round-trip against live MariaDB
- `@DynamicPropertySource` overrides `spring.datasource.url` at test startup

## Common Test Patterns

**Async/Reactive Testing:**
- Not used in this project (synchronous Spring Boot MVC)
- For any future reactive code: use `StepVerifier` from Project Reactor test support

**Exception Testing:**
```java
// AssertJ style
assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
    .isInstanceOf(EntityNotFoundException.class)
    .hasMessageContaining("not found");

// Or use assertThrows (JUnit 5 style)
assertThrows(BusinessRuleException.class, 
    () -> service.save(null, "Invalid", ...));
```

**Verification of Method Calls:**
```java
verify(mockRepository).save(any(Entity.class));  // Called at least once
verify(mockRepository, times(2)).findById(any());  // Called exactly twice
verify(mockRepository, never()).delete(any());  // Never called
```

## Test Execution in CI

**GitHub Actions:**
- On push/PR to `master`: Run full test suite via `./mvnw verify`
- On push/PR with `-Pe2e` profile: Run Playwright E2E tests (may be conditional)
- JaCoCo coverage report auto-commented on PR via `madrapps/jacoco-report` action
- SpotBugs + find-sec-bugs gate: Medium+ findings block the build (always-on)
- CodeQL SAST scanning: Run on push, PR, and weekly cron

**Local Execution:**
```bash
# Fast: unit tests only (no Spring context, no E2E)
./mvnw test

# Thorough: unit + integration (with Spring context)
./mvnw verify

# Full: unit + integration + E2E (includes Playwright)
./mvnw verify -Pe2e

# Single test class
./mvnw -Dtest=Gt7SyncServiceTest test

# Single test method
./mvnw -Dtest=Gt7SyncServiceTest#givenMixOfNewAndExistingCarsAndTracks_whenFetchAndPreview_thenReturnsCorrectCounts test
```

## Anti-Patterns to Avoid

**Don't:**
- Mix unit test and integration test in same file (use separate `*Test.java` and `*IT.java` files)
- Create tests in Spring context when pure unit test suffices (slows build, masks slow code)
- Mock everything indiscriminately (integration tests benefit from real DB + repositories)
- Use filename-based test routing instead of `@Tag` (brittle with `@Nested` classes)
- Commit hardcoded test data with production values (use prefixed test names like `T-`, `Test_`)
- Ignore test failures as "flaky" without fixing (tag as `@Tag("flaky")` only if truly intermittent and diagnosed)

---

*Testing analysis: 2026-05-18*
