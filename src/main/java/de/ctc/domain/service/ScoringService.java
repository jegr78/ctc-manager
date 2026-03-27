package de.ctc.domain.service;

import de.ctc.domain.model.RaceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ScoringService {

    private static final int[] RACE_POINTS = {20, 17, 14, 12, 10, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] QUALI_POINTS = {3, 2, 1};
    private static final int FASTEST_LAP_POINTS = 2;

    public int calculateRacePoints(int position) {
        if (position < 1 || position > 12) {
            throw new IllegalArgumentException("Position must be between 1 and 12, got: " + position);
        }
        return RACE_POINTS[position - 1];
    }

    public int calculateQualiPoints(int qualiPosition) {
        if (qualiPosition < 1 || qualiPosition > 12) {
            throw new IllegalArgumentException("Quali position must be between 1 and 12, got: " + qualiPosition);
        }
        if (qualiPosition <= QUALI_POINTS.length) {
            return QUALI_POINTS[qualiPosition - 1];
        }
        return 0;
    }

    public int calculateFastestLapPoints(boolean fastestLap) {
        return fastestLap ? FASTEST_LAP_POINTS : 0;
    }

    public void calculatePoints(RaceResult result) {
        int racePoints = calculateRacePoints(result.getPosition());
        int qualiPoints = calculateQualiPoints(result.getQualiPosition());
        int flPoints = calculateFastestLapPoints(result.isFastestLap());

        result.setPointsRace(racePoints);
        result.setPointsQuali(qualiPoints);
        result.setPointsFl(flPoints);
        result.setPointsTotal(racePoints + qualiPoints + flPoints);

        log.debug("Calculated points for driver {}: race={}, quali={}, fl={}, total={}",
                result.getDriver() != null ? result.getDriver().getPsnId() : "unknown",
                racePoints, qualiPoints, flPoints, result.getPointsTotal());
    }

    public void calculatePoints(List<RaceResult> results) {
        results.forEach(this::calculatePoints);
    }

    public int calculateTeamTotal(List<RaceResult> teamResults) {
        return teamResults.stream()
                .mapToInt(RaceResult::getPointsTotal)
                .sum();
    }
}
