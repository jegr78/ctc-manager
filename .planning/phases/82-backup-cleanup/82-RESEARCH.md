# Phase 82: Backup Cleanup - Research

**Researched:** 2026-05-16
**Domain:** Java / Spring Boot backup service — code cleanup, test harness extension
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01 — Commit strategy:** 5 fix commits + 3 test commits + 1 audit doc. No re-commits for the 7 pre-resolved WR items (CR-01, CR-02, WR-02..WR-08) — they live on master via PR #121 and are catalogued in `82-BACKLOG-AUDIT.md`.

**D-02 — Commit order:**
1. `fix(82): WR-01 extract BackupExecutedByResolver bean`
2. `fix(82): IN-04 profile-isolate import-backups-dir`
3. `fix(82): IN-03 warn on missing ZIP data entry`
4. `chore(82): IN-01 remove no-op @RequiredArgsConstructor (18 restorers)`
5. `style(82): IN-02 align restorer annotation order (@Slf4j @Component first)`
6. `test(82): BACK-01 schema-version + export-order guard test`
7. `test(82): BACK-03 ZipEntry-open count IT for restore path`
8. `test(82): BACK-05 extend BackupRoundTripIT to all 24 entities`
9. `docs(82): 82-BACKLOG-AUDIT.md (7 pre-resolved items + SHA pointers)`
10. `docs(82): STATE + manifest + verification`

**D-03 — Branch:** Feature branch off `origin/master`. PR target: `gsd/v1.11-tooling-and-cleanup`.

**D-04 — BackupExecutedByResolver:** New `@Component` in `org.ctc.backup.audit`. Single public method `String resolve(String callerOverride)`. `Environment` injected as `final` field via `@RequiredArgsConstructor`.

**D-05 — Both callers updated:** `BackupImportService` and `DataImportAuditService` lose their `resolveExecutedBy()` private methods and inject `BackupExecutedByResolver` via `@RequiredArgsConstructor`.

**D-06 — WR-01 unit test:** Plain Mockito unit test, 4 `@Test` methods, no Spring context. In `src/test/java/org/ctc/backup/audit/BackupExecutedByResolverTest.java`. Untagged (Surefire unit category).

**D-07 — IN-01/IN-02 separate commits:** IN-01 first (18 files, strip `@RequiredArgsConstructor`), then IN-02 (6 files, swap `@Component @Slf4j` → `@Slf4j @Component`).

**D-08 — CLAUDE.md convention addition:** One-line addition under `### Lombok Usage`: annotation order on Spring components is `@Slf4j @Component @RequiredArgsConstructor`. Discretion granted on whether to include it.

**D-10 — IN-03 escalation:** Change `log.debug` to `log.warn` at `BackupImportService.restoreOneTable` line 669. Keep return-0 semantics (soft tolerance).

**D-11 — IN-03 test:** Single `@Test givenZipWithMissingEntry_whenRestore_thenWarnLogEmittedAndZeroRows` using `OutputCaptureExtension`. Planner chooses whether it goes in BACK-03 IT or a sibling IT.

**D-12 — IN-04 change:** `application.yml` line 6: `import-backups-dir: data/.import-backups` → `import-backups-dir: data/${spring.profiles.active:dev}/import-backups`.

**D-13 — No backward-compat shim for IN-04.** Document path change in README. No migration script.

**D-16 — BACK-01 guard test:** `BackupSchemaGuardTest.java` at `src/test/java/org/ctc/backup/schema/`. Two `@Test` methods, no Spring context if possible — but BackupSchema requires `EntityManagerFactory` injection via `@PostConstruct`, so a `@DataJpaTest` slice or `@SpringBootTest` is required. Discretion: keep as lightweight as possible.

**D-17 — BACK-01 assertion message:** "BackupSchema.SCHEMA_VERSION changed from 1 — this is a wire contract bump; see Phase 75 SCHEMA_VERSION gate or write a new migration phase".

**D-18 — BACK-03 counter:** Package-private `AtomicInteger zipOpenCounter = new AtomicInteger(0);` on `BackupImportService`. Reset at top of `execute()`. Increment immediately before `try (ZipFile zf = new ZipFile(staged.toFile()))` at line 635.

**D-19 — BACK-03 IT:** `BackupRestoreZipOpenCountIT` in `src/test/java/org/ctc/backup/service/`. `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")`.

**D-22 — BACK-05:** One new `@Test givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch` per `@Nested` class in `BackupRoundTripIT`.

**D-25 — BACK-05 MariaDB gate:** `MariaDbRoundTripTests` uses `@EnabledIfSystemProperty(named = "docker.available", matches = "true")`. New 24-entity test inherits this gate automatically.

**D-26/D-27/D-28 — Test cadence:** Targeted tests per commit. One final `./mvnw verify -Pe2e` before raising the PR.

### Claude's Discretion

- Exact wording of `82-BACKLOG-AUDIT.md` markdown table.
- Whether README IN-04 path note is one sentence or a small paragraph.
- Whether IN-02 annotation-order convention goes in CLAUDE.md permanently (D-08).
- Whether BACK-01 guard test uses `@SpringBootTest` slice, `@DataJpaTest`, or `@SpringBootTest` full context — depends on BackupSchema's dependencies.
- Whether IN-03 WARN-log IT is a separate class or added to BACK-03 IT.
- Which log-captor to use for IN-03: `OutputCaptureExtension` (Spring Boot built-in) vs LogCaptor library. `OutputCaptureExtension` recommended.
- Whether `BackupExecutedByResolverTest` uses `Mockito.mockStatic(SecurityContextHolder.class)` or a custom indirection.
- Whether BACK-05 needs a richer fixture if 0-row playoff_* tables make the parity test trivially `0 == 0`.
- SpotBugs per-commit check cadence (D-27): run after WR-01, IN-03, IN-04; skip for annotation-only and yaml-only commits.

### Deferred Ideas (OUT OF SCOPE)

