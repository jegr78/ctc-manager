package org.ctc.domain.model;

import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PickEnrollmentTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenEnabledSeasonAndNewHomeRace_whenPersisting_thenEnrollRegardlessOfScheduleAndRetainMarkerAfterDisable(boolean scheduled) {
        // given
        var race = candidate();
        if (!scheduled) race.setDateTime(null);
        race.getMatchday().getSeason().setPicksCollectionEnabled(true);
        // when
        race.enrolForPicksIfEnabled();
        // then
        assertThat(race.isPicksManaged()).isTrue();
        race.getMatchday().getSeason().setPicksCollectionEnabled(false);
        race.setDateTime(null);
        race.enrolForPicksIfEnabled();
        assertThat(race.isPicksManaged()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "no-home", "no-away", "bye", "same-team", "detached", "results", "score", "walkover"})
    void givenNonTargetRace_whenPersisting_thenDoNotEnroll(String cause) {
        // given
        var race = candidate();
        race.getMatchday().getSeason().setPicksCollectionEnabled(true);
        switch (cause) {
            case "disabled" -> race.getMatchday().getSeason().setPicksCollectionEnabled(false);
            case "no-home" -> race.getMatch().setHomeTeam(null);
            case "no-away" -> race.getMatch().setAwayTeam(null);
            case "bye" -> race.getMatch().setBye(true);
            case "same-team" -> race.getMatch().setAwayTeam(race.getHomeTeam());
            case "detached" -> race.setMatch(null);
            case "results" -> race.getResults().add(new RaceResult());
            case "score" -> race.getMatch().setHomeScore(0);
            case "walkover" -> race.getMatch().setWalkoverTeam(race.getHomeTeam());
            default -> throw new AssertionError(cause);
        }
        // when
        race.enrolForPicksIfEnabled();
        // then
        assertThat(race.isPicksManaged()).isFalse();
    }

    private Race candidate() {
        var season = new Season("Test enrollment");
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        var day = new Matchday(phase, "Test enrollment day", 1);
        var home = new Team();
        home.setId(java.util.UUID.randomUUID());
        var away = new Team();
        away.setId(java.util.UUID.randomUUID());
        var race = new Race();
        race.setMatchday(day);
        race.setMatch(new Match(day, home, away));
        race.setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
        return race;
    }
}
