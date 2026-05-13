# Phase 75: Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT — Research

**Researched:** 2026-05-14
**Domain:** Single-`@Transactional` wipe-and-restore over 24 JPA entities via native-DELETE + `JdbcTemplate.batchUpdate` (bypassing `AuditingEntityListener`) + post-commit upload-tree move-triple + live-MariaDB UAT
**Confidence:** HIGH (locked CONTEXT carries 18 decisions; research fills 13 specific gaps below)
**Researcher:** Claude (gsd-researcher)

## Summary

Phase 75 is the **write-side** of the backup import. The riskiest invariants — single `@Transactional` boundary, native-SQL DELETE in FK-reverse order over 24 tables, L1-cache drop via `em.flush() + em.clear()`, bulk-restore via `JdbcTemplate.batchUpdate` (bypassing `AuditingEntityListener` so imported `created_at`/`updated_at` survive verbatim), `Team.parentTeam=NULL` pre-step, post-commit upload-tree atomic move-triple with 24-hour `uploads-old/` retention, mid-restore `BackupImportRollbackIT` injection at 50 %, and Saison-2023 live MariaDB UAT — are ALL locked by ROADMAP §"Phase 75" and CONTEXT D-01..D-18. Research does not re-decide them. Research closes 13 specific gaps that the planner needs to convert decisions into concrete tasks.

Key findings: (1) **UUID columns are typed `UUID` (portable JDBC type), NOT `BINARY(16)`** — CONTEXT D-08 is wrong; `UuidPacker` is unnecessary, use `ps.setObject(idx, uuid)` directly. (2) **`rewriteBatchedStatements=true` is absent from ALL `application-*.yml` profiles** — CONTEXT claim is wrong; Phase 75 must add the JDBC URL parameter to local/docker/prod. (3) **Spring 6.1+ enforces `REQUIRES_NEW` on `@TransactionalEventListener` + `@Transactional` composition** — the D-01 (REQUIRES_NEW audit) + D-14 (post-commit move-triple) combination is required by the framework, not optional. (4) The `BackupImportService.stage()` already persists `originalFilename` to a `.meta` sidecar file (`upload-<uuid>.zip.meta`) — Phase 75 reads it for the audit `source_filename` field; no in-memory state needed. (5) `BackupSchema.getExportOrder()` topo-sort already excludes the `Team.parentTeam` self-edge (verified in `EntityTopoSorter.java:62`), so the 2-pass restore-flow design (D-06) and FK-reverse DELETE order are valid without surgery to the schema. (6) Saison-2023 is the existing dev-data fixture (`feedback_test_data_isolation.md`: "Saison 2023 IS the demo data — intentional, ROADMAP-locked; does not need T-prefix"). (7) `JdbcTemplate` is on the classpath transitively via `spring-boot-starter-data-jpa` → `spring-boot-starter-jdbc`; no new Maven dependency.

**Primary recommendation:** Use `JdbcTemplate.batchUpdate(String sql, Collection<T> batchArgs, int batchSize, ParameterizedPreparedStatementSetter<T> pss)` for the 24 restorers (idiomatic Spring 6.x, auto-chunking maps directly to D-07 batch-size=500). Use `@TransactionalEventListener(phase=AFTER_COMMIT)` for the post-commit move-triple (Spring 6.x recommended idiom; framework auto-enforces REQUIRES_NEW for any nested `@Transactional` work inside the listener — satisfies D-01 audit-write requirement). Use `ps.setObject(idx, uuid)` for UUID parameters (Hibernate/MariaDB native UUID type, no BINARY(16) packing). Skip `UuidPacker` — add it only if a setter test reveals JDBC-driver friction.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** `@Transactional(propagation = REQUIRES_NEW)` on a dedicated `DataImportAuditService.recordResult(...)` method. ROADMAP-Goal-3 requires a `success=false` audit row to persist after a mid-restore failure — a same-TX audit write would be rolled back together with the wipe. REQUIRES_NEW is invoked from BOTH the success path (post-batch-restore, before `tx.commit()` returns) and the catch-block in `BackupImportService.execute(...)`. The new service lives at `org.ctc.backup.audit.DataImportAuditService` (companion to the existing `DataImportAuditRepository` from Phase 72).

**D-02:** Audit row fields captured in both success and failure paths: `id` (UUID), `executedAt` (set by service, NOT by `AuditingEntityListener`), `executedBy` (`SecurityContextHolder.getContext().getAuthentication().getName()` on prod/docker, `"dev"` literal on dev/local), `schemaVersion` (from manifest), `tableCountsWiped` JSON (filled only on success — empty `{}` on failure-before-wipe), `tableCountsRestored` JSON (filled only on success — empty `{}` on failure), `sourceFilename` (from `MultipartFile.getOriginalFilename()` carried via staging UUID), `success` (`true` only when DB-commit succeeded AND audit-row write succeeded; `false` on any exception path).

**D-03:** Failure-row stack-trace excerpt is NOT a DB column. Full stack trace written via `log.error("Import failed for staging-id {}: ", stagingId, e)` at SLF4J ERROR level. Phase 72 V7 schema has no `failure_stack` / `error_message` column; adding one would be a Flyway V8 migration which the ROADMAP scopes to Phase 75 explicitly as "zero Flyway migrations". Operator reads SLF4J log if they need the trace.

**D-04:** 24 hand-written `INSERT INTO ...` SQL templates, one per entity, organized as a small `EntityRestorer` interface with 24 implementations. Reflection-from-JPA-Metamodel was rejected.

**D-05:** Layout: `org.ctc.backup.restore.entity.<Entity>Restorer` (24 files). Each implements `interface EntityRestorer<T> { String tableName(); String insertSql(); BatchPreparedStatementSetter setter(List<JsonNode> rows); }`. The orchestrator (`BackupImportService.restore(...)`) iterates `BackupSchema.getExportOrder()`, looks up the matching `EntityRestorer` from a `Map<String, EntityRestorer<?>>` bean (Spring-discovered via `@Component`), reads the JSON array stream from the staged ZIP, splits into batches of **500** rows, and calls `jdbcTemplate.batchUpdate(restorer.insertSql(), restorer.setter(batch))`.

**D-06:** `TeamRestorer` is the only 2-pass implementation. Pass-1 SQL: `INSERT INTO teams (id, name, short_name, ..., parent_team_id) VALUES (?, ?, ?, ?, NULL)`. Pass-2 SQL: `UPDATE teams SET parent_team_id = ? WHERE id = ?`, executed after the entire `data/teams.json` was pass-1-inserted.

**D-07:** Batch size = 500 rows per `JdbcTemplate.batchUpdate(...)` call. JDBC driver `rewriteBatchedStatements=true` keeps the wire-protocol efficient. *(CONTEXT claims this is already in local/docker/prod yml — **research found it is NOT** — see §10 below; Phase 75 must add it.)*

**D-08:** Type-coercion lives in `EntityRestorer`-internal setters. *(CONTEXT claims UUIDs are `BINARY(16)` — **research found they are portable `UUID` columns** — see §5 below; no `UuidPacker` needed.)*

**D-09:** Strict 3-step atomic-move sequence with step-by-step logging and best-effort revert on Step-2 failure: (1) `Files.move(uploads, importBackupDir.resolve("uploads-old"), ATOMIC_MOVE)`, (2) `Files.move(stagedUploadsDir, uploads, ATOMIC_MOVE)` with revert-on-failure, (3) `DataImportAuditService.recordResult(success=true, ...)`. Triggered AFTER `tx.commit()`.

**D-10:** No preflight, no auto-revert beyond Step-2.

**D-11:** `<ts>` timestamp format is `ISO_INSTANT` truncated to seconds with `:` → `-`. Example: `data/.import-backups/2026-05-14T17-23-09Z/uploads-old/`. Generated by `Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-")`. Same `<ts>` reused for Phase 76 SECU-07's `auto-backup-before-import.zip`.

**D-12:** Staged uploads tree extracted to `data/.import-backups/<ts>/uploads-new/` during the `@Transactional` wipe+restore method, BEFORE the post-commit move. Extraction reuses Phase 74's `BackupArchiveService` reader infrastructure with `PathTraversalGuard` validation. 24-hour retention applies to `uploads-old/` only.

**D-13:** Test-only `RestoreFailureInjector` interface, default no-op `NoopRestoreFailureInjector` `@Primary` in main, test-scope override. Interface lives in `org.ctc.backup.restore`. Single method: `void maybeFailAt(String tableName, int rowIndex)`. Called inside the batch loop after every 50 rows.

**D-14:** Extend `BackupImportService` with a new `execute(UUID stagingId)` method — no new service class. Phase 74 D-19 explicitly anchored this extension point. Default propagation = `REQUIRED`, isolation = `READ_COMMITTED`. The post-commit move-triple lives in a `@TransactionalEventListener(phase = AFTER_COMMIT)` listener (or a manual `TransactionTemplate.executeAfterCommitCallback`-style — planner picks).

**D-15:** Three flash strings (English, locked terse style):
1. Success: `Import completed. {restored} rows restored across {entities} tables.`
2. Failure: `Import failed and was rolled back — see logs. Audit-id: {auditUuid}.`
3. Uploads-restore-soft-fail: `Import database succeeded but uploads restore failed and was reverted. See logs. Audit-id: {auditUuid}.`

**D-16:** `75-HUMAN-UAT.md` with screenshot-pair checklist on local MariaDB + 1 `BackupImportMariaDbSmokeIT` for CI coverage. Two-layer verification. Six screenshot routes: `/seasons/2023` Standings (Phase=REGULAR, Group=A), Phase=REGULAR Group=B, Driver Ranking, `/seasons/2023/playoff` PLAYOFF bracket, `/teams/<sub-team>` Phase Breakdown, `/drivers/<top-driver>` Phase Breakdown. Screenshots in `.screenshots/75/before/` and `.screenshots/75/after/`. The CI IT runs round-trip seed→export→wipe→import→assert row counts equal pre-export counts. SHA-256 byte-equality is **out of scope** (Phase 77 QUAL-02).

**D-17:** No new templates. `admin/backup-confirm.html` (Phase 74) survives unchanged.

**D-18:** Audit-history page is OUT of scope. Audit-id surfaced via D-15 flash strings only.

### Claude's Discretion

