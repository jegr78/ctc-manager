package org.ctc.domain.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.ctc.domain.exception.BusinessRuleException;

public final class MatchdayPickWindowRules {
    private MatchdayPickWindowRules() { }

    public static LocalDateTime deadline(Race race) {
        var match = race.getMatch();
        var playoff = race.getPlayoffMatchup();
        if (match != null && match.getPickDeadlineOverride() != null || playoff != null && playoff.getPickDeadlineOverride() != null) requireOwnedDeadline(race);
        if (playoff != null) {
            if (match != null && match.getPickDeadlineOverride() != null
                    && !Objects.equals(match.getPickDeadlineOverride(), playoff.getPickDeadlineOverride())) {
                throw new BusinessRuleException("The pairing deadline has inconsistent playoff ownership; management must review it");
            }
            return playoff.getPickDeadlineOverride() != null ? playoff.getPickDeadlineOverride() : race.getMatchday().getPickDeadline();
        }
        return match != null && match.getPickDeadlineOverride() != null
                ? match.getPickDeadlineOverride() : race.getMatchday().getPickDeadline();
    }

    private static void requireOwnedDeadline(Race race) {
        var day = race.getMatchday();
        if (day == null || day.getSeason() == null || day.getSeason().getId() == null
                || race.getMatch() != null && (race.getMatch().getMatchday() == null || !Objects.equals(day.getId(), race.getMatch().getMatchday().getId()))) {
            throw new BusinessRuleException("The pick deadline ownership requires management review");
        }
        var playoff = race.getPlayoffMatchup();
        if (playoff != null && (playoff.getRound() == null || playoff.getRound().getPlayoff() == null
                || playoff.getRound().getPlayoff().getSeason() == null
                || !day.getSeason().getId().equals(playoff.getRound().getPlayoff().getSeason().getId()))) {
            throw new BusinessRuleException("The pick deadline ownership requires management review");
        }
    }

    public static boolean complete(List<Match> matches, List<Race> races, int expectedLegs) {
        if (expectedLegs < 1 || races.stream().anyMatch(race -> race.getMatch() == null && race.getPlayoffMatchup() == null)) return false;
        var playoffs = new LinkedHashMap<java.util.UUID, PlayoffMatchup>();
        var bridges = races.stream().filter(race -> race.getPlayoffMatchup() != null && race.getMatch() != null)
                .map(race -> race.getMatch().getId()).collect(Collectors.toSet());
        if (races.stream().anyMatch(race -> race.getMatch() != null && race.getPlayoffMatchup() == null && bridges.contains(race.getMatch().getId()))) return false;
        var matchIds = matches.stream().map(Match::getId).collect(Collectors.toSet());
        if (races.stream().anyMatch(race -> race.getMatch() != null && !matchIds.contains(race.getMatch().getId()))) return false;
        for (var race : races) if (race.getPlayoffMatchup() != null) playoffs.put(race.getPlayoffMatchup().getId(), race.getPlayoffMatchup());
        var ordinary = matches.stream().filter(match -> !bridges.contains(match.getId())).toList();
        if (ordinary.isEmpty() && playoffs.isEmpty()) return false;
        for (var match : ordinary) {
            if (match.isBye() || match.getWalkoverTeam() != null) continue;
            if (match.getHomeScore() == null || match.getAwayScore() == null) return false;
            var legs = races.stream().filter(race -> race.getPlayoffMatchup() == null && race.getMatch() != null
                    && match.getId().equals(race.getMatch().getId())).toList();
            if (legs.stream().anyMatch(race -> !race.getResults().isEmpty())
                    && (legs.size() < expectedLegs || legs.stream().anyMatch(race -> race.getResults().isEmpty()))) return false;
        }
        return playoffs.values().stream().allMatch(pairing -> pairing.isBye() || pairing.isComplete());
    }
}
