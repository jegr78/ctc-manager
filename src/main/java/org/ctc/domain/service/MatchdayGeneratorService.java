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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdayGeneratorService {

    private final SeasonRepository seasonRepository;
    private final MatchdayRepository matchdayRepository;
    private final MatchRepository matchRepository;
    private final RaceRepository raceRepository;

    public record GeneratorFormData(Season season, int teamCount, int optimalRounds) {}

    public GeneratorFormData getFormData(UUID seasonId) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        var teams = season.getEligibleTeams();
        int n = teams.size();
        int optimalRounds = (n % 2 == 0) ? n - 1 : n;
        return new GeneratorFormData(season, n, optimalRounds);
    }

    @Transactional
    public void generate(UUID seasonId, int numberOfRounds, boolean homeAndAway) {
        var season = seasonRepository.findById(seasonId).orElseThrow();

        if (season.getFormat() == SeasonFormat.SWISS) {
            throw new IllegalArgumentException("Generator does not support Swiss format — use Swiss Rounds instead");
        }
        if (!matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId).isEmpty()) {
            throw new IllegalStateException("Season already has matchdays — delete them first");
        }
        var teams = season.getEligibleTeams();
        if (teams.size() < 2) {
            throw new IllegalStateException("Need at least 2 teams to generate matchdays");
        }

        List<List<int[]>> rounds = circleMethod(teams.size(), numberOfRounds);

        int sortIndex = 1;
        for (var round : rounds) {
            var matchday = matchdayRepository.save(new Matchday(season, "MD " + sortIndex, sortIndex));
            createMatchesForRound(matchday, round, teams, false);
            sortIndex++;
        }

        if (homeAndAway) {
            for (var round : rounds) {
                var matchday = matchdayRepository.save(new Matchday(season, "MD " + sortIndex, sortIndex));
                createMatchesForRound(matchday, round, teams, true);
                sortIndex++;
            }
        }

        log.info("Generated {} matchdays for season {}", sortIndex - 1, season.getName());
    }

    /**
     * Circle method (polygon scheduling) for round-robin tournament scheduling.
     * Fixes team[0], rotates team[1..N-1]. For odd team counts, a phantom team
     * is added to create byes.
     *
     * Returns list of rounds, each round is a list of [homeIdx, awayIdx] pairs.
     * awayIdx == -1 means bye.
     */
    List<List<int[]>> circleMethod(int teamCount, int maxRounds) {
        boolean odd = teamCount % 2 != 0;
        int n = odd ? teamCount + 1 : teamCount;
        int[] circle = new int[n];
        for (int i = 0; i < n; i++) circle[i] = i;

        List<List<int[]>> rounds = new ArrayList<>();
        int totalRounds = Math.min(n - 1, maxRounds);

        for (int round = 0; round < totalRounds; round++) {
            List<int[]> pairs = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                int a = circle[i];
                int b = circle[n - 1 - i];

                if (odd && (a == teamCount || b == teamCount)) {
                    int realTeam = (a == teamCount) ? b : a;
                    pairs.add(new int[]{realTeam, -1});
                } else {
                    pairs.add(new int[]{a, b});
                }
            }
            rounds.add(pairs);

            // Rotate: fix circle[0], rotate circle[1..n-1]
            int last = circle[n - 1];
            System.arraycopy(circle, 1, circle, 2, n - 2);
            circle[1] = last;
        }

        balanceHomeAway(rounds, teamCount);
        return rounds;
    }

    /**
     * Post-process pairings to balance home/away distribution.
     * For each match, decide which team is home by tracking counts and
     * swapping to keep the difference ≤ 1.
     */
    private void balanceHomeAway(List<List<int[]>> rounds, int teamCount) {
        int[] homeCounts = new int[teamCount];
        int[] awayCounts = new int[teamCount];

        for (var pairs : rounds) {
            for (int[] pair : pairs) {
                if (pair[1] == -1) continue;
                int a = pair[0];
                int b = pair[1];

                int aDiff = homeCounts[a] - awayCounts[a];
                int bDiff = homeCounts[b] - awayCounts[b];

                // Assign home to the team with fewer home games
                if (aDiff > bDiff) {
                    pair[0] = b;
                    pair[1] = a;
                    homeCounts[b]++;
                    awayCounts[a]++;
                } else if (bDiff > aDiff) {
                    // Keep as is
                    homeCounts[a]++;
                    awayCounts[b]++;
                } else {
                    // Tie: alternate
                    homeCounts[a]++;
                    awayCounts[b]++;
                }
            }
        }
    }

    private void createMatchesForRound(Matchday matchday, List<int[]> pairs, List<Team> teams, boolean reversed) {
        for (int[] pair : pairs) {
            if (pair[1] == -1) {
                createMatchWithRace(matchday, teams.get(pair[0]), null, true);
            } else if (reversed) {
                createMatchWithRace(matchday, teams.get(pair[1]), teams.get(pair[0]), false);
            } else {
                createMatchWithRace(matchday, teams.get(pair[0]), teams.get(pair[1]), false);
            }
        }
    }

    private void createMatchWithRace(Matchday matchday, Team homeTeam, Team awayTeam, boolean bye) {
        var match = new Match(matchday, homeTeam, awayTeam);
        match.setBye(bye);
        match = matchRepository.save(match);
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        raceRepository.save(race);
    }
}
