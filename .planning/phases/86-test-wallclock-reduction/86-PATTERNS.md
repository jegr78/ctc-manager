# Phase 86: Test Wallclock Reduction - Pattern Map

**Mapped:** 2026-05-17
**Files analyzed:** 15 (6 new + 9 modified)
**Analogs found:** 14 / 15

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` | test-utility | event-driven | no direct analog — closest: `BlockingRestoreFailureInjector.Config` (test-scope infrastructure class) | partial |
| `src/test/resources/META-INF/spring.factories` | config | — | no existing spring.factories in project (only in jars on classpath) | no analog |
| `src/test/java/org/ctc/testsupport/JpaAuditingConfig.java` | test-config | — | `FailAtTableInjector.Config` / `BlockingRestoreFailureInjector.Config` (both `@TestConfiguration` inner classes) | role-match |
| `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` | test (unit) | — | any plain unit test with `@ExtendWith(MockitoExtension.class)` — e.g. `ImportLockServiceTest` | role-match |
| `src/test/java/org/ctc/backup/it/ImportLockServiceResetHelper.java` | test-component | event-driven | `BlockingRestoreFailureInjector.Config` + `FailAtTableInjector.Config` (test-scope bean config in backup IT cluster) | role-match |
| `docs/test-performance.md` | documentation | — | `docs/security/sast-acceptance.md`, `docs/uat/UAT-02-legacy-season-smoke.md` | exact shape |
| `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` (+ 6 siblings) | test (integration) | file-I/O | itself (before-state) + `BackupStagingCleanupIT` for the `@TempDir`+`@DynamicPropertySource` pattern | exact |
| `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` (+ 2 siblings) | test (integration) | event-driven | itself (before-state); reset helper is new | exact |
| `src/test/java/org/ctc/admin/TestDataServiceIntegrationTest.java` | test (integration) | CRUD | itself (before-state) | exact |
| `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` | test (integration) | CRUD | itself (before-state) | exact |
| `src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java` | test (integration) | CRUD | itself (before-state) | exact |
| `src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java` | test (integration) | CRUD | itself (before-state) | exact |

---

## Pattern Assignments

### `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` (test-utility, event-driven)

**Analog:** No direct codebase analog. Pattern derived from RESEARCH.md Pattern 4 (VERIFIED against `spring-boot-test-4.0.6.jar` `spring.factories`). The closest structural analog in the codebase is `BlockingRestoreFailureInjector` as a test-scope infrastructure class with a static nested `Config`.

**Package placement:** `org.ctc.testsupport` (new package — parallel to `org.ctc.backup.it.support`).

**Implementation pattern** (from RESEARCH.md, VERIFIED):

```java
package org.ctc.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class ContextLoadCountListener
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final AtomicInteger count = new AtomicInteger(0);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Path out = Paths.get("target/test-perf/context-loads.txt");
                Files.createDirectories(out.getParent());
                Files.writeString(out, String.valueOf(count.get()));
            } catch (IOException e) {
                System.err.println("ContextLoadCountListener: could not write marker file: "
                        + e.getMessage());
            }
        }));
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        count.incrementAndGet();
    }
}
```

**Fork-safe variant (RESEARCH Pitfall 4):** With `forkCount=2`, two JVMs each overwrite the same file. Use PID-keyed output to prevent clobber:

```java
// In the shutdown hook body — replace the single Files.writeString call with:
String pid = String.valueOf(ProcessHandle.current().pid());
Path out = Paths.get("target/test-perf/context-loads-" + pid + ".txt");
Files.createDirectories(out.getParent());
Files.writeString(out, String.valueOf(count.get()));
```

Sum all `context-loads-*.txt` files after the build: `cat target/test-perf/context-loads-*.txt | paste -sd+ - | bc`.

**SpotBugs note:** Class lives in `src/test/java/` — excluded from production SpotBugs scan per `pom.xml` JaCoCo excludes pattern. No `@SuppressFBWarnings` needed.

---

### `src/test/resources/META-INF/spring.factories` (config)

**Analog:** No existing `spring.factories` file in the project tree (only present in jar dependencies). Registration key confirmed VERIFIED by inspecting `spring-boot-test-4.0.6.jar`.

**Content:**

```properties
org.springframework.context.ApplicationContextInitializer=\
  org.ctc.testsupport.ContextLoadCountListener
