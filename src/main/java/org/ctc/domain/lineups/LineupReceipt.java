package org.ctc.domain.lineups;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LineupReceipt(UUID raceId, UUID teamId, UUID requestId, int revisionNumber, long version,
                            Instant submittedAt, String actorId, List<String> warnings) {
    public LineupReceipt { warnings = List.copyOf(warnings); }
}
