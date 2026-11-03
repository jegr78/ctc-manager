package org.ctc.domain.picks;

public record ManagementPickActor(String userId, String generation) {
    public ManagementPickActor(String userId) { this(userId, null); }
}
