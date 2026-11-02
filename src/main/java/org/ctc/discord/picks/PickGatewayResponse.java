package org.ctc.discord.picks;

import java.util.concurrent.CompletionStage;

public interface PickGatewayResponse {
    CompletionStage<Continuation> deferPrivate();
    void replyPrivate(String message);
    void openModal(PickGatewayModal modal);
    interface Continuation { void replyPrivate(String message); }
}
