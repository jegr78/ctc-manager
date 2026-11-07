package org.ctc.domain.picks;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.LobbyRulesProfile;

public record PickTeamView(HomeRaceTarget target, PickPayload payload, SubmissionReceipt receipt,
                           String carLabel, String trackLabel, String deadline, boolean matchdayOpen,
                           boolean manualClosed, String returnReason, List<Choice> cars, List<Choice> tracks,
                           LobbyRulesProfile lobbyRulesProfile) {
    public PickTeamView(HomeRaceTarget target, PickPayload payload, SubmissionReceipt receipt, String carLabel,
                        String trackLabel, String deadline, boolean matchdayOpen, boolean manualClosed,
                        String returnReason, List<Choice> cars, List<Choice> tracks) {
        this(target, payload, receipt, carLabel, trackLabel, deadline, matchdayOpen, manualClosed, returnReason, cars, tracks,
                LobbyRulesProfile.LEGACY);
    }
    public PickTeamView { cars = List.copyOf(cars); tracks = List.copyOf(tracks); }
    @Override public String toString() { return "PickTeamView[raceId=" + target.raceId() + "]"; }
    public record Choice(UUID id, UUID entryId, String label, boolean oneMake, String entryLabel) {
        public Choice(UUID id, UUID entryId, String label, boolean oneMake) {
            this(id, entryId, label, oneMake, entryId == null ? null : label);
        }
        public Choice(UUID id, UUID entryId, String label) { this(id, entryId, label, false); }
    }
}
