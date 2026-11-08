package org.ctc.domain.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.discord.repository.DiscordPostRepository;
import org.springframework.stereotype.Component;
import static org.springframework.util.StringUtils.hasText;

/** Read-only reasons; the round writer reloads them under the current owning season lock. */
@Component @RequiredArgsConstructor
public class SwissRoundProtection {
    private final RaceLineupRepository lineups;
    private final MatchdayPickRepository picks;
    private final IncidentMatchContextRepository contexts;
    private final IncidentReportRepository reports;
    private final RacePenaltyRevisionRepository penalties;
    private final RaceDisqualificationRevisionRepository disqualifications;
    private final DiscordPostRepository posts;

    public String reason(Match match, List<Race> legs) {
        if (match.getHomeScore()!=null || match.getAwayScore()!=null) return "Recorded team score";
        if (match.getWalkoverTeam()!=null) return "Walkover result";
        if (hasText(match.getDiscordChannelId())) return "Linked Discord channel and its participants";
        if (contexts.findByMatchId(match.getId()).isPresent()) return "Permanent incident context";
        if (posts.existsByMatchId(match.getId())) return "Published Discord evidence";
        for (var leg:legs) {
            if (leg.getPlayoffMatchup()!=null) return "Playoff-linked race";
            if (!match.getMatchday().getId().equals(leg.getMatchday().getId())) return "Race belongs to another matchday";
            if (!leg.getResults().isEmpty()) return "Recorded driver results";
            if (lineups.existsByRaceId(leg.getId())) return "Recorded race lineup";
            if (picks.findSnapshotByRaceId(leg.getId()).isPresent()) return "Confidential pick history";
            if (reports.existsByRaceId(leg.getId())) return "Permanent incident report history";
            if (penalties.existsByRaceId(leg.getId())) return "Permanent team penalty history";
            if (disqualifications.existsByRaceId(leg.getId())) return "Permanent driver disqualification history";
            if (!leg.getAttachments().isEmpty() || posts.existsByRaceId(leg.getId())) return "Recorded race attachments or published evidence";
        }
        return null;
    }
}
