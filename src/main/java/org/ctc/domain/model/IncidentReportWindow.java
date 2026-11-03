package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "incident_report_windows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentReportWindow extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "context_id", nullable = false, unique = true) private IncidentMatchContext context;
    @Column(name = "aggregate_version", nullable = false) private long version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private IncidentWindowState state = IncidentWindowState.DRAFT;
    private LocalDateTime plannedStartLocal;
    @Convert(converter = UtcInstantConverter.class) private Instant plannedStart;
    @Convert(converter = UtcInstantConverter.class) private Instant deadline;
    @Convert(converter = UtcInstantConverter.class) private Instant openedAt;
    @Column(length = 128) private String openedBy;
    @Convert(converter = UtcInstantConverter.class) private Instant closedAt;

    public IncidentReportWindow(IncidentMatchContext context) { this.context = context; }

    public void requireVersion(long expected) {
        if (version != expected) throw new org.ctc.domain.exception.BusinessRuleException("Reporting window changed; reload its current version");
    }
    public void requireOpenAt(Instant now) {
        if (state != IncidentWindowState.OPEN || deadline == null || !now.isBefore(deadline)) {
            throw new org.ctc.domain.exception.BusinessRuleException("Incident reporting is closed");
        }
    }
    public void open(Instant chosen, LocalDateTime local, Instant planned, String actor, Instant now) {
        this.deadline = chosen; this.plannedStartLocal = local; this.plannedStart = planned;
        this.openedBy = actor; this.openedAt = now; this.state = IncidentWindowState.OPEN; version++;
    }
    public void changeDeadline(Instant chosen) { this.deadline = chosen; version++; }
    public void close(Instant now) { this.state = IncidentWindowState.CLOSED; this.closedAt = now; version++; }
}
