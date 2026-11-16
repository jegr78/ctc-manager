package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TrackShortNameArchive {

    public static final int SHORT_NAME_VERSION = 26;
    private static final int MAX_LENGTH = 30;

    private TrackShortNameArchive() {
    }

    public static JsonNode normalize(int version, String table, JsonNode row) {
        if (!"tracks".equals(table)) {
            return row;
        }
        if (!(row instanceof ObjectNode object)) {
            throw new IllegalArgumentException("Track row must be an object");
        }
        var copy = object.deepCopy();
        var value = copy.get("displayShortName");
        if (version < SHORT_NAME_VERSION) {
            if (value != null && !value.isNull()) {
                throw new IllegalArgumentException("Track short names require backup schema 26");
            }
            copy.putNull("displayShortName");
            return copy;
        }
        if (value == null) {
            copy.putNull("displayShortName");
        } else if (!value.isNull() && (!value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > MAX_LENGTH)) {
            throw new IllegalArgumentException("Invalid track short name");
        }
        return copy;
    }
}
