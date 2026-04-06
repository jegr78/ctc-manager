package org.ctc.domain.service;

import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.RaceRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for assembling the playoff bracket view (read-only).
 * Responsible for building PlayoffBracketView from domain data.
 * Uses ScoringService.calculateTeamTotals for point aggregation (no duplication per D-06).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayoffBracketViewService {

    private final PlayoffRepository playoffRepository;
    private final RaceRepository raceRepository;
    private final PlayoffSeedRepository playoffSeedRepository;
    private final ScoringService scoringService;

    public PlayoffBracketView getBracketView(UUID playoffId) {
        Playoff playoff = playoffRepository.findById(playoffId)
                .orElseThrow(() -> new EntityNotFoundException("Playoff", playoffId));

        // Fetch all races for this playoff in one query, then group by matchup ID
        List<Race> allRaces = raceRepository.findByPlayoffMatchupRoundPlayoffId(playoffId);
        Map<UUID, List<Race>> racesByMatchup = allRaces.stream()
                .collect(Collectors.groupingBy(r -> r.getPlayoffMatchup().getId()));

        // Load seed numbers for bracket display
        Map<UUID, Integer> seedsByTeamId = playoffSeedRepository.findByPlayoffId(playoffId)
                .stream()
                .collect(Collectors.toMap(s -> s.getTeam().getId(), PlayoffSeed::getSeed));

        List<RoundView> roundViews = new ArrayList<>();
        for (PlayoffRound round : playoff.getRounds()) {
            List<MatchupView> matchupViews = new ArrayList<>();
            for (PlayoffMatchup matchup : round.getMatchups()) {
                List<Race> legs = racesByMatchup.getOrDefault(matchup.getId(), List.of());
                matchupViews.add(buildMatchupView(matchup, legs, seedsByTeamId));
            }
            roundViews.add(new RoundView(round.getLabel(), round.getRoundIndex(), matchupViews));
        }

        return new PlayoffBracketView(playoff.getId(), playoff.getName(), roundViews);
    }

    private MatchupView buildMatchupView(PlayoffMatchup matchup, List<Race> legs,
                                         Map<UUID, Integer> seedsByTeamId) {
        UUID team1Id = matchup.getTeam1() != null ? matchup.getTeam1().getId() : null;
        UUID team2Id = matchup.getTeam2() != null ? matchup.getTeam2().getId() : null;

        int team1Aggregate = 0;
        int team2Aggregate = 0;
        List<LegView> legViews = new ArrayList<>();

        for (int i = 0; i < legs.size(); i++) {
            Race leg = legs.get(i);
            int homeTotal = 0;
            int awayTotal = 0;

            if (!leg.getResults().isEmpty() && team1Id != null) {
                int[] totals = scoringService.calculateTeamTotals(leg.getResults(), leg.getId(), team1Id);
                homeTotal = totals[0];
                awayTotal = totals[1];
                team1Aggregate += homeTotal;
                team2Aggregate += awayTotal;
            }

            legViews.add(new LegView(leg.getId(), i + 1, homeTotal, awayTotal, !leg.getResults().isEmpty()));
        }

        // Boolean fields instead of string comparison in templates
        boolean team1IsWinner = matchup.getWinner() != null && team1Id != null
                && matchup.getWinner().getId().equals(team1Id);
        boolean team2IsWinner = matchup.getWinner() != null && !team1IsWinner && matchup.isComplete();

        Integer team1Seed = team1Id != null ? seedsByTeamId.get(team1Id) : null;
        Integer team2Seed = team2Id != null ? seedsByTeamId.get(team2Id) : null;

        return new MatchupView(
                matchup.getId(),
                matchup.getBracketPosition(),
                team1Id,
                team2Id,
                matchup.getTeam1() != null ? matchup.getTeam1().getShortName() : null,
                matchup.getTeam2() != null ? matchup.getTeam2().getShortName() : null,
                matchup.getTeam1() != null ? matchup.getTeam1().getLogoUrl() : null,
                matchup.getTeam2() != null ? matchup.getTeam2().getLogoUrl() : null,
                team1Seed,
                team2Seed,
                team1Aggregate,
                team2Aggregate,
                team1IsWinner,
                team2IsWinner,
                matchup.isComplete(),
                legViews
        );
    }

    // --- View classes ---

    @Getter
    @RequiredArgsConstructor
    public static class PlayoffBracketView {
        private final UUID playoffId;
        private final String name;
        private final List<RoundView> rounds;
    }

    @Getter
    @RequiredArgsConstructor
    public static class RoundView {
        private final String label;
        private final int roundIndex;
        private final List<MatchupView> matchups;
    }

    @Getter
    @RequiredArgsConstructor
    public static class MatchupView {
        private final UUID matchupId;
        private final int bracketPosition;
        private final UUID team1Id;
        private final UUID team2Id;
        private final String team1ShortName;
        private final String team2ShortName;
        private final String team1LogoUrl;
        private final String team2LogoUrl;
        private final Integer team1Seed;
        private final Integer team2Seed;
        private final int team1AggregatePoints;
        private final int team2AggregatePoints;
        private final boolean team1IsWinner;
        private final boolean team2IsWinner;
        private final boolean complete;
        private final List<LegView> legs;
    }

    @Getter
    @RequiredArgsConstructor
    public static class LegView {
        private final UUID raceId;
        private final int legNumber;
        private final int team1Total;
        private final int team2Total;
        private final boolean hasResults;
    }
}
