package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Set;
import java.util.HashSet;
import org.ctc.domain.model.PickPayload;

public final class PickPayloadReader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> INTEGERS = Set.of("laps", "tyreWear", "fuelConsumption", "refuelingSpeed", "requiredPitStops");
    private static final Set<String> TEXT = Set.of("trackId", "carId", "initialFuel", "timeProgressionMode", "weather", "timeOfDay",
            "lobbyRulesProfile", "startType", "slipstream");
    private static final Set<String> OPTIONAL = Set.of("carPoolEntryId", "timeProgressionMultiplier");
    private static final Set<String> TYRES = Set.of("availableTyres", "mandatoryTyres");
    private PickPayloadReader() { }
    public static PickPayload read(JsonNode row) {
        if (row == null || !row.isObject()) throw invalid();
        var expected = new HashSet<>(INTEGERS);
        expected.addAll(TEXT);
        expected.addAll(OPTIONAL);
        expected.addAll(TYRES);
        var fields = new HashSet<String>();
        row.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(expected)) throw invalid();
        for (String field : INTEGERS) {
            if (!row.get(field).isIntegralNumber() || !row.get(field).canConvertToInt()) throw invalid();
        }
        for (String field : TEXT) {
            if (!row.get(field).isTextual()) throw invalid();
        }
        if (!row.get("carPoolEntryId").isNull() && !row.get("carPoolEntryId").isTextual()) throw invalid();
        var multiplier = row.get("timeProgressionMultiplier");
        if (!multiplier.isNull() && (!multiplier.isIntegralNumber() || !multiplier.canConvertToInt())) throw invalid();
        for (String field : TYRES) {
            int maximum = field.equals("availableTyres") ? 5 : 3;
            if (!row.get(field).isArray() || row.get(field).size() > maximum) throw invalid();
            var distinct = new HashSet<String>();
            for (var tyre : row.get(field)) {
                if (!tyre.isTextual() || !distinct.add(tyre.textValue())) throw invalid();
            }
        }
        try {
            LobbyRulesArchive.choices(row);
            return MAPPER.treeToValue(row, PickPayload.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid pick payload field types or shape");
    }
}
