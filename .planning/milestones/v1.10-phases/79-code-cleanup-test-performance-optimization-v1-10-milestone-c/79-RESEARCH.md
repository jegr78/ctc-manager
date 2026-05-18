# Phase 79: Code Cleanup + Test Performance Optimization (v1.10 Milestone Closer) — Research

**Researched:** 2026-05-15
**Domain:** Maven Surefire/Failsafe parallelization, Spring Test Context Caching, GitHub Actions concurrency, JUnit 5 tagging, code cleanup patterns
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Cleanup Scope (D-1.x)**
- D-01: Sweep covers full `src/main/java/` AND `src/test/java/` — NOT limited to `org.ctc.backup`.
- D-02: All four cleanup classes allowed: Comment-Thinning, Dead-Code-Removal, Extract-Method + Rename (>50 LOC), Logik-Vereinfachung.
- D-03: Per-package atomic commits. After each commit: `./mvnw test`.
- D-04: Dead-code removal only with safe indicators (grep zero refs + no Spring/JPA/Jackson lifecycle annotations).
- D-20: `pom.xml` + `.github/workflows/*.yml` also in cleanup scope.

**Test Performance (D-2.x)**
- D-05: Test-Independence-Audit FIRST (reverse-order run + isolation), THEN parallelization. JUnit 5 `@Execution(CONCURRENT)` is REJECTED (D-2.1).
- D-06: Success criterion ≥ 30% wallclock reduction, measured baseline + final in `79-AUTO-UAT.md`.
- D-07: CI hygiene: `concurrency` group, `--no-transfer-progress`, `-fae` triage, path-filter review on `mariadb-migration-smoke.yml`, flaky-test quarantine.
- D-08: Codify test-invocation discipline into `TESTING.md`.

**Comment Thinning (D-3.x)**
- D-09: Delete Phase-N references, was-comments, tombstones, boilerplate Javadoc.
- D-10: Keep MariaDB/H2 quirks, workaround/pitfall warnings, meaningful public-API Javadoc.
- D-11: Test-code keeps `// given` / `// when` / `// then` BDD comments.
- D-12: Class-level Javadoc condensed to 1-3 lines.
- D-13: Grep-Schutzwortliste: `MariaDB`, `H2`, `JEP`, `CVE`, `race`, `thread-safe`, `TODO`, `HACK`, `WORKAROUND`, `FIXME`, `deadlock`, `OSIV`, `Lombok`, `Unsafe`, `transitiv`, `transitive`, `pitfall`, `auto-commit`, `auditing`, `AuditingEntityListener`.

**Milestone Closure (D-4.x)**
- D-14 through D-17: `/gsd-audit-milestone v1.10` + `/gsd-complete-milestone v1.10` run inside Phase 79. Hard-stop on findings. One Squash-PR `chore(79): v1.10 milestone closer`.

**Verification**
- D-18: JaCoCo line coverage ≥ 0.82 (stays at 0.82, no raise).
- D-19: Final gate `./mvnw verify -Pe2e` BUILD SUCCESS.

### Claude's Discretion
- CD-01: Per-package commit ordering (alphabetical default, tunable).
- CD-02: Extract-method threshold (>50 LOC locked; discretion to extract >30 LOC when readability clearly improves).
- CD-03: Logic-Vereinfachung pattern catalog.
- CD-04: `@DirtiesContext`-Audit verdicts per annotation.
- CD-05: Flaky-test quarantine list (max 5 cap).
- CD-06: `ci.yml` concurrency-group placement (workflow-level vs. job-level).
- CD-07: Plan-SUMMARY frontmatter sweep mechanics.

### Deferred Ideas (OUT OF SCOPE)
- `pom.xml` `<version>1.8.0-SNAPSHOT</version>` bump.
- JaCoCo minimum raise above 0.82.
- JUnit 5 `@Execution(CONCURRENT)`.
- v1.9 carry-overs other than Plan-SUMMARY frontmatter sweep.
- Backup-feature extensions.
- Templates / CSS / HTML cleanup.
- GSD-orchestrator changes.
</user_constraints>

---

## Summary

Phase 79 combines four streams: full-codebase code cleanup (Java + config), test-performance optimization via process-level parallelism, CI workflow hygiene, and milestone closure. The technical challenge is the intersection of JaCoCo agent instrumentation with Surefire's forked-JVM mode — this is the historically-painful integration point that has caused many failed parallelization attempts in Spring Boot projects.

The current test suite has 153 unit test files, 47 IT files, and is entirely greenfield for parallelism: no `forkCount`, `parallel`, or `reuseForks` is configured anywhere in `pom.xml`. Surefire/Failsafe versions are 3.5.5 (managed by Spring Boot 4.0.6 parent). The JaCoCo `@{argLine}` late-property-evaluation pattern is already in place and is the correct propagation mechanism for forked JVMs.

The `@DirtiesContext` audit reveals 10 genuine annotations: 3 are mandatory (ImportConcurrentLockIT, ImportLockBannerAdviceIT, ImportLockedPostRejectorIT — non-resettable `CountDownLatch` singletons require fresh context per method), and 7 are in `org.ctc.sitegen` (all use `siteProperties.setOutputDir(tempDir)` which mutates a `@ConfigurationProperties` singleton — these are also mandatory because `SiteProperties` is a mutable singleton bean).

**Primary recommendation:** Add `<forkCount>2C</forkCount><reuseForks>true</reuseForks>` to Surefire only after the independence audit passes (reverse-order run green). Keep Failsafe at `forkCount=1` or `1C` due to Testcontainers contention risk.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Surefire parallelism config | Build / Maven | — | Plugin-level config, no runtime impact |
| JaCoCo argLine propagation | Build / Maven | — | JVM agent injected by `prepare-agent` goal, consumed by forked JVM argLine |
| Spring Test Context Cache | JVM / Test Runtime | — | Static context map, shared across same-JVM test classes |
| `@DirtiesContext` eviction | JVM / Test Runtime | — | Dirties cache key, forces context rebuild |
| CI concurrency group | GitHub Actions | — | Workflow-level YAML, no Maven involvement |
| Flaky-test quarantine | Build / Maven | JUnit 5 | `@Tag("flaky")` + Surefire `<excludedGroups>` |
| Wallclock baseline | Local CLI / CI | `79-AUTO-UAT.md` | Measured with `time ./mvnw verify` |

