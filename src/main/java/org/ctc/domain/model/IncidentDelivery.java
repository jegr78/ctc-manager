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
    @Override public String toString() { return "IncidentDelivery[" + operation + "," + state + "]"; }
}
