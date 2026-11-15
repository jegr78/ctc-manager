package org.ctc.domain.stats;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/** Autocomplete candidates of one entity kind, cached per resolved season scope. */
public final class StatsCandidates {
    private final StatsCache<List<Candidate>> cache = new StatsCache<>();

    /** The cached candidates under {@code key} whose names contain the query, as at most 25 choices. */
    public List<StatsChoice> choices(String key, Supplier<List<Candidate>> load, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return cache.get(key, load).stream()
                .filter(candidate -> needle.isEmpty() || candidate.names().stream().anyMatch(name -> name.contains(needle)))
                .limit(25).map(candidate -> new StatsChoice(candidate.label(), candidate.id().toString())).toList();
    }

    public void clear() { cache.clear(); }

    /** The UUID an autocomplete choice carries, or null for typed text. */
    public static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value.strip());
        } catch (IllegalArgumentException name) {
            return null;
        }
    }

    /** A selectable entity with its label and the names a query matches against. */
    public record Candidate(UUID id, String label, List<String> names) {
        public Candidate { names = List.copyOf(names.stream().map(name -> name.toLowerCase(Locale.ROOT)).toList()); }
    }
}
