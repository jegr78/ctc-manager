package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cross-checks a bridge's optional cutoff against the canonical playoff pairing. */
public final class PickWindowBackupValidator {
    private final Map<UUID, LocalDateTime> matches = new HashMap<>();
    private final Map<UUID, LocalDateTime> playoffs = new HashMap<>();
    private final ArrayList<Bridge> bridges = new ArrayList<>();
    private int retained;
    private record Bridge(UUID match, UUID playoff) { }

    public void accept(String table, JsonNode row) {
        if (table.equals("matches") || table.equals("playoff_matchups")) {
            var cutoff = row.hasNonNull("pickDeadlineOverride") ? LocalDateTime.parse(row.get("pickDeadlineOverride").textValue()) : null;
            (table.equals("matches") ? matches : playoffs).put(id(row, "id"), cutoff);
            retained++;
        } else if (table.equals("races") && row.hasNonNull("match") && row.hasNonNull("playoffMatchup")) {
            bridges.add(new Bridge(id(row, "match"), id(row, "playoffMatchup")));
            retained += 2;
        }
        if (retained > 100_000) throw new IllegalArgumentException("Pick window validation exceeds the 100000-reference budget");
    }

    public void validate() {
        for (var bridge : bridges) {
            if (!matches.containsKey(bridge.match()) || !playoffs.containsKey(bridge.playoff())) throw new IllegalArgumentException("Missing pick window pairing reference");
            var local = matches.get(bridge.match());
            if (local != null && !local.equals(playoffs.get(bridge.playoff()))) throw new IllegalArgumentException("The bridge picks deadline differs from its canonical playoff pairing");
        }
    }
    private static UUID id(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException("Invalid pick window pairing reference");
        return UUID.fromString(value.textValue());
    }
}
