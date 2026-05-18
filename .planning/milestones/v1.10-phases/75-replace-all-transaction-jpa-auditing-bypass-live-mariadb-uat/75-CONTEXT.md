# Phase 75: Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

The **write-side** of the backup import. Phase 74 shipped a no-write preview + a stub-execute that re-validates the staged ZIP and flashes a placeholder. Phase 75 replaces that stub with the real wipe + restore transaction, post-commit `uploads/`-tree replacement, audit-row write, mid-restore-rollback IT, and a live MariaDB UAT on the Saison-2023 fixture.

The single `@Transactional` boundary owns:

1. `UPDATE teams SET parent_team_id = NULL` self-FK decoupling pre-step.
2. Native-SQL `DELETE FROM <table>` in FK-reverse order via `EntityManager.createNativeQuery(...)` over all 24 operative entities (TRUNCATE is forbidden — MariaDB auto-commits and breaks the rollback contract; ROADMAP §Phase-75 Goal locked).
3. `em.flush() + em.clear()` to drop the L1 cache between wipe and restore.
4. Bulk-restore via `JdbcTemplate.batchUpdate(...)` with 24 hand-written `INSERT INTO ... VALUES (?,?,…)` templates — bypasses `AuditingEntityListener` (`BaseEntity.java:15`) so imported `created_at` / `updated_at` survive verbatim.
5. Two-pass `Team`-restore: pass 1 inserts every team with `parent_team_id = NULL`, pass 2 `UPDATE`s `parent_team_id` from the imported JSON tree (so sub-team rows can reference parents that the same pass just inserted).

Post-commit (outside the JPA transaction):

6. Atomic-move-triple of the `uploads/` tree (alt → `uploads-old`, staged → `uploads`, then audit success-row write), see D-09 below.
7. Staging-ZIP cleanup (`BackupImportService.deleteStagingFile` from Phase 74 D-19).

**Out of scope** (Phase 76+): `ImportLockService` `ReentrantLock` + read-only banner + 503-rejector `@ControllerAdvice` (Phase 76, SECU-05..07); `auto-backup-before-import` synchronous export (Phase 76, SECU-07); `BackupRoundTripIT` SHA-256 hash assertion on H2 + MariaDB CI workflow (Phase 77, QUAL-02); README + WIKI "Backup & Restore" documentation (Phase 77, QUAL-05); JaCoCo final coverage gate hold (Phase 77, QUAL-01).

</domain>

<decisions>
## Implementation Decisions

### Audit-Row TX Scope (resolves how `success=false` survives a wipe-rollback)

- **D-01:** **`@Transactional(propagation = REQUIRES_NEW)` on a dedicated `DataImportAuditService.recordResult(...)` method.** ROADMAP-Goal-3 requires a `success=false` audit row to persist after a mid-restore failure — a same-TX audit write would be rolled back together with the wipe. REQUIRES_NEW is invoked from BOTH the success path (post-batch-restore, before `tx.commit()` returns) and the catch-block in `BackupImportService.execute(...)`. The new service lives at `org.ctc.backup.audit.DataImportAuditService` (companion to the existing `DataImportAuditRepository` from Phase 72).
- **D-02:** **Audit row fields captured in both success and failure paths:** `id` (UUID), `executedAt` (set by service, NOT by `AuditingEntityListener` — the entity deliberately skips `BaseEntity` per Phase 72 PROJECT §"Audit log persistence"), `executedBy` (`SecurityContextHolder.getContext().getAuthentication().getName()` on prod/docker, `"dev"` literal on dev/local — mirrors v1.8 audit pattern), `schemaVersion` (from manifest), `tableCountsWiped` JSON (filled only on success — empty `{}` on failure-before-wipe), `tableCountsRestored` JSON (filled only on success — empty `{}` on failure), `sourceFilename` (from `MultipartFile.getOriginalFilename()` carried via staging UUID), `success` (`true` only when DB-commit succeeded AND audit-row write succeeded; `false` on any exception path).
- **D-03:** **Failure-row stack-trace excerpt is NOT a DB column.** The full stack trace is written via `log.error("Import failed for staging-id {}: ", stagingId, e)` at SLF4J ERROR level. Phase 72 V7 schema has no `failure_stack` / `error_message` column; adding one would be a Flyway V8 migration which the ROADMAP scopes to Phase 75 explicitly as "zero Flyway migrations". Operator reads the SLF4J log if they need the trace; the audit row tells them WHICH import failed (timestamp + sourceFilename).

### Restore-SQL Strategy (resolves how 24 INSERTs are generated)

