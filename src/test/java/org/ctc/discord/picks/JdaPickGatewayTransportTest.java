package org.ctc.discord.picks;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdaPickGatewayTransportTest {
    final OutboundPolicy outbound = mock(OutboundPolicy.class);
    final JdaPickGatewayTransport transport = new JdaPickGatewayTransport(outbound);
    final JDABuilder builder = mock(JDABuilder.class, RETURNS_SELF);
    final JDA jda = mock(JDA.class);
    final Consumer<PickGatewayInteraction> receiver = mock(Consumer.class);

    @Test
    void givenOutgoingSession_whenConnectingAndStopping_thenUseLightSdkWithoutMessageContentAndCloseOnlyOwnedClient() throws Exception {
        // given
        when(builder.build()).thenReturn(jda);
        when(jda.awaitShutdown(Duration.ofSeconds(5))).thenReturn(true);
        try (var factory = mockStatic(JDABuilder.class)) {
            factory.when(() -> JDABuilder.createLight(anyString(), any(java.util.Collection.class))).thenReturn(builder);
            // when
            var session = transport.connect("test-token", "100", receiver);
            // then
            assertThat(session).isNotNull();
            verify(outbound).requireEnabled();
            factory.verify(() -> JDABuilder.createLight("test-token", EnumSet.noneOf(GatewayIntent.class)));
            verify(builder).setAutoReconnect(true);
            verify(builder).setEnableShutdownHook(false);
            verify(jda, never()).updateCommands();
            session.close();
            verify(jda).shutdown();
            verify(jda).awaitShutdown(Duration.ofSeconds(5));
            verify(jda, never()).shutdownNow();
        }
    }

    @Test
    void givenSdkShutdownFailure_whenRetryingOwnedClose_thenRetryInsteadOfTreatingClientAsClosed() throws Exception {
        // given
        when(builder.build()).thenReturn(jda);
        when(jda.awaitShutdown(Duration.ofSeconds(5))).thenReturn(true);
        doThrow(new IllegalStateException("Test shutdown failed")).doNothing().when(jda).shutdown();
        try (var factory = mockStatic(JDABuilder.class)) {
            factory.when(() -> JDABuilder.createLight(anyString(), any(java.util.Collection.class))).thenReturn(builder);
            var session = transport.connect("test-token", "100", receiver);
            // when / then
            assertThatThrownBy(session::close).isInstanceOf(IllegalStateException.class);
            session.close();
            verify(jda, times(2)).shutdown();
            verify(jda).awaitShutdown(Duration.ofSeconds(5));
        }
    }

    @ParameterizedTest
    @CsvSource({"CONNECTED,CONNECTED", "WAITING_TO_RECONNECT,RECONNECTING", "ATTEMPTING_TO_RECONNECT,RECONNECTING",
            "SHUTDOWN,STOPPED", "FAILED_TO_LOGIN,STOPPED", "CONNECTING_TO_WEBSOCKET,CONNECTING"})
    void givenSdkConnectionState_whenReadingHealth_thenUseExistingSession(JDA.Status sdk, PickGatewayTransport.ConnectionState expected) {
        // given
        when(builder.build()).thenReturn(jda); when(jda.getStatus()).thenReturn(sdk);
        try (var factory = mockStatic(JDABuilder.class)) {
            factory.when(() -> JDABuilder.createLight(anyString(), any(java.util.Collection.class))).thenReturn(builder);
            var session = transport.connect("test-token", "100", receiver);
            // when / then
            assertThat(session).isNotNull();
            assertThat(session.state()).isEqualTo(expected);
            verify(builder, times(1)).build();
        }
    }

    @Test
    void givenRecoveryOutboundDisabled_whenConnecting_thenRejectBeforeCreatingSdk() throws Exception {
        // given
        doThrow(new org.ctc.integration.ExternalIntegrationDisabledException()).when(outbound).requireEnabled();
        try (var factory = mockStatic(JDABuilder.class)) {
            // when / then
            assertThatThrownBy(() -> transport.connect("test-token", "100", receiver))
                    .isInstanceOf(IllegalStateException.class).hasMessage("External integrations are disabled");
            factory.verifyNoInteractions();
        }
    }

    @Test
    void givenInteractionEvents_whenReceiving_thenMapCommandButtonSelectionAndModalWithoutRawToken() {
        // given
        when(builder.build()).thenReturn(jda);
        try (var factory = mockStatic(JDABuilder.class)) {
            factory.when(() -> JDABuilder.createLight(anyString(), any(java.util.Collection.class))).thenReturn(builder);
            transport.connect("test-token", "100", receiver);
            var captured = ArgumentCaptor.forClass(Object.class);
            verify(builder).addEventListeners(captured.capture());
            var listener = (ListenerAdapter) captured.getValue();
            var command = mock(SlashCommandInteractionEvent.class);
            var button = mock(ButtonInteractionEvent.class);
            var select = mock(StringSelectInteractionEvent.class);
            var modal = mock(ModalInteractionEvent.class);
            var guild = mock(Guild.class); when(guild.getId()).thenReturn("100");
            var user = mock(User.class); when(user.getId()).thenReturn("300");
            for (var interaction : List.of(command, button, select, modal)) {
                when(interaction.getId()).thenReturn("opaque-id"); when(interaction.getGuild()).thenReturn(guild);
                when(interaction.getChannelId()).thenReturn("200"); when(interaction.getUser()).thenReturn(user);
                when(interaction.getTimeCreated()).thenReturn(OffsetDateTime.ofInstant(PickGatewayReplyTest.NOW, ZoneOffset.UTC));
            }
            when(command.getName()).thenReturn("picks"); when(button.getComponentId()).thenReturn("ctc-picks:button");
            when(select.getComponentId()).thenReturn("ctc-picks:select"); when(select.getValues()).thenReturn(List.of("choice"));
            when(modal.getModalId()).thenReturn("ctc-picks:modal");
            var text = mock(ModalMapping.class); when(text.getCustomId()).thenReturn("notes");
            when(text.getType()).thenReturn(Component.Type.TEXT_INPUT); when(text.getAsString()).thenReturn("Test private notes");
            var options = mock(ModalMapping.class); when(options.getCustomId()).thenReturn("weather");
            when(options.getType()).thenReturn(Component.Type.STRING_SELECT); when(options.getAsStringList()).thenReturn(List.of("dry"));
            when(modal.getValues()).thenReturn(List.of(text, options));
            // when
            listener.onSlashCommandInteraction(command); listener.onButtonInteraction(button);
            listener.onStringSelectInteraction(select); listener.onModalInteraction(modal);
            // then
            var mapped = ArgumentCaptor.forClass(PickGatewayInteraction.class);
            verify(receiver, times(4)).accept(mapped.capture());
            assertThat(mapped.getAllValues()).extracting(PickGatewayInteraction::kind).containsExactly(
                    PickGatewayInteraction.Kind.COMMAND, PickGatewayInteraction.Kind.BUTTON,
                    PickGatewayInteraction.Kind.SELECT, PickGatewayInteraction.Kind.MODAL);
            var last = mapped.getValue();
            assertThat(last.guildId()).isEqualTo("100"); assertThat(last.channelId()).isEqualTo("200");
            assertThat(last.userId()).isEqualTo("300"); assertThat(last.createdAt()).isEqualTo(PickGatewayReplyTest.NOW);
            assertThat(last.values()).isEqualTo(Map.of("notes", List.of("Test private notes"), "weather", List.of("dry")));
            assertThat(mapped.getAllValues().get(2).values()).isEqualTo(Map.of("selection", List.of("choice")));
            assertThat(last.toString()).doesNotContain("test-token", "Test private notes");
            for (var interaction : List.of(command, button, select, modal)) verify(interaction, never()).getToken();
        }
    }
}
