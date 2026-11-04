package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;

@Entity
@Table(name = "incident_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentDelivery extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false, updatable = false) private IncidentReport report;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "revision_id", nullable = false, updatable = false) private IncidentReportRevision revision;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false) private IncidentDeliveryOperation operation;
    @Column(nullable = false, unique = true, length = 64, updatable = false) private String operationKey;
    @Column(nullable = false, length = 32, updatable = false) private String guildId;
    @Column(nullable = false, length = 32, updatable = false) private String channelId;
    @Column(nullable = false, length = 25, updatable = false) private String nonce;
    @Column(nullable = false, length = 64, updatable = false) private String caseReference;
    @Column(length = 255, updatable = false) private String partyLabel;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false, updatable = false) private Instant capturedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private IncidentDeliveryState state = IncidentDeliveryState.PENDING;
    @Column(name = "aggregate_version", nullable = false) private long version;
    @Column(nullable = false) private int attempts;
    private UUID claimToken;
    @Column(length = 64) private String claimGeneration;
    @Convert(converter = UtcInstantConverter.class) private Instant leaseUntil;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false) private Instant nextAttemptAt;
    @Convert(converter = UtcInstantConverter.class) private Instant completedAt;
    @Column(length = 32) private String messageId;
    @Column(length = 64) private String failureCode;
    @Column(nullable = false) private boolean restoreReviewed;
    @Column(length = 128) private String reconciledBy;
    @Column(length = 1000) private String reconciliationReason;

    public static IncidentDelivery capture(IncidentReport report, IncidentReportRevision revision, IncidentDeliveryOperation operation, Instant capturedAt) {
        if (report == null || report.getId() == null || revision == null || revision.getReport() == null
                || !report.getId().equals(revision.getReport().getId()) || revision.getId() == null || capturedAt == null || operation == null) {
            throw new BusinessRuleException("Incident delivery requires its own permanent report revision");
        }
        var delivery = new IncidentDelivery(); delivery.report = report; delivery.revision = revision; delivery.operation = operation;
        delivery.operationKey = report.getId() + ":" + operation;
        delivery.guildId = report.getWindow().getContext().getGuildId(); delivery.channelId = report.getWindow().getContext().getChannelId();
        delivery.caseReference = report.getCaseReference(); delivery.partyLabel = operation == IncidentDeliveryOperation.DISCLOSURE ? report.getParty().getLabel() : null;
        delivery.capturedAt = capturedAt; delivery.nextAttemptAt = capturedAt;
        delivery.nonce = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.nio.ByteBuffer.allocate(16)
                .putLong(report.getId().getMostSignificantBits()).putLong(report.getId().getLeastSignificantBits()).array())
                + (operation == IncidentDeliveryOperation.RECEIPT ? "R" : "D");
        return delivery;
    }
    public void claim(UUID token, String generation, Instant now, Instant until) {
        if (token == null || generation == null || generation.isBlank() || generation.length() > 64
                || now == null || until == null || !until.isAfter(now) || report.getWindow().isDeliveryPaused()
                || state != IncidentDeliveryState.PENDING && state != IncidentDeliveryState.FAILED || now.isBefore(nextAttemptAt)) throw unavailable();
        state = IncidentDeliveryState.CLAIMED; claimToken = token; claimGeneration = generation; leaseUntil = until;
        attempts++; version++; completedAt = null; failureCode = null; messageId = null;
    }

    public void acknowledge(UUID token, long expectedVersion, String generation, Instant now, String remoteMessageId) {
        requireClaim(token, expectedVersion, generation, now);
        if (remoteMessageId == null || !remoteMessageId.matches("[0-9]{1,32}")) throw unavailable();
        state = IncidentDeliveryState.SENT; messageId = remoteMessageId; completedAt = now; failureCode = null; version++;
    }

    public void continueDisclosure(UUID token,long expectedVersion,String generation,Instant now) {
        requireClaim(token,expectedVersion,generation,now);
        if(operation!=IncidentDeliveryOperation.DISCLOSURE)throw unavailable();
        state=IncidentDeliveryState.PENDING;nextAttemptAt=now;completedAt=null;failureCode=null;messageId=null;restoreReviewed=false;
        claimToken=null;claimGeneration=null;leaseUntil=null;version++;
    }

    public void fail(UUID token, long expectedVersion, String generation, Instant now, String code, boolean uncertain, Instant retryAt) {
        requireClaim(token, expectedVersion, generation, now);
        if (code == null || !code.matches("[A-Z0-9_]{1,64}") || retryAt == null || !retryAt.isAfter(now)) throw unavailable();
        state = uncertain ? IncidentDeliveryState.UNCERTAIN : IncidentDeliveryState.FAILED;
        failureCode = code; completedAt = now; nextAttemptAt = retryAt; version++;
    }

    public boolean expireClaim(Instant now) {
        if (state != IncidentDeliveryState.CLAIMED || report.getWindow().isDeliveryPaused() || now == null || now.isBefore(leaseUntil)) return false;
        state = IncidentDeliveryState.UNCERTAIN; failureCode = "CLAIM_EXPIRED"; completedAt = now; version++; return true;
    }

    public boolean supersede(Instant now) {
        if (operation != IncidentDeliveryOperation.RECEIPT || report.getWindow().isDeliveryPaused()
                || state != IncidentDeliveryState.PENDING && state != IncidentDeliveryState.FAILED) return false;
        if (now == null || now.isBefore(capturedAt)) throw unavailable();
        state = IncidentDeliveryState.SUPERSEDED; completedAt = now; failureCode = null; version++; return true;
    }

    public void requireReview(long expectedVersion,Instant now) {
        if(version!=expectedVersion || now==null || now.isBefore(capturedAt) || state==IncidentDeliveryState.SENT || state==IncidentDeliveryState.SUPERSEDED
                || state==IncidentDeliveryState.CLAIMED && !report.getWindow().isDeliveryPaused() && now.isBefore(leaseUntil)) throw unavailable();
    }
    public void reconcileMissing(long expectedVersion,String actor,String reason,Instant now) {
        requireReview(expectedVersion,now);recordReview(actor,reason);
        state=IncidentDeliveryState.PENDING;nextAttemptAt=now;completedAt=null;messageId=null;failureCode=null;
        claimToken=null;claimGeneration=null;leaseUntil=null;version++;
    }
    public void reconcileDelivered(long expectedVersion,String actor,String reason,Instant now,String remoteMessageId) {
        requireReview(expectedVersion,now);
        if(remoteMessageId==null || !remoteMessageId.matches("[0-9]{1,32}"))throw unavailable();
        recordReview(actor,reason);state=IncidentDeliveryState.SENT;messageId=remoteMessageId;completedAt=now;failureCode=null;
        attempts=Math.max(attempts,1);version++;
    }
    public void reconcileDisclosureStep(long expectedVersion,String actor,String reason,Instant now,String completedPublicThreadId) {
        if(operation!=IncidentDeliveryOperation.DISCLOSURE)throw unavailable();
        if(completedPublicThreadId!=null)reconcileDelivered(expectedVersion,actor,reason,now,completedPublicThreadId);
        else {reconcileMissing(expectedVersion,actor,reason,now);restoreReviewed=false;}
    }
    private void recordReview(String actor,String reason) {
        if(actor==null || actor.isBlank() || actor.length()>128 || reason==null || reason.isBlank() || reason.length()>1000)throw unavailable();
        reconciledBy=actor;reconciliationReason=reason;restoreReviewed=true;
    }

    private void requireClaim(UUID token, long expectedVersion, String generation, Instant now) {
        if (state != IncidentDeliveryState.CLAIMED || report.getWindow().isDeliveryPaused() || version != expectedVersion
                || token == null || !token.equals(claimToken) || generation == null || !generation.equals(claimGeneration)
                || now == null || now.isBefore(capturedAt) || !now.isBefore(leaseUntil)) throw unavailable();
    }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("Incident delivery claim changed or is unavailable; reload its current state"); }
    @Override public String toString() { return "IncidentDelivery[" + operation + "," + state + "]"; }
}
