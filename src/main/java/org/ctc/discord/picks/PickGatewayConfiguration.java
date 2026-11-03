package org.ctc.discord.picks;

import java.time.Clock;
import org.ctc.discord.incidents.IncidentInteractionHandler;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.service.PickCollectionStatus;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.integration.OutboundPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PickGatewaySettings.class)
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
    public PickGatewayDispatcher pickGatewayDispatcher(PickGatewaySettings settings, PickInteractionHandler picks, IncidentInteractionHandler incidents,
                                                       ImportLockService imports, Clock clock, PickSessionEpoch epoch) {
        return new PickGatewayDispatcher(settings, (event, reply) -> {
            if ("incidents".equals(event.action()) || event.action().startsWith("ctc-incidents:")) incidents.onInteraction(event, reply);
            else picks.onInteraction(event, reply);
        }, imports, clock, epoch);
    }

    @Bean
    public DiscordPicksGateway discordPicksGateway(PickGatewaySettings settings, PickCollectionStatus collection,
                                                  DiscordGlobalConfigRepository configs, PickGatewayTransport transport,
                                                  @Value("${app.discord.bot-token:}") String token, PickGatewayDispatcher dispatcher) {
        return new DiscordPicksGateway(settings, collection, configs, transport, token, dispatcher);
    }
}
