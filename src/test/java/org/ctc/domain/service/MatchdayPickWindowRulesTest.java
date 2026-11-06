package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MatchdayPickWindowRulesTest {
    final Matchday day = PhaseTestFixtures.matchdayInRegularPhase(new Season("Test picks", 2027, 1), "Test round", 1);
    final LocalDateTime ordinary = LocalDateTime.of(2027, 1, 10, 20, 0);
    final LocalDateTime exception = ordinary.plusDays(2);

    @Test
    void givenOnePairingException_whenResolvingBothHomeLegs_thenKeepAnotherPairingOnTheDefault() {
        // given
        day.setPickDeadline(ordinary); var match = match(); var other = match(); match.setPickDeadlineOverride(exception);
        var first = race(match); var second = race(match); var unrelated = race(other);
        // when / then
        assertThat(MatchdayPickWindowRules.deadline(first)).isEqualTo(exception);
        assertThat(MatchdayPickWindowRules.deadline(second)).isEqualTo(exception);
        assertThat(MatchdayPickWindowRules.deadline(unrelated)).isEqualTo(ordinary);
        match.setPickDeadlineOverride(null);
        assertThat(MatchdayPickWindowRules.deadline(first)).isEqualTo(ordinary);
    }

    @Test
    void givenCanonicalPlayoffException_whenResolvingItsBridgeRace_thenUseThePairingDeadline() {
        // given
        day.setPickDeadline(ordinary); var match = match(); var race = race(match);
        var playoff = new PlayoffMatchup(); playoff.setId(UUID.randomUUID()); playoff.setPickDeadlineOverride(exception); race.setPlayoffMatchup(playoff);
        // when / then
        assertThat(MatchdayPickWindowRules.deadline(race)).isEqualTo(exception);
    }

    @Test
    void givenEmptyPlannedRound_whenCheckingCompletion_thenKeepItUnfinished() {
        assertThat(MatchdayPickWindowRules.complete(List.of(), List.of(), 2)).isFalse();
    }

    @Test
    void givenOnlyFirstOfTwoLegsScored_whenCheckingCompletion_thenKeepTheRoundOpen() {
        // given
        var match = match(); score(match); var first = race(match); var second = race(match); result(first);
        // when / then
        assertThat(MatchdayPickWindowRules.complete(List.of(match), List.of(first, second), 2)).isFalse();
    }

    @Test
    void givenEveryExpectedLegScored_whenCheckingCompletion_thenCompleteTheRound() {
        // given
        var match = match(); score(match); var first = race(match); var second = race(match); result(first); result(second);
        // when / then
        assertThat(MatchdayPickWindowRules.complete(List.of(match), List.of(first, second), 2)).isTrue();
    }

    @Test
    void givenOnlyOneOfTwoExpectedLegsExists_whenCheckingCompletion_thenDoNotTreatPartialAggregationAsComplete() {
        var match = match(); score(match); var first = race(match); result(first);
        assertThat(MatchdayPickWindowRules.complete(List.of(match), List.of(first), 2)).isFalse();
    }

    @Test
    void givenAnUnplayedOtherPairing_whenCheckingCompletion_thenWaitForEveryMatch() {
        var played = match(); score(played); var pending = match();
        assertThat(MatchdayPickWindowRules.complete(List.of(played, pending), List.of(), 2)).isFalse();
    }

    @Test
    void givenWholeMatchQuickScoreWithoutDriverRows_whenCheckingCompletion_thenRecognizeTheCompletePairing() {
        var match = match(); score(match);
        assertThat(MatchdayPickWindowRules.complete(List.of(match), List.of(race(match)), 2)).isTrue();
    }

    @Test
    void givenByeAndWalkover_whenCheckingCompletion_thenRequireNoDriverRows() {
        var bye = match(); bye.setBye(true); var walkover = match(); walkover.setWalkoverTeam(walkover.getHomeTeam());
        assertThat(MatchdayPickWindowRules.complete(List.of(bye, walkover), List.of(), 2)).isTrue();
    }

    @Test
    void givenAnUndecidedThenDecidedPlayoffBridge_whenCheckingCompletion_thenUseItsExplicitDecision() {
        var match = match(); var race = race(match); var playoff = new PlayoffMatchup(); playoff.setId(UUID.randomUUID());
        race.setPlayoffMatchup(playoff);
        assertThat(MatchdayPickWindowRules.complete(List.of(match), List.of(race), 2)).isFalse();
        playoff.setWinner(match.getHomeTeam());
        assertThat(MatchdayPickWindowRules.complete(List.of(match), List.of(race), 2)).isTrue();
    }

    @Test
    void givenConflictingBridgeDeadlines_whenResolving_thenRejectInsteadOfChoosingOne() {
        var match = match(); match.setPickDeadlineOverride(ordinary); var race = race(match);
        var playoff = new PlayoffMatchup(); playoff.setPickDeadlineOverride(exception); race.setPlayoffMatchup(playoff);
        assertThatThrownBy(() -> MatchdayPickWindowRules.deadline(race)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        playoff.setPickDeadlineOverride(null);
        assertThatThrownBy(() -> MatchdayPickWindowRules.deadline(race)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
    }

    @Test
    void givenAnUnownedRaceOrInvalidLegCount_whenCheckingCompletion_thenRemainUnfinished() {
        var bye = match(); bye.setBye(true);
        assertThat(MatchdayPickWindowRules.complete(List.of(bye), List.of(new Race()), 2)).isFalse();
        assertThat(MatchdayPickWindowRules.complete(List.of(bye), List.of(), 0)).isFalse();
    }

    @Test
    void givenMixedOrForeignPairingOwnership_whenCheckingCompletion_thenDoNotCloseTheRound() {
        var match = match(); var playoff = new PlayoffMatchup(); playoff.setId(UUID.randomUUID()); playoff.setWinner(match.getHomeTeam());
        var bridge = race(match); bridge.setPlayoffMatchup(playoff); var ordinary = race(match);
        assertThat(MatchdayPickWindowRules.complete(List.of(match), List.of(bridge, ordinary), 2)).isFalse();
        assertThat(MatchdayPickWindowRules.complete(List.of(), List.of(bridge), 2)).isFalse();
    }

    Match match() {
        var value = new Match(day, new Team("Test home", "T-HOME"), new Team("Test away", "T-AWAY"));
        value.setId(UUID.randomUUID()); return value;
    }
    Race race(Match match) { var value = new Race(); value.setId(UUID.randomUUID()); value.setMatch(match); value.setMatchday(day); return value; }
    void score(Match match) { match.setHomeScore(0); match.setAwayScore(0); }
    void result(Race race) { race.getResults().add(new RaceResult()); }
}
