---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
plan: 06
subsystem: backup-import
tags:
  - orchestrator
  - transactional
  - jpa-auditing-bypass
  - zip-extraction
  - tx-aware-event
  - failure-handling
requirements:
  - IMPORT-05
  - IMPORT-06
  - IMPORT-07
dependency_graph:
  requires:
    - 75-01 (EntityRestorer SPI, RestoreFailureInjector, BackupImportResult, BackupImportSucceededEvent, app.backup.import-backups-dir property)
    - 75-02 (DataImportAuditService.recordResult REQUIRES_NEW writer)
    - 75-03 (10 EntityRestorer @Components)
    - 75-04 (8 EntityRestorer @Components)
    - 75-05 (6 EntityRestorer @Components, incl. TeamRestorer 2-pass)
    - phase-74 (BackupImportService.stage/reparse/deleteStagingFile, BackupArchiveService.readManifest/countUploadFiles, PathTraversalGuard, LimitedInputStream)
  provides:
    - "BackupImportService.execute(UUID stagingId) — single @Transactional(REQUIRED, READ_COMMITTED, rollbackFor=Exception) orchestrator"
    - "BackupArchiveService.extractUploadsTo(Path zip, Path destDir) — Phase 74 hardening reuse for uploads-new staging"
    - "BackupImportException(UUID auditUuid, Throwable cause) — failure carrier for Plan 08 controller flash"
    - "rewriteBatchedStatements=true on application-local.yml + application-docker.yml + documented for application-prod.yml (RESEARCH §10)"
  affects:
    - 75-07 (BackupImportSucceededEvent consumer for AFTER_COMMIT move-triple + success-audit-row)
    - 75-08 (BackupController upgrade — invokes execute, binds BackupImportException.getAuditUuid into D-15 flash strings)
    - 75-09 (BackupImportRollbackIT — exception injection at restore mid-batch)
    - 75-10 (BackupImportMariaDbSmokeIT — local MariaDB round-trip on the same execute(...) method)
tech_stack:
  added: []
  patterns:
    - "@Transactional(propagation = REQUIRED, isolation = READ_COMMITTED, rollbackFor = Exception.class) on a single orchestrator method"
    - "Native EntityManager.createNativeQuery(\"DELETE FROM <table>\").executeUpdate() in FK-reverse order"
    - "em.flush() + em.clear() between wipe and restore — drops L1 cache so downstream restore queries see fresh state"
    - "Spring-discovered Map<String, EntityRestorer> via @PostConstruct wireRestorersByTableName()"
    - "TX-aware event buffering via eventPublisher.publishEvent(...) as the LAST statement inside the try block (Spring TransactionalEventListener(AFTER_COMMIT) defers delivery until commit)"
    - "Best-effort REQUIRES_NEW audit write on catch-path (survives outer rollback)"
    - "Defensive table-name allow-list (^[a-z_]+$) before native-SQL concatenation"
key_files:
  created:
    - src/main/java/org/ctc/backup/exception/BackupImportException.java
    - src/test/java/org/ctc/backup/service/BackupArchiveExtractUploadsIT.java
    - src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java
  modified:
    - src/main/java/org/ctc/backup/service/BackupImportService.java
    - src/main/java/org/ctc/backup/service/BackupArchiveService.java
    - src/main/java/org/ctc/backup/restore/entity/PlayoffRestorer.java
    - src/test/java/org/ctc/backup/restore/entity/PlayoffRestorerTest.java
    - src/main/resources/application-local.yml
    - src/main/resources/application-docker.yml
    - src/main/resources/application-prod.yml
