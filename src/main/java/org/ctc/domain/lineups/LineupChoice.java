package org.ctc.domain.lineups;

import java.util.UUID;

/** A driver of the fielding organisation, labelled with the team the driver is registered for. */
public record LineupChoice(UUID driverId, String psnId, String label) { }
