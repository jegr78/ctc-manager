package org.ctc.discord.picks;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.ctc.backup.lock.ImportLockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickGatewayDispatcherTest {
    static final String GUILD = "100000000000000001";
    final PickGatewaySettings settings = settings();
    final PickGatewayInteractionHandler handler = mock(PickGatewayInteractionHandler.class);
    final ImportLockService imports = new ImportLockService(Duration.ofSeconds(1));
    final AtomicLong epoch = new AtomicLong();
    final PickGatewayDispatcher dispatcher = new PickGatewayDispatcher(settings, handler, imports,
            Clock.fixed(PickGatewayReplyTest.NOW, ZoneOffset.UTC), epoch::get);
    final PickGatewayResponse response = mock(PickGatewayResponse.class);
    final PickGatewayResponse.Continuation continuation = mock(PickGatewayResponse.Continuation.class);

    @AfterEach
    void stop() { dispatcher.stop(); }

    @Test
    void givenConfiguredGuildCommand_whenDispatching_thenAcknowledgePrivatelyBeforeBoundedWork() throws Exception {
        // given
        when(response.deferPrivate()).thenReturn(CompletableFuture.completedFuture(continuation));
        var done = new CountDownLatch(1);
        doAnswer(invocation -> {
            ((PickGatewayReply) invocation.getArgument(1)).deferPrivate(result -> {
                result.replyPrivate("Test private processed"); done.countDown();
            }); return null;
        }).when(handler).onInteraction(any(), any());
        dispatcher.start();
        // when
        dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "picks"));
        // then
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        verify(response).deferPrivate();
        verify(continuation).replyPrivate("Test private processed");
        verify(response, never()).replyPrivate(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dm", "wrong-guild"})
    void givenOtherGuildOrDirectMessage_whenDispatching_thenDenyPrivatelyWithoutCallingFeature(String cause) {
        // given
        dispatcher.start();
        // when
        dispatcher.accept(GUILD, event(cause.equals("dm") ? null : "999", PickGatewayInteraction.Kind.COMMAND, "picks"));
        // then
        verify(response).replyPrivate("Picks are unavailable in this channel.");
        verifyNoInteractions(handler);
    }

    @Test
    void givenUnrelatedCommandOrComponent_whenDispatching_thenLeaveOtherFeaturesUntouched() {
        // given
        dispatcher.start();
        // when
        dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "unrelated"));
        dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.BUTTON, "unrelated:button"));
        // then
        verifyNoInteractions(handler, response);
    }

    @Test
    void givenRestoreHoldingAdmission_whenDispatching_thenRefuseBeforeFeatureWork() {
        // given
        dispatcher.start(); assertThat(imports.tryLock()).isTrue();
        try {
            // when
            dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "picks"));
            // then
            verify(response).replyPrivate("A backup restore is in progress. Try again later.");
            verifyNoInteractions(handler);
        } finally { imports.unlock(); }
    }

    @Test
    void givenSaturatedWorkQueue_whenThirdInteractionArrives_thenReplyBusyPrivately() throws Exception {
        // given
        when(response.deferPrivate()).thenAnswer(invocation -> CompletableFuture.completedFuture(continuation));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var busy = new CountDownLatch(1);
        doAnswer(invocation -> {
            if ("The bot is busy. Try again shortly.".equals(invocation.getArgument(0))) busy.countDown();
            return null;
        }).when(continuation).replyPrivate(anyString());
        doAnswer(invocation -> {
            ((PickGatewayReply) invocation.getArgument(1)).deferPrivate(result -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
            }); return null;
        }).when(handler).onInteraction(any(), any());
        dispatcher.start();
        try {
            dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "picks"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            // when
            dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "picks"));
            dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "picks"));
            // then
            assertThat(busy.await(2, TimeUnit.SECONDS)).isTrue();
            verify(response, never()).replyPrivate(anyString());
        } finally { release.countDown(); }
    }

    @Test
    void givenHandlerFailureBeforeAcknowledgement_whenDispatching_thenKeepDiagnosticPrivateAndGeneric() {
        // given
        doThrow(new IllegalStateException("secret token and private input")).when(handler).onInteraction(any(), any());
        dispatcher.start();
        // when / then
        assertThatCode(() -> dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "picks"))).doesNotThrowAnyException();
        verify(response).replyPrivate("This request could not be completed. Try again.");
    }

    @Test
    void givenStoppedDispatcher_whenReceivingInteraction_thenRefuseWithoutRunningFeature() {
        // given
        dispatcher.start(); dispatcher.stop();
        // when
        dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "picks"));
        // then
        verify(response).replyPrivate("Picks are unavailable. Try again later.");
        verifyNoInteractions(handler);
    }

    @Test
    void givenExpiredInitialWindow_whenDispatching_thenDropWithoutFeatureWorkOrLateCallback() {
        // given
        dispatcher.start();
        var event = event(GUILD, PickGatewayInteraction.Kind.COMMAND, "picks");
        var expired = new PickGatewayInteraction(event.id(), event.guildId(), event.channelId(), event.userId(), event.kind(),
                event.action(), event.values(), PickGatewayReplyTest.NOW.minusSeconds(3), response);
        // when
        dispatcher.accept(GUILD, expired);
        // then
        verifyNoInteractions(handler, response);
    }

    @Test
    void givenPrivateInputValues_whenFormattingInteraction_thenExcludeThemFromDiagnostics() {
        // given
        var event = event(GUILD, PickGatewayInteraction.Kind.MODAL, "ctc-picks:opaque");
        // when / then
        assertThat(event.toString()).doesNotContain("Test private weather", "ctc-picks:opaque");
    }

    @ParameterizedTest
    @ValueSource(strings = {"zero-workers", "too-many-workers", "zero-queue", "too-large-queue"})
    void givenInvalidResourceBounds_whenStarting_thenRefuseBeforeAllocatingWorkers(String invalid) {
        // given
        if (invalid.equals("zero-workers")) settings.setWorkers(0);
        if (invalid.equals("too-many-workers")) settings.setWorkers(9);
        if (invalid.equals("zero-queue")) settings.setQueueCapacity(0);
        if (invalid.equals("too-large-queue")) settings.setQueueCapacity(257);
        // when / then
        assertThatThrownBy(dispatcher::start).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(handler);
    }

    PickGatewayInteraction event(String guild, PickGatewayInteraction.Kind kind, String action) {
        return new PickGatewayInteraction("opaque-event", guild, "200000000000000002", "300000000000000003", kind,
                action, Map.of("weather", List.of("Test private weather")), PickGatewayReplyTest.NOW, response);
    }
    static PickGatewaySettings settings() {
        var settings = new PickGatewaySettings(); settings.setWorkers(1); settings.setQueueCapacity(1); return settings;
    }

    @ParameterizedTest
    @ValueSource(strings = {"command", "component"})
    void givenIncidentInteraction_whenDispatching_thenReuseTheSharedPrivateTransport(String entry) {
        dispatcher.start();
        dispatcher.accept(GUILD, event(GUILD, entry.equals("command") ? PickGatewayInteraction.Kind.COMMAND : PickGatewayInteraction.Kind.BUTTON,
                entry.equals("command") ? "incidents" : "ctc-incidents:opaque:nonce"));
        verify(handler).onInteraction(any(), any());
    }

    @Test
    void givenIncidentFromAnotherGuild_whenDispatching_thenDenyPrivatelyBeforeAnyFeature() {
        dispatcher.start();
        dispatcher.accept(GUILD, event("999", PickGatewayInteraction.Kind.COMMAND, "incidents"));
        verify(response).replyPrivate("Incident reporting is unavailable in this channel.");
        verifyNoInteractions(handler);
    }

    @ParameterizedTest
    @ValueSource(strings = {"command", "component"})
    void givenLineupInteraction_whenDispatching_thenReachTheFeatureHandler(String entry) {
        // given
        dispatcher.start();
        // when
        dispatcher.accept(GUILD, event(GUILD, entry.equals("command") ? PickGatewayInteraction.Kind.COMMAND : PickGatewayInteraction.Kind.BUTTON,
                entry.equals("command") ? "lineup" : "ctc-lineup:opaque:nonce"));
        // then
        verify(handler).onInteraction(any(), any());
    }

    @Test
    void givenLineupFromAnotherGuild_whenDispatching_thenDenyWithLineupWording() {
        // given
        dispatcher.start();
        // when
        dispatcher.accept(GUILD, event("999", PickGatewayInteraction.Kind.COMMAND, "lineup"));
        // then
        verify(response).replyPrivate("Lineups are unavailable in this channel.");
        verifyNoInteractions(handler);
    }

    @Test
    void whenClassifyingActions_thenRouteEachCommandAndReuseOnlyPicksAndLineupMessages() {
        // when / then
        assertThat(PickGatewayDispatcher.command("lineup")).isEqualTo("lineup");
        assertThat(PickGatewayDispatcher.command("ctc-lineup:a:b")).isEqualTo("lineup");
        assertThat(PickGatewayDispatcher.command("ctc-picks:a:b")).isEqualTo("picks");
        assertThat(PickGatewayDispatcher.command("ctc-incidents:a:b")).isEqualTo("incidents");
        assertThat(PickGatewayDispatcher.command("ctc-lineups:a:b")).as("a lookalike prefix belongs to no command").isNull();
        assertThat(PickGatewayDispatcher.reusesPrivateMessage("ctc-lineup:a:b")).isTrue();
        assertThat(PickGatewayDispatcher.reusesPrivateMessage("incidents")).isFalse();
    }

    @Test
    void givenRestoreHoldingAdmission_whenStatsOrPicksArrive_thenUseEachMaintenanceWording() {
        // given
        var picksResponse = mock(PickGatewayResponse.class);
        dispatcher.start(); assertThat(imports.tryLock()).isTrue();
        try {
            // when
            dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.COMMAND, "stats"));
            dispatcher.accept(GUILD, new PickGatewayInteraction("opaque-event", GUILD, "200000000000000002", "300000000000000003",
                    PickGatewayInteraction.Kind.COMMAND, "picks", Map.of(), PickGatewayReplyTest.NOW, picksResponse));
            // then
            verify(response).replyPrivate("Stats are unavailable during maintenance.");
            verify(picksResponse).replyPrivate("A backup restore is in progress. Try again later.");
            verifyNoInteractions(handler);
        } finally { imports.unlock(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"restore", "foreign-guild", "stopped"})
    void givenUnavailableAutocomplete_whenDispatching_thenAnswerWithoutChoicesAndSkipTheHandler(String cause) {
        // given
        if (!cause.equals("stopped")) dispatcher.start();
        if (cause.equals("restore")) assertThat(imports.tryLock()).isTrue();
        try {
            // when
            dispatcher.accept(GUILD, event(cause.equals("foreign-guild") ? "999" : GUILD, PickGatewayInteraction.Kind.AUTOCOMPLETE, "stats"));
            // then
            verify(response).replyChoices(List.of());
            verifyNoInteractions(handler);
        } finally { if (cause.equals("restore")) imports.unlock(); }
    }

    @Test
    void givenFailingAutocompleteHandler_whenDispatching_thenAnswerWithoutChoices() {
        // given
        dispatcher.start();
        doThrow(new IllegalStateException("Test failure")).when(handler).onInteraction(any(), any());
        // when
        dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.AUTOCOMPLETE, "stats"));
        // then
        verify(response).replyChoices(List.of());
    }

    @Test
    void givenAvailableAutocomplete_whenDispatching_thenCallTheHandlerSynchronously() {
        // given
        dispatcher.start();
        // when
        dispatcher.accept(GUILD, event(GUILD, PickGatewayInteraction.Kind.AUTOCOMPLETE, "stats"));
        // then
        verify(handler).onInteraction(any(), any());
        verify(response, never()).deferPrivate();
    }
}
