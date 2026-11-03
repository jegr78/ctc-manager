package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "pick_publication_snapshots")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickPublicationSnapshot extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "attempt_id", nullable = false, unique = true) private PickPublicationAttempt attempt;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false) private Instant confirmedAt;

    public PickPublicationSnapshot(PickPublicationAttempt attempt) {
        if (attempt == null || attempt.getState() != PickPublicationState.SUCCEEDED || attempt.fields().isEmpty()
                || attempt.getMessageId() == null || attempt.getMessageId().isBlank() || attempt.getContent() == null
                || !attempt.getContent().fingerprint().equals(attempt.getContentHash()) || attempt.getCompletedAt() == null) {
            throw new BusinessRuleException("Publication snapshot requires exact confirmed content and disclosed fields");
        }
        this.attempt = attempt;
        confirmedAt = attempt.getResolvedAt() == null ? attempt.getCompletedAt() : attempt.getResolvedAt();
    }
}
