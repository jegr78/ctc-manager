package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDateTime;
import java.util.*;
import org.ctc.domain.model.RacePenaltyHistory;

public final class RacePenaltyBackupValidator {
    private static final int MAX_VALUES = 100_000;
    private static final String HISTORY = "race_penalty_revisions";
    private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
            Map.entry("seasons", Set.of("id")), Map.entry("teams", Set.of("id", "parentTeam")),
            Map.entry("season_phases", Set.of("id", "season")), Map.entry("matchdays", Set.of("id", "phase")),
            Map.entry("playoffs", Set.of("id", "phase")), Map.entry("playoff_rounds", Set.of("id", "playoff")),
            Map.entry("matches", Set.of("id", "matchday", "homeTeam", "awayTeam", "bye", "walkoverTeam")),
            Map.entry("playoff_matchups", Set.of("id", "round", "team1", "team2", "bye", "walkoverTeam")),
            Map.entry("races", Set.of("id", "matchday", "match", "playoffMatchup", "homeTeamOverride", "awayTeamOverride")),
            Map.entry(HISTORY, Set.of("id", "race", "season", "phase", "matchday", "match", "playoffMatchup", "homeTeam", "awayTeam",
                    "revisionNumber", "homePenalty", "awayPenalty", "homeGross", "awayGross", "reason", "actorId", "resultFingerprint", "createdAt", "updatedAt")));
    private final int version;
    private int retained;
    private final Map<String, Map<UUID, JsonNode>> rows = new HashMap<>();

    public RacePenaltyBackupValidator(int version) { this.version = version; }

    public void accept(String table, JsonNode row) {
        if (!FIELDS.containsKey(table)) return;
        if (table.equals(HISTORY) && version < 13) fail("Team penalty history requires schema version 13");
        if (version < 13) return;
        if (row == null || !row.isObject()) fail("Invalid team penalty reference row");
        var projected = JsonNodeFactory.instance.objectNode();
        if (table.equals(HISTORY)) {
            row.fieldNames().forEachRemaining(field -> { if (!FIELDS.get(HISTORY).contains(field)) fail("Unexpected team penalty field"); });
            for (String field : FIELDS.get(HISTORY)) if (!row.has(field)) fail("Missing team penalty field " + field);
        }
        for (String field : FIELDS.get(table)) {
            var value = row.get(field);
            if (value == null) continue;
            if (table.equals("matches") && field.equals("walkoverTeam")) {
                var marker = MatchWalkoverReference.read(value);
                value = marker == null ? JsonNodeFactory.instance.nullNode() : JsonNodeFactory.instance.textNode(marker.toString());
            }
            if (!(value.isNull() || value.isBoolean() || value.isIntegralNumber()
                    || value.isTextual() && value.textValue().length() <= 2000)) fail("Invalid team penalty scalar " + field);
            projected.set(field, value.deepCopy());
        }
        retained += projected.size();
        if (retained > MAX_VALUES) fail("Team penalty validation exceeds the 100000-reference budget");
        if (index(table).putIfAbsent(uuid(row, "id"), projected) != null) fail("Duplicate team penalty reference identity");
    }

    public void validate() {
        var byRace = new HashMap<UUID, List<RacePenaltyHistory.Entry>>();
        for (var row : index(HISTORY).values()) {
            UUID race = reference(row, "race", "races");
            var identity = new RacePenaltyHistory.Identity(race, reference(row, "season", "seasons"),
                    reference(row, "phase", "season_phases"), reference(row, "matchday", "matchdays"),
                    optionalReference(row, "match", "matches"), optionalReference(row, "playoffMatchup", "playoff_matchups"),
                    reference(row, "homeTeam", "teams"), reference(row, "awayTeam", "teams"));
            integer(row, "homeGross"); integer(row, "awayGross");
            var entry = new RacePenaltyHistory.Entry(uuid(row, "id"), identity, integer(row, "revisionNumber"),
                    integer(row, "homePenalty"), integer(row, "awayPenalty"), text(row, "reason", 2000),
                    text(row, "actorId", 200), text(row, "resultFingerprint", 64), time(row, "createdAt"), time(row, "updatedAt"));
            byRace.computeIfAbsent(race, ignored -> new ArrayList<>()).add(entry);
        }
        for (var history : byRace.entrySet()) {
            var expected = identity(index("races").get(history.getKey()));
            var problems = RacePenaltyHistory.problems(expected, history.getValue());
            if (!problems.isEmpty()) fail("Invalid team penalty history: " + String.join("; ", problems));
        }
    }

    private RacePenaltyHistory.Identity identity(JsonNode race) {
        UUID dayId = reference(race, "matchday", "matchdays"); var day = index("matchdays").get(dayId);
        UUID phaseId = reference(day, "phase", "season_phases");
        UUID seasonId = reference(index("season_phases").get(phaseId), "season", "seasons");
        UUID matchId = optionalReference(race, "match", "matches");
        UUID playoffId = optionalReference(race, "playoffMatchup", "playoff_matchups");
        if (matchId == null && playoffId == null) fail("Team penalty race has no pairing");
        JsonNode pair = matchId != null ? index("matches").get(matchId) : index("playoff_matchups").get(playoffId);
        if (matchId != null && !dayId.equals(reference(pair, "matchday", "matchdays"))) fail("Team penalty pairing belongs to another matchday");
        if (pair.path("bye").asBoolean() || pair.hasNonNull("walkoverTeam")) fail("Team penalty history cannot belong to a bye or walkover");
        UUID home = race.hasNonNull("homeTeamOverride") ? reference(race, "homeTeamOverride", "teams")
                : reference(pair, matchId == null ? "team1" : "homeTeam", "teams");
        UUID away = race.hasNonNull("awayTeamOverride") ? reference(race, "awayTeamOverride", "teams")
                : reference(pair, matchId == null ? "team2" : "awayTeam", "teams");
        if (matchId != null && !RacePenaltyHistory.sameParticipants(reference(pair, "homeTeam", "teams"), reference(pair, "awayTeam", "teams"),
                home, away, parent(home), parent(away))) fail("Team penalty race participants differ from the parent match participants");
        if (playoffId != null) {
            var matchup = index("playoff_matchups").get(playoffId);
            var round = index("playoff_rounds").get(reference(matchup, "round", "playoff_rounds"));
            var playoff = index("playoffs").get(reference(round, "playoff", "playoffs"));
            var playoffPhase = index("season_phases").get(reference(playoff, "phase", "season_phases"));
            if (!seasonId.equals(reference(playoffPhase, "season", "seasons"))
                    || matchup.path("bye").asBoolean() || matchup.hasNonNull("walkoverTeam")) fail("Invalid team penalty playoff context");
            UUID team1 = reference(matchup, "team1", "teams"), team2 = reference(matchup, "team2", "teams");
            if (!RacePenaltyHistory.sameParticipants(team1, team2, home, away, parent(home), parent(away))) fail("Team penalty playoff participants differ");
        }
        return new RacePenaltyHistory.Identity(uuid(race, "id"), seasonId, phaseId, dayId, matchId, playoffId, home, away);
    }

    private UUID parent(UUID team) {
        var row = index("teams").get(team);
        return row.hasNonNull("parentTeam") ? reference(row, "parentTeam", "teams") : team;
    }

    private Map<UUID, JsonNode> index(String table) { return rows.computeIfAbsent(table, ignored -> new LinkedHashMap<>()); }
    private UUID optionalReference(JsonNode row, String field, String table) { return row.hasNonNull(field) ? reference(row, field, table) : null; }
    private UUID reference(JsonNode row, String field, String table) {
        UUID id = uuid(row, field); if (!index(table).containsKey(id)) fail("Missing team penalty reference " + field); return id;
    }
    private static UUID uuid(JsonNode row, String field) {
        String value = text(row, field, 36);
        try {
            UUID id = UUID.fromString(value); if (!id.toString().equalsIgnoreCase(value)) fail("Invalid team penalty identity " + field); return id;
        } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid team penalty identity " + field); }
    }
    private static int integer(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) fail("Invalid team penalty integer " + field);
        return value.intValue();
    }
    private static String text(JsonNode row, String field, int maximum) {
        var value = row == null ? null : row.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > maximum) fail("Invalid team penalty text " + field);
        return value.textValue();
    }
    private static LocalDateTime time(JsonNode row, String field) {
        try {
            var value = LocalDateTime.parse(text(row, field, 40));
            if (value.getYear() < 1000 || value.getYear() > 9999) fail("Invalid team penalty timestamp " + field); return value;
        } catch (java.time.DateTimeException invalid) { throw new IllegalArgumentException("Invalid team penalty timestamp " + field); }
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
