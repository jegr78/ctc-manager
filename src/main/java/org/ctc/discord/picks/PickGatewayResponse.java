package org.ctc.discord.picks;

import java.util.concurrent.CompletionStage;

public interface PickGatewayResponse {
    CompletionStage<Continuation> deferPrivate();
    void replyPrivate(String message);
    void openModal(PickGatewayModal modal);
    /** Answers an autocomplete request with at most 25 choices. */
    default void replyChoices(java.util.List<PickGatewayModal.Option> choices) { throw new UnsupportedOperationException("Autocomplete is unavailable"); }
    interface Continuation {
        void replyPrivate(String message);
        default void replyPrivate(PickGatewayMessage message) { throw new UnsupportedOperationException("Interactive replies are unavailable"); }
        /** Posts a message visible to the whole channel, still without mentions; send a private reply first. */
        default void postVisible(PickGatewayMessage message) { throw new UnsupportedOperationException("Visible posts are unavailable"); }
    }
}
