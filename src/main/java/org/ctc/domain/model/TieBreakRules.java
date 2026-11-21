package org.ctc.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Shared invariant of a stored tie-break winner for writes, archive preflight and the data audit. */
public final class TieBreakRules {
    private TieBreakRules() { }

    public static boolean valid(boolean phaseRule, UUID winner, UUID home, UUID away, Integer homeScore, Integer awayScore,
                                boolean bye, UUID walkover) {
        if (winner == null) return true;
        return phaseRule && !bye && walkover == null && homeScore != null && Objects.equals(homeScore, awayScore)
                && (winner.equals(home) || winner.equals(away));
    }
}
