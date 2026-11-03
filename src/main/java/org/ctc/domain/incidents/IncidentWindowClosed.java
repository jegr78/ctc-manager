package org.ctc.domain.incidents;

import java.util.List;
import org.ctc.domain.model.IncidentReport;
import org.ctc.domain.model.IncidentReportWindow;

public record IncidentWindowClosed(IncidentReportWindow window, List<IncidentReport> reports) {
    public IncidentWindowClosed { reports = List.copyOf(reports); }
    @Override public String toString() { return "IncidentWindowClosed[private]"; }
}