- CLAUDE.md annotation-order convention as a CI gate.
- BackupSchema auto-discovery via Hibernate Metamodel (BACK-01 guard pins 24; next entity triggers loud failure).
- `82-BACKLOG-AUDIT.md` as a project-wide artifact template.
- Existing `data/.import-backups/<old-path>/` migration script.
- Bumping `BackupSchema.SCHEMA_VERSION` to 2.
- BACK-03 IT on MariaDB profile.
- Promoting `BackupExecutedByResolver` to a general `ExecutorContextResolver`.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BACK-01 | `BackupSchema.SCHEMA_VERSION` remains at `1` throughout all 12 REVIEW.md fixes — verified by a guard test asserting the constant value and the 24-entity `EXPORT_ORDER` size | SCHEMA_VERSION = 1 confirmed at line 32 of BackupSchema.java; existing BackupSchemaTopologyIT asserts `hasSize(24)` proving export-order size; BACK-01 guard test must use Spring context (BackupSchema requires EntityManagerFactory) |
| BACK-02 | All 12 Phase-75 REVIEW.md Info/Warning items resolved with one atomic commit per item, each referencing the REVIEW.md ID | 7 pre-resolved items catalogued via `82-BACKLOG-AUDIT.md`; 5 new fix commits; full commit list confirmed in 82-CONTEXT.md |
| BACK-03 | `restoreOneTable` reads each entity-data ZIP entry once — verified by IT measuring the ZipEntry-open count | `try (ZipFile zf = new ZipFile(staged.toFile()))` at line 635 is the single ZIP-open; package-private `AtomicInteger zipOpenCounter` counter strategy confirmed viable |
| BACK-04 | `BackupRoundTripIT` and `BackupImportRollbackIT` both still pass on H2 and MariaDB after every commit | Both ITs confirmed present and structured; `@DynamicPropertySource` override on import-backups-dir in BackupRoundTripIT protects against IN-04 path change |
| BACK-05 | `BackupRoundTripIT` extended to assert per-entity row counts for ALL 24 entities | `captureRowCounts()` helper exists in both `@Nested` classes and already iterates `backupSchema.getExportOrder()`; the full round-trip helper `exportToBytes()` + `stage()` + `execute()` sequence exists; new `@Test` reuses these |
</phase_requirements>

---

## Summary

Phase 82 is a code-quality cleanup phase: 5 fix commits + 3 test commits + 1 audit doc. There is no schema change, no new dependency, and no Flyway migration. All work is in `src/main/java/org/ctc/backup/` and its matching test tree. The 7 previously-resolved review items (CR-01, CR-02, WR-02..WR-08) already live on master and are catalogued in `82-BACKLOG-AUDIT.md`.

The five unresolved items (WR-01, IN-01, IN-02, IN-03, IN-04) fall into three categories: (1) extract a duplicated private method into a shared `@Component` bean (WR-01), (2) pure annotation cleanup on 24 restorer classes (IN-01 + IN-02), and (3) log-level escalation and YAML property path isolation (IN-03 + IN-04). The three new test obligations (BACK-01, BACK-03, BACK-05) add guard assertions against future regressions.

The investigation confirms that all five fix sites have precise line-number anchors, all 24 restorer annotation states are verified by grep, and the existing `BackupRoundTripIT` already contains the `captureRowCounts()` helper and full round-trip sequence that BACK-05 reuses. The `BackupSchema` bean requires Spring context (`EntityManagerFactory` injection + `@PostConstruct`), so BACK-01 guard test cannot be a plain unit test — it needs at minimum a `@SpringBootTest(classes = ...)` slice or `@DataJpaTest`.

**Primary recommendation:** Implement commits in the locked D-02 order. Use `OutputCaptureExtension` for IN-03 WARN-log assertion (established pattern in `BackupImportRollbackIT`, `BackupImportPostCommitIT`, `BackupStagingCleanupIT`). For BACK-01, model the guard test after `BackupSchemaTopologyIT` (same `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")` shape), since `getExportOrder().size()` already asserts `24` there — BACK-01 adds the `SCHEMA_VERSION == 1` assertion and explicit failure message as a dedicated guard class.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| resolveExecutedBy deduplication (WR-01) | API / Backend (service layer) | — | `DataImportAuditService` + `BackupImportService` are both Spring services; the new bean lives in `org.ctc.backup.audit` alongside the existing audit service |
| Restorer annotation cleanup (IN-01, IN-02) | API / Backend (service layer) | — | All 24 `EntityRestorer` beans are Spring `@Component` classes in `org.ctc.backup.restore.entity`; annotation-only change, no tier shift |
| Missing-entry WARN escalation (IN-03) | API / Backend (service layer) | — | Log call is inside `BackupImportService.restoreOneTable`, the core restore orchestrator |
| Import directory profile-isolation (IN-04) | API / Backend (configuration) | — | `application.yml` property injection; no controller or DB change |
| Schema-version guard test (BACK-01) | API / Backend (test tier) | — | Tests `BackupSchema` bean which requires JPA Metamodel; must boot JPA slice or full context |
| ZipEntry-open count IT (BACK-03) | API / Backend (test tier) | — | Tests `BackupImportService.restoreAll` internal behavior via package-private counter |
| 24-entity parity test (BACK-05) | API / Backend (test tier) | — | Extends existing `BackupRoundTripIT` Spring integration test |

---

## Fix Site Investigation (Investigation Tasks 1–10)

### Task 1 — Exact Line Numbers for 5 Fix Sites

**WR-01: `BackupImportService.resolveExecutedBy()`**
- File: `src/main/java/org/ctc/backup/service/BackupImportService.java`
- Private method: lines 731–740 (`private String resolveExecutedBy()`)
- Call site: line 518 (`String executedBy = resolveExecutedBy();`) inside `execute()`
- Call site in `tryRecordFailure()`: passes `null` as `executedByCaller` to `dataImportAuditService.recordResult(...)` at line 756
- IMPORTANT: `BackupImportService.resolveExecutedBy()` takes NO parameters (3-branch version — no callerOverride branch). The 4-branch version lives only in `DataImportAuditService`.
- Action: Remove both `resolveExecutedBy()` private methods and add `BackupExecutedByResolver executedByResolver` as a `final` constructor field. Both callers use `executedByResolver.resolve(null)` (BackupImportService success path and tryRecordFailure) or `executedByResolver.resolve(executedByCaller)` (DataImportAuditService).

**WR-01: `DataImportAuditService.resolveExecutedBy(String)`**
- File: `src/main/java/org/ctc/backup/audit/DataImportAuditService.java`
- Private method: lines 138–150 (`private String resolveExecutedBy(String executedByCaller)`)
- Call site: line 102 (`String resolvedExecutedBy = resolveExecutedBy(executedByCaller);`)
- IMPORTANT: `DataImportAuditService` uses an explicit constructor (not `@RequiredArgsConstructor`) due to `@Qualifier("backupObjectMapper")`. The new `BackupExecutedByResolver` field must be added to the explicit constructor at lines 66–73. `Environment` field (line 58) becomes redundant after WR-01 and must be removed.

**IN-03: `BackupImportService.restoreOneTable` silent skip**
- File: `src/main/java/org/ctc/backup/service/BackupImportService.java`
- Lines 666–671: `if (entry == null) { ... log.debug("No data entry for table={} ...") ... return totalRows; }`
- Action: Change `log.debug` → `log.warn` with corruption-signal message. Keep `return totalRows` (soft tolerance preserved).

