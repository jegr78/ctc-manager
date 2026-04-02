package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwissPairingService {

    private final SeasonRepository seasonRepository;
    private final RaceRepository raceRepository;
    private final MatchRepository matchRepository;
    private final MatchdayRepository matchdayRepository;
    private final StandingsService standingsService;

    @Transactional
    public Matchday generateNextRound(UUID seasonId) {
        var season = seasonRepository.findById(seasonId).orElseThrow();

        if (season.getFormat() != SeasonFormat.SWISS) {
            throw new IllegalArgumentException("Season is not in Swiss format");
        }

        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId);
        int currentRound = matchdays.size();

        if (season.getTotalRounds() != null && currentRound >= season.getTotalRounds()) {
            throw new IllegalStateException("All rounds have been generated");
        }

        // Check that all races in the latest round have results
        if (!matchdays.isEmpty()) {
            var lastMatchday = matchdays.get(matchdays.size() - 1);
            var lastRaces = raceRepository.findByMatchdayId(lastMatchday.getId());
            boolean allComplete = lastRaces.stream()
                    .allMatch(r -> r.isBye()
                            || !r.getResults().isEmpty()
                            || (r.getHomeScore() != null && r.getAwayScore() != null));
            if (!allComplete) {
                throw new IllegalStateException("Current round has incomplete races");
            }
        }

        int roundNumber = currentRound + 1;
        var matchday = new Matchday(season, "Round " + roundNumber, roundNumber);
        matchday = matchdayRepository.save(matchday);

        List<Team> teams = getEligibleTeams(season);
        List<Race> pairings;

        if (currentRound == 0) {
            pairings = generateFirstRoundPairings(matchday, teams);
        } else {
            pairings = generateSubsequentRoundPairings(matchday, teams, seasonId);
        }

        for (Race race : pairings) {
            raceRepository.save(race);
        }

        log.info("Generated Swiss round {} for season {}: {} pairings", roundNumber, season.getName(), pairings.size());
        return matchday;
    }

    private List<Race> generateFirstRoundPairings(Matchday matchday, List<Team> teams) {
        Collections.shuffle(teams);
        return createPairingsFromOrder(matchday, teams);
    }

    private List<Race> generateSubsequentRoundPairings(Matchday matchday, List<Team> teams, UUID seasonId) {
        // Get current standings to sort teams by points
        var standings = standingsService.calculateStandings(seasonId);
        Map<UUID, Integer> pointsMap = standings.stream()
                .collect(Collectors.toMap(s -> s.getTeam().getId(), StandingsService.TeamStanding::getPoints));

        // Sort teams by points (descending), then by original order for ties
        teams.sort((a, b) -> {
            int pa = pointsMap.getOrDefault(a.getId(), 0);
            int pb = pointsMap.getOrDefault(b.getId(), 0);
            return Integer.compare(pb, pa);
        });

        var season = seasonRepository.findById(seasonId).orElseThrow();
        Map<UUID, UUID> successionMap = season.buildSuccessionMap();

        // Get played opponents for each team (resolved through succession)
        Map<UUID, Set<UUID>> playedOpponents = getPlayedOpponents(seasonId, successionMap);

        // Get teams that already had a bye (resolved through succession)
        Set<UUID> byeTeams = getByeTeams(seasonId, successionMap);

        return createSwissPairings(matchday, teams, playedOpponents, byeTeams);
    }

    private List<Race> createSwissPairings(Matchday matchday, List<Team> teams,
                                            Map<UUID, Set<UUID>> playedOpponents,
                                            Set<UUID> byeTeams) {
        List<Race> pairings = new ArrayList<>();
        List<Team> unpaired = new ArrayList<>(teams);

        // Handle bye for odd number of teams
        if (unpaired.size() % 2 != 0) {
            Team byeTeam = selectByeTeam(unpaired, byeTeams);
            unpaired.remove(byeTeam);
            pairings.add(createRaceWithMatch(matchday, byeTeam, null, true));
        }

        // Pair teams: iterate through sorted list, pair with next available opponent
        while (unpaired.size() >= 2) {
            Team team1 = unpaired.remove(0);
            Set<UUID> team1Opponents = playedOpponents.getOrDefault(team1.getId(), Set.of());

            Team opponent = null;
            for (int i = 0; i < unpaired.size(); i++) {
                Team candidate = unpaired.get(i);
                if (!team1Opponents.contains(candidate.getId())) {
                    opponent = candidate;
                    unpaired.remove(i);
                    break;
                }
            }

            if (opponent == null) {
                // Fallback: all opponents already played, pair with closest rank anyway
                opponent = unpaired.remove(0);
                log.warn("Swiss pairing: forced rematch {} vs {}", team1.getShortName(), opponent.getShortName());
            }

            pairings.add(createRaceWithMatch(matchday, team1, opponent, false));
        }

        return pairings;
    }

    private Team selectByeTeam(List<Team> teams, Set<UUID> byeTeams) {
        // Select lowest-ranked team that hasn't had a bye yet
        for (int i = teams.size() - 1; i >= 0; i--) {
            if (!byeTeams.contains(teams.get(i).getId())) {
                return teams.get(i);
            }
        }
        // All teams had a bye already, pick the lowest-ranked
        return teams.get(teams.size() - 1);
    }

    private List<Race> createPairingsFromOrder(Matchday matchday, List<Team> teams) {
        List<Race> pairings = new ArrayList<>();

        // Handle bye for odd number
        if (teams.size() % 2 != 0) {
            Team byeTeam = teams.remove(teams.size() - 1);
            pairings.add(createRaceWithMatch(matchday, byeTeam, null, true));
        }

        for (int i = 0; i < teams.size(); i += 2) {
            pairings.add(createRaceWithMatch(matchday, teams.get(i), teams.get(i + 1), false));
        }

        return pairings;
    }

    private Race createRaceWithMatch(Matchday matchday, Team homeTeam, Team awayTeam, boolean bye) {
        var match = new Match(matchday, homeTeam, awayTeam);
        match.setBye(bye);
        match = matchRepository.save(match);
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        return race;
    }

    public Map<UUID, Set<UUID>> getPlayedOpponents(UUID seasonId) {
        return getPlayedOpponents(seasonId, Map.of());
    }

    private Map<UUID, Set<UUID>> getPlayedOpponents(UUID seasonId, Map<UUID, UUID> successionMap) {
        List<Race> races = raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(seasonId);
        Map<UUID, Set<UUID>> opponents = new HashMap<>();

        for (Race race : races) {
            if (race.isBye() || race.getAwayTeam() == null) continue;
            UUID home = successionMap.getOrDefault(race.getHomeTeam().getId(), race.getHomeTeam().getId());
            UUID away = successionMap.getOrDefault(race.getAwayTeam().getId(), race.getAwayTeam().getId());
            opponents.computeIfAbsent(home, k -> new HashSet<>()).add(away);
            opponents.computeIfAbsent(away, k -> new HashSet<>()).add(home);
        }

        return opponents;
    }

    public Set<UUID> getByeTeams(UUID seasonId) {
        return getByeTeams(seasonId, Map.of());
    }

    private Set<UUID> getByeTeams(UUID seasonId, Map<UUID, UUID> successionMap) {
        List<Race> races = raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(seasonId);
        return races.stream()
                .filter(Race::isBye)
                .map(r -> successionMap.getOrDefault(r.getHomeTeam().getId(), r.getHomeTeam().getId()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns teams eligible for matches: filters out parent teams
     * that have sub-teams in the season (only sub-teams compete).
     */
    private List<Team> getEligibleTeams(Season season) {
        List<Team> activeTeams = season.getActiveTeams();
        Set<UUID> parentIdsWithSubs = activeTeams.stream()
                .filter(Team::isSubTeam)
                .map(t -> t.getParentTeam().getId())
                .collect(Collectors.toSet());

        return activeTeams.stream()
                .filter(t -> !parentIdsWithSubs.contains(t.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Map<UUID, Integer> calculateBuchholz(UUID seasonId) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        Map<UUID, UUID> successionMap = season.buildSuccessionMap();

        var standings = standingsService.calculateStandings(seasonId);
        Map<UUID, Integer> pointsMap = standings.stream()
                .collect(Collectors.toMap(s -> s.getTeam().getId(), StandingsService.TeamStanding::getPoints));

        Map<UUID, Set<UUID>> opponents = getPlayedOpponents(seasonId, successionMap);
        Map<UUID, Integer> buchholz = new HashMap<>();

        for (var entry : opponents.entrySet()) {
            int sum = entry.getValue().stream()
                    .mapToInt(oppId -> pointsMap.getOrDefault(oppId, 0))
                    .sum();
            buchholz.put(entry.getKey(), sum);
        }

        return buchholz;
    }

    public int getCurrentRound(UUID seasonId) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        return season.getMatchdays().size();
    }

    public boolean isCurrentRoundComplete(UUID seasonId) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        if (season.getMatchdays().isEmpty()) return true;

        var lastMatchday = season.getMatchdays().get(season.getMatchdays().size() - 1);
        return lastMatchday.getRaces().stream()
                .allMatch(r -> r.isBye()
                        || !r.getResults().isEmpty()
                        || (r.getHomeScore() != null && r.getAwayScore() != null));
    }
}
