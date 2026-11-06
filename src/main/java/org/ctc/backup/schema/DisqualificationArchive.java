package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DisqualificationArchive {
    private DisqualificationArchive() {}
    public static JsonNode normalize(int version, String table, JsonNode row) {
        if (!table.equals("race_results")) return row;
        if (row == null || !row.isObject()) throw new IllegalArgumentException("Invalid DSQ result row");
        var flag = row.get("disqualified");
        if (version < 16 && flag == null) {
            ObjectNode normalized = row.deepCopy(); normalized.put("disqualified", false); return normalized;
        }
        if (flag == null || !flag.isBoolean() || version < 16 && flag.booleanValue())
            throw new IllegalArgumentException("Invalid DSQ flag for archive schema");
        return row;
    }
}
