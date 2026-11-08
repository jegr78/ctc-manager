package org.ctc.discord.incidents;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import org.ctc.discord.picks.PickGatewayInteraction;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentReporterView;
import org.ctc.domain.model.IncidentParty;
import org.ctc.domain.model.IncidentPayload;

public final class IncidentDraftSessionStore {
    private final Clock clock;
    private final LongSupplier epoch;
    private final int capacity;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public IncidentDraftSessionStore(Clock clock, LongSupplier epoch, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Draft capacity must be positive");
        this.clock = clock; this.epoch = epoch; this.capacity = capacity;
    }
    synchronized Session create(PickGatewayInteraction event, IncidentReporterView view) {
        sessions.values().removeIf(session -> !live(session));
        if (sessions.size() >= capacity) throw new BusinessRuleException("Incident dialogs are busy; try again shortly");
        var session = new Session(event, view, epoch.getAsLong(), clock.instant().plusSeconds(1800));
        sessions.put(session.id, session); return session;
    }
    <T> T withAction(PickGatewayInteraction event, BiFunction<Session, Action, T> work) {
        if (event == null || event.action() == null) throw unavailable();
        String[] parts = event.action().split(":", -1); Session session;
        try {
            if (parts.length != 3 || !"ctc-incidents".equals(parts[0])) throw unavailable();
            synchronized (this) { session = sessions.get(UUID.fromString(parts[1])); }
        } catch (IllegalArgumentException invalid) { throw unavailable(); }
        if (session == null || !session.lock.tryLock()) throw unavailable();
        try {
            var action = session.actions.get(parts[2]);
            if (!live(session) || !Objects.equals(session.guild, event.guildId()) || !Objects.equals(session.channel, event.channelId())
                    || !Objects.equals(session.user, event.userId()) || action == null || action.kind() != event.kind()) throw unavailable();
            return work.apply(session, action);
        } finally { session.lock.unlock(); }
    }
    private boolean live(Session session) { return session.generation == epoch.getAsLong() && clock.instant().isBefore(session.expires); }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("This incident dialog is unavailable; use /incidents to start again"); }

    static final class Session {
        final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        final UUID id = UUID.randomUUID();
        final UUID contextId, windowId;
        final String guild, channel, user;
        final long generation;
        final Instant expires;
        final Map<String, Action> actions = new HashMap<>();
        UUID request = UUID.randomUUID();
        UUID raceId, reportId;
        long windowVersion, reportVersion;
        IncidentParty party;
        IncidentPayload payload;
        org.ctc.domain.incidents.IncidentDriverChoices driverChoices;
        java.util.Set<UUID> selectedDrivers = java.util.Set.of();
        String label, reportingParty, lineupNotice;
        Session(PickGatewayInteraction event, IncidentReporterView view, long generation, Instant expires) {
            guild = event.guildId(); channel = event.channelId(); user = event.userId();
            contextId = view.contextId(); windowId = view.windowId(); this.generation = generation; this.expires = expires;
        }
        String action(String name, PickGatewayInteraction.Kind kind) { return action(new Action(name, kind, List.of(), 0)); }
        String action(Action action) {
            for (var entry : actions.entrySet()) if (entry.getValue().equals(action)) return "ctc-incidents:" + id + ":" + entry.getKey();
            if (actions.size() >= 16) throw new IllegalStateException("Too many active incident actions");
            String nonce = UUID.randomUUID().toString(); actions.put(nonce, action); return "ctc-incidents:" + id + ":" + nonce;
        }
        void clearActions() { actions.clear(); }
        @Override public String toString() { return "IncidentDraftSession[transient]"; }
    }
    record Action(String name, PickGatewayInteraction.Kind kind, List<UUID> ids, int page) {
        Action { ids = List.copyOf(ids); }
    }
}
