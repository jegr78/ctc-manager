package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.model.PickPublicationContent;
import org.ctc.domain.model.TimeProgressionMode;

/** Strict scalar decoding, without Jackson coercion or private values in error messages. */
public final class PickPublicationContentReader {
    private static final Set<String> FIELDS = Set.of("trackId", "carId", "carPoolEntryId", "trackName", "carName", "poolEntryLabel",
            "laps", "tyreWear", "fuelConsumption", "refuelingSpeed", "initialFuel", "requiredPitStops", "timeProgressionMode",
            "timeProgressionMultiplier", "weather", "timeOfDay", "availableTyres", "mandatoryTyres", "lobbyRulesProfile", "startType", "slipstream");
    private PickPublicationContentReader() { }

    public static PickPublicationContent read(JsonNode row) {
        try {
            if (row == null || !row.isObject() || row.size() != FIELDS.size()) throw invalid();
            row.fieldNames().forEachRemaining(name -> { if (!FIELDS.contains(name)) throw invalid(); });
            var mode = text(row, "timeProgressionMode", 24);
            var rules = LobbyRulesArchive.choices(row);
            return new PickPublicationContent(uuid(row, "trackId"), uuid(row, "carId"), uuid(row, "carPoolEntryId"),
                    text(row, "trackName", 255), text(row, "carName", 1024), text(row, "poolEntryLabel", 255),
                    integer(row, "laps"), integer(row, "tyreWear"), integer(row, "fuelConsumption"), integer(row, "refuelingSpeed"),
                    text(row, "initialFuel", 255), integer(row, "requiredPitStops"), mode == null ? null : TimeProgressionMode.valueOf(mode),
                    integer(row, "timeProgressionMultiplier"), text(row, "weather", 255), text(row, "timeOfDay", 255),
                    text(row, "availableTyres", 255), text(row, "mandatoryTyres", 255), rules.profile(), rules.start(), rules.slipstream());
        } catch (IllegalArgumentException failure) { throw invalid(); }
    }

    private static UUID uuid(JsonNode row, String field) {
        String value = text(row, field, 36);
        if (value == null) return null;
        if (value.length() != 36) throw invalid();
        return UUID.fromString(value);
    }
    private static Integer integer(JsonNode row, String field) {
        if (!row.has(field)) throw invalid();
        var value = row.get(field);
        if (value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid();
        return value.intValue();
    }
    private static String text(JsonNode row, String field, int max) {
        if (!row.has(field)) throw invalid();
        var value = row.get(field);
        if (value.isNull()) return null;
        if (!value.isTextual() || value.textValue().length() > max) throw invalid();
        return value.textValue();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid pick publication content"); }
}
