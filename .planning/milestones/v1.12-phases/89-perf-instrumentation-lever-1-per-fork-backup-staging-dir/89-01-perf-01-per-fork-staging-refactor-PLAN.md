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
  - src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java
autonomous: true
requirements:
  - PERF-01
user_setup: []

tags:
  - perf
  - test-infra
  - surefire
  - failsafe
  - backup-staging

must_haves:
  truths:
    - "Both Surefire AND Failsafe `default-it` forked JVMs receive `app.backup.staging-dir=data/<profile>/backup-staging-fork-<N>` resolved with the actual fork number (1 or 2 for forkCount=2), proving the per-fork mechanism engaged"
    - "Both Surefire AND Failsafe `default-it` forked JVMs receive `app.backup.import-backups-dir=data/<profile>/import-backups-fork-<N>` per D-18 (second shared filesystem path now per-fork)"
    - "JVM-side `System.getProperty(\"surefire.forkNumber\")` returns the same fork number that appears in the resolved staging-dir path (D-04R.2 — Test 2 of BackupStagingDirPerForkIT fires non-vacuously)"
    - "Failsafe `default-it` runs with `<forkCount>2</forkCount><reuseForks>true</reuseForks>` per D-11; Failsafe `e2e-it` stays single-fork per D-11 (unchanged)"
    - "`BackupStagingCleanup` (production component, unmodified) sweeps only the per-fork dir it was injected with; sibling fork dirs are untouched (proved by `BackupStagingCleanupRaceIT`)"
    - "`ImportLockedPostRejectorIT` passes under `forkCount=2 + reuseForks=true` — D-19 lock-acquisition-timeout root-cause analysis recorded in the source patch / comment"
    - "3-seed Failsafe verification on `org.ctc.backup.**` (seeds 1234 / 5678 / 9999) produces zero failures and zero flakes — empirical cross-fork-collision proof per D-13"
    - "Legacy `data/*/backup-staging/` (no fork suffix) is removed via one-shot `rm -rf` per D-06; production `application.yml` is UNCHANGED per D-14"
  artifacts:
    - path: "pom.xml"
      provides: "Per-fork `<systemPropertyVariables>` injection for Surefire AND Failsafe default-it (staging-dir, import-backups-dir, surefire.forkNumber); Failsafe default-it `forkCount=2 reuseForks=true`; NO project-level `<surefire.forkNumber>` fallback"
      contains: "backup-staging-fork-${surefire.forkNumber}"
    - path: "src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java"
      provides: "Single-fork self-assertion IT — regex match on resolved dir name + non-vacuous JVM-side `surefire.forkNumber` parity check (D-12 CLARIFIED)"
      contains: "backup-staging-fork-\\\\d+"
    - path: "src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java"
      provides: "Sweep-isolation IT — seeds own-fork dir + sibling `-fork-99` dummy dir, re-publishes `ApplicationReadyEvent`, asserts own-fork swept while sibling untouched (D-17)"
      contains: "backup-staging-fork-99"
    - path: "src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java"
      provides: "Root-cause patch OR deadline-bump-with-justification for the D-19 lock-acquisition-timeout regression under forkCount=2 + reuseForks=true"
      contains: "hasAcquired.await"
  key_links:
    - from: "pom.xml maven-surefire-plugin <configuration>"
      to: "JVM-side System.getProperty(\"app.backup.staging-dir\")"
      via: "<systemPropertyVariables> injection at fork-dispatch time"
      pattern: "<app\\.backup\\.staging-dir>data/\\$\\{spring\\.profiles\\.active:dev\\}/backup-staging-fork-\\$\\{surefire\\.forkNumber\\}</app\\.backup\\.staging-dir>"
    - from: "pom.xml maven-failsafe-plugin default-it execution <configuration>"
      to: "JVM-side System.getProperty(\"app.backup.import-backups-dir\")"
      via: "<systemPropertyVariables> injection at fork-dispatch time"
      pattern: "<app\\.backup\\.import-backups-dir>data/\\$\\{spring\\.profiles\\.active:dev\\}/import-backups-fork-\\$\\{surefire\\.forkNumber\\}</app\\.backup\\.import-backups-dir>"
    - from: "pom.xml maven-failsafe-plugin default-it execution <configuration>"
      to: "Failsafe-orchestrated fork dispatch"
      via: "<forkCount>2</forkCount><reuseForks>true</reuseForks>"
      pattern: "<forkCount>2</forkCount>"
    - from: "BackupStagingDirPerForkIT.parityTestMethod"
      to: "JVM System.getProperty(\"surefire.forkNumber\")"
      via: "third <systemPropertyVariables> entry exposes the placeholder to the JVM per D-04R.2"
      pattern: "<surefire\\.forkNumber>\\$\\{surefire\\.forkNumber\\}</surefire\\.forkNumber>"
---

<objective>
PERF-01 Lever-1 refactor: replace the singleton `app.backup.staging-dir` and `app.backup.import-backups-dir` paths with per-fork variants resolved via Surefire/Failsafe `<systemPropertyVariables>`, elevate Failsafe `default-it` to `forkCount=2 + reuseForks=true`, prove the per-fork mechanism with two new assertion ITs, fix the D-19 lock-acquisition-timeout regression in `ImportLockedPostRejectorIT`, and empirically validate cross-fork-collision absence with a 3-seed Failsafe verification on the entire `org.ctc.backup.**` IT suite.

