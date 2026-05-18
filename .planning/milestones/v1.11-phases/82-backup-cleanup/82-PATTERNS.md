# Phase 82: Backup Cleanup - Pattern Map

**Mapped:** 2026-05-16
**Files analyzed:** 14 new/modified files
**Analogs found:** 13 / 14 (one file — 82-BACKLOG-AUDIT.md — is a markdown doc with no code analog)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/backup/audit/BackupExecutedByResolver.java` | service/component | request-response | `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` (the private method being extracted) | exact — same 4-branch logic, same package |
| `src/test/java/org/ctc/backup/audit/BackupExecutedByResolverTest.java` | unit test | — | `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` (MockitoExtension pattern) | role-match |
| `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` | integration test | — | `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` | exact — same `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")` + `@Autowired BackupSchema` |
| `src/test/java/org/ctc/backup/service/BackupRestoreZipOpenCountIT.java` | integration test | — | `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java` | exact — same `@SpringBootTest @ActiveProfiles("dev") @ExtendWith(OutputCaptureExtension.class) @Tag("integration")` |
| `.planning/phases/82-backup-cleanup/82-BACKLOG-AUDIT.md` | doc | — | none | no analog (markdown table only) |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` (WR-01 + IN-03 + BACK-03) | service | request-response | self (existing file, targeted edits) | exact — modify existing |
| `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` (WR-01) | service | request-response | self (existing file, targeted edits) | exact — modify existing |
| `src/main/java/org/ctc/backup/restore/entity/*.java` 18 files (IN-01) | component | CRUD | `src/main/java/org/ctc/backup/restore/entity/DriverRestorer.java` | exact — same class shape |
| `src/main/java/org/ctc/backup/restore/entity/*.java` 6 files (IN-02) | component | CRUD | `src/main/java/org/ctc/backup/restore/entity/DriverRestorer.java` | exact — same class shape |
| `src/main/resources/application.yml` (IN-04) | config | — | self (line 5 `staging-dir` pattern) | exact — same `${spring.profiles.active:dev}` expression |
| `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` (BACK-05) | integration test | — | self (existing `H2RoundTripTests.captureRowCounts()` + `exportToBytes()`) | exact — extend existing nested class |
| `README.md` (IN-04 note) | doc | — | none | markdown prose, no code analog |
| `CLAUDE.md` (D-08 convention, discretion) | doc | — | none | markdown prose, no code analog |

---

## Pattern Assignments

### `src/main/java/org/ctc/backup/audit/BackupExecutedByResolver.java` (new `@Component` bean — WR-01)

**Analog:** `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` (the private `resolveExecutedBy` method being extracted, lines 138–150) + `src/main/java/org/ctc/backup/service/BackupImportService.java` (the 3-branch version, lines 731–740)

**Imports pattern** — copy exactly, same package `org.ctc.backup.audit`:
```java
package org.ctc.backup.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
```

**Annotation order** (CLAUDE.md `### Lombok Usage` convention after IN-02):
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupExecutedByResolver {
    private final Environment environment;
```

**Core 4-branch pattern** — extracted verbatim from `DataImportAuditService.resolveExecutedBy` (lines 138–150):
```java
public String resolve(String callerOverride) {
    if (environment.matchesProfiles("dev | local")) {
        return "dev";
    }
    if (callerOverride != null && !callerOverride.isBlank()) {
        return callerOverride;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
        return auth.getName();
    }
    return "unknown";
}
```

**What's different from the analog:** The analog's method is private with a `callerOverride` parameter that skips branch-1 (dev profile) check before checking override. In the new bean, branch order is:
1. dev/local → "dev"  (always first, same as BackupImportService's 3-branch version)
2. callerOverride non-blank → callerOverride
3. SecurityContext → auth.getName()
4. "unknown"

`BackupImportService` calls `executedByResolver.resolve(null)` (no override).
`DataImportAuditService` calls `executedByResolver.resolve(executedByCaller)`.

---

### `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` (WR-01 modification)

**Analog:** self (current file, lines 56–73)

**Critical constraint — explicit constructor** (NOT `@RequiredArgsConstructor`, because `@Qualifier("backupObjectMapper")` is used):

The existing constructor at lines 66–73:
```java
public DataImportAuditService(
        JdbcTemplate jdbcTemplate,
        @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper,
        Environment environment) {
    this.jdbcTemplate = jdbcTemplate;
    this.backupObjectMapper = backupObjectMapper;
    this.environment = environment;
}
```

**After WR-01:** Add `BackupExecutedByResolver executedByResolver` parameter; remove `Environment environment` parameter (becomes dead code). New constructor:
```java
public DataImportAuditService(
        JdbcTemplate jdbcTemplate,
        @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper,
        BackupExecutedByResolver executedByResolver) {
    this.jdbcTemplate = jdbcTemplate;
    this.backupObjectMapper = backupObjectMapper;
    this.executedByResolver = executedByResolver;
}
```

Call site at line 102 changes from `resolveExecutedBy(executedByCaller)` to `executedByResolver.resolve(executedByCaller)`.

Remove `Environment` field (line 58), remove `resolveExecutedBy(String)` private method (lines 138–150), add `private final BackupExecutedByResolver executedByResolver` field.

---

### `src/main/java/org/ctc/backup/service/BackupImportService.java` (WR-01 + IN-03 + BACK-03 modifications)

**Analog:** self (current file)

**WR-01 changes:**
- Remove private `resolveExecutedBy()` at lines 731–740
- Remove `Environment environment` from constructor parameters and `final` fields (line 135)
- Add `final BackupExecutedByResolver executedByResolver` field (in constructor, same position)
- Line 518: `String executedBy = resolveExecutedBy();` → `String executedBy = executedByResolver.resolve(null);`
- `tryRecordFailure()` call site: pass `null` explicitly (already implicit in existing 3-branch version)

**IN-03 change** — existing `log.debug` at lines 669–670:
```java
// BEFORE (lines 666–671):
if (entry == null) {
    log.debug("No data entry for table={} (entryPath={}) — restore count is 0",
            ref.tableName(), entryPath);
    return totalRows;
}

// AFTER:
if (entry == null) {
    log.warn("Backup ZIP has no data entry for table={} (entryPath={}) — possible corruption or schema regression",
            ref.tableName(), entryPath);
    return totalRows;
}
```

**BACK-03 counter** — package-private field with Javadoc, NO `@VisibleForTesting` (annotation absent from entire codebase per research Task 6):
```java
// Place after static constants block, before @PersistenceContext field
/**
 * Package-private counter for test verification of the single-ZIP-open contract (BACK-03).
 * Reset to 0 at the top of {@link #execute(UUID)}; incremented once per {@link #restoreAll}.
 */
