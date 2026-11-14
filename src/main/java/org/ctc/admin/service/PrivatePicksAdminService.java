package org.ctc.admin.service;

import java.util.List;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.TeamPickChannelBinding;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.admin.dto.PrivatePickBindingForm;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.picks.DiscordPicksGateway;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.picks.PickChannelPermissionSource;
import org.ctc.domain.repository.TeamPickChannelBindingRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.repository.MatchRepository;

@Service
@RequiredArgsConstructor
public class PrivatePicksAdminService {
    private final TeamPickChannelBindingRepository bindings;
    private final SeasonRepository seasons;
    private final TeamRepository teams;
    private final MatchRepository matches;
    private final DiscordGlobalConfigRepository configs;
    private final DiscordRestClient discord;
    private final PickChannelPermissionSource permissions;
    private final DiscordPicksGateway gateway;

    @Transactional(readOnly = true)
    public Overview overview() {
        var config = configs.findFirstByOrderByIdAsc();
        var order = Comparator.comparing(Choice::label, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).thenComparing(Choice::id);
        var seasonChoices = seasons.findAll().stream().map(row -> new Choice(row.getId(), row.getDisplayLabel())).sorted(order).toList();
        var teamChoices = teams.findAll().stream().map(row -> new Choice(row.getId(), row.getShortName() + " - " + row.getName())).sorted(order).toList();
        var rows = bindings.findAllByOrderByGuildIdAscChannelIdAsc().stream().map(row -> new BindingRow(row.getId(),
                row.getSeason().getDisplayLabel(), row.getTeam().getShortName() + " - " + row.getTeam().getName(),
                row.getGuildId(), row.getChannelId())).toList();
        return new Overview(config == null ? "" : config.getGuildId(), healthLabel(gateway.health()),
                config != null && snowflake(config.getGuildId()) && snowflake(config.getBotApplicationId()),
                seasonChoices, teamChoices, rows);
    }

    @Transactional
    public UUID save(PrivatePickBindingForm form) {
        if (form == null || form.getSeasonId() == null || form.getTeamId() == null || !snowflake(form.getChannelId())) {
            throw new BusinessRuleException("Select a season and team and enter a valid Discord channel ID.");
        }
        var season = seasons.findByIdForUpdate(form.getSeasonId()).orElseThrow(() -> new BusinessRuleException("The season no longer exists."));
        var team = teams.findById(form.getTeamId()).orElseThrow(() -> new BusinessRuleException("The team no longer exists."));
        var config = configured();
        if (matches.existsByDiscordChannelId(form.getChannelId())) throw new BusinessRuleException("Shared match channels cannot receive private team picks.");
        var conflict = bindings.findBySeasonIdAndGuildIdAndChannelId(season.getId(), config.getGuildId(), form.getChannelId()).orElse(null);
        if (conflict != null && !team.getId().equals(conflict.getTeam().getId())) {
            throw new BusinessRuleException("This channel is already bound to another team in the season.");
        }
        var bot = currentBot();
        boolean allowed;
        try {
            allowed = permissions.hasCurrentPrivateAccess(config.getGuildId(), form.getChannelId(), bot.id());
        } catch (RuntimeException unavailable) {
            allowed = false;
        }
        if (!allowed) throw new BusinessRuleException("Choose a private server text channel that the bot can currently access.");
        var row = bindings.findBySeasonIdAndTeamIdAndGuildId(season.getId(), team.getId(), config.getGuildId())
                .orElseGet(() -> new TeamPickChannelBinding(season, team, config.getGuildId(), form.getChannelId()));
        row.setChannelId(form.getChannelId());
        return bindings.saveAndFlush(row).getId();
    }

    @Transactional
    public void delete(UUID id) {
        var row = bindings.findById(id).orElseThrow(() -> new BusinessRuleException("This binding no longer exists."));
        var season = seasons.findByIdForUpdate(row.getSeason().getId()).orElseThrow(() -> new BusinessRuleException("The season no longer exists."));
        var current = bindings.findCurrentForUpdate(season.getId(), row.getTeam().getId(), row.getGuildId(), row.getChannelId())
                .orElseThrow(() -> new BusinessRuleException("This binding changed. Reload before removing it."));
        bindings.delete(current);
        bindings.flush();
    }

    public void registerCommand() {
        var config = configured();
        if (!snowflake(config.getBotApplicationId()) || !config.getBotApplicationId().equals(currentBot().id())) {
            throw new BusinessRuleException("Configure the application ID for the existing bot identity.");
        }
        try {
            discord.registerPicksGuildCommand(config.getBotApplicationId(), config.getGuildId());
        } catch (DiscordApiException | RuntimeException unavailable) {
            throw new BusinessRuleException("The private picks command could not be registered. Retry when Discord is available.");
        }
    }

    private DiscordGlobalConfig configured() {
        var config = configs.findFirstByOrderByIdAsc();
        if (config == null || !snowflake(config.getGuildId())) throw new BusinessRuleException("Configure a Discord guild first.");
        return config;
    }

    private DiscordRestClient.BotUser currentBot() {
        try {
            var bot = discord.fetchBotUser();
            if (bot != null && snowflake(bot.id())) return bot;
        } catch (DiscordApiException | RuntimeException unavailable) {
            // The management form receives a generic error without remote diagnostic payloads.
        }
        throw new BusinessRuleException("The current bot identity could not be verified. Check its configuration and retry.");
    }

    private static boolean snowflake(String value) { return value != null && value.matches("[0-9]{1,32}"); }

    private static String healthLabel(DiscordPicksGateway.Health health) {
        return switch (health) {
            case DISABLED -> "Disabled";
            case MISCONFIGURED -> "Bot or server configuration is incomplete";
            case CONNECTING -> "Connecting";
            case CONNECTED -> "Connected";
            case RECONNECTING -> "Reconnecting";
            case STOPPED -> "Stopped";
            case FAILED -> "Connection unavailable";
        };
    }

    public record Choice(UUID id, String label) { }
    public record BindingRow(UUID id, String season, String team, String guildId, String channelId) { }
    public record Overview(String guildId, String health, boolean registrationReady,
                           List<Choice> seasons, List<Choice> teams, List<BindingRow> bindings) { }
}
