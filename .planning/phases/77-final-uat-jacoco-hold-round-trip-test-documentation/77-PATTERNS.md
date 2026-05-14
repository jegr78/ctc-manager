# Phase 77: Final UAT + JaCoCo Hold + Round-Trip Test + Documentation — Pattern Map

**Mapped:** 2026-05-14
**Files analyzed:** 7 (6 in-repo + 1 external wiki repo)
**Analogs found:** 7 / 7

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` (extend) | Failsafe Integration Test | request-response + batch | `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` | exact |
| `README.md` (add section) | Markdown doc | — | `README.md` §Features / §Quick Start | exact |
| `.screenshots/77/01-backup-page.png` | PNG asset | — | `.screenshots/76/01-banner-visible-seasons.png` (convention only) | convention |
| `.screenshots/77/02-preview-screen.png` | PNG asset | — | same | convention |
| `.screenshots/77/03-import-banner.png` | PNG asset | — | same | convention |
| `.planning/phases/77-.../77-AUTO-UAT.md` | Phase artifact | — | `.planning/phases/76-.../76-AUTO-UAT.md` | exact |
| `ctc-manager.wiki.git/Backup-and-Restore.md` | External wiki doc | — | `docs/operations/import-runbook.md` (structure) | role-match |

---

## Pattern Assignments

---

### File: `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java`

**Role:** Failsafe Integration Test extension (in-place)
**Closest analog:** `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` (Testcontainers wiring, round-trip helpers) AND `src/test/java/org/ctc/admin/SecurityIntegrationTest.java` (`@Nested` profile pattern)

---

#### 1. Testcontainers MariaDB wiring

Copy verbatim from `BackupImportMariaDbSmokeIT.java` lines 85–111:

```java
// BackupImportMariaDbSmokeIT.java lines 85-111
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@EnabledIfSystemProperty(named = "docker.available", matches = "true",
        disabledReason = "Set -Ddocker.available=true (with a running Docker daemon) to run the MariaDB Testcontainers round-trip IT")
class BackupImportMariaDbSmokeIT {

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("ctc_test")
            .withUsername("ctc")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideJdbcUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> mariadb.getJdbcUrl() + "?rewriteBatchedStatements=true");
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
    }
```

**Phase 77 delta:** These annotations and the `@Container` + `@DynamicPropertySource` block move INSIDE the `MariaDbRoundTripTests @Nested` class, not at the top-level class. The outer `BackupRoundTripIT` class retains its existing `@SpringBootTest @ActiveProfiles("dev")` (for the 4 Phase-73 tests).

---

#### 2. `@Nested` + `@SpringBootTest` + `@ActiveProfiles` pattern

Copy structure from `SecurityIntegrationTest.java` lines 15–69:

```java
// SecurityIntegrationTest.java lines 15-69
class SecurityIntegrationTest {

    @Nested
    @SpringBootTest(properties = { ... })
    @AutoConfigureMockMvc
    @ActiveProfiles("prod")
    class ProdProfileSecurityTest {
        @Autowired private MockMvc mockMvc;

        @Test
        void givenNoCredentials_whenAccessAdmin_thenUnauthorized() throws Exception { ... }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    class DevProfileSecurityTest {
        @Autowired private MockMvc mockMvc;

        @Test
        void givenDevProfile_whenAccessAdmin_thenOk() throws Exception { ... }
    }
}
```

**Phase 77 delta:** Replace `@AutoConfigureMockMvc` with `@Testcontainers` (MariaDB nested) and `@EnabledIfSystemProperty` guard. Replace `MockMvc` with the backup service beans (`BackupImportService`, `BackupArchiveService`, etc.). Each `@Nested` class declares its own `@Autowired` fields — beans come from that class's own `ApplicationContext`, not the outer class's context.

**Concrete target layout for `BackupRoundTripIT`:**

```java
// D-06 target layout
@SpringBootTest
@ActiveProfiles("dev")
class BackupRoundTripIT {

