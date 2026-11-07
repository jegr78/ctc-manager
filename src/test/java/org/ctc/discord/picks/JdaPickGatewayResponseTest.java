package org.ctc.discord.picks;

import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdaPickGatewayResponseTest {
    final SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
    final ReplyCallbackAction initial = mock(ReplyCallbackAction.class, RETURNS_SELF);
    final InteractionHook hook = mock(InteractionHook.class);
    final JdaPickGatewayResponse response = new JdaPickGatewayResponse(event);

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"LEGACY,false", "LEGACY,true", "CTC_2027,false", "CTC_2027,true"})
    void givenSavedRaceRules_whenSerializingProductionModal_thenUseRequiredSingleSelectsAndKeepNumericDefaults(
            org.ctc.domain.model.LobbyRulesProfile profile, boolean oneMake) {
        // given
        var action = mock(ModalCallbackAction.class); when(event.replyModal(any())).thenReturn(action);
        var defaults = Map.of("laps", List.of("22"), "wear", List.of("7"),
                "consumption", List.of("6"), "refuel", List.of("10"));
        // when
        response.openModal(PickInteractionViews.modal("ctc-picks:opaque", "rules", profile, oneMake, defaults));
        // then
        var captured = ArgumentCaptor.forClass(Modal.class); verify(event).replyModal(captured.capture());
        var fields = captured.getValue().toData().getArray("components");
        assertThat(fields.length()).isEqualTo(profile == org.ctc.domain.model.LobbyRulesProfile.CTC_2027 && oneMake ? 5 : 4);
        assertThat(fields.getObject(0).getObject("component").getInt("type")).isEqualTo(4);
        assertThat(fields.getObject(0).getObject("component").getString("value")).isEqualTo("22");
        for (int index = 1; index <= 3; index++) {
            String id = List.of("wear", "consumption", "refuel").get(index - 1);
            var input = fields.getObject(index).getObject("component");
            assertThat(input.getString("custom_id")).isEqualTo(id);
            assertThat(input.getInt("type")).isEqualTo(3);
            assertThat(input.getInt("min_values")).isEqualTo(1);
            assertThat(input.getInt("max_values")).isEqualTo(1);
            var options = input.getArray("options");
            assertThat(options.length()).isEqualTo(Integer.parseInt(defaults.get(id).getFirst()));
            int selected = 0;
            for (int option = 0; option < options.length(); option++) {
                var value = options.getObject(option);
                if (value.getBoolean("default", false)) {
                    assertThat(value.getString("value")).isEqualTo(defaults.get(id).getFirst());
                    selected++;
                }
            }
            assertThat(selected).isEqualTo(1);
        }
        verify(action).queue(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rules", "fuel", "mandatory", "time", "search-entry", "search-car", "search-track"})
    void givenWizardStage_whenBuildingProductionModal_thenHonorSdkLimitsAndSerializeActualFieldTypes(String stage) {
        // given
        var action = mock(ModalCallbackAction.class); when(event.replyModal(any())).thenReturn(action);
        // when
        response.openModal(PickInteractionViews.modal("ctc-picks:opaque", stage));
        // then
        var captured = ArgumentCaptor.forClass(Modal.class); verify(event).replyModal(captured.capture());
        assertThat(captured.getValue().getComponents().size()).isBetween(1, 5);
        String json = captured.getValue().toData().toString();
        assertThat(json).contains("ctc-picks:opaque");
        if (stage.equals("fuel")) assertThat(json).contains("DEFAULT", "max_values", "available").doesNotContain("mandatory");
        if (stage.equals("mandatory")) assertThat(json).contains("None", "mandatory");
        if (stage.equals("time")) assertThat(json).contains("Equal Conditions", "0x", "weather", "daytime");
        verify(event, never()).deferReply(anyBoolean());
    }

    @Test
    void givenPrivateInteractiveReply_whenSending_thenSerializeBoundedButtonsAndMenusWithExplicitPrivacy() {
        // given
        when(event.deferReply(true)).thenReturn(initial);
        var followup = mock(WebhookMessageCreateAction.class, RETURNS_SELF);
        when(hook.sendMessage("Test private chooser")).thenReturn(followup);
        var future = response.deferPrivate().toCompletableFuture();
        var acknowledged = ArgumentCaptor.forClass(Consumer.class);
        verify(initial).queue(acknowledged.capture(), any());
        acknowledged.getValue().accept(hook);
        // when
        future.join().replyPrivate(new PickGatewayMessage("Test private chooser",
                List.of(new PickGatewayMessage.Button("ctc-picks:opaque-button", "Search")),
                new PickGatewayMessage.Selection("ctc-picks:opaque-select", "Choose track",
                        List.of(new PickGatewayModal.Option("Test track", "opaque-choice", true)))));
        // then
        var rows = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(followup).setComponents(rows.capture());
        assertThat(rows.getValue()).hasSize(2);
        var payload = new net.dv8tion.jda.api.utils.messages.MessageCreateBuilder().setContent("Test private chooser")
                .setComponents(rows.getValue()).build().toData().toString();
        assertThat(payload).contains("ctc-picks:opaque-button", "ctc-picks:opaque-select", "opaque-choice", "Test track", "\"default\":true");
        verify(followup).setEphemeral(true); verify(followup).setAllowedMentions(List.of());
        verify(followup).queue(any(), any());
    }

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
                new PickGatewayModal.Selection("weather", "Weather", List.of(new PickGatewayModal.Option("Dry", "dry", true)), 1, 1)));
        // when
        response.openModal(modal);
        // then
        var captured = ArgumentCaptor.forClass(Modal.class);
        verify(event).replyModal(captured.capture());
        var payload = captured.getValue().toData().toString();
        assertThat(payload).contains("ctc-picks:opaque", "notes", "Test notes", "weather", "dry", "\"default\":true");
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
