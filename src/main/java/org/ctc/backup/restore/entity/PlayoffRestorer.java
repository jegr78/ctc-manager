package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 75 / Plan 05 — {@link EntityRestorer} for {@link org.ctc.domain.model.Playoff}.
 *
 * <p>V1 schema columns ({@code V1__initial_schema.sql} lines 163-174) extended by V3
 * ({@code V3__add_season_phase_tables.sql} line 56 — {@code phase_id UUID}):
 * <ul>
 *   <li>{@code id} (UUID PK)</li>
 *   <li>{@code season_id} (UUID NOT NULL, V1)</li>
 *   <li>{@code phase_id} (UUID, V3 — additive, currently nullable per V3 strategy)</li>
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
 * {@code row.get("phase").asText()}. The legacy V1 {@code season_id} column is filled from a
 * companion {@code seasonId} field that the export wire emits explicitly.
 *
 * <p>Single-pass insert — no FK self-cycle. Bypasses {@link org.ctc.domain.model.BaseEntity}'s
 * {@code AuditingEntityListener} per Phase 75 goal.
 */
@Component
@Slf4j
public class PlayoffRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO playoffs (id, season_id, phase_id, name, start_date, end_date, "
                    + "event_duration_minutes, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            ps.setObject(2, UUID.fromString(row.get("seasonId").asText()));
            ps.setObject(3, UUID.fromString(row.get("phase").asText()));
            ps.setString(4, row.get("name").asText());
            nullableDate(ps, 5, row, "startDate");
            nullableDate(ps, 6, row, "endDate");
            nullableInt(ps, 7, row, "eventDurationMinutes");
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
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
