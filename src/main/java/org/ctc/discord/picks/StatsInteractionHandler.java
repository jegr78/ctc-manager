package org.ctc.discord.picks;

import java.util.List;
import org.springframework.stereotype.Component;

/** Entry point of the public /stats command; the subcommands follow in their own deliveries. */
@Component
public class StatsInteractionHandler implements PickGatewayInteractionHandler {
    @Override
    public void onInteraction(PickGatewayInteraction event, PickGatewayReply reply) {
        if (event.kind() == PickGatewayInteraction.Kind.AUTOCOMPLETE) {
            reply.replyChoices(List.of());
            return;
        }
        reply.replyPrivate("Stats are not available yet.");
    }
}
