package org.ctc.discord.picks;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickPrivateMessageReuseTest {
    final InteractionHook hook = mock(InteractionHook.class);
    final WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class, RETURNS_SELF);
    final ReplyCallbackAction reply = mock(ReplyCallbackAction.class, RETURNS_SELF);
    final MessageEditCallbackAction update = mock(MessageEditCallbackAction.class, RETURNS_SELF);

    @ParameterizedTest
    @ValueSource(strings = {"button", "select", "modal"})
    void givenPrivateSource_whenContinuingPicks_thenAcknowledgeUpdateAndReplaceItsContentAndControls(String kind) {
        var event = event(kind, true);
        when(((IMessageEditCallback) event).deferEdit()).thenReturn(update);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        var pending = new JdaPickGatewayResponse(event, true).deferPrivate().toCompletableFuture();
        var accepted = ArgumentCaptor.forClass(Consumer.class);
        verify(update).queue(accepted.capture(), any());
        accepted.getValue().accept(hook);

        pending.join().replyPrivate(new PickGatewayMessage("Next private step",
                List.of(new PickGatewayMessage.Button("ctc-picks:next", "Continue")), null));

        verify(event, never()).deferReply(anyBoolean());
        verify(hook).editOriginal("Next private step");
        verify(edit).setAllowedMentions(List.of());
        var controls = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(edit).setComponents(controls.capture());
        assertThat(controls.getValue()).hasSize(1);
        verify(edit).queue(any(), any());
        verify(hook, never()).sendMessage(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"button", "select", "modal", "missing-modal"})
    void givenPublicOrMissingSource_whenContinuingPicks_thenCreateANewPrivateReplyWithoutEditingSource(String kind) {
        var event = event(kind, false);
        when(event.deferReply(true)).thenReturn(reply);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        var pending = new JdaPickGatewayResponse(event, true).deferPrivate().toCompletableFuture();
        var accepted = ArgumentCaptor.forClass(Consumer.class);
        verify(reply).queue(accepted.capture(), any());
        accepted.getValue().accept(hook);

        pending.join().replyPrivate("Private failure");

        verify((IMessageEditCallback) event, never()).deferEdit();
        verify(event).deferReply(true);
        verify(hook).editOriginal("Private failure");
        verify(edit).setComponents(List.of());
        verify(edit).setAllowedMentions(List.of());
        verify(hook, never()).sendMessage(anyString());
    }

    @Test
    void givenSlashCommand_whenRenderingChooser_thenFillThePrivateInitialReplyWithoutAnotherMessage() {
        var event = mock(SlashCommandInteractionEvent.class);
        when(event.deferReply(true)).thenReturn(reply);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        var pending = new JdaPickGatewayResponse(event, true).deferPrivate().toCompletableFuture();
        var accepted = ArgumentCaptor.forClass(Consumer.class);
        verify(reply).queue(accepted.capture(), any());
        accepted.getValue().accept(hook);

        pending.join().replyPrivate(new PickGatewayMessage("Choose a home race", List.of(), null));

        verify(event).deferReply(true);
        verify(hook).editOriginal("Choose a home race");
        verify(edit).setComponents(List.of());
        verify(edit).setAllowedMentions(List.of());
        verify(hook, never()).sendMessage(anyString());
    }

    @Test
    void givenOldControls_whenShowingTextOnlyFailure_thenRemoveAllControlsFromPrivateSource() {
        var event = event("button", true);
        when(((IMessageEditCallback) event).deferEdit()).thenReturn(update);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        var pending = new JdaPickGatewayResponse(event, true).deferPrivate().toCompletableFuture();
        var accepted = ArgumentCaptor.forClass(Consumer.class);
        verify(update).queue(accepted.capture(), any());
        accepted.getValue().accept(hook);

        pending.join().replyPrivate("This dialog expired. Use /picks again.");

        verify(edit).setComponents(List.of());
        verify(hook, never()).sendMessage(anyString());
    }

    @Test
    void givenLongOutput_whenRenderingAllChunks_thenRetainCompleteTextAndOnlyPutControlsOnFinalChunk() {
        var event = mock(SlashCommandInteractionEvent.class);
        when(event.deferReply(true)).thenReturn(reply);
        when(hook.editOriginal(anyString())).thenReturn(edit);
        var followup = mock(WebhookMessageCreateAction.class, RETURNS_SELF);
        when(hook.sendMessage(anyString())).thenReturn(followup);
        var pending = new JdaPickGatewayResponse(event, true).deferPrivate().toCompletableFuture();
        var accepted = ArgumentCaptor.forClass(Consumer.class);
        verify(reply).queue(accepted.capture(), any());
        accepted.getValue().accept(hook);
        String content = "x".repeat(4100);
        var button = new PickGatewayMessage.Button("ctc-picks:confirm", "Confirm");

        PickInteractionViews.send(pending.join(), content, List.of(button), null);

        var first = ArgumentCaptor.forClass(String.class);
        var rest = ArgumentCaptor.forClass(String.class);
        verify(hook).editOriginal(first.capture());
        verify(hook, times(2)).sendMessage(rest.capture());
        assertThat(first.getValue() + String.join("", rest.getAllValues())).isEqualTo(content);
        verify(edit).setComponents(List.of());
        verify(followup, times(2)).setEphemeral(true);
        verify(followup, times(2)).setAllowedMentions(List.of());
        var controls = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(followup).setComponents(controls.capture());
        assertThat(controls.getValue()).hasSize(1);
    }

    @Test
    void givenFailedEditAcknowledgement_whenContinuing_thenFailPrivatelyWithoutReplyOrSensitiveException() {
        var event = event("modal", true);
        when(((IMessageEditCallback) event).deferEdit()).thenReturn(update);
        var pending = new JdaPickGatewayResponse(event, true).deferPrivate().toCompletableFuture();
        var rejected = ArgumentCaptor.forClass(Consumer.class);
        verify(update).queue(any(), rejected.capture());

        rejected.getValue().accept(new IllegalStateException("private callback token"));

        assertThatThrownBy(pending::join).isInstanceOf(CompletionException.class)
                .hasMessageNotContaining("token").hasCauseInstanceOf(IllegalStateException.class);
        verify(event, never()).deferReply(anyBoolean());
        verifyNoInteractions(hook);
    }

    private IReplyCallback event(String kind, boolean ephemeral) {
        var message = mock(Message.class);
        when(message.isEphemeral()).thenReturn(ephemeral);
        return switch (kind) {
            case "button" -> {
                var event = mock(ButtonInteractionEvent.class);
                when(event.getMessage()).thenReturn(message);
                yield event;
            }
            case "select" -> {
                var event = mock(StringSelectInteractionEvent.class);
                when(event.getMessage()).thenReturn(message);
                yield event;
            }
            default -> {
                var event = mock(ModalInteractionEvent.class);
                when(event.getMessage()).thenReturn(kind.equals("missing-modal") ? null : message);
                yield event;
            }
        };
    }
}
