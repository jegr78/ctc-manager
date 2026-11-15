package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LineupSubmissionRevisionRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO lineup_submission_revisions (id,submission_id,revision_number,payload,submitted_at,actor_kind,actor_id,reason,request_id,aggregate_version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "lineup_submission_revisions"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("submission").asText()));
            ps.setInt(3, row.get("revisionNumber").intValue());
            ps.setString(4, row.get("payload").asText());
            ps.setTimestamp(5, PickRestoreSupport.instant(row, "submittedAt"));
            ps.setString(6, row.get("actorKind").asText());
            ps.setString(7, row.get("actorId").asText());
            ps.setString(8, row.hasNonNull("reason") ? row.get("reason").asText() : null);
            ps.setObject(9, UUID.fromString(row.get("requestId").asText()));
            ps.setLong(10, row.get("aggregateVersion").longValue());
            ps.setTimestamp(11, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(12, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