decisions:
  - "Single @Transactional orchestrator method per CONTEXT D-14: execute(UUID) carries Propagation.REQUIRED + Isolation.READ_COMMITTED + rollbackFor=Exception.class. Class-level @Transactional(readOnly=true) is preserved for stage/reparse/buildPreview; the method-level annotation overrides for execute(...)."
  - "Three self-FK pre-step UPDATEs are issued in a fixed order (teams.parent_team_id, season_teams.successor_season_team_id, playoff_matchups.next_matchup_id) BEFORE the FK-reverse DELETE loop — per CONTEXT D-06 / Q1 / Q2 unified resolution."
  - "Restore loop opens a fresh ZipInputStream per EntityRef to seek the data/<slug>.json entry by name. A future optimization could iterate the ZIP entries once and dispatch by name, but the per-restorer overhead is dwarfed by the JDBC batchUpdate cost (each restorer streams via a non-closing JsonParser on the matched entry)."
  - "EntityManager injected via @PersistenceContext on a field rather than the constructor — JPA spec mandates field/method injection for @PersistenceContext, and Spring's ConstructorResolver does not honor the annotation on ctor parameters in the same way."
  - "validateTableName(String) defensively gates the native-SQL concat against ^[a-z_]+$ even though the input comes from JPA @Table(name=...) annotations on hard-coded entity classes — fail-fast over trust-by-convention."
  - "Restore-failure injection point fires every 50 rows (FAIL_INJECT_INTERVAL = 50, mirrors CONTEXT D-13 cadence) — Plan 09's FailAtTableInjector reads (tableName, rowIndex) and throws RestoreFailureSimulatedException on match."
  - "tryRecordFailure(...) on the catch path wraps DataImportAuditService.recordResult(...) in a defensive try/catch — a double-failure (audit write also fails) logs ERROR but does NOT mask the original cause; the BackupImportException propagates verbatim."
  - "uploadsNewDir cleanup on rollback walks the directory in reverse-sorted order (deepest paths first) so directories empty before they are deleted. tryCleanupUploadsNew is best-effort; failures are logged at WARN and the original exception still propagates."
  - "Rule 3 auto-fix on PlayoffRestorer + PlayoffRestorerTest: V6__CleanupLegacySeasonColumns.java had dropped the legacy season_id column from the playoffs table — Plan 05's restorer carried the column nonetheless and would fail on H2 (\"Column SEASON_ID not found\") and MariaDB. Removed season_id from the INSERT template and from the test's setObject(2, ...) assertion, shifted column positions down by one. Documented as Plan 06 Rule 3 deviation."
metrics:
  duration_sec: 3354
  duration_human: "~56 minutes"
  tasks_completed: 2
  files_created: 3
  files_modified: 7
  completed_date: "2026-05-14"
commits:
  - hash: 099c346
    type: feat
    message: "feat(75-06): add real BackupImportService.execute + extractUploadsTo + BackupImportException"
  - hash: 63f76cc
    type: test
    message: "test(75-06): add rewriteBatchedStatements yml + extract-uploads + execute ITs"
---

# Phase 75 Plan 06: Backup Import Execute Orchestrator Summary

Single `@Transactional` orchestrator method `BackupImportService.execute(UUID stagingId)` that
wipes 24 tables in FK-reverse order (with 3 self-FK pre-step UPDATEs + `em.flush+clear`),
extracts the staged `uploads/` tree to `data/.import-backups/<ts>/uploads-new/`, restores all
entities via 24 `EntityRestorer` beans through `JdbcTemplate.batchUpdate` (bypassing
`AuditingEntityListener`), and publishes `BackupImportSucceededEvent` as the LAST statement
inside the try block — Spring's TX-aware buffering defers the Plan 07 listener until
`AFTER_COMMIT`. Failure path writes a `success=false` audit row via REQUIRES_NEW and throws a
new `BackupImportException` carrying the audit-row UUID for the Plan 08 controller flash.

## Performance

- **Duration:** ~56 min
- **Started:** 2026-05-14T08:01:25Z
- **Completed:** 2026-05-14T08:57:19Z (approx)
- **Tasks:** 2 (1 orchestrator + 1 yml/IT bundle)
- **Files created:** 3
- **Files modified:** 7
- **Commits:** 2 (this SUMMARY commit follows)

## Accomplishments

- **`execute(UUID)` is a single `@Transactional(REQUIRED, READ_COMMITTED, rollbackFor=Exception.class)`
  method** — the entire success-criterion-1/-2/-3 of Phase 75 hinges on this boundary. The class
  retains `@Transactional(readOnly=true)` at class level; method-level `@Transactional` overrides
  for `execute(...)`.
- **Wipe sequence locked** per CONTEXT D-06 / Q1 / Q2 unified resolution:
  - 3 self-FK pre-step UPDATEs in fixed order (`teams.parent_team_id`,
    `season_teams.successor_season_team_id`, `playoff_matchups.next_matchup_id`).
  - Forward iteration over `BackupSchema.getExportOrder().reversed()` with native
    `DELETE FROM <table>` via `EntityManager.createNativeQuery(...)`.
  - `em.flush() + em.clear()` after the loop drops the L1 cache.
