package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "incident_report_events")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentReportEvent extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "window_id", nullable = false) private IncidentReportWindow window;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id") private IncidentReport report;
    @Column(nullable = false, unique = true, length = 120) private String eventKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private IncidentEventKind kind;
    @Column(nullable = false) private long windowVersion;
    private Long reportVersion;
    private Integer revisionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private IncidentActorKind actorKind;
    @Column(nullable = false, length = 128) private String actorId;
    @Column(nullable = false, length = 1000) private String reason;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false) private Instant acceptedAt;
    @Convert(converter = UtcInstantConverter.class) private Instant previousDeadline;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false) private Instant deadline;
    @Column(unique = true) private UUID requestId;

    public static IncidentReportEvent windowDecision(IncidentReportWindow window, IncidentEventKind kind,
            IncidentActorKind actorKind, String actorId, String reason, Instant now, Instant previousDeadline) {
        var event = new IncidentReportEvent(); event.window = window; event.kind = kind;
        event.eventKey = "window:" + window.getId() + ":" + window.getVersion();
        event.windowVersion = window.getVersion(); event.actorKind = actorKind; event.actorId = actorId;
        event.reason = reason; event.acceptedAt = now; event.previousDeadline = previousDeadline; event.deadline = window.getDeadline();
        return event;
    }
    public static IncidentReportEvent reportDecision(IncidentReport report, IncidentEventKind kind, String actorId,
            Instant now, UUID requestId) {
        var event = new IncidentReportEvent(); event.window = report.getWindow(); event.report = report; event.kind = kind;
        event.eventKey = "report:" + report.getId() + ":" + report.getVersion();
        event.windowVersion = report.getWindow().getVersion(); event.reportVersion = report.getVersion();
        event.revisionNumber = report.getLatestRevisionNumber(); event.actorKind = IncidentActorKind.REPORTER;
        event.actorId = actorId; event.reason = ""; event.acceptedAt = now; event.deadline = report.getWindow().getDeadline();
        event.requestId = requestId;
        return event;
    }
    public org.ctc.domain.incidents.IncidentReportReceipt receipt() {
        if (report == null || requestId == null) throw new IllegalStateException("Only a report decision has a submission receipt");
        return new org.ctc.domain.incidents.IncidentReportReceipt(report.getId(), report.getCaseReference(), reportVersion,
                revisionNumber, kind, acceptedAt, requestId);
    }
}
