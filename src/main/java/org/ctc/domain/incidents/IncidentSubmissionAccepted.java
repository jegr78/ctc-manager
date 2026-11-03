package org.ctc.domain.incidents;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ctc.domain.model.IncidentReport;
import org.ctc.domain.model.IncidentReportRevision;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"}, matchType = edu.umd.cs.findbugs.annotations.SuppressMatchType.EXACT,
        justification = "Internal synchronous event shares locked managed JPA entities with MANDATORY listeners in the producer transaction; not a form or transport payload. See docs/security/sast-acceptance.md.")
public record IncidentSubmissionAccepted(IncidentReport report, IncidentReportRevision revision) {
    @Override public String toString() { return "IncidentSubmissionAccepted[private]"; }
}
