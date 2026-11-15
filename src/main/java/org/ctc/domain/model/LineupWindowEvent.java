package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** A management decision to open or close lineup intake for one leg; the highest sequence number is effective. */
@Entity
@Table(name = "lineup_window_events", uniqueConstraints = @UniqueConstraint(name = "uq_lineup_window_sequence", columnNames = {"race_id", "sequence_number"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineupWindowEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private LineupWindowKind kind;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Convert(converter = UtcInstantConverter.class)
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public LineupWindowEvent(Race race, int sequenceNumber, LineupWindowKind kind, String reason, String actorId, Instant occurredAt) {
        this.race = race;
        this.sequenceNumber = sequenceNumber;
        this.kind = kind;
        this.reason = reason;
        this.actorId = actorId;
        this.occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
    }
}