```

**Scope:** `src/test/resources/` — test classpath only. Production classpath is unaffected.

**Spring Boot 4 note (from RESEARCH.md):** The `spring.factories` migration to `AutoConfiguration.imports` applies to auto-configuration beans only. `ApplicationContextInitializer` registration still uses `spring.factories` in Spring Boot 4.0.6 (VERIFIED from jar).

---

### `src/test/java/org/ctc/testsupport/JpaAuditingConfig.java` (test-config)

**Analog:** `src/test/java/org/ctc/backup/service/FailAtTableInjector.java` — inner static `@TestConfiguration` `Config` class (lines 90-108). `BlockingRestoreFailureInjector.Config` (lines 96-115) follows the same pattern.

**`@TestConfiguration` pattern** (from `FailAtTableInjector.Config`, lines 90-108):

```java
@TestConfiguration
public static class Config {
    @Bean(name = "noopRestoreFailureInjector")
    @Primary
    public RestoreFailureInjector failAtTable() {
        return new FailAtTableInjector("race_results", 500);
    }
}
```

**`JpaAuditingConfig` adaptation** — standalone class (not nested), no `@Bean` needed:

```java
package org.ctc.testsupport;  // or org.ctc.domain.repository per planner discretion

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@TestConfiguration
@EnableJpaAuditing
class JpaAuditingConfig {
    // No beans — @EnableJpaAuditing activates AuditingEntityListener via annotation metadata
}
```

**Usage in `@DataJpaTest` classes:**

```java
@DataJpaTest
@Import(JpaAuditingConfig.class)
@Tag("integration")
class PhaseTeamRepositoryIT { ... }
```

**Anti-pattern to avoid:** Do NOT use `@Configuration` instead of `@TestConfiguration` — a plain `@Configuration` gets picked up by production component scan and activates auditing globally, which is unexpected outside test scope.

---

### `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` (unit test)

**Analog:** Any plain unit test without Spring context. Closest in the codebase: `src/test/java/org/ctc/backup/restore/NoopRestoreFailureInjectorTest.java` (minimal unit test for a test-infrastructure class).

**Unit test pattern** (standard for this project per TESTING.md):

```java
@ExtendWith(MockitoExtension.class)
class ContextLoadCountListenerTest {

    @Test
    void whenInitializeCalled_thenCountIncrements() {
        // given
        var listener = new ContextLoadCountListener();
        // Note: static AtomicInteger — test must account for state from previous invocations
        // OR use a fresh JVM (not reliable in unit tests). Test the increment delta instead.
        int before = ContextLoadCountListener.getCount();  // expose via package-private getter

        // when
        listener.initialize(null);  // ConfigurableApplicationContext not needed for counter

        // then
        assertThat(ContextLoadCountListener.getCount()).isEqualTo(before + 1);
    }
}
```

**No `@Tag` needed** — plain unit test (untagged) runs under Surefire `default-test` per TESTING.md convention.

**BDD naming:** `whenAction_thenResult()` pattern (no preconditions needed for atomic counter test).

---

### `src/test/java/org/ctc/backup/it/ImportLockServiceResetHelper.java` (test-component, event-driven)

**Analog:** `src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java` — test-scope helper in the backup IT cluster's `support/` package. The `Config` inner class (lines 96-115) exposes beans via `@TestConfiguration`.

**Context:** The RESEARCH.md clarifies (Pattern 2, Assumptions A1) that D-03's reset-only approach for the `ReentrantLock` does NOT address the non-resettable `CountDownLatch` beans. The helper should reset the `ImportLockService.ReentrantLock` (which IS idempotent), but the planner must decide whether `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` can actually be removed on the latch-using test methods.

**`@TestConfiguration` bean-config pattern** (from `BlockingRestoreFailureInjector.Config`, lines 96-115):

```java
// BlockingRestoreFailureInjector.Config — lines 96-115 (ANALOG)
@TestConfiguration
public static class Config {

