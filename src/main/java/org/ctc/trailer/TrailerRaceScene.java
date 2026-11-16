package org.ctc.trailer;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PickPublicationContent;

/** One track + car and settings scene; identical legs of a pairing share it. */
public record TrailerRaceScene(UUID raceId, String pairingLabel, String homeTeam, String awayTeam, String homeLogo,
                               String awayLogo, List<Integer> legNumbers, PickPublicationContent content, UUID trackId,
                               String trackName, String trackShortName, UUID carId, String carName, String homeStanding,
                               String awayStanding) {
}
