# Phase 86: Test Wallclock Reduction - Research

**Researched:** 2026-05-17
**Domain:** Maven Surefire/Failsafe test performance, Spring TestContext Framework, @DataJpaTest slice testing, ApplicationContext cache mechanics, GitHub Actions wallclock measurement
**Confidence:** HIGH (codebase verified directly; Spring Boot 4.0.6 jars inspected on disk; all @DirtiesContext usages enumerated from source)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**@DirtiesContext Audit Strategy**

- D-01: Default audit mode is **remove-then-verify**. Each `@DirtiesContext` is treated as tech debt until proven otherwise: remove the annotation, run 3x Surefire with `-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234, 5678, 9999}`, all-green = stays removed, any-red = annotation restored with an explanatory comment naming the specific shared state.
- D-02: Verify loop is **Surefire-only per removal** (`./mvnw test -Dtest=…` with random order) for fast feedback — the full `./mvnw verify -Pe2e` gate runs once at phase close.
- D-03: The 3 backup ITs with `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` (`ImportConcurrentLockIT`, `ImportLockBannerAdviceIT`, `ImportLockedPostRejectorIT`) replace the annotation with a dedicated **`ImportLockService` reset helper bean** invoked via `@AfterEach`. The shared state is concretely the `ReentrantLock` inside `ImportLockService`.
- D-04: The 7 sitegen tests with class-level `@DirtiesContext` are tackled as a **cluster root-cause fix**: hypothesis is the shared `ctc.site.output-dir` filesystem path. Use per-test `@TempDir Path siteOut` + `@DynamicPropertySource` (or property override) to bind `ctc.site.output-dir` to a per-class temp directory.

**@DataJpaTest Pilot Scope**

- D-05: **Primary pilot is `PhaseTeamRepositoryIT`** — smallest schema surface.
- D-06: **Scope is all three domain Phase repository ITs** (`PhaseTeamRepositoryIT` + `SeasonPhaseRepositoryIT` + `SeasonPhaseGroupRepositoryIT`) converted to `@DataJpaTest`.
- D-07: The legacy `// @SpringBootTest precedent honored over D-13 @DataJpaTest` comments are **removed along with the `@SpringBootTest` annotation**. No historical note needed.
- D-08: **Auditing handling:** add a dedicated `@TestConfiguration JpaAuditingConfig` class with `@EnableJpaAuditing`, imported into each `@DataJpaTest` via `@Import(JpaAuditingConfig.class)`.

**Wallclock Measurement & Baseline**

- D-09: **Local re-baseline FIRST.** Run `time ./mvnw clean verify -Pe2e` 3x on current v1.11-master and record the median in `docs/test-performance.md`.
- D-10: **CI median methodology is 5 consecutive runs, drop min+max, median of the 3 middle runs.**
- D-11: **CI is the source of truth** for the ≤7m 50s gate.
- D-12: **ApplicationContext init count instrumentation:** custom Spring `ApplicationContextInitializer` registered via `spring.factories` (test scope), writes count to `target/test-perf/context-loads.txt` at JVM shutdown.

**Blocker-Fallback Path**

- D-13: If ≥30% is not achieved, v1.12 forward path documents **top-3 structural levers** with delta/effort/risks.
- D-14: The `data/dev/backup-staging/` singleton-path race is **audited and documented as Top-1 v1.12 lever**, not fixed in Phase 86.
- D-15: **Realistic-optimistic expectation: 20-25% reduction** (8m30s-9m). ≥7m 50s likely requires Failsafe-parallelism unlock deferred to v1.12.

**Plan Scope Limit**

- D-16: **Soft cap: ~6 plans, 2 waves.**

### Claude's Discretion

- Exact wording of `docs/test-performance.md` sections; specific seed values within the (1234/5678/9999) family; whether the `JpaAuditingConfig` lives in `src/test/java/org/ctc/testsupport/` or `src/test/java/org/ctc/domain/repository/`.

### Deferred Ideas (OUT OF SCOPE)

