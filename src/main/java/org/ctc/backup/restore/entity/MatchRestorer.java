package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MatchRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO matches (id, matchday_id, home_team_id, away_team_id, "
          + "home_score, away_score, bye, created_at, updated_at, "
          + "discord_channel_id, discord_channel_webhook_url, discord_teaser, "
          + "stream_link, lobby_host, race_director, streamer, "
          + "discord_channel_archived_at, walkover_team_id) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "matches";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            setNullableUuid(ps, 2, row, "matchday");
            setNullableUuid(ps, 3, row, "homeTeam");
            setNullableUuid(ps, 4, row, "awayTeam");
            setNullableInt(ps, 5, row, "homeScore");
            setNullableInt(ps, 6, row, "awayScore");
            ps.setBoolean(7, row.get("bye").asBoolean());
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
            ps.setString(10, nullableString(row, "discordChannelId"));
            ps.setString(11, nullableString(row, "discordChannelWebhookUrl"));
            ps.setString(12, nullableString(row, "discordTeaser"));
            ps.setString(13, nullableString(row, "streamLink"));
            ps.setString(14, nullableString(row, "lobbyHost"));
            ps.setString(15, nullableString(row, "raceDirector"));
            ps.setString(16, nullableString(row, "streamer"));
            setNullableTimestamp(ps, 17, row, "discordChannelArchivedAt");
            var forfeiting = org.ctc.backup.schema.MatchWalkoverReference.read(row.get("walkoverTeam"));
            if (forfeiting == null) ps.setNull(18, Types.OTHER);
            else ps.setObject(18, forfeiting);
        });
        log.debug("MatchRestorer: restored {} rows", rows.size());
    }

    private static void setNullableUuid(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.OTHER);
        } else {
            ps.setObject(idx, UUID.fromString(n.asText()));
        }
    }

    private static void setNullableInt(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, n.asInt());
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(idx, Timestamp.valueOf(LocalDateTime.parse(n.asText())));
        }
    }

    private static String nullableString(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
