package org.ctc.discord.picks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.ctc.domain.model.Season;
import org.ctc.domain.service.DriverStatsService;
import org.ctc.domain.service.StatsSeasonService;
import org.ctc.domain.stats.DriverStats;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.StatsScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StatsInteractionHandlerTest {
    final java.time.Clock clock = java.time.Clock.fixed(PickGatewayReplyTest.NOW, java.time.ZoneOffset.UTC);
    final PickGatewayResponse response = mock(PickGatewayResponse.class);
    final PickGatewayResponse.Continuation continuation = mock(PickGatewayResponse.Continuation.class);
    final DriverStatsService drivers = mock(DriverStatsService.class);
    final StatsSeasonService seasons = mock(StatsSeasonService.class);
    final java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
    final StatsInteractionHandler handler = new StatsInteractionHandler(drivers, seasons, nanos::get);

    @Test
    void givenDriverCommand_whenFiguresExist_thenAnswerWithAPrivateEscapedEmbed() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        var season = new Season("CTC *Spring*", 2026, 1);
        when(drivers.stats("Test_driver", StatsScope.SEASON, "s1")).thenReturn(new DriverStats(UUID.randomUUID(), "Test_*driver*", "Test",
                new StatsPeriod(season, List.of(season), false), 4, 62, 412, 12, 3, 5, 2, 1, 1, 3.44, 1, 1, 1, List.of("Test @everyone"), 0));
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.COMMAND, "driver", null,
                Map.of("name", List.of("Test_driver"), "scope", List.of("season"), "season", List.of("s1"))), reply());
        // then
        var sent = ArgumentCaptor.forClass(PickGatewayMessage.class);
        verify(continuation).replyPrivate(sent.capture());
        var embed = sent.getValue().embed();
        assertThat(embed.title()).as("markdown in names is escaped").isEqualTo("Test\\_\\*driver\\*");
        assertThat(embed.description()).isEqualTo("2026 \\| \\#1 \\| CTC \\*Spring\\*");
        assertThat(embed.fields()).extracting(PickGatewayMessage.Field::value).contains("Rank 4 of 62 · 412 pts",
                "12 races · 3 wins · 5 podiums · 2 poles · 1 fastest lap", "Best P1 · Average P3.4", "412 total · 34.3 per race",
                "1 guest race · 1 DSQ");
        assertThat(embed.fields().getFirst().value()).doesNotContain("@everyone").contains("@​everyone");
    }

    @Test
    void givenNoActiveSeason_whenRenderingAlltimeFallback_thenSayItAndListTheLastTeams() {
        // when
        var embed = StatsEmbeds.driver(new DriverStats(UUID.randomUUID(), "Test", "Test", new StatsPeriod(null, List.of(), true),
                0, 0, 0, 1, 0, 0, 0, 0, 0, null, 0, 1, 2, List.of("Test A", "Test B", "Test C"), 2));
        // then
        assertThat(embed.description()).isEqualTo("Alltime · no season is active");
        assertThat(embed.fields()).extracting(PickGatewayMessage.Field::value)
                .contains("Test A, Test B, Test C +2", "Best - · Average -", "1 DSQ · 2 seasons");
        assertThat(embed.fields()).extracting(PickGatewayMessage.Field::name).doesNotContain("Rank");
    }

    @Test
    void givenUnknownDriver_whenAnswering_thenReplyWithTheLookupMessage() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        when(drivers.stats(any(), any(StatsScope.class), any())).thenThrow(new StatsLookupException("No driver found"));
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.COMMAND, "driver", null, Map.of("name", List.of("Test"))), reply());
        // then
        verify(continuation).replyPrivate("No driver found");
    }

    @Test
    void givenUnexpectedFailure_whenAnswering_thenReplyWithTheStatsFailureText() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        when(drivers.stats(any(), any(StatsScope.class), any())).thenThrow(new IllegalStateException("Test database failure"));
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.COMMAND, "driver", null, Map.of("name", List.of("Test"))), reply());
        // then
        verify(continuation).replyPrivate(StatsInteractionHandler.FAILED);
    }

    @Test
    void givenRunningAnswer_whenAnotherArrives_thenSayBusyUntilTheClaimExpires() {
        // given
        when(response.deferPrivate()).thenReturn(new CompletableFuture<>());
        var command = event(PickGatewayInteraction.Kind.COMMAND, "driver", null, Map.of("name", List.of("Test")));
        handler.onInteraction(command, reply());
        // when
        var busy = mock(PickGatewayResponse.class);
        handler.onInteraction(command, new PickGatewayReply(busy, clock, PickGatewayReplyTest.NOW, new AtomicLong()::get, Runnable::run));
        nanos.addAndGet(30_000_000_000L);
        handler.onInteraction(command, reply());
        // then
        verify(busy).replyPrivate(StatsInteractionHandler.BUSY);
        verify(response, times(2)).deferPrivate();
    }

    @Test
    void givenFinishedAnswer_whenAnotherArrives_thenComputeItAgain() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        when(drivers.stats(any(), any(StatsScope.class), any())).thenThrow(new StatsLookupException("No driver found"));
        var command = event(PickGatewayInteraction.Kind.COMMAND, "driver", null, Map.of("name", List.of("Test")));
        // when
        handler.onInteraction(command, reply());
        handler.onInteraction(command, reply());
        // then
        verify(continuation, times(2)).replyPrivate("No driver found");
    }

    @Test
    void givenTeamCommand_whenAnswering_thenSayTeamStatsAreNotAvailableYet() {
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.COMMAND, "team", null, Map.of()), reply());
        // then
        verify(response).replyPrivate("Team stats are not available yet.");
    }

    @Test
    void givenAutocomplete_whenFocusingNameOrSeason_thenOfferMatchingChoices() {
        // given
        when(drivers.choices("tes", StatsScope.CURRENT, null)).thenReturn(List.of(new StatsChoice("Test driver", "d1")));
        when(seasons.choices("20")).thenReturn(List.of(new StatsChoice("2026 | #1 | CTC", "s1")));
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE, "driver", "name", Map.of("name", List.of("tes"), "scope", List.of("bogus"))), reply());
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE, "driver", "season", Map.of("season", List.of("20"))), reply());
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE, "team", "name", Map.of()), reply());
        // then
        verify(response).replyChoices(List.of(new PickGatewayModal.Option("Test driver", "d1")));
        verify(response).replyChoices(List.of(new PickGatewayModal.Option("2026 | #1 | CTC", "s1")));
        verify(response).replyChoices(List.of());
    }

    @Test
    void givenFailingLookup_whenAnsweringAutocomplete_thenOfferNoChoices() {
        // given
        when(drivers.choices(any(), any(), any())).thenThrow(new IllegalStateException("Test failure"));
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE, "driver", "name", Map.of()), reply());
        // then
        verify(response).replyChoices(List.of());
    }

    @Test
    void givenMoreThanTwentyFiveChoices_whenAnsweringAutocomplete_thenSendOnlyTheFirstTwentyFive() {
        // given
        var choices = IntStream.range(0, 30).mapToObj(i -> new PickGatewayModal.Option("Test " + i, "v" + i)).toList();
        // when
        reply().replyChoices(choices);
        // then
        verify(response).replyChoices(choices.subList(0, 25));
    }

    private PickGatewayReply reply() { return new PickGatewayReply(response, clock, PickGatewayReplyTest.NOW, new AtomicLong()::get, Runnable::run); }

    private PickGatewayInteraction event(PickGatewayInteraction.Kind kind, String subcommand, String focused, Map<String, List<String>> values) {
        return new PickGatewayInteraction("1", "100", "200", "300", kind, "stats", values, PickGatewayReplyTest.NOW, response,
                subcommand, focused, "Test Member");
    }
}
