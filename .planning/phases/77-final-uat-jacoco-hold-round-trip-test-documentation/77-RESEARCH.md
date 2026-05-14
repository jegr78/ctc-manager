# Phase 77: Final UAT + JaCoCo Hold + Round-Trip Test + Documentation — Research

**Researched:** 2026-05-14
**Domain:** Spring Boot integration testing (Testcontainers, @Nested SpringBootTest), JDK MessageDigest SHA-256, JaCoCo line-coverage measurement, GitHub Wiki push workflow, Playwright-CLI screenshots
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Extend `BackupRoundTripIT` in-place. Keep the existing 4 manifest-only `@Test` methods byte-identical. Add new `@Test` methods inside two `@Nested` classes.
- **D-02:** Sample entities = `Race` + `SeasonDriver` + `Team` (distinct restore-code-path coverage).
- **D-03:** SHA-256 hashes the in-DB row serialized via `@Qualifier("backupObjectMapper")`, NOT the ZIP bytes. Pre-export and post-import serializations must produce identical byte arrays.
- **D-04:** Row pick = first row by natural ordering. `Race`: `findAll(Sort.by(Order.asc("id"))).getFirst()`. `SeasonDriver`: `findAll(Sort.by("id")).getFirst()` (has UUID `id`). `Team`: `findAll(Sort.by("id")).stream().filter(t -> t.getParentTeam() == null).findFirst()`.
- **D-05:** Testcontainers in main `ci.yml` — do NOT touch `mariadb-migration-smoke.yml`.
- **D-06:** Single class with two `@Nested` profile classes — `H2RoundTripTests` (`@ActiveProfiles("dev")`) and `MariaDbRoundTripTests` (`@ActiveProfiles("local")` + `@Testcontainers`).
- **D-07:** Both engines run the FULL round-trip + SHA-256.
- **D-08:** Wiki page pushed to `https://github.com/jegr78/ctc-manager.wiki.git` via `gh` CLI clone + push.
- **D-09:** README "Backup & Restore" = short overview (~30-50 lines) + cross-links. Structure: description → Export quick-ref → Import quick-ref → Recovery pointer → Full-guide wiki link.
- **D-10:** 3 screenshots via `playwright-cli` stored in `.screenshots/77/`, embedded in Wiki via raw.githubusercontent.com URLs.
- **D-11:** `pom.xml` minimum stays at `0.82` — DO NOT raise.
- **D-12:** ZERO new coverage exclusions in `pom.xml`.
- **D-13:** AUTO-UAT only (`77-AUTO-UAT.md`). No `77-HUMAN-UAT.md`.
- **D-14:** `BackupImportRollbackIT` is touched ZERO times.
- **D-15:** Phase 77 is NOT the milestone-closer. Phase 79 will be.
- **D-16:** `pom.xml` `<version>1.8.0-SNAPSHOT</version>` stays unchanged.
- **D-17:** Touchable files: `BackupRoundTripIT.java`, `README.md`, `pom.xml` (zero diff), `.screenshots/77/`, `77-AUTO-UAT.md`, `ctc-manager.wiki.git/Backup-and-Restore.md` (external).
- **D-18:** No new Maven dependencies. SHA-256 via `java.security.MessageDigest`. `java.util.HexFormat` for hex rendering.
- **D-19:** No new Flyway migrations.
- **D-20:** No new templates, CSS, or controllers.

### Claude's Discretion

- **CD-01:** Exact `@Nested` class names — `H2RoundTripTests` + `MariaDbRoundTripTests` recommended.
- **CD-02:** Helper extraction — private methods inside `BackupRoundTripIT` first; extract `RoundTripScenario` class only if a second consumer emerges.
- **CD-03:** Read-only banner screenshot mechanism — `@TestComponent` flipping `ImportLockService.isLocked()` recommended.
- **CD-04:** Wiki repo discovery + auth — verify existence before executing plan.
- **CD-05:** README placement — after "Features", before "Quick Start".
- **CD-06:** Screenshot resolution — `playwright-cli` defaults (1280×720).

### Deferred Ideas (OUT OF SCOPE)

- Phase 79: Code Cleanup + Test Performance Optimization (new milestone-closer).
- `pom.xml` version bump from `1.8.0-SNAPSHOT`.
- Raising JaCoCo minimum above `0.82`.
- Per-season Export/Import selectivity (v1.11+).
- SHA-256 checksum file `manifest.sha256` (v1.11+).
- Verify-Only Import Mode (v1.11+).
- `HUMAN-UAT` file for Phase 77.
- Helper extraction (`RoundTripScenario`) unless second consumer exists.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| QUAL-01 | JaCoCo line coverage ≥ 82% held (v1.9 baseline 87.02%) | §JaCoCo Measurement shows current gate at `0.82` in pom.xml, unchanged. Measure via `./mvnw verify`, read `target/site/jacoco/jacoco.csv`. |
| QUAL-02 | `BackupRoundTripIT` on H2 AND MariaDB: export → wipe → import → per-table row counts equal + SHA-256 byte-equal on ≥ 3 sample entities | §Round-Trip Mechanics + §SHA-256 Hashing + §Testcontainers Pattern fully document the implementation approach. |
| QUAL-04 | `BackupImportRollbackIT` from Phase 75 stays green in final `verify -Pe2e` run | §Read-Only Verification — Phase 75 shipped this IT, Phase 77 only verifies it continues to pass. D-14: zero modifications. |
| QUAL-05 | README "Backup & Restore" section + GitHub Wiki page "Backup-and-Restore" | §README Structure + §GitHub Wiki Workflow document exact insertion point and push mechanism. |
</phase_requirements>

---

## Summary

Phase 77 is a verification-and-documentation phase. It extends one existing test class (`BackupRoundTripIT`) with two `@Nested` classes covering H2 and MariaDB engines, writes a README section and GitHub Wiki page, captures 3 screenshots, and measures JaCoCo coverage without touching the gate value. No new production code, no new dependencies, no Flyway migrations.

