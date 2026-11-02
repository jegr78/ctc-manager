package org.ctc.discord.picks;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdaPickGatewayResponseTest {
    final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    final ReplyCallbackAction initial = mock(ReplyCallbackAction.class, RETURNS_SELF);
    final InteractionHook hook = mock(InteractionHook.class);
    final JdaPickGatewayResponse response = new JdaPickGatewayResponse(event);

    @Test
    void givenDeferredReply_whenSdkAcknowledges_thenOnlyPrivateMentionFreeFollowupsAreAvailable() {
        // given
        when(event.deferReply(anyBoolean())).thenReturn(initial);
        var followup = mock(WebhookMessageCreateAction.class, RETURNS_SELF);
        when(hook.sendMessage("Test private payload")).thenReturn(followup);
        // when
        var future = response.deferPrivate().toCompletableFuture();
        // then
        assertThat(future).isNotDone();
        var success = ArgumentCaptor.forClass(Consumer.class);
        verify(initial).queue(success.capture(), any());
        success.getValue().accept(hook);
        future.join().replyPrivate("Test private payload");
        verify(event).deferReply(true);
        verify(event, never()).deferReply(false);
        verify(hook).setEphemeral(true);
        verify(followup).setEphemeral(true);
        verify(followup).setAllowedMentions(List.of());
        verify(followup).queue(any(), any());
        verify(event, never()).deferReply();
        verify(event, never()).reply(anyString());
    }

    @Test
    void givenFailedAcknowledgement_whenSdkFails_thenExposeGenericFailureWithoutTokenOrPayload() {
        // given
        when(event.deferReply(anyBoolean())).thenReturn(initial);
        // when
        var future = response.deferPrivate().toCompletableFuture();
        var failure = ArgumentCaptor.forClass(Consumer.class);
        verify(initial).queue(any(), failure.capture());
        failure.getValue().accept(new IllegalStateException("secret callback URI and payload"));
        // then
        assertThatThrownBy(future::join).isInstanceOf(CompletionException.class)
                .hasMessageNotContaining("secret").hasCauseInstanceOf(IllegalStateException.class);
        assertThat(future.handle((value, error) -> error.getCause()).join()).isNull();
        verifyNoInteractions(hook);
    }

    @Test
    void givenImmediatePrivateError_whenReplying_thenExplicitlySetEphemeralAndSuppressMentions() {
        // given
        when(event.reply("Test error")).thenReturn(initial);
        // when
        response.replyPrivate("Test error");
        // then
        verify(initial).setEphemeral(true);
        verify(initial).setAllowedMentions(List.of());
        verify(initial).queue(any(), any());
        verify(event, never()).deferReply(anyBoolean());
    }

    @Test
    void givenModalWithTextAndSelection_whenOpening_thenSendImmediateSupportedComponents() {
        // given
        var action = mock(ModalCallbackAction.class);
        when(event.replyModal(any())).thenReturn(action);
        var modal = new PickGatewayModal("ctc-picks:opaque", "Private choice", List.of(
                new PickGatewayModal.Text("notes", "Notes", true, "Test notes", 0, 200, false),
                new PickGatewayModal.Selection("weather", "Weather", List.of(new PickGatewayModal.Option("Dry", "dry")), 1, 1)));
        // when
        response.openModal(modal);
        // then
        var captured = ArgumentCaptor.forClass(Modal.class);
        verify(event).replyModal(captured.capture());
        var payload = captured.getValue().toData().toString();
        assertThat(payload).contains("ctc-picks:opaque", "notes", "Test notes", "weather", "dry");
        assertThat(captured.getValue().getComponents()).hasSize(2);
        verify(action).queue(any(), any());
        verify(event, never()).deferReply(anyBoolean());
    }

    @Test
    void givenModalSubmission_whenTryingToOpenAnotherModal_thenRefuseUnsupportedResponse() {
        // given
        var submission = mock(ModalInteractionEvent.class);
        var submitted = new JdaPickGatewayResponse(submission);
        // when / then
        assertThatThrownBy(() -> submitted.openModal(new PickGatewayModal("ctc-picks:next", "Next", List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessage("This interaction cannot open a modal");
        verifyNoInteractions(submission);
    }
}
