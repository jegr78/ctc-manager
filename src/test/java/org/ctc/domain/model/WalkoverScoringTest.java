package org.ctc.domain.model;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class WalkoverScoringTest {
    private final Team home = team("Test home"), away = team("Test away");
    private static Team team(String label) { var team = new Team(label, label); team.setId(UUID.randomUUID()); return team; }
    private final RaceScoring scoring = new RaceScoring("Test standard", "20,17,14,12,10,8,7,6", "3,2,1", 2);

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void givenWalkoverWithBonuses_whenScoring_thenOnlySixRacePositionsCount(boolean homeForfeits) {
        var score = WalkoverScoring.score(home, away, homeForfeits ? home : away, scoring);
        assertThat(score.home()).isEqualTo(homeForfeits ? 0 : 81);
        assertThat(score.away()).isEqualTo(homeForfeits ? 81 : 0);
        assertThat(WalkoverScoring.points(new RaceScoring("Test alternate", "10,9,8,7,6,5,4", "50,40", 30))).isEqualTo(45);
        assertThat(WalkoverScoring.points(new RaceScoring("Test short", "10,5", "50", 30))).isEqualTo(15);
    }

    @Test void givenInvalidParticipant_whenScoring_thenRejectWithoutInventingAWinner() {
        assertThatThrownBy(() -> WalkoverScoring.score(home, away, team("Test foreign"), scoring)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> WalkoverScoring.score(home, null, home, scoring)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> WalkoverScoring.score(home, home, home, scoring)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> WalkoverScoring.score(home, away, null, scoring)).isInstanceOf(BusinessRuleException.class);
    }

    @Test void givenOverflowingScoring_whenSumming_thenRejectInsteadOfWrappingTheTeamScore() {
        assertThatThrownBy(() -> WalkoverScoring.points(new RaceScoring("Test overflow", "2147483647,1", "", 0))).isInstanceOf(ArithmeticException.class);
    }

    @Test void givenReversedRaceAndRetainedResults_whenScoring_thenOrientTheTeamOnlyResultAndExcludeDriverCredit() {
        var phase = new SeasonPhase(); phase.setRaceScoring(scoring); phase.setSeason(new Season("Test season"));
        var day = new Matchday(phase, "Test round", 1);
        var match = new Match(day, home, away); match.setWalkoverTeam(home);
        var race = new Race(); race.setMatchday(day); race.setMatch(match); race.setHomeTeamOverride(away); race.setAwayTeamOverride(home);
        var result = new RaceResult(race, new Driver("Test driver", "Test driver"), 1, 1, true); result.setPointsTotal(25);
        assertThat(WalkoverScoring.score(race, away.getId())).isEqualTo(new WalkoverScoring.Score(81, 0));
        assertThat(WalkoverScoring.countsForDriverStatistics(result)).isFalse();
        assertThatThrownBy(() -> WalkoverScoring.score(race, UUID.randomUUID())).isInstanceOf(BusinessRuleException.class);
        assertThat(match.getHomeScore()).isNull(); assertThat(match.getOfficialHomeScore()).isZero(); assertThat(match.getOfficialAwayScore()).isEqualTo(81);
        match.setWalkoverTeam(null); assertThat(WalkoverScoring.countsForDriverStatistics(result)).isTrue(); assertThat(result.getPointsTotal()).isEqualTo(25);
        match.setBye(true); assertThat(match.getOfficialHomeScore()).isNull();
    }
    @Test void givenPlayoffWalkoverWithRegularMatchAnchor_whenScoring_thenUsePlayoffParticipantsAndScoring() {
        var regular = new SeasonPhase(); regular.setRaceScoring(scoring);
        var day = new Matchday(regular, "Test anchor", 1);
        var playoffPhase = new SeasonPhase(); playoffPhase.setRaceScoring(new RaceScoring("Test playoff", "10,9,8,7,6,5", "50", 50));
        var playoff = new Playoff(); playoff.setPhase(playoffPhase);
        var matchup = new PlayoffMatchup(new PlayoffRound(playoff, "Test final", 1), 0); matchup.setId(UUID.randomUUID());
        matchup.setTeam1(home); matchup.setTeam2(away); matchup.setWalkoverTeam(away);
        var race = new Race(); race.setMatchday(day); race.setMatch(new Match(day, home, away)); race.setPlayoffMatchup(matchup);
        assertThat(WalkoverScoring.isPlayoffWalkover(race)).isTrue();
        assertThat(WalkoverScoring.forfeitingTeam(race)).isSameAs(away);
        assertThat(WalkoverScoring.pairingId(race)).isEqualTo(matchup.getId());
        assertThat(WalkoverScoring.score(race, home.getId())).isEqualTo(new WalkoverScoring.Score(45, 0));
        assertThat(WalkoverScoring.score(race, away.getId())).isEqualTo(new WalkoverScoring.Score(0, 45));
    }

}