- **Restore sequence**: forward iteration over `BackupSchema.getExportOrder()`. Per entity,
  open the matching `data/<slug>.json` ZIP entry, parse with Jackson `JsonParser`, accumulate
  batches of `RESTORE_BATCH_SIZE` (500 — CONTEXT D-07), call
  `restorer.restore(batch, jdbcTemplate)`, invoke
  `failureInjector.maybeFailAt(tableName, rowIndex)` every 50 rows.
- **Uploads extraction (D-12)** via the new `BackupArchiveService.extractUploadsTo(Path, Path)`
  helper — reuses Phase 74's `PathTraversalGuard.assertWithin(...)` and `LimitedInputStream`
  per ZIP entry, applies `BackupImportLimits.MAX_ENTRY_BYTES` (50 MB) + `MAX_TOTAL_BYTES`
  (500 MB) + `MAX_ENTRIES` (50 000) caps.
- **TX-aware event publish (D-14 / RESEARCH OQ §4)**: `eventPublisher.publishEvent(new
  BackupImportSucceededEvent(...))` runs as the LAST statement inside the try block.
  Spring's `@TransactionalEventListener(phase=AFTER_COMMIT)` buffers the listener (Plan 07)
  until the outer commit completes.
- **Failure path** (catch-all `Exception`):
  1. SLF4J ERROR with full stack trace.
  2. Best-effort `dataImportAuditService.recordResult(..., success=false)` (REQUIRES_NEW —
     survives outer rollback per Plan 02 contract). Double-failure logs ERROR but does not
     mask the original cause.
  3. Best-effort recursive cleanup of partially-extracted `uploads-new/` directory.
  4. Throw `BackupImportException(auditUuid, cause)` — Plan 08 binds the auditUuid into
     the D-15 #2 failure-flash placeholder.
- **`BackupImportException`** mirrors `BackupArchiveException`'s structural template
  (`RuntimeException`, no Lombok, no enum) but carries a `UUID auditUuid` field accessible
  via `getAuditUuid()`.
- **`rewriteBatchedStatements=true`** appended to `application-local.yml` +
  `application-docker.yml` MariaDB JDBC URLs (with inline `Phase 75 RESEARCH §10` reference
  comment). `application-prod.yml` carries a comment requiring the env-templated
  `DATABASE_URL` to include the parameter (cannot append in YAML since the URL is
  externalized).
- **Two new Failsafe ITs**:
  - `BackupArchiveExtractUploadsIT` — 3 scenarios: benign extraction (3 files byte-identical
    at dest), `PATH_TRAVERSAL` rejection, `ENTRY_TOO_LARGE` rejection via `LimitedInputStream`
    50-MB cap. ZIP fixtures generated programmatically.
  - `BackupImportExecuteIT` — 2 scenarios: full round-trip (Saison 2023 + Saison 2024 +
    Saison 2024-Empty + Saison 2026 seeded by `testDataService.seed()`) with verbatim
    `createdAt` assertion on a pinned Season (IMPORT-05.e), and `parent_team_id` self-FK
    chain reconstructed by `TeamRestorer` 2-pass (IMPORT-06).

## Tasks Executed

### Task 1 — orchestrator + helper + exception — `099c346`

Extended `BackupImportService` from the Phase-74 stateless preview service to a full
preview-and-execute orchestrator. Constructor now accepts 8 additional dependencies
(`JdbcTemplate`, `List<EntityRestorer>`, `@Qualifier("backupObjectMapper") ObjectMapper`,
`RestoreFailureInjector`, `DataImportAuditService`, `ApplicationEventPublisher`,
`Environment`, `@Value("${app.backup.import-backups-dir}")`, `@Value("${app.upload-dir}")`),
and `EntityManager` is injected via `@PersistenceContext` on a field. Two new
`@PostConstruct` wiring methods (`wireRepositoriesByTableName` from Phase 74 + new
`wireRestorersByTableName`) build the two lookup maps and fail-fast at startup if the
counts diverge from `BackupSchema.getExportOrder().size()`.

