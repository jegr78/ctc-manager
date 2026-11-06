package org.ctc.domain.model;

import java.util.Collection;
import java.util.List;

/** Race-position classification; recorded positions and bonus-point inputs remain unchanged. */
public final class RaceClassification {
    private RaceClassification() { }

    public static boolean hasUniqueFinishingPositions(Collection<RaceResult> results) {
        return results.stream().allMatch(r -> r.getPosition() >= 1 && r.getPosition() <= 12)
                && results.stream().map(RaceResult::getPosition).distinct().count() == results.size();
    }

    public static int position(RaceResult result) {
        var race = result.getRace();
        return position(result, race == null ? List.of(result) : race.getResults());
    }

    public static int position(RaceResult result, Collection<RaceResult> results) {
        if (result.isDisqualified()) return 0;
        int removed = 0;
        for (var other : results) {
            if (other.isDisqualified() && sameRace(result, other) && other.getPosition() < result.getPosition()) removed++;
        }
        return result.getPosition() - removed;
    }

    private static boolean sameRace(RaceResult first, RaceResult second) {
        var race = first.getRace();
        var other = second.getRace();
        return race != null && (race == other || other != null && race.getId() != null && race.getId().equals(other.getId()));
    }
}
