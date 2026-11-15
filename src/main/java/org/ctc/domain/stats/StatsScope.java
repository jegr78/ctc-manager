package org.ctc.domain.stats;

import java.util.Locale;

public enum StatsScope {
    CURRENT, ALLTIME, SEASON;

    /** Reads the Discord option value; a missing value means the current season. */
    public static StatsScope parse(String value) {
        if (value == null || value.isBlank()) return CURRENT;
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new StatsLookupException("Choose current, alltime or season as scope");
        }
    }
}
