package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MatchdayPickRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO matchday_picks (id,race_id,season_id,team_id,aggregate_version,state,latest_revision_number,approved_revision_number,manual_open,manual_closed,created_at,updated_at,original_matchday_id,original_away_team_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "matchday_picks"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("race").asText()));
            ps.setObject(3, UUID.fromString(row.get("season").asText()));
            ps.setObject(4, UUID.fromString(row.get("team").asText()));
            ps.setLong(5, row.get("version").longValue());
            ps.setString(6, row.get("state").asText());
            ps.setObject(7, row.hasNonNull("latestRevisionNumber") ? row.get("latestRevisionNumber").intValue() : null);
            ps.setObject(8, row.hasNonNull("approvedRevisionNumber") ? row.get("approvedRevisionNumber").intValue() : null);
            ps.setBoolean(9, row.get("manualOpen").booleanValue());
            ps.setBoolean(10, row.get("manualClosed").booleanValue());
            ps.setTimestamp(11, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(12, PickRestoreSupport.auditTime(row, "updatedAt"));
            ps.setObject(13, UUID.fromString(row.get("originalMatchday").asText()));
            ps.setObject(14, UUID.fromString(row.get("originalAwayTeam").asText()));
        });
    }
}