    // === Existing Phase-73 @Autowired fields and 4 @Test methods (UNTOUCHED) ===

    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    class H2RoundTripTests {
        @Autowired BackupImportService backupImportService;
        @Autowired BackupArchiveService backupArchiveService;
        @Autowired TestDataService testDataService;
        @Autowired BackupSchema backupSchema;
        @Autowired JdbcTemplate jdbcTemplate;
        @Autowired @Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper;
        @Value("${app.backup.staging-dir}") String stagingDirRaw;

        @Test
        void givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch() { ... }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("local")
    @Testcontainers
    @EnabledIfSystemProperty(named = "docker.available", matches = "true",
            disabledReason = "Set -Ddocker.available=true (with Docker daemon) to run MariaDB round-trip IT")
    class MariaDbRoundTripTests {
        @Container
        static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
                .withDatabaseName("ctc_test").withUsername("ctc").withPassword("test");

        @DynamicPropertySource
        static void overrideJdbcUrl(DynamicPropertyRegistry registry) { /* mirror SmokeIT */ }

        @Autowired BackupImportService backupImportService;
        // ... same fields as H2RoundTripTests ...

        @Test
        void givenLiveMariaDb_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch() { ... }
    }
}
```

---

#### 3. `captureRowCounts()` / `exportToBytes()` / `awaitAuditRow()` helper signatures

Copy verbatim from `BackupImportMariaDbSmokeIT.java` lines 216–267:

```java
// BackupImportMariaDbSmokeIT.java lines 90-90 (class-level constant)
private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[a-z_]+$");

// BackupImportMariaDbSmokeIT.java lines 216-220 (exportToBytes)
private byte[] exportToBytes() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    backupArchiveService.writeZip(baos, Instant.now());
    return baos.toByteArray();
}

// BackupImportMariaDbSmokeIT.java lines 228-240 (captureRowCounts)
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

// BackupImportMariaDbSmokeIT.java lines 253-267 (awaitAuditRow)
private DataImportAudit awaitAuditRow(UUID auditUuid, Duration timeout) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    Optional<DataImportAudit> maybe;
    while (Instant.now().isBefore(deadline)) {
        maybe = dataImportAuditRepository.findById(auditUuid);
        if (maybe.isPresent()) { return maybe.get(); }
        Thread.sleep(100L);
    }
    maybe = dataImportAuditRepository.findById(auditUuid);
    return maybe.orElseThrow(() -> new AssertionError(
            "data_import_audit row " + auditUuid + " did not materialize within " + timeout));
}
```

**Phase 77 delta:** These helpers must be duplicated inside each `@Nested` class (not the outer class), because they reference `@Autowired` beans from that nested class's own `ApplicationContext`. `awaitAuditRow()` is optional for Phase 77 round-trip tests — only needed if the test asserts on the audit row (per SmokeIT precedent it can be included for parity; round-trip tests may skip it).

---

#### 4. `BackupArchiveService.writeZip(OutputStream, Instant)` call shape

From `BackupImportMariaDbSmokeIT.java` lines 216–219 and `BackupRoundTripIT.java` lines 63–67:

```java
// BackupRoundTripIT.java lines 63-67 (existing Phase-73 usage — outer class)
Instant exportDate = Instant.parse("2026-05-12T07:00:00Z");
ByteArrayOutputStream out = new ByteArrayOutputStream();
archiveService.writeZip(out, exportDate);

// BackupImportMariaDbSmokeIT.java lines 216-219 (helper wrapper)
private byte[] exportToBytes() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    backupArchiveService.writeZip(baos, Instant.now());
    return baos.toByteArray();
}
```

Note: The outer class field is named `archiveService`; the SmokeIT names it `backupArchiveService`. Phase 77 nested classes should use `backupArchiveService` to match the SmokeIT convention (the outer class field name is legacy from Phase 73 and should not be changed).

---

#### 5. `BackupImportService.stage(MultipartFile) + execute(UUID)` call shape

From `BackupImportMariaDbSmokeIT.java` lines 168–174:

```java
// BackupImportMariaDbSmokeIT.java lines 168-174
byte[] zipBytes = exportToBytes();
MockMultipartFile file = new MockMultipartFile(
        "file", "mariadb-smoke-export.zip", "application/zip", zipBytes);
BackupImportPreview preview = backupImportService.stage(file);
UUID stagingId = preview.stagingId();