Purpose: Largest single-delta v1.12 wallclock lever (CONTEXT D-01 highest-risk-first). Without per-fork isolation the `data/dev/backup-staging/` singleton blocks Failsafe `forkCount>1` (the `forkCount=2` path was reverted in Plan 89-01 Attempt 1 — see `89-FLAKE-DIAGNOSTIC.md`). This re-plan addresses all 4 empirical findings from that flake diagnostic: (1) Maven eager-substitution of project-property fallback → no `<surefire.forkNumber>0</surefire.forkNumber>` in `<properties>`; (2) Surefire 3.5.5 does not auto-expose `surefire.forkNumber` to the JVM → explicit `<systemPropertyVariables>` entry; (3) `app.backup.import-backups-dir` is a second shared path → identical per-fork treatment; (4) `ImportLockedPostRejectorIT` lock-acquisition deadline → root-cause investigation + targeted fix.

Output: Updated `pom.xml`; two new assertion ITs in `org.ctc.backup.service`; targeted patch (or justified deadline bump) in `ImportLockedPostRejectorIT`; legacy `data/*/backup-staging/` directories swept on the dev machine; 3-seed proof recorded in SUMMARY. `application.yml` is UNTOUCHED (D-14 invariant — production data layout unaffected). `BackupStagingCleanup` source is UNTOUCHED (resolved `@Value` already does the right thing).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-FLAKE-DIAGNOSTIC.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-RESEARCH.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md
@.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md
@.planning/codebase/TESTING.md
@CLAUDE.md

<interfaces>
<!-- Key contracts extracted from codebase + Surefire/Failsafe 3.5.5 plugin.xml — RESEARCH §RQ-1, §RQ-5. Executor uses these directly. -->

Surefire/Failsafe 3.5.5 fork-time placeholder (RESEARCH RQ-1, VERIFIED in plugin.xml):
- `${surefire.forkNumber}` is resolved by the plugin at fork-dispatch time inside `<systemPropertyVariables>` and `<argLine>`. Range: 1..forkCount when forking, 0 if forkCount=0.
- CRITICAL: a project-level `<properties><surefire.forkNumber>...</surefire.forkNumber></properties>` entry causes Maven to eager-substitute at POM-load time, BEFORE the plugin runs its fork-dispatch substitution. This was the Attempt 1 failure mode. Per D-04R, DO NOT add such a fallback.
- Surefire 3.5.5 does NOT auto-inject `surefire.forkNumber` as a JVM `System.getProperty(...)`-visible property. Per D-04R.2, the JVM side needs an explicit `<surefire.forkNumber>${surefire.forkNumber}</surefire.forkNumber>` entry inside `<systemPropertyVariables>`.

Spring property-source precedence (RESEARCH §RQ-1 "Substitution precedence check"):
- JVM `System.getProperty(...)` set via `<systemPropertyVariables>` is picked up by Spring's `SystemEnvironmentPropertySource` chain at HIGHER precedence than `application*.yml` defaults — no custom `@PropertySource` required.
- `@DynamicPropertySource` in `BackupStagingCleanupIT` overrides BOTH — that IT continues to manage its own `@TempDir` and is unaffected by this plan (RESEARCH §RQ-5).

`BackupStagingCleanup` production component (READ-ONLY for this plan, src/main/java/org/ctc/backup/service/BackupStagingCleanup.java):
```java
@Component
class BackupStagingCleanup {
    private final Path stagingDir;
    BackupStagingCleanup(@Value("${app.backup.staging-dir}") Path stagingDir) { this.stagingDir = stagingDir; }

    @EventListener(ApplicationReadyEvent.class)
    void sweepStagingDir() { /* operates exclusively on this.stagingDir, package-visible deletion of upload-*.zip / upload-*.zip.meta */ }
}
```
After this plan: `this.stagingDir` IS the per-fork path because `<systemPropertyVariables>` overrides the `application.yml` default in test JVMs.

`ImportLockService` (READ-ONLY for this plan, src/main/java/org/ctc/backup/lock/ImportLockService.java):
- `@Service @Scope("singleton")` — Spring-managed singleton, NOT a `static` field. State lives on the Spring context, so a fresh context = fresh lock.
- Per-fork JVM = per-fork Spring context (under `forkCount=2 + reuseForks=true` the SAME context can be reused across test classes within one fork JVM). State leak risk: a prior test class in the same fork left the lock held without `releaseLatch.countDown()` finishing. The reset helper at `ImportLockServiceResetHelper` is already wired in `@AfterEach`.
- `tryLock()` is non-blocking (zero timeout); the IT's 10s deadline at line 212-214 is on `CountDownLatch hasAcquired.await(10, TimeUnit.SECONDS)` — that is the SIGNAL that thread A successfully entered the blocking-restore-failure-injector, not on the ReentrantLock itself.

