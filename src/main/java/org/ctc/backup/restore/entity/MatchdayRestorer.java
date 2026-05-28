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
 * Restores rows into the {@code matchdays} table from the
 * {@code data/matchdays.json} array in a backup ZIP.
 *
 * <p>Schema:
 * <ul>
 *   <li>V1 + V3: {@code id UUID PK, phase_id UUID NOT NULL, group_id UUID NULL,
 *       label VARCHAR NOT NULL, sort_index INT NOT NULL, created_at TIMESTAMP,
 *       updated_at TIMESTAMP}.</li>
 *   <li>V15: {@code pick_deadline TIMESTAMP NULL, scheduled_weekend VARCHAR(64) NULL}.</li>
 * </ul>
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
public class MatchdayRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO matchdays (id, phase_id, group_id, label, sort_index, "
          + "created_at, updated_at, pick_deadline, scheduled_weekend) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "matchdays";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            setNullableUuid(ps, 2, row, "phase");
            setNullableUuid(ps, 3, row, "group");
            ps.setString(4, row.get("label").asText());
            ps.setInt(5, row.get("sortIndex").asInt());
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
            setNullableTimestamp(ps, 8, row, "pickDeadline");
            ps.setString(9, nullableString(row, "scheduledWeekend"));
        });
        log.debug("MatchdayRestorer: restored {} rows", rows.size());
    }

    private static void setNullableUuid(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.OTHER);
        } else {
            ps.setObject(idx, UUID.fromString(n.asText()));
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(idx, Timestamp.valueOf(LocalDateTime.parse(n.asText())));
        }
    }

    private static String nullableString(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