**IN-04: `application.yml` line 6**
- File: `src/main/resources/application.yml`
- Line 6: `import-backups-dir: data/.import-backups`
- Action: Change to `import-backups-dir: data/${spring.profiles.active:dev}/import-backups`
- Pattern already established: line 5 uses `staging-dir: data/${spring.profiles.active:dev}/backup-staging`

**BACK-03: ZipFile open location**
- File: `src/main/java/org/ctc/backup/service/BackupImportService.java`
- Line 635: `try (ZipFile zf = new ZipFile(staged.toFile())) {` inside `restoreAll()`
- Counter increment goes immediately BEFORE this line (inside `restoreAll`, after the field is declared at class level)
- Reset location: top of `execute(UUID stagingId)` method

### Task 2 — IN-01: 18 No-Op @RequiredArgsConstructor Restorers (VERIFIED)

Confirmed by grep: all 18 have `@RequiredArgsConstructor` AND zero `private final` fields.

| Restorer | Has @RequiredArgsConstructor | Has private final fields |
|----------|------------------------------|--------------------------|
| DriverRestorer | YES | NO (no-op) |
| MatchRestorer | YES | NO (no-op) |
| MatchScoringRestorer | YES | NO (no-op) |
| MatchdayRestorer | YES | NO (no-op) |
| PhaseTeamRestorer | YES | NO (no-op) |
| PsnAliasRestorer | YES | NO (no-op) |
| RaceAttachmentRestorer | YES | NO (no-op) |
| RaceLineupRestorer | YES | NO (no-op) |
| RaceRestorer | YES | NO (no-op) |
| RaceResultRestorer | YES | NO (no-op) |
| RaceScoringRestorer | YES | NO (no-op) |
| RaceSettingsRestorer | YES | NO (no-op) |
| SeasonDriverRestorer | YES | NO (no-op) |
| SeasonPhaseGroupRestorer | YES | NO (no-op) |
| SeasonPhaseRestorer | YES | NO (no-op) |
| SeasonRestorer | YES | NO (no-op) |
| SeasonTeamRestorer | YES | NO (no-op) |
| TeamRestorer | YES | NO (no-op) |

**Total: 18 restorers confirmed.** These match the 82-CONTEXT.md list exactly.

### Task 2 — IN-02: 6 @Component-First Restorers (VERIFIED)

Confirmed by grep on first annotation line per file:

| Restorer | Current Order | Target Order |
|----------|--------------|--------------|
| CarRestorer | `@Component @Slf4j` | `@Slf4j @Component` |
| PlayoffMatchupRestorer | `@Component @Slf4j` | `@Slf4j @Component` |
| PlayoffRestorer | `@Component @Slf4j` | `@Slf4j @Component` |
| PlayoffRoundRestorer | `@Component @Slf4j` | `@Slf4j @Component` |
| PlayoffSeedRestorer | `@Component @Slf4j` | `@Slf4j @Component` |
| TrackRestorer | `@Component @Slf4j` | `@Slf4j @Component` |

**Total: 6 restorers confirmed.** These match the 82-CONTEXT.md list exactly.

**Overlap check (D-07):** After IN-01 strips `@RequiredArgsConstructor` from the 18, the resulting annotation state for any file that was in both groups would be `@Slf4j @Component`. IN-01 must run first. Checking for overlapping files: none of the 6 IN-02 files appear in the 18 IN-01 files (CarRestorer, PlayoffMatchupRestorer, PlayoffRestorer, PlayoffRoundRestorer, PlayoffSeedRestorer, TrackRestorer all have 0 `@RequiredArgsConstructor` already). No ordering conflict.

### Task 3 — BackupRoundTripIT Helper Method Map (VERIFIED)

**Class-level helpers (used by both `@Nested` classes):**
- `static void cleanDirContents(Path dir)` — best-effort recursive wipe of import-backups temp dir
- `static Path IMPORT_BACKUPS_ROOT` — shared temp dir allocated at class-init

**H2RoundTripTests helpers:**
- `private byte[] exportToBytes() throws IOException` — writes via `backupArchiveService.writeZip(baos, Instant.now())`; returns raw ZIP bytes
- `private Map<String, Long> captureRowCounts()` — iterates `backupSchema.getExportOrder()`, uses `jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)`, returns `LinkedHashMap`
- `private byte[] hashEntity(Object entity)` — SHA-256 via `backupObjectMapper.writeValueAsBytes(entity)`
- `private DataImportAudit awaitAuditRow(UUID auditUuid, Duration timeout)` — polling helper (marked `@SuppressWarnings("unused")`)

**Full round-trip sequence (inside existing `@Test`):**
```java
byte[] zipBytes = exportToBytes();
MockMultipartFile file = new MockMultipartFile("file", "h2-round-trip-export.zip", "application/zip", zipBytes);
BackupImportPreview preview = backupImportService.stage(file);
BackupImportResult result = backupImportService.execute(preview.stagingId());
```

**BACK-05 reuse plan:** The new `@Test givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch` captures `captureRowCounts()` before the round-trip, then runs the same `exportToBytes()` + `stage()` + `execute()` sequence, then asserts `captureRowCounts()` again equals the pre-round-trip map. The `captureRowCounts()` helper already iterates ALL 24 entities — no changes needed to the helper itself.

**MariaDbRoundTripTests:** Identical helpers (code is duplicated per `@Nested` class per the comment "Helpers — duplicated per @Nested class (each class has its own ApplicationContext)"). Same pattern applies.

### Task 4 — BackupSchema Spring Context Requirement (VERIFIED)

`BackupSchema` REQUIRES Spring/JPA context. Evidence:
- `@Component @RequiredArgsConstructor @Slf4j` at class level
- Injects `EntityManagerFactory entityManagerFactory` and `EntityTopoSorter entityTopoSorter` — both Spring-managed
- `EntityTopoSorter` is also `@Component` (verified at line 29-30)
- `@PostConstruct void initializeExportOrder()` calls `entityManagerFactory.getMetamodel().getEntities()` — requires live JPA context

**BACK-01 guard test shape:** CANNOT be a plain unit test. Must use either:
- `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")` — matches existing `BackupSchemaTopologyIT` pattern (simplest, consistent)
- `@DataJpaTest` slice — lighter but requires additional Spring Security / component scan config

**Recommendation:** Model after `BackupSchemaTopologyIT` which already uses `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")` and already asserts `exportOrder.hasSize(24)`. BACK-01 is a new class with two dedicated `@Test` methods adding the `SCHEMA_VERSION == 1` assertion with the prescribed failure message. Note that `BackupSchemaTopologyIT` already asserts size 24, so BACK-01 is an additional guard with a human-readable failure message as per D-17.

