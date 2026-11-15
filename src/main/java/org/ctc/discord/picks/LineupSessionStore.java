package org.ctc.discord.picks;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.lineups.LineupChoice;
import org.ctc.domain.lineups.LineupTarget;
import org.ctc.domain.model.LineupPayload;

/** Transient lineup dialogs bound to one user, guild and channel; restores and expiry invalidate them. */
public final class LineupSessionStore {
    static final String PREFIX = "ctc-lineup";
    private final Clock clock;
    private final LongSupplier epoch;
    private final int capacity;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public LineupSessionStore(Clock clock, LongSupplier epoch, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Session capacity must be positive");
        this.clock = clock;
        this.epoch = epoch;
        this.capacity = capacity;
    }

    synchronized Session create(PickGatewayInteraction event) {
        sessions.values().removeIf(session -> !live(session));
        if (sessions.size() >= capacity) throw new BusinessRuleException("Lineups are busy. Try again shortly.");
        var session = new Session(event.guildId(), event.channelId(), event.userId(), epoch.getAsLong(), clock.instant().plusSeconds(1800));
        sessions.put(session.id, session);
        return session;
    }

    <T> T withAction(PickGatewayInteraction event, BiFunction<Session, Action, T> operation) {
        String[] parts = event.action().split(":", -1);
        Session session;
        try {
            if (parts.length != 3 || !PREFIX.equals(parts[0])) throw expired();
            synchronized (this) { session = sessions.get(UUID.fromString(parts[1])); }
        } catch (IllegalArgumentException invalid) { throw expired(); }
        if (session == null) throw expired();
        if (!session.lock.tryLock()) throw new BusinessRuleException("This dialog is busy. Try again shortly.");
        try {
            var action = session.actions.get(parts[2]);
            if (!live(session) || !Objects.equals(session.guild, event.guildId()) || !Objects.equals(session.channel, event.channelId())
                    || !Objects.equals(session.user, event.userId()) || action == null || action.kind() != event.kind()) throw expired();
            return operation.apply(session, action);
        } finally { session.lock.unlock(); }
    }

    private boolean live(Session session) { return session.generation == epoch.getAsLong() && clock.instant().isBefore(session.expires); }
    static BusinessRuleException expired() { return new BusinessRuleException("This dialog expired or was reset. Use /lineup to start again."); }

    static final class Session {
        final ReentrantLock lock = new ReentrantLock();
        final UUID id = UUID.randomUUID();
        final String guild;
        final String channel;
        final String user;
        final long generation;
        final Instant expires;
        final Map<String, Action> actions = new HashMap<>();
        final LinkedHashMap<UUID, LineupChoice> selected = new LinkedHashMap<>();
        final List<LineupPayload.Entry> proposed = new ArrayList<>();
        List<LineupChoice> choices = List.of();
        UUID request = UUID.randomUUID();
        UUID raceId;
        UUID teamId;
        long version;
        int size;
        String label;

        Session(String guild, String channel, String user, long generation, Instant expires) {
            this.guild = guild; this.channel = channel; this.user = user; this.generation = generation; this.expires = expires;
        }

        String action(String name, PickGatewayInteraction.Kind kind) { return action(new Action(name, kind, 0, List.of(), List.of())); }

        String action(Action action) {
            if (actions.size() >= 12) throw new IllegalStateException("Too many active dialog actions");
            String nonce = UUID.randomUUID().toString();
            actions.put(nonce, action);
            return PREFIX + ":" + id + ":" + nonce;
        }

        void clearActions() { actions.clear(); }

        int count() { return selected.size() + proposed.size(); }

        List<LineupPayload.Entry> entries() {
            var entries = new ArrayList<LineupPayload.Entry>();
            selected.values().forEach(choice -> entries.add(new LineupPayload.Entry(choice.driverId(), choice.psnId(), null)));
            entries.addAll(proposed);
            return entries;
        }

        @Override public String toString() { return "LineupSession[transient]"; }
    }

    record Action(String name, PickGatewayInteraction.Kind kind, int page, List<LineupTarget> targets, List<LineupChoice> choices) {
        Action { targets = List.copyOf(targets); choices = List.copyOf(choices); }
    }
}
