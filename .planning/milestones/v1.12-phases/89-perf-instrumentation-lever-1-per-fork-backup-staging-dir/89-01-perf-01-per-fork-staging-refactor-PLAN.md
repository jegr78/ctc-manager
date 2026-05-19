---
phase: 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - pom.xml
  - src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java
  - src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java
autonomous: true
requirements: [PERF-01]
must_haves:
  truths:
    - "`pom.xml` Surefire `<systemPropertyVariables>` injects `app.backup.staging-dir=data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}` into every Surefire fork JVM."
    - "`pom.xml` Failsafe `default-it` execution sets `<forkCount>2</forkCount><reuseForks>true</reuseForks>` AND the same per-fork `app.backup.staging-dir` `<systemPropertyVariables>` value."
    - "`pom.xml` project-level `<properties>` defines `<surefire.forkNumber>0</surefire.forkNumber>` so non-forked invocations resolve `backup-staging-fork-0` instead of `backup-staging-fork-`."
    - "`BackupStagingDirPerForkIT` boots a `@SpringBootTest` context, injects `@Value(\"${app.backup.staging-dir}\") Path stagingDir;`, and asserts the directory-name matches regex `backup-staging-fork-\\d+`; when `System.getProperty(\"surefire.forkNumber\")` is non-null, the suffix number equals the system-property value."
    - "`BackupStagingCleanupRaceIT` re-publishes `ApplicationReadyEvent` after seeding fixture files in (a) the own per-fork dir and (b) a sibling `backup-staging-fork-99` dummy dir; sweep removes the own-fork `upload-*.zip` files; the sibling dummy directory and its files are 100 % untouched."
    - "3-seed Failsafe verification (`./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=<SEED>` with SEED ∈ {1234, 5678, 9999}) produces zero failures and zero flakes across all three runs."
    - "Legacy `data/*/backup-staging/` directories (no fork suffix) are removed via one-shot `rm -rf data/*/backup-staging` documented in the plan SUMMARY (D-06)."
    - "All existing backup ITs (`BackupStagingCleanupIT`, `BackupImportServiceIT`, `BackupRoundTripIT`, `BackupImportMariaDbSmokeIT`, etc.) continue to pass without source modification under elevated `forkCount=2`."
  artifacts:
    - path: "pom.xml"
      provides: "Surefire + Failsafe `default-it` per-fork system property + Failsafe forkCount=2 + project-level surefire.forkNumber=0 fallback"
      contains: "backup-staging-fork-${surefire.forkNumber}"
    - path: "src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java"
      provides: "Per-fork dir contract assertion IT"
      contains: "@Tag(\"integration\")"
    - path: "src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java"
      provides: "Cross-fork sweep-isolation assertion IT"
      contains: "ApplicationReadyEvent"
  key_links:
    - from: "pom.xml `<systemPropertyVariables>`"
      to: "Spring `Environment` `app.backup.staging-dir` resolution"
      via: "JVM system property at fork-dispatch time"
      pattern: "app\\.backup\\.staging-dir.*backup-staging-fork"
    - from: "BackupStagingDirPerForkIT"
      to: "the resolved per-fork path"
      via: "@Value(\"${app.backup.staging-dir}\") Path stagingDir injection"
      pattern: "backup-staging-fork-\\\\d\\+"
    - from: "BackupStagingCleanupRaceIT"
      to: "BackupStagingCleanup sweep behavior"
      via: "ApplicationContext.publishEvent(ApplicationReadyEvent)"
      pattern: "publishEvent.*ApplicationReadyEvent"
---

<objective>
PERF-01: Refactor `app.backup.staging-dir` from a shared singleton path (`data/${profile}/backup-staging`) into a per-fork variant (`data/${profile}/backup-staging-fork-${surefire.forkNumber}`) so Failsafe `default-it` can permanently run at `forkCount=2` without staging-dir races, halving the wallclock cost of the 27+ backup IT cluster.

Purpose: Land the largest single-delta v1.12 PERF lever (D-14 from Phase 86). This is the highest-risk plan in Phase 89 because elevating Failsafe `forkCount` may surface latent test-isolation bugs in non-backup ITs as well. Plan 89-01 runs FIRST so plans 89-02 and 89-03 work on a stabilised infrastructure.

