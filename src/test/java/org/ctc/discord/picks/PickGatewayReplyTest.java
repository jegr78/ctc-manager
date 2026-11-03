package org.ctc.discord.picks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickGatewayReplyTest {
    static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    final TestClock clock = new TestClock();
    final AtomicLong epoch = new AtomicLong();
    final PickGatewayResponse raw = mock(PickGatewayResponse.class);
    final PickGatewayResponse.Continuation continuation = mock(PickGatewayResponse.Continuation.class);
    final CompletableFuture<PickGatewayResponse.Continuation> acknowledged = new CompletableFuture<>();
    final PickGatewayReply reply = new PickGatewayReply(raw, clock, NOW, epoch::get, Runnable::run);

    @Test
    void givenInteractiveContinuation_whenLiveThenExpiredOrReset_thenForwardOnlyLivePrivateComponents() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        var saved = new AtomicReference<PickGatewayResponse.Continuation>();
        reply.deferPrivate(saved::set); acknowledged.complete(continuation);
        var message = new PickGatewayMessage("Test private chooser", List.of(new PickGatewayMessage.Button("opaque", "Choose")), null);
        // when / then
        saved.get().replyPrivate(message); verify(continuation).replyPrivate(message);
        clearInvocations(continuation);
        clock.now = NOW.plusSeconds(900); saved.get().replyPrivate(message); verifyNoInteractions(continuation);
        clock.now = NOW.plusSeconds(1); epoch.incrementAndGet(); saved.get().replyPrivate(message);
        verify(continuation).replyPrivate("This interaction was reset. Start again.");
        verify(continuation, never()).replyPrivate(message);
    }

    @Test
    void givenDeferredReply_whenAcknowledgementArrives_thenOnlyThenRunPrivateWork() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        var worked = new AtomicBoolean();
        // when
        reply.deferPrivate(result -> { worked.set(true); result.replyPrivate("Test private saved receipt"); });
        // then
        verify(raw).deferPrivate();
        assertThat(worked).isFalse();
        acknowledged.complete(continuation);
        assertThat(worked).isTrue();
        verify(continuation).replyPrivate("Test private saved receipt");
        verify(raw, never()).replyPrivate(anyString());
    }

    @Test
    void givenFailedAcknowledgement_whenDeferring_thenRunNoBusinessWorkOrPublicFallback() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        var worked = new AtomicBoolean();
        // when
        reply.deferPrivate(result -> worked.set(true));
        acknowledged.completeExceptionally(new IllegalStateException("private token fixture"));
        // then
        assertThat(worked).isFalse();
        verifyNoInteractions(continuation);
        verify(raw, never()).replyPrivate(anyString());
    }

    @Test
    void givenImmediateModalWithTextAndSelect_whenOpening_thenUseModalResponseWithoutDeferring() {
        // given
        var modal = modal();
        // when / then
        assertThat(reply.openModal(modal)).isTrue();
        verify(raw).openModal(modal);
        verify(raw, never()).deferPrivate();
        assertThat(modal.toString()).doesNotContain("Test private weather", "Test selected car");
    }

    @Test
    void givenAlreadyDeferredInteraction_whenOpeningModal_thenRejectUnsupportedResponseChain() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        reply.deferPrivate(result -> { });
        // when / then
        assertThatThrownBy(() -> reply.openModal(modal())).isInstanceOf(IllegalStateException.class).hasMessageContaining("acknowledgement");
        verify(raw, never()).openModal(any());
    }

    @Test
    void givenExactlyExpiredInitialWindow_whenReplyingOrDeferring_thenDoNotAttemptLateAcknowledgement() {
        // given
        clock.now = NOW.plusSeconds(3);
        var worked = new AtomicBoolean();
        // when / then
        assertThat(reply.replyPrivate("Test private error")).isFalse();
        assertThat(reply.deferPrivate(result -> worked.set(true))).isFalse();
        assertThat(reply.openModal(modal())).isFalse();
        assertThat(worked).isFalse();
        verifyNoInteractions(raw, continuation);
    }

    @Test
    void givenAcknowledgedInteractionAtContinuationExpiry_whenSending_thenDropExpiredTokenUse() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        var saved = new AtomicReference<PickGatewayResponse.Continuation>();
        reply.deferPrivate(saved::set); acknowledged.complete(continuation);
        // when / then
        assertThat(saved.get()).isNotNull();
        clock.now = NOW.plusSeconds(900);
        saved.get().replyPrivate("Test private pick data");
        verifyNoInteractions(continuation);
    }

    @Test
    void givenRestoreBeforeAcknowledgementCompletion_whenResuming_thenInvalidatePendingWork() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        var worked = new AtomicBoolean();
        reply.deferPrivate(result -> worked.set(true));
        // when
        epoch.incrementAndGet(); acknowledged.complete(continuation);
        // then
        assertThat(worked).isFalse();
        verify(continuation).replyPrivate("This interaction was reset. Start again.");
    }

    @Test
    void givenRestoreAfterWorkStarted_whenReplyingWithOldPayload_thenSendOnlyResetNotice() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        var saved = new AtomicReference<PickGatewayResponse.Continuation>();
        reply.deferPrivate(saved::set); acknowledged.complete(continuation);
        // when / then
        assertThat(saved.get()).isNotNull();
        epoch.incrementAndGet(); saved.get().replyPrivate("Test private old payload");
        verify(continuation).replyPrivate("This interaction was reset. Start again.");
        verify(continuation, never()).replyPrivate("Test private old payload");
    }

    @Test
    void givenFullWorkQueue_whenAcknowledgementSucceeds_thenReturnPrivateBusyWithoutBusinessWork() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        var worked = new AtomicBoolean();
        var saturated = new PickGatewayReply(raw, clock, NOW, epoch::get, work -> { throw new RejectedExecutionException(); });
        saturated.deferPrivate(result -> worked.set(true));
        // when
        acknowledged.complete(continuation);
        // then
        assertThat(worked).isFalse();
        verify(continuation).replyPrivate("The bot is busy. Try again shortly.");
        verify(raw, never()).replyPrivate(anyString());
    }

    @Test
    void givenBusinessFailureAfterPrivateAcknowledgement_whenHandling_thenKeepErrorPrivateAndGeneric() {
        // given
        when(raw.deferPrivate()).thenReturn(acknowledged);
        reply.deferPrivate(result -> { throw new IllegalStateException("test secret token private weather"); });
        // when
        acknowledged.complete(continuation);
        // then
        verify(continuation).replyPrivate("This request could not be completed. Check your current submission before retrying.");
        verify(raw, never()).replyPrivate(anyString());
    }

    static PickGatewayModal modal() {
        return new PickGatewayModal("opaque-test-modal", "Test private form", List.of(
                new PickGatewayModal.Text("weather", "Weather", true, "Test private weather", 1, 255, true),
                new PickGatewayModal.Selection("car", "Car", List.of(new PickGatewayModal.Option("Test selected car", "opaque-choice")), 1, 1)));
    }

    static class TestClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
