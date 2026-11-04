package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "incident_ruling_revisions", uniqueConstraints = @UniqueConstraint(name = "uq_incident_ruling_revision", columnNames = {"ruling_id", "revision_number"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentRulingRevision extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ruling_id", nullable = false, updatable = false)
    private IncidentRuling ruling;
    @Column(nullable = false) private int revisionNumber;
    @Column(name = "ruling_text", nullable = false, length = 1800) private String text;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false, length = 128) private String actorId;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false) private Instant acceptedAt;

    IncidentRulingRevision(IncidentRuling ruling, int revisionNumber, String text, String reason, String actorId, Instant acceptedAt) {
        this.ruling = ruling; this.revisionNumber = revisionNumber; this.text = text;
        this.reason = reason; this.actorId = actorId; this.acceptedAt = acceptedAt;
    }

    public static String acceptedText(String text) {
        if (text == null || text.isBlank() || text.length() > 1800) throw new BusinessRuleException("Enter a ruling of 1 to 1800 characters");
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(++i))) throw invalidUnicode();
            } else if (Character.isLowSurrogate(current)) throw invalidUnicode();
        }
        return text;
    }
    private static BusinessRuleException invalidUnicode() { return new BusinessRuleException("Use valid Unicode text for the ruling"); }
    @Override public String toString() { return "IncidentRulingRevision[private]"; }
}
