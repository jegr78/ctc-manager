package org.ctc.domain.incidents;

import java.time.Instant;
import java.util.UUID;

public record PublicIncidentCase(UUID id, String reference, UUID matchdayId, String matchdayLabel, int matchdayOrder,
                                 String pairing, String raceLabel, String reportingParty, String involvedDrivers,
                                 String replayTimestamp, String description, String evidenceReferences,
                                 String ruling, int rulingVersion, Instant publishedAt) {
    public String getPublishedAtLabel() {
        return java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'", java.util.Locale.ENGLISH)
                .withZone(java.time.ZoneOffset.UTC).format(publishedAt);
    }
    @Override public String toString() { return "PublicIncidentCase[redacted]"; }
}
