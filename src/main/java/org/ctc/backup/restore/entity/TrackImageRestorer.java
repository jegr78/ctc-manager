package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TrackImageRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO track_images (id,track_id,kind,source,source_key,label,image_path,width,height,sort_index,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "track_images"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> {
            ps.setObject(1, UUID.fromString(row.get("id").asText()));
            ps.setObject(2, UUID.fromString(row.get("track").asText()));
            ps.setString(3, row.get("kind").asText());
            ps.setString(4, row.get("source").asText());
            ps.setString(5, text(row, "sourceKey"));
            ps.setString(6, text(row, "label"));
            ps.setString(7, row.get("imagePath").asText());
            ps.setInt(8, row.get("width").intValue());
            ps.setInt(9, row.get("height").intValue());
            ps.setInt(10, row.get("sortIndex").intValue());
            ps.setTimestamp(11, PickRestoreSupport.auditTime(row, "createdAt"));
            ps.setTimestamp(12, PickRestoreSupport.auditTime(row, "updatedAt"));
        });
    }

    private static String text(JsonNode row, String field) {
        var value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