BackupImportResult result = backupImportService.execute(stagingId);
```

**Phase 77 delta:** Use a distinct filename per nested class for clarity, e.g., `"h2-round-trip-export.zip"` and `"mariadb-round-trip-export.zip"`.

---

#### 6. `backupObjectMapper` qualifier injection

From `BackupRoundTripIT.java` lines 56–57 (existing outer class):

```java
// BackupRoundTripIT.java lines 56-57
@Autowired
@Qualifier("backupObjectMapper")
private ObjectMapper backupObjectMapper;
```

Each `@Nested` class that performs SHA-256 hashing must redeclare this field with the same `@Qualifier`. The SHA-256 helper:

```java
// New for Phase 77 — add inside each @Nested class
private byte[] hashEntity(Object entity) throws Exception {
    byte[] bytes = backupObjectMapper.writeValueAsBytes(entity);
    return MessageDigest.getInstance("SHA-256").digest(bytes);
}
```

Assert pattern (from RESEARCH.md §SHA-256 Hashing):

```java
// Assertion pattern using AssertJ byte-array containsExactly
assertThat(postHash)
    .as("SHA-256 of Race %s must be byte-equal after round-trip\npre=%s\npost=%s",
        preRace.getId(),
        HexFormat.of().formatHex(preHash),
        HexFormat.of().formatHex(postHash))
    .containsExactly(preHash);
```

---

#### 7. `@BeforeEach` seed + staging-dir setup

From `BackupImportMariaDbSmokeIT.java` lines 136–146:

```java
// BackupImportMariaDbSmokeIT.java lines 136-146
@BeforeEach
void seedFixture() throws IOException {
    testDataService.seed();
    stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
    Files.createDirectories(stagingDir);
}
```

Copy verbatim inside each `@Nested` class. The `stagingDirRaw` is injected via `@Value("${app.backup.staging-dir}")`.

---

#### 8. Given-When-Then test naming

From `BackupImportMariaDbSmokeIT.java` line 153 and `BackupRoundTripIT.java` line 60:

```java
// BackupImportMariaDbSmokeIT.java line 153
void givenDevFixtureOnMariaDb_whenRoundTripExecuted_thenAllRowCountsMatch()

// BackupRoundTripIT.java line 60
void givenDevFixture_whenWriteZipAndReadManifest_thenManifestRoundTripsThroughBackupObjectMapper()
```

Phase 77 test method names (from D-06):
- `givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch()`
- `givenLiveMariaDb_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch()`

---

#### 9. Import block for the nested classes

Based on `BackupImportMariaDbSmokeIT.java` lines 1–38 and `BackupRoundTripIT.java` lines 1–22:

```java
// Imports to add to BackupRoundTripIT.java for the new @Nested classes
import org.ctc.admin.TestDataService;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.schema.EntityRef;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
```

---

**Project convention notes:**
- No `@RequiredArgsConstructor` / `@Slf4j` in test classes — Spring `@Autowired` field injection is the convention for integration tests throughout the project.
- No Lombok on test classes (per project-wide test patterns in TESTING.md).
- `@Transactional` is NOT used on these IT methods — the round-trip requires committed transactions (wipe + restore must commit for row counts to be observable); rollback would defeat the test.
- Test classes live in `src/test/java/org/ctc/backup/service/` — mirrors the production package of the classes under test.

---

### File: `README.md`

**Role:** README section addition
**Closest analog:** `README.md` lines 15–30 (`## Features` section) for bullet style; lines 32–45 (`## Quick Start`) for code block + heading style.

**Insertion point:** After `## Features` (line 30), before `## Quick Start` (line 32).

**README heading and bullet convention** (from `README.md` lines 15–30):

```markdown
## Features

- **Seasons & Matchdays** — League and Swiss-system formats with configurable rounds
- **Teams & Sub-Teams** — Parent/child team hierarchy with sub-team lineups per matchday
```

**Code block convention** (from `README.md` lines 33–45):

```markdown
## Quick Start

```bash
# Development (H2 in-memory, port 9090)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
```

**Section to insert** (D-09 structure, ~30-50 lines):

```markdown
## Backup & Restore

v1.10 introduces a full database backup/restore feature accessible via `/admin/backup`.

### Export

1. Navigate to `/admin/backup` in the admin sidebar.
2. Click **Export Backup** — a ZIP file (`ctc-backup-<ISO-instant>.zip`) downloads immediately.
3. Store the ZIP in a safe location. Each export captures all 24 entity tables.

### Import

1. Navigate to `/admin/backup` and upload a ZIP via **Import Backup**.
2. Review the preview: per-table row counts (current vs. backup) and schema-version match indicator.
3. Check the **I understand** confirmation and click **Execute Import**. The database is replaced atomically.