The research confirms all prior-phase infrastructure is in place and reusable: `BackupArchiveService.writeZip(OutputStream, Instant)` is the export entry point; `BackupImportService.stage(MultipartFile)` + `execute(UUID)` is the import entry point; `captureRowCounts()` and the `@DynamicPropertySource` Testcontainers pattern are both already implemented in `BackupImportMariaDbSmokeIT` and can be copied verbatim. The SHA-256 hashing uses only JDK classes already on the classpath.

**Critical finding:** `BackupImportMariaDbSmokeIT` uses `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` and the current `ci.yml` `build-and-test` job does NOT pass `-Ddocker.available=true`. This means the existing Phase 75 MariaDB IT is skipped in CI. The new `MariaDbRoundTripTests` @Nested class should mirror this same guard so CI behavior is consistent. CONTEXT D-05 says "Testcontainers in main ci.yml" — interpreted as "the test runs via Failsafe's default `*IT.java` pattern" (which is already true), not "the CI workflow must pass the guard property." The D-05 guard is already in the main ci.yml workflow (not mariadb-migration-smoke.yml).

**Primary recommendation:** Mirror `BackupImportMariaDbSmokeIT` exactly for the Testcontainers wiring, copy its `captureRowCounts()` helper, add SHA-256 hashing as private methods, and keep all new code inside `BackupRoundTripIT.java`.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Round-trip test (export → wipe → import → assert) | Integration test (`src/test/java`) | — | Pure test code; no production tier involved |
| SHA-256 entity hashing | Integration test (in-test JDK `MessageDigest`) | — | JDK primitive; no new production dependency |
| Row-count parity assertion (all 24 tables) | Integration test (`JdbcTemplate.queryForObject`) | — | Mirrors Phase 75 pattern already proven |
| JaCoCo measurement | Maven `verify` lifecycle | — | Already wired in pom.xml |
| README update | Documentation (`README.md`) | — | Static markdown only |
| GitHub Wiki push | External repo (`ctc-manager.wiki.git`) | `gh` CLI | Separate git repo per D-08 |
| Screenshots | `.screenshots/77/` (in main repo) | `playwright-cli` tool | Per `feedback_screenshots_folder` memory |

---

## Research Findings by Topic

### 1. Round-Trip Mechanics in Tests

**Verified from:** `BackupImportMariaDbSmokeIT.java` (Phase 75) [VERIFIED: codebase]

The full round-trip in an IT class follows this exact sequence:

1. **Seed:** `testDataService.seed()` in `@BeforeEach` loads the full dev fixture (Saison 2023 + 2024 + 2024-Empty + 2026).
2. **Snapshot pre-export counts:** `captureRowCounts()` iterates `backupSchema.getExportOrder()` and calls `jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)` for each table. Returns `LinkedHashMap<String, Long>`.
3. **Export to bytes:** `backupArchiveService.writeZip(baos, Instant.now())` — `backupArchive` is autowired `BackupArchiveService`. Returns `byte[]` via `ByteArrayOutputStream`.
4. **Stage:** `backupImportService.stage(new MockMultipartFile("file", "...", "application/zip", zipBytes))` — returns `BackupImportPreview` carrying `stagingId`.
5. **Execute import (wipe + restore):** `backupImportService.execute(stagingId)` — runs inside `@Transactional(REQUIRED, READ_COMMITTED)`. Returns `BackupImportResult`.
6. **Assert row-count parity:** Capture post-import counts via same `captureRowCounts()` method, assert `postImportCounts.equals(preExportCounts)`.
7. **Assert SHA-256 (Phase 77 addition):** Query first entity of each sample type pre-export and post-import, serialize via `backupObjectMapper.writeValueAsBytes(entity)`, compute `MessageDigest.getInstance("SHA-256").digest(bytes)`, assert `Arrays.equals(preHash, postHash)`.

**Wipe strategy:** `BackupImportService.execute()` internally calls `wipeAllTables()` (native DELETE in FK-reverse order via `EntityManager.createNativeQuery`) — no explicit wipe call needed in the test. The import IS the wipe + restore.

**Staging directory setup:** `@BeforeEach` must call `Files.createDirectories(stagingDir)` where `stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize()`. This is already the pattern in `BackupImportMariaDbSmokeIT`.

**Reuse:** The entire `captureRowCounts()` helper, `exportToBytes()` helper, and `awaitAuditRow()` helper from `BackupImportMariaDbSmokeIT` can be copied verbatim as private methods in `BackupRoundTripIT`. The existing 4 `@Test` methods in `BackupRoundTripIT` already use `readEntry()` and `countEntriesMatching()` private helpers — Phase 77 adds alongside those.

---

### 2. SHA-256 Hashing Through `backupObjectMapper`

**Verified from:** `BackupObjectMapperConfig.java`, `BackupRoundTripIT.java` class-level Javadoc, CONTEXT D-03 [VERIFIED: codebase]

The hashing approach:

```java
// Pre-export: query sample entity
Race preRace = raceRepository.findAll(Sort.by(Sort.Order.asc("id"))).getFirst();
byte[] preBytes = backupObjectMapper.writeValueAsBytes(preRace);
byte[] preHash = MessageDigest.getInstance("SHA-256").digest(preBytes);

// Post-import: query same entity by its persisted id
Race postRace = raceRepository.findById(preRace.getId())
    .orElseThrow(() -> new AssertionError("Race " + preRace.getId() + " missing after import"));
byte[] postBytes = backupObjectMapper.writeValueAsBytes(postRace);
byte[] postHash = MessageDigest.getInstance("SHA-256").digest(postBytes);

// Assert
assertThat(postHash)
    .as("SHA-256 of Race %s must be byte-equal after round-trip", preRace.getId())
    .containsExactly(preHash);
```

**Why this is deterministic:** `backupObjectMapper` is configured with `WRITE_DATES_AS_TIMESTAMPS=false` (ISO-8601 strings), `JavaTimeModule`, and `FAIL_ON_UNKNOWN_PROPERTIES=true`. All 24 per-entity Jackson MixIns are registered. Jackson 2.x serializes object fields in declaration order (with `ObjectMapper` configured without `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`), and entity fields are ordered by the JPA entity class definition. UUID fields stored as `BINARY(16)` are deserialized back to `UUID` objects and serialized as UUID strings — consistent both times.