AtomicInteger zipOpenCounter = new AtomicInteger(0);
```

Reset at top of `execute(UUID stagingId)` (first statement before any try/catch):
```java
zipOpenCounter.set(0);
```

Increment immediately before line 635 `try (ZipFile zf = ...)` inside `restoreAll()`:
```java
zipOpenCounter.incrementAndGet();
try (ZipFile zf = new ZipFile(staged.toFile())) {
```

**Import to add for BACK-03:** `import java.util.concurrent.atomic.AtomicInteger;`

---

### `src/test/java/org/ctc/backup/audit/BackupExecutedByResolverTest.java` (new plain unit test — WR-01)

**Analog:** No existing Mockito unit test in `org.ctc.backup.audit` package. Use general Mockito pattern from `@ExtendWith(MockitoExtension.class)` convention.

**Class header** — untagged plain unit test (CLAUDE.md: unit tests stay untagged):
```java
package org.ctc.backup.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupExecutedByResolverTest {

    @Mock
    Environment environment;

    BackupExecutedByResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BackupExecutedByResolver(environment);
    }
```

**SecurityContextHolder pattern** — use thread-local setup/teardown, NOT `Mockito.mockStatic` (research confirmed: simpler, no JVM agent needed on JDK 25):
```java
@Test
void givenNonDevProfileAndNoOverrideAndAuth_whenResolve_thenReturnsAuthName() {
    // given
    when(environment.matchesProfiles("dev | local")).thenReturn(false);
    Authentication auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("testuser");
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
        // when
        String result = resolver.resolve(null);
        // then
        assertThat(result).isEqualTo("testuser");
    } finally {
        SecurityContextHolder.clearContext();
    }
}
```

**Four test method names** (Given-When-Then per CLAUDE.md):
- `givenDevProfile_whenResolve_thenReturnsDev`
- `givenNonDevProfileAndCallerOverride_whenResolve_thenReturnsOverride`
- `givenNonDevProfileAndNoOverrideAndAuth_whenResolve_thenReturnsAuthName`
- `givenNonDevProfileAndNoOverrideAndNoAuth_whenResolve_thenReturnsUnknown`

---

### `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` (new IT — BACK-01)

**Analog:** `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` (lines 1–51)

**Class header** — exact copy of BackupSchemaTopologyIT boilerplate:
```java
package org.ctc.backup.schema;

