package org.ctc.domain.service;

import java.util.Collection;
import java.util.UUID;
import java.util.Comparator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PickLifecycleGuard {
    private final MatchdayPickRepository picks;
    public void requireNoHistory(Collection<Race> races) {
        for (var race : races.stream().filter(row -> row.getId() != null).sorted(Comparator.comparing(Race::getId)).toList()) {
            if (protects(race)) throw new BusinessRuleException("Cannot delete a race with confidential pick history");
        }
    }

    public void requireIdentity(Race race, UUID matchdayId, Team home, Team away) {
        String reason = identityRejection(race, matchdayId, home, away);
        if (reason != null) throw new BusinessRuleException(reason);
    }

    public String identityRejection(Race race, UUID matchdayId, Team home, Team away) {
        boolean changed = !Objects.equals(race.getMatchday() == null ? null : race.getMatchday().getId(), matchdayId)
                || !Objects.equals(id(race.getHomeTeam()), id(home)) || !Objects.equals(id(race.getAwayTeam()), id(away));
        return changed && protects(race) ? "Cannot reassign a race with confidential pick history" : null;
    }

    public boolean protects(Race race) {
        return race.getId() != null && picks.findByRaceId(race.getId()).isPresent();
    }

    private static UUID id(Team team) { return team == null ? null : team.getId(); }
}
