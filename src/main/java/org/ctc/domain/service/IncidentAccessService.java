package org.ctc.domain.service;

import java.util.UUID;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IncidentAccessService {
    private final IncidentContextService contexts;
    private final IncidentMatchContextRepository bindings;
    private final IncidentReportWindowRepository windows;
    private final MatchRepository matches;
    private final DiscordGlobalConfigRepository configs;
    private final IncidentChannelAccessSource source;

    public AuthorizedIncidentActor authorizeChannel(String guild, String channel, String user) {
        try {
            if (!IncidentContextService.snowflake(guild) || !IncidentContextService.snowflake(channel) || !IncidentContextService.snowflake(user)) throw unavailable();
            var config = configs.findFirstByOrderByIdAsc();
            if (config == null || !guild.equals(config.getGuildId())) throw unavailable();
            var context = bindings.findByGuildIdAndChannelId(guild, channel).orElseThrow(IncidentAccessService::unavailable);
            var reference = IncidentContextService.reference(context); var target = contexts.resolve(reference);
            if (!guild.equals(context.getGuildId()) || !channel.equals(context.getChannelId()) || !reference.equals(target.reference())
                    || !context.getSeason().getId().equals(target.season().getId()) || !context.getHomeTeam().getId().equals(target.home().getId())
                    || !context.getAwayTeam().getId().equals(target.away().getId())) throw unavailable();
            for (var legacy : matches.findAllByDiscordChannelIdOrderByIdAsc(channel)) {
                if (!contexts.resolve(IncidentMatchRef.match(legacy.getId())).reference().equals(reference)) throw unavailable();
            }
            var roles = source.currentRoles(guild, channel, user);
            if (roles == null) throw unavailable();
            String home = target.home().getEffectiveDiscordRoleId(), away = target.away().getEffectiveDiscordRoleId();
            boolean knownHome = IncidentContextService.snowflake(home), knownAway = IncidentContextService.snowflake(away);
            IncidentParty party;
            if (knownHome && knownAway && home.equals(away)) {
                Team organization = target.home().getParentOrSelf();
                if (!organization.getId().equals(target.away().getParentOrSelf().getId())) throw new IncidentAccessDeniedException(IncidentAccessDeniedException.Reason.SHARED_ROLE);
                if (!roles.contains(home)) throw new IncidentAccessDeniedException(IncidentAccessDeniedException.Reason.NO_ROLE);
                party = new IncidentParty(IncidentPartyType.ORGANIZATION, organization.getId(), organization.getName(), home);
            } else {
                boolean ours = knownHome && roles.contains(home), theirs = knownAway && roles.contains(away);
                if (ours && theirs) throw new IncidentAccessDeniedException(IncidentAccessDeniedException.Reason.OPPOSING_ROLES);
                if (!ours && !theirs) throw new IncidentAccessDeniedException(IncidentAccessDeniedException.Reason.NO_ROLE);
                var team = ours ? target.home() : target.away();
                party = new IncidentParty(IncidentPartyType.TEAM, team.getId(), team.getName(), ours ? home : away);
            }
            return new AuthorizedIncidentActor(context.getId(), guild, channel, user, party);
        } catch (IncidentAccessDeniedException denied) { throw denied; }
        catch (RuntimeException denied) { throw unavailable(); }
    }

    public AuthorizedIncidentActor authorizeWindow(String guild, String channel, String user, UUID window) {
        var actor = authorizeChannel(guild, channel, user);
        if (window == null) throw unavailable();
        var saved = windows.findById(window).orElseThrow(IncidentAccessService::unavailable);
        if (!saved.getContext().getId().equals(actor.contextId())) throw unavailable();
        return actor;
    }

    public void requireCurrent(AuthorizedIncidentActor actor, IncidentMatchContext context, IncidentParty original) {
        if (actor == null || context == null || !Objects.equals(actor.contextId(), context.getId())) throw unavailable();
        var current = authorizeChannel(actor.guildId(), actor.channelId(), actor.userId());
        if (!current.contextId().equals(context.getId()) || !Objects.equals(actor.party(), current.party())
                || original != null && (original.getType() != current.party().getType() || !original.getId().equals(current.party().getId()))) throw unavailable();
    }

    private static BusinessRuleException unavailable() { return new IncidentAccessDeniedException(IncidentAccessDeniedException.Reason.UNAVAILABLE); }
}