---

## Standard Stack

### Core (verified for this phase)

| Library / Tool | Version | Purpose | Source |
|----------------|---------|---------|--------|
| Maven Surefire Plugin | 3.5.5 | Unit test execution, forking | [VERIFIED: Spring Boot 4.0.6 BOM] |
| Maven Failsafe Plugin | 3.5.5 | IT execution, forking | [VERIFIED: Spring Boot 4.0.6 BOM] |
| JaCoCo Maven Plugin | 0.8.14 | Coverage + `prepare-agent` goal | [VERIFIED: pom.xml] |
| Mockito Core | 5.20.0 | Mock framework; `-javaagent` in argLine | [VERIFIED: Spring Boot 4.0.6 BOM] |
| JUnit 5 (Jupiter) | managed by SB 4.0.6 | Test framework, `@Tag` annotation | [VERIFIED: Spring Boot 4.0.6 BOM] |

### No New Dependencies Required

Phase 79 uses only built-in Maven plugin features. No new plugins or libraries need to be added. [VERIFIED: all capabilities (forkCount, runOrder, excludedGroups, concurrency) are built into the already-present plugin versions]

---

## Architecture Patterns

### 1. Surefire Parallel Configuration with JaCoCo Propagation

**Verified pattern** for `@{argLine}` late evaluation with forked JVMs:

The JaCoCo `prepare-agent` goal sets the Maven property `argLine` to something like `-javaagent:/path/to/jacocoagent.jar=...`. Surefire must consume this via `@{argLine}` (not `${argLine}`). The `@{...}` syntax is Surefire's "late property evaluation" — it resolves the property after `prepare-agent` has run, ensuring the JaCoCo agent path is always present in the forked JVM's command line.

[CITED: https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html] [CITED: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html]

**Concrete configuration block for `pom.xml` (Surefire, replace existing `<configuration>`):**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->
        <!-- Mockito inline-agent: required for Mockito 5.x subclass mocking in forked JVMs -->
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
        <!-- forkCount=2C: 2 forked JVMs per CPU core. reuseForks=true: each JVM processes multiple
             test classes sequentially (saves JVM startup overhead vs. reuseForks=false).
             Process-level parallelism is safe for Mockito + JUnit 5 unit tests. -->
        <forkCount>2C</forkCount>
        <reuseForks>true</reuseForks>
        <excludes>
            <exclude>**/e2e/**</exclude>
            <exclude>**/*IT.java</exclude>
        </excludes>
    </configuration>
