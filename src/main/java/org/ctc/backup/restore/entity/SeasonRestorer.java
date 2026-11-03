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

@Slf4j
@Component
public class SeasonRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO seasons (id, name, season_year, season_number, description, active, "
                    + "created_at, updated_at, discord_race_results_thread_id, "
                    + "discord_standings_thread_id, car_pool_mode, car_pool_state, picks_collection_enabled) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                    ps.setString(11, row.path("carPoolMode").asText("FIXED_CARS"));
                    ps.setString(12, row.path("carPoolState").asText("DRAFT"));
                    ps.setBoolean(13, row.path("picksCollectionEnabled").asBoolean(false));
                });
        for (var row : rows) {
            restorePool(row, "cars", true, jdbcTemplate);
            restorePool(row, "tracks", false, jdbcTemplate);
        }
        log.debug("SeasonRestorer: restored {} rows", rows.size());
    }

    private static void restorePool(JsonNode row, String field, boolean cars, JdbcTemplate jdbc) {
        String sql = cars ? "INSERT INTO season_cars (season_id,car_id) VALUES (?,?)" : "INSERT INTO season_tracks (season_id,track_id) VALUES (?,?)";
        if (!row.hasNonNull(field)) return;
        var values = row.get(field);
        if (!values.isArray()) throw new IllegalArgumentException("Season pool references must be arrays");
        var ids = new java.util.ArrayList<UUID>();
        for (var value : values) ids.add(UUID.fromString(value.asText()));
        UUID seasonId = UUID.fromString(row.get("id").asText());
        jdbc.batchUpdate(sql, ids, 500, (statement, id) -> { statement.setObject(1, seasonId); statement.setObject(2, id); });
    }

    private static String nullableString(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
