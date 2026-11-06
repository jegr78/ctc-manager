package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Validates new window settings without rewriting closed legacy relationships. */
public final class PickWindowBackupValidator {
    private static final Set<String> TABLES = Set.of("seasons", "season_phases", "matchdays", "matches", "playoffs", "playoff_rounds", "playoff_matchups", "races");
    private final Set<UUID> seasons = new HashSet<>();
    private final Map<UUID, UUID> phaseSeasons = new HashMap<>(), playoffPhases = new HashMap<>(), roundPlayoffs = new HashMap<>();
    private final Map<UUID, Day> days = new HashMap<>();
    private final Map<UUID, Match> matches = new HashMap<>();
    private final Map<UUID, Pairing> pairings = new HashMap<>();
    private final ArrayList<Race> races = new ArrayList<>();
    private int retained;
    private record Day(UUID phase, boolean open) { }
    private record Match(UUID day, LocalDateTime cutoff) { }
    private record Pairing(UUID round, LocalDateTime cutoff) { }
    private record Race(UUID day, UUID match, UUID playoff) { }

    public static boolean supports(String table) { return TABLES.contains(table); }
    public void accept(String table, JsonNode row) {
        if (!supports(table)) return;
        UUID id = reference(row, "id");
        if (id == null) throw invalid();
        switch (table) {
            case "seasons" -> seasons.add(id);
            case "season_phases" -> phaseSeasons.put(id, reference(row, "season"));
            case "matchdays" -> days.put(id, new Day(reference(row, "phase"), row.path("picksOpen").asBoolean(false)));
            case "matches" -> matches.put(id, new Match(reference(row, "matchday"), cutoff(row)));
            case "playoffs" -> playoffPhases.put(id, reference(row, "phase"));
            case "playoff_rounds" -> roundPlayoffs.put(id, reference(row, "playoff"));
            case "playoff_matchups" -> pairings.put(id, new Pairing(reference(row, "round"), cutoff(row)));
            case "races" -> races.add(new Race(reference(row, "matchday"), reference(row, "match"), reference(row, "playoffMatchup")));
            default -> throw new IllegalStateException("Unexpected pick window table");
        }
        retained += 4;
        if (retained > 100_000) throw new IllegalArgumentException("Pick window validation exceeds the 100000-reference budget");
    }

    public void validate() {
        for (var race : races) {
            var match = matches.get(race.match()); var pairing = pairings.get(race.playoff());
            if (race.match() != null && race.playoff() != null && match != null && match.cutoff() != null
                    && (pairing == null || !match.cutoff().equals(pairing.cutoff()))) {
                throw new IllegalArgumentException("The bridge picks deadline differs from its canonical playoff pairing");
            }
        }
        for (var match : matches.values()) if (match.cutoff() != null) daySeason(match.day());
        for (var pairing : pairings.values()) if (pairing.cutoff() != null) playoffSeason(pairing);
        for (var race : races) {
            var day = days.get(race.day()); var match = matches.get(race.match()); var pairing = pairings.get(race.playoff());
            boolean active = day != null && day.open() || match != null && match.cutoff() != null || pairing != null && pairing.cutoff() != null;
            if (!active) continue;
            UUID season = daySeason(race.day());
            if (race.match() != null && (match == null || !race.day().equals(match.day()))) throw invalid();
            if (race.playoff() != null && (pairing == null || !season.equals(playoffSeason(pairing)))) throw invalid();
        }
    }

    private UUID daySeason(UUID id) {
        var day = days.get(id);
        return season(day == null ? null : day.phase());
    }
    private UUID playoffSeason(Pairing pairing) {
        return season(playoffPhases.get(roundPlayoffs.get(pairing.round())));
    }
    private UUID season(UUID phase) {
        UUID season = phaseSeasons.get(phase);
        if (season == null || !seasons.contains(season)) throw invalid();
        return season;
    }
    private static LocalDateTime cutoff(JsonNode row) {
        return row.hasNonNull("pickDeadlineOverride") ? LocalDateTime.parse(row.get("pickDeadlineOverride").textValue()) : null;
    }
    private static UUID reference(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return UUID.fromString(value.textValue());
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Pick window ownership disagrees with its matchday, pairing or season");
    }
}