Output:
- `pom.xml` edits: project-level `<surefire.forkNumber>0</surefire.forkNumber>` fallback (D-04), Surefire `<systemPropertyVariables>` injecting the per-fork path (D-03), Failsafe `default-it` `<systemPropertyVariables>` injecting the same value (D-05), Failsafe `default-it` `<forkCount>2</forkCount><reuseForks>true</reuseForks>` (D-11).
- New `BackupStagingDirPerForkIT` — single-fork self-assertion per D-12.
- New `BackupStagingCleanupRaceIT` — sibling-fork-dir sweep-isolation proof per D-17.
- Empirical cross-fork-collision proof via 3-seed Failsafe verification on all `org.ctc.backup.**` ITs per D-13.
- Legacy `data/*/backup-staging/` (no fork suffix) wiped one-shot per D-06.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md
@CLAUDE.md
@docs/test-performance.md
@pom.xml
@src/main/java/org/ctc/backup/service/BackupStagingCleanup.java
@src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java
@src/test/java/org/ctc/backup/service/BackupImportServiceIT.java

<interfaces>
<!-- Key contracts and conventions for executors. No codebase exploration needed beyond these. -->

`BackupStagingCleanup` (production `@Component`, package `org.ctc.backup.service`, package-private):
```java
@Component
@Slf4j
class BackupStagingCleanup {
    private final Path stagingDir;
    BackupStagingCleanup(@Value("${app.backup.staging-dir}") Path stagingDir) { this.stagingDir = stagingDir; }
    @EventListener(ApplicationReadyEvent.class)
    void sweepStagingDir() { /* lists stagingDir; deletes upload-*.zip + upload-*.zip.meta; logs "Cleared {N} stale staging files" */ }
}
```
(Source: src/main/java/org/ctc/backup/service/BackupStagingCleanup.java — UNCHANGED per D-14.)

`application.yml` line 4-5: `app.backup.staging-dir: data/${spring.profiles.active:dev}/backup-staging` — UNCHANGED per D-14.

`pom.xml` Surefire current config (lines 264-280, the `<configuration>` block this plan extends):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
        <forkCount>2</forkCount>
        <reuseForks>true</reuseForks>
        <excludedGroups>integration,e2e,flaky</excludedGroups>
    </configuration>
</plugin>
```

`pom.xml` Failsafe `default-it` current config (lines 281-309, the `<execution><id>default-it</id>...<configuration>` block this plan extends):
```xml
<execution>
    <id>default-it</id>
    <goals><goal>integration-test</goal><goal>verify</goal></goals>
    <configuration>
        <argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
        <groups>integration</groups>
        <excludedGroups>e2e,flaky</excludedGroups>
    </configuration>
</execution>
```

`pom.xml` current `<properties>` block (lines 16-24):
```xml
<properties>
    <java.version>25</java.version>
    <playwright.version>1.59.0</playwright.version>
    <lombok.version>1.18.46</lombok.version>
    <testcontainers.version>2.0.5</testcontainers.version>
</properties>
```

Maven property substitution constraint (RESEARCH RQ-1, HIGH confidence): `${surefire.forkNumber}` is substituted at FORK-DISPATCH time, not POM-parse time. It must NOT be wrapped in a Maven `<properties>` entry (Maven would interpolate it as an empty/missing Maven property). The full per-fork path must be LITERALLY DUPLICATED in both Surefire and Failsafe `<systemPropertyVariables>` blocks. The token `${spring.profiles.active:dev}` is a Spring SpEL placeholder that Spring resolves at runtime — it survives Maven interpolation untouched because it is not a Maven `${...}` property.

`BackupStagingCleanupIT` analog (`src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java`) — this is the analog file for `BackupStagingCleanupRaceIT`. Key load-bearing pattern (lines 79-80) is the `ApplicationReadyEvent` re-trigger:
```java
context.publishEvent(new ApplicationReadyEvent(
        new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));
