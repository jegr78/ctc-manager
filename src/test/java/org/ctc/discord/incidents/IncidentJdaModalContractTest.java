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
