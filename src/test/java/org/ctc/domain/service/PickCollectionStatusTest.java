package org.ctc.domain.service;

import org.ctc.discord.picks.DiscordPicksGateway;
import org.ctc.discord.picks.PickGatewayDispatcher;
import org.ctc.discord.picks.PickGatewaySettings;
import org.ctc.discord.picks.PickGatewayTransport;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PickCollectionStatusTest {
    @Test
    void givenDeliveredPicksCapability_whenUsingDefaultConfiguration_thenPermitPrivateRestoreWithoutStartingAConsumer() {
        var collection = new PickCollectionStatus();
        var settings = new PickGatewaySettings();
        var configs = mock(DiscordGlobalConfigRepository.class);
        var transport = mock(PickGatewayTransport.class);
        var dispatcher = mock(PickGatewayDispatcher.class);
        var gateway = new DiscordPicksGateway(settings, collection, configs, transport, "", dispatcher);

        assertThat(collection.isConfidentialityReady()).isTrue();
        assertThat(collection.isAvailable()).isTrue();
        gateway.start();

        assertThat(settings.isEnabled()).isFalse();
        assertThat(gateway.isRunning()).isFalse();
        assertThat(gateway.health()).isEqualTo(DiscordPicksGateway.Health.DISABLED);
        verifyNoInteractions(configs, transport, dispatcher);
    }
}
