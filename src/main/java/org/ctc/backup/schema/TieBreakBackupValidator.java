package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.ctc.domain.model.TieBreakRules;

/** Checks every archived tie-break decision against its owning phase before a restore wipes data. */
public final class TieBreakBackupValidator {
    private static final int MAX_VALUES = 100_000;
    private final HashMap<UUID, Boolean> phaseRules = new HashMap<>();
    private final HashMap<UUID, UUID> matchdayPhases = new HashMap<>();
    private final HashMap<UUID, UUID> playoffPhases = new HashMap<>();
    private final HashMap<UUID, UUID> roundPlayoffs = new HashMap<>();
    private final ArrayList<Decision> matches = new ArrayList<>();
    private final ArrayList<Decision> matchups = new ArrayList<>();
    private int retained;

    private record Decision(UUID owner, UUID winner, UUID home, UUID away, Integer homeScore, Integer awayScore,
                            boolean bye, UUID walkover) { }

    public void accept(String table, JsonNode row) {
        switch (table) {
            case "season_phases" -> phaseRules.put(id(row, "id"), row.path("tieBreakRaceWinner").asBoolean(false));
            case "matchdays" -> matchdayPhases.put(id(row, "id"), id(row, "phase"));
            case "playoffs" -> playoffPhases.put(id(row, "id"), id(row, "phase"));
            case "playoff_rounds" -> roundPlayoffs.put(id(row, "id"), id(row, "playoff"));
            case "matches" -> {
                if (row.hasNonNull("tieBreakWinner")) matches.add(decision(row, "matchday", "tieBreakWinner", "homeTeam", "awayTeam"));
            }
            case "playoff_matchups" -> {
                if (row.path("decidedByTieBreak").asBoolean(false)) matchups.add(decision(row, "round", "winner", "team1", "team2"));
            }
            default -> { return; }
        }
        if (++retained > MAX_VALUES) throw new IllegalArgumentException("Tie-break validation exceeds the 100000-reference budget");
    }

    public void validate() {
        for (var match : matches) {
            if (!valid(phaseRules.get(matchdayPhases.get(match.owner())), match)) {
                throw new IllegalArgumentException("Invalid match tie-break winner");
            }
        }
        for (var matchup : matchups) {
            var phase = playoffPhases.get(roundPlayoffs.get(matchup.owner()));
            if (matchup.winner() == null || !valid(phaseRules.get(phase), matchup)) {
                throw new IllegalArgumentException("Invalid playoff tie-break decision");
            }
        }
    }

    private static boolean valid(Boolean rule, Decision decision) {
        return TieBreakRules.valid(Boolean.TRUE.equals(rule), decision.winner(), decision.home(), decision.away(),
                decision.homeScore(), decision.awayScore(), decision.bye(), decision.walkover());
    }

    private static Decision decision(JsonNode row, String owner, String winner, String home, String away) {
        return new Decision(id(row, owner), id(row, winner), id(row, home), id(row, away), score(row, "homeScore"),
                score(row, "awayScore"), row.path("bye").asBoolean(false), id(row, "walkoverTeam"));
    }

    private static UUID id(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Invalid reference " + field);
        return UUID.fromString(value.textValue());
    }

    private static Integer score(JsonNode row, String field) {
        var value = row.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }
}
