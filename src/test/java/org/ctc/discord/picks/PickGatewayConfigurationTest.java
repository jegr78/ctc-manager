package org.ctc.discord.picks;

import java.time.Clock;
import java.time.ZoneOffset;
import org.ctc.domain.service.PickSessionEpoch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PickGatewayConfigurationTest {
    @Test
    void givenConfiguredDraftStore_whenRestoreCommits_thenInvalidateOldActions() {
        // given
        var epoch = new PickSessionEpoch();
        var store = new PickGatewayConfiguration().pickDraftSessionStore(Clock.fixed(PickGatewayReplyTest.NOW, ZoneOffset.UTC), epoch);
        var session = store.create(PickDraftSessionStoreTest.event("100", "200", "300", "picks", PickGatewayInteraction.Kind.COMMAND));
        var action = session.action("view", PickGatewayInteraction.Kind.BUTTON);
        // when
        epoch.afterRestore(null);
        // then
        assertThatThrownBy(() -> store.withAction(PickDraftSessionStoreTest.event("100", "200", "300", action, PickGatewayInteraction.Kind.BUTTON), (s, a) -> true))
                .hasMessageContaining("expired");
    }

    @Test
    void givenConfiguredDispatcher_whenLineupArrives_thenRouteToLineupHandlerOnly() {
        // given
        var picks = org.mockito.Mockito.mock(PickInteractionHandler.class);
        var incidents = org.mockito.Mockito.mock(org.ctc.discord.incidents.IncidentInteractionHandler.class);
        var lineups = org.mockito.Mockito.mock(LineupInteractionHandler.class);
        var settings = PickGatewayDispatcherTest.settings();
        var dispatcher = new PickGatewayConfiguration().pickGatewayDispatcher(settings, picks, incidents, lineups,
                new org.ctc.backup.lock.ImportLockService(java.time.Duration.ofSeconds(1)), Clock.fixed(PickGatewayReplyTest.NOW, ZoneOffset.UTC), new PickSessionEpoch());
        dispatcher.start();
        try {
            // when
            dispatcher.accept("100", new PickGatewayInteraction("opaque", "100", "200", "300", PickGatewayInteraction.Kind.COMMAND, "lineup",
                    java.util.Map.of(), PickGatewayReplyTest.NOW, org.mockito.Mockito.mock(PickGatewayResponse.class)));
            // then
            org.mockito.Mockito.verify(lineups).onInteraction(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.verifyNoInteractions(picks, incidents);
        } finally {
            dispatcher.stop();
        }
    }
}
