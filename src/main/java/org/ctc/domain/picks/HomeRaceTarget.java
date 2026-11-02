package org.ctc.domain.picks;

import java.util.UUID;
import org.ctc.domain.model.PickState;

public record HomeRaceTarget(UUID raceId, String label, PickState state, long version, boolean editable, String reason) { }
