package org.ctc.domain.model;

import java.util.UUID;

public final class MatchWalkoverRules {
    private MatchWalkoverRules() {}

    public static boolean valid(UUID forfeiting, UUID home, UUID away, boolean bye) {
        return forfeiting == null || !bye && home != null && away != null
                && (forfeiting.equals(home) || forfeiting.equals(away));
    }
}
