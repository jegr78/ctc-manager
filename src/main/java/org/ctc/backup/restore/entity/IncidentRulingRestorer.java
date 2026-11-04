package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentRulingRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_rulings (id,report_id,aggregate_version,latest_revision_number,requested_revision_number,published_revision_number,published_message_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "incident_rulings"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "report"));
            ps.setObject(3, IncidentRestoreSupport.number(row, "version"));
            ps.setObject(4, IncidentRestoreSupport.number(row, "latestRevisionNumber"));
            ps.setObject(5, IncidentRestoreSupport.number(row, "requestedRevisionNumber"));
            ps.setObject(6, IncidentRestoreSupport.number(row, "publishedRevisionNumber"));
            ps.setObject(7, IncidentRestoreSupport.text(row, "publishedMessageId"));
            ps.setObject(8, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(9, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