> **Schema-Version lock:** The import is rejected if the backup's schema version does not match the
> current application version. Do not import backups from a different major schema version.

### Recovery

If an import fails or you need to revert, see [`docs/operations/import-runbook.md`](docs/operations/import-runbook.md)
for step-by-step recovery from `data/.import-backups/<ts>/`.

### Full Guide

See the [Backup & Restore wiki page](../../wiki/Backup-and-Restore) for the step-by-step export
workflow, import workflow, schema-version explanation, and recovery procedures.
```

**Phase 77 delta from analog:** New `## Backup & Restore` section with `###` sub-headings. No bold-bullet list items (like Features uses) — the content is procedural steps using numbered lists. Cross-link to local runbook uses relative path `docs/operations/import-runbook.md`. Cross-link to wiki uses `../../wiki/Backup-and-Restore` (GitHub's relative wiki URL convention from the repo root).

---

### File: `.screenshots/77/01-backup-page.png`

**Role:** PNG asset captured via `playwright-cli`
**Closest analog:** `.screenshots/76/01-banner-visible-seasons.png` (project convention — not on disk as a pattern reference, but the path convention is established by Phase 76).

**Capture command:**

```bash
# Dev server must be running: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
mkdir -p .screenshots/77
playwright-cli screenshot http://localhost:9090/admin/backup .screenshots/77/01-backup-page.png
```

**Project convention notes:** Screenshots live at `.screenshots/<phase>/`, committed to the main repo. Per `feedback_screenshots_folder` memory. Default viewport 1280×720 (CD-06). Never at repo root.

**Phase 77 delta from analog:** Different URL and different target directory number (`77` vs `76`). Content: Export button + Import file picker visible.

---

### File: `.screenshots/77/02-preview-screen.png`

**Role:** PNG asset captured via `playwright-cli` interactive mode
**Closest analog:** Same `.screenshots/76/` convention.

**Capture approach (RESEARCH §8 — requires interactive session):**

```bash
# Requires manual navigation: upload a real ZIP to trigger the preview screen
playwright-cli open http://localhost:9090/admin/backup
# In the interactive browser: upload a backup ZIP → navigate to preview → screenshot via DevTools
# OR: playwright-cli screenshot <preview-URL-after-upload> .screenshots/77/02-preview-screen.png
```

**Phase 77 delta from analog:** Cannot be captured non-interactively — preview screen requires a staged file. Use `playwright-cli open` to interact, then save screenshot.

---

### File: `.screenshots/77/03-import-banner.png`

**Role:** PNG asset showing the yellow read-only banner during an active import
**Closest analog:** `.screenshots/76/01-banner-visible-seasons.png` (captures the same banner component).

**Capture approach (RESEARCH §8, RESEARCH open question on D-17 compliance):**

```bash
# Approach: trigger a real import in one tab, immediately navigate to /admin/seasons in another
# The banner is rendered by ImportLockBannerAdvice when importLockService.isLocked() == true
playwright-cli screenshot http://localhost:9090/admin/seasons .screenshots/77/03-import-banner.png
# (taken while an import is in progress)
```

**Phase 77 delta from analog:** Phase 76 screenshot was taken during a real UAT import run. Phase 77 uses the same real-import approach (D-17 forbids new test-component files). Banner HTML is rendered by `admin/layout.html` (Phase 76 D-12 wiring).

---

### File: `.planning/phases/77-.../77-AUTO-UAT.md`

**Role:** Phase artifact — automated UAT checklist
**Closest analog:** `.planning/phases/76-.../76-AUTO-UAT.md` — full structure to mirror.

**Front-matter block** (from `76-AUTO-UAT.md` lines 1–9):

```yaml
---
phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
executed: 2026-05-14T20:38:00Z
server_profile: dev,demo
total: 5
passed: 5
failed: 0
skipped: 0
---
```

**Phase 77 version:**

```yaml
---
phase: 77-final-uat-jacoco-hold-round-trip-test-documentation
executed: <ISO-instant of execution>
server_profile: dev,demo
total: 6
passed: <N>
failed: <N>
skipped: <N>
---
```

**Section structure** (from `76-AUTO-UAT.md` lines 11–103 — map to D-13 items):

