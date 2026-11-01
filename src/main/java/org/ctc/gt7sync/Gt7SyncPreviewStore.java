package org.ctc.gt7sync;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class Gt7SyncPreviewStore {
    private final Clock clock;
    private final Duration lifetime;
    private final int maximum;
    private final Map<UUID, Snapshot> snapshots = new LinkedHashMap<>();

    public Gt7SyncPreviewStore() { this(Clock.systemUTC(), Duration.ofMinutes(20), 64); }

    Gt7SyncPreviewStore(Clock clock, Duration lifetime, int maximum) {
        if (lifetime.isNegative() || lifetime.isZero() || maximum < 1) throw new IllegalArgumentException("Invalid preview limits");
        this.clock = clock;
        this.lifetime = lifetime;
        this.maximum = maximum;
    }

    public synchronized UUID capture(Map<String, String> hashes) {
        Instant now = clock.instant();
        snapshots.entrySet().removeIf(entry -> !entry.getValue().createdAt().plus(lifetime).isAfter(now));
        while (snapshots.size() >= maximum) snapshots.remove(snapshots.keySet().iterator().next());
        UUID id = UUID.randomUUID();
        snapshots.put(id, new Snapshot(now, Map.copyOf(hashes)));
        return id;
    }

    public synchronized boolean matches(UUID id, String carId, String hash) {
        var snapshot = snapshots.get(id);
        return snapshot != null && snapshot.createdAt().plus(lifetime).isAfter(clock.instant())
                && hash != null && Objects.equals(snapshot.hashes().get(carId), hash);
    }

    private record Snapshot(Instant createdAt, Map<String, String> hashes) {}
}
