package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

public final class TieBreakArchive {
    public static final int VERSION = 28;

    private TieBreakArchive() { }

    public static JsonNode normalize(int version, String table, JsonNode row) {
        return switch (table) {
            case "season_phases" -> flag(version, row, "tieBreakRaceWinner");
            case "playoff_matchups" -> flag(version, row, "decidedByTieBreak");
            case "matches" -> winner(version, row);
            default -> row;
        };
    }

    private static JsonNode flag(int version, JsonNode row, String field) {
        var copy = object(row);
        var value = copy.get(field);
        if (version < VERSION) {
            if (value != null && !value.isNull() && !(value.isBoolean() && !value.booleanValue())) throw tooOld();
            copy.put(field, false);
        } else if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("Invalid tie-break flag " + field);
        }
        return copy;
    }

    private static JsonNode winner(int version, JsonNode row) {
        var copy = object(row);
        var value = copy.get("tieBreakWinner");
        if (version < VERSION) {
            if (value != null && !value.isNull()) throw tooOld();
            copy.putNull("tieBreakWinner");
        } else if (value == null) {
            copy.putNull("tieBreakWinner");
        } else if (!value.isNull()) {
            if (!value.isTextual()) throw new IllegalArgumentException("Invalid match tie-break winner");
            UUID.fromString(value.textValue());
        }
        return copy;
    }

    private static ObjectNode object(JsonNode row) {
        if (!(row instanceof ObjectNode object)) throw new IllegalArgumentException("Archive row must be an object");
        return object.deepCopy();
    }

    private static IllegalArgumentException tooOld() {
        return new IllegalArgumentException("Tie-break fields require backup schema 28");
    }
}
