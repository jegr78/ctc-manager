package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentReportWindowRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_report_windows (id,context_id,aggregate_version,state,planned_start_local,planned_start,deadline,opened_at,opened_by,closed_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "incident_report_windows"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "context"));
            ps.setObject(3, IncidentRestoreSupport.number(row, "version"));
            ps.setObject(4, IncidentRestoreSupport.text(row, "state"));
            ps.setObject(5, PickRestoreSupport.auditTime(row, "plannedStartLocal"));
            ps.setObject(6, IncidentRestoreSupport.instant(row, "plannedStart"));
            ps.setObject(7, IncidentRestoreSupport.instant(row, "deadline"));
            ps.setObject(8, IncidentRestoreSupport.instant(row, "openedAt"));
            ps.setObject(9, IncidentRestoreSupport.text(row, "openedBy"));
            ps.setObject(10, IncidentRestoreSupport.instant(row, "closedAt"));
            ps.setObject(11, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(12, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
