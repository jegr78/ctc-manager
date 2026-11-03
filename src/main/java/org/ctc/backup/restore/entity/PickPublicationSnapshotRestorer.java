package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PickPublicationSnapshotRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO pick_publication_snapshots (id,attempt_id,confirmed_at,created_at,updated_at) VALUES (?,?,?,?,?)";
    @Override public String tableName() { return "pick_publication_snapshots"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("attempt").asText()));
            ps.setTimestamp(3, PickRestoreSupport.instant(row, "confirmedAt"));
            ps.setTimestamp(4, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(5, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
