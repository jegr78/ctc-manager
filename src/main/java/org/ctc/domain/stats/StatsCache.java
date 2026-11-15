package org.ctc.domain.stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** A small bounded cache that keeps autocomplete lookups off the database for a minute. */
public final class StatsCache<V> {
    private static final long TTL_NANOS = 60_000_000_000L;
    private static final int MAX_ENTRIES = 64;
    private final Map<String, Entry<V>> entries = new ConcurrentHashMap<>();
    private final LongSupplier nanos;

    public StatsCache() { this(System::nanoTime); }
    public StatsCache(LongSupplier nanos) { this.nanos = nanos; }

    public V get(String key, Supplier<V> load) {
        long now = nanos.getAsLong();
        var cached = entries.get(key);
        if (cached != null && now - cached.loadedAt() < TTL_NANOS) return cached.value();
        V value = load.get();
        if (entries.size() >= MAX_ENTRIES) entries.clear();
        entries.put(key, new Entry<>(value, now));
        return value;
    }

    private record Entry<V>(V value, long loadedAt) { }
}
