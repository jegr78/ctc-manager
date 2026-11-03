package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentReportRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_reports (id,window_id,race_id,original_matchday_id,original_home_team_id,original_away_team_id,case_reference,author_id,party_type,party_id,party_label,party_role_id,state,aggregate_version,latest_revision_number,frozen_revision_number,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "incident_reports"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "window"));
            ps.setObject(3, IncidentRestoreSupport.uuid(row, "race"));
            ps.setObject(4, IncidentRestoreSupport.uuid(row, "originalMatchday"));
            ps.setObject(5, IncidentRestoreSupport.uuid(row, "originalHomeTeam"));
            ps.setObject(6, IncidentRestoreSupport.uuid(row, "originalAwayTeam"));
            ps.setObject(7, IncidentRestoreSupport.text(row, "caseReference"));
            ps.setObject(8, IncidentRestoreSupport.text(row, "authorId"));
            ps.setObject(9, IncidentRestoreSupport.text(row.get("party"), "type"));
            ps.setObject(10, IncidentRestoreSupport.uuid(row.get("party"), "id"));
            ps.setObject(11, IncidentRestoreSupport.text(row.get("party"), "label"));
            ps.setObject(12, IncidentRestoreSupport.text(row.get("party"), "roleId"));
            ps.setObject(13, IncidentRestoreSupport.text(row, "state"));
            ps.setObject(14, IncidentRestoreSupport.number(row, "version"));
            ps.setObject(15, IncidentRestoreSupport.number(row, "latestRevisionNumber"));
            ps.setObject(16, IncidentRestoreSupport.number(row, "frozenRevisionNumber"));
            ps.setObject(17, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(18, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