**Important implication:** Since BACK-01 uses `@Tag("integration")`, it is NOT a plain unit test. The planner's task cadence command should be:
```bash
./mvnw verify -Dit.test=BackupSchemaGuardTest -DfailIfNoTests=false
```

### Task 5 — Log Captor Convention (VERIFIED)

Existing convention in this codebase is **`OutputCaptureExtension`** from `org.springframework.boot.test.system`. Used in:
- `BackupImportRollbackIT` (lines 34–35, 93): `@ExtendWith(OutputCaptureExtension.class)` + `CapturedOutput output` as test method parameter
- `BackupImportPostCommitIT` (lines 23, 61): same pattern
- `BackupStagingCleanupIT` (lines 18, 51): same pattern

**Usage pattern:**
```java
@ExtendWith(OutputCaptureExtension.class)
// ... Spring/IT annotations
class SomeIT {
    @Test
    void givenCondition_whenAction_thenWarnLogged(CapturedOutput output) {
        // when
        service.doSomething();
        // then
        String captured = output.getOut() + output.getErr();
        assertThat(captured).contains("expected WARN substring");
    }
}
```

**No LogCaptor library found** — there is no `logcaptor` import anywhere in the test tree. `OutputCaptureExtension` is the clear project standard.

**CRITICAL NOTE for IN-03 test:** The WARN log will only appear in captured output if the logback-test.xml root level is WARN or above. The `src/test/resources/logback-test.xml` is configured at `level="WARN"` — WARN logs ARE captured by `OutputCaptureExtension`. Confirm by checking that `log.warn(...)` at the right logger level is not filtered.

### Task 6 — @VisibleForTesting Usage (VERIFIED)

`grep -rn "@VisibleForTesting"` on `src/main/java` returns **no results**. The annotation is not used anywhere in the production codebase.

**BACK-03 counter decision:** The `AtomicInteger zipOpenCounter` field on `BackupImportService` should be **plain package-private** (no annotation) with a Javadoc comment explaining it is test-only. Example:
```java
/**
 * Package-private counter for test verification of the single-ZIP-open contract (BACK-03).
 * Reset to 0 at the top of {@link #execute(UUID)}; incremented once per {@link #restoreAll}.
 */
AtomicInteger zipOpenCounter = new AtomicInteger(0);
```

### Task 7 — SpotBugs EI_EXPOSE_REP Risk for WR-01 and BACK-03 (VERIFIED)

**WR-01 — BackupExecutedByResolver bean:** The new bean has one `final Environment environment` field (Spring-managed bean). Looking at `config/spotbugs-exclude.xml`:
- `EI_EXPOSE_REP*` on `org.ctc.backup.service.*` top-level classes is suppressed via a pattern entry
- `EI_EXPOSE_REP*` on `org.ctc.backup.(dto|schema|audit|event).*` is also suppressed
- The new `BackupExecutedByResolver` in `org.ctc.backup.audit` is covered by the existing `backup.audit` package pattern entry

**Conclusion:** No new SpotBugs suppression entry needed for WR-01's new bean. The `config/spotbugs-exclude.xml` already covers `org.ctc.backup.audit.*` EI_EXPOSE_REP patterns.

**BACK-03 — `AtomicInteger zipOpenCounter` field:** The field is package-private (no getter). The existing suppression entry for `org.ctc.backup.service.*` top-level classes covers `EI_EXPOSE_REP*`. Since the field has no `public` or `protected` accessor, SpotBugs is unlikely to flag it at all. If it does flag the bare field, the existing class-level exclusion pattern covers it.

**Conclusion:** No new `config/spotbugs-exclude.xml` entries anticipated. After WR-01 commit, run `./mvnw spotbugs:check -DskipTests` to confirm.

### Task 8 — Profile-Specific YAML Overrides for import-backups-dir (VERIFIED)

Grep result on all 4 profile YAML files (`application-dev.yml`, `application-local.yml`, `application-docker.yml`, `application-prod.yml`):

**All 4 files have no backup-related configuration at all.** None override `import-backups-dir` or `staging-dir`. The root `application.yml` is the sole source for both properties.

**Conclusion:** IN-04 changes ONLY `src/main/resources/application.yml` line 6. No profile-specific files need updating.

**Additional IN-04 consideration:** The `BackupRoundTripIT` and other ITs that call `execute()` use `@DynamicPropertySource` to override `app.backup.import-backups-dir` to a temp directory. These overrides are hardcoded paths (not profile-interpolated), so the IN-04 change in `application.yml` has no effect on existing IT behavior. The ITs override the property entirely via `registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString)`.

### Task 9 — MariaDB Gate on BackupRoundTripIT (VERIFIED)

**Gate annotation confirmed:**
```java
@EnabledIfSystemProperty(named = "docker.available", matches = "true",
    disabledReason = "Set -Ddocker.available=true (with Docker daemon) to run the MariaDB Testcontainers round-trip IT")
```

**Gate property:** `docker.available` (NOT `mariadb.smoke` as mentioned in D-25 of 82-CONTEXT.md — the context doc contains an error on this point). The correct value is `docker.available=true`.

**BACK-05 inheritance:** Per TESTING.md §"Test Categorization (`@Tag`)": `@Nested` inner classes inherit their parent's tags AND their parent's class-level annotations. The `@EnabledIfSystemProperty` is on `MariaDbRoundTripTests` itself (not the outer class). The new `@Test` added inside `MariaDbRoundTripTests` inherits this gate automatically — it will only run when `docker.available=true` is set (i.e., in the Docker-available CI workflow).

**The planner's per-commit verification note (D-26):**
```bash
./mvnw verify -Dit.test=BackupRoundTripIT -DfailIfNoTests=false
```
On a dev machine without Docker, this runs only the `H2RoundTripTests` nested class; the `MariaDbRoundTripTests` nested class is skipped.

### Task 10 — BackupSchema.EXPORT_ORDER Size and SCHEMA_VERSION Value (VERIFIED)

**SCHEMA_VERSION:** `public static final int SCHEMA_VERSION = 1;` at line 32 of `BackupSchema.java`. Value is `1`.

**Export order size:** The `BackupSchemaTopologyIT.givenSpringContext_whenGetExportOrder_thenReturns24Entities()` test at line 50 asserts `assertThat(exportOrder).hasSize(24)`. This is a green integration test on the current codebase. The domain model package contains 25 `@Entity` classes, but `DataImportAudit` (in `org.ctc.backup.audit`, not `org.ctc.domain.model`) is excluded by the package filter. Of the 25 entities in `domain.model`, `BaseEntity` is `@MappedSuperclass` (not `@Entity`) — leaving exactly 24 concrete JPA entities.

