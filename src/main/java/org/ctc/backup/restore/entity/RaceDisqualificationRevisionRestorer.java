package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RaceDisqualificationRevisionRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO race_disqualification_revisions (id,result_id,race_id,driver_id,season_id,phase_id,matchday_id,match_id,playoff_matchup_id,home_team_id,away_team_id,revision_number,previous_disqualified,disqualified,recorded_position,reason,actor_id,result_fingerprint,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "race_disqualification_revisions"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            var references = List.of("id", "result", "race", "driver", "season", "phase", "matchday", "match", "playoffMatchup", "homeTeam", "awayTeam");
            for (int i = 0; i < references.size(); i++) {
                var value = row.get(references.get(i)); ps.setObject(i + 1, value == null || value.isNull() ? null : UUID.fromString(value.textValue()));
            }
            ps.setInt(12, row.get("revisionNumber").intValue());
            ps.setBoolean(13, row.get("previousDisqualified").booleanValue()); ps.setBoolean(14, row.get("disqualified").booleanValue());
            ps.setInt(15, row.get("recordedPosition").intValue());
            ps.setString(16, row.get("reason").textValue()); ps.setString(17, row.get("actorId").textValue());
            ps.setString(18, row.get("resultFingerprint").textValue());
            ps.setTimestamp(19, PickRestoreSupport.auditTime(row, "createdAt")); ps.setTimestamp(20, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
