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
 * Phase 75 / Plan 04 — restores rows into the {@code race_scorings} table from the
 * {@code data/race-scorings.json} array in a backup ZIP.
 *
 * <p>Schema (V1): {@code id UUID PK, name VARCHAR UNIQUE, race_points VARCHAR(500) NOT NULL,
 * quali_points VARCHAR(500) NULL, fastest_lap_points INT NOT NULL, created_at TIMESTAMP,
 * updated_at TIMESTAMP}.
 *
 * <p>CRITICAL: {@code race_points} and {@code quali_points} are {@code VARCHAR(500)} storing
 * comma-separated integers verbatim (e.g., {@code "25,18,15,12,10,8,6,4,2,1"} —
 * see {@link org.ctc.domain.model.RaceScoring#getRacePointsArray()}). They MUST be bound via
 * {@code setString} — never numeric coercion — so the VARCHAR string survives the round-trip.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceScoringRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO race_scorings (id, name, race_points, quali_points, "
          + "fastest_lap_points, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "race_scorings";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setString(2, row.get("name").asText());
            // race_points is VARCHAR(500) storing comma-separated integers verbatim — setString only.
            ps.setString(3, row.get("racePoints").asText());
            setNullableString(ps, 4, row, "qualiPoints");
            ps.setInt(5, row.get("fastestLapPoints").asInt());
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("RaceScoringRestorer: restored {} rows", rows.size());
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
