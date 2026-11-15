package org.ctc.domain.stats;

import java.util.List;
import java.util.UUID;

/**
 * Public figures of one team over a {@link StatsPeriod}. {@code group} and {@code playoffResult} apply to a season,
 * {@code seasons}, {@code bestResult}, {@code playoffAppearances} and {@code titles} to alltime; absent values are null or 0.
 */
public record TeamStats(UUID teamId, String name, StatsPeriod period, int rank, int ranked, String group, int played, int wins,
                        int draws, int losses, int pointsFor, int pointsAgainst, int matchPoints, int races, String playoffResult,
                        int seasons, String bestResult, int playoffAppearances, int titles, List<TopDriver> topDrivers,
                        String profileSeasonLabel) {
    public TeamStats { topDrivers = List.copyOf(topDrivers); }
    public int winRate() { return played == 0 ? 0 : Math.round(100f * wins / played); }
    public int difference() { return pointsFor - pointsAgainst; }

    public record TopDriver(String psnId, int points) { }
}
