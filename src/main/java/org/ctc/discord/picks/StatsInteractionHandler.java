package org.ctc.discord.picks;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.service.DriverStatsService;
import org.ctc.domain.service.StatsSeasonService;
import org.ctc.domain.service.TeamStatsService;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.StatsScope;
import org.ctc.sitegen.SiteProfileLinks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Entry point of the public /stats command. Answers are private embeds without mentions and carry a stateless
 * "Share in channel" button that recomputes the answer and posts it visibly. Stats answers hold at most one worker
 * fewer than the pool has, so with at least two workers public traffic always leaves one for the private pick, lineup
 * and incident commands.
 */
@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StatsInteractionHandler implements PickGatewayInteractionHandler {
    static final String BUSY = "Stats are busy. Try again shortly.";
    static final String FAILED = "Stats could not be loaded. Try again shortly.";
    static final String GONE = "No longer available";
    static final String SHARE = "ctc-stats:share:";
    private final DriverStatsService drivers;
    private final TeamStatsService teams;
    private final StatsSeasonService seasons;
    private final SiteProfileLinks links;
    private final Semaphore slots;

    @Autowired
    public StatsInteractionHandler(DriverStatsService drivers, TeamStatsService teams, StatsSeasonService seasons, SiteProfileLinks links,
                                   PickGatewaySettings settings) {
        this(drivers, teams, seasons, links, new Semaphore(Math.max(1, settings.getWorkers() - 1)));
    }

    @Override
    public void onInteraction(PickGatewayInteraction event, PickGatewayReply reply) {
        switch (event.kind()) {
            case AUTOCOMPLETE -> reply.replyChoices(choices(event));
            case BUTTON -> share(event, reply);
            default -> {
                boolean driver = "driver".equals(event.subcommand());
                if (!driver && !"team".equals(event.subcommand())) {
                    reply.replyPrivate("Choose /stats driver or /stats team.");
                    return;
                }
                answer(reply, null, () -> compute(driver, event.option("name"), event.option("scope"), event.option("season")));
            }
        }
    }

    private void share(PickGatewayInteraction event, PickGatewayReply reply) {
        var request = ShareRequest.parse(event.action());
        if (request == null) {
            reply.replyPrivate(GONE);
            return;
        }
        String sharedBy = "Shared by " + (event.userName() == null || event.userName().isBlank() ? "a member" : PickInteractionViews.escape(event.userName()));
        answer(reply, sharedBy, () -> {
            if (!(request.driver() ? drivers.exists(request.id()) : teams.exists(request.id()))) throw new StatsLookupException(GONE);
            return compute(request.driver(), request.id().toString(), request.scope(), request.season());
        });
    }

    /** Computes on a worker after the private acknowledgement; a share posts the embed visibly only once it exists. */
    private void answer(PickGatewayReply reply, String sharedBy, Supplier<Answer> work) {
        if (!slots.tryAcquire()) {
            reply.replyPrivate(BUSY);
            return;
        }
        boolean queued = false;
        try {
            Consumer<PickGatewayResponse.Continuation> operation = continuation -> {
                try {
                    var answer = work.get();
                    if (sharedBy == null) {
                        continuation.replyPrivate(new PickGatewayMessage("", List.of(new PickGatewayMessage.Button(answer.shareId(), "Share in channel")),
                                null, answer.embed()));
                    } else {
                        continuation.replyPrivate("Sharing in this channel.");
                        continuation.postVisible(new PickGatewayMessage(sharedBy, List.of(), null, answer.embed()));
                    }
                } catch (StatsLookupException unknown) {
                    continuation.replyPrivate(sharedBy == null ? PickInteractionViews.escape(unknown.getMessage()) : GONE);
                } catch (RuntimeException failure) {
                    log.warn("Stats answer failed: {}", failure.getClass().getSimpleName());
                    continuation.replyPrivate(FAILED);
                }
            };
            queued = reply.deferPrivate(operation, slots::release);
        } finally {
            if (!queued) slots.release();
        }
    }

    private Answer compute(boolean driver, String name, String scopeOption, String seasonOption) {
        var scope = StatsScope.parse(scopeOption);
        if (driver) {
            var stats = drivers.stats(name, scope, seasonOption);
            return new Answer(StatsEmbeds.driver(stats, links.driver(stats.driverId(), stats.profileSeasonLabel())),
                    ShareRequest.id(true, stats.driverId(), stats.period()));
        }
        var stats = teams.stats(name, scope, seasonOption);
        return new Answer(StatsEmbeds.team(stats, links.team(stats.teamId(), stats.profileSeasonLabel())),
                ShareRequest.id(false, stats.teamId(), stats.period()));
    }

    private List<PickGatewayModal.Option> choices(PickGatewayInteraction event) {
        try {
            List<StatsChoice> found;
            if ("season".equals(event.focused())) {
                found = seasons.choices(event.option("season"));
            } else if ("name".equals(event.focused()) && "driver".equals(event.subcommand())) {
                found = drivers.choices(event.option("name"), scope(event.option("scope")), event.option("season"));
            } else if ("name".equals(event.focused()) && "team".equals(event.subcommand())) {
                found = teams.choices(event.option("name"), scope(event.option("scope")), event.option("season"));
            } else {
                found = List.of();
            }
            return found.stream().map(choice -> new PickGatewayModal.Option(choice.label(), choice.value())).toList();
        } catch (RuntimeException failure) {
            log.warn("Stats autocomplete failed: {}", failure.getClass().getSimpleName());
            return List.of();
        }
    }

    private static StatsScope scope(String option) {
        try {
            return StatsScope.parse(option);
        } catch (StatsLookupException invalid) {
            return StatsScope.ALLTIME;
        }
    }

    private record Answer(PickGatewayMessage.Embed embed, String shareId) { }

    /**
     * The share button's custom ID: {@code ctc-stats:share:<d|t>:<entity>:<a|s>:<season or ->}, at most 93 characters. It
     * names the season the answer showed; {@code c} from earlier buttons still parses as the current season.
     */
    record ShareRequest(boolean driver, UUID id, String scope, String season) {
        static String id(boolean driver, UUID entity, StatsPeriod period) {
            String shown = period.alltime() ? "a:-" : "s:" + period.season().id();
            return SHARE + (driver ? "d" : "t") + ":" + entity + ":" + shown;
        }

        static ShareRequest parse(String action) {
            if (action == null || !action.startsWith(SHARE)) return null;
            String[] parts = action.substring(SHARE.length()).split(":", -1);
            if (parts.length != 4 || !List.of("d", "t").contains(parts[0])) return null;
            String scope = switch (parts[2]) { case "c" -> "current"; case "a" -> "alltime"; case "s" -> "season"; default -> null; };
            try {
                UUID entity = UUID.fromString(parts[1]);
                String season = "-".equals(parts[3]) ? null : UUID.fromString(parts[3]).toString();
                if (scope == null || ("season".equals(scope) != (season != null))) return null;
                return new ShareRequest("d".equals(parts[0]), entity, scope, season);
            } catch (IllegalArgumentException malformed) {
                return null;
            }
        }
    }
}
