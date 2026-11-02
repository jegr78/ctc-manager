package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.*;
import org.ctc.domain.model.*;

public final class CarPoolBackupValidator {
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "seasons", Set.of("id", "carPoolMode", "carPoolState"),
            "cars", Set.of("id"),
            "season_car_pool_entries", Set.of("id", "season", "label", "sortIndex"),
            "season_car_pool_clauses", Set.of("id", "entry", "category", "drivetrain", "modelYear", "sortIndex"),
            "season_car_pool_members", Set.of("id", "entry", "car"),
            "races", Set.of("id", "matchday", "carPoolEntry", "car", "match", "playoffMatchup", "homeTeamOverride"),
            "matchdays", Set.of("id", "phase"),
            "season_phases", Set.of("id", "season"),
            "matches", Set.of("id", "homeTeam", "bye"),
            "playoff_matchups", Set.of("id", "team1"));
    private static final int MAX_RETAINED_ROWS = 100_000;
    private final int version;
    private final Map<String, Map<String, JsonNode>> rows = new HashMap<>();
    private int retainedRows;

    public CarPoolBackupValidator(int version) { this.version = version; }

    public void accept(String table, JsonNode row) {
        if (!FIELDS.containsKey(table)) return;
        if (version < 4) {
            validateLegacyRow(table, row);
            return;
        }
        if (!(row instanceof ObjectNode object)) throw new IllegalArgumentException("Backup row must be an object.");
        String id = uuid(row, "id");
        var indexed = rows.computeIfAbsent(table, ignored -> new LinkedHashMap<>());
        if (indexed.containsKey(id)) fail("Duplicate ID in " + table);
        if (retainedRows >= MAX_RETAINED_ROWS) fail("Category pool validation exceeds the 100000-row budget.");
        indexed.put(id, scalarProjection(table, object));
        retainedRows++;
    }

    private static ObjectNode scalarProjection(String table, ObjectNode source) {
        var projection = JsonNodeFactory.instance.objectNode();
        for (String field : FIELDS.get(table)) {
            JsonNode value = source.get(field);
            if (value == null) continue;
            boolean valid = value.isNull() || switch (field) {
                case "sortIndex", "modelYear" -> value.isIntegralNumber() && value.canConvertToInt();
                case "bye" -> value.isBoolean();
                default -> value.isTextual() && value.textValue().length() <= (field.equals("label") ? 120 : 36);
            };
            if (!valid) fail("Invalid retained scalar field " + (field.equals("modelYear") ? "model year" : field));
            projection.set(field, value);
        }
        return projection;
    }

    private static void validateLegacyRow(String table, JsonNode row) {
        if (table.startsWith("season_car_pool_") || table.equals("races") && row.hasNonNull("carPoolEntry")) fail("Pool data requires backup schema version 4.");
        if (table.equals("seasons") && (CarPoolMode.valueOf(row.path("carPoolMode").asText("FIXED_CARS")) != CarPoolMode.FIXED_CARS
                || CarPoolState.valueOf(row.path("carPoolState").asText("DRAFT")) != CarPoolState.DRAFT)) fail("Category pools require backup schema version 4.");
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
            if (!row.hasNonNull("carPoolMode") || !row.hasNonNull("carPoolState")) fail("Missing season pool mode/state.");
            var mode = CarPoolMode.valueOf(row.path("carPoolMode").asText("FIXED_CARS"));
            var state = CarPoolState.valueOf(row.path("carPoolState").asText("DRAFT"));
            if (mode == CarPoolMode.FIXED_CARS && state != CarPoolState.DRAFT) fail("Fixed season cannot have a frozen category pool.");
            modes.put(item.getKey(), mode);
            states.put(item.getKey(), state);
        }
        var bySeason = new HashMap<String, List<String>>();
        for (var item : entries.entrySet()) {
            var row = item.getValue();
            var season = reference(row, "season", seasons);
            String label = text(row, "label");
            if (label.isBlank()) fail("Invalid pool entry label.");
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
                if (year.intValue() < 1000 || year.intValue() > 9999) fail("Invalid pool clause year.");
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
        for (var item : entries.entrySet()) {
            var entry = item.getKey();
            if (!definitions.containsKey(entry)) fail("Pool entry has no clauses.");
            var season = uuid(item.getValue(), "season");
            if (states.get(season) == CarPoolState.FROZEN && !memberships.containsKey(entry)) fail("Frozen pool entry is empty.");
        }
        for (var season : seasons.keySet()) {
            if (states.get(season) == CarPoolState.FROZEN && !bySeason.containsKey(season)) fail("Frozen pool is empty.");
        }
        var matchdays = index("matchdays");
        var phases = index("season_phases");
        var matches = index("matches");
        var matchups = index("playoff_matchups");
        var consumption = new HashSet<String>();
        for (var row : index("races").values()) {
            boolean hasEntry = row.hasNonNull("carPoolEntry");
            boolean hasCar = row.hasNonNull("car");
            if (!hasEntry && !hasCar) continue;
            var matchday = reference(row, "matchday", matchdays);
            var phase = reference(matchdays.get(matchday), "phase", phases);
            var season = reference(phases.get(phase), "season", seasons);
            if (modes.get(season) == CarPoolMode.FIXED_CARS) {
                if (hasEntry) fail("Fixed mode race cannot reference a pool entry.");
                continue;
            }
            if (!hasEntry || !hasCar) fail("A category selection requires both entry and car.");
            var entry = reference(row, "carPoolEntry", entries);
            var car = reference(row, "car", cars);
            if (!uuid(entries.get(entry), "season").equals(season)) fail("Race pool entry belongs to a different season.");
            if (!memberships.getOrDefault(entry, Set.of()).contains(car)) fail("Race car is not a frozen pool member.");
            JsonNode match = row.hasNonNull("match") ? matches.get(reference(row, "match", matches)) : null;
            if (match != null && match.path("bye").asBoolean()) fail("A bye cannot consume a pool entry.");
            String home;
            if (row.hasNonNull("homeTeamOverride")) home = uuid(row, "homeTeamOverride");
            else if (match != null) home = uuid(match, "homeTeam");
            else if (row.hasNonNull("playoffMatchup")) home = uuid(matchups.get(reference(row, "playoffMatchup", matchups)), "team1");
            else throw new IllegalArgumentException("Category selection has no effective home team.");
            if (!consumption.add(season + "/" + home + "/" + entry)) fail("Duplicate entry consumption by effective home team.");
        }
    }

    private Map<String, JsonNode> index(String table) {
        return rows.getOrDefault(table, Map.of());
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
