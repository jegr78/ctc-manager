package org.ctc.domain.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.ctc.domain.model.IncidentMatchContext;
import org.ctc.domain.model.IncidentReportWindow;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.IncidentMatchContextRepository;
import org.ctc.domain.repository.IncidentReportWindowRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentContextService {
    private final MatchRepository matches;
    private final PlayoffMatchupRepository playoffs;
    private final SeasonRepository seasons;
    private final IncidentMatchContextRepository contexts;
    private final IncidentReportWindowRepository windows;
    private final DiscordGlobalConfigRepository configs;
    private final EntityManager entityManager;
    private final PickWriteExecutor writes;
    private final Clock clock;

    @Transactional(readOnly = true)
    public IncidentMatchTarget resolve(IncidentMatchRef reference) {
        if (reference == null || (reference.matchId() == null) == (reference.playoffMatchupId() == null)) {
            throw new BusinessRuleException("Select exactly one incident pairing");
        }
        if (reference.matchId() != null) {
            var match = matches.findById(reference.matchId()).orElseThrow(() -> new BusinessRuleException("Incident match is unavailable"));
            var playoffIds = match.getRaces().stream().map(Race::getPlayoffMatchup).filter(Objects::nonNull)
                    .map(PlayoffMatchup::getId).distinct().toList();
            if (!playoffIds.isEmpty()) {
                if (playoffIds.size() != 1 || match.getRaces().stream().anyMatch(race -> race.getPlayoffMatchup() == null)) {
                    throw new BusinessRuleException("The match has ambiguous regular/playoff ownership");
                }
                return resolve(IncidentMatchRef.playoff(playoffIds.getFirst()));
            }
            if (match.isBye() || match.getMatchday() == null) throw contested();
            return target(reference, match, null, match.getMatchday().getSeason(), match.getHomeTeam(), match.getAwayTeam(), match.getRaces());
        }
        var playoff = playoffs.findById(reference.playoffMatchupId()).orElseThrow(() -> new BusinessRuleException("Incident playoff pairing is unavailable"));
        if (playoff.isBye() || playoff.getRound() == null || playoff.getRound().getPlayoff() == null
                || playoff.getRound().getPlayoff().getPhase() == null) throw contested();
        return target(reference, null, playoff, playoff.getRound().getPlayoff().getPhase().getSeason(),
                playoff.getTeam1(), playoff.getTeam2(), playoff.getRaces());
    }

    private IncidentMatchTarget target(IncidentMatchRef reference, Match match, PlayoffMatchup playoff, Season season,
                                       Team home, Team away, List<Race> races) {
        if (season == null || season.getId() == null || home == null || away == null || home.getId() == null
                || away.getId() == null || home.getId().equals(away.getId())) throw contested();
        var reportable = races.stream().filter(race -> !race.isBye() && race.getId() != null
                && race.getHomeTeam() != null && race.getAwayTeam() != null).toList();
        if (reportable.isEmpty()) throw contested();
        for (var race : reportable) {
            boolean samePairing = playoff != null ? race.getPlayoffMatchup() != null && playoff.getId().equals(race.getPlayoffMatchup().getId())
                    : race.getMatch() != null && match.getId().equals(race.getMatch().getId()) && race.getPlayoffMatchup() == null;
            boolean participants = home.getId().equals(race.getHomeTeam().getId()) && away.getId().equals(race.getAwayTeam().getId())
                    || home.getId().equals(race.getAwayTeam().getId()) && away.getId().equals(race.getHomeTeam().getId());
            if (!samePairing || !participants || race.getMatchday() == null || race.getMatchday().getSeason() == null
                    || !season.getId().equals(race.getMatchday().getSeason().getId())) {
                throw new BusinessRuleException("The incident race has ambiguous pairing ownership");
            }
        }
        return new IncidentMatchTarget(reference, match, playoff, season, home, away, reportable);
    }

    public UUID bind(IncidentMatchRef reference, String guild, String channel, IncidentManagementActor actor) {
        return bind(reference, guild, channel, 0, actor);
    }

    public UUID bind(IncidentMatchRef reference, String guild, String channel, long expectedVersion, IncidentManagementActor actor) {
        try {
            return writes.execute(() -> {
                requireManager(actor); requireGuild(guild);
                if (!snowflake(channel)) throw new BusinessRuleException("Enter a valid incident channel ID");
                var target = resolve(reference);
                seasons.findByIdForUpdate(target.season().getId()).orElseThrow(() -> new BusinessRuleException("Incident season is unavailable"));
                lockGuild(guild);
                target = refresh(target);
                requireChannel(target, guild, channel);
                var existing = find(target.reference());
                if (existing != null) {
                    requireIdentity(existing, target);
                    if (!guild.equals(existing.getGuildId()) || !channel.equals(existing.getChannelId())) {
                        throw new BusinessRuleException("This incident pairing is already bound; preserve its recorded channel relationship");
                    }
                    return existing.getId();
                }
                if (expectedVersion != 0) throw new BusinessRuleException("Incident binding changed; reload before binding");
                var conflict = contexts.findByGuildIdAndChannelId(guild, channel).orElse(null);
                if (conflict != null) throw new BusinessRuleException("This channel is already bound to another incident context");
                var context = contexts.saveAndFlush(new IncidentMatchContext(target.match(), target.playoffMatchup(), target.season(),
                        target.home(), target.away(), guild, channel, actor.id(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
                windows.save(new IncidentReportWindow(context));
                return context.getId();
            });
        } catch (DataIntegrityViolationException conflict) {
            throw new BusinessRuleException("Incident pairing or channel was bound concurrently; reload before binding");
        }
    }

    @Transactional(readOnly = true)
    public IncidentMatchContext get(UUID id) {
        return contexts.findById(id).orElseThrow(() -> new BusinessRuleException("Incident context is unavailable"));
    }

    @Transactional(readOnly = true)
    public IncidentMatchContext find(IncidentMatchRef reference) {
        return reference.matchId() != null ? contexts.findByMatchId(reference.matchId()).orElse(null)
                : contexts.findByPlayoffMatchupId(reference.playoffMatchupId()).orElse(null);
    }

    IncidentMatchContext lock(UUID id) {
        var context = get(id);
        seasons.findByIdForUpdate(context.getSeason().getId()).orElseThrow(() -> new BusinessRuleException("Incident season is unavailable"));
        lockGuild(context.getGuildId());
        entityManager.flush(); entityManager.refresh(context);
        var target = refresh(resolve(reference(context)));
        refreshRoleMetadata(target);
        requireIdentity(context, target); requireGuild(context.getGuildId());
        requireChannel(target, context.getGuildId(), context.getChannelId());
        return context;
    }

    public static IncidentMatchRef reference(IncidentMatchContext context) {
        return context.getMatch() != null ? IncidentMatchRef.match(context.getMatch().getId())
                : IncidentMatchRef.playoff(context.getPlayoffMatchup().getId());
    }

    private IncidentMatchTarget refresh(IncidentMatchTarget target) {
        entityManager.flush();
        target.races().forEach(entityManager::refresh);
        entityManager.refresh(target.match() != null ? target.match() : target.playoffMatchup());
        return resolve(target.reference());
    }

    private void refreshRoleMetadata(IncidentMatchTarget target) {
        var participants = new java.util.TreeMap<UUID, Team>();
        participants.put(target.home().getId(), target.home()); participants.put(target.away().getId(), target.away());
        participants.values().forEach(team -> entityManager.refresh(team, jakarta.persistence.LockModeType.PESSIMISTIC_READ));
        var parents = new java.util.TreeMap<UUID, Team>();
        for (var team : participants.values()) {
            var parent = team.getParentTeam();
            if (parent != null && !participants.containsKey(parent.getId())) parents.put(parent.getId(), parent);
        }
        parents.values().forEach(team -> entityManager.refresh(team, jakarta.persistence.LockModeType.PESSIMISTIC_READ));
    }

    private void requireChannel(IncidentMatchTarget target, String guild, String channel) {
        if (target.match() != null && target.match().getDiscordChannelArchivedAt() != null) {
            throw new BusinessRuleException("An archived match channel cannot open incident reporting");
        }
        if (target.match() != null && !channel.equals(target.match().getDiscordChannelId())) {
            throw new BusinessRuleException("The incident channel must match the regular match's configured channel");
        }
        for (var legacy : matches.findByDiscordChannelId(channel)) {
            IncidentMatchRef owner;
            try { owner = resolve(IncidentMatchRef.match(legacy.getId())).reference(); }
            catch (BusinessRuleException invalid) { throw new BusinessRuleException("The legacy channel binding is ambiguous; resolve it before opening incident reports"); }
            if (!owner.equals(target.reference())) throw new BusinessRuleException("The legacy channel is shared by unrelated matches and is ambiguous");
            if (legacy.getDiscordChannelArchivedAt() != null) throw new BusinessRuleException("An archived match channel cannot open incident reporting");
        }
        var context = contexts.findByGuildIdAndChannelId(guild, channel).orElse(null);
        if (context != null && !reference(context).equals(target.reference())) {
            throw new BusinessRuleException("This channel is already bound to another incident context");
        }
    }

    private void lockGuild(String guild) {
        var config = configs.findFirstByOrderByIdAsc();
        if (config != null) { entityManager.flush(); entityManager.refresh(config, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); }
        requireGuild(guild);
    }

    private void requireGuild(String guild) {
        var config = configs.findFirstByOrderByIdAsc();
        if (!snowflake(guild) || config == null || !guild.equals(config.getGuildId())) {
            throw new BusinessRuleException("Use the configured Discord guild for incident reports");
        }
    }

    private static void requireIdentity(IncidentMatchContext context, IncidentMatchTarget target) {
        if (!reference(context).equals(target.reference()) || !context.getSeason().getId().equals(target.season().getId())
                || !context.getHomeTeam().getId().equals(target.home().getId()) || !context.getAwayTeam().getId().equals(target.away().getId())) {
            throw new BusinessRuleException("The incident pairing identity changed; preserve its original participants and season");
        }
    }

    static void requireManager(IncidentManagementActor actor) {
        if (actor == null || actor.id() == null || actor.id().isBlank() || actor.id().length() > 128) {
            throw new BusinessRuleException("An authenticated management actor is required");
        }
    }
    static boolean snowflake(String value) { return value != null && value.matches("[0-9]{1,32}"); }
    private static BusinessRuleException contested() { return new BusinessRuleException("Incident reporting requires a contested pairing with a reportable race"); }
}
