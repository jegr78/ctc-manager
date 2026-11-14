package org.ctc.discord.picks;

import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscordPicksGatewayTest {
    final PickGatewaySettings settings = new PickGatewaySettings();
    final DiscordGlobalConfigRepository configs = mock(DiscordGlobalConfigRepository.class);
    final PickGatewayTransport transport = mock(PickGatewayTransport.class);
    final PickGatewayTransport.Session session = mock(PickGatewayTransport.Session.class);
    final PickGatewayDispatcher dispatcher = mock(PickGatewayDispatcher.class);
    DiscordPicksGateway gateway;

    @BeforeEach
    void setUp() {
        // given
        var config = new DiscordGlobalConfig(); config.setGuildId("100000000000000001");
        when(configs.findFirstByOrderByIdAsc()).thenReturn(config);
        when(transport.connect(anyString(), anyString(), any())).thenReturn(session);
        when(session.state()).thenReturn(PickGatewayTransport.ConnectionState.CONNECTED);
        gateway = new DiscordPicksGateway(settings, configs, transport, "test-gateway-token", dispatcher);
    }

    @Test
    void givenDefaultSettings_whenStarting_thenCreateNoSessionAndReadNoDiscordConfiguration() {
        // when
        gateway.start();
        // then
        assertThat(gateway.isRunning()).isFalse();
        assertThat(gateway.health()).isEqualTo(DiscordPicksGateway.Health.DISABLED);
        verifyNoInteractions(transport, configs);
    }

    @Test
    void givenReadyOptIn_whenStartingTwice_thenConnectOneSessionWithExistingIdentity() {
        // given
        settings.setEnabled(true);
        // when
        gateway.start(); gateway.start();
        // then
        assertThat(gateway.isRunning()).isTrue();
        assertThat(gateway.health()).isEqualTo(DiscordPicksGateway.Health.CONNECTED);
        verify(transport, times(1)).connect(eq("test-gateway-token"), eq("100000000000000001"), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-token", "missing-config", "invalid-guild"})
    void givenIncompleteIdentity_whenStarting_thenRefuseConnectionWithoutExposingToken(String cause) {
        // given
        settings.setEnabled(true);
        if (cause.equals("missing-token")) gateway = new DiscordPicksGateway(settings, configs, transport, " ", dispatcher);
        if (cause.equals("missing-config")) when(configs.findFirstByOrderByIdAsc()).thenReturn(null);
        if (cause.equals("invalid-guild")) { var config = new DiscordGlobalConfig(); config.setGuildId("spoofed"); when(configs.findFirstByOrderByIdAsc()).thenReturn(config); }
        // when
        gateway.start();
        // then
        assertThat(gateway.isRunning()).isFalse();
        assertThat(gateway.health()).isEqualTo(DiscordPicksGateway.Health.MISCONFIGURED);
        verifyNoInteractions(transport);
    }

    @Test
    void givenConnectedConsumer_whenStoppingTwice_thenCloseOnlyOwnedSessionOnce() {
        // given
        settings.setEnabled(true); gateway.start();
        // when
        gateway.stop(); gateway.stop();
        // then
        assertThat(gateway.isRunning()).isFalse();
        assertThat(gateway.health()).isEqualTo(DiscordPicksGateway.Health.STOPPED);
        verify(session, times(1)).close();
    }

    @Test
    void givenTransportReconnect_whenInspectingHealth_thenReportSdkStateWithoutAnotherConsumer() {
        // given
        settings.setEnabled(true); gateway.start();
        when(session.state()).thenReturn(PickGatewayTransport.ConnectionState.RECONNECTING);
        // when / then
        assertThat(gateway.health()).isEqualTo(DiscordPicksGateway.Health.RECONNECTING);
        assertThat(gateway.isRunning()).isTrue();
        verify(transport, times(1)).connect(anyString(), anyString(), any());
    }

    @Test
    void givenTransportFailure_whenStarting_thenReturnGenericHealthWithoutLeakingExceptionOrIdentity() {
        // given
        settings.setEnabled(true);
        when(transport.connect(anyString(), anyString(), any())).thenThrow(new IllegalStateException("test-gateway-token private payload"));
        // when / then
        assertThatCode(gateway::start).doesNotThrowAnyException();
        assertThat(gateway.isRunning()).isFalse();
        assertThat(gateway.health()).isEqualTo(DiscordPicksGateway.Health.FAILED);
        assertThat(gateway.toString()).doesNotContain("test-gateway-token", "private payload");
    }

    @Test
    void givenReadyConsumer_whenReceivingInteraction_thenForwardUsingConfiguredGuildAndStopOwnedWorkers() {
        // given
        settings.setEnabled(true);
        gateway.start();
        var receiver = org.mockito.ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(transport).connect(anyString(), anyString(), receiver.capture());
        var event = mock(PickGatewayInteraction.class);
        // when
        receiver.getValue().accept(event);
        gateway.stop();
        // then
        verify(dispatcher).start();
        verify(dispatcher).accept("100000000000000001", event);
        verify(dispatcher).stop();
    }
}