- Exact location of `BackupSchema.getWipeOrder()` — new public method on `BackupSchema` vs. inline `Lists.reverse(schema.getExportOrder())`. Planner picks. *(Recommendation: inline `Lists.reverse(...)` in `BackupImportService` — adding a public method on `BackupSchema` requires a unit-test for the trivial single-line wrapping; the inline call is verified end-to-end by the rollback-IT and round-trip-IT.)*
- Whether `uploads-new/` (D-12 staging area) is deleted on rollback or retained for forensic inspection. *(Recommendation: delete in `finally{}` to keep `data/.import-backups/<ts>/` clean — failed imports leave only the empty `uploads-old/`.)*
- Spring 6.x idiom for the post-commit move-triple. *(Recommendation: `@TransactionalEventListener(phase=AFTER_COMMIT)` — see §2 below; the framework documents this as the canonical post-commit hook in Spring 6.x.)*
- `BackupImportMariaDbSmokeIT` test-class location — `org.ctc.backup.it` vs `org.ctc.backup.service`. *(Recommendation: `org.ctc.backup.service` — mirrors Phase 74 ITs which all live under `.../backup/service/`; no `org.ctc.backup.it` package exists yet.)*
- Whether `EntityRestorer` is `interface` or `sealed interface`. *(Recommendation: plain `interface` — sealed-with-24-permits is over-engineering.)*
- `BatchPreparedStatementSetter` flavor. *(Recommendation locked here: **`ParameterizedPreparedStatementSetter<T>` with auto-chunking** — see §1 below; D-07's `batchSize=500` constant maps directly to the auto-chunking argument.)*

### Deferred Ideas (OUT OF SCOPE)

- `/admin/backup/history` audit-viewer page (v1.11+).
- `SET FOREIGN_KEY_CHECKS = 0` during wipe (rejected — FK-reverse ordering is correct without it, and MariaDB-only syntax breaks H2).
- `@Scheduled` cleanup of `data/.import-backups/<ts>/uploads-old/` beyond 24 h (operator-driven via cron).
- SHA-256 hash byte-equality verification on sample entities (Phase 77 QUAL-02 `BackupRoundTripIT`).
- README + WIKI "Backup & Restore" section (Phase 77 QUAL-05).
- `ImportLockService` + read-only banner + `auto-backup-before-import` (Phase 76 SECU-05..07).
- Reflection-based `EntityRestorer`-generator (deferred indefinitely; Phase 75 hand-writes 24).
- `UploadsRestoreException`-specific `GlobalExceptionHandler` mapping (planner judgment — defer if internal service catch covers it).

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| IMPORT-05 | Replace-all wipe+restore in single `@Transactional`: (a) `Team.parentTeam=NULL` pre-step, (b) FK-reverse `EntityManager.createNativeQuery("DELETE FROM ...")`, (c) `em.flush() + em.clear()`, (d) `JdbcTemplate.batchUpdate` restore bypassing `AuditingEntityListener`, (e) audit row | §1 JdbcTemplate batch idiom, §3 EM cache-flush semantics, §4 FK-reverse order validity, §11 BackupSchema topo-sort already excludes self-FK, §5 UUID column-type binding |
| IMPORT-06 | Post-commit upload-tree restore via stage-and-rename. Old tree retained at `data/.import-backups/<ts>/uploads-old/` for 24 h | §2 `@TransactionalEventListener(AFTER_COMMIT)` idiom + REQUIRES_NEW interaction, §8 `originalFilename` carried via `.meta` sidecar |
| IMPORT-07 | Audit-log row written via REQUIRES_NEW so `success=false` survives wipe-rollback | §2 REQUIRES_NEW composition with AFTER_COMMIT listener verified by Spring 6.1+ framework requirement, §9 REQUIRES_NEW + AFTER_COMMIT composition is canonically supported |
| QUAL-03 | Live UAT on local MariaDB Saison-2023 fixture, manual visual confirmation post-import + 1 `BackupImportMariaDbSmokeIT` for CI | §7 Testcontainers-MariaDB / `mariadb-migration-smoke.yml` invocation pattern, §10 `rewriteBatchedStatements=true` must be added, §12 project skill `gsd-auto-uat` for Playwright HUMAN-UAT screenshots |

</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Wipe transaction orchestration | API/Backend (Spring `@Transactional`) | Database (MariaDB / H2 InnoDB FK enforcement) | Single boundary; MariaDB checks FK constraints immediately per row — FK-reverse DELETE order is the only correct strategy without `SET FOREIGN_KEY_CHECKS=0` |
| Native-SQL bulk DELETE | API/Backend (Hibernate `EntityManager.createNativeQuery`) | Database | Bypasses Hibernate cascade + dirty-check; required because TRUNCATE auto-commits on MariaDB and breaks the rollback contract |
| L1-cache drop | API/Backend (`em.flush()` + `em.clear()`) | — | Without `em.clear()`, Hibernate's persistence context still holds managed references to deleted rows; subsequent JPA queries return stale entities |
| Bulk INSERT restore | API/Backend (Spring `JdbcTemplate.batchUpdate`) | Database (MariaDB `rewriteBatchedStatements`) | Bypasses `AuditingEntityListener` so imported `created_at`/`updated_at` survive verbatim — non-negotiable design driver |
| `Team.parentTeam` self-FK decoupling | API/Backend (2-pass restore in `TeamRestorer`) | — | Self-FK precludes single-pass insert when sub-team and parent are siblings in the same JSON array |
| Audit row persistence | API/Backend (`@Transactional(REQUIRES_NEW)`) | Database (`data_import_audit` table from V7) | REQUIRES_NEW propagation creates an autonomous TX that commits even when the parent rollback fires |
| Post-commit upload-tree move | API/Backend (`@TransactionalEventListener(AFTER_COMMIT)`) | Filesystem (`Files.move(... ATOMIC_MOVE)`) | File-system mutations cannot be enrolled in the JPA TX; AFTER_COMMIT listener is Spring's canonical post-commit hook |
| Upload tree retention | Filesystem (`data/.import-backups/<ts>/uploads-old/`) | — | 24-hour manual recovery window; cleanup is operator-driven (no `@Scheduled` job in v1.10) |
| Mid-restore failure simulation | API/Backend (`RestoreFailureInjector` test-only bean) | — | Extension-point pattern; production code calls `injector.maybeFailAt(table, rowIndex)`, default no-op `@Primary` impl is empty |
| Flash messaging | API/Backend (`RedirectAttributes`) | Browser (cookie-backed `FlashMap`) | Standard Spring MVC POST-Redirect-GET; D-15 strings populate `successMessage`/`errorMessage` |
| Live MariaDB UAT | Database (MariaDB 11) + API/Backend (`@ActiveProfiles("local")` IT) | Browser (Playwright screenshots) | Manual visual verification on Saison-2023 fixture; CI smoke IT covers row-count parity |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | `@Transactional`, `@TransactionalEventListener`, `JdbcTemplate`, `@ControllerAdvice` | [VERIFIED: pom.xml:8] Already pinned |
| Spring JDBC (`spring-jdbc`) | (managed by SB 4.0.6 → Spring Framework 7.0.x) | `JdbcTemplate.batchUpdate` for bulk INSERT | [VERIFIED: transitive via `spring-boot-starter-data-jpa` → `spring-boot-starter-jdbc`] No POM change needed — `JdbcTemplate` is auto-configured via Spring Boot |
| Hibernate ORM | (managed by SB 4.0.6 → Hibernate 7.x) | `EntityManager.createNativeQuery("DELETE FROM ...")` for FK-reverse wipe, `em.flush() + em.clear()` for L1-cache drop | [VERIFIED: Hibernate 7 ships with Spring Boot 4.x; STATE.md L77 "no new dependencies"] |
| MariaDB JDBC Driver | (declared in profiles, version pinned by `mariadb-java-client`) | Driver for `local`/`docker`/`prod` profiles | [VERIFIED: `application-local.yml:9` `org.mariadb.jdbc.Driver`] Existing; add `rewriteBatchedStatements=true` param to URL (§10) |
| H2 | 2.x (managed by SB 4.0.6) | In-memory DB for `dev` profile + Surefire tests | [VERIFIED: `application-dev.yml:9` `jdbc:h2:mem:ctcdb`] Existing |
| Jakarta Persistence | 3.2.x (managed by SB 4.0.6) | `EntityManager` injection in `BackupImportService` | [VERIFIED: existing `BackupImportService` uses Spring Data JPA] No new import beyond `import jakarta.persistence.EntityManager;` |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jackson (`backupObjectMapper` qualifier) | 2.21.x | Stream-parse `data/<entity>.json` arrays during restore | [VERIFIED: `BackupObjectMapperConfig` from Phase 72; PROJECT.md §"ObjectMapper isolation"] `FAIL_ON_UNKNOWN_PROPERTIES=true` will fire on tampered JSON, triggering wipe-rollback |
| Playwright | 1.59.0 | E2E `BackupImportE2ETest` Phase 75 extension, HUMAN-UAT screenshots | [VERIFIED: pom.xml] Existing `-Pe2e` profile; D-16 says extend Phase 74's `BackupImportE2ETest` with one more `@Test` covering real success flash |
| Testcontainers MariaDB | NOT USED | — | [VERIFIED: `mariadb-migration-smoke.yml`] CTC's MariaDB-CI smoke does NOT use Testcontainers — it boots a `mariadb:11` service container and runs the packaged JAR with `--spring.profiles.active=local`. `BackupImportMariaDbSmokeIT` follows the same shape (see §7) |
| Lombok | 1.18.46 | `@RequiredArgsConstructor`, `@Slf4j` on `BackupImportService` extension + 24 `EntityRestorer` classes + `DataImportAuditService` | [VERIFIED: pom.xml; CONVENTIONS.md §"Lombok Usage"] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `ParameterizedPreparedStatementSetter` + auto-chunking | `BatchPreparedStatementSetter` (single batch) | [CITED: Spring Framework reference / mkyong.com / concretepage.com] Single-batch flavor returns `int[]`; ParameterizedPreparedStatementSetter returns `int[][]` (per-chunk) and accepts a `batchSize` argument — D-07 locks `batchSize=500` which maps directly to the auto-chunking flavor. Lambda-friendly: `(ps, row) -> { ps.setObject(1, row.id()); ... }` |
| `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization{ afterCommit() })` | `@TransactionalEventListener(phase=AFTER_COMMIT)` | [CITED: docs.spring.io] Both valid; `@TransactionalEventListener` is the higher-level idiom (recommended for Spring 6.x) and integrates with the application-event bus, making it observable via `ApplicationEventPublisher`-aware tests |
| `SET FOREIGN_KEY_CHECKS=0` MariaDB wipe | FK-reverse DELETE order | [VERIFIED: 75-CONTEXT.md "Deferred Ideas"] Rejected: MariaDB-only syntax (H2 uses `SET REFERENTIAL_INTEGRITY FALSE`), FK-bugs in export data would be silently masked. FK-reverse ordering via `Lists.reverse(BackupSchema.getExportOrder())` is correct without it |
| TRUNCATE TABLE | `EntityManager.createNativeQuery("DELETE FROM ...").executeUpdate()` | [CITED: MariaDB docs / ROADMAP §Phase 75 Goal] TRUNCATE auto-commits on MariaDB → breaks rollback contract → forbidden |
| `repo.saveAll(...)` for restore | `JdbcTemplate.batchUpdate(...)` bypassing audit listener | [VERIFIED: `BaseEntity.java:15` `@EntityListeners(AuditingEntityListener.class)`] `saveAll` would stamp `created_at`/`updated_at` to NOW on every restored row, destroying the round-trip contract |
| `UuidPacker` `BINARY(16)` helper | `ps.setObject(idx, uuid)` direct | [VERIFIED: `V1__initial_schema.sql:5,15,25,46,...` — all PKs are typed `UUID`, NOT `BINARY(16)`] CONTEXT D-08 assumes BINARY(16); reality is portable native UUID type. No helper needed |

**Installation:** No new Maven dependencies. `spring-jdbc` (provides `JdbcTemplate`) is already pulled transitively by `spring-boot-starter-data-jpa`. Verified via `pom.xml` grep — no `spring-boot-starter-jdbc` line is present, but its `JdbcTemplate` autoconfiguration is loaded via the JPA starter.

**Version verification:**
- `spring-boot-starter-parent` 4.0.6 → [VERIFIED: pom.xml:8]
- MariaDB driver (declared in `application-local.yml`/`application-docker.yml`/`application-prod.yml` as `org.mariadb.jdbc.Driver`) — version is managed by `spring-boot-starter-parent` (`org.mariadb.jdbc:mariadb-java-client` BOM-pinned)
- H2 — managed by SB 4.0.6; `dev` profile uses `jdbc:h2:mem:ctcdb;DB_CLOSE_DELAY=-1`

## Architecture Patterns

### System Architecture Diagram

```
                Admin clicks "Confirm import"
                          |
                          v
         BackupController.importExecute(form, BindingResult, RedirectAttrs)
                          |
                          | (1) validate form (@AssertTrue acknowledged)
                          | (2) backupImportService.reparse(stagingId)  // D-09 defense-in-depth
                          | (3) backupImportService.execute(stagingId)  // NEW Phase 75 entry
                          v
            +-----------------------------------+
            | BackupImportService.execute(...)  |
            | @Transactional(REQUIRED,           |
            |                isolation=          |
            |                READ_COMMITTED)    |
            +-----------------------------------+
                          |
                          v
        +-------------------------+ +-------------------+
        | (A) wipeAllTables()      | | (B) extractStaged |
        |  - UPDATE teams          | |     Uploads(zip,  |
        |    SET parent_team_id=   | |     uploadsNewDir)|
        |    NULL                  | |  uses Phase 74    |
        |  - For each tableName    | |  BackupArchive-   |
        |    in REVERSE(           | |  Service +        |
        |    EXPORT_ORDER):        | |  PathTraversal-   |
        |     em.createNativeQuery | |  Guard            |
        |     ("DELETE FROM ...")  | +-------------------+
        |     .executeUpdate()     |          |
        |  - em.flush(); em.clear()|          |
        +-------------------------+           |
                    |                          |
                    v                          v
        +----------------------------------------+
        | (C) restoreAll(entityJsonPaths)         |
        |  For each ref in EXPORT_ORDER:          |
        |    1. Open data/<file>.json stream      |
        |    2. parse via backupObjectMapper      |
        |    3. chunk to 500-row batches          |
        |    4. injector.maybeFailAt(tn, rowIdx)  | <-- D-13 hook
        |    5. EntityRestorer<T> setter →        |
        |       jdbcTemplate.batchUpdate(         |
        |         restorer.insertSql(),           |
        |         batch, 500,                     |
        |         restorer.setter())              |
        |    -- TeamRestorer.restore() runs Pass-1|
        |       (parent_team_id=NULL) then Pass-2 |
        |       (UPDATE teams SET parent=?)       |
        |    -- AuditingEntityListener BYPASSED   |
        |       because we never touch entities   |
        +----------------------------------------+
                          |
            +-------------+---------------+
            v                             v
       (success path)                (failure path: catch block)
            |                             |
            v                             v
   eventPublisher.publishEvent(    DataImportAuditService.recordResult(
     new BackupImportSucceeded-      success=false, sourceFilename,
       Event(...))                   schemaVersion, audit-id);
   tx.commit()                       // @Transactional(REQUIRES_NEW)
            |                        throw RuntimeException
            v                        // outer @Transactional rolls back
   --- transaction boundary ---     all of (A) + (C)
            |                             |
            v                             v
   @TransactionalEventListener      RedirectAttrs.errorMessage =
   (phase=AFTER_COMMIT)               "Import failed and was rolled
   onSuccess(event):                   back — see logs. Audit-id: ..."
     - Files.move(uploads,
         backupDir/uploads-old,
         ATOMIC_MOVE)                <- Step 1 (D-09)
     - Files.move(uploadsNew,
         uploads,
         ATOMIC_MOVE)                <- Step 2; on fail, revert Step 1
     - DataImportAuditService
         .recordResult(             <- Step 3 (REQUIRES_NEW auto-
         success=true, ...)           enforced by Spring 6.1+ when
                                      composing with @TransactionalEventListener)
     - backupImportService
         .deleteStagingFile(id)     <- Cleanup staged ZIP + .meta
            |
            v
   RedirectAttrs.successMessage =
     "Import completed. {N} rows
      restored across 24 tables."
```

File-to-implementation mapping (Component Responsibilities table):

| Concern | File (new in Phase 75) | Notes |
|---------|-----------------------|-------|
| Orchestrator entry point | extends `org.ctc.backup.service.BackupImportService` | New method `execute(UUID stagingId)` per D-14 |
| Audit-row writer | `org.ctc.backup.audit.DataImportAuditService` (new) | `@Transactional(REQUIRES_NEW)` per D-01 |
| Restorer contract | `org.ctc.backup.restore.EntityRestorer` (new interface) | `tableName()` / `insertSql()` / `setter(...)` per D-05 |
| 24 restorer impls | `org.ctc.backup.restore.entity.<Entity>Restorer` (24 new) | TeamRestorer is 2-pass per D-06; rest are 1-pass |
| Failure injection seam | `org.ctc.backup.restore.RestoreFailureInjector` (new interface) + `NoopRestoreFailureInjector` (new `@Component @Primary`) | Test-scope override per D-13 |
| Uploads extraction helper | extends `org.ctc.backup.service.BackupArchiveService` | New method `extractUploadsTo(Path zip, Path destDir)` per D-12 |
| Post-commit move-triple listener | new `@Component` in `org.ctc.backup.service` (e.g. `BackupImportPostCommitListener`) | `@TransactionalEventListener(phase=AFTER_COMMIT)` per D-14 |
| Custom exception | `org.ctc.backup.exception.UploadsRestoreException` (new) | Thrown by Step-2 of move-triple per D-09 |
| Simulated-failure exception | `org.ctc.backup.exception.RestoreFailureSimulatedException` (new, used only by test bean) | Lives in main per D-13 extension-point discipline |
| Controller wiring | extends `org.ctc.backup.BackupController` | Replace Phase 74 stub-flash in `importExecute` with real call per D-15; URL unchanged per D-17 |
| Application property | `application.yml` (new key) | `app.backup.import-backups-dir: data/.import-backups` |
| JDBC URL parameter | `application-local.yml` + `application-docker.yml` + `application-prod.yml` | Append `?rewriteBatchedStatements=true` (or merge query string) per §10 |
| Rollback IT | `org.ctc.backup.service.BackupImportRollbackIT` (new) | D-13 injection at 50 % of largest table |
| MariaDB smoke IT | `org.ctc.backup.service.BackupImportMariaDbSmokeIT` (new) | `@ActiveProfiles("local")` — runs in CI `mariadb-migration-smoke.yml` per §7 |
| Audit service test | `org.ctc.backup.audit.DataImportAuditServiceTest` (new) | Verifies REQUIRES_NEW propagation |
| 24 restorer unit tests | `org.ctc.backup.restore.entity.<Entity>RestorerTest` (24 new) | Each verifies `insertSql()` form + single-row setter coercion |
| HUMAN-UAT doc | `.planning/phases/75-.../75-HUMAN-UAT.md` (new) | Six screenshot-pair checklist per D-16 |

### Recommended Project Structure

```
src/main/java/org/ctc/backup/
├── BackupController.java                    # MODIFY: replace stub with real execute
├── audit/
│   ├── DataImportAudit.java                 # EXISTING — read only
│   ├── DataImportAuditRepository.java       # EXISTING — read only
│   └── DataImportAuditService.java          # NEW: REQUIRES_NEW writer
├── exception/
│   ├── BackupArchiveException.java          # EXISTING
│   ├── BackupUploadExceptionHandler.java    # EXISTING
│   ├── RestoreFailureSimulatedException.java # NEW
│   └── UploadsRestoreException.java         # NEW
├── restore/                                  # NEW package
│   ├── EntityRestorer.java                  # NEW interface (D-05)
│   ├── NoopRestoreFailureInjector.java      # NEW @Primary no-op
│   ├── RestoreFailureInjector.java          # NEW interface (D-13)
│   └── entity/
│       ├── CarRestorer.java                 # NEW (24 total)
│       ├── DriverRestorer.java
│       ├── MatchRestorer.java
│       ├── MatchScoringRestorer.java
│       ├── MatchdayRestorer.java
│       ├── PhaseTeamRestorer.java
│       ├── PlayoffMatchupRestorer.java
│       ├── PlayoffRestorer.java
│       ├── PlayoffRoundRestorer.java
│       ├── PlayoffSeedRestorer.java
│       ├── PsnAliasRestorer.java
│       ├── RaceAttachmentRestorer.java
│       ├── RaceLineupRestorer.java
│       ├── RaceRestorer.java
│       ├── RaceResultRestorer.java
│       ├── RaceScoringRestorer.java
│       ├── RaceSettingsRestorer.java
│       ├── SeasonDriverRestorer.java
│       ├── SeasonPhaseGroupRestorer.java
│       ├── SeasonPhaseRestorer.java
│       ├── SeasonRestorer.java
│       ├── SeasonTeamRestorer.java
│       ├── TeamRestorer.java                # NEW 2-pass impl (D-06)
│       └── TrackRestorer.java
├── schema/                                   # EXISTING — read only
├── security/                                 # EXISTING — read only
├── serialization/                            # EXISTING — read only
└── service/
    ├── BackupArchiveService.java            # MODIFY: + extractUploadsTo(Path, Path)
    ├── BackupExportService.java             # UNCHANGED
    ├── BackupImportService.java             # MODIFY: + execute(UUID)
    ├── BackupImportPostCommitListener.java  # NEW @TransactionalEventListener
    └── BackupStagingCleanup.java            # EXISTING — read only
```

### Pattern 1: JdbcTemplate.batchUpdate with ParameterizedPreparedStatementSetter (auto-chunking)

**What:** Idiomatic Spring 6.x bulk INSERT that auto-chunks a collection into fixed-size batches at the JDBC driver level.

**When to use:** All 24 `EntityRestorer` implementations. D-07 locks batch-size = 500, which maps directly to the auto-chunking `batchSize` argument. `BatchPreparedStatementSetter` (single-batch flavor) would require the caller to pre-chunk and loop manually — strictly more code and no benefit.

**Example:**
```java
// Source: docs.spring.io/spring-framework/reference/data-access/jdbc/advanced.html
// + verified usage in concretepage.com/spring/spring-jdbctemplate-batchupdate
public int[][] insert(List<JsonNode> rows, JdbcTemplate jdbc) {
    String sql = """
        INSERT INTO seasons (id, name, season_year, season_number, ..., created_at, updated_at)
        VALUES (?, ?, ?, ?, ..., ?, ?)
        """;
    return jdbc.batchUpdate(
        sql,
        rows,
        500,                                          // D-07 batch size
        (PreparedStatement ps, JsonNode row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));   // native UUID column
            ps.setString(2, row.get("name").asText());
            ps.setInt(3, row.get("season_year").asInt());
            // ...
            ps.setTimestamp(N - 1, Timestamp.valueOf(LocalDateTime.parse(row.get("created_at").asText())));
            ps.setTimestamp(N,     Timestamp.valueOf(LocalDateTime.parse(row.get("updated_at").asText())));
        }
    );
}
```

**Why the lambda over a class:** Spring 6.x reference docs and community examples both demonstrate the lambda form as the modern idiom. Returns `int[][]` (per-chunk row update counts) which a verification IT can sum to assert total row count.

### Pattern 2: @TransactionalEventListener(AFTER_COMMIT) for post-commit work

**What:** A `@Component` method annotated with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` is invoked AFTER the JPA transaction commits, outside the TX boundary.

**When to use:** Step 1 + Step 2 of the upload-tree move-triple (D-09), and the success-path audit-row write (Step 3). File-system mutations cannot be enrolled in the JPA transaction.

**Spring 6.1+ critical caveat (research finding §9):** When a `@TransactionalEventListener` method ALSO carries `@Transactional` (e.g. invokes the REQUIRES_NEW audit-service write), Spring 6.1+ requires `Propagation.REQUIRES_NEW` to avoid `TransactionRequiredException`. The D-01 (REQUIRES_NEW audit) + D-14 (AFTER_COMMIT listener) composition is therefore not only sound, it is the **required** shape per framework documentation.

**Example:**
```java
// Source: docs.spring.io/spring-framework/reference/data-access/transaction/event.html
// + DZone.com 2026 Best Practices Guide
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupImportPostCommitListener {

    private final DataImportAuditService dataImportAuditService;
    private final BackupImportService backupImportService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSuccess(BackupImportSucceededEvent event) {
        Path importBackupDir = event.importBackupDir();
        Path uploadsTarget   = event.uploadsTarget();
        Path uploadsNewDir   = event.uploadsNewDir();
        UUID auditUuid       = event.auditUuid();

        // Step 1 — move existing uploads to uploads-old
        try {
            Files.move(uploadsTarget, importBackupDir.resolve("uploads-old"), ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Step 1 failed; uploads tree unchanged", e);
            dataImportAuditService.recordResult(/*success=*/ false, ..., auditUuid);  // REQUIRES_NEW
            throw new UploadsRestoreException(e);
        }

        // Step 2 — promote uploads-new to uploads (with revert-on-failure)
        try {
            Files.move(uploadsNewDir, uploadsTarget, ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Step 2 failed; attempting Step-1 revert", e);
            try {
                Files.move(importBackupDir.resolve("uploads-old"), uploadsTarget, ATOMIC_MOVE);
                log.warn("Step-1 revert succeeded");
            } catch (IOException revertEx) {
                log.error("Step-1 revert ALSO failed — manual recovery required", revertEx);
            }
            dataImportAuditService.recordResult(/*success=*/ false, ..., auditUuid);  // REQUIRES_NEW
            throw new UploadsRestoreException(e);
        }

        // Step 3 — success audit row (REQUIRES_NEW; auto-enforced by Spring 6.1+)
        dataImportAuditService.recordResult(/*success=*/ true, ..., auditUuid);
        backupImportService.deleteStagingFile(event.stagingId());
    }
}
```

### Pattern 3: 2-pass restore for self-FK (TeamRestorer)

**What:** Pass 1 inserts every team with `parent_team_id = NULL`. Pass 2 issues `UPDATE teams SET parent_team_id = ? WHERE id = ?` for each row whose JSON carries a non-null `parent_team_id`.

**When to use:** Only `TeamRestorer` (D-06). All other 23 entities are pure 1-pass inserts.

**Why:** Sub-team A's `parent_team_id` must reference parent P's `id`. Both A and P are in `data/teams.json` (flat array, not topologically sorted within the file). Forward-iterating and inserting with `parent_team_id` set would fail when P hasn't been inserted yet. The 2-pass discipline is hidden inside `TeamRestorer.restore(...)` so the orchestrator stays generic.

**Example:**
```java
// Source: 75-CONTEXT.md D-06, verified against V1__initial_schema.sql:46-57
public class TeamRestorer implements EntityRestorer<JsonNode> {

    @Override public String tableName() { return "teams"; }

    @Override public String insertSql() {
        return "INSERT INTO teams (id, name, short_name, logo_url, primary_color, " +
               "secondary_color, accent_color, parent_team_id, created_at, updated_at) " +
               "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)";   // pass-1: parent_team_id hard-coded NULL
    }

    public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        // Pass 1 — INSERT all teams with parent_team_id=NULL
        jdbc.batchUpdate(insertSql(), rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setString(2, row.get("name").asText());
            ps.setString(3, row.get("short_name").asText());
            // ... other columns
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse(row.get("created_at").asText())));
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.parse(row.get("updated_at").asText())));
        });

        // Pass 2 — UPDATE parent_team_id for rows whose JSON has a non-null parent
        List<JsonNode> withParent = rows.stream()
            .filter(r -> r.hasNonNull("parent_team_id"))
            .toList();
        if (!withParent.isEmpty()) {
            jdbc.batchUpdate(
                "UPDATE teams SET parent_team_id = ? WHERE id = ?",
                withParent, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("parent_team_id").asText()));
                    ps.setObject(2, UUID.fromString(row.get("id").asText()));
                });
        }
    }
}
```

### Anti-Patterns to Avoid

- **`repo.deleteAll()` for wipe.** Triggers Hibernate cascade + dirty-check, fires AuditingEntityListener side-effects, and serializes one-DELETE-per-row over the wire. Use `EntityManager.createNativeQuery("DELETE FROM <table>").executeUpdate()` instead.
- **`repo.saveAll(entities)` for restore.** Re-maps Jackson nodes to entities, then triggers `AuditingEntityListener` to overwrite `created_at`/`updated_at` with `LocalDateTime.now()`. Destroys the round-trip contract. Use `JdbcTemplate.batchUpdate` directly on raw column values.
- **TRUNCATE TABLE for wipe.** MariaDB auto-commits TRUNCATE → outer `@Transactional` cannot roll back. ROADMAP-locked: forbidden.
- **`SET FOREIGN_KEY_CHECKS=0` (MariaDB) / `SET REFERENTIAL_INTEGRITY FALSE` (H2).** Masks FK-bugs in export data and is dialect-divergent. FK-reverse ordering is correct without it. Phase 75 Deferred Ideas explicitly rejects this.
- **Skipping `em.flush() + em.clear()` between wipe and restore.** Hibernate's L1 cache still holds managed references to the deleted entities; the success-path row-count read for `tableCountsRestored` returns stale entity references. ROADMAP-locked: required.
- **Same-TX audit row write (NOT REQUIRES_NEW).** A wipe-rollback also rolls back the audit row, defeating "`success=false` survives rollback" requirement of ROADMAP Goal-3.
- **File-system moves inside the `@Transactional` method.** Files moved before `tx.commit()` are physically in place but a downstream wipe-rollback leaves the FS in an inconsistent state with no DB rows. Must be post-commit via `@TransactionalEventListener`.
- **Mutating `BackupSchema.getExportOrder()` to include the `Team.parentTeam` self-edge.** The topo-sort already excludes it (`EntityTopoSorter.java:62` `if (depClass.equals(ownerClass)) continue;`). Phase 75 must NOT touch `BackupSchema` or `EntityTopoSorter`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bulk INSERT with chunking | Manual `Stream.partition` + per-chunk PreparedStatement loop | `JdbcTemplate.batchUpdate(sql, batchArgs, batchSize, ParameterizedPreparedStatementSetter)` | Built-in auto-chunking + driver-level batching with `rewriteBatchedStatements=true` |
| Post-commit hook | Manual `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization{ afterCommit(){...} })` from inside the service | `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` on a separate `@Component` | Higher-level, observable via ApplicationEvent bus, framework auto-enforces REQUIRES_NEW on nested `@Transactional` |
| UUID type-coercion for JDBC | Custom `UuidPacker` `BINARY(16)` byte-packing helper | `ps.setObject(idx, uuid)` direct | All UUID columns are typed `UUID` (portable native), not `BINARY(16)`. JDBC driver handles conversion |
| Topological sort of entities | Hand-maintained `List<EntityRef>` ordering | `BackupSchema.getExportOrder()` (existing JPA-Metamodel Kahn sort) | Phase 72 already shipped this; reverse for wipe via `Lists.reverse(...)` |
| ZIP entry path-traversal check | Custom `startsWith(uploadDir.toRealPath())` re-implementation | `PathTraversalGuard.assertWithin(stagingRoot, entryName)` (existing) | Reuse Phase 74 guard verbatim in `BackupArchiveService.extractUploadsTo(...)` |
| ZIP-bomb defense | Custom inflated-byte counter | `LimitedInputStream` (existing Phase 74 wrapper) | Reuse Phase 74 `assertEntrySafe` invariants in the new extract method |
| Atomic file rename | Custom copy-then-delete fallback | `Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE)` | JDK 25 NIO.2 native; CTC `data/` directory is on a single filesystem so atomic-move is always available |
| Audit row save | `entityManager.persist(audit)` from inside outer `@Transactional` | `DataImportAuditService.recordResult(...)` with `@Transactional(REQUIRES_NEW)` | REQUIRES_NEW creates an autonomous TX that commits even when the outer rollback fires |
| Schema-mismatch flash | Re-implement the `BackupArchiveException` reason mapping | Reuse existing `BackupController.mapReason(BackupArchiveException)` switch | Phase 74 already covers all reject paths; Phase 75 inherits |

**Key insight:** This phase is almost entirely about REUSING Phase 72/73/74 infrastructure. The only genuine new abstraction is the `EntityRestorer` interface + 24 implementations. Every other primitive (topo-sort, ZIP reader, path-traversal guard, manifest schema check, audit entity, staging-file lifecycle) is reused verbatim.

## Runtime State Inventory

Phase 75 is a **greenfield writer phase** (not a rename/refactor) — no existing runtime state needs migration. The phase ADDS new state. For completeness:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `data_import_audit` table (Phase 72 V7) is currently empty — Phase 75 writes the first row. New `data/.import-backups/<ts>/` filesystem subtree is created at first import. | None (greenfield) |
| Live service config | None — no external services. | None |
| OS-registered state | None — no OS-level registrations. | None |
| Secrets/env vars | None — `executedBy` reads `SecurityContextHolder` on prod/docker; on dev/local writes literal `"dev"`. No new secret. | None |
| Build artifacts | None — pom.xml is untouched (no new dependencies). | None |

**Nothing found in any category.** Verified by: (a) grep for `data_import_audit` across `src/main` (only in V7 migration + Phase 72 entity), (b) grep for `data/.import-backups` (only in CONTEXT/ROADMAP; not yet referenced in code), (c) `application.yml` audit (existing key `app.backup.staging-dir` is unchanged; new key `app.backup.import-backups-dir` is additive).

## Common Pitfalls

### Pitfall 1: Native DELETE leaves managed entities in the persistence context

**What goes wrong:** After `em.createNativeQuery("DELETE FROM seasons").executeUpdate()`, Hibernate still has the deleted `Season` objects in its L1 cache. A subsequent `seasonRepository.findAll()` returns stale entities; a `repo.count()` in the audit `tableCountsRestored` calculation returns a wrong number.

**Why it happens:** Native queries bypass Hibernate's session management. The session has no idea the rows are gone.

**How to avoid:** `em.flush()` + `em.clear()` AFTER all DELETE statements complete. `flush()` writes pending changes (none here, but defensive), `clear()` detaches all entities. ROADMAP-locked: required between wipe and restore.

**Warning signs:** Audit `tableCountsRestored` mismatches the JSON's row counts; second-level read in the test asserts a row exists that has been deleted.

### Pitfall 2: `@TransactionalEventListener` swallows exceptions silently

**What goes wrong:** An exception thrown from `onSuccess(event)` (e.g. Step-2 `IOException` revert-failure) propagates to the caller's stack trace, but because the outer transaction already committed, the wipe-rollback does NOT fire. The DB is in the post-import state; the FS is in the pre-import state. Inconsistent.

**Why it happens:** AFTER_COMMIT means the TX is done. There is no "outer transaction" to roll back.

**How to avoid:** Best-effort revert in Step 2 (D-09's revert-on-failure clause), log ERROR loudly, write `success=false` audit row via REQUIRES_NEW, and surface the soft-fail flash string per D-15#3. The user's operational recovery is to manually move `data/.import-backups/<ts>/uploads-old/` back into place — documented in the flash.

**Warning signs:** Test fails because the FS state isn't what the assertion expects, but the test report shows the listener executed; check the ERROR log for `UploadsRestoreException`.

### Pitfall 3: REQUIRES_NEW audit write inside the outer @Transactional rollback path

**What goes wrong:** Inside the outer `BackupImportService.execute(...) catch (Exception e) { ... }`, calling `dataImportAuditService.recordResult(success=false, ...)` (REQUIRES_NEW) MUST commit even though the outer is rolling back.

**Why it happens:** Spring's REQUIRES_NEW semantics: a new TX is opened, the outer is suspended. When the inner commits, the outer remains suspended; control returns to the catch block; outer rolls back. Audit row survives.

**How to avoid:** Verify by injecting a Mockito `@MockitoSpyBean PlatformTransactionManager` in `DataImportAuditServiceTest` and asserting `getTransaction(...)` is called with a REQUIRES_NEW definition. Also: make sure `DataImportAuditService` is a separate Spring bean (not a self-injected method on `BackupImportService`) so the AOP proxy fires.

**Warning signs:** `BackupImportRollbackIT` assertion `dataImportAuditRepository.count() == 1` after wipe-rollback fails (the audit row was rolled back together with the outer TX).

### Pitfall 4: MariaDB FK constraint immediate-check fires mid-DELETE on a sibling self-reference

**What goes wrong:** If `Team A` has `parent_team_id = B.id` and the wipe issues `DELETE FROM teams`, MariaDB InnoDB checks the self-FK per row. If row B is deleted before row A, A's FK reference is dangling.

**Why it happens:** MariaDB InnoDB checks FK constraints **immediately** (per row), not deferred to commit (research finding §4).

**How to avoid:** The `UPDATE teams SET parent_team_id = NULL` pre-step (D-06 mirror for the wipe direction) decouples ALL parent-child references before the FK-reverse-DELETE walks the table. Then `DELETE FROM teams` is FK-self-safe.

**Warning signs:** `IntegrityConstraintViolationException` mid-wipe; investigation reveals the failing row references a parent that was already deleted.

### Pitfall 5: `JdbcTemplate` requires `@Autowired` injection but `BackupImportService` uses explicit constructor

**What goes wrong:** Phase 74 `BackupImportService` uses an explicit constructor (not `@RequiredArgsConstructor`) because `@Value("${app.backup.staging-dir}")` is needed for the `stagingDir` field. Adding `JdbcTemplate` as a new `final` dependency means extending the explicit constructor signature, not adding a `@Autowired` annotation alone.

**Why it happens:** When a class has any explicit constructor, Lombok's `@RequiredArgsConstructor` is not generated; ALL dependencies must be in the manual constructor.

**How to avoid:** Extend the existing constructor in `BackupImportService.java:79` with the new params: `JdbcTemplate jdbcTemplate`, `EntityManager entityManager`, `ApplicationEventPublisher eventPublisher`, `Map<String, EntityRestorer<?>> restorersByTable`, `BackupArchiveService backupArchiveService` (already there but unused for execute today), `RestoreFailureInjector failureInjector`, and `@Value("${app.backup.import-backups-dir}")` for the new property.

**Warning signs:** Spring fails to start because two constructors compete for `@Autowired`; or a `JdbcTemplate jdbcTemplate` field is `null` at runtime.

### Pitfall 6: Test isolation when `BackupImportMariaDbSmokeIT` mutates the DB

**What goes wrong:** A round-trip IT that does export → wipe → import on the shared `mariadb:11` service container leaves residual state. The next test (or next CI run) starts with the post-import state.

**Why it happens:** The CI service container `mariadb:11` is per-job, but within a single test class the DB persists across `@Test` methods. The test wipes data; subsequent ITs running in the same Failsafe execution see the wiped state.

**How to avoid:** (a) `BackupImportMariaDbSmokeIT` runs in a Failsafe-IT-only environment (not Surefire), so it does NOT mix with H2 unit tests. (b) Use `@TestMethodOrder` + `@Order` if multiple `@Test` methods exist, and rely on Flyway re-seeding + `TestDataService` re-population. (c) Alternative: extend `mariadb-migration-smoke.yml` workflow to spin up a fresh `mariadb:11` per IT (operationally heavy; skip unless coverage requires it). D-16 says ONE smoke IT, so a single `@Test` method avoids the issue.

**Warning signs:** Flaky `BackupImportMariaDbSmokeIT` runs on CI; passes locally with fresh DB.

### Pitfall 7: `BackupImportE2ETest` cookie-jar reset assumption breaks if the success path stores anything in session

**What goes wrong:** Phase 74's `BackupImportE2ETest` is stateless-proof via cookie-jar reset between steps. Phase 75's success path (with the success flash) could regress this if any new model attribute leaks into the session.

**Why it happens:** A new `@SessionAttributes("preview")` or `@ModelAttribute` returning a non-flash value would silently re-introduce state.

**How to avoid:** Phase 75 extends the existing `BackupImportE2ETest` with one new `@Test` (success scenario). The new test must reset cookies between confirm and execute, mirroring the existing two `@Test` methods. CONTEXT D-18 ("UUID round-trips through hidden form input") is the canonical defense; the controller must NEVER store the preview in session.

**Warning signs:** Cookie-jar test still passes but a `playwright-cli` manual session-tab walk shows persistent UI state.

## Code Examples

Verified patterns from official sources and the existing CTC codebase.

### Wipe — FK-reverse native DELETE with cache drop

```java
// Source: Hibernate 7 docs + thorben-janssen.com/hibernate-tips-remove-entities-persistence-context
// + ROADMAP §"Phase 75 Goal" + 75-CONTEXT.md D-06
@Transactional   // outer @Transactional propagation = REQUIRED on execute()
private void wipeAllTables() {
    // Pre-step: decouple Team.parentTeam self-FK
    entityManager.createNativeQuery("UPDATE teams SET parent_team_id = NULL").executeUpdate();

    // FK-reverse DELETE
    List<EntityRef> wipeOrder = backupSchema.getExportOrder().reversed();   // Java 21+ List.reversed()
    for (EntityRef ref : wipeOrder) {
        int rows = entityManager.createNativeQuery(
            "DELETE FROM " + ref.tableName()).executeUpdate();
        log.debug("Wiped {} rows from {}", rows, ref.tableName());
    }

    // Drop L1 cache so subsequent JPA reads return fresh state
    entityManager.flush();
    entityManager.clear();
}
```

### Restore — orchestrator + per-entity batch INSERT

```java
// Source: docs.spring.io/spring-framework/reference/data-access/jdbc/advanced.html
// + 75-CONTEXT.md D-05/D-07/D-13
@Transactional   // same outer boundary as wipeAllTables
private RestoreCounts restoreAll(Path stagedZip, Path uploadsNewDir) throws IOException {
    Map<String, Long> restored = new LinkedHashMap<>();

    for (EntityRef ref : backupSchema.getExportOrder()) {                  // forward order
        EntityRestorer<?> restorer = restorersByTable.get(ref.tableName());
        if (restorer == null) {
            throw new IllegalStateException("No EntityRestorer wired for " + ref.tableName());
        }

        long rowCount = 0;
        try (InputStream in = openZipEntry(stagedZip, ref.fileName());
             JsonParser parser = backupObjectMapper.getFactory().createParser(in)) {

            parser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new BackupArchiveException(Reason.MANIFEST_INVALID,
                    "data file is not a JSON array: " + ref.fileName());
            }

            List<JsonNode> batch = new ArrayList<>(500);
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                batch.add(backupObjectMapper.readTree(parser));
                rowCount++;
                failureInjector.maybeFailAt(ref.tableName(), (int) rowCount);   // D-13 hook every row
                if (batch.size() == 500) {
                    restorer.restore(batch, jdbcTemplate);                       // delegates to per-entity setter
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) restorer.restore(batch, jdbcTemplate);
        }
        restored.put(ref.tableName(), rowCount);
    }
    return new RestoreCounts(restored);
}
```

### Audit row writer with REQUIRES_NEW

```java
// Source: docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html
// + 75-CONTEXT.md D-01
@Slf4j
@Service
@RequiredArgsConstructor
public class DataImportAuditService {

