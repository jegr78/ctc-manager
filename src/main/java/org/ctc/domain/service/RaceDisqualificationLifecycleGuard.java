package org.ctc.domain.service;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceDisqualificationRevisionRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RaceDisqualificationLifecycleGuard {
    private final RaceDisqualificationRevisionRepository revisions;
    private final RaceResultRepository results;

    public boolean protects(Race race) {
        if (race.getId() == null) return false;
        try { return !revisions.findRaceHistoryForUpdate(race.getId()).isEmpty()
                || race.getResults().stream().anyMatch(org.ctc.domain.model.RaceResult::isDisqualified); }
        catch (RuntimeException failure) { throw RaceSelectionConcurrency.translate(failure); }
    }

    public void requireRetainedDrivers(Race race, java.util.Set<UUID> driverIds) {
        if (race.getId() == null) return;
        try {
            if (race.getResults().stream().anyMatch(r -> r.isDisqualified() && !driverIds.contains(r.getDriver().getId())))
                throw new BusinessRuleException("Cannot remove a driver result with DSQ state or permanent DSQ history");
            if (revisions.findRaceHistoryForUpdate(race.getId()).stream().anyMatch(r -> !driverIds.contains(r.getDriver().getId())))
                throw new BusinessRuleException("Cannot remove a driver result with permanent DSQ history");
        } catch (RuntimeException failure) { throw RaceSelectionConcurrency.translate(failure); }
    }

    public String identityRejection(Race race, UUID matchday, Team home, Team away) {
        boolean changed = !Objects.equals(race.getMatchday() == null ? null : race.getMatchday().getId(), matchday)
                || !Objects.equals(id(race.getHomeTeam()), id(home)) || !Objects.equals(id(race.getAwayTeam()), id(away));
        return changed && protects(race) ? "Cannot reassign a race with permanent driver DSQ history" : null;
    }

    public void requireIdentity(Race race, UUID matchday, Team home, Team away) {
        String reason = identityRejection(race, matchday, home, away);
        if (reason != null) throw new BusinessRuleException(reason);
    }

    public void requireNoHistory(Collection<Race> races) {
        if (races.stream().anyMatch(this::protects)) {
            throw new BusinessRuleException("Preserve permanent driver DSQ history before deleting or forfeiting a played race");
        }
    }

    public void requirePlayoffIdentity(PlayoffMatchup matchup, Team home, Team away) {
        if (!Objects.equals(id(matchup.getTeam1()), id(home)) || !Objects.equals(id(matchup.getTeam2()), id(away))) {
            try {
                if (matchup.getId() != null && (!revisions.findPlayoffHistoryForUpdate(matchup.getId()).isEmpty()
                        || !results.findDisqualifiedPlayoffResultsForUpdate(matchup.getId()).isEmpty())) {
                    throw new BusinessRuleException("Cannot reassign a playoff pairing with DSQ state or permanent driver DSQ history");
                }
            } catch (RuntimeException failure) { throw RaceSelectionConcurrency.translate(failure); }
        }
    }

    private static UUID id(Team team) { return team == null ? null : team.getId(); }
}