**BACK-01 assertion values to pin:**
- `BackupSchema.SCHEMA_VERSION == 1`
- `backupSchema.getExportOrder().size() == 24`

---

## Standard Stack

Phase 82 introduces no new dependencies. All libraries are already on the classpath.

### Core (Already Present)

| Library | Purpose in Phase 82 | Source |
|---------|--------------------|-----------------------------|
| Spring Boot 4.0.6 | Application framework, `@Component`, `Environment`, `@Transactional` | pom.xml (spring-boot-starter-parent) |
| Spring Security | `SecurityContextHolder` access in `BackupExecutedByResolver` | spring-boot-starter-security |
| Lombok | `@RequiredArgsConstructor`, `@Slf4j` on new bean | lombok (managed by Spring Boot) |
| JUnit 5 Jupiter | `@Test`, `@Tag`, `@Nested`, `@ExtendWith` | spring-boot-starter-test |
| Mockito | Static mocking for `SecurityContextHolder` in WR-01 unit test | mockito-core (Spring Boot test) |
| AssertJ | Fluent assertions in all tests | assertj-core (Spring Boot test) |
| Spring Boot Test | `OutputCaptureExtension`, `CapturedOutput`, `@SpringBootTest` | spring-boot-starter-test |
| Testcontainers 2.0.5 | MariaDB for `MariaDbRoundTripTests` (BACK-05) | testcontainers-bom |
| JaCoCo | Coverage gate at 82% minimum | pom.xml (jacoco-maven-plugin) |
| SpotBugs + findsecbugs | Static analysis gate on every `verify` | pom.xml (Phase 81) |

### No New Dependencies

`pom.xml` is NOT modified in Phase 82. The `@VisibleForTesting` annotation is not used (no Guava or similar needed). The `AtomicInteger` is from `java.util.concurrent.atomic` (JDK).

---

## Architecture Patterns

### System Architecture Diagram

```
BackupImportService.execute(stagingId)
    │
    ├── zipOpenCounter.incrementAndGet()    ← BACK-03 counter (new)
    ├── try (ZipFile zf = new ZipFile(...)) ← line 635, single open
    │       └── restoreAll(staged, restoredCounts)
    │               └── for each EntityRef in getExportOrder():
    │                       restoreOneTable(zf, ref, restorer)
    │                           ├── zf.getEntry(entryPath)
    │                           ├── [entry == null] → log.warn(...)  ← IN-03 escalation
    │                           └── stream JSON → batchUpdate
    │
    ├── executedByResolver.resolve(null)    ← WR-01: replaces resolveExecutedBy()
    └── publish BackupImportSucceededEvent

DataImportAuditService.recordResult(...)
    └── executedByResolver.resolve(executedByCaller)  ← WR-01: replaces resolveExecutedBy(...)

BackupExecutedByResolver.resolve(callerOverride)   ← WR-01 new bean
    ├── dev|local profile → "dev"
    ├── callerOverride non-blank → callerOverride
    ├── SecurityContext auth → auth.getName()
    └── fallback → "unknown"

application.yml
    └── import-backups-dir: data/${spring.profiles.active:dev}/import-backups  ← IN-04
```

### Recommended Project Structure (New Files Only)

```
src/main/java/org/ctc/backup/audit/
├── BackupExecutedByResolver.java    ← WR-01 new bean

src/test/java/org/ctc/backup/audit/
├── BackupExecutedByResolverTest.java  ← WR-01 unit test (4 branches)

src/test/java/org/ctc/backup/schema/
├── BackupSchemaGuardTest.java        ← BACK-01 guard (2 @Test methods)

src/test/java/org/ctc/backup/service/
├── BackupRestoreZipOpenCountIT.java  ← BACK-03 IT

.planning/phases/82-backup-cleanup/
├── 82-BACKLOG-AUDIT.md               ← audit doc for 7 pre-resolved items
```

### Pattern 1: BackupExecutedByResolver Bean

```java
// src/main/java/org/ctc/backup/audit/BackupExecutedByResolver.java
package org.ctc.backup.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackupExecutedByResolver {

    private final Environment environment;

    /**
     * Resolves the {@code executedBy} string for audit rows.
     *
     * <ol>
     *   <li>dev/local profile → literal {@code "dev"}</li>
     *   <li>callerOverride non-blank → callerOverride</li>
     *   <li>SecurityContext authentication name (non-blank) → auth.getName()</li>
     *   <li>fallback → {@code "unknown"}</li>
     * </ol>
     */
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
}
```

### Pattern 2: BackupExecutedByResolverTest (4-Branch Mockito Unit Test)

```java
// src/test/java/org/ctc/backup/audit/BackupExecutedByResolverTest.java
@ExtendWith(MockitoExtension.class)
class BackupExecutedByResolverTest {

    @Mock
    Environment environment;

    BackupExecutedByResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BackupExecutedByResolver(environment);
    }

    @Test
    void givenDevProfile_whenResolve_thenReturnsDev() {
        // given
        when(environment.matchesProfiles("dev | local")).thenReturn(true);
        // when
        String result = resolver.resolve(null);
        // then
        assertThat(result).isEqualTo("dev");
    }

    @Test
    void givenNonDevProfileAndCallerOverride_whenResolve_thenReturnsOverride() {
        // given
        when(environment.matchesProfiles("dev | local")).thenReturn(false);
        // when
        String result = resolver.resolve("admin");
        // then
        assertThat(result).isEqualTo("admin");
    }

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

    @Test
    void givenNonDevProfileAndNoOverrideAndNoAuth_whenResolve_thenReturnsUnknown() {
        // given
        when(environment.matchesProfiles("dev | local")).thenReturn(false);
        SecurityContextHolder.clearContext();
        // when
        String result = resolver.resolve(null);
        // then
        assertThat(result).isEqualTo("unknown");
    }
}
```

**Note on SecurityContextHolder static mocking:** The pattern above uses `SecurityContextHolder.getContext().setAuthentication(auth)` with a `finally` clear, avoiding `Mockito.mockStatic`. This is simpler and does not require `mockito-inline` — `SecurityContextHolder` is not a final class and its `getContext()` is thread-local, so setup/teardown works correctly in unit tests.

### Pattern 3: IN-03 WARN Log Test with OutputCaptureExtension

