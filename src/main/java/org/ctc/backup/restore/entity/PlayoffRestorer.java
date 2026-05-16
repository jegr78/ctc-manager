package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link EntityRestorer} for {@link org.ctc.domain.model.Playoff}.
 *
 * <p>V6 schema (after {@code V6__CleanupLegacySeasonColumns.java} drops {@code season_id}):
 * <ul>
 *   <li>{@code id} (UUID PK)</li>
 *   <li>{@code phase_id} (UUID NOT NULL — flipped in V4)</li>
 *   <li>{@code name} (VARCHAR NOT NULL)</li>
 *   <li>{@code start_date} (DATE, nullable)</li>
 *   <li>{@code end_date} (DATE, nullable)</li>
 *   <li>{@code event_duration_minutes} (INT, nullable)</li>
 *   <li>{@code created_at} (TIMESTAMP)</li>
 *   <li>{@code updated_at} (TIMESTAMP)</li>
 * </ul>
 *
 * <p>{@code Playoff.phase} is rendered by {@link org.ctc.backup.serialization.PlayoffMixIn} as a
 * raw UUID string via {@code @JsonIdentityReference(alwaysAsId=true)} — accessed via
 * {@code row.get("phase").asText()}. The V1 {@code season_id} column was dropped by V6 so
 * the INSERT omits it.
 *
 * <p>Single-pass insert — no FK self-cycle. Bypasses {@link org.ctc.domain.model.BaseEntity}'s
 * {@code AuditingEntityListener}.
 */
@Slf4j
@Component
public class PlayoffRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO playoffs (id, phase_id, name, start_date, end_date, "
                    + "event_duration_minutes, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final int BATCH_SIZE = 500;

    @Override
    public String tableName() {
        return "playoffs";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        if (rows.isEmpty()) {
            log.debug("PlayoffRestorer: skipping batchUpdate — no rows");
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("phase").asText()));
            ps.setString(3, row.get("name").asText());
            nullableDate(ps, 4, row, "startDate");
            nullableDate(ps, 5, row, "endDate");
            nullableInt(ps, 6, row, "eventDurationMinutes");
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("PlayoffRestorer: inserted {} playoff rows", rows.size());
    }

    private static void nullableDate(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode value = row.get(field);
        if (value == null || value.isNull()) {
            ps.setNull(idx, Types.DATE);
        } else {
            ps.setDate(idx, Date.valueOf(LocalDate.parse(value.asText())));
        }
    }

    private static void nullableInt(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode value = row.get(field);
        if (value == null || value.isNull()) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value.asInt());
        }
    }
}
