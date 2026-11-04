package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;

@Entity
@Table(name = "incident_rulings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentRuling extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false, unique = true, updatable = false)
    private IncidentReport report;
    @Column(name = "aggregate_version", nullable = false) private long version;
    @Column(nullable = false) private int latestRevisionNumber;
    private Integer requestedRevisionNumber;
    private Integer publishedRevisionNumber;
    @Column(length = 32) private String publishedMessageId;

    public static IncidentRuling forReport(IncidentReport report) {
        if (report == null || report.getId() == null) throw unavailable();
        var ruling = new IncidentRuling();
        ruling.report = report;
        return ruling;
    }

    public IncidentRulingRevision draft(long expectedVersion, String text, String reason, IncidentManagementActor actor, Instant at) {
        requireVersion(expectedVersion);
        if (actor == null || actor.id() == null || actor.id().isBlank() || actor.id().length() > 128 || at == null) throw unavailable();
        String explanation = reason == null ? "" : reason;
        if (explanation.length() > 1000 || publishedRevisionNumber != null && explanation.isBlank()) {
            throw new BusinessRuleException("A correction requires a reason of at most 1000 characters");
        }
        String accepted = IncidentRulingRevision.acceptedText(text);
        if (latestRevisionNumber == Integer.MAX_VALUE || version == Long.MAX_VALUE) throw unavailable();
        var revision = new IncidentRulingRevision(this, latestRevisionNumber + 1, accepted, explanation, actor.id(), at);
        latestRevisionNumber++; version++;
        return revision;
    }

    public void requireVersion(long expectedVersion) {
        if (expectedVersion != version) throw unavailable();
    }

    public void requestPublication(long expectedVersion, int revision) {
        requireVersion(expectedVersion);
        if (revision < 1 || revision != latestRevisionNumber || Objects.equals(requestedRevisionNumber, revision)
                || publishedRevisionNumber != null && publishedRevisionNumber >= revision || version == Long.MAX_VALUE) throw unavailable();
        requestedRevisionNumber = revision; version++;
    }

    public void confirmPublication(int revision, Integer previous, String messageId) {
        if (!Objects.equals(requestedRevisionNumber, revision) || !Objects.equals(publishedRevisionNumber, previous)
                || messageId == null || !messageId.matches("[0-9]{1,20}")
                || publishedMessageId != null && !publishedMessageId.equals(messageId) || version == Long.MAX_VALUE) throw unavailable();
        publishedRevisionNumber = revision; publishedMessageId = messageId; version++;
    }

    private static BusinessRuleException unavailable() { return new BusinessRuleException("Incident ruling state changed; reload the current case"); }
    @Override public String toString() { return "IncidentRuling[private]"; }
}
