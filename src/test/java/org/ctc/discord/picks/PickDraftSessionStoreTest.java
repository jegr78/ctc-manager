package org.ctc.discord.picks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PickDraftSessionStoreTest {
    final MutableClock clock = new MutableClock();
    final AtomicLong epoch = new AtomicLong();
    final PickDraftSessionStore store = new PickDraftSessionStore(clock, epoch::get, 2);

    @Test
    void givenBoundSession_whenResolvingToken_thenAcceptOnlyExactActorChannelKindAndNonce() {
        // given
        var session = store.create(event("100", "200", "300", "picks", PickGatewayInteraction.Kind.COMMAND));
        var token = session.action("view", PickGatewayInteraction.Kind.BUTTON);
        // when / then
        assertThat(store.<String>withAction(event("100", "200", "300", token, PickGatewayInteraction.Kind.BUTTON), (s, a) -> a.name())).isEqualTo("view");
        for (var denied : new PickGatewayInteraction[] {
                event("100", "200", "301", token, PickGatewayInteraction.Kind.BUTTON),
                event("100", "201", "300", token, PickGatewayInteraction.Kind.BUTTON),
                event("101", "200", "300", token, PickGatewayInteraction.Kind.BUTTON),
                event("100", "200", "300", token, PickGatewayInteraction.Kind.MODAL),
                event("100", "200", "300", token + "x", PickGatewayInteraction.Kind.BUTTON)}) {
            assertThatThrownBy(() -> store.withAction(denied, (s, a) -> a.name())).hasMessageContaining("expired");
        }
        assertThat(session.toString()).doesNotContain("300", "200");
    }

    @Test
    void givenThirtyMinuteLifetime_whenAtBoundaryOrAfterRestore_thenInvalidateWithoutExtendingDeadline() {
        // given
        var session = store.create(event("100", "200", "300", "picks", PickGatewayInteraction.Kind.COMMAND));
        var token = session.action("view", PickGatewayInteraction.Kind.BUTTON);
        clock.now = clock.now.plusSeconds(1799);
        assertThat(store.<Boolean>withAction(event("100", "200", "300", token, PickGatewayInteraction.Kind.BUTTON), (s, a) -> true)).isTrue();
        // when / then
        clock.now = clock.now.plusSeconds(1);
        assertThatThrownBy(() -> store.withAction(event("100", "200", "300", token, PickGatewayInteraction.Kind.BUTTON), (s, a) -> true)).hasMessageContaining("expired");
        var fresh = store.create(event("100", "200", "300", "picks", PickGatewayInteraction.Kind.COMMAND));
        var freshToken = fresh.action("view", PickGatewayInteraction.Kind.BUTTON);
        epoch.incrementAndGet();
        assertThatThrownBy(() -> store.withAction(event("100", "200", "300", freshToken, PickGatewayInteraction.Kind.BUTTON), (s, a) -> true)).hasMessageContaining("expired");
    }

    @Test
    void givenBoundedStore_whenFull_thenRefuseNewDraftUntilExpiredEntriesAreRemoved() {
        // given
        store.create(event("100", "200", "300", "picks", PickGatewayInteraction.Kind.COMMAND));
        store.create(event("100", "200", "301", "picks", PickGatewayInteraction.Kind.COMMAND));
        // when / then
        assertThatThrownBy(() -> store.create(event("100", "200", "302", "picks", PickGatewayInteraction.Kind.COMMAND))).hasMessageContaining("busy");
        clock.now = clock.now.plusSeconds(1800);
        assertThat(store.create(event("100", "200", "302", "picks", PickGatewayInteraction.Kind.COMMAND))).isNotNull();
    }

    @Test
    void givenAdvancedDialog_whenOldComponentIsDelivered_thenRejectRetiredRoute() {
        // given
        var session = store.create(event("100", "200", "300", "picks", PickGatewayInteraction.Kind.COMMAND));
        var token = session.action("view", PickGatewayInteraction.Kind.BUTTON);
        // when
        session.clearActions();
        // then
        assertThatThrownBy(() -> store.withAction(event("100", "200", "300", token, PickGatewayInteraction.Kind.BUTTON), (s, a) -> true))
                .hasMessageContaining("expired");
    }

    @Test
    void givenInFlightSessionWork_whenAnotherInteractionArrives_thenRejectImmediatelyWithoutBlockingAcknowledgement() throws Exception {
        // given
        var session = store.create(event("100", "200", "300", "picks", PickGatewayInteraction.Kind.COMMAND));
        var token = session.action("view", PickGatewayInteraction.Kind.BUTTON);
        var event = event("100", "200", "300", token, PickGatewayInteraction.Kind.BUTTON);
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> store.withAction(event, (s, a) -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                return true;
            }));
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            // when
            var second = executor.submit(() -> {
                try { store.withAction(event, (s, a) -> true); return "accepted"; }
                catch (org.ctc.domain.exception.BusinessRuleException busy) { return "rejected"; }
            });
            String result;
            try { result = second.get(200, java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch (java.util.concurrent.TimeoutException blocked) { result = "blocked"; }
            // then
            assertThat(result).isEqualTo("rejected");
            release.countDown(); assertThat(first.get(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    static PickGatewayInteraction event(String guild, String channel, String user, String action, PickGatewayInteraction.Kind kind) {
        return new PickGatewayInteraction("1000", guild, channel, user, kind, action, Map.of(), Instant.parse("2026-11-02T12:00:00Z"), null);
    }
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-02T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
