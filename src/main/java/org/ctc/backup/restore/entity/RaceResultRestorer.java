package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 75 / Plan 04 — restores rows into the {@code race_results} table from the
 * {@code data/race-results.json} array in a backup ZIP.
 *
 * <p>HOTTEST PATH of the entire restore on the Saison-2023 fixture (~1000 rows). The setter
 * body must stay lean — no per-row reflection, no allocations beyond the four UUIDs and two
 * timestamps per row.
 *
 * <p>Schema (V1): {@code id UUID PK, race_id UUID NOT NULL, driver_id UUID NOT NULL,
 * position INT NOT NULL, quali_position INT NOT NULL, fastest_lap BOOLEAN NOT NULL,
 * points_race INT NOT NULL, points_quali INT NOT NULL, points_fl INT NOT NULL,
 * points_total INT NOT NULL, created_at TIMESTAMP, updated_at TIMESTAMP}.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceResultRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO race_results (id, race_id, driver_id, position, quali_position, "
          + "fastest_lap, points_race, points_quali, points_fl, points_total, "
          + "created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "race_results";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("race").asText()));
            ps.setObject(3, UUID.fromString(row.get("driver").asText()));
            ps.setInt(4, row.get("position").asInt());
            ps.setInt(5, row.get("qualiPosition").asInt());
            ps.setBoolean(6, row.get("fastestLap").asBoolean());
            ps.setInt(7, row.get("pointsRace").asInt());
            ps.setInt(8, row.get("pointsQuali").asInt());
            ps.setInt(9, row.get("pointsFl").asInt());
            ps.setInt(10, row.get("pointsTotal").asInt());
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("RaceResultRestorer: restored {} rows", rows.size());
    }
}
