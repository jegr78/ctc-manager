package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentDeliveryRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO incident_deliveries (id,report_id,revision_id,operation,operation_key,guild_id,channel_id,nonce,case_reference,party_label,captured_at,state,aggregate_version,attempts,claim_token,claim_generation,lease_until,next_attempt_at,completed_at,message_id,failure_code,reconciled_by,reconciliation_reason,created_at,updated_at,pairing_label,race_label,presentation_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "incident_deliveries"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, IncidentRestoreSupport.uuid(row, "id"));
            ps.setObject(2, IncidentRestoreSupport.uuid(row, "report"));
            ps.setObject(3, IncidentRestoreSupport.uuid(row, "revision"));
            ps.setObject(4, IncidentRestoreSupport.text(row, "operation"));
            ps.setObject(5, IncidentRestoreSupport.text(row, "operationKey"));
            ps.setObject(6, IncidentRestoreSupport.text(row, "guildId"));
            ps.setObject(7, IncidentRestoreSupport.text(row, "channelId"));
            ps.setObject(8, IncidentRestoreSupport.text(row, "nonce"));
            ps.setObject(9, IncidentRestoreSupport.text(row, "caseReference"));
            ps.setObject(10, IncidentRestoreSupport.text(row, "partyLabel"));
            ps.setObject(11, IncidentRestoreSupport.instant(row, "capturedAt"));
            ps.setObject(12, IncidentRestoreSupport.text(row, "state"));
            ps.setObject(13, IncidentRestoreSupport.number(row, "version"));
            ps.setObject(14, IncidentRestoreSupport.number(row, "attempts"));
            ps.setObject(15, IncidentRestoreSupport.uuid(row, "claimToken"));
            ps.setObject(16, IncidentRestoreSupport.text(row, "claimGeneration"));
            ps.setObject(17, IncidentRestoreSupport.instant(row, "leaseUntil"));
            ps.setObject(18, IncidentRestoreSupport.instant(row, "nextAttemptAt"));
            ps.setObject(19, IncidentRestoreSupport.instant(row, "completedAt"));
            ps.setObject(20, IncidentRestoreSupport.text(row, "messageId"));
            ps.setObject(21, IncidentRestoreSupport.text(row, "failureCode"));
            ps.setObject(22, IncidentRestoreSupport.text(row, "reconciledBy"));
            ps.setObject(23, IncidentRestoreSupport.text(row, "reconciliationReason"));
            ps.setObject(24, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setObject(25, PickRestoreSupport.auditTime(row, "updatedAt"));
            ps.setObject(26, IncidentRestoreSupport.text(row, "pairingLabel"));
            ps.setObject(27, IncidentRestoreSupport.text(row, "raceLabel"));
            ps.setObject(28, IncidentRestoreSupport.number(row, "presentationVersion"));
        });
    }
}
