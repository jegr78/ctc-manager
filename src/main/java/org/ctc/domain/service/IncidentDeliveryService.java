package org.ctc.domain.service;

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

    private IncidentDeliveryClaim claim(UUID id,String generation) {
        requireGeneration(generation);var row=locked(id);
        if(row==null || row.getOperation()!=IncidentDeliveryOperation.RECEIPT || row.getReport().getWindow().isDeliveryPaused()) return null;
        var now=now();
        if(row.getState()==IncidentDeliveryState.CLAIMED) {row.expireClaim(now);return null;}
        var detailed=rows.findByOperationKeyForUpdate(row.getReport().getId()+":DISCLOSURE");
        if(detailed.isPresent()) {
            em.refresh(detailed.orElseThrow(),LockModeType.PESSIMISTIC_WRITE);
            if(detailed.orElseThrow().getState()==IncidentDeliveryState.SENT) {row.supersede(now);return null;}
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
