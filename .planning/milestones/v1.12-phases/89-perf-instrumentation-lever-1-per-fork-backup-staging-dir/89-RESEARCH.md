# Phase 89: PERF Instrumentation & Lever 1 (Per-Fork Backup-Staging-Dir) - Research

**Researched:** 2026-05-19
**Domain:** Maven Surefire/Failsafe fork-number injection, Spring TCF `MergedContextConfiguration` cache-key fingerprinting, `BackupStagingCleanup` sweep-isolation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Three sequential inline plans on branch `gsd/v1.12-driver-import-and-test-perf` — Plan 89-01 (PERF-01), Plan 89-02 (PERF-02), Plan 89-03 (Wave-4 measurement).
- **D-02:** Wave-4 measurement = honest local reporting (3 runs, median + delta vs. 10:24 baseline + JaCoCo ≥ 88.88 %). No hard local reduction gate.
- **D-03:** Surefire + Failsafe `<systemPropertyVariables>` set `app.backup.staging-dir=data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}`.
- **D-04:** Per-fork path schema = `data/${profile}/backup-staging-fork-${surefire.forkNumber}`. Project-level `<properties><surefire.forkNumber>0</surefire.forkNumber></properties>` fallback for non-forked IDE/CLI invocations.
- **D-05:** BOTH Surefire AND Failsafe `default-it` `<systemPropertyVariables>` set the same value.
- **D-06:** Legacy `data/*/backup-staging/` wiped via one-shot `rm -rf` documented in Plan-01 SUMMARY; no production code change.
- **D-07:** Hybrid surface — `ContextLoadCountListener` stays as `ApplicationContextInitializer` (count only); new `ContextCacheKeyFingerprintListener` is a `TestExecutionListener` (hash + display).
- **D-08:** Marker file = `target/test-perf/context-loads-{PID}.txt`, extended format: Line 1 = `total <count>`; subsequent lines = `<hex-hash>\t<mcc-display>`.
- **D-09:** Aggregator = `scripts/test-perf/aggregate-fingerprints.sh` (shellcheck-clean, executable). `docs/test-performance.md § PERF-02 Forensics` shows usage + Top-5 output sample.
- **D-10:** Hash = `Integer.toHexString(mergedContextConfiguration.hashCode())`. Display = `mergedContextConfiguration.toString()` truncated to ~200 chars.
- **D-11:** Failsafe `default-it` permanently `<forkCount>2</forkCount><reuseForks>true</reuseForks>`. Failsafe `e2e-it` stays single-fork.
- **D-12:** `BackupStagingDirPerForkIT` — single-fork self-assertion: regex match on dir name + fork-number property parity.
- **D-13:** 3-seed scope = all `src/test/java/org/ctc/backup/**` ITs, seeds 1234/5678/9999.
- **D-14:** `application.yml` is NOT modified. Production behavior unchanged.
- **D-15:** Standard quality gates — JaCoCo ≥ 88.88 %, SpotBugs 0, CodeQL exit 0.
- **D-16:** Doc surfaces — `docs/test-performance.md` (§ PERF-02 Forensics + § Post-Optimization Wallclock Wave 4 + § v1.12 Forward Path update) + `README.md` pointer. `CLAUDE.md` and `docs/operations/import-runbook.md` NOT touched.
- **D-17:** `BackupStagingCleanupRaceIT` — sweep-isolation proof under `forkCount=2`; sibling dummy fork dir (`-fork-99` placeholder).

### Claude's Discretion

- Shared pom.xml per-fork path: `<properties>` entry (`staging.dir.test` or similar) vs. literal duplication in Surefire + Failsafe — planner picks based on Maven-property-substitution ordering.
- `ContextCacheKeyFingerprintListener` registration: `META-INF/spring.factories` vs. `@TestExecutionListeners(mergeMode = MERGE_WITH_DEFAULTS)` base class — planner picks lowest-friction option.
- `MergedContextConfiguration.toString()` truncation cutoff (D-08 says ~200 chars) — planner picks value that keeps marker file under ~10KB per fork.
- `BackupStagingCleanupRaceIT` sibling-fork-dir name (D-17 placeholder `-fork-99`) — planner picks any value guaranteed not to clash with realistic `${surefire.forkNumber}` 0–32.
- Fingerprint listener filename: `ContextCacheKeyFingerprintListener` vs. `ContextCacheKeyDumpListener` — planner picks.
- `§ PERF-02 Forensics` prose wording + Top-5 output format (table vs. plain list).

### Deferred Ideas (OUT OF SCOPE)

- PERF-03 cluster consolidation (Phase 90)
- PERF-04 Testcontainers `withReuse` (Phase 90)
- PERF-05 test-module-split decision (Phase 90)
- PERF-06 CI 5-run re-harvest (Phase 91)
- UX-01 Google-API error UX (Phase 91 stretch)
- `application-prod.yml` / `import-runbook.md` changes
- Aggressive `<forkCount>1C</forkCount>`
- Legacy-dir startup sweeper bean (YAGNI)
- JaCoCo gate tightening (88.88 % → 89.0 %)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PERF-01 | `app.backup.staging-dir` resolves to per-fork variant via Surefire/Failsafe fork-numbering system property; `BackupStagingCleanup` respects per-fork path; verified by per-fork-dir assertion IT + re-run at elevated `forkCount` | RQ-1, RQ-5, RQ-6 findings; `BackupStagingCleanup` source confirming pure `@Value` injection; Surefire/Failsafe 3.5.5 plugin.xml confirming `${surefire.forkNumber}` fork-time substitution |
| PERF-02 | `ContextLoadCountListener` extended to dump per-context cache-key hashes into PID-keyed marker files; aggregator script + `docs/test-performance.md` § PERF-02 Forensics | RQ-2, RQ-3, RQ-4, RQ-7 findings; Spring 7.0.6 `DefaultContextCache` source confirming `MergedContextConfiguration.hashCode()` as bucketing key; `DefaultTestContext` source confirming cast path |
</phase_requirements>

