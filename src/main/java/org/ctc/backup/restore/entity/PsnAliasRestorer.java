package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores rows into the {@code psn_aliases} table from the
 * {@code data/psn-aliases.json} array in a backup ZIP.
 *
 * <p>Schema reference (V1__initial_schema.sql):
 * <pre>
 * CREATE TABLE psn_aliases (
 *   id UUID PRIMARY KEY,
 *   driver_id UUID NOT NULL,
 *   alias VARCHAR(255) NOT NULL UNIQUE,
 *   created_at TIMESTAMP, updated_at TIMESTAMP,
 *   FK driver_id REFERENCES drivers(id) ON DELETE CASCADE);
 * </pre>
 *
 * <p>JSON shape (from {@code PsnAliasMixIn}): FK field {@code driver} renders as bare UUID
 * string via {@code @JsonIdentityReference(alwaysAsId=true)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PsnAliasRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO psn_aliases (id, driver_id, alias, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "psn_aliases";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setObject(2, UUID.fromString(row.get("driver").asText()));
                    ps.setString(3, row.get("alias").asText());
                    ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("PsnAliasRestorer: restored {} rows", rows.size());
    }
}
