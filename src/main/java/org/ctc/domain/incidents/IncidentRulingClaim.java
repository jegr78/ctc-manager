package org.ctc.domain.incidents;

import java.time.Instant;
import java.util.UUID;

public record IncidentRulingClaim(UUID id, UUID reportId, UUID windowId, UUID token, long version, String generation,
        Instant validUntil, IncidentThreadClaim thread, String content, String previousContent, String previousMessageId, String nonce) {
    public String marker() { return "[CTC " + reportId + " RULING]"; }
    @Override public String toString() { return "IncidentRulingClaim[private]"; }
}
