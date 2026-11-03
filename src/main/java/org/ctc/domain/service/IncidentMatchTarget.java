package org.ctc.domain.service;

import java.util.List;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;

public record IncidentMatchTarget(IncidentMatchRef reference, Match match, PlayoffMatchup playoffMatchup,
                                  Season season, Team home, Team away, List<Race> races) {
    public IncidentMatchTarget { races = List.copyOf(races); }
}
