package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TeamPickChannelBindingRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO team_pick_channel_bindings (id,season_id,team_id,guild_id,channel_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?)";
    @Override public String tableName() { return "team_pick_channel_bindings"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("season").asText()));
            ps.setObject(3, UUID.fromString(row.get("team").asText()));
            ps.setString(4, row.get("guildId").asText());
            ps.setString(5, row.get("channelId").asText());
            ps.setTimestamp(6, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(7, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