    @Bean
    public CountDownLatch hasAcquired() {
        return new CountDownLatch(1);
    }

    @Bean
    public CountDownLatch releaseLatch() {
        return new CountDownLatch(1);
    }

    @Bean(name = "noopRestoreFailureInjector")
    @Primary
    public RestoreFailureInjector blockingInjector(CountDownLatch hasAcquired,
                                                   CountDownLatch releaseLatch) {
        return new BlockingRestoreFailureInjector(hasAcquired, releaseLatch, "race_results", 50);
    }
}
```

**`ImportLockServiceResetHelper` adaptation** — `@TestComponent` with `@AfterEach` reset:

```java
package org.ctc.backup.it;

import org.ctc.backup.lock.ImportLockService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.stereotype.Component;

@TestComponent
class ImportLockServiceResetHelper {

    private final ImportLockService importLockService;

    ImportLockServiceResetHelper(ImportLockService importLockService) {
        this.importLockService = importLockService;
    }

    @AfterEach
    void resetLock() {
        // Idempotent: unlock() is safe even if the lock is not held
        if (importLockService.isLocked()) {
            importLockService.forceUnlock();  // requires new package-private method or reflection
        }
    }
}
```

**Latch caveat** (RESEARCH Assumption A1): `CountDownLatch` is non-resettable. The reset helper alone is insufficient to remove `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` on test methods that use the latch handshake. The planner must classify each `@Test` method in the 3 backup ITs as latch-dependent or latch-free (see RESEARCH Open Question 1).

**ImportLockService API check needed:** Verify whether `ImportLockService` exposes a `forceUnlock()` or package-private reset method. If not, add one (or use `ReentrantLock.unlock()` if `isHeldByCurrentThread()` returns true). Read `src/main/java/org/ctc/backup/lock/ImportLockService.java` before planning the reset method.

---

### `docs/test-performance.md` (documentation)

**Analog:** `docs/security/sast-acceptance.md` (living phase-deliverable with ToC + per-section tables) and `docs/uat/UAT-02-legacy-season-smoke.md` (structured procedure doc with pass criteria).

**Shape from `sast-acceptance.md`** (lines 1-19):

```markdown
# SAST Acceptance Log

**Buckets:**
- **fixed** — ...
- **suppressed** — ...

## SSRF (Server-Side Request Forgery)

| Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
|----------|------|----------|--------|-----------|---------------|
| ... | ... | ... | ... | ... | ... |
```

**Shape from `UAT-02-legacy-season-smoke.md`** (lines 1-50):

```markdown
# UAT-02: Legacy Season Visual Smoke (Phase 83 QUAL-05)

## Purpose
...

## Pre-Conditions
...

## Procedure
1. ...
2. ...

## Pass Criteria
- ...

## Fail Handling
...
```

**`docs/test-performance.md` structure** (combining both conventions):

```markdown
# Test Performance Log (Phase 86)

**Baseline date:** <date>
**Goal:** ≤7m 50s (≥30% reduction from v1.10 baseline of 11m 11s)

## PERF-02: ApplicationContext Init Counts

| Measurement Point | Context Loads | Run Command |
|-------------------|---------------|-------------|
| Pre-audit baseline | — | `./mvnw clean verify -Pe2e` |
| Post-optimization | — | `./mvnw clean verify -Pe2e` |

## PERF-04: Wallclock Measurements

### Local Baseline (D-09)
| Run | Maven "Total time" | bash `real` |
|-----|--------------------|-------------|
| 1   | —                  | —           |
...
Median: —

### CI Results (D-10 — 5 runs, drop min+max)
| Run | GitHub Actions "Total time" |
|-----|----------------------------|
...
Median: —

## PERF-05: Result Verdict

**Achieved:** —% reduction (— → —)
**Gate:** ≤7m 50s ✓ / ✗

## v1.12 Forward Path (if ≥30% not reached)

