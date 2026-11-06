package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.Set;

/** Typed window fields shared by preflight and execution; old archives remain closed. */
public final class PickWindowArchive {
    private static final Set<String> TABLES = Set.of("matchdays", "matches", "playoff_matchups");
    private PickWindowArchive() { }

    public static JsonNode normalize(int version, String table, JsonNode row) {
        if (!TABLES.contains(table)) return row;
        if (!(row instanceof ObjectNode object)) throw new IllegalArgumentException("Pick window row must be an object");
        var copy = object.deepCopy();
        if (version < 17) {
            if (copy.has("pickWindowVersion") && (!copy.path("pickWindowVersion").isIntegralNumber() || !copy.path("pickWindowVersion").canConvertToLong() || copy.path("pickWindowVersion").longValue() != 0)
                    || table.equals("matchdays") && copy.has("picksOpen") && (!copy.path("picksOpen").isBoolean() || copy.path("picksOpen").booleanValue())
                    || !table.equals("matchdays") && copy.hasNonNull("pickDeadlineOverride")) {
                throw new IllegalArgumentException("Pick window settings require backup schema 17");
            }
            copy.put("pickWindowVersion", 0L);
            if (table.equals("matchdays")) copy.put("picksOpen", false);
            else copy.putNull("pickDeadlineOverride");
        }
        var token = copy.path("pickWindowVersion");
        if (!token.isIntegralNumber() || !token.canConvertToLong() || token.longValue() < 0) throw new IllegalArgumentException("Invalid pick window version");
        if (table.equals("matchdays")) {
            if (!copy.path("picksOpen").isBoolean()) throw new IllegalArgumentException("Invalid matchday picks opening flag");
        } else {
            var cutoff = copy.get("pickDeadlineOverride");
            if (cutoff == null) throw new IllegalArgumentException("Missing match picks deadline override");
            if (!cutoff.isNull()) {
                if (!cutoff.isTextual() || cutoff.textValue().length() > 40) throw new IllegalArgumentException("Invalid match picks deadline override");
                try {
                    var value = LocalDateTime.parse(cutoff.textValue());
                    if (value.getNano() != 0 || value.getYear() < 1000 || value.getYear() > 9999 || ZoneId.of("Europe/Berlin").getRules().getValidOffsets(value).size() != 1)
                        throw new IllegalArgumentException("Match picks deadline needs an unambiguous Europe/Berlin time");
                } catch (DateTimeException invalid) { throw new IllegalArgumentException("Invalid match picks deadline override", invalid); }
            }
        }
        return copy;
    }
}