**`@JsonIgnore` scope:** MixIns apply `@JsonIgnore` to lazy collection fields and back-references (e.g., `Race.matchday` is included as a reference ID, not as a full nested object, via `@JsonIdentityInfo`). The same MixIn rules apply to both the pre-export and post-import serialization passes — the hash surface is identical.

**`AuditingEntityListener` bypass contract:** Phase 75's `JdbcTemplate.batchUpdate` bypass ensures `created_at` / `updated_at` are preserved verbatim from the export ZIP. Post-import, the entity's `createdAt` and `updatedAt` values must equal the pre-export values. The SHA-256 hash covers these fields (they are wire fields per the MixIn configuration), so a hash match proves the auditing bypass worked correctly. [VERIFIED: Phase 75 CONTEXT D-04 + BackupImportService.java]

**`HexFormat` usage in assertion messages (Java 17+):**
```java
String.format("pre=%s, post=%s",
    HexFormat.of().formatHex(preHash),
    HexFormat.of().formatHex(postHash))
```
`HexFormat` is available on Java 17+; the project runs Java 25. [VERIFIED: pom.xml `<java.version>25</java.version>`]

---

### 3. Row-Count Parity for All 24 Entities

**Verified from:** `BackupImportMariaDbSmokeIT.captureRowCounts()` [VERIFIED: codebase]

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

This private helper can be copied verbatim into `BackupRoundTripIT`. The `SAFE_TABLE_NAME` pattern (`^[a-z_]+$`) must also be copied as a class-level constant.

**Note on `data_import_audit` exclusion:** `BackupSchema.getExportOrder()` explicitly filters to `org.ctc.domain.model.*` entities only (via package-name gate in `BackupSchema.initializeExportOrder()`). `DataImportAudit` is in `org.ctc.backup.audit` — it is automatically excluded. [VERIFIED: BackupSchema.java lines 42-45]

---

### 4. Testcontainers + @Nested + @SpringBootTest Interaction

**Verified from:** `BackupImportMariaDbSmokeIT.java`, TESTING.md §Nested Test Classes, `SecurityIntegrationTest` pattern [VERIFIED: codebase]

**Pattern:**

```java
@SpringBootTest
class BackupRoundTripIT {

    // === EXISTING Phase 73 manifest @Test methods (UNTOUCHED) ===

    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    class H2RoundTripTests {
        // Autowired from parent context or fresh child context
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

        @Test
        void givenLiveMariaDb_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch() { ... }
    }
}
```

**`@SpringBootTest` on `@Nested`:** Each `@Nested` class with its own `@SpringBootTest` and `@ActiveProfiles` gets its own isolated Spring application context. This is the `SecurityIntegrationTest` pattern documented in `TESTING.md` §"Nested Test Classes". The outer `BackupRoundTripIT` class-level `@SpringBootTest @ActiveProfiles("dev")` serves the existing 4 manifest-only tests; it does NOT contaminate the nested contexts.

**`@DynamicPropertySource` on static methods of `@Nested` inner classes:** Works in JUnit 5 + Spring Boot because `@DynamicPropertySource` is processed during the `ApplicationContext` refresh cycle for the nested test context. The container is declared `static` so it starts before the nested class's Spring context. [ASSUMED — standard Testcontainers + Spring Boot 3.x/4.x pattern; Phase 75 confirmed it works in this project via `BackupImportMariaDbSmokeIT`]

**`@EnabledIfSystemProperty` guard:** The existing `BackupImportMariaDbSmokeIT` uses this guard with `named = "docker.available", matches = "true"`. The Phase 77 `MariaDbRoundTripTests` should mirror this exact guard. The current `ci.yml` `build-and-test` job runs `./mvnw verify -Dspring.profiles.active=dev` — it does NOT pass `-Ddocker.available=true`. This means both the existing MariaDB smoke IT and the new MariaDB round-trip tests are currently SKIPPED in CI by default. [VERIFIED: ci.yml line 29]

**Research-vs-Context Conflict flag:** CONTEXT D-05 states "Testcontainers in main ci.yml" and the ROADMAP success criterion #1 states "the `mariadb-migration-smoke.yml` CI workflow is BUILD SUCCESS on MariaDB; both runs include `BackupRoundTripIT`". The literal CI gate for MariaDB is currently only enabled when `-Ddocker.available=true` is passed. For Phase 77 to satisfy this criterion in CI, the `build-and-test` job in `ci.yml` would need to pass `-Ddocker.available=true`. This is a pre-existing situation from Phase 75. See §Open Questions #1.

---

### 5. Sample Entity Deterministic First-Row Picking (D-04)

**Verified from:** entity class inspection, CONTEXT D-04 [VERIFIED: codebase]

All three sample entities (`Race`, `SeasonDriver`, `Team`) have a `UUID id` field and extend `BaseEntity`. They are all in `JpaRepository<Entity, UUID>`, which inherits `findAll(Sort sort)` from `PagingAndSortingRepository`.

**Race** — UUID PK `id`, table `races`:
```java
Race preRace = raceRepository.findAll(Sort.by(Sort.Order.asc("id"))).getFirst();
```
UUID BINARY(16) on both H2 and MariaDB sorts lexicographically by byte sequence. Deterministic across both engines for the same dataset.

**SeasonDriver** — UUID PK `id` (not composite-key; the composite constraint is a unique constraint on `season_id + driver_id`, not the PK), table `season_drivers`:
```java
SeasonDriver preSeasonDriver = seasonDriverRepository.findAll(Sort.by(Sort.Order.asc("id"))).getFirst();
```
Note: CONTEXT D-04 mentions `Sort.by("seasonId", "driverId")` but this is the logical path; using `id` (the actual UUID PK) is simpler and equally deterministic. Either works.

**Team** — UUID PK `id`, self-FK `parentTeam`, table `teams`:
```java
Team preTeam = teamRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
        .filter(t -> t.getParentTeam() == null)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No root team found in dev fixture"));
```
The `getParentTeam() == null` filter selects a root team (no parent), which is the entity exercising the 2-pass restore path's pass-1 NULL state-survival.

**Pre-export capture and post-import retrieval:** Store the pre-export entity's `id` UUID. Post-import, use `repository.findById(preId).orElseThrow()` to get the same entity. This is safe because the import preserves all UUIDs (the `EntityRestorer` SQL inserts the original `id` bytes).