`BackupStagingCleanupIT` (RESEARCH §RQ-6 reference) — re-publishes `ApplicationReadyEvent` to retrigger the sweep AFTER fixture seeding:
```java
context.publishEvent(new ApplicationReadyEvent(
    new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));
```
Copy this pattern verbatim into `BackupStagingCleanupRaceIT`.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: pom.xml — per-fork `<systemPropertyVariables>` injection + Failsafe forkCount=2 (no project-property fallback)</name>
  <files>pom.xml</files>
  <requirement>PERF-01</requirement>
  <read_first>
    - pom.xml lines 16-24 (existing `<properties>` block — MUST NOT add `<surefire.forkNumber>` here per D-04R)
    - pom.xml lines 264-280 (existing Surefire `<configuration>` — add `<systemPropertyVariables>` after `<reuseForks>true</reuseForks>`)
    - pom.xml lines 281-309 (existing Failsafe `default-it` execution — add `<forkCount>2</forkCount><reuseForks>true</reuseForks>` AND `<systemPropertyVariables>` block)
    - pom.xml lines 436-465 (Failsafe `e2e-it` execution — UNTOUCHED; stays single-fork per D-11)
    - src/main/resources/application.yml lines 4-6 (current shared paths — UNTOUCHED per D-14; the per-fork override happens via JVM system properties only)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-FLAKE-DIAGNOSTIC.md Findings 1, 2, 3 (load-bearing — explains why D-04 was superseded by D-04R and why D-18 was added)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md lines 161-235 (pom.xml pattern excerpt — literal duplication, no `<properties>` extraction)
  </read_first>
  <action>
    Edit `pom.xml` (no other files).

    Surefire plugin `<configuration>` (currently lines 267-279): after the existing `<reuseForks>true</reuseForks>` line and before `<excludedGroups>integration,e2e,flaky</excludedGroups>`, insert a `<systemPropertyVariables>` block containing THREE entries (literal text, NOT extracted to `<properties>`):
    - `<app.backup.staging-dir>data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}</app.backup.staging-dir>` (D-03/D-04R)
    - `<app.backup.import-backups-dir>data/${spring.profiles.active:dev}/import-backups-fork-${surefire.forkNumber}</app.backup.import-backups-dir>` (D-18 NEW — second shared path)
    - `<surefire.forkNumber>${surefire.forkNumber}</surefire.forkNumber>` (D-04R.2 — exposes the placeholder to the JVM as a `System.getProperty(...)`-visible property; without this Test 2 of `BackupStagingDirPerForkIT` passes vacuously per FLAKE-DIAGNOSTIC Finding 2)

    Failsafe plugin `default-it` execution `<configuration>` (currently lines 298-306): add IDENTICAL three-entry `<systemPropertyVariables>` block. Additionally, before `<groups>integration</groups>`, add `<forkCount>2</forkCount>` and `<reuseForks>true</reuseForks>` (D-11). Order inside `<configuration>`: existing `<argLine>` first, then NEW `<forkCount>` + `<reuseForks>` + `<systemPropertyVariables>`, then existing `<groups>integration</groups>` + `<excludedGroups>e2e,flaky</excludedGroups>`.

    Failsafe `e2e-it` execution (pom.xml lines 436-465, inside `<profile id="e2e">`): UNTOUCHED. e2e tests stay single-fork per D-11 (Playwright RANDOM_PORT constraint).

    Project-level `<properties>` block (lines 16-24): UNTOUCHED. Per D-04R, DO NOT add `<surefire.forkNumber>0</surefire.forkNumber>` — Maven would eager-substitute it from POM-load time, defeating the plugin's fork-dispatch substitution (Attempt 1 failure mode, FLAKE-DIAGNOSTIC Finding 1).

    `application.yml` and `application-{dev,local,docker,prod}.yml`: UNTOUCHED. D-14 invariant.
  </action>
  <verify>
    <automated>
      Run these verification checks (each must exit 0):
      1. `./mvnw -q clean test-compile` exits 0 (pom.xml well-formed; compile baseline preserved).
      2. `grep -c '&lt;app\.backup\.staging-dir&gt;data/\${spring\.profiles\.active:dev}/backup-staging-fork-\${surefire\.forkNumber}&lt;/app\.backup\.staging-dir&gt;' pom.xml` returns 2 (one for Surefire, one for Failsafe default-it).
      3. `grep -c '&lt;app\.backup\.import-backups-dir&gt;data/\${spring\.profiles\.active:dev}/import-backups-fork-\${surefire\.forkNumber}&lt;/app\.backup\.import-backups-dir&gt;' pom.xml` returns 2.
      4. `grep -c '&lt;surefire\.forkNumber&gt;\${surefire\.forkNumber}&lt;/surefire\.forkNumber&gt;' pom.xml` returns 2 (D-04R.2 — JVM-side exposure for both plugins).
      5. ANTI-REGRESSION: `awk '/&lt;properties&gt;/,/&lt;\/properties&gt;/' pom.xml | grep -c 'surefire\.forkNumber'` returns 0 (D-04R — no project-property fallback inside `<properties>`).
      6. `grep -c '&lt;forkCount&gt;2&lt;/forkCount&gt;' pom.xml` returns 2 (Surefire existing + Failsafe default-it new).
      7. `grep -c '&lt;reuseForks&gt;true&lt;/reuseForks&gt;' pom.xml` returns 2 (Surefire existing + Failsafe default-it new).
      8. `git diff --stat src/main/resources/application.yml src/main/resources/application-dev.yml src/main/resources/application-local.yml src/main/resources/application-docker.yml src/main/resources/application-prod.yml` shows zero changes (D-14 invariant).
    </automated>
  </verify>
  <done>
    pom.xml carries per-fork `<systemPropertyVariables>` injection for BOTH Surefire and Failsafe default-it with three entries each (staging-dir, import-backups-dir, surefire.forkNumber); Failsafe default-it has `forkCount=2 reuseForks=true`; project `<properties>` does NOT carry a `surefire.forkNumber` fallback; production yml files are untouched; `./mvnw clean test-compile` exits 0.
  </done>
