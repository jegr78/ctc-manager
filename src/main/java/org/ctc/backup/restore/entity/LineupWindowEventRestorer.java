package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LineupWindowEventRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO lineup_window_events (id,race_id,sequence_number,kind,reason,actor_id,occurred_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "lineup_window_events"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("race").asText()));
            ps.setInt(3, row.get("sequenceNumber").intValue());
            ps.setString(4, row.get("kind").asText());
            ps.setString(5, row.get("reason").asText());
            ps.setString(6, row.get("actorId").asText());
            ps.setTimestamp(7, PickRestoreSupport.instant(row, "occurredAt"));
            ps.setTimestamp(8, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(9, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
