package org.ctc.discord.picks;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.service.DriverStatsService;
import org.ctc.domain.service.StatsSeasonService;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsScope;
import org.springframework.stereotype.Component;

/** Entry point of the public /stats command. Answers are private embeds without mentions. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsInteractionHandler implements PickGatewayInteractionHandler {
    private final DriverStatsService drivers;
    private final StatsSeasonService seasons;

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
        reply.deferPrivate(continuation -> {
            try {
                var stats = drivers.stats(event.option("name"), StatsScope.parse(event.option("scope")), event.option("season"));
                continuation.replyPrivate(new PickGatewayMessage("", List.of(), null, StatsEmbeds.driver(stats)));
            } catch (StatsLookupException unknown) {
                continuation.replyPrivate(unknown.getMessage());
            }
        });
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