- Per-fork `data/dev/backup-staging/` refactor (Top-1 v1.12 lever)
- Shared `@SpringBootTest` context strategy via explicit `@ContextConfiguration` classes
- Testcontainers MariaDB reuse (`withReuse(true)`)
- Wider `@DataJpaTest` migration beyond the 3 Phase repository ITs
- Spring TCF Cache stats `DEBUG` logging as alternative to custom listener

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PERF-01 | Every `@DirtiesContext` usage in `src/test/java/` is audited — each is either removed (after random-order verification) or retained with an explanatory comment naming the specific shared state | 11 actual `@DirtiesContext` annotation sites identified and classified by root cause; remove-then-verify protocol confirmed in TESTING.md (Phase 79 precedent) |
| PERF-02 | A diagnostic logging pass counts unique Spring `ApplicationContext` initialisations during `./mvnw verify -Pe2e` and baseline + post-optimization counts are recorded in `docs/test-performance.md` | `ContextLoadCountListener` pattern using `ApplicationContextInitializer` + `spring.factories` (test scope) confirmed viable in Spring Boot 4.0.6; JVM shutdown hook for file write confirmed |
| PERF-03 | At least one repository-only IT is converted from full `@SpringBootTest` to `@DataJpaTest` slice without losing assertion coverage | `PhaseTeamRepositoryIT` identified as primary pilot; `@DataJpaTest` + `@Import(JpaAuditingConfig.class)` + `@Tag("integration")` pattern documented; no Flyway auto-run needed (H2 schema from `@DataJpaTest`'s embedded DB) |
| PERF-04 | `./mvnw verify -Pe2e` wallclock is reduced by ≥30% OR architectural blocker documented with v1.12 forward path | Realistic expectation 20-25%; top-3 v1.12 levers identified and ready to document: (1) backup-staging singleton race, (2) shared `@ContextConfiguration`, (3) Testcontainers MariaDB reuse |
| PERF-05 | Improved wallclock verified on CI over 3 consecutive runs; median recorded as new v1.11 baseline | CI workflow already supports `workflow_dispatch` manual re-runs; `time` wrapper + Maven "Total time" dual-record pattern established in Phase 79 |

</phase_requirements>

---

## Summary

Phase 86 targets a ≥30% wallclock reduction of `./mvnw verify -Pe2e` from the v1.10 baseline of 11m 11s to ≤7m 50s. The codebase shows **11 actual `@DirtiesContext` annotation sites** across 11 test classes, split into three clusters: (1) 7 sitegen tests with class-level `@DirtiesContext` due to a shared `SiteProperties` bean whose `outputDir` is mutated in `@BeforeAll` — this is the structural root cause, not just filesystem coupling; (2) 3 backup ITs using `BEFORE_EACH_TEST_METHOD` mode to reset non-resettable `CountDownLatch` beans injected by `@Import(BlockingRestoreFailureInjector.Config.class)`; and (3) 1 `TestDataServiceIntegrationTest` with a defensive `@DirtiesContext` added in Phase 79 to guard against shared H2 state.

The three domain Phase repository ITs (`PhaseTeamRepositoryIT`, `SeasonPhaseRepositoryIT`, `SeasonPhaseGroupRepositoryIT`) are currently `@SpringBootTest` + `@Transactional` and carry the comment `// @SpringBootTest precedent honored over D-13 @DataJpaTest`. They use only `JpaRepository` methods, `EntityManager`, and Flyway-seeded `RaceScoring`/`MatchScoring` rows — precisely the clean profile that `@DataJpaTest` supports. The auditing issue (`@EnableJpaAuditing` lives on `CtcManagerApplication`) is solved by a `@TestConfiguration JpaAuditingConfig` class with `@EnableJpaAuditing` imported via `@Import`.

The realistic wallclock improvement from the D-03/D-04/D-06 work is estimated at 20-25%. The primary structural blocker to ≥30% remains the Failsafe `data/dev/backup-staging/` singleton path that prevents per-fork parallelism — confirmed from Phase 79 investigation. The phase closes cleanly with the blocker documented as a v1.12 forward path if 30% is not reached.

**Primary recommendation:** Execute audits in wave order: (Wave 1) re-baseline + sitegen cluster root-cause fix + ContextLoadCountListener instrumentation; (Wave 2) backup IT reset-bean replacement + @DataJpaTest pilot conversions + CI wallclock harvest + docs/test-performance.md finalization.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `@DirtiesContext` audit and removal | Test infrastructure | — | Pure test-class annotation changes; no production code touched |
| `ImportLockService` reset helper bean | Test infrastructure (new `@TestComponent` or `@BeforeEach` helper) | Production service read path | `ReentrantLock.unlock()` is already safe to call from non-holding thread; reset only needs to call `unlock()` idempotently |
| Sitegen outputDir isolation | Test infrastructure (`@DynamicPropertySource`) | Spring `SiteProperties` bean | `SiteProperties.outputDir` is a mutable singleton; `@DynamicPropertySource` re-binds the property before context is built for each test class |
| `@DataJpaTest` JPA auditing | Test configuration (`@TestConfiguration JpaAuditingConfig`) | `@EnableJpaAuditing` normally on `CtcManagerApplication` | Slice tests do not load `CtcManagerApplication` — auditing must be re-enabled explicitly in test scope |
| ApplicationContext counting | Test infrastructure (`ContextLoadCountListener` via `spring.factories`) | JVM shutdown hook + `target/test-perf/` file | No production code change needed; listener is registered only in `src/test/resources/META-INF/spring.factories` |
| Wallclock measurement | CI (GitHub Actions runner) | Local baseline (fast feedback only) | D-11: CI is source of truth for the ≤7m 50s gate |
| `docs/test-performance.md` | Documentation (new file) | — | Deliverable required by PERF-02/PERF-04/PERF-05 |

---

## Standard Stack

No new external dependencies are introduced in Phase 86. All tools are already on the classpath.

### Core (already available)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-test-autoconfigure` | 4.0.6 | `@DataJpaTest` slice annotation | Ships with Spring Boot; auto-configures JPA slice including H2 |
| `spring-test` | 6.x (managed by SB 4.0.6) | `@DirtiesContext`, `@DynamicPropertySource`, `ApplicationContextInitializer` | Core Spring test infrastructure |
| `junit-jupiter` | 5.x (managed) | `@TempDir`, `@BeforeEach`, `@Tag` | JUnit 5 Jupiter, already in use |
| `maven-surefire-plugin` | 3.5.5 (installed) | Random order, per-fork JVM | Already wired in `pom.xml` with `forkCount=2` |
| `h2` | 2.x (managed) | In-memory DB for `@DataJpaTest` | Already the dev/test profile database |

### No New Dependencies Required

`@DataJpaTest` is available from `spring-boot-starter-test` which is already on the test classpath. The `JpaAuditingConfig` `@TestConfiguration` class is hand-rolled Java (< 10 lines). The `ContextLoadCountListener` is a hand-rolled `ApplicationContextInitializer` (< 30 lines).

**Package Legitimacy Audit:** N/A — no new packages are installed in Phase 86.

---

## Architecture Patterns

### System Architecture Diagram

```
./mvnw verify -Pe2e
      │
      ├─► Surefire (forkCount=2, reuseForks=true)
      │       │
      │       ├─► Fork JVM 1 ──► Unit tests (@Tag untagged)
      │       └─► Fork JVM 2 ──► Unit tests (@Tag untagged)
      │           │
      │           └─► Spring TCF cache: reuses context across test classes
      │                   └─► @DirtiesContext → evicts → new context startup (~5-8s each)
      │
      ├─► Failsafe default-it (forkCount=1C, reuseForks=true)
      │       │
      │       └─► Single fork ──► ITs (@Tag("integration"))
      │           │
      │           └─► Spring TCF cache: reuses context unless @DirtiesContext evicts
      │                   ├─► @DirtiesContext(BEFORE_EACH_TEST_METHOD) → evict per method
      │                   └─► Class-level @DirtiesContext → evict after class
      │
      └─► Failsafe e2e-it (only with -Pe2e, forkCount from default)
              └─► @Tag("e2e") Playwright tests
                  └─► @SpringBootTest(RANDOM_PORT) → always new context
```

**Wallclock reduction levers in scope for Phase 86:**
- Removing class-level `@DirtiesContext` from 7 sitegen tests → fewer forced context evictions in Surefire
- Replacing `BEFORE_EACH_TEST_METHOD` on 3 backup ITs → fewer forced context evictions in Failsafe
- Converting 3 `@SpringBootTest` repository ITs to `@DataJpaTest` → faster context startup for those tests

### Recommended Project Structure (new files only)

```
src/test/java/org/ctc/
├── domain/
│   └── repository/
│       └── JpaAuditingConfig.java        # @TestConfiguration @EnableJpaAuditing
├── backup/
│   └── it/
│       └── ImportLockResetHelper.java    # @TestComponent: @AfterEach → importLockService.unlock()
src/test/resources/
└── META-INF/
    └── spring.factories                  # registers ContextLoadCountListener
docs/
└── test-performance.md                   # PERF-02/04/05 deliverable (new file)
target/test-perf/
└── context-loads.txt                     # written by ContextLoadCountListener at JVM shutdown
```

### Pattern 1: Sitegen outputDir Isolation via @DynamicPropertySource

**What:** Replace `@DirtiesContext` on sitegen test classes by making each test class bind `ctc.site.output-dir` to a unique `@TempDir` before the ApplicationContext is built. This prevents the `SiteProperties` singleton from sharing a filesystem path across test classes.

**Root cause confirmed:** All 7 sitegen test classes call `siteProperties.setOutputDir(tempDir.toString())` in `@BeforeAll`, mutating the singleton `SiteProperties` bean. Since Spring's TestContext Framework caches contexts by their configuration key, a second test class that hits the same cached context receives a `SiteProperties` bean already mutated by the first class's `@BeforeAll`. `@DirtiesContext` is currently the escape hatch.

**Correct fix:** `@DynamicPropertySource` binds `ctc.site.output-dir` to a per-class `@TempDir` value BEFORE context creation, making each test class have a distinct context cache key (or — if the path is injected only at test setup time, see caveat below).

**Caveat — `@DynamicPropertySource` with `@TempDir` interaction:** `@DynamicPropertySource` is evaluated when the context is first created. `@TempDir` injects a new temp dir per test class lifecycle. The binding must be done in a `static` method so it runs at context-creation time.

**When to use:** Whenever a test class mutates a singleton Spring bean that affects filesystem output paths.

```java
// Source: Spring Framework docs @DynamicPropertySource + @TempDir (verified Spring Framework 6.x)
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// NO @DirtiesContext here — each class gets its own context via @DynamicPropertySource
class DriverProfilePageGeneratorTest {

    @TempDir
    static Path tempDir;  // static so @DynamicPropertySource can reference it

    @DynamicPropertySource
    static void siteOutputDir(DynamicPropertyRegistry registry) {
        registry.add("ctc.site.output-dir", () -> tempDir.toString());
    }

    // ... rest unchanged
}
```

**Important:** Because `tempDir` is `static` and `@TempDir` injects a new directory per class, and because `@DynamicPropertySource` uses a different context cache key per unique property set, each test class gets an isolated Spring context with its own `SiteProperties.outputDir`. The 7 sitegen test classes currently share a Spring context — the sitegen cluster may end up with 7 separate contexts instead of 1 shared one. This is the expected tradeoff: instead of 7 `@DirtiesContext` evictions (which destroy and rebuild the context mid-suite), we get 7 fixed context startups at class load time. The net wallclock impact depends on how many of these 7 classes happen to run sequentially in the same Surefire fork.

**Alternative if full context-key isolation causes more startups:** Restore `siteProperties.setOutputDir()` in `@BeforeAll` but add a `@AfterAll` that resets `siteProperties.setOutputDir("target/site")` to the default. This approach keeps the shared context (no extra startup) but requires every sitegen test class to clean up after itself — fragile under parallelism. `@DynamicPropertySource` is the cleaner solution.

### Pattern 2: ImportLockService Reset Helper Bean

**What:** Replace `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` on 3 backup ITs with an `@AfterEach` reset that directly unlocks the `ImportLockService.ReentrantLock`.

**Root cause:** `ImportConcurrentLockIT`, `ImportLockBannerAdviceIT`, and `ImportLockedPostRejectorIT` all `@Import(BlockingRestoreFailureInjector.Config.class)`, which exposes two `CountDownLatch` singleton beans. `CountDownLatch` is non-resettable after it reaches 0, so the shared context becomes corrupted between test methods. `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` forces a fresh context (with fresh latches) before each test method — at the cost of a full context reload per method.

**D-03 fix:** The CONTEXT.md decision is to reset `ImportLockService` (the `ReentrantLock`) via `@AfterEach`. However, the real non-resettable state is the `CountDownLatch` beans, not the `ReentrantLock`. The `ReentrantLock` is already idempotently safe (`unlock()` is a no-op if not held). The `CountDownLatch` cannot be reset.

**Implication for D-03:** The D-03 "reset helper bean" approach will NOT replace `@DirtiesContext` on `ImportConcurrentLockIT` if that test's only `@Test` method relies on `hasAcquired.await()` — because the latch is used inside the test, not just in `@BeforeEach`. However, for `ImportLockBannerAdviceIT` and `ImportLockedPostRejectorIT`, if there are test methods that do NOT use the slow-import handshake, those can be kept in the same context. The `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` is needed only for methods that trigger the latch countdown.

**Workable approach (research recommendation):** For the 3 backup ITs, verify whether any test methods do NOT use the latch-based slow-import scenario. If yes, split those methods out. For latch-using methods, `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` must be **retained** with an explanatory comment per D-01 (annotation stays if shared state requires it). The Phase 79 comment already explains this correctly. The `ImportLockService.unlock()` reset-only approach (without addressing the `CountDownLatch`) is insufficient for these 3 ITs. [ASSUMED — needs verification by running the 3 seeds and checking if removing the annotation causes test failures]

### Pattern 3: @DataJpaTest with JpaAuditingConfig

**What:** Convert `PhaseTeamRepositoryIT`, `SeasonPhaseRepositoryIT`, `SeasonPhaseGroupRepositoryIT` from `@SpringBootTest` to `@DataJpaTest` slices.

**`@DataJpaTest` auto-configures in Spring Boot 4.0.6:** [VERIFIED: spring-boot-test-autoconfigure-4.0.6.jar inspected on disk]
- Embedded H2 database (no profile override needed for `dev` since `@DataJpaTest` configures its own embedded DB)
- Hibernate JPA with H2 dialect
- Flyway auto-run (disabled by default — `@DataJpaTest` disables `spring.flyway.enabled`)
- `TestEntityManager` autowirable alternative to `EntityManager`
- Does NOT load `@Service`, `@Controller`, `@Component` beans — pure JPA slice

**Critical gap: Flyway + scoring rows.** The 3 repository ITs access `raceScoringRepository.findAll().get(0)` and `matchScoringRepository.findAll().get(0)` — these rows must exist in the database. With `@DataJpaTest`, Flyway is disabled by default, so the V1 migration (which seeds `RaceScoring` and `MatchScoring` rows) does not run. Two options:

- Option A: Re-enable Flyway for the slice via `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` + `@TestPropertySource(properties = "spring.flyway.enabled=true")` — then Flyway runs against H2
- Option B: Insert `RaceScoring` and `MatchScoring` rows in `@BeforeEach` using `TestEntityManager` instead of relying on Flyway seed data

**Recommended: Option B** for cleaner test isolation. The repository ITs only need one `RaceScoring` and one `MatchScoring` instance to exist — constructing them in `@BeforeEach` is 5 lines and removes the Flyway dependency entirely.

**`@EnableJpaAuditing` gap:** `CtcManagerApplication` carries `@EnableJpaAuditing`. `@DataJpaTest` does not load `CtcManagerApplication`, so `AuditingEntityListener` (which populates `createdAt`/`updatedAt` on `BaseEntity`) is not activated. Fix: `JpaAuditingConfig.java`:

```java
// Source: Spring Boot test documentation @DataJpaTest + @Import pattern [ASSUMED: confirmed pattern from Spring Data docs]
package org.ctc.domain.repository;  // or org.ctc.testsupport

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@TestConfiguration
@EnableJpaAuditing
class JpaAuditingConfig {
    // no beans needed — annotation activates AuditingEntityListener
}
```

Used in each test class: `@DataJpaTest @Import(JpaAuditingConfig.class) @Tag("integration")`.

**`@Tag("integration")` requirement:** TESTING.md §"Test Categorization (@Tag)" mandates `@Tag("integration")` on every `*IT.java` class so Failsafe `default-it` picks them up. `@DataJpaTest` does not affect `@Tag` routing — the tag must remain.

**Failsafe fork and embedded DB:** Failsafe `default-it` runs with the same `reuseForks=true` (inherited from Surefire plugin config, though Failsafe may differ — check `pom.xml`). `@DataJpaTest` creates an embedded H2 per context; since `reuseForks=true` and the `@DataJpaTest` context key is distinct from the `@SpringBootTest` context key, these contexts are separate. No shared-state risk with the existing full `@SpringBootTest` ITs.

### Pattern 4: ContextLoadCountListener

**What:** Custom `ApplicationContextInitializer` that increments a static `AtomicInteger` on each new Spring context creation, writes the count to `target/test-perf/context-loads.txt` at JVM shutdown.

**Registration:** `src/test/resources/META-INF/spring.factories` (test-scope resource):

```properties
# Source: spring-boot-test 4.0.6 jar META-INF/spring.factories uses same registration key
org.springframework.context.ApplicationContextInitializer=\
org.ctc.testsupport.ContextLoadCountListener
```

**Implementation pattern:**

```java
// Source: Spring Framework ApplicationContextInitializer javadoc [ASSUMED — standard pattern]
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
                System.err.println("ContextLoadCountListener: could not write marker file: " + e.getMessage());
            }
        }));
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        count.incrementAndGet();
    }
}
```

**Spring Boot 4 / Spring Framework 6 spring.factories status:** [VERIFIED: spring-boot-test 4.0.6 jar still ships `META-INF/spring.factories` with `org.springframework.context.ApplicationContextInitializer` key]. The migration from `spring.factories` to `org.springframework.boot.autoconfigure.AutoConfiguration.imports` applies to AUTO-CONFIGURATION beans only, not `ApplicationContextInitializer`. Test-scope registration via `src/test/resources/META-INF/spring.factories` remains the standard mechanism in Spring Boot 4.0.6.

**Multi-fork consideration:** Surefire forkCount=2 creates 2 JVMs. Each JVM has its own static `AtomicInteger`. The shutdown hook writes a file per JVM at shutdown. To aggregate across forks, use distinct file names per fork (e.g., append `System.currentTimeMillis()` or use a fork-specific suffix) and sum during reporting. Simpler alternative: use a single file with atomic append + count the lines post-run. For Phase 86's purpose (pre/post delta), the sum from both fork files is sufficient.

### Pattern 5: CI Wallclock Measurement

**What:** Capture Maven wallclock time reliably in GitHub Actions.

**Current CI state:** `ci.yml` runs `./mvnw verify --no-transfer-progress` then `./mvnw verify -Pe2e --no-transfer-progress` as two separate steps. Maven's "Total time" is printed at the end of each invocation. GitHub Actions step summaries capture stdout.

**Recommended approach:** Wrap the E2E verify invocation in a `time` bash call and capture both Maven "Total time" and bash wall time:

```yaml
- name: E2E Tests (timed)
  run: |
    { time ./mvnw verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true; } 2>&1 | tee /tmp/e2e-output.txt
    grep "BUILD\|Total time" /tmp/e2e-output.txt
```

**5 consecutive runs methodology (D-10):** GitHub Actions does not natively loop a single job step 5 times. Options:
- Manual `workflow_dispatch` re-run 5x — straightforward, documented
- Matrix strategy with `strategy: { matrix: { run: [1,2,3,4,5] } }` — runs 5 parallel jobs (different runners, so valid for variance but not sequential on the same hardware)
- Post-merge harvest over 5 PRs (only if PRs land in sequence) — impractical
- **Recommended:** `workflow_dispatch` manual re-trigger 5x after Phase 86 branch is merged or on a throwaway commit. Record the 5 "Total time" values, drop min+max, take median of 3.

### Anti-Patterns to Avoid

- **Removing `@DirtiesContext` without running 3 random seeds:** D-01 requires all 3 seeds (1234/5678/9999) to be green before any `@DirtiesContext` removal is committed. Running only the default order may miss ordering-dependent failures.
- **Adding `@AutoConfigureTestDatabase` without checking `replace.NONE` implications:** The default `@DataJpaTest` replaces the configured DataSource with an embedded one. With `replace.NONE`, the Flyway-sourced `dev` profile H2 is used but Flyway still does not run (disabled by default) — inconsistent. Prefer Option B (inline scoring data in `@BeforeEach`).
- **Forgetting `@Tag("integration")` on `@DataJpaTest` ITs:** Without the tag, Failsafe `default-it` does not execute the tests (they are not picked up by `<groups>integration</groups>`). The test would silently not run rather than fail visibly.
- **Using `static` `@TempDir` without understanding context-key implications:** If `@DynamicPropertySource` binds to a `static @TempDir`, the context cache key differs from classes without this binding. This is desired behavior here but would create separate contexts for each test class. Confirm the net context-count delta is acceptable.
- **`@EnableJpaAuditing` in a non-`@TestConfiguration` class:** If placed on a `@Configuration` class instead of a `@TestConfiguration`, it may be picked up by component scanning in the production context — always use `@TestConfiguration`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JPA slice test setup | Custom `@BeforeEach` wiring all JPA beans | `@DataJpaTest` slice annotation | Configures H2, Hibernate, repositories automatically; 10x less boilerplate |
| Property override in tests | `@TestPropertySource` + string parsing | `@DynamicPropertySource` | Thread-safe, evaluated before context startup, supports `Supplier<>` for dynamic values |
| ApplicationContext counting | Custom `TestExecutionListener` with aspect weaving | `ApplicationContextInitializer` + `spring.factories` | Initializer fires once per context creation; no aspect proxy needed |
| Fork-level JVM isolation | `@Nested` class workarounds | Surefire `forkCount` + `reuseForks` (already configured) | Already correct; do not change fork count per Phase 79 findings |

---

## @DirtiesContext Audit: Complete Inventory

**11 actual `@DirtiesContext` annotation sites across 11 test classes.** (Counted from `grep "^@DirtiesContext\|^\s*@DirtiesContext" src/test/java --include="*.java"` — verified 2026-05-17.)

### Cluster A: Sitegen Tests (7 files — class-level `@DirtiesContext`)

| File | Root Cause | D-04 Fix Viable? |
|------|-----------|-----------------|
| `DriverProfilePageGeneratorTest` | `SiteProperties.outputDir` mutation in `@BeforeAll` | Yes — `@DynamicPropertySource` |
| `MatchdaysPageGeneratorTest` | Same | Yes |
| `StandingsPageGeneratorTest` | Same | Yes |
| `DriverRankingPageGeneratorTest` | Same | Yes |
| `TeamProfilePageGeneratorTest` | Same | Yes |
| `SiteGeneratorE2ETest` | Same | Yes |
| `SiteGeneratorPhaseAwarenessIT` | Same + Flyway `clean()`+`migrate()` in `@BeforeAll` | Partially — `@DynamicPropertySource` fixes outputDir; Flyway reset still needed |

**Note on `SiteGeneratorPhaseAwarenessIT`:** This test additionally runs `Flyway.configure().clean().load().clean()` + `.migrate()` in `@BeforeAll` to get a clean DB state. Even with `@DynamicPropertySource` fixing the `outputDir` issue, the Flyway clean/migrate modifies the shared H2 DB (which is `DB_CLOSE_DELAY=-1`, so it persists across context reloads). The `@DirtiesContext` may still be needed on this specific test to evict the Spring context and get a fresh Hibernate session cache after the Flyway reset. This is the highest-risk removal in the cluster — D-01 random-seed verification is especially important here.

### Cluster B: Backup ITs (3 files — `BEFORE_EACH_TEST_METHOD`)

| File | Root Cause | D-03 Fix |
|------|-----------|---------|
| `ImportConcurrentLockIT` | `CountDownLatch` beans (non-resettable) in `BlockingRestoreFailureInjector.Config` | Only 1 `@Test` method — cannot split; `@DirtiesContext` must be **retained** per D-01 unless CountDownLatch is replaced with a resettable mechanism |
| `ImportLockBannerAdviceIT` | Same; multiple test methods, some use the latch | Check per-method: latch-free methods may not need `@DirtiesContext`; annotate at method level if possible |
| `ImportLockedPostRejectorIT` | Same | Same approach as `ImportLockBannerAdviceIT` |

**Research finding:** D-03 proposes an `ImportLockService` reset bean (`@AfterEach` unlock). The `ImportLockService.ReentrantLock` is already idempotent on unlock. However, the primary non-resettable state is the `CountDownLatch` injected by `@Import(BlockingRestoreFailureInjector.Config.class)` — not the `ReentrantLock`. To truly remove `@DirtiesContext(BEFORE_EACH_TEST_METHOD)`, the `BlockingRestoreFailureInjector` must be refactored to use a resettable synchronization primitive (e.g., a `Semaphore` or a `BlockingQueue`). This is a code change to `BlockingRestoreFailureInjector.Config`, not just to the test. Whether this is worth the complexity vs. retaining the annotation is a planner decision; research flags it as [ASSUMED] that D-03 applies cleanly to all 3 ITs without touching `BlockingRestoreFailureInjector`. [ASSUMED]

### Cluster C: TestDataServiceIntegrationTest (1 file — class-level)

| File | Root Cause | Removal Viable? |
|------|-----------|----------------|
| `TestDataServiceIntegrationTest` | Added in Phase 79 as a defensive guard against shared H2 state left by preceding sitegen tests under random ordering (seed=1234). Comment in file confirms this: "defend against shared H2 state left by preceding @DirtiesContext sitegen tests under random Surefire orderings" | **Conditionally removable:** if the sitegen cluster fixes (Cluster A) eliminate the sitegen `@DirtiesContext` context evictions that leave dirty H2 state, the defensive `@DirtiesContext` on this file may become unnecessary. Verify with 3 random seeds AFTER the sitegen fixes are applied. |

---

## Common Pitfalls

### Pitfall 1: sitegen `siteProperties.setOutputDir()` in `@BeforeAll` is Not Thread-Safe

**What goes wrong:** Two Surefire forks that both have sitegen test classes share the same H2 database (via `DB_CLOSE_DELAY=-1` — the H2 URL in `application-dev.yml` is `jdbc:h2:mem:ctcdb;DB_CLOSE_DELAY=-1`). Surefire `forkCount=2` creates 2 separate JVMs — each JVM has its own Spring context and its own H2 in-memory DB instance. So the sitegen tests in different forks do NOT share state. The `@DirtiesContext` on sitegen tests only causes within-fork context evictions.

**Why it happens:** Misunderstanding of `DB_CLOSE_DELAY=-1` scope — it keeps H2 alive across context reloads within the same JVM, not across JVMs.

**How to avoid:** The `@DynamicPropertySource` fix is correct and sufficient for within-fork isolation. No cross-fork issue exists for the sitegen cluster.

**Warning signs:** If sitegen tests start failing with "season already exists" errors after removing `@DirtiesContext` — the Flyway clean+migrate in `SiteGeneratorPhaseAwarenessIT.@BeforeAll` has dirtied the H2 DB and a subsequent test class found stale data. This confirms the Flyway-reset issue, not the `outputDir` issue.

### Pitfall 2: @DataJpaTest Does Not Run Flyway by Default

**What goes wrong:** `raceScoringRepository.findAll().get(0)` in `PhaseTeamRepositoryIT` returns an empty list after `@DataJpaTest` conversion because Flyway V1 migration (which inserts the default `RaceScoring` and `MatchScoring` rows) did not run.

**Why it happens:** `@DataJpaTest` sets `spring.flyway.enabled=false` by default to keep the slice pure. The H2 schema is created via Hibernate's `ddl-auto=create-drop` for the slice context.

**How to avoid:** Replace `raceScoringRepository.findAll().get(0)` with an inline `@BeforeEach` fixture that saves a new `RaceScoring` and `MatchScoring` instance. Use `@Transactional` rollback to clean up. This makes the test self-contained.

**Warning signs:** `IndexOutOfBoundsException: Index 0 out of bounds for length 0` on `findAll().get(0)` calls.

### Pitfall 3: `@DynamicPropertySource` Method Must Be Static

**What goes wrong:** `@TempDir` field is instance-level (`Path tempDir`), but `@DynamicPropertySource` method must be `static`. Referencing an instance field from a static method causes compile error.

**Why it happens:** `@DynamicPropertySource` is evaluated before context creation, before any instance is constructed.

**How to avoid:** Declare `@TempDir static Path tempDir` — JUnit 5 supports static `@TempDir` fields on `PER_CLASS` test instances.

**Warning signs:** Compile error `non-static field cannot be referenced from a static context`.

### Pitfall 4: `ContextLoadCountListener` Counts Cross All Surefire Forks but Writes Per-JVM

**What goes wrong:** With `forkCount=2`, two JVMs each write their own `target/test-perf/context-loads.txt` at shutdown — the second write overwrites the first. The reported count is only one fork's count.

**How to avoid:** Write to fork-unique files: `target/test-perf/context-loads-{PID}.txt` using `ProcessHandle.current().pid()` or use `target/test-perf/context-loads-{UUID}.txt`. Sum all files after the build to get the total.

**Warning signs:** Context load count appears suspiciously low (roughly half of expected).

### Pitfall 5: Removing @DirtiesContext Without All 3 Seeds Produces False Confidence

**What goes wrong:** Testing only with the default Surefire order (alphabetical) shows no failures after removing `@DirtiesContext`. But seed=5678 orders tests differently and reveals a state leak, causing a CI red on the first PR run.

**How to avoid:** D-01 mandates exactly 3 seeds (1234/5678/9999). All 3 must be green before committing the removal.

**Warning signs:** CI fails on the first run after a `@DirtiesContext` removal that passed locally with default order.

---

## Runtime State Inventory

> Phase 86 is a test infrastructure refactoring phase, not a rename/refactor/migration. No runtime state is affected. **SKIPPED** — confirmed by phase description: all changes are in `src/test/java/` and `src/test/resources/` only. No database migrations, no production code changes, no stored data affected.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Temurin) | Maven build | ✓ | 25 | — |
| Maven (./mvnw) | All test invocations | ✓ | 3.9.x (wrapper) | — |
| H2 (in-memory) | @DataJpaTest slices, dev profile | ✓ | 2.x (managed) | — |
| `time` bash builtin | Local re-baseline (D-09) | ✓ | bash built-in | `date` with arithmetic |
| GitHub Actions runner | CI wallclock source of truth (D-11) | ✓ | ubuntu-latest | — |
| `gh` CLI | Manual workflow re-trigger for 5 runs | ✓ | installed | GitHub UI fallback |

