package org.ctc.domain.service;

import org.ctc.domain.model.Match;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final SeasonRepository seasonRepository;
    private final RaceRepository raceRepository;

    @Transactional(readOnly = true)
    public List<TeamStanding> calculateStandings(UUID seasonId) {
        var season = seasonRepository.findById(seasonId).orElse(null);
        if (season == null) return List.of();

        var matchScoring = season.getMatchScoring();
        List<Match> matches = matchRepository.findByMatchdaySeasonId(seasonId);
        Map<UUID, TeamStanding> standingsMap = new HashMap<>();
        Map<UUID, UUID> successionMap = season.buildSuccessionMap();

        for (Team team : season.getActiveTeams()) {
            standingsMap.put(team.getId(), new TeamStanding(team));
        }

        for (Match match : matches) {
            processMatch(match, standingsMap, matchScoring, successionMap);
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

    @Transactional(readOnly = true)
    public List<TeamStanding> calculateAlltimeStandings() {
        List<Season> allSeasons = seasonRepository.findAll();
        Map<UUID, TeamStanding> alltimeMap = new HashMap<>();

        for (Season season : allSeasons) {
            List<TeamStanding> seasonStandings = calculateStandings(season.getId());
            if (seasonStandings.isEmpty()) continue;

            for (TeamStanding standing : seasonStandings) {
                Team parentTeam = standing.getTeam().getParentOrSelf();
                TeamStanding alltime = alltimeMap.computeIfAbsent(
                    parentTeam.getId(), id -> new TeamStanding(parentTeam));
                alltime.merge(standing);
            }
        }

        List<TeamStanding> result = new ArrayList<>(alltimeMap.values());
        result.sort(Comparator
            .<TeamStanding, Integer>comparing(TeamStanding::getPoints, Comparator.reverseOrder())
            .thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
            .thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

        log.debug("Calculated alltime standings: {} teams across {} seasons", result.size(), allSeasons.size());
        return result;
    }

    @Transactional(readOnly = true)
    public List<TeamStanding> calculateStandingsWithBuchholz(UUID seasonId) {
        var standings = calculateStandings(seasonId);
        if (standings.isEmpty()) return standings;

        Map<UUID, Integer> buchholzScores = calculateBuchholzScores(seasonId);
        for (var standing : standings) {
            standing.setBuchholz(buchholzScores.getOrDefault(standing.getTeam().getId(), 0));
        }

        standings.sort(Comparator
                .<TeamStanding, Integer>comparing(TeamStanding::getPoints, Comparator.reverseOrder())
                .thenComparing(TeamStanding::getBuchholz, Comparator.reverseOrder())
                .thenComparing(TeamStanding::getPointDifference, Comparator.reverseOrder())
                .thenComparing(TeamStanding::getPointsFor, Comparator.reverseOrder()));

        log.debug("Calculated standings with Buchholz for season {}: {} teams", seasonId, standings.size());
        return standings;
    }

    private Map<UUID, Integer> calculateBuchholzScores(UUID seasonId) {
        var season = seasonRepository.findById(seasonId).orElse(null);
        if (season == null) return Map.of();

        Map<UUID, UUID> successionMap = season.buildSuccessionMap();

        // Build points map from standings
        var standings = calculateStandings(seasonId);
        Map<UUID, Integer> pointsMap = standings.stream()
                .collect(Collectors.toMap(s -> s.getTeam().getId(), TeamStanding::getPoints));

        // Build opponents map from races (same logic as SwissPairingService.getPlayedOpponents)
        List<Race> races = raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(seasonId);
        Map<UUID, Set<UUID>> opponents = new HashMap<>();
        for (Race race : races) {
            if (race.isBye() || race.getAwayTeam() == null) continue;
            UUID home = successionMap.getOrDefault(race.getHomeTeam().getId(), race.getHomeTeam().getId());
            UUID away = successionMap.getOrDefault(race.getAwayTeam().getId(), race.getAwayTeam().getId());
            opponents.computeIfAbsent(home, k -> new HashSet<>()).add(away);
            opponents.computeIfAbsent(away, k -> new HashSet<>()).add(home);
        }

        // Calculate Buchholz as sum of opponents' points
        Map<UUID, Integer> buchholz = new HashMap<>();
        for (var entry : opponents.entrySet()) {
            int sum = entry.getValue().stream()
                    .mapToInt(oppId -> pointsMap.getOrDefault(oppId, 0))
                    .sum();
            buchholz.put(entry.getKey(), sum);
        }

        return buchholz;
    }

    private void processMatch(Match match, Map<UUID, TeamStanding> standingsMap,
                              MatchScoring matchScoring, Map<UUID, UUID> successionMap) {
        if (match.isBye()) {
            UUID homeId = resolveTeamId(match.getHomeTeam().getId(), successionMap);
            var homeStanding = standingsMap.get(homeId);
            if (homeStanding != null) {
                homeStanding.addWin();
                homeStanding.addMatchPoints(matchScoring.getPointsWin());
            }
            return;
        }

        if (match.getHomeScore() == null || match.getAwayScore() == null) return;

        int homeTotal = match.getHomeScore();
        int awayTotal = match.getAwayScore();

        UUID homeId = resolveTeamId(match.getHomeTeam().getId(), successionMap);
        UUID awayId = match.getAwayTeam() != null ? resolveTeamId(match.getAwayTeam().getId(), successionMap) : null;

        var homeStanding = standingsMap.get(homeId);
        var awayStanding = awayId != null ? standingsMap.get(awayId) : null;

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

    private UUID resolveTeamId(UUID teamId, Map<UUID, UUID> successionMap) {
        return successionMap.getOrDefault(teamId, teamId);
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

        public void merge(TeamStanding other) {
            this.wins += other.wins;
            this.draws += other.draws;
            this.losses += other.losses;
            this.points += other.points;
            this.pointsFor += other.pointsFor;
            this.pointsAgainst += other.pointsAgainst;
        }

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
