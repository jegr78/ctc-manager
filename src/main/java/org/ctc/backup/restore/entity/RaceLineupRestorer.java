package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores rows into the {@code race_lineups} table from a backup ZIP.
 *
 * <p>Writes via {@link JdbcTemplate#batchUpdate} to bypass {@code AuditingEntityListener}
 * so {@code createdAt}/{@code updatedAt} are preserved verbatim from the backup.
 */
@Slf4j
@Component
public class RaceLineupRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO race_lineups (id, race_id, driver_id, team_id, is_guest, "
          + "created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

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
            ps.setBoolean(5, row.path("guest").asBoolean(false));
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("RaceLineupRestorer: restored {} rows", rows.size());
    }
}