</task>

<task type="auto">
  <name>Task 2: BackupStagingDirPerForkIT — per-fork path contract (regex + non-vacuous JVM parity check)</name>
  <files>src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java</files>
  <requirement>PERF-01</requirement>
  <read_first>
    - src/test/java/org/ctc/backup/service/BackupImportServiceIT.java lines 1-65 (analog — exact `@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS) @Tag("integration")` stack + `@Value("${app.backup.staging-dir}")` injection — RESEARCH PATTERN A)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-12 CLARIFIED + D-04R.2 (Test 2 is now non-vacuous because Task 1 exposes `surefire.forkNumber` to the JVM)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-FLAKE-DIAGNOSTIC.md Finding 2 (why Test 2 silently passed in Attempt 1)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md lines 32-79 (BackupStagingDirPerForkIT pattern excerpt)
    - CLAUDE.md "Tag Tests by Category" section (`@Tag("integration")` mandatory)
    - CLAUDE.md "Test Naming (Given-When-Then)" section
  </read_first>
  <action>
    Create `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java`.

    Package: `org.ctc.backup.service` (same as the production component being asserted against).

    Annotation stack on the class (alphabetical / canonical order): `@SpringBootTest`, `@ActiveProfiles("dev")`, `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`, `@Tag("integration")`.

    Field: `@Value("${app.backup.staging-dir}") Path stagingDir;` (inject as `java.nio.file.Path` directly).

    Test 1 — `whenAppRuns_thenStagingDirNameMatchesPerForkPattern`: assert `stagingDir.getFileName().toString()` matches regex `backup-staging-fork-\d+` using AssertJ's `matches(String regex)`. Given-when-then comments.

    Test 2 — `givenSurefireForkNumberPropertySet_whenAppRuns_thenStagingDirSuffixEqualsForkNumber` (non-vacuous parity check per D-04R.2): read `String forkNum = System.getProperty("surefire.forkNumber");`. Branch:
    - If `forkNum != null && !forkNum.isBlank()` (forked execution under Surefire/Failsafe): assert `stagingDir.getFileName().toString()` ends with `"-" + forkNum` (e.g. fork 1 → ends with `"-1"`).
    - If `forkNum == null` (IDE direct-launch bypass — `<systemPropertyVariables>` not applied): skip the assertion with an explanatory comment referencing D-04R.2. Use AssertJ's `Assumptions.assumeThat(forkNum).isNotNull()` so the skip surfaces in test reports rather than passing vacuously.

    No `@Autowired` services needed; no `@BeforeAll` / `@BeforeEach` needed (pure property-value check). No `@DynamicPropertySource`.

    Imports: `org.junit.jupiter.api.{Tag, Test, TestInstance}`, `org.springframework.beans.factory.annotation.Value`, `org.springframework.boot.test.context.SpringBootTest`, `org.springframework.test.context.ActiveProfiles`, `java.nio.file.Path`, `static org.assertj.core.api.Assertions.assertThat`, `static org.assertj.core.api.Assumptions.assumeThat`.

    No SpotBugs suppression expected on this class (`System.getProperty` of a known token is a normal JVM-property read; no reflection, no `setAccessible`).
  </action>
  <verify>
    <automated>
      1. `test -f src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` (file exists).
      2. `grep -c '@Tag("integration")' src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` returns 1.
      3. `grep -c 'backup-staging-fork-\\\\d+' src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` returns 1 (Test 1 regex literal).
      4. `grep -c 'System.getProperty("surefire.forkNumber")' src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` returns 1 (Test 2 reads the JVM-exposed property per D-04R.2).
      5. `grep -c 'assumeThat\|Assumptions' src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` >= 1 (skip surfaces in reports for IDE direct-launch).
      6. `./mvnw verify -Dit.test='BackupStagingDirPerForkIT' -Djacoco.skip=true -DfailIfNoTests=true --no-transfer-progress` exits 0 — runs under Failsafe forkCount=2 from Task 1, both Test 1 and Test 2 pass non-vacuously.
      7. Behavior check: after the verify run, inspect `target/failsafe-reports/TEST-org.ctc.backup.service.BackupStagingDirPerForkIT.xml` `<properties>` section — must contain a `<property name="surefire.forkNumber" value="1"/>` OR `value="2"/>` entry (D-04R.2 evidence) AND a `<property name="app.backup.staging-dir" value="data/dev/backup-staging-fork-1"/>` OR `value="data/dev/backup-staging-fork-2"/>` entry (D-04R evidence). If the path resolves to `backup-staging-fork-0` or `backup-staging-fork-${surefire.forkNumber}` (literal placeholder), Task 1 regressed — fix before continuing.
    </automated>
  </verify>
  <done>
    `BackupStagingDirPerForkIT` exists with 2 test methods; Test 1 asserts regex; Test 2 asserts non-vacuous JVM-side `surefire.forkNumber` parity via `assumeThat` skip-marker; `./mvnw verify -Dit.test='BackupStagingDirPerForkIT'` exits 0 under Failsafe forkCount=2; the failsafe-reports XML proves the per-fork mechanism engaged (path suffix matches fork number).
  </done>