import org.ctc.backup.schema.BackupSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupSchemaGuardTest {

    @Autowired
    BackupSchema backupSchema;
```

**Why Spring context is required:** `BackupSchema` is `@Component @RequiredArgsConstructor` with injected `EntityManagerFactory` and `EntityTopoSorter`, populated via `@PostConstruct void initializeExportOrder()`. Cannot instantiate without a JPA context. `BackupSchemaTopologyIT` (same pattern) is the confirmed working analog.

**Two guard test methods** — per D-17, assertion message must cite Phase 75 gate:
```java
@Test
void givenBackupSchema_whenInspected_thenSchemaVersionIsOne() {
    assertThat(BackupSchema.SCHEMA_VERSION)
        .as("BackupSchema.SCHEMA_VERSION changed from 1 — this is a wire contract bump; "
            + "see Phase 75 SCHEMA_VERSION gate or write a new migration phase")
        .isEqualTo(1);
}

@Test
void givenBackupSchema_whenInspected_thenExportOrderHasTwentyFourEntities() {
    assertThat(backupSchema.getExportOrder().size())
        .as("BackupSchema.EXPORT_ORDER size changed from 24 — if a new entity was added, "
            + "bump SCHEMA_VERSION and update BackupRoundTripIT expected row-count assertions")
        .isEqualTo(24);
}
```

**Per-commit test command** (not `mvnw test`, because `@Tag("integration")` routes to Failsafe):
```
./mvnw verify -Dit.test=BackupSchemaGuardTest -DfailIfNoTests=false
```

---

### `src/test/java/org/ctc/backup/service/BackupRestoreZipOpenCountIT.java` (new IT — BACK-03 + IN-03)

**Analog:** `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java` (lines 1–63) — same `@SpringBootTest @ActiveProfiles("dev") @ExtendWith(OutputCaptureExtension.class) @Tag("integration")` combination.

**Class header** (copy from BackupImportPostCommitIT lines 59–63):
```java
@SpringBootTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
@Tag("integration")
class BackupRestoreZipOpenCountIT {
```

**OutputCaptureExtension usage** — confirmed pattern from `BackupImportRollbackIT` (lines 34–35, 93) and `BackupImportPostCommitIT` (lines 23, 61):
```java
@Test
void givenZipWithMissingEntry_whenRestore_thenWarnLogEmittedAndZeroRows(
        CapturedOutput output) throws Exception {
    // given — build a synthetic ZIP missing one data entry (e.g., data/cars.json)
    // when — call backupImportService with the synthetic staged zip
    // then
    assertThat(output.getAll()).contains("Backup ZIP has no data entry for table=");
}
```

**BACK-03 counter test:**
```java
@Test
void givenStagedBackup_whenExecuteImport_thenZipOpenedExactlyOnce() throws Exception {
    // given — use testDataService.seed() + full export + stage
    // when
    backupImportService.execute(stagingId);
    // then
    assertThat(backupImportService.zipOpenCounter.get())
        .as("ZIP must be opened exactly once per execute() call (WR-05 contract)")
        .isEqualTo(1);
}
```

**Key autowires needed:**
```java
@Autowired
BackupImportService backupImportService;

@Autowired
BackupArchiveService backupArchiveService;

@Autowired
TestDataService testDataService;
```

**Note on `output.getAll()` vs `output.getOut()`:** `getAll()` = stdout + stderr combined. Use `getAll()` for WARN-level log assertions to be safe regardless of Logback appender target. Per research Task 5: `logback-test.xml` root level is `WARN` — WARN logs ARE captured.

---

### `src/main/java/org/ctc/backup/restore/entity/*.java` — 18 files (IN-01: remove `@RequiredArgsConstructor`)

**Analog:** `src/main/java/org/ctc/backup/restore/entity/DriverRestorer.java` (current state, lines 37–40)

**Current state** (DriverRestorer, representative of all 18):
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverRestorer implements EntityRestorer {
```

**Target state after IN-01** (no `@RequiredArgsConstructor` — no `final` fields, so annotation is a no-op):
```java
@Slf4j
@Component
public class DriverRestorer implements EntityRestorer {
```

**18 files to modify:**
DriverRestorer, MatchRestorer, MatchScoringRestorer, MatchdayRestorer, PhaseTeamRestorer, PsnAliasRestorer, RaceAttachmentRestorer, RaceLineupRestorer, RaceRestorer, RaceResultRestorer, RaceScoringRestorer, RaceSettingsRestorer, SeasonDriverRestorer, SeasonPhaseGroupRestorer, SeasonPhaseRestorer, SeasonRestorer, SeasonTeamRestorer, TeamRestorer

**Verification after IN-01:**
```bash
git grep -l "@RequiredArgsConstructor" src/main/java/org/ctc/backup/restore/entity/
```
Should return empty (no restorer has `private final` fields).

---

### `src/main/java/org/ctc/backup/restore/entity/*.java` — 6 files (IN-02: swap `@Component @Slf4j` → `@Slf4j @Component`)

**Analog:** `src/main/java/org/ctc/backup/restore/entity/CarRestorer.java` (lines 36–38, current wrong state)

**Current state** (CarRestorer, representative of all 6):
```java
@Component
@Slf4j
public class CarRestorer implements EntityRestorer {
```

**Target state after IN-02** (post-IN-01: only these 6 need reorder; none overlap with the 18):
```java
@Slf4j
@Component
public class CarRestorer implements EntityRestorer {
```

**6 files to modify:** CarRestorer, PlayoffMatchupRestorer, PlayoffRestorer, PlayoffRoundRestorer, PlayoffSeedRestorer, TrackRestorer

**Verification after IN-02:**
```bash
git grep -E "^@Component" src/main/java/org/ctc/backup/restore/entity/*.java
```
Should return empty (no restorer starts with `@Component` as first annotation).

---

### `src/main/resources/application.yml` line 6 (IN-04)

**Analog:** `src/main/resources/application.yml` line 5 (the `staging-dir` property — same `${spring.profiles.active:dev}` expression)

**Existing line 5 (pattern to copy):**
```yaml
staging-dir: data/${spring.profiles.active:dev}/backup-staging
```

**Line 6 before:**
```yaml
import-backups-dir: data/.import-backups
```

**Line 6 after:**
```yaml
import-backups-dir: data/${spring.profiles.active:dev}/import-backups
```

**No profile-specific YAML overrides needed:** Research Task 8 confirmed all 4 profile files (`application-dev.yml`, `application-local.yml`, `application-docker.yml`, `application-prod.yml`) have no backup-related configuration.

**Existing ITs unaffected:** `BackupRoundTripIT` and `BackupImportRollbackIT` override `app.backup.import-backups-dir` via `@DynamicPropertySource` to a temp dir — the YAML value is never used in those tests.

**Pre-commit grep for hardcoded old path** (per research Pitfall 4):
```bash
grep -rn "data/.import-backups" src/test/
```
Replace any hardcoded path references before committing IN-04.

---

### `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` — BACK-05 (add one `@Test` per `@Nested` class)

**Analog:** `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` `H2RoundTripTests` (lines 334–403) — the existing `givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch` test, which already calls `captureRowCounts()` + `exportToBytes()` + round-trip sequence.

**`captureRowCounts()` helper signature** (lines 424–436, already in both nested classes — DO NOT modify):
```java
private Map<String, Long> captureRowCounts() {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (EntityRef ref : backupSchema.getExportOrder()) {
        String table = ref.tableName();
        if (!SAFE_TABLE_NAME.matcher(table).matches()) {
            throw new IllegalStateException("Unsafe table name in BackupSchema: " + table);
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table, Long.class);
        counts.put(table, count == null ? 0L : count);
    }
    return counts;
}
```

**`exportToBytes()` helper signature** (lines 413–417, already in both nested classes):
```java
private byte[] exportToBytes() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    backupArchiveService.writeZip(baos, Instant.now());
    return baos.toByteArray();
}
```

**Full round-trip sequence** (lines 357–362, copy this pattern):
```java
byte[] zipBytes = exportToBytes();
MockMultipartFile file = new MockMultipartFile(
        "file", "h2-round-trip-export.zip", "application/zip", zipBytes);
BackupImportPreview preview = backupImportService.stage(file);
BackupImportResult result = backupImportService.execute(preview.stagingId());
```

**New `@Test` to add inside `H2RoundTripTests` (and duplicate inside `MariaDbRoundTripTests`)**:
```java
@Test
void givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch() throws Exception {
    // given — capture pre-export counts for all 24 entities
    Map<String, Long> preCounts = captureRowCounts();

    // when — full round-trip: export → stage → execute (wipes + restores)
    byte[] zipBytes = exportToBytes();
    MockMultipartFile file = new MockMultipartFile(
            "file", "h2-24entity-parity-export.zip", "application/zip", zipBytes);
    BackupImportPreview preview = backupImportService.stage(file);
    backupImportService.execute(preview.stagingId());

    // then — all 24 entities must have identical row counts after restore
    Map<String, Long> postCounts = captureRowCounts();
    for (EntityRef ref : backupSchema.getExportOrder()) {
        assertThat(postCounts.get(ref.tableName()))
                .as("row-count parity for table=" + ref.tableName())
                .isEqualTo(preCounts.get(ref.tableName()));
    }
}
```

**MariaDB gate inheritance:** The new test inside `MariaDbRoundTripTests` inherits `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` from the nested class — no extra annotation needed.

**Note on trivial 0==0 (D-25 discretion):** If research Open Question 2 finds that playoff tables are 0-row in the dev fixture, add before the loop:
```java
assertThat(preCounts.values().stream().filter(c -> c > 0).count())
    .as("fixture must have data in at least 12 entities for a meaningful parity test")
    .isGreaterThan(12);
```

---

## Shared Patterns

### `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")` IT Boilerplate
**Source:** `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` (lines 33–39)
**Apply to:** `BackupSchemaGuardTest.java`, `BackupRestoreZipOpenCountIT.java`
```java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class SomeIT {
    @Autowired
    BackupSchema backupSchema;
```

### `OutputCaptureExtension` Log Assertion Pattern
**Source:** `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java` (lines 59–62) + `BackupImportRollbackIT.java` (lines 88–94)
**Apply to:** `BackupRestoreZipOpenCountIT.java` (IN-03 WARN test)
```java
@ExtendWith(OutputCaptureExtension.class)
// ... (on class, combined with @SpringBootTest)

@Test
void someTest(CapturedOutput output) throws Exception {
    // when ...
    assertThat(output.getAll()).contains("expected log substring");
}
```

### `@RequiredArgsConstructor` + `final` Field Injection Pattern
**Source:** `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` (lines 56–58) — existing `environment` field
**Apply to:** `BackupExecutedByResolver.java`
```java
private final Environment environment;
// (injected via @RequiredArgsConstructor — ONLY when no @Qualifier on constructor params)
```

### Explicit Constructor Pattern (when `@Qualifier` is needed)
**Source:** `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` (lines 66–73)
**Apply to:** Updated `DataImportAuditService` constructor after WR-01
```java
public DataImportAuditService(
        JdbcTemplate jdbcTemplate,
        @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper,
        BackupExecutedByResolver executedByResolver) {
    this.jdbcTemplate = jdbcTemplate;
    this.backupObjectMapper = backupObjectMapper;
    this.executedByResolver = executedByResolver;
}
```

### Given-When-Then Test Method Naming
**Source:** all existing `*IT.java` in `org.ctc.backup.service`
**Apply to:** all new test methods
```
void givenContext_whenAction_thenExpectedResult()
    // given
    // when
    // then
```

### Package-Private Test Counter Pattern
**Source:** no existing `@VisibleForTesting` in codebase (research Task 6 — grep returned 0 results)
**Apply to:** `zipOpenCounter` in `BackupImportService.java`
```java
/**
 * Package-private counter for test verification of the single-ZIP-open contract (BACK-03).
 * Reset to 0 at the top of {@link #execute(UUID)}; incremented once per {@link #restoreAll}.
 */
AtomicInteger zipOpenCounter = new AtomicInteger(0);
```
Do NOT use `@VisibleForTesting` — the annotation is not available on the classpath (no Guava dependency).

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `.planning/phases/82-backup-cleanup/82-BACKLOG-AUDIT.md` | doc | — | Markdown table mapping REVIEW.md IDs to commit SHAs — no code pattern applies; use the template from D-SPECIFICS section of CONTEXT.md |

---

## Critical Pitfalls (for planner task prompts)

1. **`DataImportAuditService` has EXPLICIT constructor** — `@RequiredArgsConstructor` cannot be added. Add `BackupExecutedByResolver` as a new constructor parameter; remove `Environment environment` parameter (becomes dead code after WR-01).

2. **`BackupImportService.resolveExecutedBy()` takes NO parameters** — the 3-branch version. New call sites use `executedByResolver.resolve(null)`. `DataImportAuditService` uses `executedByResolver.resolve(executedByCaller)`.

3. **`BackupSchemaGuardTest` is `@Tag("integration")`**, not a plain unit test. Per-commit command: `./mvnw verify -Dit.test=BackupSchemaGuardTest`, NOT `./mvnw test -Dtest=BackupSchemaGuardTest`.

4. **`zipOpenCounter.set(0)` must be the FIRST statement in `execute(UUID)`** — before any try/catch — to ensure singleton bean counter resets on every call.

5. **IN-01 and IN-02 do NOT overlap** — the 6 IN-02 files (CarRestorer, PlayoffMatchupRestorer, PlayoffRestorer, PlayoffRoundRestorer, PlayoffSeedRestorer, TrackRestorer) already have 0 `@RequiredArgsConstructor`, so they are NOT in the 18 IN-01 list. Run IN-01 first, then IN-02 (D-07).

6. **MariaDB gate is `docker.available`**, not `mariadb.smoke` (CONTEXT.md D-25 contains a typo — RESEARCH.md Task 9 corrected it). The gate: `@EnabledIfSystemProperty(named = "docker.available", matches = "true")`.

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/backup/`, `src/test/java/org/ctc/backup/`, `src/main/resources/application.yml`
**Files read:** 11 source files + 2 context docs
**Pattern extraction date:** 2026-05-16
