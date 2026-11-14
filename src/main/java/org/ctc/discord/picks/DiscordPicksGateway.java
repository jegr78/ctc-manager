package org.ctc.discord.picks;

import lombok.RequiredArgsConstructor;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.springframework.context.SmartLifecycle;

@RequiredArgsConstructor
public class DiscordPicksGateway implements SmartLifecycle {
    private final PickGatewaySettings settings;
    private final DiscordGlobalConfigRepository configs;
    private final PickGatewayTransport transport;
    private final String botToken;
    private final PickGatewayDispatcher dispatcher;
    private PickGatewayTransport.Session session;
    private Health status = Health.DISABLED;

    @Override
    public synchronized void start() {
        if (session != null) return;
        if (!settings.isEnabled()) { status = Health.DISABLED; return; }
        try {
            var config = configs.findFirstByOrderByIdAsc();
            if (botToken == null || botToken.isBlank() || config == null || config.getGuildId() == null
                    || !config.getGuildId().matches("[0-9]{1,32}")) {
                status = Health.MISCONFIGURED;
                return;
            }
            status = Health.CONNECTING;
            dispatcher.start();
            String guildId = config.getGuildId();
            session = transport.connect(botToken, guildId, interaction -> dispatcher.accept(guildId, interaction));
            if (session == null) { dispatcher.stop(); status = Health.FAILED; }
        } catch (RuntimeException unavailable) {
            dispatcher.stop();
            status = Health.FAILED;
        }
    }

    @Override
    public synchronized void stop() {
        dispatcher.stop();
        if (session == null) { status = Health.STOPPED; return; }
        try {
            session.close();
            session = null;
            status = Health.STOPPED;
        } catch (RuntimeException unavailable) {
            status = Health.FAILED;
        }
    }

    @Override
    public synchronized boolean isRunning() { return session != null; }

    public synchronized Health health() {
        if (session == null || status == Health.FAILED) return status;
        var connection = session.state();
        if (connection == null) return Health.CONNECTING;
        return switch (connection) {
            case CONNECTING -> Health.CONNECTING;
            case CONNECTED -> Health.CONNECTED;
            case RECONNECTING -> Health.RECONNECTING;
            case STOPPED -> Health.STOPPED;
        };
    }

    public enum Health { DISABLED, MISCONFIGURED, CONNECTING, CONNECTED, RECONNECTING, STOPPED, FAILED }
}