The `execute(UUID)` method body implements the full skeleton per
`<interfaces>` pseudo-code: staging-file lookup → manifest read → wipe → uploads-extract →
restore → TX-aware event publish → return `BackupImportResult`. Catch-block runs SLF4J
ERROR + REQUIRES_NEW audit + uploads-new cleanup + `BackupImportException` throw.

`BackupArchiveService.extractUploadsTo(Path, Path)` was added next to the Phase-73/74
reader methods, reusing `openHardened`, `nonClosingView`, `LimitedInputStream`, and
`assertEntrySafe` for entry-count + total-inflated-bytes caps. The new helper strips the
`uploads/` prefix from each entry name and resolves the stripped path against `destDir`
through `PathTraversalGuard.assertWithin(...)` (Phase 74 D-11 reuse).

`BackupImportException` was added as a third backup-exception class
(`RuntimeException`-based; structural twin of `BackupArchiveException`/`UploadsRestoreException`).

### Task 2 — yml hardening + ITs + Rule 3 fix — `63f76cc`

Three yml files received the `rewriteBatchedStatements=true` parameter (or its documentation
for the env-templated prod profile) with an inline RESEARCH §10 reference comment.

`BackupArchiveExtractUploadsIT` and `BackupImportExecuteIT` both follow the `@SpringBootTest
@ActiveProfiles("dev")` pattern with programmatic fixture generation (`@TempDir` +
`MockMultipartFile`). The execute IT uses `testDataService.seed()` directly (idempotent
guard via `seasonRepository.count() > 0`) and builds the export ZIP via
`backupArchiveService.writeZip(...)` (REVISION-1 correction: `BackupExportService.export(...)`
does NOT exist).

Rule 3 deviation applied during IT debugging: `PlayoffRestorer.INSERT_SQL` carried a legacy
`season_id` column from Plan 05, but `V6__CleanupLegacySeasonColumns.java` had already
dropped that column from the `playoffs` table. The INSERT failed on H2 with
`Column "SEASON_ID" not found`. Fixed `PlayoffRestorer` (removed `season_id` from the SQL
template; shifted column positions down by one) AND `PlayoffRestorerTest` (removed the
`season_id` assertion; updated `setObject(...)` index expectations). Both files compile and
test-green.

## Verification

| Check | Result |
| ----- | ------ |
| `./mvnw -q -o compile` | BUILD SUCCESS (exit 0) |
| `./mvnw -q -o test-compile` | BUILD SUCCESS (exit 0) |
| `./mvnw -q -o -Dit.test='BackupArchiveExtractUploadsIT,BackupImportExecuteIT' verify` | failsafe-summary: completed=10 errors=0 failures=0 |
| `BackupArchiveExtractUploadsIT` — 3 tests | 3/0/0 (0.45s) |
| `BackupImportExecuteIT` — 2 tests | 2/0/0 (41.55s — full Spring boot + restore on dev fixture) |
| `PlayoffRestorerTest` — 4 tests after Rule 3 fix | 4/0/0 |
| `grep -c '@Transactional' BackupImportService.java` | 5 (class-level readOnly + execute + 4 in Javadoc references) |
| `grep -c 'propagation = Propagation.REQUIRED'` | 1 |
| `grep -c 'Isolation.READ_COMMITTED'` | 1 |
| `grep -c 'rollbackFor = Exception.class'` | 1 |
| `grep -c 'parent_team_id = NULL'` | 1 |
| `grep -c 'successor_season_team_id = NULL'` | 1 |
| `grep -c 'next_matchup_id = NULL'` | 1 |
| `grep -c 'entityManager.flush'` | 2 |
| `grep -c 'entityManager.clear'` | 2 |
| `grep -c 'eventPublisher.publishEvent'` | 1 |
| `grep -c 'dataImportAuditService.recordResult'` | 1 |
| `grep -c 'extractUploadsTo' BackupArchiveService.java` | 1 |
| `grep -c 'pathTraversalGuard\|PathTraversalGuard' BackupArchiveService.java` | 4 |
| `grep -c 'class BackupImportException'` | 1 |
| `grep -c 'getAuditUuid'` | 2 (declaration + accessor) |
| `grep -c 'rewriteBatchedStatements=true' application-local.yml` | 2 (URL + comment) |
| `grep -c 'rewriteBatchedStatements=true' application-docker.yml` | 2 (URL + comment) |
| `grep -c 'rewriteBatchedStatements' application-prod.yml` | 2 (comment lines) |
| `grep -c 'Phase 75' application-local.yml` | 1 |
| `grep -c 'awaitAuditRow\|flagForCommit\|@Commit' BackupImportExecuteIT.java` | 0 (Option A — covered by Plan 10) |
| `grep -c 'testDataService.seed()' BackupImportExecuteIT.java` | 3 |
| `grep -c 'seedSaison2023' BackupImportExecuteIT.java` | 0 |
| `grep -c 'backupArchiveService.writeZip\|exportToBytes' BackupImportExecuteIT.java` | 4 |
| `grep -c 'backupExportService.export' BackupImportExecuteIT.java` | 0 |

