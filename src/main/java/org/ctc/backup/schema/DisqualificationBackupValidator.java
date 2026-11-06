package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDateTime;
import java.util.*;
import org.ctc.domain.model.RaceDisqualificationHistory;

public final class DisqualificationBackupValidator {
    private static final String HISTORY = "race_disqualification_revisions";
    private static final int MAX_VALUES = 100_000;
    private static final Set<String> HISTORY_FIELDS = Set.of("id", "result", "race", "driver", "season", "phase", "matchday", "match",
            "playoffMatchup", "homeTeam", "awayTeam", "revisionNumber", "previousDisqualified", "disqualified", "recordedPosition",
            "reason", "actorId", "resultFingerprint", "createdAt", "updatedAt");
    private static final Set<String> RESULT_FIELDS = Set.of("id", "race", "driver", "position", "disqualified", "pointsRace", "pointsQuali", "pointsFl", "pointsTotal");
    private final int version;
    private final RacePenaltyBackupValidator context = new RacePenaltyBackupValidator(16);
    private final Set<UUID> drivers = new HashSet<>();
    private final Map<UUID, JsonNode> results = new LinkedHashMap<>();
    private final Map<UUID, JsonNode> history = new LinkedHashMap<>();
    private int retained;

    public DisqualificationBackupValidator(int version) { this.version = version; }

    public void accept(String table, JsonNode row) {
        // Reuse the exact regular/playoff ownership and parent-team policy; do not retain unrelated private penalty metadata.
        if (!table.equals("race_penalty_revisions")) {
            try { context.accept(table, row); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid DSQ context: " + invalid.getMessage()); }
        }
        if (table.equals("drivers")) {
            retain(1); if (!drivers.add(uuid(row, "id"))) fail("Duplicate DSQ driver reference"); return;
        }
        if (!table.equals(HISTORY) && !table.equals("race_results")) return;
        if (row == null || !row.isObject()) fail("Invalid DSQ archive row");
        if (table.equals(HISTORY) && version < 16) fail("DSQ history requires schema version 16");
        var fields = table.equals(HISTORY) ? HISTORY_FIELDS : RESULT_FIELDS;
        if (table.equals(HISTORY)) {
            row.fieldNames().forEachRemaining(field -> { if (!fields.contains(field)) fail("Unexpected DSQ history field"); });
            for (String field : fields) if (!row.has(field)) fail("Missing DSQ history field " + field);
        }
        var projected = JsonNodeFactory.instance.objectNode();
        for (String field : fields) {
            var value = row.get(field); if (value == null) continue;
            if (!(value.isNull() || value.isBoolean() || value.isIntegralNumber()
                    || value.isTextual() && value.textValue().length() <= 2000)) fail("Invalid DSQ scalar " + field);
            projected.set(field, value.deepCopy());
        }
        retain(projected.size());
        var index = table.equals(HISTORY) ? history : results;
        if (index.putIfAbsent(uuid(row, "id"), projected) != null) fail("Duplicate DSQ archive identity");
    }

    public void validate() {
        var byResult = new HashMap<UUID, List<RaceDisqualificationHistory.Entry>>();
        for (var row : history.values()) {
            var resultId = uuid(row, "result");
            if (!results.containsKey(resultId)) fail("Missing DSQ result reference");
            var race = new org.ctc.domain.model.RacePenaltyHistory.Identity(uuid(row, "race"), uuid(row, "season"), uuid(row, "phase"),
                    uuid(row, "matchday"), optionalUuid(row, "match"), optionalUuid(row, "playoffMatchup"), uuid(row, "homeTeam"), uuid(row, "awayTeam"));
            var entry = new RaceDisqualificationHistory.Entry(uuid(row, "id"), new RaceDisqualificationHistory.Identity(resultId, uuid(row, "driver"), race),
                    integer(row, "revisionNumber"), flag(row, "previousDisqualified"), flag(row, "disqualified"), integer(row, "recordedPosition"),
                    text(row, "reason", 2000), text(row, "actorId", 200), text(row, "resultFingerprint", 64), time(row, "createdAt"), time(row, "updatedAt"));
            byResult.computeIfAbsent(resultId, ignored -> new ArrayList<>()).add(entry);
        }
        var protectedRaces = new HashSet<UUID>();
        var positionsByRace = new HashMap<UUID, List<Integer>>();
        var driversByRace = new HashMap<UUID, Set<UUID>>();
        for (var result : results.values()) {
            positionsByRace.computeIfAbsent(uuid(result, "race"), ignored -> new ArrayList<>()).add(integer(result, "position"));
            boolean disqualified = flag(result, "disqualified");
            var driver = uuid(result, "driver"); if (!drivers.contains(driver)) fail("Missing DSQ driver reference");
            if (!driversByRace.computeIfAbsent(uuid(result, "race"), ignored -> new HashSet<>()).add(driver))
                fail("Duplicate DSQ race/driver result ownership");
            var entries = byResult.getOrDefault(uuid(result, "id"), List.of());
            if (entries.isEmpty() && !disqualified) continue;
            var raceId = uuid(result, "race"); protectedRaces.add(raceId);
            org.ctc.domain.model.RacePenaltyHistory.Identity race;
            try { race = context.identityForRace(raceId); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid DSQ race context: " + invalid.getMessage()); }
            var expected = new RaceDisqualificationHistory.Identity(uuid(result, "id"), driver, race);
            var problems = RaceDisqualificationHistory.problems(expected, disqualified, entries);
            if (!problems.isEmpty()) fail("Invalid DSQ history: " + String.join("; ", problems));
            if (disqualified && integer(result, "pointsRace") != 0) fail("DSQ result has race-position points");
            if (disqualified) {
                try {
                    if (integer(result, "pointsTotal") != Math.addExact(integer(result, "pointsQuali"), integer(result, "pointsFl")))
                        fail("DSQ bonus points differ from the result total");
                } catch (ArithmeticException invalid) { fail("DSQ bonus points overflow"); }
            }
        }
        for (UUID race : protectedRaces) {
            var positions = new HashSet<Integer>();
            for (int position : positionsByRace.getOrDefault(race, List.of())) {
                if (position < 1 || position > 12 || !positions.add(position)) fail("DSQ race requires unique finishing positions between 1 and 12");
            }
        }
    }

    private void retain(int count) { retained += count; if (retained > MAX_VALUES) fail("DSQ validation exceeds the 100000-reference budget"); }
    private static UUID optionalUuid(JsonNode row, String field) { return row.hasNonNull(field) ? uuid(row, field) : null; }
    private static UUID uuid(JsonNode row, String field) {
        var value = text(row, field, 36);
        try { var id = UUID.fromString(value); if (!id.toString().equalsIgnoreCase(value)) fail("Invalid DSQ identity " + field); return id; }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid DSQ identity " + field); }
    }
    private static int integer(JsonNode row, String field) {
        var value = row.get(field); if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) fail("Invalid DSQ integer " + field);
        return value.intValue();
    }
    private static boolean flag(JsonNode row, String field) {
        var value = row.get(field); if (value == null || !value.isBoolean()) fail("Invalid DSQ boolean " + field); return value.booleanValue();
    }
    private static String text(JsonNode row, String field, int maximum) {
        var value = row == null ? null : row.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > maximum) fail("Invalid DSQ text " + field);
        return value.textValue();
    }
    private static LocalDateTime time(JsonNode row, String field) {
        try { return LocalDateTime.parse(text(row, field, 40)); }
        catch (java.time.DateTimeException invalid) { throw new IllegalArgumentException("Invalid DSQ timestamp " + field); }
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