- **D-04:** **24 hand-written `INSERT INTO ...` SQL templates, one per entity, organized as a small `EntityRestorer` interface with 24 implementations.** Reflection-from-JPA-Metamodel was rejected because (a) the Jackson-tree → JDBC-type coercion layer (LocalDateTime, UUID, BigDecimal, enums, embeddable composite keys) would be its own non-trivial subsystem, and (b) `Team.parentTeam` needs a 2-pass NULL-then-UPDATE flow that reflection-only doesn't model cleanly. Per-entity classes are debuggable, the SQL is explicit in code review, and schema-drift cost (24 file edits on a new column) is acceptable for a backup feature touched once per milestone.
- **D-05:** **Layout: `org.ctc.backup.restore.entity.<Entity>Restorer` (24 files).** Each implements `interface EntityRestorer<T> { String tableName(); String insertSql(); BatchPreparedStatementSetter setter(List<JsonNode> rows); }`. The orchestrator (`BackupImportService.restore(...)`) iterates `BackupSchema.getExportOrder()`, looks up the matching `EntityRestorer` from a `Map<String, EntityRestorer<?>>` bean (Spring-discovered via `@Component`), reads the JSON array stream from the staged ZIP, splits into batches of **500** rows, and calls `jdbcTemplate.batchUpdate(restorer.insertSql(), restorer.setter(batch))`.
- **D-06:** **`TeamRestorer` is the only 2-pass implementation.** Pass-1 SQL: `INSERT INTO teams (id, name, short_name, ..., parent_team_id) VALUES (?, ?, ?, ?, NULL)` — `parent_team_id` is hard-coded NULL regardless of source. Pass-2 SQL: `UPDATE teams SET parent_team_id = ? WHERE id = ?`, executed after the entire `data/teams.json` was pass-1-inserted. The 2-pass discipline is hidden inside `TeamRestorer.restore(...)` so the orchestrator stays generic.
- **D-07:** **Batch size = 500 rows per `JdbcTemplate.batchUpdate(...)` call.** Saison-2023 fixture has ~1000 race-results + ~500 race-lineups + sub-100 for everything else — 500 fits in a single batch for most entities and gives 2 round-trips for the biggest. JDBC driver `rewriteBatchedStatements=true` (already in `application-{local,docker,prod}.yml`) keeps the wire-protocol efficient. Planner can tune if profiling shows a hot path; this is the starting default, not a forever-constant.
- **D-08:** **Type-coercion lives in `EntityRestorer`-internal setters.** Each `setter()` calls `ps.setLong(1, row.get("id").asLong())`, `ps.setString(2, row.get("name").asText())`, `ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.parse(row.get("created_at").asText())))`, etc. No shared `JsonNodeRowMapper` utility class — the per-entity nature of the templates means coercion lives next to the SQL. UUIDs are stored as `BINARY(16)` per existing V1 schema → `UUID.fromString(...)` + `setBytes(...)` with `(uuid.getMostSigBits, uuid.getLeastSigBits)` packing. **Open for the planner:** verify the existing `UUIDByteConverter`-or-equivalent helper from `org.ctc.domain.model.*` is reusable; if not, add `org.ctc.backup.restore.UuidPacker` as a tiny utility.

### Uploads-Restore Failure Recovery (resolves D-09 of ROADMAP-Goal-4)

- **D-09:** **Strict 3-step atomic-move sequence with step-by-step logging and best-effort revert on Step-2 failure.** Step numbering and behavior:
  1. `Files.move(uploads, importBackupDir.resolve("uploads-old"), ATOMIC_MOVE)` — log `info` before + after; on failure throw `UploadsRestoreException` BEFORE the DB-commit attempt completes (handled by the same catch-block that writes audit `success=false`). This step is the LAST action inside the @Transactional method's try-block, but the actual move runs AFTER the JPA `tx.commit()` (file-system mutations cannot be enrolled in the JPA tx — Phase 75 ROADMAP-Goal §"file-system mutations are NOT in the JPA transaction").
  2. `Files.move(stagedUploadsDir, uploads, ATOMIC_MOVE)` — log `info` before + after; on failure attempt `Files.move(importBackupDir.resolve("uploads-old"), uploads, ATOMIC_MOVE)` to revert step 1, log `warn` if revert succeeds, log `error` if revert ALSO fails (rare double-failure case — Loud Flash with concrete recovery path). Throw `UploadsRestoreException` regardless.
  3. `DataImportAuditService.recordResult(success=true, ...)` (REQUIRES_NEW per D-01) — Failure at this step logs ERROR but does NOT trigger a step-1 revert (files are already in their target state; missing audit row is a soft-failure that the operator sees via flash + log).
