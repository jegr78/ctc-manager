package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.LobbyRulesProfile;
import org.ctc.domain.model.RaceStartType;
import org.ctc.domain.model.RaceSlipstream;

public final class LobbyRulesArchive {
    private LobbyRulesArchive() { }

    public static JsonNode normalize(int version, String table, JsonNode row) {
        if (!java.util.Set.of("seasons", "race_settings", "matchday_pick_revisions", "matchday_pick_decisions", "pick_publication_attempts").contains(table)) return row;
        if (version < 5 && table.startsWith("matchday_pick_") || version < 7 && table.equals("pick_publication_attempts")) return row;
        if (row == null || !row.isObject()) throw invalid();
        ObjectNode normalized = row.deepCopy();
        switch (table) {
            case "seasons" -> {
                defaultText(version, normalized, "lobbyRulesProfile", "LEGACY");
                profile(normalized);
            }
            case "race_settings" -> {
                defaultText(version, normalized, "startType", "GRID_FALSE_START");
                defaultText(version, normalized, "slipstream", "REAL");
                try { RaceStartType.valueOf(text(normalized, "startType")); RaceSlipstream.valueOf(text(normalized, "slipstream")); }
                catch (IllegalArgumentException failure) { throw invalid(); }
            }
            case "matchday_pick_revisions" -> normalized.set("payload", normalizeChoices(version, normalized.get("payload")));
            case "matchday_pick_decisions" -> {
                if (normalized.hasNonNull("correctionPayload")) normalized.set("correctionPayload", normalizeChoices(version, normalized.get("correctionPayload")));
            }
            case "pick_publication_attempts" -> {
                var mask = normalized.get("fieldMask");
                if (version < 14 && mask != null && mask.isIntegralNumber() && (mask.longValue() & ~8191L) != 0) throw invalid();
                normalized.set("content", normalizeChoices(version, normalized.get("content")));
            }
            default -> throw invalid();
        }
        return normalized;
    }

    private static JsonNode normalizeChoices(int version, JsonNode row) {
        if (row == null || !row.isObject()) throw invalid();
        ObjectNode result = row.deepCopy();
        defaultText(version, result, "lobbyRulesProfile", "LEGACY");
        defaultText(version, result, "startType", "GRID_FALSE_START");
        defaultText(version, result, "slipstream", "REAL");
        choices(result);
        return result;
    }

    private static void defaultText(int version, ObjectNode row, String field, String legacy) {
        if (version < 14) {
            if (!row.has(field)) row.put(field, legacy);
            if (!legacy.equals(text(row, field))) throw invalid();
        } else text(row, field);
    }

    public static LobbyRulesProfile profile(JsonNode row) {
        try { return LobbyRulesProfile.valueOf(text(row, "lobbyRulesProfile")); }
        catch (IllegalArgumentException failure) { throw invalid(); }
    }

    public static Choices choices(JsonNode row) {
        try {
            var profile = profile(row);
            var start = RaceStartType.valueOf(text(row, "startType"));
            var slipstream = RaceSlipstream.valueOf(text(row, "slipstream"));
            profile.requireChoices(start, slipstream);
            return new Choices(profile, start, slipstream);
        } catch (IllegalArgumentException | BusinessRuleException failure) { throw invalid(); }
    }

    static String text(JsonNode row, String field) {
        if (row == null || !row.isObject() || !row.hasNonNull(field) || !row.get(field).isTextual()) throw invalid();
        return row.get(field).textValue();
    }

    public record Choices(LobbyRulesProfile profile, RaceStartType start, RaceSlipstream slipstream) { }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid lobby rules archive fields or version"); }
}
