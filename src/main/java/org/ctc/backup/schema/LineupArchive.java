package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ctc.domain.model.Season;

/** Season lineup sizes arrive with schema 23; older archives restore the historical six drivers per team. */
public final class LineupArchive {
    public static final int LINEUP_VERSION = 23;

    private LineupArchive() { }

    public static JsonNode normalize(int version, String table, JsonNode row) {
        if (!"seasons".equals(table)) return row;
        if (!(row instanceof ObjectNode object)) throw new IllegalArgumentException("Season row must be an object");
        var copy = object.deepCopy();
        var value = copy.get("lineupSize");
        if (version < LINEUP_VERSION) {
            if (value != null && !value.isNull() && !(value.isIntegralNumber() && value.intValue() == Season.LINEUP_SIZE_DEFAULT)) {
                throw new IllegalArgumentException("Season lineup sizes other than " + Season.LINEUP_SIZE_DEFAULT + " require backup schema 23");
            }
            copy.put("lineupSize", Season.LINEUP_SIZE_DEFAULT);
        } else if (value == null || !value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > Season.LINEUP_SIZE_MAX) {
            throw new IllegalArgumentException("Season lineup size must be between 1 and " + Season.LINEUP_SIZE_MAX);
        }
        return copy;
    }

    public static int lineupSize(JsonNode row) {
        var value = row.get("lineupSize");
        if (value == null || !value.isIntegralNumber()) return Season.LINEUP_SIZE_DEFAULT;
        return value.asInt();
    }
}