- **D-10:** **No preflight, no auto-revert beyond Step-2.** Preflight (check write permissions / disk space before commit) was considered and rejected — race-condition window between preflight and real move would still exist, and the existing `FileStorageService.validate()` doesn't preflight either. The audit row's `success=true` ONLY signals "DB-row-count delta matches the manifest"; the operator interprets a missing `uploads/` content via the manual-recovery instructions in the Loud Flash (which name `data/.import-backups/<ts>/uploads-old/` and `<staged uploads location>` explicitly).
- **D-11:** **`<ts>` timestamp format is `ISO_INSTANT` truncated to seconds + `Z` suffix.** Example directory: `data/.import-backups/2026-05-14T17-23-09Z/uploads-old/`. Generated by `Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-")` (colon-to-dash because Windows-incompatible paths are a future-proofing courtesy even though CTC runs on Linux/macOS). The same `<ts>` is reused for Phase 76 SECU-07's `auto-backup-before-import.zip` co-location.
- **D-12:** **Staged uploads tree is extracted to `data/.import-backups/<ts>/uploads-new/` during the `@Transactional` wipe+restore method, BEFORE the post-commit move.** Extraction reuses Phase 74's `BackupArchiveService` reader infrastructure (`PathTraversalGuard` validation per entry — Phase 74 D-11). On wipe-or-restore rollback, the partially-extracted `uploads-new/` is deleted in a `finally` block (or left for forensic inspection? — see Claude's Discretion below). 24-hour retention applies to `uploads-old/` only; `uploads-new/` is consumed by Step-2 and disappears unless the import failed.

### Mid-Restore-Failure Test Injection (resolves how `BackupImportRollbackIT` triggers the failure)

