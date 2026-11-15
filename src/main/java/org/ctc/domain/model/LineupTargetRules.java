package org.ctc.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Which (leg, team) pairs accept a lineup and which existing lineup rows belong to the fielding side. */
public final class LineupTargetRules {
    private LineupTargetRules() { }

    /** A scheduled, unfinished leg with two distinct participants, of which {@code team} is one. */
    public static boolean isOpenTarget(Race race, Team team) {
        if (race == null || team == null || team.getId() == null || race.getMatchday() == null || race.getMatchday().getSeason() == null) return false;
        var home = race.getHomeTeam();
        var away = race.getAwayTeam();
        if (home == null || away == null || home.getId() == null || home.getId().equals(away.getId()) || race.isBye()) return false;
        if (race.getMatch() == null && race.getPlayoffMatchup() == null) return false;
        if (race.getPlayoffMatchup() != null && race.getPlayoffMatchup().isBye()) return false;
        if (PickTargetRules.isFinished(race)) return false;
        return team.getId().equals(home.getId()) || team.getId().equals(away.getId());
    }

    public static boolean sameOrganisation(Team first, Team second) {
        return first != null && second != null && Objects.equals(first.getParentOrSelf().getId(), second.getParentOrSelf().getId());
    }

    /** The bound team serves its own legs and those of its sub-teams. */
    public static boolean serves(UUID boundTeamId, Team team) {
        return boundTeamId != null && team != null
                && (boundTeamId.equals(team.getId()) || boundTeamId.equals(team.getParentOrSelf().getId()));
    }

    /**
     * Whether a stored lineup row belongs to the side the fielding team plays on. When both participants share one
     * organisation only rows of the exact team count; otherwise every row of the organisation does, because the editor
     * may attribute drivers to a sister team.
     */
    public static boolean fieldingSide(RaceLineup row, Race race, Team fielding) {
        if (row.getTeam().getId().equals(fielding.getId())) return true;
        var opponent = fielding.getId().equals(race.getHomeTeam().getId()) ? race.getAwayTeam() : race.getHomeTeam();
        return !sameOrganisation(opponent, fielding) && sameOrganisation(row.getTeam(), fielding);
    }
}
