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
 * Restores rows into the {@code seasons} table from the
 * {@code data/seasons.json} array in a backup ZIP.
 *
 * <p>Schema reference (V1__initial_schema.sql:24-43 — DB-level columns
 * {@code race_scoring_id}, {@code match_scoring_id}, {@code format}, {@code total_rounds},
 * {@code legs}, {@code event_duration_minutes} are present at the table level but are NOT
 * mapped on the {@code Season} entity class — they have moved to {@code SeasonPhase} in V3.
 * They are therefore absent from the {@code data/seasons.json} JSON shape and are left for the
 * DB-level defaults to fill (or remain NULL where allowed)).
 *
 * <p>Mapped columns (Season entity → {@code seasons} table):
 * <pre>
 * id UUID PRIMARY KEY
 * name VARCHAR(255) NOT NULL
 * season_year INT NOT NULL
 * season_number INT NOT NULL
 * description VARCHAR(255)                          (nullable)
 * active BOOLEAN NOT NULL DEFAULT FALSE
 * created_at TIMESTAMP
 * updated_at TIMESTAMP
 * discord_race_results_thread_id VARCHAR(32) NULL   (V13)
 * discord_standings_thread_id VARCHAR(32) NULL      (V13)
 * </pre>
 *
 * <p>JSON shape (from {@code SeasonMixIn}): {@code {"id":"<uuid>","name":"...","year":<int>,
 * "number":<int>,"description":"...","active":true,"cars":["<uuid>",...],"tracks":["<uuid>",...],
 * "createdAt":"<iso>","updatedAt":"<iso>"}}. {@code cars}/{@code tracks} are owned by the
 * {@code season_cars} / {@code season_tracks} join tables (out of scope for this plan).
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} to bypass
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener} so that the
 * imported {@code createdAt}/{@code updatedAt} values survive verbatim.
 */
@Slf4j
@Component
public class SeasonRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO seasons (id, name, season_year, season_number, description, active, "
                    + "created_at, updated_at, discord_race_results_thread_id, "
                    + "discord_standings_thread_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "seasons";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500,
                (ps, row) -> {
                    ps.setObject(1, UUID.fromString(row.get("id").asText()));
                    ps.setString(2, row.get("name").asText());
                    ps.setInt(3, row.get("year").asInt());
                    ps.setInt(4, row.get("number").asInt());
                    ps.setString(5, nullableString(row, "description"));
                    ps.setBoolean(6, row.get("active").asBoolean());
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
                    ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
                    ps.setString(9, nullableString(row, "discordRaceResultsThreadId"));
                    ps.setString(10, nullableString(row, "discordStandingsThreadId"));
                });
        log.debug("SeasonRestorer: restored {} rows", rows.size());
    }

    private static String nullableString(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