</task>

<task type="auto">
  <name>Task 3: BackupStagingCleanupRaceIT — sweep-isolation under forkCount=2 (own-fork swept, sibling untouched)</name>
  <files>src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java</files>
  <requirement>PERF-01</requirement>
  <read_first>
    - src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java entire file (analog — 138 lines, lifecycle + `publishEvent(new ApplicationReadyEvent(...))` re-trigger pattern verbatim, RESEARCH §RQ-6)
    - src/main/java/org/ctc/backup/service/BackupStagingCleanup.java entire file (production component — package-private, sweeps `upload-*.zip` and `upload-*.zip.meta`)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-17 (sibling-fork-dir name = any value ≥ 10; planner chose `99` placeholder, any value not in 0..32 is safe)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-PATTERNS.md lines 83-157 (BackupStagingCleanupRaceIT pattern excerpt — what to copy, what to adapt)
  </read_first>
  <action>
    Create `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java`.

    Package: `org.ctc.backup.service` (required — `BackupStagingCleanup` is package-private).

    Annotation stack: `@SpringBootTest`, `@ActiveProfiles("dev")`, `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`, `@Tag("integration")`.

    Fields:
    - `@Value("${app.backup.staging-dir}") Path ownForkDir;`
    - `@Autowired ApplicationContext context;`
    - `@Autowired BackupStagingCleanup cleanup;` (package-private constructor injection still works because the IT is in the same package)
    - `private Path siblingForkDir;` (computed in `@BeforeAll` as `ownForkDir.getParent().resolve("backup-staging-fork-99")`)

    `@BeforeAll setUp()`:
    - `Files.createDirectories(ownForkDir);`
    - `siblingForkDir = ownForkDir.getParent().resolve("backup-staging-fork-99");`
    - `Files.createDirectories(siblingForkDir);`

    Test 1 — `givenFilesInOwnAndSiblingForkDirs_whenApplicationReady_thenOnlyOwnForkFilesRemoved`:
    - Given: seed `upload-test-A.zip` + `upload-test-A.zip.meta` into BOTH `ownForkDir` and `siblingForkDir` using `Files.write(path, "test".getBytes())`. Also seed a non-upload file `unrelated.txt` in each dir (sweep must not delete those — proves selectivity stays intact).
    - When: re-publish `ApplicationReadyEvent` using the verbatim pattern from `BackupStagingCleanupIT` lines 79-80: `context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[]{}, (ConfigurableApplicationContext) context, Duration.ZERO));`
    - Then: assert ownForkDir no longer contains `upload-test-A.zip` or `.zip.meta` (`Files.exists(...)` is false for both); ownForkDir still contains `unrelated.txt` (selectivity); siblingForkDir still contains ALL three files (proof of sweep-isolation — siblings untouched).

    `@AfterAll tearDown()`: recursively delete `siblingForkDir` (use `Files.walk(siblingForkDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete)` or the equivalent NIO-only pattern from `BackupStagingCleanupIT`).

    No `@DynamicPropertySource`, no `@TempDir`, no `@ExtendWith(OutputCaptureExtension.class)` — this IT proves filesystem state, not log output.

    Imports: `java.io.IOException`, `java.nio.file.Files`, `java.nio.file.Path`, `java.time.Duration`, `java.util.Comparator`, `org.junit.jupiter.api.{AfterAll, BeforeAll, Tag, Test, TestInstance}`, `org.springframework.beans.factory.annotation.{Autowired, Value}`, `org.springframework.boot.SpringApplication`, `org.springframework.boot.context.event.ApplicationReadyEvent`, `org.springframework.boot.test.context.SpringBootTest`, `org.springframework.context.{ApplicationContext, ConfigurableApplicationContext}`, `org.springframework.test.context.ActiveProfiles`, `static org.assertj.core.api.Assertions.assertThat`.
  </action>
  <verify>
    <automated>
      1. `test -f src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java`.
      2. `grep -c '@Tag("integration")' src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` returns 1.
      3. `grep -c 'backup-staging-fork-99' src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` returns 1 (sibling placeholder dir name per D-17).
      4. `grep -c 'context.publishEvent(new ApplicationReadyEvent' src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` returns 1 (re-trigger pattern from analog).
      5. `./mvnw verify -Dit.test='BackupStagingCleanupRaceIT' -Djacoco.skip=true -DfailIfNoTests=true --no-transfer-progress` exits 0.
      6. Behavior: post-run, assert NO leftover `backup-staging-fork-99` directory exists under `data/dev/` (proves `@AfterAll` tearDown ran): `! test -d data/dev/backup-staging-fork-99`.
    </automated>
  </verify>
  <done>
    `BackupStagingCleanupRaceIT` exists, seeds own-fork + sibling-fork-99 dirs, re-publishes `ApplicationReadyEvent`, asserts own-fork upload-*.zip files removed AND sibling-fork-99 files untouched (D-17 sweep-isolation proof); `./mvnw verify -Dit.test='BackupStagingCleanupRaceIT'` exits 0; `@AfterAll` cleanup removes the sibling dir.
  </done>
</task>

