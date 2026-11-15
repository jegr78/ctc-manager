package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.stats.DriverStats;
import org.ctc.domain.stats.StatsCache;
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
    private final StatsCache<List<Candidate>> candidates = new StatsCache<>();

    public DriverStats stats(String nameOption, StatsScope scope, String seasonOption) {
        return stats(nameOption, seasons.period(scope, seasonOption));
    }

    public DriverStats stats(String nameOption, StatsPeriod period) {
        var known = new LinkedHashSet<>(ids(seasons.counted()));
        known.addAll(period.seasonIds());
        var driver = resolve(nameOption, known);
        var ranking = period.alltime() ? rankings.calculateAlltimeRanking(period.seasonIds())
                : rankings.aggregateAcrossPhases(phases.findBySeasonIdOrderBySortIndex(period.season().id()).stream().map(SeasonPhase::getId).toList(),
                        period.season().id());
        for (int index = 0; index < ranking.size(); index++) {
            var entry = ranking.get(index);
            if (entry.getDriver().getId().equals(driver.getId())) {
                return figures(driver, period, entry.getRaceResults(), index + 1, ranking.size());
            }
        }
        throw new StatsLookupException("No results for " + driver.getPsnId() + " in " + (period.alltime() ? "any season" : period.label()));
    }

    /** Drivers with a counted result in the scoped seasons whose PSN ID, nickname or alias contains the query. */
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
        if (scoped.isEmpty()) return List.of();
        return drivers.findWithResultsInSeasons(scoped).stream()
                .sorted(Comparator.comparing(Driver::getPsnId, String.CASE_INSENSITIVE_ORDER).thenComparing(Driver::getId))
                .map(driver -> new Candidate(driver.getId(), label(driver), names(driver).stream().map(name -> name.toLowerCase(Locale.ROOT)).toList()))
                .toList();
    }

    /** The UUID an autocomplete choice carries, else one exact case-insensitive PSN ID, nickname or alias, among drivers with counted results. */
    private Driver resolve(String option, Collection<UUID> seasonIds) {
        if (option == null || option.isBlank() || seasonIds.isEmpty()) throw new StatsLookupException("No driver found");
        String wanted = option.strip();
        var known = drivers.findWithResultsInSeasons(seasonIds);
        UUID id = uuid(wanted);
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
                raced.size(), teams.subList(more, teams.size()), more);
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

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException name) {
            return null;
        }
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

    private record Candidate(UUID id, String label, List<String> names) { }
}
