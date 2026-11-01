package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.ctc.domain.model.*;

public final class CarPoolBackupValidator {
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "seasons", Set.of("id", "carPoolMode", "carPoolState"),
            "cars", Set.of("id"),
            "season_car_pool_entries", Set.of("id", "season", "label", "sortIndex"),
            "season_car_pool_clauses", Set.of("id", "entry", "category", "drivetrain", "modelYear", "sortIndex"),
            "season_car_pool_members", Set.of("id", "entry", "car"),
            "races", Set.of("id", "matchday", "carPoolEntry", "car"),
            "matchdays", Set.of("id", "phase"),
            "season_phases", Set.of("id", "season"));
    private final int version;
    private final Map<String, List<JsonNode>> rows = new HashMap<>();

    public CarPoolBackupValidator(int version) { this.version = version; }

    public void accept(String table, JsonNode row) {
        if (FIELDS.containsKey(table)) {
            if (!(row instanceof ObjectNode object)) throw new IllegalArgumentException("Backup row must be an object.");
            object.retain(FIELDS.get(table));
            rows.computeIfAbsent(table, ignored -> new ArrayList<>()).add(object);
        }
    }

    public void validate() {
        var seasons = index("seasons");
        var cars = index("cars");
        var entries = index("season_car_pool_entries");
        var clauses = index("season_car_pool_clauses");
        var members = index("season_car_pool_members");
        var modes = new HashMap<String, CarPoolMode>();
        var states = new HashMap<String, CarPoolState>();
        for (var item : seasons.entrySet()) {
            var row = item.getValue();
            if (version >= 4 && (!row.hasNonNull("carPoolMode") || !row.hasNonNull("carPoolState"))) fail("Missing season pool mode/state.");
            var mode = CarPoolMode.valueOf(row.path("carPoolMode").asText("FIXED_CARS"));
            var state = CarPoolState.valueOf(row.path("carPoolState").asText("DRAFT"));
            if (version < 4 && (mode != CarPoolMode.FIXED_CARS || state != CarPoolState.DRAFT)) fail("Category pools require backup schema version 4.");
            if (mode == CarPoolMode.FIXED_CARS && state != CarPoolState.DRAFT) fail("Fixed season cannot have a frozen category pool.");
            modes.put(item.getKey(), mode);
            states.put(item.getKey(), state);
        }
        if (version < 4 && (!entries.isEmpty() || !clauses.isEmpty() || !members.isEmpty())) fail("Pool data requires backup schema version 4.");
        var bySeason = new HashMap<String, List<String>>();
        for (var item : entries.entrySet()) {
            var row = item.getValue();
            var season = reference(row, "season", seasons);
            String label = text(row, "label");
            if (label.isBlank() || label.length() > 120) fail("Invalid pool entry label.");
            nonnegativeInt(row, "sortIndex");
            bySeason.computeIfAbsent(season, ignored -> new ArrayList<>()).add(item.getKey());
        }
        var definitions = new HashMap<String, Set<List<String>>>();
        for (var row : clauses.values()) {
            var entry = reference(row, "entry", entries);
            var category = CarCategory.valueOf(text(row, "category"));
            var drivetrain = row.hasNonNull("drivetrain") ? Drivetrain.valueOf(text(row, "drivetrain")) : null;
            if (row.hasNonNull("modelYear")) {
                var year = row.get("modelYear");
                if (!year.isIntegralNumber() || !year.canConvertToInt() || year.intValue() < 1000 || year.intValue() > 9999) fail("Invalid pool clause year.");
            }
            nonnegativeInt(row, "sortIndex");
            var definition = List.of(category.name(), drivetrain == null ? "" : drivetrain.name(), row.path("modelYear").asText(""));
            var entryDefinitions = definitions.computeIfAbsent(entry, ignored -> new HashSet<>());
            if (!entryDefinitions.add(definition)) fail("Duplicate pool clauses.");
            if (entryDefinitions.size() > 32) fail("Too many pool clauses.");
        }
        var memberships = new HashMap<String, Set<String>>();
        var seasonCars = new HashMap<String, Set<String>>();
        for (var row : members.values()) {
            var entry = reference(row, "entry", entries);
            var car = reference(row, "car", cars);
            var season = uuid(entries.get(entry), "season");
            if (states.get(season) != CarPoolState.FROZEN || modes.get(season) != CarPoolMode.CATEGORY_POOL) fail("A draft pool cannot have snapshot members.");
            if (!memberships.computeIfAbsent(entry, ignored -> new HashSet<>()).add(car)) fail("Duplicate snapshot entry/car pair.");
            if (!seasonCars.computeIfAbsent(season, ignored -> new HashSet<>()).add(car)) fail("Frozen entries overlap.");
        }
        for (var entry : entries.keySet()) {
            if (!definitions.containsKey(entry)) fail("Pool entry has no clauses.");
            var season = uuid(entries.get(entry), "season");
            if (states.get(season) == CarPoolState.FROZEN && !memberships.containsKey(entry)) fail("Frozen pool entry is empty.");
        }
        for (var season : seasons.keySet()) {
            if (states.get(season) == CarPoolState.FROZEN && !bySeason.containsKey(season)) fail("Frozen pool is empty.");
        }
        var matchdays = index("matchdays");
        var phases = index("season_phases");
        for (var row : list("races")) {
            if (!row.hasNonNull("carPoolEntry")) continue;
            var entry = reference(row, "carPoolEntry", entries);
            var car = reference(row, "car", cars);
            var matchday = reference(row, "matchday", matchdays);
            var phase = reference(matchdays.get(matchday), "phase", phases);
            var season = reference(phases.get(phase), "season", seasons);
            if (!uuid(entries.get(entry), "season").equals(season)) fail("Race pool entry belongs to a different season.");
            if (!memberships.getOrDefault(entry, Set.of()).contains(car)) fail("Race car is not a frozen pool member.");
        }
    }

    private List<JsonNode> list(String table) { return rows.getOrDefault(table, List.of()); }

    private Map<String, JsonNode> index(String table) {
        var result = new LinkedHashMap<String, JsonNode>();
        for (var row : list(table)) {
            if (result.putIfAbsent(uuid(row, "id"), row) != null) fail("Duplicate ID in " + table);
        }
        return result;
    }

    private static String reference(JsonNode row, String field, Map<String, JsonNode> targets) {
        var id = uuid(row, field);
        if (!targets.containsKey(id)) fail("Missing " + field + " reference " + id);
        return id;
    }

    private static String uuid(JsonNode row, String field) { return UUID.fromString(text(row, field)).toString(); }

    private static String text(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException("Invalid pool " + field + ".");
        return value.textValue();
    }

    private static void nonnegativeInt(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) fail("Invalid pool " + field);
    }

    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
