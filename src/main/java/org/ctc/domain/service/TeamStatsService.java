package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.PlayoffRound;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.WalkoverScoring;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.stats.StatsCache;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.StatsScope;
import org.ctc.domain.stats.TeamStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team figures for the public /stats command. Season figures are the team's row in the website's regular-season
 * standings; alltime figures are its row in the website's alltime standings with subteams rolled up to the parent.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamStatsService {
    private final StatsSeasonService seasons;
    private final StandingsService standings;
    private final SeasonPhaseService phases;
    private final SeasonRepository seasonRows;
    private final TeamRepository teams;
    private final PhaseTeamRepository phaseTeams;
    private final PlayoffRepository playoffs;
    private final RaceRepository races;
    private final RaceLineupRepository lineups;
    private final StatsCache<List<Candidate>> candidates = new StatsCache<>();

    public TeamStats stats(String nameOption, StatsScope scope, String seasonOption) {
        return stats(nameOption, seasons.period(scope, seasonOption));
    }

    public TeamStats stats(String nameOption, StatsPeriod period) {
        var known = new LinkedHashSet<>(ids(seasons.counted()));
        known.addAll(period.seasonIds());
        var team = resolve(nameOption, known, period.alltime());
        return period.alltime() ? alltime(team.getParentOrSelf(), period) : season(team, period);
    }

    public boolean exists(UUID teamId) { return teams.existsById(teamId); }

    /** Teams on a regular-season roster of the scoped seasons, plus their parents outside a season scope, matching the query. */
    public List<StatsChoice> choices(String query, StatsScope scope, String seasonOption) {
        boolean seasonScope = scope == StatsScope.SEASON && seasonOption != null;
        var found = candidates.get(seasonScope ? "season:" + seasonOption : "all", () -> candidates(seasonScope ? seasonOption : null));
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return found.stream().filter(candidate -> needle.isEmpty() || candidate.names().stream().anyMatch(name -> name.contains(needle)))
                .limit(25).map(candidate -> new StatsChoice(candidate.label(), candidate.id().toString())).toList();
    }

    private List<Candidate> candidates(String seasonOption) {
        List<UUID> scoped;
        try {
            scoped = seasonOption != null ? seasons.period(StatsScope.SEASON, seasonOption).seasonIds() : ids(seasons.counted());
        } catch (StatsLookupException unknownSeason) {
            scoped = ids(seasons.counted());
        }
        return rostered(scoped, seasonOption == null).stream()
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Team::getId))
                .map(team -> new Candidate(team.getId(), team.getName() + " (" + team.getShortName() + ")",
                        List.of(team.getName().toLowerCase(Locale.ROOT), team.getShortName().toLowerCase(Locale.ROOT))))
                .toList();
    }

    private Team resolve(String option, Collection<UUID> seasonIds, boolean withParents) {
        if (option == null || option.isBlank()) throw new StatsLookupException("No team found");
        String wanted = option.strip();
        UUID id = uuid(wanted);
        var matches = rostered(seasonIds, withParents).stream().filter(team -> id != null ? team.getId().equals(id)
                : team.getName().equalsIgnoreCase(wanted) || team.getShortName().equalsIgnoreCase(wanted)).toList();
        if (matches.isEmpty()) throw new StatsLookupException("No team found");
        if (matches.size() > 1) throw new StatsLookupException("Several teams match, pick one from the list");
        return matches.getFirst();
    }

    /** Parents count alltime because alltime standings roll subteams up, even for a parent never rostered itself. */
    private List<Team> rostered(Collection<UUID> seasonIds, boolean withParents) {
        var found = new LinkedHashSet<Team>();
        for (UUID seasonId : seasonIds) {
            phases.findByType(seasonId, PhaseType.REGULAR).ifPresent(regular -> phaseTeams.findByPhaseId(regular.getId()).forEach(row -> {
                found.add(row.getTeam());
                if (withParents) found.add(row.getTeam().getParentOrSelf());
            }));
        }
        return List.copyOf(found);
    }

    private TeamStats season(Team team, StatsPeriod period) {
        UUID seasonId = period.season().id();
        var table = regularStandings(seasonId);
        int index = indexOf(table, standing -> standing.getTeam().getId().equals(team.getId()));
        if (index < 0) throw new StatsLookupException("No results for " + team.getName() + " in " + period.label());
        var row = table.get(index);
        Predicate<Team> own = candidate -> candidate != null && candidate.getId().equals(team.getId());
        var playoffResult = playoffs.findBySeasonId(seasonId).map(playoff -> playoffResult(playoff.getRounds(), own)).orElse(null);
        return new TeamStats(team.getId(), team.getName(), period, index + 1, table.size(),
                row.getGroup() == null ? null : row.getGroup().getName(), row.getPlayed(), row.getWins(), row.getDraws(),
                row.getLosses(), row.getPointsFor(), row.getPointsAgainst(), row.getPoints(), races(List.of(seasonId), own),
                playoffResult, 0, null, 0, 0, topDrivers(List.of(seasonId), own), period.label());
    }

    private TeamStats alltime(Team team, StatsPeriod period) {
        var counted = seasonRows.findAllById(period.seasonIds()).stream()
                .sorted(Comparator.comparingInt(Season::getYear).thenComparingInt(Season::getNumber).thenComparing(Season::getId)).toList();
        var table = standings.calculateAlltimeStandings(counted);
        int index = indexOf(table, standing -> standing.getTeam().getId().equals(team.getId()));
        if (index < 0) throw new StatsLookupException("No results for " + team.getName() + " in any season");
        var row = table.get(index);
        Predicate<Team> own = candidate -> candidate != null && candidate.getParentOrSelf().getId().equals(team.getId());
        int seasonCount = 0, bestRank = 0, appearances = 0, titles = 0;
        String bestSeason = null, profileSeason = null;
        for (var season : counted) {
            var regular = regularStandings(season.getId());
            int rank = indexOf(regular, standing -> own.test(standing.getTeam())) + 1;
            if (indexOf(regular, standing -> standing.getTeam().getId().equals(team.getId())) >= 0) profileSeason = season.getDisplayLabel();
            if (rank > 0) {
                seasonCount++;
                if (bestRank == 0 || rank < bestRank) { bestRank = rank; bestSeason = season.getDisplayLabel(); }
            }
            var playoff = playoffs.findBySeasonId(season.getId());
            if (playoff.isPresent()) {
                String result = playoffResult(playoff.get().getRounds(), own);
                if (result != null) appearances++;
                if ("Champion".equals(result)) titles++;
            }
        }
        String best = bestRank == 0 ? null : ordinal(bestRank) + " in " + bestSeason;
        return new TeamStats(team.getId(), team.getName(), period, index + 1, table.size(), null, row.getPlayed(), row.getWins(),
                row.getDraws(), row.getLosses(), row.getPointsFor(), row.getPointsAgainst(), row.getPoints(),
                races(period.seasonIds(), own), null, seasonCount, best, appearances, titles, topDrivers(period.seasonIds(), own), profileSeason);
    }

    private List<StandingsService.TeamStanding> regularStandings(UUID seasonId) {
        return phases.findByType(seasonId, PhaseType.REGULAR).map(regular -> standings.calculateStandings(regular.getId(), null)).orElse(List.of());
    }

    /** "Champion" for the winner of the highest round, otherwise the label of the last round the team played; null without a matchup. */
    static String playoffResult(List<PlayoffRound> rounds, Predicate<Team> own) {
        PlayoffRound reached = null;
        PlayoffMatchup last = null;
        for (var round : rounds) {
            for (var matchup : round.getMatchups()) {
                if ((own.test(matchup.getTeam1()) || own.test(matchup.getTeam2())) && (reached == null || round.getRoundIndex() > reached.getRoundIndex())) {
                    reached = round;
                    last = matchup;
                }
            }
        }
        if (reached == null) return null;
        int finalIndex = rounds.stream().mapToInt(PlayoffRound::getRoundIndex).max().orElse(reached.getRoundIndex());
        return reached.getRoundIndex() == finalIndex && own.test(last.getWinner()) ? "Champion" : reached.getLabel();
    }

    private int races(List<UUID> seasonIds, Predicate<Team> own) {
        int count = 0;
        for (UUID seasonId : seasonIds) {
            for (Race race : races.findByMatchdaySeasonId(seasonId)) {
                if (!race.getResults().isEmpty() && !WalkoverScoring.isWalkover(race) && (own.test(race.getHomeTeam()) || own.test(race.getAwayTeam()))) count++;
            }
        }
        return count;
    }

    /** The three drivers with the most counted points while fielded for the team. */
    private List<TeamStats.TopDriver> topDrivers(List<UUID> seasonIds, Predicate<Team> own) {
        var points = new HashMap<String, Integer>();
        for (UUID seasonId : seasonIds) {
            Map<UUID, List<RaceLineup>> byRace = new HashMap<>();
            for (var row : lineups.findByRaceMatchdaySeasonId(seasonId)) {
                if (own.test(row.getTeam())) byRace.computeIfAbsent(row.getRace().getId(), id -> new ArrayList<>()).add(row);
            }
            for (var entry : byRace.values()) {
                var race = entry.getFirst().getRace();
                if (WalkoverScoring.isWalkover(race)) continue;
                var fielded = entry.stream().map(row -> row.getDriver().getId()).toList();
                race.getResults().stream().filter(result -> fielded.contains(result.getDriver().getId()))
                        .forEach(result -> points.merge(result.getDriver().getPsnId(), result.getPointsTotal(), Integer::sum));
            }
        }
        return points.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .limit(3).map(entry -> new TeamStats.TopDriver(entry.getKey(), entry.getValue())).toList();
    }

    private static <T> int indexOf(List<T> rows, Predicate<T> match) {
        for (int index = 0; index < rows.size(); index++) if (match.test(rows.get(index))) return index;
        return -1;
    }

    private static String ordinal(int rank) {
        String suffix = rank % 100 >= 11 && rank % 100 <= 13 ? "th" : switch (rank % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
        return rank + suffix;
    }

    private static List<UUID> ids(List<Season> list) { return list.stream().map(Season::getId).toList(); }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException name) {
            return null;
        }
    }

    private record Candidate(UUID id, String label, List<String> names) { }
}
