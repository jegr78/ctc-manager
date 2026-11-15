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
import org.ctc.domain.service.TeamStatsService;
import org.ctc.sitegen.SiteProfileLinks;
import org.ctc.domain.stats.DriverStats;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.StatsScope;
import org.ctc.domain.stats.TeamStats;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StatsInteractionHandlerTest {
    final java.time.Clock clock = java.time.Clock.fixed(PickGatewayReplyTest.NOW, java.time.ZoneOffset.UTC);
    final PickGatewayResponse response = mock(PickGatewayResponse.class);
    final PickGatewayResponse.Continuation continuation = mock(PickGatewayResponse.Continuation.class);
    final DriverStatsService drivers = mock(DriverStatsService.class);
    final StatsSeasonService seasons = mock(StatsSeasonService.class);
    final TeamStatsService teams = mock(TeamStatsService.class);
    final SiteProfileLinks links = mock(SiteProfileLinks.class);
    final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(1);
    final StatsInteractionHandler handler = new StatsInteractionHandler(drivers, teams, seasons, links, slots);

    @Test
    void givenDriverCommand_whenFiguresExist_thenAnswerWithAPrivateEscapedEmbed() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        var season = new Season("CTC *Spring*", 2026, 1);
        season.setId(UUID.randomUUID());
        when(drivers.stats("Test_driver", StatsScope.SEASON, "s1")).thenReturn(new DriverStats(UUID.randomUUID(), "Test_*driver*", "Test",
                StatsPeriod.of(season, List.of(season), false), 4, 62, 412, 12, 3, 5, 2, 1, 1, 3.44, 1, 1, 1, List.of("Test @everyone"), 0, "2026 | #1 | CTC *Spring*"));
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
        assertThat(embed.url()).as("without a public base URL the title carries no link").isNull();
        var share = sent.getValue().buttons().getFirst();
        assertThat(share.label()).isEqualTo("Share in channel");
        assertThat(share.id()).startsWith(StatsInteractionHandler.SHARE + "d:").hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void givenTeamCommand_whenFiguresExist_thenAnswerWithTheTeamEmbedAndItsProfileLink() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        var season = new Season("CTC", 2026, 1);
        season.setId(UUID.randomUUID());
        var team = new TeamStats(UUID.randomUUID(), "Test_Alpha", StatsPeriod.of(season, List.of(season), false), 3, 10, "Group A", 8, 5, 1, 2,
                400, 350, 16, 16, "Semifinal", 0, null, 0, 0, List.of(new TeamStats.TopDriver("Test_one", 120)), "2026 | #1 | CTC");
        when(teams.stats("Test_Alpha", StatsScope.SEASON, season.getId().toString())).thenReturn(team);
        when(links.team(team.teamId(), "2026 | #1 | CTC")).thenReturn("https://example.test/season/2026-1-ctc/team/t-alf.html");
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.COMMAND, "team", null, Map.of("name", List.of("Test_Alpha"),
                "scope", List.of("season"), "season", List.of(season.getId().toString()))), reply());
        // then
        var sent = ArgumentCaptor.forClass(PickGatewayMessage.class);
        verify(continuation).replyPrivate(sent.capture());
        var embed = sent.getValue().embed();
        assertThat(embed.title()).isEqualTo("Test\\_Alpha");
        assertThat(embed.url()).isEqualTo("https://example.test/season/2026-1-ctc/team/t-alf.html");
        assertThat(embed.fields()).extracting(PickGatewayMessage.Field::value).contains("Rank 3 of 10 · Group A",
                "8 matches · 5 wins · 1 draw · 2 losses · 63% won", "400 scored · 350 conceded · +50 difference · 16 match points",
                "16 races", "Semifinal", "Test\\_one 120 pts");
        assertThat(sent.getValue().buttons().getFirst().id())
                .isEqualTo(StatsInteractionHandler.SHARE + "t:" + team.teamId() + ":s:" + season.getId());
    }

    @Test
    void givenShareClick_whenTheEntityExists_thenConfirmPrivatelyAndPostVisiblyWithoutAButton() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        var id = UUID.randomUUID();
        when(drivers.exists(id)).thenReturn(true);
        when(drivers.stats(id.toString(), StatsScope.ALLTIME, null)).thenReturn(new DriverStats(id, "Test", "Test",
                StatsPeriod.of(null, List.of(), false), 1, 1, 10, 1, 1, 1, 0, 0, 1, 1.0, 0, 0, 1, List.of(), 0, null));
        // when
        handler.onInteraction(button(StatsInteractionHandler.SHARE + "d:" + id + ":a:-"), reply());
        // then
        var sent = ArgumentCaptor.forClass(PickGatewayMessage.class);
        var order = inOrder(continuation);
        order.verify(continuation).replyPrivate("Sharing in this channel.");
        order.verify(continuation).postVisible(sent.capture());
        assertThat(sent.getValue().content()).as("the poster is named without a mention").isEqualTo("Shared by Test Member");
        assertThat(sent.getValue().buttons()).isEmpty();
        assertThat(sent.getValue().embed().title()).isEqualTo("Test");
    }

    @Test
    void givenShareClickForARemovedEntityOrVanishedSeason_whenClicking_thenSayNoLongerAvailableOnlyPrivately() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        var removed = UUID.randomUUID();
        var team = UUID.randomUUID();
        var season = UUID.randomUUID();
        when(teams.exists(team)).thenReturn(true);
        when(teams.stats(team.toString(), StatsScope.SEASON, season.toString())).thenThrow(new StatsLookupException("No season found"));
        // when
        handler.onInteraction(button(StatsInteractionHandler.SHARE + "t:" + removed + ":c:-"), reply());
        handler.onInteraction(button(StatsInteractionHandler.SHARE + "t:" + team + ":s:" + season), reply());
        // then
        verify(continuation, times(2)).replyPrivate(StatsInteractionHandler.GONE);
        verify(continuation, never()).postVisible(any());
        verify(drivers, never()).exists(any());
    }

    @Test
    void givenMalformedShareId_whenClicking_thenAnswerPrivatelyWithoutWork() {
        // given
        var malformed = StatsInteractionHandler.SHARE + "t:" + UUID.randomUUID() + ":s:-";
        // when
        handler.onInteraction(button(malformed), reply());
        // then
        verify(response).replyPrivate(StatsInteractionHandler.GONE);
        verify(response, never()).deferPrivate();
        verifyNoInteractions(teams);
    }

    @Test
    void givenLongestShareId_whenEncodingAndParsing_thenStayWithinDiscordLimitsAndRoundTrip() {
        // given
        var season = new Season("CTC", 2026, 1);
        season.setId(UUID.randomUUID());
        var entity = UUID.randomUUID();
        // when
        String id = StatsInteractionHandler.ShareRequest.id(false, entity, StatsPeriod.of(season, List.of(season), false));
        var parsed = StatsInteractionHandler.ShareRequest.parse(id);
        // then
        assertThat(id).hasSizeLessThanOrEqualTo(100);
        assertThat(parsed).isEqualTo(new StatsInteractionHandler.ShareRequest(false, entity, "season", season.getId().toString()));
        assertThat(StatsInteractionHandler.ShareRequest.parse(StatsInteractionHandler.SHARE + "x:" + entity + ":a:-")).isNull();
        assertThat(StatsInteractionHandler.ShareRequest.id(true, entity, StatsPeriod.of(null, List.of(season), true)))
                .as("an alltime fallback shares alltime").isEqualTo(StatsInteractionHandler.SHARE + "d:" + entity + ":a:-");
        assertThat(StatsInteractionHandler.ShareRequest.parse(StatsInteractionHandler.SHARE + "d:" + entity + ":c:-"))
                .as("buttons posted before the season was encoded still parse").isEqualTo(new StatsInteractionHandler.ShareRequest(true, entity, "current", null));
    }

    @Test
    void givenNoActiveSeason_whenRenderingAlltimeFallback_thenSayItAndListTheLastTeams() {
        // when
        var embed = StatsEmbeds.driver(new DriverStats(UUID.randomUUID(), "Test", "Test", StatsPeriod.of(null, List.of(), true),
                0, 0, 0, 1, 0, 0, 0, 0, 0, null, 0, 1, 2, List.of("Test A", "Test B", "Test C"), 2, null), null);
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
    void givenLookupMessageWithMarkdown_whenAnswering_thenEscapeIt() {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        when(drivers.stats(any(), any(StatsScope.class), any())).thenThrow(new StatsLookupException("No results for Test_Max_Power in 2026 | #1 | CTC"));
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.COMMAND, "driver", null, Map.of("name", List.of("Test"))), reply());
        // then
        verify(continuation).replyPrivate("No results for Test\\_Max\\_Power in 2026 \\| \\#1 \\| CTC");
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
    void givenAllSlotsBusy_whenAnotherArrives_thenSayBusyAndFreeTheSlotWhenTheAcknowledgementFails() {
        // given
        var pending = new CompletableFuture<PickGatewayResponse.Continuation>();
        when(response.deferPrivate()).thenReturn(pending);
        var command = event(PickGatewayInteraction.Kind.COMMAND, "driver", null, Map.of("name", List.of("Test")));
        handler.onInteraction(command, reply());
        // when
        var busy = mock(PickGatewayResponse.class);
        handler.onInteraction(command, new PickGatewayReply(busy, clock, PickGatewayReplyTest.NOW, new AtomicLong()::get, Runnable::run));
        pending.completeExceptionally(new IllegalStateException("Test acknowledgement failure"));
        // then
        verify(busy).replyPrivate(StatsInteractionHandler.BUSY);
        assertThat(slots.availablePermits()).as("a skipped answer frees its slot at once").isEqualTo(1);
    }

    @Test
    void givenInteractionTooOldToAcknowledge_whenAnswering_thenReturnTheSlotExactlyOnce() {
        // given
        var command = event(PickGatewayInteraction.Kind.COMMAND, "driver", null, Map.of("name", List.of("Test")));
        // when
        for (int attempt = 0; attempt < 2; attempt++) {
            handler.onInteraction(command, new PickGatewayReply(response, clock, PickGatewayReplyTest.NOW.minusSeconds(5), new AtomicLong()::get, Runnable::run));
        }
        // then
        assertThat(slots.availablePermits()).as("an unacknowledged answer releases its slot once").isEqualTo(1);
        verify(response, never()).deferPrivate();
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
    void givenAutocomplete_whenFocusingNameOrSeason_thenOfferMatchingChoices() {
        // given
        when(drivers.choices("tes", StatsScope.CURRENT, null)).thenReturn(List.of(new StatsChoice("Test driver", "d1")));
        when(seasons.choices("20")).thenReturn(List.of(new StatsChoice("2026 | #1 | CTC", "s1")));
        when(teams.choices("alp", StatsScope.CURRENT, null)).thenReturn(List.of(new StatsChoice("Test Alpha (T-ALF)", "t1")));
        // when
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE, "driver", "name", Map.of("name", List.of("tes"), "scope", List.of("bogus"))), reply());
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE, "driver", "season", Map.of("season", List.of("20"))), reply());
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE, "team", "name", Map.of("name", List.of("alp")))
                , reply());
        handler.onInteraction(event(PickGatewayInteraction.Kind.AUTOCOMPLETE, "team", "scope", Map.of()), reply());
        // then
        verify(response).replyChoices(List.of(new PickGatewayModal.Option("Test driver", "d1")));
        verify(response).replyChoices(List.of(new PickGatewayModal.Option("2026 | #1 | CTC", "s1")));
        verify(response).replyChoices(List.of(new PickGatewayModal.Option("Test Alpha (T-ALF)", "t1")));
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

    private PickGatewayInteraction button(String action) {
        return new PickGatewayInteraction("1", "100", "200", "300", PickGatewayInteraction.Kind.BUTTON, action, Map.of(), PickGatewayReplyTest.NOW,
                response, null, null, "Test Member");
    }

    private PickGatewayInteraction event(PickGatewayInteraction.Kind kind, String subcommand, String focused, Map<String, List<String>> values) {
        return new PickGatewayInteraction("1", "100", "200", "300", kind, "stats", values, PickGatewayReplyTest.NOW, response,
                subcommand, focused, "Test Member");
    }
}
