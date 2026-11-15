package org.ctc.domain.stats;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.Season;

/**
 * The seasons a stats answer covers: one season, or every counted season for alltime. {@code fallback} marks an
 * alltime answer given because no season is active.
 */
public record StatsPeriod(StatsSeason season, List<StatsSeason> seasons, boolean fallback) {
    public StatsPeriod { seasons = List.copyOf(seasons); }

    public static StatsPeriod of(Season season, List<Season> seasons, boolean fallback) {
        return new StatsPeriod(season == null ? null : StatsSeason.of(season), seasons.stream().map(StatsSeason::of).toList(), fallback);
    }

    public boolean alltime() { return season == null; }
    public String label() { return season == null ? "Alltime" : season.label(); }
    public List<UUID> seasonIds() { return seasons.stream().map(StatsSeason::id).toList(); }

    /** A counted season by ID and display label. */
    public record StatsSeason(UUID id, String label) {
        public static StatsSeason of(Season season) { return new StatsSeason(season.getId(), season.getDisplayLabel()); }
    }
}