```java
// Extends existing BACK-03 IT or new BackupImportMissingEntryWarnIT
@SpringBootTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
@Tag("integration")
class BackupRestoreZipOpenCountIT {

    @Test
    void givenZipWithMissingEntry_whenRestore_thenWarnLogEmittedAndZeroRows(
            CapturedOutput output) throws Exception {
        // given — build a ZIP missing data/cars.json
        // when — execute import
        // then
        assertThat(output.getAll()).contains("Backup ZIP has no data entry for table=");
    }
}
```

### Pattern 4: BACK-01 Guard Test

```java
// src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupSchemaGuardTest {

    @Autowired
    BackupSchema backupSchema;

    @Test
    void givenBackupSchema_whenInspected_thenSchemaVersionIsOne() {
        assertThat(BackupSchema.SCHEMA_VERSION)
            .as("BackupSchema.SCHEMA_VERSION changed from 1 — this is a wire contract bump; " +
                "see Phase 75 SCHEMA_VERSION gate or write a new migration phase")
            .isEqualTo(1);
    }

    @Test
    void givenBackupSchema_whenInspected_thenExportOrderHasTwentyFourEntities() {
        assertThat(backupSchema.getExportOrder().size())
            .as("BackupSchema.EXPORT_ORDER size changed from 24 — if a new entity was added, " +
                "bump SCHEMA_VERSION and update BackupRoundTripIT expected row-count assertions")
            .isEqualTo(24);
    }
}
```

**Note:** `BackupSchemaGuardTest` duplicates assertions already in `BackupSchemaTopologyIT`. This is intentional — the guard class has a stronger, human-readable assertion message per D-17 and serves as a dedicated regression net for the wire contract constant.

### Pattern 5: BACK-03 ZipEntry Counter in BackupImportService

```java
// In BackupImportService class body (package-private, after static constants):
/**
 * Package-private counter for test verification of the single-ZIP-open contract (BACK-03).
 * Reset to 0 at the top of {@link #execute(UUID)}; incremented once per {@link #restoreAll}.
 */
AtomicInteger zipOpenCounter = new AtomicInteger(0);

// In execute(UUID stagingId) — at the very top, before any try/catch:
zipOpenCounter.set(0);

// In restoreAll(Path staged, Map<String, Long> restoredCounts) — immediately before line 635:
zipOpenCounter.incrementAndGet();
try (ZipFile zf = new ZipFile(staged.toFile())) {
    // ... existing code
}
```

### Pattern 6: BACK-05 New @Test in H2RoundTripTests

