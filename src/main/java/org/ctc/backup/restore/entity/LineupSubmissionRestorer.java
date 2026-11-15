package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LineupSubmissionRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO lineup_submissions (id,race_id,team_id,latest_revision_number,applied_revision_number,applied_at,applied_by,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "lineup_submissions"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("race").asText()));
            ps.setObject(3, UUID.fromString(row.get("team").asText()));
            ps.setInt(4, row.get("latestRevisionNumber").intValue());
            if (row.hasNonNull("appliedRevisionNumber")) ps.setInt(5, row.get("appliedRevisionNumber").intValue()); else ps.setNull(5, Types.INTEGER);
            ps.setTimestamp(6, row.hasNonNull("appliedAt") ? PickRestoreSupport.instant(row, "appliedAt") : null);
            ps.setString(7, row.hasNonNull("appliedBy") ? row.get("appliedBy").asText() : null);
            ps.setLong(8, row.get("version").longValue());
            ps.setTimestamp(9, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(10, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
