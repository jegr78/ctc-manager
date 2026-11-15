package org.ctc.domain.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.stats.StatsCache;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsPeriod;
import org.ctc.domain.stats.StatsScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The seasons public stats count: those the website publishes, ordered by year and number. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsSeasonService {
    private final SeasonRepository seasons;
    private final SeasonPhaseService phases;
    private final StatsCache<List<StatsChoice>> choices = new StatsCache<>();

    public List<Season> counted() {
        return seasons.findAll().stream()
                .filter(season -> !season.getName().contains("Test"))
                .filter(season -> phases.findByType(season.getId(), PhaseType.REGULAR).isPresent())
                .sorted(Comparator.comparingInt(Season::getYear).thenComparingInt(Season::getNumber).thenComparing(Season::getId))
                .toList();
    }

    public StatsPeriod period(StatsScope scope, String seasonOption) {
        var counted = counted();
        return switch (scope) {
            case ALLTIME -> new StatsPeriod(null, counted, false);
            case CURRENT -> counted.stream().filter(Season::isActive).findFirst()
                    .map(season -> new StatsPeriod(season, List.of(season), false))
                    .orElseGet(() -> new StatsPeriod(null, counted, true));
            case SEASON -> {
                if (seasonOption == null) throw new StatsLookupException("Choose a season for the Season scope");
                var season = counted.stream().filter(row -> matches(row, seasonOption)).toList();
                if (season.size() != 1) throw new StatsLookupException("No season found");
                yield new StatsPeriod(season.getFirst(), season, false);
            }
        };
    }

    public List<StatsChoice> choices(String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return choices.get("seasons", () -> counted().reversed().stream()
                        .map(season -> new StatsChoice(season.getDisplayLabel(), season.getId().toString())).toList()).stream()
                .filter(choice -> choice.label().toLowerCase(Locale.ROOT).contains(needle)).limit(25).toList();
    }

    private static boolean matches(Season season, String option) {
        try {
            return season.getId().equals(UUID.fromString(option));
        } catch (IllegalArgumentException label) {
            return season.getDisplayLabel().equalsIgnoreCase(option.strip());
        }
    }
}
