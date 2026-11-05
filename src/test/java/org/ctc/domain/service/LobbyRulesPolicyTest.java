package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LobbyRulesPolicyTest {
    @Test
    void givenAChangedSeasonProfile_whenApplyingAnOldDraft_thenRequireReloadBeforeAnyChoiceIsApplied() {
        // given
        var season = new Season("Test profile change"); season.setLobbyRulesProfile(LobbyRulesProfile.CTC_2027);
        // when / then
        assertThatThrownBy(() -> LobbyRulesPolicy.requireCurrent(season, LobbyRulesProfile.LEGACY,
                RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("changed").hasMessageContaining("reload");
    }

    @Test
    void givenMissingProfileOrLegacyForbiddenChoices_whenApplying_thenRejectWithoutDefaulting() {
        // given
        var season = new Season("Test rules validation");
        // when / then
        assertThatThrownBy(() -> LobbyRulesPolicy.requireCurrent(null, LobbyRulesProfile.LEGACY,
                RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LobbyRulesPolicy.requireCurrent(season, null, RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LobbyRulesPolicy.requireCurrent(season, LobbyRulesProfile.LEGACY, RaceStartType.ROLLING, RaceSlipstream.REAL))
                .isInstanceOf(BusinessRuleException.class);
        season.setLobbyRulesProfile(null);
        assertThatThrownBy(() -> LobbyRulesPolicy.requireCurrent(season, null, RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenMatching2027ProfileAndChoices_whenApplying_thenAcceptTheExactCapturedVersion() {
        // given
        var season = new Season("Test matching rules"); season.setLobbyRulesProfile(LobbyRulesProfile.CTC_2027);
        // when / then
        assertThatCode(() -> LobbyRulesPolicy.requireCurrent(season, LobbyRulesProfile.CTC_2027,
                RaceStartType.ROLLING, RaceSlipstream.WEAK)).doesNotThrowAnyException();
    }
}