---

## Domain Background

Phase 89 touches three distinct technical domains: (1) Maven Surefire/Failsafe fork-number injection, (2) Spring TCF context-cache key fingerprinting, and (3) `BackupStagingCleanup` sweep-isolation under parallel forks.

**Maven fork injection** — Surefire and Failsafe both support the `${surefire.forkNumber}` placeholder in `<systemPropertyVariables>` and `<argLine>`. This token is replaced at **fork-dispatch time** (not POM-parse time) with an integer in the range 1..`forkCount` (or 0 for non-forked execution). The placeholder works identically in both plugins; Failsafe's `plugin.xml` copies the same documentation from Surefire's.

**Spring TCF fingerprinting** — The Spring Test Context Framework (Spring 7.0.6, wrapped by Spring Boot 4.0.6) stores loaded contexts in `DefaultContextCache`, which uses `MergedContextConfiguration` as the `Map` key. Since Java `Map` contract requires `hashCode()` and `equals()`, the value returned by `MergedContextConfiguration.hashCode()` is exactly the bucketing function. `Integer.toHexString(mcc.hashCode())` produces the same bucket token the cache uses. Accessing `MergedContextConfiguration` from a `TestExecutionListener` requires a cast to `DefaultTestContext` — this cast is stable across Spring TCF 4.0+ generations.

**Sweep isolation** — `BackupStagingCleanup` is a package-private `@Component` injected with `@Value("${app.backup.staging-dir}")` as a `Path`. Its `sweepStagingDir()` fires on `ApplicationReadyEvent` and operates exclusively on `this.stagingDir`. Since `this.stagingDir` will be a per-fork path after Plan 89-01 lands, the sweep is inherently fork-scoped — no production code logic change is needed.

---

## Technical Findings

### RQ-1: Maven Property Substitution Order — `${surefire.forkNumber}` in `<systemPropertyVariables>`

**Conclusion:** `${surefire.forkNumber}` inside `<systemPropertyVariables>` is substituted at **JVM-fork time**, not POM-parse time. Each forked JVM receives the resolved system property with its own fixed integer (1 or 2 for `forkCount=2`).

**Evidence:** Surefire plugin 3.5.5 `META-INF/maven/plugin.xml` line 362 [VERIFIED: local Maven repo, Surefire 3.5.5 plugin.xml]:

> "The system properties and the argLine of the forked processes may contain the place holder string `${surefire.forkNumber}`, which is replaced with a fixed number for each of the parallel forks, ranging from 1 to the effective value of forkCount..."

Failsafe 3.5.5 `META-INF/maven/plugin.xml` contains an **identical statement** [VERIFIED: local Maven repo, Failsafe 3.5.5 plugin.xml], confirming same behavior for the `default-it` execution.

**Implication for D-03/D-04/D-05:** The value `data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}` in `<systemPropertyVariables>` is safe to use in both plugin configurations. `${spring.profiles.active:dev}` is evaluated by Spring at context-load time from the system property that is already set; `${surefire.forkNumber}` is resolved by Surefire/Failsafe before the JVM starts, so Spring sees the fully-resolved literal string as a JVM system property.

**Substitution precedence check:** Spring's `SystemEnvironmentPropertySource` chain picks up JVM system properties (`System.getProperty(...)`) at a higher precedence than `application*.yml` defaults. A `<systemPropertyVariables>` entry becomes a JVM system property inside the forked JVM via `System.setProperty(...)` before the Spring context boots. No `@PropertySource` or custom resolver is needed.

