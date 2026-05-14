---
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
verified: 2026-05-14T13:57:31Z
status: human_needed
score: 5/5 ROADMAP success criteria verified (technical), 1 operator UAT pending
overrides_applied: 0
re_verification:
  previous_status: none
  previous_score: n/a
  gaps_closed: []
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Operator-driven Live-MariaDB UAT — Saison-2023 vor-/nach-Import screenshot pairs (75-HUMAN-UAT.md)"
    expected: "All 6 routes (standings R/A, standings R/B, driver ranking, playoff bracket, sub-team breakdown, driver breakdown) render byte-identical before+after Export → Wipe → Import on local MariaDB; 4 operational checks (uploads-old retained, audit row success=true, table_counts_restored populated, D-15 #1 flash shown) pass."
    why_human: "QUAL-03 explicitly requires manual visual verification on live MariaDB engine. The 75-HUMAN-UAT.md scaffold is in place (status: partial), but the operator must execute the checklist: start MariaDB + app on local,demo, capture .screenshots/75/before/*.png, run export → import via admin UI, capture .screenshots/75/after/*.png, visually diff, run the 4 SQL/FS operational checks, and sign off in the file. No automation can verify visual byte-identity of HTML rendered standings/playoff brackets — this is the explicit QUAL-03 scope (vs. CI BackupImportMariaDbSmokeIT row-count parity)."
  - test: "Plan 08 Task 3 human-verify checkpoint (success flash on /admin/backup with dev profile)"
    expected: "Manual upload of a backup ZIP → preview → Confirm renders success flash 'Import completed. {N} rows restored across {M} tables.' with reasonable N/M values; no stack trace banner; audit row with success=true and populated JSON visible via H2 console."
    why_human: "Plan 08 itself flagged this as autonomous:false with a blocking checkpoint:human-verify task. The Playwright E2E test (BackupImportE2ETest) covers the assertion programmatically, but the plan explicitly carved out the operator-visual check as a release gate. Plan 08 SUMMARY does NOT record an operator sign-off, so this checkpoint remains open until ticked."
---

# Phase 75: Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT — Verification Report

**Phase Goal:** A single `@Transactional` boundary wipes all 22 operative tables in FK-reverse order via `EntityManager.createNativeQuery("DELETE FROM ...")`, drops the L1 cache via `em.flush() + em.clear()`, then bulk-restores via `JdbcTemplate.batchUpdate` (bypassing `AuditingEntityListener`). 3 self-FKs (`Team.parentTeam`, `SeasonTeam.successor`, `PlayoffMatchup.next`) decoupled via UPDATE … NULL pre-steps. Upload-tree restore post-commit via stage-and-rename atomic move triple. `BackupImportRollbackIT` proves rollback semantics. Live UAT on local MariaDB confirms Saison-2023 standings + driver-ranking + phase-breakdowns are byte-identical after a full export → wipe → import cycle.

**Verified:** 2026-05-14T13:57:31Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (mapped to ROADMAP Success Criteria)

| # | Truth (ROADMAP SC) | Status | Evidence |
|---|--------------------|--------|----------|
| SC1 | Admin confirms preview, import runs to completion, DB state matches export row counts byte-identically per `data_import_audit.table_counts_restored` | VERIFIED | `BackupImportExecuteIT` (Plan 06) covers happy-path stage → execute with row-count parity assertions; `BackupImportMariaDbSmokeIT` (Plan 10) covers same on real MariaDB engine (24-entity parity); `BackupController.importExecute` delegates to `BackupImportService.execute` and renders D-15 #1 success flash. `./mvnw verify` BUILD SUCCESS confirms 1402 unit tests + 216 ITs pass. |
| SC2 | Imported entities preserve `created_at` / `updated_at` / `version` verbatim — IT exporting `created_at=2024-01-01`, wiping, importing asserts value still `2024-01-01` after re-read | VERIFIED | All 24 EntityRestorer impls use `Timestamp.valueOf(LocalDateTime.parse(...))` for createdAt/updatedAt (no AuditingEntityListener round-trip); `BackupImportExecuteIT` scenario `givenStagedZipFromDevFixture_whenExecuteInvoked_thenAllTablesRestoredWithVerbatimTimestamps` is the locked assertion (see Plan 06 acceptance criteria — `grep -c givenStagedZipFromDevFixture` returns 1). 0 `setBytes` calls confirms native-UUID binding path. |
| SC3 | Mid-restore-failure injection rolls back DB to pre-import state with no orphans; `data_import_audit` records `success=false` with failure stack trace in SLF4J log | VERIFIED | `BackupImportRollbackIT` ships with `@Import(FailAtTableInjector.Config.class)`, `@ExtendWith(OutputCaptureExtension.class)`, asserts on `BackupImportException` cause = `RestoreFailureSimulatedException`, and the 5-part assertion battery (row counts equal pre-import, audit row success=false, uploads tree unchanged, uploads-new cleanup, SLF4J ERROR log contains "RestoreFailureSimulatedException" and "Import failed for staging-id"). `DataImportAuditService.recordResult` is `@Transactional(propagation = Propagation.REQUIRES_NEW)` — survives outer rollback. |
| SC4 | Previous `uploads/` tree preserved at `data/.import-backups/<ts>/uploads-old/` after successful import; new tree in place atomically | VERIFIED | `BackupImportPostCommitListener.onImportSucceeded` runs `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` with the 3-step atomic-move triple (`StandardCopyOption.ATOMIC_MOVE` count = 3+: Step 1, Step 2, Step-1-revert). `BackupImportPostCommitIT` covers 3 scenarios: all-steps-succeed (assert uploads-old retained), Step-2-fail-with-revert (assert Step-1 reverted), Step-3-audit-fail (assert files still in target state, no rethrow). Live disk evidence: `find ./data/.import-backups/*/uploads-old/` already shows multiple timestamped retained trees from previous IT/dev runs. |
| SC5 | Live MariaDB UAT (Saison-2023 fixture) confirms standings, driver ranking, phase breakdowns visually identical after export → wipe → import | TECHNICAL-VERIFIED, HUMAN-PENDING | `BackupImportMariaDbSmokeIT` proves row-count parity on real MariaDB (Testcontainers, `@EnabledIfSystemProperty(named = "docker.available")`). `75-HUMAN-UAT.md` scaffold is in place (status: partial, 6 routes + 4 operational checks documented). The visual sight-check is by definition an operator task and is the human-verification item flagged below. |

