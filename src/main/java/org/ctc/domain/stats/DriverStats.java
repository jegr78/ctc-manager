package org.ctc.domain.stats;

import java.util.List;
import java.util.UUID;

/**
 * Public figures of one driver over a {@link StatsPeriod}. {@code bestPosition} is 0 and {@code averagePosition} null
 * when every counted result is a disqualification. {@code profileSeasonLabel} names the season whose website profile
 * represents the driver.
 */
public record DriverStats(UUID driverId, String psnId, String nickname, StatsPeriod period, int rank, int ranked,
                          int points, int races, int wins, int podiums, int poles, int fastestLaps, int bestPosition,
                          Double averagePosition, int guestRaces, int disqualifications, int seasons, List<String> teams, int moreTeams,
                          String profileSeasonLabel) {
    public DriverStats { teams = List.copyOf(teams); }
    public double averagePoints() { return races == 0 ? 0 : (double) points / races; }
}
