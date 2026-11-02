package org.ctc.domain.picks;

import java.util.UUID;

public record AuthorizedPickActor(UUID raceId, UUID seasonId, UUID teamId, String userId) { }