**Missing dependencies with no fallback:** None.

---

## Code Examples

### @DataJpaTest Slice — PhaseTeamRepositoryIT (converted)

```java
// Source: Spring Boot 4.0.6 @DataJpaTest documentation [ASSUMED pattern — standard Spring Boot test]
@DataJpaTest
@Import(JpaAuditingConfig.class)
@Tag("integration")
class PhaseTeamRepositoryIT {

    @Autowired PhaseTeamRepository phaseTeamRepository;
    @Autowired SeasonPhaseRepository seasonPhaseRepository;
    @Autowired SeasonRepository seasonRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired RaceScoringRepository raceScoringRepository;
    @Autowired MatchScoringRepository matchScoringRepository;
    @Autowired TestEntityManager em;

    RaceScoring rs;
    MatchScoring ms;

    @BeforeEach
    void seedScoring() {
        // Replace Flyway-seeded scoring rows with inline fixtures
        rs = raceScoringRepository.save(new RaceScoring("Test RS", /* scoring params */ ));
        ms = matchScoringRepository.save(new MatchScoring("Test MS", /* scoring params */ ));
    }

    @Test
    void givenPhaseTeams_whenFindByPhaseId_thenReturnsAll() {
        // given
        var season = seasonRepository.save(new Season("Phase58-Test-PT-S1", 9988, 1));
        var phase = seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        // ... rest unchanged
    }

    private SeasonPhase newPhase(Season season, PhaseType type, PhaseLayout layout, int sortIndex) {
        var phase = new SeasonPhase(season, type, layout, sortIndex);
        phase.setRaceScoring(rs);
        phase.setMatchScoring(ms);
        return phase;
    }
}
```

