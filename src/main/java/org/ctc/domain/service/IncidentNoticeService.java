package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentSubmissionAccepted;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.IncidentDeliveryRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentNoticeService {
    private final IncidentDeliveryRepository deliveries;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void submitted(IncidentSubmissionAccepted event) { enqueueFirstSubmission(event.report(), event.revision()); }

    @Transactional(propagation = Propagation.MANDATORY)
    public IncidentDelivery enqueueFirstSubmission(IncidentReport report, IncidentReportRevision revision) {
        if (report == null || report.getId() == null || revision == null || revision.getReport() == null
                || !report.getId().equals(revision.getReport().getId()) || revision.getRevisionNumber() != 1) {
            throw new BusinessRuleException("A neutral receipt requires the report's first confirmed revision");
        }
        return deliveries.findByOperationKey(report.getId() + ":RECEIPT").orElseGet(() ->
                deliveries.save(IncidentDelivery.capture(report, revision, IncidentDeliveryOperation.RECEIPT, revision.getAcceptedAt())));
    }
    public static String neutralText() { return "An incident report has been received. Details remain private until the reporting deadline."; }
}
