package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentMatchContextRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_match_contexts (id,match_id,playoff_matchup_id,season_id,home_team_id,away_team_id,guild_id,channel_id,aggregate_version,bound_by,bound_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "incident_match_contexts"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "match"));
            ps.setObject(3, IncidentRestoreSupport.uuid(row, "playoffMatchup"));
            ps.setObject(4, IncidentRestoreSupport.uuid(row, "season"));
            ps.setObject(5, IncidentRestoreSupport.uuid(row, "homeTeam"));
            ps.setObject(6, IncidentRestoreSupport.uuid(row, "awayTeam"));
            ps.setObject(7, IncidentRestoreSupport.text(row, "guildId"));
            ps.setObject(8, IncidentRestoreSupport.text(row, "channelId"));
            ps.setObject(9, IncidentRestoreSupport.number(row, "version"));
            ps.setObject(10, IncidentRestoreSupport.text(row, "boundBy"));
            ps.setObject(11, IncidentRestoreSupport.instant(row, "boundAt"));
            ps.setObject(12, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(13, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