### JpaAuditingConfig (@TestConfiguration)

```java
// Source: Spring Data JPA @EnableJpaAuditing documentation [ASSUMED — confirmed pattern]
package org.ctc.domain.repository;  // co-located with repository ITs

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@TestConfiguration
@EnableJpaAuditing
class JpaAuditingConfig {
    // No beans — @EnableJpaAuditing activates AuditingEntityListener via annotation metadata
}
```

### Random-Seed Verification Invocation

```bash
# Source: TESTING.md §"Run Order for Independence Verification" [VERIFIED: TESTING.md Phase 79 D-08]
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 -Dtest=DriverProfilePageGeneratorTest
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678 -Dtest=DriverProfilePageGeneratorTest
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999 -Dtest=DriverProfilePageGeneratorTest
```

For a cluster removal (all 7 sitegen tests at once):
```bash
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 \
  -Dtest="DriverProfilePageGeneratorTest,MatchdaysPageGeneratorTest,StandingsPageGeneratorTest,DriverRankingPageGeneratorTest,TeamProfilePageGeneratorTest,SiteGeneratorE2ETest"
```

Note: `SiteGeneratorPhaseAwarenessIT` is `@Tag("integration")` → runs under Failsafe, not Surefire. Use:
```bash
./mvnw verify -Dit.test=SiteGeneratorPhaseAwarenessIT
```

