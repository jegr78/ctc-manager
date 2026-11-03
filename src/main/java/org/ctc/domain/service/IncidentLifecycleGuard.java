package org.ctc.domain.service;

import java.util.Collection;
import java.util.UUID;
import java.util.Objects;
import org.ctc.domain.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.IncidentMatchContextRepository;
import org.ctc.domain.repository.IncidentReportRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IncidentLifecycleGuard {
    private final IncidentMatchContextRepository contexts;
    private final IncidentReportRepository reports;

    public boolean protects(Race race) {
        if (race.getId() != null && reports.existsByRaceId(race.getId())) return true;
        if (race.getPlayoffMatchup() != null) return contexts.findByPlayoffMatchupId(race.getPlayoffMatchup().getId()).isPresent();
        return race.getMatch() != null && race.getMatch().getId() != null && contexts.findByMatchId(race.getMatch().getId()).isPresent();
    }
    public String identityRejection(Race race, UUID matchdayId, Team home, Team away) {
        boolean changed = !Objects.equals(race.getMatchday() == null ? null : race.getMatchday().getId(), matchdayId)
                || !Objects.equals(id(race.getHomeTeam()), id(home)) || !Objects.equals(id(race.getAwayTeam()), id(away));
        return changed && protects(race) ? "Cannot reassign a race with an incident context or report history" : null;
    }
    public void requireIdentity(Race race, UUID matchdayId, Team home, Team away) {
        String reason = identityRejection(race, matchdayId, home, away);
        if (reason != null) throw new BusinessRuleException(reason);
    }
    public void requireNoHistory(Collection<Race> races) {
        if (races.stream().anyMatch(race -> race.getId() != null && reports.existsByRaceId(race.getId()))) {
            throw new BusinessRuleException("Cannot delete a race with permanent incident report history");
        }
    }
    public void requireMatchDeletion(Match match) {
        if (match.getId() != null && contexts.findByMatchId(match.getId()).isPresent()) {
            throw new BusinessRuleException("Cannot delete a pairing with a permanent incident context");
        }
        requireNoHistory(match.getRaces());
    }
    public void requirePlayoffIdentity(PlayoffMatchup matchup, Team home, Team away) {
        if (matchup.getId() == null) return;
        var context = contexts.findByPlayoffMatchupId(matchup.getId()).orElse(null);
        if (context != null && (!Objects.equals(id(context.getHomeTeam()), id(home))
                || !Objects.equals(id(context.getAwayTeam()), id(away)))) {
            throw new BusinessRuleException("Cannot reassign a playoff pairing with a permanent incident context");
        }
    }

    private static UUID id(Team team) { return team == null ? null : team.getId(); }
}
