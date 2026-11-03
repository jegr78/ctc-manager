package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.util.UUID;

final class IncidentRestoreSupport {
    private IncidentRestoreSupport() { }
    static UUID uuid(JsonNode row, String field) { return row.hasNonNull(field) ? UUID.fromString(row.get(field).textValue()) : null; }
    static String text(JsonNode row, String field) { return row.hasNonNull(field) ? row.get(field).textValue() : null; }
    static Long number(JsonNode row, String field) { return row.hasNonNull(field) ? row.get(field).longValue() : null; }
    static Timestamp instant(JsonNode row, String field) { return row.hasNonNull(field) ? PickRestoreSupport.instant(row, field) : null; }
}
