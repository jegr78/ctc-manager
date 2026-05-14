package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 75 / Plan 04 — restores rows into the {@code races} table from the
 * {@code data/races.json} array in a backup ZIP.
 *
 * <p>Schema (V1): {@code id UUID PK, matchday_id UUID NOT NULL, match_id UUID NULL,
 * playoff_matchup_id UUID NULL, track_id UUID NULL, car_id UUID NULL,
 * home_team_id UUID NULL, away_team_id UUID NULL, date_time TIMESTAMP NULL,
 * calendar_event_id VARCHAR NULL, created_at TIMESTAMP, updated_at TIMESTAMP}.
 *
 * <p>JSON FK property naming (per Phase 73 {@code RaceMixIn}): {@code matchday}, {@code match},
 * {@code playoffMatchup}, {@code track}, {@code car}, {@code homeTeamOverride},
 * {@code awayTeamOverride}. The entity-level overrides bind into the schema's
 * {@code home_team_id}/{@code away_team_id} columns (no DDL rename).
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO races (id, matchday_id, match_id, playoff_matchup_id, "
          + "track_id, car_id, home_team_id, away_team_id, "
          + "date_time, calendar_event_id, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "races";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            setNullableUuid(ps, 2, row, "matchday");
            setNullableUuid(ps, 3, row, "match");
            setNullableUuid(ps, 4, row, "playoffMatchup");
            setNullableUuid(ps, 5, row, "track");
            setNullableUuid(ps, 6, row, "car");
            setNullableUuid(ps, 7, row, "homeTeamOverride");
            setNullableUuid(ps, 8, row, "awayTeamOverride");
            setNullableTimestamp(ps, 9, row, "dateTime");
            setNullableString(ps, 10, row, "calendarEventId");
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("RaceRestorer: restored {} rows", rows.size());
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

    private static void setNullableString(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, n.asText());
        }
    }
}