### Local Re-Baseline Measurement (D-09)

```bash
# Run 3x, record Maven "Total time" and bash wall time
for i in 1 2 3; do
  echo "=== Run $i ===" && { time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev; } 2>&1 | grep -E "Total time|real|sys|user"
done
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring.factories` for autoconfiguration | `AutoConfiguration.imports` | Spring Boot 3.0 | Only affects auto-config beans; `ApplicationContextInitializer` and `ContextCustomizerFactory` still use `spring.factories` |
| Filename-based Surefire/Failsafe routing (`*IT.java`) | `@Tag`-based routing | Phase 79 | Tags are inherited by `@Nested` — eliminates double-run bug |
| `@SpringBootTest @Transactional` for all repository tests | `@DataJpaTest` for pure repository tests | Phase 86 (pilot) | Faster context startup; no full app context loaded |
| `surefire.runOrder` default (alphabetical) | `surefire.runOrder=random` with explicit seeds | Phase 79 | Detects ordering-dependent failures before they hit CI |

**Deprecated/outdated:**
- Filename-based `<includes>**/*IT.java</includes>` in Surefire: removed in Phase 79, replaced by `@Tag`.
- `@DirtiesContext` as a blanket pattern for shared-state tests: Phase 86 audits and reduces usage.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | D-03 `ImportLockService.unlock()` reset-only approach replaces `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` on all 3 backup ITs without touching `BlockingRestoreFailureInjector` | Cluster B analysis, Pattern 2 | Tests fail with `CountDownLatch` at 0 on second/third method; `@DirtiesContext` must be retained on those ITs |
| A2 | `@DataJpaTest` `@Import(JpaAuditingConfig.class)` is sufficient to activate `AuditingEntityListener` for `BaseEntity.createdAt`/`updatedAt` | Pattern 3, Code Examples | Auditing timestamps remain `null` in slice tests; assertions that check `createdAt != null` fail |
| A3 | Replacing `raceScoringRepository.findAll().get(0)` with an inline `@BeforeEach` fixture is sufficient for the `@DataJpaTest` pilot — no other Flyway-seeded data is needed by the 3 Phase repository ITs | Pattern 3 | Additional Flyway-seeded rows are needed; must re-enable Flyway via `@TestPropertySource` (Option A) |
| A4 | `@DynamicPropertySource` with `static @TempDir` creates a distinct context cache key, resulting in 7 separate Spring contexts for the 7 sitegen test classes (vs. the current 1 shared + 7 evictions) | Pattern 1 | Context count increases rather than decreases; net wallclock may be worse if 7 separate startups cost more than 7 evictions |
| A5 | Removing `TestDataServiceIntegrationTest`'s `@DirtiesContext` after the sitegen cluster fixes is safe | Cluster C analysis | Test still sees dirty H2 state from `SiteGeneratorPhaseAwarenessIT`'s Flyway reset under some random orderings; `@DirtiesContext` must be restored |

