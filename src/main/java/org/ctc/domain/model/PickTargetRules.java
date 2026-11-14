package org.ctc.domain.model;

public final class PickTargetRules {
    private PickTargetRules() { }

    /** A race whose result or pairing decision is recorded; its setup is no longer confidential. */
    public static boolean isFinished(Race race) {
        return !race.getResults().isEmpty()
                || race.getMatch() != null && (race.getMatch().getWalkoverTeam() != null
                        || race.getMatch().getHomeScore() != null || race.getMatch().getAwayScore() != null)
                || race.getPlayoffMatchup() != null && race.getPlayoffMatchup().isComplete();
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
