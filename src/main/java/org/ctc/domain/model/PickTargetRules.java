package org.ctc.domain.model;

public final class PickTargetRules {
    private PickTargetRules() { }

    /**
     * A leg whose own result or pairing decision is recorded; its setup is no longer confidential. A match score
     * counts only as a whole-match quick score, because aggregated scores already exist after the first leg.
     */
    public static boolean isFinished(Race race) {
        if (!race.getResults().isEmpty()) return true;
        if (race.getPlayoffMatchup() != null) return race.getPlayoffMatchup().isComplete();
        var match = race.getMatch();
        if (match == null) return false;
        boolean scored = match.getHomeScore() != null || match.getAwayScore() != null;
        return match.getWalkoverTeam() != null
                || scored && match.getRaces().stream().allMatch(leg -> leg.getResults().isEmpty());
    }

    public static boolean isUnfinishedTarget(Race race) {
        return isUnfinishedTarget(race, race.getHomeTeam(), race.getAwayTeam());
    }

    public static boolean isUnfinishedTarget(Race race, Team home, Team away) {
        if (race.getMatchday() == null || race.getMatchday().getSeason() == null
                || home == null || away == null || race.isBye()
                || !race.getResults().isEmpty() || race.getMatch() == null && race.getPlayoffMatchup() == null) return false;
        if (home.getId() == null || home.getId().equals(away.getId())) return false;
        if (race.getMatch() != null && (race.getMatch().getWalkoverTeam() != null
                || race.getMatch().getHomeScore() != null || race.getMatch().getAwayScore() != null)) return false;
        return race.getPlayoffMatchup() == null
                || !race.getPlayoffMatchup().isBye() && !race.getPlayoffMatchup().isComplete();
    }
}
