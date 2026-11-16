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
    /** The short name shown to members: "Alltime" or the season title. */
    public String title() { return season == null ? "Alltime" : season.title(); }
    public List<UUID> seasonIds() { return seasons.stream().map(StatsSeason::id).toList(); }

    /** A counted season by ID, its display label (the key for profile links and season choices) and its short title. */
    public record StatsSeason(UUID id, String label, String title) {
        public static StatsSeason of(Season season) { return new StatsSeason(season.getId(), season.getDisplayLabel(), title(season)); }

        /** The season name, with its year in parentheses unless the name already contains it. */
        public static String title(Season season) {
            String year = String.valueOf(season.getYear());
            return season.getName().contains(year) ? season.getName() : season.getName() + " (" + year + ")";
        }
    }
}
