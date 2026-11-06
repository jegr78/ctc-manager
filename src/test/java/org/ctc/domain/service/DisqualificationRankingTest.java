package org.ctc.domain.service;

import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DisqualificationRankingTest {
    @Test
    void givenDisqualifiedWinnerWithBonuses_whenRankingDriver_thenKeepAppearanceAndBonusesButNoBestPosition() {
        // given
        var result = new RaceResult(new Race(), new Driver(), 1, 1, true);
        result.setDisqualified(true); result.setPointsQuali(3); result.setPointsFl(2); result.setPointsTotal(5);
        var ranking = new DriverRankingService.DriverRanking(result.getDriver(), new Team());
        // when
        ranking.addResult(result);
        // then
        assertThat(ranking.getBestPosition()).isZero(); assertThat(ranking.getTotalPoints()).isEqualTo(5);
        assertThat(ranking.getRacesCount()).isEqualTo(1); assertThat(ranking.getTotalQualiPoints()).isEqualTo(3);
        assertThat(ranking.getTotalFlPoints()).isEqualTo(2);
    }

    @Test
    void givenDsqAhead_whenRankingFollowingDriver_thenUseSharedEffectivePositionAndKeepOriginalEvidence() {
        // given
        var race = new Race(); var removed = new RaceResult(race, new Driver(), 1, 1, false); removed.setDisqualified(true);
        var result = new RaceResult(race, new Driver(), 2, 2, false); race.getResults().addAll(java.util.List.of(removed, result));
        var ranking = new DriverRankingService.DriverRanking(result.getDriver(), new Team());
        // when
        ranking.addResult(result);
        // then
        assertThat(ranking.getBestPosition()).isEqualTo(1); assertThat(result.getPosition()).isEqualTo(2);
    }
}
