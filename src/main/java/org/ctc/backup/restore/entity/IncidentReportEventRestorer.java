package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentReportEventRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_report_events (id,window_id,report_id,event_key,kind,window_version,report_version,revision_number,actor_kind,actor_id,reason,accepted_at,previous_deadline,deadline,request_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "incident_report_events"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "window"));
            ps.setObject(3, IncidentRestoreSupport.uuid(row, "report"));
            ps.setObject(4, IncidentRestoreSupport.text(row, "eventKey"));
            ps.setObject(5, IncidentRestoreSupport.text(row, "kind"));
            ps.setObject(6, IncidentRestoreSupport.number(row, "windowVersion"));
            ps.setObject(7, IncidentRestoreSupport.number(row, "reportVersion"));
            ps.setObject(8, IncidentRestoreSupport.number(row, "revisionNumber"));
            ps.setObject(9, IncidentRestoreSupport.text(row, "actorKind"));
            ps.setObject(10, IncidentRestoreSupport.text(row, "actorId"));
            ps.setObject(11, IncidentRestoreSupport.text(row, "reason"));
            ps.setObject(12, IncidentRestoreSupport.instant(row, "acceptedAt"));
            ps.setObject(13, IncidentRestoreSupport.instant(row, "previousDeadline"));
            ps.setObject(14, IncidentRestoreSupport.instant(row, "deadline"));
            ps.setObject(15, IncidentRestoreSupport.uuid(row, "requestId"));
            ps.setObject(16, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(17, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
