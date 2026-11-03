package org.ctc.domain.picks;

import java.util.UUID;
import org.ctc.domain.model.RaceSettingsValues;

public record PublicPickProjection(UUID raceId, String carName, String poolEntryLabel, String trackName, RaceSettingsValues settings) { }
