package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import lombok.Getter;

@Entity
@Table(name = "matchday_pick_decisions", uniqueConstraints =
        @UniqueConstraint(name = "uq_pick_decision_version", columnNames = {"pick_id", "aggregate_version"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchdayPickDecision extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pick_id", nullable = false)
    private MatchdayPick pick;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PickDecisionKind kind;
    @Column(nullable = false)
    private long aggregateVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PickActorKind actorKind;
    @Column(nullable = false, length = 128)
    private String actorId;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Convert(converter = UtcInstantConverter.class)
    @Column(nullable = false)
    private Instant decidedAt;

    @Embedded
    private PickPayload correctionPayload;

    public MatchdayPickDecision(MatchdayPick pick, PickDecisionKind kind, long aggregateVersion,
                               String actorId, String reason, Instant decidedAt) {
        this(pick, kind, aggregateVersion, actorId, reason, decidedAt, null);
    }

    public MatchdayPickDecision(MatchdayPick pick, PickDecisionKind kind, long aggregateVersion,
                               String actorId, String reason, Instant decidedAt, PickPayload correctionPayload) {
        this.pick = pick;
        this.kind = kind;
        this.aggregateVersion = aggregateVersion;
        this.actorKind = PickActorKind.MANAGEMENT;
        this.actorId = actorId;
        this.reason = reason;
        this.decidedAt = decidedAt.truncatedTo(ChronoUnit.MICROS);
        this.correctionPayload = correctionPayload;
    }
}
