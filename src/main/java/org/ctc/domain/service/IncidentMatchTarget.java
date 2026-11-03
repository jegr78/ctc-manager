package org.ctc.domain.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"}, justification =
        "Transaction-bound pairing adapter intentionally retains managed JPA entities; its race list is defensively copied. "
                + "It is not a form or transport payload. See docs/security/sast-acceptance.md.")
public record IncidentMatchTarget(IncidentMatchRef reference, Match match, PlayoffMatchup playoffMatchup,
                                  Season season, Team home, Team away, List<Race> races) {
    public IncidentMatchTarget { races = List.copyOf(races); }
}
