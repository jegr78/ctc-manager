package org.ctc.domain.picks;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.LobbyRulesProfile;

public record PickTeamView(HomeRaceTarget target, PickPayload payload, SubmissionReceipt receipt,
                           String carLabel, String trackLabel, String deadline, boolean manualOpen,
                           boolean manualClosed, String returnReason, List<Choice> cars, List<Choice> tracks,
                           LobbyRulesProfile lobbyRulesProfile) {
    public PickTeamView(HomeRaceTarget target, PickPayload payload, SubmissionReceipt receipt, String carLabel,
                        String trackLabel, String deadline, boolean manualOpen, boolean manualClosed,
                        String returnReason, List<Choice> cars, List<Choice> tracks) {
        this(target, payload, receipt, carLabel, trackLabel, deadline, manualOpen, manualClosed, returnReason, cars, tracks,
                LobbyRulesProfile.LEGACY);
    }
    public PickTeamView { cars = List.copyOf(cars); tracks = List.copyOf(tracks); }
    @Override public String toString() { return "PickTeamView[raceId=" + target.raceId() + "]"; }
    public record Choice(UUID id, UUID entryId, String label) { }
}
