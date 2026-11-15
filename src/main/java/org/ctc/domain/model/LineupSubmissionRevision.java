package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "lineup_submission_revisions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_lineup_revision_number", columnNames = {"submission_id", "revision_number"}),
        @UniqueConstraint(name = "uq_lineup_revision_request", columnNames = "request_id")})
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineupSubmissionRevision extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private LineupSubmission submission;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Convert(converter = UtcInstantConverter.class)
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, length = 16)
    private LineupActorKind actorKind;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(length = 500)
    private String reason;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    public LineupSubmissionRevision(LineupSubmission submission, int revisionNumber, LineupPayload payload, Instant submittedAt,
                                    LineupActorKind actorKind, String actorId, String reason, UUID requestId) {
        this.submission = submission;
        this.revisionNumber = revisionNumber;
        this.payload = payload.toJson();
        this.submittedAt = submittedAt.truncatedTo(ChronoUnit.MICROS);
        this.actorKind = actorKind;
        this.actorId = actorId;
        this.reason = reason;
        this.requestId = requestId;
        this.aggregateVersion = submission.getVersion();
    }

    public LineupPayload lineup() { return LineupPayload.fromJson(payload); }
}
