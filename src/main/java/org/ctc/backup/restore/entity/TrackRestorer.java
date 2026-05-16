package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link EntityRestorer} for {@link org.ctc.domain.model.Track}.
 *
 * <p>GT7 reference-data leaf entity: no foreign keys. V1 schema columns
 * ({@code V1__initial_schema.sql}):
 * <ul>
 *   <li>{@code id} (UUID PK)</li>
 *   <li>{@code name} (VARCHAR NOT NULL UNIQUE)</li>
 *   <li>{@code country} (VARCHAR(100), nullable)</li>
 *   <li>{@code image_url} (VARCHAR(500), nullable)</li>
 *   <li>{@code created_at} (TIMESTAMP)</li>
 *   <li>{@code updated_at} (TIMESTAMP)</li>
 * </ul>
 *
 * <p>Single-pass insert — mirrors {@link CarRestorer}. Bypasses
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}.
 */
@Component
@Slf4j
public class TrackRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO tracks (id, name, country, image_url, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final int BATCH_SIZE = 500;

    @Override
    public String tableName() {
        return "tracks";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        if (rows.isEmpty()) {
            log.debug("TrackRestorer: skipping batchUpdate — no rows");
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setString(2, row.get("name").asText());
            nullableString(ps, 3, row, "country");
            nullableString(ps, 4, row, "imageUrl");
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("TrackRestorer: inserted {} track rows", rows.size());
    }

    private static void nullableString(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode value = row.get(field);
        if (value == null || value.isNull()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value.asText());
        }
    }
}