```java
// Inside H2RoundTripTests @Nested class:
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

**Note on trivial 0==0 assertions (D-25 discretion):** The dev fixture (TestDataService.seed()) loads Race + SeasonDriver + Team data but may leave `playoff_*` tables empty. A `0 == 0` assertion is technically correct (if 0 rows were exported and 0 rows were restored, parity holds). If the planner judges this insufficient, add an explicit assertion that at least N entities have non-zero counts — e.g., `assertThat(preCounts.values().stream().filter(c -> c > 0).count()).isGreaterThan(10)`.

### Anti-Patterns to Avoid

- **Using `@SuppressWarnings("all")` on the new `BackupExecutedByResolver` bean:** Always use targeted `@SuppressFBWarnings({"SPECIFIC_CODE"}, justification="...")` if needed. The existing package-level exclusion in `spotbugs-exclude.xml` covers `org.ctc.backup.audit.*` and makes any suppression unnecessary.
- **Adding `@VisibleForTesting` as a compile dependency:** The codebase does not use this annotation. Use package-private with Javadoc comment instead.
- **Mocking `ZipFile` with `Mockito.mockStatic(ZipFile.class)`:** Brittle on JDK 25 with Mockito's bytecode-based mock mechanism. The in-source `AtomicInteger` counter is the approved approach (D-20).
- **Re-running `./mvnw verify` between every commit:** Use targeted `-Dtest=` or `-Dit.test=` invocations per D-26. One final `./mvnw verify -Pe2e` at phase end (D-28).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Log capture in tests | Custom log appender or LogCaptor dependency | `OutputCaptureExtension` | Built into `spring-boot-test`; already used in 3 existing backup ITs |
| SecurityContextHolder mock | `mockito-inline` `mockStatic` on JDK class | `SecurityContextHolder.setAuthentication()` + `finally clearContext()` | Thread-local setup is simpler; works without extra Mockito modules; no JVM agent needed |
| ZIP-open count verification | Mockito static mock of `ZipFile` | Package-private `AtomicInteger zipOpenCounter` | Static mocking of JDK classes is fragile on JDK 25 (module system); counter is 3 lines, zero production overhead |
| Schema-size discovery | Reflection over `@Entity` annotations in test | `backupSchema.getExportOrder().size()` | `BackupSchema` already does the Metamodel enumeration at startup; duplicating it in the test is redundant |

---

## Common Pitfalls

### Pitfall 1: DataImportAuditService Has Explicit Constructor (Not @RequiredArgsConstructor)

**What goes wrong:** Adding `BackupExecutedByResolver` as a `final` field and assuming `@RequiredArgsConstructor` will handle injection — but `DataImportAuditService` has an explicit constructor at lines 66–73 (required because of `@Qualifier("backupObjectMapper")`).

**Root cause:** `@Qualifier` on constructor parameters is incompatible with Lombok's `@RequiredArgsConstructor`.

**How to avoid:** Add `BackupExecutedByResolver executedByResolver` as a new constructor parameter in the EXPLICIT constructor. Also remove the `Environment environment` field after WR-01 (it becomes dead code in `DataImportAuditService`).

**Warning signs:** Compilation error if `@RequiredArgsConstructor` is added to `DataImportAuditService` while `@Qualifier` remains on a parameter.

### Pitfall 2: BackupImportService.resolveExecutedBy() Has No callerOverride Parameter

**What goes wrong:** Assuming `BackupImportService.resolveExecutedBy()` takes a `callerOverride` parameter (as `DataImportAuditService`'s version does) — it does not. The service-level method at line 731 is a 3-branch version with no override param.

**Root cause:** The two implementations diverged organically.

**How to avoid:** The new `BackupExecutedByResolver.resolve(String callerOverride)` is the 4-branch unified version. `BackupImportService` calls `executedByResolver.resolve(null)` (no caller override). `DataImportAuditService` calls `executedByResolver.resolve(executedByCaller)`.

**Warning signs:** If the new `BackupImportService` call passes `callerOverride` as a non-null value, branch 2 of the resolver would fire, returning the override string instead of checking SecurityContext.

### Pitfall 3: BACK-03 zipOpenCounter Not Reset in execute()

**What goes wrong:** If `zipOpenCounter.set(0)` is not placed at the top of `execute()`, a second call to `execute()` on the same bean instance (e.g., in an IT that calls execute twice) would see `zipOpenCounter.get() == 2` instead of `1`.

**Root cause:** Spring beans are singletons by default; the counter persists across test methods unless reset.

**How to avoid:** Place `zipOpenCounter.set(0)` as the FIRST statement inside `execute(UUID stagingId)`, before any other logic.

**Warning signs:** `BackupRestoreZipOpenCountIT` asserts `== 1` after a single execute call; if a prior test left the counter at a non-zero value, the assertion will fail non-deterministically.

### Pitfall 4: IN-04 YAML Change May Break BackupRoundTripIT Path Collision Check

**What goes wrong:** The `${spring.profiles.active:dev}` expression resolves to the FIRST active profile in the comma-separated list. Tests that set `@ActiveProfiles("dev")` will get `data/dev/import-backups`; tests with `@ActiveProfiles("local")` get `data/local/import-backups`. The `BackupRoundTripIT` already overrides the property via `@DynamicPropertySource` to a temp directory, so the expression is never evaluated in those tests.

**Root cause:** `BackupImportServiceIT`, `BackupImportRollbackIT`, and other ITs that do NOT override `import-backups-dir` will start using `data/dev/import-backups` instead of `data/.import-backups`. This is the intended change — but the old path must not be hardcoded anywhere in test assertions.

**How to avoid:** Grep for `data/.import-backups` in the test tree before committing IN-04 and replace any hardcoded path references.

**Warning signs:** IT asserting that the import-backups directory exists at `data/.import-backups/<ts>/` will fail after IN-04.

### Pitfall 5: BackupSchemaGuardTest Is @Tag("integration"), Not a Unit Test

**What goes wrong:** Including `BackupSchemaGuardTest` in the Surefire unit-test phase (untagged) — the `@SpringBootTest` context boot would then run under Surefire's fork config for unit tests, which lacks the JPA DataSource setup.

**Root cause:** BACK-01 guard cannot be a plain unit test due to `BackupSchema`'s Spring dependency.

**How to avoid:** Add `@Tag("integration")` to `BackupSchemaGuardTest`. The per-commit test command is `./mvnw verify -Dit.test=BackupSchemaGuardTest`, not `./mvnw test -Dtest=BackupSchemaGuardTest`.

### Pitfall 6: IN-01 and IN-02 May Confuse IDE Caches After Lombok Annotation Removal

**What goes wrong:** After removing `@RequiredArgsConstructor` from 18 restorer files, the IDE (VS Code / Eclipse JDT) may show spurious "no-arg constructor missing" compilation errors.

**Root cause:** Lombok annotation processing requires rebuild; JDT caches may not invalidate.

**How to avoid:** Per CLAUDE.md `feedback_clean_maven_build_authority`: if IDE shows stale errors after IN-01/IN-02, run `./mvnw clean test-compile` and trust the Maven output.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito + Spring Boot Test |
| Config file | `pom.xml` (Surefire: lines 184–194; Failsafe: lines 256–278) |
| Quick run command (unit) | `./mvnw test -Dtest=<ClassName>` |
| Quick run command (IT) | `./mvnw verify -Dit.test=<ClassNameIT> -DfailIfNoTests=false` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BACK-01 | `SCHEMA_VERSION == 1` AND `getExportOrder().size() == 24` with prescribed failure message | Integration (SpringBootTest) | `./mvnw verify -Dit.test=BackupSchemaGuardTest` | ❌ Wave 0 |
| BACK-02 | Each of 12 items traceable to one commit (5 new + 7 via SHA in audit doc) | Manual audit | Review `82-BACKLOG-AUDIT.md` | ❌ Wave 0 |
| BACK-03 | `restoreAll` opens ZIP exactly once per execute call | Integration (SpringBootTest) | `./mvnw verify -Dit.test=BackupRestoreZipOpenCountIT` | ❌ Wave 0 |
| BACK-03 (IN-03) | Missing ZIP entry escalates to WARN log | Integration (SpringBootTest) | `./mvnw verify -Dit.test=BackupRestoreZipOpenCountIT` or sibling IT | ❌ Wave 0 |
| BACK-04 | `BackupRoundTripIT` + `BackupImportRollbackIT` still pass after every commit | Integration (SpringBootTest) | `./mvnw verify -Dit.test=BackupRoundTripIT,BackupImportRollbackIT` | ✅ Existing |
| BACK-05 | 24-entity row-count parity after round-trip | Integration (SpringBootTest) | `./mvnw verify -Dit.test=BackupRoundTripIT` | ❌ Wave 0 (new @Test in existing file) |
| WR-01 | `BackupExecutedByResolver` covers all 4 resolution branches | Unit (MockitoExtension) | `./mvnw test -Dtest=BackupExecutedByResolverTest` | ❌ Wave 0 |
| IN-01/IN-02 | Annotation cleanup compiles and restorers still wire correctly | Integration (SpringBootTest via BackupImportServiceIT) | `./mvnw verify -Dit.test=BackupImportServiceIT` | ✅ Existing |
| IN-04 | Profile-isolated path resolves correctly | Integration (BackupImportServiceIT spring context boot) | `./mvnw verify -Dit.test=BackupImportServiceIT` | ✅ Existing (path override via @DynamicPropertySource) |

### Sampling Rate

- **Per commit (code-touching):** Targeted test as specified in D-26
- **After IN-01/IN-02 (annotation-only):** `./mvnw test` (Surefire unit only — cheap; catch Lombok compilation issues)
- **Phase gate:** `./mvnw verify -Pe2e` — one time, before raising PR

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/backup/audit/BackupExecutedByResolverTest.java` — covers WR-01 (4 unit tests)
- [ ] `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` — covers BACK-01 (2 IT tests)
- [ ] `src/test/java/org/ctc/backup/service/BackupRestoreZipOpenCountIT.java` — covers BACK-03 + IN-03 (2 IT tests)
- [ ] New `@Test givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch` in `BackupRoundTripIT.H2RoundTripTests` — covers BACK-05 H2 path
- [ ] New `@Test givenSeasonOneFixture_whenRoundTrip_thenAll24EntityRowCountsMatch` in `BackupRoundTripIT.MariaDbRoundTripTests` — covers BACK-05 MariaDB path (gated by `docker.available`)
- [ ] `.planning/phases/82-backup-cleanup/82-BACKLOG-AUDIT.md` — doc artifact for BACK-02

---

## Security Domain

Phase 82 touches the `resolveExecutedBy` logic (SecurityContextHolder access) and a log-level escalation. No new ASVS controls are introduced.

### Applicable ASVS Categories

