package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/** Binds a car or track gallery row; both tables share their columns and differ only in the owner. */
final class GalleryImageRows {
    private GalleryImageRows() { }

    static void bind(PreparedStatement ps, JsonNode row, String owner) throws SQLException {
        ps.setObject(1, UUID.fromString(row.get("id").asText()));
        ps.setObject(2, UUID.fromString(row.get(owner).asText()));
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
    }

    private static String text(JsonNode row, String field) {
        var value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
