package org.ctc.domain.service;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.ctc.domain.model.PickState;
import org.ctc.domain.model.PickTargetRules;
import org.ctc.domain.picks.ChannelPickActor;
import org.ctc.domain.picks.HomeRaceTarget;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.model.MatchdayPick;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PickTargetService {
    private final SeasonRepository seasons;
    private final RaceRepository races;
    private final MatchdayPickRepository picks;
    private final EntityManager entityManager;
    private final PickDeadlinePolicy deadlines;
    private final PickCollectionStatus collection;
    private final PickAccessService access;
    private final PickSessionEpoch epoch;

    @Transactional(readOnly = true)
    public List<HomeRaceTarget> listHomeRaces(ChannelPickActor actor) {
        if (actor == null || !snowflake(actor.guildId()) || !snowflake(actor.channelId())
                || !snowflake(actor.userId()) || actor.bindings().isEmpty()) {
            throw new BusinessRuleException("An authorized channel actor is required");
        }
        actor = access.authorizeChannel(actor.guildId(), actor.channelId(), actor.userId());
        var result = new LinkedHashMap<UUID, HomeRaceTarget>();
        for (var binding : actor.bindings()) {
            if (binding.seasonId() == null || binding.teamId() == null) throw unavailable();
            var season = seasons.findById(binding.seasonId()).orElse(null);
            if (season == null) continue;
            var rows = races.findByMatchdaySeasonId(season.getId());
            for (var race : rows) {
                if (!race.isPicksManaged() || race.getHomeTeam() == null || !binding.teamId().equals(race.getHomeTeam().getId())) continue;
                var pick = picks.findSnapshotByRaceId(race.getId()).orElse(null);
                if (pick != null && !pick.matchesTarget(race)) throw unavailable();
                boolean eligible = isEligible(race);
                if (!eligible && (pick == null || pick.getLatestRevisionNumber() == null)) continue;
                var state = pick == null ? PickState.MISSING : pick.getState();
                var decision = deadlines.assess(new PickDeadlinePolicy.Context(state, season.isPicksCollectionEnabled(), collection.isAvailable(),
                        eligible, pick != null && pick.isManualOpen(), pick != null && pick.isManualClosed(), race.getMatchday().getPickDeadline()));
                result.putIfAbsent(race.getId(), new HomeRaceTarget(race.getId(), label(season, race, rows), state,
                        pick == null ? 0 : pick.getVersion(), decision.editable(), decision.reason()));
            }
        }
        return result.values().stream().sorted(Comparator.comparing(HomeRaceTarget::label).thenComparing(HomeRaceTarget::raceId)).toList();
    }

    public static String label(Season season, Race race, List<Race> rows) {
        var phase = race.getMatchday().getPhase();
        var parts = new ArrayList<String>();
        parts.add(season.getDisplayLabel());
        parts.add(phase.getLabel() == null || phase.getLabel().isBlank() ? phase.getPhaseType().name() : phase.getLabel());
        if (race.getMatchday().getGroup() != null) parts.add(race.getMatchday().getGroup().getName());
        parts.add(race.getMatchday().getLabel());
        parts.add("vs " + (race.getAwayTeam() == null ? "Bye" : race.getAwayTeam().getShortName()));
        var legs = rows.stream().filter(other -> Objects.equals(pairingId(other), pairingId(race)))
                .sorted(Comparator.comparingInt((Race other) -> other.getMatchday().getSortIndex())
                        .thenComparing(Race::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Race::getId)).toList();
        parts.add("Leg " + (legs.indexOf(race) + 1));
        return String.join(" | ", parts);
    }

    private static UUID pairingId(Race race) {
        return race.getPlayoffMatchup() != null ? race.getPlayoffMatchup().getId() : race.getMatch() == null ? null : race.getMatch().getId();
    }

    private static boolean snowflake(String value) { return value != null && value.matches("[0-9]{1,32}"); }
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedTarget lockForTeam(UUID raceId, AuthorizedPickActor actor) {
        if (actor == null || raceId == null || !raceId.equals(actor.raceId()) || actor.seasonId() == null
                || actor.teamId() == null || actor.userId() == null || !actor.userId().matches("[0-9]{1,32}")) {
            throw unavailable();
        }
        var target = locked(actor.seasonId(), raceId);
        if (target.race().getHomeTeam() == null || !actor.teamId().equals(target.race().getHomeTeam().getId())) {
            throw unavailable();
        }
        if (actor.channel() != null) access.recheck(actor);
        return target;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedTarget lockForManagement(UUID raceId, ManagementPickActor actor) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank() || actor.userId().length() > 128) {
            throw new BusinessRuleException("An authenticated management actor is required");
        }
        var race = races.findById(raceId).orElseThrow(PickTargetService::unavailable);
        var target = locked(race.getMatchday().getSeason().getId(), raceId);
        if (actor.generation() != null && actor.generation() != epoch.getAsLong()) {
            throw new BusinessRuleException("This management form was reset by a restore; reload its current version");
        }
        return target;
    }

    private LockedTarget locked(UUID seasonId, UUID raceId) {
        var season = seasons.findByIdForUpdate(seasonId).orElseThrow(PickTargetService::unavailable);
        var race = races.findBySeasonForSelectionUpdate(seasonId).stream()
                .filter(row -> raceId.equals(row.getId())).findFirst().orElseThrow(PickTargetService::unavailable);
        if (!race.isPicksManaged()) throw unavailable();
        entityManager.refresh(race.getMatchday(), LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(race.getMatchday().getPhase(), LockModeType.PESSIMISTIC_WRITE);
        if (!seasonId.equals(race.getMatchday().getSeason().getId())) throw unavailable();
        var pick = picks.findByRaceId(raceId).orElse(null);
        if (pick != null) {
            entityManager.refresh(pick, LockModeType.PESSIMISTIC_WRITE);
            if (!pick.matchesTarget(race)) throw unavailable();
        }
        return new LockedTarget(season, race, pick);
    }

    public boolean isEligible(Race race) {
        return race.isPicksManaged() && PickTargetRules.isScheduledTarget(race);
    }

    private static BusinessRuleException unavailable() {
        return new BusinessRuleException("Pick target is unavailable");
    }
    public record LockedTarget(Season season, Race race, MatchdayPick pick) { }
}
