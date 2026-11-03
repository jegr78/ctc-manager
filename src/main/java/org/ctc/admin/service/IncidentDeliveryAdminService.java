package org.ctc.admin.service;

import java.security.Principal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.*;
import org.ctc.domain.exception.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.IncidentDeliveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentDeliveryAdminService {
    private final IncidentAdminService incidents;
    private final IncidentDeliveryService delivery;
    private final IncidentDeliveryRepository rows;
    private final IncidentReportWindowRepository windows;
    @Transactional(readOnly=true)
    public Overview overview(UUID contextId) {
        var page=incidents.view(contextId);
        var window=windows.findByContextId(contextId).orElseThrow(() -> new BusinessRuleException("Reporting window is unavailable"));
        var entries=rows.findByReportWindowId(window.getId()).stream().sorted(Comparator.comparing(IncidentDelivery::getCaseReference).thenComparing(IncidentDelivery::getOperation)).map(this::entry).toList();
        return new Overview(contextId,page.seasonId(),page.pairing(),window.isDeliveryPaused(),window.getVersion(),delivery.reviewGeneration(),entries);
    }
    @Transactional(readOnly=true)
    public Review review(UUID id) {
        var row=rows.findById(id).orElseThrow(() -> new BusinessRuleException("Incident delivery is unavailable"));
        return new Review(row.getReport().getWindow().getContext().getId(),row.getReport().getWindow().getContext().getSeason().getId(),entry(row),row.getVersion(),delivery.reviewGeneration());
    }
    public UUID reconcile(UUID id,IncidentDeliveryReviewForm form,Principal principal) {
        boolean confirmed=form.getDecision()==IncidentDeliveryReviewForm.Decision.CONFIRM_SENT;
        if(form.getDecision()==null || confirmed && (form.getMessageId()==null || form.getMessageId().isBlank()))throw new BusinessRuleException("Enter the existing receipt message ID before confirming delivery");
        delivery.reconcile(id,form.getExpectedVersion(),incidents.actor(principal),form.getReason(),form.isReviewed(),confirmed?form.getMessageId():"",form.getGeneration());
        return review(id).contextId();
    }
    public void resume(UUID contextId,IncidentDeliveryResumeForm form,Principal principal) {
        var windowId=windows.findByContextId(contextId).orElseThrow(() -> new BusinessRuleException("Reporting window is unavailable")).getId();
        delivery.resumeWindow(windowId,form.getExpectedVersion(),incidents.actor(principal),form.getReason(),form.isReviewed(),form.getGeneration());
    }
    private Entry entry(IncidentDelivery row) {
        String state=switch(row.getState()) {case PENDING -> "Waiting";case CLAIMED -> "Sending or awaiting outcome";case SENT -> "Sent";case FAILED -> "Rejected, retry pending";case UNCERTAIN -> "Outcome unknown";case SUPERSEDED -> "Details already disclosed";};
        boolean editable=row.getState()!=IncidentDeliveryState.SENT && row.getState()!=IncidentDeliveryState.SUPERSEDED;
        return new Entry(row.getId(),row.getCaseReference(),row.getOperation()==IncidentDeliveryOperation.RECEIPT?"Neutral receipt":"Post-deadline disclosure",state,row.getMessageId(),row.getFailureCode(),row.getReconciledBy(),row.getReconciliationReason(),editable,row.getOperation()==IncidentDeliveryOperation.RECEIPT);
    }
    public record Entry(UUID id,String reference,String operation,String state,String messageId,String failure,String reviewer,String reason,boolean editable,boolean receipt) {}
    public record Overview(UUID contextId,UUID seasonId,String pairing,boolean paused,long version,String generation,List<Entry> entries) {public Overview {entries=List.copyOf(entries);}}
    public record Review(UUID contextId,UUID seasonId,Entry entry,long version,String generation) {}
}
