package org.ctc.discord.incidents;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.ctc.discord.picks.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentReporterView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentDraftSessionStoreTest {
    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    final Clock clock = mock(Clock.class);
    final AtomicLong epoch = new AtomicLong();
    final IncidentDraftSessionStore store = new IncidentDraftSessionStore(clock, epoch::get, 1);
    final IncidentReporterView view = new IncidentReporterView(UUID.randomUUID(), UUID.randomUUID(), 1, true, "Test pairing", List.of(), List.of());

    @BeforeEach void setUp() { when(clock.instant()).thenReturn(NOW); }

    @Test
    void givenAuthorizedTransientDraft_whenResolvingOpaqueAction_thenKeepItsScopeAndRequestWithoutPrivateTextInIds() {
        var session = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> store.create(event("incidents", PickGatewayInteraction.Kind.COMMAND, "300", "200", "100"), view));
        var id = session.action("open-report", PickGatewayInteraction.Kind.BUTTON);
        assertThat(id).matches("ctc-incidents:[a-f0-9-]{36}:[a-f0-9-]{36}").hasSizeLessThanOrEqualTo(100);
        var request = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> store.withAction(event(id, PickGatewayInteraction.Kind.BUTTON, "300", "200", "100"), (saved, action) -> saved.request));
        assertThat(request).isEqualTo(session.request); assertThat(session.contextId).isEqualTo(view.contextId());
        assertThat(session.toString()).doesNotContain("300", "Test pairing");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "channel", "guild", "kind", "nonce", "prefix", "expired", "generation"})
    void givenWrongScopeOrExpiredDraft_whenResolving_thenDenyBeforeTheOperation(String changed) {
        var session = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> store.create(event("incidents", PickGatewayInteraction.Kind.COMMAND, "300", "200", "100"), view));
        String id = session.action("open-report", PickGatewayInteraction.Kind.BUTTON);
        if (changed.equals("expired")) when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
        if (changed.equals("generation")) epoch.incrementAndGet();
        if (changed.equals("nonce")) id = "ctc-incidents:" + session.id + ":" + UUID.randomUUID();
        if (changed.equals("prefix")) id = id.replace("ctc-incidents:", "ctc-picks:");
        var attempted = event(id, changed.equals("kind") ? PickGatewayInteraction.Kind.MODAL : PickGatewayInteraction.Kind.BUTTON,
                changed.equals("user") ? "999" : "300", changed.equals("channel") ? "999" : "200", changed.equals("guild") ? "999" : "100");
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        assertThatThrownBy(() -> store.withAction(attempted, (saved, action) -> { called.set(true); return true; })).isInstanceOf(BusinessRuleException.class);
        assertThat(called).isFalse();
    }

    @Test
    void givenNewStageInvalidatingOldActions_whenReplayingOldButton_thenDenyInsteadOfConfirmingAnotherDraft() {
        var session = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> store.create(event("incidents", PickGatewayInteraction.Kind.COMMAND, "300", "200", "100"), view));
        String old = session.action("confirm-submit", PickGatewayInteraction.Kind.BUTTON); session.clearActions();
        session.action("open-report", PickGatewayInteraction.Kind.BUTTON);
        assertThatThrownBy(() -> store.withAction(event(old, PickGatewayInteraction.Kind.BUTTON, "300", "200", "100"), (saved, action) -> true)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenFullDraftCapacity_whenAnotherDraftArrives_thenDenyWithoutReplacingTheExistingDraft() {
        var first = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> store.create(event("incidents", PickGatewayInteraction.Kind.COMMAND, "300", "200", "100"), view));
        assertThatThrownBy(() -> store.create(event("incidents", PickGatewayInteraction.Kind.COMMAND, "301", "200", "100"), view)).isInstanceOf(BusinessRuleException.class);
        var action = first.action("open-report", PickGatewayInteraction.Kind.BUTTON);
        UUID savedId = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> store.withAction(event(action, PickGatewayInteraction.Kind.BUTTON, "300", "200", "100"), (saved, selected) -> saved.id));
        assertThat(savedId).isEqualTo(first.id);
    }

    static PickGatewayInteraction event(String action, PickGatewayInteraction.Kind kind, String user, String channel, String guild) {
        return new PickGatewayInteraction(UUID.randomUUID().toString(), guild, channel, user, kind, action, Map.of(), NOW, mock(PickGatewayResponse.class));
    }
}
