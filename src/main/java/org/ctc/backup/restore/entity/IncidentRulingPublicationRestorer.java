package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentRulingPublicationRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_ruling_publications (id,revision_id,thread_publication_id,guild_id,channel_id,content,previous_content,previous_revision_number,previous_message_id,nonce,requested_by,requested_at,state,aggregate_version,attempts,claim_token,claim_generation,lease_until,next_attempt_at,completed_at,message_id,failure_code,reconciled_by,reconciliation_reason,created_at,updated_at,restore_reviewed) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE)";
    @Override public String tableName() { return "incident_ruling_publications"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "revision"));
            ps.setObject(3, IncidentRestoreSupport.uuid(row, "threadPublication"));
            ps.setObject(4, IncidentRestoreSupport.text(row, "guildId"));
            ps.setObject(5, IncidentRestoreSupport.text(row, "channelId"));
            ps.setObject(6, IncidentRestoreSupport.text(row, "content"));
            ps.setObject(7, IncidentRestoreSupport.text(row, "previousContent"));
            ps.setObject(8, IncidentRestoreSupport.number(row, "previousRevisionNumber"));
            ps.setObject(9, IncidentRestoreSupport.text(row, "previousMessageId"));
            ps.setObject(10, IncidentRestoreSupport.text(row, "nonce"));
            ps.setObject(11, IncidentRestoreSupport.text(row, "requestedBy"));
            ps.setObject(12, IncidentRestoreSupport.instant(row, "requestedAt"));
            ps.setObject(13, IncidentRestoreSupport.text(row, "state"));
            ps.setObject(14, IncidentRestoreSupport.number(row, "version"));
            ps.setObject(15, IncidentRestoreSupport.number(row, "attempts"));
            ps.setObject(16, IncidentRestoreSupport.uuid(row, "claimToken"));
            ps.setObject(17, IncidentRestoreSupport.text(row, "claimGeneration"));
            ps.setObject(18, IncidentRestoreSupport.instant(row, "leaseUntil"));
            ps.setObject(19, IncidentRestoreSupport.instant(row, "nextAttemptAt"));
            ps.setObject(20, IncidentRestoreSupport.instant(row, "completedAt"));
            ps.setObject(21, IncidentRestoreSupport.text(row, "messageId"));
            ps.setObject(22, IncidentRestoreSupport.text(row, "failureCode"));
            ps.setObject(23, IncidentRestoreSupport.text(row, "reconciledBy"));
            ps.setObject(24, IncidentRestoreSupport.text(row, "reconciliationReason"));
            ps.setObject(25, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(26, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