---

### 6. JaCoCo Measurement and Reporting

**Verified from:** `pom.xml` lines 306-354 [VERIFIED: codebase]

**Command:** `./mvnw verify` (without `-Pe2e` for the JaCoCo measurement step; then also with `-Pe2e` for the final gate).

**Report location:** `target/site/jacoco/index.html` (human-readable), `target/site/jacoco/jacoco.csv` (machine-readable), `target/site/jacoco/jacoco.xml` (CI integration).

**JaCoCo gate:** pom.xml configures `<counter>LINE</counter>` `<value>COVEREDRATIO</value>` `<minimum>0.82</minimum>` at `BUNDLE` level. This is the enforced gate; the `verify` lifecycle fails if coverage falls below 0.82. [VERIFIED: pom.xml lines 342-350]

**Coverage extraction for `77-AUTO-UAT.md`:** The simplest extraction from `jacoco.csv`:
```bash
# After ./mvnw verify
# jacoco.csv last data row has aggregate totals. Extract INSTRUCTION_COVERED / (INSTRUCTION_MISSED + INSTRUCTION_COVERED).
# Alternatively read the "Total" row from index.html — but CSV is easier to parse.
# The CSV header: GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,...LINE_MISSED,LINE_COVERED,...
# Fastest: grep the "Total" label from jacoco.xml or use awk on jacoco.csv.
awk -F',' 'NR>1{miss+=$4; cov+=$5} END{printf "%.1f%%\n", cov/(miss+cov)*100}' target/site/jacoco/jacoco.csv
```
This computes instruction coverage (the standard metric). For LINE coverage specifically, use columns 8 and 9 (LINE_MISSED, LINE_COVERED). The pom.xml gate is LINE coverage.

**Current minimum:** `0.82` (82%). v1.9 baseline was 87.02%. Phase 72-76 backup code adds new test-covered paths — the actual measured value is expected to be ≥ 82% with a comfort buffer. The planner should run `./mvnw verify` as the first task to get the actual number before writing `77-AUTO-UAT.md`.

---

### 7. GitHub Wiki Repository Workflow

**Verified from:** `git ls-remote https://github.com/jegr78/ctc-manager.wiki.git HEAD` (returns `1c49617f...`) [VERIFIED: git remote check]

The wiki repo EXISTS. It is a standard git repository at `https://github.com/jegr78/ctc-manager.wiki.git`.

