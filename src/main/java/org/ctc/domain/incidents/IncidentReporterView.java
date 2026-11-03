package org.ctc.domain.incidents;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.IncidentPayload;

public record IncidentReporterView(UUID contextId, UUID windowId, long windowVersion, boolean editable,
                                   String label, List<RaceChoice> races, List<CaseChoice> cases) {
    public IncidentReporterView { races = List.copyOf(races); cases = List.copyOf(cases); }
    @Override public String toString() { return "IncidentReporterView[redacted]"; }
    public record RaceChoice(UUID id, String label) {
        @Override public String toString() { return "IncidentRaceChoice[redacted]"; }
    }
    public record CaseChoice(UUID id, UUID raceId, String reference, String state, String party, long version, int revision, boolean editable) {
        @Override public String toString() { return "IncidentCaseChoice[redacted]"; }
    }
    public record CaseDetail(CaseChoice summary, IncidentPayload payload) {
        @Override public String toString() { return "IncidentPrivateCase[redacted]"; }
    }
}
