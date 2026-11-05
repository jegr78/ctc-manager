package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.LobbyRulesProfile;
import org.ctc.domain.model.RaceStartType;
import org.ctc.domain.model.RaceSlipstream;

public final class LobbyRulesBackupValidator {
    private static final Set<String> TABLES = Set.of("seasons", "season_phases", "matchdays", "races", "race_settings");
    private final int limit;
    private int retained;
    private final Map<String, LobbyRulesProfile> seasons = new HashMap<>();
    private final Map<String, String> phases = new HashMap<>();
    private final Map<String, String> days = new HashMap<>();
    private final Map<String, String> races = new HashMap<>();
    private final Map<String, Settings> settings = new LinkedHashMap<>();

    public LobbyRulesBackupValidator() { this(100_000); }
    LobbyRulesBackupValidator(int limit) { this.limit = limit; }
    public static boolean supports(String table) { return TABLES.contains(table); }

    public void accept(String table, JsonNode row) {
        if (!supports(table)) return;
        if (++retained > limit) throw new ValidationException("Lobby rules validation exceeds its compact reference budget");
        try {
            String id = uuid(row, "id");
            switch (table) {
                case "seasons" -> put(seasons, id, LobbyRulesArchive.profile(row));
                case "season_phases" -> put(phases, id, uuid(row, "season"));
                case "matchdays" -> put(days, id, uuid(row, "phase"));
                case "races" -> put(races, id, uuid(row, "matchday"));
                case "race_settings" -> put(settings, id, new Settings(uuid(row, "race"),
                        RaceStartType.valueOf(LobbyRulesArchive.text(row, "startType")),
                        RaceSlipstream.valueOf(LobbyRulesArchive.text(row, "slipstream"))));
                default -> throw new ValidationException("Unknown lobby rules reference table");
            }
        } catch (ValidationException failure) { throw failure; }
        catch (IllegalArgumentException failure) { throw new ValidationException("Invalid lobby rules reference fields"); }
    }

    public void validate() {
        for (String season : phases.values()) require(seasons.containsKey(season));
        for (String phase : days.values()) require(phases.containsKey(phase));
        for (String day : races.values()) require(days.containsKey(day));
        var configuredRaces = new HashSet<String>();
        for (var value : settings.values()) {
            require(configuredRaces.add(value.race()));
            String day = reference(races, value.race());
            String phase = reference(days, day);
            var profile = seasons.get(reference(phases, phase));
            require(profile != null);
            try { profile.requireChoices(value.start(), value.slipstream()); }
            catch (BusinessRuleException failure) {
                throw new ValidationException("Invalid lobby choices for race " + value.race());
            }
        }
    }

    private static String uuid(JsonNode row, String field) {
        String value = LobbyRulesArchive.text(row, field);
        if (value.length() != 36) throw new ValidationException("Invalid lobby rules identity");
        var id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new ValidationException("Invalid lobby rules identity");
        return id.toString();
    }

    private static <T> void put(Map<String, T> rows, String id, T value) {
        if (rows.putIfAbsent(id, value) != null) throw new ValidationException("Duplicate lobby rules identity " + id);
    }
    private static String reference(Map<String, String> rows, String id) {
        var value = rows.get(id); require(value != null); return value;
    }
    private static void require(boolean condition) {
        if (!condition) throw new ValidationException("Missing or ambiguous lobby rules context");
    }
    private record Settings(String race, RaceStartType start, RaceSlipstream slipstream) { }
    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) { super(message); }
    }
}
