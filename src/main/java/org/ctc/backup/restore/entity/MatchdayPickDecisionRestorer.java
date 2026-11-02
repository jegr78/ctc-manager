package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.ctc.backup.schema.PickPayloadReader;

@Component
public class MatchdayPickDecisionRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO matchday_pick_decisions (id,pick_id,kind,aggregate_version,actor_kind,actor_id,reason,decided_at," + PickRestoreSupport.PAYLOAD_COLUMNS + ",created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "matchday_pick_decisions"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("pick").asText()));
            ps.setString(3, row.get("kind").asText());
            ps.setLong(4, row.get("aggregateVersion").longValue());
            ps.setString(5, row.get("actorKind").asText());
            ps.setString(6, row.get("actorId").asText());
            ps.setString(7, row.get("reason").asText());
            ps.setTimestamp(8, PickRestoreSupport.instant(row, "decidedAt"));
            PickRestoreSupport.payload(ps, 9, row.hasNonNull("correctionPayload") ? PickPayloadReader.read(row.get("correctionPayload")) : null);
            ps.setTimestamp(24, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(25, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
