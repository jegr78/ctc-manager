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
 * Restores rows into the {@code teams} table from the
 * {@code data/teams.json} array in a backup ZIP.
 *
 * <p><strong>2-pass restore</strong>: {@code teams.parent_team_id} is a self-FK
 * — sub-team A references parent P, and both rows arrive in a flat JSON array that is NOT
 * topologically sorted. Forward-iterating with the parent set would fail when P has not been
 * inserted yet.
 * <ol>
 *   <li><strong>Pass 1</strong>: {@code INSERT INTO teams (...) VALUES (..., NULL, ...)} —
 *       every row inserted with {@code parent_team_id} hard-coded NULL.</li>
 *   <li><strong>Pass 2</strong>: {@code UPDATE teams SET parent_team_id = ? WHERE id = ?} —
 *       executed only for the subset of source rows whose JSON {@code parentTeam} is non-null.
 *       Skipped entirely when the subset is empty to avoid a no-op round-trip.</li>
 * </ol>
 *
 * <p>JSON shape (from {@code TeamMixIn}): {@code parentTeam} renders as a bare UUID string via
 * {@code @JsonIdentityReference(alwaysAsId=true)} — NOT a nested {@code {id:...}} object.
 * {@code subTeams} and {@code seasonDrivers} back-references are suppressed by the MixIn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamRestorer implements EntityRestorer {

    private static final String INSERT_SQL_PASS1 =
            "INSERT INTO teams (id, name, short_name, logo_url, primary_color, "
                    + "secondary_color, accent_color, parent_team_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)";

    private static final String UPDATE_SQL_PASS2 =
            "UPDATE teams SET parent_team_id = ? WHERE id = ?";

    @Override
    public String tableName() {
        return "teams";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        // Pass 1: INSERT every row with parent_team_id hard-coded NULL.
        jdbcTemplate.batchUpdate(INSERT_SQL_PASS1, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setString(2, row.get("name").asText());
                    ps.setString(3, row.get("shortName").asText());
                    ps.setString(4, nullableString(row, "logoUrl"));
                    ps.setString(5, nullableString(row, "primaryColor"));
                    ps.setString(6, nullableString(row, "secondaryColor"));
                    ps.setString(7, nullableString(row, "accentColor"));
                    ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                });

        // Pass 2: UPDATE parent_team_id for the subset of rows with a non-null parentTeam.
        List<JsonNode> withParent = rows.stream()
                .filter(r -> r.get("parentTeam") != null && !r.get("parentTeam").isNull())
                .toList();

        if (!withParent.isEmpty()) {
            jdbcTemplate.batchUpdate(UPDATE_SQL_PASS2, withParent, 500,
                    (ps, row) -> {
                        ps.setObject(1, UUID.fromString(row.get("parentTeam").asText()));
                        ps.setObject(2, UUID.fromString(row.get("id").asText()));
                    });
        }

        log.debug("TeamRestorer: pass1Rows={}, pass2Rows={}", rows.size(), withParent.size());
    }

    private static String nullableString(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
