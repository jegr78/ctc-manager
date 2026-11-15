package org.ctc.discord.picks;

import java.util.concurrent.CompletionStage;

public interface PickGatewayResponse {
    CompletionStage<Continuation> deferPrivate();
    /** Acknowledges visibly in the channel; the continuation's messages are public and still mention nobody. */
    default CompletionStage<Continuation> deferPublic() { throw new UnsupportedOperationException("Public replies are unavailable"); }
    void replyPrivate(String message);
    void openModal(PickGatewayModal modal);
    /** Answers an autocomplete request with at most 25 choices. */
    default void replyChoices(java.util.List<PickGatewayModal.Option> choices) { throw new UnsupportedOperationException("Autocomplete is unavailable"); }
    interface Continuation {
        void replyPrivate(String message);
        default void replyPrivate(PickGatewayMessage message) { throw new UnsupportedOperationException("Interactive replies are unavailable"); }
    }
}
