package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 75 / Plan 03 — restores rows into the {@code season_teams} table from the
 * {@code data/season-teams.json} array in a backup ZIP.
 *
 * <p><strong>2-pass restore (PLAN-Q1 resolution)</strong>:
 * {@code season_teams.successor_season_team_id} is a self-FK
 * (V1__initial_schema.sql:90 — {@code CONSTRAINT fk_st_successor FOREIGN KEY
 * (successor_season_team_id) REFERENCES season_teams(id)}). It is structurally identical to
 * {@code teams.parent_team_id} (D-06) and therefore receives the same 2-pass treatment.
 *
 * <p>CONTEXT D-06 only enumerated {@code TeamRestorer} as 2-pass; PATTERNS.md flagged the
 * {@code SeasonTeam} successor self-FK as an open question (Q1). This plan resolves Q1 by
 * adopting the identical pattern — the alternative (sorting the JSON array topologically
 * within the file) would couple the restorer to source-array order, which Phase 73's
 * exporter does not guarantee.
 *
 * <ol>
 *   <li><strong>Pass 1</strong>: {@code INSERT} every row with {@code successor_season_team_id}
 *       hard-coded NULL.</li>
 *   <li><strong>Pass 2</strong>: {@code UPDATE season_teams SET successor_season_team_id = ?
 *       WHERE id = ?} for the subset of rows whose JSON {@code successor} is non-null.
 *       Skipped when the subset is empty.</li>
 * </ol>
 *
 * <p>JSON shape (from {@code SeasonTeamMixIn}): all three FK fields ({@code season},
 * {@code team}, {@code successor}) render as bare UUID strings via
 * {@code @JsonIdentityReference(alwaysAsId=true)}. {@code rating} is a nullable INT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonTeamRestorer implements EntityRestorer {

    private static final String INSERT_SQL_PASS1 =
            "INSERT INTO season_teams (id, season_id, team_id, rating, primary_color, "
                    + "secondary_color, accent_color, logo_url, successor_season_team_id, "
                    + "replaced_at, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)";

    private static final String UPDATE_SQL_PASS2 =
            "UPDATE season_teams SET successor_season_team_id = ? WHERE id = ?";

    @Override
    public String tableName() {
        return "season_teams";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        // Pass 1: INSERT every row with successor_season_team_id hard-coded NULL.
        jdbcTemplate.batchUpdate(INSERT_SQL_PASS1, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setObject(2, UUID.fromString(row.get("season").asText()));
                    ps.setObject(3, UUID.fromString(row.get("team").asText()));
                    ps.setObject(4, nullableInt(row, "rating"), Types.INTEGER);
                    ps.setString(5, nullableString(row, "primaryColor"));
                    ps.setString(6, nullableString(row, "secondaryColor"));
                    ps.setString(7, nullableString(row, "accentColor"));
                    ps.setString(8, nullableString(row, "logoUrl"));
                    ps.setDate(9, nullableDate(row, "replacedAt"));
                    ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });

        // Pass 2: UPDATE successor_season_team_id for the subset with a non-null successor.
        List<JsonNode> withSuccessor = rows.stream()
                .filter(r -> r.get("successor") != null && !r.get("successor").isNull())
                .toList();

        if (!withSuccessor.isEmpty()) {
            jdbcTemplate.batchUpdate(UPDATE_SQL_PASS2, withSuccessor, 500,
                    (ps, row) -> {
                        ps.setObject(1, UUID.fromString(row.get("successor").asText()));
                        ps.setObject(2, UUID.fromString(row.get("id").asText()));
                    });
        }

        log.debug("SeasonTeamRestorer: pass1Rows={}, pass2Rows={}",
                rows.size(), withSuccessor.size());
    }

    private static String nullableString(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static Integer nullableInt(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return (n == null || n.isNull()) ? null : n.asInt();
    }

    private static Date nullableDate(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return (n == null || n.isNull()) ? null : Date.valueOf(LocalDate.parse(n.asText()));
    }
}