## Decisions Made

1. **`@Transactional(REQUIRED, READ_COMMITTED, rollbackFor=Exception.class)` on `execute(UUID)`** — explicit `rollbackFor=Exception.class` so a checked exception (e.g. `IOException` from the inner restore loop) also triggers rollback. Default Spring behavior rolls back only on `RuntimeException`; the explicit attribute is the wire contract.
2. **3 self-FK pre-step UPDATEs hard-coded in fixed order** — the SQL strings are literal native-SQL statements with hard-coded table + column names; no parameter substitution risk. The validate-table-name regex guard fires only on the dynamic DELETE loop where the table name comes from `EntityRef`.
3. **`em.flush() + em.clear()` AFTER the DELETE loop, not BEFORE the restore loop** — same TX boundary, but doing it immediately after wipe means downstream code (audit row counting via repository `count()` from Plan 06) sees a clean session if it executes between wipe and restore.
4. **Restore opens a fresh `ZipInputStream` per entity** — simpler than maintaining a per-ZIP iterator state across 24 entity boundaries. Each restorer-call seeks the matching entry by name (linear scan over <100 entries) and parses only that entry. Performance is dominated by JDBC batch round-trips (the rewriteBatchedStatements=true win matters here), not the ZIP seek.
5. **`failureInjector.maybeFailAt(tableName, rowIndex)` fires every `FAIL_INJECT_INTERVAL = 50` rows** — mirrors CONTEXT D-13 cadence. Production `NoopRestoreFailureInjector` returns immediately; the test-injection latency is bounded by the row counter modulo.
6. **`BackupImportException` carries `UUID auditUuid` — not a `Reason` enum** — there is only one failure mode here ("import rolled back"). The actual cause (wipe failure, restore failure, JSON parse failure, etc.) lives in `getCause()` + the SLF4J ERROR log line. Branching on cause-type is the controller's concern (Plan 08), and the Plan 08 flash strings are uniform regardless.
7. **`uploadsNewDir` cleanup walks via `Files.walk(...).sorted(Comparator.reverseOrder())`** — deepest paths first so directories empty before delete. Standard idiom; documented as best-effort with WARN logs on individual failures.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking Issue] PlayoffRestorer carried legacy `season_id` column dropped by V6**

- **Found during:** Task 2 IT execution (`BackupImportExecuteIT`)
- **Issue:** `PlayoffRestorer.INSERT_SQL` (from Plan 05) included a `season_id` column in
  `INSERT INTO playoffs (id, season_id, phase_id, ...)`. The H2 schema at this point reflects
  Flyway migrations through V7, and `V6__CleanupLegacySeasonColumns.java` line 60 executes
  `ALTER TABLE playoffs DROP COLUMN season_id`. Result: H2 throws
  `JdbcSQLSyntaxErrorException: Column "SEASON_ID" not found`.
- **Fix:** Removed `season_id` from `PlayoffRestorer.INSERT_SQL`, shifted all subsequent
  column positions down by one in the `ParameterizedPreparedStatementSetter`. Updated
  `PlayoffRestorerTest` (which is a Plan 05 unit test that locked the SQL shape) to assert
  the V6-correct positions and to assert `doesNotContain("season_id")` on the captured SQL.
- **Files modified:** `src/main/java/org/ctc/backup/restore/entity/PlayoffRestorer.java`,
  `src/test/java/org/ctc/backup/restore/entity/PlayoffRestorerTest.java`
- **Commit:** `63f76cc`

### Authentication Gates

None.

