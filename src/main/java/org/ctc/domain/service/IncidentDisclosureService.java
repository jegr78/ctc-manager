package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentWindowClosed;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentDisclosureService {
    private final IncidentDeliveryRepository deliveries;
    private final IncidentReportRevisionRepository revisions;
    private final IncidentWindowService windows;

    public boolean freezeDueWindow(UUID windowId) { return windows.closeDue(windowId, null); }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void closed(IncidentWindowClosed event) { captureClosedWindow(event.window(), event.reports()); }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<IncidentDelivery> captureClosedWindow(IncidentReportWindow window, List<IncidentReport> reports) {
        if (window == null || window.getState() != IncidentWindowState.CLOSED || window.getClosedAt() == null
                || window.getDeadline() == null || window.getClosedAt().isBefore(window.getDeadline())) {
            throw new BusinessRuleException("Incident disclosure requires a confirmed closed reporting window");
        }
        var queued = new ArrayList<IncidentDelivery>();
        for (var report : reports) {
            if (!window.getId().equals(report.getWindow().getId())) throw new BusinessRuleException("Incident disclosure belongs to another reporting window");
            if (report.getState() != IncidentReportState.ACTIVE) continue;
            var existing = deliveries.findByOperationKey(report.getId() + ":DISCLOSURE");
            if (existing.isPresent()) { queued.add(existing.orElseThrow()); continue; }
            if (report.getFrozenRevisionNumber() == null || report.getFrozenRevisionNumber() != report.getLatestRevisionNumber()) throw new BusinessRuleException("Incident disclosure requires its final frozen revision");
            var revision = revisions.findByReportIdAndRevisionNumber(report.getId(), report.getFrozenRevisionNumber())
                    .orElseThrow(() -> new BusinessRuleException("The frozen incident revision is unavailable"));
            queued.add(deliveries.save(IncidentDelivery.capture(report, revision, IncidentDeliveryOperation.DISCLOSURE, window.getClosedAt())));
        }
        return List.copyOf(queued);
    }
}
