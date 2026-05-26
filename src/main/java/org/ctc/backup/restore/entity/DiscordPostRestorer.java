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

/**
 * Restores rows into the {@code discord_post} table from the
 * {@code data/discord-post.json} array in a backup ZIP.
 *
 * <p>Column origins: V12 (12 cols incl. 4 nullable FKs with {@code ON DELETE SET NULL}),
 * V14 ({@code phase_id} FK). Total: 13 data + 1 id + 2 audit = 15 columns.
 *
 * <p>The five FK columns ({@code match_id}, {@code matchday_id}, {@code race_id},
 * {@code season_id}, {@code phase_id}) are nullable; the V12/V14 DB-level
 * {@code ON DELETE SET NULL} cascade handles wipe-time nullification, so no pre-step
 * {@code UPDATE} is needed in {@code BackupImportService.wipeAllTables}.
 *
 * <p>Auditing bypass: written via {@link JdbcTemplate#batchUpdate} so
 * {@link org.ctc.domain.model.BaseEntity}'s {@code AuditingEntityListener}
 * does NOT overwrite {@code createdAt}/{@code updatedAt}.
 */
@Slf4j
@Component
public class DiscordPostRestorer implements EntityRestorer {

    private static final String INSERT_SQL =
            "INSERT INTO discord_post (id, channel_id, message_id, webhook_id, webhook_token, "
          + "post_type, match_id, matchday_id, race_id, season_id, phase_id, posted_at, "
          + "attachments_replaced_at, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public String tableName() {
        return "discord_post";
    }

    @Override
    public void restore(List<JsonNode> rows, JdbcTemplate jdbcTemplate) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 500, (ps, row) -> {
            ps.setLong(1, row.get("id").asLong());
            ps.setString(2, row.get("channelId").asText());
            ps.setString(3, row.get("messageId").asText());
            ps.setString(4, row.get("webhookId").asText());
            ps.setString(5, row.get("webhookToken").asText());
            ps.setString(6, row.get("postType").asText());
            setNullableUuid(ps, 7, row, "matchId");
            setNullableUuid(ps, 8, row, "matchdayId");
            setNullableUuid(ps, 9, row, "raceId");
            setNullableUuid(ps, 10, row, "seasonId");
            setNullableUuid(ps, 11, row, "phaseId");
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.parse(row.get("postedAt").asText())));
            setNullableTimestamp(ps, 13, row, "attachmentsReplacedAt");
            ps.setTimestamp(14, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
            ps.setTimestamp(15, Timestamp.valueOf(LocalDateTime.parse(row.get("updatedAt").asText())));
        });
        log.debug("DiscordPostRestorer: restored {} rows", rows.size());
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

    private static void setNullableTimestamp(PreparedStatement ps, int idx, JsonNode row, String field)
            throws SQLException {
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) {
            ps.setNull(idx, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(idx, Timestamp.valueOf(LocalDateTime.parse(n.asText())));
        }
    }
}