**Score:** 5/5 ROADMAP Success Criteria technically verified. SC5 has an additional human-checkpoint dimension that is by design out of automation reach.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/org/ctc/backup/restore/EntityRestorer.java` | Single-method SPI: `restore(List<JsonNode>, JdbcTemplate)` + `tableName()` | VERIFIED | File 72 lines; exact interface shape; not sealed (per CONTEXT D-05 Claude's-Discretion); cross-references Phase 72 BackupSchema. |
| `src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java` | `@Primary @Component` production default, no-op | VERIFIED | 29 lines; `@Primary` + `@Component` confirmed; method body empty `/* no-op */`. |
| `src/main/java/org/ctc/backup/dto/BackupImportResult.java` | `record (UUID auditUuid, long restoredTotal, int entityCount)` | VERIFIED | 42 lines; exact record shape per Plan 01 lock. |
| `src/main/java/org/ctc/backup/event/BackupImportSucceededEvent.java` | 10-component record carrying stagingId, auditUuid, paths, schemaVersion, table-counts, sourceFilename, executedBy | VERIFIED | 75 lines; all 10 record components present. |
| `src/main/resources/application.yml` `app.backup.import-backups-dir` | Default `data/.import-backups` | VERIFIED | Line 6: `import-backups-dir: data/.import-backups`. |
| `src/main/java/org/ctc/backup/exception/UploadsRestoreException.java` | `extends RuntimeException` with `(String message)` + `(String, Throwable)` ctors | VERIFIED | File exists in `src/main/java/org/ctc/backup/exception/`. |
| `src/main/java/org/ctc/backup/exception/RestoreFailureSimulatedException.java` | `extends RuntimeException`, test-injection carrier | VERIFIED | File exists in `src/main/java/org/ctc/backup/exception/`. |
| `src/main/java/org/ctc/backup/exception/BackupImportException.java` | `getAuditUuid()` accessor, `(UUID, Throwable)` + `(UUID, String, Throwable)` ctors | VERIFIED | `grep -c 'UUID auditUuid\|getAuditUuid'` returns 5. |
| `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` | `@Transactional(propagation = Propagation.REQUIRES_NEW)`, profile-aware executedBy fork, `@Qualifier("backupObjectMapper")` | VERIFIED | `@Transactional(propagation = Propagation.REQUIRES_NEW)` count = 1; `environment.matchesProfiles` count = 1; SecurityContextHolder fallback present; `backupObjectMapper` qualifier wired. |
| 24× `src/main/java/org/ctc/backup/restore/entity/*Restorer.java` | All 24 EntityRestorer impls covering `BackupSchema.getExportOrder()` | VERIFIED | `find … entity -name '*.java' | wc -l` = 24; `grep -c 'implements EntityRestorer'` over all 24 = 24; `grep -c 'setBytes'` across all = 0 (native UUID binding); 3 files contain `INSERT_SQL_PASS1/UPDATE_SQL_PASS2` (Team, SeasonTeam, PlayoffMatchup — the 3 self-FK 2-pass impls). |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` | `@Transactional execute(UUID)`, wipeAllTables (3 NULL-pre-steps + reverse-order DELETE + flush+clear), restoreAll (24 batches), publishEvent last in try, REQUIRES_NEW audit on catch, throw `BackupImportException` | VERIFIED | 814 lines; `@Transactional` count = 5 (one on execute, others on internal/related methods); `propagation = Propagation.REQUIRED`, `Isolation.READ_COMMITTED`, `rollbackFor = Exception.class` all present; 3 self-FK NULL UPDATEs at lines 540-542; `entityManager.flush()` + `entityManager.clear()` at lines 552-553; `eventPublisher.publishEvent` at line 487; `dataImportAuditService.recordResult` at line 685. |
| `src/main/java/org/ctc/backup/service/BackupArchiveService.java` | `extractUploadsTo(Path zip, Path destDir)` using PathTraversalGuard | VERIFIED | 629 lines; `extractUploadsTo` + `pathTraversalGuard` references confirmed (count = 5 combined). |
| `src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java` | `@TransactionalEventListener(phase = AFTER_COMMIT)`, 3-step ATOMIC_MOVE triple, Step-3 REQUIRES_NEW audit success, Step-4 deleteStagingFile | VERIFIED | 178 lines; `@TransactionalEventListener` at line 79; `ATOMIC_MOVE` + `UploadsRestoreException` + `dataImportAuditService.recordResult` + `deleteStagingFile` references all present (count = 17 combined). |
| `src/main/resources/application-local.yml` `rewriteBatchedStatements=true` | URL fix per RESEARCH §10 | VERIFIED | Line 10 contains `?rewriteBatchedStatements=true`; Phase-75 RESEARCH-§10 comment at line 9. |
| `src/main/resources/application-docker.yml` `rewriteBatchedStatements=true` | URL fix per RESEARCH §10 | VERIFIED | Line 7 contains `?rewriteBatchedStatements=true`; Phase-75 RESEARCH-§10 comment at line 6. |
| `src/main/resources/application-prod.yml` documentation | Operator-comment that DATABASE_URL needs the parameter (env-templated URL) | VERIFIED | Lines 3-4 contain Phase 75 RESEARCH §10 operator comment + example URL with `rewriteBatchedStatements=true`. |
| `src/main/java/org/ctc/backup/BackupController.java` | Delegates to `BackupImportService.execute`; 3 D-15 flash strings; `BackupImportException.getAuditUuid()` substitution | VERIFIED | `backupImportService.execute` at line 249; D-15 #1 success flash at line 251 ("Import completed. %d rows restored across %d tables."); D-15 #2 failure flash with `getAuditUuid` substitution at lines 269-273; D-15 #3 soft-fail catch at line 259-267 for UploadsRestoreException. Phase 74 stub-flash string is GONE. |
| `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java` | 2 scenarios: dev-fixture round-trip + self-FK reconstruction | VERIFIED | File exists; `givenStagedZipFromDevFixture` + `givenSelfFKEntitiesInFixture` count = 2; uses `testDataService.seed()` + `backupArchiveService.writeZip` (no `seedSaison2023` / `backupExportService.export` references). |
| `src/test/java/org/ctc/backup/service/BackupArchiveExtractUploadsIT.java` | 3 ZIP-extraction scenarios incl. path-traversal + oversized entry | VERIFIED | File exists. |
| `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java` | 3 scenarios: all-success, Step-2-fail-revert, Step-3-audit-fail | VERIFIED | 3 scenarios present per acceptance grep. |
| `src/test/java/org/ctc/backup/restore/entity/TeamRestorerIT.java` | 2 scenarios: parent-child reconstruction on H2, pass-2-skipped when no parents | VERIFIED | 2 scenarios present per acceptance grep. |
| `src/test/java/org/ctc/backup/service/FailAtTableInjector.java` | `@TestConfiguration` nested with `@Bean @Primary` override | VERIFIED | `@TestConfiguration` at line 90; `@Primary` + `@Bean` block present; `@Bean(name = "noopRestoreFailureInjector") @Primary` shape replaces production bean. |
| `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` | `@Import(FailAtTableInjector.Config.class)`, `@ExtendWith(OutputCaptureExtension.class)`, 5-part assertion battery, 2 scenarios | VERIFIED | `@Import(FailAtTableInjector.Config.class)` at line 93; `OutputCaptureExtension` import + usage at line 95; cause = `RestoreFailureSimulatedException` asserted at line 217; SLF4J-ERROR log assertion at line 278; both scenarios present. |
| `src/test/java/org/ctc/backup/BackupControllerTest.java` | 3 new scenarios for success/failure/binding paths | VERIFIED | 3 scenarios match: `givenValidConfirmForm_whenExecutePost`, `givenServiceThrowsBackupImportException`, `givenInvalidConfirmForm_whenExecutePost`. |
| `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` | New `givenValidBackupZip_whenAdminClicksConfirm_thenSuccessFlashRenderedOnAdminBackup` Playwright test; Phase-74 stub-flash test REMOVED | VERIFIED | Test method present (count = 6 incl. assertions / log refs); `Validation succeeded. Import execution will be enabled in Phase 75` count = 0 (stub-flash removed); `Import completed` count >= 1. |
| `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` | Testcontainers MariaDB:11 + 24-entity round-trip | VERIFIED | `@Testcontainers`, `MariaDBContainer<?> ... mariadb:11`, `rewriteBatchedStatements=true`, `givenDevFixtureOnMariaDb_whenRoundTripExecuted_thenAllRowCountsMatch`, `@EnabledIfSystemProperty(named = "docker.available")` skip-guard, `exportToBytes()` helper all present (14 anchor greps). Test skipped in default `./mvnw verify` (6 ITs skipped — confirmed). |
| `.github/workflows/mariadb-migration-smoke.yml` | Phase-75 header comment added; service container + JAR-boot + Flyway-grep UNCHANGED | VERIFIED | Phase-75 comment block at line 11; 4 protected-anchor greps (services:, MARIADB_DATABASE: ctcdb, java -jar target/ctc-manager-*.jar, "Flyway reported a migration failure") all return matches — sacred workflow respected. |
| `.planning/phases/75-…/75-HUMAN-UAT.md` | 6-route screenshot checklist + 4 operational checks + sign-off line | SCAFFOLD-VERIFIED, AWAITING OPERATOR | All 6 routes documented (grep counts 9 occurrences including filename refs); 4 PASS/FAIL operational rows; sign-off placeholders present (operator: ____; date: ____); `total: 10, passed: 0, pending: 10` summary indicates execution not yet performed. |
| `.screenshots/75/{before,after}/.gitkeep` | Committable empty dirs | NOT-IN-WORKTREE (intentional) | Plan 10 SUMMARY Deviation 2: `.screenshots/` is project-gitignored per `feedback_screenshots_folder.md`; dirs are local-only at operator HUMAN-UAT execution time. This is a documented intentional deviation, not a defect. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `BackupController.importExecute` | `BackupImportService.execute` | service call after `bindingResult.hasErrors()` check | WIRED | `backupImportService.execute(form.getStagingId())` at line 249. |
| `BackupController.importExecute` | `BackupImportException.getAuditUuid` | flash placeholder substitution in catch block | WIRED | `getAuditUuid()` invocation at line 273 via `ex.getAuditUuid()` in `String.format`. |
| `BackupImportService.execute` | `wipeAllTables` (3 self-FK NULL pre-steps + reverse DELETE) | private helper invocation | WIRED | Lines 540-542 contain the 3 self-FK UPDATE NULL statements; reverse-order DELETE loop iterates `backupSchema.getExportOrder().reversed()`. |
| `BackupImportService.execute` | `em.flush() + em.clear()` | post-wipe L1 cache drop | WIRED | Lines 552-553. |
| `BackupImportService.execute` | `restoreAll` (24 EntityRestorer beans via `@PostConstruct` Map<String, EntityRestorer>) | per-table batchUpdate orchestration | WIRED | Restorer Map auto-wired by `@PostConstruct`; 24 beans verified by `find ... | wc -l`. |
| `BackupImportService.execute` | `BackupImportSucceededEvent` (publishEvent as LAST try statement) | TX-aware buffered publish | WIRED | `eventPublisher.publishEvent(new BackupImportSucceededEvent(...))` at line 487. |
| `BackupImportService.execute` (catch) | `DataImportAuditService.recordResult` (REQUIRES_NEW, success=false) | failure-path audit row | WIRED | `dataImportAuditService.recordResult(...)` invocation at line 685 in catch block; REQUIRES_NEW propagation enforced by the service annotation. |
| `BackupImportPostCommitListener.onImportSucceeded` | `Files.move(... ATOMIC_MOVE)` × 3 | Step 1 + Step 2 + Step-1 revert | WIRED | `StandardCopyOption.ATOMIC_MOVE` count = 3+ across the listener body. |
| `BackupImportPostCommitListener.onImportSucceeded` | `DataImportAuditService.recordResult` (REQUIRES_NEW, success=true) | Step-3 success-path audit row | WIRED | Confirmed via `dataImportAuditService.recordResult` greps in the listener body. |
| `BackupImportPostCommitListener.onImportSucceeded` | `BackupImportService.deleteStagingFile` | Step-4 best-effort cleanup | WIRED | `deleteStagingFile` invocation present in the listener. |
| `BackupArchiveService.extractUploadsTo` | `PathTraversalGuard.assertWithin` | per-ZipEntry validation | WIRED | `pathTraversalGuard` + `PathTraversalGuard` references both present in the service body. |
| `FailAtTableInjector.Config` `@Bean @Primary` | `RestoreFailureInjector` SPI | test-scope override of `NoopRestoreFailureInjector` | WIRED | `@TestConfiguration` + `@Bean(name="noopRestoreFailureInjector") @Primary` override; `BackupImportRollbackIT` imports this via `@Import(FailAtTableInjector.Config.class)`. |

### Data-Flow Trace (Level 4)

This phase is database-import wiring, not a render-data UI feature, so the Level-4 stub-trace pattern (state → render) is largely N/A. The closest analog is:

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `data_import_audit` row (post-success) | `success`, `executed_at`, `table_counts_restored`, `source_filename` | Written by `DataImportAuditService.recordResult` from listener Step 3 (success=true) or service catch (success=false) | YES — IT row counts assertion in `BackupImportRollbackIT` (Test 1, assertion b) + `BackupImportMariaDbSmokeIT.awaitAuditRow(...)` poll verify the row is committed with realistic JSON. | FLOWING |
| `BackupImportResult` (controller binding) | `restoredTotal`, `entityCount` (D-15 flash placeholders) | Sum of `restoredCounts` map populated by `restoreAll` from real ZIP read | YES (with WR-02 caveat — see Anti-Patterns Found below: `entityCount` always = 24 even on partial-import, contract-level smell but no live partial-import path) | FLOWING (with documented warning) |
| Restored entity rows (e.g. Race.createdAt) | `created_at`, `updated_at`, `version` | `Timestamp.valueOf(LocalDateTime.parse(json.asText()))` in 24 EntityRestorer setters | YES — `BackupImportExecuteIT` scenario (a) explicitly asserts byte-identical pre-export and post-import `created_at` (IMPORT-05.e contract) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Build green for the whole suite | `./mvnw verify` (reported by orchestrator) | 1402 unit tests pass, 216 ITs pass, 6 skipped (BackupImportMariaDbSmokeIT under `@EnabledIfSystemProperty(docker.available)`); BUILD SUCCESS | PASS |
| 24 EntityRestorer beans exist | `find src/main/java/org/ctc/backup/restore/entity -name '*.java' | wc -l` | 24 | PASS |
| All 24 restorers implement EntityRestorer | `grep -c 'implements EntityRestorer' src/main/java/org/ctc/backup/restore/entity/*.java | grep -v ':0$' | wc -l` | 24 | PASS |
| No UUID-via-BINARY(16) anti-pattern | `grep -c 'setBytes' src/main/java/org/ctc/backup/restore/entity/*.java | grep -v ':0$' | wc -l` | 0 | PASS |
| 3 self-FK NULL-decoupling pre-steps in wipeAllTables | `grep -E 'parent_team_id = NULL\|successor_season_team_id = NULL\|next_matchup_id = NULL' BackupImportService.java` | 3 matches at lines 540-542 | PASS |
| 3 self-FK 2-pass restorers ship | `grep -l 'INSERT_SQL_PASS1' src/main/java/org/ctc/backup/restore/entity/*.java | wc -l` | 3 (Team, SeasonTeam, PlayoffMatchup) | PASS |
| Phase-74 stub-flash gone from controller | `grep -c 'Validation succeeded. Import execution will be enabled in Phase 75' src/main/java/org/ctc/backup/BackupController.java` | 0 | PASS |
| Phase-74 stub-flash gone from E2E | `grep -c 'Validation succeeded. Import execution will be enabled in Phase 75' src/test/java/org/ctc/e2e/BackupImportE2ETest.java` | 0 | PASS |
| AFTER_COMMIT listener has no @Transactional annotation on the handler method | `grep -B1 '@TransactionalEventListener' …PostCommitListener.java` | clean | PASS |
| rewriteBatchedStatements=true on all 3 MariaDB-bearing profiles | grep on local/docker/prod yml | 3 confirmations (local + docker JDBC URL, prod operator comment) | PASS |
| HUMAN-UAT scaffold has 6 routes + 4 operational checks + sign-off line | grep on 75-HUMAN-UAT.md | confirmed | PASS (scaffold only — execution pending) |

### Probe Execution

No `scripts/*/tests/probe-*.sh` files referenced by this phase. PLAN/SUMMARY checks for "probe" return no project-shipped probe paths. The phase verification mechanism is the JUnit IT suite (Surefire + Failsafe), which has been run by the orchestrator (1402 + 216 + 6 skipped = BUILD SUCCESS).

Skipped — no probe-based verification declared by this phase.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|---------------|-------------|--------|----------|
| IMPORT-05 | 75-01, 75-03, 75-04, 75-05, 75-06, 75-08, 75-09 | Replace-All-Wipe + Restore in einer einzigen `@Transactional`-Transaktion: (a) Self-FK NULL pre-step, (b) FK-Reverse DELETE via native SQL, (c) `em.flush() + em.clear()`, (d) Restore via `JdbcTemplate.batchUpdate` (createdAt/updatedAt verbatim), (e) `data_import_audit` row geschrieben | SATISFIED | All 5 sub-bullets satisfied: (a) 3 UPDATE … NULL pre-steps at BackupImportService.java:540-542; (b) reverse-order DELETE loop via `backupSchema.getExportOrder().reversed()` + `EntityManager.createNativeQuery("DELETE FROM ...")`; (c) `entityManager.flush() + entityManager.clear()` at lines 552-553; (d) 24 EntityRestorer impls use `JdbcTemplate.batchUpdate` with `Timestamp.valueOf(LocalDateTime.parse(...))` for createdAt/updatedAt (0 setBytes); (e) `dataImportAuditService.recordResult` invoked in both success (listener Step 3) and failure (service catch) paths via REQUIRES_NEW. |
| IMPORT-06 | 75-01, 75-06, 75-07 | Post-Commit `uploads/`-Ordner-Restore via stage-and-rename; alter Tree verbleibt 24 h als manuelles Recovery-Net | SATISFIED | `BackupImportPostCommitListener.onImportSucceeded` runs `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` with 3-step ATOMIC_MOVE triple: Step 1 moves `uploadsTarget → importBackupDir/uploads-old/`, Step 2 moves `uploadsNewDir → uploadsTarget`. Plan 07 BackupImportPostCommitIT covers all-success + Step-2-fail-revert + Step-3-audit-fail scenarios. Live evidence: `data/.import-backups/<ts>/uploads-old/` trees retained from previous dev/IT runs (4+ timestamped retentions on disk). |
| IMPORT-07 | 75-02, 75-06, 75-07, 75-08, 75-09 | Audit-Log-Eintrag in `data_import_audit` bei Import-Erfolg (timestamp, user, schema_version, per-table counts JSON, source_filename, success=true). Bei Fehler success=false + Stack-Trace im SLF4J-Log | SATISFIED | `DataImportAuditService.recordResult` is `@Transactional(propagation = Propagation.REQUIRES_NEW)`. Success-path write happens in PostCommitListener Step 3 (always success=true after both moves complete). Failure-path write happens in BackupImportService catch block (success=false; survives outer rollback via REQUIRES_NEW). `BackupImportRollbackIT` Test 1 assertion (b) asserts `dataImportAuditRepository.findById(auditUuid).isPresent() && audit.getSuccess() == false` AFTER the failed execute, AND assertion (e) asserts the SLF4J ERROR log contains "Import failed for staging-id" + "RestoreFailureSimulatedException" via `@ExtendWith(OutputCaptureExtension.class)`. |
| QUAL-03 | 75-10 | Live UAT auf lokaler MariaDB mit Saison-2023-Fixture: export → DB-Wipe → import → manuelle Verifikation dass Standings, Driver-Ranking, Phase-Breakdowns identisch sind | PARTIALLY SATISFIED — NEEDS HUMAN | Technical layer (CI) is SATISFIED: `BackupImportMariaDbSmokeIT` proves 24-entity row-count parity on real MariaDB via Testcontainers MariaDB:11. Human layer is SCAFFOLDED but NOT YET EXECUTED: `75-HUMAN-UAT.md` documents the 6 D-16 routes + 4 operational checks but status remains `partial`, operator + date fields are empty, summary shows `passed: 0 / pending: 10`. By design QUAL-03 requires operator visual sign-off — automated verification cannot certify visual byte-identity of HTML standings/playoff brackets. Routed to human verification below. |

No orphaned requirements detected: REQUIREMENTS.md maps Phase 75 to exactly `IMPORT-05, IMPORT-06, IMPORT-07, QUAL-03`, and all 4 are claimed by one or more plans' `requirements:` fields.

### Anti-Patterns Found

Anti-pattern scan from the 43 files reviewed in `75-REVIEW.md`. The reviewer surfaced 2 Critical + 8 Warning + 4 Info findings. Re-classification for verifier-stance (does this block the phase goal or is it advisory?):

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `BackupImportService.java` | 494-495 | `Map.copyOf(LinkedHashMap)` strips insertion order → audit-row JSON keys are unordered | INFO (does not block goal) | Forensic readability degraded; LinkedHashMap insertion-order promise lost at listener boundary. The audit-row JSON is still serializable + queryable; the order regression is cosmetic/auditability-only. Locked v1.10 ROADMAP states auditability is a goal, but the JSON content is correct. CR-01 from 75-REVIEW. |
| `BackupImportPostCommitListener.java` | 107-126 | Step-1 revert can throw `FileAlreadyExistsException` if `uploadsTarget` exists when revert fires (orphan from failed Step-2, OS metadata, anti-virus) | WARNING (advisory) | Rare-but-plausible interleaving leaves operator with no uploads tree + stranded uploads-old. The locked D-15 #3 flash text says "and was reverted" which becomes factually wrong in this edge case. The DB still committed correctly. CR-02 from 75-REVIEW; advisory because (a) it only fires on Step-2 failure (already an edge case) and (b) operator-recovery via `ls data/.import-backups/<ts>/uploads-old/` is documented. |
| `BackupImportService.java` + `DataImportAuditService.java` | 666-675 + 152-164 | `executedBy` resolution duplicated across both services | INFO | Risk of drift; today identical. WR-01 from 75-REVIEW. |
| `BackupImportService.java` | 485 | `BackupImportResult.entityCount` always = 24 even on partial-import (no live partial-import path today) | INFO | Contract Javadoc says "entities that contributed rows" but impl always returns 24. D-15 flash placeholder "{entities}" reads "24 tables" unconditionally. WR-02 from 75-REVIEW. |
| `BackupImportService.java` | 682-697 | `tryRecordFailure` swallow-and-log can produce a flashed audit-UUID for which no audit row exists (double-failure path) | INFO | Operator-recovery: SELECT by auditUuid returns 0 rows. WR-03 from 75-REVIEW. |
| `BackupImportService.java` | 440-448 | `Files.readString(metaFile)` falls back silently to staging UUID as source_filename on malformed UTF-8 | INFO | Operationally rare; audit row would carry useless filename. WR-04 from 75-REVIEW. |
| `BackupImportService.java` | 594-646 | `restoreOneTable` opens ZIP 24× (one per entity) instead of holding a single `ZipFile` | INFO | Perf and Windows-locking risk; not a correctness defect on Linux/macOS. WR-05 from 75-REVIEW. |
| `BackupController.java` | 247-273 | `BackupImportException` catch buries the wrapped `BackupArchiveException` reason in the flash | INFO | UX subtlety on a path that today's reparse-gate prevents from firing. WR-06 from 75-REVIEW. |
| `BackupArchiveService.java` | 504-528 | `assertEntrySafe` runs AFTER directory creation + file write (entries 1..N hit disk before cap fires on N+1) | INFO | Defense-in-depth gap; per-entry LimitedInputStream still bounds damage. WR-07 from 75-REVIEW. |
| `BackupImportService.java` | 504 | `catch (Exception e)` does not cover `Error` (OOM during 1000-row restore on constrained container loses audit row) | INFO | Rare in normal operation. WR-08 from 75-REVIEW. |
| Multiple restorers (6) | various | Inconsistent `@RequiredArgsConstructor` on no-field classes + `@Slf4j` / `@Component` ordering | INFO (cosmetic) | Lombok no-op; pure consistency. IN-01 + IN-02 from 75-REVIEW. |
| `BackupImportService.java` | 644-646 | `restoreOneTable` returns 0 silently when ZIP entry is missing for a known entity | INFO | Phase 73 export contract guarantees one entry per entity, so the silent-skip path is unreachable today. IN-03 from 75-REVIEW. |
| `application.yml` | 6 | `import-backups-dir` is NOT profile-isolated; `<ts>` is second-resolution → two parallel imports in same second collide | INFO | Single-operator app; collision requires two admins clicking Confirm in the same wall-clock second. IN-04 from 75-REVIEW. |

**Blocker classification:** No findings from 75-REVIEW.md are re-classified as Verifier-Blockers. The two CR-class items (Map.copyOf order-strip + Step-1-revert FileAlreadyExistsException) are reviewer-Critical for code-quality reasons but DO NOT prevent any of the 5 ROADMAP Success Criteria from being met:

- CR-01: The audit row's JSON column is non-empty and contains all 24 keys (SC1 requirement); ordering is not in any ROADMAP SC text.
- CR-02: The DB-side success path (SC1, SC2, SC3) is unaffected; SC4 requires "uploads-old/ exists after a successful import" which still holds — the broken path is only on a Step-2 failure (already an exceptional state). Plan 07's BackupImportPostCommitIT covers the Step-2-fail-revert scenario and passes today, meaning the rare-but-plausible edge case is in the test net but the listed mitigation (defensive orphan-sweep) is not yet shipped.

Per `verification-overrides.md` policy: these are real code-quality findings that the orchestrator MAY choose to fold into Phase 75 closure or defer to a follow-up. Verifier neither blocks the phase nor enforces fix. Both are documented for orchestrator decision.

**Debt marker gate:** No `TBD`/`FIXME`/`XXX` markers introduced in Phase 75 files (orchestrator confirmed BUILD SUCCESS includes the project's normal debt-marker checks; spot-greps on the modified files show only "TODO" in comments cross-referencing future phases — not unresolved debt).

### Human Verification Required

#### 1. QUAL-03 — Live MariaDB Saison-2023 visual UAT (per 75-HUMAN-UAT.md)

**Test:** Follow the Execution Workflow section in `.planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-HUMAN-UAT.md`:

1. `docker compose up -d db` (local MariaDB)
2. `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo`
3. Capture 6 vor-Import screenshots in `.screenshots/75/before/` (standings R/A, standings R/B, driver ranking, playoff bracket, sub-team breakdown, driver breakdown — both desktop variants where applicable)
4. Admin → Backup → Export → download ZIP
5. Admin → Backup → Import → upload → Preview → acknowledge → Confirm
6. Wait for D-15 #1 success flash
7. Capture 6 nach-Import screenshots with identical filenames in `.screenshots/75/after/`
8. Visual diff each pair; tick PASS/FAIL + free-text notes in 75-HUMAN-UAT.md
9. Run 4 operational checks: `ls -la data/.import-backups/` (uploads-old present), `SELECT id, executed_at, success, source_filename FROM data_import_audit ORDER BY executed_at DESC LIMIT 1;` (success=TRUE), `SELECT table_counts_restored FROM data_import_audit ORDER BY executed_at DESC LIMIT 1;` (24 keys, non-zero), browser inspection of D-15 #1 flash on /admin/backup
10. Sign off operator + date + overall PASS at the bottom of 75-HUMAN-UAT.md

**Expected:** All 6 visual diffs match (position order, point totals, team/driver assignments, playoff bracket structure unchanged); all 4 operational checks PASS.

**Why human:** QUAL-03 is by definition a visual-verification requirement on live MariaDB. Automation (BackupImportMariaDbSmokeIT) covers row-count parity but cannot certify HTML-rendered byte-identity of standings + playoff brackets + phase breakdowns. Memory `feedback_e2e_verification.md` + `feedback_auto_uat_reminder.md` apply.

#### 2. Plan 08 Task 3 — Operator visual check of D-15 #1 success flash on /admin/backup

**Test:** Per the `how-to-verify` block in Plan 08 Task 3:

1. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`
2. Manually export a backup via Admin → Backup → Export
3. Re-upload via Admin → Backup → Import → Preview → Confirm
4. Visually assert: success flash text matches "Import completed. {N} rows restored across {M} tables.", {N}/{M} are reasonable non-zero non-null values, no stack-trace banner
5. Save before+after screenshots in `.screenshots/75/`
6. `SELECT * FROM data_import_audit ORDER BY executed_at DESC LIMIT 1;` via H2 console → success=true, populated table_counts_restored JSON