| ASVS Category | Applies | Notes |
|---------------|---------|-------|
| V2 Authentication | Indirectly | `BackupExecutedByResolver` reads `SecurityContextHolder` — WR-01 does not change the authentication logic, only extracts it |
| V3 Session Management | No | — |
| V4 Access Control | No | No controller or endpoint changes |
| V5 Input Validation | No | No new user input paths |
| V6 Cryptography | No | — |

### Known Threat Patterns

No new threat surface introduced. The `BackupExecutedByResolver.resolve()` method is read-only access to the current authentication context — identical pattern to the two methods it replaces.

---

## Environment Availability

Phase 82 is purely code/config/test changes with no new external tool dependencies.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Maven (`./mvnw`) | Build + test | ✓ | via wrapper | — |
| Java 25 (Temurin) | All compilation | ✓ | 25 (CLAUDE.md) | — |
| H2 (in-memory) | `dev` profile ITs | ✓ | auto-configured | — |
| Docker daemon | `BackupRoundTripIT.MariaDbRoundTripTests` (BACK-05 MariaDB path) | Optional | — | Test skips via `@EnabledIfSystemProperty(docker.available)` |
| SpotBugs via pom.xml | Every `./mvnw verify` | ✓ | Phase 81 wired | — |

**No missing dependencies with no fallback.**

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The `TestDataService.seed()` fixture used in `H2RoundTripTests` seeds at least some rows in 10+ of the 24 entities, making BACK-05's parity assertions non-trivially true | BACK-05 pattern | If most entities have 0 rows, parity assertions pass trivially (0==0) — consider adding an explicit "at least N entities have non-zero counts" assertion |
| A2 | Logback-test.xml root level `WARN` means WARN logs from `BackupImportService` appear in `OutputCaptureExtension` captured output | IN-03 pitfall | If logback-test.xml suppresses `org.ctc.backup.*` package below WARN, the IN-03 test will pass but not actually verify the log — add a package-level WARN override to logback-test.xml if needed |
| A3 | Using `SecurityContextHolder.setAuthentication()` directly in `BackupExecutedByResolverTest` (branch 3) works in the test JVM thread without context propagation issues | WR-01 unit test pattern | If test framework runs in a thread that loses the SecurityContext, the branch-3 test would see null auth — use `@BeforeEach SecurityContextHolder.clearContext()` for isolation |

**If this table had no entries:** All claims were verified or cited.

---

## Open Questions

1. **IN-03 test placement: sibling IT or within BACK-03 IT?**
   - What we know: D-11 says "extend the existing BACK-03 IT or add a sibling". `OutputCaptureExtension` can be added to `BackupRestoreZipOpenCountIT`.
   - What's unclear: Whether the BACK-03 IT's existing fixture (a staged backup ZIP) can easily have one entry removed for the IN-03 test.
   - Recommendation: Place both the ZipEntry-count test (BACK-03) and the WARN-log test (IN-03) in `BackupRestoreZipOpenCountIT`. The WARN-log test uses a synthetic ZIP with a missing data entry, constructed inline in the test. Both tests share the `@SpringBootTest` Spring context startup cost.

2. **BACK-05 fixture richness for playoff_* tables:**
   - What we know: `TestDataService.seed()` loads Saison 2023 + 2024 + 2024-Empty + 2026. Playoff data may be 0 rows if no playoff seasons are seeded.
   - What's unclear: Whether `testDataService.seed()` includes playoff data (requires checking TestDataService source).
   - Recommendation: The planner verifies by running `./mvnw verify -Dit.test=BackupRoundTripIT` and checking the row counts logged at INFO during the existing H2 round-trip test. If all playoff tables are 0, add an explicit `assertThat(preCounts.values().stream().filter(c -> c > 0).count()).as("fixture must have data in at least 12 entities").isGreaterThan(12)`.

---

## Sources

### Primary (HIGH confidence — verified by direct file inspection)

- `src/main/java/org/ctc/backup/service/BackupImportService.java` — lines 635, 660–671, 731–740 (ZipFile open, restoreOneTable skip, resolveExecutedBy)
- `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` — lines 66–73, 102, 138–150 (constructor, call site, resolveExecutedBy)
- `src/main/java/org/ctc/backup/schema/BackupSchema.java` — lines 32, 51 (SCHEMA_VERSION, getExportOrder)
- `src/main/java/org/ctc/backup/restore/entity/*.java` — all 24 files (annotation audit)
- `src/main/resources/application.yml` — line 6 (import-backups-dir)
- `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` — full file (helper methods, MariaDB gate)
- `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` — lines 34–35, 93, 271–275 (OutputCaptureExtension usage)
- `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java` — lines 23, 61 (OutputCaptureExtension usage)
- `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` — lines 18, 51 (OutputCaptureExtension usage)
- `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` — lines 33–50 (@SpringBootTest pattern, size==24 assertion)
- `config/spotbugs-exclude.xml` — full file (EI_EXPOSE_REP suppressions for backup.audit, backup.service)
- `src/main/resources/application-{dev,local,docker,prod}.yml` — all 4 files confirmed no backup-dir overrides

### Secondary (MEDIUM confidence — verified by grep/find)

- `src/main/java/org/ctc/backup/schema/EntityTopoSorter.java` — line 29 (`@Component` confirmed)
- Domain model `@Entity` count: 24 confirmed by `BackupSchemaTopologyIT.hasSize(24)` assertion + direct entity list inspection
- `@VisibleForTesting` annotation: not present anywhere in `src/main/java` (grep result)
- Per-profile YAML override absence: confirmed by grep on all 4 profile files

---

## Metadata

**Confidence breakdown:**
- Fix sites and line numbers: HIGH — verified by direct file read
- Restorer annotation states: HIGH — verified by grep on all 24 files
- BackupRoundTripIT helper methods: HIGH — verified by full file read
- BackupSchema Spring context requirement: HIGH — verified by constructor inspection + EntityTopoSorter @Component
- OutputCaptureExtension convention: HIGH — found in 3 existing backup ITs
- @VisibleForTesting absence: HIGH — grep returned no results
- SpotBugs impact assessment: HIGH — spotbugs-exclude.xml read in full
- Profile YAML override absence: HIGH — grep confirmed no backup config in any profile file
- MariaDB gate property name: HIGH — verified in BackupRoundTripIT source (`docker.available`, not `mariadb.smoke`)
- SCHEMA_VERSION == 1: HIGH — confirmed at line 32 of BackupSchema.java
- Export order size == 24: HIGH — asserted by existing green BackupSchemaTopologyIT

**Research date:** 2026-05-16
**Valid until:** 2026-06-16 (stable codebase; no fast-moving ecosystem dependencies)

---

## RESEARCH COMPLETE
