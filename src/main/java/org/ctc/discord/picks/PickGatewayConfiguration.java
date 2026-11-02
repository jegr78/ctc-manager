package org.ctc.discord.picks;

import java.time.Clock;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.PickAccessService;
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
    public PickGatewayInteractionHandler pickGatewayInteractionHandler(PickAccessService access) {
        return (interaction, reply) -> reply.deferPrivate(continuation -> {
            try {
                access.authorizeChannel(interaction.guildId(), interaction.channelId(), interaction.userId());
                continuation.replyPrivate("Private picks are not available yet.");
            } catch (BusinessRuleException denied) {
                continuation.replyPrivate("Picks are unavailable in this channel.");
            }
        });
    }

    @Bean
    public PickGatewayDispatcher pickGatewayDispatcher(PickGatewaySettings settings, PickGatewayInteractionHandler handler,
                                                       ImportLockService imports, Clock clock, PickSessionEpoch epoch) {
        return new PickGatewayDispatcher(settings, handler, imports, clock, epoch);
    }

    @Bean
    public DiscordPicksGateway discordPicksGateway(PickGatewaySettings settings, PickCollectionStatus collection,
                                                  DiscordGlobalConfigRepository configs, PickGatewayTransport transport,
                                                  @Value("${app.discord.bot-token:}") String token, PickGatewayDispatcher dispatcher) {
        return new DiscordPicksGateway(settings, collection, configs, transport, token, dispatcher);
    }
}
