package org.ctc.domain.model;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.service.IncidentRulingProjection;

@Entity
@Table(name = "incident_ruling_publications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentRulingPublication extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "revision_id", nullable = false, unique = true, updatable = false)
    private IncidentRulingRevision revision;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "thread_publication_id", nullable = false, updatable = false)
    private IncidentThreadPublication threadPublication;
    @Column(nullable = false, length = 32, updatable = false) private String guildId;
    @Column(nullable = false, length = 32, updatable = false) private String channelId;
    @Column(nullable = false, length = 2000, updatable = false) private String content;
    @Column(length = 2000, updatable = false) private String previousContent;
    @Column(updatable = false) private Integer previousRevisionNumber;
    @Column(length = 32, updatable = false) private String previousMessageId;
    @Column(nullable = false, length = 25, updatable = false) private String nonce;
    @Column(nullable = false, length = 128, updatable = false) private String requestedBy;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false, updatable = false) private Instant requestedAt;
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

    public static IncidentRulingPublication capture(IncidentRulingRevision revision, IncidentThreadPublication thread,
            String content, String previousContent, IncidentManagementActor actor, Instant now) {
        if (revision == null || revision.getId() == null || thread == null || thread.getId() == null
                || thread.getDelivery() == null || thread.getDelivery().getReport() == null || actor == null || actor.id() == null || actor.id().isBlank()
                || actor.id().length() > 128 || now == null) throw unavailable();
        var root = revision.getRuling();
        if (root == null || root.getId() == null) throw unavailable();
        var report = root.getReport();
        if (report == null || report.getWindow() == null || !report.getId().equals(thread.getDelivery().getReport().getId())
                || report.getState() != IncidentReportState.ACTIVE || report.getWindow().getState() != IncidentWindowState.CLOSED
                || report.getWindow().getClosedAt() == null || report.getWindow().getDeadline() == null
                || now.isBefore(report.getWindow().getClosedAt()) || now.isBefore(report.getWindow().getDeadline())
                || report.getWindow().isDeliveryPaused() || report.getFrozenRevisionNumber() == null
                || !thread.publicComplete() || !snowflake(thread.getPublicThreadId()) || !snowflake(thread.getDelivery().getGuildId())
                || !Objects.equals(root.getRequestedRevisionNumber(), revision.getRevisionNumber())
                || now.isBefore(revision.getAcceptedAt())
                || !IncidentRulingProjection.render(report.getId(), report.getCaseReference(), revision.getText()).equals(content)) throw unavailable();
        boolean correction = root.getPublishedRevisionNumber() != null;
        if (correction ? !snowflake(root.getPublishedMessageId()) || previousContent == null || previousContent.isBlank()
                || previousContent.length() > 2000 || revision.getReason().isBlank() : previousContent != null || root.getPublishedMessageId() != null) throw unavailable();
        var row = new IncidentRulingPublication(); row.revision = revision; row.threadPublication = thread;
        row.guildId = thread.getDelivery().getGuildId(); row.channelId = thread.getPublicThreadId(); row.content = content;
        row.previousContent = previousContent; row.previousRevisionNumber = root.getPublishedRevisionNumber(); row.previousMessageId = root.getPublishedMessageId();
        row.nonce = nonce(report.getId(), content); row.requestedBy = actor.id(); row.requestedAt = now; row.nextAttemptAt = now;
        return row;
    }

    public static String nonce(UUID reportId, String content) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest((reportId + ":" + content).getBytes(StandardCharsets.UTF_8))).substring(0, 25);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("Required SHA-256 is unavailable", impossible); }
    }
    public void claim(UUID token, String generation, Instant now, Instant until) {
        if (token == null || generation == null || generation.isBlank() || generation.length() > 64 || now == null
                || until == null || !until.isAfter(now) || paused() || now.isBefore(nextAttemptAt)
                || state != IncidentDeliveryState.PENDING && state != IncidentDeliveryState.FAILED
                || version == Long.MAX_VALUE || attempts == Integer.MAX_VALUE) throw unavailable();
        state = IncidentDeliveryState.CLAIMED; claimToken = token; claimGeneration = generation; leaseUntil = until;
        attempts++; version++; completedAt = null; failureCode = null; messageId = null;
    }
    public void acknowledge(UUID token, long expectedVersion, String generation, Instant now, String remoteId) {
        requireClaim(token, expectedVersion, generation, now);
        if (!snowflake(remoteId) || previousMessageId != null && !previousMessageId.equals(remoteId)) throw unavailable();
        state = IncidentDeliveryState.SENT; messageId = remoteId; completedAt = now; failureCode = null; version++;
    }
    public void fail(UUID token, long expectedVersion, String generation, Instant now, String code, boolean uncertain, Instant retryAt) {
        requireClaim(token, expectedVersion, generation, now);
        if (code == null || !code.matches("[A-Z0-9_]{1,64}") || retryAt == null || !retryAt.isAfter(now)) throw unavailable();
        state = uncertain ? IncidentDeliveryState.UNCERTAIN : IncidentDeliveryState.FAILED;
        failureCode = code; completedAt = now; nextAttemptAt = retryAt; version++;
    }
    public boolean expireClaim(Instant now) {
        if (state != IncidentDeliveryState.CLAIMED || paused() || now == null || now.isBefore(leaseUntil)) return false;
        state = IncidentDeliveryState.UNCERTAIN; failureCode = "CLAIM_EXPIRED"; completedAt = now; version++; return true;
    }
    public void supersede(Instant now) {
        if (paused() || state != IncidentDeliveryState.PENDING && state != IncidentDeliveryState.FAILED
                || revision.getRuling().getReport().getWindow().getDeliveryResumedAt() != null && !restoreReviewed
                || now == null || now.isBefore(requestedAt) || version == Long.MAX_VALUE) throw unavailable();
        state = IncidentDeliveryState.SUPERSEDED; completedAt = now; failureCode = null; version++;
    }
    public void requireReview(long expectedVersion, Instant now) {
        if (version != expectedVersion || now == null || now.isBefore(requestedAt) || version == Long.MAX_VALUE
                || state == IncidentDeliveryState.SENT || state == IncidentDeliveryState.SUPERSEDED
                || state == IncidentDeliveryState.CLAIMED && !paused() && now.isBefore(leaseUntil)) throw unavailable();
    }
    public void reconcileMissing(long expectedVersion, String actor, String reason, Instant now) {
        requireReview(expectedVersion, now); recordReview(actor, reason);
        state = IncidentDeliveryState.PENDING; nextAttemptAt = now; completedAt = null; messageId = null; failureCode = null;
        claimToken = null; claimGeneration = null; leaseUntil = null; version++;
    }
    public void reconcileDelivered(long expectedVersion, String actor, String reason, Instant now, String remoteId) {
        requireReview(expectedVersion, now);
        if (!snowflake(remoteId) || previousMessageId != null && !previousMessageId.equals(remoteId)) throw unavailable();
        recordReview(actor, reason); state = IncidentDeliveryState.SENT; messageId = remoteId; completedAt = now; failureCode = null;
        attempts = Math.max(1, attempts); version++;
    }
    private void recordReview(String actor, String reason) {
        if (actor == null || actor.isBlank() || actor.length() > 128 || reason == null || reason.isBlank() || reason.length() > 1000) throw unavailable();
        reconciledBy = actor; reconciliationReason = reason; restoreReviewed = true;
    }
    private boolean paused() { return revision.getRuling().getReport().getWindow().isDeliveryPaused(); }
    private void requireClaim(UUID token, long expectedVersion, String generation, Instant now) {
        if (state != IncidentDeliveryState.CLAIMED || paused() || version != expectedVersion || version == Long.MAX_VALUE
                || token == null || !token.equals(claimToken) || generation == null || !generation.equals(claimGeneration)
                || now == null || now.isBefore(requestedAt) || !now.isBefore(leaseUntil)) throw unavailable();
    }
    private static boolean snowflake(String value) { return value != null && value.matches("[0-9]{1,20}"); }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("Ruling publication changed or requires delivery review; reload the current case"); }
    @Override public String toString() { return "IncidentRulingPublication[" + state + "]"; }
}
