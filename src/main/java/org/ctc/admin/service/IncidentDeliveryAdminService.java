package org.ctc.admin.service;

import java.security.Principal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.*;
import org.ctc.domain.exception.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.IncidentDeliveryService;
import org.ctc.domain.service.IncidentThreadPublicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentDeliveryAdminService {
    private final IncidentAdminService incidents;
    private final IncidentDeliveryService delivery;
    private final IncidentThreadPublicationService threads;
    private final IncidentThreadPublicationRepository publications;
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
        if(form.getDecision()==null || confirmed && (form.getMessageId()==null || form.getMessageId().isBlank()))throw new BusinessRuleException("Enter the existing message or thread ID before confirming delivery");
        var row=rows.findById(id).orElseThrow(() -> new BusinessRuleException("Incident delivery is unavailable"));
        if(row.getOperation()==IncidentDeliveryOperation.RECEIPT)delivery.reconcile(id,form.getExpectedVersion(),incidents.actor(principal),form.getReason(),form.isReviewed(),confirmed?form.getMessageId():"",form.getGeneration());
        else threads.reconcile(id,form.getExpectedVersion(),incidents.actor(principal),form.getReason(),form.isReviewed(),confirmed?form.getMessageId():"",form.getGeneration());
        return review(id).contextId();
    }
    public void resume(UUID contextId,IncidentDeliveryResumeForm form,Principal principal) {
        var windowId=windows.findByContextId(contextId).orElseThrow(() -> new BusinessRuleException("Reporting window is unavailable")).getId();
        delivery.resumeWindow(windowId,form.getExpectedVersion(),incidents.actor(principal),form.getReason(),form.isReviewed(),form.getGeneration());
    }
    private Entry entry(IncidentDelivery row) {
        String state=switch(row.getState()) {case PENDING -> "Waiting";case CLAIMED -> "Sending or awaiting outcome";case SENT -> "Sent";case FAILED -> "Rejected, retry pending";case UNCERTAIN -> "Outcome unknown";case SUPERSEDED -> "Details already disclosed";};
        boolean editable=row.getState()!=IncidentDeliveryState.SENT && row.getState()!=IncidentDeliveryState.SUPERSEDED;
        var publication=publications.findByDeliveryId(row.getId()).orElse(null);
        String step=publication==null?null:switch(publication.stage()) {
            case PRIVATE_STARTER -> "Private starter";case PRIVATE_THREAD -> "Private thread";case PRIVATE_BODY -> "Private facts, part "+(publication.chunkIndex()+1);
            case PUBLIC_STARTER -> "Public starter";case PUBLIC_THREAD -> "Public thread";case PUBLIC_BODY -> "Public facts, part "+(publication.chunkIndex()+1);
            case PRIVATE_REFERENCE -> "Private reference to match discussion";case COMPLETE -> "Complete";
        };
        if(row.getOperation()==IncidentDeliveryOperation.DISCLOSURE && row.getState()==IncidentDeliveryState.PENDING && row.getReport().getWindow().getDeliveryResumedAt()!=null && !row.isRestoreReviewed())state="Remote review required for next restored step";
        return new Entry(row.getId(),row.getCaseReference(),row.getOperation()==IncidentDeliveryOperation.RECEIPT?"Neutral receipt":"Post-deadline disclosure",state,row.getMessageId(),row.getFailureCode(),row.getReconciledBy(),row.getReconciliationReason(),editable,row.getOperation()==IncidentDeliveryOperation.RECEIPT,step,
                publication==null?null:threadUrl(row,publication.getPrivateThreadId()),publication==null?null:threadUrl(row,publication.getPublicThreadId()));
    }
    private static String threadUrl(IncidentDelivery row,String threadId) {return threadId==null?null:"https://discord.com/channels/"+row.getGuildId()+"/"+threadId;}
    public record Entry(UUID id,String reference,String operation,String state,String messageId,String failure,String reviewer,String reason,boolean editable,boolean receipt,String step,String privateThreadUrl,String publicThreadUrl) {}
    public record Overview(UUID contextId,UUID seasonId,String pairing,boolean paused,long version,String generation,List<Entry> entries) {public Overview {entries=List.copyOf(entries);}}
    public record Review(UUID contextId,UUID seasonId,Entry entry,long version,String generation) {}
}
