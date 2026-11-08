package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final IncidentContextService contexts;

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
            var target = contexts.resolve(IncidentContextService.reference(window.getContext()));
            var ordered = target.races().stream().sorted(Comparator.comparingInt((Race race) -> race.getMatchday().getSortIndex())
                    .thenComparing(Race::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Race::getId)).toList();
            int leg = ordered.stream().map(Race::getId).toList().indexOf(report.getRace().getId()) + 1;
            if (leg < 1) throw new BusinessRuleException("The frozen report no longer identifies its concrete pairing race");
            var delivery = IncidentDelivery.capture(report, revision, IncidentDeliveryOperation.DISCLOSURE, window.getClosedAt());
            delivery.freezeReadableIdentity(target.home().getShortName() + " vs " + target.away().getShortName(),
                    "Leg " + leg + " · " + target.season().getName() + " · " + report.getOriginalMatchday().getLabel());
            queued.add(deliveries.save(delivery));
        }
        return List.copyOf(queued);
    }
}
