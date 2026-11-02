package org.ctc.domain.picks;

import java.time.Instant;
import java.util.UUID;

public record SubmissionReceipt(UUID raceId, UUID requestId, int revisionNumber, long aggregateVersion,
                                Instant confirmedAt, String submitterId) { }
