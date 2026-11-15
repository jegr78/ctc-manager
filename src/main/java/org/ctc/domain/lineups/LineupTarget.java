package org.ctc.domain.lineups;

import java.time.Instant;
import java.util.UUID;
import org.ctc.domain.model.LineupStatus;

/** One leg and fielding team offered in a private team channel. */
public record LineupTarget(UUID raceId, UUID teamId, String label, LineupStatus status, long version,
                           boolean editable, String reason, Instant deadline, Instant start) { }
