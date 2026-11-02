package org.ctc.domain.service;

import java.util.Collection;
import java.util.UUID;
import java.util.Comparator;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.RaceSettingsValues;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PickLifecycleGuard {
    private final MatchdayPickRepository picks;
    public void requireNoHistory(Collection<Race> races) {
        for (var race : races.stream().filter(row -> row.getId() != null).sorted(Comparator.comparing(Race::getId)).toList()) {
            if (hasHistory(race)) throw new BusinessRuleException("Cannot delete a race with confidential pick history");
        }
    }

    public void requireIdentity(Race race, UUID matchdayId, Team home, Team away) {
        String reason = identityRejection(race, matchdayId, home, away);
        if (reason != null) throw new BusinessRuleException(reason);
    }

    public String identityRejection(Race race, UUID matchdayId, Team home, Team away) {
        boolean changed = !Objects.equals(race.getMatchday() == null ? null : race.getMatchday().getId(), matchdayId)
                || !Objects.equals(id(race.getHomeTeam()), id(home)) || !Objects.equals(id(race.getAwayTeam()), id(away));
        return changed && hasHistory(race) ? "Cannot reassign a race with confidential pick history" : null;
    }

    public void requireSetupUnchanged(Race race, UUID trackId, UUID carId, UUID entryId, RaceSettingsValues settings) {
        String reason = setupRejection(race, trackId, carId, entryId, settings);
        if (reason != null) throw new BusinessRuleException(reason);
    }

    public String setupRejection(Race race, UUID trackId, UUID carId, UUID entryId, RaceSettingsValues settings) {
        if (!race.isPicksManaged()) return null;
        boolean changed = !Objects.equals(trackId, race.getTrack() == null ? null : race.getTrack().getId())
                || !Objects.equals(carId, race.getCar() == null ? null : race.getCar().getId())
                || !Objects.equals(entryId, race.getCarPoolEntry() == null ? null : race.getCarPoolEntry().getId())
                || !Objects.equals(settings, RaceSettingsValues.from(race.getSettings()));
        return changed ? "Use pick review to change this managed race's setup" : null;
    }

    private boolean hasHistory(Race race) {
        return race.isPicksManaged() && race.getId() != null && picks.findByRaceId(race.getId()).isPresent();
    }

    private static UUID id(Team team) { return team == null ? null : team.getId(); }
}
