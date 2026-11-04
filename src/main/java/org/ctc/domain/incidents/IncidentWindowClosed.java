package org.ctc.domain.incidents;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.ctc.domain.model.IncidentReport;
import org.ctc.domain.model.IncidentReportWindow;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"}, matchType = edu.umd.cs.findbugs.annotations.SuppressMatchType.EXACT,
        justification = "Internal synchronous event shares locked managed JPA entities with MANDATORY listeners in the producer transaction; not a form or transport payload. See docs/security/sast-acceptance.md.")
public record IncidentWindowClosed(IncidentReportWindow window, List<IncidentReport> reports) {
    public IncidentWindowClosed { reports = List.copyOf(reports); }
    @Override public String toString() { return "IncidentWindowClosed[private]"; }
}
