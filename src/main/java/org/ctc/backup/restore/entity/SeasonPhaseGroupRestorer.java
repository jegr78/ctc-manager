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
 * Restores rows into the {@code season_phase_groups} table from the
 * {@code data/season-phase-groups.json} array in a backup ZIP.
 *
 * <p>Schema reference (V3__add_season_phase_tables.sql:28-36):
 * <pre>
 * CREATE TABLE season_phase_groups (
 *   id UUID PRIMARY KEY,
 *   phase_id UUID NOT NULL,
 *   name VARCHAR(255) NOT NULL,
 *   sort_index INT NOT NULL,
 *   created_at TIMESTAMP, updated_at TIMESTAMP);
 * </pre>
 *
 * <p>JSON shape (from {@code SeasonPhaseGroupMixIn}): FK field {@code phase} renders as bare
 * UUID string via {@code @JsonIdentityReference(alwaysAsId=true)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonPhaseGroupRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO season_phase_groups (id, phase_id, name, sort_index, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "season_phase_groups";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setObject(2, UUID.fromString(row.get("phase").asText()));
                    ps.setString(3, row.get("name").asText());
                    ps.setInt(4, row.get("sortIndex").asInt());
                    ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("SeasonPhaseGroupRestorer: restored {} rows", rows.size());
    }
}
