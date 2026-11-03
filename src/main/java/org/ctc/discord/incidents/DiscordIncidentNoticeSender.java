package org.ctc.discord.incidents;

import lombok.RequiredArgsConstructor;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.IncidentDeliveryOperation;
import org.ctc.domain.service.IncidentNoticeService;
import org.ctc.integration.OutboundPolicy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscordIncidentNoticeSender implements IncidentReceiptSender {
    private final DiscordRestClient discord;
    private final DiscordGlobalConfigRepository configs;
    private final OutboundPolicy outbound;

    @Override public IncidentSendResult send(IncidentDeliveryClaim claim) {
        if (!outbound.isEnabled()) return rejected("OUTBOUND_DISABLED");
        if (claim == null || claim.operation() != IncidentDeliveryOperation.RECEIPT || !snowflake(claim.guildId()) || !snowflake(claim.channelId())
                || claim.nonce() == null || !claim.nonce().matches("[A-Za-z0-9_-]{1,25}")) return rejected("INVALID_CLAIM");
        boolean posting = false;
        try {
            var config = configs.findFirstByOrderByIdAsc();
            if (config == null || !claim.guildId().equals(config.getGuildId())) return rejected("CONFIGURATION");
            var channel = discord.fetchAccessChannel(claim.channelId());
            if (channel == null || !claim.channelId().equals(channel.id()) || !claim.guildId().equals(channel.guildId()) || !Integer.valueOf(0).equals(channel.type())) return rejected("DESTINATION");
            posting = true;
            var message = discord.createBotMessage(claim.channelId(), claim.nonce(), IncidentNoticeService.neutralText());
            if (message == null || !snowflake(message.id()) || !claim.channelId().equals(message.channelId())) return uncertain("INVALID_RESPONSE");
            return new IncidentSendResult(IncidentSendResult.Disposition.SENT, message.id(), null);
        } catch (DiscordApiException failure) {
            boolean unknown = posting && failure.category() == DiscordApiException.Category.TRANSIENT
                    && !(failure instanceof DiscordTransientException transientFailure && transientFailure.isConfirmedRejection());
            return unknown ? uncertain("SEND_UNCERTAIN") : rejected(posting ? "DISCORD_REJECTED" : "DESTINATION_UNAVAILABLE");
        } catch (RuntimeException failure) {
            return posting ? uncertain("SEND_UNCERTAIN") : rejected("DESTINATION_UNAVAILABLE");
        }
    }
    private static boolean snowflake(String id) { return id != null && id.matches("[0-9]{1,32}"); }
    private static IncidentSendResult rejected(String code) { return new IncidentSendResult(IncidentSendResult.Disposition.REJECTED,null,code); }
    private static IncidentSendResult uncertain(String code) { return new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN,null,code); }
}
