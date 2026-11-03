package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentReportRevisionRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_report_revisions (id,report_id,revision_number,report_version,involved_drivers,replay_timestamp,description,evidence_links,actor_id,accepted_at,request_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "incident_report_revisions"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "report"));
            ps.setObject(3, IncidentRestoreSupport.number(row, "revisionNumber"));
            ps.setObject(4, IncidentRestoreSupport.number(row, "reportVersion"));
            ps.setObject(5, IncidentRestoreSupport.text(row.get("payload"), "involvedDrivers"));
            ps.setObject(6, IncidentRestoreSupport.text(row.get("payload"), "replayTimestamp"));
            ps.setObject(7, IncidentRestoreSupport.text(row.get("payload"), "description"));
            ps.setObject(8, IncidentRestoreSupport.text(row.get("payload"), "evidenceLinks"));
            ps.setObject(9, IncidentRestoreSupport.text(row, "actorId"));
            ps.setObject(10, IncidentRestoreSupport.instant(row, "acceptedAt"));
            ps.setObject(11, IncidentRestoreSupport.uuid(row, "requestId"));
            ps.setObject(12, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(13, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
