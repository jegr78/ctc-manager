package org.ctc.domain.model;

import java.util.Objects;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;

public final class WalkoverScoring {
    private WalkoverScoring() { }

    public static int points(RaceScoring scoring) {
        if (scoring == null) return 0;
        int total = 0;
        int[] positions = scoring.getRacePointsArray();
        for (int index = 0; index < Math.min(6, positions.length); index++) total = Math.addExact(total, positions[index]);
        return total;
    }

    public static boolean isWalkover(Race race) {
        return race != null && (race.getMatch() != null && race.getMatch().getWalkoverTeam() != null
                || race.getPlayoffMatchup() != null && race.getPlayoffMatchup().getWalkoverTeam() != null);
    }

    public static boolean countsForDriverStatistics(RaceResult result) {
        return !isWalkover(result.getRace());
    }

    public static Score score(Team home, Team away, Team forfeiter, RaceScoring scoring) {
        if (home == null || away == null || forfeiter == null || home.getId() == null || away.getId() == null
                || home.getId().equals(away.getId()) || !MatchWalkoverRules.valid(forfeiter.getId(), home.getId(), away.getId(), false)
                || forfeiter.getId() == null) throw new BusinessRuleException("A walkover requires its two distinct teams and their forfeiting participant");
        int points = points(scoring);
        return home.getId().equals(forfeiter.getId()) ? new Score(0, points) : new Score(points, 0);
    }

    public static Score score(Race race, UUID firstTeamId) {
        Team home; Team away; Team forfeiter;
        if (race.getMatch() != null) {
            var match = race.getMatch(); home = match.getHomeTeam(); away = match.getAwayTeam(); forfeiter = match.getWalkoverTeam();
        } else if (race.getPlayoffMatchup() != null) {
            var matchup = race.getPlayoffMatchup(); home = matchup.getTeam1(); away = matchup.getTeam2(); forfeiter = matchup.getWalkoverTeam();
        } else throw new BusinessRuleException("A walkover requires its owning pairing");
        var score = score(home, away, forfeiter, race.getMatchday().getPhase().getRaceScoring());
        if (Objects.equals(firstTeamId, home.getId())) return score;
        if (Objects.equals(firstTeamId, away.getId())) return new Score(score.away(), score.home());
        throw new BusinessRuleException("Walkover score orientation requires one of its participants");
    }

    public record Score(int home, int away) { }
}
