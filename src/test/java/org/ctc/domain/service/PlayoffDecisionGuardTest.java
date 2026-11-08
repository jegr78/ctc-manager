package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PlayoffDecisionGuardTest {
    @Test void givenTeamOnlyWalkover_whenAdmittingIndividualWrites_thenRejectUntilCleared() {
        var match = new Match(); match.setWalkoverTeam(new Team("Test forfeiter", "Test forfeiter"));
        var race = new Race(); race.setMatch(match);
        assertThatThrownBy(() -> PlayoffDecisionGuard.requireOpen(race)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("team-only result");
        match.setWalkoverTeam(null);
        assertThatCode(() -> PlayoffDecisionGuard.requireOpen(race)).doesNotThrowAnyException();
    }
}
