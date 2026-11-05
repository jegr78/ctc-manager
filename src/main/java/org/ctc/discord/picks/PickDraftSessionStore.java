package org.ctc.discord.picks;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.LobbyRulesProfile;
import org.ctc.domain.picks.HomeRaceTarget;
import org.ctc.domain.picks.PickTeamView.Choice;

public final class PickDraftSessionStore {
    private final Clock clock;
    private final LongSupplier epoch;
    private final int capacity;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PickDraftSessionStore(Clock clock, LongSupplier epoch, int capacity) {
        this.clock = clock;
        this.epoch = epoch;
        if (capacity < 1) throw new IllegalArgumentException("Draft capacity must be positive");
        this.capacity = capacity;
    }

    synchronized Session create(PickGatewayInteraction event) {
        sessions.values().removeIf(session -> !live(session));
        if (sessions.size() >= capacity) throw new BusinessRuleException("Picks are busy. Try again shortly.");
        var session = new Session(event.guildId(), event.channelId(), event.userId(), epoch.getAsLong(), clock.instant().plusSeconds(1800));
        sessions.put(session.id, session);
        return session;
    }

    <T> T withAction(PickGatewayInteraction event, BiFunction<Session, Action, T> operation) {
        String[] parts = event.action().split(":", -1);
        Session session;
        try {
            if (parts.length != 3 || !"ctc-picks".equals(parts[0])) throw expired();
            synchronized (this) { session = sessions.get(UUID.fromString(parts[1])); }
        } catch (IllegalArgumentException invalid) { throw expired(); }
        if (session == null) throw expired();
        if (!session.lock.tryLock()) throw new BusinessRuleException("This dialog is busy. Check its current submission shortly.");
        try {
            var action = session.actions.get(parts[2]);
            if (!live(session) || !Objects.equals(session.guild, event.guildId()) || !Objects.equals(session.channel, event.channelId())
                    || !Objects.equals(session.user, event.userId()) || action == null || action.kind() != event.kind()) throw expired();
            return operation.apply(session, action);
        } finally { session.lock.unlock(); }
    }

    private boolean live(Session session) { return session.generation == epoch.getAsLong() && clock.instant().isBefore(session.expires); }
    private static BusinessRuleException expired() { return new BusinessRuleException("This dialog expired or was reset. Use /picks to start again."); }

    static final class Session {
        final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        final UUID id = UUID.randomUUID();
        final UUID request = UUID.randomUUID();
        final String guild;
        final String channel;
        final String user;
        final long generation;
        final Instant expires;
        final Map<String, Action> actions = new HashMap<>();
        final Map<String, List<String>> fields = new HashMap<>();
        UUID raceId;
        long version;
        String label;
        Choice track;
        Choice car;
        PickPayload payload;
        LobbyRulesProfile lobbyRulesProfile = LobbyRulesProfile.LEGACY;
        Session(String guild, String channel, String user, long generation, Instant expires) {
            this.guild = guild; this.channel = channel; this.user = user; this.generation = generation; this.expires = expires;
        }
        String action(String name, PickGatewayInteraction.Kind kind) { return action(new Action(name, kind, List.of(), List.of(), 0, "")); }
        String action(Action action) {
            if (actions.size() >= 12) throw new IllegalStateException("Too many active dialog actions");
            String nonce = UUID.randomUUID().toString(); actions.put(nonce, action);
            return "ctc-picks:" + id + ":" + nonce;
        }
        void clearActions() { actions.clear(); }
        @Override public String toString() { return "PickDraftSession[transient]"; }
    }
    record Action(String name, PickGatewayInteraction.Kind kind, List<Choice> choices, List<HomeRaceTarget> targets, int page, String query) {
        Action { choices = List.copyOf(choices); targets = List.copyOf(targets); }
    }
}