## Known Stubs

None. The orchestrator + helpers are production code; the ITs cover the happy + self-FK paths.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: filesystem-write | `BackupArchiveService.extractUploadsTo` | New filesystem write surface — extracts ZIP entries into a per-import directory. Mitigation: `PathTraversalGuard.assertWithin(...)` per entry (Phase 74 D-11 reuse), `LimitedInputStream` per-entry inflate cap (50 MB), aggregate inflate cap (500 MB), entry-count cap (50 000). All hardening surfaces are exercised by the 3 `BackupArchiveExtractUploadsIT` scenarios. |
| threat_flag: native-sql | `BackupImportService.wipeAllTables` | New native-SQL surface — `DELETE FROM <table>` concatenates the table name. Mitigation: input source is `EntityRef.tableName()` (from JPA `@Table(name=...)` annotations on hard-coded entity classes — not user input). Defensive `validateTableName(String)` regex `^[a-z_]+$` guard fires before concat. Three pre-step UPDATEs are static literals, no concat. |

## TDD Gate Compliance

Plan is `type: execute` — plan-level RED/GREEN gate sequencing is not enforced.

Task 1 ships the orchestrator implementation in a `feat:` commit; Task 2 ships the locking
ITs + the locking unit test repair in a `test:` commit. The implementation is unreachable
from production callers until Plan 08 wires the controller; the IT contract is locked by the
green test run at the Task 2 boundary.

## Next Plan Readiness

- **Plan 07 (AFTER_COMMIT listener):** Consumes the published `BackupImportSucceededEvent` for
  the `uploads/`-tree move triple (Step-1 / Step-2) and the success-time REQUIRES_NEW audit
  row write. The 10-component event record is locked; the listener wires against
  `BackupImportSucceededEvent.importBackupDir / uploadsTarget / uploadsNewDir / stagingId /
  auditUuid / schemaVersion / tableCountsWiped / tableCountsRestored / sourceFilename /
  executedBy`.
- **Plan 08 (Controller upgrade):** Replaces the Phase-74 import-execute stub with a real
  `backupImportService.execute(stagingId)` call. Catches `BackupImportException` and binds
  `getAuditUuid()` into the D-15 #2 failure-flash placeholder. The
  `BackupImportConfirmForm.acknowledged @AssertTrue` gate from Phase 74 D-10 is preserved.
- **Plan 09 (Rollback IT):** Installs `FailAtTableInjector` via `@TestConfiguration`,
  triggers `RestoreFailureSimulatedException` at 50% of the largest table, asserts:
  - `BackupImportException` thrown with non-null `auditUuid`.
  - `success=false` `data_import_audit` row exists after the outer rollback (REQUIRES_NEW
    survival, Plan 02 contract).
  - Row counts pre-execute match row counts post-execute (DB rolled back to pre-wipe state).
- **Plan 10 (Live MariaDB smoke):** Uses the same `execute(...)` method on a Testcontainers
  MariaDB. The `rewriteBatchedStatements=true` parameter now lives on the wire for the
  `local` profile.

## Self-Check: PASSED

**Files checked (all FOUND):**

- `src/main/java/org/ctc/backup/exception/BackupImportException.java`
- `src/main/java/org/ctc/backup/service/BackupImportService.java` (modified)
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java` (modified — `extractUploadsTo`)
- `src/main/java/org/ctc/backup/restore/entity/PlayoffRestorer.java` (modified — Rule 3 fix)
- `src/test/java/org/ctc/backup/restore/entity/PlayoffRestorerTest.java` (modified — Rule 3 fix)
- `src/main/resources/application-local.yml` (modified — `rewriteBatchedStatements=true`)
- `src/main/resources/application-docker.yml` (modified — `rewriteBatchedStatements=true`)
- `src/main/resources/application-prod.yml` (modified — `rewriteBatchedStatements` documented)
- `src/test/java/org/ctc/backup/service/BackupArchiveExtractUploadsIT.java`
- `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java`

**Commits checked (all FOUND in `git log`):**

- `099c346` — feat(75-06): add real BackupImportService.execute + extractUploadsTo + BackupImportException
- `63f76cc` — test(75-06): add rewriteBatchedStatements yml + extract-uploads + execute ITs

---
*Phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat*
*Completed: 2026-05-14*
