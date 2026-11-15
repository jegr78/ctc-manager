package org.ctc.domain.stats;

import java.util.List;
import org.ctc.domain.model.Season;

/**
 * The seasons a stats answer covers: one season, or every counted season for alltime. {@code fallback} marks an
 * alltime answer given because no season is active.
 */
public record StatsPeriod(Season season, List<Season> seasons, boolean fallback) {
    public StatsPeriod { seasons = List.copyOf(seasons); }
    public boolean alltime() { return season == null; }
    public String label() { return season == null ? "Alltime" : season.getDisplayLabel(); }
}
