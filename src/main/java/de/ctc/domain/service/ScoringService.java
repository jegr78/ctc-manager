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
            Match match = race.getMatch();
            UUID hId = match.getHomeTeam().getId();

            // Collect all legs — use match.getRaces() but ensure current race is included
            var legs = new java.util.ArrayList<>(match.getRaces());
            if (legs.stream().noneMatch(r -> r.getId() != null && r.getId().equals(race.getId()))) {
                legs.add(race);
            }

            int matchHome = 0, matchAway = 0;
            for (Race leg : legs) {
                if (leg.getResults().isEmpty()) continue;
                matchHome += leg.getResults().stream()
                        .filter(r -> isDriverInTeam(r, hId))
                        .mapToInt(RaceResult::getPointsTotal).sum();
                matchAway += leg.getResults().stream()
                        .filter(r -> !isDriverInTeam(r, hId))
                        .mapToInt(RaceResult::getPointsTotal).sum();
            }
            match.setHomeScore(matchHome);
            match.setAwayScore(matchAway);
            log.info("Aggregated match scores: {} {} : {} {}",
                    match.getHomeTeam().getShortName(), matchHome, matchAway,
                    match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "?");
        }

        if (race.getPlayoffMatchup() != null && race.getPlayoffMatchup().getTeam1() != null) {
            PlayoffMatchup matchup = race.getPlayoffMatchup();
            UUID t1Id = matchup.getTeam1().getId();

            var legs = new java.util.ArrayList<>(matchup.getRaces());
            if (legs.stream().noneMatch(r -> r.getId() != null && r.getId().equals(race.getId()))) {
                legs.add(race);
            }

            int mHome = 0, mAway = 0;
            for (Race leg : legs) {
                if (leg.getResults().isEmpty()) continue;
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
