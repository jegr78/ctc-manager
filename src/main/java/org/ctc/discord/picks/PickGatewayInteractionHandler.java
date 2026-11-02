package org.ctc.discord.picks;

@FunctionalInterface
public interface PickGatewayInteractionHandler {
    void onInteraction(PickGatewayInteraction interaction, PickGatewayReply reply);
}
