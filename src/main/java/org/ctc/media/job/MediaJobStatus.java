package org.ctc.media.job;

import java.time.Instant;
import java.util.UUID;

public record MediaJobStatus(UUID jobId, MediaJobKind kind, String scopeKey, MediaJobState state, int step, int steps,
                             String message, Instant updatedAt) {

    public boolean running() {
        return !state.finished();
    }
}
