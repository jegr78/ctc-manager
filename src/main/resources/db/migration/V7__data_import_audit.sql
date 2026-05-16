-- Phase 72: data_import_audit table — operational audit log for backup imports.
-- This table is PERMANENTLY OUT OF EXPORT SCOPE (IMPORT-08, see PROJECT.md Decisions row).
-- Phase 75 writes rows here as part of the import @Transactional path; Phase 72 ships the
-- table empty and the JPA entity inert.
--
-- Column type rationale (Phase 72 D-09):
--   - id UUID:                  portable across H2 2.x + MariaDB 10.7+ (UUID is native on both).
--   - LONGTEXT (not JSON):      MariaDB JSON is itself a LONGTEXT alias with CHECK JSON_VALID();
--                               H2's JSON validates differently. Keeping textual avoids dialect drift.
--                               Jackson serialization enforces the JSON shape at write time, not DDL.
--   - TIMESTAMP NOT NULL:       portable; H2 and MariaDB both accept and store as DATETIME-equivalent.
--   - BOOLEAN:                  H2 native; MariaDB stores as TINYINT(1) — both accept the keyword.
--
-- Compatible with H2 2.x and MariaDB 10.7+.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

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

-- Single index on the most plausible query column (admin history view in a future milestone).
-- FK indexes are not needed: this table has no FKs.
CREATE INDEX idx_data_import_audit_executed_at ON data_import_audit (executed_at);
