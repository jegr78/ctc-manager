package org.ctc.discord.picks;

import java.time.Clock;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import org.ctc.backup.lock.ImportLockService;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@RequiredArgsConstructor
public class PickGatewayDispatcher {
    private final PickGatewaySettings settings;
    private final PickGatewayInteractionHandler handler;
    private final ImportLockService imports;
    private final Clock clock;
    private final LongSupplier epoch;
    private volatile ThreadPoolTaskExecutor executor;

    public synchronized void start() {
        if (executor != null) return;
        if (settings.getWorkers() < 1 || settings.getWorkers() > 8
                || settings.getQueueCapacity() < 1 || settings.getQueueCapacity() > 256) {
            throw new IllegalArgumentException("Picks workers or queue capacity are outside supported bounds");
        }
        var pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(settings.getWorkers());
        pool.setMaxPoolSize(settings.getWorkers());
        pool.setQueueCapacity(settings.getQueueCapacity());
        pool.setThreadNamePrefix("discord-picks-");
        pool.setDaemon(true);
        pool.initialize();
        executor = pool;
    }

    public synchronized void stop() {
        var pool = executor;
        executor = null;
        if (pool != null) pool.shutdown();
    }

    public void accept(String expectedGuild, PickGatewayInteraction interaction) {
        if (!belongsToReporting(interaction) || interaction.createdAt() == null
                || !clock.instant().isBefore(interaction.createdAt().plusSeconds(3))) return;
        var pool = executor;
        var reply = new PickGatewayReply(interaction.response(), clock, interaction.createdAt(), epoch,
                task -> {
                    if (pool == null || executor != pool) throw new java.util.concurrent.RejectedExecutionException();
                    pool.execute(task);
                });
        if (expectedGuild == null || !expectedGuild.equals(interaction.guildId())) {
            reply.replyPrivate(incident(interaction) ? "Incident reporting is unavailable in this channel." : "Picks are unavailable in this channel.");
        } else if (pool == null) {
            reply.replyPrivate(incident(interaction) ? "Incident reporting is unavailable. Try again later." : "Picks are unavailable. Try again later.");
        } else if (imports.isLocked()) {
            reply.replyPrivate("A backup restore is in progress. Try again later.");
        } else {
            try {
                handler.onInteraction(interaction, reply);
            } catch (RuntimeException failure) {
                if (!reply.isAcknowledged()) reply.replyPrivate("This request could not be completed. Try again.");
            }
        }
    }

    private boolean belongsToReporting(PickGatewayInteraction interaction) {
        if (interaction == null || interaction.kind() == null || interaction.action() == null) return false;
        return interaction.kind() == PickGatewayInteraction.Kind.COMMAND
                ? "picks".equals(interaction.action()) || "incidents".equals(interaction.action())
                : interaction.action().startsWith("ctc-picks:") || interaction.action().startsWith("ctc-incidents:");
    }

    private static boolean incident(PickGatewayInteraction event) { return "incidents".equals(event.action()) || event.action().startsWith("ctc-incidents:"); }
}
