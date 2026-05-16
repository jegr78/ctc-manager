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
 * Restores rows into the {@code match_scorings} table from the
 * {@code data/match-scorings.json} array in a backup ZIP.
 *
 * <p>Schema (V1): {@code id UUID PK, name VARCHAR UNIQUE, points_win INT NOT NULL,
 * points_draw INT NOT NULL, points_loss INT NOT NULL, created_at TIMESTAMP,
 * updated_at TIMESTAMP}. Leaf entity — no foreign keys.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScoringRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO match_scorings (id, name, points_win, points_draw, points_loss, "
          + "created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "match_scorings";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setString(2, row.get("name").asText());
            ps.setInt(3, row.get("pointsWin").asInt());
            ps.setInt(4, row.get("pointsDraw").asInt());
            ps.setInt(5, row.get("pointsLoss").asInt());
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("MatchScoringRestorer: restored {} rows", rows.size());
    }
}
