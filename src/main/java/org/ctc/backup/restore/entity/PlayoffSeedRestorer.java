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
 * {@link EntityRestorer} for {@link org.ctc.domain.model.PlayoffSeed}.
 *
 * <p>V1 schema columns ({@code V1__initial_schema.sql}):
 * <ul>
 *   <li>{@code id} (UUID PK)</li>
 *   <li>{@code playoff_id} (UUID NOT NULL, FK)</li>
 *   <li>{@code team_id} (UUID NOT NULL, FK)</li>
 *   <li>{@code seed} (INT NOT NULL)</li>
 *   <li>{@code created_at} (TIMESTAMP)</li>
 *   <li>{@code updated_at} (TIMESTAMP)</li>
 * </ul>
 *
 * <p>Single-pass insert — two regular FKs ({@code playoff_id}, {@code team_id}). Bypasses
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}.
 */
@Slf4j
@Component
public class PlayoffSeedRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO playoff_seeds (id, playoff_id, team_id, seed, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final int BATCH_SIZE = 500;

    @Override
    public String tableName() {
        return "playoff_seeds";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        if (rows.isEmpty()) {
            log.debug("PlayoffSeedRestorer: skipping batchUpdate — no rows");
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("playoff").asText()));
            ps.setObject(3, UUID.fromString(row.get("team").asText()));
            ps.setInt(4, row.get("seed").asInt());
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("PlayoffSeedRestorer: inserted {} playoff_seed rows", rows.size());
    }
}