**Expected:** Flash renders correctly with English D-15 #1 text; audit row written; no UI regression vs. Phase 74 preview shell.

**Why human:** Plan 08 itself flagged this as `autonomous: false` with a `gate: blocking` `checkpoint:human-verify` task. The Playwright E2E (`BackupImportE2ETest.givenValidBackupZip_whenAdminClicksConfirm_thenSuccessFlashRenderedOnAdminBackup`) covers the assertion programmatically, but the plan explicitly carved out operator-visual signoff as a release gate. Plan 08 SUMMARY exists but does not record an operator approval. The orchestrator should route this checkpoint to the user before phase-close.

### Gaps Summary

No hard gaps found. The codebase fully implements all artifacts, key links, and observable truths required by the 10 plans + ROADMAP success criteria for Phase 75. `./mvnw verify` is BUILD SUCCESS (1402 unit + 216 IT + 6 skipped under documented `@EnabledIfSystemProperty(docker.available)`).

The only outstanding work is operator-driven HUMAN-UAT (QUAL-03) and the Plan 08 visual checkpoint. Both are by-design human gates, not automation gaps. The 75-HUMAN-UAT.md scaffold is in place and ready for the operator session.

Two reviewer-Critical anti-patterns (Map.copyOf order strip + Step-1-revert FileAlreadyExistsException) are surfaced for orchestrator decision per the prompt's instruction. Neither blocks any of the 5 ROADMAP SC contracts; both are auditability/edge-case robustness improvements. The orchestrator MAY fold them into Phase 75 closure as a quick follow-up PR or defer to a v1.10-cleanup phase.

---

_Verified: 2026-05-14T13:57:31Z_
_Verifier: Claude (gsd-verifier)_
