package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Restores rows into the {@code discord_global_config} table from the
 * {@code data/discord-global-config.json} array in a backup ZIP.
 *
 * <p>Column origins: V8 (10 cols), V9 ({@code current_match_category_id}),
 * V13 ({@code race_results_forum_webhook_url}, {@code standings_forum_webhook_url}),
 * V15 ({@code matchday_pairings_template}). Total: 10 data + 1 id + 2 audit = 13 columns.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
public class DiscordGlobalConfigRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO discord_global_config (id, guild_id, announcement_webhook_url, "
          + "race_results_forum_channel_id, standings_forum_channel_id, "
          + "race_results_forum_webhook_url, standings_forum_webhook_url, vs_emoji_name, "
          + "bot_application_id, current_match_category_id, matchday_pairings_template, "
          + "created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "discord_global_config";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setLong(1, row.get("id").asLong());
            ps.setString(2, requireText(row, "guildId"));
            ps.setString(3, requireText(row, "announcementWebhookUrl"));
            ps.setString(4, requireText(row, "raceResultsForumChannelId"));
            ps.setString(5, requireText(row, "standingsForumChannelId"));
            setNullableString(ps, 6, row, "raceResultsForumWebhookUrl");
            setNullableString(ps, 7, row, "standingsForumWebhookUrl");
            ps.setString(8, requireText(row, "vsEmojiName"));
            setNullableString(ps, 9, row, "botApplicationId");
            ps.setString(10, requireText(row, "currentMatchCategoryId"));
            setNullableString(ps, 11, row, "matchdayPairingsTemplate");
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.parse(requireText(row, "createdAt"))));
            ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.parse(requireText(row, "updatedAt"))));
        });
        log.debug("DiscordGlobalConfigRestorer: restored {} rows", rows.size());
    }

    private static String requireText(JsonNode row, String field) {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            throw new BackupArchiveException(Reason.MANIFEST_INVALID,
                    "missing required column '" + field + "' in discord_global_config row");
        }
        return n.asText();
    }

    private static void setNullableString(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, n.asText());
        }
    }
}
