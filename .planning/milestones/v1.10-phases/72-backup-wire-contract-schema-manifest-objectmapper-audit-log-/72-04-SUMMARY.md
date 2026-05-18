---
phase: 72
plan: 04
subsystem: backup
tags: [v1.10, backup, flyway, audit-log, migration, schema]
requires:
  - "Flyway baseline V1-V6 applied on H2 + MariaDB"
  - "BackupSchema component from plan 01 (D-06 package-name filter)"
  - "BackupSchemaExclusionIT FQN-string stub from plan 01"
provides:
  - "data_import_audit table (8 columns + 1 index) on H2 (dev/test) and MariaDB (local/docker/prod)"
  - "DataImportAudit JPA entity in org.ctc.backup.audit (Lombok class, NOT BaseEntity, NOT record)"
  - "DataImportAuditRepository stock JpaRepository<DataImportAudit, UUID>"
  - "V7DataImportAuditMigrationIT — 3-test Failsafe IT (column-set, NOT NULL, index)"
  - "BackupSchemaExclusionIT tightened to direct-import + DataImportAudit.class assertion"
affects:
  - "Phase 75 IMPORT-05 (will @Autowired DataImportAuditRepository.save(...) for audit-row writes)"
  - "IMPORT-08 enforcement is now structurally provable end-to-end via DataImportAudit.class lookup"
tech-stack:
  added: []
  patterns:
    - "Pure-SQL Flyway forward migration (mirrors V1/V2/V3 pattern; D-10 — no Java variant)"
    - "Lombok @Entity with @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString"
    - "@SpringBootTest(classes = CtcManagerApplication.class) for db.migration package ITs (V4 analog)"
    - "JDBC DatabaseMetaData.getColumns/getIndexInfo for portable post-migration schema inspection"
key-files:
  created:
    - "src/main/resources/db/migration/V7__data_import_audit.sql"
    - "src/main/java/org/ctc/backup/audit/DataImportAudit.java"
    - "src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java"
    - "src/test/java/db/migration/V7DataImportAuditMigrationIT.java"
  modified:
    - "src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java"
decisions:
  - "Dropped @Lob from tableCountsWiped/tableCountsRestored (Rule 1 bug fix — RESEARCH P-4 was incomplete)"
  - "Kept columnDefinition = \"LONGTEXT\" on both JSON-text fields (MariaDB native; H2 maps to VARCHAR)"
metrics:
  completed: 2026-05-11
  duration: "~12 min (incl. one Rule 1 auto-fix iteration)"
  tasks: 2
  files_created: 4
  files_modified: 1
---

# Phase 72 Plan 04: Wire Flyway V7 + DataImportAudit Entity + Repository Summary

Ship the persistence side of the backup audit log — Flyway V7 migration creates `data_import_audit` (8 columns + 1 index, H2 + MariaDB portable), the `DataImportAudit` Lombok JPA entity in `org.ctc.backup.audit` (NOT extending `BaseEntity` per D-08, NOT a record per RESEARCH P-1), and the stock `DataImportAuditRepository` Phase 75 will write into. The plan-01 `BackupSchemaExclusionIT` is tightened from an FQN-string lookup to a direct `import org.ctc.backup.audit.DataImportAudit;` + `doesNotContain(DataImportAudit.class)` assertion — IMPORT-08 D-06 enforcement is now end-to-end provable against the class object.

## What Was Built

**Task 1 — Wave 0 RED stub (`ce8293f`)**
- `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` — 3 tests:
  1. `givenH2WithV7Applied_whenInspectingDataImportAuditColumns_thenAllExpectedColumnsExist` — exact 8-column set via `DatabaseMetaData.getColumns(...)`.
  2. `givenH2WithV7Applied_whenInspectingExecutedAtColumn_thenItIsNotNullable` — `NULLABLE == columnNoNulls`.
  3. `givenH2WithV7Applied_whenInspectingIndex_thenExecutedAtIndexExists` — `idx_data_import_audit_executed_at` on `EXECUTED_AT` via `DatabaseMetaData.getIndexInfo(...)`.
