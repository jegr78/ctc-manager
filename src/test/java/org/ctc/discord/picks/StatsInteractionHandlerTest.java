package org.ctc.discord.picks;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StatsInteractionHandlerTest {
    final java.time.Clock clock = java.time.Clock.fixed(PickGatewayReplyTest.NOW, java.time.ZoneOffset.UTC);
    final PickGatewayResponse response = mock(PickGatewayResponse.class);

    @Test
    void givenFoundation_whenCommandOrAutocompleteArrives_thenAnswerPrivatelyAndWithoutChoices() {
        // given
        var handler = new StatsInteractionHandler();
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.COMMAND), reply());
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE), reply());
        // then
        verify(response).replyPrivate("Stats are not available yet.");
        verify(response).replyChoices(List.of());
    }

    @Test
    void givenMoreThanTwentyFiveChoices_whenAnsweringAutocomplete_thenSendOnlyTheFirstTwentyFive() {
        // given
        var choices = IntStream.range(0, 30).mapToObj(i -> new PickGatewayModal.Option("Test " + i, "v" + i)).toList();
        // when
        reply().replyChoices(choices);
        // then
        verify(response).replyChoices(choices.subList(0, 25));
    }

    private PickGatewayReply reply() { return new PickGatewayReply(response, clock, PickGatewayReplyTest.NOW, new AtomicLong()::get, Runnable::run); }

    private PickGatewayInteraction event(PickGatewayInteraction.Kind kind) {
        return new PickGatewayInteraction("1", "100", "200", "300", kind, "stats", Map.of(), PickGatewayReplyTest.NOW, response,
                "driver", kind == PickGatewayInteraction.Kind.AUTOCOMPLETE ? "name" : null, "Test Member");
    }
}
