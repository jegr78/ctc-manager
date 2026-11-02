package org.ctc.discord.picks;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class PickGatewayReply {
    private static final String RESET = "This interaction was reset. Start again.";
    private static final String BUSY = "Picks are busy. Try again shortly.";
    private static final String FAILED = "This request could not be completed. Check your current submission before retrying.";
    private final PickGatewayResponse response;
    private final Clock clock;
    private final Instant createdAt;
    private final LongSupplier epoch;
    private final Executor work;
    private final long generation;
    private final AtomicBoolean acknowledged = new AtomicBoolean();

    public PickGatewayReply(PickGatewayResponse response, Clock clock, Instant createdAt,
                            LongSupplier epoch, Executor work) {
        this.response = Objects.requireNonNull(response);
        this.clock = Objects.requireNonNull(clock);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.epoch = Objects.requireNonNull(epoch);
        this.work = Objects.requireNonNull(work);
        this.generation = epoch.getAsLong();
    }

    public boolean deferPrivate(Consumer<PickGatewayResponse.Continuation> operation) {
        if (!claimInitial()) return false;
        try {
            response.deferPrivate().thenAccept(continuation -> {
                if (continuation == null || !continuationValid()) return;
                PickGatewayResponse.Continuation guarded = message -> {
                    if (continuationValid()) safelyReply(continuation, currentGeneration() ? message : RESET);
                };
                if (!currentGeneration()) { guarded.replyPrivate(RESET); return; }
                try {
                    work.execute(() -> {
                        if (!continuationValid()) return;
                        if (!currentGeneration()) { guarded.replyPrivate(RESET); return; }
                        try {
                            operation.accept(guarded);
                        } catch (RuntimeException failure) {
                            guarded.replyPrivate(FAILED);
                        }
                    });
                } catch (RejectedExecutionException saturated) {
                    guarded.replyPrivate(BUSY);
                }
            }).exceptionally(failure -> null);
            return true;
        } catch (RuntimeException acknowledgementFailed) {
            return false;
        }
    }

    public boolean replyPrivate(String message) {
        if (!claimInitial()) return false;
        try {
            response.replyPrivate(message);
            return true;
        } catch (RuntimeException acknowledgementFailed) {
            return false;
        }
    }

    public boolean openModal(PickGatewayModal modal) {
        if (!claimInitial()) return false;
        try {
            response.openModal(modal);
            return true;
        } catch (RuntimeException acknowledgementFailed) {
            return false;
        }
    }

    public boolean isAcknowledged() { return acknowledged.get(); }

    private boolean claimInitial() {
        if (!clock.instant().isBefore(createdAt.plusSeconds(3)) || !currentGeneration()) return false;
        if (!acknowledged.compareAndSet(false, true)) {
            throw new IllegalStateException("An initial response must be sent before acknowledgement");
        }
        return true;
    }

    private boolean continuationValid() { return clock.instant().isBefore(createdAt.plusSeconds(900)); }
    private boolean currentGeneration() { return generation == epoch.getAsLong(); }

    private static void safelyReply(PickGatewayResponse.Continuation continuation, String message) {
        try {
            continuation.replyPrivate(message);
        } catch (RuntimeException unavailable) {
            // An expired or failed private callback must never fall back to an ordinary channel post.
        }
    }
}
