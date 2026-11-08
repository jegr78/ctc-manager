package org.ctc.domain.service;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.CarPoolMode;
import org.ctc.domain.model.PickDecisionKind;
import org.ctc.domain.model.PickState;
import org.ctc.domain.picks.ChannelPickActor;
import org.ctc.domain.picks.PickTeamView;
import org.ctc.domain.picks.PickTeamView.Choice;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PickTeamReadService {
    private final PickAccessService access;
    private final PickTargetService targets;
    private final RaceRepository races;
    private final MatchdayPickRepository picks;
    private final MatchdayPickRevisionRepository revisions;
    private final MatchdayPickDecisionRepository decisions;
    private final RaceCarSelectionService selections;
    private final PickReservationService reservations;
    private final CarRepository cars;
    private final TrackRepository tracks;
    private final PickDeadlinePolicy deadlines;

    @Transactional(readOnly = true)
    public PickTeamView load(ChannelPickActor channel, UUID raceId) {
        var actor = access.authorizeTarget(channel, raceId);
        var target = targets.homeRace(channel, raceId);
        var race = races.findById(raceId).orElseThrow(() -> new BusinessRuleException("Pick target is unavailable"));
        var season = race.getMatchday().getSeason();
        var pick = picks.findSnapshotByRaceId(raceId).orElse(null);
        var revision = pick == null || pick.getLatestRevisionNumber() == null ? null
                : revisions.findSnapshotByPickIdAndRevisionNumber(pick.getId(), pick.getLatestRevisionNumber())
                        .orElseThrow(() -> new BusinessRuleException("Pick history is unavailable"));
        var available = selections.availability(actor.seasonId(), actor.teamId(), raceId);
        List<Choice> carChoices = available.poolMode() == CarPoolMode.FIXED_CARS
                ? season.getCars().stream().filter(car -> !available.usedCarIds().contains(car.getId()))
                        .map(car -> new Choice(car.getId(), null, car.getDisplayName())).toList()
                : available.entries().stream().filter(entry -> entry.reason() == null)
                        .flatMap(entry -> entry.cars().stream().map(car -> new Choice(car.id(), entry.id(), car.label(),
                                car.id().equals(entry.oneMakeCarId()), entry.label()))).toList();
        Set<UUID> usedTracks = races.findByMatchdaySeasonId(season.getId()).stream()
                .filter(other -> !raceId.equals(other.getId()) && !other.isBye()
                        && (other.getPlayoffMatchup() == null || !other.getPlayoffMatchup().isBye())
                        && other.getHomeTeam() != null && actor.teamId().equals(other.getHomeTeam().getId()) && other.getTrack() != null)
                .map(other -> other.getTrack().getId()).collect(Collectors.toSet());
        reservations.pending(season.getId(), false).stream()
                .filter(other -> !raceId.equals(other.raceId()) && actor.teamId().equals(other.teamId()))
                .forEach(other -> usedTracks.add(other.trackId()));
        var trackChoices = season.getTracks().stream().filter(track -> !usedTracks.contains(track.getId()))
                .map(track -> new Choice(track.getId(), null, track.getName(), false, null, org.ctc.domain.model.TrackEnvironmentValues.from(track).timeOfDayOptions())).toList();
        String reason = pick == null || pick.getState() != PickState.RETURNED ? null
                : decisions.findFirstByPickIdAndKindOrderByAggregateVersionDesc(pick.getId(), PickDecisionKind.RETURN)
                        .map(org.ctc.domain.model.MatchdayPickDecision::getReason).orElse(null);
        var payload = revision == null ? null : revision.getPayload();
        var deadline = org.ctc.domain.model.MatchdayPickWindowRules.deadline(race);
        var offsets = deadline == null ? List.<java.time.ZoneOffset>of() : ZoneId.of("Europe/Berlin").getRules().getValidOffsets(deadline);
        String deadlineLabel = deadline == null ? "Not configured" : deadline + " Europe/Berlin "
                + (offsets.size() == 1 ? offsets.getFirst() : "requires management review");
        java.time.Instant deadlineAt = null;
        if (deadline != null && offsets.size() == 1) deadlineAt = deadlines.resolveDeadline(deadline);
        return new PickTeamView(target, payload, revision == null ? null : revision.receipt(),
                payload == null ? null : cars.findById(payload.carId()).orElseThrow().getDisplayName(),
                payload == null ? null : tracks.findById(payload.trackId()).orElseThrow().getName(), deadlineLabel,
                race.getMatchday().isPicksOpen(), pick != null && pick.isManualClosed(), reason,
                sorted(carChoices), sorted(trackChoices), season.getLobbyRulesProfile(), deadlineAt);
    }

    private static List<Choice> sorted(List<Choice> choices) {
        return choices.stream().sorted(Comparator.comparing(Choice::label).thenComparing(Choice::id)).toList();
    }
}
