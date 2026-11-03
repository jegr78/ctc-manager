package org.ctc.domain.incidents;

import org.ctc.domain.model.IncidentReport;
import org.ctc.domain.model.IncidentReportRevision;

public record IncidentSubmissionAccepted(IncidentReport report, IncidentReportRevision revision) {
    @Override public String toString() { return "IncidentSubmissionAccepted[private]"; }
}