| Lever | Estimated Delta | Effort | Risks/Dependencies | Touchpoints |
|-------|----------------|--------|--------------------|-------------|
| 1. backup-staging singleton race | ~90s | L | ... | BackupImportService, BackupStagingCleanup |
| 2. Shared @ContextConfiguration | ~60s | M | ... | All IT clusters |
| 3. Testcontainers MariaDB reuse | ~120s | M | ... | Testcontainers setup |
```

---

### Sitegen cluster: 7 files removing `@DirtiesContext` (integration test, file-I/O)

**Files:**
- `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java`
- `src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java`
- `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java`

**Before-state analog:** `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` (read in full above)

**Before-state: current annotation cluster** (lines 40-63 of `DriverProfilePageGeneratorTest.java`):

```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext                          // <-- TO REMOVE
class DriverProfilePageGeneratorTest {

    private Path tempDir;

    @Autowired private SiteProperties siteProperties;

    @BeforeAll
    void setUp(@TempDir Path injectedTempDir) {
        this.tempDir = injectedTempDir;
        siteProperties.setOutputDir(tempDir.toString());  // <-- ROOT CAUSE: singleton mutation
        // ... Flyway clean+migrate+seed
    }
}
```

**After-state pattern** (from `BackupStagingCleanupIT.java` lines 55-61 — the canonical `@TempDir` + `@DynamicPropertySource` pattern in this codebase):

```java
// BackupStagingCleanupIT.java lines 55-61 — EXACT CODEBASE ANALOG
@TempDir
static Path tempStagingDir;

@DynamicPropertySource
static void overrideStagingDir(DynamicPropertyRegistry registry) {
    registry.add("app.backup.staging-dir", () -> tempStagingDir.toString());
}
```

**Sitegen adaptation:**

```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// NO @DirtiesContext — each class gets a distinct context key via @DynamicPropertySource
class DriverProfilePageGeneratorTest {

    @TempDir
    static Path tempDir;  // static so @DynamicPropertySource can reference it

    @DynamicPropertySource
    static void siteOutputDir(DynamicPropertyRegistry registry) {
        registry.add("ctc.site.output-dir", () -> tempDir.toString());
    }

