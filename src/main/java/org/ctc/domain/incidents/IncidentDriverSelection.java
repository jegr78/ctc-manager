package org.ctc.domain.incidents;

import java.util.List;
import java.util.UUID;

public record IncidentDriverSelection(UUID raceId, String fingerprint, List<UUID> driverIds) {
    public IncidentDriverSelection { driverIds = List.copyOf(driverIds); }
    @Override public String toString() { return "IncidentDriverSelection[private]"; }
}
