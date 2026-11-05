package org.ctc.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;

public final class RacePenaltyHistory {
    public static final int MAX_REVISIONS = 100_000;
    private RacePenaltyHistory() {}

    public record Identity(UUID race, UUID season, UUID phase, UUID matchday, UUID match, UUID playoff,
                           UUID home, UUID away) {}
    public record Entry(UUID id, Identity identity, int revision, int homePenalty, int awayPenalty,
                        String reason, String actor, String fingerprint, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public static Identity identity(Race race) {
        var day = race.getMatchday();
        var phase = day == null ? null : day.getPhase();
        return new Identity(race.getId(), phase == null ? null : id(phase.getSeason()), id(phase), id(day),
                id(race.getMatch()), id(race.getPlayoffMatchup()), id(race.getHomeTeam()), id(race.getAwayTeam()));
    }

    public static Entry entry(RacePenaltyRevision row) {
        var identity = new Identity(id(row.getRace()), id(row.getSeason()), id(row.getPhase()), id(row.getMatchday()),
                id(row.getMatch()), id(row.getPlayoffMatchup()), id(row.getHomeTeam()), id(row.getAwayTeam()));
        return new Entry(row.getId(), identity, row.getRevisionNumber(), row.getHomePenalty(), row.getAwayPenalty(),
                row.getReason(), row.getActorId(), row.getResultFingerprint(), row.getCreatedAt(), row.getUpdatedAt());
    }

    public static List<String> problems(Identity expected, List<Entry> entries) {
        var problems = new ArrayList<String>();
        if (entries.isEmpty()) return problems;
        if (entries.size() > MAX_REVISIONS) return List.of("Penalty history exceeds its revision budget");
        if (expected.race() == null || expected.season() == null || expected.phase() == null || expected.matchday() == null
                || expected.home() == null || expected.away() == null || expected.home().equals(expected.away())) {
            problems.add("Penalty history requires a complete race identity with two distinct teams");
        }
        var ids = new HashSet<UUID>();
        var sorted = entries.stream().sorted(Comparator.comparingInt(Entry::revision)).toList();
        for (int i = 0; i < sorted.size(); i++) {
            var row = sorted.get(i);
            if (row.revision() != i + 1) problems.add("Penalty revision sequence must start at one without gaps or duplicates");
            if (row.id() == null || !ids.add(row.id())) problems.add("Penalty revision IDs must be present and unique");
            if (!Objects.equals(expected, row.identity())) problems.add("Penalty history identity differs from the current race");
            if (row.homePenalty() < 0 || row.awayPenalty() < 0) problems.add("Penalty totals must be nonnegative");
            if (row.reason() == null || row.reason().isBlank() || row.reason().length() > 2000) problems.add("Penalty correction reason is missing or too long");
            if (row.actor() == null || row.actor().isBlank() || row.actor().length() > 200) problems.add("Penalty actor is missing or too long");
            if (row.fingerprint() == null || !row.fingerprint().matches("[0-9a-f]{64}")) problems.add("Penalty result fingerprint is invalid");
            if (row.createdAt() == null || row.updatedAt() == null) problems.add("Penalty audit timestamps are missing");
            else if (row.createdAt().getYear() < 1000 || row.createdAt().getYear() > 9999
                    || row.updatedAt().getYear() < 1000 || row.updatedAt().getYear() > 9999) problems.add("Penalty audit timestamps are outside the supported range");
        }
        return problems.stream().distinct().toList();
    }

    public static List<String> problems(Race race) {
        var problems = new ArrayList<>(problems(identity(race), race.getPenaltyRevisions().stream().map(RacePenaltyHistory::entry).toList()));
        if (!race.getPenaltyRevisions().isEmpty()) problems.addAll(contextProblems(race));
        return problems.stream().distinct().toList();
    }

    public static List<String> contextProblems(Race race) {
        var problems = new ArrayList<String>();
        if (race.getMatch() == null && race.getPlayoffMatchup() == null) problems.add("Penalty corrections require a parent pairing");
        var home = race.getHomeTeam(); var away = race.getAwayTeam();
        UUID homeId = id(home), awayId = id(away);
        UUID homeParent = home == null ? null : id(home.getParentOrSelf());
        UUID awayParent = away == null ? null : id(away.getParentOrSelf());
        if (race.getMatch() != null) {
            var match = race.getMatch();
            if (!sameParticipants(id(match.getHomeTeam()), id(match.getAwayTeam()), homeId, awayId, homeParent, awayParent)) {
                problems.add("Penalty race participants differ from the parent match participants");
            }
            if (!Objects.equals(id(race.getMatchday()), id(match.getMatchday()))) problems.add("Penalty race belongs to another matchday than its pairing");
        }
        if (race.getPlayoffMatchup() != null) {
            var matchup = race.getPlayoffMatchup();
            if (!sameParticipants(id(matchup.getTeam1()), id(matchup.getTeam2()), homeId, awayId, homeParent, awayParent)) {
                problems.add("Penalty race participants differ from the parent playoff participants");
            }
            var season = race.getMatchday() == null ? null : race.getMatchday().getSeason();
            if (matchup.getRound() == null || matchup.getRound().getPlayoff() == null
                    || !Objects.equals(id(season), id(matchup.getRound().getPlayoff().getSeason()))) {
                problems.add("Penalty race belongs to another playoff season");
            }
        }
        return problems;
    }

    public static boolean sameParticipants(UUID first, UUID second, UUID home, UUID away, UUID homeParent, UUID awayParent) {
        if (first == null || second == null || first.equals(second)) return false;
        int side = side(first, home, away, homeParent, awayParent);
        return side != 0 && side == -side(second, home, away, homeParent, awayParent);
    }

    private static int side(UUID team, UUID home, UUID away, UUID homeParent, UUID awayParent) {
        if (team == null) return 0;
        if (team.equals(home)) return 1;
        if (team.equals(away)) return -1;
        boolean matchesHome = team.equals(homeParent), matchesAway = team.equals(awayParent);
        return matchesHome == matchesAway ? 0 : matchesHome ? 1 : -1;
    }

    public static RacePenaltyTotals totals(Race race, UUID firstTeam, int firstGross, int secondGross) {
        var rows = race.getPenaltyRevisions();
        if (rows.isEmpty()) return new RacePenaltyTotals(new RacePenaltyTotals.TeamScore(firstGross, 0, 0, firstGross),
                new RacePenaltyTotals.TeamScore(secondGross, 0, 0, secondGross));
        var invalid = problems(race);
        if (!invalid.isEmpty()) throw new BusinessRuleException(String.join("; ", invalid));
        var latest = rows.stream().max(Comparator.comparingInt(RacePenaltyRevision::getRevisionNumber)).orElseThrow();
        int orientation = side(firstTeam, race.getHomeTeam().getId(), race.getAwayTeam().getId(),
                race.getHomeTeam().getParentOrSelf().getId(), race.getAwayTeam().getParentOrSelf().getId());
        if (orientation != 0) return RacePenaltyTotals.calculate(firstGross, secondGross,
                orientation == 1 ? latest.getHomePenalty() : latest.getAwayPenalty(),
                orientation == 1 ? latest.getAwayPenalty() : latest.getHomePenalty());
        throw new BusinessRuleException("Penalty totals require an unambiguous participating team");
    }

    private static UUID id(Object value) {
        if (value == null) return null;
        if (value instanceof Race row) return row.getId();
        if (value instanceof Season row) return row.getId();
        if (value instanceof SeasonPhase row) return row.getId();
        if (value instanceof Matchday row) return row.getId();
        if (value instanceof Match row) return row.getId();
        if (value instanceof PlayoffMatchup row) return row.getId();
        if (value instanceof Team row) return row.getId();
        throw new IllegalArgumentException("Unsupported penalty identity reference");
    }
}