    @BeforeAll
    void setUp() {
        // siteProperties.setOutputDir() call removed — bound via @DynamicPropertySource
        // Flyway clean+migrate+seed stays unchanged
    }
}
```

**Imports to add:**

```java
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
```

**Imports to remove:**

```java
import org.springframework.test.annotation.DirtiesContext;
```

**`SiteGeneratorPhaseAwarenessIT` caveat** (RESEARCH Cluster A table): This IT additionally runs `Flyway.configure().clean().load().clean() + .migrate()` in `@BeforeAll`, which modifies the shared H2 DB (`DB_CLOSE_DELAY=-1`). The `@DirtiesContext` may still be needed after the `outputDir` fix — D-01 random-seed verification must confirm. The `@Tag("integration")` on this class must be preserved (it routes to Failsafe, not Surefire).

**`@TempDir` context-key tradeoff** (RESEARCH Open Question 2): Each test class that binds a unique `@TempDir` path via `@DynamicPropertySource` gets a distinct Spring TCF cache key — up to 7 separate context startups instead of the current 1 shared + 7 evictions. The `ContextLoadCountListener` pre/post delta will quantify whether this is a net win.

---

### Backup IT cluster: 3 files with `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` (integration test, event-driven)

**Files:**
- `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java`
- `src/test/java/org/ctc/backup/it/ImportLockBannerAdviceIT.java`
- `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java`

**Before-state: current annotation pattern** (from `ImportConcurrentLockIT.java` lines 66-82):

```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(BlockingRestoreFailureInjector.Config.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)  // <-- audit target
@Tag("integration")
class ImportConcurrentLockIT {
```

**Root cause** (RESEARCH Cluster B): `BlockingRestoreFailureInjector.Config` exposes two `CountDownLatch` singleton beans. `CountDownLatch` is non-resettable after `countDown()` reaches 0. The `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` is the current workaround to get fresh latches per test method.

**D-03 reset helper** (planner decision): The `ImportLockService.ReentrantLock` IS idempotently resettable via `unlock()`. The reset helper covers only this lock. The latch cannot be reset without either (a) retaining `@DirtiesContext`, or (b) refactoring `BlockingRestoreFailureInjector.Config` to use a resettable primitive (e.g. `Semaphore`). See RESEARCH Assumption A1.

**`ImportLockBannerAdviceIT` method analysis** (lines 121-207): The class has 3 `@Test` methods:
- `givenLockHeld_whenGetAdminSeasons_thenResponseBodyContainsBannerWording()` — uses `hasAcquired` + `releaseLatch` (LATCH-DEPENDENT)
- `givenLockHeld_whenGetSiteIndex_thenBannerWordingAbsent()` — uses `hasAcquired` + `releaseLatch` (LATCH-DEPENDENT)
- `givenLockNotHeld_whenGetAdminSeasons_thenBannerWordingAbsent()` — does NOT use latches (LATCH-FREE)

The latch-free method could potentially move to a separate IT that does not `@Import(BlockingRestoreFailureInjector.Config.class)`. This is the planner's structural decision per RESEARCH Open Question 1.

---

### `TestDataServiceIntegrationTest.java` — remove `@DirtiesContext` (integration test, CRUD)

**Before-state** (lines 22-32):

```java
// Phase 79 Wave 1 fix: matches sitegen-test pattern (Flyway clean+migrate+seed in @BeforeAll
// + @DirtiesContext) to defend against shared H2 (DB_CLOSE_DELAY=-1) state left by preceding
// @DirtiesContext sitegen tests under random Surefire orderings (e.g. seed=1234).
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext                          // <-- conditional removal after sitegen cluster fix
@Transactional
class TestDataServiceIntegrationTest {
```

**Removal condition** (RESEARCH Cluster C): This `@DirtiesContext` was added defensively in Phase 79 to guard against H2 state left by sitegen tests. If the sitegen cluster fix (D-04) eliminates the sitegen-driven H2 pollution, this annotation may become unnecessary. D-01 verification with all 3 seeds AFTER the sitegen fix must confirm removal safety.

**After removal:** The comment block at lines 22-26 is also removed (D-07 rule: no historical notes needed — commit message captures the reversal).

---

### Repository ITs: `@DataJpaTest` conversion (3 files)

**Files:**
- `src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java`
- `src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java`
- `src/test/java/org/ctc/domain/repository/SeasonPhaseGroupRepositoryIT.java`

**Before-state** (from `PhaseTeamRepositoryIT.java` lines 1-29):

```java
// @SpringBootTest precedent honored over D-13 @DataJpaTest — see RESEARCH Open Question 1
// ... (comment removed per D-07)

@SpringBootTest          // <-- REPLACE
@ActiveProfiles("dev")   // <-- REMOVE (DataJpaTest uses its own embedded DB)
@Transactional           // <-- REMOVE (DataJpaTest is @Transactional by default)
@Tag("integration")      // <-- KEEP
class PhaseTeamRepositoryIT {
```

**After-state annotation cluster:**

```java
@DataJpaTest
@Import(JpaAuditingConfig.class)
@Tag("integration")
class PhaseTeamRepositoryIT {
```

**Imports: before:**

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
```

**Imports: after:**

```java
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.ctc.testsupport.JpaAuditingConfig;  // or org.ctc.domain.repository per planner decision
```

**`TestEntityManager` option:** `@DataJpaTest` auto-configures `TestEntityManager` as an injectable alternative to `EntityManager`. The existing `@PersistenceContext EntityManager entityManager` field continues to work — no change required. Optionally replace with `@Autowired TestEntityManager em` (functionally equivalent; `em.flush()` maps to `entityManager.flush()`).

**Flyway seed data fix** (RESEARCH Pitfall 2): `@DataJpaTest` disables Flyway by default. The existing `newPhase()` helper in `PhaseTeamRepositoryIT.java` (lines 141-146) calls `raceScoringRepository.findAll().get(0)` — this returns empty under `@DataJpaTest` because no Flyway migration ran. Replace with inline fixture in `@BeforeEach`:

```java
// Before: in newPhase() helper (line 143-144)
phase.setRaceScoring(raceScoringRepository.findAll().get(0));
phase.setMatchScoring(matchScoringRepository.findAll().get(0));

// After: @BeforeEach seeds scoring rows
RaceScoring rs;
MatchScoring ms;

@BeforeEach
void seedScoring() {
    rs = raceScoringRepository.save(new RaceScoring(/* minimal valid constructor args */));
    ms = matchScoringRepository.save(new MatchScoring(/* minimal valid constructor args */));
}

// In newPhase() helper — replace findAll().get(0) references:
phase.setRaceScoring(rs);
phase.setMatchScoring(ms);
```

**Check `RaceScoring` and `MatchScoring` constructors** before implementing: read `src/main/java/org/ctc/domain/model/RaceScoring.java` to determine the minimal constructor signature.

---

## Shared Patterns

### `@TempDir` + `@DynamicPropertySource` (sitegen cluster)

**Source:** `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` (lines 55-61)
**Apply to:** All 7 sitegen test files

```java
// BackupStagingCleanupIT.java lines 55-61 — copy this exactly
@TempDir
static Path tempStagingDir;

@DynamicPropertySource
static void overrideStagingDir(DynamicPropertyRegistry registry) {
    registry.add("app.backup.staging-dir", () -> tempStagingDir.toString());
}
```

Adapt: change field name to `tempDir`; change property key to `"ctc.site.output-dir"`.

Required imports:

```java
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
```

### `@TestConfiguration` pattern for test-scope beans

**Source:** `src/test/java/org/ctc/backup/service/FailAtTableInjector.java` (lines 90-108)
**Apply to:** `JpaAuditingConfig.java`

```java
// FailAtTableInjector.Config lines 90-108 — ANALOG for @TestConfiguration shape
@TestConfiguration
public static class Config {
    @Bean(name = "noopRestoreFailureInjector")
    @Primary
    public RestoreFailureInjector failAtTable() {
        return new FailAtTableInjector("race_results", 500);
    }
}
```

`JpaAuditingConfig` is a top-level class (not nested), uses `@TestConfiguration @EnableJpaAuditing` with no `@Bean` method body.

### `@Tag("integration")` on all IT classes

**Source:** TESTING.md §"Test Categorization (`@Tag`)" + all existing `*IT.java` files (e.g. `PhaseTeamRepositoryIT.java` line 29, `SiteGeneratorPhaseAwarenessIT.java` line 51).
**Apply to:** All `*IT.java` files including converted `@DataJpaTest` ITs.

```java
// Placement: directly above the class declaration, after framework annotations
@DataJpaTest
@Import(JpaAuditingConfig.class)
@Tag("integration")               // <-- mandatory for Failsafe default-it routing
class PhaseTeamRepositoryIT { ... }
```

### BDD test method naming

**Source:** TESTING.md §"Test Naming Convention (BDD Style)"
**Apply to:** `ContextLoadCountListenerTest.java`

Pattern: `givenContext_whenAction_thenResult()` or `whenAction_thenResult()` for simple cases.

### D-01 random-seed verification command

**Source:** TESTING.md §"Test Invocation Discipline" + RESEARCH.md Code Examples

```bash
# Per-class removal verification (3 seeds required per D-01)
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 \
  -Dtest=DriverProfilePageGeneratorTest
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678 \
  -Dtest=DriverProfilePageGeneratorTest
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999 \
  -Dtest=DriverProfilePageGeneratorTest

# For Failsafe IT classes (SiteGeneratorPhaseAwarenessIT, DataJpaTest ITs):
./mvnw verify -Dit.test=SiteGeneratorPhaseAwarenessIT
./mvnw verify -Dit.test=PhaseTeamRepositoryIT
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/test/resources/META-INF/spring.factories` | config | — | No `spring.factories` exists anywhere in `src/` — only in jar dependencies on the classpath. Registration key and format taken from RESEARCH.md (VERIFIED against `spring-boot-test-4.0.6.jar`). |

---

## Metadata

**Analog search scope:** `src/test/java/org/ctc/` (all packages), `docs/`
**Files scanned:** 12 source files read in full
**Pattern extraction date:** 2026-05-17
