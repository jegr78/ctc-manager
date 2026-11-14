package org.ctc.domain.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.picks.*;
import org.ctc.domain.repository.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PickAccessService {
    private final TeamPickChannelBindingRepository bindings;
    private final PickChannelPermissionSource permissions;
    private final DiscordGlobalConfigRepository configs;
    private final MatchRepository matches;
    private final RaceRepository races;
    private final MatchdayPickRepository picks;
    private final PickSessionEpoch epoch;

    public ChannelPickActor authorizeChannel(String guildId, String channelId, String userId) {
        if (!snowflake(guildId) || !snowflake(channelId) || !snowflake(userId)) throw unavailable();
        var config = configs.findFirstByOrderByIdAsc();
        if (config == null || !guildId.equals(config.getGuildId()) || matches.existsByDiscordChannelId(channelId)) throw unavailable();
        var rows = bindings.findByGuildIdAndChannelId(guildId, channelId);
        if (rows == null || rows.isEmpty()) throw unavailable();
        var teamsBySeason = new HashMap<UUID, UUID>();
        var scopes = new HashSet<ChannelPickActor.Binding>();
        for (var binding : rows) {
            if (binding == null || binding.getSeason() == null || binding.getTeam() == null
                    || binding.getSeason().getId() == null || binding.getTeam().getId() == null
                    || !guildId.equals(binding.getGuildId()) || !channelId.equals(binding.getChannelId())) throw unavailable();
            var seasonId = binding.getSeason().getId();
            var teamId = binding.getTeam().getId();
            if (teamsBySeason.putIfAbsent(seasonId, teamId) != null) throw unavailable();
            scopes.add(new ChannelPickActor.Binding(seasonId, teamId));
        }
        boolean allowed;
        try {
            allowed = permissions.hasCurrentPrivateAccess(guildId, channelId, userId);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        if (!allowed) throw unavailable();
        return new ChannelPickActor(guildId, channelId, userId, scopes);
    }

    public AuthorizedPickActor authorizeTarget(ChannelPickActor channel, UUID raceId) {
        if (channel == null || raceId == null) throw unavailable();
        long generation = epoch.getAsLong();
        var current = authorizeChannel(channel.guildId(), channel.channelId(), channel.userId());
        var race = races.findById(raceId).orElseThrow(PickAccessService::unavailable);
        if (race.getMatchday() == null || race.getMatchday().getSeason() == null
                || race.getHomeTeam() == null || race.getAwayTeam() == null) throw unavailable();
        var seasonId = race.getMatchday().getSeason().getId();
        var teamId = race.getHomeTeam().getId();
        if (teamId == null || teamId.equals(race.getAwayTeam().getId())) throw unavailable();
        var binding = current.bindings().stream()
                .filter(row -> seasonId.equals(row.seasonId()) && servesHome(row.teamId(), race)).findFirst()
                .orElseThrow(PickAccessService::unavailable);
        var pick = picks.findSnapshotByRaceId(raceId).orElse(null);
        if (pick != null && !pick.matchesTarget(race)) throw unavailable();
        if (generation != epoch.getAsLong()) throw unavailable();
        return new AuthorizedPickActor(raceId, seasonId, teamId, current.userId(),
                new AuthorizedPickActor.ChannelContext(current.guildId(), current.channelId(), generation, binding.teamId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recheck(AuthorizedPickActor actor) {
        var channel = actor.channel();
        if (channel == null || channel.generation() != epoch.getAsLong()
                || bindings.findCurrentForUpdate(actor.seasonId(), channel.bindingTeamId(), channel.guildId(), channel.channelId()).isEmpty()) {
            throw unavailable();
        }
        var current = authorizeChannel(channel.guildId(), channel.channelId(), actor.userId());
        if (!current.bindings().contains(new ChannelPickActor.Binding(actor.seasonId(), channel.bindingTeamId()))
                || channel.generation() != epoch.getAsLong()) throw unavailable();
    }

    /** A channel bound to an organisation serves its own home legs and those of its sub-teams. */
    public static boolean servesHome(UUID boundTeamId, org.ctc.domain.model.Race race) {
        var home = race.getHomeTeam();
        return boundTeamId != null && home != null
                && (boundTeamId.equals(home.getId()) || boundTeamId.equals(home.getParentOrSelf().getId()));
    }

    private static boolean snowflake(String value) { return value != null && value.matches("[0-9]{1,32}"); }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("Private channel access is unavailable"); }
}
