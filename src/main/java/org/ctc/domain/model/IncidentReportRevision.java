package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "incident_report_revisions", uniqueConstraints = @UniqueConstraint(name = "uq_incident_report_revision", columnNames = {"report_id", "revision_number"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentReportRevision extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false) private IncidentReport report;
    @Column(nullable = false) private int revisionNumber;
    @Column(nullable = false) private long reportVersion;
    @Embedded private IncidentPayload payload;
    @Column(nullable = false, length = 32) private String actorId;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false) private Instant acceptedAt;
    @Column(nullable = false, unique = true) private UUID requestId;

    public IncidentReportRevision(IncidentReport report, int revisionNumber, long reportVersion, IncidentPayload payload,
                                  String actorId, Instant acceptedAt, UUID requestId) {
        this.report = report; this.revisionNumber = revisionNumber; this.reportVersion = reportVersion; this.payload = payload;
        this.actorId = actorId; this.acceptedAt = acceptedAt; this.requestId = requestId;
    }
}
