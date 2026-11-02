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
}