```markdown
# Auto-UAT Report: Phase 77

<lead paragraph: what was verified and when>

## Results

### 1. ./mvnw verify -Pe2e BUILD SUCCESS (H2 + Rollback IT)

- **Status:** passed / failed
- **Evidence:**
  - BackupRoundTripIT$H2RoundTripTests: <test output line>
  - BackupImportRollbackIT: <test output line>
  - Full suite BUILD SUCCESS

### 2. JaCoCo Line Coverage Measured

- **Status:** passed
- **Evidence:**
  - Measured: NN.N% — N.N% buffer over the 82% gate
  - Command: `awk -F',' 'NR>1{miss+=$8;cov+=$9}END{printf "%.1f%%\n", cov/(miss+cov)*100}' target/site/jacoco/jacoco.csv`

### 3. README "Backup & Restore" Section Renders

- **Status:** passed
- **Evidence:**
  - playwright-cli open https://github.com/jegr78/ctc-manager — section visible
  - Cross-links to runbook and wiki page both resolve

### 4. GitHub Wiki Page Exists with 3 Screenshots

- **Status:** passed
- **Evidence:**
  - playwright-cli open https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore
  - 3 screenshots render (raw.githubusercontent.com URLs)
  - Internal wiki links work

### 5. BackupRoundTripIT$MariaDbRoundTripTests GREEN (local run)

- **Status:** passed
- **Evidence:**
  - ./mvnw -Ddocker.available=true -Dit.test="BackupRoundTripIT#MariaDbRoundTripTests" verify
  - <test output line or CI run link>

### 6. 3 Screenshots Committed to .screenshots/77/

- **Status:** passed
- **Evidence:**
  - test -f .screenshots/77/01-backup-page.png: OK
  - test -f .screenshots/77/02-preview-screen.png: OK
  - test -f .screenshots/77/03-import-banner.png: OK

## Summary

| # | Test | Status | Evidence |
|---|------|--------|----------|
| 1 | ./mvnw verify -Pe2e BUILD SUCCESS | passed | CI logs |
| 2 | JaCoCo NN.N% ≥ 82% | passed | jacoco.csv |
| 3 | README section renders | passed | playwright-cli |
| 4 | Wiki page + screenshots | passed | playwright-cli |
| 5 | MariaDB round-trip green | passed | local mvn run |
| 6 | Screenshots committed | passed | filesystem |

**6/6 passed, 0 failed, 0 skipped.** Phase 77 AUTO-UAT complete.
```

**Phase 77 delta from analog:** 6 items instead of 5. No human-UAT narrative (D-13 replaces HUMAN-UAT entirely). JaCoCo measurement result appears as a data line inside item 2. MariaDB item has a "(local run)" caveat because CI does not pass `-Ddocker.available=true` by default.

---

### File: `ctc-manager.wiki.git/Backup-and-Restore.md`

**Role:** External wiki doc — new page in the separate `https://github.com/jegr78/ctc-manager.wiki.git` repo
**Closest analog:** `docs/operations/import-runbook.md` (Phase 76 D-22) — for section heading style, step-by-step numbered list style, and cross-link conventions. No on-disk analog in the wiki repo itself (first wiki page per D-08).

**GitHub wiki push pattern** (from RESEARCH.md §7 — no on-disk precedent, canonical pattern):

```bash
# Step 1: clone wiki repo to a temp directory
WIKI_TMP=$(mktemp -d)
git clone https://github.com/jegr78/ctc-manager.wiki.git "$WIKI_TMP"

# Step 2: write the page (file name = URL slug with dashes)
# File: Backup-and-Restore.md → URL: /wiki/Backup-and-Restore
cat > "$WIKI_TMP/Backup-and-Restore.md" << 'EOF'
# Backup & Restore
...
EOF

# Step 3: commit and push
git -C "$WIKI_TMP" add Backup-and-Restore.md
git -C "$WIKI_TMP" commit -m "docs: add Backup & Restore wiki page (Phase 77)"
git -C "$WIKI_TMP" push origin master

# Step 4: cleanup
rm -rf "$WIKI_TMP"
```

**Auth prerequisite:** Verify `gh auth status` shows authenticated as `jegr78` before the push step. The `gh` CLI credential helper handles HTTPS auth for `github.com` automatically when `gh auth login` has been completed.

**Wiki page structure** (mirroring D-09 README structure but with more depth, and image embeds):

