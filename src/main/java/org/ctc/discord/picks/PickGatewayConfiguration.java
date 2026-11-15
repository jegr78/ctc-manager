package org.ctc.discord.picks;

import java.time.Clock;
import org.ctc.discord.incidents.IncidentInteractionHandler;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.integration.OutboundPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PickGatewaySettings.class, StatsGatewaySettings.class})
public class PickGatewayConfiguration {
    @Bean
    public PickGatewayTransport pickGatewayTransport(OutboundPolicy outbound) {
        return new JdaPickGatewayTransport(outbound);
    }

    @Bean
    public PickDraftSessionStore pickDraftSessionStore(Clock clock, PickSessionEpoch epoch) {
        return new PickDraftSessionStore(clock, epoch, 512);
    }

    @Bean
    public LineupSessionStore lineupSessionStore(Clock clock, PickSessionEpoch epoch) {
        return new LineupSessionStore(clock, epoch, 512);
    }

    @Bean
    public PickGatewayDispatcher pickGatewayDispatcher(PickGatewaySettings settings, StatsGatewaySettings stats, PickInteractionHandler picks,
                                                       IncidentInteractionHandler incidents, LineupInteractionHandler lineups,
                                                       StatsInteractionHandler statistics, ImportLockService imports, Clock clock, PickSessionEpoch epoch) {
        return new PickGatewayDispatcher(settings, (event, reply) -> {
            switch (String.valueOf(PickGatewayDispatcher.command(event.action()))) {
                case "incidents" -> incidents.onInteraction(event, reply);
                case "lineup" -> lineups.onInteraction(event, reply);
                case "stats" -> statistics.onInteraction(event, reply);
                default -> picks.onInteraction(event, reply);
            }
        }, imports, clock, epoch, command -> "stats".equals(command) ? stats.isEnabled() : settings.isEnabled() || !stats.isEnabled());
    }

    @Bean
    public DiscordPicksGateway discordPicksGateway(PickGatewaySettings settings, StatsGatewaySettings stats,
                                                  DiscordGlobalConfigRepository configs, PickGatewayTransport transport,
                                                  @Value("${app.discord.bot-token:}") String token, PickGatewayDispatcher dispatcher) {
        return new DiscordPicksGateway(() -> settings.isEnabled() || stats.isEnabled(), configs, transport, token, dispatcher);
    }
}