</plugin>
```

**Why `reuseForks=true`:** With `reuseForks=false`, each test class gets a new JVM (maximum isolation, 10-20x JVM startup overhead). With `reuseForks=true`, each fork processes multiple classes before dying. For ~150 unit test classes across 2C forks on a 10-core machine, this means each fork processes ~7-8 classes. JVM startup overhead is the dominant cost for fast unit tests; `reuseForks=true` is the standard recommendation. [CITED: https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html]

**Concrete configuration block for `pom.xml` (Failsafe `default-it` execution, replace existing `<configuration>`):**

```xml
<execution>
    <id>default-it</id>
    <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
    </goals>
    <configuration>
        <!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->
        <!-- Mockito inline-agent: required for Mockito 5.x subclass mocking in forked JVMs -->
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
        <!-- forkCount=1C: 1 forked JVM per CPU core. Conservative vs. Surefire's 2C
             because each IT boots a Spring context (~3-5 seconds startup).
             Testcontainers MariaDB ITs (BackupImportMariaDbSmokeIT) use dynamic ports
             via @DynamicPropertySource — safe from port collision.
             The 3 ImportLock ITs use @DirtiesContext(BEFORE_EACH) to reset CountDownLatch
             state — context rebuilds in the same fork are safe. -->
        <forkCount>1C</forkCount>
        <reuseForks>true</reuseForks>
        <includes>
            <include>**/*IT.java</include>
        </includes>
        <excludes>
            <exclude>**/e2e/**</exclude>
        </excludes>
    </configuration>
</execution>
```

**Failsafe `e2e-it` execution** (in `<profile id="e2e">`) keeps `forkCount` unset (defaults to 1) because Playwright E2E tests require exactly one Spring context per port — adding forks here would require multiple ports and complex test setup. Leave as-is.

**Risk: `forkCount=1C` for Failsafe vs. port conflicts.** IT classes using `@SpringBootTest(webEnvironment = RANDOM_PORT)` (BackupImportMultipartLimitIT, PlaywrightConfig base class) each get their own random port. When multiple forks run in parallel, each fork boots its own Spring context with its own random port — no conflicts. The `forkCount=1C` limit means at most 1 IT JVM per CPU core, preventing resource starvation. [VERIFIED: pom.xml inspection — only 2 classes use RANDOM_PORT: BackupImportMultipartLimitIT + PlaywrightConfig]

### 2. argLine Propagation Pattern

**Critical invariant:** `@{argLine}` (at-sign, late evaluation) MUST be used instead of `${argLine}` (dollar-sign, early evaluation). JaCoCo's `prepare-agent` goal runs in the `initialize` phase, before Surefire's `test` phase — but Maven property interpolation for `${}` happens at parse-time, before any plugin executes. The `@{}` syntax tells Surefire to resolve the property at execution time, after `prepare-agent` has set it.

If `${argLine}` is used, the JaCoCo agent is missing from the forked JVM's command line, and coverage data is not captured. This is the most common JaCoCo + Surefire misconfiguration. [CITED: https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html]

The current `pom.xml` already uses `@{argLine}` correctly on all three argLine entries (lines 265, 292, 412). The `forkCount` additions do not change the argLine syntax — they are independent configuration elements.

### 3. `forkCount` Syntax Reference

| Value | Meaning | Example (10-core machine) |
|-------|---------|--------------------------|
| `1` | 1 fork total | 1 JVM |
| `2` | 2 forks total | 2 JVMs |
| `1C` | 1 × CPU cores | 10 JVMs |
| `2C` | 2 × CPU cores | 20 JVMs |
| `2.5C` | 2.5 × CPU cores | 25 JVMs |

[CITED: https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html]

For CI (GitHub Actions, typically 2 CPU cores): `2C` = 4 Surefire JVMs, `1C` = 2 Failsafe JVMs. This is safe and reasonable.

### 4. `runOrder` for Independence Audit

Surefire supports `<runOrder>` with the following values: `filesystem` (default), `alphabetical`, `reversealphabetical`, `random`, `hourly`, `failedfirst`, `balanced`. [VERIFIED: Context7, Maven Surefire docs]

**For test-independence detection:**

```xml
<!-- Temporary: add to Surefire <configuration> during Wave 1 audit only -->
<runOrder>reversealphabetical</runOrder>
```

Or via command line (no pom.xml change needed):
```bash
./mvnw test -Dsurefire.runOrder=reversealphabetical
```

For random-seed runs to detect order-dependence:
```bash
# Run 3 times with different random seeds
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999
```

Failsafe IT independence test:
```bash
./mvnw verify -Dsurefire.runOrder=reversealphabetical -Dfailsafe.runOrder=reversealphabetical
```

[CITED: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html]

### 5. `@DirtiesContext` Audit Decision Tree

**Verified facts from code inspection:**

There are exactly 10 actual `@DirtiesContext` annotations (non-comment lines):

| Class | Annotation | Rationale | Verdict |
|-------|-----------|-----------|---------|
| `ImportConcurrentLockIT` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | `BlockingRestoreFailureInjector.Config` exposes non-resettable `CountDownLatch` singletons. Context cache key shared with the other two lock ITs. Latch state cannot be reset — new context is the only solution. | **KEEP — mandatory** |
| `ImportLockBannerAdviceIT` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | Same rationale as above. Comment in source explains it explicitly. | **KEEP — mandatory** |
| `ImportLockedPostRejectorIT` | `@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)` | Same rationale as above. | **KEEP — mandatory** |
| `DriverProfilePageGeneratorTest` | `@DirtiesContext` | Uses `siteProperties.setOutputDir(tempDir.toString())` in `@BeforeAll`. `SiteProperties` is a `@ConfigurationProperties` singleton bean — `setOutputDir` mutates the shared singleton. Without `@DirtiesContext`, subsequent test classes sharing the context see the mutated `outputDir`. | **KEEP — mandatory** |
| `DriverRankingPageGeneratorTest` | `@DirtiesContext` | Same pattern: `siteProperties.setOutputDir(tempDir.toString())`. | **KEEP — mandatory** |
| `MatchdaysPageGeneratorTest` | `@DirtiesContext` | Same pattern. | **KEEP — mandatory** |
| `SiteGeneratorE2ETest` | `@DirtiesContext` | Uses `siteGeneratorService.setOutputDir(tempDir.toString())` and `siteProperties.setLinks(...)`. Multiple singleton mutations. | **KEEP — mandatory** |
| `SiteGeneratorPhaseAwarenessIT` | `@DirtiesContext` | Same pattern: `siteProperties.setOutputDir(tempDir.toString())`. | **KEEP — mandatory** |
| `StandingsPageGeneratorTest` | `@DirtiesContext` | Same pattern. | **KEEP — mandatory** |
| `TeamProfilePageGeneratorTest` | `@DirtiesContext` | Same pattern. | **KEEP — mandatory** |

**Decision tree for auditing additional `@DirtiesContext` candidates:**

```
Is @DirtiesContext present?
├── YES: Does the test mutate a Spring-managed singleton bean?
│   ├── YES (ConfigurationProperties setter, setX() on @Component) → KEEP
│   ├── YES (non-resettable shared state, e.g., CountDownLatch, AtomicBoolean) → KEEP  
│   ├── YES (@MockBean that replaces a bean definition, not just behavior) → KEEP
│   ├── NO: Is @Transactional present on the test class/method?
│   │   ├── YES → REMOVE (rollback handles DB isolation; context is clean)
│   │   └── NO: Does the test delete/modify rows without rollback?
│   │       ├── YES → Assess: can @Transactional be added? If yes → replace with @Transactional, REMOVE @DirtiesContext
│   │       └── NO → REMOVE (defensive cargo, no actual dirtying)
└── NO → nothing to do
```

**Key insight for sitegen tests:** The 7 sitegen `@DirtiesContext` annotations cannot be removed by simply adding `@Transactional` because the mutation is a filesystem-path property change, not a database mutation. A better long-term fix would be to inject the output directory as a test-scoped `@TestPropertySource` override, but that changes the test structure and is out of Phase 79 scope (behavior-preserving cleanup only). **These 7 annotations stay.** [VERIFIED: code inspection]

**Summary verdict: All 10 `@DirtiesContext` annotations are mandatory and must be kept.** CD-04 discretion area is resolved: zero removals. This is a positive finding — it means the 119 `@SpringBootTest` annotation count is not inflated by defensive `@DirtiesContext` misuse.

### 6. Spring Test Context Cache Key

Spring caches `ApplicationContext` instances keyed on the unique combination of: `contextClass`, `locations/classes/value`, `activeProfiles`, `propertySourceLocations`, `propertySourceProperties`, `contextInitializerClasses`, and bean overrides (`@MockitoBean`, `@MockBean`).

**Current context pool analysis (from code inspection):**
- ~85 tests: `@SpringBootTest @ActiveProfiles("dev")` — share the same cached context
- ~5 tests: `@SpringBootTest(classes = CtcManagerApplication.class)` — separate cache entry (migration tests)
- 3 tests: `@SpringBootTest(properties = {...}) @ActiveProfiles("prod")` — separate entry (security ITs)
- 2 tests: `@SpringBootTest(webEnvironment = RANDOM_PORT) @ActiveProfiles("dev")` — separate entry
- 3 tests: `@SpringBootTest @Import(BlockingRestoreFailureInjector.Config.class) @TestPropertySource(...)` — separate entry (lock ITs)
- 21 tests with `@MockitoBean`: each unique combination of mock types creates a separate cache key

**Important Spring Boot 4.x / Spring 7.x change:** `@MockitoBean` (new API replacing `@MockBean`) resets mocks between tests but does NOT invalidate the context cache. The context is shared and the mock is reset to `Mockito.reset()` state before each test method. This is safe for parallelization at process level. [CITED: Context7, Spring Framework docs]

### 7. CI Concurrency Group Configuration

**Verified GitHub Actions syntax** for workflow-level concurrency: [CITED: https://docs.github.com/en/actions/using-jobs/using-concurrency]

**Placement decision (CD-06):** Workflow-level concurrency cancels ALL jobs in the workflow when a new push arrives. This is the correct choice for `ci.yml` because:
- `build-and-test` + `dockerfile-noble-pin-guard` + `docker-build` are a logical unit — partial cancellation mid-workflow would leave artifacts in an undefined state.
- `cancel-in-progress: true` cancels the running workflow run for the same branch/ref when a new push arrives, freeing CI minutes for the most-recent commit.

**Block to add to `ci.yml` after the `name:` and `on:` blocks, before `permissions:`:**

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

**Note on `github.ref` vs `github.workflow + github.ref`:** Using `${{ github.ref }}` alone as the group key would conflict between different workflows that have the same branch ref. The `${{ github.workflow }}-${{ github.ref }}` pattern scopes the group to this specific workflow + branch. [ASSUMED — based on GitHub Actions documentation patterns; verify against project's specific workflow naming if multiple workflows share a branch]

**Interaction with required-status-checks:** If `build-and-test` is configured as a required status check in GitHub branch protection rules, a cancelled workflow run reports as "cancelled" (not "failed"). GitHub branch protection treats "cancelled" differently from "failed" — a cancelled required check does NOT block the PR, it simply removes the check from the PR's required list until a new run completes. This is the desired behavior: new push cancels old run, new run provides fresh status. [ASSUMED — standard GitHub Actions behavior for required status checks with cancel-in-progress; verify in project's repo settings if strict protection rules apply]

**Workflow-level placement in ci.yml:**
```yaml
name: CI

on:
  push:
    branches: [ master, main ]
  pull_request:
    branches: [ master, main ]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read
  pull-requests: write

jobs:
  ...
```

### 8. Flaky-Test Quarantine Mechanism

**JUnit 5 `@Tag` + Surefire `<excludedGroups>` — verified configuration:**

[CITED: https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html]
[CITED: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html]

**Usage on a flaky test:**
```java
@Test
@Tag("flaky")
void givenConcurrentRequests_whenTimingDependent_thenMayFail() {
    // ...
}
```

**Surefire configuration (default build excludes `flaky` tagged tests):**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludedGroups>flaky</excludedGroups>
        <!-- ... rest of config -->
    </configuration>
</plugin>
```

**Failsafe configuration (IT build also excludes `flaky` tagged tests):**
```xml
<configuration>
    <excludedGroups>flaky</excludedGroups>
    <!-- ... rest of config -->
</configuration>
```

**On-demand quarantine run (re-includes flaky tests):**
```bash
./mvnw verify -Dsurefire.excludedGroups="" -Dfailsafe.excludedGroups=""
# Or: explicitly include flaky only
./mvnw test -Dgroups=flaky
```

**Coverage impact of excluded tests:** JaCoCo measures coverage based on which lines are executed during test runs. If a test tagged `@Tag("flaky")` exercises production code that no other test covers, excluding it reduces the line coverage percentage. The JaCoCo denominator (total lines) does not change — only the numerator (covered lines) decreases. This is relevant for D-18: if quarantined tests cover unique code paths, the 0.82 threshold may be at risk. The plan must verify coverage before and after adding `<excludedGroups>flaky</excludedGroups>`. [VERIFIED: JaCoCo docs — instrumentation is coverage-of-executed-lines, not coverage-of-test-presence]

**Current flaky-test candidates identified (from timing-sensitive code inspection):**

The 3 ImportLock ITs use `CountDownLatch.await(10, TimeUnit.SECONDS)` with `@DirtiesContext(BEFORE_EACH_TEST_METHOD)`. They rebuild the Spring context before each method and rely on a blocking injector. These are high-risk for occasional timeouts on slow CI runners. CD-05 applies: if audit finds >5 candidates, prioritize the 5 most timing-sensitive.

No tests are currently tagged `@Tag("flaky")` — this mechanism is being introduced fresh. [VERIFIED: grep for `@Tag` across test files shows only `@Tag("e2e")` on `BackupImportE2ETest`]

### 9. Wallclock Baseline Measurement

**Reliable measurement approach:**

```bash
# Step 1: Baseline (run once, record result in 79-AUTO-UAT.md)
./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true
# Prepend: time ./mvnw clean verify ...
# Output: real Xm Ys

# Step 2: Final measurement (after all D-05 optimizations applied)
time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true
```

**`--no-transfer-progress`:** Suppresses Maven's download progress output. This is already in D-07 scope. It does not affect timing but cleans logs. [VERIFIED: Maven docs flag exists in Maven 3.6.1+]

**Why `clean`:** Without `clean`, incremental compilation skips work. Baseline must use `clean` to ensure reproducibility. Both baseline and final measurements use `clean`.

**What to time:** `./mvnw clean verify -Pe2e` (full, including Playwright E2E). D-06 specifies this explicitly. The `--offline` flag is NOT recommended here because it skips dependency resolution checks that may affect timing in unexpected ways in CI — instead, `--no-transfer-progress` keeps download noise without impacting actual timing.

**Output format for `79-AUTO-UAT.md`:**

```markdown
## Wallclock Baseline

| Measurement | Git SHA | Invocation | Duration | Date |
|-------------|---------|-----------|----------|------|
| Baseline (before D-05) | `abc1234` | `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true` | `8m 34s` | 2026-05-15 |
| Final (after D-05) | `def5678` | `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev -Ddocker.available=true` | `5m 12s` | 2026-05-15 |

**Reduction: 39% — MEETS ≥30% D-06 threshold** (or: **DOES NOT MEET — retry with tuned forkCount**)
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Forked JVM test parallelism | Custom fork scripts | `forkCount` + `reuseForks` in Surefire/Failsafe | Built-in, Maven lifecycle aware |
| JaCoCo agent propagation | Manual `-javaagent` path resolution | `@{argLine}` late evaluation pattern | Handles JaCoCo agent path automatically |
| Test tag filtering | Custom test runner | `<excludedGroups>flaky</excludedGroups>` | JUnit 5 Platform Provider built-in |
| Test order randomization | Custom test sorter | `surefire.runOrder=random` + seed | Surefire built-in |
| CI concurrency management | Custom lock/queue | GitHub Actions `concurrency:` block | Native GHA feature, zero-overhead |

---

## Common Pitfalls

### Pitfall 1: Using `${argLine}` instead of `@{argLine}` with JaCoCo

**What goes wrong:** JaCoCo agent is not injected into the forked JVM. Coverage data is either lost or JaCoCo reports 0% for all classes run in forks.
**Why it happens:** `${argLine}` resolves at parse-time (before `prepare-agent` runs). `@{argLine}` resolves at execution-time (after `prepare-agent` has set the property).
**How to avoid:** The existing `pom.xml` already uses `@{argLine}` correctly — do not change this syntax when adding `forkCount`.
**Warning signs:** JaCoCo coverage drops sharply after adding forkCount; `target/jacoco.exec` is small or missing.
[CITED: https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html]

### Pitfall 2: `reuseForks=false` with forked Spring contexts

**What goes wrong:** With `reuseForks=false`, every unit test class gets a new JVM. For ~150 test classes, this means ~150 JVM startups. On a modern machine with JVM warm-up, each startup costs ~0.3-1.0 seconds, multiplying test time significantly.
**Why it happens:** `reuseForks=false` is sometimes recommended for maximum isolation (avoids static state leakage between test classes in the same fork). But unit tests using `@ExtendWith(MockitoExtension.class)` have no shared Spring context and no static state issues.
**How to avoid:** Use `reuseForks=true` (the default) for Surefire. Spring IT contexts ARE the shared state — but `@Transactional` rollback handles DB isolation.

### Pitfall 3: Testcontainers port conflicts with `forkCount > 1` in Failsafe

**What goes wrong:** If `forkCount=2C` for Failsafe, multiple IT JVMs start simultaneously. `BackupImportMariaDbSmokeIT` uses `@DynamicPropertySource` + Testcontainers — each container gets its own random port and is fully isolated. However, `BackupImportMultipartLimitIT` and `PlaywrightConfig` use `WebEnvironment.RANDOM_PORT` — also safe because each fork's Spring context gets its own port. The risk is resource starvation on the CI runner (2 Failsafe JVMs × 2 cores = 4 JVMs simultaneously, each with a Testcontainers MariaDB).
**How to avoid:** Use `forkCount=1C` for Failsafe (1 IT JVM per core) to limit parallelism.

### Pitfall 4: `@DirtiesContext` with `reuseForks=true` — context eviction within a fork

**What goes wrong:** When `reuseForks=true`, multiple test classes run sequentially in the same JVM. Classes with `@DirtiesContext` evict the context from the cache. If the next class needs the same context, it rebuilds (correct behavior). But the rebuild cost is paid in the same fork. This is expected and harmless — `@DirtiesContext` is designed for exactly this case.
**How to avoid:** No action needed. The 10 verified mandatory `@DirtiesContext` annotations are functionally required and their context-rebuild cost is already being paid today (just sequentially).

### Pitfall 5: CI `cancel-in-progress` cancels a merge commit run

**What goes wrong:** If a push to `master` arrives seconds after a previous push to `master`, the first run is cancelled mid-way. If the cancelled run was for a "post-merge CI check," the merge result's CI status may be lost.
**Why it happens:** `cancel-in-progress: true` with `group: ${{ github.workflow }}-${{ github.ref }}` cancels any run for the same ref, including `refs/heads/master`.
**How to avoid:** For this project, direct pushes to `master` are forbidden (PR-only merge via `gh pr merge --squash`). Therefore, the only `push` events on `master` are squash-merge commits. Each squash-merge is a single push event — no concurrent pushes to `master` occur in normal workflow. Risk is negligible. [VERIFIED: CLAUDE.md Git Workflow — "no direct pushes to master"]

### Pitfall 6: `pom.xml` comment cleanup removes PLAT-06 rationale

**What goes wrong:** The PLAT-06 comment block at lines 273-280 explains WHY Failsafe is explicitly bound in the default lifecycle (without this, `*IT.java` files are only picked up by explicit `-Dtest=...`). If this comment is stripped under D-09 (Phase-N references), future maintainers add `failsafe-plugin` again in the wrong way.
**How to avoid:** D-20 cleanup for `pom.xml` must preserve the technical rationale within PLAT-06, even when stripping the `PLAT-06:` prefix tag. Rewrite as: `<!-- Bind Failsafe in the default lifecycle so *IT.java integration tests run on every ./mvnw verify. Without this binding, *IT.java files are only picked up by explicit -Dtest=... invocations. -->` — drop the Phase reference, keep the why.

### Pitfall 7: `sitegen @DirtiesContext` removal breaking test isolation

**What goes wrong:** All 7 sitegen `@DirtiesContext` annotations mutate `SiteProperties.outputDir` (a `@ConfigurationProperties` singleton). Removing them causes subsequent test classes sharing the context to see the previous test's mutated `outputDir` value. Test A writes to `/tmp/testA/`, test B expects to write to `/tmp/testB/` but `siteProperties.outputDir` still points to `/tmp/testA/` from the last `@BeforeAll`.
**How to avoid:** Do NOT remove any `@DirtiesContext` from sitegen tests. All 10 audited annotations are mandatory (see `@DirtiesContext` Audit Decision Tree section above).

---

## Per-Package Cleanup Ordering

Based on the cross-package import dependency graph (verified by `grep -r "import org.ctc.X"` across all main source files):

**Import count = number of other source files that import from this package. Higher = more dependencies on this package = higher-risk cleanup target = clean LAST.**

| Rank | Package | Import Count | File Count | Cleanup Priority |
|------|---------|-------------|------------|-----------------|
| 1 (lowest import count — leaf packages, clean FIRST) | `org.ctc.admin.controller` | 0 | 23 | FIRST |
| 2 | `org.ctc.backup.serialization` | 0 | 25 | Early |
| 3 | `org.ctc.backup.config` | 0 | 1 | Early |
| 4 | `org.ctc.backup.io` | 1 | 1 | Early |
| 5 | `org.ctc.backup.security` | 1 | 1 | Early |
| 6 | `org.ctc.backup.lock` | 2 | 3 | Early |
| 7 | `org.ctc.backup.event` | 2 | 1 | Early |
| 8 | `org.ctc.backup.audit` | 2 | 3 | Early |
| 9 | `org.ctc.admin.service` | 14 | 19 | Mid |
| 10 | `org.ctc.sitegen` | 13 | 11+5=16 | Mid |
| 11 | `org.ctc.gt7sync` | 4 | 4 | Mid |
| 12 | `org.ctc.dataimport` | 8 | 7 | Mid |
| 13 | `org.ctc.backup.dto` | 6 | 4 | Mid |
| 14 | `org.ctc.backup.schema` | 8 | 4 | Mid |
| 15 | `org.ctc.backup.service` | 2 | 7 | Mid |
| 16 | `org.ctc.backup.restore` | 26 | 3+24=27 | Mid |
| 17 | `org.ctc.backup.exception` | 15 | 6 | Mid |
| 18 | `org.ctc.admin.dto` | 28 | 22 | Late |
| 19 | `org.ctc.backup` (root package) | 65 | 1 | Late |
| 20 | `org.ctc.admin` (root package) | 42 | 6 | Late |
| 21 | `org.ctc.domain.service` | 68 | 25 | Late |
| 22 | `org.ctc.domain.exception` | 52 | 3 | Late |
| 23 | `org.ctc.domain.repository` | 147 | 24 | LAST |
| 24 | `org.ctc.domain.model` | 264 | 29 | LAST |

**Rationale:** Cleaning leaf packages first (controllers, serializers, config) minimizes the risk that a rename or deletion in a high-import package ripples through dependent files before those files are also cleaned. If a method rename is done in `org.ctc.domain.service` (68 imports), all 68 callers must be updated in the same commit — this is a Big-Bang risk. Deferring foundation packages to later waves allows cleanup of callers first.

**Per D-03 commit granularity:** Each row above = one commit + `./mvnw test`.

**Test packages follow the same ordering:** Mirror the main package cleanup with the corresponding test package in the same commit (e.g., clean `org.ctc.admin.controller` + `org/ctc/admin/controller/*Test.java` together).

---

## pom.xml Comment Cleanup Inventory

**Verified line-by-line assessment:**

| Lines | Content | Decision | Rationale |
|-------|---------|----------|-----------|
| 20-24 | `<!-- Phase 75 Plan 10: Testcontainers MariaDB... Phase 77 hotfix: bumped 1.21.3 → 2.0.5 because Docker Engine 29+... -->` | **PARTIAL REWRITE** | Strip "Phase 75 Plan 10:" and "Phase 77 hotfix:" prefix tags. Keep the technical rationale: `<!-- Spring Boot 4.0.x does NOT manage Testcontainers BOM. Pinned to 2.0.5: Testcontainers 1.x sends Docker API 1.32 which is rejected by Docker Engine 29+ (requires API >= 1.40). Testcontainers 2.x ships API 1.44. See gh:testcontainers/testcontainers-java#11235. -->` |
| 34-36 | `<!-- Phase 75 Plan 10: Testcontainers BOM aligns... -->` | **CONDENSE** | `<!-- Testcontainers BOM aligns testcontainers, junit-jupiter, and mariadb modules on a single coherent version. -->` — drop phase tag, keep intent |
| 82-91 | Jackson comment with `Phase 72 plans 02 + 03` reference | **REWRITE** | Strip "Phase 72 plans 02 + 03." Keep the technical explanation of Jackson 2.x vs Jackson 3 coexistence — this is load-bearing context (transitiv keyword). Shorten to: `<!-- Required for BackupManifest wire-contract serialization (Instant ISO-8601 strings via backupObjectMapper). Spring Boot 4 auto-configures Jackson 3 (tools.jackson.*); the backup module uses Jackson 2.x ObjectMapper (transitively via flyway-core) which does NOT register JavaTimeModule by default. -->` |
| 203-211 | Phase 75 Plan 10 Testcontainers dependencies comment | **CONDENSE** | Strip Phase references. Keep: `<!-- Testcontainers MariaDB for BackupImportMariaDbSmokeIT. Uses @DynamicPropertySource to override spring.datasource.url at @SpringBootTest startup so Flyway runs against the live engine. Auto-detects host Docker daemon; works locally + on GitHub Actions runners. -->` |
| 253-258 | `<!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->` (compiler) | **KEEP AS-IS** | Contains `JEP`, `Lombok`, `Unsafe` — all three Schutzwortliste keywords. |
| 264 | Same JEP 498 comment (Surefire) | **KEEP AS-IS** | Same reasoning |
| 273-280 | `<!-- PLAT-06: bind Failsafe in the default lifecycle... -->` | **REWRITE** | Strip `PLAT-06:` prefix. Keep technical rationale. See Pitfall 6. |
| 291 | Same JEP 498 comment (Failsafe default-it) | **KEEP AS-IS** | Same reasoning |
| 411 | Same JEP 498 comment (Failsafe e2e-it) | **KEEP AS-IS** | Same reasoning |

**Net: 3 Phase-N prefixes stripped + rewrites, 5 JEP/Lombok/Unsafe comments preserved in full.**

---

## ci.yml Comment Cleanup Inventory

**Verified line-by-line assessment:**

| Lines | Content | Decision | Rationale |
|-------|---------|----------|-----------|
| 69-72 | `# Phase 78: Structural guard... Fails if any FROM... Whitelist-on-suffix approach (D-05)... Cross-platform grep idiom mirrors pom.xml PLAT-07 build-guard (commit f451ff4).` | **REWRITE** | Strip `Phase 78:` prefix and `(D-05)` reference. Keep: `# Structural guard for eclipse-temurin -noble suffix pin in Dockerfile.` + next line about cross-platform grep |
| 89-93 | Inline `#` comments inside the shell script | **KEEP MOSTLY** | These explain the two-stage grep approach (technical rationale). Strip only the `(the exact portability trap commit f451ff4 documented for the Phase 71-05 build-guard)` reference. Keep the cross-platform-grep explanation. |
| 96 | `# Whitelist: every FROM eclipse-temurin:...` | **KEEP AS-IS** | Pure technical explanation |
| 98 | `echo "Reason: Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky), which the bare '25-jre' tag silently rotated to in release run 25609204039."` | **KEEP AS-IS** | This is the load-bearing Playwright-noble-pin pitfall rationale. D-10 + D-13 Schutzwortliste (`pitfall` implied by context). Removing this loses the "why" for the `-noble` pin permanently. |
| 99 | `echo "See .planning/phases/78-docker-release-image-fix/78-CONTEXT.md (decisions D-01, D-05, D-06)."` | **REPLACE** | Replace with: `echo "See Dockerfile and its comment block for the -noble pin rationale."` — drop phase reference, keep pointer |
| 108-114 | `# Phase 78: Exercises docker build .` job comment | **REWRITE** | Strip `Phase 78:` and `D-07:`, `D-08:`. Keep: `# Exercises docker build . on every PR + push to master so Dockerfile / base-image regressions fail fast. # In particular, exercises stage 2's playwright install chromium RUN step. # Acceptable cost: +1-3 minutes CI per PR.` |

**`mariadb-migration-smoke.yml` cleanup:** The SACRED body must not change. The header comments (lines 1-17) include one Phase-75 reference: `# Phase 75 (v1.10): BackupImportMariaDbSmokeIT...`. If the cleanup subagent touches this file at all, it may only strip the `# Phase 75 (v1.10):` prefix tag from line 14 — the rest of the rationale is load-bearing. Per D-20: "cleanup is opportunity-based and may be empty" — given the risk, the safe verdict is **LEAVE ENTIRELY** unless the cleanup subagent is explicitly instructed to handle it with the Schutzwortliste-check.

---

## Test-Invocation-Discipline Section Draft for TESTING.md

**New section to append to `.planning/codebase/TESTING.md` per D-08:**

```markdown
## Test Invocation Discipline

**Codified from `feedback_test_call_optimization` (Phase 79 D-08).**

### Rule: One Final Full Run Per Phase

Each GSD phase uses **one and only one** `./mvnw verify -Pe2e` invocation as its final gate (D-19 / Phase 77 D-13). This is the only invocation that counts for coverage, E2E smoke, and CI GREEN status.

Between waves within a phase, use **targeted invocations** for fast feedback:

```bash
# Run only a single test class
./mvnw test -Dtest=BackupImportServiceTest

# Run only a single IT class
./mvnw verify -Dit.test=BackupRoundTripIT

# Run all tests in a package
./mvnw test -Dtest="org.ctc.backup.service.*"

# Run tests matching a method name pattern
./mvnw test -Dtest="BackupImportServiceTest#givenValid*"
```

### Rule: Do Not Re-Run Full Suite Between Waves

Running `./mvnw verify` (or `./mvnw verify -Pe2e`) after every plan task wastes CI minutes and developer time. The full suite is a GATE, not a development loop.

| Context | Invocation |
|---------|-----------|
| After implementing a single task | `./mvnw test -Dtest=<AffectedTestClass>` |
| After per-package cleanup commit (D-03) | `./mvnw test` (unit + IT, no E2E) |
| After pom.xml / ci.yml change | `./mvnw verify` (full, no E2E) |
| Phase final gate (D-19) | `./mvnw verify -Pe2e` |
| Triage a test failure | `./mvnw verify -fae` (fail-at-end, sees all failures) |

### Rule: Run Order for Independence Verification

Before enabling Surefire/Failsafe parallelism, verify test independence:

```bash
# Reverse alphabetical order — detects setup-dependent ordering
./mvnw test -Dsurefire.runOrder=reversealphabetical

# Random order — three seeds for statistical confidence
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678
./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999
```

All three random-seed runs must be GREEN before `forkCount` is increased.
```

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito 5.20.0 |
| Config file | `pom.xml` (Surefire lines 260-302, Failsafe lines 273-420) |
| Quick run command | `./mvnw test -Dtest=<TargetClass>` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| D-05 Wave 1 | All tests pass in reverse order | Independence audit | `./mvnw test -Dsurefire.runOrder=reversealphabetical` | Gate before Wave 2 |
| D-05 Wave 1 | All tests pass with 3 random seeds | Independence audit | `./mvnw test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` | Repeat ×3 |
| D-05 Wave 2 | Surefire with forkCount=2C passes | Parallelism | `./mvnw verify` | After Wave 1 green |
| D-06 | Baseline wallclock recorded | Manual | `time ./mvnw clean verify -Pe2e ...` | Before any D-05 changes |
| D-06 | Final wallclock ≥30% reduction | Manual | `time ./mvnw clean verify -Pe2e ...` | After D-05 complete |
| D-18 | JaCoCo ≥ 0.82 after cleanup | Coverage | `./mvnw verify` | Built-in, fails build if violated |
| D-19 | Final gate green | Full E2E | `./mvnw verify -Pe2e` | Phase final gate |

### Sampling Rate

- **Per cleanup-package commit (D-03):** `./mvnw test`
- **Per wave merge:** `./mvnw verify` (no E2E)
- **Phase gate:** `./mvnw verify -Pe2e` BUILD SUCCESS

### Wave 0 Gaps

None — existing test infrastructure covers all phase requirements. Phase 79 adds no new production code requiring new test coverage.

---

## Security Domain

Phase 79 is code cleanup + config changes only. No new authentication, authorization, cryptography, or input-handling paths are introduced. The `security_enforcement` default-enabled constraint does not add new test requirements because:
- No new endpoints are added (D-01: verhaltens-erhaltend).
- No Spring Security configuration is modified.
- The flaky-tag quarantine mechanism is a build-time configuration only.

ASVS categories V2/V3/V4/V6 do not apply. V5 (Input Validation) is not affected — existing validation annotations remain unchanged.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Temurin) | All test execution | ✓ | 25 | — |
| Maven Wrapper | `./mvnw` | ✓ | Managed by project | — |
| Surefire 3.5.5 | Parallel unit tests | ✓ | 3.5.5 (SB 4.0.6 BOM) | — |
| Failsafe 3.5.5 | Parallel IT | ✓ | 3.5.5 (SB 4.0.6 BOM) | — |
| JaCoCo 0.8.14 | Coverage | ✓ | 0.8.14 (pom.xml) | — |
| Docker | Testcontainers MariaDB IT | ✓ (local) / CI runner | Available in CI via `docker.available=true` | Skip IT via `@EnabledIfSystemProperty` |
| Playwright Chromium | E2E + Graphics | ✓ (if installed) | 1.59.0 | Skip E2E if not installed |
| GitHub Actions | CI concurrency group | ✓ | N/A | N/A |

**Missing dependencies with no fallback:** None.

---

## Open Questions

1. **Wallclock baseline on CI vs. local machine**
   - What we know: D-06 says "baseline-measured ≥ 30% wallclock reduction" but doesn't specify local vs. CI.
   - What's unclear: Local machine timings vary by hardware; CI timings are more reproducible but require a PR to trigger.
   - Recommendation: Measure locally first for the plan baseline. If CI baseline is needed, trigger a dry-run PR. Document both in `79-AUTO-UAT.md` if both are available.

2. **`forkCount=2C` heap impact on CI runners**
   - What we know: GitHub Actions ubuntu-latest runners have 7 GB RAM, 2 CPU cores. `2C` = 4 Surefire JVMs.
   - What's unclear: Each Spring-context-free unit test JVM uses ~256-512 MB heap. 4 × 512 MB = 2 GB — well within CI limits.
   - Recommendation: No special heap tuning needed. If OOM occurs in CI, add `<argLine>@{argLine} ... -Xmx512m</argLine>` as a fallback.

3. **Failsafe `1C` vs `2C` — Testcontainers MariaDB contention**
   - What we know: `BackupImportMariaDbSmokeIT` uses `@DynamicPropertySource` + Testcontainers. Two simultaneous IT forks would each start their own MariaDB container.
   - What's unclear: Does GitHub Actions CI runner have enough Docker resources for 2 simultaneous MariaDB containers?
   - Recommendation: Start with `forkCount=1C` for Failsafe. If Wave 1 independence audit is clean and CI is stable, upgrade to `1C` and monitor. The `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` guard already handles the case where Docker is unavailable.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `cancel-in-progress: true` with `group: github.workflow + github.ref` does NOT block a PR when the run is cancelled (branch protection treats cancelled ≠ failed) | CI Concurrency Group | If wrong: PRs would be blocked until a new CI run completes. Mitigation: test with a throwaway PR before landing D-07. |
| A2 | GitHub Actions ubuntu-latest has 2 CPU cores in 2026 (so `2C` = 4 Surefire JVMs) | Environment Availability | If wrong: more/fewer JVMs. Consequence is timing-only, not correctness. |

---

## Sources

### Primary (HIGH confidence)
- `/websites/maven_apache_surefire_maven-surefire-plugin` (Context7) — `forkCount`, `reuseForks`, `runOrder`, `excludedGroups`, `@{argLine}` late evaluation
- `/websites/jacoco_jacoco_trunk_doc` (Context7) — `prepare-agent`, `argLine` property propagation pattern
- `/apache/maven-surefire` (Context7) — `forkCount` multiplier syntax (`2C`, `1C`)
- `/spring-projects/spring-framework` (Context7) — `@DirtiesContext` semantics, test context caching
- `pom.xml` (verified: Surefire 3.5.5, Failsafe 3.5.5, JaCoCo 0.8.14, Mockito 5.20.0, argLine pattern at lines 265/292/412)
- `.github/workflows/ci.yml` (verified: no existing concurrency block, Phase-78 comments at lines 69-72/108-114, noble-pin rationale at line 98)
- `src/test/java/**` grep results (verified: 10 `@DirtiesContext` annotations, all mandatory; 119 `@SpringBootTest` annotations; 42 pure Mockito tests; `@Tag("flaky")` does not exist yet)
- `src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java` (verified: CountDownLatch non-resettable rationale comment)
- `src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java` + siblings (verified: `siteProperties.setOutputDir()` mutation pattern)

### Secondary (MEDIUM confidence)
- `https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html` — `reuseForks` tradeoffs
- `https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html` — `@{argLine}` late evaluation requirement

### Tertiary (LOW confidence — tagged ASSUMED where used)
- GitHub Actions `concurrency:` + required-status-checks interaction — [ASSUMED] based on documented GHA behavior; verify with test PR if strict branch protection is configured.

---

## Metadata

**Confidence breakdown:**
- Standard stack (Surefire/Failsafe versions, argLine syntax): HIGH — verified against Spring Boot 4.0.6 BOM and existing pom.xml
- `@DirtiesContext` audit verdicts: HIGH — verified by code inspection of all 10 annotations
- Surefire forkCount configuration blocks: HIGH — verified against Context7 + official Surefire docs
- CI concurrency YAML: HIGH — standard GitHub Actions syntax; interaction with required-status-checks is ASSUMED (LOW)
- Per-package cleanup ordering: HIGH — import counts verified by grep
- pom.xml + ci.yml comment inventory: HIGH — verified by direct line-range reads

**Research date:** 2026-05-15
**Valid until:** 2026-06-15 (stable stack, 30 days)