    private final DataImportAuditRepository repository;
    private final ObjectMapper backupObjectMapper;   // @Qualifier handled by Spring auto-wire

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DataImportAudit recordResult(
            UUID auditId,
            String executedBy,
            int schemaVersion,
            Map<String, Long> tableCountsWiped,
            Map<String, Long> tableCountsRestored,
            String sourceFilename,
            boolean success) {

        DataImportAudit audit = DataImportAudit.builder()
            .id(auditId)
            .executedAt(Instant.now())
            .executedBy(executedBy)
            .schemaVersion(schemaVersion)
            .tableCountsWiped(writeJson(tableCountsWiped))
            .tableCountsRestored(writeJson(tableCountsRestored))
            .sourceFilename(sourceFilename)
            .success(success)
            .build();
        DataImportAudit saved = repository.save(audit);
        log.info("Audit row written: id={}, success={}, rows={}",
                saved.getId(), success, tableCountsRestored.values().stream().mapToLong(Long::longValue).sum());
        return saved;
    }

    private String writeJson(Map<String, Long> map) {
        try {
            return backupObjectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize table counts", e);
        }
    }
}
```

### Test failure-injector

```java
// Source: 75-CONTEXT.md D-13
public interface RestoreFailureInjector {
    void maybeFailAt(String tableName, int rowIndex);
}