**If this table is empty:** Not applicable — 5 assumptions identified that need runtime verification via the D-01 random-seed protocol.

---

## Open Questions

1. **Can the 3 backup ITs truly remove `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` with only `ImportLockService` reset?**
   - What we know: The latches are non-resettable; the `ImportLockService.ReentrantLock` is resettable.
   - What's unclear: Whether any test method in `ImportLockBannerAdviceIT` or `ImportLockedPostRejectorIT` does NOT use the latch-countdown handshake. If such methods exist, they could be moved to a separate IT that doesn't import `BlockingRestoreFailureInjector.Config`.
   - Recommendation: Read each test method body in all 3 ITs (planner task), classify latch-dependent vs. latch-free methods, determine if splitting is feasible.

2. **Will the sitegen `@DynamicPropertySource` fix actually reduce wallclock or increase it?**
   - What we know: 7 class-level `@DirtiesContext` currently cause 7 forced context evictions. With `@DynamicPropertySource`, each class gets a distinct context key → potentially 7 separate context startups at class load time instead of 7 evictions mid-run.
   - What's unclear: Whether the Spring TCF cache can still reuse a context across sitegen test classes if they all bind to `@TempDir` paths (which are unique per class). If each class creates a unique path, each gets a unique context.
   - Recommendation: Measure the context count pre- and post-fix using `ContextLoadCountListener`. If context count increases after the fix, the wallclock may not improve. Consider the alternative (cleanup `@AfterAll` with `siteProperties.setOutputDir("target/site")` reset) which preserves context sharing.

