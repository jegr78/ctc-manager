package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PlayoffDecisionGuardTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void givenTeamOnlyWalkover_whenAdmittingIndividualWrites_thenRejectUntilCleared(boolean playoff) {
        var match = new Match(); match.setWalkoverTeam(new Team("Test forfeiter", "Test forfeiter"));
        var race = new Race(); race.setMatch(match);
        var matchup = new PlayoffMatchup();
        if (playoff) { matchup.setWalkoverTeam(match.getWalkoverTeam()); match.setWalkoverTeam(null); race.setPlayoffMatchup(matchup); }
        assertThatThrownBy(() -> PlayoffDecisionGuard.requireOpen(race)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("team-only result");
        match.setWalkoverTeam(null); matchup.setWalkoverTeam(null);
        assertThatCode(() -> PlayoffDecisionGuard.requireOpen(race)).doesNotThrowAnyException();
    }
}
