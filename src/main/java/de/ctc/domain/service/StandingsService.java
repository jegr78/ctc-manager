package de.ctc.domain.service;

import de.ctc.domain.model.Race;
import de.ctc.domain.model.RaceResult;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.MatchdayLineupRepository;
import de.ctc.domain.repository.RaceRepository;
import de.ctc.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsService {

    private final RaceRepository raceRepository;
    private final TeamRepository teamRepository;
    private final MatchdayLineupRepository matchdayLineupRepository;
    private final ScoringService scoringService;

    public List<TeamStanding> calculateStandings(UUID seasonId) {
        List<Race> races = raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(seasonId);
        Map<UUID, TeamStanding> standingsMap = new HashMap<>();

        for (Team team : teamRepository.findAll()) {
            standingsMap.put(team.getId(), new TeamStanding(team));
        }

        for (Race race : races) {
            if (race.isBye()) {
                var homeStanding = standingsMap.get(race.getHomeTeam().getId());
                if (homeStanding != null) homeStanding.addWin();
                continue;
            }
            if (race.getResults().isEmpty()) continue;
            processRace(race, standingsMap);
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

    public List<TeamStanding> calculateAlltimeStandings() {
        List<Race> races = raceRepository.findByPlayoffMatchupIsNull();
        Map<UUID, TeamStanding> standingsMap = new HashMap<>();

        for (Team team : teamRepository.findAll()) {
            Team parent = team.getParentOrSelf();
            standingsMap.putIfAbsent(parent.getId(), new TeamStanding(parent));
        }

        for (Race race : races) {
            if (race.getResults().isEmpty()) continue;

            Team homeParent = race.getHomeTeam().getParentOrSelf();
            Team awayParent = race.getAwayTeam().getParentOrSelf();

            // Skip intra-parent races (e.g. CLR 1 vs CLR 2)
            if (homeParent.getId().equals(awayParent.getId())) continue;

            processAlltimeRace(race, standingsMap, homeParent, awayParent);
        }

        List<TeamStanding> standings = new ArrayList<>(standingsMap.values());
        standings.removeIf(s -> s.getPlayed() == 0);
        standings.sort(Comparator
                .<TeamStanding, Integer>comparing(TeamStanding::getPoints, Comparator.reverseOrder())
                .thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
                .thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

        log.debug("Calculated alltime standings: {} teams", standings.size());
        return standings;
    }

    private void processAlltimeRace(Race race, Map<UUID, TeamStanding> standingsMap,
                                     Team homeParent, Team awayParent) {
        UUID homeTeamId = race.getHomeTeam().getId();
        UUID awayTeamId = race.getAwayTeam().getId();

        List<RaceResult> homeResults = race.getResults().stream()
                .filter(r -> isDriverInTeam(r, homeTeamId, race))
                .toList();
        List<RaceResult> awayResults = race.getResults().stream()
                .filter(r -> isDriverInTeam(r, awayTeamId, race))
                .toList();

        int homeTotal = scoringService.calculateTeamTotal(homeResults);
        int awayTotal = scoringService.calculateTeamTotal(awayResults);

        TeamStanding homeStanding = standingsMap.get(homeParent.getId());
        TeamStanding awayStanding = standingsMap.get(awayParent.getId());

        if (homeStanding == null || awayStanding == null) return;

        homeStanding.addPointsFor(homeTotal);
        homeStanding.addPointsAgainst(awayTotal);
        awayStanding.addPointsFor(awayTotal);
        awayStanding.addPointsAgainst(homeTotal);

        if (homeTotal > awayTotal) {
            homeStanding.addWin();
            awayStanding.addLoss();
        } else if (homeTotal < awayTotal) {
            homeStanding.addLoss();
            awayStanding.addWin();
        } else {
            homeStanding.addDraw();
            awayStanding.addDraw();
        }
    }

    private void processRace(Race race, Map<UUID, TeamStanding> standingsMap) {
        UUID homeTeamId = race.getHomeTeam().getId();
        UUID awayTeamId = race.getAwayTeam().getId();

        List<RaceResult> homeResults = race.getResults().stream()
                .filter(r -> isDriverInTeam(r, homeTeamId, race))
                .toList();
        List<RaceResult> awayResults = race.getResults().stream()
                .filter(r -> isDriverInTeam(r, awayTeamId, race))
                .toList();

        int homeTotal = scoringService.calculateTeamTotal(homeResults);
        int awayTotal = scoringService.calculateTeamTotal(awayResults);

        TeamStanding homeStanding = standingsMap.get(homeTeamId);
        TeamStanding awayStanding = standingsMap.get(awayTeamId);

        if (homeStanding == null || awayStanding == null) {
            log.warn("Team not found in standings map for race {}", race.getId());
            return;
        }

        homeStanding.addPointsFor(homeTotal);
        homeStanding.addPointsAgainst(awayTotal);
        awayStanding.addPointsFor(awayTotal);
        awayStanding.addPointsAgainst(homeTotal);

        if (homeTotal > awayTotal) {
            homeStanding.addWin();
            awayStanding.addLoss();
        } else if (homeTotal < awayTotal) {
            homeStanding.addLoss();
            awayStanding.addWin();
        } else {
            homeStanding.addDraw();
            awayStanding.addDraw();
        }
    }

    private boolean isDriverInTeam(RaceResult result, UUID teamId, Race race) {
        UUID matchdayId = race.getMatchday().getId();
        UUID seasonId = race.getMatchday().getSeason().getId();

        // Check MatchdayLineup first (for sub-teams)
        var lineup = matchdayLineupRepository.findByMatchdayIdAndDriverId(matchdayId, result.getDriver().getId());
        if (lineup.isPresent()) {
            return lineup.get().getTeam().getId().equals(teamId);
        }

        // Fallback: SeasonDriver (standalone teams without sub-teams)
        return result.getDriver().getSeasonDrivers().stream()
                .anyMatch(sd -> sd.getSeason().getId().equals(seasonId)
                        && sd.getTeam().getId().equals(teamId));
    }

    public static class TeamStanding {
        private final Team team;
        private int wins;
        private int draws;
        private int losses;
        private int pointsFor;
        private int pointsAgainst;
        private int buchholz;

        public TeamStanding(Team team) {
            this.team = team;
        }

        public void addWin() { wins++; }
        public void addDraw() { draws++; }
        public void addLoss() { losses++; }
        public void addPointsFor(int points) { pointsFor += points; }
        public void addPointsAgainst(int points) { pointsAgainst += points; }
        public void setBuchholz(int buchholz) { this.buchholz = buchholz; }

        public Team getTeam() { return team; }
        public int getWins() { return wins; }
        public int getDraws() { return draws; }
        public int getLosses() { return losses; }
        public int getPlayed() { return wins + draws + losses; }
        public int getPoints() { return wins * 3 + draws; }
        public int getPointsFor() { return pointsFor; }
        public int getPointsAgainst() { return pointsAgainst; }
        public int getPointDifference() { return pointsFor - pointsAgainst; }
        public String getPointsRatio() { return pointsFor + ":" + pointsAgainst; }
        public int getBuchholz() { return buchholz; }
        public String getMatchRecord() { return wins + " - " + losses + " - " + draws; }
    }
}
