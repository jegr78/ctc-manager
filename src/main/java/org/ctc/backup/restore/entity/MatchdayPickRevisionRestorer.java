package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.ctc.backup.schema.PickPayloadReader;

@Component
public class MatchdayPickRevisionRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO matchday_pick_revisions (id,pick_id,revision_number,confirmed_at,submitter_id,request_id,receipt_version," + PickRestoreSupport.PAYLOAD_COLUMNS + ",created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "matchday_pick_revisions"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("pick").asText()));
            ps.setInt(3, row.get("revisionNumber").intValue());
            ps.setTimestamp(4, PickRestoreSupport.instant(row, "confirmedAt"));
            ps.setString(5, row.get("submitterId").asText());
            ps.setObject(6, UUID.fromString(row.get("requestId").asText()));
            ps.setLong(7, row.get("receiptVersion").longValue());
            PickRestoreSupport.payload(ps, 8, PickPayloadReader.read(row.get("payload")));
            ps.setTimestamp(26, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(27, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
