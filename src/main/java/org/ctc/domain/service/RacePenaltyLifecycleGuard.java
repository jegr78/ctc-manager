package org.ctc.domain.service;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RacePenaltyRevisionRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RacePenaltyLifecycleGuard {
    private final RacePenaltyRevisionRepository revisions;

    public boolean protects(Race race) {
        if (race.getId() == null) return false;
        try { return !revisions.findHistoryForUpdate(race.getId()).isEmpty(); }
        catch (RuntimeException failure) { throw RaceSelectionConcurrency.translate(failure); }
    }

    public String identityRejection(Race race, UUID matchday, Team home, Team away) {
        boolean changed = !Objects.equals(race.getMatchday() == null ? null : race.getMatchday().getId(), matchday)
                || !Objects.equals(id(race.getHomeTeam()), id(home)) || !Objects.equals(id(race.getAwayTeam()), id(away));
        return changed && protects(race) ? "Cannot reassign a race with permanent team penalty history" : null;
    }

    public void requireIdentity(Race race, UUID matchday, Team home, Team away) {
        String reason = identityRejection(race, matchday, home, away);
        if (reason != null) throw new BusinessRuleException(reason);
    }

    public void requireNoHistory(Collection<Race> races) {
        if (races.stream().anyMatch(this::protects)) {
            throw new BusinessRuleException("Preserve permanent team penalty history before deleting or forfeiting a played race");
        }
    }

    public void requirePlayoffIdentity(PlayoffMatchup matchup, Team home, Team away) {
        if (!Objects.equals(id(matchup.getTeam1()), id(home)) || !Objects.equals(id(matchup.getTeam2()), id(away))) {
            try {
                if (matchup.getId() != null && !revisions.findPlayoffHistoryForUpdate(matchup.getId()).isEmpty()) {
                    throw new BusinessRuleException("Cannot reassign a playoff pairing with permanent team penalty history");
                }
            } catch (RuntimeException failure) { throw RaceSelectionConcurrency.translate(failure); }
        }
    }

    private static UUID id(Team team) { return team == null ? null : team.getId(); }
}
