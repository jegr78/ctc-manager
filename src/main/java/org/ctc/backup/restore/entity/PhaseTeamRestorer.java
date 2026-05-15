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
 * Restores rows into the {@code phase_teams} table from the
 * {@code data/phase-teams.json} array in a backup ZIP.
 *
 * <p>Schema reference (V3__add_season_phase_tables.sql:38-49):
 * <pre>
 * CREATE TABLE phase_teams (
 *   id UUID PRIMARY KEY,
 *   phase_id UUID NOT NULL,
 *   team_id UUID NOT NULL,
 *   group_id UUID,                      (nullable — no sub-group bracket assignment)
 *   created_at TIMESTAMP, updated_at TIMESTAMP);
 * </pre>
 *
 * <p>JSON shape (from {@code PhaseTeamMixIn}): all three FK fields ({@code phase},
 * {@code team}, {@code group}) render as bare UUID strings via
 * {@code @JsonIdentityReference(alwaysAsId=true)}; {@code group} may be JSON {@code null}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhaseTeamRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "phase_teams";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setObject(2, UUID.fromString(row.get("phase").asText()));
                    ps.setObject(3, UUID.fromString(row.get("team").asText()));
                    ps.setObject(4, nullableUuid(row, "group"));
                    ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });
        log.debug("PhaseTeamRestorer: restored {} rows", rows.size());
    }

    private static UUID nullableUuid(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return (n == null || n.isNull()) ? null : UUID.fromString(n.asText());
    }
}
