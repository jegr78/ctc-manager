package org.ctc.domain.model;

public final class PickTargetRules {
    private PickTargetRules() { }

    public static boolean isScheduledTarget(Race race) {
        return isScheduledTarget(race, race.getHomeTeam(), race.getAwayTeam());
    }

    public static boolean isScheduledTarget(Race race, Team home, Team away) {
        return race.getDateTime() != null && isUnfinishedTarget(race, home, away);
    }

    public static boolean isUnfinishedTarget(Race race) {
        return isUnfinishedTarget(race, race.getHomeTeam(), race.getAwayTeam());
    }

    private static boolean isUnfinishedTarget(Race race, Team home, Team away) {
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