**`<properties>` vs. literal duplication (Claude's Discretion):** Maven `<properties>` entries are POM-resolved (Maven property interpolation). Because `${surefire.forkNumber}` must survive POM interpolation to reach the plugin at fork-dispatch time, it must NOT be included in a `<properties>` definition (Maven would try to resolve it as a Maven property and leave it empty or fail). The correct pattern is one of:
- **Literal duplication** in both Surefire and Failsafe `<systemPropertyVariables>` blocks. Safe; Maven sees `${surefire.forkNumber}` as a literal string for the plugin to handle.
- **`<properties>` entry using `@{…}` late-binding** — this is JaCoCo `argLine` style and applies only to `argLine`, not to `<systemPropertyVariables>` values.

**Recommended for planner (Claude's Discretion resolution):** Use **literal duplication** in both Surefire and Failsafe `<systemPropertyVariables>`. A shared `<properties>` entry for the path prefix (`data/${spring.profiles.active:dev}/backup-staging-fork-`) would not help because Maven would expand the Spring EL `${spring.profiles.active:dev}` segment too, corrupting the value. Literal duplication of the full value is the only safe approach.

**Non-fork fallback (D-04):** A project-level `<properties><surefire.forkNumber>0</surefire.forkNumber></properties>` entry provides Maven-level default `0`. When Surefire/Failsafe runs without actual forking (e.g., `forkCount=0`, IDE run, `-Dtest=` targeted run without the Failsafe fork), the token `${surefire.forkNumber}` in `<systemPropertyVariables>` is not substituted by the plugin (no fork dispatch), and the Maven property fallback `0` ensures the path resolves to `backup-staging-fork-0` rather than `backup-staging-fork-` (trailing hyphen). Confirmed safe: `0` is below the minimum real fork number (`1`) and IDE runs inject the app under `data/dev/backup-staging` (no `<systemPropertyVariables>` override applied by IDE).

---

### RQ-2: `MergedContextConfiguration` Access from `TestExecutionListener#beforeTestClass`

**Conclusion:** Access `MergedContextConfiguration` via a direct cast: `((DefaultTestContext) testContext).getMergedContextConfiguration()` — but the `getMergedContextConfiguration()` method is not on the `TestContext` interface. The `mergedConfig` field is `private final` on `DefaultTestContext`.

**Verified from Spring 7.0.6 source** [VERIFIED: spring-test-7.0.6-sources.jar]:

`DefaultTestContext` structure:
```java
// org.springframework.test.context.support.DefaultTestContext
public class DefaultTestContext implements TestContext {
    private final MergedContextConfiguration mergedConfig; // private final field

    // No public getMergedContextConfiguration() method exposed on TestContext interface
    // TestContext interface only exposes:
    //   - getApplicationContext()
    //   - getTestClass()
    //   - getTestInstance()
    //   - getTestMethod()
    //   - hasApplicationContext()
    //   + AttributeAccessor methods
}
```

The `TestContext` interface (Spring 7.0.6) does **not** expose a `getMergedContextConfiguration()` method. Three implementation paths are available:

**Path A — Reflection (safest for interface contract, but verbose):**
```java
Field f = DefaultTestContext.class.getDeclaredField("mergedConfig");
f.setAccessible(true);
MergedContextConfiguration mcc = (MergedContextConfiguration) f.get(testContext);
```
Risk: `setAccessible` trips find-sec-bugs `REFLECTIVE_ACCESS` pattern. Needs `@SuppressFBWarnings`.

**Path B — Cast to `DefaultTestContext` (concise, cast-safe since Spring 4.0):**
The production `TestContext` implementation in Spring has been `DefaultTestContext` since Spring 4.0. Spring Boot 4.0.6 uses Spring 7.0.x; the `DefaultTestContext` class is in `spring-test.jar`. A cast `(DefaultTestContext) testContext` is safe as long as no custom `TestContextBootstrapper` replaces the default with a non-`DefaultTestContext` implementation (this project has no such customization).
However, `mergedConfig` is `private final` — even with the cast, direct field access is impossible. A getter does not exist on the class. **Reflection is therefore required regardless of the cast path.**

**Path C — Re-derive hash from `testContext.getTestClass()` annotations (avoids reflection):**
`MergedContextConfiguration.hashCode()` incorporates: test class's `@ContextConfiguration` locations/classes, active profiles, property source descriptors, property source properties, context customizers, context loader class name, and parent. All of these are accessible via annotations on `testContext.getTestClass()` plus `testContext.getApplicationContext()`.

However, re-deriving the hash from annotations would not match the Spring TCF-internal `MergedContextConfiguration.hashCode()` exactly (custom `ContextCustomizer` contributions from Spring Boot's `@SpringBootTest` processing are part of the hash but are not directly accessible from annotations). This approach is fragile.

**Recommended path for planner (resolving D-07):** Use **reflection on `mergedConfig`** via `ReflectionUtils.findField` + `ReflectionUtils.makeAccessible`. Spring's own `ReflectionUtils` is already on the classpath:
```java
// org.springframework.test.context.TestExecutionListener#beforeTestClass
Field f = ReflectionUtils.findField(DefaultTestContext.class, "mergedConfig");
ReflectionUtils.makeAccessible(f);
MergedContextConfiguration mcc = (MergedContextConfiguration) ReflectionUtils.getField(f, testContext);
```
This produces an `EI_EXPOSE_REP`-style SpotBugs hit at most; add `@SuppressFBWarnings({"DP_DO_INSIDE_DO_PRIVILEGED"}, justification="Test-only PERF-02 instrumentation listener — reflection is intentional")` if needed. If find-sec-bugs fires `REFLECTIVE_ACCESS`, add targeted suppression per CLAUDE.md SpotBugs discipline.

**Alternative: expose `getMergedContextConfiguration()` via a package-private accessor in `ContextLoadCountListener`'s sibling class** — but this requires modifying `DefaultTestContext`, which is a Spring class. Not viable.

**Cast-safety confirmation:** `DefaultTestContext` has been the only concrete `TestContext` implementation since Spring 4.0 [CITED: Spring TCF source, DefaultTestContext.java Javadoc "@since 4.0"]. Spring Boot does not substitute it. The cast `instanceof DefaultTestContext` check is a safe pre-flight if defensive coding is required.

---

### RQ-3: Spring Boot Test Listener Registration Paths

**Conclusion:** `META-INF/spring.factories` with key `org.springframework.test.context.TestExecutionListener` is the standard, low-friction registration path. Spring Boot 4.0.6 does not change or break this mechanism.

**Evidence** [VERIFIED: spring-test-7.0.6.jar spring.factories, spring-boot-test-4.0.6.jar spring.factories, spring-boot-test-autoconfigure-4.0.6.jar spring.factories]:

1. `spring-test-7.0.6.jar/META-INF/spring.factories` contains the canonical `org.springframework.test.context.TestExecutionListener=...` block listing all 12 default Spring listeners. This is the established `SpringFactoriesLoader` mechanism.

2. `spring-boot-test-4.0.6.jar/META-INF/spring.factories` registers only `ContextCustomizerFactory` and `ApplicationContextInitializer` entries — no `TestExecutionListener` entries. Spring Boot 4.0.6 does **not** override the `SpringFactoriesLoader`-based discovery of `TestExecutionListener` implementations.

3. `spring-boot-test-autoconfigure-4.0.6.jar/META-INF/spring.factories` similarly registers only `ContextCustomizerFactory` entries.

**Registration mechanism (D-07):**
- Option A (`META-INF/spring.factories`): Add `org.springframework.test.context.TestExecutionListener=org.ctc.testsupport.ContextCacheKeyFingerprintListener` as a new line in `src/test/resources/META-INF/spring.factories` (same file as the existing `ApplicationContextInitializer` entry). This auto-registers the listener for **all** test classes in the JVM — no per-class annotation needed. Best fit given ~50 backup IT classes.
- Option B (`@TestExecutionListeners(mergeMode = MERGE_WITH_DEFAULTS)` base class): Requires all target ITs to extend a new base class. For 25+ backup ITs already written without a common base, this would require modifications to existing files — higher blast radius.

**Recommended:** Option A (`spring.factories` extension). One-line addition, zero changes to existing IT classes. The new listener will auto-register for all test classes, just like `ContextLoadCountListener`'s `ApplicationContextInitializer` registration currently does.

**`mergeMode = MERGE_WITH_DEFAULTS` caveat:** If the planner chooses Option B for any reason, `mergeMode = MERGE_WITH_DEFAULTS` is required on the base class's `@TestExecutionListeners` so that the default Spring listeners (dependency injection, transaction, etc.) remain active.

---

### RQ-4: Forks vs. `reuseForks` — Listener Firing and PID-Keying

**Conclusion:** Under `forkCount=2 + reuseForks=true`, `TestExecutionListener#beforeTestClass` fires once per test class per fork-JVM. The existing PID-keyed output (`context-loads-{PID}.txt`) correctly segregates data per fork because each fork is a distinct JVM with a distinct PID.

**Breakdown:**

| Fork config | `beforeTestClass` fires | Context builds | PID output |
|-------------|------------------------|----------------|------------|
| `forkCount=1 + reuseForks=true` | Once per test class in that JVM | Spring TCF cache-hit if same cache key | Single PID |
| `forkCount=2 + reuseForks=true` | Once per test class in the JVM that runs it | Spring TCF cache-hit per fork independently | Two PIDs |

Under `reuseForks=true`, tests are distributed across the forked JVMs; each forked JVM runs multiple test classes sequentially. `beforeTestClass` fires for each class on whichever fork runs it. Therefore:

- Fork 1 (PID=AAAA) runs test classes C1, C3, C5 → `beforeTestClass` fires 3 times on Fork 1.
- Fork 2 (PID=BBBB) runs test classes C2, C4, C6 → `beforeTestClass` fires 3 times on Fork 2.
- Each fork writes its accumulated hash records to `context-loads-{PID}.txt` at JVM shutdown.

**`beforeTestClass` vs. context-build relationship:** `beforeTestClass` fires once per class regardless of whether the context was loaded fresh or retrieved from cache. The fingerprint listener must therefore guard against recording duplicate hashes for the same context-cache key (i.e., two classes sharing the same `MergedContextConfiguration.hashCode()` should produce two entries in the marker file, not one). This is expected behavior — `occurrence × cluster-size` metric relies on counting all `beforeTestClass` invocations, not distinct context builds.

**D-08 marker file format migration:** The existing aggregator loop in `docs/test-performance.md` L233-239 (`cat $f` summed as integer) will break after PERF-02 because the marker file now contains multi-line text. Plan-02 SUMMARY must document this migration: `head -1 $f | awk '{print $2}'` extracts the count from the `total <N>` first line.

---

### RQ-5: Failsafe `forkCount=2 + reuseForks=true` — Known Pitfalls for Backup ITs

**Confirmed risk areas (from Phase 86 SUMMARY and codebase inspection):**

| Risk | Applies to Backup ITs? | Mitigation |
|------|------------------------|------------|
| **Shared filesystem state** — two forks write to the same path simultaneously | YES (primary reason `forkCount>1` was blocked) | D-03 per-fork staging-dir eliminates the race |
| **`System.setProperty` leakage across test classes within a fork** | Low risk — `reuseForks=true` reuses JVMs but test isolation is per-class | `@DirtiesContext` already handles the `CountDownLatch` cases |
| **Static-field state** | `ImportLockService` uses `ReentrantLock` (reset via helper per Phase-86-03) | Already addressed; each fork has its own JVM statics |
| **Port collisions** | Not applicable to `default-it` (no `RANDOM_PORT` in backup ITs) | N/A |
| **`@DirtiesContext` semantics under fork reuse** | `@DirtiesContext(AFTER_METHOD)` on backup IT lock-latch tests closes the context in the fork that ran them; the other fork has its own context | No cross-fork eviction possible — contexts are per-JVM |
| **`target/` report file collisions** | Surefire/Failsafe write per-fork surefire-reports in `target/surefire-reports/` and `target/failsafe-reports/`. Under `forkCount=2` both forks write to this dir, but Surefire/Failsafe manage this via per-test-class XML files (not a shared file) | No collision expected |
| **`BackupStagingCleanup.sweepStagingDir()` firing N times per fork** | Under `reuseForks=true`, the Spring context may be loaded and reloaded (if `@DirtiesContext` fires); each reload triggers `ApplicationReadyEvent` again, sweeping the per-fork dir again | Safe — the sweep is idempotent (no-op on already-empty dir or non-existent dir) |

**Phase 86 cache-key-fragmentation lesson (from 86-02-SUMMARY):** The `BackupStagingCleanupIT` already uses `@DynamicPropertySource` to override `app.backup.staging-dir` with a `@TempDir`. After D-03 lands, the Surefire/Failsafe `<systemPropertyVariables>` value overrides the `application.yml` default, but `BackupStagingCleanupIT`'s `@DynamicPropertySource` overrides BOTH — `@DynamicPropertySource` is applied during context customization and has higher precedence than external system properties in Spring Boot's `TestPropertySourceUtils` chain. This means `BackupStagingCleanupIT` will continue using its `@TempDir` regardless of the per-fork path — this is correct and desired behavior (the IT already manages its own isolated dir).

**CRITICAL NOTE for planner:** The existing `BackupStagingCleanupIT` uses `static @TempDir` + `@DynamicPropertySource` under the standard `@TestInstance(PER_METHOD)` lifecycle (unlike the sitegen tests). JUnit 5 initializes `static @TempDir` fields before `@DynamicPropertySource` resolution under `PER_METHOD` (Phase 86-02 SUMMARY Pitfall 3 confirmed this is safe for `PER_METHOD`; `PER_CLASS` is the problematic case). `BackupStagingCleanupIT` has `@TestInstance` not declared, so it defaults to `PER_METHOD` — the `static @TempDir` works correctly.

---

### RQ-6: `BackupStagingCleanupRaceIT` Design (D-17)

**Conclusions:**

**(a) Sibling dummy fork dir safety:** A sibling dir `…-fork-99` is safe as long as `forkCount` stays ≤ 32. With `forkCount=2`, Surefire/Failsafe use fork numbers 1 and 2. With the project-level fallback `<surefire.forkNumber>0</surefire.forkNumber>`, non-forked runs use fork 0. Fork numbers 3–32 are never assigned under the current config. `99` has a comfortable margin. The planner may use any value ≥ 10 for clarity.

**(b) `BackupStagingCleanup` event timing in test context:**
Looking at `BackupStagingCleanup.java` [VERIFIED: codebase]:
```java
@EventListener(ApplicationReadyEvent.class)
void sweepStagingDir() { ... }
```
Under a `@SpringBootTest` context boot, `ApplicationReadyEvent` IS published automatically by the Spring Boot test infrastructure. `sweepStagingDir()` fires once on context bootstrap (before `@BeforeAll`/`@BeforeEach` in the test class). To verify sweep behavior AFTER seeding fixture files, the IT must **re-publish** the event — exactly the pattern used in the existing `BackupStagingCleanupIT`:
```java
context.publishEvent(new ApplicationReadyEvent(
    new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));
```
This is a proven pattern (4 tests in `BackupStagingCleanupIT` use it without `@DirtiesContext`). The new `BackupStagingCleanupRaceIT` should reuse it.

**(c) File-write atomics vs. sweep race:** Standard `Files.write(path, bytes)` on POSIX (macOS, Linux) is atomic for small files at the OS level (the write happens before the directory is scanned by the sweep). In the test context this is sequential (not concurrent), so no atomicity concern.

**Shape recommendation:**
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupStagingCleanupRaceIT {
    @Value("${app.backup.staging-dir}") Path ownForkDir;
    @Autowired ApplicationContext context;
    @Autowired BackupStagingCleanup cleanup;

    private Path siblingForkDir; // constructed from ownForkDir.getParent() + "backup-staging-fork-99"

    @BeforeAll void setUp() throws IOException {
        siblingForkDir = ownForkDir.getParent().resolve("backup-staging-fork-99");
        Files.createDirectories(siblingForkDir);
    }

    @Test void givenFilesInBothDirs_whenSweep_thenOnlyOwnDirFilesRemoved() throws IOException {
        // seed own dir + sibling dir
        // re-publish ApplicationReadyEvent
        // assert: own dir upload-*.zip removed, sibling untouched
    }

    @AfterAll void tearDown() throws IOException {
        // rm -rf siblingForkDir
    }
}
```

`BackupStagingCleanup` is package-private. `BackupStagingCleanupRaceIT` must be in `org.ctc.backup.service` (same package) to access it directly via `@Autowired` — consistent with the existing `BackupStagingCleanupIT` location [VERIFIED: codebase].

---

### RQ-7: `Integer.toHexString(MergedContextConfiguration.hashCode())` — Collision Risk and Cache-Bucket Alignment

**Conclusion:** `MergedContextConfiguration.hashCode()` is **exactly** the bucketing function used by `DefaultContextCache`. The 8-char hex form via `Integer.toHexString(...)` produces the same bucket identifier. Collision risk is Java's standard 32-bit hash collision probability (negligible for the < 100 distinct contexts expected per test suite run).

**Evidence** [VERIFIED: spring-test-7.0.6-sources.jar]:

`DefaultContextCache` uses `Map<MergedContextConfiguration, ApplicationContext>`:
```java
// org.springframework.test.context.cache.DefaultContextCache
private final Map<MergedContextConfiguration, ApplicationContext> contextMap =
    Collections.synchronizedMap(new LinkedHashMap<>(64));
```
Java's `LinkedHashMap` uses `key.hashCode()` and `key.equals()` for bucketing. Therefore `MergedContextConfiguration.hashCode()` is the exact function the cache uses.

`MergedContextConfiguration.hashCode()` [VERIFIED: spring-test-7.0.6-sources.jar lines 539-550]:
```java
public int hashCode() {
    int result = Arrays.hashCode(this.locations);
    result = 31 * result + Arrays.hashCode(this.classes);
    result = 31 * result + this.contextInitializerClasses.hashCode();
    result = 31 * result + Arrays.hashCode(this.activeProfiles);
    result = 31 * result + this.propertySourceDescriptors.hashCode();
    result = 31 * result + Arrays.hashCode(this.propertySourceProperties);
    result = 31 * result + this.contextCustomizers.hashCode();
    result = 31 * result + (this.parent != null ? this.parent.hashCode() : 0);
    result = 31 * result + nullSafeClassName(this.contextLoader).hashCode();
    return result;
}
```
Note: **`testClass` is NOT included in `hashCode()`** (only in `toString()`). Two different test classes that share the same `@SpringBootTest` configuration (identical profiles, properties, customizers, loader) will produce the **same hash** and the **same `equals()` result** — they share a single cached context. This is exactly the "cluster" concept for PERF-03: same hash = shared context. Different hash = separate context load.

**`toString()` includes `testClass`** — so the `<mcc-display>` column in the marker file will distinguish per-test-class detail, while the hash groups them into cache-key clusters. The aggregator's grouping by hash correctly identifies clusters.

**`Integer.toHexString()` caveat:** Returns 1–8 hex chars (no leading zeros for positive values, e.g., `3fa2c1` not `003fa2c1`). For negative hash values, Java's `int` wraps to 32-bit two's complement; `Integer.toHexString(-1)` returns `ffffffff` (8 chars). This is consistent but the planner should document the format in the aggregator's comment header.

---

### RQ-8: Shell Script Conventions in This Repo

**Verified from existing scripts** [VERIFIED: codebase — `scripts/app.sh`, `scripts/serve-site.sh`, `scripts/deploy-site.sh`]:

| Convention | Value |
|------------|-------|
| Shebang | `#!/usr/bin/env bash` |
| Error handling | `set -euo pipefail` at top (all three scripts) |
| Variable quoting | Double quotes throughout (`"$var"`, `"$(cmd)"`) |
| Command substitution | `$(...)` style (not backticks) |
| Subshell dir handling | `cd "$(dirname "$0")" && pwd` pattern |
| Script location | `scripts/` (flat), no subdirectories yet |
| Comments | `#` with space, full sentence descriptions |
| No shellcheck disables | None found — scripts appear shellcheck-clean by convention |

**`scripts/test-perf/` subdirectory:** This directory does not yet exist. The planner must include a task to `mkdir -p scripts/test-perf/` and create `aggregate-fingerprints.sh` there. The new script should follow the same conventions: `#!/usr/bin/env bash`, `set -euo pipefail`, double-quoted variables.

**Aggregator script skeleton** (for planner reference):
```bash
#!/usr/bin/env bash
# Aggregate ContextCacheKeyFingerprintListener output from target/test-perf/
# and report the top-N cache-key clusters by fragmentation score
# (occurrence × cluster-size).
#
# Usage: scripts/test-perf/aggregate-fingerprints.sh [target/test-perf]
set -euo pipefail

MARKER_DIR="${1:-target/test-perf}"
TOP_N="${2:-5}"

# ... awk pipeline grouping by hex hash, counting occurrences, listing top-N
```

---

### RQ-9: `docs/test-performance.md` Structural Conventions

**Verified from existing file** [VERIFIED: codebase — `docs/test-performance.md`]:

| Element | Convention |
|---------|------------|
| Document title | `# Test Performance Log (Phase 86)` — H1, phase-anchored |
| Section headers | `## Section Name` — H2 |
| Subsections | None observed (no H3 in main sections) |
| Tables | GFM pipe-table with `| --- |` separator row |
| Code blocks | Triple-backtick with `bash` language tag |
| Inline code | Single backtick |
| Horizontal rules | `---` between major sections |
| Context load count table | `| Measurement Point | Context Loads | Run Command |` — three columns |
| Wallclock run table | `| Run | Maven Total time | bash real | Context loads | Notes |` — five columns |
| Result verdict pattern | Bold text for key numbers, `**Median: ...**` |

**New sections to be added by Plan 89-02 and 89-03:**

`§ PERF-02 Forensics` should follow the `## Context Load Counts (PERF-02)` section (which is at L195-239 of the current file). Insert between `## Context Load Counts` and `## Per-Decision Evidence`. Format:
- H2 heading: `## PERF-02 Forensics — Cache-Key Fingerprint Analysis`
- Usage block: `bash` fenced code block showing `scripts/test-perf/aggregate-fingerprints.sh`
- Example output: either a fenced `text` block or a GFM table matching the Top-5-cluster format

`§ Post-Optimization Wallclock (Wave 4)` should follow the existing `## Post-Optimization Wallclock (Wave 3)` section. Use the same five-column table format. Add bold median + delta lines below.

`§ v1.12 Forward Path` update: change Lever 1 row to include "DONE (Phase 89)" notation in the `Required Touchpoints` column; add a note below the table referencing `§ Post-Optimization Wallclock (Wave 4)` for the measured delta and `§ PERF-02 Forensics` for the fingerprint data feeding PERF-03.

**Existing aggregator loop at L233-239** (must be migrated in Plan 89-02):
```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  TOTAL=$((TOTAL + $(cat "$f")))
done
echo "Total context loads: $TOTAL"
```
After D-08 format change (Line 1 = `total <count>`, subsequent lines = hash entries), this loop will incorrectly sum multi-line file contents. Plan 89-02 must update it to:
```bash
TOTAL=0
for f in target/test-perf/context-loads-*.txt; do
  TOTAL=$((TOTAL + $(head -1 "$f" | awk '{print $2}')))
done
echo "Total context loads: $TOTAL"
```

---

## Validation Architecture

*(nyquist_validation enabled per .planning/config.json)*

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via Spring Boot 4.0.6 |
| Config file | `pom.xml` lines 266-309 (Surefire) + 290-309 (Failsafe `default-it`) |
| Quick run command | `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` |
| Full suite command | `./mvnw clean verify -Pe2e --no-transfer-progress` |

### Success Criteria → Validation Map

| SC# | Success Criterion | Validation Type | Test Class / Doc Section | Proof |
|-----|-------------------|-----------------|--------------------------|-------|
| SC-1 | `app.backup.staging-dir` resolves to per-fork path; no two concurrent forks observe same staging dir | Integration IT | `BackupStagingDirPerForkIT` | Asserts regex + fork-number property parity; 3-seed verification returns zero flakes |
| SC-2 | `BackupStagingCleanup` respects per-fork path; backup IT suite passes at `forkCount=2` without flakes | Integration IT suite | All `org.ctc.backup.**` ITs including `BackupStagingCleanupRaceIT` | 3-seed `forkCount=2` run: zero failures / zero flakes across all 27 ITs |
| SC-3 | `ContextCacheKeyFingerprintListener` dumps `<hex-hash>\t<mcc-display>` into `context-loads-{PID}.txt` | Integration IT (side-effect assertion) | New `ContextCacheKeyFingerprintListenerTest` (unit) + marker-file content assertion in test | Marker file exists, Line 1 matches `total \d+`, subsequent lines match `[0-9a-f]{1,8}\t.+` |
| SC-4 | `aggregate-fingerprints.sh` + `§ PERF-02 Forensics` in docs | Doc assertion + shellcheck | `scripts/test-perf/aggregate-fingerprints.sh` (`shellcheck` clean); `docs/test-performance.md` presence | `shellcheck scripts/test-perf/aggregate-fingerprints.sh` exits 0; doc section present |
| SC-5 | Wave-4 measurement populates `§ Post-Optimization Wallclock (Wave 4)` with delta vs. 10:24; JaCoCo ≥ 88.88 % | Manual measurement + CI gate | `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` | 3 local runs logged; JaCoCo LINE coverage ratio in `target/site/jacoco/jacoco.csv` ≥ 0.8888 |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PERF-01 | Per-fork path contract | integration | `./mvnw verify -Dit.test='BackupStagingDirPerForkIT'` | ❌ Wave 0 |
| PERF-01 | Sweep-isolation under `forkCount=2` | integration | `./mvnw verify -Dit.test='BackupStagingCleanupRaceIT'` | ❌ Wave 0 |
| PERF-01 | Full backup IT suite stability | integration | `./mvnw verify -Dit.test='org.ctc.backup.**'` | ✅ (existing ITs) |
| PERF-02 | Fingerprint listener fires + writes marker | unit | `./mvnw test -Dtest='ContextCacheKeyFingerprintListenerTest'` | ❌ Wave 0 |
| PERF-02 | Marker file format correctness | integration (side-effect) | Embedded in fingerprint listener test | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` (targeted backup IT scope)
- **Per wave merge:** `./mvnw clean verify --no-transfer-progress` (full unit + integration, no E2E)
- **Phase gate:** `./mvnw clean verify -Pe2e --no-transfer-progress` (full suite including E2E)

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` — covers SC-1 / PERF-01
- [ ] `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` — covers SC-2 / PERF-01
- [ ] `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java` — covers SC-3 / PERF-02
- [ ] `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` — new listener class (implementation)
- [ ] `scripts/test-perf/aggregate-fingerprints.sh` — covers SC-4 / PERF-02

---

## Risks & Open Questions

### Risk 1: `@DynamicPropertySource` overrides system-property precedence for some backup ITs

**What:** Several backup ITs (`BackupStagingCleanupIT`, potentially others) use `@DynamicPropertySource` to override `app.backup.staging-dir`. `@DynamicPropertySource`-backed properties are added to the `MergedContextConfiguration` as `propertySourceProperties` entries during context customization. Spring Boot's property source order places `DynamicPropertySources` above JVM system properties for `@SpringBootTest` contexts.

**Impact:** Any IT with `@DynamicPropertySource` for `app.backup.staging-dir` will NOT receive the per-fork system-property path — it will use its own `@DynamicPropertySource` value. This is likely desirable (e.g., `BackupStagingCleanupIT` manages its own `@TempDir`), but the planner must audit all 16 test files referencing `app.backup.staging-dir` and document which ones use `@DynamicPropertySource` override vs. plain `@Value` injection.

**Resolution:** Confirm `BackupStagingCleanupIT` (the only confirmed `@DynamicPropertySource` user) is intended to keep its `@TempDir` override. The 15 other `@Value`-injected ITs will automatically receive the per-fork system-property path without code changes.

### Risk 2: `MergedContextConfiguration.hashCode()` does not include `testClass`

**What:** Multiple test classes with identical Spring configuration (same profiles, properties, annotations) will produce the same `hashCode()`. The `equals()` method also does not include `testClass`. This means a "cluster" in PERF-02 terms = a group of test classes that all share a single Spring context. The aggregator script must group by hash correctly to identify these clusters.

**Impact:** Low — this is expected and desired behavior. The PERF-03 consolidation effort targets exactly these clusters. The `toString()` display in the marker file includes `testClass`, so the cluster members are identifiable in the aggregator output.

### Risk 3: SpotBugs `REFLECTIVE_ACCESS` on `ReflectionUtils.makeAccessible`

**What:** `ReflectionUtils.makeAccessible(field)` on a private field may trigger find-sec-bugs `REFLECTIVE_ACCESS` (or similar pattern). Spring Boot 4.0.6 + Java 25 has JEP-498 strong encapsulation; accessing private fields on Spring framework classes may produce a JVM warning or throw `InaccessibleObjectException`.

**Mitigation:** `DefaultTestContext` is in `org.springframework.test.context.support` — a Spring framework package, not a JDK module. Java 25 strong encapsulation applies to JDK modules, not to Spring JARs on the classpath. `setAccessible(true)` on Spring classes should succeed in Java 25. If SpotBugs fires, add targeted `@SuppressFBWarnings` per CLAUDE.md discipline. The planner should test this early in Plan 89-02.

### Risk 4: `ContextLoadCountListenerTest` may need update for extended marker format

**What:** `ContextLoadCountListenerTest` currently calls `listener.initialize(null)` and asserts `count.get()` increments. After PERF-02 adds the new `ContextCacheKeyFingerprintListener` writing to the SAME marker file, the `ContextLoadCountListener` shutdown hook changes the marker format (Line 1 = `total <N>` instead of bare integer).

**Impact:** `ContextLoadCountListenerTest` is a pure unit test that tests the in-memory counter — it doesn't test file I/O. However, the shutdown hook writes `String.valueOf(count.get())` (bare integer). After PERF-02, the shutdown hook must write `"total " + count.get()` to maintain the new Line 1 format. `ContextLoadCountListenerTest` may need an additional assertion for the file format, or the marker format migration must be clearly documented.

**Resolution:** Plan 89-02 must update `ContextLoadCountListener`'s shutdown hook to write `"total " + count.get()` AND update `ContextLoadCountListenerTest` to match. This is a small but necessary change to `ContextLoadCountListener`.

### Open Question: `aggregate-fingerprints.sh` Top-5 output format

D-09 specifies "Top-5 by occurrence × cluster size". The aggregator needs to: (1) parse `<hex-hash>\t<mcc-display>` lines from all PID-keyed marker files, (2) group by hex hash, (3) count occurrences per hash, (4) count distinct display strings per hash (= cluster size), (5) sort by `occurrence × cluster-size` descending, (6) emit top 5. This is a 3-4 pass awk/sort pipeline. The exact output format (table vs. list) is Claude's Discretion.

---

## References

### Primary (HIGH confidence)

- `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java` — VERIFIED source; `@Value("${app.backup.staging-dir}")` constructor injection; `@EventListener(ApplicationReadyEvent.class)` event timing
- `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` — VERIFIED; `@DynamicPropertySource` pattern + `publishEvent` re-trigger pattern
- `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — VERIFIED; shutdown hook writing bare integer to `target/test-perf/context-loads-{PID}.txt`
- `src/test/resources/META-INF/spring.factories` — VERIFIED; `ApplicationContextInitializer` registration; extension point for new `TestExecutionListener` line
- `pom.xml` lines 264-309 — VERIFIED; Surefire `forkCount=2 reuseForks=true`; Failsafe `default-it` current config (no `forkCount`); Failsafe `e2e-it` single-fork
- `pom.xml` line 16-24 — VERIFIED; `<properties>` block; Spring Boot 4.0.6 parent
- `src/main/resources/application.yml` lines 4-5 — VERIFIED; `app.backup.staging-dir: data/${spring.profiles.active:dev}/backup-staging`; no profile-specific YAML override found
- Surefire 3.5.5 `plugin.xml` — VERIFIED: `${surefire.forkNumber}` description: fork-time substitution, range 1..forkCount
- Failsafe 3.5.5 `plugin.xml` — VERIFIED: identical `${surefire.forkNumber}` description
- Spring 7.0.6 `DefaultContextCache.java` — VERIFIED: `Map<MergedContextConfiguration, ApplicationContext>` bucketing
- Spring 7.0.6 `MergedContextConfiguration.java` — VERIFIED: `hashCode()` implementation (excludes `testClass`); `toString()` includes `testClass`
- Spring 7.0.6 `DefaultTestContext.java` — VERIFIED: `private final MergedContextConfiguration mergedConfig`; no public getter
- Spring 7.0.6 `TestContext.java` — VERIFIED: no `getMergedContextConfiguration()` on interface
- Spring 7.0.6 `TestExecutionListener.java` — VERIFIED: `beforeTestClass(TestContext)` method; `META-INF/spring.factories` registration mechanism confirmed
- `spring-test-7.0.6.jar/META-INF/spring.factories` — VERIFIED: canonical `TestExecutionListener` key
- `spring-boot-test-4.0.6.jar/META-INF/spring.factories` — VERIFIED: no `TestExecutionListener` override
- `scripts/app.sh` — VERIFIED: shell script conventions (`#!/usr/bin/env bash`, `set -euo pipefail`, double-quoting)
- `docs/test-performance.md` — VERIFIED: section structure, table format, aggregator loop at L233-239

### Secondary (MEDIUM confidence)

- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-02-SUMMARY.md` — `@DynamicPropertySource` cache-key-fragmentation lesson; `static @TempDir` lifecycle under `PER_METHOD` vs. `PER_CLASS`
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md` — D-09 idle protocol; D-12 `ContextLoadCountListener` design

### Tertiary (LOW confidence)

- None.

---

## Metadata

**Confidence breakdown:**

| Area | Level | Reason |
|------|-------|--------|
| Maven `${surefire.forkNumber}` fork-time substitution | HIGH | Verified from Surefire 3.5.5 + Failsafe 3.5.5 `plugin.xml` in local Maven repo |
| `MergedContextConfiguration.hashCode()` = cache bucket key | HIGH | Verified from Spring 7.0.6 `DefaultContextCache` + `MergedContextConfiguration` sources |
| `TestExecutionListener` registration via `spring.factories` | HIGH | Verified from spring-test-7.0.6.jar + spring-boot-test-4.0.6.jar factories files |
| Reflection on `mergedConfig` field approach | MEDIUM | Confirmed field is private; reflection path viable; SpotBugs response on Java 25 untested |
| `BackupStagingCleanupRaceIT` shape | HIGH | Derived from existing `BackupStagingCleanupIT` patterns in codebase |
| Shell script conventions | HIGH | Verified from 3 existing scripts |
| `docs/test-performance.md` format | HIGH | Verified from the file itself |

**Research date:** 2026-05-19
**Valid until:** 2026-06-19 (Spring Boot 4.0.x / Spring 7.0.x stable; Surefire/Failsafe 3.5.x stable)
