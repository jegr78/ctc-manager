package de.ctc.domain.service;

import de.ctc.domain.model.Match;
import de.ctc.domain.model.PlayoffMatchup;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.RaceResult;
import de.ctc.domain.model.RaceScoring;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

    /**
     * Aggregates race result scores onto the parent Match or PlayoffMatchup.
     * Call this after saving race results to keep match scores in sync.
     */
    public void aggregateMatchScores(Race race) {
        if (race.getResults().isEmpty()) return;

        if (race.getMatch() != null && race.getMatch().getHomeTeam() != null) {
            // For multi-leg: sum across all legs of the match
            Match match = race.getMatch();
            int matchHome = 0, matchAway = 0;
            for (Race leg : match.getRaces()) {
                if (leg.getResults().isEmpty()) continue;
                UUID hId = match.getHomeTeam().getId();
                matchHome += leg.getResults().stream()
                        .filter(r -> isDriverInTeam(r, hId))
                        .mapToInt(RaceResult::getPointsTotal).sum();
                matchAway += leg.getResults().stream()
                        .filter(r -> !isDriverInTeam(r, hId))
                        .mapToInt(RaceResult::getPointsTotal).sum();
            }
            match.setHomeScore(matchHome);
            match.setAwayScore(matchAway);
            log.debug("Aggregated match scores: {} {} : {} {}",
                    match.getHomeTeam().getShortName(), matchHome, matchAway,
                    match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "?");
        }

        if (race.getPlayoffMatchup() != null && race.getPlayoffMatchup().getTeam1() != null) {
            PlayoffMatchup matchup = race.getPlayoffMatchup();
            int mHome = 0, mAway = 0;
            for (Race leg : matchup.getRaces()) {
                if (leg.getResults().isEmpty()) continue;
                UUID t1Id = matchup.getTeam1().getId();
                mHome += leg.getResults().stream()
                        .filter(r -> isDriverInTeam(r, t1Id))
                        .mapToInt(RaceResult::getPointsTotal).sum();
                mAway += leg.getResults().stream()
                        .filter(r -> !isDriverInTeam(r, t1Id))
                        .mapToInt(RaceResult::getPointsTotal).sum();
            }
            matchup.setHomeScore(mHome);
            matchup.setAwayScore(mAway);
        }
    }

    private boolean isDriverInTeam(RaceResult result, UUID teamId) {
        return result.getDriver().getSeasonDrivers().stream()
                .anyMatch(sd -> sd.getTeam().getId().equals(teamId));
    }
}