- **D-13:** **Test-only `RestoreFailureInjector` interface, default no-op `NoopRestoreFailureInjector` `@Primary` in main, test-scope override.** Interface lives in `org.ctc.backup.restore` (production package — it's an extension point, not test-only code). Single method: `void maybeFailAt(String tableName, int rowIndex)`. The `BackupImportService.restore(...)` calls it inside the batch loop after every 50 rows (cheap, predictable). Default impl is empty. The IT registers a `@TestConfiguration` that provides a `FailAtTableInjector(String targetTable, int targetRowIndex)` bean that throws `RestoreFailureSimulatedException` when its targets match — covers "fail at 50% of the largest table". This pattern avoids `Mockito.spy(jdbcTemplate)` brittleness and stays observable in production code (the injection point is a method call, not AOP magic).

### Service Layout (resolves where the wipe+restore code lives)

- **D-14:** **Extend `BackupImportService` with a new `execute(UUID stagingId)` method — no new service class.** Phase 74 D-19 explicitly anchored this extension point. The new method is `@Transactional` (default propagation = `REQUIRED`, isolation = `READ_COMMITTED`). It delegates to package-private helpers in the same class for: wipe (`#wipeAllTables()`), restore (`#restoreAll(Map<String, Path> entityJsonPaths)`), and uploads-extract (`#extractStagedUploads(Path zip, Path destDir)` — extraction lives in `BackupArchiveService` actually, the helper just orchestrates). The post-commit move-triple lives in a `@TransactionalEventListener(phase = AFTER_COMMIT)` listener (or a manual `TransactionTemplate.executeAfterCommitCallback`-style — planner picks the cleanest Spring 6.x idiom). REQUIRES_NEW audit calls happen inside the catch-block (failure path) and from the post-commit listener (success path).
- **D-15:** **`BackupController` `POST /admin/backup/import-execute` is upgraded from Phase 74's validation-stub to the real execute call.** The Phase 74 D-08 stub-flash (`"Validation succeeded. Import execution will be enabled in Phase 75."`) is removed. New flash messages (English, locked terse style per Phase 74 D-01/D-02):
  1. Success: `Import completed. {restored} rows restored across {entities} tables.`
  2. Failure (any exception path — wipe-rollback OR uploads-move-fail): `Import failed and was rolled back — see logs. Audit-id: {auditUuid}.`
  3. Uploads-restore-soft-fail (DB succeeded, files reverted to pre-import state): `Import database succeeded but uploads restore failed and was reverted. See logs. Audit-id: {auditUuid}.`

### HUMAN-UAT Scope (resolves QUAL-03)

- **D-16:** **`75-HUMAN-UAT.md` with screenshot-pair checklist on local MariaDB + 1 `BackupImportMariaDbSmokeIT` for CI coverage.** Two-layer verification:
  - **Human layer:** `75-HUMAN-UAT.md` lists 5-7 vor-/nach-Import screenshot pairs on Saison 2023:
    1. `/seasons/2023` Standings (Phase=REGULAR, Group=A)
    2. `/seasons/2023` Standings (Phase=REGULAR, Group=B)
    3. `/seasons/2023` Driver Ranking (default phase)
    4. `/seasons/2023/playoff` PLAYOFF bracket
    5. `/teams/<sub-team>` Phase Breakdown (Saison-2023-Group-A sub-team)
    6. `/drivers/<top-driver>` Phase Breakdown (any Saison-2023 driver with results across multiple phases)
    Screenshots land in `.screenshots/75/before/` and `.screenshots/75/after/`. Manual sight check that position order + point totals + group split + driver-team assignment are byte-identical post-import. Operator records PASS/FAIL + free-text in `75-HUMAN-UAT.md`.
  - **CI layer:** `BackupImportMariaDbSmokeIT` (Failsafe IT) runs against the `mariadb-migration-smoke.yml` workflow (analog to v1.9 D-22 pattern) with `@ActiveProfiles("local")` + Testcontainers-MariaDB (planner verifies the exact incantation that matches the v1.9 setup — `local` profile may need a Testcontainers override). Test scenario: seed Saison-2023 fixture → export → wipe → import → assert per-entity row counts equal pre-export counts for all 24 entities. **Out of scope for this IT:** SHA-256 hash byte-equality on sample entities — that is Phase 77's QUAL-02 `BackupRoundTripIT`, scoped distinctly so a Phase 75 failure points at the replace-all path, not at hash-coverage.

### UI / Confirm-Page Carry-Forward (Phase 74 → Phase 75 seam)

- **D-17:** **No new templates. `admin/backup-confirm.html` (Phase 74) survives unchanged; its `Confirm` submit POSTs to the same `/admin/backup/import-execute` endpoint that Phase 74 stubbed.** The `BackupImportConfirmForm.acknowledged @AssertTrue Boolean` validator still gates the request server-side (Phase 74 D-10). The flash-target redirect changes (success/fail strings per D-15), and the templates' Thymeleaf does NOT need to know — `admin/backup.html` just renders `${successMessage}` / `${errorMessage}` like every other admin page.
- **D-18:** **Audit-history page is OUT of scope.** Phase 74 deferred-ideas listed `/admin/backup/history` as v1.11+ work; Phase 75 surfaces audit-id in error flash strings (D-15) so the operator can `SELECT * FROM data_import_audit WHERE id = ?` directly, but no UI page is added.

### Claude's Discretion

- Exact location of `BackupSchema.getWipeOrder()` — either a new public method on `BackupSchema` (cleanest, lives next to `getExportOrder()`) or inline `Lists.reverse(schema.getExportOrder())` in `BackupImportService`. Planner picks; no observable difference.
- Whether `uploads-new/` (D-12 staging area) is deleted on rollback or retained for forensic inspection. Default recommendation: delete on rollback in `finally{}` to keep `data/.import-backups/<ts>/` clean — failed imports leave only the (empty? un-renamed?) `uploads-old/`. Planner re-evaluates if there's an operational reason to keep.
- Spring 6.x idiom for the post-commit move-triple — `@TransactionalEventListener(phase=AFTER_COMMIT)` is one option, `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization { afterCommit })` is another, manual `executeWithoutResult` + AOP-aware split is a third. Planner picks the cleanest; success criterion is that the move happens AFTER tx-commit, not inside the @Transactional method body.
- `BackupImportMariaDbSmokeIT` test-class location — package `org.ctc.backup.it` (mirrors `dataimport.it`) vs `org.ctc.backup` (flat). Planner picks per `.planning/codebase/TESTING.md`.
- Whether `EntityRestorer` is `interface` or `sealed interface` (Java 25 sealed-with-24-permits is over-engineering for a feature touched once per milestone). Default recommendation: plain `interface`.
- `BatchPreparedStatementSetter` flavor — `BatchPreparedStatementSetter` (single batch) vs `ParameterizedPreparedStatementSetter` + `JdbcTemplate.batchUpdate(sql, args, 500, setter)` (auto-chunking). Planner picks; D-07's batch-size constant maps directly to the auto-chunking flavor.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v1.10 milestone foundation

- `.planning/ROADMAP.md` §"Phase 75" — Goal text (locks single-`@Transactional`, native-DELETE-FK-reverse, `em.flush+clear`, `JdbcTemplate.batchUpdate` auditing-bypass, `Team.parentTeam=NULL` pre-step, post-commit stage-and-rename, 24h `uploads-old/` retention, `BackupImportRollbackIT` 50% injection, Saison-2023 MariaDB UAT). 5 success criteria; this CONTEXT does not override any of them.
- `.planning/REQUIREMENTS.md` §IMPORT-05 / IMPORT-06 / IMPORT-07 / QUAL-03 — Acceptance criteria. **NOTE:** German UI strings in IMPORT-04 (carry-forward from Phase 74) are overridden by Phase 74 CONTEXT D-01/D-02; D-15 of THIS CONTEXT supplies the final English flash strings for Phase 75's success/failure/soft-fail paths.
- `.planning/PROJECT.md` §"Backup Wire Contract (v1.10)" — Invariants 1-4 (integer SCHEMA_VERSION = 1, manifest-first ZIP, 24-entity scope via JPA-Metamodel topo-sort, ObjectMapper isolation). Phase 75 reads `BackupSchema.getExportOrder()` REVERSED for wipe-order, FORWARD for restore-order.
- `.planning/PROJECT.md` §"Audit log persistence" — `DataImportAudit` is Lombok-`@Entity`-NOT-record, deliberately does NOT extend `BaseEntity` (so Phase 75 writer fully controls `executedAt` without `AuditingEntityListener` interference). V7 columns use `LONGTEXT` for the JSON-shape text fields `table_counts_wiped` and `table_counts_restored`.

### Prior-phase context (mandatory carry-forward)

- `.planning/phases/72-backup-wire-contract-schema-manifest-objectmapper-audit-log-/72-CONTEXT.md` — `BackupSchema.SCHEMA_VERSION = 1`, `EXPORT_ORDER` via JPA-Metamodel Kahn topo-sort, `backupObjectMapper` strict mapper, `DataImportAudit` Lombok-entity (not record), V7 migration with `LONGTEXT` JSON columns. Phase 75 reads only — does not modify.
- `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-CONTEXT.md` — `BackupArchiveService.writeZip` shape, `@EntityGraph`-eager-fetch per `findAllForBackup()`, 24 Jackson MixIns under `org.ctc.backup.serialization`. Phase 75 reads the corresponding `data/<entity>.json` arrays back; MixIn rules govern the on-disk JSON shape that Phase 75's `EntityRestorer` setters MUST coerce back to JDBC parameters.
- `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` — `BackupImportService.stage / reparse / deleteStagingFile` (D-19), `BackupArchiveService.readManifest / countDataEntries / countUploadFiles` (D-20), `BackupImportPreview` + `BackupImportConfirmForm` DTOs (D-21), `POST /admin/backup/import-execute` validation-stub (D-08 — Phase 75 upgrades this to the real execute), `PathTraversalGuard` per-entry validation (D-11), `BackupArchiveException` reject paths, staging-file lifecycle (D-15..D-18). All ZIP-Slip / ZipBomb / Multipart-Limit / Schema-Version-Gate work is carried forward unchanged.
- `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VERIFICATION.md` (when written) — Phase 74's BackupImportE2ETest cookie-jar proof that the preview→confirm→execute path is stateless. Phase 75 inherits the stateless contract.

### Existing code Phase 75 references (mix of reuse + extend)

- `src/main/java/org/ctc/domain/model/BaseEntity.java:15` — `@EntityListeners(AuditingEntityListener.class)`. This is exactly the listener that `JdbcTemplate.batchUpdate` BYPASSES (the whole point of the bypass strategy — restored entities' `created_at` / `updated_at` survive verbatim from the export JSON).
- `src/main/java/org/ctc/backup/service/BackupImportService.java` — Phase 74 service. Phase 75 ADDS `execute(UUID stagingId)` per D-14; existing `stage` / `reparse` / `deleteStagingFile` are UNTOUCHED.
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java` — Phase 73 + 74 archive service. Phase 75 ADDS `void extractUploadsTo(Path zip, Path destDir)` (streaming extraction of `uploads/*` entries with `PathTraversalGuard` per-entry validation reused). Does NOT modify `writeZip` / `readManifest` / `countDataEntries`.
- `src/main/java/org/ctc/backup/BackupController.java` — Phase 74 controller. Phase 75 REPLACES the stub-execute logic inside the `POST /admin/backup/import-execute` handler with a delegation to `BackupImportService.execute(stagingId)`. New flash strings per D-15. Endpoint URL UNCHANGED.
- `src/main/java/org/ctc/backup/audit/DataImportAudit.java` — Phase 72 Lombok entity. Phase 75 instantiates it (sets all fields manually since `BaseEntity` is deliberately skipped per Phase 72 PROJECT §"Audit log persistence").
- `src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java` — Phase 72 repository. Phase 75 calls `save(...)` from the new `DataImportAuditService` (D-01).
- `src/main/java/org/ctc/backup/schema/BackupSchema.java` — Phase 72 schema. Phase 75 reads `getExportOrder()` REVERSED for wipe and FORWARD for restore. **Open for planner:** add `getWipeOrder()` convenience method (Claude's Discretion above).
- `src/main/java/org/ctc/backup/security/PathTraversalGuard.java` — Phase 74 guard. Phase 75's uploads-extraction step (`BackupArchiveService.extractUploadsTo`) reuses the same `Paths.resolve(name).normalize().startsWith(destDir.toRealPath())` idiom for every `ZipEntry`.
- `src/main/resources/application-local.yml` + `application-docker.yml` — JDBC URL with `rewriteBatchedStatements=true` (already set — verify in planning). Critical for D-07's batch-size of 500 to actually compile into a multi-row INSERT on the wire.
- `src/main/resources/application.yml` — `app.backup.staging-dir`. Phase 75 ADDS a sibling property `app.backup.import-backups-dir` (default `data/.import-backups/`) so the directory location is configurable per profile.
- `src/main/resources/templates/admin/backup-confirm.html` — Phase 74 template. Phase 75 does NOT modify — the same `Confirm` submit lands on the now-real execute endpoint per D-17.

### Existing code Phase 75 references but does NOT modify

- `src/main/java/org/ctc/domain/repository/*.java` — 24 repositories. Phase 75 deliberately does NOT use `repo.deleteAll()` / `repo.saveAll(...)` (would invoke `AuditingEntityListener` and break the timestamp-preservation contract). Repositories are READ-ONLY in this phase — used only for the audit row counts on success.
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — v1.8 staging-path pattern. Phase 75 inherits the same stateless invariant (UUID in form, re-read on execute) carried over from Phase 74 D-18. CSV import is NOT touched.
- `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — Phase 74 added the `MaxUploadSizeExceededException` mapping. Phase 75 ADDS one more `@ExceptionHandler(UploadsRestoreException.class)` mapping that returns redirect-flash to `/admin/backup` with D-15-strings — and one for `RestoreFailureSimulatedException` (test-only path; in main it manifests as a generic `Exception` caught by the import service). **Planner judgment call:** maybe these don't need their own handlers if `BackupImportService.execute(...)` already catches and flashes — Global handler then only fires if catching is bypassed (defensive default).

### Test infrastructure (mandatory reading)

- `.planning/codebase/TESTING.md` §"Failsafe IT split" + §"@SpringBootTest profile policy" — Phase 75 IT location, Testcontainers-MariaDB invocation, `@ActiveProfiles` discipline.
- `.planning/codebase/STACK.md` §"DB profiles" — H2 vs MariaDB dialect divergence reference; especially the FK-DDL / `BINARY(16)` UUID storage / `LONGTEXT` JSON-text column discussion.
- `src/test/java/org/ctc/.../TemplateRenderingSmokeIT.java` (Phase 71) — Reference pattern for dynamic-test factory; Phase 75's `BackupImportRollbackIT` does NOT need this pattern (single scenario), but `BackupImportMariaDbSmokeIT` follows the same `@SpringBootTest(properties=...) + @ActiveProfiles("local")` shape.
- `.github/workflows/mariadb-migration-smoke.yml` — v1.9 MariaDB-CI-smoke workflow. Phase 75's `BackupImportMariaDbSmokeIT` plugs into this workflow (planner verifies the workflow file path is exact; the v1.9 D-22 pattern is the precedent).

### Project conventions (mandatory reading)

- `CLAUDE.md` §"Architectural Principles" — Controllers thin, DTOs in controllers (`BackupImportConfirmForm` already exists), no fallback calculations, no inline styles, do-not-modify-Flyway-migrations (Phase 75 has zero new migrations — V7 from Phase 72 is sufficient).
- `CLAUDE.md` §"Constraints" — Coverage ≥ 82 % (Phase 75 adds significant code volume — 24 restorer classes + the orchestrator + the audit service + 3 ITs; planner monitors JaCoCo proactively), OSIV active (read-side queries that the audit-row counts use stay simple).
- `CLAUDE.md` `feedback_test_data_isolation.md` — Saison 2023 IS the demo data (intentional, ROADMAP-locked); does NOT need T-prefix isolation because it's the ROADMAP-mandated UAT fixture.
- `CLAUDE.md` `feedback_no_inline_styles.md` — D-17 carry-forward (no new templates anyway).
- `CLAUDE.md` `feedback_e2e_verification.md` — Final `./mvnw verify -Pe2e` BUILD SUCCESS is the Phase 75 verification gate (NOT Phase 77's milestone-final gate — Phase 75 must already pass on its own).
- `.planning/codebase/CONVENTIONS.md` §"flash attributes" — `successMessage` / `errorMessage` keys are reused; D-15 strings populate these.

### External APIs (consulted, not on-disk)

- Spring `JdbcTemplate.batchUpdate(String sql, BatchPreparedStatementSetter pss)` — single-batch API.
- Spring `JdbcTemplate.batchUpdate(String sql, Collection<T> batchArgs, int batchSize, ParameterizedPreparedStatementSetter<T> pss)` — auto-chunking API (D-07's natural fit).
- Spring `@Transactional(propagation = Propagation.REQUIRES_NEW)` — D-01's audit-row mechanism.
- Spring `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` — one candidate idiom for D-14's post-commit move-triple.
- `java.nio.file.Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)` — D-09's rename API. Atomic-move is a same-filesystem requirement; cross-filesystem falls back to copy-then-delete (not atomic).
- MariaDB `SET FOREIGN_KEY_CHECKS = 0` — explicitly NOT used. The FK-reverse-DELETE-order strategy works without disabling constraints; disabling would mask FK-bugs in the export data.
- MariaDB `SET autocommit = 0` — automatically managed by Spring's `@Transactional`; no manual override needed.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`BackupSchema.getExportOrder()` (Phase 72)** — Single source of truth for the 24-entity order. Phase 75 reverses for wipe, uses forward for restore. Schema-drift-resistant via JPA-Metamodel-runtime-topo-sort.
- **`BackupArchiveService` (Phase 73 + 74)** — Reader infrastructure already exists; Phase 75 adds `extractUploadsTo(...)` next to it. Same `@Qualifier("backupObjectMapper")` injection, same `PathTraversalGuard` defense.
- **`PathTraversalGuard` (Phase 74)** — Validates every `ZipEntry.getName()` against destination realpath. Phase 75's uploads-extraction reuses verbatim.
- **`BackupImportService.stage / reparse / deleteStagingFile` (Phase 74)** — Staging-file lifecycle is intact. Phase 75's `execute(UUID stagingId)` is the missing fourth method; the staging-file is deleted by `execute` on success path (not by Phase 74's `deleteStagingFile` — that's the Cancel-button path).
- **`BackupImportConfirmForm.acknowledged @AssertTrue` (Phase 74)** — Server-side checkbox gate. Phase 75 keeps the contract; the controller delegates to the new execute method only after `BindingResult.hasErrors() == false`.
- **`DataImportAudit` entity + `DataImportAuditRepository` (Phase 72)** — Audit-row persistence layer. Phase 75 ADDS the orchestrating `DataImportAuditService` (D-01) on top.
- **`backupObjectMapper` qualifier (Phase 72)** — `FAIL_ON_UNKNOWN_PROPERTIES=true` is what reads `data/<entity>.json` arrays during restore. Tampered JSON with extra fields → exception → wipe-rollback → success=false audit row.
- **`mariadb-migration-smoke.yml` (v1.9 D-22)** — Existing CI workflow that runs IT on MariaDB. Phase 75's `BackupImportMariaDbSmokeIT` plugs into it (planner verifies workflow file path matches the v1.9 precedent).
- **v1.8 `CsvImportController` staging-path pattern** — Stateless-via-staging-UUID is already in the team's muscle memory; Phase 75 follows it without re-deriving the idiom.

### Established Patterns

- **`@RequiredArgsConstructor` + `@Slf4j`** — `DataImportAuditService`, `BackupImportService` extension, all 24 `EntityRestorer`s follow.
- **DTOs in controllers** — No new form DTO needed; `BackupImportConfirmForm` is reused.
- **`RedirectAttributes`-backed Flash** — D-15 strings, success+failure+soft-fail.
- **`@Transactional` + propagation** — D-01 REQUIRES_NEW for audit; D-14 default REQUIRED for execute.
- **Given-When-Then test naming** — `BackupImportRollbackIT` + `BackupImportMariaDbSmokeIT` + any orchestrator unit tests follow.
- **`@Nested` profile classes for Security ITs** — Not needed in Phase 75 (the security matrix was nailed in Phase 73 `BackupControllerSecurityIT` and is unchanged — the endpoint URL doesn't move).

### Integration Points

- **New package contents (no new packages — extends `org.ctc.backup`):**
  - `org.ctc.backup.audit.DataImportAuditService` (new — D-01, REQUIRES_NEW audit writer)
  - `org.ctc.backup.restore.EntityRestorer` (new interface — D-05)
  - `org.ctc.backup.restore.entity.<Entity>Restorer` (24 new classes — D-06, D-08)
  - `org.ctc.backup.restore.RestoreFailureInjector` + `NoopRestoreFailureInjector` (new — D-13)
  - `org.ctc.backup.exception.UploadsRestoreException` (new — D-09)
  - `org.ctc.backup.exception.RestoreFailureSimulatedException` (test-only, but lives in main package per D-13 extension-point discipline)
  - `org.ctc.backup.restore.UuidPacker` (new utility — D-08 fallback if no existing helper found)
- **Extended classes:**
  - `org.ctc.backup.service.BackupImportService` — adds `execute(UUID stagingId)` per D-14
  - `org.ctc.backup.service.BackupArchiveService` — adds `extractUploadsTo(Path zip, Path destDir)` per D-12
  - `org.ctc.backup.BackupController` — replaces stub-execute with real call per D-15 (URL unchanged)
  - `org.ctc.admin.controller.GlobalExceptionHandler` — optionally adds `UploadsRestoreException` handler (D-15 planner judgment)
  - `src/main/resources/application.yml` — adds `app.backup.import-backups-dir` property
- **No new templates** — D-17 (backup-confirm.html survives unchanged).
- **Security:** unchanged from Phase 74 — profile-conditional auth, CSRF token on the import-execute POST. `BackupControllerSecurityIT` does NOT need extension (URL stable).
- **Tests:**
  - `BackupImportRollbackIT` (Failsafe IT) — exception injection at 50% of largest table per D-13.
  - `BackupImportE2ETest` (Playwright, `-Pe2e`) — Phase 74's test is EXTENDED with a second `@Test` method covering the real execute → success flash. The original Phase 74 stub-flash test is removed (the stub is gone).
  - `BackupImportMariaDbSmokeIT` (Failsafe IT, `@ActiveProfiles("local")`) — Saison-2023 round-trip row-count assertion per D-16.
  - `DataImportAuditServiceTest` (Surefire unit test) — REQUIRES_NEW propagation verified via `@MockitoSpyBean` on the underlying `PlatformTransactionManager` OR via Spring Test's `TransactionTestExecutionListener` (planner picks).
  - 24 `<Entity>RestorerTest` Surefire unit tests — each asserts the `insertSql()` is well-formed AND the setter correctly maps a single sample `JsonNode`. Bulk tests (real batch + real DB) live in the ITs.
- **CSS:** none.

</code_context>

<specifics>
## Specific Ideas

- **`AuditingEntityListener` is the whole reason for the JdbcTemplate detour.** Phase 75's design isn't a performance choice — `repo.saveAll(...)` would be simpler. But `BaseEntity:15` would invoke the listener and stamp `created_at` / `updated_at` to NOW on every restored row, destroying the round-trip contract. `JdbcTemplate.batchUpdate` is the only way to write to those `@Column`s with the exported values. This is THE design driver; every restorer must respect it (no `save()`, no `persist()`, no `entityManager.merge()` anywhere on the restore path).
- **The `Team.parentTeam` 2-pass is irreducible.** Sub-team A's `parent_team_id` must reference Team P's `id`. Both A and P are in `data/teams.json`. Forward-iterating and inserting with `parent_team_id` set would fail when P hasn't been inserted yet — and the JSON file isn't sorted topologically (it's a flat array). Two passes (Pass-1 inserts ALL teams with NULL parent, Pass-2 UPDATEs the FK) is the cleanest solution per D-06. ROADMAP-Goal locks this; CONTEXT confirms.
- **`em.flush() + em.clear()` is non-negotiable between wipe and restore.** Without it, Hibernate's L1 cache thinks all the deleted entities are still managed. The first `JdbcTemplate.batchUpdate` would still work (raw JDBC bypasses the session), but any downstream JPA query in the same tx (e.g., the success-path row-count query for the audit's `tableCountsRestored` JSON) would return stale entities. Goal-text-locked; CONTEXT confirms.
- **`Instant.now().truncatedTo(SECONDS).toString().replace(":","-")` ≠ ISO-8601 strict.** It's almost-ISO-8601 with `T` and `Z` preserved but `:` replaced. This is a deliberate pragmatic choice — Windows-pathable, sortable lexicographically, human-readable. Documented in D-11 so nobody "fixes" it later.
- **`rewriteBatchedStatements=true` matters.** Without it, MariaDB driver emits one round-trip per INSERT, and a 500-row batch suddenly becomes 500 round-trips. Verify in planning that all relevant `application-*.yml` profiles have it set; if not, FIX before merging Phase 75.
- **`UuidPacker` may already exist.** A grep for `UUID.fromString` + `setBytes` in `org.ctc.domain.model` will reveal whether a helper exists. If yes, reuse; if no, add as `org.ctc.backup.restore.UuidPacker`.
- **`mariadb-migration-smoke.yml` is sacred.** v1.9 D-22 invested heavily in getting this workflow stable. Phase 75 PLUGS IN a new test class; it does NOT modify the workflow file beyond adding the test class to the appropriate test-group.

</specifics>

<deferred>
## Deferred Ideas

- **`/admin/backup/history` audit-viewer page** — Renders `data_import_audit` rows in a table with status icons + drill-down to the matching SLF4J log lines. v1.11+ candidate (Phase 74 already deferred this; Phase 75 surfaces audit-id in error flash for SQL drill-down per D-18).
- **`SET FOREIGN_KEY_CHECKS = 0` during wipe** — Considered as a simpler alternative to FK-reverse-DELETE ordering, rejected because (a) MariaDB-only syntax (H2 uses `SET REFERENTIAL_INTEGRITY FALSE`), (b) FK-bugs in the export data would be silently masked, (c) FK-reverse ordering is correct without it. Document the decision; do not implement.
- **`@Scheduled` cleanup of `data/.import-backups/<ts>/uploads-old/` beyond 24h** — Goal text says "24h manual recovery" — the cleanup is operator-driven (`rm -rf` in cron). Phase 75 does NOT add a scheduled cleanup; if operational pain warrants it, v1.11 adds a one-line `@Scheduled` job.
- **SHA-256 hash byte-equality verification on sample entities** — Phase 77 QUAL-02 `BackupRoundTripIT` does this. Phase 75's `BackupImportMariaDbSmokeIT` STOPS at row-count parity (per D-16).
- **README + WIKI "Backup & Restore" section** — Phase 77 QUAL-05 deliverable. Phase 75 writes audit-id into flashes; the user-facing recovery-from-`data/.import-backups/<ts>/` guide is Phase 77.
- **`ImportLockService` + read-only banner + `auto-backup-before-import`** — Phase 76 (SECU-05..07). Phase 75 deliberately ships WITHOUT the lock — the assumption is admin-discipline (single admin, no concurrent imports in practice). Phase 76 adds defense-in-depth.
- **Reflection-based `EntityRestorer`-generator** — Considered for D-04, deferred indefinitely. If a future entity-explosion phase needs it, a v2.0 refactor is on the table.
- **`UploadsRestoreException`-specific `GlobalExceptionHandler` mapping** — D-15 / Claude's Discretion. If the service-internal catch+flash already covers it, the GlobalExceptionHandler doesn't need to know. Defer the decision to the planner's reading of the service code.

</deferred>

---

*Phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat*
*Context gathered: 2026-05-14*
