package org.ctc.domain.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchOutcomeTest {
    private final Team home = team("HOM");
    private final Team away = team("AWY");

    @Test
    void givenHigherHomeScore_whenOutcome_thenHomeWins() {
        // given
        var match = match(50, 40);
        // when
        var outcome = match.outcome();
        // then
        assertThat(outcome.winner()).isSameAs(home);
        assertThat(outcome.loser()).isSameAs(away);
        assertThat(outcome.tieBreak()).isFalse();
    }

    @Test
    void givenHigherAwayScore_whenOutcome_thenAwayWins() {
        // when
        var outcome = match(40, 50).outcome();
        // then
        assertThat(outcome.isWinner(away)).isTrue();
        assertThat(outcome.isWinner(home)).isFalse();
    }

    @Test
    void givenWalkover_whenOutcome_thenOpponentOfForfeiterWins() {
        // given
        var match = match(null, null);
        match.setWalkoverTeam(home);
        // when
        var outcome = match.outcome();
        // then
        assertThat(outcome.winner()).isSameAs(away);
        assertThat(outcome.tieBreak()).isFalse();
    }

    @Test
    void givenEqualScoresWithoutTieBreakWinner_whenOutcome_thenDraw() {
        // when
        var outcome = match(45, 45).outcome();
        // then
        assertThat(outcome.isDraw()).isTrue();
        assertThat(outcome.winner()).isNull();
    }

    @Test
    void givenEqualScoresWithTieBreakWinner_whenOutcome_thenTieBreakWin() {
        // given
        var match = match(45, 45);
        match.setTieBreakWinner(away);
        // when
        var outcome = match.outcome();
        // then
        assertThat(outcome.winner()).isSameAs(away);
        assertThat(outcome.loser()).isSameAs(home);
        assertThat(outcome.tieBreak()).isTrue();
        assertThat(outcome.isDraw()).isFalse();
    }

    @Test
    void givenUnplayedMatch_whenOutcome_thenNull() {
        // when / then
        assertThat(match(null, null).outcome()).isNull();
    }

    private Match match(Integer homeScore, Integer awayScore) {
        var match = new Match(null, home, away);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return match;
    }

    private static Team team(String shortName) {
        var team = new Team(shortName, shortName);
        team.setId(UUID.randomUUID());
        return team;
    }
}
