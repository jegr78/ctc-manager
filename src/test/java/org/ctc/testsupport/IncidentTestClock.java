package org.ctc.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class IncidentTestClock extends Clock {
    private final AtomicReference<Instant> now;
    private final ZoneId zone;
    public IncidentTestClock() { this(new AtomicReference<>(Instant.parse("2026-11-03T12:00:00Z")), ZoneOffset.UTC); }
    private IncidentTestClock(AtomicReference<Instant> now, ZoneId zone) { this.now = now; this.zone = zone; }
    public void set(Instant value) { now.set(value); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId value) { return new IncidentTestClock(now, value); }
    @Override public Instant instant() { return now.get(); }
}
