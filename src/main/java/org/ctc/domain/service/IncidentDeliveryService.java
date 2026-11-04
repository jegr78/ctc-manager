package org.ctc.domain.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.IncidentDeliveryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IncidentDeliveryService {
    private final IncidentDeliveryRepository rows;
    private final IncidentWindowService windows;
    private final ImportLockService imports;
    private final TransactionTemplate transactions;
    private final EntityManager em;
    private final Clock clock;
    private final PickSessionEpoch epoch;
    private final IncidentReceiptSender sender;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", matchType = edu.umd.cs.findbugs.annotations.SuppressMatchType.EXACT,
            justification = "Spring injects the shared transaction-scoped EntityManager proxy; copying it would break persistence context identity. See docs/security/sast-acceptance.md.")
    public IncidentDeliveryService(IncidentDeliveryRepository rows, IncidentWindowService windows, ImportLockService imports,
                                   PlatformTransactionManager transactions, EntityManager em, Clock clock, PickSessionEpoch epoch, IncidentReceiptSender sender) {
        this.rows=rows;this.windows=windows;this.imports=imports;this.transactions=new TransactionTemplate(transactions);
        this.em=em;this.clock=clock;this.epoch=epoch;this.sender=sender;
    }

    public boolean dispatch(UUID id) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Incident dispatch admission must precede its transaction");
        String generation=epoch.managementGeneration();
        if (!imports.tryEnterWriter()) return false;
        try {
            var claim=transactions.execute(status -> claim(id,generation));
            if (claim==null) return false;
            requireGeneration(generation);
            IncidentSendResult result;
            try { result=sender.send(claim); }
            catch (RuntimeException failed) { result=new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN,null,"SEND_UNCERTAIN"); }
            var outcome=normalize(result);
            transactions.executeWithoutResult(status -> {
                requireGeneration(generation);var row=locked(id);
                if(row==null) throw unavailable();
                var now=now();
                if(outcome.disposition()==IncidentSendResult.Disposition.SENT) row.acknowledge(claim.token(),claim.version(),generation,now,outcome.messageId());
                else row.fail(claim.token(),claim.version(),generation,now,outcome.code(),outcome.disposition()==IncidentSendResult.Disposition.UNCERTAIN,now.plusSeconds(60));
            });
            return true;
        } finally { imports.exitWriter(); }
    }

    public void reconcile(UUID id,long expectedVersion,IncidentManagementActor actor,String reason,boolean reviewed,String messageId,String generation) {
        requireReview(actor,reason,reviewed,generation);
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Incident review admission must precede its transaction");
        if(!imports.tryEnterWriter())throw unavailable();
        try {
            String remote=messageId==null?"":messageId;
            if(!remote.isEmpty()) {
                if(!remote.matches("[0-9]{1,32}"))throw unavailable();
                var claim=transactions.execute(status -> {
                    requireGeneration(generation);var row=locked(id);if(row==null)throw unavailable();row.requireReview(expectedVersion,now());
                    if(row.getOperation()!=IncidentDeliveryOperation.RECEIPT || row.getState()==IncidentDeliveryState.PENDING && !row.getReport().getWindow().isDeliveryPaused())throw unavailable();
                    return new IncidentDeliveryClaim(row.getId(),row.getReport().getWindow().getId(),row.getClaimToken(),row.getVersion(),generation,row.getOperation(),row.getGuildId(),row.getChannelId(),row.getNonce());
                });
                IncidentSendResult verified;
                try {verified=sender.verify(claim,remote);}catch(RuntimeException failed){throw unavailable();}
                if(verified==null || verified.disposition()!=IncidentSendResult.Disposition.SENT || !remote.equals(verified.messageId()))throw new BusinessRuleException("The existing bot receipt could not be verified; keep its outcome unresolved");
                transactions.executeWithoutResult(status -> {requireGeneration(generation);var row=locked(id);if(row==null)throw unavailable();row.reconcileDelivered(expectedVersion,actor.id(),reason,now(),remote);});
            } else transactions.executeWithoutResult(status -> {requireGeneration(generation);var row=locked(id);if(row==null || row.getOperation()!=IncidentDeliveryOperation.RECEIPT)throw unavailable();row.reconcileMissing(expectedVersion,actor.id(),reason,now());});
        } finally {imports.exitWriter();}
    }
    public void resumeWindow(UUID id,long expectedVersion,IncidentManagementActor actor,String reason,boolean reviewed,String generation) {
        requireReview(actor,reason,reviewed,generation);
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Incident review admission must precede its transaction");
        if(!imports.tryEnterWriter())throw unavailable();
        try {transactions.executeWithoutResult(status -> {
            requireGeneration(generation);var window=windows.lock(id);window.requireVersion(expectedVersion);
            for(var row:rows.findByWindowForUpdate(id)) {
                em.refresh(row,LockModeType.PESSIMISTIC_WRITE);
                if(row.getState()==IncidentDeliveryState.CLAIMED || row.getState()==IncidentDeliveryState.UNCERTAIN
                        || (row.getState()==IncidentDeliveryState.PENDING || row.getState()==IncidentDeliveryState.FAILED) && !row.isRestoreReviewed())throw new BusinessRuleException("Review every unresolved restored delivery before resuming this context");
            }
            window.resumeDelivery(actor.id(),reason,now());
        });} finally {imports.exitWriter();}
    }
    public String reviewGeneration() { return epoch.managementGeneration(); }
    private void requireReview(IncidentManagementActor actor,String reason,boolean reviewed,String generation) {
        IncidentContextService.requireManager(actor);
        if(!reviewed || reason==null || reason.isBlank() || reason.length()>1000 || generation==null)throw new BusinessRuleException("Confirm your external delivery review and give its reason");
        requireGeneration(generation);
    }

    private IncidentDeliveryClaim claim(UUID id,String generation) {
        requireGeneration(generation);var row=locked(id);
        if(row==null || row.getOperation()!=IncidentDeliveryOperation.RECEIPT || row.getReport().getWindow().isDeliveryPaused()) return null;
        var now=now();
        if(row.getState()==IncidentDeliveryState.CLAIMED) {row.expireClaim(now);return null;}
        var detailed=rows.findByOperationKeyForUpdate(row.getReport().getId()+":DISCLOSURE");
        if(detailed.isPresent()) {
            em.refresh(detailed.orElseThrow(),LockModeType.PESSIMISTIC_WRITE);
            var disclosure=detailed.orElseThrow();
            var publication=em.createQuery("select p from IncidentThreadPublication p where p.delivery.id=:id",IncidentThreadPublication.class)
                    .setParameter("id",disclosure.getId()).getResultList().stream().findFirst().orElse(null);
            if(disclosure.getState()==IncidentDeliveryState.SENT || publication!=null && publication.publicComplete()) {row.supersede(now);return null;}
            if(disclosure.getState()==IncidentDeliveryState.CLAIMED || disclosure.getState()==IncidentDeliveryState.UNCERTAIN)return null;
        }
        if(row.getState()!=IncidentDeliveryState.PENDING && row.getState()!=IncidentDeliveryState.FAILED || now.isBefore(row.getNextAttemptAt())) return null;
        var token=UUID.randomUUID();row.claim(token,generation,now,now.plusSeconds(600));
        return new IncidentDeliveryClaim(row.getId(),row.getReport().getWindow().getId(),token,row.getVersion(),generation,row.getOperation(),row.getGuildId(),row.getChannelId(),row.getNonce());
    }

    private IncidentDelivery locked(UUID id) {
        if(id==null) return null;
        var snapshot=rows.findById(id).orElse(null);if(snapshot==null) return null;
        var window=windows.lock(snapshot.getReport().getWindow().getId());
        var row=rows.findByIdForUpdate(id).orElse(null);if(row==null) return null;
        em.refresh(row,LockModeType.PESSIMISTIC_WRITE);em.refresh(row.getReport(),LockModeType.PESSIMISTIC_READ);
        if(!row.getReport().getWindow().getId().equals(window.getId()) || !row.getGuildId().equals(window.getContext().getGuildId())
                || !row.getChannelId().equals(window.getContext().getChannelId())) throw unavailable();
        return row;
    }

    @Transactional(readOnly=true)
    public List<UUID> dueReceipts(int limit) { return rows.findDueReceiptIds(now(),page(limit)); }
    @Transactional(readOnly=true)
    public List<UUID> dueWindows(int limit) { return windows.dueWindowIds(now(),page(limit)); }
    private static PageRequest page(int limit) { if(limit<1 || limit>25) throw new IllegalArgumentException("Incident due-work batches must contain between 1 and 25 rows");return PageRequest.of(0,limit); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private void requireGeneration(String generation) { if(!generation.equals(epoch.managementGeneration())) throw unavailable(); }
    private static IncidentSendResult normalize(IncidentSendResult result) {
        if(result==null || result.disposition()==null || result.disposition()==IncidentSendResult.Disposition.SENT && (result.messageId()==null || !result.messageId().matches("[0-9]{1,32}"))) return new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN,null,"INVALID_RESPONSE");
        if(result.disposition()!=IncidentSendResult.Disposition.SENT && (result.code()==null || !result.code().matches("[A-Z0-9_]{1,64}"))) return new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN,null,"SEND_UNCERTAIN");
        return result;
    }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("Incident delivery changed or was reset; inspect its current state"); }
}
