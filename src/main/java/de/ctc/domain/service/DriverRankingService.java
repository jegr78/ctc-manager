package de.ctc.domain.service;

import de.ctc.domain.model.Driver;
import de.ctc.domain.model.RaceResult;
import de.ctc.domain.model.SeasonDriver;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.RaceResultRepository;
import de.ctc.domain.repository.SeasonDriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverRankingService {

    private final RaceResultRepository raceResultRepository;
    private final SeasonDriverRepository seasonDriverRepository;

    public List<DriverRanking> calculateRanking(UUID seasonId) {
        List<RaceResult> results = raceResultRepository.findByRaceMatchdaySeasonId(seasonId);
        List<SeasonDriver> seasonDrivers = seasonDriverRepository.findBySeasonId(seasonId);

        Map<UUID, Team> driverTeamMap = seasonDrivers.stream()
                .collect(Collectors.toMap(
                        sd -> sd.getDriver().getId(),
                        SeasonDriver::getTeam,
                        (a, b) -> a));

        Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();

        for (RaceResult result : results) {
            UUID driverId = result.getDriver().getId();
            DriverRanking ranking = rankingMap.computeIfAbsent(driverId,
                    id -> new DriverRanking(result.getDriver(), driverTeamMap.get(id)));

            ranking.addResult(result);
        }

        List<DriverRanking> rankings = new ArrayList<>(rankingMap.values());
        rankings.sort(Comparator
                .<DriverRanking, Integer>comparing(DriverRanking::getTotalPoints, Comparator.reverseOrder())
                .thenComparing(DriverRanking::getRacesCount)
                .thenComparing(DriverRanking::getAveragePoints, Comparator.reverseOrder()));

        log.debug("Calculated driver ranking for season {}: {} drivers", seasonId, rankings.size());
        return rankings;
    }

    public List<DriverRanking> calculateAlltimeRanking() {
        List<RaceResult> results = raceResultRepository.findByRacePlayoffMatchupIsNull();
        List<SeasonDriver> allSeasonDrivers = seasonDriverRepository.findAll();

        // For each driver, find their most recent team (by season name), resolved to parent
        Map<UUID, Team> driverTeamMap = allSeasonDrivers.stream()
                .collect(Collectors.groupingBy(sd -> sd.getDriver().getId()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .max(Comparator.comparing(sd -> sd.getSeason().getName()))
                                .map(sd -> sd.getTeam().getParentOrSelf())
                                .orElse(null)));

        Map<UUID, DriverRanking> rankingMap = new LinkedHashMap<>();

        for (RaceResult result : results) {
            UUID driverId = result.getDriver().getId();
            DriverRanking ranking = rankingMap.computeIfAbsent(driverId,
                    id -> new DriverRanking(result.getDriver(), driverTeamMap.get(id)));
            ranking.addResult(result);
        }

        List<DriverRanking> rankings = new ArrayList<>(rankingMap.values());
        rankings.sort(Comparator
                .<DriverRanking, Integer>comparing(DriverRanking::getTotalPoints, Comparator.reverseOrder())
                .thenComparing(DriverRanking::getRacesCount)
                .thenComparing(DriverRanking::getAveragePoints, Comparator.reverseOrder()));

        log.debug("Calculated alltime driver ranking: {} drivers", rankings.size());
        return rankings;
    }

    public static class DriverRanking {
        private final Driver driver;
        private final Team team;
        private int totalPoints;
        private int totalRacePoints;
        private int totalQualiPoints;
        private int totalFlPoints;
        private int racesCount;
        private int bestPosition = Integer.MAX_VALUE;

        public DriverRanking(Driver driver, Team team) {
            this.driver = driver;
            this.team = team;
        }

        public void addResult(RaceResult result) {
            totalPoints += result.getPointsTotal();
            totalRacePoints += result.getPointsRace();
            totalQualiPoints += result.getPointsQuali();
            totalFlPoints += result.getPointsFl();
            racesCount++;
            if (result.getPosition() < bestPosition) {
                bestPosition = result.getPosition();
            }
        }

        public Driver getDriver() { return driver; }
        public Team getTeam() { return team; }
        public int getTotalPoints() { return totalPoints; }
        public int getTotalRacePoints() { return totalRacePoints; }
        public int getTotalQualiPoints() { return totalQualiPoints; }
        public int getTotalFlPoints() { return totalFlPoints; }
        public int getRacesCount() { return racesCount; }
        public int getBestPosition() { return bestPosition == Integer.MAX_VALUE ? 0 : bestPosition; }
        public double getAveragePoints() { return racesCount > 0 ? (double) totalPoints / racesCount : 0; }
    }
}
