package org.ctc.domain.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IncidentThreadPublicationService {
    private final IncidentDeliveryRepository deliveries;
    private final IncidentThreadPublicationRepository publications;
    private final IncidentWindowService windows;
    private final DiscordGlobalConfigRepository configs;
    private final ImportLockService imports;
    private final TransactionTemplate transactions;
    private final EntityManager em;
    private final Clock clock;
    private final PickSessionEpoch epoch;
    private final IncidentThreadSender sender;

    @SuppressFBWarnings(value="EI_EXPOSE_REP2",matchType=edu.umd.cs.findbugs.annotations.SuppressMatchType.EXACT,
            justification="Spring injects its transaction-scoped EntityManager proxy; copying it would break locking context identity. See docs/security/sast-acceptance.md.")
    public IncidentThreadPublicationService(IncidentDeliveryRepository deliveries,IncidentThreadPublicationRepository publications,
            IncidentWindowService windows,DiscordGlobalConfigRepository configs,ImportLockService imports,
            PlatformTransactionManager manager,EntityManager em,Clock clock,PickSessionEpoch epoch,IncidentThreadSender sender) {
        this.deliveries=deliveries;this.publications=publications;this.windows=windows;this.configs=configs;this.imports=imports;
        this.transactions=new TransactionTemplate(manager);this.em=em;this.clock=clock;this.epoch=epoch;this.sender=sender;
    }

    public boolean dispatch(UUID id) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Thread dispatch admission must precede its transaction");
        String generation=epoch.managementGeneration();if(!imports.tryEnterWriter())return false;
        try {
            var claim=transactions.execute(status -> claim(id,generation));if(claim==null)return false;
            requireGeneration(generation);IncidentThreadResult result;
            try {result=sender.send(claim);}catch(RuntimeException failure){result=uncertain();}
            var outcome=normalize(result);
            transactions.executeWithoutResult(status -> {
                requireGeneration(generation);var row=locked(id);if(row==null)throw unavailable();
                var mapping=mapping(row);var now=now();
                if(outcome.disposition()==IncidentSendResult.Disposition.SENT) {
                    if(mapping.stage()!=claim.work().stage() || mapping.chunkIndex()!=claim.work().chunkIndex())throw unavailable();
                    mapping.accept(claim.work().stage(),claim.work().chunkIndex(),outcome.messageId(),outcome.threadId());
                    if(mapping.stage()==IncidentThreadStage.COMPLETE)row.acknowledge(claim.token(),claim.version(),generation,now,mapping.getPublicThreadId());
                    else row.continueDisclosure(claim.token(),claim.version(),generation,now);
                } else row.fail(claim.token(),claim.version(),generation,now,outcome.code(),outcome.disposition()==IncidentSendResult.Disposition.UNCERTAIN,now.plusSeconds(60));
            });
            return true;
        } catch(RuntimeException failure) {throw RaceSelectionConcurrency.translate(failure)==failure?failure:unavailable();}
        finally {imports.exitWriter();}
    }

    public void reconcile(UUID id,long expectedVersion,IncidentManagementActor actor,String reason,boolean reviewed,String remoteId,String generation) {
        IncidentContextService.requireManager(actor);
        if(!reviewed || reason==null || reason.isBlank() || reason.length()>1000 || generation==null)throw new BusinessRuleException("Confirm your external delivery review and give its reason");
        requireGeneration(generation);
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Thread review admission must precede its transaction");
        if(!imports.tryEnterWriter())throw unavailable();
        try {
            String remote=remoteId==null?"":remoteId;
            if(!remote.isEmpty() && !remote.matches("[0-9]{1,20}"))throw unavailable();
            var review=transactions.execute(status -> {
                requireGeneration(generation);var row=locked(id);
                if(row==null || row.getOperation()!=IncidentDeliveryOperation.DISCLOSURE)throw unavailable();
                row.requireReview(expectedVersion,now());var projection=IncidentThreadProjection.capture(row);
                var mapping=captureMapping(row,projection);if(mapping==null || mapping.stage()==IncidentThreadStage.COMPLETE)throw unavailable();
                var previous=new java.util.ArrayList<Completed>();
                if(mapping.getPrivateThreadId()!=null)previous.add(new Completed(snapshot(row,mapping,projection,generation,IncidentThreadStage.PRIVATE_THREAD,0),mapping.getPrivateThreadId(),mapping.getPrivateStarterId(),mapping.getPrivateThreadId()));
                else if(mapping.getPrivateStarterId()!=null)previous.add(new Completed(snapshot(row,mapping,projection,generation,IncidentThreadStage.PRIVATE_STARTER,0),mapping.getPrivateStarterId(),mapping.getPrivateStarterId(),null));
                for(int index=0;index<mapping.privateMessages().size();index++) {String message=mapping.privateMessages().get(index);previous.add(new Completed(snapshot(row,mapping,projection,generation,IncidentThreadStage.PRIVATE_BODY,index+1),message,message,null));}
                if(mapping.getPublicThreadId()!=null)previous.add(new Completed(snapshot(row,mapping,projection,generation,IncidentThreadStage.PUBLIC_THREAD,0),mapping.getPublicThreadId(),mapping.getPublicStarterId(),mapping.getPublicThreadId()));
                else if(mapping.getPublicStarterId()!=null)previous.add(new Completed(snapshot(row,mapping,projection,generation,IncidentThreadStage.PUBLIC_STARTER,0),mapping.getPublicStarterId(),mapping.getPublicStarterId(),null));
                for(int index=0;index<mapping.publicMessages().size();index++) {String message=mapping.publicMessages().get(index);previous.add(new Completed(snapshot(row,mapping,projection,generation,IncidentThreadStage.PUBLIC_BODY,index+1),message,message,null));}
                return new Review(snapshot(row,mapping,projection,generation),previous);
            });
            for(var previous:review.previous()) {
                var verified=verify(previous.claim(),previous.remote());
                if(verified.disposition()!=IncidentSendResult.Disposition.SENT || !previous.message().equals(verified.messageId()) || !Objects.equals(previous.thread(),verified.threadId()))throw unavailable();
            }
            var verified=verify(review.claim(),remote);
            boolean adopted=verified.disposition()==IncidentSendResult.Disposition.SENT;
            if(adopted ? !remote.isEmpty() && !remote.equals(verified.messageId()) && !remote.equals(verified.threadId())
                    : verified.disposition()!=IncidentSendResult.Disposition.REJECTED || !"PROVEN_MISSING".equals(verified.code())) {
                throw new BusinessRuleException("The existing thread step or its absence could not be verified; keep the outcome unresolved");
            }
            transactions.executeWithoutResult(status -> {
                requireGeneration(generation);var row=locked(id);if(row==null)throw unavailable();row.requireReview(expectedVersion,now());
                var mapping=mapping(row);var work=review.claim().work();
                if(mapping.stage()!=work.stage() || mapping.chunkIndex()!=work.chunkIndex())throw unavailable();
                if(adopted) {
                    mapping.accept(work.stage(),work.chunkIndex(),verified.messageId(),verified.threadId());
                    row.reconcileDisclosureStep(expectedVersion,actor.id(),reason,now(),mapping.stage()==IncidentThreadStage.COMPLETE?mapping.getPublicThreadId():null);
                } else row.reconcileMissing(expectedVersion,actor.id(),reason,now());
            });
        } catch(RuntimeException failure) {throw RaceSelectionConcurrency.translate(failure)==failure?failure:unavailable();}
        finally {imports.exitWriter();}
    }

    private IncidentThreadClaim claim(UUID id,String generation) {
        requireGeneration(generation);var row=locked(id);
        if(row==null || row.getOperation()!=IncidentDeliveryOperation.DISCLOSURE || row.getReport().getWindow().isDeliveryPaused())return null;
        var now=now();var window=row.getReport().getWindow();
        if(window.getState()!=IncidentWindowState.CLOSED || window.getDeadline()==null || now.isBefore(window.getDeadline()))return null;
        if(row.getState()==IncidentDeliveryState.CLAIMED){row.expireClaim(now);return null;}
        if(window.getDeliveryResumedAt()!=null && !row.isRestoreReviewed())return null;
        if(row.getState()!=IncidentDeliveryState.PENDING && row.getState()!=IncidentDeliveryState.FAILED || now.isBefore(row.getNextAttemptAt()))return null;
        var receipt=deliveries.findByOperationKeyForUpdate(row.getReport().getId()+":RECEIPT").orElse(null);
        if(receipt!=null) {
            em.refresh(receipt,LockModeType.PESSIMISTIC_WRITE);
            if(receipt.getState()==IncidentDeliveryState.CLAIMED){receipt.expireClaim(now);return null;}
            if(receipt.getState()==IncidentDeliveryState.UNCERTAIN)return null;
        }
        var projection=IncidentThreadProjection.capture(row);
        var mapping=captureMapping(row,projection);if(mapping==null)return null;
        if(mapping.stage()==IncidentThreadStage.COMPLETE || mapping.getMessageChunks()!=projection.messages().size())throw unavailable();
        var token=UUID.randomUUID();row.claim(token,generation,now,now.plusSeconds(600));
        return snapshot(row,mapping,projection,generation);
    }

    private IncidentThreadPublication captureMapping(IncidentDelivery row,IncidentThreadProjection.Payload projection) {
        var mapping=publications.findByDeliveryIdForUpdate(row.getId()).orElse(null);
        if(mapping!=null)em.refresh(mapping,LockModeType.PESSIMISTIC_WRITE);
        else {
            var config=configs.findFirstByOrderByIdAsc();
            if(config==null || !row.getGuildId().equals(config.getGuildId()) || config.getStewardsChannelId().isEmpty() || config.getIncidentStaffRoleIds().isEmpty())return null;
            mapping=publications.save(IncidentThreadPublication.capture(row,config.getStewardsChannelId(),config.getIncidentStaffRoleIds(),config.getStewardsForumTagIds(),projection.messages().size()));
        }
        if(mapping.getMessageChunks()!=projection.messages().size())throw unavailable();return mapping;
    }
    private IncidentThreadResult verify(IncidentThreadClaim claim,String remote) {
        try {return normalize(sender.verify(claim,remote));}catch(RuntimeException failure){throw unavailable();}
    }

    private IncidentThreadClaim snapshot(IncidentDelivery row,IncidentThreadPublication mapping,IncidentThreadProjection.Payload projection,String generation) {
        return snapshot(row,mapping,projection,generation,mapping.stage(),mapping.chunkIndex());
    }
    private IncidentThreadClaim snapshot(IncidentDelivery row,IncidentThreadPublication mapping,IncidentThreadProjection.Payload projection,String generation,IncidentThreadStage stage,int index) {
        boolean privateSide=switch(stage){case PUBLIC_STARTER,PUBLIC_THREAD,PUBLIC_BODY -> false;default -> true;};
        var context=row.getReport().getWindow().getContext();
        Set<String> participants=java.util.stream.Stream.of(context.getHomeTeam().getEffectiveDiscordRoleId(),context.getAwayTeam().getEffectiveDiscordRoleId())
                .filter(Objects::nonNull).filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
        var destination=new IncidentThreadClaim.Destination(row.getGuildId(),row.getChannelId(),mapping.getStewardsChannelId(),
                IncidentThreadPublication.ids(mapping.getStaffRoleIds(),60),IncidentThreadPublication.ids(mapping.getForumTagIds(),5),participants);
        String marker=marker(row,privateSide,index),content=projection.messages().get(index)+"\n"+marker;
        if(stage==IncidentThreadStage.PRIVATE_REFERENCE) {
            marker="[CTC "+row.getCaseReference()+" REFERENCE]";
            content="Incident "+row.getCaseReference()+"\nMatch discussion: https://discord.com/channels/"+row.getGuildId()+"/"+mapping.getPublicThreadId()+"\n"+marker;
        }
        var work=new IncidentThreadClaim.Work(stage,index,row.getCaseReference(),marker,projection.title(),content,projection.messages().getFirst()+"\n"+marker(row,privateSide,0),
                nonce(row.getReport().getId(),marker),privateSide?mapping.getPrivateStarterId():mapping.getPublicStarterId(),privateSide?mapping.getPrivateThreadId():mapping.getPublicThreadId());
        return new IncidentThreadClaim(row.getId(),row.getReport().getWindow().getId(),row.getClaimToken(),row.getVersion(),generation,row.getLeaseUntil(),destination,work);
    }

    private IncidentDelivery locked(UUID id) {
        if(id==null)return null;var snapshot=deliveries.findById(id).orElse(null);if(snapshot==null)return null;
        var window=windows.lock(snapshot.getReport().getWindow().getId());var row=deliveries.findByIdForUpdate(id).orElse(null);if(row==null)return null;
        em.refresh(row,LockModeType.PESSIMISTIC_WRITE);em.refresh(row.getReport(),LockModeType.PESSIMISTIC_READ);
        if(!window.getId().equals(row.getReport().getWindow().getId()) || !window.getContext().getGuildId().equals(row.getGuildId()) || !window.getContext().getChannelId().equals(row.getChannelId()))throw unavailable();
        return row;
    }
    private IncidentThreadPublication mapping(IncidentDelivery row) {
        var mapping=publications.findByDeliveryIdForUpdate(row.getId()).orElseThrow(IncidentThreadPublicationService::unavailable);
        em.refresh(mapping,LockModeType.PESSIMISTIC_WRITE);return mapping;
    }
    @Transactional(readOnly=true)
    public List<UUID> dueDisclosures(int limit) {
        if(limit<1 || limit>25)throw new IllegalArgumentException("Incident due-work batches must contain between 1 and 25 rows");
        return deliveries.findDueDisclosureIds(now(),PageRequest.of(0,limit));
    }
    private record Completed(IncidentThreadClaim claim,String remote,String message,String thread) { }
    private record Review(IncidentThreadClaim claim,List<Completed> previous) {public Review {previous=List.copyOf(previous);}}
    private static String marker(IncidentDelivery row,boolean privateSide,int index) {return "[CTC "+row.getCaseReference()+" "+(privateSide?"PRIVATE":"PUBLIC")+":"+index+"]";}
    private static String nonce(UUID id,String marker) {
        try {return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest((id+":"+marker).getBytes(StandardCharsets.UTF_8))).substring(0,25);}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("Required SHA-256 is unavailable",impossible);}
    }
    private static IncidentThreadResult normalize(IncidentThreadResult result) {
        if(result==null || result.disposition()==null)return uncertain();
        if(result.disposition()==IncidentSendResult.Disposition.SENT && (result.messageId()==null || !result.messageId().matches("[0-9]{1,20}") || result.threadId()!=null && !result.threadId().matches("[0-9]{1,20}")))return uncertain();
        if(result.disposition()!=IncidentSendResult.Disposition.SENT && (result.code()==null || !result.code().matches("[A-Z0-9_]{1,64}")))return uncertain();
        return result;
    }
    private static IncidentThreadResult uncertain() {return new IncidentThreadResult(IncidentSendResult.Disposition.UNCERTAIN,null,null,"SEND_UNCERTAIN");}
    private Instant now() {return clock.instant().truncatedTo(ChronoUnit.MICROS);}
    private void requireGeneration(String generation) {if(!epoch.managementGeneration().equals(generation))throw unavailable();}
    private static BusinessRuleException unavailable() {return new BusinessRuleException("Incident thread delivery changed or was reset; inspect its current state");}
}
