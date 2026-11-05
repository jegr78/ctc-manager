package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public final class MatchWalkoverReference {
    private MatchWalkoverReference() {}

    public static UUID read(JsonNode value) {
        if (value == null || value.isNull()) return null;
        return requiredId(value.isObject() ? value.get("id") : value);
    }

    public static UUID requiredId(JsonNode value) {
        if (value == null || !value.isTextual() || value.textValue().length() != 36) throw invalid();
        try {
            var id = UUID.fromString(value.textValue());
            if (!id.toString().equalsIgnoreCase(value.textValue())) throw invalid();
            return id;
        } catch (IllegalArgumentException failure) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid match walkover team reference");
    }
}
