package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.stats.DriverStats;
import org.ctc.domain.stats.StatsCandidates;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.StatsScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver figures for the public /stats command, taken from the website's driver ranking: walkover races never count,
 * playoffs always do, and a disqualified result counts as a race but not towards wins, podiums or positions.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverStatsService {
    private static final int SHOWN_TEAMS = 3;
    private final StatsSeasonService seasons;
    private final DriverRepository drivers;
    private final RaceLineupRepository lineups;
    private final SeasonDriverRepository seasonDrivers;
    private final DriverRankingService rankings;
    private final SeasonPhaseRepository phases;
    private final StatsCandidates candidates = new StatsCandidates();

    public DriverStats stats(String nameOption, StatsScope scope, String seasonOption) {
        var counted = seasons.counted();
        return stats(nameOption, seasons.period(counted, scope, seasonOption), ids(counted));
    }

    /** Figures for an explicit period; drivers resolve among the counted seasons and the period's own seasons. */
    public DriverStats stats(String nameOption, StatsPeriod period) {
        var known = new LinkedHashSet<>(ids(seasons.counted()));
        known.addAll(period.seasonIds());
        return stats(nameOption, period, known);
    }

    private DriverStats stats(String nameOption, StatsPeriod period, Collection<UUID> known) {
        var driver = resolve(nameOption, known);
        var ranking = ranking(period);
        for (int index = 0; index < ranking.size(); index++) {
            var entry = ranking.get(index);
            if (entry.getDriver().getId().equals(driver.getId())) {
                return figures(driver, period, entry.getRaceResults(), index + 1, ranking.size());
            }
        }
        throw new StatsLookupException("No results for " + driver.getPsnId() + " in " + (period.alltime() ? "any season" : period.title()));
    }

    /** Figures of every ranked driver of the period, keyed by driver ID in ranking order; one ranking for all. */
    public java.util.Map<UUID, DriverStats> ranked(StatsPeriod period) {
        var ranking = ranking(period);
        var figures = new java.util.LinkedHashMap<UUID, DriverStats>();
        for (int index = 0; index < ranking.size(); index++) {
            var entry = ranking.get(index);
            figures.put(entry.getDriver().getId(), figures(entry.getDriver(), period, entry.getRaceResults(), index + 1, ranking.size()));
        }
        return figures;
    }

    private List<DriverRankingService.DriverRanking> ranking(StatsPeriod period) {
        return period.alltime() ? rankings.calculateAlltimeRanking(period.seasonIds())
                : rankings.aggregateAcrossPhases(phases.findBySeasonIdOrderBySortIndex(period.season().id()).stream().map(SeasonPhase::getId).toList(),
                        period.season().id());
    }

    public boolean exists(UUID driverId) { return drivers.existsById(driverId); }

    /** Drivers with a counted result in the scoped seasons whose PSN ID, nickname or alias contains the query. */
    public List<StatsChoice> choices(String query, StatsScope scope, String seasonOption) {
        UUID season = scope == StatsScope.SEASON ? seasons.seasonKey(seasonOption) : null;
        return candidates.choices(season == null ? "all" : season.toString(), () -> candidates(season), query);
    }

    void clearCaches() { candidates.clear(); }

    private List<StatsCandidates.Candidate> candidates(UUID season) {
        List<UUID> scoped = season != null ? List.of(season) : ids(seasons.counted());
        if (scoped.isEmpty()) return List.of();
        return drivers.findWithResultsInSeasons(scoped).stream()
                .sorted(Comparator.comparing(Driver::getPsnId, String.CASE_INSENSITIVE_ORDER).thenComparing(Driver::getId))
                .map(driver -> new StatsCandidates.Candidate(driver.getId(), label(driver), names(driver)))
                .toList();
    }

    /** The UUID an autocomplete choice carries, else one exact case-insensitive PSN ID, nickname or alias, among drivers with counted results. */
    private Driver resolve(String option, Collection<UUID> seasonIds) {
        if (option == null || option.isBlank() || seasonIds.isEmpty()) throw new StatsLookupException("No driver found");
        String wanted = option.strip();
        var known = drivers.findWithResultsInSeasons(seasonIds);
        UUID id = StatsCandidates.uuid(wanted);
        var matches = known.stream().filter(driver -> id != null ? driver.getId().equals(id)
                : names(driver).stream().anyMatch(name -> name.equalsIgnoreCase(wanted))).toList();
        if (matches.isEmpty()) throw new StatsLookupException("No driver found");
        if (matches.size() > 1) throw new StatsLookupException("Several drivers match, pick one from the list");
        return matches.getFirst();
    }

    private DriverStats figures(Driver driver, StatsPeriod period, List<RaceResult> counted, int rank, int ranked) {
        int wins = 0, podiums = 0, poles = 0, fastest = 0, best = 0, dsq = 0, guests = 0, points = 0, classified = 0, positionSum = 0;
        var raced = new LinkedHashSet<UUID>();
        for (var result : counted) raced.add(result.getRace().getMatchday().getSeason().getId());
        var seasonLineups = new java.util.HashMap<UUID, List<RaceLineup>>();
        var guestRaces = new HashSet<UUID>();
        for (UUID seasonId : raced) {
            var rows = lineups.findByDriverIdAndRaceMatchdaySeasonId(driver.getId(), seasonId);
            seasonLineups.put(seasonId, rows);
            rows.stream().filter(RaceLineup::isGuest).forEach(row -> guestRaces.add(row.getRace().getId()));
        }
        for (var result : counted) {
            points += result.getPointsTotal();
            if (result.getQualiPosition() == 1) poles++;
            if (result.isFastestLap()) fastest++;
            if (guestRaces.contains(result.getRace().getId())) guests++;
            if (result.isDisqualified()) { dsq++; continue; }
            int position = result.getClassificationPosition();
            classified++;
            positionSum += position;
            if (position == 1) wins++;
            if (position <= 3) podiums++;
            if (best == 0 || position < best) best = position;
        }
        var teams = teams(driver, period, raced, seasonLineups);
        int more = Math.max(0, teams.size() - SHOWN_TEAMS);
        return new DriverStats(driver.getId(), driver.getPsnId(), driver.getNickname(), period, rank, ranked, points, counted.size(),
                wins, podiums, poles, fastest, best, classified == 0 ? null : (double) positionSum / classified, guests, dsq,
                raced.size(), teams.subList(more, teams.size()), more, profileSeason(driver, period, raced));
    }

    /** The website links a driver to the newest season with a roster entry, else to the newest season raced. */
    private String profileSeason(Driver driver, StatsPeriod period, Set<UUID> raced) {
        if (!period.alltime()) return period.label();
        var rostered = seasonDrivers.findByDriverId(driver.getId()).stream().map(row -> row.getSeason().getId()).collect(java.util.stream.Collectors.toSet());
        String fallback = null;
        for (var season : period.seasons().reversed()) {
            if (rostered.contains(season.id())) return season.label();
            if (fallback == null && raced.contains(season.id())) fallback = season.label();
        }
        return fallback;
    }

    /** One team per season in chronological order, consecutive repeats merged; alltime names the parent team. */
    private List<String> teams(Driver driver, StatsPeriod period, Set<UUID> raced, java.util.Map<UUID, List<RaceLineup>> seasonLineups) {
        var names = new ArrayList<String>();
        for (var season : period.seasons()) {
            if (!raced.contains(season.id())) continue;
            var team = seasonTeam(driver, season.id(), seasonLineups.getOrDefault(season.id(), List.of()));
            if (team != null && period.alltime()) team = team.getParentOrSelf();
            if (team != null && (names.isEmpty() || !names.getLast().equals(team.getName()))) names.add(team.getName());
        }
        return names;
    }

    /** The team of the driver's latest non-guest lineup in the season, else the season roster, else the latest guest lineup. */
    private Team seasonTeam(Driver driver, UUID seasonId, List<RaceLineup> lineupRows) {
        var rows = lineupRows.stream()
                .sorted(Comparator.comparingInt((RaceLineup row) -> row.getRace().getMatchday().getPhase().getSortIndex())
                        .thenComparingInt(row -> row.getRace().getMatchday().getSortIndex())
                        .thenComparing(row -> row.getRace().getDateTime(), Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        var regular = rows.stream().filter(row -> !row.isGuest()).reduce((first, second) -> second);
        if (regular.isPresent()) return regular.get().getTeam();
        var roster = seasonDrivers.findBySeasonIdAndDriverId(seasonId, driver.getId());
        if (roster.isPresent()) return roster.get().getTeam();
        return rows.isEmpty() ? null : rows.getLast().getTeam();
    }

    private static List<UUID> ids(List<Season> list) { return list.stream().map(Season::getId).toList(); }


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
