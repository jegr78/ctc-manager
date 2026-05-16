package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores rows into the {@code matches} table from the
 * {@code data/matches.json} array in a backup ZIP.
 *
 * <p>Schema (V1): {@code id UUID PK, matchday_id UUID NOT NULL, home_team_id UUID NOT NULL,
 * away_team_id UUID NULL, home_score INT NULL, away_score INT NULL,
 * bye BOOLEAN NOT NULL, created_at TIMESTAMP, updated_at TIMESTAMP}.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO matches (id, matchday_id, home_team_id, away_team_id, "
          + "home_score, away_score, bye, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "matches";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            setNullableUuid(ps, 2, row, "matchday");
            setNullableUuid(ps, 3, row, "homeTeam");
            setNullableUuid(ps, 4, row, "awayTeam");
            setNullableInt(ps, 5, row, "homeScore");
            setNullableInt(ps, 6, row, "awayScore");
            ps.setBoolean(7, row.get("bye").asBoolean());
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("MatchRestorer: restored {} rows", rows.size());
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

    private static void setNullableInt(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, n.asInt());
        }
    }
}
