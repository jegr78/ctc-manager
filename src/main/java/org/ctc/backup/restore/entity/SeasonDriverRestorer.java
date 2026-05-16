package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores rows into the {@code season_drivers} table from the
 * {@code data/season-drivers.json} array in a backup ZIP.
 *
 * <p>Schema reference (V1__initial_schema.sql):
 * <pre>
 * CREATE TABLE season_drivers (
 *   id UUID PRIMARY KEY,
 *   season_id UUID NOT NULL,
 *   driver_id UUID NOT NULL,
 *   team_id UUID NOT NULL,              (FK to teams, NOT season_teams)
 *   created_at TIMESTAMP, updated_at TIMESTAMP,
 *   CONSTRAINT uk_season_driver UNIQUE (season_id, driver_id));
 * </pre>
 *
 * <p>JSON shape (from {@code SeasonDriverMixIn}): all three FK fields render as bare UUID
 * strings via {@code @JsonIdentityReference(alwaysAsId=true)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonDriverRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO season_drivers (id, season_id, driver_id, team_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "season_drivers";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setObject(2, UUID.fromString(row.get("season").asText()));
                    ps.setObject(3, UUID.fromString(row.get("driver").asText()));
                    ps.setObject(4, UUID.fromString(row.get("team").asText()));
                    ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("SeasonDriverRestorer: restored {} rows", rows.size());
    }
}
