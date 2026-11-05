package org.ctc.domain.model;

import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;

public enum LobbyRulesProfile {
    LEGACY("Previous CTC rules", "Off"), CTC_2027("CTC 2027", "On");

    private final String label;
    private final String flagRules;
    LobbyRulesProfile(String label, String flagRules) { this.label = label; this.flagRules = flagRules; }
    public String getLabel() { return label; }
    public String getFlagRules() { return flagRules; }
    public int getQualifyingMinutes() { return 10; }
    public String getQualifierSlipstream() { return "Off"; }
    public List<RaceStartType> getStartTypes() {
        return this == LEGACY ? List.of(RaceStartType.GRID_FALSE_START) : List.of(RaceStartType.values());
    }
    public List<RaceSlipstream> getSlipstreams() {
        return this == LEGACY ? List.of(RaceSlipstream.REAL) : List.of(RaceSlipstream.values());
    }
    public void requireChoices(RaceStartType start, RaceSlipstream slipstream) {
        if (start == null || slipstream == null || !getStartTypes().contains(start) || !getSlipstreams().contains(slipstream)) {
            throw new BusinessRuleException("Choose a start type and race slipstream allowed by the season rules.");
        }
    }

    public static LobbyRulesProfile from(Race race) {
        return race.getMatchday() == null || race.getMatchday().getSeason() == null
                ? LEGACY : race.getMatchday().getSeason().getLobbyRulesProfile();
    }
}
