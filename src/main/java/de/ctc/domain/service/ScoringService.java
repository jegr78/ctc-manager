package de.ctc.domain.service;

import de.ctc.domain.model.RaceResult;
import de.ctc.domain.model.RaceScoring;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ScoringService {

    public void calculatePoints(RaceResult result, RaceScoring scoring) {
        int[] racePoints = scoring.getRacePointsArray();
        int[] qualiPoints = scoring.getQualiPointsArray();

        int rp = (result.getPosition() >= 1 && result.getPosition() <= racePoints.length)
                ? racePoints[result.getPosition() - 1] : 0;
        int qp = (result.getQualiPosition() >= 1 && result.getQualiPosition() <= qualiPoints.length)
                ? qualiPoints[result.getQualiPosition() - 1] : 0;
        int fp = result.isFastestLap() ? scoring.getFastestLapPoints() : 0;

        result.setPointsRace(rp);
        result.setPointsQuali(qp);
        result.setPointsFl(fp);
        result.setPointsTotal(rp + qp + fp);

        log.debug("Calculated points for driver {}: race={}, quali={}, fl={}, total={}",
                result.getDriver() != null ? result.getDriver().getPsnId() : "unknown",
                rp, qp, fp, result.getPointsTotal());
    }

    public void calculatePoints(List<RaceResult> results, RaceScoring scoring) {
        results.forEach(r -> calculatePoints(r, scoring));
    }

    public int calculateTeamTotal(List<RaceResult> teamResults) {
        return teamResults.stream()
                .mapToInt(RaceResult::getPointsTotal)
                .sum();
    }
}