3. **What is the actual Phase 86 re-baseline (D-09)?**
   - What we know: v1.10 baseline was 11m 11s. Phases 80-85 added SpotBugs, CodeQL compile step (CI-only, not in `./mvnw verify`), and OpenRewrite (CI-neutral). Phase 83 added a few new tests.
   - What's unclear: Exact current wallclock on the same hardware.
   - Recommendation: First task in Wave 1 is the re-baseline run (D-09). This must precede any optimizations.

---

## Project Constraints (from CLAUDE.md)

- **Test Coverage Minimum:** 82% line coverage must be maintained. `@DataJpaTest` conversions may affect coverage slightly (slices exclude production beans from coverage instrumentation differently). Verify with `./mvnw verify` after each conversion.
- **Flyway:** Do not change existing V1 migration. The `@DataJpaTest` Option B (inline fixtures in `@BeforeEach`) is required — do not re-enable Flyway migrations in slice tests if doing so requires changing the V1 migration.
- **No Flyway V1 changes:** `@DataJpaTest` must self-seed required lookup rows (RaceScoring, MatchScoring) in `@BeforeEach`. The V1 migration file must remain untouched.
- **Test Invocation Discipline:** One final `./mvnw verify -Pe2e` per phase. Targeted invocations (`-Dtest=`, `-Dit.test=`) between waves (TESTING.md §"Test Invocation Discipline").
- **Tag Tests by Category:** `@DataJpaTest` ITs keep `@Tag("integration")` so Failsafe `default-it` picks them up. No `*IT.java` may be untagged.
- **OSIV Remains Enabled:** No changes to `spring.jpa.open-in-view=true`. `@DataJpaTest` tests run against H2 with the default Hibernate configuration; OSIV does not apply in test scope.
- **TDD Sequence:** Unit Tests → Implementation → Integration Tests → E2E Tests. For Phase 86 (infrastructure refactoring), the sequence is: verify-remove → commit → targeted test run → repeat.
- **Conventional Commits:** All commit messages must follow the `test(...):`/`refactor(...):`/`docs(...):`/`perf(...):`/`feat(...):`/`fix(...):`/`chore(...):`/`ci(...):`  prefixes. Phase 86 commits will primarily use `test:` and `perf:` prefixes.
- **Branch Protection:** All work on `gsd/v1.11-tooling-and-cleanup`. No direct pushes to master.
- **Backward Compatibility:** No breaking changes to existing URLs/endpoints. Phase 86 is test-only — no endpoint changes.
- **SpotBugs gate:** `./mvnw verify` runs SpotBugs. New `@TestConfiguration` and `ContextLoadCountListener` classes must pass SpotBugs. Place `ContextLoadCountListener` in `src/test/java/` (test scope — excluded from production SpotBugs scan).

---

## Validation Architecture