```markdown
# Backup & Restore

> Available since v1.10. Accessible at `/admin/backup`.

## Overview

<1-paragraph description of the feature>

## Export

![Backup page](/backup-page.png) (embedded via raw.githubusercontent.com — see §Screenshots below)

Step-by-step:

1. ...
2. ...
3. ...

## Import

![Preview screen](...)

Step-by-step:

1. ...
2. ...
3. ...

> **Schema-Version lock:** ...

## Schema Version

<explanation of SCHEMA_VERSION = 1, what it means, when it blocks import>

## Recovery

If an import fails, a pre-import auto-backup ZIP is saved to `data/.import-backups/<ts>/`.

See [import-runbook.md](https://github.com/jegr78/ctc-manager/blob/master/docs/operations/import-runbook.md)
for step-by-step recovery.

## Screenshots

All screenshots are stored in the main repository and embedded here via stable raw URLs:

- `https://raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/01-backup-page.png`
- `https://raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/02-preview-screen.png`
- `https://raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/03-import-banner.png`
```

**Image embed syntax for GitHub Wiki:**

```markdown
![Backup Page](https://raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/01-backup-page.png)
```

**Phase 77 delta from analog:** This is a user-facing how-to document (not an operator runbook). `docs/operations/import-runbook.md` is an operator-recovery reference. The wiki page focuses on the normal Export + Import workflow with screenshots. Cross-links go TO the runbook (not the other way around).

---

## Shared Patterns

### Given-When-Then Comment Structure
**Source:** `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` lines 154–203
**Apply to:** Both `@Nested` test methods in `BackupRoundTripIT`

```java
// given — seed pre-state and capture per-entity row counts and sample hashes
Map<String, Long> preExportCounts = captureRowCounts();
// ... sample entity queries + hash computation ...

// when — export → wipe → import (full round-trip)
byte[] zipBytes = exportToBytes();
BackupImportPreview preview = backupImportService.stage(new MockMultipartFile(...));
BackupImportResult result = backupImportService.execute(preview.stagingId());

// then — row-count parity + SHA-256 sample entity equality
assertThat(captureRowCounts()).isEqualTo(preExportCounts);
assertThat(postHash).containsExactly(preHash);
```

### `@BeforeEach` Setup Pattern
**Source:** `BackupImportMariaDbSmokeIT.java` lines 136–146
**Apply to:** Both `@Nested` classes in `BackupRoundTripIT`

```java
@BeforeEach
void seedFixture() throws IOException {
    testDataService.seed();
    stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
    Files.createDirectories(stagingDir);
}
```

### Deterministic First-Row Entity Selection
**Source:** CONTEXT.md D-04 + RESEARCH.md §5
**Apply to:** Both `@Nested` test methods (pre-export capture)

```java
// Race — smallest UUID by BINARY(16) ordering
Race preRace = raceRepository.findAll(Sort.by(Sort.Order.asc("id"))).getFirst();

// SeasonDriver — UUID PK
SeasonDriver preSeasonDriver = seasonDriverRepository.findAll(Sort.by(Sort.Order.asc("id"))).getFirst();

// Team — root team (parentTeam == null) with smallest id
Team preTeam = teamRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
        .filter(t -> t.getParentTeam() == null)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No root team found in dev fixture"));
```

### Import Block Import-Order Convention
**Source:** `BackupImportMariaDbSmokeIT.java` lines 1–38
**Apply to:** `BackupRoundTripIT.java` (add these imports above existing imports)

Order: own project (`org.ctc.*`) → Jakarta (none in test) → Spring (`org.springframework.*`) → Testcontainers → JUnit → Java standard library (`java.*`)

---

## No Analog Found

No files are entirely without analog in this phase. All patterns have direct precedents in the codebase.

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `ctc-manager.wiki.git/Backup-and-Restore.md` | External wiki doc | — | No prior wiki pages exist (first page). Structure borrowed from `docs/operations/import-runbook.md`; push mechanics documented from RESEARCH.md §7 only. |

---

## Metadata

**Analog search scope:** `src/test/java/org/ctc/backup/`, `src/test/java/org/ctc/admin/`, `README.md`, `.planning/phases/76-*/76-AUTO-UAT.md`
**Files scanned:** 7 source files fully read
**Pattern extraction date:** 2026-05-14
