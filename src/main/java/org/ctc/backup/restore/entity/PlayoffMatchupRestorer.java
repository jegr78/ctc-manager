package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Phase 75 / Plan 05 — 2-pass {@link EntityRestorer} for
 * {@link org.ctc.domain.model.PlayoffMatchup}.
 *
 * <p>PATTERNS Q2 resolution: {@code next_matchup_id} is a self-FK
 * ({@code V1__initial_schema.sql} lines 187-204:
 * {@code CONSTRAINT fk_pm_next FOREIGN KEY (next_matchup_id) REFERENCES playoff_matchups(id)})
 * — structurally identical to {@code Team.parent_team_id}. The 2-pass NULL-then-UPDATE
 * treatment locked by CONTEXT D-06 (Team) is applied here too.
 *
 * <p>Pass-1 INSERT writes every matchup with {@code next_matchup_id = NULL} (hard-coded in the
 * VALUES clause). Pass-2 UPDATE fills {@code next_matchup_id} for matchups whose source JSON
 * had a non-null {@code nextMatchup} reference.
 *
 * <p>V1 schema columns ({@code V1__initial_schema.sql} lines 187-204):
 * <ul>
 *   <li>{@code id} (UUID PK)</li>
 *   <li>{@code round_id} (UUID NOT NULL, FK)</li>
 *   <li>{@code bracket_position} (INT NOT NULL)</li>
 *   <li>{@code team1_id} (UUID, nullable FK)</li>
 *   <li>{@code team2_id} (UUID, nullable FK)</li>
 *   <li>{@code winner_id} (UUID, nullable FK)</li>
 *   <li>{@code next_matchup_id} (UUID, nullable self-FK)</li>
 *   <li>{@code home_score} (INT, nullable)</li>
 *   <li>{@code away_score} (INT, nullable)</li>
 *   <li>{@code created_at}, {@code updated_at} (TIMESTAMP)</li>
 * </ul>
 *
 * <p>Nullable team FKs use a local {@code setNullableUuid} helper (CONTEXT D-08 — no shared
 * utility class).
 *
 * <p>Bypasses {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener} per
 * Phase 75 goal.
 */
@Component
@Slf4j
public class PlayoffMatchupRestorer implements EntityRestorer {

    /**
     * Pass-1 INSERT with {@code next_matchup_id} hard-coded {@code NULL}. Column order matches
     * V1__initial_schema.sql lines 187-204.
     */
    private static final String INSERT_SQL_PASS1 =
            "INSERT INTO playoff_matchups (id, round_id, team1_id, team2_id, winner_id, "
                    + "next_matchup_id, bracket_position, home_score, away_score, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)";

    /**
     * Pass-2 UPDATE binds {@code next_matchup_id} from the JSON, scoped by {@code id}.
     */
    private static final String UPDATE_SQL_PASS2 =
            "UPDATE playoff_matchups SET next_matchup_id = ? WHERE id = ?";

    private static final int BATCH_SIZE = 500;

    @Override
    public String tableName() {
        return "playoff_matchups";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        if (rows.isEmpty()) {
            log.debug("PlayoffMatchupRestorer: skipping batchUpdate — no rows");
            return;
        }

        // Pass 1: INSERT every row with next_matchup_id = NULL
        jdbcTemplate.batchUpdate(INSERT_SQL_PASS1, rows, BATCH_SIZE, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("round").asText()));
            setNullableUuid(ps, 3, row, "team1");
            setNullableUuid(ps, 4, row, "team2");
            setNullableUuid(ps, 5, row, "winner");
            ps.setInt(6, row.get("bracketPosition").asInt());
            setNullableInt(ps, 7, row, "homeScore");
            setNullableInt(ps, 8, row, "awayScore");
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });

        // Pass 2: UPDATE next_matchup_id for the subset with a non-null nextMatchup reference
        List<JsonNode> withNext = rows.stream()
                .filter(r -> r.get("nextMatchup") != null && !r.get("nextMatchup").isNull())
                .toList();
        if (!withNext.isEmpty()) {
            jdbcTemplate.batchUpdate(UPDATE_SQL_PASS2, withNext, BATCH_SIZE, (ps, row) -> {
                ps.setObject(1, UUID.fromString(row.get("nextMatchup").asText()));
                ps.setObject(2, UUID.fromString(row.get("id").asText()));
            });
        }
        log.debug("PlayoffMatchupRestorer: pass1Rows={}, pass2Rows={}", rows.size(), withNext.size());
    }

    /**
     * Binds a nullable UUID column from a JSON field that may be missing OR explicitly
     * {@code null} (per CONTEXT D-08: no shared utility class — local helper).
     */
    private static void setNullableUuid(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode value = row.get(field);
        if (value == null || value.isNull()) {
            ps.setNull(idx, Types.OTHER);
        } else {
            ps.setObject(idx, UUID.fromString(value.asText()));
        }
    }

    private static void setNullableInt(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode value = row.get(field);
        if (value == null || value.isNull()) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value.asInt());
        }
    }
}
