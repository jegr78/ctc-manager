package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentRulingRevisionRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_ruling_revisions (id,ruling_id,revision_number,ruling_text,reason,actor_id,accepted_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "incident_ruling_revisions"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "ruling"));
            ps.setObject(3, IncidentRestoreSupport.number(row, "revisionNumber"));
            ps.setObject(4, IncidentRestoreSupport.text(row, "text"));
            ps.setObject(5, IncidentRestoreSupport.text(row, "reason"));
            ps.setObject(6, IncidentRestoreSupport.text(row, "actorId"));
            ps.setObject(7, IncidentRestoreSupport.instant(row, "acceptedAt"));
            ps.setObject(8, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(9, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
