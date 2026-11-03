package org.ctc.domain.incidents;

import java.util.UUID;

public record IncidentMatchRef(UUID matchId, UUID playoffMatchupId) {
    public static IncidentMatchRef match(UUID id) { return new IncidentMatchRef(id, null); }
    public static IncidentMatchRef playoff(UUID id) { return new IncidentMatchRef(null, id); }
}