```
`BackupStagingCleanupIT` currently uses `@DynamicPropertySource` + `static @TempDir` to override `app.backup.staging-dir`. `BackupStagingCleanupRaceIT` must NOT use that override — it must consume the per-fork system-property path directly via `@Value("${app.backup.staging-dir}") Path ownForkDir`.

`BackupImportServiceIT` annotation stack (`src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` lines 45-49) — this is the analog file for `BackupStagingDirPerForkIT`:
```java
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class BackupImportServiceIT { ... }
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add Maven `<properties>` fork-number fallback + Surefire + Failsafe `<systemPropertyVariables>` + Failsafe `forkCount=2` in pom.xml</name>
  <files>pom.xml</files>
  <read_first>
    - pom.xml (lines 1-50 for `<properties>` block at lines 16-24; lines 260-310 for Surefire + Failsafe `default-it` blocks; lines 436-470 for Failsafe `e2e-it` which MUST stay untouched).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md sections D-03, D-04, D-05, D-11 (all locked user decisions for this task).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § RQ-1 (Maven property substitution ordering — explains why literal duplication is required and `<properties>` extraction is unsafe).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md § `pom.xml` block for the exact XML insertion points.
  </read_first>
  <action>
    Make four edits to pom.xml. Use the Edit tool, not heredocs.

    EDIT 1 — Add fork-number fallback to project `<properties>` (D-04). Inside the existing `<properties>` block at pom.xml lines 16-24, add immediately before `</properties>`:
    ```
    <!-- PERF-01: default for non-forked invocations (IDE, ./mvnw test -Dtest=...).
         Surefire/Failsafe overwrite this at fork-dispatch time to 1, 2, ... -->
    <surefire.forkNumber>0</surefire.forkNumber>
    ```
    This guarantees that `${surefire.forkNumber}` resolves to `0` in any context where the plugin does not substitute it (e.g., IDE run, JaCoCo CSV report goal, `./mvnw versions:set`).

    EDIT 2 — Add `<systemPropertyVariables>` to Surefire `<configuration>` (D-03, D-05). Inside the existing `maven-surefire-plugin` `<configuration>` block at pom.xml lines 267-279, insert immediately after the `<reuseForks>true</reuseForks>` line (line 272) and before the `<excludedGroups>` line:
    ```
    <!-- PERF-01: per-fork app.backup.staging-dir prevents staging-dir races across forks.
         Literal duplication is required: ${surefire.forkNumber} must reach the plugin
         unsubstituted (Maven would otherwise interpolate it at POM-parse time). -->
    <systemPropertyVariables>
        <app.backup.staging-dir>data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}</app.backup.staging-dir>
    </systemPropertyVariables>
    ```
    Verbatim path: `data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}`. Do NOT extract to a shared Maven `<properties>` entry (RQ-1: Maven property interpolation would corrupt it).

    EDIT 3 — Add `<forkCount>2</forkCount><reuseForks>true</reuseForks>` + identical `<systemPropertyVariables>` to Failsafe `default-it` `<configuration>` (D-05, D-11). Inside the existing `<execution><id>default-it</id>` `<configuration>` block at pom.xml lines 298-306, insert immediately after the `<argLine>` line (line 300) and before the `<groups>integration</groups>` line:
    ```
    <!-- PERF-01: mirror Surefire forkCount=2 + per-fork staging-dir for the
         default-it execution. The e2e-it execution intentionally stays single-fork
         (Playwright requires one Spring context per RANDOM_PORT). -->
    <forkCount>2</forkCount>
    <reuseForks>true</reuseForks>
    <systemPropertyVariables>
        <app.backup.staging-dir>data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}</app.backup.staging-dir>
    </systemPropertyVariables>
    ```
    The path string is byte-identical to EDIT 2 — single source of truth conceptually, literal-duplicated for Maven correctness.

    EDIT 4 — VERIFY that the Failsafe `e2e-it` execution at pom.xml lines 436-470 is UNCHANGED (D-11). It must NOT receive `<forkCount>2</forkCount>` (Playwright/RANDOM_PORT constraint). No edit here — this is a sanity-check during the post-edit diff review.

    After all edits, run `./mvnw -q help:effective-pom -Dverbose=false | grep -A3 -B1 "backup-staging-fork"` (or read the edited pom.xml directly) to confirm BOTH Surefire and Failsafe `<configuration>` blocks contain the `<app.backup.staging-dir>` entry with the literal `${surefire.forkNumber}` token (NOT a pre-resolved value like `backup-staging-fork-0`).
  </action>
  <verify>
    <automated>
      grep -c 'data/\${spring.profiles.active:dev}/backup-staging-fork-\${surefire.forkNumber}' pom.xml
      # Must equal 2 (one in Surefire, one in Failsafe default-it).
      grep -c '<surefire.forkNumber>0</surefire.forkNumber>' pom.xml
      # Must equal 1 (the new project-level fallback).
      awk '/<execution>/,/<\/execution>/' pom.xml | grep -A2 'default-it' | grep -c 'forkCount>2'
      # Must equal 1 (Failsafe default-it forkCount=2; e2e-it stays single-fork).
      ./mvnw -q clean test-compile
      # Must exit 0 — verifies pom.xml is well-formed XML and Maven can parse it.
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: `pom.xml` contains the literal string `data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}` EXACTLY TWICE (once inside the Surefire `<configuration>` block at lines ~264-280, once inside the Failsafe `<execution><id>default-it</id>...<configuration>` block at lines ~291-309).
    - Source: `pom.xml` `<properties>` block (lines 16-24) contains one new entry `<surefire.forkNumber>0</surefire.forkNumber>` with explanatory comment.
    - Source: The Failsafe `<execution><id>default-it</id>...<configuration>` block contains `<forkCount>2</forkCount>` and `<reuseForks>true</reuseForks>`.
    - Source: The Failsafe `<execution><id>e2e-it</id>` block at pom.xml lines 436-470 is byte-identical to its pre-edit state (no `<forkCount>2</forkCount>` injected, no `<systemPropertyVariables>` injected).
    - Behavior: `./mvnw -q clean test-compile` exits 0 (pom.xml parses cleanly).
    - Behavior: Running a single backup IT (e.g., `./mvnw verify -Dit.test='BackupRoundTripIT' -Djacoco.skip=true`) succeeds and a `data/dev/backup-staging-fork-1/` (or `-fork-2/`) directory appears on disk during the test run, while NO `data/dev/backup-staging/` directory (no fork suffix) is created.
  </acceptance_criteria>
  <done>
    Both Surefire and Failsafe `default-it` configurations inject the per-fork system property; Failsafe `default-it` runs `forkCount=2 reuseForks=true`; non-forked invocations resolve to `backup-staging-fork-0` via the project-level fallback; Failsafe `e2e-it` is untouched; pom.xml parses cleanly under Maven.
  </done>