- Bootstrap: `@SpringBootTest(classes = CtcManagerApplication.class) @ActiveProfiles("dev")` — mandatory because `db.migration` is outside the `org.ctc.*` component-scan tree (mirrors `V4MigrationSmokeIT`).
- RED confirmed: 3/3 failing because table did not exist yet.

**Task 2 — V7 + entity + repository + exclusion-IT tightening (`9b45d33`)**

`V7__data_import_audit.sql` (pure SQL, D-10):
```sql
CREATE TABLE data_import_audit (
    id UUID PRIMARY KEY,
    executed_at TIMESTAMP NOT NULL,
    executed_by VARCHAR(255) NOT NULL,
    schema_version INT NOT NULL,
    table_counts_wiped LONGTEXT NOT NULL,
    table_counts_restored LONGTEXT NOT NULL,
    source_filename VARCHAR(255) NOT NULL,
    success BOOLEAN NOT NULL
);
CREATE INDEX idx_data_import_audit_executed_at ON data_import_audit (executed_at);
```
Header comment `-- Compatible with H2 2.x and MariaDB 10.7+` mirrors V1/V3 convention.

`DataImportAudit` Lombok class in `org.ctc.backup.audit`:
- `@Entity @Table(name = "data_import_audit") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString`
- Does **NOT** extend `BaseEntity` (D-08 + RESEARCH §Pattern 5 — auditing-listener bypass for Phase 75 IMPORT-05).
- **NOT** a Java record (RESEARCH P-1 — Hibernate 7 cannot proxy records).
- 8 fields: `id UUID`, `executedAt Instant`, `executedBy String`, `schemaVersion int`, `tableCountsWiped String`, `tableCountsRestored String`, `sourceFilename String`, `success boolean`.
- JavaDoc explicitly documents the three structural choices (no `BaseEntity`, not a record, lives in `org.ctc.backup.audit`).

`DataImportAuditRepository`:
- `extends JpaRepository<DataImportAudit, UUID>` — no custom finders. Phase 75 calls only `save(...)`.
- Co-located with the entity in `org.ctc.backup.audit` (NOT `org.ctc.domain.repository`) — keeps the IMPORT-08 package-filter as the single source of truth (D-06).

`BackupSchemaExclusionIT` tightening:
- Removed `DATA_IMPORT_AUDIT_FQN` / `DATA_IMPORT_AUDIT_TABLE` string constants.
- Added `import org.ctc.backup.audit.DataImportAudit;`.
- Assertion now: `assertThat(exportOrder).extracting(EntityRef::entityClass).doesNotContain(DataImportAudit.class)` (defense-in-depth `tableName` assertion preserved).

## Verification

```
./mvnw -Dit.test='V7DataImportAuditMigrationIT,BackupSchemaExclusionIT,BackupSchemaTopologyIT' \
       -Dsurefire.skip=true verify -Pe2e
```

Result: **BUILD SUCCESS** — all 8 IT tests GREEN:
- `BackupSchemaTopologyIT`: 4/4 (24-entity scope intact — `DataImportAudit` NOT counted)
- `BackupSchemaExclusionIT`: 1/1 (direct-import exclusion assertion GREEN)
- `V7DataImportAuditMigrationIT`: 3/3 (column-set, NOT NULL, index)

Coverage check: `[INFO] All coverage checks have been met.` (≥82% gate satisfied).

Acceptance-criteria grep checks all PASS (V7 SQL contains `CREATE TABLE data_import_audit` + all 8 column declarations + the `CREATE INDEX` line + the `Compatible with H2 2.x and MariaDB 10.7+` header; entity has all 8 Lombok annotations, no `extends BaseEntity`, no `public record`, is `public class DataImportAudit`; repository has `extends JpaRepository<DataImportAudit, UUID>`; exclusion IT has the new direct-import + `doesNotContain(DataImportAudit.class)` assertions and no `DATA_IMPORT_AUDIT_FQN` constant).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Dropped `@Lob` from `tableCountsWiped` and `tableCountsRestored`**

