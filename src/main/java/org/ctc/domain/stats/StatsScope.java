package org.ctc.domain.stats;

import java.util.Locale;

public enum StatsScope {
    CURRENT, ALLTIME, SEASON;

    /** Reads the Discord option value; a missing value means alltime. */
    public static StatsScope parse(String value) {
        if (value == null || value.isBlank()) return ALLTIME;
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new StatsLookupException("Choose current, alltime or season as scope");
        }
    }
}
