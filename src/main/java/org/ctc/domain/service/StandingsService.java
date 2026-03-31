package org.ctc.domain.service;

import org.ctc.domain.model.Match;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final SeasonRepository seasonRepository;

    @Transactional(readOnly = true)
    public List<TeamStanding> calculateStandings(UUID seasonId) {
        var season = seasonRepository.findById(seasonId).orElse(null);
        if (season == null) return List.of();

        var matchScoring = season.getMatchScoring();
        List<Match> matches = matchRepository.findByMatchdaySeasonId(seasonId);
        Map<UUID, TeamStanding> standingsMap = new HashMap<>();

        for (Team team : season.getTeams()) {
            standingsMap.put(team.getId(), new TeamStanding(team));
        }

        for (Match match : matches) {
            processMatch(match, standingsMap, matchScoring);
        }

        List<TeamStanding> standings = new ArrayList<>(standingsMap.values());
        standings.removeIf(s -> s.getPlayed() == 0);
        standings.sort(Comparator
                .<TeamStanding, Integer>comparing(TeamStanding::getPoints, Comparator.reverseOrder())
                .thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
                .thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

        log.debug("Calculated standings for season {}: {} teams", seasonId, standings.size());
        return standings;
    }

    private void processMatch(Match match, Map<UUID, TeamStanding> standingsMap, MatchScoring matchScoring) {
        if (match.isBye()) {
            var homeStanding = standingsMap.get(match.getHomeTeam().getId());
            if (homeStanding != null) {
                homeStanding.addWin();
                homeStanding.addMatchPoints(matchScoring.getPointsWin());
            }
            return;
        }

        if (match.getHomeScore() == null || match.getAwayScore() == null) return;

        int homeTotal = match.getHomeScore();
        int awayTotal = match.getAwayScore();

        var homeStanding = standingsMap.get(match.getHomeTeam().getId());
        var awayStanding = match.getAwayTeam() != null ? standingsMap.get(match.getAwayTeam().getId()) : null;

        if (homeStanding == null) return;

        homeStanding.addPointsFor(homeTotal);
        homeStanding.addPointsAgainst(awayTotal);
        if (awayStanding != null) {
            awayStanding.addPointsFor(awayTotal);
            awayStanding.addPointsAgainst(homeTotal);
        }

        if (homeTotal > awayTotal) {
            homeStanding.addWin();
            homeStanding.addMatchPoints(matchScoring.getPointsWin());
            if (awayStanding != null) {
                awayStanding.addLoss();
                awayStanding.addMatchPoints(matchScoring.getPointsLoss());
            }
        } else if (homeTotal < awayTotal) {
            homeStanding.addLoss();
            homeStanding.addMatchPoints(matchScoring.getPointsLoss());
            if (awayStanding != null) {
                awayStanding.addWin();
                awayStanding.addMatchPoints(matchScoring.getPointsWin());
            }
        } else {
            homeStanding.addDraw();
            homeStanding.addMatchPoints(matchScoring.getPointsDraw());
            if (awayStanding != null) {
                awayStanding.addDraw();
                awayStanding.addMatchPoints(matchScoring.getPointsDraw());
            }
        }
    }

    public static class TeamStanding {
        private final Team team;
        private int wins;
        private int draws;
        private int losses;
        private int points;
        private int pointsFor;
        private int pointsAgainst;
        private int buchholz;

        public TeamStanding(Team team) {
            this.team = team;
        }

        public void addWin() { wins++; }
        public void addDraw() { draws++; }
        public void addLoss() { losses++; }
        public void addMatchPoints(int pts) { points += pts; }
        public void addPointsFor(int pts) { pointsFor += pts; }
        public void addPointsAgainst(int pts) { pointsAgainst += pts; }
        public void setBuchholz(int buchholz) { this.buchholz = buchholz; }

        public Team getTeam() { return team; }
        public int getWins() { return wins; }
        public int getDraws() { return draws; }
        public int getLosses() { return losses; }
        public int getPlayed() { return wins + draws + losses; }
        public int getPoints() { return points; }
        public int getPointsFor() { return pointsFor; }
        public int getPointsAgainst() { return pointsAgainst; }
        public int getPointDifference() { return pointsFor - pointsAgainst; }
        public String getPointsRatio() { return pointsFor + ":" + pointsAgainst; }
        public int getBuchholz() { return buchholz; }
        public String getMatchRecord() { return wins + " - " + losses + " - " + draws; }
    }
}
