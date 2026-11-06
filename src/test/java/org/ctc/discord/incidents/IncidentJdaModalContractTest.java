package org.ctc.discord.incidents;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import org.ctc.discord.picks.JdaPickGatewayResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentJdaModalContractTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {4000, 4001, 20000})
    void givenConfirmedIncidentText_whenSerializingDefaults_thenPreserveSupportedValuesAndKeepOversizedTextOutOfSdkValues(int length) {
        // given
        var saved = new org.ctc.domain.model.IncidentPayload("Test ä🚗", "00:00", "d".repeat(length), "");
        var form = IncidentInteractionViews.form("ctc-incidents:session:action", saved);
        var description = (org.ctc.discord.picks.PickGatewayModal.Text) form.fields().get(2);
        assertThat(description.value()).isEqualTo(length <= 4000 ? saved.getDescription() : null);
        assertThat(description.required()).isEqualTo(length <= 4000);
        var event = mock(SlashCommandInteractionEvent.class); var callback = mock(ModalCallbackAction.class);
        when(event.replyModal(any())).thenReturn(callback);
        // when
        new JdaPickGatewayResponse(event).openModal(form);
        // then
        var modal = ArgumentCaptor.forClass(Modal.class); verify(event).replyModal(modal.capture());
        var data = modal.getValue().toData().getArray("components");
        assertThat(data.length()).isEqualTo(4);
        assertThat(data.getObject(0).getObject("component").getString("value")).isEqualTo(saved.getInvolvedDrivers());
        assertThat(data.getObject(2).getObject("component").getString("value", ""))
                .isEqualTo(length <= 4000 ? saved.getDescription() : "");
        verify(callback).queue(any(), any()); verify(event, never()).deferReply(anyBoolean());
    }

    @Test
    void givenPrivateIncidentFields_whenBuildingTheActualSdkModal_thenPreserveAllFourFieldsAndTheirLimits() {
        var event = mock(SlashCommandInteractionEvent.class); var callback = mock(ModalCallbackAction.class);
        when(event.replyModal(any())).thenReturn(callback);
        var response = new JdaPickGatewayResponse(event);
        response.openModal(IncidentInteractionViews.form("ctc-incidents:opaque-session:opaque-action"));
        var modal = ArgumentCaptor.forClass(Modal.class); verify(event).replyModal(modal.capture());
        assertThat(modal.getValue().getComponents()).hasSize(4);
        String payload = modal.getValue().toData().toString();
        assertThat(payload).contains("drivers", "replay", "description", "links", "2000", "200", "4000");
        verify(callback).queue(any(), any()); verify(event, never()).deferReply(anyBoolean());
    }
}