- **Found during:** Task 2 verification — Hibernate schema validation crashed with `SchemaManagementException: Schema validation: wrong column type encountered in column [table_counts_restored] in table [data_import_audit]; found [character varying (Types#VARCHAR)], but expecting [longtext (Types#CLOB)]`.
- **Root cause:** RESEARCH P-4 stated `LONGTEXT` is portable across H2 2.x + MariaDB. That is true for **DDL acceptance** (Flyway runs cleanly on both DBs), but **not** for Hibernate strict schema validation: H2 maps `LONGTEXT` to a `CHARACTER VARYING (VARCHAR)` column at the metadata level, while `@Lob` on the entity forces Hibernate to expect a `CLOB` family type. The DDL succeeds; the validator fails.
- **Fix:** Removed both `@Lob` annotations (and the unused `import jakarta.persistence.Lob;`). Retained `columnDefinition = "LONGTEXT"` on both `@Column` declarations so the MariaDB DDL semantics stay unambiguous. On H2 the column is `VARCHAR` (sufficient for JSON-text storage; the schema-validator now matches the entity's plain `String` type without LOB hinting). Inline comments document the H2 vs MariaDB type-resolution difference.
- **Files modified:** `src/main/java/org/ctc/backup/audit/DataImportAudit.java`.
- **Commit:** `9b45d33` (Task 2; deviation logged in commit body).
- **Threat model impact:** none — Jackson serialization still owns the JSON-shape contract at write time; column-level type changes only affect storage representation, not semantics.

### Other deviations

None — V7 SQL header comment, column ordering, index name, and the entity field order all match the plan verbatim. The exclusion-IT tightening followed the plan's prescribed code shape exactly.

## File Inventory

**Created (4):**
- `src/main/resources/db/migration/V7__data_import_audit.sql` (31 lines, pure SQL)
- `src/main/java/org/ctc/backup/audit/DataImportAudit.java` (86 lines)
- `src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java` (20 lines)
- `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` (100 lines, 3 IT tests)

**Modified (1):**
- `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` (FQN-string → direct-import + by-class assertion)

## Pointers Forward

- **Phase 75 (IMPORT-05 — Replace-All Transaction):** Will `@Autowired DataImportAuditRepository` and call `save(DataImportAudit.builder()....build())` exactly once per import attempt (success or failure path). `BaseEntity` is deliberately absent so `AuditingEntityListener` does not interfere with the manually-set `executedAt`.
- **Phase 73 (EXPORT-04 — Jackson MixIns):** No interaction — `DataImportAudit` is structurally excluded from `BackupSchema.exportOrder` (D-06), so no MixIn is ever registered for it. The 24-entity export-scope count holds.
- **v1.11+ (audit-history admin UI):** `DataImportAuditRepository` is ready to back `findTop10ByOrderByExecutedAtDesc()` or similar — out of v1.10 scope per CONTEXT §Claude's Discretion.
- **Per-DB column-type follow-up:** the H2 `VARCHAR` resolution of `LONGTEXT` works for v1.10 because JSON payloads in `table_counts_wiped`/`table_counts_restored` are bounded by the entity inventory (24 entries × ~30 chars ≈ 1 KB). If Phase 75 ever decides to dump larger payloads, the column may need a `TEXT` (H2) / `LONGTEXT` (MariaDB) split via a dialect-aware Java migration — but the SCHEMA_VERSION bump mechanism handles that cleanly.

## Self-Check: PASSED

- [x] `src/main/resources/db/migration/V7__data_import_audit.sql` — FOUND
- [x] `src/main/java/org/ctc/backup/audit/DataImportAudit.java` — FOUND
- [x] `src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java` — FOUND
- [x] `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` — FOUND
- [x] `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` — MODIFIED (FQN-string removed; direct import + by-class assertion present)
- [x] Commit `ce8293f` (Task 1) — FOUND in `git log`
- [x] Commit `9b45d33` (Task 2) — FOUND in `git log`
- [x] `./mvnw -Dit.test=V7DataImportAuditMigrationIT,BackupSchemaExclusionIT,BackupSchemaTopologyIT -Dsurefire.skip=true verify -Pe2e` → BUILD SUCCESS (8/8 GREEN)
- [x] JaCoCo coverage check passed