> `nyquist_validation: true` in `.planning/config.json` — section is REQUIRED.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) 5.x, Maven Surefire 3.5.5, Failsafe (managed by Spring Boot 4.0.6 parent) |
| Config file | `pom.xml` (Surefire lines 266-279, Failsafe lines 290-309) |
| Quick run command | `./mvnw test -Dtest=PhaseTeamRepositoryIT` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PERF-01 | All `@DirtiesContext` sites either removed or annotated with explanatory comment | Automated audit (grep) + random-order Surefire | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` (x3 seeds) | ✅ Existing tests; audit is a process step |
| PERF-01 | No ordering-sensitive test failures after annotation removal | Integration (random-seed runs) | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678` | ✅ Existing tests |
| PERF-02 | `target/test-perf/context-loads.txt` is written after `./mvnw verify -Pe2e` | Smoke assertion | `ls target/test-perf/context-loads.txt && cat target/test-perf/context-loads.txt` | ❌ Wave 0 gap — `ContextLoadCountListener` must be created |
| PERF-02 | `docs/test-performance.md` contains pre/post ApplicationContext counts | Manual verification | `grep "context.*load\|ApplicationContext" docs/test-performance.md` | ❌ Wave 0 gap — `docs/test-performance.md` must be created |
| PERF-03 | `PhaseTeamRepositoryIT` is annotated `@DataJpaTest` (not `@SpringBootTest`) | Static check | `grep "@DataJpaTest" src/test/java/org/ctc/domain/repository/PhaseTeamRepositoryIT.java` | ✅ File exists; annotation will be replaced |
| PERF-03 | `PhaseTeamRepositoryIT` all existing test methods still pass under `@DataJpaTest` | Integration | `./mvnw verify -Dit.test=PhaseTeamRepositoryIT` | ✅ File exists |
| PERF-03 | JaCoCo coverage remains ≥82% after `@DataJpaTest` conversion | Integration gate | `./mvnw verify` (coverage enforced in verify phase) | ✅ Existing gate |
| PERF-04 | `docs/test-performance.md` contains wallclock measurement (local + CI) | Manual verification | `grep "wallclock\|Total time\|baseline" docs/test-performance.md` | ❌ Wave 0 gap — file must be created |
| PERF-04 | If ≥30% not reached: v1.12 top-3 levers table present in `docs/test-performance.md` | Manual verification | `grep "v1.12\|lever" docs/test-performance.md` | ❌ Wave 0 gap |
| PERF-05 | CI GitHub Actions wallclock median from 5 runs recorded in `docs/test-performance.md` | CI observation | N/A (manual harvest from Actions logs) | ❌ Requires 5 CI runs |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=<AffectedTestClass>` or `./mvnw verify -Dit.test=<AffectedITClass>`
- **Per wave merge:** `./mvnw verify` (no E2E, for fast iteration)
- **Phase gate:** `./mvnw verify -Pe2e` (full suite, once at phase close per TESTING.md discipline)

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — covers PERF-02 (context load counting)
- [ ] `src/test/resources/META-INF/spring.factories` — registers `ContextLoadCountListener`
- [ ] `src/test/java/org/ctc/domain/repository/JpaAuditingConfig.java` — covers PERF-03 (@DataJpaTest auditing)
- [ ] `docs/test-performance.md` — covers PERF-02, PERF-04, PERF-05 (baseline + results document)

*(All four are new files to be created in Wave 1 of Phase 86, not pre-existing infrastructure gaps.)*

---

## Security Domain

> `security_enforcement` is absent from config (defaults to enabled). However, Phase 86 makes no changes to production code, endpoints, or security configuration — it is exclusively test infrastructure refactoring. No ASVS categories apply.

**Applicable ASVS Categories:**

| ASVS Category | Applies | Rationale |
|---------------|---------|-----------|
| V2 Authentication | No | No auth changes |
| V3 Session Management | No | No session changes |
| V4 Access Control | No | No access changes |
| V5 Input Validation | No | No input handling changes |
| V6 Cryptography | No | No crypto changes |

**Threat patterns:** None applicable — test infrastructure changes only. The `ContextLoadCountListener` writes to `target/` (gitignored, build output) — no filesystem security concern.

---

## Sources

### Primary (HIGH confidence)

- `src/test/java/**/*.java` — all 11 `@DirtiesContext` sites enumerated directly from source [VERIFIED: grep on source tree]
- `pom.xml` — Surefire config `forkCount=2`, `reuseForks=true`, Failsafe `groups=integration`, `excludedGroups=e2e,flaky` [VERIFIED: read directly]
- `src/main/java/org/ctc/backup/lock/ImportLockService.java` — `ReentrantLock` singleton; `isHeldByCurrentThread()` + idempotent `unlock()` [VERIFIED: read directly]
- `src/main/java/org/ctc/sitegen/SiteProperties.java` — `@ConfigurationProperties(prefix = "ctc.site")` with mutable `outputDir` [VERIFIED: read directly]
- `application-dev.yml` — `ctc.site.output-dir: target/site`, `jdbc:h2:mem:ctcdb;DB_CLOSE_DELAY=-1` [VERIFIED: read directly]
- `~/.m2/repository/org/springframework/boot/spring-boot-test-autoconfigure/4.0.6/spring-boot-test-autoconfigure-4.0.6.jar` — `spring.factories` key `ContextCustomizerFactory` (NOT `ApplicationContextInitializer`) [VERIFIED: jar extracted on disk]
- `~/.m2/repository/org/springframework/boot/spring-boot-test/4.0.6/spring-boot-test-4.0.6.jar` — `spring.factories` key `org.springframework.context.ApplicationContextInitializer` still present [VERIFIED: jar extracted on disk]
- `.planning/codebase/TESTING.md` — Tag categorization, Surefire routing, random-seed invocations, D-08 discipline [VERIFIED: read directly]
- `.planning/phases/86-test-wallclock-reduction/86-CONTEXT.md` — D-01 through D-16 locked decisions [VERIFIED: read directly]

### Secondary (MEDIUM confidence)

- Maven Surefire 3.5.5 installed at `~/.m2/repository/org/apache/maven/plugins/maven-surefire-plugin/3.5.5/` — confirms the version in use; `-Dsurefire.runOrder.random.seed` flag confirmed by usage in TESTING.md Phase 79 D-08 section [VERIFIED: jar present on disk; documented usage in TESTING.md]

### Tertiary (LOW confidence)

- `@DataJpaTest` + `@Import(JpaAuditingConfig.class)` pattern for `@EnableJpaAuditing` re-activation [ASSUMED — standard Spring Data JPA test pattern; not verified against official Spring Boot 4.0.6 test-autoconfigure sources]
- `@DynamicPropertySource` with `static @TempDir` for sitegen outputDir isolation — context cache key implications [ASSUMED — standard Spring Framework 6.x pattern; needs runtime verification for the specific 7-class context-count tradeoff]

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — no new dependencies; all tooling already in use
- Architecture (DirtiesContext clusters): HIGH — all 11 sites verified from source; root causes confirmed by reading implementation code
- DataJpaTest conversion: MEDIUM — pattern is standard but Flyway/scoring-row interaction and `JpaAuditingConfig` effectiveness are [ASSUMED]
- Sitegen @DynamicPropertySource fix: MEDIUM — correct pattern but context-count impact on wallclock is unknown until measured (D-09 re-baseline)
- Backup IT BEFORE_EACH_TEST_METHOD removal: LOW — D-03 claim about reset-bean replacing `@DirtiesContext` may not apply to `CountDownLatch`-dependent tests; flagged as A1 assumption
- CI wallclock measurement: HIGH — `ci.yml` structure confirmed; `workflow_dispatch` re-run approach is straightforward

**Research date:** 2026-05-17
**Valid until:** 2026-06-17 (30 days — Spring Boot 4.x is stable; no major API changes expected)
