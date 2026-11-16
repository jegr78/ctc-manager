package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MusicTrackRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO music_tracks (id,title,artist,credit,file_path,duration_seconds,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)";

    @Override public String tableName() { return "music_tracks"; }

    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setString(2, row.get("title").asText());
            ps.setString(3, row.get("artist").asText());
            var credit = row.get("credit");
            ps.setString(4, credit == null || credit.isNull() ? null : credit.asText());
            ps.setString(5, row.get("filePath").asText());
            ps.setInt(6, row.get("durationSeconds").intValue());
            ps.setTimestamp(7, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(8, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }
}