**Push workflow (D-08):**
```bash
# Step 1: clone into a temp directory
WIKI_TMP=$(mktemp -d)
git clone https://github.com/jegr78/ctc-manager.wiki.git "$WIKI_TMP"

# Step 2: write the page (GitHub wiki naming: dashes for spaces, no path prefix)
# File: Backup-and-Restore.md → URL slug: /wiki/Backup-and-Restore
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

**Auth:** `gh auth` must be configured for `jegr78`. GitHub wiki repos share the same credential as the main repo. The `gh` CLI's credential helper handles HTTPS auth for `github.com`.

**Verification:** `curl -s -o /dev/null -w "%{http_code}" https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` should return `200` after push. (Note: GitHub wiki pages may have a short propagation delay of ~30s.)

**Wiki naming conventions:**
- File: `Backup-and-Restore.md` (dashes for spaces)
- URL slug: `/wiki/Backup-and-Restore`
- Image embeds: `![alt](https://raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/01-backup-page.png)`

**Note:** `gh repo view jegr78/ctc-manager.wiki` returns a GraphQL "Could not resolve to a Repository" error — this is expected because GitHub does not expose wiki repos through the GitHub API's repository endpoint. The direct `git ls-remote` confirms the repo exists. Use `git clone` directly, not `gh repo clone`, for the wiki repo. [VERIFIED: direct test]

---

### 8. Playwright-CLI Screenshots for D-10

**Verified from:** `feedback_screenshots_folder` memory, `feedback_playwright_cli` memory, Phase 76 AUTO-UAT.md [VERIFIED: codebase]

**Three required screenshots:**

1. **`01-backup-page.png`** — `/admin/backup` with export button + import file picker. Requires dev server running:
   ```bash
   # Server must be running at http://localhost:9090
   playwright-cli screenshot http://localhost:9090/admin/backup .screenshots/77/01-backup-page.png
   ```

2. **`02-preview-screen.png`** — `/admin/backup/preview` or the confirm screen. This requires navigating through the multi-step flow. May need to use `playwright-cli open` to interact and then screenshot, OR load the confirm template from a real staging state.
   - Phase 74 template is `admin/backup-confirm.html`. The URL is `GET /admin/backup/import-preview` redirects to `POST /admin/backup/import-preview` (multipart). This requires a file upload to get to the preview screen — difficult with `playwright-cli screenshot` alone.
   - Recommended: use `playwright-cli open http://localhost:9090/admin/backup` in interactive mode to upload a real ZIP, navigate to preview, then screenshot manually.

3. **`03-import-banner.png`** — any admin page with the yellow read-only banner during a simulated active import.
   - `ImportLockService.isLocked()` is controlled by `lock.isLocked()` on the `ReentrantLock`. The banner is rendered by `ImportLockBannerAdvice` which calls `importLockService.isLocked()`.
   - To trigger the banner without a real import in progress, CD-03 recommends a `@TestComponent` that overrides `ImportLockService` to return `isLocked() = true`. However, D-17 restricts touchable files — no new production or test support classes are allowed (only `BackupRoundTripIT.java`).
   - **Alternative approach (D-17 compliant):** Start an actual slow import in a background thread by uploading a real ZIP and calling `import-execute`, then immediately screenshot another admin page before the import completes. This is flaky but authentic.
   - **Simplest D-17-compliant approach:** Use `playwright-cli open http://localhost:9090/admin/backup` interactively. Click Execute Import on a real ZIP, then quickly open another tab and navigate to `/admin/seasons` to capture the banner. Screenshot manually from the interactive session.
   - **Research note:** The banner in `admin/layout.html` renders when `importLockService.isLocked()` returns true. The `ImportLockBannerAdvice` is a `@ControllerAdvice` that adds the lock status to the model. There is no way to set this to true from a test without either (a) a real in-flight import or (b) a `@TestComponent` override. Since D-17 forbids new test support files, the planner must pick the "real in-flight import" approach for this screenshot.

**Storage:** `.screenshots/77/` directory (create if missing). Screenshots are committed to the main repo, not the wiki repo. [VERIFIED: feedback_screenshots_folder memory]

---

### 9. CI Workflow — Failsafe Pattern

**Verified from:** `pom.xml` lines 269-299, `ci.yml` [VERIFIED: codebase]

The Failsafe `default-it` execution (`pom.xml` lines 282-299) includes all `**/*IT.java` files except `**/e2e/**`. Phase 77's new `@Nested` inner classes (`H2RoundTripTests`, `MariaDbRoundTripTests`) are inner classes of `BackupRoundTripIT.java`. Failsafe discovers the outer class `BackupRoundTripIT.java` (matches `**/*IT.java`) and runs all test methods including nested ones.

**No workflow changes needed.** The `build-and-test` CI job already runs `./mvnw verify` which triggers `maven-failsafe-plugin`'s `default-it` execution.

**MariaDB IT skip in CI:** `BackupImportMariaDbSmokeIT` has `@EnabledIfSystemProperty(named = "docker.available", matches = "true")`. Since `ci.yml` does not pass `-Ddocker.available=true`, this IT is currently SKIPPED in CI (runs locally only when a Docker daemon is available and the property is set). The new `MariaDbRoundTripTests` must mirror this guard to maintain consistent behavior.

---

### 10. README Structure — Insertion Point

**Verified from:** `README.md` [VERIFIED: codebase]

Current README structure:
1. `# CTC Manager` — title + description
2. `## Tech Stack`
3. `## Features` — list of 14 features
4. `## Quick Start` — dev commands
5. `## Playwright Setup (Team Cards + E2E Tests)` — chromium install
6. `## Documentation` — wiki link

**D-09 + CD-05 placement:** Insert "Backup & Restore" AFTER `## Features` and BEFORE `## Quick Start`. This is line ~29 in the current README (after the Features list ends).

**Section structure per D-09:**
```markdown
## Backup & Restore

v1.10 introduces a full database backup/restore feature accessible via `/admin/backup`.

### Export

1. Navigate to `/admin/backup` in the admin sidebar.
2. Click **Export Backup** — a ZIP file (`ctc-backup-<ISO-instant>.zip`) downloads immediately.
3. Store the ZIP in a safe location. Each export captures all 24 entity tables and the `uploads/` tree.

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

See the [Backup & Restore wiki page](../../wiki/Backup-and-Restore) for the step-by-step export workflow,
import workflow, schema-version explanation, and recovery procedures.
```

This is approximately 30 lines, within the D-09 ~30-50 line target.

---

### 11. `77-AUTO-UAT.md` Template

**Verified from:** `76-AUTO-UAT.md` [VERIFIED: codebase]

The Phase 76 AUTO-UAT.md uses this front-matter + structure:
```yaml
---
phase: 76-...
executed: 2026-05-14T20:38:00Z
server_profile: dev,demo
total: 5
passed: 5
failed: 0
skipped: 0
---
```

Then:
- Header: `# Auto-UAT Report: Phase 77`
- Lead paragraph explaining what was verified.
- `## Results` section with numbered items per CONTEXT D-13:
  1. `./mvnw verify -Pe2e` BUILD SUCCESS on H2 (with `BackupRoundTripIT$H2RoundTripTests` green).
  2. JaCoCo line coverage measured — `Measured: NN.N % — X.X% buffer over the 82% gate`.
  3. `playwright-cli open https://github.com/jegr78/ctc-manager` → README "Backup & Restore" section renders with working cross-links.
  4. `playwright-cli open https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` → page exists, 3 screenshots render, all internal links work.
  5. `BackupRoundTripIT$MariaDbRoundTripTests` BUILD SUCCESS (local run with `-Ddocker.available=true`) — link to local test output or CI logs if available.
  6. `BackupImportRollbackIT` still green in `./mvnw verify -Pe2e`.
- `## Summary` table.

---

### 12. `BackupObjectMapperConfig` — Jackson MixIn Registration

**Verified from:** `BackupObjectMapperConfig.java` [VERIFIED: codebase]

The `backupObjectMapper` bean receives `List<Module> backupMixInModules` via Spring DI. Phase 73 added 24 per-entity `@Component` MixIn `Module` beans. These are all registered in the `backupObjectMapper` at startup. When the IT calls `backupObjectMapper.writeValueAsBytes(entity)`, all 24 MixIns are active — including the `RaceMixIn`, `SeasonDriverMixIn`, and `TeamMixIn` that control what fields appear in the JSON (and therefore in the SHA-256 hash).

**Determinism note:** Jackson serializes object fields in field-declaration order for POJOs when no `@JsonPropertyOrder` is specified, and the MixIns do not reorder fields. The same entity class serialized twice through the same `ObjectMapper` instance produces identical bytes (no randomization). [ASSUMED — verified by general Jackson 2.x behavior; the backup code's determinism was implicitly validated by Phase 73's IT]

---

### 13. Environment Audit

**Verified from:** direct tool checks [VERIFIED: environment probe]

| Dependency | Required By | Available | Version | Notes |
|------------|------------|-----------|---------|-------|
| Docker daemon | `MariaDbRoundTripTests` Testcontainers | Yes | 29.4.3 | Available locally; CI requires `-Ddocker.available=true` |
| `gh` CLI | Wiki push (D-08) | Yes | 2.69.0 | Authenticated; wiki push uses direct `git clone` |
| `playwright-cli` | Screenshots (D-10) | Assumed | — | Via `./mvnw exec:java ... playwright-cli`; dev server must be running |
| `MariaDBContainer("mariadb:11")` | Testcontainers pull | Assumed | — | Pulled on first run; auto-detected by Testcontainers |
| `java.security.MessageDigest` | SHA-256 hashing | Yes | JDK 25 | JDK built-in, no dependency |
| `java.util.HexFormat` | Hash formatting | Yes | JDK 25 (Java 17+) | JDK built-in |
| `BackupImportMariaDbSmokeIT` | Blueprint reference | Yes | — | File exists at expected path |

**Missing dependencies with no fallback:** None. All tools are available.

---

## Standard Stack

### Core (existing — no additions per D-18)

| Library | Purpose | Source |
|---------|---------|--------|
| `java.security.MessageDigest` | SHA-256 computation | JDK 25 built-in |
| `java.util.HexFormat` | Hex encoding for assertion messages | JDK 25 built-in (Java 17+) |
| `org.testcontainers:mariadb` | Live MariaDB engine for `MariaDbRoundTripTests` | Already in pom.xml test scope (Phase 75) |
| `org.testcontainers:junit-jupiter` | `@Testcontainers` + `@Container` annotations | Already in pom.xml test scope (Phase 75) |
| `org.springframework.mock.web.MockMultipartFile` | Wrapping ZIP bytes for `stage()` call | Already in Spring Boot test starter |
| `spring-data:JpaRepository.findAll(Sort)` | Deterministic first-row selection | Built into Spring Data JPA |
| `AssertJ` | Fluent assertions including `containsExactly` for byte arrays | Already in test classpath |

### No New Dependencies

D-18 is locked: zero new Maven dependencies. Everything above is already on the classpath. [VERIFIED: pom.xml]

---

## Architecture Patterns

### Round-Trip Test Structure in BackupRoundTripIT

```
BackupRoundTripIT (outer class)
├── @SpringBootTest @ActiveProfiles("dev")
├── Existing private helpers: readEntry(), countEntriesMatching()
├── Existing 4 @Test methods (Phase 73 manifest contract — UNTOUCHED)
│
├── H2RoundTripTests (@Nested @SpringBootTest @ActiveProfiles("dev"))
│   ├── @BeforeEach seedFixture() + createStagingDir()
│   └── @Test givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch()
│       ├── captureRowCounts() — pre-export
│       ├── captureSampleHashes() — pre-export (Race, SeasonDriver, Team)
│       ├── exportToBytes() → backupArchiveService.writeZip(baos, Instant.now())
│       ├── stage() → backupImportService.stage(MockMultipartFile)
│       ├── execute() → backupImportService.execute(stagingId)
│       ├── assertRowCountParity()
│       └── assertSampleHashesEqual() — post-import re-hash via backupObjectMapper
│
└── MariaDbRoundTripTests (@Nested @SpringBootTest @ActiveProfiles("local") @Testcontainers
                           @EnabledIfSystemProperty("docker.available=true"))
    ├── @Container static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")...
    ├── @DynamicPropertySource overrideJdbcUrl() — mirror BackupImportMariaDbSmokeIT
    ├── @BeforeEach seedFixture() + createStagingDir()
    └── @Test givenLiveMariaDb_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch()
        └── (same scenario as H2, different engine)
```

### Private Helpers to Add

All private methods added to `BackupRoundTripIT` (inside the outer class or as package-private for @Nested inheritance):

```java
// Reuse from BackupImportMariaDbSmokeIT
private Map<String, Long> captureRowCounts() { ... }
private byte[] exportToBytes() throws IOException { ... }

// New for Phase 77 SHA-256
private byte[] hashEntity(Object entity) throws Exception {
    byte[] bytes = backupObjectMapper.writeValueAsBytes(entity);
    return MessageDigest.getInstance("SHA-256").digest(bytes);
}
```

**Note on `@Nested` field access:** `@Nested` inner classes can access private methods of the enclosing outer class only if they are declared in the outer class. For `@Nested` with their own `@SpringBootTest`, each nested class has its own `@Autowired` fields. The private helpers that use autowired beans (`backupSchema`, `jdbcTemplate`, `backupObjectMapper`) should be methods on the `@Nested` class itself, not the outer class, since the outer class's beans are from a different `ApplicationContext`.

**Correct structure:** Both `H2RoundTripTests` and `MariaDbRoundTripTests` should each independently declare their own `@Autowired` fields and duplicate the private helpers. This is the trade-off of the two-context design (DRY vs. isolation). Alternatively, if the helpers use only the outer class's context beans, move them to the outer class and accept that they only work for H2. The planner should duplicate helpers per `@Nested` class for correctness.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SHA-256 hashing | Custom digest implementation | `java.security.MessageDigest.getInstance("SHA-256")` | JDK built-in; proven; no new dep |
| Hex encoding | Custom hex formatter | `java.util.HexFormat.of().formatHex(bytes)` | JDK 17+ built-in; zero boilerplate |
| Row counts across 24 tables | Custom count loop | `captureRowCounts()` pattern from `BackupImportMariaDbSmokeIT` | Already tested, proven pattern |
| Testcontainers MariaDB wiring | Custom DataSource override | `@DynamicPropertySource` + `@Container static MariaDBContainer` | Phase 75 precedent; avoid duplication |
| ZIP byte buffering | Streaming to disk | `ByteArrayOutputStream` → `byte[]` → `MockMultipartFile` | Phase 75 precedent pattern |

---

## Common Pitfalls

### Pitfall 1: `@Nested` Bean Injection from Wrong Context
**What goes wrong:** `@Autowired` in an `@Nested @SpringBootTest` class may inject beans from the outer class's `ApplicationContext` if the nested class does not declare its own `@SpringBootTest` properly.
**Why it happens:** JUnit 5 + Spring Boot shares test contexts when `@SpringBootTest` configurations match (same properties, profiles, etc.). The `@Nested` `@ActiveProfiles("dev")` H2 class and the outer `@ActiveProfiles("dev")` class MAY share a context.
**How to avoid:** Both nested classes should explicitly redeclare `@SpringBootTest` with their own `@ActiveProfiles`. Add `@Autowired` on each nested class's fields explicitly. Test with `assertThat(environment.matchesProfiles("local")).isTrue()` in the MariaDB nested class.
**Warning signs:** MariaDB nested test connects to H2 instead of MariaDB.

### Pitfall 2: SHA-256 Hash Mismatch Due to Lazy Loading
**What goes wrong:** `backupObjectMapper.writeValueAsBytes(preRace)` triggers a `LazyInitializationException` when the `BackupRoundTripIT` outer context has no `@Transactional` wrapper.
**Why it happens:** `Race` has lazy associations (`matchday`, `match`, `track`, `car`). When serializing outside a Hibernate session, Jackson triggers lazy loading and gets `LazyInitializationException`.
**How to avoid:** The IT's export step calls `backupArchiveService.writeZip()` which runs `@Transactional(readOnly=true)` — the session is open inside that service. For direct entity serialization in the test, query inside a `@Transactional` method or use `@Transactional` on the test method. However, `BackupRoundTripIT` currently does NOT use `@Transactional` on test methods (the round-trip wipe must commit). Solution: The `backupObjectMapper` MixIns use `@JsonIdentityInfo` and `@JsonIgnore` on lazy collections — if MixIns `@JsonIgnore` all lazy associations and only serialize FK-ID scalars, lazy loading is never triggered. Verify by checking the `RaceMixIn` to confirm no lazy association is included in the serialized shape. [ASSUMED — needs MixIn inspection during implementation]
**Warning signs:** `LazyInitializationException` in test output during `writeValueAsBytes()`.

### Pitfall 3: MariaDB Container Startup Delay on CI
**What goes wrong:** `MariaDBContainer.start()` takes 10-30 seconds; if the Spring `ApplicationContext` initialization starts before the container is ready, `@DynamicPropertySource` returns a JDBC URL before the container port is bound.
**Why it happens:** `@Container static` + Testcontainers JUnit 5 extension starts the container before the test class is instantiated. Spring processes `@DynamicPropertySource` after container start because `@DynamicPropertySource` is a static method called during context bootstrap.
**How to avoid:** Standard `@Container static` pattern ensures the container is started and port is bound before `@DynamicPropertySource` runs. This is the Phase 75 proven pattern. No special handling needed.
**Warning signs:** `Connection refused` or timeout on `spring.datasource.url`.

### Pitfall 4: Entity Hash Mismatch After Import Due to Version Field
**What goes wrong:** SHA-256 hash of pre-export entity ≠ post-import entity despite same data.
**Why it happens:** `BaseEntity` has `createdAt` and `updatedAt` (from `AuditingEntityListener`). If the Phase 75 `JdbcTemplate.batchUpdate` bypass works correctly, these are preserved. But if a MixIn includes a `version` field (optimistic locking), its value may differ post-import.
**How to avoid:** Check whether `BaseEntity` or any of the three sample entities (`Race`, `SeasonDriver`, `Team`) have an `@Version` field. None of the three entities examined have `@Version`. Also verify the MixIns do not include any field that the `EntityManager` modifies post-persist (only `AuditingEntityListener` fields, which are bypassed).
**Warning signs:** First run of `assertSampleHashesEqual` fails; debug by logging `preBytes` vs `postBytes` to find the differing JSON field.

### Pitfall 5: Wiki Push Authentication
**What goes wrong:** `git push` to `https://github.com/jegr78/ctc-manager.wiki.git` fails with 403 or credential prompt.
**Why it happens:** GitHub CLI uses HTTPS with OAuth tokens for auth. If `gh auth` is configured, git credential helpers pick up the token automatically via `gh auth git-credential`. If not configured, the push fails.
**How to avoid:** Verify `gh auth status` shows authenticated as `jegr78` before the wiki push step. The plan step should include this check.
**Warning signs:** `remote: Permission to jegr78/ctc-manager.wiki.git denied`.

### Pitfall 6: Screenshot Preview Screen Requires Real Upload
**What goes wrong:** Navigating to `/admin/backup/preview` directly returns 404 or redirects, because the preview requires a staged file (stateless per Phase 74 D-19).
**Why it happens:** `GET /admin/backup` → `POST /admin/backup/import-preview` (multipart upload) → preview screen. The preview URL is not directly accessible.
**How to avoid:** Use `playwright-cli open` in interactive mode to manually upload a backup ZIP and capture the preview screen. The dev server must be running with the dev fixture seeded.
**Warning signs:** Screenshot shows redirect or empty page instead of preview.

---

## Open Questions

1. **Should the `MariaDbRoundTripTests` class also pass `-Ddocker.available=true` in CI?**
   - What we know: `BackupImportMariaDbSmokeIT` uses `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` and is currently SKIPPED in CI (ci.yml does not pass this property). CONTEXT D-05 says "Testcontainers in main ci.yml" — but does not specify adding the system property.
   - What's unclear: The ROADMAP success criterion #1 says "the `mariadb-migration-smoke.yml` CI workflow is BUILD SUCCESS on MariaDB; both runs include `BackupRoundTripIT`". If "both runs" means the main ci.yml run AND the smoke workflow, then the MariaDB IT needs the system property in ci.yml. If "both runs" means H2 run and MariaDB run (on the same phase-77 PR), then the MariaDB test must not be guarded.
   - Recommendation: D-17 says "no ci.yml changes" is implied by "no new workflow edits". Mirror the existing `@EnabledIfSystemProperty` guard. The MariaDB round-trip is validated locally by the developer before PR. If the team wants CI-level MariaDB validation, that's a Phase 79 scope item (add `-Ddocker.available=true` to ci.yml).

2. **How to capture `02-preview-screen.png` without an interactive browser session?**
   - What we know: The preview screen requires a prior `POST /admin/backup/import-preview` upload; the URL alone does not work. `playwright-cli screenshot` is non-interactive.
   - What's unclear: Does `playwright-cli` support form submission? Or is the only reliable path `playwright-cli open` + manual navigation?
   - Recommendation: Use `playwright-cli open` in interactive mode. Upload a real ZIP, navigate to the preview, and screenshot manually. This is the same approach used for Phase 76 screenshots.

3. **`Team.parentTeam` lazy-loading during SHA-256 hashing:**
   - What we know: `Team.parentTeam` is `@ManyToOne(fetch = FetchType.LAZY)`. The `TeamMixIn` may or may not include `parentTeam` in the wire shape.
   - What's unclear: If the MixIn includes `parentTeam` (as an ID reference via `@JsonIdentityInfo`), then serializing a root team (where `parentTeam == null`) will serialize `null` for that field — no lazy-loading issue. If the MixIn includes it as a nested object, lazy loading may trigger.
   - Recommendation: Inspect `TeamMixIn.java` during implementation. If `parentTeam` is included as an ID reference (expected), no issue. If it causes `LazyInitializationException`, wrap the hash computation in a `jdbcTemplate.execute(...)` or read the entity inside a proper session.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test 4.x |
| Config file | `pom.xml` (Failsafe `default-it` execution) |
| Quick run command | `./mvnw -Dit.test=BackupRoundTripIT verify` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| QUAL-01 | JaCoCo LINE coverage ≥ 82% | Maven gate | `./mvnw verify` (fails if < 82%) | Yes — pom.xml |
| QUAL-02 H2 | Export → wipe → import → row-count parity + SHA-256 on H2 | Failsafe IT | `./mvnw -Dit.test=BackupRoundTripIT\$H2RoundTripTests verify` | No — Wave 1 |
| QUAL-02 MariaDB | Export → wipe → import → row-count parity + SHA-256 on MariaDB | Failsafe IT | `./mvnw -Ddocker.available=true -Dit.test=BackupRoundTripIT\$MariaDbRoundTripTests verify` | No — Wave 1 |
| QUAL-04 | `BackupImportRollbackIT` still green | Failsafe IT (no changes) | `./mvnw -Dit.test=BackupImportRollbackIT verify` | Yes — Phase 75 |
| QUAL-05 README | README "Backup & Restore" section renders | Visual (playwright-cli) | `playwright-cli open https://github.com/jegr78/ctc-manager` | No — Wave 2 |
| QUAL-05 Wiki | Wiki page at `/wiki/Backup-and-Restore` exists with 3 screenshots | Visual (playwright-cli) | `playwright-cli open https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` | No — Wave 2 |

### Sampling Rate
- **Per task commit:** `./mvnw -Dit.test=BackupRoundTripIT verify` (fast subset)
- **Per wave merge:** `./mvnw verify` (all non-E2E tests + JaCoCo gate)
- **Phase gate:** `./mvnw verify -Pe2e` BUILD SUCCESS before closing PR

### Wave 0 Gaps
None — existing test infrastructure (`BackupImportMariaDbSmokeIT`, Testcontainers BOM in pom.xml, JaCoCo config) covers all new test infrastructure needs. Phase 77 adds to an existing `BackupRoundTripIT` class.

---

## Security Domain

Phase 77 adds no new production endpoints, no new authentication paths, and no new data flows. The round-trip IT exercises existing security-relevant paths (the Phase 75/76 import pipeline), but adds no new attack surface. The Wiki push uses authenticated `git` with `gh auth` credentials.

**ASVS categories:** Not applicable — Phase 77 is test + documentation only. No new production code.

---

## Sources

### Primary (HIGH confidence)
- `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` — Testcontainers wiring, `captureRowCounts()`, `exportToBytes()`, `awaitAuditRow()` patterns
- `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` — extension target; Phase 73 contract; Javadoc anchoring Phase 77 intent
- `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` — D-14 reference (untouched)
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java` — `writeZip(OutputStream, Instant)` API
- `src/main/java/org/ctc/backup/service/BackupImportService.java` — `stage(MultipartFile)` + `execute(UUID)` API
- `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` — `backupObjectMapper` bean configuration
- `src/main/java/org/ctc/backup/schema/BackupSchema.java` — 24-entity `getExportOrder()` API
- `src/main/java/org/ctc/backup/lock/ImportLockService.java` — `isLocked()` API
- `pom.xml` — JaCoCo gate 0.82, Testcontainers BOM, Failsafe `*IT.java` inclusion
- `.github/workflows/ci.yml` — `./mvnw verify` invocation; no `-Ddocker.available=true`
- `README.md` — current structure; insertion point for "Backup & Restore"
- `docs/operations/import-runbook.md` — cross-link target from README D-09
- `.planning/phases/76-.../76-AUTO-UAT.md` — AUTO-UAT template to mirror
- `git ls-remote https://github.com/jegr78/ctc-manager.wiki.git HEAD` — wiki repo confirmed to exist

### Secondary (MEDIUM confidence)
- `.planning/codebase/TESTING.md` §"Nested Test Classes" — `SecurityIntegrationTest` pattern for `@Nested @SpringBootTest`
- `.planning/phases/77-final-uat-.../77-CONTEXT.md` — locked decisions D-01..D-20

### Tertiary (LOW confidence)
- Jackson 2.x field serialization order determinism — [ASSUMED] based on known Jackson behavior; not re-verified in this session

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Jackson 2.x serializes POJO fields in declaration order (consistent between two calls on the same mapper instance, same entity state) | §SHA-256 Hashing | SHA-256 hashes would not match across two serializations; fix by sorting JSON keys before hashing |
| A2 | `@DynamicPropertySource` on `@Nested @SpringBootTest` inner class methods works correctly in Spring Boot 4.x | §Testcontainers Pattern | MariaDB nested class might connect to wrong DataSource; fix by moving to a separate top-level IT class |
| A3 | `TeamMixIn` renders `parentTeam` as an ID reference (not a nested object), so serializing a root team (null parent) does not trigger lazy loading | §Common Pitfalls Pitfall 2 | `LazyInitializationException` during SHA-256 hash computation; fix by reading entity inside a `@Transactional` helper |
| A4 | GitHub wiki propagates within ~30 seconds after push; `curl` verification shows 200 shortly after | §GitHub Wiki Workflow | Verification step in AUTO-UAT fails; add a 60s sleep before verification |

---

## Metadata

**Confidence breakdown:**
- Round-trip test implementation: HIGH — all APIs verified from existing code
- Testcontainers wiring: HIGH — Phase 75 blueprint fully documented and mirrored
- SHA-256 determinism: MEDIUM — Jackson behavior is well-known but not re-tested in this session
- JaCoCo measurement: HIGH — pom.xml config fully read and verified
- GitHub Wiki workflow: HIGH — wiki repo confirmed to exist via `git ls-remote`; `gh` CLI authenticated
- README insertion point: HIGH — README structure fully read and verified

**Research date:** 2026-05-14
**Valid until:** 2026-06-14 (stable libraries; Testcontainers and JaCoCo versions are pinned in pom.xml)
