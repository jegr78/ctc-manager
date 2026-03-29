package de.ctc.domain.service;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayoffService {

    private final PlayoffRepository playoffRepository;
    private final PlayoffRoundRepository playoffRoundRepository;
    private final PlayoffMatchupRepository playoffMatchupRepository;
    private final RaceRepository raceRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;

    private static final Map<Integer, List<String>> DEFAULT_ROUND_LABELS = Map.of(
            4, List.of("Halbfinale", "Finale"),
            8, List.of("Viertelfinale", "Halbfinale", "Finale")
    );

    @Transactional
    public Playoff createPlayoff(UUID seasonId, String name, int numberOfTeams) {
        if (numberOfTeams != 4 && numberOfTeams != 8) {
            throw new IllegalArgumentException("Number of teams must be 4 or 8, got: " + numberOfTeams);
        }

        // #7: Check for existing playoff
        if (playoffRepository.findBySeasonId(seasonId).isPresent()) {
            throw new IllegalArgumentException("Playoff already exists for this season");
        }

        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonId));

        Playoff playoff = new Playoff(season, name);
        playoff = playoffRepository.save(playoff);

        List<String> labels = DEFAULT_ROUND_LABELS.get(numberOfTeams);
        int numRounds = labels.size();

        // Create rounds and matchups
        List<List<PlayoffMatchup>> allRoundMatchups = new ArrayList<>();
        for (int r = 0; r < numRounds; r++) {
            PlayoffRound round = new PlayoffRound(playoff, labels.get(r), r);
            round = playoffRoundRepository.save(round);
            playoff.getRounds().add(round);

            int matchupsInRound = numberOfTeams / (int) Math.pow(2, r + 1);
            List<PlayoffMatchup> matchups = new ArrayList<>();
            for (int m = 0; m < matchupsInRound; m++) {
                PlayoffMatchup matchup = new PlayoffMatchup(round, m);
                matchup = playoffMatchupRepository.save(matchup);
                round.getMatchups().add(matchup);
                matchups.add(matchup);
            }
            allRoundMatchups.add(matchups);
        }

        // Wire nextMatchup links: each pair of matchups in round N feeds into one matchup in round N+1
        for (int r = 0; r < numRounds - 1; r++) {
            List<PlayoffMatchup> currentRound = allRoundMatchups.get(r);
            List<PlayoffMatchup> nextRound = allRoundMatchups.get(r + 1);
            for (int m = 0; m < currentRound.size(); m++) {
                PlayoffMatchup matchup = currentRound.get(m);
                matchup.setNextMatchup(nextRound.get(m / 2));
                playoffMatchupRepository.save(matchup);
            }
        }

        log.info("Created playoff '{}' for season '{}' with {} teams, {} rounds",
                name, season.getName(), numberOfTeams, numRounds);
        return playoff;
    }

    @Transactional
    public void addSeasonToPlayoff(UUID playoffId, UUID seasonId) {
        Playoff playoff = playoffRepository.findById(playoffId)
                .orElseThrow(() -> new IllegalArgumentException("Playoff not found: " + playoffId));
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonId));
        if (!playoff.getSeasons().contains(season)) {
            playoff.getSeasons().add(season);
            playoffRepository.save(playoff);
        }
    }

    @Transactional
    public void removeSeasonFromPlayoff(UUID playoffId, UUID seasonId) {
        Playoff playoff = playoffRepository.findById(playoffId)
                .orElseThrow(() -> new IllegalArgumentException("Playoff not found: " + playoffId));
        playoff.getSeasons().removeIf(s -> s.getId().equals(seasonId));
        playoffRepository.save(playoff);
    }

    @Transactional(readOnly = true)
    public List<Team> getPlayoffTeams(UUID playoffId) {
        Playoff playoff = playoffRepository.findById(playoffId)
                .orElseThrow(() -> new IllegalArgumentException("Playoff not found: " + playoffId));
        // Collect teams from all linked seasons, deduplicate by ID
        Map<UUID, Team> teamMap = new LinkedHashMap<>();
        for (Season season : playoff.getSeasons()) {
            for (Team team : season.getTeams()) {
                teamMap.putIfAbsent(team.getId(), team);
            }
        }
        // Also include teams from the main season
        for (Team team : playoff.getSeason().getTeams()) {
            teamMap.putIfAbsent(team.getId(), team);
        }
        return new ArrayList<>(teamMap.values());
    }

    @Transactional
    public void seedTeam(UUID matchupId, UUID teamId, int slot) {
        PlayoffMatchup matchup = playoffMatchupRepository.findById(matchupId)
                .orElseThrow(() -> new IllegalArgumentException("Matchup not found: " + matchupId));

        if (slot == 1) {
            matchup.setTeam1(teamId != null ? findTeam(teamId) : null);
        } else if (slot == 2) {
            matchup.setTeam2(teamId != null ? findTeam(teamId) : null);
        } else {
            throw new IllegalArgumentException("Slot must be 1 or 2, got: " + slot);
        }
        playoffMatchupRepository.save(matchup);
    }

    private Team findTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
    }

    @Transactional
    public void determineWinner(UUID matchupId) {
        PlayoffMatchup matchup = playoffMatchupRepository.findById(matchupId)
                .orElseThrow(() -> new IllegalArgumentException("Matchup not found: " + matchupId));

        if (!matchup.isReady()) {
            throw new IllegalStateException("Matchup is not ready - both teams must be set");
        }

        List<Race> legs = raceRepository.findByPlayoffMatchupId(matchupId);
        if (legs.isEmpty()) {
            throw new IllegalStateException("No races found for matchup");
        }

        UUID seasonId = matchup.getRound().getPlayoff().getSeason().getId();
        UUID team1Id = matchup.getTeam1().getId();

        // #2: Use shared helper for point calculation
        int team1Total = 0;
        int team2Total = 0;
        for (Race leg : legs) {
            if (leg.getResults().isEmpty()) continue;
            int[] totals = calculateTeamTotals(leg.getResults(), seasonId, team1Id);
            team1Total += totals[0];
            team2Total += totals[1];
        }

        // Store aggregated scores on matchup
        matchup.setHomeScore(team1Total);
        matchup.setAwayScore(team2Total);

        // #1: Explicit tie handling — ties are not silently resolved
        if (team1Total == team2Total) {
            playoffMatchupRepository.save(matchup);
            throw new IllegalStateException(
                    "Tie (%d:%d) — Winner must be set manually".formatted(team1Total, team2Total));
        }

        Team winner = team1Total > team2Total ? matchup.getTeam1() : matchup.getTeam2();
        matchup.setWinner(winner);
        playoffMatchupRepository.save(matchup);

        // Advance winner to next matchup
        if (matchup.getNextMatchup() != null) {
            PlayoffMatchup next = matchup.getNextMatchup();
            if (matchup.getBracketPosition() % 2 == 0) {
                next.setTeam1(winner);
            } else {
                next.setTeam2(winner);
            }
            playoffMatchupRepository.save(next);
        }

        log.info("Matchup winner determined: {} ({}:{}) - advancing to next round",
                winner.getShortName(), team1Total, team2Total);
    }

    @Transactional
    public void setWinnerManually(UUID matchupId, UUID winnerTeamId) {
        PlayoffMatchup matchup = playoffMatchupRepository.findById(matchupId)
                .orElseThrow(() -> new IllegalArgumentException("Matchup not found: " + matchupId));

        Team winner = findTeam(winnerTeamId);

        boolean isParticipant = (matchup.getTeam1() != null && matchup.getTeam1().getId().equals(winnerTeamId))
                || (matchup.getTeam2() != null && matchup.getTeam2().getId().equals(winnerTeamId));
        if (!isParticipant) {
            throw new IllegalArgumentException("Winner must be one of the matchup participants");
        }

        matchup.setWinner(winner);
        playoffMatchupRepository.save(matchup);

        if (matchup.getNextMatchup() != null) {
            PlayoffMatchup next = matchup.getNextMatchup();
            if (matchup.getBracketPosition() % 2 == 0) {
                next.setTeam1(winner);
            } else {
                next.setTeam2(winner);
            }
            playoffMatchupRepository.save(next);
        }

        log.info("Matchup winner set manually: {}", winner.getShortName());
    }

    @Transactional(readOnly = true)
    public PlayoffBracketView getBracketView(UUID playoffId) {
        Playoff playoff = playoffRepository.findById(playoffId)
                .orElseThrow(() -> new IllegalArgumentException("Playoff not found: " + playoffId));

        // #4: Fetch all races for this playoff in one query, then group by matchup ID
        List<Race> allRaces = raceRepository.findByPlayoffMatchupRoundPlayoffId(playoffId);
        Map<UUID, List<Race>> racesByMatchup = allRaces.stream()
                .collect(Collectors.groupingBy(r -> r.getPlayoffMatchup().getId()));

        UUID seasonId = playoff.getSeason().getId();

        List<RoundView> roundViews = new ArrayList<>();
        for (PlayoffRound round : playoff.getRounds()) {
            List<MatchupView> matchupViews = new ArrayList<>();
            for (PlayoffMatchup matchup : round.getMatchups()) {
                List<Race> legs = racesByMatchup.getOrDefault(matchup.getId(), List.of());
                matchupViews.add(buildMatchupView(matchup, legs, seasonId));
            }
            roundViews.add(new RoundView(round.getLabel(), round.getRoundIndex(), matchupViews));
        }

        return new PlayoffBracketView(playoff.getId(), playoff.getName(), roundViews);
    }

    // #2: Shared helper — calculates [team1Points, team2Points] from race results
    private int[] calculateTeamTotals(List<RaceResult> results, UUID seasonId, UUID team1Id) {
        int team1Total = 0;
        int team2Total = 0;
        for (RaceResult result : results) {
            if (isDriverInTeam(result, seasonId, team1Id)) {
                team1Total += result.getPointsTotal();
            } else {
                team2Total += result.getPointsTotal();
            }
        }
        return new int[]{team1Total, team2Total};
    }

    private boolean isDriverInTeam(RaceResult result, UUID seasonId, UUID teamId) {
        return result.getDriver().getSeasonDrivers().stream()
                .anyMatch(sd -> sd.getSeason().getId().equals(seasonId)
                        && sd.getTeam().getId().equals(teamId));
    }

    private MatchupView buildMatchupView(PlayoffMatchup matchup, List<Race> legs, UUID seasonId) {
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
                int[] totals = calculateTeamTotals(leg.getResults(), seasonId, team1Id);
                homeTotal = totals[0];
                awayTotal = totals[1];
                team1Aggregate += homeTotal;
                team2Aggregate += awayTotal;
            }

            legViews.add(new LegView(leg.getId(), i + 1, homeTotal, awayTotal, !leg.getResults().isEmpty()));
        }

        // #3: Boolean fields instead of string comparison in templates
        boolean team1IsWinner = matchup.getWinner() != null && team1Id != null
                && matchup.getWinner().getId().equals(team1Id);
        boolean team2IsWinner = matchup.getWinner() != null && !team1IsWinner && matchup.isComplete();

        return new MatchupView(
                matchup.getId(),
                matchup.getBracketPosition(),
                team1Id,
                team2Id,
                matchup.getTeam1() != null ? matchup.getTeam1().getShortName() : null,
                matchup.getTeam2() != null ? matchup.getTeam2().getShortName() : null,
                matchup.getTeam1() != null ? matchup.getTeam1().getLogoUrl() : null,
                matchup.getTeam2() != null ? matchup.getTeam2().getLogoUrl() : null,
                team1Aggregate,
                team2Aggregate,
                team1IsWinner,
                team2IsWinner,
                matchup.isComplete(),
                legViews
        );
    }

    // --- DTOs ---

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
