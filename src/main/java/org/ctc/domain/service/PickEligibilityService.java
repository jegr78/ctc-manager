package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.Race;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickEligibilityService {
    private final RaceCarSelectionService cars;
    private final RaceRepository races;
    private final TrackRepository tracks;
    private final PickReservationService reservations;
    public void validate(Race race, PickPayload payload) {
        var season = race.getMatchday().getSeason();
        LobbyRulesPolicy.requireCurrent(season, payload.lobbyRulesProfile(), payload.startType(), payload.slipstream());
        var result = cars.validate(season.getId(), race.getHomeTeam().getId(), race.getId(), payload.carPoolEntryId(), payload.carId());
        if (!result.valid()) throw new BusinessRuleException(result.message());
        if (tracks.findById(payload.trackId()).isEmpty()
                || season.getTracks().stream().noneMatch(track -> payload.trackId().equals(track.getId()))) {
            throw new BusinessRuleException("Pick track must belong to this season");
        }
        boolean used = races.findBySeasonForSelectionUpdate(season.getId()).stream()
                .filter(other -> !other.isBye() && (other.getPlayoffMatchup() == null || !other.getPlayoffMatchup().isBye()))
                .filter(other -> !race.getId().equals(other.getId()) && other.getHomeTeam() != null
                        && race.getHomeTeam().getId().equals(other.getHomeTeam().getId()))
                .anyMatch(other -> other.getTrack() != null && payload.trackId().equals(other.getTrack().getId()));
        if (used) throw new BusinessRuleException("This home team has already used this track");
        boolean reserved = reservations.pending(season.getId(), true).stream()
                .filter(other -> !race.getId().equals(other.raceId()) && race.getHomeTeam().getId().equals(other.teamId()))
                .anyMatch(other -> payload.trackId().equals(other.trackId()));
        if (reserved) throw new BusinessRuleException("This track is reserved by another pending pick for this home team");
    }
}
