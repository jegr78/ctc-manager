package org.ctc.domain.service;

import java.util.Collection;
import java.util.UUID;
import java.util.Objects;
import org.ctc.domain.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.IncidentMatchContextRepository;
import org.ctc.domain.repository.IncidentReportRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IncidentLifecycleGuard {
    private final IncidentMatchContextRepository contexts;
    private final IncidentReportRepository reports;
    private final org.ctc.domain.repository.IncidentReportWindowRepository windows;

    public boolean protects(Race race) {
        if (race.getId() != null && fresh(() -> reports.findFirstByRaceIdOrderByIdAsc(race.getId())).isPresent()) return true;
        if (race.getPlayoffMatchup() != null) return fresh(() -> contexts.findByPlayoffMatchupIdForUpdate(race.getPlayoffMatchup().getId())).isPresent();
        return race.getMatch() != null && race.getMatch().getId() != null && fresh(() -> contexts.findByMatchIdForUpdate(race.getMatch().getId())).isPresent();
    }
    public String identityRejection(Race race, UUID matchdayId, Team home, Team away) {
        boolean changed = !Objects.equals(race.getMatchday() == null ? null : race.getMatchday().getId(), matchdayId)
                || !Objects.equals(id(race.getHomeTeam()), id(home)) || !Objects.equals(id(race.getAwayTeam()), id(away));
        return changed && protects(race) ? "Cannot reassign a race with an incident context or report history" : null;
    }
    public void requireIdentity(Race race, UUID matchdayId, Team home, Team away) {
        String reason = identityRejection(race, matchdayId, home, away);
        if (reason != null) throw new BusinessRuleException(reason);
    }
    public void requireNoHistory(Collection<Race> races) {
        if (races.stream().anyMatch(this::protects)) {
            throw new BusinessRuleException("Cannot delete a race with a permanent incident context or report history");
        }
    }
    public void requireMatchDeletion(Match match) {
        if (match.getId() != null && fresh(() -> contexts.findByMatchIdForUpdate(match.getId())).isPresent()) {
            throw new BusinessRuleException("Cannot delete a pairing with a permanent incident context");
        }
        requireNoHistory(match.getRaces());
    }
    public void requirePlayoffIdentity(PlayoffMatchup matchup, Team home, Team away) {
        if (matchup.getId() == null) return;
        var context = fresh(() -> contexts.findByPlayoffMatchupIdForUpdate(matchup.getId())).orElse(null);
        if (context != null && (!Objects.equals(id(context.getHomeTeam()), id(home))
                || !Objects.equals(id(context.getAwayTeam()), id(away)))) {
            throw new BusinessRuleException("Cannot reassign a playoff pairing with a permanent incident context");
        }
    }

    public void requireNewChannel(Match match) {
        if (ownedContext(match) != null) throw new BusinessRuleException("Preserve the incident context's recorded channel");
    }

    public void requireChannelLink(Match match, String channelId) {
        var owned = ownedContext(match);
        if (owned != null && !owned.getChannelId().equals(channelId)) {
            throw new BusinessRuleException("Preserve the incident context's recorded channel");
        }
        for (var context : fresh(() -> contexts.findByChannelId(channelId))) {
            if (!owns(context, match)) throw new BusinessRuleException("This channel belongs to another permanent incident context");
        }
    }

    public void requireChannelArchival(Match match) {
        var bindings = new java.util.LinkedHashMap<UUID, org.ctc.domain.model.IncidentMatchContext>();
        var owned = ownedContext(match);
        if (owned != null) bindings.put(owned.getId(), owned);
        for (var context : fresh(() -> contexts.findByChannelId(match.getDiscordChannelId()))) bindings.put(context.getId(), context);
        for (var context : bindings.values()) {
            var window = windows.findByContextId(context.getId()).orElse(null);
            if (window == null || window.getState() != org.ctc.domain.model.IncidentWindowState.CLOSED) {
                throw new BusinessRuleException("Close incident reporting before archiving this channel; preserve its original relationship");
            }
        }
    }

    private org.ctc.domain.model.IncidentMatchContext ownedContext(Match match) {
        var direct = fresh(() -> contexts.findByMatchIdForUpdate(match.getId())).orElse(null);
        if (direct != null) return direct;
        for (var race : match.getRaces()) {
            if (race.getPlayoffMatchup() != null) {
                var context = fresh(() -> contexts.findByPlayoffMatchupIdForUpdate(race.getPlayoffMatchup().getId())).orElse(null);
                if (context != null) return context;
            }
        }
        return null;
    }

    private static boolean owns(org.ctc.domain.model.IncidentMatchContext context, Match match) {
        if (context.getMatch() != null) return context.getMatch().getId().equals(match.getId());
        return !match.getRaces().isEmpty() && match.getRaces().stream().allMatch(race -> race.getPlayoffMatchup() != null
                && context.getPlayoffMatchup().getId().equals(race.getPlayoffMatchup().getId()));
    }

    private static <T> T fresh(java.util.function.Supplier<T> read) {
        try { return read.get(); }
        catch (RuntimeException failure) {
            var translated = RaceSelectionConcurrency.translate(failure);
            if (translated instanceof BusinessRuleException && translated != failure) {
                throw new BusinessRuleException("The incident relationships changed; reload before changing this pairing");
            }
            throw translated;
        }
    }

    private static UUID id(Team team) { return team == null ? null : team.getId(); }
}
