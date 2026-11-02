package org.ctc.discord.picks;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.PickAccessService;
import org.ctc.domain.service.PickSessionEpoch;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickGatewayConfigurationTest {
    final PickAccessService access = mock(PickAccessService.class);
    final PickGatewayResponse response = mock(PickGatewayResponse.class);
    final PickGatewayResponse.Continuation continuation = mock(PickGatewayResponse.Continuation.class);

    @Test
    void givenInitialCommandBeforeWizardDelivery_whenHandling_thenDeferPrivatelyBeforeCurrentChannelAuthorization() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        var handler = new PickGatewayConfiguration().pickGatewayInteractionHandler(access);
        // when
        handler.onInteraction(event(), reply());
        // then
        var order = inOrder(response, access, continuation);
        order.verify(response).deferPrivate();
        order.verify(access).authorizeChannel("100", "200", "300");
        order.verify(continuation).replyPrivate("Private picks are not available yet.");
        verify(response, never()).replyPrivate(anyString());
    }

    @Test
    void givenUnboundOrRevokedChannel_whenHandling_thenReplyWithPrivateGenericDenial() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        when(access.authorizeChannel("100", "200", "300")).thenThrow(new BusinessRuleException("Test private metadata"));
        var handler = new PickGatewayConfiguration().pickGatewayInteractionHandler(access);
        // when
        handler.onInteraction(event(), reply());
        // then
        verify(continuation).replyPrivate("Picks are unavailable in this channel.");
        verify(continuation, never()).replyPrivate(contains("metadata"));
    }

    PickGatewayReply reply() {
        return new PickGatewayReply(response, Clock.fixed(PickGatewayReplyTest.NOW, ZoneOffset.UTC), PickGatewayReplyTest.NOW,
                new PickSessionEpoch(), Runnable::run);
    }
    PickGatewayInteraction event() {
        return new PickGatewayInteraction("opaque", "100", "200", "300", PickGatewayInteraction.Kind.COMMAND,
                "picks", Map.of(), PickGatewayReplyTest.NOW, response);
    }
}
