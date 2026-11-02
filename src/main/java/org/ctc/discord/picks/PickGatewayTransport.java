package org.ctc.discord.picks;

import java.util.function.Consumer;

public interface PickGatewayTransport {
    Session connect(String botToken, String guildId, Consumer<PickGatewayInteraction> receiver);
    interface Session {
        ConnectionState state();
        void close();
    }
    enum ConnectionState { CONNECTING, CONNECTED, RECONNECTING, STOPPED }
}
