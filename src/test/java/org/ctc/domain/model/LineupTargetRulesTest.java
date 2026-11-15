package org.ctc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LineupTargetRulesTest {
    private final Team parent = team("P", null);
    private final Team first = team("P1", parent);
    private final Team second = team("P2", parent);
    private final Team other = team("O", null);

    @Test
    void givenParentBinding_whenCheckingService_thenServeOwnAndSubTeamsOnly() {
        // when / then
        assertThat(LineupTargetRules.serves(parent.getId(), first)).isTrue();
        assertThat(LineupTargetRules.serves(parent.getId(), parent)).isTrue();
        assertThat(LineupTargetRules.serves(first.getId(), second)).as("a sub-team binding does not serve its sister").isFalse();
        assertThat(LineupTargetRules.serves(parent.getId(), other)).isFalse();
    }

    @Test
    void givenScheduledLeg_whenCheckingTarget_thenOfferBothParticipants() {
        // given
        var race = race(first, other);
        // when / then
        assertThat(LineupTargetRules.isOpenTarget(race, first)).isTrue();
        assertThat(LineupTargetRules.isOpenTarget(race, other)).as("away teams submit too").isTrue();
        assertThat(LineupTargetRules.isOpenTarget(race, second)).as("a sister team does not play this leg").isFalse();
    }

    @Test
    void givenWalkoverOrResults_whenCheckingTarget_thenClosed() {
        // given
        var walkover = race(first, other);
        walkover.getMatch().setWalkoverTeam(other);
        var played = race(first, other);
        played.getResults().add(new RaceResult());
        // when / then
        assertThat(LineupTargetRules.isOpenTarget(walkover, first)).isFalse();
        assertThat(LineupTargetRules.isOpenTarget(played, first)).isFalse();
    }

    @Test
    void givenFirstLegScored_whenCheckingSecondLeg_thenStillOpen() {
        // given
        var firstLeg = race(first, other);
        var secondLeg = race(first, other);
        secondLeg.setMatch(firstLeg.getMatch());
        firstLeg.getMatch().getRaces().add(firstLeg);
        firstLeg.getMatch().getRaces().add(secondLeg);
        firstLeg.getResults().add(new RaceResult());
        firstLeg.getMatch().setHomeScore(30);
        firstLeg.getMatch().setAwayScore(20);
        // when / then
        assertThat(LineupTargetRules.isOpenTarget(secondLeg, first)).as("an aggregated score does not finish later legs").isTrue();
    }

    @Test
    void givenSisterTeamsMeeting_whenCheckingFieldingSide_thenUseExactTeam() {
        // given
        var sisters = race(first, second);
        var mixed = race(first, other);
        // when / then
        assertThat(LineupTargetRules.fieldingSide(new RaceLineup(sisters, new Driver(), second), sisters, first)).isFalse();
        assertThat(LineupTargetRules.fieldingSide(new RaceLineup(mixed, new Driver(), second), mixed, first))
                .as("an editor row attributed to a sister team belongs to the fielding side").isTrue();
        assertThat(LineupTargetRules.fieldingSide(new RaceLineup(mixed, new Driver(), other), mixed, first)).isFalse();
    }

    private static Team team(String name, Team parent) {
        var team = new Team(name, name, parent);
        team.setId(UUID.randomUUID());
        return team;
    }

    private static Race race(Team home, Team away) {
        var season = new Season();
        var day = new Matchday();
        day.setPhase(new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        var match = new Match();
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(day);
        race.setMatch(match);
        return race;
    }
}
