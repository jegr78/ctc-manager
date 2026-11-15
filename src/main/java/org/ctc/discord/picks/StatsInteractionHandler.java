package org.ctc.discord.picks;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.service.DriverStatsService;
import org.ctc.domain.service.StatsSeasonService;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Entry point of the public /stats command. Answers are private embeds without mentions. Only one answer is computed
 * at a time so public traffic always leaves workers for the private pick, lineup and incident commands.
 */
@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StatsInteractionHandler implements PickGatewayInteractionHandler {
    static final String BUSY = "Stats are busy. Try again shortly.";
    static final String FAILED = "Stats could not be loaded. Try again shortly.";
    private static final long IDLE = Long.MIN_VALUE;
    private static final long STALE_NANOS = 30_000_000_000L;
    private final DriverStatsService drivers;
    private final StatsSeasonService seasons;
    private final AtomicLong runningSince = new AtomicLong(IDLE);
    private final LongSupplier nanos;

    @Autowired
    public StatsInteractionHandler(DriverStatsService drivers, StatsSeasonService seasons) {
        this(drivers, seasons, System::nanoTime);
    }

    @Override
    public void onInteraction(PickGatewayInteraction event, PickGatewayReply reply) {
        if (event.kind() == PickGatewayInteraction.Kind.AUTOCOMPLETE) {
            reply.replyChoices(choices(event));
            return;
        }
        if (!"driver".equals(event.subcommand())) {
            reply.replyPrivate("Team stats are not available yet.");
            return;
        }
        long claim = claim();
        if (claim == IDLE) {
            reply.replyPrivate(BUSY);
            return;
        }
        boolean queued = false;
        try {
            queued = reply.deferPrivate(continuation -> {
                try {
                    var stats = drivers.stats(event.option("name"), StatsScope.parse(event.option("scope")), event.option("season"));
                    continuation.replyPrivate(new PickGatewayMessage("", List.of(), null, StatsEmbeds.driver(stats)));
                } catch (StatsLookupException unknown) {
                    continuation.replyPrivate(unknown.getMessage());
                } catch (RuntimeException failure) {
                    log.warn("Stats answer failed: {}", failure.getClass().getSimpleName());
                    continuation.replyPrivate(FAILED);
                } finally {
                    runningSince.compareAndSet(claim, IDLE);
                }
            });
        } finally {
            if (!queued) runningSince.compareAndSet(claim, IDLE);
        }
    }

    /** Claims the single answer slot; a claim whose work never ran expires after 30 seconds. */
    private long claim() {
        long now = nanos.getAsLong();
        if (now == IDLE) now++;
        long current = runningSince.get();
        if (current != IDLE && now - current < STALE_NANOS) return IDLE;
        return runningSince.compareAndSet(current, now) ? now : IDLE;
    }

    private List<PickGatewayModal.Option> choices(PickGatewayInteraction event) {
        try {
            List<StatsChoice> found;
            if ("season".equals(event.focused())) {
                found = seasons.choices(event.option("season"));
            } else if ("driver".equals(event.subcommand()) && "name".equals(event.focused())) {
                found = drivers.choices(event.option("name"), scope(event.option("scope")), event.option("season"));
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
            return StatsScope.CURRENT;
        }
    }
}
