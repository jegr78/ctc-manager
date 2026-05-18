package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores rows into the {@code drivers} table from the
 * {@code data/drivers.json} array in a backup ZIP.
 *
 * <p>Schema reference (V1__initial_schema.sql):
 * <pre>
 * CREATE TABLE drivers (
 *   id UUID PRIMARY KEY,
 *   psn_id VARCHAR(255) NOT NULL UNIQUE,
 *   nickname VARCHAR(255) NOT NULL,
 *   active BOOLEAN NOT NULL DEFAULT TRUE,
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * );
 * </pre>
 *
 * <p>JSON shape (from {@code DriverMixIn}): {@code {"id":"<uuid>","psnId":"...","nickname":"...",
 * "active":true,"createdAt":"<iso>","updatedAt":"<iso>"}}.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener} does NOT overwrite
 * the imported {@code createdAt}/{@code updatedAt} values (round-trip fidelity).
 */
@Slf4j
@Component
public class DriverRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO drivers (id, psn_id, nickname, active, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "drivers";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setString(2, row.get("psnId").asText());
                    ps.setString(3, row.get("nickname").asText());
                    ps.setBoolean(4, row.get("active").asBoolean());
                    ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("DriverRestorer: restored {} rows", rows.size());
    }
}
