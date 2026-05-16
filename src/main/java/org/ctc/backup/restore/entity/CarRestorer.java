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
 * {@link EntityRestorer} for {@link org.ctc.domain.model.Car}.
 *
 * <p>GT7 reference-data leaf entity: no foreign keys. V1 schema columns
 * ({@code V1__initial_schema.sql}):
 * <ul>
 *   <li>{@code id} (UUID PK)</li>
 *   <li>{@code manufacturer} (VARCHAR NOT NULL)</li>
 *   <li>{@code name} (VARCHAR NOT NULL)</li>
 *   <li>{@code gt7_id} (VARCHAR, nullable)</li>
 *   <li>{@code image_url} (VARCHAR(500), nullable)</li>
 *   <li>{@code created_at} (TIMESTAMP)</li>
 *   <li>{@code updated_at} (TIMESTAMP)</li>
 * </ul>
 *
 * <p>Single-pass insert via {@link JdbcTemplate#batchUpdate(String, java.util.Collection, int,
 * org.springframework.jdbc.core.ParameterizedPreparedStatementSetter)} — bypasses
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener} so the imported
 * {@code created_at} / {@code updated_at} values survive verbatim.
 */
@Component
@Slf4j
public class CarRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO cars (id, manufacturer, name, gt7_id, image_url, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final int BATCH_SIZE = 500;

    @Override
    public String tableName() {
        return "cars";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        if (rows.isEmpty()) {
            log.debug("CarRestorer: skipping batchUpdate — no rows");
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setString(2, row.get("manufacturer").asText());
            ps.setString(3, row.get("name").asText());
            nullableString(ps, 4, row, "gt7Id");
            nullableString(ps, 5, row, "imageUrl");
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("CarRestorer: inserted {} car rows", rows.size());
    }

    /**
     * Binds a nullable VARCHAR column from a JSON field that may be missing OR explicitly
     * {@code null}.
     */
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
