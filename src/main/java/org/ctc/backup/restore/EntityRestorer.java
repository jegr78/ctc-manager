package org.ctc.backup.restore;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * SPI — restores a single entity's JSON-array slice into its target table.
 *
 * <p>Implementations live under {@code org.ctc.backup.restore.entity.<Entity>Restorer} and are
 * discovered as Spring {@code @Component} beans. The orchestrator
 * ({@code BackupImportService.execute(...)}) iterates
 * {@link org.ctc.backup.schema.BackupSchema#getExportOrder()}, looks each
 * {@link org.ctc.backup.schema.EntityRef#tableName()} up in a {@code Map<String, EntityRestorer>}
 * keyed by {@link #tableName()}, and invokes {@link #restore(List, JdbcTemplate)} per entity
 * batch.
 *
 * <p>The interface is intentionally collapsed to a single {@code restore(rows, jdbcTemplate)}
 * method. The original three-method shape ({@code tableName()} + {@code insertSql()} +
 * {@code setter()}) cannot model the 2-pass {@code TeamRestorer}, which needs two SQL strings
 * and two setters to break the {@code parent_team_id} self-FK cycle. Hiding the
 * per-implementation discipline behind a single {@code restore} call keeps the orchestrator
 * generic; per-{@code Restorer} unit tests assert on the emitted SQL via a spy
 * {@link JdbcTemplate}.
 *
 * <p>The {@code JdbcTemplate} parameter (not a field) lets the orchestrator pass the
 * transaction-bound template into every batch and keeps each {@code Restorer} stateless and
 * trivially mock-testable.
 *
 * <p>Implementations MUST NOT call any JPA repository {@code save}/{@code saveAll}/{@code merge}
 * on the restore path: the entire point of the {@code JdbcTemplate.batchUpdate} detour is to
 * bypass {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener} so that
 * imported {@code created_at} / {@code updated_at} values survive verbatim.
 *
 * <p>Implementations are plain Java classes — this is a plain interface (a 24-permits sealed
 * type would be over-engineering for a feature touched once per milestone).
 */
public interface EntityRestorer {

    /**
     * Returns the snake_case table name of the entity this restorer owns.
     *
     * <p>The returned value MUST equal the corresponding
     * {@link org.ctc.backup.schema.EntityRef#tableName()} entry produced by
     * {@link org.ctc.backup.schema.BackupSchema#getExportOrder()} so the orchestrator
     * lookup keyed by table name resolves without ambiguity.
     *
     * @return snake_case table name, never {@code null}
     */
    String tableName();

    /**
     * Restores the given rows into this entity's target table using bulk JDBC.
     *
     * <p>Default discipline (24 of 24 implementations): build the {@code INSERT INTO ... VALUES
     * (?, ?, ...)} template as a {@code private static final} constant inside the implementation
     * and call {@link JdbcTemplate#batchUpdate(String, java.util.List, int,
     * org.springframework.jdbc.core.ParameterizedPreparedStatementSetter)} (batch size = 500).
     * {@code TeamRestorer} is the only 2-pass implementation: pass-1 inserts every row with
     * {@code parent_team_id = NULL}; pass-2 issues {@code UPDATE teams SET parent_team_id = ?
     * WHERE id = ?} for every row whose source JSON had a non-null {@code parent_team_id}.
     *
     * @param rows         the JSON rows to restore, in source-file order (never {@code null};
     *                     may be empty)
     * @param jdbcTemplate the transaction-bound template the orchestrator owns; never
     *                     {@code null}
     */
    void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate);
}
