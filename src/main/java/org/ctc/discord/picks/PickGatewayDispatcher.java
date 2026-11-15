package org.ctc.discord.picks;

import java.time.Clock;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
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
    private final Predicate<String> enabled;
    private volatile ThreadPoolTaskExecutor executor;

    public PickGatewayDispatcher(PickGatewaySettings settings, PickGatewayInteractionHandler handler, ImportLockService imports,
                                 Clock clock, LongSupplier epoch) {
        this(settings, handler, imports, clock, epoch, command -> true);
    }

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
        if (interaction.kind() == PickGatewayInteraction.Kind.AUTOCOMPLETE) {
            autocomplete(expectedGuild, interaction, pool);
            return;
        }
        var reply = new PickGatewayReply(interaction.response(), clock, interaction.createdAt(), epoch,
                task -> {
                    if (pool == null || executor != pool) throw new java.util.concurrent.RejectedExecutionException();
                    pool.execute(task);
                });
        if (expectedGuild == null || !expectedGuild.equals(interaction.guildId())) {
            reply.replyPrivate(feature(interaction) + " unavailable in this channel.");
        } else if (pool == null || !enabled.test(command(interaction.action()))) {
            reply.replyPrivate(feature(interaction) + " unavailable. Try again later.");
        } else if (imports.isLocked()) {
            reply.replyPrivate("stats".equals(command(interaction.action())) ? "Stats are unavailable during maintenance."
                    : "A backup restore is in progress. Try again later.");
        } else {
            try {
                handler.onInteraction(interaction, reply);
            } catch (RuntimeException failure) {
                if (!reply.isAcknowledged()) reply.replyPrivate("This request could not be completed. Try again.");
            }
        }
    }

    /** Autocomplete answers on the gateway thread; a failure or an unavailable feature answers with no choices. */
    private void autocomplete(String expectedGuild, PickGatewayInteraction interaction, ThreadPoolTaskExecutor pool) {
        var reply = new PickGatewayReply(interaction.response(), clock, interaction.createdAt(), epoch, Runnable::run);
        try {
            if (expectedGuild == null || !expectedGuild.equals(interaction.guildId()) || pool == null
                    || !enabled.test(command(interaction.action())) || imports.isLocked()) {
                reply.replyChoices(java.util.List.of());
            } else {
                handler.onInteraction(interaction, reply);
            }
        } catch (RuntimeException failure) {
            if (!reply.isAcknowledged()) reply.replyChoices(java.util.List.of());
        }
    }

    private boolean belongsToReporting(PickGatewayInteraction interaction) {
        if (interaction == null || interaction.kind() == null || interaction.action() == null) return false;
        return interaction.kind() == PickGatewayInteraction.Kind.COMMAND || interaction.kind() == PickGatewayInteraction.Kind.AUTOCOMPLETE
                ? COMMANDS.contains(interaction.action())
                : COMMANDS.stream().anyMatch(command -> interaction.action().startsWith(prefix(command)));
    }

    private static final java.util.List<String> COMMANDS = java.util.List.of("picks", "incidents", "lineup", "stats");

    /** Routes an interaction to the command it belongs to, or null when it belongs to none. */
    public static String command(String action) {
        if (action == null) return null;
        return COMMANDS.stream().filter(command -> command.equals(action) || action.startsWith(prefix(command))).findFirst().orElse(null);
    }

    /** Picks and lineups edit their initial private reply; incident delivery keeps its own response mode. */
    static boolean reusesPrivateMessage(String action) {
        String command = command(action);
        return "picks".equals(command) || "lineup".equals(command);
    }

    private static String prefix(String command) { return "ctc-" + command + ":"; }

    private static String feature(PickGatewayInteraction event) {
        return switch (String.valueOf(command(event.action()))) {
            case "incidents" -> "Incident reporting is";
            case "lineup" -> "Lineups are";
            case "stats" -> "Stats are";
            default -> "Picks are";
        };
    }
}
