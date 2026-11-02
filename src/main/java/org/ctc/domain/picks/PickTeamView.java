package org.ctc.domain.picks;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PickPayload;

public record PickTeamView(HomeRaceTarget target, PickPayload payload, SubmissionReceipt receipt,
                           String carLabel, String trackLabel, String deadline, boolean manualOpen,
                           boolean manualClosed, String returnReason, List<Choice> cars, List<Choice> tracks) {
    public PickTeamView { cars = List.copyOf(cars); tracks = List.copyOf(tracks); }
    @Override public String toString() { return "PickTeamView[raceId=" + target.raceId() + "]"; }
    public record Choice(UUID id, UUID entryId, String label) { }
}
