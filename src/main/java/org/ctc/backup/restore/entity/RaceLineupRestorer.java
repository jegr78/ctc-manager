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
 * Restores rows into the {@code race_lineups} table from the
 * {@code data/race-lineups.json} array in a backup ZIP.
 *
 * <p>Schema (V1): {@code id UUID PK, race_id UUID NOT NULL, driver_id UUID NOT NULL,
 * team_id UUID NOT NULL, created_at TIMESTAMP, updated_at TIMESTAMP}.
 *
 * <p>Operationally critical: {@code RaceLineup} is the source-of-truth for driver-team
 * assignments. The setter MUST preserve every row's {@code race_id} / {@code driver_id} /
 * {@code team_id} triple verbatim from the source JSON — sub-team assignments depend on this.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceLineupRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO race_lineups (id, race_id, driver_id, team_id, "
          + "created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "race_lineups";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("race").asText()));
            ps.setObject(3, UUID.fromString(row.get("driver").asText()));
            ps.setObject(4, UUID.fromString(row.get("team").asText()));
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("RaceLineupRestorer: restored {} rows", rows.size());
    }
}
