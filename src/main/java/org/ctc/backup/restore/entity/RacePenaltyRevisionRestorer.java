package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RacePenaltyRevisionRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO race_penalty_revisions (id,race_id,season_id,phase_id,matchday_id,match_id,playoff_matchup_id,home_team_id,away_team_id,revision_number,home_penalty,away_penalty,home_gross,away_gross,reason,actor_id,result_fingerprint,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    @Override public String tableName() { return "race_penalty_revisions"; }

    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            var references = List.of("id", "race", "season", "phase", "matchday", "match", "playoffMatchup", "homeTeam", "awayTeam");
            for (int i = 0; i < references.size(); i++) {
                String field = references.get(i);
                ps.setObject(i + 1, row.hasNonNull(field) ? UUID.fromString(row.get(field).asText()) : null);
            }
            var numbers = List.of("revisionNumber", "homePenalty", "awayPenalty", "homeGross", "awayGross");
            for (int i = 0; i < numbers.size(); i++) ps.setInt(10 + i, row.get(numbers.get(i)).intValue());
            ps.setString(15, row.get("reason").asText());
            ps.setString(16, row.get("actorId").asText());
            ps.setString(17, row.get("resultFingerprint").asText());
            ps.setTimestamp(18, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(19, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
