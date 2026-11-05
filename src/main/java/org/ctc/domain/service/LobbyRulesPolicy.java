package org.ctc.domain.service;

import org.ctc.domain.model.LobbyRulesProfile;
import org.ctc.domain.model.RaceSlipstream;
import org.ctc.domain.model.RaceStartType;
import org.ctc.domain.model.Season;
import org.ctc.domain.exception.BusinessRuleException;

public final class LobbyRulesPolicy {
    private LobbyRulesPolicy() { }

    public static void requireCurrent(Season season, LobbyRulesProfile captured,
                                      RaceStartType start, RaceSlipstream slipstream) {
        if (season == null || captured == null || captured != season.getLobbyRulesProfile()) {
            throw new BusinessRuleException("The season rules changed or were not captured; reload the form or start a new pick draft.");
        }
        captured.requireChoices(start, slipstream);
    }
}
