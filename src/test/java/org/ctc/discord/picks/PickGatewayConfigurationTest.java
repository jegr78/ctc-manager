package org.ctc.discord.picks;

import java.time.Clock;
import java.time.ZoneOffset;
import org.ctc.domain.service.PickSessionEpoch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PickGatewayConfigurationTest {
    @Test
    void givenConfiguredDraftStore_whenRestoreCommits_thenInvalidateOldActions() {
        // given
        var epoch = new PickSessionEpoch();
        var store = new PickGatewayConfiguration().pickDraftSessionStore(Clock.fixed(PickGatewayReplyTest.NOW, ZoneOffset.UTC), epoch);
        var session = store.create(PickDraftSessionStoreTest.event("100", "200", "300", "picks", PickGatewayInteraction.Kind.COMMAND));
        var action = session.action("view", PickGatewayInteraction.Kind.BUTTON);
        // when
        epoch.afterRestore(null);
        // then
        assertThatThrownBy(() -> store.withAction(PickDraftSessionStoreTest.event("100", "200", "300", action, PickGatewayInteraction.Kind.BUTTON), (s, a) -> true))
                .hasMessageContaining("expired");
    }

    @Test
    void givenConfiguredDispatcher_whenLineupArrives_thenRouteToLineupHandlerOnly() {
        // given
        var routing = routing(true, false);
        // when
        routing.dispatch(PickGatewayInteraction.Kind.COMMAND, "lineup");
        // then
        org.mockito.Mockito.verify(routing.lineups).onInteraction(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(routing.picks, routing.incidents, routing.stats);
    }

    @Test
    void givenOnlyStatsEnabled_whenCommandsArrive_thenRouteStatsAndKeepPicksAndIncidentsInactive() {
        // given
        var routing = routing(false, true);
        // when
        var stats = routing.dispatch(PickGatewayInteraction.Kind.COMMAND, "stats");
        var picks = routing.dispatch(PickGatewayInteraction.Kind.COMMAND, "picks");
        var incidents = routing.dispatch(PickGatewayInteraction.Kind.COMMAND, "incidents");
        // then
        org.mockito.Mockito.verify(routing.stats).onInteraction(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(routing.picks, routing.incidents, routing.lineups);
        org.mockito.Mockito.verify(picks).replyPrivate("Picks are unavailable. Try again later.");
        org.mockito.Mockito.verify(incidents).replyPrivate("Incident reporting is unavailable. Try again later.");
        org.mockito.Mockito.verifyNoInteractions(stats);
    }

    @Test
    void givenNoStatsOnlyGateway_whenIncidentArrives_thenRouteItToTheIncidentHandler() {
        // given
        var routing = routing(false, false);
        // when
        routing.dispatch(PickGatewayInteraction.Kind.COMMAND, "incidents");
        // then
        org.mockito.Mockito.verify(routing.incidents).onInteraction(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(routing.picks, routing.lineups, routing.stats);
    }

    @Test
    void givenStatsDisabled_whenAutocompleteArrives_thenAnswerWithoutChoicesAndSkipTheHandler() {
        // given
        var routing = routing(true, false);
        // when
        var response = routing.dispatch(PickGatewayInteraction.Kind.AUTOCOMPLETE, "stats");
        // then
        org.mockito.Mockito.verify(response).replyChoices(java.util.List.of());
        org.mockito.Mockito.verifyNoInteractions(routing.stats);
    }

    @Test
    void givenStatsSwitchOnly_whenStartingConfiguredGateway_thenPassTheSwitch() {
        // given
        var picks = new PickGatewaySettings();
        var stats = new StatsGatewaySettings();
        stats.setEnabled(true);
        var configs = org.mockito.Mockito.mock(org.ctc.discord.repository.DiscordGlobalConfigRepository.class);
        // when
        var gateway = new PickGatewayConfiguration().discordPicksGateway(picks, stats, configs,
                org.mockito.Mockito.mock(PickGatewayTransport.class), "", org.mockito.Mockito.mock(PickGatewayDispatcher.class));
        gateway.start();
        // then
        org.assertj.core.api.Assertions.assertThat(gateway.health()).as("stats alone passes the switch and reaches the configuration check")
                .isEqualTo(DiscordPicksGateway.Health.MISCONFIGURED);
        stats.setEnabled(false);
        var disabled = new PickGatewayConfiguration().discordPicksGateway(picks, stats, configs,
                org.mockito.Mockito.mock(PickGatewayTransport.class), "", org.mockito.Mockito.mock(PickGatewayDispatcher.class));
        disabled.start();
        org.assertj.core.api.Assertions.assertThat(disabled.health()).isEqualTo(DiscordPicksGateway.Health.DISABLED);
    }

    private static Routing routing(boolean picksEnabled, boolean statsEnabled) {
        var settings = PickGatewayDispatcherTest.settings();
        settings.setEnabled(picksEnabled);
        var stats = new StatsGatewaySettings();
        stats.setEnabled(statsEnabled);
        var routing = new Routing(org.mockito.Mockito.mock(PickInteractionHandler.class), org.mockito.Mockito.mock(org.ctc.discord.incidents.IncidentInteractionHandler.class),
                org.mockito.Mockito.mock(LineupInteractionHandler.class), org.mockito.Mockito.mock(StatsInteractionHandler.class));
        routing.dispatcher = new PickGatewayConfiguration().pickGatewayDispatcher(settings, stats, routing.picks, routing.incidents, routing.lineups, routing.stats,
                new org.ctc.backup.lock.ImportLockService(java.time.Duration.ofSeconds(1)), Clock.fixed(PickGatewayReplyTest.NOW, ZoneOffset.UTC), new PickSessionEpoch());
        routing.dispatcher.start();
        return routing;
    }

    private static final class Routing {
        final PickInteractionHandler picks;
        final org.ctc.discord.incidents.IncidentInteractionHandler incidents;
        final LineupInteractionHandler lineups;
        final StatsInteractionHandler stats;
        PickGatewayDispatcher dispatcher;
        Routing(PickInteractionHandler picks, org.ctc.discord.incidents.IncidentInteractionHandler incidents, LineupInteractionHandler lineups, StatsInteractionHandler stats) {
            this.picks = picks; this.incidents = incidents; this.lineups = lineups; this.stats = stats;
        }
        PickGatewayResponse dispatch(PickGatewayInteraction.Kind kind, String action) {
            var response = org.mockito.Mockito.mock(PickGatewayResponse.class);
            try {
                dispatcher.accept("100", new PickGatewayInteraction("opaque", "100", "200", "300", kind, action,
                        java.util.Map.of(), PickGatewayReplyTest.NOW, response));
            } finally {
                dispatcher.stop();
                dispatcher.start();
            }
            return response;
        }
    }
}
