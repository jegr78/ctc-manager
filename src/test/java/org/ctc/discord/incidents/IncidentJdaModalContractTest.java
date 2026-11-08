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
    static final java.util.UUID DRIVER = java.util.UUID.randomUUID();
    static org.ctc.domain.incidents.IncidentDriverChoices choices(String label) {
        return new org.ctc.domain.incidents.IncidentDriverChoices(java.util.UUID.randomUUID(), "f".repeat(64), java.util.List.of(
                new org.ctc.domain.incidents.IncidentDriverChoices.DriverChoice(DRIVER, java.util.UUID.randomUUID(), label, true)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {4000, 4001, 20000})
    void givenConfirmedIncidentText_whenSerializingDefaults_thenPreserveSupportedValuesAndKeepOversizedTextOutOfSdkValues(int length) {
        // given
        var saved = new org.ctc.domain.model.IncidentPayload("Test ä🚗", "00:00", "d".repeat(length), "");
        var form = IncidentInteractionViews.form("ctc-incidents:session:action", saved, choices(saved.getInvolvedDrivers()), java.util.Set.of(DRIVER));
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
        var driverMenu = data.getObject(0).getObject("component");
        assertThat(driverMenu.getInt("type")).isEqualTo(3);
        assertThat(driverMenu.getArray("options").getObject(0).getBoolean("default")).isTrue();
        assertThat(data.getObject(2).getObject("component").getString("value", ""))
                .isEqualTo(length <= 4000 ? saved.getDescription() : "");
        verify(callback).queue(any(), any()); verify(event, never()).deferReply(anyBoolean());
    }

    @Test
    void givenPrivateIncidentFields_whenBuildingTheActualSdkModal_thenPreserveAllFourFieldsAndTheirLimits() {
        var event = mock(SlashCommandInteractionEvent.class); var callback = mock(ModalCallbackAction.class);
        when(event.replyModal(any())).thenReturn(callback);
        var response = new JdaPickGatewayResponse(event);
        response.openModal(IncidentInteractionViews.form("ctc-incidents:opaque-session:opaque-action", null, choices("Test driver"), java.util.Set.of()));
        var modal = ArgumentCaptor.forClass(Modal.class); verify(event).replyModal(modal.capture());
        assertThat(modal.getValue().getComponents()).hasSize(4);
        String payload = modal.getValue().toData().toString();
        assertThat(payload).contains("drivers", "replay", "description", "links", "min_values", "max_values", "200", "4000");
        verify(callback).queue(any(), any()); verify(event, never()).deferReply(anyBoolean());
    }
    @Test
    void givenMoreThan25Drivers_whenSerializing_thenUseTwoOptionalMenusWithIndependentDefaults() {
        var drivers = java.util.stream.IntStream.range(0, 32).mapToObj(index ->
                new org.ctc.domain.incidents.IncidentDriverChoices.DriverChoice(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Test driver " + index, index < 16)).toList();
        var choices = new org.ctc.domain.incidents.IncidentDriverChoices(java.util.UUID.randomUUID(), "f".repeat(64), drivers);
        var selected = java.util.Set.of(drivers.getFirst().id(), drivers.getLast().id());
        var event = mock(SlashCommandInteractionEvent.class); var callback = mock(ModalCallbackAction.class);
        when(event.replyModal(any())).thenReturn(callback);
        new JdaPickGatewayResponse(event).openModal(IncidentInteractionViews.form("ctc-incidents:opaque:action", null, choices, selected));
        var modal = ArgumentCaptor.forClass(Modal.class); verify(event).replyModal(modal.capture());
        var components = modal.getValue().toData().getArray("components");
        assertThat(components.length()).isEqualTo(5);
        for (int index = 0; index < 2; index++) {
            var menu = components.getObject(index).getObject("component");
            assertThat(menu.getInt("min_values")).isZero();
            assertThat(menu.getInt("max_values")).isEqualTo(16);
            assertThat(menu.getBoolean("required")).isFalse();
            long checked = java.util.stream.IntStream.range(0, 16).filter(option -> menu.getArray("options").getObject(option).getBoolean("default", false)).count();
            assertThat(checked).isEqualTo(1);
        }
    }
}