</task>

<task type="auto">
  <name>Task 2: Create `BackupStagingDirPerForkIT` (per-fork dir contract assertion)</name>
  <files>src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java</files>
  <read_first>
    - src/test/java/org/ctc/backup/service/BackupImportServiceIT.java (lines 1-80 — analog file: `@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS) @Tag("integration")` annotation stack and `@Value("${app.backup.staging-dir}")` injection pattern).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-12 (locks the assertion shape: regex match + fork-number-property parity check).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md § `BackupStagingDirPerForkIT.java` (line-by-line analog mapping).
    - CLAUDE.md § "Tag Tests by Category" (every `*IT.java` MUST carry `@Tag("integration")`) and § "Test Naming (Given-When-Then)".
  </read_first>
  <action>
    Create `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` exactly as specified. Use the Write tool, not heredocs.

    Package and class header per `BackupImportServiceIT` analog:
    - `package org.ctc.backup.service;`
    - Class-level annotations IN THIS EXACT ORDER:
      ```
      @SpringBootTest
      @ActiveProfiles("dev")
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      @Tag("integration")
      ```
    - Class name: `BackupStagingDirPerForkIT` (package-private; matches `BackupImportServiceIT` visibility).
    - Javadoc on class: brief 3-4 sentences referencing PERF-01 (D-12) — explain that this IT proves the per-fork `app.backup.staging-dir` contract: the resolved path must be of the shape `backup-staging-fork-<N>` and N must equal the JVM's `surefire.forkNumber` system property (when set).

    Field injection:
    ```
    @Value("${app.backup.staging-dir}")
    Path stagingDir;
    ```
    (Inject as `java.nio.file.Path` directly — Spring's converter handles String→Path. Do NOT use `String stagingDirRaw` + manual conversion; per PATTERNS § "What to adapt".)

    Test method 1 — regex assertion (always runs):
    ```
    @Test
    void givenSurefireFork_whenContextResolvesStagingDir_thenPathHasForkSuffix() {
        // given — context booted by @SpringBootTest, stagingDir injected

        // then — the resolved directory name must match the per-fork pattern.
        assertThat(stagingDir.getFileName().toString())
                .matches("backup-staging-fork-\\d+");
    }
    ```
    Static AssertJ import: `import static org.assertj.core.api.Assertions.assertThat;`.

    Test method 2 — fork-number parity assertion (conditional on `surefire.forkNumber` being set):
    ```
    @Test
    void givenSurefireForkNumberSet_whenContextResolvesStagingDir_thenPathSuffixMatchesForkNumber() {
        // given
        String forkNum = System.getProperty("surefire.forkNumber");

        // then — only assert parity when the JVM was launched by Surefire/Failsafe.
        // Under IDE / direct -Dtest invocations the property is null; per D-12 we
        // silently pass (the regex assertion in test 1 still guards the shape).
        if (forkNum != null && !forkNum.isBlank()) {
            assertThat(stagingDir.getFileName().toString())
                    .endsWith("-" + forkNum);
        }
    }
    ```

    Imports — derive from `BackupImportServiceIT` and reduce to only what is needed:
    - `java.nio.file.Path`
    - `org.junit.jupiter.api.Tag`
    - `org.junit.jupiter.api.Test`
    - `org.junit.jupiter.api.TestInstance`
    - `org.springframework.beans.factory.annotation.Value`
    - `org.springframework.boot.test.context.SpringBootTest`
    - `org.springframework.test.context.ActiveProfiles`
    - static `org.assertj.core.api.Assertions.assertThat`

    Use Given/When/Then comment structure inside method bodies (per CLAUDE.md). No `@BeforeAll`/`@BeforeEach`. No `@Autowired` services. Pure property-value assertion test.
  </action>
  <verify>
    <automated>
      ./mvnw verify -Dit.test='BackupStagingDirPerForkIT' -Djacoco.skip=true -DfailIfNoTests=true
      # Must exit 0; both test methods pass on Failsafe fork JVMs.
      grep -c '@Tag("integration")' src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java
      # Must equal 1 (CLAUDE.md tag convention).
      grep -c 'backup-staging-fork-\\\\d+' src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java
      # Must equal 1 (regex assertion present).
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: file exists at `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java`.
    - Source: class has the four-annotation stack `@SpringBootTest @ActiveProfiles("dev") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @Tag("integration")` in that exact order.
    - Source: contains `@Value("${app.backup.staging-dir}") Path stagingDir;` field.
    - Source: contains a test method asserting `stagingDir.getFileName().toString()` matches regex `backup-staging-fork-\d+`.
    - Source: contains a second test method asserting suffix parity with `System.getProperty("surefire.forkNumber")` when the property is non-null/non-blank.
    - Behavior: `./mvnw verify -Dit.test='BackupStagingDirPerForkIT' -Djacoco.skip=true -DfailIfNoTests=true` exits 0.
    - Behavior: Both test methods green; the second method passes both when Surefire injects `surefire.forkNumber` (Failsafe forks 1 or 2) and when run via IDE (property null → conditional skip).
  </acceptance_criteria>
  <done>
    `BackupStagingDirPerForkIT` exists, follows the canonical `@Tag("integration")` IT shape, asserts the per-fork dir contract via regex + fork-number parity, and passes under Failsafe `forkCount=2`.
  </done>
</task>

<task type="auto">
  <name>Task 3: Create `BackupStagingCleanupRaceIT` (sibling-fork sweep-isolation proof)</name>
  <files>src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java</files>
  <read_first>
    - src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java (full file — analog for the `ApplicationReadyEvent` re-trigger pattern at lines 79-80 and the package-private `@Autowired BackupStagingCleanup cleanup` access pattern).
    - src/main/java/org/ctc/backup/service/BackupStagingCleanup.java (full file — confirms the sweep targets `upload-*.zip` + `upload-*.zip.meta` only, and that the listener fires on `ApplicationReadyEvent`).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-17 (locks the sibling-dummy-fork-99 dir pattern and the sweep-isolation contract).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § RQ-6 (full IT shape recommendation including `@BeforeAll setUp` + `@AfterAll tearDown` lifecycle).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md § `BackupStagingCleanupRaceIT.java` (load-bearing structural pattern: keep `publishEvent` re-trigger; drop `@DynamicPropertySource` + `@TempDir`).
  </read_first>
  <action>
    Create `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java`. Use the Write tool.

    Package: `org.ctc.backup.service` (MUST be same package as `BackupStagingCleanup` since it is package-private — per existing `BackupStagingCleanupIT` pattern).

    Class-level annotations IN THIS EXACT ORDER:
    ```
    @SpringBootTest
    @ActiveProfiles("dev")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("integration")
    ```
    (`PER_CLASS` enables instance-method `@BeforeAll`/`@AfterAll` per the JUnit 5 contract — RESEARCH § RQ-6.)

    Class name: `BackupStagingCleanupRaceIT` (package-private). Javadoc: 3-4 sentences referencing PERF-01 D-17 — "Proves that `BackupStagingCleanup.sweepStagingDir()` operates exclusively on the per-fork dir it received via `@Value` injection and does NOT touch a sibling fork's directory. Sibling dir uses fork number 99 (never assigned under realistic `forkCount` ≤ 32, per D-17)."

    Fields:
    ```
    @Value("${app.backup.staging-dir}")
    Path ownForkDir;

    @Autowired
    ApplicationContext context;

    @Autowired
    BackupStagingCleanup cleanup;     // package-private access — same package required

    private Path siblingForkDir;      // constructed in @BeforeAll
    ```

    `@BeforeAll` (instance method, no `static` — `PER_CLASS` lifecycle):
    ```
    @BeforeAll
    void setUp() throws IOException {
        // Construct sibling dir at: <parent-of-own>/backup-staging-fork-99
        siblingForkDir = ownForkDir.getParent().resolve("backup-staging-fork-99");
        Files.createDirectories(ownForkDir);
        Files.createDirectories(siblingForkDir);
    }
    ```

    Test method (single test):
    ```
    @Test
    void givenStaleFilesInOwnAndSiblingForkDirs_whenSweepFires_thenOnlyOwnForkFilesRemoved() throws IOException {
        // given — seed own-fork dir with one stale upload-*.zip + one upload-*.zip.meta
        Path ownStale = ownForkDir.resolve("upload-" + UUID.randomUUID() + ".zip");
        Path ownStaleMeta = ownForkDir.resolve("upload-" + UUID.randomUUID() + ".zip.meta");
        Files.write(ownStale, new byte[]{0x50, 0x4B, 0x05, 0x06}); // empty-zip EOCD
        Files.write(ownStaleMeta, "{\"upload\":\"meta\"}".getBytes());

        // and seed sibling-fork dir with the same shape — these MUST survive the sweep
        Path siblingFile = siblingForkDir.resolve("upload-" + UUID.randomUUID() + ".zip");
        Files.write(siblingFile, new byte[]{0x50, 0x4B, 0x05, 0x06});

        // when — re-trigger ApplicationReadyEvent (analog: BackupStagingCleanupIT line 79-80)
        context.publishEvent(new ApplicationReadyEvent(
                new SpringApplication(), new String[]{},
                (ConfigurableApplicationContext) context, Duration.ZERO));

        // then — own-fork stale files removed
        assertThat(Files.exists(ownStale)).isFalse();
        assertThat(Files.exists(ownStaleMeta)).isFalse();

        // and — sibling-fork file completely untouched
        assertThat(Files.exists(siblingFile)).isTrue();
        try (Stream<Path> stream = Files.list(siblingForkDir)) {
            assertThat(stream.count()).isEqualTo(1L);
        }
    }
    ```

    `@AfterAll` (instance method, recursive sibling-dir cleanup):
    ```
    @AfterAll
    void tearDown() throws IOException {
        if (siblingForkDir != null && Files.isDirectory(siblingForkDir)) {
            try (Stream<Path> stream = Files.walk(siblingForkDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException e) { /* best-effort cleanup */ }
                });
            }
        }
    }
    ```

    Required imports:
    - `java.io.IOException`
    - `java.nio.file.Files`
    - `java.nio.file.Path`
    - `java.time.Duration`
    - `java.util.Comparator`
    - `java.util.UUID`
    - `java.util.stream.Stream`
    - `org.junit.jupiter.api.AfterAll`
    - `org.junit.jupiter.api.BeforeAll`
    - `org.junit.jupiter.api.Tag`
    - `org.junit.jupiter.api.Test`
    - `org.junit.jupiter.api.TestInstance`
    - `org.springframework.beans.factory.annotation.Autowired`
    - `org.springframework.beans.factory.annotation.Value`
    - `org.springframework.boot.SpringApplication`
    - `org.springframework.boot.context.event.ApplicationReadyEvent`
    - `org.springframework.boot.test.context.SpringBootTest`
    - `org.springframework.context.ApplicationContext`
    - `org.springframework.context.ConfigurableApplicationContext`
    - `org.springframework.test.context.ActiveProfiles`
    - static `org.assertj.core.api.Assertions.assertThat`

    NOTE: do NOT use `@ExtendWith(OutputCaptureExtension.class)` / `CapturedOutput` — this IT verifies filesystem state, not log output (PATTERNS § "What to adapt").

    NOTE: do NOT use `@DynamicPropertySource` or `@TempDir` — the IT consumes the per-fork system-property path directly to actually exercise the per-fork mechanism (per D-17 and PATTERNS § "What to adapt").
  </action>
  <verify>
    <automated>
      ./mvnw verify -Dit.test='BackupStagingCleanupRaceIT' -Djacoco.skip=true -DfailIfNoTests=true
      # Must exit 0; sweep-isolation contract proven.
      grep -c 'backup-staging-fork-99' src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java
      # Must equal 1 (sibling-fork-dir name per D-17).
      grep -c '@DynamicPropertySource\|@TempDir' src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java
      # Must equal 0 (must use per-fork system-property path, not a TempDir override).
    </automated>
  </verify>
  <acceptance_criteria>
    - Source: file exists at `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` in package `org.ctc.backup.service`.
    - Source: class has annotation stack `@SpringBootTest @ActiveProfiles("dev") @TestInstance(TestInstance.Lifecycle.PER_CLASS) @Tag("integration")`.
    - Source: contains `@Value("${app.backup.staging-dir}") Path ownForkDir;` plus `@Autowired ApplicationContext context;` plus `@Autowired BackupStagingCleanup cleanup;`.
    - Source: `@BeforeAll setUp()` resolves sibling dir as `ownForkDir.getParent().resolve("backup-staging-fork-99")`.
    - Source: test method seeds own-fork dir (with `upload-<UUID>.zip` + `upload-<UUID>.zip.meta`), seeds sibling-fork dir (with `upload-<UUID>.zip`), re-publishes `ApplicationReadyEvent`, asserts own-fork files removed AND sibling file present + sibling dir count == 1.
    - Source: `@AfterAll tearDown()` recursively removes sibling-fork-99 dir.
    - Source: NO `@DynamicPropertySource` and NO `@TempDir` (the IT MUST use the per-fork system-property path).
    - Behavior: `./mvnw verify -Dit.test='BackupStagingCleanupRaceIT' -Djacoco.skip=true -DfailIfNoTests=true` exits 0.
  </acceptance_criteria>
  <done>
    `BackupStagingCleanupRaceIT` proves sweep-isolation under `forkCount=2` by seeding a sibling `backup-staging-fork-99` dir, re-publishing `ApplicationReadyEvent`, and asserting the sweep leaves the sibling dir 100 % untouched while removing the own-fork stale uploads.
  </done>
</task>

<task type="auto">
  <name>Task 4: 3-seed Failsafe verification + legacy backup-staging cleanup</name>
  <files>(no source files — verification + filesystem cleanup; outcomes recorded in plan SUMMARY)</files>
  <read_first>
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-06 (legacy-dir one-shot cleanup) and D-13 (3-seed verification scope = all `org.ctc.backup.**` ITs, seeds 1234/5678/9999).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md § RQ-5 (Failsafe `forkCount=2 + reuseForks=true` known pitfalls — confirms `@DirtiesContext`, port collisions, and shared filesystem state are already addressed).
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md § Sampling Rate (3-seed proof is the SC-2 acceptance gate).
    - CLAUDE.md § "Test-Aufrufe optimieren" — one targeted command per seed; this task is intentionally three explicit invocations (NOT a Maven loop) so each seed gets a distinct surefire-reports artifact.
  </read_first>
  <action>
    Two distinct sub-actions; perform in order.

    SUB-ACTION 1 — Legacy `data/*/backup-staging/` cleanup (D-06). Run ONCE:
    ```
    rm -rf data/dev/backup-staging data/local/backup-staging data/docker/backup-staging data/prod/backup-staging 'data/dev,demo/backup-staging' 2>/dev/null || true
    ```
    The non-fork-suffixed legacy dirs are gitignored (`.gitignore` line 59 covers `data/`); this is dev-machine hygiene only. Confirm via `find data -type d -name 'backup-staging' -not -name 'backup-staging-fork-*'` returning empty.

    SUB-ACTION 2 — 3-seed Failsafe verification (D-13). Run three explicit invocations, in sequence, on the current working tree (post Tasks 1-3 merged):
    ```
    ./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 -Djacoco.skip=true
    ./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678 -Djacoco.skip=true
    ./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999 -Djacoco.skip=true
    ```
    Each command MUST exit 0 with zero failures, zero errors, zero flakes. If ANY seed produces a failure or flake, do NOT proceed to the plan SUMMARY — instead, capture the failure stack + `target/failsafe-reports/*-output.txt` for the failing IT, append a `## Flake Diagnostic` section to the plan SUMMARY, and STOP for user review. Per CLAUDE.md `[[no-flaky-dismissal]]`: tests that were green before this plan and fail now are regressions, not "flaky" — find the root cause.

    Record the outcome in the plan SUMMARY (under a `## 3-Seed Failsafe Verification` section): for each seed, list seed-number + total tests run + passed/failed counts + wallclock for the verify step.

    Coverage of all `org.ctc.backup.**` ITs (per D-13 enumeration): `BackupRoundTripIT`, `BackupImportMariaDbSmokeIT`, `BackupImportRollbackIT`, `BackupRestoreZipOpenCountIT`, `ImportConcurrentLockIT`, `ImportLockBannerAdviceIT`, `ImportLockedPostRejectorIT`, `BackupImportServiceIT`, `BackupImportConfirmFormValidationIT`, `BackupImportSchemaMismatchIT`, `BackupUploadsMirrorIT`, `BackupExportServiceIT`, `BackupSchemaTopologyIT`, `BackupSchemaExclusionIT`, `BackupControllerSecurityIT`, `BackupImportControllerSecurityIT`, `BackupControllerIT`, `BackupObjectMapperConfigIT`, `BackupEntityAnnotationCleanlinessIT`, `BackupRepositoryEntityGraphIT`, `AutoBackupBeforeImportPathIT`, `AutoBackupCatchOrderIT`, `AutoBackupBeforeImportFailureIT`, `AdminLayoutIT`, plus the two new ITs (`BackupStagingDirPerForkIT`, `BackupStagingCleanupRaceIT`).
  </action>
  <verify>
    <automated>
      # Sub-action 1 — legacy dirs cleaned
      test -z "$(find data -type d -name 'backup-staging' -not -name 'backup-staging-fork-*' 2>/dev/null)"
      # Must exit 0 (no non-fork-suffixed dirs remain).

      # Sub-action 2 — 3-seed proof (each exits 0, zero failures)
      ./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 -Djacoco.skip=true
      ./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678 -Djacoco.skip=true
      ./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999 -Djacoco.skip=true
    </automated>
  </verify>
  <acceptance_criteria>
    - Behavior: After sub-action 1, `find data -type d -name 'backup-staging' -not -name 'backup-staging-fork-*'` produces zero lines.
    - Behavior: Each of the three `./mvnw verify ... -Dsurefire.runOrder.random.seed=<SEED>` invocations exits 0 with "BUILD SUCCESS" and Failsafe summary "Tests run: <N>, Failures: 0, Errors: 0, Skipped: 0" across all backup ITs.
    - Documentation: Plan SUMMARY contains a `## 3-Seed Failsafe Verification` section with per-seed test-counts and wallclock; SUMMARY confirms zero flakes across all three runs.
    - Documentation: Plan SUMMARY contains a `## Legacy Cleanup (D-06)` note documenting the `rm -rf` of non-fork-suffixed dirs.
  </acceptance_criteria>
  <done>
    Legacy `data/*/backup-staging/` (no fork suffix) directories are removed locally; the 3-seed Failsafe verification on `org.ctc.backup.**` under elevated `forkCount=2` is green across all three seeds (1234, 5678, 9999) with zero failures and zero flakes; outcomes recorded in plan SUMMARY.
  </done>
</task>

</tasks>

<verification>
Full-plan gate (post-Tasks-1-through-4):
- `./mvnw -q clean test-compile` exits 0 (pom.xml well-formed).
- `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true` exits 0 with all backup ITs green (deterministic order — covers the standard Failsafe runOrder).
- 3-seed verification (Task 4 sub-action 2) — three explicit runs at seeds 1234/5678/9999, each exits 0 with zero failures/flakes.
- All existing backup ITs (`BackupStagingCleanupIT`, `BackupImportServiceIT`, `BackupRoundTripIT`, etc.) continue to pass without source modification. `BackupStagingCleanupIT`'s existing `@DynamicPropertySource` + `@TempDir` override mechanism is unaffected (per RESEARCH § RQ-5: `@DynamicPropertySource` has higher precedence than JVM system properties for `@SpringBootTest` contexts).
- No `application.yml` / `application-{prod,dev,local,docker}.yml` changes (D-14).
- No `CLAUDE.md` / `docs/operations/import-runbook.md` changes (D-16).
</verification>

<success_criteria>
PERF-01 satisfied when:
- pom.xml carries the per-fork `app.backup.staging-dir` system property in both Surefire and Failsafe `default-it` `<systemPropertyVariables>` blocks (literal duplication per RQ-1), Failsafe `default-it` is `forkCount=2 reuseForks=true`, and the project-level `<surefire.forkNumber>0</surefire.forkNumber>` fallback is in place.
- `BackupStagingDirPerForkIT` and `BackupStagingCleanupRaceIT` exist with the canonical `@Tag("integration") @SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS)` annotation stack, prove the per-fork dir contract and the sibling-fork sweep-isolation contract respectively, and both pass under Failsafe `forkCount=2`.
- 3-seed Failsafe verification on `org.ctc.backup.**` (seeds 1234/5678/9999) returns zero failures and zero flakes across all three runs.
- Legacy non-fork-suffixed `data/*/backup-staging/` directories removed.
- Production behavior unchanged: `application.yml` and all `application-{prod,dev,local,docker}.yml` files are byte-identical to pre-plan state.
</success_criteria>

<output>
Create `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` when done, including:

- `## Changes` — pom.xml edits (4 distinct edits enumerated); two new IT class files.
- `## 3-Seed Failsafe Verification` — table with rows per seed (1234/5678/9999) listing test count, pass/fail/skipped, wallclock for the verify step.
- `## Legacy Cleanup (D-06)` — one-line note confirming legacy `data/*/backup-staging/` dirs removed.
- `## Acceptance` — explicit confirmation each acceptance criterion in `<must_haves>` is met.
- `## Forward Path` — Plan 89-02 (PERF-02 fingerprint listener) now unblocked.
</output>