<task type="auto">
  <name>Task 4: ImportLockedPostRejectorIT — D-19 lock-acquisition-timeout root-cause investigation + fix</name>
  <files>src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java</files>
  <requirement>PERF-01</requirement>
  <read_first>
    - src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java entire file (especially lines 199-225 — the failing `givenLockHeld_whenGetAdminSeasons_thenPassesThrough` method)
    - src/test/java/org/ctc/backup/it/support/BlockingRestoreFailureInjector.java (the `Config.class` injected via `@Import` — provides the `hasAcquired` + `releaseLatch` `CountDownLatch` beans + the failure-injector that blocks thread A mid-restore inside `ImportLockService.tryLock()`-held section)
    - src/test/java/org/ctc/backup/it/ImportLockServiceResetHelper.java (`@AfterEach tearDownLock()` already calls `reset()`; verify what `reset()` does — is it a full `tryLock + unlock` cycle or just `unlock`?)
    - src/main/java/org/ctc/backup/lock/ImportLockService.java entire file (Spring `@Service @Scope("singleton")`, `ReentrantLock` is INSTANCE field — fresh context = fresh lock; under `reuseForks=true` SAME context across test classes => SAME lock instance)
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-FLAKE-DIAGNOSTIC.md Finding 4 (D-19 source) — 12.10 s actual wallclock vs 10 s deadline under `reuseForks=true + forkCount=2`
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-19 (root-cause-first; silent deadline bump FORBIDDEN per [[no-flaky-dismissal]])
    - CLAUDE.md "Keine Flaky-Test-Vertagung" section (German: tests that were green and now fail are regressions, not flaky; root-cause first, never out-of-scope)
  </read_first>
  <action>
    Investigate the D-19 regression in `givenLockHeld_whenGetAdminSeasons_thenPassesThrough` (line 199-225). The failing assertion is `hasAcquired.await(10, TimeUnit.SECONDS)` at line 212-214 — the latch tracks whether thread A successfully ENTERED the blocking-restore-failure-injector (which fires AFTER `ImportLockService.tryLock()` returns true on thread A). Wallclock 12.10s exceeded 10s deadline under `reuseForks=true + forkCount=2`.

    Root-cause investigation steps (in this order — choose the FIRST applicable resolution, document the choice in a Javadoc-style comment block above line 200):

    (a) **Latent state-leak check:** read `ImportLockServiceResetHelper.reset()`. If it only calls `unlock()` and a prior test method failed mid-execution leaving thread A's future hanging, the next test reuses a context where the latches (`hasAcquired`, `releaseLatch`) are still non-zero. Inspect: under `@TestInstance(PER_CLASS)`, the `@Autowired CountDownLatch hasAcquired` and `releaseLatch` beans are recreated per-test-class. Under `reuseForks=true` they are NOT shared across test classes in the same fork (different Spring contexts per class with `@DirtiesContext(AFTER_METHOD)` on this specific method). BUT — verify: does the `@Import(BlockingRestoreFailureInjector.Config.class)` define `CountDownLatch` as `@Scope("prototype")` or default singleton? If singleton AND the prior method's future never completed, the latch counter is already at 0 when this method runs, making `await(10s)` block on a fresh latch that nobody decrements. **Fix option a1:** ensure `hasAcquired` + `releaseLatch` beans are re-created per test method (either `@Scope("prototype")` in `Config.class`, OR add `@BeforeEach` reset hook that replaces the latches via `BeanFactory.destroySingleton` + re-registration — choose the lighter-touch).

    (b) **Context-load contention check:** under `forkCount=2 + reuseForks=true`, two Failsafe forks each load Spring contexts in parallel. The first time `ImportLockedPostRejectorIT` runs in a fork, that fork is also bootstrapping other backup IT contexts. CPU/IO contention can stretch the BlockingRestoreFailureInjector's `tryLock + sleep + signal-hasAcquired` chain past 10s. **Fix option b1:** add an `@BeforeAll` warm-up that triggers the context once before the timed assertion runs (e.g. a no-op `mockMvc.perform(get("/admin/health-or-similar"))` to amortise cold-start outside the timed window).

    (c) **Deadline bump with justification (last-resort, ONLY if a/b can't be done within Task 4 scope):** raise the deadline from 10s to 20s in `assertThat(hasAcquired.await(10, TimeUnit.SECONDS))` (line 212). MANDATORY in this case:
    - Add a Javadoc-style block comment above the test method (lines 199-200) explaining: (i) what was investigated (state-leak + context-load contention), (ii) why neither was the root cause OR why fixing them is out of scope for Phase 89, (iii) explicit reference to the FLAKE-DIAGNOSTIC.md Finding 4 + D-19, (iv) creation of a follow-up GitHub issue or a tech-debt entry in `.planning/STATE.md`'s "Deferred Items" section.
    - The bump itself must use a named constant `private static final long LOCK_ACQ_DEADLINE_SECONDS = 20L;` so the reasoning is grep-discoverable.

    Per CLAUDE.md [[no-flaky-dismissal]]: silent deadline bump WITHOUT root-cause analysis is FORBIDDEN. The Javadoc block above the test method MUST be the artefact proving the investigation happened.

    Touch ONLY `ImportLockedPostRejectorIT.java`. Do NOT modify `ImportLockService.java` (production code), `BlockingRestoreFailureInjector.java`, or `ImportLockServiceResetHelper.java` unless option (a) requires it AND the change is purely test-scope.
  </action>
  <verify>
    <automated>
      1. `git diff src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` is non-empty (the file MUST change — silent no-op forbidden per D-19).
      2. `grep -c -E 'D-19|FLAKE-DIAGNOSTIC|reuseForks.*forkCount' src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` >= 1 (the Javadoc block references the diagnostic context — proof of root-cause analysis per [[no-flaky-dismissal]]).
      3. `./mvnw verify -Dit.test='ImportLockedPostRejectorIT' -Djacoco.skip=true -DfailIfNoTests=true --no-transfer-progress` exits 0 under Failsafe `forkCount=2 + reuseForks=true` from Task 1.
      4. `git diff src/main/java/org/ctc/backup/lock/ImportLockService.java` is empty (production lock code untouched — Task 4 is test-scope only).
      5. Anti-regression: if option (c) was chosen, `grep -c 'LOCK_ACQ_DEADLINE_SECONDS' src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` >= 2 (named constant declared once + used once).
    </automated>
  </verify>
  <done>
    `ImportLockedPostRejectorIT.givenLockHeld_whenGetAdminSeasons_thenPassesThrough` passes under `forkCount=2 + reuseForks=true`; the source change is documented in a Javadoc block referencing D-19 / FLAKE-DIAGNOSTIC Finding 4 (root-cause-first per [[no-flaky-dismissal]]); production `ImportLockService` is unchanged.
  </done>
</task>

<task type="auto">
  <name>Task 5: 3-seed empirical race proof on org.ctc.backup.** + legacy backup-staging cleanup</name>
  <files>.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md</files>
  <requirement>PERF-01</requirement>
  <read_first>
    - .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md D-13 (3-seed scope = all `src/test/java/org/ctc/backup/**` ITs, seeds 1234/5678/9999) + D-06 (legacy `data/*/backup-staging/` one-shot `rm -rf`)
    - .planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md D-09 (3-seed Failsafe verification methodology — copy-forward pattern)
    - CLAUDE.md "Test-Aufrufe optimieren" + "Clean Build/Test Only" (use `-Dit.test=`, `-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=N`; NO skip-flags)
    - $HOME/.claude/get-shit-done/templates/summary.md (SUMMARY.md template)
  </read_first>
  <action>
    Run the 3-seed empirical cross-fork-collision proof against ALL backup ITs under the elevated Failsafe `forkCount=2 + reuseForks=true` from Task 1, then sweep legacy directories and write the plan SUMMARY.

    Step 1 — Legacy sweep (D-06): from project root, run `rm -rf data/dev/backup-staging data/dev,demo/backup-staging data/local/backup-staging` (all gitignored, harmless on non-existent). Also sweep `data/dev/import-backups` and similar siblings if they exist as singleton (no fork suffix) — these will be auto-recreated as `import-backups-fork-N` per the new D-18 mechanism.

    Step 2 — Seed 1234 run: `./mvnw verify -Dit.test='org.ctc.backup.**' -Djacoco.skip=true -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234 -DfailIfNoTests=true --no-transfer-progress`. Capture output to `/tmp/89-01-seed-1234.log` for forensics. Must exit 0 with zero failures and zero flakes (re-runs).

    Step 3 — Seed 5678 run: same command with `seed=5678`, log to `/tmp/89-01-seed-5678.log`. Must exit 0.

    Step 4 — Seed 9999 run: same command with `seed=9999`, log to `/tmp/89-01-seed-9999.log`. Must exit 0.

    Step 5 — Write `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` following the standard SUMMARY template. Sections to include:
    - Frontmatter (phase, plan, completed date, requirements: [PERF-01], depends_on: [], wave: 1).
    - "What Shipped" — bullet list of files modified (pom.xml + 3 test files) + the 2 new ITs.
    - "Decisions Honored" — explicit table mapping D-01 (5 tasks), D-03 (Surefire+Failsafe injection), D-04R (no project-property fallback), D-04R.2 (JVM-side exposure), D-05 (both plugins same value), D-06 (legacy sweep performed), D-11 (Failsafe forkCount=2), D-12 (BackupStagingDirPerForkIT Test 2 non-vacuous), D-13 (3-seed scope), D-14 (application.yml untouched — git diff proof), D-17 (BackupStagingCleanupRaceIT created), D-18 (import-backups-dir per-fork), D-19 (lock-timeout investigation method/outcome).
    - "3-Seed Verification Evidence" — table with three rows (seeds 1234 / 5678 / 9999), columns: command, exit code, total tests run, failures, flakes, wallclock. The actual numbers from Steps 2-4. If a seed run revealed an additional flake not seen in Attempt 1's diagnostic, STOP and report — do NOT silently rerun until green.
    - "Legacy Cleanup" — record the `rm -rf` paths executed in Step 1.
    - "FLAKE-DIAGNOSTIC Findings Resolution" — checklist mapping each of the 4 findings to its resolving task: Finding 1 → Task 1 (no project-property fallback); Finding 2 → Task 1 (third `<systemPropertyVariables>` entry) + Task 2 (Test 2 non-vacuous); Finding 3 → Task 1 (import-backups-dir per-fork); Finding 4 → Task 4 (lock-timeout root-cause).
    - "Next Plan" — pointer to Plan 89-02 (PERF-02 fingerprint listener).
  </action>
  <verify>
    <automated>
      1. `test -f .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md`.
      2. `grep -c '^## 3-Seed' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` >= 1.
      3. `grep -c -E 'seed=?1234|seed.*1234' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` >= 1.
      4. `grep -c -E 'seed=?5678|seed.*5678' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` >= 1.
      5. `grep -c -E 'seed=?9999|seed.*9999' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` >= 1.
      6. `grep -c -E 'Finding [1234]' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` >= 4 (each of the 4 FLAKE-DIAGNOSTIC findings explicitly addressed).
      7. `grep -c 'D-04R\|D-18\|D-19' .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` >= 3 (the three load-bearing new decisions named).
      8. ALL THREE seed runs from Steps 2-4 exited 0 with zero failures.
      9. Legacy sweep: `! test -e data/dev/backup-staging || true` (gitignored; either absent or, if a non-fork run later regenerates a stub, harmless).
    </automated>
  </verify>
  <done>
    All 3 seeds (1234 / 5678 / 9999) of `./mvnw verify -Dit.test='org.ctc.backup.**'` exit 0 under Failsafe `forkCount=2 + reuseForks=true` — empirical cross-fork-collision proof; legacy `data/*/backup-staging/` swept; `89-01-SUMMARY.md` documents the decision-honored map, the 3-seed evidence table, and the 4-finding resolution checklist.
  </done>
</task>

</tasks>

<threat_model>
threats="LOW — test-infrastructure refactor; no production behavior change; no user-input surface; production `application.yml` unchanged per D-14"

mitigation="Per-fork system properties are set ONLY inside Surefire/Failsafe-forked JVMs via pom.xml `<systemPropertyVariables>`; production runtime sees no injection. `BackupStagingCleanup` production source is UNTOUCHED — only the resolved `@Value` differs at test time. No new external dependencies. No CodeQL/SpotBugs surface added — `System.getProperty(\"surefire.forkNumber\")` is a normal JDK call, no reflection, no `setAccessible`. The `ImportLockedPostRejectorIT` Task 4 change is test-scope only (production `ImportLockService` unchanged per Task 4 verify rule 4)."

stride_register="
| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-89-01-01 | Tampering | pom.xml `<systemPropertyVariables>` injection path | accept | Test-scope only; production runtime resolves `application.yml` defaults; D-14 invariant. Verified by Task 1 Step 8 (git diff zero on production yml). |
| T-89-01-02 | Information Disclosure | `target/failsafe-reports/TEST-*.xml` `<property>` dumps reveal absolute paths | accept | Already standard Surefire/Failsafe behavior pre-Phase-89; per-fork suffix adds fork number only, no new sensitive data. |
| T-89-01-03 | DoS | Failsafe `forkCount=2 + reuseForks=true` doubles peak memory | accept | Existing Surefire `forkCount=2` already proves the dev/CI envelope supports 2 JVMs; Phase 91 PERF-06 will re-harvest CI wallclock to confirm no regression on CI runners. |
"
</threat_model>

<verification>
After all 5 tasks complete:
- `./mvnw clean test-compile` exits 0 (Task 1 compile baseline).
- `./mvnw verify -Dit.test='BackupStagingDirPerForkIT,BackupStagingCleanupRaceIT' -Djacoco.skip=true --no-transfer-progress` exits 0 (Task 2 + Task 3 ITs both green under forkCount=2).
- `./mvnw verify -Dit.test='ImportLockedPostRejectorIT' -Djacoco.skip=true --no-transfer-progress` exits 0 (Task 4 fix verified).
- 3-seed verification (Task 5) all green: zero failures across 1234 / 5678 / 9999.
- `git diff --stat src/main/resources/application.yml src/main/resources/application-*.yml src/main/java/org/ctc/backup/service/BackupStagingCleanup.java src/main/java/org/ctc/backup/lock/ImportLockService.java` reports zero changes (D-14 invariant + Task 4 scope).
- 89-01-SUMMARY.md exists and maps all 4 FLAKE-DIAGNOSTIC findings to their resolving tasks.
</verification>

<success_criteria>
- pom.xml carries three `<systemPropertyVariables>` entries each for Surefire and Failsafe default-it (staging-dir, import-backups-dir, surefire.forkNumber); Failsafe default-it `forkCount=2 reuseForks=true`; no project-level `<surefire.forkNumber>` fallback.
- `BackupStagingDirPerForkIT` exists in `org.ctc.backup.service`, has 2 test methods, Test 2 fires non-vacuously (assumeThat skip-marker for IDE direct-launch).
- `BackupStagingCleanupRaceIT` exists in `org.ctc.backup.service`, proves own-fork swept + sibling-fork-99 untouched.
- `ImportLockedPostRejectorIT` passes under `forkCount=2 + reuseForks=true`; source change documented with D-19 / FLAKE-DIAGNOSTIC Finding 4 reference.
- 3-seed Failsafe verification on `org.ctc.backup.**` returns zero failures and zero flakes across seeds 1234, 5678, 9999.
- Legacy `data/*/backup-staging/` (no fork suffix) swept via `rm -rf` per D-06.
- `application.yml` and all `application-{dev,local,docker,prod}.yml` files are git-clean (D-14 invariant).
- 89-01-SUMMARY.md exists and follows the standard SUMMARY template.
</success_criteria>

<output>
Create `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` per the standard SUMMARY template, including the 3-seed evidence table and the 4-finding resolution checklist.
</output>