@Primary
@Component
public class NoopRestoreFailureInjector implements RestoreFailureInjector {
    @Override public void maybeFailAt(String tableName, int rowIndex) { /* no-op */ }
}

// In test scope (BackupImportRollbackIT @TestConfiguration):
@TestConfiguration
class FailAtTableConfig {
    @Bean
    @Primary    // overrides Noop@Primary because test config trumps main @Primary
    RestoreFailureInjector failAtTable() {
        return (tableName, rowIndex) -> {
            // Fail at 50% of the largest expected table (race_results ~ 1000 rows for Saison 2023)
            if ("race_results".equals(tableName) && rowIndex == 500) {
                throw new RestoreFailureSimulatedException(
                    "Simulated mid-restore failure at race_results:500");
            }
        };
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `repo.deleteAll()` + `repo.saveAll(...)` for bulk replace | Native-SQL DELETE + `JdbcTemplate.batchUpdate` bypassing `AuditingEntityListener` | Phase 75 (this phase) | Required: `repo.saveAll` would re-stamp `created_at`/`updated_at` via listener — destroys round-trip contract |
| TRUNCATE TABLE | DELETE FROM in FK-reverse order | Phase 75 ROADMAP | TRUNCATE auto-commits on MariaDB → forbidden |
| `BatchPreparedStatementSetter` (anonymous-class style, single batch) | `ParameterizedPreparedStatementSetter` (lambda style, auto-chunking) | Spring 6.x | Idiomatic Spring 6.x form; community examples consistently demonstrate lambda |
| `TransactionSynchronizationManager.registerSynchronization(...)` | `@TransactionalEventListener(phase=AFTER_COMMIT)` | Spring 4.2+ (canonical in 6.x) | Higher-level idiom; framework auto-enforces REQUIRES_NEW on nested `@Transactional` |
| Custom `UUID` byte packing for `BINARY(16)` columns | `ps.setObject(idx, uuid)` direct via JDBC driver | Hibernate 6 + MariaDB 10.7+ | MariaDB native UUID type since 10.7 (released 2021); JDBC driver handles conversion |
| Hand-maintained FK-DELETE order list | `BackupSchema.getExportOrder().reversed()` (JPA-Metamodel Kahn sort) | Phase 72 | Self-correcting when new entities are added; eliminates hand-maintenance |

**Deprecated/outdated:**
- `@PostConstruct` from `javax.annotation.*` — replaced by `jakarta.annotation.PostConstruct` (Spring Boot 4 uses Jakarta EE 11)
- `BatchPreparedStatementSetter` is NOT deprecated; the auto-chunking variant is simply preferred for fixed-size batches per Spring 6.x reference

## Assumptions Log

This research verified all critical assumptions via codebase grep or official documentation. The few remaining items tagged `[ASSUMED]` are listed below.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Saison 2023 fixture in `DevDataSeeder` / `TestDataService` produces sufficient row counts (~1000 race-results + ~500 race-lineups + sub-100 for other 22 entities) to make the "50 % injection at largest table" rollback IT meaningful | §1 RestoreFailureInjector, D-13 | If fixture is sparse, "50 % of 30 rows" → row 15 is not a representative mid-restore failure point. Planner verifies by running `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` and inspecting `repo.count()` per table. |
| A2 | `MariaDB 10.7+` (CI uses `mariadb:11`) treats `UUID` column type as native; JDBC `setObject(idx, uuid)` succeeds without explicit `Types.OTHER` | §5 UUID storage | Falls back to "use `setString(idx, uuid.toString())`" if driver rejects setObject. Verified by a single `BackupImportMariaDbSmokeIT` round-trip assert at planner-execution time |
| A3 | Spring Boot 4.0.6 ships Spring Framework 7.0.x which inherits the Spring 6.1+ REQUIRES_NEW auto-enforcement rule on `@TransactionalEventListener` + `@Transactional` | §2 AFTER_COMMIT listener, D-14 | If auto-enforcement is no longer active in 7.x, the audit-row write inside the listener silently fails with `TransactionRequiredException`. Mitigation: explicitly annotate `DataImportAuditService.recordResult` with `@Transactional(propagation = Propagation.REQUIRES_NEW)` — same defensive code regardless |
| A4 | The `data/<profile>/uploads/` filesystem and `data/.import-backups/<ts>/` are on the same filesystem (single mount), so `Files.move(..., ATOMIC_MOVE)` works without fallback | §7 atomic-move | If they straddle filesystems, ATOMIC_MOVE throws `AtomicMoveNotSupportedException`. Mitigation: catch and fall back to copy-then-delete (non-atomic). Document in Pitfall 2 |
| A5 | `dev`/`local`/`docker`/`prod` profiles do NOT set Hibernate `hibernate.type.preferred_uuid_jdbc_type` — default UUID-as-native (MariaDB 10.7+) / UUID-as-CHAR(36) (H2) behaviour is in effect | §5 UUID storage | If profile-specific `hibernate.type.preferred_uuid_jdbc_type=BINARY` is set somewhere, BINARY(16) packing would be needed. Verified by grep — no such property is set |

**If this table feels long:** A1..A5 are all minor and verifiable at plan-execution time. A1 is the only one that could materially affect the rollback IT design.

## Open Questions

1. **Audit row's `executedBy` value on dev/local profile**
   - What we know: D-02 says `SecurityContextHolder.getContext().getAuthentication().getName()` on prod/docker, `"dev"` literal on dev/local
   - What's unclear: Where to place the profile-detection logic — inside `DataImportAuditService` itself (Spring `Environment` injection), or in `BackupImportService.execute(...)` passing the result to `recordResult(...)` as a parameter?
   - Recommendation: Inject `Environment` into `DataImportAuditService`, check `env.matchesProfiles("dev | local")`. Centralizes the rule next to the audit logic; avoids leaking profile knowledge to `BackupImportService`.

2. **`tableCountsWiped` vs `tableCountsRestored` JSON shape**
   - What we know: V7 schema columns are `LONGTEXT` storing JSON. Jackson serialization enforces shape at write time (PROJECT.md §"Audit log persistence").
   - What's unclear: Map keys — table_name (snake_case) or human_label?
   - Recommendation: Use `tableName` (snake_case) for both — consistent with `manifest.json:table_counts` shape from Phase 73. Human-label conversion happens in the future Phase v1.11+ audit-history UI.

3. **Whether `BackupImportService.execute(...)` returns a structured result or `void`**
   - What we know: D-15 success flash uses `{restored}` and `{entities}` placeholders.
   - What's unclear: Does the controller compute the placeholders by re-reading `data_import_audit` after the listener fires, or does `execute()` return a `BackupImportResult` record the controller binds to the flash string?
   - Recommendation: `execute(...)` returns `BackupImportResult(UUID auditUuid, long restoredTotal, int entityCount)` — the controller binds the placeholders before redirect; the listener writes the audit row but the controller already has the data in hand. Avoids a wasted DB round-trip.

4. **Whether the success-path event is published from inside the `@Transactional` method**
   - What we know: D-14 says post-commit move-triple is in a `@TransactionalEventListener(AFTER_COMMIT)` listener.
   - What's unclear: The publish call must be **inside** the `@Transactional` method so Spring's TX-aware event publisher buffers the event until commit. If publish happens outside the TX, the listener fires immediately.
   - Recommendation: Inject `ApplicationEventPublisher` into `BackupImportService`, publish the event as the LAST statement of the `try` block in `execute(...)`. Spring's `TransactionSynchronizationEventListenerAdapter` captures it and fires after commit.

## Environment Availability

Phase 75 depends only on existing infrastructure. No new external services or runtimes.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java | Compile + runtime | ✓ | 25 (Temurin) | — |
| Maven (`./mvnw`) | Build | ✓ | 3.9.x wrapper | — |
| MariaDB | Live UAT (`local` profile) + CI smoke IT | ✓ (host: localhost:3306 for local UAT; `mariadb:11` service container in CI) | 11.x | — |
| H2 | `dev` profile + Surefire unit tests + most ITs | ✓ | 2.x (managed) | — |
| Spring Boot starter `data-jpa` | `JdbcTemplate`, `EntityManager`, `JpaRepository` | ✓ | 4.0.6 transitive | — |
| Spring JDBC (transitive `spring-jdbc`) | `JdbcTemplate.batchUpdate` API | ✓ (transitive via `data-jpa`) | 7.0.x | — |
| Jackson + `backupObjectMapper` qualifier | JSON stream-parse of restore data | ✓ | 2.21.x | — |
| Playwright | Phase 75 E2E test extension; HUMAN-UAT screenshots | ✓ | 1.59.0 | — |
| `BackupSchema.getExportOrder()` | FK-reverse + forward order over 24 entities | ✓ | Phase 72 | — |
| `BackupArchiveService` reader | Read manifest + stream data/<entity>.json + extract uploads/<...> | ✓ (Phase 74 — add `extractUploadsTo` method) | Phase 74 | — |
| `PathTraversalGuard` | Validate every ZipEntry name during upload extract | ✓ | Phase 74 | — |
| `DataImportAuditRepository` | Save audit rows | ✓ | Phase 72 | — |
| MariaDB driver param `rewriteBatchedStatements=true` | JdbcTemplate batchUpdate efficiency on MariaDB | ✗ — see §10 below | — | Phase 75 MUST add to JDBC URLs in local/docker/prod yml |

**Missing dependencies with no fallback:**
- None.

**Missing dependencies with fallback:**
- `rewriteBatchedStatements=true` is absent from all profiles. Without it, MariaDB driver issues one round-trip per INSERT statement (500 round-trips for a 500-row batch). The batch will succeed but at 100× the wire cost. **Fallback:** the import still works correctly without the parameter — Phase 75 just becomes slow at scale. Phase 75 plan should add this URL parameter to the three yml files as a fix.

## Validation Architecture

Per the Nyquist validation gate. The 8 Nyquist dimensions mapped to concrete test surface for Phase 75:

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot 4.0.6 Test + Mockito + Playwright (E2E) + AssertJ |
| Config file | `pom.xml` (Surefire L184-194; Failsafe L256-278; JaCoCo L270-319) |
| Quick run command | `./mvnw -q -Dtest='BackupImport*Test,EntityRestorer*Test,DataImportAuditServiceTest,*RestorerTest' test` |
| Full suite command | `./mvnw verify` (Unit + IT + JaCoCo); E2E via `./mvnw verify -Pe2e` |
| MariaDB smoke command | `./mvnw verify -Dit.test=BackupImportMariaDbSmokeIT -Dspring-boot.run.profiles=local` (via CI workflow per §7) |
| Estimated runtime | ~90 s (`verify`), ~3 min (`verify -Pe2e`), +90 s for MariaDB smoke on CI |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| IMPORT-05 | Single `@Transactional` wipe+restore; `Team.parentTeam=NULL` pre-step; FK-reverse `DELETE`; `em.flush()+em.clear()`; `JdbcTemplate.batchUpdate` restore | IT | `./mvnw -Dit.test=BackupImportExecuteIT verify` | ❌ Wave 0 (new) |
| IMPORT-05 (sub) | Each of 24 `EntityRestorer.insertSql()` is well-formed and the setter coerces a single sample `JsonNode` correctly | Unit | `./mvnw -Dtest='*RestorerTest' test` | ❌ Wave 0 (24 new) |
| IMPORT-05 (sub) | `TeamRestorer` 2-pass: Pass-1 inserts every team with NULL parent, Pass-2 sets parent for sub-teams | Unit + IT | `./mvnw -Dtest=TeamRestorerTest test` + `./mvnw -Dit.test=TeamRestorerIT verify` | ❌ Wave 0 (new) |
| IMPORT-06 | Post-commit `Files.move(uploads, uploads-old, ATOMIC_MOVE)` + `Files.move(uploads-new, uploads, ATOMIC_MOVE)` + revert-on-Step-2-failure | IT | `./mvnw -Dit.test=BackupImportPostCommitIT verify` | ❌ Wave 0 (new) |
| IMPORT-06 (sub) | Audit row's `success=true` survives the post-commit listener; `success=false` survives wipe-rollback via REQUIRES_NEW | IT | `./mvnw -Dit.test=BackupImportRollbackIT verify` | ❌ Wave 0 (new) |
| IMPORT-07 | `executedBy=dev` on dev profile; `executedBy=admin-principal` on prod profile (mock) | Unit | `./mvnw -Dtest=DataImportAuditServiceTest test` | ❌ Wave 0 (new) |
| IMPORT-07 (sub) | `tableCountsWiped` + `tableCountsRestored` LONGTEXT JSON round-trips through `backupObjectMapper` | Unit | `./mvnw -Dtest=DataImportAuditSerializationTest test` | ❌ Wave 0 (new) |
| QUAL-03 | Live MariaDB UAT: Saison-2023 export → wipe → import → row counts equal pre-export (CI layer) | IT (MariaDB smoke) | `./mvnw verify -Dit.test=BackupImportMariaDbSmokeIT` + `mariadb-migration-smoke.yml` workflow | ❌ Wave 0 (new) |
| QUAL-03 (sub) | Operator runs Saison-2023 fixture on local MariaDB, captures 6 screenshot pairs, asserts visual identity | HUMAN-UAT (manual) | `playwright-cli open http://localhost:9091/seasons/2023` (Desktop + Mobile) per `feedback_playwright_cli.md` + `.skills/gsd-auto-uat` | ❌ Wave 0 (75-HUMAN-UAT.md doc + screenshots dir) |
| (cross-cutting) | Phase 74's `BackupImportE2ETest` extended with one `@Test` covering real success-flash flow | E2E (Playwright) | `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest` | ✓ (extend; file exists at `src/test/java/org/ctc/e2e/BackupImportE2ETest.java`) |
| (coverage) | JaCoCo line coverage ≥ 82 % held across +30 new Java classes | Build gate | `./mvnw verify` (jacoco:check phase) | ✓ (gate exists in pom.xml:312) |

### Sampling Rate

- **Per task commit:** `./mvnw -q -Dtest='<JustWrittenTest>' test` (~10 s)
- **Per wave merge:** `./mvnw verify` (Unit + IT + JaCoCo; ~90 s)
- **Phase gate:** Full `./mvnw verify -Pe2e` green + MariaDB smoke green via local invocation `./mvnw verify -Pe2e -Dspring-boot.run.profiles=local` (operator) AND `mariadb-migration-smoke.yml` CI green
- **Max feedback latency:** ~60 s for unit, ~90 s for IT, ~3 min including E2E. MariaDB smoke ~2 min on CI.

### Wave 0 Gaps

- [ ] `src/main/java/org/ctc/backup/restore/EntityRestorer.java` — interface (D-05)
- [ ] `src/main/java/org/ctc/backup/restore/RestoreFailureInjector.java` — interface (D-13)
- [ ] `src/main/java/org/ctc/backup/restore/NoopRestoreFailureInjector.java` — `@Primary` no-op
- [ ] `src/main/java/org/ctc/backup/restore/entity/{Car,Driver,Match,MatchScoring,Matchday,PhaseTeam,Playoff,PlayoffMatchup,PlayoffRound,PlayoffSeed,PsnAlias,Race,RaceAttachment,RaceLineup,RaceResult,RaceScoring,RaceSettings,Season,SeasonDriver,SeasonPhase,SeasonPhaseGroup,SeasonTeam,Team,Track}Restorer.java` — 24 restorers
- [ ] `src/main/java/org/ctc/backup/audit/DataImportAuditService.java` — REQUIRES_NEW audit writer
- [ ] `src/main/java/org/ctc/backup/exception/{UploadsRestoreException,RestoreFailureSimulatedException}.java`
- [ ] `src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java` — `@TransactionalEventListener(AFTER_COMMIT)`
- [ ] `src/test/java/org/ctc/backup/restore/entity/*RestorerTest.java` × 24 — Surefire unit tests
- [ ] `src/test/java/org/ctc/backup/audit/DataImportAuditServiceTest.java` — REQUIRES_NEW verification
- [ ] `src/test/java/org/ctc/backup/service/BackupImportExecuteIT.java` — IMPORT-05 happy path
- [ ] `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` — D-13 failure injection
- [ ] `src/test/java/org/ctc/backup/service/BackupImportPostCommitIT.java` — IMPORT-06 atomic-move
- [ ] `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` — QUAL-03 CI layer
- [ ] `src/test/java/org/ctc/backup/restore/entity/TeamRestorerIT.java` — 2-pass restore against H2
- [ ] `.planning/phases/75-.../75-HUMAN-UAT.md` — D-16 six-screenshot checklist
- [ ] `application.yml` — new key `app.backup.import-backups-dir: data/.import-backups`
- [ ] `application-local.yml` / `application-docker.yml` / `application-prod.yml` — append `?rewriteBatchedStatements=true` to JDBC URL (§10)
- [ ] `.screenshots/75/before/` and `.screenshots/75/after/` directories (operator-populated during UAT)

### Mapping to Nyquist 8 Dimensions

| Dimension | Phase 75 Coverage |
|-----------|------------------|
| **1. Unit** | 24 `<Entity>RestorerTest` (insertSql shape + setter coercion); `DataImportAuditServiceTest` (REQUIRES_NEW propagation via `@MockitoSpyBean` on `PlatformTransactionManager`); `DataImportAuditSerializationTest` (LONGTEXT JSON round-trip) |
| **2. Integration (H2)** | `BackupImportExecuteIT` (happy path on H2); `BackupImportPostCommitIT` (file-system move-triple on H2 + tmpfs); `TeamRestorerIT` (2-pass restore against H2); `BackupImportRollbackIT` (D-13 failure injection on H2) |
| **3. Integration (MariaDB)** | `BackupImportMariaDbSmokeIT` — `@ActiveProfiles("local")` + CI workflow `mariadb-migration-smoke.yml` (per §7) |
| **4. Rollback / negative** | `BackupImportRollbackIT` asserts: (a) outer @Transactional rolled back DB to pre-import state, (b) audit row exists with `success=false`, (c) `uploads/` is unchanged (move-triple never fired), (d) `uploads-new/` is cleaned up |
| **5. Playwright E2E** | `BackupImportE2ETest` (Phase 74 file; extend with one new `@Test` for the real-success flash); CookieJar-reset between steps preserves Phase 74 stateless contract |
| **6. HUMAN-UAT** | `75-HUMAN-UAT.md` (D-16): operator runs Saison-2023 fixture export → wipe → import on `local` MariaDB, captures 6 vor-/nach-import screenshot pairs in `.screenshots/75/{before,after}/` (Standings R/A, R/B, Driver Ranking, PLAYOFF bracket, sub-team Phase Breakdown, driver Phase Breakdown), records PASS/FAIL |
| **7. JaCoCo coverage** | Phase 75 adds ~30 new Java classes. JaCoCo gate ≥ 82 % held by: (a) 24 small `<Entity>RestorerTest` covering insertSql + setter, (b) `DataImportAuditServiceTest` covering REQUIRES_NEW + JSON serialization, (c) ITs covering the orchestrator + listener + rollback. Excluded: NONE (all backup code stays in coverage scope) |
| **8. Security** | Carry-forward from Phase 74: profile-conditional auth + CSRF on `POST /admin/backup/import-execute` (no change — URL stable per D-17). New: `RestoreFailureInjector` extension point is locked behind `@Primary NoopRestoreFailureInjector` (production cannot inject a failing impl without a test-only `@TestConfiguration`). UPLOAD-EXTRACTION re-uses `PathTraversalGuard` from Phase 74 (verified at every entry) |

## Security Domain

(`security_enforcement` is enabled per default — not explicitly disabled in `.planning/config.json`.)

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (carry-forward) | Phase 73's profile-conditional `SecurityConfig` (prod/docker) vs `OpenSecurityConfig` (dev/local). URL unchanged — no extension needed. |
| V3 Session Management | yes (carry-forward) | Cookie-backed `FlashMap` for D-15 strings. No `@SessionAttributes` (carry-forward D-18). |
| V4 Access Control | yes | `/admin/**` requires auth on prod/docker. Phase 75 endpoint is `POST /admin/backup/import-execute` (URL stable per D-17). |
| V5 Input Validation | yes | `BackupImportConfirmForm.@AssertTrue acknowledged` (Phase 74); `BackupArchiveException` reject paths in extraction; `backupObjectMapper.FAIL_ON_UNKNOWN_PROPERTIES=true` rejects tampered JSON. |
| V6 Cryptography | no | Phase 75 does NOT introduce encryption. (Future SECU-FUT-01 considers AES-256 ZIP.) |
| V7 Error Handling | yes | Audit row + ERROR-level SLF4J log on every failure. D-15 flash strings include audit-id. |
| V8 Data Protection | yes | `executedBy` recorded from `SecurityContextHolder` so import attribution is preserved across the rollback path via REQUIRES_NEW audit row. |
| V11 Communication | no (admin-only app on local network) | — |
| V12 Files & Resources | yes | `PathTraversalGuard` on every ZipEntry during extract (carry-forward from Phase 74). `Files.move(...)` uses `ATOMIC_MOVE` to prevent half-written file states. Per-entry inflate-size cap (50 MB) inherited from Phase 74. |
| V13 Logging | yes | `log.error("Import failed for staging-id {}: ", stagingId, e)` includes full stack trace. Operator reads SLF4J log if they need details. |

### Known Threat Patterns for Spring Boot 4 + MariaDB + Hibernate 7

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| ZIP-Slip during uploads extraction | Tampering | Reuse `PathTraversalGuard.assertWithin(stagingRoot, entryName)` from Phase 74 in `BackupArchiveService.extractUploadsTo(...)` (verified per-entry) |
| ZipBomb during uploads extraction | DoS | Reuse `LimitedInputStream` per-entry inflate-size cap (50 MB) + `MAX_ENTRIES` (50_000) + `MAX_TOTAL_BYTES` (500 MB) from Phase 74 |
| SQL injection via Jackson-parsed values | Tampering | All restorer SQL is hard-coded `INSERT INTO ... VALUES (?,...)` — values bound via `PreparedStatement.setObject/setString/setTimestamp`. Jackson never touches the SQL. |
| L1-cache staleness after native DELETE | Information Disclosure | `em.flush()` + `em.clear()` (Hibernate docs); subsequent `repo.count()` for `tableCountsRestored` returns accurate counts |
| Race between two concurrent imports | Tampering / DoS | OUT OF SCOPE for Phase 75 (Phase 76 SECU-05 adds `ImportLockService`). Phase 75 assumes single-admin operational discipline. |
| Audit-row tampering | Tampering | `data_import_audit` is structurally excluded from export scope (Phase 72 IMPORT-08). Rows are insert-only (no UPDATE/DELETE from application code). |
| Backup file containing malicious JSON | Tampering | `backupObjectMapper.FAIL_ON_UNKNOWN_PROPERTIES=true` rejects unknown fields → exception → wipe-rollback → `success=false` audit row. |
| Atomic-move-failure leaves inconsistent FS | Repudiation / Information Disclosure | D-09 best-effort Step-1 revert + ERROR-level log + D-15#3 flash string with audit-id for manual recovery. |
| `executedBy` impersonation via admin-only Basic Auth | Spoofing | Phase 73's `BackupControllerSecurityIT` covers this matrix (URL stable, no extension). |

## Sources

### Primary (HIGH confidence)

- [CTC Manager codebase] `src/main/java/org/ctc/backup/service/BackupImportService.java:155-258` — `stage()` already writes `originalFilename` to `.meta` sidecar; `reparse()` reads it. Confirms §8: no in-memory staging-metadata needed.
- [CTC Manager codebase] `src/main/java/org/ctc/backup/schema/BackupSchema.java:42-50` + `EntityTopoSorter.java:62` `if (depClass.equals(ownerClass)) continue;` — Confirms §11: topo-sort already excludes `Team.parentTeam` self-edge.
- [CTC Manager codebase] `src/main/resources/db/migration/V1__initial_schema.sql:5,15,25,46,...` and `V3__add_season_phase_tables.sql` and `V7__data_import_audit.sql:18` — ALL UUID columns are typed `UUID` (portable), NOT `BINARY(16)`. Confirms §5.
- [CTC Manager codebase] `src/main/resources/application-{local,docker,prod}.yml` — JDBC URLs contain NO `rewriteBatchedStatements=true`. Confirms §10.
- [CTC Manager codebase] `src/main/java/org/ctc/domain/model/BaseEntity.java:14-27` — `@EntityListeners(AuditingEntityListener.class)` is the listener that `JdbcTemplate.batchUpdate` MUST bypass.
- [CTC Manager codebase] `.github/workflows/mariadb-migration-smoke.yml` — Existing CI MariaDB workflow shape (mariadb:11 service container + `--spring.profiles.active=local`). Confirms §7 invocation pattern for `BackupImportMariaDbSmokeIT`.
- [CTC Manager codebase] `pom.xml:8` (spring-boot 4.0.6), `pom.xml:312` (`<minimum>0.82</minimum>`), `pom.xml:273-288` (JaCoCo excludes — none of the new Phase 75 classes are excluded). Confirms §13 coverage approach.
- [Spring Framework reference] `docs.spring.io/spring-framework/reference/data-access/jdbc/advanced.html` "JDBC Batch Operations" — Idiomatic `ParameterizedPreparedStatementSetter` lambda usage with `batchSize` arg. Confirms §1.
- [Spring Framework reference] `docs.spring.io/spring-framework/reference/data-access/transaction/event.html` — `@TransactionalEventListener(phase=AFTER_COMMIT)` canonical use. Confirms §2 / §9.
- [Hibernate ORM 7 docs] `docs.hibernate.org/orm/7.0/introduction/html_single/` — UUID type mapping defaults; native UUID on MariaDB 10.7+. Confirms §5.
- [MariaDB docs] `mariadb.com/docs/server/architecture/server-constraints/foreign-key-constraints` — InnoDB checks FK constraints immediately, not deferred. Confirms §4 + Pitfall 4.
- [MariaDB docs] `mariadb.com/kb/en/uuid-data-type/` — Native UUID type since MariaDB 10.7. Confirms §5.

### Secondary (MEDIUM confidence)

- [DZone 2026 Best Practices] `dzone.com/articles/transaction-synchronization-and-spring-application` — Spring 6.1+ REQUIRES_NEW auto-enforcement on `@TransactionalEventListener` + `@Transactional`. Confirms §9.
- [thorben-janssen.com] `thorben-janssen.com/hibernate-tips-remove-entities-persistence-context/` — `em.flush()` + `em.clear()` pattern before/after bulk native queries. Confirms §3.
- [mkyong.com] `mkyong.com/spring/spring-jdbctemplate-batchupdate-example/` — `JdbcTemplate.batchUpdate` lambda style. Confirms §1.
- [concretepage.com] `concretepage.com/spring/spring-jdbctemplate-batchupdate` — Comparison of `BatchPreparedStatementSetter` vs `ParameterizedPreparedStatementSetter`. Confirms §1.
- [Baeldung] `baeldung.com/java-hibernate-uuid-primary-key` — UUID PK generation patterns in Hibernate. Confirms §5.

### Tertiary (LOW confidence — to be re-verified at plan-execution time)

- None — all critical claims are backed by primary sources.

## Metadata

**Confidence breakdown:**
- Standard stack (JdbcTemplate, AFTER_COMMIT listener, EntityManager.native, UUID storage): HIGH — verified against codebase + Spring/Hibernate/MariaDB docs.
- Architecture (per-entity restorers, 2-pass Team, single @Transactional + post-commit listener): HIGH — locked by CONTEXT D-01..D-18 + ROADMAP §"Phase 75".
- Pitfalls (L1-cache, REQUIRES_NEW, atomic-move-fallback): HIGH — backed by Hibernate + Spring docs.
- UUID storage correction (§5): HIGH — verified by direct read of `V1__initial_schema.sql` + `application-*.yml`.
- `rewriteBatchedStatements` correction (§10): HIGH — verified by grep across all application yml files.
- Validation Architecture (Nyquist 8 dimensions): HIGH — every dimension has a concrete test class name + automated command.
- Open Questions (#1..#4): MEDIUM — these are gaps the planner closes during plan drafting, not blockers.
- Assumptions A1..A5: LOW — verifiable at plan-execution time; none block planning.

**Research date:** 2026-05-14
**Valid until:** 2026-06-13 (30 days — stable stack; no dependencies on fast-moving libraries)

---

## Project Constraints (from CLAUDE.md)

- **Language:** UI texts + documentation + code + comments in English. (User communication is German.)
- **Test coverage:** ≥ 82 % line coverage (pom.xml:312).
- **Flyway:** Phase 75 ships ZERO new migrations — V7 from Phase 72 is sufficient.
- **Profiles:** Auth only for `prod`/`docker`; `dev`/`local` remains without auth. Phase 75 does not change this.
- **OSIV:** Remains enabled. Phase 75 uses `@EntityGraph` only for the existing `repo.count()` calls used in the audit-counts read; no extension.
- **Playwright:** Remains compile-scope. Phase 75 extends `BackupImportE2ETest` (`-Pe2e` Failsafe IT) + uses `playwright-cli` for HUMAN-UAT screenshots.
- **No inline styles:** D-17 says no new templates — N/A for Phase 75.
- **TDD per `superpowers:test-driven-development`:** Plans must follow Red→Green→Refactor; 24 `<Entity>RestorerTest` written FIRST, then the restorer impls.
- **Subagent rules:** Implementation subagents must use `model: opus` or `model: sonnet`. Never haiku for code.
- **Test naming:** Given-When-Then BDD pattern, `// given` / `// when` / `// then` comments.
- **Test data isolation:** Saison 2023 IS the demo data (intentional, ROADMAP-locked) — does NOT need T-prefix isolation. The CI smoke IT and HUMAN-UAT both use the existing `DevDataSeeder` Saison-2023 fixture.
- **Test calls optimized:** No multiple `mvnw verify` — gated `-Dtest=` and `-Dit.test=` per task commit, ONE final `verify -Pe2e` at phase gate.
- **Wave-pause:** `/gsd-execute-phase` waits for user feedback after every wave merge (project memory `feedback_wave_pause.md`).
- **No `git stash` / `git checkout` / `git reset` in subagent prompts.** Branch protection per `feedback_subagent_stability.md`. Branch for Phase 75 milestone is `gsd/v1.10-platform-and-backup` (already active per project STATUS).

## Project Skills

Skills available in `.claude/skills/` and `.agents/skills/`:
- **`claude-md-improver`** — for CLAUDE.md updates if Phase 75 surfaces a new convention (unlikely; Phase 75 follows all existing conventions).
- **`playwright-cli`** — REQUIRED for HUMAN-UAT (D-16): operator captures the 6 screenshot pairs via `playwright-cli open http://localhost:9091/...`. Skill SKILL.md should be consulted by planner to lock the exact command shape per memory `feedback_playwright_cli.md`.
- **`gsd-auto-uat`** — Available for the HUMAN-UAT screenshot capture step. Project memory `feedback_auto_uat_reminder.md` says: "Bei UI-lastigen Phasen aktiv vorschlagen statt manuell playwright-cli". Phase 75 has the 6-screenshot UAT — planner should propose `gsd-auto-uat` invocation.

---

## RESEARCH COMPLETE
