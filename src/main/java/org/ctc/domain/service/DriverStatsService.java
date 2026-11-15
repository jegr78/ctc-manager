package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.WalkoverScoring;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.stats.DriverStats;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.StatsScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver figures for the public /stats command. Walkover races never count, playoffs always do, a disqualified result
 * counts as a race but not towards wins, podiums or positions, and the rank comes from the website's driver ranking.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverStatsService {
    private static final int SHOWN_TEAMS = 3;
    private final StatsSeasonService seasons;
    private final DriverRepository drivers;
    private final RaceResultRepository results;
    private final RaceLineupRepository lineups;
    private final SeasonDriverRepository seasonDrivers;
    private final DriverRankingService rankings;
    private final SeasonPhaseRepository phases;

    public DriverStats stats(String nameOption, StatsScope scope, String seasonOption) {
        return stats(nameOption, seasons.period(scope, seasonOption));
    }

    public DriverStats stats(String nameOption, StatsPeriod period) {
        var driver = resolve(nameOption);
        var counted = counted(period).stream().filter(result -> result.getDriver().getId().equals(driver.getId())).toList();
        if (counted.isEmpty()) throw new StatsLookupException("No results for " + driver.getPsnId() + " in " + (period.alltime() ? "any season" : period.label()));
        return figures(driver, period, counted);
    }

    public List<StatsChoice> choices(String query, StatsScope scope, String seasonOption) {
        List<Season> scoped;
        try {
            scoped = scope == StatsScope.SEASON && seasonOption != null ? seasons.period(scope, seasonOption).seasons() : seasons.counted();
        } catch (StatsLookupException unknownSeason) {
            scoped = seasons.counted();
        }
        if (scoped.isEmpty()) return List.of();
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return drivers.findWithResultsInSeasons(scoped.stream().map(Season::getId).toList()).stream()
                .filter(driver -> needle.isEmpty() || names(driver).stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).contains(needle)))
                .sorted(Comparator.comparing(Driver::getPsnId, String.CASE_INSENSITIVE_ORDER).thenComparing(Driver::getId))
                .limit(25).map(driver -> new StatsChoice(label(driver), driver.getId().toString())).toList();
    }

    /** The UUID an autocomplete choice carries, else one exact case-insensitive PSN ID, nickname or alias. */
    Driver resolve(String option) {
        if (option == null) throw new StatsLookupException("No driver found");
        try {
            return drivers.findById(UUID.fromString(option.strip())).orElseThrow(() -> new StatsLookupException("No driver found"));
        } catch (IllegalArgumentException name) {
            String wanted = option.strip();
            var matches = drivers.findAll().stream()
                    .filter(driver -> names(driver).stream().anyMatch(candidate -> candidate.equalsIgnoreCase(wanted))).toList();
            if (matches.isEmpty()) throw new StatsLookupException("No driver found");
            if (matches.size() > 1) throw new StatsLookupException("Several drivers match, pick one from the list");
            return matches.getFirst();
        }
    }

    private DriverStats figures(Driver driver, StatsPeriod period, List<RaceResult> counted) {
        int wins = 0, podiums = 0, poles = 0, fastest = 0, best = 0, dsq = 0, guests = 0, points = 0, classified = 0, positionSum = 0;
        var seasonIds = new HashSet<UUID>();
        for (var result : counted) {
            points += result.getPointsTotal();
            if (result.getQualiPosition() == 1) poles++;
            if (result.isFastestLap()) fastest++;
            seasonIds.add(result.getRace().getMatchday().getSeason().getId());
            if (lineups.findByRaceIdAndDriverId(result.getRace().getId(), driver.getId()).map(RaceLineup::isGuest).orElse(false)) guests++;
            if (result.isDisqualified()) { dsq++; continue; }
            int position = result.getClassificationPosition();
            classified++;
            positionSum += position;
            if (position == 1) wins++;
            if (position <= 3) podiums++;
            if (best == 0 || position < best) best = position;
        }
        var ranking = period.alltime() ? rankings.calculateAlltimeRanking(period.seasons().stream().map(Season::getId).toList())
                : rankings.aggregateAcrossPhases(phases.findBySeasonIdOrderBySortIndex(period.season().getId()).stream().map(SeasonPhase::getId).toList(), period.season().getId());
        int rank = 0;
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getDriver().getId().equals(driver.getId())) { rank = i + 1; break; }
        }
        var teams = teams(driver, period, seasonIds);
        int more = Math.max(0, teams.size() - SHOWN_TEAMS);
        return new DriverStats(driver.getId(), driver.getPsnId(), driver.getNickname(), period, rank, ranking.size(), points, counted.size(),
                wins, podiums, poles, fastest, best, classified == 0 ? null : (double) positionSum / classified, guests, dsq,
                seasonIds.size(), teams.subList(more, teams.size()), more);
    }

    /** One team per season in chronological order, consecutive repeats merged. */
    private List<String> teams(Driver driver, StatsPeriod period, Set<UUID> raced) {
        var names = new ArrayList<String>();
        for (var season : period.seasons()) {
            if (!raced.contains(season.getId())) continue;
            var team = seasonTeam(driver, season);
            if (team != null && period.alltime()) team = team.getParentOrSelf();
            if (team != null && (names.isEmpty() || !names.getLast().equals(team.getName()))) names.add(team.getName());
        }
        return names;
    }

    /** The team of the driver's latest non-guest lineup in the season, else the season roster, else the latest guest lineup. */
    private Team seasonTeam(Driver driver, Season season) {
        var rows = lineups.findByDriverIdAndRaceMatchdaySeasonId(driver.getId(), season.getId()).stream()
                .sorted(Comparator.comparingInt((RaceLineup row) -> row.getRace().getMatchday().getSortIndex())
                        .thenComparing(row -> row.getRace().getDateTime(), Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        var regular = rows.stream().filter(row -> !row.isGuest()).reduce((first, second) -> second);
        if (regular.isPresent()) return regular.get().getTeam();
        var roster = seasonDrivers.findBySeasonIdAndDriverId(season.getId(), driver.getId());
        if (roster.isPresent()) return roster.get().getTeam();
        return rows.isEmpty() ? null : rows.getLast().getTeam();
    }

    private List<RaceResult> counted(StatsPeriod period) {
        if (period.seasons().isEmpty()) return List.of();
        return results.findByRaceMatchdaySeasonIdIn(period.seasons().stream().map(Season::getId).toList()).stream()
                .filter(WalkoverScoring::countsForDriverStatistics).toList();
    }

    private static List<String> names(Driver driver) {
        var names = new ArrayList<String>();
        names.add(driver.getPsnId());
        if (driver.getNickname() != null) names.add(driver.getNickname());
        driver.getAliases().stream().map(PsnAlias::getAlias).filter(Objects::nonNull).forEach(names::add);
        return names;
    }

    private static String label(Driver driver) {
        return driver.getNickname() == null || driver.getNickname().equalsIgnoreCase(driver.getPsnId())
                ? driver.getPsnId() : driver.getPsnId() + " (" + driver.getNickname() + ")";
    }
}
