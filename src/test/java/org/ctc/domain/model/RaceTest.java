package org.ctc.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RaceTest {

    private Team teamWithId(String shortName) {
        var team = new Team("Team " + shortName, shortName);
        team.setId(UUID.randomUUID());
        return team;
    }

    @Test
    void givenNoOverride_whenGetHomeTeam_thenDelegatesToMatch() {
        // given
        var home = teamWithId("H");
        var away = teamWithId("A");
        var matchday = new Matchday(new Season("S", 2026, 1), "MD 1", 1);
        var match = new Match(matchday, home, away);

        var race = new Race();
        race.setMatch(match);

        // when / then
        assertThat(race.getHomeTeam()).isEqualTo(home);
        assertThat(race.getAwayTeam()).isEqualTo(away);
    }

    @Test
    void givenOverrideSet_whenGetHomeTeam_thenReturnsOverride() {
        // given
        var home = teamWithId("H");
        var away = teamWithId("A");
        var matchday = new Matchday(new Season("S", 2026, 1), "MD 1", 1);
        var match = new Match(matchday, home, away);

        var race = new Race();
        race.setMatch(match);
        race.setHomeTeamOverride(away);
        race.setAwayTeamOverride(home);

        // when / then
        assertThat(race.getHomeTeam()).isEqualTo(away);
        assertThat(race.getAwayTeam()).isEqualTo(home);
    }

    @Test
    void givenOnlyHomeOverride_whenGetAwayTeam_thenDelegatesToMatch() {
        // given
        var home = teamWithId("H");
        var away = teamWithId("A");
        var matchday = new Matchday(new Season("S", 2026, 1), "MD 1", 1);
        var match = new Match(matchday, home, away);

        var race = new Race();
        race.setMatch(match);
        race.setHomeTeamOverride(away);

        // when / then
        assertThat(race.getHomeTeam()).isEqualTo(away);
        assertThat(race.getAwayTeam()).isEqualTo(away); // match delegation
    }

    @Test
    void givenNoMatchNoOverride_whenGetHomeTeam_thenReturnsNull() {
        // given
        var race = new Race();

        // when / then
        assertThat(race.getHomeTeam()).isNull();
        assertThat(race.getAwayTeam()).isNull();
    }
}
