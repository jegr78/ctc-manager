package org.ctc.domain.picks;

public record ManagementPickActor(String userId, Long generation) {
    public ManagementPickActor(String userId) { this(userId, null); }
}
